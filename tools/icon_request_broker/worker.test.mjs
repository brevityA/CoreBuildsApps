import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { webcrypto } from "node:crypto";
import {
  validate, issueBody, discordPayload, rateLimitKey, rateLimited, appJwt,
  readCapped, handle, TITLE_PREFIX, ISSUE_LABEL, WORKER_VERSION,
} from "./worker.mjs";

// ------------------------------------------------------------------ grammar

const OK = { app_name: "Internet Speed Test",
             component: "com.rma.speedtesttv/com.rma.speedtesttv.ui.SplashActivity",
             device: "Scanned on TCL C735, Android 12, pack 1.9.2" };

test("validate accepts the exact payload the auditor sends", () => {
  const v = validate(OK);
  assert.equal(v.ok, true);
  assert.equal(v.value.component, OK.component);
});

test("validate accepts a relative-spelling component too", () => {
  const v = validate({ ...OK, component: "com.rma.speedtesttv/.ui.SplashActivity" });
  assert.equal(v.ok, true);
});

for (const [name, bad] of [
  ["no body", null],
  ["array body", []],
  ["unknown field", { ...OK, note: "hack the body only" }],
  ["newline in name", { ...OK, app_name: "a\nb" }],
  ["overlong name", { ...OK, app_name: "x".repeat(81) }],
  ["component without slash", { ...OK, component: "com.example.tv" }],
  ["component with URL", { ...OK, component: "com.evil.tv/http://example.com" }],
  ["empty name", { ...OK, app_name: "  " }],
]) {
  test(`validate rejects ${name}`, () => {
    assert.equal(validate(bad).ok, false, JSON.stringify(bad));
  });
}

test("device note is optional and capped", () => {
  const { device, ...rest } = OK;
  assert.equal(validate(rest).ok, true);
  assert.equal(validate({ ...rest, device: "y".repeat(121) }).ok, false);
});

// ------------------------------------------------------- issue-body contract

test("issue body mirrors the issue form's text fields, in form order", () => {
  const yml = readFileSync(
    new URL("../../.github/ISSUE_TEMPLATE/1.new_icon_request.yml", import.meta.url),
    "utf8");
  const labels = [...yml.matchAll(/- type: (?:input|textarea|dropdown)[\s\S]*?label: ([^\n]+)/g)]
    .map((m) => m[1].trim());
  assert.ok(labels.length >= 4, `expected the form's fields, got ${labels}`);
  const body = issueBody({ appName: OK.app_name, component: OK.component, device: OK.device });
  let cursor = -1;
  for (const label of labels) {
    const at = body.indexOf(`### ${label}`);
    assert.ok(at > cursor, `form label missing or out of order: ${label}`);
    cursor = at;
  }
  assert.ok(body.includes("CoreBuilds-requests[bot]"), "anonymous provenance line");
});

test("the Discord card carries the same three fields", () => {
  const p = discordPayload({ appName: OK.app_name, component: OK.component, device: OK.device });
  assert.equal(p.embeds[0].title, `${TITLE_PREFIX}${OK.app_name}`);
  assert.equal(p.embeds[0].fields[0].value, `\`${OK.component}\``);
  assert.equal(p.embeds[0].fields[1].value, OK.device);
});

// ------------------------------------------------------------ rate limiting

test("rate-limit keys rotate hourly", () => {
  assert.notEqual(rateLimitKey("1.2.3.4", 0), rateLimitKey("1.2.3.4", 3_600_000));
  assert.equal(rateLimitKey("1.2.3.4", 100), rateLimitKey("1.2.3.4", 1000));
});

function mapKv(init = {}) {
  const store = new Map(Object.entries(init));
  return {
    store,
    get: async (k) => (store.has(k) ? store.get(k) : null),
    put: async (k, v) => void store.set(k, v),
  };
}

test("KV bucket trips after the hourly cap", async () => {
  const env = { RATE_KV: mapKv(), RATE_LIMIT_PER_HOUR: 3 };
  const ip = "9.9.9.1";
  assert.equal(await rateLimited(env, ip), false);
  assert.equal(await rateLimited(env, ip), false);
  assert.equal(await rateLimited(env, ip), false);
  assert.equal(await rateLimited(env, ip), true);
});

test("a KV write collision inside the window counts AGAINST the caller", async () => {
  let writes = 0;
  const flaky = { get: async () => "0", put: async () => { writes++; throw new Error("slow"); } };
  assert.equal(await rateLimited({ RATE_KV: flaky }, "9.9.9.2"), true);
  assert.equal(writes, 1);
});

test("the RL binding outranks KV when present", async () => {
  const kv = mapKv();
  const env = { RL_ICON_REQUEST: { limit: async () => ({ success: false }) }, RATE_KV: kv };
  assert.equal(await rateLimited(env, "9.9.9.3"), true);
  assert.equal(kv.store.size, 0, "KV must not be consulted under the binding");
});

test("with zero bindings the in-isolate floor applies", async () => {
  const ip = "9.9.9.4";
  for (let i = 0; i < 25; i++) assert.equal(await rateLimited({}, ip), false);
  assert.equal(await rateLimited({}, ip), true);
  assert.equal(await rateLimited({}, ""), false, "unknown clients are not bucketed");
});

// ------------------------------------------------------------------- reader

test("readCapped accepts small bodies and rejects streams above the cap", async () => {
  const small = new Request("https://x/", { method: "POST", body: JSON.stringify(OK) });
  assert.ok((await readCapped(small)).includes("app_name"));
  await assert.rejects(
    readCapped(new Request("https://x/", { method: "POST",
      body: "x".repeat(3000) }), 2048),
    (err) => err.status === 413);
});

// -------------------------------------------------------------- GitHub auth

test("JWT is a verifiable RS256 token with the app's id as issuer", async () => {
  const { subtle } = webcrypto;
  const pair = await subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true, ["sign", "verify"]);
  const pkcs8 = Buffer.from(await subtle.exportKey("pkcs8", pair.privateKey));
  const b64 = pkcs8.toString("base64");
  const env = { GITHUB_APP_ID: "123456", GITHUB_APP_PRIVATE_KEY:
    `-----BEGIN PRIVATE KEY-----\n${b64.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----` };
  const jwt = await appJwt(env);
  const [h, p, s] = jwt.split(".");
  assert.equal(JSON.parse(Buffer.from(h, "base64url")).alg, "RS256");
  const claims = JSON.parse(Buffer.from(p, "base64url"));
  assert.equal(claims.iss, "123456");
  assert.equal(claims.exp - claims.iat, 600);
  const ok = await subtle.verify("RSASSA-PKCS1-v1_5", pair.publicKey,
    Buffer.from(s, "base64url"), Buffer.from(`${h}.${p}`));
  assert.equal(ok, true, "signature must verify against the same key");
});

// ------------------------------------------------------------- handler flow

function req(payload, ip = "1.2.3.4") {
  return new Request("https://broker.test/", {
    method: "POST", headers: { "CF-Connecting-IP": ip },
    body: JSON.stringify(payload),
  });
}
const primed = () => mapKv({ "gh:installation_token": "tok" });
const ENV = { GITHUB_APP_ID: "1", GITHUB_APP_INSTALLATION_ID: "2",
              GITHUB_APP_PRIVATE_KEY: "x", RATE_KV: primed() };

function githubStub({ duplicate = null } = {}) {
  const calls = [];
  const f = async (url, init = {}) => {
    calls.push([String(url), init.method || "GET"]);
    const mk = (status, body) => new Response(JSON.stringify(body), { status });
    if (url.includes("/access_tokens")) return mk(201, { token: "tok" });
    if (url.startsWith("https://api.github.com/search/issues")) {
      return mk(200, { items: duplicate ? [{ state: "open", number: duplicate,
        title: `${TITLE_PREFIX}Internet Speed Test` }] : [] });
    }
    if (url.includes("/labels/")) return mk(200, { name: ISSUE_LABEL });
    if (url.endsWith("/labels")) return mk(201, { name: ISSUE_LABEL });
    if (url.endsWith("/issues")) return mk(201, { number: 777 });
    if (url.includes("/comments")) return mk(201, {});
    return mk(500, {});
  };
  return { f, calls };
}

async function withFetch(stubF, fn) {
  const realFetch = globalThis.fetch;
  globalThis.fetch = stubF;
  try { return await fn(); }
  finally { globalThis.fetch = realFetch; }
}

test("healthz answers the version with no I/O; unknown routes 404", async () => {
  const res = await handle(new Request("https://x/healthz"), {});
  assert.equal(res.status, 200);
  assert.equal((await res.json()).version, WORKER_VERSION);
  assert.equal((await handle(new Request("https://x/nope"), {})).status, 404);
});

test("405 for non-POST, 400 for bad JSON and bad payloads, 413 for big bodies", async () => {
  assert.equal((await handle(new Request("https://x/", { method: "GET" }), ENV)).status, 405);
  assert.equal((await handle(new Request("https://x/", { method: "POST", body: "{{" }), ENV)).status, 400);
  assert.equal((await handle(req({ app_name: "x" }), ENV)).status, 400);
  const big = new Request("https://x/", { method: "POST", body: "x".repeat(5000) });
  assert.equal((await handle(big, ...argsFor(ENV))).status, 413);
});

function argsFor(env) { return [env]; }

test("files a new issue when no duplicate exists", async () => {
  const stub = githubStub();
  await withFetch(stub.f, async () => {
    const res = await handle(req(OK, "7.7.7.1"), ENV);
    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.issue, 777);
    assert.equal(body.duplicate, false);
    assert.ok(stub.calls.some(([u, m]) => u.endsWith("/issues") && m === "POST"));
  });
});

test("a duplicate press comments instead of opening a second issue", async () => {
  const stub = githubStub({ duplicate: 55 });
  await withFetch(stub.f, async () => {
    const res = await handle(req(OK, "7.7.7.2"), { ...ENV, RATE_KV: primed() });
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.issue, 55);
    assert.equal(body.duplicate, true);
    assert.ok(stub.calls.some(([u]) => u.includes("/issues/55/comments")));
    assert.ok(!stub.calls.some(([u, m]) => u.endsWith("/issues") && m === "POST"));
  });
});

test("rate-limited triple-press answers 429 without touching GitHub", async () => {
  const kv = primed();
  const env = { ...ENV, RATE_KV: kv, RATE_LIMIT_PER_HOUR: 1 };
  const stub = githubStub();
  await withFetch(stub.f, async () => {
    assert.equal((await handle(req(OK, "7.7.7.3"), env)).status, 201);
    assert.equal((await handle(req(OK, "7.7.7.3"), env)).status, 429);
  });
});

test("without GitHub vars a Discord webhook receives the same request", async () => {
  const calls = [];
  const stubF = async (url, init = {}) => {
    calls.push([String(url), init.method || "GET"]);
    // A 204 must be bodyless — construct it as such (the worker only reads .ok).
    return new Response(url.includes("discord") ? null : "{}", { status: 204 });
  };
  const env = { RATE_KV: mapKv(), ICON_REQUEST_DISCORD_WEBHOOK_URL: "https://discord.com/api/webhooks/t/x" };
  await withFetch(stubF, async () => {
    const res = await handle(req(OK, "7.7.7.4"), env);
    assert.equal(res.status, 201);
    assert.equal((await res.json()).via, "discord");
    assert.equal(calls.length, 1);
    assert.ok(calls[0][0].includes("discord.com/api/webhooks"));
  });
});

test("with neither sink configured the broker says 503", async () => {
  const res = await handle(req(OK, "7.7.7.5"), { RATE_KV: mapKv() });
  assert.equal(res.status, 503);
});
