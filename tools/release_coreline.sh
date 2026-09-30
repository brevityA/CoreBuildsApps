#!/usr/bin/env bash
# Core Line: manual tag and release.
#
#   tools/release_coreline.sh check      # read-only: is main ready to tag?
#   tools/release_coreline.sh tag        # push coreline-v<version>
#   tools/release_coreline.sh release    # optional: build, sign and publish yourself
#   tools/release_coreline.sh metadata   # tell installed copies (opens a PR)
#
# Run from the root of a clone of brevityA/CoreBuildsApps, logged in with `gh`.
# The version is read from ticker/android/app/build.gradle.kts, so bump it
# (and suite.json, AGENTS.md, README.md, ticker/package.json, the changelog)
# in a PR first — see PUBLISHING.md.
#
# After `tag`, the APK reaches the release one of two ways:
#
#   A. CI (normal). core-line-apk.yml runs on the tag: it builds and signs the
#      release APK with the repo's existing KEYSTORE_* secrets, publishes the
#      versioned release, and moves the floating `coreline` Downloader channel
#      (7375676). Nothing to set up. Watch it, then run `metadata`.
#      suite-release.yml also fires on coreline-v* and has failed at
#      setup-android since 2026-09-13; that does not block A.
#
#   B. By hand, only if CI cannot publish. `release` does what
#      core-line-apk.yml does, on your machine. It needs your own copy of the
#      signing key, because your machine cannot read GitHub's secrets:
#
#        export KEYSTORE_PATH=/path/to/release.jks
#        export KEYSTORE_PASSWORD=...  KEY_ALIAS=...  KEY_PASSWORD=...
#        export ANDROID_HOME=/path/to/android-sdk   # with build-tools
#
#      It refuses to publish unless the certificate matches the APK already
#      on `coreline`, since installed copies would refuse the update.
#
# `metadata` is always last: it stamps Latestrelease/coreline-version.json
# (what installed copies poll) from the *published* APK's own SHA-256, on a
# branch, and opens a PR. Merging that PR announces the update. Doing it
# before the APK is live would announce a version Download does not serve,
# which is what tests/test_update_manifest_gate.py exists to stop.
#
# Never moves a versioned tag. Core EQ has its own tag lane (coreeq-v*).
set -euo pipefail

GRADLE="ticker/android/app/build.gradle.kts"
[ -f "$GRADLE" ] || { echo "✗ run this from the root of a CoreBuildsApps clone" >&2; exit 1; }
VERSION="$(grep -oE 'versionName = "[^"]+"' "$GRADLE" | head -1 | cut -d'"' -f2)"
CODE="$(grep -oE 'versionCode = [0-9]+' "$GRADLE" | head -1 | grep -oE '[0-9]+')"
TAG="coreline-v$VERSION"
FLOAT="coreline"
APK_NAME="coreline-release.apk"
REPO="brevityA/CoreBuildsApps"
META="Latestrelease/coreline-version.json"
DIST="dist-coreline-$VERSION"

fail() { echo "✗ $*" >&2; exit 1; }
ok()   { echo "✓ $*"; }
note() { echo "· $*"; }

need() { command -v "$1" >/dev/null 2>&1 || fail "$1 is not installed"; }
need git
need gh
gh auth status >/dev/null 2>&1 || fail "gh is not logged in: run gh auth login"

on_main() {
  [ -z "$(git status --porcelain --untracked-files=no)" ] || fail "working tree has changes; commit or stash first"
  git fetch --quiet origin main --tags --force
  git checkout --quiet main
  git pull --quiet --ff-only origin main
}

check_version() {
  # Re-read after on_main: the version that counts is main's, not whatever
  # branch the script was started from.
  VERSION="$(grep -oE 'versionName = "[^"]+"' "$GRADLE" | head -1 | cut -d'"' -f2)"
  CODE="$(grep -oE 'versionCode = [0-9]+' "$GRADLE" | head -1 | grep -oE '[0-9]+')"
  TAG="coreline-v$VERSION"; DIST="dist-coreline-$VERSION"
  [ -n "$VERSION" ] && [ -n "$CODE" ] || fail "could not read versionName/versionCode from $GRADLE"
  grep -q "\"versionName\": \"$VERSION\"" suite.json || fail "suite.json does not say $VERSION: finish the version bump PR first"
  grep -q "^## \[$VERSION\]" ticker/CHANGELOG.md  || fail "ticker/CHANGELOG.md has no [$VERSION] section"
  ok "main carries Core Line $VERSION / versionCode $CODE"
}

check_ci_green() {
  local sha bad
  sha="$(git rev-parse HEAD)"
  bad="$(gh run list --repo "$REPO" --commit "$sha" --json name,status,conclusion \
        --jq '[.[] | select(.status != "completed" or (.conclusion != "success" and .conclusion != "skipped"))] | map(.name + " (" + (.conclusion // .status) + ")") | join(", ")')"
  [ -z "$bad" ] || fail "CI on main $(git rev-parse --short HEAD) is not green: $bad"
  ok "CI green on main $(git rev-parse --short HEAD)"
}

tag_exists_remote() { [ -n "$(git ls-remote --tags origin "refs/tags/$1")" ]; }

# ---------------------------------------------------------------------------

cmd_check() {
  on_main
  check_version
  check_ci_green
  if tag_exists_remote "$TAG"; then note "$TAG already exists on origin ($(git rev-list -n1 "$TAG" | cut -c1-9))"
  else ok "$TAG not tagged yet"; fi
  python3 tools/check_suite_truth.py
  python3 tools/audit_contract.py
}

cmd_tag() {
  on_main
  check_version
  check_ci_green
  tag_exists_remote "$TAG" && fail "$TAG already exists. Never move a release tag; bump to a new version instead"
  git tag -a "$TAG" -m "Core Line $VERSION (versionCode $CODE)"
  git push origin "refs/tags/$TAG"
  ok "pushed $TAG at $(git rev-parse --short HEAD)"
  echo
  echo "Now either:"
  echo "  A. let CI publish it:  gh run watch --repo $REPO \$(gh run list --repo $REPO --workflow core-line-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
  echo "     then:  $0 metadata"
  echo "  B. publish it yourself: $0 release   (keystore env vars; see the header)"
}

cmd_release() {
  on_main
  check_version
  tag_exists_remote "$TAG" || fail "$TAG is not pushed yet: run $0 tag first"
  local tag_sha; tag_sha="$(git rev-list -n1 "$TAG")"
  git merge-base --is-ancestor "$tag_sha" origin/main || fail "$TAG is not on main"

  # Do not race CI: if core-line-apk.yml is already publishing this tag, stop.
  local running
  running="$(gh run list --repo "$REPO" --workflow core-line-apk.yml --branch "$TAG" --json status --jq '[.[] | select(.status != "completed")] | length' 2>/dev/null || echo 0)"
  [ "$running" = "0" ] || fail "core-line-apk.yml is still running for $TAG; let it finish (path A) or cancel it before publishing by hand"
  if gh release view "$TAG" --repo "$REPO" --json assets --jq '.assets[].name' 2>/dev/null | grep -qx "$APK_NAME"; then
    fail "$TAG already has $APK_NAME (CI published it). Skip to: $0 metadata"
  fi

  for v in KEYSTORE_PATH KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD ANDROID_HOME; do
    [ -n "${!v:-}" ] || fail "$v is not set (see the header)"
  done
  [ -f "$KEYSTORE_PATH" ] || fail "KEYSTORE_PATH does not point at a file"
  local apksigner
  apksigner="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
  [ -x "$apksigner" ] || fail "no apksigner under \$ANDROID_HOME/build-tools"

  # Build exactly the tagged commit, in a clean worktree, never your checkout.
  local wt; wt="$(mktemp -d)/coreline-$VERSION"
  git worktree add --quiet --detach "$wt" "$tag_sha"
  trap 'git worktree remove --force "'"$wt"'" >/dev/null 2>&1 || true' EXIT
  (cd "$wt/ticker/android" && ./gradlew :app:testReleaseUnitTest :app:assembleRelease --no-daemon)
  local built
  built="$(ls "$wt"/ticker/android/app/build/outputs/apk/release/*.apk | grep -v unsigned | head -1 || true)"
  [ -n "$built" ] || fail "the build produced no signed APK (only *-unsigned.apk?): check the keystore env vars"
  mkdir -p "$DIST"
  cp "$built" "$DIST/$APK_NAME"
  "$apksigner" verify --print-certs "$DIST/$APK_NAME" | tee "$DIST/signature.txt"
  ok "signed: $DIST/$APK_NAME ($(wc -c < "$DIST/$APK_NAME") bytes)"

  # Same key as the release people already have installed, or Android
  # refuses the update. Compare against the current floating APK.
  local prev="$DIST/previous-$APK_NAME"
  if gh release download "$FLOAT" --repo "$REPO" --pattern "$APK_NAME" --output "$prev" --clobber 2>/dev/null; then
    local a b
    a="$("$apksigner" verify --print-certs "$prev" | grep -m1 'SHA-256 digest' || true)"
    b="$("$apksigner" verify --print-certs "$DIST/$APK_NAME" | grep -m1 'SHA-256 digest' || true)"
    [ -n "$a" ] && [ "$a" = "$b" ] || fail "signing certificate differs from the current '$FLOAT' release ($a vs $b); installed copies would refuse this update"
    ok "same signing certificate as the current '$FLOAT' release"
  else
    note "could not download the current '$FLOAT' APK to compare certificates; confirm the key by hand"
    read -r -p "  Same keystore as every earlier Core Line release? [y/N] " yn; [ "$yn" = "y" ] || exit 1
  fi
  sha256sum "$DIST/$APK_NAME" | tee "$DIST/SHA256SUMS"

  read -r -p "Publish $TAG and move '$FLOAT' to it now? [y/N] " yn; [ "$yn" = "y" ] || exit 1

  # Versioned release. Releases are immutable once published, so attach the
  # asset while it is a draft, then publish (core-line-apk.yml's order).
  gh release delete "$TAG" --repo "$REPO" --yes 2>/dev/null || true
  gh release create "$TAG" --repo "$REPO" --draft --generate-notes --verify-tag \
    --title "Core Line $VERSION" "$DIST/$APK_NAME"
  gh release edit "$TAG" --repo "$REPO" --draft=false --latest=false
  ok "published $TAG"

  # Floating Downloader channel: the tag and its release both move.
  git tag -f "$FLOAT" "$tag_sha"
  git push -f origin "refs/tags/$FLOAT"
  gh release delete "$FLOAT" --repo "$REPO" --yes 2>/dev/null || true
  gh release create "$FLOAT" --repo "$REPO" --draft --title "Core Line (latest)" --notes "Always the current Core Line APK. Versioned notes live on \`coreline-v*\` releases.

Downloader code: 7375676
https://github.com/$REPO/releases/download/$FLOAT/$APK_NAME" "$DIST/$APK_NAME"
  gh release edit "$FLOAT" --repo "$REPO" --draft=false --latest=false
  ok "moved '$FLOAT' to $VERSION (Downloader 7375676 now serves it)"
  echo
  echo "Last step: $0 metadata"
}

cmd_metadata() {
  on_main
  check_version
  local dl; dl="$(mktemp -d)"
  gh release download "$TAG" --repo "$REPO" --pattern "$APK_NAME" --dir "$dl/v" \
    || fail "$TAG has no $APK_NAME yet: finish path A or B first"
  gh release download "$FLOAT" --repo "$REPO" --pattern "$APK_NAME" --dir "$dl/f" \
    || fail "the floating '$FLOAT' release has no $APK_NAME"
  local sv sf
  sv="$(sha256sum "$dl/v/$APK_NAME" | cut -d' ' -f1)"
  sf="$(sha256sum "$dl/f/$APK_NAME" | cut -d' ' -f1)"
  [ "$sv" = "$sf" ] || fail "'$FLOAT' ($sf) is not the $TAG APK ($sv): Downloader would serve something else"
  ok "both channels serve the same APK: sha256 $sv"

  local branch="release/coreline-$VERSION-metadata"
  git checkout --quiet -B "$branch" origin/main
  # Computes versionName/versionCode from Gradle and apkSha256 from the file
  # people will actually download.
  python3 tools/generate_release_metadata.py coreline --apk "$dl/v/$APK_NAME" --out "$META"
  # Point the notes at this release, as the 1.4.1 file did, not the list.
  python3 - "$META" "https://github.com/$REPO/releases/tag/$TAG" <<'PY2'
import json, sys
p, url = sys.argv[1], sys.argv[2]
d = json.load(open(p)); d['releaseNotesUrl'] = url
open(p, 'w').write(json.dumps(d, indent=2) + '\n')
PY2
  grep -q "\"versionName\": \"$VERSION\"" "$META" || fail "$META did not come out as $VERSION"
  python3 tools/audit_contract.py
  python3 tools/check_suite_truth.py
  git add "$META"
  git commit --quiet -m "Publish Core Line $VERSION updater metadata

$TAG is tagged and the signed APK is live on both the versioned release and
the floating \`$FLOAT\` Downloader channel (7375676), with the same bytes:
sha256 $sv. Installed copies can now be told about it."
  git push --quiet -u origin "$branch"
  gh pr create --repo "$REPO" --base main --head "$branch" \
    --title "Publish Core Line $VERSION updater metadata" \
    --body "Stamps \`$META\` to $VERSION / $CODE from the published APK (sha256 \`$sv\`, identical on \`$TAG\` and \`$FLOAT\`). Merging this is what announces the update to installed copies."
  ok "metadata PR opened; merge it to announce $VERSION"
  git checkout --quiet main
}

case "${1:-}" in
  check)    cmd_check ;;
  tag)      cmd_tag ;;
  release)  cmd_release ;;
  metadata) cmd_metadata ;;
  *) echo "usage: $0 check|tag|release|metadata"; exit 2 ;;
esac
