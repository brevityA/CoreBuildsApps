/**
 * Core Builds icon-request broker — a Cloudflare Worker.
 *
 * What it is: the one trusted hop that lets the icon pack's on-device
 * auditor file icon requests for people who do not have (and should never
 * need) a GitHub account. The auditor POSTs three validated fields; this
 * worker files (or +1s) a GitHub issue as a bot — or, when the GitHub App
 * is not configured yet, forwards the same payload to a Discord webhook,
 * because the Core Builds webtools worker (core-builds-cors-proxy) already
 * runs exactly that pattern in production. The GitHub App credential —
 * private key — lives only here as a Workers secret, scoped to `issues:
 * write` on one repo. Never in the APK, never in this repository.
 *
 * Ops lineage: this file deliberately follows the conventions of the
 * Core-Builds `cloudflare-worker` (core-builds-cors-proxy): a version tag
 * compared by smoke checks, a byte-capped body reader, layered rate
 * limiting (Workers Rate Limiting binding → KV hourly bucket →
 * in-isolate floor), route table at the bottom, and structured,
 * secret-free log lines. Differences from a blind copy are documented in
 * README.md.
 *
 * What it refuses to be: a general text channel. Every field is validated
 * against an exact grammar, free-form prose cannot reach GitHub or
 * Discord through this endpoint, and a repeat press dedupes (GitHub:
 * +1 comment; Discord: the same formatted card).
 */

// Shared literals live in constants.mjs — see that file for why a Workers
// entry module must not export plain strings (workerd refuses to boot).
import { REPO, ISSUE_LABEL, TITLE_PREFIX, WORKER_VERSION } from "./constants.mjs";

const COMPONENT_RE = /^[A-Za-z0-9_.]+\/[A-Za-z0-9_.$]+$/;
const MAX_APP_NAME = 80;
const MAX_DEVICE_NOTE = 120;
const BODY_CAP = 2 * 1024;             // bytes; the audit payload is ~300
const RATE_PER_HOUR = 5;
const ISOLATE_FLOOR_PER_HOUR = 25;

// ---------------------------------------------------------------------------
// small utilities in the conventions of the reference worker

export function logEvent(evt) {
  try { console.info(JSON.stringify({ ts: Date.now(), worker: "icon-request", ...evt })); }
  catch (_) { /* logging must never fail the route */ }
}

/** Raw body, rejecting streams above the cap: a POST this small never needs one. */
export async function readCapped(request, cap = BODY_CAP) {
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > cap) throw Object.assign(new Error("payload too large"), { status: 413 });
    chunks.push(value);
  }
  const merged = new Uint8Array(total);
  let at = 0;
  for (const c of chunks) { merged.set(c, at); at += c.byteLength; }
  return new TextDecoder().decode(merged);
}

export function getClientIp(request) {
  // cf-connecting-ip is edge-set and client-proof; X-Forwarded-For never.
  return request.headers.get("cf-connecting-ip") || "";
}

// ---------------------------------------------------------------------------
// payload grammar — the same three fields the issue-form prefill carries

export function validate(payload) {
  if (payload === null || typeof payload !== "object" || Array.isArray(payload)) {
    return { ok: false, status: 400, error: "expected a JSON object" };
  }
  const keys = Object.keys(payload);
  const unknown = keys.filter((k) => !["app_name", "component", "device"].includes(k));
  if (unknown.length) return { ok: false, status: 400, error: `unknown fields: ${unknown.join(", ")}` };

  const appName = payload.app_name;
  if (typeof appName !== "string" || appName.trim().length === 0 ||
      appName.length > MAX_APP_NAME || /[\r\n]/.test(appName)) {
    return { ok: false, status: 400, error: "app_name must be one printable line, " +
      `at most ${MAX_APP_NAME} chars` };
  }
  const component = payload.component;
  if (typeof component !== "string" || !COMPONENT_RE.test(component)) {
    return { ok: false, status: 400, error: "component must look like com.example/.MainActivity" };
  }
  const device = payload.device ?? "";
  if (typeof device !== "string" || device.length > MAX_DEVICE_NOTE ||
      /[\r\n]/.test(device)) {
    return { ok: false, status: 400, error: "device must be a single printable line" };
  }
  return { ok: true, value: { appName: appName.trim(), component, device } };
}

// ---------------------------------------------------------------------------
// layered rate limiting, strongest available wins (see the reference worker):
//   1. Workers Rate Limiting binding (per-colo, shared across isolates)
//   2. KV hourly bucket — a KV WRITE failure inside the window counts AGAINST
//      the caller (someone else is writing this key), per the audit note
//   3. in-isolate floor that always exists, even with zero bindings

export function rateLimitKey(ip, at = Date.now()) {
  const hour = Math.floor(at / 3_600_000);
  return `rate:${ip}:${hour}`;
}

const isolateWindows = new Map();      // ip -> {hour, count}
const MAX_ISOLATE_KEYS = 10_000;       // never let the floor map grow without one

function isolateLimited(ip) {
  const hour = Math.floor(Date.now() / 3_600_000);
  let slot = isolateWindows.get(ip);
  if (!slot) {
    if (isolateWindows.size >= MAX_ISOLATE_KEYS) isolateWindows.clear();
    slot = { hour, count: 0 };
    isolateWindows.set(ip, slot);
  }
  if (slot.hour !== hour) { slot.hour = hour; slot.count = 0; }
  slot.count += 1;
  return slot.count > ISOLATE_FLOOR_PER_HOUR;
}

export async function rateLimited(env, ip) {
  if (!ip) return false;               // cannot fairly bucket an unknown client
  if (env.RL_ICON_REQUEST) {
    const { success } = await env.RL_ICON_REQUEST.limit({ key: ip });
    return !success;
  }
  if (env.RATE_KV) {
    const key = rateLimitKey(ip);
    const count = Number(await env.RATE_KV.get(key)) || 0;
    const cap = Number(env.RATE_LIMIT_PER_HOUR) || RATE_PER_HOUR;
    if (count >= cap) return true;
    try {
      await env.RATE_KV.put(key, String(count + 1), { expirationTtl: 3700 });
    } catch (_) {
      return true;                     // write collision inside the window = counted
    }
    return false;
  }
  return isolateLimited(ip);
}

// ---------------------------------------------------------------------------
// GitHub App authentication: RS256 JWT -> installation token, cached in KV

function b64url(buf) {
  const bytes = buf instanceof ArrayBuffer ? new Uint8Array(buf) : buf;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function appJwt(env) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const payload = b64url(new TextEncoder().encode(JSON.stringify({
    iat: now - 60, exp: now + 540, iss: env.GITHUB_APP_ID,
  })));
  const pem = env.GITHUB_APP_PRIVATE_KEY.replace(/\\n/g, "\n");
  const pkcs8 = Uint8Array.from(atob(pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "")), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", pkcs8.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key,
    new TextEncoder().encode(`${header}.${payload}`));
  return `${header}.${payload}.${b64url(sig)}`;
}

export async function installationToken(env) {
  if (env.RATE_KV) {
    const cached = await env.RATE_KV.get("gh:installation_token");
    if (cached) return cached;
  }
  const jwt = await appJwt(env);
  const res = await fetch(
    `https://api.github.com/app/installations/${env.GITHUB_APP_INSTALLATION_ID}/access_tokens`,
    { method: "POST", headers: ghHeaders(`Bearer ${jwt}`) });
  if (!res.ok) throw new Error(`installation token request failed: ${res.status}`);
  const { token } = await res.json();
  if (env.RATE_KV) await env.RATE_KV.put("gh:installation_token", token, { expirationTtl: 3000 });
  return token;
}

function ghHeaders(auth) {
  return {
    Authorization: auth,
    Accept: "application/vnd.github+json",
    "User-Agent": "corebuilds-icon-request-broker/1.0",
    "X-GitHub-Api-Version": "2022-11-28",
  };
}

// ---------------------------------------------------------------------------
// sinks: GitHub (bot files or +1s the issue) or Discord (a formatted card)

/** Issue body: mirrors the new-icon-request form's field order. */
export function issueBody({ appName, component, device }) {
  return [
    "### App name", "", appName,
    "", "### Component name", "", component,
    "", "### Store or download link", "",
    "_not supplied — device-side auditor report; a real APK or store link matters",
    "_only when the component cannot be reproduced._",
    "", "### Device type", "", device || "_not supplied_",
    "", "### Anything else", "",
    "_Filed by CoreBuilds-requests[bot] from the in-app auditor's one-press",
    "request — anonymous device report; no GitHub account on the reporter side._",
  ].join("\n");
}

async function findOpenDuplicate(token, appName) {
  const q = encodeURIComponent(`${TITLE_PREFIX.trim()} "${appName}" in:title repo:${REPO}`);
  const res = await fetch(`https://api.github.com/search/issues?q=${q}&state=open&per_page=5`,
    { headers: ghHeaders(`Bearer ${token}`) });
  if (!res.ok) return null;
  const { items } = await res.json();
  const hit = (items || []).find((i) => i.state === "open" &&
    i.title.toLowerCase().includes(appName.toLowerCase()));
  return hit ? hit.number : null;
}

async function ensureLabel(token) {
  const name = encodeURIComponent(ISSUE_LABEL);
  const res = await fetch(`https://api.github.com/repos/${REPO}/labels/${name}`,
    { headers: ghHeaders(`Bearer ${token}`) });
  if (res.status === 404) {
    await fetch(`https://api.github.com/repos/${REPO}/labels`, {
      method: "POST", headers: ghHeaders(`Bearer ${token}`),
      body: JSON.stringify({ name: ISSUE_LABEL, color: "0d8048",
        description: "App icon requests (incl. anonymous auditor reports)" }),
    });
  }
}

async function fileOrComment(token, value) {
  const dup = await findOpenDuplicate(token, value.appName);
  if (dup) {
    await fetch(`https://api.github.com/repos/${REPO}/issues/${dup}/comments`, {
      method: "POST", headers: ghHeaders(`Bearer ${token}`),
      body: JSON.stringify({ body:
        `+1 from the in-app auditor's one-press request` +
        (value.device ? ` — ${value.device}` : "") +
        ". A repeat counts as a vote, not a new issue." }),
    });
    return { number: dup, duplicate: true };
  }
  await ensureLabel(token);
  const res = await fetch(`https://api.github.com/repos/${REPO}/issues`, {
    method: "POST", headers: ghHeaders(`Bearer ${token}`),
    body: JSON.stringify({ title: `${TITLE_PREFIX}${value.appName}`,
      body: issueBody(value), labels: [ISSUE_LABEL] }),
  });
  if (!res.ok) throw new Error(`issue create failed: ${res.status}`);
  const { number } = await res.json();
  return { number, duplicate: false };
}

function githubConfigured(env) {
  return Boolean(env.GITHUB_APP_ID && env.GITHUB_APP_INSTALLATION_ID &&
                 env.GITHUB_APP_PRIVATE_KEY);
}

/** The Discord card, same three fields, same labels as the issue body. */
export function discordPayload(value) {
  return {
    username: "Core Builds Icon Auditor",
    embeds: [{
      title: `${TITLE_PREFIX}${value.appName}`,
      color: 0x32c8f0,
      fields: [
        { name: "Component name", value: "`" + value.component + "`" },
        { name: "Device", value: value.device || "_not supplied_" },
      ],
      footer: { text: "One-press anonymous auditor request — convert to a GitHub issue" },
    }],
  };
}

async function postDiscord(env, value) {
  const res = await fetch(env.ICON_REQUEST_DISCORD_WEBHOOK_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(discordPayload(value)),
  });
  if (!res.ok) throw new Error(`discord webhook failed: ${res.status}`);
}

// ---------------------------------------------------------------------------
// routes

async function handleRequest(request, env) {
  if (await rateLimited(env, getClientIp(request))) {
    logEvent({ evt: "rate_limited" });
    return json({ ok: false, error: "rate limited; the QR fallback is right there" }, 429);
  }
  let raw;
  try { raw = await readCapped(request); }
  catch (err) { return json({ ok: false, error: err.message }, err.status || 400); }

  let payload;
  try { payload = JSON.parse(raw); }
  catch (_) { return json({ ok: false, error: "body is not JSON" }, 400); }

  const verdict = validate(payload);
  if (!verdict.ok) return json({ ok: false, error: verdict.error }, verdict.status);

  try {
    if (githubConfigured(env)) {
      const token = await installationToken(env);
      const { number, duplicate } = await fileOrComment(token, verdict.value);
      logEvent({ evt: duplicate ? "issue_plus_one" : "issue_created", issue: number });
      return json({ ok: true, issue: number, duplicate }, duplicate ? 200 : 201);
    }
    if (env.ICON_REQUEST_DISCORD_WEBHOOK_URL) {
      await postDiscord(env, verdict.value);
      logEvent({ evt: "discord_forwarded" });
      return json({ ok: true, via: "discord" }, 201);
    }
    logEvent({ evt: "not_configured" });
    return json({ ok: false, error: "broker not configured" }, 503);
  } catch (err) {
    logEvent({ evt: "sink_error", msg: String(err).slice(0, 120) });
    return json({ ok: false, error: String(err) }, 502);
  }
}

function json(body, status) {
  return new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": "application/json" },
  });
}

export async function handle(request, env) {
  const url = new URL(request.url);
  if (url.pathname === "/healthz") {
    return json({ ok: true, version: WORKER_VERSION }, 200);
  }
  if (url.pathname !== "/" && url.pathname !== "/v1/request") {
    return json({ ok: false, error: "no such route" }, 404);
  }
  if (request.method !== "POST") {
    return json({ ok: false, error: "POST JSON only" }, 405);
  }
  return handleRequest(request, env);
}

export default { fetch: handle };
