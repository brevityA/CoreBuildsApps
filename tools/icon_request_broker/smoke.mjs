#!/usr/bin/env node
/**
 * Post-deploy smoke for the icon-request broker — safe against production:
 * everything it checks is non-mutating. Never creates a request/issue.
 *
 *   node smoke.mjs [--strict] [--require-sink] https://<name>.<subdomain>.workers.dev
 *
 * --strict requires the /healthz version to equal this checkout's
 * WORKER_VERSION (the deploy pipeline's wait-loop pass exactly like the
 * CORS proxy's does).
 *
 * --require-sink fails when the worker has no way to file a request
 * (/healthz reports sink "none"): a deploy that set its secrets must not
 * pass smoke while the worker still answers every press with a 503.
 */
import { readFileSync } from "node:fs";

const strict = process.argv.includes("--strict");
const requireSink = process.argv.includes("--require-sink");
const base = process.argv.find((a) => a.startsWith("http"));
if (!base) {
  console.error("usage: node smoke.mjs [--strict] [--require-sink] https://…workers.dev");
  process.exit(2);
}

const ownVersion = readFileSync(new URL("./constants.mjs", import.meta.url), "utf8")
  .match(/export const WORKER_VERSION = "([^"]+)"/)[1];

let failures = 0;
const check = async (name, actual, want) => {
  const ok = typeof want === "function" ? want(actual) : actual === want;
  console.log(`${ok ? "ok  " : "FAIL"} ${name}  (${JSON.stringify(actual)})`);
  if (!ok) failures += 1;
};

const health = await (await fetch(`${base}/healthz`)).json();
check("healthz ok", health.ok, true);
check("healthz version present", typeof health.version, (v) => v === "string" && v.length > 4);
if (strict) check("version matches checkout", health.version, ownVersion);
if (requireSink) check("a filing sink is configured", health.sink, (s) => typeof s === "string" && s !== "none");

const getRoot = await fetch(`${base}/`);
check("GET / is 405", getRoot.status, 405);

const bad = await fetch(`${base}/`, { method: "POST", body: "{{" });
check("invalid JSON is 400", bad.status, 400);

const nope = await fetch(`${base}/nope`);
check("unknown route is 404", nope.status, 404);

// A deliberately invalid (but rate-friendly) payload proves validation wired
// end-to-end on the live worker.
const malformed = await fetch(`${base}/`, {
  method: "POST", headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ app_name: "smoke" }),
});
check("grammar violation is 400", malformed.status, 400);

process.exit(failures ? 1 : 0);
