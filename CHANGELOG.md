# Changelog

All notable changes to the Core Builds Icon Pack. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Banners for every app, from one switch.** Settings → Art style now
  changes the icons your launcher applies automatically, not just the
  catalogue and the icon picker. Switch to Banners and every app on the home
  screen gets its 16:9 card; switch back to Glyphs and they return to square.
  The first switch to Banners downloads Core Builds Banners, a small
  companion pack with no icon of its own, from the same release (Android asks
  you to confirm the install once). Projectivy, Nova and Lawnchair switch
  straight away; launchers without an apply shortcut open their own icon-pack
  setting with the pack to pick named.
- **`tests/test_banners_pack.py` companion contract.** The companion covers
  exactly the glyph pack's components, is discovered by the same launchers,
  has no launcher entry, and is published under the filename the app
  downloads.
- **`tests/test_icon_identity.py` duplicate-icon ratchet.** Different apps
  whose square icons are byte-for-byte identical — 22 groups, 46 icons, all
  two-letter monograms on a shared shell and accent or the stock folder — are
  frozen in a list that can only shrink. A new collision fails the suite, and
  a group given distinct art has to be removed from the list.

### Changed

- **Missing-app auditor sends mapping reports to the right form.** An app the
  pack already maps under a different activity now opens "Icon not
  auto-assigning" with its real component prefilled, instead of a new-icon
  request for an icon that already exists.

### Fixed

- **Missing-app auditor no longer lists apps whose icon works.** An app with
  a mapped TV activity and an unmapped phone-launcher activity was listed as
  missing; the auditor now judges each app by the activity a TV launcher
  shows. Matching also keys on the package, so a shared activity class under
  another app's ID no longer hides a row.
- **Missing-app auditor opens without a pause.** The scan runs off the main
  thread, and focus lands on the first row when the list arrives.

## [1.9.3] — 2026-09-23

### Added

- **Unified In-App Art Style Switcher (Square Glyphs / 16:9 Banners).** Integrated an in-app art style toggle directly under Settings > Display ("Art style: Glyphs or Banners") that seamlessly switches between Square Glyphs (512×512) and 16:9 Banner cards (320×180). Toggling this setting immediately updates the entire catalogue grid, the per-app launcher icon picker (`ACTION_PICK_ICON`), and the inspector export without requiring a separate application.

## [1.9.2] — 2026-09-23

### Added

- **Adaptive wordmark monograms.** The 525 letter-tile fallbacks no longer
  set one borrowed letter for every app that starts with it: each category
  shell now sets the app's own short token — Kemo Stream reads KS, AI Cam
  View reads AC, Mediaset Infinity reads MIT — in the same Outfit ExtraBold
  the banner wordmarks use, closing the one axis the design research still
  conceded to the Projectivy Icon Pack, whose fallbacks carry per-app
  wordmarks. The font adapts rather than the typeface changing: an
  optical-size tier per character count, a per-shell interior width so a
  music tile's type sits tighter than a film frame's, and a 96px counter
  floor below which a three-char mark trades back to two — letters that
  would close at a 48dp Projectivy tile never ship. Tokens derive by the
  same rule Pixel Neon's pixel monograms already use (multi-word: up to
  three initials; one word: first two letters), so the suite speaks one
  fallback language, and the three names whose single letter genuinely is
  the icon keep it (K+, U, World Radios' W). Two gates hold it honest: the
  catalog validator computes every mark's final cap inside its own shell
  budget and names any that slips under the floor, and two entries sharing
  shell, mark and colour across brands is now a build error, not a phase.
  Pop and Pixel Neon are deliberately untouched — Pop reads the same glyph
  registry, so its committed glyph metrics stay a pure function of it, and
  the square, the 16:9 banner and the contact sheet all set the same token.
- **Brand-informed mark styles, tranche 1.** Owner direction clarified the
  ask behind the wordmarks: the marks should wear something of the original
  app logo — which is exactly how far the pack can go, never the logotype
  itself (the pack's identity-over-reproduction rule and the wordmark test
  gate both stand). What ships instead is a researched treatment axis on the
  adaptive font, `mark_style`, grown one cue at a time like the glyph
  tranches. Tranche 1 is `lower`, the lowercase lock lowercase-wordmark
  brands wear, measured against the same shell budget and counter floor as
  the caps tokens, and it opens with the five monograms whose lowercase
  treatment is public and unambiguous: **Joyn** sets its whole word joyn,
  **Movistar Plus**, **Telenet**, **Voyo Sk** and **Waipu TV** set their
  initials in the lock. Every styled mark carries a `mark_style_source`
  note in the catalog saying where the cue was seen; a style without its
  note, an unknown style name, or a style with no mark all fail validation.
  A style that ships also joins the duplicate-render key, so two brands
  cannot collide into the same picture.
- **First auditor round: fifteen of the twenty unmapped apps are mapped.**
  Sideload-side-audit from an owner TV (catalog 943 → 958): AK47Sports
  (sport ball, gold crest cue), AnikenTV (anime tile), Voltra TV (green V
  cue), Button Mapper TV, Fast Task Killer, PMX and MagiConnect (tool nut),
  Mediaspelare (player screen), Galleri (photos print), and the TCL system
  shelf — Användarmanual, User Center, TCL Home Passive, Meddelandelåda,
  Works with Alexa and the com.tcl.tv tuner — each with its component read
  off the device auditor, its family from the house rules, and its accent
  from the photos where a stock-icon cue was visible. Two components were
  truncated by the audit row itself, so those entries carry the exact
  reported string plus the conventional completion as an alias
  (Fast Task Killer `…Optimization[Activity]`, PMX `…fwk.MainActivityM[/]`).
  All fifteen land on monograms by default and their marks auto-derive
  under the counter floor: PMX's three-char override doesn't hold the tool
  shell (79px), so it sets PM; Works with Alexa's WWA and WW both miss the
  app shell, so it carries WA explicitly. suite.json, both updaters'
  version.json files and the README stamp move in lockstep to 958 icons and
  1174 components. The remaining five auditor rows weren't visible in the
  photo set — round two waits on them.
- **One-press anonymous icon requests, groundwork shipped.** Pressing an
  auditor row now tries the direct path first: the app POSTs the three
  fields the GitHub form would have been prefilled with — app name,
  component, device note — to the Core Builds request broker, which files
  the issue as `CoreBuilds-requests[bot]` (and folds a repeat press into a
  +1 comment instead of a second issue). No GitHub account, no phone, no
  email on the reporter side; the GitHub App credential lives only as a
  Workers secret, never in the APK, never in git — the whole broker, its
  deploy notes and its test suite live in `tools/icon_request_broker/`.
  Where the GitHub App isn't configured yet, the same worker forwards the
  request as a card to a Discord webhook instead — the pattern the Core
  Builds webtools worker runs in production today — so the press works
  from day one and self-upgrades the moment the App secrets exist. The
  worker implements the webtools worker's documented hardening
  conventions: build tag compared by a prod-safe smoke check, byte-capped
  body reader, and three-layer rate limiting (binding → KV → isolate
  floor) — plus a full `[env.staging]` rehearsal lane and dispatch-only
  deploy workflow mirroring the webtools repo's, so nothing in production
  has to happen before a staging rehearsal passes; a zero-cloud rehearsal
  (`node rehearse-local.mjs`) already ran green end to end in the real
  Workers runtime and caught a boot-blocking entry-export bug before any
  deploy could.
  Pop compiles the same auditor and inherits this. Until the broker URL is
  baked into the generated resources (one `--endpoint` flag on the prefill
  generator after deploy), nothing changes on-device: presses go straight
  to the QR panel as before.
- **The QR fallback now carries its payload visibly.** Field-level prefill
  on GitHub's issue forms is unreliable
  in places a TV can never see (the GitHub mobile app rewrites the URL
  and drops the fields; logged-out logins can lose them), and no URL can
  ever carry a picture of the app's current icon. So the QR panel itself
  now shows the request's identity in plain pixels — the app's launcher
  icon next to its name and component — with a hint that a photograph of
  the screen attaches to the issue, and the QR payload can point at
  `docs/icon-request/index.html` (this repo's GitHub Pages) once Pages
  serves /docs: a .github.io link the GitHub app cannot claim, which
  always renders the three values, offers the account form, and — when
  the broker endpoint is baked — a no-login send that POSTs from the
  phone's browser (the worker learned Origin-reflect CORS and an OPTIONS
  preflight for exactly that; version tag `2026-09-22-iconreq03`, unit
  tests 28 → 30). One generator flag, `--landing`, bakes it the same way
  `--endpoint` does, and both persist across regenerations.
- **Square glyphs are the shipped default; banners are now opt-in.** Every
  appfilter entry in all three packs — 2,875 drawable references across the
  icon pack, Pop and Pixel Neon — used to map launchers to the banner art;
  they now map to the square glyph, and the launcher picker browses Square
  sections first with banners following. No banner is deleted: the art
  ships exactly as before, so a launcher that offers its own banner mode
  keeps working, and the icon picker's shape chips remember whichever
  shape a user last delivered. A new Settings row (Banner previews, off)
  mirrors that preference for the pack's own pick-mode chips — the one
  per-user channel a launcher genuinely exposes — while the shipped
  default means nobody needs it: apply once and the home screen is square
  glyphs by default. (Why not a per-user runtime switch reaching the
  launcher: launcher-facing art lives in static APK assets and launchers
  self-apply; the rewired default is the honest version of that toggle.)
- **A What's New sheet narrates each update.** The update bar has room for
  one line before a build installs; after the install the answer to "so
  what changed?" used to be nowhere in-app. Now the build's own manifest
  moves into the APK (`assets/version.json`, byte-identical to
  `Latestrelease/version.json` — validate.py checks the pair the way it
  does the dual appfilter copies), and a full-screen sheet reads the
  highlights off it: once per upgrade from the home screen (fresh installs
  are detected via firstInstallTime vs lastUpdateTime and skip the
  narration), any time from Settings > What's new. Fully offline: the
  UpdateChecker switch stays the only network the app ever speaks. Pop
  inherits the screen through its mirrored sources; Pixel Neon's slimmer
  build keeps the update bar only.
- **Three requested icons: TVLok, GridStreamr, Launch on Boot.** All four
  currently open `[Icon]` issues were triaged. TVLok (#155, a
  tvlok.com playlist player) and Launch on Boot (#157,
  `news.androidtv.launchonboot`, confirmed via its F-Droid page) each map
  to a researched component pair; GridStreamr (#156) maps both Play
  packages (`com.gridstreamr.gridstreamr` mobile and the native
  `com.gridstreamr.androidtv`). The fourth, "My File" (#158), arrived with
  an empty form — no package, no link — so it's answered on the issue with
  a request for its component and will ship when identified. All three
  shipped marks are category-monogram treatments (adaptive wordmark
  monograms in the shared palette), the pack's standing answer for brands
  without a reviewed glyph.
- **Unthemed apps now land on the pack's own cards.** The main appfilter
  carried no fallback furniture at all: anything outside the catalog
  arrived in a launcher that supports the composite schema (iconback /
  iconmask / iconupon / scale) as a naked stock icon next to 960-odd
  carefully regulated glyphs. The pack now ships the kit it was missing —
  ten card backs in the grid's #151923 fill nudged ten ways along the
  palette (blue, violet, cyan, green, ember, orchid, marine, slate,
  night, graphite), one shared card mask, a hairline-white upon, and a
  0.70 scale that matches the 352/512 ink box the glyphs themselves sit
  in. An app the pack has never heard of now reads as a Core Builds card
  first and an outage second. Pop shipped its own version of this kit
  from day one (pop_back_*, pop_mask, pop_upon); this closes the same
  door in the flagship.
- **The banner rail wears the app's colour.** For a year every banner
  carried the same cyan-to-violet stripe on its left edge: pack-level
  uniformity. Two things outgrew it. Launchers that colour-sample the
  icon to theme the surroundings — Monet's navigation glow, any
  Palette-swatch engine — met exactly one large saturated mass in our art,
  because a monoline glyph is a thin-line drawing; the rail answered for
  the brand and bleached a cyan halo around a red SmartTube. And per-icon
  identity, the exact thing the square glyphs already trade in, never
  reached the banners. The rail keeps its shape (same 16px width, same
  inset, same rounded cap) but its stops are each icon's accent now,
  dimming 55% toward the card at the bottom; icons with a declared
  two-stop gradient ride the same ramp as their glyph. Across the grid
  the rails still read as one family — laid out side by side they are a
  stripe-set, not a costume party — and a sampling launcher now sees the
  brand it was asking about. Pop's swatch backs never did this wrong; the
  fixed stripe in the flagship is the only rail that did.
- **Generic glyphs now wear their category as their shape.** Of the 961
  icons, 546 are adaptive wordmark monograms (family shell + letter +
  mark), and the shell assignment had flattened over time: 248 of the 550
  APP-category monograms sat in the broadcast shell, VOD/PLAYER rows in
  broadcast too, tool-shaped rows hiding behind an APP categorization —
  a TOOL row, a STREAM row and a STORE row were becoming the same glyph
  in three colours. Every family monogram now sits in the shell its
  catalog category declares (app/broadcast/film/sport/tool/music/files/
  vpn/gaming/browser/store/debrid/anime/kids/photos): 366 icons
  re-shelled, letters and marks untouched, so a wrench-shaped row is
  actually a TOOL and the stream family stays broadcast-shaped. The
  art changes only; components, drawables and appfilters are untouched.

### Fixed

- **Pixel Neon sprites are a pure function of their app's catalog row
  again.** Issue #111, root cause: four generators inside
  `tools/build_pixel_neon.py` mixed the row's positional index into the
  seed, so adding one icon repainted every icon after it. The row's
  drawable hash now supplies everything positional used to. Cost of truth:
  every sprite re-rolls exactly once with this build (they all change);
  after that, a repaint only moves when its own row does.

- **The auditor stops hiding apps whose package is partially mapped.**
  Suppression was package-level: one appfilter row anywhere for a package
  removed that package from the missing-apps scan — even when the mapped
  activity had gone stale in an app update, which is exactly when the icon
  silently stops applying and the audit is the one couch-side way to say
  so. An Internet Speed Test report proved the chain: the pack maps
  `com.rma.speedtesttv/…ui.SplashActivity` (inherited from the reference
  pack's own appfilter), current builds moved on, so nothing assigned *and*
  nothing appeared to report. The screen now compares full components —
  folding the appfilter's relative-activity spelling to the absolute form
  PackageManager reports — which is what its own docstring always claimed:
  a package with a stale mapping shows as unmapped again and can be filed.
  Pop had also inherited a stale icon_pack mirror from the first auditor
  round; that's corrected and the mirror is now test-held (`test_pop`
  gains the SharedResourceMirror class).

## [1.9.1] — 2026-09-20

### Fixed

- **Pressing an unmapped app in the auditor crashed the screen.** The
  generated deep-link template carried its URL-encoded title prefix as a
  bare `%5B`, and the screen reads that template through
  `getString(id, args)` — `String.format`, whose grammar parses `%5B` as
  width 5 plus a boolean conversion and throws before any URL exists; every
  row press, on every TV, since the string was introduced in 1.8.21. The
  generator now writes every literal percent as `%%` (the grammar's
  escape), and a new gate in the wiring suite scans every string resource
  in all three packs with the Formatter's grammar, so a percent the runtime
  cannot honour cannot ship again. AGENTS.md's Pop receipt, which had
  drifted from the validator's current output, is refreshed in passing
  (14767 → 14775 checks).

## [1.9.0] — 2026-09-20

### Added

- **The app now wears its design sheets.** The catalogue and the wallpapers
  browser are rebuilt to the approved sheets, which live again at
  `docs/design/app-ui-*.png` as the spec — the generated frames in `docs/` are
  the as-built truth, not the design. Home screen, in the sheet's order: cyan
  caps wordmark with the pack's two counts on the left ("943 icons - 1765
  components", the second read off `appfilter.xml` at launch), the Apply CTA
  with the detected launchers listed under it on the right, three full-width
  entry rows (Wallpapers, Settings, About) with what each holds in slate at the
  row's right end, an ALSO APPLIES TO kicker with the other installed launchers
  as plain chips, a full-width search field whose hint carries the catalogue
  size, the category chips, and a five-column grid of glyph-only tiles — the
  label under each tile is gone, the name lives in the inspector and in the
  tile's content description. Wallpapers: the count line reads "84 walls -
  series chips filter the grid", three columns of named thumbs, and the export
  affordance left the header for a ghost pill over the paragraph that says what
  it does (Monet rotates `Pictures/CoreBuilds`).

  Both screens are landscape two-pane, because a TV is 16:9 and the sheet's
  panel is portrait: a fixed left rail (`cb_rail_width`) carries the sheet's
  vertical stack — and has the panel's full height for it, so the entry rows
  keep title over subtitle exactly as drawn, and the wallpapers series chips
  stand on their side as a scrolling list — while the grid owns the right pane
  and gets two rows of near-square tiles on a 1080p panel instead of the
  cropped sliver a single column of chrome left it. Column counts are derived,
  not declared: pane width over tile pitch, both from dimens.
  `MainActivity.syncFocusChain()` was rewritten for the new order (rail rows →
  band → field → chips → grid, LEFT/RIGHT crossing panes geometrically);
  Classic and Pop share the Kotlin, Pixel Neon keeps its fork and gained the
  new resource names.
- **The UI renders at the same physical size on every TV panel.** Android TV
  sets disagree about the dp box they report - 1080p says 960x540dp, 4K says
  1280x720dp or 1920x1080dp depending on the OEM's density bucket - so a
  dp-written layout was rendering at a different physical size on each: on the
  doubled box every dp is half the millimetres, a 16sp label an 8sp label at
  the same three metres. Resource qualifiers cannot fix that (they are steps,
  wrong between steps: a 1280x720dp panel matches `sw720dp` and would wear the
  1920dp panel's metrics, 1.5x too big), so the app normalises the box instead:
  `TvActivity`, which every screen inherits, overrides
  `configuration.densityDpi` in `attachBaseContext` to the density that makes
  the panel's pixel width come out as exactly the 960x540dp canvas every layout
  and mockup frame is drawn against - continuously exact at any panel, `sp`
  and the accessibility font scale included, `drawable-nodpi` art laid out into
  dp-sized boxes as before, and per-app only. Column counts fall out of it
  invariant, since pane-over-pitch is measured in the normalised box.
  `tests/test_tv_scale.py` pins the arithmetic against every panel report the
  suite knows, pins every Activity to the base class, and pins the retired
  `values-sw720dp` buckets to staying retired.

- **Later, on the update bar.** The what's-new bar gains a dismiss. While it is
  up it also reclaims the header's launcher list and the ALSO APPLIES TO row,
  because on a 540dp panel the bar, the band and a full row of tiles do not all
  fit and the tiles are the screen; Later gives the two rows back for the
  session. The check still runs and `pendingUpdate` still stands, so About and
  Settings keep reporting the update and Download is one screen away.

- **`tests/test_navigation_graph.py`: a gate that audits menus.** `tests/test_navigation_graph.py`, in all three
  modules: every `clickable="true"` view in every activity and item layout is
  named by a handler in the Kotlin that inflates it (row roots bound through
  `itemView` excepted, and documented); every activity in the manifest is
  reachable from MainActivity by following `Intent(..., X::class.java)`; every
  `nextFocus*` edge ends on a view that can hold the cursor, bar one documented
  "stay put" edge per screen; and every `requestFocus()` names an id some
  layout declares. It exists because a redesign that restacks whole screens
  can leave a button with no listener or a screen with no door, and neither
  shows up in a layout diff.
### Changed

- **`tools/build_app_ui_mockups.py`: the UI mockups are generated from the source they depict.**
  `tools/build_app_ui_mockups.py` renders 1920×1080 TV frames of the catalogue,
  wallpapers browser, settings, FAQ, auditor, inspector and suite hub straight
  from `strings.xml`, `dimens.xml`, `colors.xml`, the generated arrays,
  `suite_hub.xml`, `Latestrelease/version.json` and the real bundled icon and
  wallpaper art, through the same Outfit-to-SVG-path and resvg pipeline the
  icon generators use. The four hand-drawn sheets it replaces had drifted off
  the published app — a stacked-rows home screen the layout never had,
  unlabeled tiles, "1765 components", a v1.8.20 header and chip counts from an
  older tranche — which is what "the mockups look nothing like what was
  published" meant. The sheets were not wrong, they were *ahead*: this release
  rebuilds the app to them, and they are restored under `docs/design/` as the
  spec the frames are checked against by eye. `--check` fails CI on drift; the
  frames carry a MOCKUP caption naming which values are example data (the
  detected launchers, the focused tile).
### Fixed

- **`tools/build_app_ui_mockups.py`: --check stopped comparing PNG bytes, because no two machines render the same bytes.** Pinning the renderer was not enough. With identical pins on both sides - Pillow 10.4.0, fontTools 4.59.0, resvg-py 0.4.0 - and byte-identical input rasters, the nine frames generated on CPython 3.11 differed from the same generator's output on a runner's 3.12 in *every* frame: 2-6% of pixels, mean channel delta under 5, max 232. Two more runs with the check made to name its inputs narrowed it down: the sources hashed identically on both sides - all 31 of them, and the 19 rasters the frames paste - while every frame's drawn structure differed, which is not antialiasing but *measurement*. Pillow shapes and measures text through raqm/harfbuzz when the wheel can find it and through FreeType's own advances when it cannot, so whether a runner happens to have libraqm changes how wide a string is, where `wrap()` breaks a line, and where a centred label sits. `font()` now pins `layout_engine=BASIC`, available everywhere, so the frames are laid out by the same rules on every machine - a no-op here, which is why the local PNGs did not move, and the whole fix on a runner that had raqm. The manifest records a metrics probe (one fixed string measured in all nine fonts the frames use) so that if this ever fires again the failure says "text metrics differ, the layout engine moved" instead of "the frames do not match". The check now compares what the frames are a function of: `docs/app-ui-mockups.json` records a sha256 for every source - all 15 layouts, the values files, the catalog, the update manifest, the bundled appfilter and wallpaper manifest, the fonts, the generator's own source - plus the aggregate hash of the 19 rasters the frames paste and, per frame, a digest of what was drawn: every string, its position, size, font and colour, every artwork and its box. Change any of it without regenerating and the check names the files that moved; a frame that no longer decodes, or is not 1920px wide, fails too. The structural digest is the stricter half of the old comparison - it fails on a one-dp move, which bytes only caught by accident of encoding. `--report` still prints both sides' hashes and a pixel-level difference, because when two machines disagree that is the only way to see how far apart they are, and it is how this was diagnosed: a runner's log storage is not reachable from the workspace that has to fix the failure, so the check now repeats its diagnosis as annotations. Mutated three ways - a dimen moved, the generator drawing 3px lower, a committed frame corrupted mid-stream - and each was caught and named.
- **`tests/test_render_stack.py`: the frames were rendered on a Pillow nobody else has.** `build_app_ui_mockups.py --check` compares the committed frames byte for byte, which only means anything if both sides used the same rasteriser - Pillow's text layout and PNG encoding move between major versions. The nine frames and `docs/icon-fidelity-preview.png` had been generated in a workspace carrying Pillow 12.3.0 against a pin of 10.4.0, so every local check passed (the frames matched the frames) while CI failed the same comparison in two workflows, twice, for a difference no reviewer could see. `tools/requirements.txt` had said "pinned so PNG output stays reproducible across machines and CI" all along; nothing checked that the machine doing the committing had installed them. The frames are regenerated on the pinned stack, and the new gate compares what is installed against the pins and says so in the failure message with the command that fixes it.
- **`.github/workflows/device-check.yml`: the emulator asserted a sentence the redesign retired.** The on-device check greps the launched home screen for "943 icons installed". The two-pane rebuild changed that line to `pack_stats_fmt` - "943 icons - 1765 components" - so the branch's first CI run failed on a TV emulator that had in fact installed both packs side by side, found them discoverable, launched them and rendered the counts correctly. The check now derives the sentence from `strings.xml`, the catalog and the bundled appfilter, counting `component=` occurrences the way `MainActivity.mappedComponents()` does, so it follows the copy instead of pinning it and a stripped or stale appfilter still changes the number. `build_app_ui_mockups.py --check` moved ahead of build.yml's asset regeneration for the same class of reason: the frames paste real rasters out of `res/drawable-nodpi`, PNG bytes move with the runner's rasteriser, and comparing after a regeneration measures this runner's libcairo rather than the repository. suite-ci.yml, which runs no builder at all, checks the frames too.
- **`tests/test_ui_wiring.py`: the wallpapers focus chain it pinned was still the portrait one.** The gate demanded six substrings from the pre-redesign layout, and two of them - something pointing *down* at Back, something pointing *up* at the export pill - cannot exist on a two-pane screen, where Back is the rail's top stop and the pill hangs under the paragraph that says what it does. It failed on the release PR's first CI run and had never failed locally, because the file was a script with a `main()` and no test functions: `pytest tests/` collected nothing from it, so every local sweep reported the suite green while CI ran the checks and the layout had moved underneath them. `test_icon_uniformity.py` was built the same way and had consequently never measured a glyph on a laptop either. Both now wrap the collector the script already used in a `unittest.TestCase`, so there is one runner and one answer, and `tests/test_ci_coverage.py` fails on any test file pytest cannot see. The wallpapers chain is pinned properly while it was open: seventeen parsed `(owner, direction) -> target` edges instead of substrings - a substring test could not tell "Back leads down to the selection bar" from "the export pill does" - plus a refusal of any edge the table does not list, because an unplanned edge is how a cursor escapes the panel. Both rules were broken on purpose in the layout and the gate had to notice.
- **`tests/test_ci_coverage.py`: five tests and two validators ran on a laptop and nowhere else.** `tests/`
  held 21 test files and the twelve workflows named 16 of them, one
  `python tests/x.py` line each — so `test_mapping_hygiene`,
  `test_monet_handoff`, `test_navigation_graph`, `test_presence` and
  `test_tv_scale` were invoked by no workflow at all, including both gates
  written this release. `tools/check_ui_resources.py`, which the 1.8.13 notes
  describe as one of five static gates, was referenced by none of them, and
  neither was Core Shift's `validate_motion_feed.py`. `build.yml` also watched
  only `app/**` while its gates read all three app modules, so this cycle's
  Pixel Neon-only fix — the dead no-results buttons below — would have merged
  without a single check run against it. All seven are wired in now: the
  stdlib ones into `suite-ci.yml`, which has no path filter and so runs on every
  push and PR, the app-relevant ones into `build.yml`, whose filter gained
  `pop/**`, `pixel-neon/**` and `docs/**`. `tests/test_ci_coverage.py` is the
  meta-gate that fails if a test file or a `check_*`/`validate*` tool ever stops
  being invoked; local-only is now an opt-in carrying a written excuse, and
  `check_glyph.py` is the one tool that takes it.
- **`tests/test_changelog_contract.py`: a duplicate `### Added` hid a bullet.**
  The menu-audit gate below was
  appended to `[Unreleased]` under its own `### Added` heading rather than
  merged into the one at the top, and `prepare_release.py` finds each kind with
  a single `re.search` - first match wins. The bullet was correctly formatted,
  correctly placed in the file, and invisible to the stamper, so
  `Latestrelease/version.json` would have shipped eight highlights that left
  out a feature this release adds. No gate read the CHANGELOG at all. The
  section is merged and ordered Added, Changed, Fixed; the new test asserts
  that order, that no section repeats a kind heading, that every
  `[Unreleased]` bullet carries the bold lead the card renders, that the two
  gate receipts stay off the card, and that `1.8.7` remains the only version
  ever stamped without a tag. The kind vocabulary is a ratchet from 1.8.20, so
  history (1.8.6's two `### Fixed` blocks, 1.8.7's Colour/Style/Verified) is
  left exactly as published. Each rule was broken on purpose in a scratch copy
  and the test had to notice.
- **Pixel Neon's no-results state was a blank grid with two dead buttons.**
  `activity_main.xml` carried the empty state - title, explanation, "Clear
  search", "Show all N icons" - with both buttons `clickable="true"` and no
  code behind any of it: `applyFilter()` never toggled the container, so a
  filter with no match rendered an empty grid and no way back but backspacing,
  and had the container ever been shown the buttons would have swallowed
  presses. Ported Core Builds' `bindEmptyState`/`bindEmptyActions` and the
  chain's "down from the chips names whatever is on screen" edge into the
  fork. Found by the menu audit above, which is why the audit is now a gate.
- **A pending update could collapse the catalogue to chrome.** The bar stacked
  every highlight in `version.json` — eight at present, ~160dp of bullets —
  under the label and the download explanation inside fixed chrome whose
  remainder is the grid. On a 1080p panel, which reports 540dp, that left the
  weighted grid zero height: no tiles, and a focus target that renders nothing,
  i.e. the "lists disappeared" report with a network callback as the trigger
  instead of the D-pad. The bar now shows the release's first highlight, one
  ellipsised line, in place of the download explanation (never both), which is
  also what the generated update frame draws.


- **Two UP presses from the search box made the catalogue look dead.** The
  vertical D-pad chain runs `apply_targets → update_bar → chip_row`, and both
  header containers are GONE unless something shows them — the update bar only
  when a newer manifest exists, the target row only when a second launcher is
  installed. Both were still focusable while hidden, and `isFocusable()`
  ignores visibility: UP from the search field parked the ring on the invisible
  bar, the next UP on the invisible target row, and from there UP/DOWN
  ping-ponged between two views nobody can see while the chips and the grid
  went unreachable. From the sofa that read as "the lists disappeared", and the
  earlier "press right and they disappear" report was the same hole entered
  from another edge. The layout comment claimed FocusFinder collapses a chain
  through a GONE target by following the target's own nextFocus; the device
  report is the counterexample, so the chain is now explicit instead of
  folklore: both containers carry `focusable="false"` so a hidden one can never
  hold the cursor, and a new `MainActivity.syncFocusChain()` rewrites every
  edge that used to route through them to the nearest VISIBLE stop — the bar's
  button when the bar is shown, the target row when only that is, the
  wallpapers entry otherwise, and the empty state's undo below the filter row
  when the grid is the container that just went GONE. It runs at each of the
  five places that toggle a container, in Classic and Pop (shared Kotlin) and
  in Pixel Neon's fork, which carried the same hole.
  `tests/test_search_focus.py` gained three checks: no `visibility="gone"` view
  in any of the three modules' main layout may declare `focusable="true"`, the
  chain is resynced at every visibility toggle, and the wallpaper preview's
  decode callback reveals without grabbing (below).
- **The inspector listed no components and Launch was a dead button.**
  `componentsFor()` matched `drawable="<square name>"` in the bundled
  `appfilter.xml`, but every one of the 1765 component items in that file maps
  to the *banner* drawable — the square glyph reaches launchers through
  `drawable.xml`, not through a component mapping — so the match came back
  empty for every tile in the pack: the component list rendered blank and
  **Launch app** toasted "No component maps to this drawable in appfilter.xml"
  on icons that are mapped four ways. The pattern now accepts the `_banner`
  suffix (non-capturing, so `groupValues[1]` stays the component), in Classic
  and Pop. `tests/test_ui_wiring.py` locks both halves: the suffix in the
  pattern, and a data check that no drawable in `icon_pack.xml` is absent from
  `appfilter.xml` altogether. The mockup generator is what caught it — rendering
  the inspector frame from the asset produced an empty component list.
- **The wallpaper preview yanked the cursor back to Set mid-browse.**
  `decodeAndShow` ended in an unconditional `requestFocus()` on the primary
  action, and it runs on a download callback — which resolves whenever the
  network likes, including after the user has D-pad right onto the next
  wallpaper and moved the cursor to Save there. Same class of defect as the
  update bar's grab fixed in 1.8.11/1.8.21, same rule: reveal, and take the
  cursor only when nothing has been chosen yet (no focus, the decor view, or
  the Back button the screen opens on). Fixed in `app` and mirrored into Pixel
  Neon's fork.

## [1.8.21] — 2026-09-19

### Fixed

- **Searching moved the cursor out of the search field** — a tester's report of
  "weird focus behavior for the keyboard", which turned out to be six separate
  defects in the one path. None was reachable from a layout check: the focus
  chain was intact and every control on the screen was reachable, which is what
  `check_ui_resources.py` asks. What nothing asked is who owns the cursor while
  the user types. Fixed in `app/src/main/java`, so one change covers Classic and
  Pop. Pixel Neon keeps a deliberate fork of this screen and still carries four
  of the six — the update-bar grab, the per-keystroke filter, the dead Search
  key and the animated chip rows; it has no empty-state or focus-restore logic
  at all, so the other two have nothing there to fix yet.
  - `showUpdateAvailable()` ended in an unconditional `button.requestFocus()`,
    and it runs on `UpdateChecker`'s network callback — which resolves whenever
    it likes, commonly about a second after launch, which is exactly when
    somebody has reached the search field and started typing. The Download
    button took the cursor mid-word and the keyboard went down with it. 1.8.11
    fixed the bar's *reachability* and kept the grab deliberately; the grab was
    the bug. The bar now reveals, and takes the cursor only when nothing has
    been chosen yet — no focus, the decor view, or the Apply button this screen
    opens on. The startup grab is guarded the same way, matching
    `WallpapersActivity`.
  - **A filtered-out icon had no destination.** `applyFilter` handled only the
    case where the icon under the cursor survived the filter. When it did not,
    RecyclerView's own preserve-focus-after-layout pass found the remembered
    item id gone and handed focus to its first focusable child: the ring
    teleporting to the top-left tile. The cursor now stays on its icon or moves
    to the nearest survivor, measured in catalogue order because that is the
    only distance that survives a filter — both the old list and the new one are
    order-preserving subsets of it.
  - **Surviving a filter jumped the grid anyway.** The surviving case scrolled
    to the icon's new position, and `scrollToPosition` pins a position to the
    *top* of the viewport, so a filter that left the icon exactly where it was
    still dragged the whole grid up to it. A tile that is already laid out now
    takes focus directly and RecyclerView brings it on screen the way it does
    for any focus move; only an off-screen target is scrolled to first.
  - **Hiding a container left the cursor nowhere to live.** `bindEmptyState`
    swaps the grid and the empty state on every filter, in both directions: the
    grid goes `GONE` when the last result goes, and the empty state goes `GONE`
    when results come back — which is exactly what pressing its own **Clear
    filter** does, with the cursor on the button being hidden. Android either
    drops focus outright and restores the window's own default on the next
    traversal (the Apply button at the top of the screen, two stops away from the
    results being read), or leaves it on a view that is no longer shown: no
    highlight, key events still landing on it, and the next D-pad move computed
    from a rectangle that no longer exists. Both are now caught after every
    filter and the cursor is put on the first tile, or on whichever undo the
    empty state is actually offering. The check only ever fires when focus was
    lost, and only for the views that swap, so it cannot interrupt typing — nor
    move a cursor the user left in the search field while the activity sits
    behind another window.
  - **The keyboard's Search key was dead.** The field declares
    `imeOptions="actionSearch"` and nothing registered an
    `OnEditorActionListener`, so the one key that means "I have finished typing"
    fell through to TextView's default: hide the IME, move nothing, and leave
    the user pressing Down to find out whether the search had worked. It now
    closes the keyboard and puts the cursor on the first result, or on the empty
    state's undo when there are none. Enter on a hardware keyboard arrives as
    the same action and does the same thing.
  - **A chip press dropped the chip's own highlight.** All three chip rows on
    this screen — categories, the picker's banner/square pair, and "Also
    &lt;launcher&gt;" — kept RecyclerView's default item animator.
    `ChipAdapter.select()` answers a press with two `notifyItemChanged` calls,
    and the default change animation swaps the pressed chip for a fresh
    ViewHolder and cross-fades the pair, so the highlight vanished on the press
    that was supposed to move it. `WallpapersActivity` already set
    `itemAnimator = null` on its chip row, with a comment saying the main screen
    did the same; it did not, and now does.
- **The presence pass could die mid-build on a mark flush against SAFE.**
  `keyline_radius()` caps the ring at the headroom actually available outside
  the ink, and four marks sit at exactly the SAFE margin, so their cap is zero —
  and a zero radius reached `MaxFilter(1)`, a 1x1 rank window. A 1x1 max filter
  is mathematically a no-op (the ring would subtract to empty), but Pillow's C
  rank filter divides by the window area, so on current Pillow the build died
  with SIGFPE on the fifth icon instead of skipping the ring. `apply_presence`
  now returns the source untouched when there is no headroom, which is what the
  cap always meant; shipped pixels are identical either way.

### Changed

- **The keyboard follows the search field.** A focus gained by D-pad does not
  reliably open the IME — the platform shows soft input for touch-mode focus —
  so the field asks for it explicitly on focus, and puts it away when the cursor
  leaves for the grid or a chip, where it would otherwise hang over the results
  the cursor just moved into.
- **Typing filters as a burst rather than per character.** One keystroke used to
  mean one pass over 943 icons, one `DiffUtil.calculateDiff` and one full grid
  layout, all on the main thread inside `afterTextChanged`. Keystrokes are now
  debounced 120ms — longer than a remote's key repeat, shorter than the pause
  between words — and the diff drops move detection, which cannot report a move
  for an order-preserving subset and so cost a second pass over the matched
  items per keystroke to compute nothing. A chip press, a Clear and the Search
  key still filter at once and cancel anything queued; a queued pass is flushed
  in `onPause` so it cannot decide focus against a window nobody is looking at,
  and dropped in `onDestroy`, which it outlived before. When the cursor is outside the grid the
  results scroll to the top, because the set just changed and the tail of the
  previous scroll position is not where anyone wants to look.
- **Janky's mark got its weight fixed, not its composition replaced.** The
  Projectivy screenshots ("See the uniformability. Also the new Janky icon seems
  like it is sized wrong") measured out exact: the ring-and-beside-play lockup
  shipped in a 0.80 x 0.47 ink box, aspect 1.71, against a pack median of
  0.78 x 0.74, aspect 1.05. A first pass (RB4) read that as a composition fault
  and promoted the ring to a full-size container with the hook and play inside
  it. The user rejected that build — "the version before look better then this
  current one. I just needed some weight fixing etc" — and the measurements
  agree with them: ink coverage was never the problem (RB3 sat at 0.152 against
  a pack median of 0.173, lighter than 290 of 477 tiles). The fault was
  *relative stroke*: 32px on a 240px ring is a 0.133 stroke ratio where the
  pack's container rings (mpv 32/388, stremio 32/389) sit at 0.082, so the small
  ring read a step and a half chunkier than every ring beside it — which is
  exactly what "sized wrong" looks like at a 100px tile, stroke being mass in
  every iconography reference consulted. RB5 therefore restores the approved
  lockup and re-weights it: ring 26 on a 264px outer (ratio 0.098, inside the
  container family's band), hook 22, play 24 — one step down the house weight
  vocabulary, hierarchy intact — and the taller ring lifts the ink box from
  0.47 to 0.52 of the grid, towards the optical grid's horizontal-rectangle
  proportion (wider *and* shorter than the square, never flatter) instead of a
  flat band. Width stays 0.82 inside SAFE, coverage lands at 0.147, counters
  hold 2→2→2 at 96/48/32, and the collision pass keeps it far from any twin
  (nearest 0.761). Paints untouched — off-white hook, cyan-to-violet ring and
  play. Regenerated through the full pipeline for Classic, Pop and Pixel Neon,
  including `measure_pop_glyphs.py`, because glyph geometry changed.
  `tests/test_icon_uniformity.py` (new gate, in `build.yml` and `suite-ci.yml`)
  now locks both failure classes: container-grammar marks must span 0.55-0.90
  of GRID in both axes at aspect 0.80-1.30 (Janky is deliberately *not* on that
  list — it is a lockup, and the optical grid gives lockups a wider, shorter
  box on purpose), and no shipped tile glyph may carry the flat full-width band
  signature RB3 shipped with (aspect > 1.65 at 0.44-0.50 height and >= 0.78
  width) — the rule that still fails the 1.8.20 geometry today.

### Added

- **The on-device missing-app auditor, with a QR code that prefills the
  request.** Settings → HELP → *Scan for unmapped apps* lists every launchable
  app on the TV that `appfilter.xml` never names — the scan needs no new
  permission, because two intent-filter entries in `<queries>`
  (`ACTION_MAIN` + the leanback and plain launcher categories) make every
  launchable package visible to `queryIntentActivities`, which is the same
  Play-safe grammar launcher detection already lives under and the reason this
  never reaches for `QUERY_ALL_PACKAGES`. Pressing a row draws a QR code that
  opens the icon-request issue with the app name, the exact component and a
  device note already filled in. The deep link is generated, not typed:
  `tools/build_issue_prefills.py` now also writes
  `res/values/issue_prefill.xml` from the issue form (template slug, labels and
  title prefix baked in, three positional arguments the app URL-encodes), and
  its `--check` gate fails on drift, so a renamed form field is a CI failure
  rather than a silently empty box on GitHub. The encoder is vendored rather
  than hand-rolled — Nayuki's QR Code generator (Java, MIT, upstream
  `3c6d0b3`, checksums and licence in `THIRD_PARTY_NOTICES.md`) — with
  `QrBitmap.kt` as the wrapper: error correction M for sofa-angle photography,
  the four-module quiet zone drawn into the bitmap so dark chrome cannot
  swallow it, black-on-white modules. Back leaves the QR panel before it
  leaves the screen. Mirrored into Pop and Pixel Neon's parity surface; the
  wiring gate grew an auditor block that reads the `<queries>` span itself,
  because the app's own intent-filter declares `LEANBACK_LAUNCHER` and a
  whole-manifest grep would pass on the wrong occurrence. Mockup with a real
  scannable code: `docs/app-ui-auditor.png`.
- **Sideload round one: launcher tools, a sofa FAQ, and a what's-new bar.**
  The enhancement proposal is committed verbatim at
  `docs/NON_PLAYSTORE_ENHANCEMENTS.md` and triaged feature by feature against
  the source in `docs/NON_PLAYSTORE_TRIAGE.md`; this is the first tranche of
  it. Settings gains a LAUNCHER group — **Refresh launcher icons** re-fires the
  detected launcher's apply contract through `ApplyIconPack`, **Launcher app
  info** opens the system details page where a force stop clears a bitmap cache
  re-applying cannot reach — and a HELP row into the new `FaqActivity`: four
  read-only cards paraphrasing the docs that actually hold the knowledge
  (`WHY_PROJECTIVY_CANT_SEE_IT`, the Projectivy override behaviour,
  `MONET_LAUNCHER`, `ADB_SCANNING`), in the Settings chrome grammar with the
  cards deliberately unfocusable so the D-pad never lands on a paragraph. The
  update bar grows release highlights when `version.json` carries a
  `highlights` array (optional field, capped at six, view gone unless non-empty
  so older manifests render the old bar exactly), and the category chips carry
  counts tallied from the same generated arrays that feed the grid. The
  proposal's `killBackgroundProcesses` and `QUERY_ALL_PACKAGES` routes were
  declined with reasons in the triage; the QR auditor, icon masking, inspector,
  suite hub and bumper skips are queued there with what unblocks each.
  Mirrored into Pop (same Kotlin, own resources) and Pixel Neon's parity
  surface; the wiring gate grew a sideload-rows block covering row wiring in
  both modules, both manifest registrations, the FAQ's single focusable and the
  highlights gating. Mockup: `docs/app-ui-sideload-round.png`.
- **`artemis_pad` — the Artemis mark the tester actually chose.** Artemis
  (Moonlight's noir fork, `com.limelight.noir`) shipped in 1.8.20 as
  `retro_pad`, the straight-sided shell, and the tester's verdict on the
  rendered candidates was not subtle: "I think the controller of Moonlight
  looks better than this, so maybe you could do something similar. The
  original looks a bit generic", then "Let's go with the controller", the
  winged pad on the right of both contact sheets. Similar, not identical:
  Moonlight and Daijishou already carry `gamepad` side by side in the same
  launcher row, so the new glyph keeps the family silhouette and earns its
  own grammar — a body about 6 percent narrower than `gamepad`'s with a
  deeper waist notch, and a start/select dash across the middle that
  `gamepad` does not carry (nearest-glyph similarity 0.877 against the
  0.965 twin ceiling). The catalog entry keeps its drawable name
  (`limelight`), its reviewed violet and its device-evidenced component;
  only the glyph reference moves. Three passes of the counter gate are
  recorded in the glyph's docstring: ring buttons closed at 96px
  (4 → 1 → 1), and the second pass's closing counter turned out to be a
  3px LANCZOS ringing sliver inside the left wall at the 256px measure —
  a resampling phase artifact, not geometry, cleared by widening the body
  two units per side (1 → 1 → 1). `retro_pad` retires with its design
  notes intact for whoever asks for a straight-sided shell again.
- **`tests/test_search_focus.py`** — 15 static checks over the search path, in
  the same shape as `test_tv_layout_fit.py`: scoped to one function body at a
  time, so a `currentFocus` read somewhere else in the file cannot satisfy the
  check on the grab, and comment lines are dropped before a line is scanned, so
  a comment quoting the call it replaced cannot satisfy the check either. 14 of
  the 15 fail against the pre-fix source; the 15th locks the layout routes the
  Kotlin half depends on (`imeOptions`, and the `nextFocus` edges between field,
  grid and tile). Named in `build.yml` and `suite-ci.yml`, because CI runs these
  suites file by file and `unittest discover` does not see them.
- **`tests/test_ui_wiring.py`** — static gate over the manifest contract and
  the screens around the main grid, named in `build.yml` and `suite-ci.yml`.
  It locks the five declared permissions in both manifests (with
  `WRITE_EXTERNAL_STORAGE` capped at `maxSdk 28`), the `<queries>` entries
  every package-visibility-filtered probe depends on (Monet and Projectivy
  packages, the HOME intent, the apply contracts), the wallpapers screen's
  three focus guards (starting focus, both item animators off, focus handed
  to the header export when the selection bar goes `GONE`), the export
  screen's cancel and retry focus, and `FLAG_GRANT_READ_URI_PERMISSION` on
  every FileProvider intent that leaves the app. Born from a permissions
  audit whose verdict was that the set is complete and deliberately minimal
  - nothing to add for any shipped feature - and that the interesting
  surface was never the permissions but the visibility contract and the
  wiring around it. Deliberately *not* declared, and why:
  `QUERY_ALL_PACKAGES` (Play-sensitive; the targeted `<queries>` covers every
  probe), `MANAGE_EXTERNAL_STORAGE` (scoped storage needs nothing past API
  28), `POST_NOTIFICATIONS` (no notifications on a TV surface),
  `READ_MEDIA_IMAGES` (export only ever inserts its own MediaStore rows).

## [1.8.20] — 2026-09-18

### Added

- **Twelve-wall series convention** — Circuit Core gains `Circuit Nexus` and
  `Circuit Vault`, Retrowave gains `Laser Dusk` and `Ember Horizon`, and the
  AMOLED set gains `Eclipse` and `Corner Signal`. Every active series now has
  12 walls, except the original Fieldwork double set at 24; the classic total
  is 84.
- **Series 8 AMOLED wallpapers** — twelve deterministic 4K designs on exact
  `#000000`, with sparse Core cyan, violet, blue, and ember accents. Every wall
  retains 92.4–99.8% true-black pixels; the generator and wallpaper tests both
  enforce a minimum of 50%. Includes bundled thumbnails, manifest entries
  69–78 and 83–84, and `docs/amoled-wallpapers.png` as the visual receipt.

- **Isolated test APK lane** — `:app:assembleCandidate` builds
  `tv.corebuilds.iconpack.test` with debug signing and a `TEST_SOURCE_COMMIT`
  stamp, so a candidate installs alongside production instead of over it and
  never reaches the in-app updater. Driven by `iconpack-test-apk.yml`, which
  attaches the APK and its SHA-256 to an unpublished draft release; production
  signing material is never read.
- **Objective glyph identity checks** — `tools/test_glyph_identity.py` measures
  counter survival at 96px and 48px, safe-area margin against the raster
  presence pass, and similarity against every glyph in the registry, so a new
  mark cannot quietly duplicate one already shipping.

- **Brandmark badge and filter** — 526 of the 940 icons are monograms: a letter
  in one of the 15 `FAMILY_SHELLS` containers. The other 414 are drawn marks,
  and nothing in the grid said which was which, so the hand-drawn work was
  invisible unless you already recognised the brand. Tiles carrying a drawn
  mark now show a small ink dot in the icon's corner, and a **Brandmarks** chip
  filters to them. The flag is generated into `R.array.icon_bespoke` by all
  three pack generators from `glyphs.MONOGRAM_GLYPHS`, which is derived from
  the shell and tile registries rather than pattern-matched off glyph names —
  a regex would misclassify any mark whose name begins with a family word. The
  dot is ink, not signal cyan, because the focus ring is cyan and a cyan dot on
  a focused tile reads as part of the ring. `tests/test_brandmark_badge.py`
  checks the array is one flag per icon in every pack, that each pack agrees
  with `tools/catalog.json`, and that the three packs agree with each other.
  `tools/validate_pixel_neon.py` counted raw `<item>` elements against
  `3 * len(icons)`, so a fourth generated array failed it with "does not
  contain three complete generated arrays" — a message naming the wrong
  problem and no array in particular. It now counts each array by name and
  says which one is short.

- **Settings and About screens** — the app's first persisted state. Until now
  nothing was stored at all. Four settings, each wired to a reader rather than
  to a screenshot: update checks gate `UpdateChecker` (off means the app makes
  no network request of its own), reduce motion gates the chip focus animation,
  AMOLED chrome repaints the window `cb_void`, and the storage row clears the
  directory `WallpaperDownloader` actually writes to. About states the complete
  list of what the app sends — two requests, both user-initiated — as an
  enumeration rather than a reassurance, so it cannot stay true while the
  behaviour drifts. Reached from the Home header, inside the existing D-pad
  chain rather than behind an overflow menu. Pop mirrors both screens through
  its generator; Pixel Neon has its own Kotlin and is untouched.

### Changed

- **Twelve generic constructions became bespoke marks** — AirScreen, Aerial
  Views, AnyDesk and DW; Crossy Road, Blokada (legacy v4), Private Internet
  Access, mpv, Audiomack, ATRESplayer, Kinopoisk and AIDA64. Each is original
  Core Builds geometry drawn from the identifying cue recorded in the
  2026-09-15 tranche research — no vendor artwork is traced, and no fill,
  container or effect is introduced. Package and activity mappings are
  unchanged for every one of them; only their glyph references moved. The
  registry is now 966 glyphs.
- **DW redrawn as strokes** — its first form filled the right body solid and
  cut the W out with an SVG mask. That broke the monoline contract two ways:
  `<defs>`, `<mask>` and solid fills are all forbidden, and two of its four
  counters closed by 48px, leaving an unreadable blob at launcher size. Ink sat
  at 30.8 per cent against a 34 per cent slab ceiling — inside it, but with no
  headroom. Now two overlapping outlined bodies with a drawn D and W, 23.4 per
  cent ink, legible at 48px, and opted into `core_monoline` so the mask cannot
  return.

- **Icon pack test builds are now a public channel** — `iconpack-test-apk.yml`
  published to an unlisted draft, which no sideloader could reach because a
  draft has no git tag and its assets 404 without credentials. It now
  force-moves a floating `iconpack-test` tag and publishes a prerelease
  carrying one fixed asset name, `iconpack-test.apk`, so a single Downloader
  code generated at go.aftvnews.com keeps resolving after every rebuild. The
  build still runs candidate code with `contents: read`; the publishing job
  still never checks that code out, and moves the tag through the API rather
  than a checkout to keep it that way. Debug signing only — no `KEYSTORE_*`
  value is read, and the production `iconpack` release is untouched.


- **Janky Player's mark is now the shipped app's own tile mark.** Research on
  2026-09-19 found the app nowhere in public - no Play, APKPure, Uptodown or
  F-Droid listing, and a GitHub code search for its package id
  `com.player.janky` returns only this repository's own generated files - so
  the only sources are the device and the user-supplied rebuild sheet. The
  vendor wordmark (an anarchy A with a hamster in the ring, which the earlier
  glyph copied) is banner art; the launcher tile is a heavy J-hook cradling a
  play triangle in the app's accent, apex welded to the stem. core_monoline
  translates that twice: the shipped solid triangle becomes a 26.2 outline
  nested in the bowl (its counter still holds open at 32px), and the sheet's
  two-tone white-hook-plus-accent-triangle becomes the single accent,
  #1E88E5 - the default the user-themable slate aggregator ships. `check_glyph.py` at
  96/48/32px: ink 10.9 percent, counters 1 -> 1 -> 1, bbox
  (112,80,352,416) inside the 40px safe area, core_monoline clean. The
  colour's pre-provenance amber claim is gone; `color_reviewed` is true with
  the sheet as its source. Revised once more the same day on tester feedback
  (RB3, via Discord): smaller J, circle around it, bigger play sign - so the
  hook now hangs at the detail weight inside its own 32px ring, the circle
  the vendor's anarchy A wore on the banner, beside a free-standing
  primary-weight play sign 200px tall against the previous 136.
  `check_glyph.py` again: ink 16.9 percent, counters 2 -> 2 -> 2, bbox
  (56,134,468,382). Final pass the same day, on the pack's own weight
  grammar: the ring is the sole 32px primary with the J and the play sign
  both at 26.2 - core_monoline's rule keeps the detail weights subordinate
  rather than flattening every level to 32, the way `browser_globe` and
  `play_hex` hierarchise their containers - and the lockup sits centred on
  the 256 axis. Ink 16.3 percent at 48px, counters 2 -> 2 -> 2, bbox
  (56,136,465,376). Closed last by the user, from the banner render they
  approved: the floating lockup is the final composition - the receiver
  stand and aerials drafted for the IPTV family are retired here - and it
  wears the two paints they named. The J in off-white ink (#E6EDF3, the
  Brand Guide ink): the shipped tile is a white hook cradling an accent
  triangle, and flattening that two-tone into one accent had been a
  core_monoline limitation rather than a design choice. And a gradient: the
  banner rail's cyan-to-violet ramp (#00D4FF to #A78BFA, the left edge of
  the approved render) extended into the ring and the play through the
  catalog's declared `gradient`, the same mechanism nuvio and tizentube
  ship, so tile, banner and rail share one paint story. core_monoline grew
  two declared extensions for this - a catalog `gradient`, and a catalog
  `ink` sanctioning exactly one reviewed secondary paint - cross-asserted
  by validate.py and build_icons.py, so undeclared whites still fail.
  Geometry is unchanged from the weight-grammar pass, so its receipts
  stand: ink 16.3 percent at 48px, counters 2 -> 2 -> 2, bbox
  (56,136,465,376). The app's GitHub org (jankyapp/jankyapp - a readme stub
  with fourteen beta releases, v0.95.47-beta shipped 2026-09-19) was found
  the same day; its APK assets and org avatar are unreachable from this
  sandbox, so the sheet remains the pixel source.

### Fixed

- **Android CI setup outage** — PR workflows use the SDK already installed on
  GitHub's Ubuntu runner and call its pinned `sdkmanager` path directly instead
  of failing inside `android-actions/setup-android` before compilation starts.
- **Nuvio TV inner play mark off-centre** — the wedge inside the ring sat left
  of centre; the icon and banner are re-rendered in both Classic and Pop. This
  is the thirteenth icon to change since 1.8.19 and was missing from the twelve
  listed above, which describe glyph replacements rather than this correction.

- **Settings clipped its last row off a TV screen** — found by a tester on the
  first `iconpack-test` install, before any of this reached a tagged release.
  The screen shipped with no scroll container on the reasoning that its four
  rows fit the 1080p safe area; that height was taken off a 1920×1080 mockup in
  pixels, and Android lays out in dp, where a 1080p TV is 960×540dp. The
  content needed about 615dp, and a plain `LinearLayout` holding a `weight=1`
  spacer clipped the rest in silence — no scrollbar, no focus escape, the Clear
  button and the footer simply unreachable. The rows now sit in a `ScrollView`
  with the header and footer pinned outside it, so nothing non-focusable is
  stranded past the end of a D-pad chain, and trimming (the kicker inline with
  the title, tighter section and row spacing, local gutters) brings the content
  to about 520dp so a default-scale TV does not scroll at all. A new suite,
  `tests/test_tv_layout_fit.py`, re-derives these heights from the XML and
  fails any full-screen layout that overflows the viewport without a scroll
  container — it reproduces the 615dp independently, and is named in
  `build.yml` and `suite-ci.yml` because CI runs these suites file by file and
  `unittest discover` does not see them. It measures all three modules against
  their own `dimens.xml` rather than trusting Pop's generated copies and Pixel
  Neon's hand-synced ones to match, and their figures do differ. About needs no
  change at 399dp of fixed content; its body is a weighted region holding
  fixed-height cards, so it flexes rather than stacks.

## [1.8.19] — 2026-09-15

### Added

- **Projectivy internal cards** — the long-posted r/Projectivy_Launcher
  request ("a banner shaped icon for … my HDMI outputs so I don't have the
  square HDMI icon that Projectivy use") gets four new entries, all rendered
  from existing glyphs, zero new art: `av_source` (the AV input card,
  `monitor_wave`, the input family's cyan alongside `hdmi_source`),
  `projectivy_settings` (gear), `projectivy_categories` (folder) and
  `projectivy_channels` (tv_stack), mapped to Projectivy 4.70's internal
  activities (Settings + AppSettings, Category/Channel shortcuts, SourceAV)
  in both name forms. All eight components are `unverified` — the launcher is
  closed-source, so they clear at the next ADB device scan; the
  `MappingHygieneTests` ratchet moves 69 → 77 for the batch and is expected
  only to fall from there. HDMI 1–4 keeps the unified `hdmi_source` mark;
  the four numbered input marks are deferred until on-device confirmation of
  whether the launcher labels the cards (see
  `docs/research/community-input-icons-2026-09.md`).
- **Projectivy input marks** — the launcher's remaining pinnable input
  surfaces get their own marks in the input family's cyan: `tv_source`
  (antenna — the tuner; the original request thread calls out "TV … very
  ugly"), `source_input` (a screen with the signal entering — the
  on-screen choose-source menu) and `media_explorer` (USB stick + play —
  the launcher's shortcut to the stock media explorer). Activity names
  follow the launcher's documented `Source{X}Activity` /
  `{X}ShortcutActivity` patterns in both name forms; none is corroborated
  by any public source, so all six components are `unverified` and the
  `MappingHygieneTests` ratchet moves 77 → 83 for the batch — expected to
  clear or be re-pointed at the next ADB device scan
  (`docs/ADB_SCANNING.md`). Component/S-Video/optical stay folded into the
  AV card: Tizen does not expose them as separate inputs. Receipt
  `docs/projectivy-input-cards-2026-09.png`.
- **Nuvio TV launcher activities** — the entry mapped only `.MainActivity`,
  which in the official manifest carries deep links only; on a real device
  the icon resolved from the LAUNCHER activity and so never applied. All six
  launcher activities from the official manifest (`launcher.AppIconDefault`
  plus the five user-switchable icon variants) are now mapped — 12
  components, manifest-verified against NuvioMedia/NuvioTV `dev`
  (2026-09-12), so they cost no unverified budget.

### Fixed

- **Suite-truth gate false positive** — the stale-claim guard's
  `"40 icons"` needle matched the correct current claim `"940 icons"` as a
  bare substring, so the 940-icon catalog tripped its own gate. Count-shaped
  needles now carry a leading-digit lookbehind (the version-shaped needles
  already had the lookahead treatment), so a stale "40 icons" is still
  caught while "940 icons" is blessed.
- **GeForce Now** (`tegrazone3`) — shipped in v1.8.18 but never documented
  in that release's notes, which is how it surfaced as a user report: the
  card showed a "T" letter tile (the launcher's fallback for the Tegra Zone
  label — the legacy brand of the same app, package `com.nvidia.tegrazone3`)
  instead of the mark, and a per-app reset restored the native icon at its
  native size. The shipped asset and the appfilter entry were verified to
  match the reference pack's mapping exactly, so the failure is a launcher
  that could not resolve the drawable from the installed pack's resources —
  a stale pack install or a stale launcher resource cache after a pack
  update. Remedy on-device: update to the latest pack and force a launcher
  refresh (reboot, or re-apply the pack from Projectivy settings). The
  failure class is now closed at the source: new CI gate
  `tools/check_appfilter_integrity.py` verifies that every drawable named by
  every appfilter (Icon Pack, Pop, Pixel Neon — res/xml and assets copies)
  exists in that pack's res tree, and `tools/build_icons.py` refuses to
  write an appfilter that references a missing drawable instead of warning
  and shipping it.

- **Recognisability tranche 2** — twelve entries leave category shells and
  letter tiles for constructions, off the 57.7%-and-falling generic share:
  7plus / Seven Plus get `seven_plusmark` (the Seven Network's 7 carrying the
  plus), 9Now / 9Now CTV get `ninenow_mark` (the nine with the play that says
  "now"), 10 Play gets `ten_mark` (the 10 lockup — the last `tile_*` retired),
  ABC iview gets `abc_lollipops` (the centre seed with its petal ring), Māori+
  gets `maori_koru` (the unfurling fern frond), JioHotstar gets
  `hotstar_spark` (the star with its glint), and Magenta Sport gets
  `magenta_t`. Neon, Crave and Hayu take Outfit monograms like every other
  wordmark brand since v1.8.18. Receipt: `docs/icon-tranche2-2026-09.png`.
- **Verification tranche** — 464 of the 533 `unverified` components are
  cleared as corroborated verbatim by the published Projectivy Icon Pack
  1.1.9 appfilter (the reference-pack inheritances the mapping was seeded
  from). 69 remain, and `MappingHygieneTests` ratchets the count: the
  ceiling may only move down, and a re-added corroborated component fails
  the suite. Protocol: `docs/research/mapping-verification-2026-09.md`.
- `tools/verify_mappings.py` — report/clear corroborated components against
  the reference pack; the next tranche (the surviving 69) is one command.
- `tools/prepare_release.py` — stamps all eight version surfaces (Gradle
  name/code, catalog, `Latestrelease/version.json`, `suite.json`, README
  suite-stamp, `docs/IconPackList.md`, CHANGELOG), rebuilds the five
  text-asset builders, and runs the gate suite before the commit.
- `tools/sync_wallpaper_manifest.py` — keeps the bundled wallpaper
  manifests and thumb sets in the APKs byte-identical to the repo;
  `Wallpapers/README.md` step 4 now runs it instead of copying by hand.

### Changed

- Magenta Sport's accent is now Telekom Magenta `#E20074` (it was carrying a
  borrowed Jio blue); `magenta_t` reads in the brand's own colour.
- The diversity gate in `tests/test_icon_identity.py` re-based to the wider
  "generic" definition (a `tile_*` or any category shell carrying a bare
  letter/digit; Outfit monograms stay bespoke): ceiling 57.7%, floor 395,
  and a test that no `tile_*` glyph remains. The researched-emblem set gains
  the nine tranche-2 drawables.
- Seven new marks join the `core_monoline` style contract; the banner, Pop,
  and Pixel Neon sets are regenerated to match.
- **Letter-mark pass (owner direction)** — a single letter is only carried
  when the letter itself is part of the original logo; the four wordmark
  brands that shipped invented initials are redesigned against their marks:
  Crave (`bellmedia`) becomes `crave_c`, the wordmark's leading c in its
  own case, in the published CraveBlue `#00A9EE` (was a palette purple);
  Hayu (`hayu`) becomes `hayu_y`, the 2022 wordmark's signature y with its
  sweeping descender, in the rebrand's pink-red `#FF285A` (was palette
  red); Neon (`neon`) becomes `neon_tube_n`, the app mark's own bent-tube
  segments with open joints, in the app icon's acid green `#C8FB34` (was a
  simple-icons green); and Magenta Sport (`magenta_sport`) gains the
  Telekom t-dot at the crossbar shoulder — the parent brand's actual mark,
  which also closes the Magenta Sport wordmark. Single letters that are
  the logo (the 7+, 9 and 10 network numerals) are untouched. Receipt:
  `docs/icon-lettermarks-2026-09.png`.
- **TizenTube** (`tizentube`) — the v1.8.18 fix read as "kinda random"
  (its chord floated under the wedge, a stroke the real mark does not
  have), and this day's iterations — the measured globe emblem, then the
  frame with the logo's open wedge — kept narrowing the direction until
  the final one (owner-directed): use the exact TizenTube logo within
  the box, in the pack's style. So the whole official emblem
  (reisxd/TizenTube standalone banner, art by @Zyborg777) now sits
  inside the normal YouTube frame — the pack's own `yt_play` frame,
  never the vendor mark: the two-arc globe (the main circle's arc
  meeting the left circle's arc at the rim intersections), the play's
  left edge, the struck top chord running rim to rim, the bottom edge
  ending in its free rounded cap, and the tip dot below the chord,
  right of the free cap, as in the art. Every coordinate was measured
  off the banner (main circle c(378.6,430.8) r215.0, left circle
  c(323.9,445.4) r202.8, stroke 34; dot an ellipse 22.9×16.6 rotated
  −28.9°) and rebuilt at 0.4563× in the pack's monoline language,
  emblem strokes on the lightest weight (21.8 after normalisation) so
  the construction stays open at tile size — the dot held 6px clear of
  the chord the heavier stroke would otherwise close. `tizen_play`
  carries the whole emblem and `tizen_play_dot` (the 16:9 card, the
  appfilter's auto-assign target) shares the same construction, banner
  and square included. The accent is the brand's true paint: the
  official icon's field gradient `#47DDFF` → `#C5E9FF` (measured top
  (71,221,255) and bottom (197,233,255) of the icon's field), applied
  at render time through the catalog `gradient` field — the Nuvio
  pattern — so Pop and Pixel Neon keep their flat strokes. Decision
  records `docs/tizentube-options-2026-09.png`,
  `docs/tizentube-emblem-2026-09.png` and
  `docs/tizentube-frame-2026-09.png` (the interim passes); receipt
  `docs/tizentube-inframe-2026-09.png`.
  Round 9 (owner: "in the correct direction, just needs polish"): the
  mark kept as-is, its geometry refitted. The round-7 circles came out
  of junction-contaminated sampling and were systematically too large —
  the main circle 12px, the left 25px — which stretched the globe and
  ran the chord past the true rim. Clean per-arc ray samples (junctions
  and wordmark excluded, least-squares fit) give main
  c(372.2,434.2) r202.8 and left c(314.7,442.9) r177.5, rim
  intersections (236.4,283.6) / (286.8,618.1), vertical x=242.5 from
  the chord to the left arc (y=605.1), chord (242.5,261.5) to the true
  right rim (574.5,447.1), bottom-edge caps (289,595.5)-(462,495.5);
  the dot is unchanged (22.9×16.6, -28.9 deg), still held ~5px off the
  chord so the heavier stroke keeps the art's near-touching gap open.
  The reconstruction overlays the banner at 3.5% pixel mismatch; scale
  re-set to 0.4570x about the frame centre to hold the round-7 optical
  size; gradient and lightest monoline weight unchanged. Receipt
  `docs/tizentube-polish-2026-09.png`.

### Removed

- The last `tile_*` glyph (`tile_10` on 10 Play).

### Receipts
- Classic: `Validated 940 icons · 1150 components · 25750 checks run`,
  `Ran 199 tests ... OK`.
- Pop: `Validated 940 icons · 1150 components · 16 swatches ·
  14679 checks run` (glyph metrics re-measured first — new input marks
  and the re-centred TizenTube dot changed geometry).
- Pixel Neon: `Validated Pixel Neon · 940 icons · 1150 catalog
  components · 13057 checks run`.
- Truth gates: suite truth, contract audit, appfilter integrity,
  issue-prefill stamp and wallpaper manifest all pass.
- Full sweep: `Ran 199 tests ... OK` across every test module,
  wallpaper manifest and thumbnail gates included.

## [1.8.18] — 2026-09-14

**Feedback pass: the TizenTube stray line, the Nuvio lookalike, the
six-app request list (Fandango at Home, Pluto TV, Tubi, LocalSend,
Hi Browser, Screen Recording App), the Indonesia pair (BitTV, Vidio),
and the retrowave wallpaper series.** 931 → 933 icons, 1120 → 1130 components.

### Changed
- **TizenTube** (`tizentube`): the diagonal no longer floats as a stub off
  the bottom-left corner. An earlier legibility pass had moved the
  ad-block slash clear of the play wedge and orphaned it; the official
  mark runs that diagonal as a chord from the rim, under the wedge,
  ending in a detached dot — so the line is back inside the construction
  (circle + wedge + chord + dot) instead of reading as a stray stroke.
- **Nuvio TV** (`nuvio`): the pre-research wave-in-a-circle — a mark Nuvio
  does not have — is replaced by the official wedge, and the guessed rose
  accent becomes the brand violet `#A238F0`. Second pass: the recreation
  runs the brand's real gradient — cyan `#2FCCE6` at the top fading to
  the violet `#A238F0` below, painted at render time as a vertical
  `linearGradient` (`tools/glyphs.py` gradient support) — superseding the
  interim two-ink split; the knocked-out centre still reads via the dark
  card. The Pop Art and Pixel Neon variants stay flat by design, since
  those pipelines repaint strokes from a sentinel colour.
- **Icons are glyphs, never wordmarks.** Per the owner's rule an icon
  carries a single letterform, not a name: Tubi, Vidio and BitTV — which
  drew their wordmark (or store mark) as the icon glyph — now wear monogram
  letters cut from the pack's own typeface, Outfit ExtraBold via
  `tools/typeface.py`: Tubi a `t` carrying the brand's shoulder dot (a lone
  t otherwise reads as a plus), Vidio a `v`, BitTV a `b`, each in its brand
  colour. Their banners return to the standard lockup — mark beside the
  printed name, said once — so the interim mark-only `banner_style` is
  withdrawn, and a new test forbids any `wordmark` glyph re-entering the
  catalog.
- **Pluto TV** (`pluto_tv`): planet-with-orbit becomes the disc with its
  planetary echo arcs (2020 lockup device), and the black accent — which
  the contrast policy had to substitute anyway — becomes the 2024 disc
  yellow `#FFF200`.
- **Tubi** (`tubi`): the invented bare-T gate becomes the brand's actual
  identity: first the rounded lowercase wordmark, then — under the glyph
  rule above — the Outfit ExtraBold monogram `t` with its shoulder dot;
  accent corrected to the wordmark yellow `#F5E600`.
- **LocalSend** (`localsend`): the two-phone packet becomes the official
  hub disc ringed by eight beam dashes — a broadcast, not a transfer.
- **Vudu → Fandango at Home**: the entry is renamed and redrawn as the
  notched orange ticket stub with its cut F (`fandango_ticket`,
  `#FF7300`). The app rebranded in place and keeps the Vudu package, so
  the existing `air.com.vudu.air.DownloaderTablet` mapping carries over.

### Added
- **Hi Browser** (`hi_browser`, `com.hisense.odinbrowser`): Hisense's
  Android TV browser on a globe crossed by its orbit ring, brand teal
  `#00A8A8`. Launcher activity is a Play-listing guess, flagged
  unverified in the catalog.
- **Screen Recording App** (`screen_recording_app`,
  `de.twokit.screen.recording.app`): 2kit's TV-first recorder on a screen
  holding the record target. Activity guesses flagged unverified.
- **BitTV** (`bit_tv`): the Indonesian sideload/Play digital-TV app
  (com.live_streaming_tv.online_tv, also shipped as
  com.bittv.androiddigitaltvapp) previously rode the shared
  `iptv_player` glyph under the store name "Live Streaming TV" in
  yellow. Renamed and redrawn in the brand blue `#008FD7` — the icon glyph
  now the Outfit monogram `b` (glyph rule above), the banner the printed
  lockup.
- **Vidio** (`vidio`): Indonesia's Vidio was borrowing Megogo's
  play-banner in a guessed blue. Now drawn in the sampled lockup pink-red
  `#FB0E4D` — icon glyph the Outfit monogram `v` (glyph rule above),
  banner the printed lockup — with the
  mobile `com.vidio.android` package mapped beside the TV one
  (activities guessed, flagged unverified).
- `docs/icon-feedback-pass-2026-09.png`: visual receipt — every changed
  mark beside unchanged Classic neighbours, actual-size banners, and a
  48px dock-size legibility strip.
- `docs/icon-indonesia-request-2026-09.png`: same receipt for the
  BitTV/Vidio pass.
- **series-7-retrowave** (`corebuilds-59`–`corebuilds-68`, ten 4K walls):
  the pack's first retrowave/synthwave series, built on the 2026
  trend research (nostalgic retro-gradient / Y2K among the most-searched
  device-wallpaper genres; Pinterest's 2026 colour forecast feeding the
  palettes). Sliced gradient suns, perspective grids, chrome ridges and
  starfields on the night ground, via the new
  `tools/build_synthwave_wallpapers.py`. Classic manifest 58 → 68;
  `docs/retrowave-series-preview.png` is the contact sheet.
- `docs/icon-monogram-gradient-2026-09.png`: receipt for the gradient
  Nuvio wedge and the Outfit monogram icons — squares above, actual-size
  banner lockups below. (`docs/icon-wordmark-banners-2026-09.png` remains
  as the record of the interim mark-only banner pass.)

### Fixed
- The bundled wallpaper manifest
  (`app/src/main/assets/manifest/wallpapers.json`) still shipped the
  pre-retrowave 58 entries while the repo manifest and the bundled thumbs
  had moved on to 68 — so the in-app grid could never list series 7. The
  bundled copy is re-synced with `Wallpapers/manifest.json`, and the full
  `unittest discover` sweep (193 tests, incl. `test_wallpapers`) is now
  part of the standing pre-release run that caught it.
- README suite stamp said "58 wallpapers" for the Icon Pack row; the badge
  generator's template now prints the true 68.

### Receipts
- Classic: `Validated 933 icons · 1716 components · 25373 checks run`,
  `Ran 46 tests ... OK` (icon identity, incl. the new gradient and
  no-wordmark-glyph guards).
- Pop: `Validated 933 icons · 1126 components · 16 swatches ·
  14510 checks run`, `Ran 29 tests ... OK` (glyph metrics re-measured
  first, as geometry changed).
- Pixel Neon: `Validated Pixel Neon · 933 icons · 1130 catalog
  components · 12829 checks run`.
- Truth gates: suite truth, contract audit, issue-prefill stamp and
  README badge all pass.
- Full sweep: `Ran 193 tests ... OK` across every test module, wallpaper
  manifest and thumbnail gates included.

## [1.8.17] — 2026-09-13

**The launcher icon stops being a brand scene and becomes a pack icon — and
the inputs row finally gets marks of its own.**

### Changed
- Launcher icon redrawn AS A PACK ICON in `tools/build_branding.py`: the
  rounded app tile holding a 2x2 grid of marks, drawn to the catalog's own
  Core monoline grammar (32 / 26.2 rounded strokes, one accent #00D4FF,
  no fills, no effects, transparent ground). The script now asserts the mark
  against `icon_style.core_monoline_errors` before writing any asset, and the
  legacy PNG gets the same raster presence pass as every square icon. The
  adaptive background keeps the night card so OEM masks never crop onto flat
  black. `cb_banner` and `docs/banner.png` carry the new mark.
- Core Builds itself now gets the standard Outfit/category/rail banner like
  every other icon; the mark-only `banner_style: glyph` exception is retired.

### Added
- **Stremize** (`com.stremize.player`) on a new `stremize_z` mark.
- **Input/source marks** for the devices that reach the TV over HDMI:
  `xbox_orb` (Xbox), `switch_joycons` (Nintendo Switch),
  `playstation_shapes` (PlayStation), `hdmi_connector` (HDMI Source).
  - HDMI Source maps Projectivy's `SourceHDMI1`–`SourceHDMI4` activities on
    BOTH activity paths — the legacy `.activities.input.*` and the 4.0.1+
    `.ui.guidedActions.activities.input.*` — so the tile themes on old and
    new launcher builds alike (HDMI tiles auto-theme in Projectivy).
  - Console icons map the companion packages users keep beside the box
    (Xbox Game Pass; PS Remote Play + PlayStation App; Nintendo Switch
    Online) for manual theming, mirrored in `unverified` until seen on a
    device.
- Binge gains the Foxtel platform main activity on `au.com.binge.tv` and
  `au.com.streamotion.ares`; Kayo gains it on `au.com.streamotion.hyperion`;
  Launcher Manager gains the `com.wolf.lms` build (both name forms).
- `docs/research/icon-craft-2026-09.md` — this tranche's icon research.
- `docs/research/infra-backend-frontend-2026-09.md` — updater/release
  infrastructure roadmap (phases 1-3).

### Receipts
- Classic `validate.py`: 931 icons · 1696 components · 25232 checks, all
  passing. Pop: 931 icons · 1120 components · 16 swatches · 14410 checks.
  Pixel Neon: 931 icons · 1120 components · 12721 checks. Suite truth,
  contracts, prefills and the 44-test icon identity suite pass.

## [1.8.16] — 2026-09-13

**The pack's own launcher badge finally looks like the brand.**

### Changed
- Launcher icon rebuilt in `tools/build_branding.py`: depth-gradient disc
  (panel ink to void), edge keyline for a visible rim on near-black
  launchers, heavier glowing hexagon linework and a lit gradient diamond.
- Adaptive icon split into a gradient-field background layer plus the lit
  mark foreground; the `mipmap-anydpi-v26` XML is now generator-written so
  the layers and their wiring cannot disagree.

### Added
- `docs/icon-before-after.png` — old vs new badge on a dark shelf.
- Receipts: `validate.py` 926 icons · 1664 components · 24957 checks;
  suite truth, contracts, prefills and the 44-test icon identity suite pass.

## [1.8.15] — 2026-09-13

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
