#!/usr/bin/env node
/**
 * corebuilds-tools MCP server — stdio transport, JSON-RPC 2.0.
 *
 * Wraps the CoreBuildsApps validators and generators as typed MCP tools
 * so Claude gets structured results instead of parsed stdout.
 *
 * No external dependencies. Runs with bare Node.js >= 18.
 */

import { createInterface } from "node:readline";
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = resolve(__dirname, "..", "..");

const SERVER_INFO = {
  name: "corebuilds-tools",
  version: "1.0.0",
};

const TOOLS = [
  {
    name: "validate_catalog",
    description:
      "Run the icon pack validator (tools/validate.py). " +
      "Checks PNG dimensions/alpha, appfilter integrity, manifest intents, " +
      "palette, version sync, and banner composition. " +
      "Returns the check count, pass/fail status, and any failures by name.",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
  },
  {
    name: "validate_motion",
    description:
      "Run the Motion asset validator (tools/validate_motion.py). " +
      "Checks manifest-motion.json structure, video entries, file existence, " +
      "URL hosts, feed sync, and bundled manifest parity. " +
      "Returns check count, pass/fail, and named failures.",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
  },
  {
    name: "build_icons",
    description:
      "Run the icon asset pipeline (tools/build_icons.py). " +
      "Reads tools/catalog.json and writes SVG masters, 512px PNGs, " +
      "appfilter.xml, drawable.xml, iconpack.xml, icon_pack.xml, " +
      "IconPackList.md, and the preview sheet. " +
      "Returns the generator receipt (what was written, counted).",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
  },
  {
    name: "build_overflight_feed_check",
    description:
      "Check whether Motion/overflight-feed.json is in sync with its " +
      "sources (Motion/live-feed.json + Motion/motion-feed.json). " +
      "Pass check_only=false to regenerate the file.",
    inputSchema: {
      type: "object",
      properties: {
        check_only: {
          type: "boolean",
          description: "If true (default), only check sync without writing.",
          default: true,
        },
      },
      additionalProperties: false,
    },
  },
  {
    name: "check_version_sync",
    description:
      "Verify that version numbers are consistent across " +
      "app/build.gradle.kts, Latestrelease/version.json, and " +
      "tools/catalog.json meta.version. " +
      "Reports each source's values and any mismatches.",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
  },
];

function runPython(script, args = []) {
  const cmd = ["python3", resolve(REPO_ROOT, script), ...args].join(" ");
  try {
    const stdout = execSync(cmd, {
      cwd: REPO_ROOT,
      encoding: "utf-8",
      timeout: 120_000,
      maxBuffer: 4 * 1024 * 1024,
      env: { ...process.env, PYTHONDONTWRITEBYTECODE: "1" },
    });
    return { exitCode: 0, output: stdout.trimEnd() };
  } catch (err) {
    const output = (err.stdout || "") + (err.stderr || "");
    return { exitCode: err.status ?? 1, output: output.trimEnd() };
  }
}

function checkVersionSync() {
  const results = [];
  let mismatches = 0;

  // 1. app/build.gradle.kts
  let gradleCode = null;
  let gradleName = null;
  try {
    const gradle = readFileSync(
      resolve(REPO_ROOT, "app", "build.gradle.kts"),
      "utf-8"
    );
    const codeMatch = gradle.match(/versionCode\s*=\s*(\d+)/);
    const nameMatch = gradle.match(/versionName\s*=\s*"([^"]+)"/);
    gradleCode = codeMatch ? parseInt(codeMatch[1], 10) : null;
    gradleName = nameMatch ? nameMatch[1] : null;
    results.push(
      `build.gradle.kts: versionCode=${gradleCode}, versionName="${gradleName}"`
    );
  } catch (e) {
    results.push(`build.gradle.kts: could not read — ${e.message}`);
    mismatches++;
  }

  // 2. Latestrelease/version.json
  let vjCode = null;
  let vjName = null;
  let vjIconCount = null;
  try {
    const vj = JSON.parse(
      readFileSync(
        resolve(REPO_ROOT, "Latestrelease", "version.json"),
        "utf-8"
      )
    );
    vjCode = vj.versionCode ?? null;
    vjName = vj.versionName ?? null;
    vjIconCount = vj.iconCount ?? null;
    results.push(
      `version.json: versionCode=${vjCode}, versionName="${vjName}", iconCount=${vjIconCount}`
    );
  } catch (e) {
    results.push(`version.json: could not read — ${e.message}`);
    mismatches++;
  }

  // 3. tools/catalog.json meta
  let catalogVersion = null;
  let catalogCount = null;
  let catalogIconsLength = null;
  try {
    const catalog = JSON.parse(
      readFileSync(resolve(REPO_ROOT, "tools", "catalog.json"), "utf-8")
    );
    catalogVersion = catalog.meta?.version ?? null;
    catalogCount = catalog.meta?.count ?? null;
    catalogIconsLength = Array.isArray(catalog.icons)
      ? catalog.icons.length
      : null;
    results.push(
      `catalog.json: meta.version="${catalogVersion}", meta.count=${catalogCount}, actual icons=${catalogIconsLength}`
    );
  } catch (e) {
    results.push(`catalog.json: could not read — ${e.message}`);
    mismatches++;
  }

  // Treat missing required fields as mismatches
  if (gradleCode === null) {
    results.push("MISSING: versionCode not found in build.gradle.kts");
    mismatches++;
  }
  if (gradleName === null) {
    results.push("MISSING: versionName not found in build.gradle.kts");
    mismatches++;
  }
  if (vjCode === null) {
    results.push("MISSING: versionCode not found in version.json");
    mismatches++;
  }
  if (vjName === null) {
    results.push("MISSING: versionName not found in version.json");
    mismatches++;
  }
  if (vjIconCount === null) {
    results.push("MISSING: iconCount not found in version.json");
    mismatches++;
  }
  if (catalogVersion === null) {
    results.push("MISSING: meta.version not found in catalog.json");
    mismatches++;
  }
  if (catalogCount === null) {
    results.push("MISSING: meta.count not found in catalog.json");
    mismatches++;
  }

  // Cross-check
  if (gradleCode !== null && vjCode !== null && gradleCode !== vjCode) {
    results.push(
      `MISMATCH: versionCode — gradle=${gradleCode} vs version.json=${vjCode}`
    );
    mismatches++;
  }
  if (gradleName !== null && vjName !== null && gradleName !== vjName) {
    results.push(
      `MISMATCH: versionName — gradle="${gradleName}" vs version.json="${vjName}"`
    );
    mismatches++;
  }
  if (gradleName !== null && catalogVersion !== null && gradleName !== catalogVersion) {
    results.push(
      `MISMATCH: versionName — gradle="${gradleName}" vs catalog.json meta.version="${catalogVersion}"`
    );
    mismatches++;
  }
  if (vjName !== null && catalogVersion !== null && vjName !== catalogVersion) {
    results.push(
      `MISMATCH: versionName — version.json="${vjName}" vs catalog.json meta.version="${catalogVersion}"`
    );
    mismatches++;
  }
  if (vjIconCount !== null && catalogCount !== null && vjIconCount !== catalogCount) {
    results.push(
      `MISMATCH: iconCount — version.json=${vjIconCount} vs catalog.json meta.count=${catalogCount}`
    );
    mismatches++;
  }
  if (catalogCount !== null && catalogIconsLength !== null && catalogCount !== catalogIconsLength) {
    results.push(
      `MISMATCH: catalog.json meta.count=${catalogCount} but actual icons array has ${catalogIconsLength} entries`
    );
    mismatches++;
  }

  const ok = mismatches === 0;
  return {
    ok,
    mismatches,
    details: results,
    summary: ok
      ? `Version sync OK — versionCode=${gradleCode}, versionName="${gradleName}", iconCount=${catalogCount}`
      : `${mismatches} mismatch(es) found`,
  };
}

function handleTool(name, args = {}) {
  switch (name) {
    case "validate_catalog": {
      const r = runPython("tools/validate.py");
      return {
        ok: r.exitCode === 0,
        exitCode: r.exitCode,
        output: r.output,
      };
    }
    case "validate_motion": {
      const r = runPython("tools/validate_motion.py");
      return {
        ok: r.exitCode === 0,
        exitCode: r.exitCode,
        output: r.output,
      };
    }
    case "build_icons": {
      const r = runPython("tools/build_icons.py");
      return {
        ok: r.exitCode === 0,
        exitCode: r.exitCode,
        output: r.output,
      };
    }
    case "build_overflight_feed_check": {
      const flag = args.check_only !== false ? "--check" : "";
      const r = runPython("tools/build_overflight_feed.py", flag ? [flag] : []);
      return {
        ok: r.exitCode === 0,
        exitCode: r.exitCode,
        output: r.output,
      };
    }
    case "check_version_sync": {
      return checkVersionSync();
    }
    default:
      throw { code: -32601, message: `Unknown tool: ${name}` };
  }
}

function validateArgs(args, schema) {
  const errors = [];
  if (typeof args !== "object" || args === null || Array.isArray(args)) {
    return ["arguments must be a JSON object"];
  }
  const props = schema.properties ?? {};
  if (schema.additionalProperties === false) {
    for (const key of Object.keys(args)) {
      if (!(key in props)) {
        errors.push(`unknown argument: ${key}`);
      }
    }
  }
  for (const [key, def] of Object.entries(props)) {
    if (key in args && typeof args[key] !== def.type) {
      errors.push(`${key} must be ${def.type}, got ${typeof args[key]}`);
    }
  }
  return errors;
}

// JSON-RPC 2.0 + MCP protocol handling

function makeResponse(id, result) {
  return { jsonrpc: "2.0", id, result };
}

function makeError(id, code, message, data) {
  const err = { jsonrpc: "2.0", id, error: { code, message } };
  if (data !== undefined) err.error.data = data;
  return err;
}

function handleRequest(req) {
  const { id, method, params } = req;

  switch (method) {
    case "initialize":
      return makeResponse(id, {
        protocolVersion: "2024-11-05",
        capabilities: { tools: { listChanged: false } },
        serverInfo: SERVER_INFO,
      });

    case "notifications/initialized":
      return null;

    case "tools/list":
      return makeResponse(id, { tools: TOOLS });

    case "tools/call": {
      const toolName = params?.name;
      const toolArgs = params?.arguments ?? {};
      const toolDef = TOOLS.find((t) => t.name === toolName);
      if (!toolDef) {
        return makeResponse(id, {
          content: [
            { type: "text", text: JSON.stringify({ error: `Unknown tool: ${toolName}` }) },
          ],
          isError: true,
        });
      }
      const argErrors = validateArgs(toolArgs, toolDef.inputSchema);
      if (argErrors.length > 0) {
        return makeResponse(id, {
          content: [
            { type: "text", text: JSON.stringify({ error: argErrors.join("; ") }) },
          ],
          isError: true,
        });
      }
      try {
        const result = handleTool(toolName, toolArgs);
        return makeResponse(id, {
          content: [
            { type: "text", text: JSON.stringify(result, null, 2) },
          ],
        });
      } catch (err) {
        if (err.code) {
          return makeError(id, err.code, err.message);
        }
        return makeResponse(id, {
          content: [
            { type: "text", text: JSON.stringify({ error: String(err) }) },
          ],
          isError: true,
        });
      }
    }

    case "ping":
      return makeResponse(id, {});

    default:
      if (method?.startsWith("notifications/")) return null;
      return makeError(id, -32601, `Method not found: ${method}`);
  }
}

// stdio transport — one JSON-RPC message per line

const rl = createInterface({ input: process.stdin, terminal: false });

rl.on("line", (line) => {
  const trimmed = line.trim();
  if (!trimmed) return;
  let parsed;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    process.stderr.write(`mcp: ignoring malformed JSON line\n`);
    return;
  }
  if (parsed === null || typeof parsed !== "object") return;

  const response = handleRequest(parsed);
  if (response !== null) {
    process.stdout.write(JSON.stringify(response) + "\n");
  }
});

rl.on("close", () => process.exit(0));
process.on("SIGTERM", () => process.exit(0));
process.on("SIGINT", () => process.exit(0));
