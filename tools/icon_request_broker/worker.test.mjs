import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { webcrypto } from "node:crypto";
import {
  validate, issueBody, discordPayload, rateLimitKey, rateLimited, appJwt,
  readCapped, handle, reportKind, sinkFor, plusOwned, verifyPlus, plusConfigured,
  validateBatch, batchKey, batchAllowed,
} from "./worker.mjs";
import { TITLE_PREFIX, ISSUE_LABEL, MAPPING_LABEL, MAPPING_TITLE_PREFIX,
         PRIORITY_LABEL, PLAY_PACKAGE, PLUS_PRODUCT_ID, WORKER_VERSION,
         BATCH_MAX, BATCH_PER_DAY } from "./constants.mjs";

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
  ["non-boolean mapped", { ...OK, mapped: "yes" }],
]) {
  test(`validate rejects ${name}`, () => {
    assert.equal(validate(bad).ok, false, JSON.stringify(bad));
  });
}

test("mapped is optional, boolean, and defaults to a new-icon request", () => {
  assert.equal(validate(OK).value.mapped, false);
  assert.equal(validate({ ...OK, mapped: true }).value.mapped, true);
  assert.equal(validate({ ...OK, mapped: false }).value.mapped, false);
});

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
  assert.ok(body.includes("Core Builds request broker"), "anonymous provenance line");
});

test("a mapped report mirrors the not-applying form's text fields, in form order", () => {
  const yml = readFileSync(
    new URL("../../.github/ISSUE_TEMPLATE/2.icon_not_applying.yml", import.meta.url),
    "utf8");
  const labels = [...yml.matchAll(/- type: (?:input|textarea|dropdown)[\s\S]*?label: ([^\n]+)/g)]
    .map((m) => m[1].trim());
  assert.ok(labels.length >= 4, `expected the form's fields, got ${labels}`);
  const body = issueBody({ appName: OK.app_name, component: OK.component,
                           device: OK.device, mapped: true });
  let cursor = -1;
  for (const label of labels) {
    const at = body.indexOf(`### ${label}`);
    assert.ok(at > cursor, `form label missing or out of order: ${label}`);
    cursor = at;
  }
  assert.ok(!body.includes("### Store or download link"), "no new-icon fields");
});

test("report kind matches each issue form's title and label", () => {
  for (const [file, mapped] of [["1.new_icon_request.yml", false],
                                ["2.icon_not_applying.yml", true]]) {
    const yml = readFileSync(
      new URL(`../../.github/ISSUE_TEMPLATE/${file}`, import.meta.url), "utf8");
    const kind = reportKind({ mapped });
    assert.equal(yml.match(/^title: "([^"]*)"/m)[1], kind.prefix, file);
    assert.equal(yml.match(/^labels: \["([^"]+)"\]/m)[1], kind.label, file);
  }
  assert.equal(reportKind({ mapped: true }).prefix, MAPPING_TITLE_PREFIX);
  assert.equal(reportKind({ mapped: true }).label, MAPPING_LABEL);
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
  const health = await res.json();
  assert.equal(health.version, WORKER_VERSION);
  assert.equal(health.sink, "none");
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

test("a mapped report files a not-applying issue, not a new-icon request", async () => {
  const bodies = [];
  // The stub's open duplicate is a new-icon issue for the same app.
  const stub = githubStub({ duplicate: 55 });
  const f = async (url, init = {}) => {
    if (init.body) bodies.push([String(url), JSON.parse(init.body)]);
    return stub.f(url, init);
  };
  await withFetch(f, async () => {
    const res = await handle(req({ ...OK, mapped: true }, "7.7.7.9"),
                             { ...ENV, RATE_KV: primed() });
    assert.equal(res.status, 201);
    const search = stub.calls.find(([u]) => u.includes("/search/issues"))[0];
    assert.ok(decodeURIComponent(search).includes(MAPPING_TITLE_PREFIX.trim()));
    assert.ok(!stub.calls.some(([u]) => u.includes("/comments")),
              "a new-icon issue for the same app is not this report's duplicate");
    const [, issue] = bodies.find(([u]) => u.endsWith("/issues"));
    assert.equal(issue.title, `${MAPPING_TITLE_PREFIX}${OK.app_name}`);
    assert.deepEqual(issue.labels, [MAPPING_LABEL]);
    assert.ok(issue.body.includes("### The component name on YOUR device"));
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

test("a mapped Discord card is titled as a mapping report", () => {
  const p = discordPayload({ appName: OK.app_name, component: OK.component,
                             device: OK.device, mapped: true });
  assert.equal(p.embeds[0].title, `${MAPPING_TITLE_PREFIX}${OK.app_name}`);
});

test("sink precedence: GitHub App, then personal token, then Discord", () => {
  const app = { GITHUB_APP_ID: "1", GITHUB_APP_INSTALLATION_ID: "2", GITHUB_APP_PRIVATE_KEY: "k" };
  const token = { GITHUB_TOKEN: "github_pat_x" };
  const hook = { ICON_REQUEST_DISCORD_WEBHOOK_URL: "https://discord.com/api/webhooks/t/x" };
  assert.equal(sinkFor({ ...app, ...token, ...hook }), "github-app");
  assert.equal(sinkFor({ ...token, ...hook }), "github-token");
  assert.equal(sinkFor({ GITHUB_APP_ID: "1", ...hook }), "discord", "a partial App trio is not a sink");
  assert.equal(sinkFor(hook), "discord");
  assert.equal(sinkFor({}), "none");
});

test("healthz names the sink and never echoes a secret", async () => {
  const env = { GITHUB_TOKEN: "github_pat_SECRET" };
  const res = await handle(new Request("https://x/healthz"), env);
  const text = await res.text();
  assert.equal(JSON.parse(text).sink, "github-token");
  assert.ok(!text.includes("SECRET"));
});

test("a personal token files the issue directly, with no App token exchange", async () => {
  const auths = [];
  const stub = githubStub();
  const f = async (url, init = {}) => {
    auths.push(init.headers?.Authorization);
    return stub.f(url, init);
  };
  await withFetch(f, async () => {
    const res = await handle(req(OK, "7.7.7.8"),
                             { GITHUB_TOKEN: "github_pat_x", RATE_KV: mapKv() });
    assert.equal(res.status, 201);
    assert.equal((await res.json()).issue, 777);
    assert.ok(!stub.calls.some(([u]) => u.includes("/access_tokens")));
    assert.ok(auths.every((a) => a === "Bearer github_pat_x"));
    assert.ok(stub.calls.some(([u, m]) => u.endsWith("/issues") && m === "POST"));
  });
});

test("with neither sink configured the broker says 503", async () => {
  const res = await handle(req(OK, "7.7.7.5"), { RATE_KV: mapKv() });
  assert.equal(res.status, 503);
});

// --------------------------------------------------------------- entry exports
// workerd refuses to boot a deployment whose entry module exports non-function,
// non-ExportedHandler values at the top level ("Incorrect type for map entry
// '<name>'": caught first by rehearse-local.mjs, then hard-coded into this
// mirror of that constraint so a future `export const` can never regress into
// the shipped worker unnoticed. Shared strings belong in constants.mjs.

// -------------------------------------------------------------------- cors
// The QR interstitial (docs/icon-request/) POSTs from *.github.io, which a
// browser insists on preflighting; OkHttp from the TV never sends Origin and
// must see a completely unchanged response.

test("browser preflight is answered with the caller's origin reflected", async () => {
  const req = new Request("https://broker.example/v1/request", {
    method: "OPTIONS",
    headers: { Origin: "https://brevitya.github.io" },
  });
  const res = await handle(req, {});
  assert.equal(res.status, 204);
  assert.equal(res.headers.get("Access-Control-Allow-Origin"),
    "https://brevitya.github.io");
  assert.equal(res.headers.get("Access-Control-Allow-Methods"), "POST");
});

test("a browser POST reads ok with CORS; an OkHttp hit is header-identical", async () => {
  const withOrigin = new Request("https://broker.example/", {
    method: "POST",
    headers: { "Content-Type": "application/json",
               Origin: "https://brevitya.github.io" },
    body: JSON.stringify({ app_name: "x", component: "bad" }),
  });
  const corsRes = await handle(withOrigin, {});
  assert.equal(corsRes.headers.get("Access-Control-Allow-Origin"),
    "https://brevitya.github.io");

  const plain = await handle(req({ app_name: "x", component: "bad" }, "7.7.7.6"), {});
  assert.equal(plain.headers.get("Access-Control-Allow-Origin"), null);
  assert.equal(plain.headers.get("Vary"), null);
});

// ---------------------------------------------------------------- Plus
// The Play build's Core Builds Plus token: checked with Google, never passed on.

const PLUS_TOKEN = "opaque-token-abcdefghijklmnop.AO-J1Oz_example";

test("the Play ids the broker checks are the ones the app ships", () => {
  const gradle = readFileSync(new URL("../../app/build.gradle.kts", import.meta.url), "utf8");
  const appId = gradle.match(/applicationId = "([^"]+)"/)[1];
  assert.equal(PLAY_PACKAGE, `${appId}.play`);
  const store = readFileSync(new URL(
    "../../app/src/play/java/tv/corebuilds/iconpack/PlusStore.kt", import.meta.url), "utf8");
  assert.equal(store.match(/PRODUCT_ID = "([^"]+)"/)[1], PLUS_PRODUCT_ID);
});

test("validate passes a well-formed plus_token through and rejects anything else", () => {
  assert.equal(validate({ ...OK, plus_token: PLUS_TOKEN }).value.plusToken, PLUS_TOKEN);
  assert.equal(validate(OK).value.plusToken, null);
  for (const bad of [42, "", "short", "has space in it here!", "x".repeat(1025), "a/b".repeat(10)]) {
    assert.equal(validate({ ...OK, plus_token: bad }).ok, false, JSON.stringify(bad));
  }
});

test("Plus is owned only while purchased and not wholly refunded", () => {
  assert.equal(plusOwned({ purchaseState: 0 }), true);
  assert.equal(plusOwned({ purchaseState: 0, refundableQuantity: 1, productId: PLUS_PRODUCT_ID }), true);
  assert.equal(plusOwned({ purchaseState: 0, refundableQuantity: 0 }), false, "refunded");
  assert.equal(plusOwned({ purchaseState: 1 }), false, "cancelled");
  assert.equal(plusOwned({ purchaseState: 2 }), false, "pending");
  assert.equal(plusOwned({ purchaseState: 0, productId: "something_else" }), false);
  assert.equal(plusOwned(null), false);
});

async function serviceAccountJson() {
  const { subtle } = webcrypto;
  const pair = await subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true, ["sign", "verify"]);
  const b64 = Buffer.from(await subtle.exportKey("pkcs8", pair.privateKey)).toString("base64");
  return JSON.stringify({
    type: "service_account", client_email: "broker@core-builds.iam.gserviceaccount.com",
    private_key: `-----BEGIN PRIVATE KEY-----\n${b64.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----\n`,
  });
}
const SA = await serviceAccountJson();

/** GitHub as githubStub, plus Google's token endpoint and the Play purchase check. */
function playStub({ purchase = { purchaseState: 0, refundableQuantity: 1 }, status = 200,
                    duplicate = null } = {}) {
  const gh = githubStub({ duplicate });
  const sent = [];
  const f = async (url, init = {}) => {
    url = String(url);
    sent.push([url, init.body ? String(init.body) : ""]);
    if (url === "https://oauth2.googleapis.com/token") {
      const form = new URLSearchParams(String(init.body));
      assert.equal(form.get("grant_type"), "urn:ietf:params:oauth:grant-type:jwt-bearer");
      const claims = JSON.parse(Buffer.from(form.get("assertion").split(".")[1], "base64url"));
      assert.equal(claims.scope, "https://www.googleapis.com/auth/androidpublisher");
      return new Response(JSON.stringify({ access_token: "ya29.x", expires_in: 3599 }), { status: 200 });
    }
    if (url.startsWith("https://androidpublisher.googleapis.com/")) {
      return new Response(JSON.stringify(purchase), { status });
    }
    return gh.f(url, init);
  };
  return { f, sent, calls: gh.calls };
}

test("without a service account a token changes nothing and Google is never asked", async () => {
  assert.equal(plusConfigured({}), false);
  assert.equal(plusConfigured({ GOOGLE_SERVICE_ACCOUNT_JSON: "{not json" }), false);
  const stub = playStub();
  await withFetch(stub.f, async () => {
    assert.equal(await verifyPlus({ RATE_KV: mapKv() }, PLUS_TOKEN), false);
  });
  assert.equal(stub.sent.length, 0);
});

test("a confirmed purchase is cached under a hash of the token, never the token", async () => {
  const kv = mapKv();
  const env = { GOOGLE_SERVICE_ACCOUNT_JSON: SA, RATE_KV: kv };
  const stub = playStub();
  await withFetch(stub.f, async () => {
    assert.equal(await verifyPlus(env, PLUS_TOKEN), true);
    assert.equal(await verifyPlus(env, PLUS_TOKEN), true);
  });
  const checks = stub.sent.filter(([u]) => u.includes("androidpublisher.googleapis.com"));
  assert.equal(checks.length, 1, "the second press is answered from the cache");
  assert.ok(checks[0][0].includes(
    `/applications/${PLAY_PACKAGE}/purchases/products/${PLUS_PRODUCT_ID}/tokens/`));
  for (const [k, v] of kv.store) {
    assert.ok(!k.includes(PLUS_TOKEN) && !String(v).includes(PLUS_TOKEN), `KV holds the token: ${k}`);
  }
  assert.ok([...kv.store.keys()].some((k) => /^plus:[0-9a-f]{64}$/.test(k)));
});

test("Google refusing the token is a no; Google failing is a no that is not cached", async () => {
  for (const [status, cached] of [[404, true], [410, true], [503, false]]) {
    const kv = mapKv();
    const stub = playStub({ status, purchase: {} });
    await withFetch(stub.f, async () => {
      assert.equal(await verifyPlus({ GOOGLE_SERVICE_ACCOUNT_JSON: SA, RATE_KV: kv }, PLUS_TOKEN), false);
    });
    assert.equal([...kv.store.keys()].some((k) => k.startsWith("plus:")), cached, String(status));
  }
});

test("a Plus request files with the priority label, and the token reaches no one but Google", async () => {
  const stub = playStub();
  const env = { ...ENV, RATE_KV: primed(), GOOGLE_SERVICE_ACCOUNT_JSON: SA };
  await withFetch(stub.f, async () => {
    const res = await handle(req({ ...OK, plus_token: PLUS_TOKEN }, "7.7.8.1"), env);
    assert.equal(res.status, 201);
    assert.equal((await res.json()).priority, true);
  });
  const [, created] = stub.sent.find(([u, b]) => u.endsWith("/issues") && b);
  const issue = JSON.parse(created);
  assert.deepEqual(issue.labels, [ISSUE_LABEL, PRIORITY_LABEL]);
  assert.ok(issue.body.includes("Core Premium"));
  for (const [url, body] of stub.sent) {
    if (url.includes("androidpublisher.googleapis.com")) continue;
    assert.ok(!url.includes(PLUS_TOKEN) && !body.includes(PLUS_TOKEN), `token sent to ${url}`);
  }
});

test("a Plus +1 on an open issue adds the priority label to it", async () => {
  const stub = playStub({ duplicate: 55 });
  const env = { ...ENV, RATE_KV: primed(), GOOGLE_SERVICE_ACCOUNT_JSON: SA };
  await withFetch(stub.f, async () => {
    const res = await handle(req({ ...OK, plus_token: PLUS_TOKEN }, "7.7.8.2"), env);
    assert.equal(res.status, 200);
  });
  const labelled = stub.sent.find(([u]) => u.endsWith("/issues/55/labels"));
  assert.ok(labelled, "priority label added to the duplicate");
  assert.deepEqual(JSON.parse(labelled[1]).labels, [PRIORITY_LABEL]);
});

test("an unconfirmed token still files the request, without priority", async () => {
  const stub = playStub({ purchase: { purchaseState: 0, refundableQuantity: 0 } });
  const env = { ...ENV, RATE_KV: primed(), GOOGLE_SERVICE_ACCOUNT_JSON: SA };
  await withFetch(stub.f, async () => {
    const res = await handle(req({ ...OK, plus_token: PLUS_TOKEN }, "7.7.8.3"), env);
    assert.equal(res.status, 201);
    assert.equal((await res.json()).priority, false);
  });
  const [, created] = stub.sent.find(([u, b]) => u.endsWith("/issues") && b);
  assert.deepEqual(JSON.parse(created).labels, [ISSUE_LABEL]);
});

test("the Discord card says priority only for a confirmed Plus request", () => {
  const base = { appName: OK.app_name, component: OK.component, device: OK.device };
  assert.equal(discordPayload(base).embeds[0].fields.length, 2);
  assert.equal(discordPayload({ ...base, priority: true }).embeds[0].fields[2].name, "Priority");
});

test("healthz says whether Plus is checked, never with what", async () => {
  const res = await handle(new Request("https://x/healthz"), { GOOGLE_SERVICE_ACCOUNT_JSON: SA });
  const text = await res.text();
  assert.equal(JSON.parse(text).plus, true);
  assert.ok(!text.includes("PRIVATE KEY") && !text.includes("iam.gserviceaccount.com"));
  assert.equal((await (await handle(new Request("https://x/healthz"), {})).json()).plus, false);
});

// ----------------------------------------------------- Request all (batch)

const APP2 = { app_name: "Second App", component: "com.example.second/.Main" };
const BATCH = { device: OK.device, plus_token: PLUS_TOKEN,
                apps: [{ app_name: OK.app_name, component: OK.component }, APP2] };

function batchReq(payload, ip = "6.6.6.1") {
  return new Request("https://broker.test/v1/batch", {
    method: "POST", headers: { "CF-Connecting-IP": ip },
    body: JSON.stringify(payload),
  });
}

test("validateBatch accepts up to BATCH_MAX apps under one device note and token", () => {
  const v = validateBatch(BATCH);
  assert.equal(v.ok, true);
  assert.equal(v.value.plusToken, PLUS_TOKEN);
  assert.equal(v.value.device, OK.device);
  assert.deepEqual(v.value.apps.map((a) => a.component), [OK.component, APP2.component]);
  assert.equal(v.value.apps[1].mapped, false);
  const full = Array.from({ length: BATCH_MAX }, (_, i) =>
    ({ app_name: `App ${i}`, component: `com.example.a${i}/.Main` }));
  assert.equal(validateBatch({ ...BATCH, apps: full }).ok, true);
});

test("validateBatch refuses a batch without Plus, too long, empty, repeated or malformed", () => {
  const { plus_token, ...noToken } = BATCH;
  const tooMany = Array.from({ length: BATCH_MAX + 1 }, (_, i) =>
    ({ app_name: `App ${i}`, component: `com.example.a${i}/.Main` }));
  for (const [why, bad] of [
    ["no token", noToken],
    ["too many", { ...BATCH, apps: tooMany }],
    ["empty", { ...BATCH, apps: [] }],
    ["not a list", { ...BATCH, apps: "x" }],
    ["twice", { ...BATCH, apps: [APP2, APP2] }],
    ["bad component", { ...BATCH, apps: [{ app_name: "x", component: "nope" }] }],
    ["extra app field", { ...BATCH, apps: [{ ...APP2, device: "x" }] }],
    ["extra field", { ...BATCH, priority: true }],
    ["bad token", { ...BATCH, plus_token: "short" }],
  ]) {
    assert.equal(validateBatch(bad).ok, false, why);
  }
});

test("Request all files every app as priority, once per label, and the token reaches only Google", async () => {
  const stub = playStub();
  const env = { ...ENV, RATE_KV: primed(), GOOGLE_SERVICE_ACCOUNT_JSON: SA };
  let body;
  await withFetch(stub.f, async () => {
    const res = await handle(batchReq(BATCH), env);
    assert.equal(res.status, 201);
    body = await res.json();
  });
  assert.equal(body.priority, true);
  assert.deepEqual(body.results.map((r) => r.component), [OK.component, APP2.component]);
  assert.ok(body.results.every((r) => r.issue === 777 && r.duplicate === false));
  const created = stub.sent.filter(([u, b]) => u.endsWith("/issues") && b).map(([, b]) => JSON.parse(b));
  assert.equal(created.length, 2);
  for (const issue of created) assert.deepEqual(issue.labels, [ISSUE_LABEL, PRIORITY_LABEL]);
  const labelChecks = stub.calls.filter(([u, m]) => u.includes("/labels/") && m === "GET");
  assert.equal(labelChecks.length, 2, "each label is ensured once per batch, not once per app");
  for (const [url, b] of stub.sent) {
    if (url.includes("androidpublisher.googleapis.com")) continue;
    assert.ok(!url.includes(PLUS_TOKEN) && !b.includes(PLUS_TOKEN), `token sent to ${url}`);
  }
});

test("Request all without a confirmed purchase is refused before GitHub is touched", async () => {
  const stub = playStub({ purchase: { purchaseState: 0, refundableQuantity: 0 } });
  const env = { ...ENV, RATE_KV: primed(), GOOGLE_SERVICE_ACCOUNT_JSON: SA };
  await withFetch(stub.f, async () => {
    assert.equal((await handle(batchReq(BATCH, "6.6.6.2"), env)).status, 403);
  });
  assert.equal(stub.calls.filter(([u]) => u.includes("api.github.com")).length, 0);
});

test("Request all is limited to BATCH_PER_DAY presses a day per purchase", async () => {
  const kv = mapKv({ "gh:installation_token": "tok" });
  const env = { ...ENV, RATE_KV: kv, GOOGLE_SERVICE_ACCOUNT_JSON: SA, RATE_LIMIT_PER_HOUR: 99 };
  const stub = playStub();
  const statuses = [];
  await withFetch(stub.f, async () => {
    for (let i = 0; i <= BATCH_PER_DAY; i++) {
      statuses.push((await handle(batchReq(BATCH, "6.6.6.3"), env)).status);
    }
  });
  assert.deepEqual(statuses, [...Array(BATCH_PER_DAY).fill(201), 429]);
  assert.ok([...kv.store.keys()].some((k) => /^batch:[0-9a-f]{64}:\d+$/.test(k)),
            "the daily count is kept under a hash of the token");
  assert.ok(![...kv.store.keys()].some((k) => k.includes(PLUS_TOKEN)));
});

test("the daily batch key rotates with the UTC day; with no KV only the IP limit applies", async () => {
  const day = 86_400_000;
  assert.notEqual(batchKey("h", 5 * day + 1), batchKey("h", 6 * day + 1));
  assert.equal(batchKey("h", 5 * day + 1), batchKey("h", 6 * day - 1));
  assert.equal(await batchAllowed({}, "h"), true);
});

test("one failed issue does not undo the rest of the batch", async () => {
  const stub = playStub();
  let creates = 0;
  const f = async (url, init = {}) => {
    if (String(url).endsWith("/issues") && init.method === "POST" && ++creates === 1) {
      return new Response("{}", { status: 502 });
    }
    return stub.f(url, init);
  };
  const env = { ...ENV, RATE_KV: primed(), GOOGLE_SERVICE_ACCOUNT_JSON: SA };
  let body;
  await withFetch(f, async () => { body = await (await handle(batchReq(BATCH, "6.6.6.4"), env)).json(); });
  assert.ok(body.results[0].error.includes("502"));
  assert.equal(body.results[1].issue, 777);
});

test("Request all needs the GitHub sink, and its body cap is the batch's", async () => {
  const discord = { ICON_REQUEST_DISCORD_WEBHOOK_URL: "https://discord.test/hook", RATE_KV: mapKv() };
  assert.equal((await handle(batchReq(BATCH, "6.6.6.5"), discord)).status, 501);
  const big = new Request("https://x/v1/batch", { method: "POST", body: "x".repeat(9000) });
  assert.equal((await handle(big, ENV)).status, 413);
  assert.equal((await handle(new Request("https://x/v1/batch"), ENV)).status, 405);
});

test("entry-module exports are all functions or ExportedHandlers (workerd rule)", async () => {
  const m = await import("./worker.mjs");
  for (const [name, value] of Object.entries(m)) {
    const legal = typeof value === "function" ||
      (value !== null && typeof value === "object" && "fetch" in value);
    assert.ok(legal, `export ${name} is not legal on a Workers entry module`);
  }
  assert.equal(typeof m.default?.fetch, "function");
});
