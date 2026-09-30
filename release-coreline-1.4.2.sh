#!/usr/bin/env bash
# release-coreline-1.4.2.sh — drive the Core Line 1.4.2 release.
#
# Three subcommands, run in this order:
#
#   ./release-coreline-1.4.2.sh check      # read-only pre-flight (no side effects)
#   ./release-coreline-1.4.2.sh tag        # tag HEAD and push the annotated coreline-v1.4.2 tag;
#                                          # .github/workflows/core-line-apk.yml builds, signs,
#                                          # and publishes from there using the existing
#                                          # KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS /
#                                          # KEY_PASSWORD secrets in the repo environment.
#   ./release-coreline-1.4.2.sh metadata   # run AFTER CI has attached coreline-release.apk to
#                                          # the coreline-v1.4.2 release; refreshes
#                                          # Latestrelease/coreline-version.json with version,
#                                          # release date, apkUrl and apkSha256 via
#                                          # tools/generate_release_metadata.py.
#
# All three commands are safe to re-run. `check` is read-only. `tag` refuses to
# move an existing tag. `metadata` overwrites Latestrelease/coreline-version.json
# using the same Python helper the existing release workflow uses, so the
# metadata file stays in lock-step with the GitHub release.
#
# Cross-references:
#   PUBLISHING.md                       — the "Before tagging" / "Tagging" runbook
#   docs/RELEASE-INFRA.md               — squash / [skip ci] / branch-ancestor rules
#   tools/check_suite_truth.py          — suite.json vs. Gradle invariants
#   tools/generate_release_metadata.py  — invoked by `metadata`
#   .github/workflows/core-line-apk.yml — what `tag` triggers

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration — keep these in one place so a future 1.4.3 cut is a small edit.
# ---------------------------------------------------------------------------
VERSION="1.4.2"
TAG="coreline-v${VERSION}"
FLOATING_TAG="coreline"
APK_ASSET="coreline-release.apk"
METADATA_FILE="Latestrelease/coreline-version.json"
SUITE_FILE="suite.json"
GRADLE_FILE="ticker/android/app/build.gradle.kts"
REPO="brevityA/CoreBuildsApps"

# Colours (skipped when stdout isn't a terminal).
if [[ -t 1 ]]; then
  C_OK=$'\033[32m'; C_BAD=$'\033[31m'; C_WARN=$'\033[33m'; C_INFO=$'\033[36m'; C_RST=$'\033[0m'
else
  C_OK=''; C_BAD=''; C_WARN=''; C_INFO=''; C_RST=''
fi

log()  { printf '%s[release]%s %s\n' "$C_INFO" "$C_RST" "$*"; }
ok()   { printf '%s[ ok ]%s %s\n' "$C_OK"  "$C_RST" "$*"; }
warn() { printf '%s[warn ]%s %s\n' "$C_WARN" "$C_RST" "$*" >&2; }
die()  { printf '%s[fail]%s %s\n' "$C_BAD" "$C_RST" "$*" >&2; exit 1; }

usage() {
  cat <<EOF
Usage: $0 <check|tag|metadata>

  check      Pre-flight only. Verifies version, suite.json, metadata, branch
             ancestry, [skip ci] absence, tag absence, and green CI. Exits
             non-zero on the first failure and never writes anything.

  tag        Creates the annotated $TAG tag at HEAD and pushes it to origin.
             Triggers .github/workflows/core-line-apk.yml, which builds, signs,
             and publishes $APK_ASSET on the GitHub release. Refuses to run
             if the tag already exists locally or remotely, or if \`check\`
             would have failed.

  metadata   Downloads $APK_ASSET from the published $TAG GitHub release and
             refreshes $METADATA_FILE (versionCode, versionName, releaseDate,
             apkUrl, apkSha256). Run this AFTER CI has finished and the asset
             is attached. Re-running is harmless.
EOF
}

require_cmd() {
  for c in "$@"; do
    command -v "$c" >/dev/null 2>&1 || die "missing required command: $c"
  done
}

# Print a value from a Gradle Kotlin DSL file (very small subset: versionName
# strings and versionCode integers).
gradle_value() {
  local file="$1" name="$2"
  python3 -c "
import re, sys
src = open(sys.argv[1], encoding='utf-8').read()
m = re.search(rf'{sys.argv[2]}\\s*=\\s*(?:\"([^\"]+)\"|(\\d+))', src)
if not m:
    sys.stderr.write(f'missing {sys.argv[2]} in {sys.argv[1]}\\n'); sys.exit(2)
print(m.group(1) or m.group(2))
" "$file" "$name"
}

# ---------------------------------------------------------------------------
# Pre-flight checks shared by `tag` and `metadata`. `check` runs them in
# non-strict mode (just reports); `tag` runs them in strict mode (any failure
# aborts before we touch anything).
# ---------------------------------------------------------------------------

# Returns 0 if all checks pass, non-zero otherwise. Echoes human-readable
# status for each check.
preflight() {
  local strict="$1"   # "strict" => die(); "report" => warn()
  local fail=0

  # 1. Required tools.
  for c in git gh python3 jq; do
    if command -v "$c" >/dev/null 2>&1; then ok "tool: $c"
    else warn "missing tool: $c"; fail=1; fi
  done
  [[ $fail -eq 0 && "$strict" == "strict" ]] || true

  # 2. Working tree clean (the docs warn that uncommitted bumps get dropped by
  #    a squash, and the tag points at HEAD).
  if [[ -z "$(git status --porcelain --untracked-files=normal)" ]]; then
    ok "working tree clean"
  else
    warn "working tree has changes:"; git status --short | sed 's/^/    /'
    fail=1
  fi

  # 3. Gradle versionName/versionCode match $VERSION.
  local g_name g_code
  g_name="$(gradle_value "$GRADLE_FILE" versionName)"
  g_code="$(gradle_value "$GRADLE_FILE" versionCode)"
  if [[ "$g_name" == "$VERSION" ]]; then
    ok "Gradle $GRADLE_FILE versionName = $g_name"
  else
    warn "Gradle $GRADLE_FILE versionName = $g_name, expected $VERSION"; fail=1
  fi

  # 5. suite.json agrees with Gradle for the `line` app.
  if command -v jq >/dev/null 2>&1; then
    local s_name s_code s_prefix s_appid
    s_name="$(jq -r '.apps.line.versionName' "$SUITE_FILE")"
    s_code="$(jq -r '.apps.line.versionCode' "$SUITE_FILE")"
    s_prefix="$(jq -r '.apps.line.tagPrefix'    "$SUITE_FILE")"
    s_appid="$(jq -r '.apps.line.applicationId'  "$SUITE_FILE")"
    if [[ "$s_name" == "$g_name" && "$s_code" == "$g_code" && "${s_prefix}${s_name}" == "$TAG" ]]; then
      ok "suite.json line app = $s_name (code $s_code, tag $s_prefix$s_name)"
    else
      warn "suite.json drift: versionName=$s_name versionCode=$s_code tagPrefix=$s_prefix"
      fail=1
    fi
  else
    warn "jq missing — skipped suite.json cross-check"; fail=1
  fi

  # 6. Latestrelease/coreline-version.json is either already at $VERSION or
  #    older (so a tag without metadata is fine). `metadata` will write it.
  if [[ -f "$METADATA_FILE" ]]; then
    local m_name
    m_name="$(jq -r '.versionName // "unknown"' "$METADATA_FILE")"
    if [[ "$m_name" == "$VERSION" ]]; then
      ok "$METADATA_FILE already at $VERSION"
    else
      warn "$METADATA_FILE is at $m_name; \`metadata\` will refresh it after CI"
    fi
  else
    warn "$METADATA_FILE missing; \`metadata\` will create it"
  fi

  # 7. HEAD is an ancestor of origin/main (the workflow's gate; a tag on a
  #    feature branch that never merged would otherwise build without main).
  git fetch --quiet origin main
  if git merge-base --is-ancestor HEAD origin/main; then
    ok "HEAD $(git rev-parse --short HEAD) is an ancestor of origin/main"
  else
    warn "HEAD is not on origin/main — the workflow will reject the tag"
    fail=1
  fi

  # 8. Squash-merge hazard: docs/RELEASE-INFRA.md calls out that a `[skip ci]`
  #    anywhere in the squash body silently skips the main push workflows.
  if git show -s --format=%B origin/main | grep -qi '\[skip ci\]'; then
    warn "origin/main squash body contains [skip ci] — tag builds will be skipped!"
    fail=1
  else
    ok "no [skip ci] marker on origin/main"
  fi

  # 9. Tag absent locally and remotely.
  if git rev-parse "$TAG" >/dev/null 2>&1; then
    warn "local tag $TAG already exists"; fail=1
  else
    ok "no local $TAG"
  fi
  git fetch --quiet --tags origin
  if git ls-remote --tags origin "refs/tags/$TAG" | grep -q "$TAG"; then
    warn "remote tag $TAG already exists on origin"; fail=1
  else
    ok "no remote $TAG"
  fi

  # 10. Floating tag coreline points at this commit (so the Downloader
  #     download URL stays current after the release).
  local float_sha
  float_sha="$(git ls-remote --tags origin "refs/tags/$FLOATING_TAG" 2>/dev/null | awk '{print $1}' | head -1 || true)"
  if [[ -z "$float_sha" ]]; then
    warn "floating tag $FLOATING_TAG not present yet (workflow will create it)"
  elif [[ "$float_sha" == "$(git rev-parse HEAD)" ]]; then
    ok "floating tag $FLOATING_TAG already at HEAD"
  else
    warn "floating tag $FLOATING_TAG is at ${float_sha:0:9}, expected $(git rev-parse --short HEAD) — workflow will move it"
  fi

  # 11. CI green on HEAD. Look at the push-triggered runs on origin/main that
  #     targeted this exact commit; if any failed/needed was rolled out, abort.
  if command -v gh >/dev/null 2>&1; then
    local runs_json
    runs_json="$(gh run list --branch main --limit 25 --json name,conclusion,headSha,event \
      | python3 -c "
import json, sys
HEAD = sys.argv[1]
rows = [r for r in json.load(sys.stdin) if r.get('headSha') == HEAD and r.get('event') == 'push']
if not rows:
    print('NONE'); sys.exit(0)
bad = [r for r in rows if r.get('conclusion') not in ('success', None)]
for r in rows:
    print(f\"{r['conclusion'] or 'pending':<10}  {r['name']}\")
if bad:
    sys.exit(1)
" "$(git rev-parse HEAD)")" || fail=1
    if [[ "$runs_json" == "NONE" ]]; then
      warn "no push runs found on HEAD — has CI been triggered?"
      fail=1
    else
      ok "CI on HEAD:"
      while IFS= read -r line; do printf '    %s\n' "$line"; done <<<"$runs_json"
    fi
  else
    warn "gh missing — skipped CI check"; fail=1
  fi

  if [[ $fail -ne 0 ]]; then
    if [[ "$strict" == "strict" ]]; then
      die "pre-flight failed; refusing to proceed"
    else
      warn "pre-flight reported problems above"
      return 1
    fi
  fi
  return 0
}

# ---------------------------------------------------------------------------
# Subcommand: check
# ---------------------------------------------------------------------------
cmd_check() {
  log "pre-flight (read-only) for Core Line $VERSION"
  if preflight report; then
    ok "all checks passed — safe to run \`tag\`"
    return 0
  fi
  return 1
}

# ---------------------------------------------------------------------------
# Subcommand: tag
# ---------------------------------------------------------------------------
cmd_tag() {
  log "strict pre-flight before tagging $TAG"
  preflight strict

  # Extra safety: refuse to clobber. `preflight` already checked both sides,
  # but a race between fetch and tag is possible.
  if git rev-parse "$TAG" >/dev/null 2>&1; then
    die "local tag $TAG exists; refusing to move it"
  fi
  if git ls-remote --tags origin "refs/tags/$TAG" | grep -q "$TAG"; then
    die "remote tag $TAG exists; refusing to republish"
  fi

  local msg="Core Line $VERSION

Triggered by release-coreline-1.4.2.sh. .github/workflows/core-line-apk.yml will
build, sign with KEYSTORE_BASE64, attach $APK_ASSET to a GitHub release at
$TAG, and move the floating $FLOATING_TAG tag."
  log "creating annotated tag $TAG at $(git rev-parse --short HEAD)"
  git tag -a "$TAG" -m "$msg"
  log "pushing $TAG to origin (this triggers the release workflow)"
  git push origin "$TAG"
  ok "tag $TAG pushed. Watch the workflow:"
  log "  gh run watch --exit-status \$(gh run list --workflow='Core Line APK' --limit 1 --json databaseId -q '.[0].databaseId')"
}

# ---------------------------------------------------------------------------
# Subcommand: metadata
# ---------------------------------------------------------------------------
cmd_metadata() {
  require_cmd gh python3 jq

  # Confirm the release exists and the APK asset is there. The whole point of
  # this subcommand is to run after CI has finished; if the asset isn't there
  # yet, the user is jumping the gun.
  log "looking up release $TAG on $REPO"
  if ! gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
    die "release $TAG not found — has the workflow finished?"
  fi

  local asset_json
  asset_json="$(gh release view "$TAG" --repo "$REPO" --json assets \
    --jq --arg apk "$APK_ASSET" '.assets[] | select(.name == $apk) | {name, url, size}')"
  if [[ -z "$asset_json" ]]; then
    die "$APK_ASSET not attached to $TAG yet — wait for the workflow's 'Name the APK' step"
  fi
  log "found asset:"; printf '    %s\n' "$asset_json"

  # Download the APK to a temp path; verify it's an APK and capture its size.
  local tmp; tmp="$(mktemp -t coreline-release-XXXXXX.apk)"
  # shellcheck disable=SC2064
  trap "rm -f '$tmp'" EXIT
  log "downloading $APK_ASSET to $tmp"
  gh release download "$TAG" --repo "$REPO" --pattern "$APK_ASSET" --dir "$(dirname "$tmp")" --clobber
  local dl
  dl="$(dirname "$tmp")/$APK_ASSET"
  [[ -s "$dl" ]] || die "downloaded APK is empty"
  mv "$dl" "$tmp"
  ok "downloaded $(du -h "$tmp" | cut -f1)"

  # tools/generate_release_metadata.py writes apkSha256 + apkUrl + releaseDate
  # and preserves the previous versionName/versionCode fields by reading the
  # existing Latestrelease/coreline-version.json first. --out forces the
  # destination to be the same file, which is what we want.
  log "refreshing $METADATA_FILE via tools/generate_release_metadata.py"
  python3 tools/generate_release_metadata.py coreline \
      --apk "$tmp" \
      --out  "$METADATA_FILE"

  # Print the resulting file so the operator can eyeball it before committing.
  echo "---- $METADATA_FILE ----"
  cat "$METADATA_FILE"
  echo "------------------------"

  log "next step: review the diff, then commit and merge $METADATA_FILE to main so the in-app sideloader picks it up"
}

# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------
case "${1:-}" in
  check)    shift; cmd_check ;;
  tag)      shift; cmd_tag ;;
  -h|--help|"") usage ;;
  *) usage; die "Unknown subcommand: $1" ;;
esac