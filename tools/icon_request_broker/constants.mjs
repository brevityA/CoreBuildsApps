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
// Core Builds Plus (the Google Play build's one-time product): a request that
// carries a purchase token Google confirms is filed with PRIORITY_LABEL.
// Both ids are pinned to the app by worker.test.mjs.
export const PRIORITY_LABEL = "priority";
export const PLAY_PACKAGE = "tv.corebuilds.iconpack.play";
export const PLUS_PRODUCT_ID = "core_builds_plus";
// Core Builds Plus "Request all": one press files up to BATCH_MAX missing apps,
// at most BATCH_PER_DAY presses a day per purchase. Pinned to the app's
// BatchRequest by tests/test_batch_requests.py.
export const BATCH_MAX = 10;
export const BATCH_PER_DAY = 3;
export const WORKER_VERSION = "2026-10-28-iconreq07";
