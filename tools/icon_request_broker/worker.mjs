/**
 * Core Builds icon-request broker — a Cloudflare Worker.
 *
 * What it is: the one trusted hop that lets the icon pack's on-device
 * auditor file icon requests (and mapping reports) for people who do not have (and should never
 * need) a GitHub account. The auditor POSTs three validated fields; this
 * worker files (or +1s) a GitHub issue — as the GitHub App's bot, or as the
 * owner through a fine-grained GITHUB_TOKEN limited to this repo's issues —
 * or, when neither is configured, forwards the same payload to a Discord webhook,
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
 *
 * Core Builds Plus: the Google Play build sends `plus_token`, a Play purchase
 * token, when its owner bought Plus. With a service account configured
 * (GOOGLE_SERVICE_ACCOUNT_JSON), the broker asks the Play Developer API
 * whether that token is a purchase of PLUS_PRODUCT_ID that is neither
 * cancelled nor refunded, and labels the issue PRIORITY_LABEL if so. The
 * token is never logged, filed or stored: only a SHA-256 of it keys a short
 * KV cache of the verdict. A failed check files the request without priority;
 * it never blocks one.
 */

// Shared literals live in constants.mjs — see that file for why a Workers
// entry module must not export plain strings (workerd refuses to boot).
import { REPO, ISSUE_LABEL, TITLE_PREFIX, MAPPING_LABEL, MAPPING_TITLE_PREFIX,
         PRIORITY_LABEL, PLAY_PACKAGE, PLUS_PRODUCT_ID, WORKER_VERSION,
         BATCH_MAX, BATCH_PER_DAY } from "./constants.mjs";

const COMPONENT_RE = /^[A-Za-z0-9_.]+\/[A-Za-z0-9_.$]+$/;
const MAX_APP_NAME = 80;
const MAX_DEVICE_NOTE = 120;
// A Play purchase token is a few hundred URL-safe characters; the cap is slack.
const PLUS_TOKEN_RE = /^[A-Za-z0-9._-]{16,1024}$/;
const BODY_CAP = 2 * 1024;             // bytes; the audit payload is ~300, ~600 with a Plus token
// A batch is up to BATCH_MAX app entries (~150 bytes each) plus the token.
const BATCH_BODY_CAP = 8 * 1024;
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
// payload grammar — the same three fields the issue-form prefill carries, plus
// `mapped`: true when the pack already maps the app under another activity,
// which makes it a mapping report rather than a request for new art, and
// `plus_token`: the Play build's Core Builds Plus purchase token, if any

export function validate(payload) {
  if (payload === null || typeof payload !== "object" || Array.isArray(payload)) {
    return { ok: false, status: 400, error: "expected a JSON object" };
  }
  const keys = Object.keys(payload);
  const unknown = keys.filter((k) =>
    !["app_name", "component", "device", "mapped", "plus_token"].includes(k));
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
  const mapped = payload.mapped ?? false;
  if (typeof mapped !== "boolean") {
    return { ok: false, status: 400, error: "mapped must be true or false" };
  }
  const plusToken = payload.plus_token ?? null;
  if (plusToken !== null && (typeof plusToken !== "string" || !PLUS_TOKEN_RE.test(plusToken))) {
    return { ok: false, status: 400, error: "plus_token must be a Google Play purchase token" };
  }
  return { ok: true, value: { appName: appName.trim(), component, device, mapped, plusToken } };
}

/**
 * The "Request all" payload: one device note and one Plus token for up to
 * BATCH_MAX apps, each in the single request's grammar. The token is required:
 * a batch is a Core Builds Plus extra, and handleBatch refuses one Google does
 * not confirm.
 */
export function validateBatch(payload) {
  if (payload === null || typeof payload !== "object" || Array.isArray(payload)) {
    return { ok: false, status: 400, error: "expected a JSON object" };
  }
  const unknown = Object.keys(payload).filter((k) => !["apps", "device", "plus_token"].includes(k));
  if (unknown.length) return { ok: false, status: 400, error: `unknown fields: ${unknown.join(", ")}` };
  if (typeof payload.plus_token !== "string") {
    return { ok: false, status: 400, error: "plus_token is required: Request all is a Core Builds Plus extra" };
  }
  const apps = payload.apps;
  if (!Array.isArray(apps) || apps.length === 0 || apps.length > BATCH_MAX) {
    return { ok: false, status: 400, error: `apps must list 1 to ${BATCH_MAX} apps` };
  }
  const value = { device: "", plusToken: null, apps: [] };
  const seen = new Set();
  for (const [i, app] of apps.entries()) {
    if (app === null || typeof app !== "object" || Array.isArray(app)) {
      return { ok: false, status: 400, error: `apps[${i}] must be an object` };
    }
    const extra = Object.keys(app).filter((k) => !["app_name", "component", "mapped"].includes(k));
    if (extra.length) return { ok: false, status: 400, error: `apps[${i}]: unknown fields: ${extra.join(", ")}` };
    const one = validate({ ...app, device: payload.device, plus_token: payload.plus_token });
    if (!one.ok) return { ...one, error: `apps[${i}]: ${one.error}` };
    if (seen.has(one.value.component)) {
      return { ok: false, status: 400, error: `apps[${i}]: ${one.value.component} is listed twice` };
    }
    seen.add(one.value.component);
    value.device = one.value.device;
    value.plusToken = one.value.plusToken;
    value.apps.push({ appName: one.value.appName, component: one.value.component,
                      mapped: one.value.mapped });
  }
  return { ok: true, value };
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

/** One purchase's "Request all" presses today; the day is UTC. */
export function batchKey(tokenHash, at = Date.now()) {
  return `batch:${tokenHash}:${Math.floor(at / 86_400_000)}`;
}

/**
 * True while this purchase has a Request all left today, and counts it. With
 * no KV there is no day to count across isolates, so only the per-IP limit
 * stands in front of a batch.
 */
export async function batchAllowed(env, tokenHash, at = Date.now()) {
  if (!env.RATE_KV) return true;
  const key = batchKey(tokenHash, at);
  const count = Number(await env.RATE_KV.get(key)) || 0;
  if (count >= BATCH_PER_DAY) return false;
  await env.RATE_KV.put(key, String(count + 1), { expirationTtl: 86_400 + 600 });
  return true;
}

// ---------------------------------------------------------------------------
// RS256 JWTs: the GitHub App's, and the Google service account's below

function b64url(buf) {
  const bytes = buf instanceof ArrayBuffer ? new Uint8Array(buf) : buf;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** A JWT over [claims], signed with a PKCS#8 PEM key (literal "\n"s allowed, as secrets paste them). */
async function rs256(pemKey, claims) {
  const header = b64url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const payload = b64url(new TextEncoder().encode(JSON.stringify(claims)));
  const pem = pemKey.replace(/\\n/g, "\n");
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

// GitHub App authentication: RS256 JWT -> installation token, cached in KV

export async function appJwt(env) {
  const now = Math.floor(Date.now() / 1000);
  return rs256(env.GITHUB_APP_PRIVATE_KEY,
               { iat: now - 60, exp: now + 540, iss: env.GITHUB_APP_ID });
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

// ---------------------------------------------------------------------------
// Core Builds Plus: is this a real, unrefunded purchase? Asked of Google with
// a service account that Play Console granted "View financial data"

const ANDROIDPUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const PLUS_OWNED_TTL = 6 * 3600;       // a refund shows within six hours
const PLUS_NOT_OWNED_TTL = 600;        // a pending purchase may clear soon

/** The service account key Play Console's API access page downloads, or null. */
export function serviceAccount(env) {
  if (!env.GOOGLE_SERVICE_ACCOUNT_JSON) return null;
  try {
    const sa = JSON.parse(env.GOOGLE_SERVICE_ACCOUNT_JSON);
    return sa.client_email && sa.private_key ? sa : null;
  } catch (_) {
    return null;
  }
}

export function plusConfigured(env) {
  return serviceAccount(env) !== null;
}

export async function googleAccessToken(env) {
  if (env.RATE_KV) {
    const cached = await env.RATE_KV.get("google:access_token");
    if (cached) return cached;
  }
  const sa = serviceAccount(env);
  const now = Math.floor(Date.now() / 1000);
  const assertion = await rs256(sa.private_key, {
    iss: sa.client_email, scope: ANDROIDPUBLISHER_SCOPE,
    aud: GOOGLE_TOKEN_URL, iat: now - 60, exp: now + 3540,
  });
  const res = await fetch(GOOGLE_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion,
    }).toString(),
  });
  if (!res.ok) throw new Error(`google token request failed: ${res.status}`);
  const { access_token: token, expires_in: expiresIn } = await res.json();
  if (env.RATE_KV) {
    await env.RATE_KV.put("google:access_token", token,
      { expirationTtl: Math.max(60, (Number(expiresIn) || 3600) - 300) });
  }
  return token;
}

async function sha256Hex(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** What Google's answer means: owned only if purchased and not wholly refunded. */
export function plusOwned(purchase) {
  if (!purchase || purchase.purchaseState !== 0) return false;
  if (purchase.productId && purchase.productId !== PLUS_PRODUCT_ID) return false;
  return purchase.refundableQuantity === undefined || purchase.refundableQuantity > 0;
}

/**
 * True only when Google confirms the token. Every failure is false: a request
 * filed without priority is recoverable by hand, a blocked one is lost.
 */
export async function verifyPlus(env, token) {
  if (!token || !plusConfigured(env)) return false;
  const cacheKey = `plus:${await sha256Hex(token)}`;
  if (env.RATE_KV) {
    const cached = await env.RATE_KV.get(cacheKey);
    if (cached !== null) return cached === "1";
  }
  try {
    const access = await googleAccessToken(env);
    const res = await fetch(
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PLAY_PACKAGE}` +
      `/purchases/products/${PLUS_PRODUCT_ID}/tokens/${encodeURIComponent(token)}`,
      { headers: { Authorization: `Bearer ${access}` } });
    let owned;
    if (res.ok) owned = plusOwned(await res.json());
    else if (res.status === 400 || res.status === 404 || res.status === 410) owned = false;
    else throw new Error(`play purchase check failed: ${res.status}`);
    if (env.RATE_KV) {
      await env.RATE_KV.put(cacheKey, owned ? "1" : "0",
        { expirationTtl: owned ? PLUS_OWNED_TTL : PLUS_NOT_OWNED_TTL });
    }
    logEvent({ evt: "plus_checked", owned });
    return owned;
  } catch (err) {
    // Not cached: the next request asks again.
    logEvent({ evt: "plus_error", msg: String(err).slice(0, 120) });
    return false;
  }
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

/** Title prefix and label: the form this report would have been filed with. */
export function reportKind({ mapped }) {
  return mapped
    ? { prefix: MAPPING_TITLE_PREFIX, label: MAPPING_LABEL,
        description: "App is in the pack but its icon does not auto-assign" }
    : { prefix: TITLE_PREFIX, label: ISSUE_LABEL,
        description: "App icon requests (incl. anonymous auditor reports)" };
}

/**
 * Issue body: mirrors the field order of the form the report belongs to —
 * 2.icon_not_applying.yml for a mapped app, 1.new_icon_request.yml otherwise.
 */
export function issueBody({ appName, component, device, mapped = false, priority = false }) {
  return reportBody({ appName, component, device, mapped }) + (priority
    ? "\n\n_Core Builds Plus: the purchase was checked with Google Play, so this is a priority request._"
    : "");
}

function reportBody({ appName, component, device, mapped }) {
  if (mapped) {
    return [
      "### App name", "", appName,
      "", "### The component name on YOUR device", "", component,
      "", "### Device and OS", "", device || "_not supplied_",
      "", "### Launcher", "", "_not supplied — device-side auditor report_",
      "",
      "_Filed by the Core Builds request broker from the in-app auditor's",
      "one-press request — the pack maps this app under a different activity, so this",
      "device's component is the fix. Anonymous device report; no GitHub",
      "account on the reporter side._",
    ].join("\n");
  }
  return [
    "### App name", "", appName,
    "", "### Component name", "", component,
    "", "### Store or download link", "",
    "_not supplied — device-side auditor report; a real APK or store link matters",
    "_only when the component cannot be reproduced._",
    "", "### Device type", "", device || "_not supplied_",
    "", "### Anything else", "",
    "_Filed by the Core Builds request broker from the in-app auditor's",
    "one-press request — anonymous device report; no GitHub account on the reporter side._",
  ].join("\n");
}

async function findOpenDuplicate(token, { appName, mapped }) {
  const { prefix } = reportKind({ mapped });
  const q = encodeURIComponent(`${prefix.trim()} "${appName}" in:title repo:${REPO}`);
  const res = await fetch(`https://api.github.com/search/issues?q=${q}&state=open&per_page=5`,
    { headers: ghHeaders(`Bearer ${token}`) });
  if (!res.ok) return null;
  const { items } = await res.json();
  // The search is fuzzy; the prefix check keeps a mapping report from
  // voting on a new-icon issue for the same app, and the other way round.
  const hit = (items || []).find((i) => i.state === "open" &&
    i.title.startsWith(prefix) &&
    i.title.toLowerCase().includes(appName.toLowerCase()));
  return hit ? hit.number : null;
}

async function ensureLabel(token, { label, description }) {
  const name = encodeURIComponent(label);
  const res = await fetch(`https://api.github.com/repos/${REPO}/labels/${name}`,
    { headers: ghHeaders(`Bearer ${token}`) });
  if (res.status === 404) {
    await fetch(`https://api.github.com/repos/${REPO}/labels`, {
      method: "POST", headers: ghHeaders(`Bearer ${token}`),
      body: JSON.stringify({ name: label, color: "0d8048", description }),
    });
  }
}

const PRIORITY_KIND = { label: PRIORITY_LABEL,
  description: "Requested by a Core Builds Plus owner; done first" };

async function fileOrComment(token, value, ready = null) {
  // A batch ensures each label once, not once per app: Workers cap the
  // subrequests a single request may make.
  const ensure = async (k) => {
    if (ready?.has(k.label)) return;
    await ensureLabel(token, k);
    ready?.add(k.label);
  };
  const kind = reportKind(value);
  const dup = await findOpenDuplicate(token, value);
  if (dup) {
    await fetch(`https://api.github.com/repos/${REPO}/issues/${dup}/comments`, {
      method: "POST", headers: ghHeaders(`Bearer ${token}`),
      body: JSON.stringify({ body:
        `+1 from the in-app auditor's one-press request` +
        (value.device ? ` — ${value.device}` : "") +
        ". A repeat counts as a vote, not a new issue." +
        (value.priority ? " From a Core Builds Plus owner, so it is now priority." : "") }),
    });
    if (value.priority) {
      await ensure(PRIORITY_KIND);
      await fetch(`https://api.github.com/repos/${REPO}/issues/${dup}/labels`, {
        method: "POST", headers: ghHeaders(`Bearer ${token}`),
        body: JSON.stringify({ labels: [PRIORITY_LABEL] }),
      });
    }
    return { number: dup, duplicate: true };
  }
  await ensure(kind);
  if (value.priority) await ensure(PRIORITY_KIND);
  const res = await fetch(`https://api.github.com/repos/${REPO}/issues`, {
    method: "POST", headers: ghHeaders(`Bearer ${token}`),
    body: JSON.stringify({ title: `${kind.prefix}${value.appName}`,
      body: issueBody(value),
      labels: value.priority ? [kind.label, PRIORITY_LABEL] : [kind.label] }),
  });
  if (!res.ok) throw new Error(`issue create failed: ${res.status}`);
  const { number } = await res.json();
  return { number, duplicate: false };
}

function githubAppConfigured(env) {
  return Boolean(env.GITHUB_APP_ID && env.GITHUB_APP_INSTALLATION_ID &&
                 env.GITHUB_APP_PRIVATE_KEY);
}

/**
 * Which sink files a request, strongest first: the GitHub App (issues filed
 * as its bot), then GITHUB_TOKEN (a fine-grained personal token limited to
 * Issues: write on this repo, so issues are filed as its owner), then the
 * Discord webhook. Names only; /healthz reports this, never a value.
 */
export function sinkFor(env) {
  if (githubAppConfigured(env)) return "github-app";
  if (env.GITHUB_TOKEN) return "github-token";
  if (env.ICON_REQUEST_DISCORD_WEBHOOK_URL) return "discord";
  return "none";
}

/** The Discord card, same three fields, titled like the issue it stands for. */
export function discordPayload(value) {
  return {
    username: "Core Builds Icon Auditor",
    embeds: [{
      title: `${reportKind(value).prefix}${value.appName}`,
      color: 0x32c8f0,
      fields: [
        { name: "Component name", value: "`" + value.component + "`" },
        { name: "Device", value: value.device || "_not supplied_" },
        ...(value.priority
          ? [{ name: "Priority", value: "Core Builds Plus, checked with Google Play" }]
          : []),
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

  const sink = sinkFor(env);
  if (sink === "none") {
    logEvent({ evt: "not_configured" });
    return json({ ok: false, error: "broker not configured" }, 503);
  }
  // The token goes no further than this line: only the verdict travels on.
  const { plusToken, ...fields } = verdict.value;
  const value = { ...fields, priority: await verifyPlus(env, plusToken) };
  try {
    if (sink === "github-app" || sink === "github-token") {
      const token = sink === "github-app" ? await installationToken(env) : env.GITHUB_TOKEN;
      const { number, duplicate } = await fileOrComment(token, value);
      logEvent({ evt: duplicate ? "issue_plus_one" : "issue_created", issue: number,
                 priority: value.priority });
      return json({ ok: true, issue: number, duplicate, priority: value.priority },
                  duplicate ? 200 : 201);
    }
    if (sink === "discord") {
      await postDiscord(env, value);
      logEvent({ evt: "discord_forwarded", priority: value.priority });
      return json({ ok: true, via: "discord", priority: value.priority }, 201);
    }
  } catch (err) {
    logEvent({ evt: "sink_error", msg: String(err).slice(0, 120) });
    return json({ ok: false, error: String(err) }, 502);
  }
}

/**
 * Core Builds Plus "Request all": up to BATCH_MAX apps from one TV in one
 * press, each filed (or +1ed) as a priority issue exactly as a single press
 * would file it. Refused unless Google confirms the purchase, and limited to
 * BATCH_PER_DAY presses a day per purchase on top of the per-IP limit. Each
 * app answers on its own: one failed issue does not undo the others.
 */
async function handleBatch(request, env) {
  if (await rateLimited(env, getClientIp(request))) {
    logEvent({ evt: "rate_limited", route: "batch" });
    return json({ ok: false, error: "rate limited" }, 429);
  }
  let raw;
  try { raw = await readCapped(request, BATCH_BODY_CAP); }
  catch (err) { return json({ ok: false, error: err.message }, err.status || 400); }
  let payload;
  try { payload = JSON.parse(raw); }
  catch (_) { return json({ ok: false, error: "body is not JSON" }, 400); }
  const verdict = validateBatch(payload);
  if (!verdict.ok) return json({ ok: false, error: verdict.error }, verdict.status);

  const sink = sinkFor(env);
  if (sink === "none") return json({ ok: false, error: "broker not configured" }, 503);
  if (sink === "discord") {
    return json({ ok: false, error: "Request all needs the GitHub sink" }, 501);
  }
  const { plusToken, device, apps } = verdict.value;
  if (!(await verifyPlus(env, plusToken))) {
    logEvent({ evt: "batch_refused" });
    return json({ ok: false,
                  error: "Request all is a Core Builds Plus extra; Google Play did not confirm this purchase" },
                403);
  }
  if (!(await batchAllowed(env, await sha256Hex(plusToken)))) {
    logEvent({ evt: "batch_daily_cap" });
    return json({ ok: false, error: `Request all is limited to ${BATCH_PER_DAY} presses a day` }, 429);
  }
  const token = sink === "github-app" ? await installationToken(env) : env.GITHUB_TOKEN;
  const ready = new Set();
  const results = [];
  for (const app of apps) {
    try {
      const { number, duplicate } = await fileOrComment(token, { ...app, device, priority: true }, ready);
      results.push({ component: app.component, issue: number, duplicate });
    } catch (err) {
      results.push({ component: app.component, error: String(err).slice(0, 120) });
    }
  }
  logEvent({ evt: "batch_filed", apps: apps.length,
             filed: results.filter((r) => r.issue).length });
  return json({ ok: true, priority: true, results }, 201);
}

function json(body, status) {
  return new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": "application/json" },
  });
}

// The QR interstitial runs on github.io and POSTs cross-origin, so browsers
// preflight it (OkHttp from the TV never does). There are no cookies and no
// user auth anywhere in this service, so reflecting the caller's Origin
// grants nothing a curl could not already do; it exists purely to let the
// phone's browser read its own response.
function corsHeaders(request) {
  const origin = request.headers.get("origin");
  return origin ? {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "POST",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin",
  } : {};
}

async function handleInner(request, env) {
  const url = new URL(request.url);
  if (url.pathname === "/healthz") {
    return json({ ok: true, version: WORKER_VERSION, sink: sinkFor(env),
                  plus: plusConfigured(env) }, 200);
  }
  const batch = url.pathname === "/v1/batch";
  if (!batch && url.pathname !== "/" && url.pathname !== "/v1/request") {
    return json({ ok: false, error: "no such route" }, 404);
  }
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders(request) });
  }
  if (request.method !== "POST") {
    return json({ ok: false, error: "POST JSON only" }, 405);
  }
  return batch ? handleBatch(request, env) : handleRequest(request, env);
}

export async function handle(request, env) {
  const res = await handleInner(request, env);
  for (const [key, value] of Object.entries(corsHeaders(request))) {
    res.headers.set(key, value);
  }
  return res;
}

export default { fetch: handle };
