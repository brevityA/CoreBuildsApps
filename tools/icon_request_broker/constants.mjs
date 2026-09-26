/**
 * Shared literals for the icon-request broker, deliberately in their own
 * (non-entry) module: the entry module of a deployed Worker may only export
 * functions or ExportedHandlers — workerd rejects a top-level
 * `export const <string>` at boot with
 * "Incorrect type for map entry '<name>': the provided value is not of type
 * 'function or ExportedHandler'". Node's ESM loader accepts them, so unit
 * tests alone cannot catch this; the first deployed (or local-runtime) boot
 * is the only gate that does. worker.mjs imports these; tests, smoke.mjs,
 * and rehearse-local.mjs import them straight from here.
 */
export const REPO = "brevityA/CoreBuildsApps";
export const ISSUE_LABEL = "icon request";
export const TITLE_PREFIX = "[Icon] ";
// An app the pack already maps under another activity is a mapping report,
// filed the way .github/ISSUE_TEMPLATE/2.icon_not_applying.yml files one.
export const MAPPING_LABEL = "mapping";
export const MAPPING_TITLE_PREFIX = "[Not applying] ";
export const WORKER_VERSION = "2026-09-24-iconreq05";
