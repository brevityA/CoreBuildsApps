# Changelog

All notable changes to the Core Builds Icon Pack. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

**Monet Launcher never reads the system wallpaper, so "Set wallpaper" was a
no-op on every Monet home screen.**

Found by decompiling Monet v1.0.84 (versionCode 118) rather than reading its
Play listing: the APK contains no `WallpaperManager` reference at all. What it
does contain, since v1.0.72, is an exported `WallpaperShareActivity` that takes
`ACTION_SEND` / `ACTION_SEND_MULTIPLE` `image/*` and copies the file into
Monet's own background library. Research and raw probe output are in
`docs/MONET_LAUNCHER.md` and `docs/research/monet-probe/`.

### Added
- **Send to Monet.** When Monet is the HOME launcher and exposes the share
  target, the preview's primary button reads *Send to Monet* and hands the
  cached download straight to Monet — no `SET_WALLPAPER`, no storage
  permission, no export first. Monet toasts the outcome itself, including its
  Premium notice on the free tier.
- **Send N to Monet** on the export result screen: one `ACTION_SEND_MULTIPLE`
  puts the whole selection into Monet → Settings → Background → Gallery.
- FileProvider now exports `cache/wallpapers/` (read-only, per-URI grant)
  alongside `cache/updates/`, in all three packs.
- `tests/test_monet_handoff.py` pins the share contract, the provider path,
  the Pixel Neon mirror and the Pop mirror.

### Fixed
- Monet manual path said *Settings → Icons → Icon pack*; Monet 1.0.80 moved it
  to *Settings → Apps → Icon pack*. The "Saved" fallback hint likewise named a
  *Wallpaper → Your own images* screen that no longer exists.
- `docs/PLUGIN_RESEARCH.md` §2 claimed Monet re-themes from the system
  wallpaper; marked superseded with the dex evidence.

### Unchanged, on purpose
- Icon apply to Monet stays **Manual**. v1.0.84 has no inbound apply intent
  and its settings activity is not exported; the pack is discovered through
  the standard actions already in the manifest and picked inside Monet.

## [1.8.14] — 2026-09-12

**Two thirds of the pack was a letter in a box, 429 icons shipped the same
picture as another icon, and thirty marks collapsed at tile size.**

Found by measuring the shipped pack rather than reading it: hashing all 926
drawables returned only 655 distinct images. 608 icons fell back to a
`tile_*` monogram and accents come from a cycled palette of 49, so a shared
letter plus a shared palette slot produced a byte-identical PNG. One image
served seven apps — Plus Messenger, Tele Quebec, Telly, Torrent Search,
TV 2 Play, TV Manager and Twilight were the same file.

### Added
- **56 new marks covering 58 apps** — debrid clients, players, launchers,
  browsers, file and network tools: the categories a Core Builds user
  actually has installed. Utilities are drawn by what they do, because
  nobody recognises "Dr Nettools" by its logo but a network tool reads as a
  node graph with a pulse. Where a brand owns a device, the device wins:
  Arrow's arrow, Streamyfin's fin, Weyd's compass.
- All 58 are stroke-only and opt into `style: core_monoline`, so the strict
  gate now covers **86 icons instead of 28**.

### Fixed
- **74 monograms showed a letter the app name does not contain** — Corridor
  showed W, Crave showed B, Channels showed G; 55 showed a letter absent
  from the name entirely. 72 are repointed at the app's first alphanumeric.
  Seven Plus keeps its 7 (it is the network's 7plus) and Daijishou takes the
  gamepad, both deliberate.
- **30 marks lost their structure at a 48px Projectivy tile.** MUBI lost all
  seven counters and read as a solid rectangle; France 24 lost twelve of
  sixteen; DS file sat at 53% ink. 36 glyph shapes are redrawn to a 32px
  minimum counter on the 512 grid. The recurring cause was a container or a
  detail tracing another contour a stroke's width away, so the fix is almost
  always fewer elements rather than thinner ones.
- Two of those needed the raster pipeline, not the vector: `presence.py`
  dilates every edge by the keyline radius on the shipped PNG, so MUBI's
  rings merged into one component even with the vector counters open.
  Spacing moved 136 to 146 and the dots are seven shapes again.
- Accents are spread within each glyph group so no two apps on one mark
  share a colour. Only the icons whose `color_source` reads "core-builds
  palette (no published brand colour found)" are moved — a citable brand
  colour is never touched, which is why BBC iPlayer, Paramount+ and Syncler
  still match across their duplicate catalog entries.

### Numbers
```
byte-identical icons      429 -> 34
distinct artworks         655 -> 902
letter tiles              608 -> 549
glyph shapes              274 -> 330
icons under core_monoline  28 -> 86
worst mark at tile size   53% ink -> 30%
glyphs losing counters     30 -> 0
```

### Known
549 icons (59%) are still letter tiles, and 455 of those sit in the generic
`APP` category — regional broadcasters and niche utilities. That tail is a
programme, not a sprint, and is not addressed here. 436 colours remain
flagged `unverified` against a real brand source.

## [1.8.13] — 2026-09-11

**A safe-area constant that nothing enforced, a module that stopped
compiling, the pack's own banner with its last letter cut off, and marks
that now hold against a bright wallpaper.**

1.8.12 was prepared but never published — its tag was pushed at a commit
that still read versionName 1.8.11, and `build.yml` refused it before
building anything. Its notes are folded in here rather than left pointing
at a release that does not exist.

**A safe-area constant that nothing enforced, a module that stopped
compiling, and the pack's own banner with its last letter cut off.**

### Fixed
- **Seven glyphs put ink outside the safe area.** `SAFE = 432` had been a
  constant in `glyphs.py` that nothing checked. The glyphs place coordinates,
  not ink, so a path drawn to the safe edge still hangs its stroke half-width
  past it. RetroArch was drawn to `x=496`; the 26.2 monoline put ink at
  **509.1 on a 512 grid** — 3px of margin where SAFE promises 40. Two separate
  faults: four marks genuinely oversized, three the right size but drawn
  off-centre (Acorn sat 39px high, Red Bull was built about `cy=240`). Fixed in
  the geometry, not with a render-time transform, so no stroke weight changed
  and the monoline reads the same. **8 Classic icons and 11 Pop assets** move.
- **Pixel Neon did not compile.** 1.8.11's wallpaper seed palette drew its
  swatch borders with `R.color.cb_hairline` and declared the colour in `app/`
  and `pop/` only. Pixel Neon forks that Kotlin under its own package, so the
  module broke and every pull request opened afterwards was red on a failure
  unrelated to itself.
- **46 Pixel Neon icons had drifted from the catalog.** 94 files no longer
  matched what the generator produces. The sprite seed includes the icon's
  *positional index*, so inserting one catalog entry silently re-rolls every
  sprite after it — splitting WeatherBug out of Streamflix in 1.8.11 did
  exactly that. Regenerated here; the seed itself is tracked separately.
- **The pack's own Leanback banner clipped its last letter.** It shipped
  `for Projectivy · Android T` — 27 monospace characters from `x=164` ending at
  326.6 on a 320-wide canvas. A plain **TV-OV** failure on the one asset every
  Android TV home row shows for this app. The wordmark also named
  `Georgia,serif` and the strapline `ui-monospace` as `<text>`, so what shipped
  depended on the build host's fonts; Georgia is not licensed for
  redistribution either. Both are now outlined to paths from `tools/fonts`
  (SIL OFL), and an `assert` measures the lockup against the 5% overscan
  margin.

- **Three gates, because each of these was silent.** A test measures the rendered alpha of all 322 glyphs against SAFE. `pop_glyph_metrics.json` carries a `geometry_sha256` of the bodies it was measured from, so editing a glyph without re-running the measurement is caught rather than leaving Pop scaling a mark to an ink box it no longer has. `check_ui_resources.py` now pairs every module with the Kotlin it actually compiles — it had hardcoded one path, which is why it printed OK on a tree that could not build.

### Added
- **Prefilled icon-request issue links.** `tools/build_issue_prefills.py` reads
  `.github/ISSUE_TEMPLATE/*.yml` and writes the README's **Request an icon**
  block: both forms open on the right template with the right title prefix and
  label, and `--app`/`--component`/`--field` build a link for one specific app —
  the thing to paste into a reply. GitHub only fills `input`/`textarea`, so the
  generator refuses to prefill a dropdown or tick a confirm box, and `--check`
  (now in Suite CI) fails the block if a form or the stamp drifts.
- **Wallpaper preview shows the launcher seed palette.** Five chips under the
  title (seed, secondary, tertiary, container, on-seed) extracted from the
  image via `WallpaperColors` on API 27+ and a chromatic 32×32 sample below
  that. They preview what Monet / a tinting launcher will pick up after Set.
  Display-only — not focusable, not in the D-pad chain. Icon rasters are
  unchanged.


- **Six generic letter tiles became researched brand marks.** New monoline
  `aljazeera_flame`, `france24_mark`, `cbc_gem`, `cnbc_peacock` and
  `mgm_reel`; `sbsondemand` joins `sbs` on the five-splice globe. Tile share
  66.4% → 65.9% (615 → 610 of 926), bespoke marks 311 → 316, with a
  tile-share ceiling and bespoke floor added as tests so the number can only
  move the right way. Original linework throughout — no vendor silhouette or
  wordmark.

- **Marks that vanished on a bright wallpaper now hold.** A raster presence
  pass adds a night keyline and a short accent bloom *after* rasterisation,
  so the SVG masters stay style-AA monoline and the identity gates are
  untouched. Both layers are rings around existing ink, not dilations, so
  open interiors stay open — YouTube's play counter and MUBI's seven islands
  are unchanged. The ring is bounded by the safe-area margin actually
  available: 920 of 926 icons get a keyline, 183 at the full 11px, and six
  marks sitting flush at SAFE get none rather than ship outside it.

### Changed
- **Same-name marks that were two products, or one product with two tiles.**
  DIG is no longer labelled Daijishou. Jawwy TV and ERTFLIX package
  migrations share one glyph and accent. Wholphin leaves the Damonte D-tile
  for a whale-back construction. The Tanasi Streamflix fork is a flow/play
  mark, not a letter S. Yettel Selfcare is named apart from Yettel TV.

Coverage: **926 icons / 1101 catalog components / 58 classic wallpapers**.
versionName 1.8.13, versionCode 23.

## [1.8.11] — 2026-09-10

**The rest of the D-pad dead ends, two mis-mapped apps, and the checks that
should have caught both.**

### Fixed
- **Four more controls unreachable by D-pad.** 1.8.10 fixed the Wallpapers
  button by rewiring a single `nextFocus` edge; the same bug class survived in
  four more places, in all three packs. The update **Download/Install** button
  was reachable only via the one-shot focus grab when the update bar appeared
  — once focus moved, the remote could never return to it, so an available
  update could not be installed. The **"Also &lt;launcher&gt;"** row was never
  reachable at all when a second supported launcher was installed. On the
  wallpapers screen the on-screen **Back** button was unreachable, and
  **Select all / Clear / Export N** could not be returned to once focus left,
  which broke multi-select export outright. Focus chains now route *through*
  these views rather than around them, so a hidden control collapses to the
  next visible one instead of stranding whatever sits between.
- **Wallpaper chips lost their highlight on press.** `WallpaperChipAdapter`
  rebound the whole list on every series press, dropping focus from the chip
  the user had just pressed. Wallpaper tiles also split the focus highlight
  and the click target across two views; the focusable card is now the root,
  with the selection ring as a non-focusable overlay.
- **WeatherBug applied the Streamflix icon.** `com.weatherbug.firetv` was a
  component of `streamflix_2`, copied from Projectivy 1.1.9. It now has its
  own catalog entry (`weatherbug`, `#2F6F8C` `tile_W`).
- **7plus on `com.swm.live` only mapped setup activities.** Added
  `au.com.seven.inferno.MainActivity` and `.MainActivity` (unverified) so the
  live Play Store package has a chance of matching the Leanback/home
  activity, not just first-run setup.
- **Pixel Neon category chips dropped D-pad highlight on press.** Same
  `notifyDataSetChanged()` bug fixed on the wallpaper chips; the icon filter
  row now uses targeted `notifyItemChanged`.

### Added
- **Focus-reachability check in `check_ui_resources.py`.** A fifth static
  check reports any focusable view that an explicit `nextFocus` chain hops
  over. It flags all four regressions above *and* the original 1.8.10 one when
  run against the pre-fix layouts — nothing in CI would have caught either
  before.

### Internal
- **Pixel Neon asset drift is now gated in full.** The CI check listed five
  paths and omitted `res/drawable-nodpi`, the 1851 shipping rasters, so a
  stale icon passed CI silently — that is how a blue 7plus tile survived the
  1.8.8 colour remediation and shipped until 1.8.10 regenerated it. The gate
  now covers the whole generated tree.

Coverage: **926 icons / 1101 catalog components / 58 classic wallpapers**.
versionName 1.8.11, versionCode 21.

## [1.8.10] — 2026-09-10

**Post-1.8.9 hotfix: TV focus reachability, 7plus artwork, launcher-neutral
wallpaper copy.**

### Fixed
- **Wallpapers button unreachable by D-pad.** `apply_button.nextFocusDown`
  skipped past `wallpapers_entry` and went straight to the chip row, so the
  entry button could not be reached from the keyboard path at all. The focus
  chain is now Apply → Wallpapers → chips/search → grid and back; mirrored in
  the Pixel Neon and Pop layouts.
- **7plus showed the wrong artwork.** The secondary/legacy `seven_plus`
  catalog entry (the `com.swm.live` setup activities) carried a stale orange
  accent and generic `tile_S` glyph while the primary `sevenplus` entry
  (au.com.seven.inferno) is brand red. Both now resolve to the same red
  `#E81820` `tile_7` artwork, category VOD, with regenerated icons and
  banners across all three packs; Pixel Neon rasters were additionally
  refreshed from the current catalog, which also retired a stale blue
  7plus tile that predated the v1.8.8 colour remediation.
- **Wallpaper copy over-stated Monet.** In-app strings (and mirrored Pixel
  Neon/Pop strings, plus the README) now describe setting, rotating and
  exporting wallpapers as works with any launcher or the system wallpaper
  flow, not a Monet-only feature.

Coverage unchanged: **925 icons / 1099 catalog components / 58 classic
wallpapers**. versionName 1.8.10, versionCode 20.

## [1.8.9] — 2026-09-10

**Android TV hardening and wallpaper expansion.** This release makes Core Builds more reliable from a 10-foot viewing distance and adds eight new 4K wallpapers.

### Android TV and launcher UX
- Added correctly sized Android TV banner and launcher resources.
- Improved D-pad focus, focus restoration, chip navigation, accessibility labels, and TV typography.
- Corrected Home launcher detection so the icon pack cannot detect itself as the launcher.
- Removed misleading FLauncher apply support because FLauncher does not support icon packs.
- Added Core Builds, Remotes, and Tracking filters.
- Cached drawable resource lookups for lower-end TV devices.

### Wallpapers
- Added Motion wallpapers 51–54 and Horizons wallpapers 55–58.
- Added D-pad Left/Right wallpaper preview navigation while preserving button focus behavior.
- Rebuilt new wallpaper artwork for stronger 4K visibility and cleaner gradients.

Coverage remains **925 icons / 1099 catalog components / 1661 expanded mappings / 58 classic wallpapers**.

## [1.8.8] — 2026-09-08

**Brand-colour remediation.** A 925-icon colour audit identified 32 icons
whose catalog hex was wrong — cycled palette colours or inaccurate brand
references. All 32 are now corrected across four batches, with provenance
fields (`color_source`, `color_reviewed`, `color_note`) on every catalog entry.
Prepared as `versionCode 18`. Coverage remains **925 icons / 1099 catalog
components / 1661 expanded Classic mappings**.

### Colour accuracy (batches 1–4)
- **Batch 1–2 (24 icons):** provenance pass + hue-preserving tonal ramp
  infrastructure in `display_accent()`. Dark accents now lighten along their
  own hue to clear 3:1 contrast on `#0D1117`. Achromatic accents map to
  `LIGHT_INK` (`#E6EDF3`). New `monochrome` flag for brands with near-black
  marks above the 0.08 saturation threshold.
- **Batch 3 (4 icons):** ABC iview `#00B6E4→#20B8B8`, 9Now `#00A0DC→#1048E0`,
  SBS On Demand `#6A4C93→#182020` (monochrome), Kayo `#00E676→#58B068`.
- **Batch 4 (8 icons):** FIFA+ `#2EC4B6→#326295`, Maze `#2EC4B6→#000000`
  (monochrome), Mpv `#FEE440→#691F69`, NOW `#4CC9F0→#001211` (monochrome),
  Peloton `#B5179E→#181A1D` (monochrome), RetroArch `#F7B32B→#000000`
  (monochrome), Shadow `#F94144→#0A0C0D` (monochrome),
  Binge `#E6007E→#B80472` (gradient brand).

## [1.8.7] — Unreleased

**Core Builds identity first.** The initial candidate used filled vendor
silhouettes and a standalone white NoBuffr wordmark. User review rejected that
style drift. Those treatments have been replaced, not merely recoloured.
Prepared as `versionCode 17`; no release tag or Downloader target is moved.
Coverage remains **925 icons / 1099 catalog components / 1661 expanded Classic
mappings**. Counts include regional/build variants, not unique services.

### Style correction
- **18 brand constructions / 22 entries** now use Core Builds-authored rounded
  monoline geometry: **32px main stroke**, existing **26.2px / 21.8px** detail,
  one accent, transparent interiors, no solid vendor slabs or private effects.
  This includes the 16 reviewed existing brands, the iPlayer variants and
  NoBuffr. The established parent mark and other neighbouring glyphs remain
  unchanged.
- **NoBuffr** now uses the observed lowercase **no + interrupted buffer
  underline** cue in the same linework, rather than the traced white wordmark.
  Its full name appears in the **standard Outfit + PLAYER category +
  cyan/violet rail banner**. Plex also returns to the common banner layout.
  There is no third-party wordmark-only exception for the reviewed entries.
- Brand reference geometry remains local and hash-pinned, but catalog `artwork`
  records are explicitly **reference-only**. The direct vendor-SVG glyph
  registration route is removed. Actual icon constructions live in
  `tools/glyphs.py`; rights/reference scope is clear in `THIRD_PARTY_NOTICES.md`.
- Recognition cues are retained in the pack's style: Kodi's split diamond,
  Stremio/Jellyfin triangles, Spotify's three arcs, Crunchyroll's circular curl,
  Twitch's chat/twin-bar form, NordVPN's mountain, Proton VPN's folded triangle,
  Deezer's heart waveform, MUBI's **2–3–2** round elements, Plex's chevron,
  Paramount+'s simplified mountain/star glints and the YouTube variants.
  These are stylistic interpretations, not claims of exact vendor reproduction.
- The compact review image now shows **mixed Classic rows with unchanged
  neighbours and actual-size 320×180 banners**, not an isolated vendor-logo
  gallery that conceals a mismatch with the pack.

### Verified NoBuffr support retained
- Downloaded the exact supplied URL,
  `https://downloads.nobuffr.com/android/nobuffr.apk`, and statically inspected
  version `1.0.0` / code `210246` without installing/executing it.
- Actual component: **`com.nobuffr.app/tv.tivitime.compose.app.AppActivity`**,
  with phone and TV launcher categories. The style correction does not alter
  the component or add any guessed `MainActivity` variants.
- APK SHA-256:
  `ec835a672087b5cb56700ddc3ed4e050519f5829d10e86800d5506d79afda5bf`.
  Small receipts/reference resources are in `tools/reference/nobuffr/`; no
  APK is committed or bundled. The temporary branch-only fetch workflow was
  removed after inspection; normal builds are offline.
- Square, banner, picker and auto-assignment support stays present in Classic,
  Pop and Pixel Neon. The generated Classic app list links to the supplied APK.

### Colour, coverage and research fixes retained
- Correct v1.8.6's mistaken **iPlayer-red** claim: all three variants retain
  pink `#FF4C98`, now as three rounded strokes rather than filled beams.
- Seven variant groups share one glyph/accent: iPlayer, 9Now, Paramount+,
  Syncler, Weyd, SmartTube and tvQuickActions. Existing components and drawable
  names are retained.
- **79 dark accents** use a shared `#E6EDF3` light-ink alternative when below
  3:1 on the recommended `#0D1117` card. The original colour stays in the
  catalog. This is a dark-card treatment, not universal wallpaper contrast.
- YouTube interiors and equaliser faders are real alpha, not night-colour plugs.
- The request audit distinguishes genuine gaps (TDUK Cache Cleaner/App Killer,
  Tata Play Binge, Beacon) from already-mapped apps needing fidelity/mapping
  review (Wholphin, Nuvio, ZEE5, File Manager+). No duplicate-icon inflation.
- Pixel Neon's validation and the README stamp derive counts from the catalog;
  exact mappings, duplicates and wrong drawable assignments are checked.

### Regression gates and regeneration
- **35 identity tests**, including mutations that reject fills, fixed-white
  wordmarks, square caps, overweight strokes, private scaling and mark-only
  banners. The old NoBuffr vendor-silhouette similarity test was replaced with
  the correct style contract; APK/hash/component/resource checks remain.
- The Classic validator checks source and generated SVG style. Classic and Pop
  now share the catalog validator instead of maintaining diverging copies.
- All four Classic generators, Pop metrics + full Pop generation, and Pixel
  Neon generation run from the catalog. No generated artwork/XML is hand-edited.
- Classic: `Validated 925 icons · 1661 components · 24769 checks run`.
- Pop: `Validated 925 icons · 1099 components · 16 swatches · 13890 checks run`;
  all 28 Pop tests pass.
- Pixel Neon: `Validated Pixel Neon · 925 icons · 1099 catalog components · 12520 checks run`.
- Android build/lint/emulator results are recorded against the PR head.
  Physical-device Projectivy auto-assignment, official brand approval and a
  complete long-tail logo audit are not claimed.

## [1.8.6] — 2026-09-08

Corrected landing of PR #88 ("icon pack consistency & wallpaper series
replacement"). Half of that PR was a real fix applied in the wrong place; the
other half was a misreading of generated output. Both are resolved here at the
source of truth, `tools/catalog.json`.

### Fixed
- **BBC iPlayer accent is red, not pink.** All three iPlayer drawables
  (`iplayer`, `bbc_iplayer`, `bbciplayer`) shipped `#FF4C98`, a palette pink that
  no BBC surface uses. Now `#FF0000`. The PR hand-edited the generated
  `assets/svg/*.svg` files, which the drift gate in `build.yml` rejects by
  design; the colour now lives in the catalog, so the SVG masters, 924 icon
  PNGs, 924 banners, Pop, and Pixel Neon were re-rendered from it together.
- **Wallpaper swap is complete.** PR #88 deleted `Wallpapers/series-4-core-mark/`
  and its manifest entries but left the thumbnails in `Wallpapers/thumbs/` and in
  `app/src/main/assets/wallpapers_thumbs/`, and never touched the bundled
  `app/src/main/assets/manifest/wallpapers.json` — so the in-app grid pointed at
  404s and `tests/test_wallpapers.py` failed. Files, thumbs, repo manifest and
  bundled copy now agree.

### Changed
- **Wallpaper collection v3.0 → v4.0: 70 → 50 wallpapers.** The 30-wall
  `series-4-core-mark` series is retired and replaced by ten lit-circuit
  `series-6-circuit-core` wallpapers (numbers 41–50, 2.1 MB). Index 5 stays
  Pop's, so the two collections never share a series number.
- `tests/test_wallpaper_export.py` no longer pins `versionCode = 15` verbatim; it
  asserts the export release (v1.7.2 / code 12) as a floor, so shipping a newer
  version stops failing a test. Exact version equality stays with
  `tools/check_suite_truth.py`.
- Series 6 is honestly labelled `1376x768`. It is not 4K, and the sources are
  JPEGs, so upscaling to make the old claim true would only buy soft edges and
  ~4 MB per file. The in-app browser and Monet extraction do not care.

### Added
- `tests/test_wallpapers.py` gates the parts that PR #88 got wrong: every entry's
  `resolution` must equal the decoded pixels of the file on disk; the bundled
  thumb set must equal the manifest set exactly; a series retired in the
  manifest must be gone from disk, and no directory may sit unindexed. Plus a
  16:9 aspect check per shipped file, since the system setter crops.

### Fixed — tests that could not fail
- `tests/test_core_shift_content.py` and `tests/test_prequel_engine.py` were lists
  of bare `test_*` functions with **no runner**: `python tests/<file>` exited 0
  having asserted nothing, so CI's "Python content tests" step counted them as
  green while the expectations inside went stale (the shift file still pinned
  v2.3.4/code 10 while shift ships 2.3.5/11). Both now run every test they define,
  discovered from the module rather than hand-listed, so a new test cannot escape
  the run — and the shift version check reads `suite.json` instead of a literal.

### Rejected from PR #88, on purpose
- **"Unify stroke-width to 32.0 across all 925 SVGs."** The 26.2/21.8 values are
  not drift: `tools/glyphs.py` `monoline()` snaps strokes ≥30 to the canonical
  32.0 and scales lighter detail to 0.82×/0.68× so film-reel perforations and
  equaliser knobs stay subordinate to the primary line. Flattening them would
  rewrite all 924 icons to one uniform weight and undo style AA.
- **Eight "new icons" → zero.** `kick`, `kayo`, and `unlinked` already ship;
  ZEE5 ships as `graymatrix` and Analiti as `fastest`, which is why searching the
  catalog by app name missed them. That leaves NoBuffr, Tata Play Binge, and TDUK
  Cache Cleaner, and `build_icons.py` refuses a catalog entry with no components
  ("icon would never auto-assign"). Their launch activities are not in
  `tools/reference/projectivy-1.1.9-appfilter.xml` or `docs/logo-research/`, and
  guessing them is the documented top cause of icons that silently never apply —
  so they need a `./tools/scan_device.sh` pass, not a placeholder rectangle.
  The count therefore stays 924, and the PR's "932" was 924 plus eight rows,
  five of which were already in the pack.
- `banner.png` / `banner_320x180.png` at the repo root (2.6 MB) — no generator
  writes those paths; the README banner is `docs/banner.png` from
  `tools/build_branding.py`.
- `ICONPACK_REVIEW.md` — its stroke-width and fill findings are both misreadings
  of generator output, recorded above instead.

### Verification
- `python tools/build_icons.py && python tools/build_banners.py` → 924 icons,
  1098 components. `tools/build_branding.py` / `build_brand_preview.py` not
  re-run: they render the Core Builds mark and five fixed icons
  (stremio, kodi, jellyfin, plex, youtube), none of which changed.
- `python tools/validate.py` → `Validated 924 icons · 1660 components · 23743 checks run`.
- `python tools/build_pop.py && python tools/validate_pop.py && python tests/test_pop.py`
  → `Validated 924 icons · 1098 components · 16 swatches · 13877 checks run` and
  `Ran 28 tests ... OK`. `tools/pop_glyph_metrics.json` untouched: no glyph geometry
  changed, so no re-measure is due. `python tools/build_pixel_neon.py && python tools/validate_pixel_neon.py`
  → `Validated Pixel Neon · 924 icons · 1098 catalog components · 9188 checks run`.
- `python tools/check_suite_truth.py && python tools/audit_contract.py` → both pass.
- `python tests/test_wallpapers.py` (21 checks), `test_wallpaper_export.py`,
  `test_v151_robustness.py`, `test_core_shift_content.py`,
  `test_core_shift_screensaver.py`, `test_prequel_engine.py` → all pass.
- `cd ticker && npm test` → pass. Android `:app:lintDebug :app:testDebugUnitTest
  :app:assembleDebug` not run locally: no Android SDK in this environment. CI
  covers it.
- Version note: this is 1.8.6, not the 1.8.3 in PR #88's title — tags
  `v1.8.3`–`v1.8.5` already exist in the repo.

## [Pop 1.0.0] — 2026-09-07

First release of **Core Builds Pop**, a second icon pack generated from the
same `tools/catalog.json` as the classic pack. Separate package
(`tv.corebuilds.iconpack.pop`), separate release tag (`pop-v*`), separate
update manifest. Nothing about the classic pack's shipped product changes.

### Added
- **924 pop-art icons + 924 banners.** One superellipse container, a heavy ink
  keyline, a cream mark, and a Ben-Day halftone screen on every one.
- **A locked 16-swatch palette.** 169 catalog accents snap by hue to 16
  swatches, so colour still carries brand meaning while saturation and value
  stop varying. Census: `docs/pop-palette.png`.
- **Optical size normalisation.** Every mark is scaled so its inked bounding
  box matches one target, ending the 2×+ size variance in the source glyphs.
  The 601 letter-tile icons drop their inner box and roughly double in size.
- **12 matching 4K wallpapers** (`Wallpapers/series-5-pop`, 2.5 MB total) with
  their own manifest and bundled thumbnails.
- `tools/popart.py` render engine, `tools/build_pop.py`,
  `tools/build_pop_wallpapers.py`, `tools/validate_pop.py` (13,722 checks),
  `tests/test_pop.py` (24 contract tests), and `.github/workflows/pop-apk.yml`.
- Research: `docs/research/iconpack-demand-2026.md` and
  `docs/research/wallpaper-directions.md`.

- **Fallback masking for unthemed apps.** `iconback` (all 16 swatches),
  `iconmask`, `iconupon` and `scale="0.69"`. Apps the pack does not cover still
  get the Pop container, so the pack's claim is "every app on your device", not
  "924 icons". Nobody else on Android TV ships this.
- **Projectivy Launcher's own cards are themed** — settings, categories,
  channels, and HDMI 1–4 / AV inputs, numbered so they are told apart at a
  glance. Uses the internal-activity mapping added in Projectivy 4.70
  ([miproja1#512](https://github.com/spocky/miproja1/issues/512)); 22 entries,
  both component name forms.
- Research: `docs/research/android-tv-icon-packs.md` — launcher landscape,
  ADW spec gaps, and a prioritised roadmap.

### Fixed
- **`popart.snap()` was not idempotent.** `snap(SWATCHES["pop_slate"])` returned
  `pop_marine`: the two neutral swatches are chosen by saturation/value
  thresholds their own hex values do not satisfy. Invisible in the icon
  pipeline, which never snaps twice — it surfaced when the new launcher-
  furniture cards asked for slate by value and rendered blue. `snap` now
  exact-matches its own palette first. No catalog accent equals a swatch hex,
  so none of the 924 icons changed. Invariant now enforced.

### Changed
- **`UpdateInstaller.AUTHORITY` and `UpdateChecker`'s manifest URL now come
  from `BuildConfig`.** Both packs compile the same Kotlin; two installed
  packages may not share a FileProvider authority, and Pop must poll its own
  release manifest. Values for the classic pack are unchanged, so its
  behaviour is identical.
- `tools/validate.py` follows that indirection instead of grepping the
  constant, and now asserts the Gradle field matches the manifest.

## [1.7.1] — 2026-08-21

Hot patch on 1.7.0: export wallpapers to `Pictures/CoreBuilds/` for launcher
auto-rotation, plus hardening for the in-app wallpapers surface shipped in 1.7.0.

### Added
- **Multi-select export.** Long-press a wallpaper (or press the header Export
  button) to enter selection mode; Select all / Clear / Export N start a bulk
  copy to `Pictures/CoreBuilds/`. A progress screen reports saved, skipped and
  failed counts with **Retry failed** — no unnamed errors.
- **`WallpaperExporter`** — sequential download-then-copy that streams original
  bytes (no bitmap decode, no re-encode). Idempotent: same-named, same-size
  files are skipped; pre-flight free-space check; failed files never leave
  half-written MediaStore rows.
- **`ExportProgressActivity`** — determinate progress, then a row of installed
  launchers (detected via the existing `ApplyIconPack` catalog) so the user can
  open Monet/Projectivy/etc. and finish enabling rotation.
- **Save button** on the wallpaper preview, alongside Set. Saves the original
  4K file into `Pictures/CoreBuilds/` without setting it.
- `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion=28` for API 21–28 (Fire TV /
  older Shield); API 29+ uses scoped storage with no runtime permission.
- `tests/test_wallpaper_export.py` — contract tests for export wiring.

### Fixed
- Wallpaper series labels crashed on API 21–23 (`CharSequence.titlecase()` is
  API 24+). Now uses `toUpperCase(Locale)`.
- Preview could recycle a bitmap still held by its ImageView on destroy
  ("Canvas: trying to use a recycled bitmap"). Detaches before recycling and
  guards all background callbacks against a destroyed activity.
- Concurrent wallpaper downloads could write the same cache file from two
  threads. Requests for a URL already in flight now coalesce onto one fetch;
  downloads write through a `.part` temp and atomically rename.
- Wallpaper preview now requests initial focus after layout (not in `onCreate`),
  and thumbnail decoding runs on a shared 2-thread pool instead of one thread
  per bind.

### Changed
- `WallpaperSetter` gained a public `copyFileToPictures(File)` used by both Save
  and export; the bitmap-only path remains for the Fire TV set fallback.
- `WallpaperDownloader` exposes `fetchUrl()` and uses a single worker plus a
  shared main Handler (was one Handler allocated per thumbnail load).

## [1.7.0] — 2026-08-21

In-app wallpapers. The Core Builds wallpaper collection is now browsable and
settable from inside the app, on Android TV / Google TV and (via the Pictures
fallback) Fire TV.

### Added
- **Wallpapers browser** (`WallpapersActivity`): a night-chrome grid of the full
  Core Builds collection with series filter chips. Thumbnails are bundled so the
  grid renders instantly offline.
- **Full-screen preview** (`WallpaperPreviewActivity`): shows the bundled thumb
  immediately, then downloads the 4K image on demand with a byte progress label
  and sets it in one press.
- **30 new "Core Mark" wallpapers** (series 4, #41–#70): the lit hex + faceted
  core diamond rendered from the Brand Guide v1.0 construction constants. Added
  as 3840×2160 PNGs in `Wallpapers/series-4-core-mark/` with thumbs; collection
  manifest bumped to **v3.0 (70 wallpapers)**.
- **`WallpaperSetter`**: sets the system wallpaper through `WallpaperManager`
  (the path Monet uses to extract its Material You palette). On devices that
  block third-party writes (Fire TV) it saves to `Pictures/CoreBuilds` and opens
  the system crop/set intent.
- **`WallpaperDownloader`**: on-demand full-image fetch with a GitHub-host
  allowlist, https-only, and a 12-file internal-storage LRU cache. Reuses the
  app's no-library HTTPS discipline.
- A `Wallpapers` entry chip on the main screen showing the live collection count.
- `SET_WALLPAPER` permission (only consulted on API ≤ 28).
- `tests/test_wallpapers.py` — 17 contract checks covering the manifest, bundled
  thumbs, series-4 files, and Android wiring; wired into `build.yml`.

### Changed
- Version bumped to 1.7.0 (version code 10).

## [1.6.0] — 2026-08-20

Projectivy-scale coverage and evidence-based launcher matching.

### Added
- **401 new original icons and 16:9 banners**, bringing the pack from 516 to **917 icons**.
- Exact component mappings from the Projectivy Icon Pack 1.1.9 reference set: **1,090 source mappings**, **958 packages**, and **1,646 generated appfilter rows**.
- A decoded, auditable mapping snapshot at `tools/reference/projectivy-1.1.9-appfilter.xml`; inherited entries carry `mapping_source` provenance and remain marked unverified pending hardware confirmation.
- Byte-identical `assets/appfilter.xml` and `assets/drawable.xml` compatibility copies for launchers that do not read `res/xml`.
- Canonical component and asset-parity validation.
- `resvg-py` as the deterministic primary rasterizer so generation is stable on minimal hosts without libcairo.
- Floating `iconpack` release automation with permanent `iconpack-release.apk` and compatibility `app-release.apk` assets, restoring Downloader code `5270601` without competing with Core Line's floating release.
- Expanded Android launcher matching research in `docs/COMPARISON.md`.

### Changed
- Version bumped to 1.6.0 (version code 9).
- Coverage claims now distinguish selectable icons, mapped art, source components, generated aliases, and unique packages instead of conflating them.

## [1.5.1] — 2026-08-19

Robustness. Mixed-launcher apply. Safer updater. Picker can hand a banner or a square. In-app update download. Name audit.

### Fixed
- **In-app install crashed.** `UpdateInstaller` wrote a FileProvider path but the manifest never declared the provider.
- **Updater followed any redirect host.** Now https-only and GitHub CDN allowlisted; download must be a ZIP/APK and ≥ 200 KB.
- **Install permission return did nothing.** After Unknown Sources, `onResume` now opens the installer once.
- **Picker only sent a bitmap unless a rare extra was set.** Now always returns `EXTRA_SHORTCUT_ICON_RESOURCE` plus a bitmap fallback.
- **Picker always delivered the 1:1 glyph.** Default is the 16:9 banner; a Banner / Square chip switches.
- **Twitch / 19 other aliases** from SicMundus 1.1.9. Monet 1.0.76 has **no apply extra** — Manual path is the real contract.
- **Launcher Manager** was labelled Lucky Manager (`com.wolf.google.lm`).
- **180+ display names** that were still package slugs or vendor fragments
  (Acorn TV, A&E, HISTORY, Fubo, Hulu, F1 TV, YouTube Kids, Paramount+,
  Crave, Streamyfin, TIDAL, FLauncher, CinemaGlow, DRM Info, …).
- **Twitch** was mapped to the mobile activity
  (`tv.twitch.android.app/.core.LandingActivity`). Android TV launches
  `tv.twitch.android.apps.TVLandingActivity` (older) or
  `tv.twitch.starshot64.app.StarshotActivity` (current, SicMundus 1.1.9).
  Both are now mapped, plus the older `TwitchActivity` alias (unverified).
- **19 more documented aliases** harvested from Projectivy Icon Pack 1.1.9
  for packages we already ship (10 Play You.i, iview `.ui.MainActivity`,
  Kayo Fox Sports Martian, Stan splash, Max Beam, Trakt TV, S0undTV Fire TV,
  Solid Explorer class name, SmartTube beta, …). Marked `unverified`.
  Same approach as other TV packs: extra `ComponentInfo` lines, not a
  package wildcard — Projectivy matches the literal string.

### Added
- FileProvider `tv.corebuilds.iconpack.update` + validator guards.
- CI runs `build_banners.py` so renamed wordmarks cannot drift in XML/SVG.
- Apply names Home first and lists every other known installed launcher.
- **Download / Install** bar when `Latestrelease/version.json` is newer.
  One press pulls the APK from GitHub and opens the system installer.
- Apply targets every known installed launcher. The primary button names
  the Home launcher (Projectivy, Monet, AT4K, Leanback on Fire, L TV,
  FLauncher, ChillHub, Nova, Lawnchair, Apex, ADW). Extra chips list the
  others. No apply contract → opens the launcher with the named path.

## [1.5.0] — 2026-08-18

Outfit wordmarks, dedicated file/NAS icons, named in-app browser.

### Added
- **Outfit Bold / ExtraBold** bundled under `tools/fonts/` (SIL OFL). Banner
  wordmarks and square monograms are converted to paths from that one family,
  so a row of cards no longer mixes hand-drawn letters with whatever sans the
  host has installed.
- **14 icons**: LocalSend, RS File Manager, Sparkle TV, DS file, DS video,
  DS photo, DS audio, DS finder, Synology Drive, DS get, FX File Explorer,
  Solid Explorer, Material Files, Ghost Commander.
- 10 new glyphs: `localsend_nodes`, `sparkle_burst`, `nas_stack`, `nas_play`,
  `nas_image`, `folder_rs`, `folder_wifi`, `folder_fx`, `folder_solid`,
  `radar_dish`.
- In-app **category chips**, **name search**, and **labeled tiles**. D-pad
  moves apply → chips → search → grid; the grid is no longer trapped inside
  a NestedScrollView.
- `drawable.xml` grouped by catalog category (Banners · Files, Square · Live
  TV, …) so Projectivy's icon picker can jump a section.

### Fixed
- **200+ slug names** imported from package fragments now read as app names
  (WiFi File Explorer, CX File Explorer, X-plore, 9Now, Peacock, …).
- **Files** no longer steals CX / MiXplorer / FX components. Each file
  manager maps to its own icon. X-plore is Files, not Gaming.
- Banner wordmarks no longer depend on DejaVu/Liberation being present.

### Notes
- New Synology / LocalSend / RS File Manager / Sparkle TV components are
  best-known, not device-confirmed. If one does not auto-assign, open an
  issue with `adb shell cmd package resolve-activity --brief <package>`.

## [1.1.0] — 2026-08-17

Built from a real device scan (`himalaya`, Android 14 / API 34).

### Fixed — icons that were silently not applying
Eight apps were mapped to activities the device does not launch, so their
icons never applied and nothing reported it. Device-verified components added:

- **Projectivy Launcher** `.ui.home.MainActivity`
- **Prime Video** `com.amazon.ignition.IgnitionActivity`
- **Stan** `au.com.stan.presentation.tv.splash.SplashScreenActivity`
- **10 Play** `com.tenplay.MainActivity`
- **Just Player** `.PlayerActivity`
- **Disney+** `com.bamtechmedia.dominguez.main.MainActivity`
- **Downloader** `.ui.main.MainActivity`
- **Spotify** `com.spotify.app.androidtv.MainActivity`

### Added
- **28 icons** (40 → 68), every component read off hardware: WuPlay, Aurora
  Store, Aptoide TV, SAI, APKTime, Strexo, ADB App Control, Monet, Moonlight,
  Janky, Wave TV, Strmr, Send Files to TV, Projectivy Blueprint, TV Quick
  Actions, Home Button, Cinema HD, Unlinked, Vimu, TizenTube, Play Store,
  Live TV, SD Maid SE, Shizuku, Tasker, Poweramp EQ, ATV Tools, Lucky Manager.
- 11 new glyphs: store bag, install box, stream tower, gamepad, wrench, send
  arrow, broom, shield key, automation graph, home button, tv stack.
- Scanner reports mismatches on catalogued system apps instead of filtering
  them out.

## [1.0.1] — 2026-08-17

### Fixed
- **Projectivy could not see the pack.** The manifest never declared
  `com.spocky.projengmenu.icons.ACTION_PICK_ICON`, Projectivy's own discovery
  action, so the pack installed successfully and remained invisible in
  Appearance → Cards → Icon Pack. Found by decompiling the shipped v1.0.0 APK
  and diffing its manifest against the reference pack.
- **Android 11+ package visibility.** With `targetSdk 34` and no `<queries>`
  block, every launcher lookup returned "not installed", so direct apply could
  never have fired on a modern device — silently, since nothing throws.

### Added
- One-press **direct apply** for Projectivy, Nova, Lawnchair, Apex and ADW.
  The button names its target before it is pressed and reports
  Applied / NotInstalled / Manual by name, with the exact menu path on fallback.
- Remaining launcher discovery actions (Sony, Fede, Lawnchair PICK_ICON,
  OnePlus, Turbo, Nova CUSTOM_ICON_PICKER).
- 10 validator checks covering the intent contract and `<queries>` (419 total).

## [1.0.0] — 2026-08-17

First release.

### Added
- **40 icons** covering **78 launcher components** — Core Builds ecosystem
  (Stremio, Kodi, Jellyfin, Emby, Plex, Nuvio TV, Syncler, Weyd, Trakt, TorBox,
  Real-Debrid, AllDebrid, Premiumize, Downloader, VLC, MX Player, Just Player,
  Kore, SmartTube), mainstream streaming (Netflix, Prime Video, Disney+, Max,
  Apple TV, YouTube, Spotify, Twitch), and AU free-to-air (ABC iview, 9Now,
  7plus, 10 Play, SBS, Stan, Binge, Kayo).
- Transparent-background glyphs on a 512 grid, drawn as original geometry in
  the Core Builds icon language (Brand Guide v1.0 §07).
- Android module with `appfilter.xml` auto-assignment, Leanback banner,
  `LEANBACK_LAUNCHER` category, and seven icon-pack discovery intents
  (Projectivy, Nova, Lawnchair, ADW, Apex, Tesla, GO).
- In-app browser grid on night chrome with the signature cyan-gradient CTA.
- Generator pipeline: `build_icons.py`, `build_branding.py`,
  `build_brand_preview.py` — all output derives from `tools/catalog.json`.
- Validator with 409 coherence checks (`tools/validate.py`).
- CI that regenerates assets, fails on drift from the catalog, validates,
  builds the APK, and publishes on a `v*` tag.

### Notes
- Designed for dark card backgrounds. Light launcher themes will wash the
  icons out.
- Component names for niche apps are best-known values. If one doesn't
  auto-assign, open an issue with the output of
  `adb shell cmd package resolve-activity --brief <package>`.

[1.0.0]: https://github.com/brevityA/CoreBuildsIconPack/releases/tag/v1.0.0
