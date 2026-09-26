#!/usr/bin/env node
/**
 * Zero-cloud rehearsal for the icon-request broker:
 *
 *   node rehearse-local.mjs
 *
 * Boots the worker in the REAL Workers runtime (`wrangler dev`, local mode —
 * no Cloudflare account, no credentials), stands up a throwaway receiver in
 * place of the Discord webhook, and drives the exact request sequence the
 * staging rehearsal would: health → routing → validation → a card landing in
 * the receiver → the KV hourly bucket clamping the third press. Requires
 * only Node 18+ and network access to fetch the wrangler package.
 *
 * Points this proves that unit tests cannot: the worker parses as deployed,
 * the rate-limit KV binding is wired the way wrangler.toml promises, and a
 * subrequest carrying the Discord card actually leaves the isolate.
 *
 * It creates no requests, files no issues, and touches nothing in prod.
 */

import http from "node:http";
import net from "node:net";
import { spawn } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { WORKER_VERSION } from "./constants.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const TEST_IP = "203.0.113.7";          // TEST-NET-3: the request is synthetic on its face

let failures = 0;
function check(name, ok, detail = "") {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${ok ? "" : `  — ${detail}`}`);
  if (!ok) failures += 1;
}

const freePort = () => new Promise((resolve, reject) => {
  const s = net.createServer().once("error", reject);
  s.listen(0, "127.0.0.1", () => { const p = s.address().port; s.close(() => resolve(p)); });
});

// 1. throwaway Discord receiver: records every POST, answers 204 like a real webhook.
const received = [];
const mock = http.createServer((req, res) => {
  const chunks = [];
  req.on("data", (c) => chunks.push(c));
  req.on("end", () => {
    if (req.method === "POST") {
      let body = null;
      try { body = JSON.parse(Buffer.concat(chunks).toString("utf8")); } catch (_) { /* record raw */ }
      received.push({ path: req.url, body });
    }
    res.writeHead(204).end();
  });
});
await new Promise((r) => mock.listen(0, "127.0.0.1", r));
const webhookUrl = `http://127.0.0.1:${mock.address().port}/hooks/iconreq`;

// 2. a wrangler config that lives only for the rehearsal: real binding names
//    (RATE_KV), rehearsal-only values. Nothing here is committed.
const dir = mkdtempSync(path.join(tmpdir(), "iconreq-rehearse-"));
const cfg = path.join(dir, "wrangler.toml");
writeFileSync(cfg, [
  'name = "iconreq-rehearse-local"',
  `main = "${path.join(here, "worker.mjs")}"`,
  'compatibility_date = "2026-09-01"',
  'kv_namespaces = [ { binding = "RATE_KV", id = "rehearsal-local-kv" } ]',
  "",
  "[vars]",
  `ICON_REQUEST_DISCORD_WEBHOOK_URL = "${webhookUrl}"`,
  'RATE_LIMIT_PER_HOUR = "2"',
].join("\n"));

// 3. boot the real runtime and wait for readiness.
const port = await freePort();
const wrangler = spawn("npx", ["--yes", "wrangler@4", "dev",
  "--config", cfg, "--port", String(port), "--ip", "127.0.0.1"],
  { stdio: ["ignore", "pipe", "pipe"] });
let bootLog = "";
const ready = new Promise((resolve, reject) => {
  const timer = setTimeout(() => reject(new Error("wrangler dev never became ready")), 180_000);
  wrangler.stdout.on("data", (d) => {
    bootLog += d;
    if (bootLog.includes("Ready on http")) { clearTimeout(timer); resolve(); }
  });
  wrangler.stderr.on("data", (d) => { bootLog += d; });
  wrangler.once("exit", (code) => {
    clearTimeout(timer);
    reject(new Error(`wrangler dev exited early (${code})\n${bootLog.slice(-1200)}`));
  });
});

const base = `http://127.0.0.1:${port}`;
const post = (body, ip = TEST_IP) => fetch(`${base}/v1/request`, {
  method: "POST",
  headers: { "Content-Type": "application/json", "cf-connecting-ip": ip },
  body: JSON.stringify(body),
});

try {
  try { await ready; }
  catch (err) {
    check("wrangler dev boots locally", false, err.message);
    throw new Error("boot failed");
  }
  check("wrangler dev boots locally (no account, no credentials)", true);

  const health = await fetch(`${base}/healthz`).then((r) => r.json());
  check("/healthz reports the deployed version tag",
    health.version === WORKER_VERSION, `saw ${health.version}, want ${WORKER_VERSION}`);

  const notFound = await fetch(`${base}/nope`);
  check("unknown route 404s", notFound.status === 404, `${notFound.status}`);

  // Malformed probes get their own IP: the broker clamps BEFORE validating
  // (cheapest alternative first, per the reference worker), so every request
  // to the route consumes the budget — including refused ones. A second
  // probe on the same IP is the second token; a third is clamped, which is
  // exactly what an attacker spamming invalid payloads should hit.
  const PROBE_IP = "203.0.113.6";
  const bad = await post({ app_name: "Nope", component: "not a component" }, PROBE_IP);
  check("invalid component is refused before any sink", bad.status === 400, `${bad.status}`);
  check("…and nothing reached the receiver", received.length === 0,
    `${received.length} posts`);
  const badAgain = await post({ app_name: "Nope", component: "not a component" }, PROBE_IP);
  await post({ app_name: "Nope", component: "not a component" }, PROBE_IP);
  const badFlood = await post({ app_name: "Nope", component: "not a component" }, PROBE_IP);
  check("a flood of malformed requests trips the clamp, not just the validator",
    badAgain.status === 400 && badFlood.status === 429,
    `probe statuses ${badAgain.status}/${badFlood.status}`);

  const first = await post({ app_name: "Rehearsal Carol",
    component: "tv.rehearse/.MainActivity", device: "rehearse-local" });
  const firstJson = await first.json().catch(() => ({}));
  check("first press is accepted via discord", first.status === 201 && firstJson.via === "discord",
    `${first.status} ${JSON.stringify(firstJson)}`);
  const card = received[0] && received[0].body;
  check("the receiver got the card once", received.length === 1, `${received.length} posts`);
  check("the card is the real [Icon] embed with component + device",
    Boolean(card?.embeds?.[0]?.title === "[Icon] Rehearsal Carol" &&
      card.embeds[0].fields?.some((f) => f.name === "Component name" &&
        f.value.includes("tv.rehearse/.MainActivity")) &&
      card.embeds[0].fields?.some((f) => f.name === "Device" &&
        f.value.includes("rehearse-local")) && card.username === "Core Builds Icon Auditor"),
    card ? JSON.stringify(card).slice(0, 300) : "no post recorded");

  const second = await post({ app_name: "Rehearsal Carol",
    component: "tv.rehearse/.MainActivity" });
  check("second press (same IP, same hour) also accepted", second.status === 201, `${second.status}`);

  const third = await post({ app_name: "Rehearsal Carol",
    component: "tv.rehearse/.MainActivity" });
  check("third press inside RATE_LIMIT_PER_HOUR=2 is clamped by the KV bucket",
    third.status === 429, `${third.status}`);
  const thirdJson = await third.json().catch(() => ({}));
  check("…with the caller-facing QR-fallback message",
    /rate limited/.test(thirdJson.error ?? ""), JSON.stringify(thirdJson));

  const other = await post({ app_name: "Another App", component: "tv.other/.Main" },
    "203.0.113.9");
  check("a different source IP has its own bucket", other.status === 201, `${other.status}`);
} catch (err) {
  if (err.message !== "boot failed") { check("rehearsal run", false, err.message); }
} finally {
  wrangler.kill("SIGTERM");
  setTimeout(() => wrangler.kill("SIGKILL"), 3_000).unref();
  mock.close();
  rmSync(dir, { recursive: true, force: true });
}

console.log(failures === 0
  ? "\nREHEARSAL GREEN — the worker behaves as promised end to end, zero cloud touched."
  : `\nREHEARSAL RED — ${failures} check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
