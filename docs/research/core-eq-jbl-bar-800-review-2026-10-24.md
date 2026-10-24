# Core EQ review: JBL Bar 800 screenshot and open EQ PRs (2026-10-24)

## Scope and evidence

Reviewed the checked-out `main` snapshot (commit `e3d5a35`), Core EQ's current
source/tests/docs, and open GitHub PRs whose title or branch identifies Core EQ.
The supplied image is a JBL Bar 800 device page with an **Edit Equalizer**
seven-slider panel. It is a UI screenshot, not a measurement, and the displayed
slider values have no numeric labels. The curve can only be read approximately
by eye; it must not be copied into a supposedly calibrated preset.

This is an engineering review, not a claim that the soundbar or TV has been
physically tested. Core EQ's documented constraints remain important: an
Android effect is not guaranteed to process every app or HDMI bitstream, and
room correction is distinct from a user-selected tone overlay.

## Open Core EQ PRs

### #304 — phone discovery, target import, multi-seat averaging, correction check

[PR #304](https://github.com/brevityA/CoreBuildsApps/pull/304) is the substantive
open Core EQ feature PR. Its latest PR metadata reported successful build,
unit-test, contract, candidate-APK, API 30/API 37 install, and Core EQ CI checks.
The change is substantial and addresses real gaps: pairing/reconnection,
imported targets, spatial averaging, and a post-correction check. It is not a
JBL-specific change. The branch is not part of this checkout, so this review
cannot verify its working tree or reproduce those tests locally.

**Review/follow-up gates before merge or release:**

1. **Correction check validity:** verify on each supported TV/audio route that
   the check sweep is actually emitted through the same processing path and
   level as ordinary content, and that effect suspension/resumption is correct
   across cancel, failure, activity teardown and service restart. Unit tests
   establish math, not the physical signal route. Preserve the UI's distinction
   between a measured residual and proof that all third-party playback is
   corrected.
2. **Seat averaging safety:** the PR's energy-domain averaging and
   narrowest-bandwidth/clean-in-all-seats restrictions are sound safeguards.
   Validate route identity and room/output changes on hardware, plus the
   outlier path at two vs three seats. Do not promote a three-seat average as
   universally optimal; seats must represent the listening area.
3. **Target-import ownership:** the PR describes storing a custom target with
   each profile, avoiding later imports changing old profiles. Keep validating
   malformed/oversized files, restart/backup/restore, and legacy profile
   behavior. A target curve is a target, not microphone calibration.
4. **Discovery and pairing:** mDNS is best-effort on networks that allow
   multicast; fixed port fallback, QR address fallback, and token-authenticated
   reconnect are helpful but need router/VLAN/client-isolation testing. Never
   log or advertise pairing secrets. A stale saved endpoint must not replace a
   valid pairing identity.
5. **UI/device qualification:** CI install checks do not prove usability on a
   TV remote, Android 17 edge-to-edge phone, or real HDMI/eARC route. Verify
   focus traversal and narrow-screen wrapping, interrupt/cancel behavior, and
   permission denial.
6. **Merge hygiene:** the PR body says changes remain Unreleased and version
   stays 1.5.2 / code 13. Keep that invariant until an intentional release;
   verify `coreeq/CHANGELOG.md`, generated suite contracts and release APK
   signing after merge.

These are physical/device qualification items, not evidence of a failing PR.
No blocker is inferred solely from the PR description or green CI.

### #307 — org.json dependency update

[PR #307](https://github.com/brevityA/CoreBuildsApps/pull/307) updates the
Core EQ `org.json` dependency. It is an isolated dependency bump, not a feature
or fix to the JBL controls. Review should confirm the dependency's published
coordinates/version and lock/verification policy, then run Core EQ unit tests
and release-contract checks. The current checkout predates it, so its resulting
build is not assessed here.

## What the screenshot tells us (and does not)

- The JBL control is a seven-band graphic EQ with nominal bands `≤125`, `250`,
  `500`, `1k`, `2k`, `4k`, `≥8k`, plus, zero and minus reference cues, a clear
  confirmation action, and a reset affordance.
- The curve appears to cut bass below 125 Hz, slightly cut 250 Hz, lift 500 Hz
  and 1 kHz (largest visible lift), lift 2 kHz modestly, and leave 4/8 kHz near
  zero. **No dB values are shown.** The screenshot cannot establish filter Q,
  exact gain, acoustic response, room response or a universal JBL tuning.
- The app's seven controls are not Core EQ's ten fixed bands (`40` through
  `8,000 Hz`). Their edge bands are shelves/ranges, not necessarily narrow
  peaking filters. Mapping them one-for-one to Core EQ's bell filters would
  change the response and should not be represented as exact parity.
- The screenshot's darkened background is a modal scrim; the editor itself is
  the focus. Useful interaction ideas: clear save/confirm, reset with
  confirmation/undo, obvious zero line and endpoints, and a compact touch
  layout. Core EQ currently targets Android TV/D-pad and deliberately has a
  richer ten-band editor plus headroom/status. A phone-style seven-slider
  screen is not a drop-in replacement for the TV UI.

## Existing JBL Bar 800 code: fixes and changes worth making

`HardwarePresets.kt` already has four JBL Bar 800 starting presets (Movie,
Dialogue, Music, Late night), conservative whole-model-number detection, and a
warning that they are not measured. The preset values are heuristics rather
than response data. The current comments/basis cite general specification and
review character, but do not provide source URLs or a per-claim evidence trail.

Recommended changes, in priority order:

1. **Keep the device preset opt-in.** Name-based detection may reorder the
   preset picker; it must not auto-apply an EQ when a route is detected. A
   soundbar can already run its own JBL EQ, bass level, Smart Mode, or room
   processing. Stacking unknown processing can overcorrect.
2. **Source every model claim.** Record exact Bar 800 generation/market SKU,
   manual and product-spec URLs, access date, and distinguish manufacturer's
   claimed bandwidth from measured response. Separate observed owner/reviewer
   impressions from measured facts. Remove or soften unsupported statements
   such as a model being muddy/thin unless linked to a reproducible source.
3. **Do not present heuristic presets as calibrated.** Give each preset a
   concise disclosure and a flat/disable route. Recommend measuring the actual
   output and saving a profile rather than asserting an ideal sound for all
   rooms/seats/firmware modes.
4. **Offer the screenshot-shaped curve only as a manual starting point if
   requested.** Its values cannot be reconstructed exactly. A proper import
   should accept user-entered seven-band numeric gains, map JBL edge bands with
   documented filter semantics, preview the fitted curve/error, and require
   confirmation; it should not invent values from pixels.
5. **Detect conservatively and test real Android route names.** Existing
   boundaries avoid matching `Bar 8000`, but verify JBL model names, suffixes,
   Bluetooth names and generic `HDMI` routes. Do not infer the connected
   soundbar merely from the TV manufacturer/model. Detection should remain
   advisory because TVs often expose only a generic output name.
6. **Explain processing ownership.** Add help that JBL's own sound modes and
   EQ are outside Core EQ's control; avoid claiming effects are global or that
   Dolby Atmos is changed by these tone filters. A stereo/PCM signal may behave
   differently from Dolby bitstream passthrough.
7. **Test preset lifecycle and response math.** Preserve independent profile
   and mode overlays, validate no unintended preset application on route
   changes, and test gain/headroom after combining the base correction and
   manual tone. Check the fitted ten-band response rather than just filter-list
   equality.

## Action taken / no speculative preset

No new numeric JBL preset is proposed from this image: absent gain labels and
filter definitions make exact transcription impossible. The safest change is
better provenance and clear non-measured labeling for the existing model
presets, followed by device tests. The screenshot is a useful reference for a
future compact companion/mobile editor, but cannot by itself justify a DSP
change or measured-response claim.

## Source/code locations inspected

- `coreeq/app/src/main/kotlin/tv/corebuilds/eq/dsp/HardwarePresets.kt`
- `coreeq/app/src/main/kotlin/tv/corebuilds/eq/ManualEqActivity.kt`
- `coreeq/app/src/main/kotlin/tv/corebuilds/eq/ui/ManualEqBandsView.kt`
- `coreeq/app/src/main/res/layout/activity_manual_eq.xml`
- `docs/apps/core-eq.md`
- Open PRs #304 and #307 (GitHub PR metadata at review time)
