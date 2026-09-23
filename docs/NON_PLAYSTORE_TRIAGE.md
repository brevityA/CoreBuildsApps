# Non-Play Store enhancements — triage against the real repo

Companion to `NON_PLAYSTORE_ENHANCEMENTS.md` (the proposal, committed verbatim).
Every claim in the proposal was checked against the source before any of it was
built; this file records what held up, what shipped, and what is queued with
why. Feature numbers are the proposal's.

## The premise, corrected once

The proposal assumes a Play-Store build and a sideload build. Today there is
only one APK: `PUBLISHING.md` carries Play credentials as `[USER TO SUPPLY]`, so
no Play track exists yet. That makes every feature "sideload" by default — and
still does not license Play-hostile choices, because the permissions audit
(b656721) deliberately kept the manifest inside what a future Play review would
accept. The triage rule below is therefore: *sideload freedom where it costs
nothing, Play-safe mechanisms where a Play-safe mechanism exists.*

## 1. Missing App Auditor + QR — SHIPPED, with a better mechanism than proposed

The proposal's `QUERY_ALL_PACKAGES` is the one permission the audit refused and
the wiring gate locks out. It was also unnecessary: two intent-filter entries
in `<queries>` (`ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER`, and the same with
`CATEGORY_LAUNCHER`) make every launchable app visible to
`queryIntentActivities`, which is exactly the audit's input, and intent-based
visibility is not the sensitive permission. Both entries shipped in the app
and Pop manifests, and the wiring gate reads the `<queries>` span itself to
keep them there — a whole-manifest grep would pass on the app's own
intent-filter, which declares `LEANBACK_LAUNCHER` too, while the scan came up
empty on the box.

`AuditorActivity` (Settings → HELP → *Scan for unmapped apps*) diffs that scan
against the bundled `appfilter.xml` asset at package level, lists the
unmapped apps label-over-component, and pressing one draws a QR code that
opens the icon-request form with the app name, the exact component and a
device note already filled in. Three details carry the weight:

- **The deep link is generated, not typed.** `tools/build_issue_prefills.py`
  now also writes `app/src/main/res/values/issue_prefill.xml` — a fmt string
  built from the issue form (template slug, labels, title prefix baked in;
  `%1$s` app label, `%2$s` component, `%3$s` device note), and `--check`
  fails CI on drift. The screen only URL-encodes its three arguments, so a
  renamed form field is a generator failure, never a silently empty box on
  GitHub. Fields GitHub will not prefill (`device` dropdown, `confirm`
  checkboxes) are simply absent, the same rule the README links live under.
- **The encoder is vendored, not hand-rolled.** Nayuki's QR Code generator
  (Java, MIT) at upstream commit `3c6d0b3`, checksums and licence in
  `THIRD_PARTY_NOTICES.md`; `QrBitmap.kt` is the Core Builds wrapper — error
  correction M for sofa-angle photography, four-module quiet zone drawn into
  the bitmap so dark chrome cannot swallow it, black-on-white modules.
- **Back leaves the QR panel before it leaves the screen**, the wallpapers
  selection-mode grammar, because a back press that abandons the list along
  with a code that failed to scan wastes the scan.

Mockups with a real scannable code over an example URL:
`docs/app-ui-auditor.png` and `docs/app-ui-auditor-qr.png`, rendered by
`tools/build_app_ui_mockups.py`.

**QR fall-out found in the field (and its fix).** Reported after a scan:
the phone opens "the issues page" and the package/activity — the one thing
the triage needs to assign an icon — never arrives; and no URL can carry a
picture of the current icon anyway. The investigation's evidence trail
(decoded QR payloads, the exact `return_to` across GitHub's login bounce,
v1.9.1 and HEAD resource diffs) placed the drop downstream of the pack:
GitHub's field-level prefill is unreliable where the couch can't see —
chiefly the GitHub mobile app claiming github.com links and discarding the
query payload. The structural fix, shipped with the broker milestone: the
QR panel now *displays* the identity in pixels (current launcher icon +
app name + component, photographable and attachable), and an interstitial
(`docs/icon-request/index.html`, GitHub Pages, browser-borne by domain)
can instead become the QR payload via one `--landing` flag on the prefill
generator — always rendered, offering the account form plus the same
no-login broker POST the TV makes directly. The broker carries Origin
reflection + preflight solely so that phone POST is readable.

## 2. Launcher cache buster — SHIPPED this round

Two rows under a new LAUNCHER group in Settings: **Refresh launcher icons**
re-fires the detected launcher's apply contract through the existing
`ApplyIconPack.apply`, and **Launcher app info** opens
`ACTION_APPLICATION_DETAILS_SETTINGS` for the detected launcher's installed
package, the one place a force stop lives. Both name every outcome (applied,
manual path, nothing detected) because a silent no-op is the bug being fixed.
The proposal's `killBackgroundProcesses` alternative was not taken: it needs a
new permission (`KILL_BACKGROUND_PROCESSES`) and cannot reach a foreground
launcher's in-memory bitmap cache on modern Android, so it would advertise a
cure it cannot deliver. The system page can.

## 3. What's New highlights — SHIPPED in-app, release-side follow-up queued

`UpdateChecker` now parses an optional `highlights` array (capped at six
lines) and `MainActivity` renders it as bullets inside the existing update bar,
gone unless the manifest carries them, so old manifests render exactly the old
bar. Two corrections to the proposal's JSON: the live manifest fields are
`versionName` / `apkUrl` / `apkSha256` / `iconCount` (not `version` / `url`),
and the bar was never replaced — it grows, because the bar's height sits on the
vertical D-pad chain and swapping containers mid-chain is how focus strands are
born. Queued: `tools/prepare_release.py` should emit `highlights` into
`Latestrelease/version.json` from the CHANGELOG tranche, so the field cannot go
stale by hand.

## 4. Universal icon masking in Classic — queued; probe recorded, evidence thin

Pop already ships `<iconback>` / `<iconmask>` / `<iconupon>`; `tests/test_pop.py`
locks them as that variant's art direction. Classic's identity is the opposite:
transparent monoline marks, which Projectivy and Monet composite natively. The
legacy ADW compositing tags only affect *unmapped* icons, and only in launchers
that still honour them — adopting them in Classic would double-frame unmapped
icons in some launchers while doing nothing in others. The probe ran on 2026-09-19: community packs (e.g. Blackshield) ship
`iconback`/`iconmask`/`iconupon`/`scale` and list Projectivy among the launchers
that wrap unmapped apps, but nothing Projectivy-specific confirms legacy
compositing, and the reference Projectivy pack's own behaviour on unmapped
tiles is documented only as "stays generic". Adopting the tags on that would
be shipping a guess into every launcher at once, so the question stays queued
behind a device probe on the owner's TV: one unmapped app, one pack build with
the tags, one screenshot either way.

## 5. Couch FAQ — SHIPPED this round

`FaqActivity` behind a HELP row in Settings: four read-only cards, chrome
grammar copied from Settings (pinned header, one ScrollView, visible scrollbar),
cards deliberately not focusable so the D-pad never lands on a paragraph. One
correction to the proposal: `docs/DEVICE_FINDINGS.md` is wordmark evidence, not
troubleshooting. The card bodies paraphrase the documents that actually hold
the knowledge — `WHY_PROJECTIVY_CANT_SEE_IT.md` (stale cards, force stop),
Projectivy manual-override behaviour, `MONET_LAUNCHER.md` (what Send to Monet
can and cannot take), and `ADB_SCANNING.md` (unmapped components).

## 6. Icon Inspector + PNG export — SHIPPED as a screen, not a dialog

The pieces exist: `WallpaperExporter` already inserts into MediaStore under
scoped storage with `WRITE_EXTERNAL_STORAGE` capped at maxSdk 28, so icon export
needs no new permission; the icon's name, category, drawable and components are
one catalog lookup away. A tile press outside pick mode now opens `InspectorActivity` instead of the
two-second toast: the bundled mark at full size, category and drawable, and
every component `appfilter.xml` maps to it, read from the asset so the list is
what the launcher will match. **Export PNG** copies the bundled 512px bytes to
`Pictures/CoreBuilds/Icons/` through `IconExporter` (no re-encode, same
permission contract as wallpaper export, own subfolder so icons and wallpapers
do not share a rotation source). **Launch** needed no new visibility decision
after all: the auditor's intent-filter `<queries>` already made every
launchable package visible, so `isInstalled` + `getLaunchIntentForPackage`
answer honestly, and a no is a named toast rather than an
ActivityNotFoundException. It shipped as an activity rather than the proposed
dialog because every other information screen in the app is one, and the
chrome grammar (header back, two action stops) is what the gates already
know how to hold.

## 7. Suite hub — SHIPPED within what the registry knows

`suite.json` already carries every fact the hub would show: applicationIds,
versions, and Downloader codes (Shift `8829421`, Line `7375676`, Doctor
`8664938`, Icon Pack `5270601` — the proposal's two codes check out). The hub shipped around the gap rather than waiting on it:
`check_suite_truth.py --write` stamps `res/values/suite_hub.xml` from
suite.json (names, package ids, codes; CI fails on drift), and a code the
registry still holds as `[USER TO SUPPLY]` arrives as an empty item and renders
as "Not on Downloader yet — grab the APK from the GitHub release" instead of a
blank or a placeholder. Installed state and version come from `getPackageInfo`
through six targeted `<queries>` package entries (the same Play-safe grammar),
and a row press launches the companion when it is there. The one-click
"open Downloader to this code" intent stays queued: it needs the Downloader
app's package and extra grammar verified against the real app, and guessing
either would produce a button that opens the wrong thing on a TV.

## 8. D-pad fast navigation — badges SHIPPED, bumper skip SHIPPED

Category chips now carry counts tallied from the same generated arrays that
feed the grid (`All (943) · Brandmarks (415) · Streaming (46) · …`), so a chip
can never advertise a number the grid cannot back up. The collision risk with launchers that reserve the channel keys is handled
by scope instead of a behaviour table: the skips fire only while the grid
itself holds focus, so every other surface of the app — and every other app on
the box — keeps the keys' default meaning. The anchor is the first visible row
(scrollbar semantics), a backward skip lands on the first row of the previous
letter group so both directions arrive where a group starts, and the jump is a
`scrollToPositionWithOffset` plus a focus handoff, never a 900-tile animation.

## Receipts

- Shipped: settings LAUNCHER + HELP rows, `FaqActivity`, update-bar highlights,
  chip counts; then the auditor round — `AuditorActivity`, the generated
  deep-link resource, the vendored Nayuki encoder; all mirrored into Pop
  (shared Kotlin, own resources, `build_pop` regenerates the mirror) and into
  Pixel Neon's parity surface (layout files + strings).
- Gates: `tests/test_ui_wiring.py` grew a sideload-rows block (row wiring in
  both modules' layouts, manifest registration in both manifests, FAQ's single
  focusable, highlights gated on a non-empty parse, chip counts);
  `tools/check_ui_resources.py`, `tests/test_resource_parity.py`,
  `tests/test_tv_layout_fit.py`, `tests/test_search_focus.py`, `tests/test_pop.py`
  all green after `tools/build_pop.py` regeneration.
- Release-side highlights shipped too: `tools/prepare_release.py` stamps a
  `highlights` array into `Latestrelease/version.json` from the `[Unreleased]`
  bold leads (Added, then Changed, then Fixed; capped at the eight
  `UpdateChecker` renders), so the what's-new card cannot go stale by hand.
- Visual: `tools/build_app_ui_mockups.py` renders the whole set as 1920×1080 TV
  frames from the source they depict — `docs/app-ui-catalogue.png` and
  `app-ui-catalogue-update.png` (home grid, chips + search, update bar with the
  manifest's real highlights), `app-ui-wallpapers.png`, `app-ui-settings.png`,
  `app-ui-faq.png`, `app-ui-auditor.png` + `app-ui-auditor-qr.png` (audit list
  and a real scannable QR of the generated deep link), `app-ui-inspector.png`
  and `app-ui-suite.png`. Every label comes from `strings.xml`, every metric
  from `dimens.xml`, the artwork is the bundled PNGs; each frame's caption band
  names which values are example data. `--check` is wired into build.yml, so
  the hand-drawn sheets' failure mode — drifting off the published app until
  nobody trusts them — is now a CI failure instead.
