# Icon tranche implementation — 2026-09-16

**Scope:** the eight redraw-ready candidates from
[`icon-tranche-research-2026-09-15.md`](icon-tranche-research-2026-09-15.md)
(ranks 12, 13, 14, 15, 17, 18, 20, 24).
**Branch:** `claude/new-session-9upf9g`, cut from `main` at `de7861a9`, which
already carries the merged `arena/01a0a36a-corebuildsapps` research.
**Review sheet:** [`icon-tranche-review-2026-09-16.png`](icon-tranche-review-2026-09-16.png)

**This report does not authorize v1.8.20, a release, production signing, or
any Android TV approval.** Production metadata is untouched at
`versionName 1.8.19` / `versionCode 29`.

---

## 1. What shipped

Eight generic constructions became bespoke marks. Every mark is original
geometry drawn from the *recorded identifying cue* in the research, in Core
Builds rounded monoline. No source artwork was traced, and no solid fill,
container, or effect was introduced.

| App | Was | Now | Cue implemented |
|---|---|---|---|
| Crossy Road | `gaming_C` | `crossy_chicken` | pixel fowl, head in profile: stepped comb, open beak, eye |
| Blokada (v4, `org.blokada.fyra`) | `vpn_B` | `blokada_shield` | shield face split by three descending diagonal bands |
| Private Internet Access | `vpn_P` | `pia_robot` | robot/lock head: flat top, stub antenna, wide-set eyes, mouth slot |
| mpv | `app_M` | `mpv_play` | stepped circular play mechanism |
| Audiomack | `music_A` | `audiomack_wave` | rising asymmetric waveform, leading dots, off-centre dominant stroke |
| ATRESplayer | `broadcast_A` | `atres_chevrons` | two nested angular right-facing chevrons |
| Kinopoisk | `broadcast_K` | `kinopoisk_k` | upright `K` whose arms run long into an asymmetric ray fan |
| AIDA64 | `tool_A` | `aida_sixty_four` | interlocked `64`, diagonal upper stroke, cut counters |

**Mappings were not touched.** The catalog diff is exactly eight lines, all
`"glyph":`. A before/after comparison of every entry's `components` list was
asserted equal in the same script that wrote the change.

```
tools/catalog.json | 16 ++++++++--------
1 file changed, 8 insertions(+), 8 deletions(-)
```

**Eight generic constructions were converted.** That is eight of the
twenty-two the research says are needed to pass below 55%, so the milestone
is *not* reached and no count was padded to pretend otherwise.

A restated percentage is deliberately omitted. The research's 538/940
(57.23%) baseline was produced by a counting method that is not committed as
a script, and a naive glyph-prefix count over the same catalog returns 553
before this change rather than 538 — a 15-entry disagreement about what
counts as generic. Publishing a new percentage from the broader method would
invite a false comparison against the research's figure. Re-run the original
baseline method to restate it.

## 2. Two marks were reworked before review

Both failures were found by looking at the rendered sheet, not by reading the
source.

**Crossy Road**, first pass: the whole bird drawn as stepped orthogonal
segments, with lane stripes beneath. At 96px it read as an abstract blocky
figure rather than a chicken, and the free-floating stripes read as three
unrelated marks. Replaced with a head in profile, which is what survives at
tile size. A second pass was then needed because the r=15 eye and the boxed
beak both closed into blobs by 48px: the eye was enlarged and the beak
redrawn as an open chevron, which has no counter to lose.

**Kinopoisk**, first pass: rays drawn as separate ticks floating off the
arms. They read as noise at 48px. The arms now simply run long, so the fan is
continuous with the letter — a K *with* rays instead of a K *beside* some
marks.

## 3. Two more were corrected after external review

A reviewer checked the shipped geometry against the research cues and found
two that did not match. Both findings were correct.

**Blokada** was drawn with three *horizontal* bands. The research records
"three descending **diagonal** bands" (sections 4 and 5), and the glyph's own
docstring already claimed "diagonal bands stepping down to the right" — so the
code contradicted its own documentation, and a horizontal split reads as a
generic striped shield rather than as Blokada. The bands now descend
left-to-right on a common slope. Measured side effect: its nearest registry
neighbour moved from `shield_key` at 0.908 to `nfl_ball` at 0.889, so the
correction made the mark *more* distinct, not less.

**Audiomack** was drawn as bars on a baseline, evenly spaced 48px apart.
That is an equaliser — the generic construction already in the music family,
and precisely what this redraw exists to replace. The original docstring
claimed an off-centre tall bar prevented that; it did not. It is now an
actual traced waveform: one polyline whose oscillations start small and grow,
spike to a sharp peak, and fall through the dominant downstroke before
settling, with leading dots and a terminal pulse. Spacing is deliberately
unequal so no reading recovers a bar chart.

## 4. Objective identity testing

New: `tools/test_glyph_identity.py`. It answers three questions a 512px
master cannot.

- **Reduction.** Counters counted at 96px (about what a Projectivy tile
  occupies at 1080p) and 48px.
- **Safe area.** The raster presence pass dilates every edge, so ink flush in
  the vector lands outside SAFE in the shipped PNG.
- **Collision.** Every checked glyph is compared against all 962 registered
  glyphs by downsampled-alpha cosine similarity. A new mark that renders
  nearly identically to an existing one adds a file, not an identity.

It reuses the reduction standard the research already fixed in section 5 —
160/80/48px with an 8px ink-height floor at 48 — rather than inventing a
second, competing standard.

| glyph | counters 256→96→48 | ink @48 | bounds 160/80/48 | nearest neighbour |
|---|---|---:|---|---|
| `crossy_chicken` | 3 → 3 → 2 | 13.5% | 85x93 / 42x47 / 26x28 | `monogram_0` 0.848 |
| `blokada_shield` | 1 → 1 → 3 | 18.8% | 102x115 / 52x57 / 30x34 | `nfl_ball` 0.889 |
| `pia_robot` | 3 → 3 → 3 | 18.2% | 102x112 / 52x56 / 30x33 | `screen_record_mark` 0.850 |
| `mpv_play` | 2 → 2 → 2 | 21.1% | 122x122 / 60x60 / 36x36 | `play_round` 0.945 |
| `audiomack_wave` | 0 → 0 → 0 | 11.2% | 106x92 / 53x46 / 32x28 | `alldebrid_infinity` 0.711 |
| `atres_chevrons` | 0 → 0 → 0 | 8.8% | 73x96 / 37x48 / 22x28 | `monogram_X` 0.718 |
| `kinopoisk_k` | 0 → 0 → 0 | 13.3% | 86x109 / 43x55 / 26x33 | `monogram_K` 0.770 |
| `aida_sixty_four` | 1 → 1 → 1 | 15.2% | 109x97 / 55x48 / 33x29 | `browser_globe2` 0.729 |

All eight clear the 8px floor by a wide margin (28–36px of ink height at
48px). No collision reaches the 0.965 near-twin threshold; `mpv_play` against
`play_round` at 0.945 is the closest and is worth a human eye on device,
since both are ring-plus-triangle constructions.

**One recorded exception.** `crossy_chicken` holds all three counters at
96px — the size hardware actually shows — and loses the comb notch only at
48px, which is deliberately twice as strict. It is listed in `ACCEPTED_48PX`
in the test with its reason, so the judgement is visible and reviewable
rather than hidden behind a loosened global threshold. This follows the
existing precedent for `gaming_A`, `photos_R` and `photos_X`. A glyph that
fails at 96px is never eligible for that list.

## 5. Artemis: the requested change was already in place

The instruction was to add `com.limelight.noir/com.limelight.PcView` to the
existing Moonlight entry. **That component is already mapped**, to a separate
entry:

| name | drawable | glyph | components |
|---|---|---|---|
| Moonlight | `moonlight` | `gamepad` | `com.limelight/.PcView` |
| Moonlight Noir | `limelight` | `broadcast_M` | `com.limelight.noir/com.limelight.PcView` |

`tools/validate.py` rejects duplicate components (line 109), so the component
cannot also be added to the Moonlight entry — validation would fail.

The stated purpose is satisfied as things stand: **no invented Artemis glyph
exists.** `broadcast_M` is a shared generic monogram container, not a bespoke
Artemis mark, and the evidence-backed component is mapped and shipping.

The only remaining question is cosmetic and is the owner's call: whether
Artemis should display Moonlight's `gamepad` artwork instead of the generic
`M`. That is a one-line glyph change to the Moonlight Noir entry. **It was
not made**, because it changes the appearance of an already-shipping icon and
was not what the instruction asked for. No mapping was altered.

## 6. Regeneration and validation

All three lanes were rebuilt from the changed generator, and source plus
artwork are committed together — the icon and pixel-neon drift checks compare
shipped assets to the generator, so a source-only commit turns them red.

```
build_icons          940 icons, 1150 components, 940 PNGs
build_banners        940 at 16:9
build_branding       10 files
build_brand_preview  docs/brand-preview.png (1200x940)
build_icon_review    docs/icon-fidelity-preview.png (1280x1510)
measure_pop_glyphs   962 glyphs -> tools/pop_glyph_metrics.json
build_pop            940 icons, 1906 PNGs, 16 swatches
build_pixel_neon     940 sprites, 1757 appfilter entries
```

```
validate.py              940 icons · 1756 components · 25750 checks · PASS
validate_pop.py          940 icons · 1150 components · 16 swatches · 14695 checks
validate_pixel_neon.py   940 icons · 1150 components · 13057 checks
check_suite_truth.py     passed
audit_contract.py        passed (incl. check_secrets_absent)
check_ui_resources.py    OK across app, pop, pixel-neon
build_issue_prefills.py --check   stamp matches 2 issue forms
git diff --check         clean

test_icon_identity       49 tests OK
test_pop                 29 tests OK
test_v151_robustness     27 tests OK
test_wallpapers          24 tests OK
test_wallpaper_export    32 tests OK
test_presence             6 tests OK
test_resource_parity     OK
test_glyph_identity      8 glyphs, 0 problems
```

`pytest` is not installed in this environment; each suite was run directly as
a module, which is how they are written to run.

## 7. Candidate APK lane: it does not exist yet

The instruction was to *verify* the candidate build variant. Verification
result: **there is no candidate variant to verify.** Reporting rather than
inventing it, because the missing pieces touch signing and release
publication.

| Asked | Found in `app/build.gradle.kts` / `.github/workflows/build.yml` |
|---|---|
| candidate build variant | **absent** — only `release` and implicit `debug`; no product flavors |
| test package ID | **absent** — no `applicationIdSuffix`, so a non-production build installs *over* production as `tv.corebuilds.iconpack` |
| candidate label | **absent** — no `resValue`/manifest placeholder distinguishing it |
| source hash in version metadata | **absent** — the only `buildConfigField`s are `UPDATE_AUTHORITY` and `UPDATE_MANIFEST_URL` |
| production signing never used | the only release path decodes `KEYSTORE_BASE64` and runs `assembleRelease`; a candidate lane must not reuse that job |
| checksum + draft-release attachment | **absent** |

**No APK was built.** There is no Android SDK in this environment, so any
claim of a local build would be false. Building it requires CI.

Creating this lane means a new build type, an application-ID suffix, a label,
a source-hash `BuildConfig` field, and a separate workflow that never reads
the keystore secret and attaches only to an unpublished draft release. That
is new release-adjacent infrastructure touching signing and publication, so
it is left for explicit approval rather than added here.

### Candidate APK verification checklist (for when the lane exists)

- [ ] Variant is its own build type, not `release` with flags
- [ ] `applicationIdSuffix` set (e.g. `.candidate`) — installs alongside, never over, production
- [ ] App label visibly marks it as a candidate on the launcher row
- [ ] `versionName` carries a candidate marker and is **not** `1.8.20`
- [ ] A source/commit hash is exposed in `BuildConfig` and visible in-app
- [ ] The workflow does **not** reference `KEYSTORE_BASE64`, `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` or `KEY_PASSWORD`
- [ ] Output APK reports as debug-signed under `apksigner verify --print-certs`
- [ ] SHA-256 of the APK is printed in the job log and recorded in the draft
- [ ] Artefact attached only to an **unpublished draft** release
- [ ] `Latestrelease/version.json` is untouched, so the in-app updater never sees it

## 8. Device-testing checklist (owner)

None of this can be settled from a build machine.

- [ ] Install on the target Android TV and confirm all eight appear in Projectivy
- [ ] At viewing distance, confirm each is distinguishable from its neighbours
- [ ] **`mpv_play` vs the pack's other ring/triangle marks** — closest measured neighbour at 0.945
- [ ] **`crossy_chicken`** — confirm it reads as a bird at real tile size; this is the weakest of the eight and the one with a recorded 48px exception
- [ ] **`blokada_shield` vs `shield_key`** and the rest of the `vpn` family — 0.908
- [ ] **`pia_robot`** — confirm the mouth slot and eyes stay separate
- [ ] Confirm Blokada resolves against `org.blokada.fyra` (legacy v4), not a newer Blokada package
- [ ] Confirm Artemis (`com.limelight.noir`) resolves and decide the `gamepad`-vs-`M` question above

## 9. Release blockers — unchanged and owner-controlled

- Euronews and HGTV remain **testing-gated**; no real-TV results exist
- ARTE and F-Droid remain **rights-gated**
- ITVX, Bloomberg, RUTUBE and TIMVISION remain **source/activity-gated**; no mapping was invented for any of them
- FIFA+ still requires rights review
- Production version bump to v1.8.20: **not done**, owner's decision
- Release publication, tagging and production signing: **not done**, owner's decision
- The 48–72 hour RC soak: **not started**
- Android TV success: **unverified**, no device evidence exists
