# Update & release infrastructure — backend/frontend roadmap, September 2026

Status: **roadmap**. Phases 1–3 are deliberately not built yet; this document
pins what exists today, what each phase adds, and the order that keeps every
step independently shippable. Written alongside v1.8.17.

## What exists today (v1.8.17 baseline)

- **Distribution:** GitHub Releases double as the CDN. Tagged `v*` runs of
  `build.yml` publish `iconpack-release.apk` (+ the `app-release.apk`
  compatibility name for Downloader code 5270601) to the versioned release
  and move the floating `iconpack` tag, which the stable Downloader URL
  `…/releases/download/iconpack/iconpack-release.apk` follows.
- **Discovery:** `Latestrelease/version.json` on `main` is what the in-app
  updater polls (`versionCode`, `versionName`, `iconCount`, `apkUrl`). The
  tag build's version-sync step commits it back when it lags.
- **Integrity:** `apkSha256` is intentionally ABSENT from `version.json`.
  A pre-build SHA can never match the signed APK (signing happens after the
  manifest is written), so the updater treats a blank SHA as
  skip-SHA-verification and leans on the hard checks that already exist:
  download size bound, SHA-256 of the file when a value IS present, and —
  non-skippable — the installed app's signing certificate must match the
  APK's (`GET_SIGNING_CERTIFICATES`) before `UpdateInstaller` will install.

That last point is the anchor of everything below: signature parity is
already enforced on-device; the roadmap adds machine-checkable integrity
around the release pipeline itself, without ever requiring a hand-maintained
pre-build hash.

## Phase 1 — sidecar checksums + updater jitter

**Problem.** Nothing today lets a user (or a script) verify a downloaded APK
against the release without trusting TLS alone. And every installed pack
polls `version.json` on its own rhythm — a release moment becomes a thundering
herd on one JSON file and one CDN object.

**Work.**
1. The tag build computes `sha256sum` of each published APK and uploads a
   sidecar asset per APK (`iconpack-release.apk.sha256`, same release).
   Sidecars are generated post-sign, so they can never disagree with the
   artifact the way a pre-build `apkSha256` would.
2. `version.json` gains optional `sha256Url` (never an inline hash). The
   updater fetches the sidecar lazily and verifies the download against it —
   still optional, still signature-checked regardless.
3. Update checks gain jitter: a per-install stable offset plus random backoff
   around the poll window, so a release spreads its fetches over hours
   instead of minutes. No new server needed; the manifest contract is
   unchanged for clients that do not understand the new fields.

**Done when:** a release publishes sidecars automatically, an older client
ignores the new fields safely, and release-moment request volume is flat
(measurable via GitHub release asset stats).

## Phase 2 — build provenance + cosign bundles

**Problem.** Phase 1 proves the file you fetched is the file the release
points at. It does not prove WHERE the file was built. A compromised tag or
re-run could publish a different APK with matching sidecars.

**Work.**
1. CI runs `attest-build-provenance` on the signed APK: a signed SLSA
   attestation naming the workflow, ref and source digest that produced it.
2. `cosign sign-blob` in keyless (OIDC) mode produces a `.sig` + certificate
   bundle alongside each APK; verification needs no distributed key, only the
   workflow identity (`brevityA/CoreBuildsApps/.github/workflows/build.yml@refs/tags/v*`).
3. A verify recipe (one `cosign verify-blob` command + the provenance
   predicate) lands in PUBLISHING.md so anyone can check a download
   out-of-band.

**Ordering:** Phase 2 depends on Phase 1's sidecar step existing (same post-
sign slot in the workflow), and stays compatible with the updater's
signature-parity anchor — attestation is additive evidence, not a new
install gate.

**Done when:** every v-tagged release carries attestation + cosign bundles,
and the verify recipe passes from a clean machine.

## Phase 3 — re-download verification, edge manifest, staged rollouts

**Problem.** Today the pipeline proves what it PUBLISHED. It does not prove
what users RECEIVED, the manifest lives in one branch of one repo, and a bad
release reaches 100% of updaters instantly.

**Work.**
1. **Post-publish re-download verify:** after the release goes live, CI
   re-downloads each asset from the public CDN URL, re-checks the sidecar
   SHA and the signature, and fails loudly on mismatch. Closes the loop
   between "we published X" and "the world can fetch X".
2. **Edge manifest + generated site:** generate a static update manifest
   (version, URLs, sidecars, attestation links) to Pages/edge storage so
   polling no longer depends on raw.githubusercontent branch state; the
   repo keeps owning the truth, the edge just serves a built copy.
3. **Staged rollouts:** the manifest gains a rollout percentage; the updater
   hashes its install id into the window and only sees the update when its
   cohort is served. Bad build → halt the ramp, no recall needed.

**Done when:** a release is verifiable end-to-end from a cold client, and a
rollout can be paused at the manifest without re-tagging.

## Non-goals

- No inline pre-build `apkSha256` in `version.json`, ever — the signed APK
  cannot be hashed before it exists; sidecars own that job post-sign.
- No self-hosted update server for phases 1–2; static assets on the existing
  release/edge channels until phase 3's manifest genuinely needs one.
- No change to the install gate: signing-certificate parity stays the
  non-negotiable check on the device.
