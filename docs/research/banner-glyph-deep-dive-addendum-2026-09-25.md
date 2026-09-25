# Banner and glyph market deep-dive — addendum

> **Status on `main` (2026-09-25):** the accent-coloured category kicker this document describes was proposed in Arena's commit `f9d3028` and was **not** merged. `main` still draws every kicker in Signal cyan `#00d4ff`, from one `ACCENT` constant in `tools/build_banners.py`. Read statements that the fixed-colour kicker is "corrected" as a proposal awaiting a device A/B test, not as shipped behaviour.

**Date:** 25 September 2026 (Sydney)  
**Purpose:** second-pass research after the competitive analysis in [`banner-glyph-competitive-analysis-2026-09-25.md`](banner-glyph-competitive-analysis-2026-09-25.md).  
**Scope:** search for additional competitors, launcher implementations, official requirements, current user signals, icon-pack infrastructure, TV legibility research, colour science, and distribution patterns.  
**Implementation status:** research only. This addendum does not approve changes to `tools/catalog.json`, `tools/glyphs.py`, generators, or generated assets.

## 1. The market is three different products

A common research error is to compare every image called an “icon” as if it were the same deliverable. The current market separates into three layers:

| Layer | Owner | Asset/job | Best evidence | Core Builds implication |
| --- | --- | --- | --- | --- |
| **TV application identity** | App developer, Android TV/Google TV/Fire TV store | `android:icon`, `android:banner`, Play/store listing, stock launcher | [Google TV/Android TV icon guide](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines), [Android TV quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality), [Amazon Fire TV image rules](https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html) | Core's banner and glyph dimensions are correct for the visual problem, but a pack cannot control every stock launcher. Do not promise stock Google TV or Fire TV behaviour from an appfilter APK. |
| **Icon-pack theming** | Pack + launcher | ADW `appfilter.xml`, drawable index, per-component resources, apply/update/request tools | [Lawnchair ADW documentation](https://github.com/LawnchairLauncher/lawnchair/wiki/Icon-pack-support), [Blueprint](https://github.com/jahirfiquitiva/Blueprint) | Core should remain a highly compatible standard pack with auditable component mappings and a transparent default. |
| **Launcher visual shell** | Projectivy, Monet, LTvLauncher, AT4K, other hosts | card ratio, tile background, tint, focus glow, scale, wallpaper, animation | [Projectivy 4.70](https://github.com/spocky/miproja1/releases/tag/4.70), [Projectivy 4.71](https://github.com/spocky/miproja1/releases/tag/4.71), [Monet Play listing](https://play.google.com/store/apps/details?id=com.klevico.monet&hl=en-US) | The launcher owns focus and surface behaviour. Static art should be robust under several shells, not bake one launcher’s tile or glow into every asset. |

**Strategic conclusion:** “Best on the market” for Core Builds does not mean copying a complete opaque 16:9 image from a manual LTvLauncher pack, or copying a phone pack’s adaptive monochrome layer. It means being the most reliable **cross-shell source of both square and banner artwork**, with better TV-distance recognition and better evidence of how it behaves in each host.

## 2. New current-market findings

### 2.1 Projectivy is now a mature platform, not only a launcher

The latest public Projectivy release is **4.71**, published 13 July 2026. The 4.70 release introduced or documented several surfaces that matter directly to a pack:

- animated GIF/APNG/WebP custom icons and wallpapers;
- animated-icon lifecycle fixes, including reset to the first frame when stopped;
- focused-card tint/background overrides, including swap icon/background colours;
- more glow animations, configurable glow size, and global glow colour;
- correct 1:1 custom-grid row sizing and better banner sizing in side panels;
- wallpaper/video palette extraction, with video extraction disabled by default on most devices and specifically disabled for TCL TVs because of device behaviour;
- mapping of Projectivy internal activities in icon packs;
- immediate refresh after an icon pack/plugin update or uninstall;
- font customisation, theme import/export, and improved accessibility announcements.

Release 4.71 then fixed several device crashes, theme parsing, backups, and glow-animation changes requiring a restart. These are not merely launcher features: they are constraints on a static pack. A pack that looks good only before tinting, glow, a side-panel crop, or a 1:1 grid resize is not finished.

**New recommendations from this evidence:**

1. Test Core art in the **main row, side panel, folder side panel, 1:1 grid, and manual icon picker**, not only the default 16:9 row.
2. Add a focused-state screenshot to the visual review for every experimental art change. Projectivy can add a global glow/tint that changes the apparent accent and edge weight.
3. Treat APNG as a launcher-supported experiment, not as a requirement. Projectivy's crash fixes show that animated assets have a larger device-compatibility surface than static WebP.
4. Consider mapping Projectivy internal activities as a coverage/product feature, but only from observed version/device components.

Sources: [4.70 release notes](https://github.com/spocky/miproja1/releases/tag/4.70), [4.71 release](https://github.com/spocky/miproja1/releases/tag/4.71), checked 25 September 2026.

### 2.2 Monet has materially grown since the local v1.0.84 probe

The current Play listing was updated **19 September 2026** and reports **50K+ downloads, 4.7 stars, and roughly 2.46K reviews**. These are store/vendor signals, not an independently audited user study. It advertises:

- wallpaper-derived real-time colour;
- 22 accent palettes plus a 96-shade custom picker;
- 7 icon shapes and 4 tile styles, including themed and transparent modes;
- per-app image, colour, and name overrides;
- adjustable tile size;
- 9 app launch animations and 7 focus animations;
- up to 4 profiles, backups, row/folder controls, and shared setups;
- Live TV and HDMI inputs when the device exposes them.

Recent visible reviews praise the accessibility and the TCL experience, but also mention premium/device-transfer friction, custom-wallpaper limits, and layout-density requests such as blank rows. These reviews do not prove the icon sampler or tile parser, but they do show that users notice **setup friction and layout density** more readily than subtle art details.

**Implications:**

- The local Monet v1.0.84 decompilation is now a versioned historical probe. It must not be cited as current Monet behaviour.
- Core's install/help surface should explain the difference between **Monet Glyphs** and **Projectivy Banners** before users apply the wrong companion.
- A visual review should include multiple tile sizes and the smallest dock size, not only the largest app row.
- The current resolved-accent fix is necessary but not sufficient: themed mode may tint or replace the art, while transparent mode may expose every alpha/presence edge.
- Do not guess Monet's HDMI/Live TV components. The listing says those surfaces depend on device inputs/apps; only an ADB scan of the exact build is evidence.

Source: [Monet Play listing](https://play.google.com/store/apps/details?id=com.klevico.monet&hl=en-US), accessed 25 September 2026.

### 2.3 Projectivy Icon Pack remains the trust/distribution leader

GitHub API data checked 25 September 2026 shows:

- `SicMundus86/ProjectivyIconPack`: **453 stars**, updated 23 September 2026;
- latest release **1.1.9**, published 5 April 2026;
- the release added 20 icons and explicitly listed some entries that have **no automatic mapping** and require manual assignment.

The pack's README is unusually effective product documentation: it explains apply steps, dark background recommendations, mobile-variant mapping limitations, updater/re-apply behaviour, cache problems, activity changes, and issue/request flows. The project is not perfect—its own release notes identify unmapped icons—but it is trusted because it is honest about those failure modes.

**Core should copy the honesty, not the art:** publish separately:

- icon present vs component mapped;
- auto-assigned vs manual-only;
- tested on Android/Google TV vs inferred package activity;
- stale-cache/re-apply recovery;
- current release and last mapping audit.

Sources: [repository](https://github.com/SicMundus86/ProjectivyIconPack), [1.1.9 release](https://github.com/SicMundus86/ProjectivyIconPack/releases/tag/1.1.9), checked 25 September 2026.

### 2.4 The smaller packs are active, curated competitors

The competitor field is not static:

- **Android TV Minimalist**: 21 stars, repository updated 22 September 2026, one 1.0.0 release from June. Its issue list contains current package-name/icon requests, including a 17 September request for Movistar Plus and Moonlight. It competes on symbols-first curation and a unifying background.
- **Minimal TV Icons**: 31 stars, repository updated 23 September 2026, MIT licensed. It competes on a restrained Apple TV-inspired composition, soft/solid variants, multiple resolutions, and the right to decline low-value/wordmark-only requests.
- **Blackshield**: zero stars and repository updated 19 September 2026. It claims very broad phone coverage, 20 launcher filters, adaptive/monochrome wrappers, and fallback furniture. The claims are not an audited TV benchmark and should remain labelled as repository claims.

The lesson is not that Core should become curated or opaque. It is that users value a **consistent decision rule**: Minimalist says symbol first; Minimal TV says only worthwhile/achievable apps; Projectivy says requests are welcome but mappings can be manual. Core's style guide needs the equivalent user-visible rule for when a letter fallback is better than an invented pseudo-logo.

Sources: [Minimalist](https://github.com/hqn-scl/android-tv-minimalist-icon-pack), [Minimal TV](https://github.com/Mortisshadow/minimal-tv-icons), [Blackshield](https://github.com/Blackshield-Company/blackshield-icon-pack), checked 25 September 2026.

## 3. New launcher and tooling competitors

These are easy to miss if research is limited to icon-pack repositories.

### 3.1 LTvLauncher, Arc Launcher, and Liquid Launcher

- [LTvLauncher](https://github.com/hamishakl/LtvLauncher) is an open-source FLauncher fork for Android TV, Google TV, and Fire TV. Its current README advertises four visual themes, accent-colour presets, dynamic wallpapers, and custom banner assignment for sideloaded/unthemed apps.
- [Arc Launcher](https://github.com/meddouribadis/arclauncher) builds on the LTvLauncher family and advertises custom banner support, multiple wallpapers, blur controls, and Fire TV compatibility.
- [Liquid Launcher TV](https://github.com/FirefliesStudios/liquid-launcher-tv) advertises icon-pack support and per-app custom icons in a more glass-oriented shell.

These are not evidence that Core needs a fourth asset format. They show that the addressable market is expanding beyond Projectivy, and users may expect a banner to work as a manually assigned image even where standard appfilter application is absent.

**Low-risk product response:** document an “export/manual banner” path with the existing 320 × 180 WebP, stable filenames, and a one-app manual assignment procedure. Do not create opaque variants until a target launcher demonstrates that the transparent asset is visibly deficient.

### 3.2 BareLauncher is a useful forward-looking compatibility reference

An open pull request in [BareLauncher](https://github.com/namillis/barelauncher/pull/33), updated 25 September 2026, adds standard Nova/ADW/Apex/GO icon-pack support and describes a robust matching model:

1. prefer the TV launcher activity;
2. use an unambiguous package mapping;
3. fall back to a phone launcher activity;
4. retain original artwork for unmapped apps;
5. retain per-app custom icons above pack icons;
6. cache by pack version;
7. restore defaults on uninstall and include the pack in backup/restore.

The PR is open, so this is not a shipped-platform requirement. It is nevertheless a strong design signal for Core's mapping contract. Future TV launchers may be more forgiving of standard packs if pack authors provide complete, unambiguous component mappings and avoid assuming one activity per package.

**Recommendation:** add a mapping-quality report that classifies each catalogue component as TV-specific, generic launcher, ambiguous, alias, or manually verified. This is more useful than only reporting a raw row count.

### 3.3 `tv-assets` and Tiny Launcher show a provisioning market

[cgio/tv-assets](https://github.com/cgio/tv-assets) contains a normalized landscape-card library with **869 extracted Projectivy 1.1.9 PNGs at 320 × 180** plus `icons.json` documenting package names, activity components, drawable IDs, and filenames. It is not a competing aesthetic, but it demonstrates that users and integrators want a stable CDN/file-level asset library in addition to an APK.

[Tiny Launcher](https://github.com/wyattberry-org/Tiny-launcher-) advertises direct Projectivy banner import, custom banner replacement, wallpaper-derived tile accents, card-size controls, corner-radius controls, and text-position controls. It is an important warning: the same Core banner will be viewed in launchers that move the label, crop the image, or apply a different background.

**Recommendation:** release a generated, versioned machine-readable manifest for each asset family containing:

- catalogue name and drawable;
- square and banner filenames;
- source accent and rendered/display accent;
- category;
- component list and mapping confidence;
- safe-area/ink-bbox metrics;
- whether the art is a letter fallback, generic family mark, or reviewed brand cue.

This can be generated from `tools/catalog.json`; it must not become a second source of truth.

## 4. Platform requirements that deserve sharper interpretation

### 4.1 Banners need identity, not decorative metadata

Google's current TV icon guidance says:

- 16:9 banner and 1:1 launcher icon are distinct surfaces;
- 320 × 180 is the legacy xhdpi banner size;
- keep the logo in the safe area;
- avoid cropping/spilling;
- avoid adding text or graphics that indicate additional information;
- show the full logo—icon plus text—for banners;
- avoid borders;
- Android TV does not support themed icons.

There is a tension in the wording: **full app-name text is identity; a category kicker is additional information**. The current Core category/kicker is therefore not an obvious platform failure, but it is the highest-risk element of the current banner grammar.

**Decision rule:**

- Keep the light app name in the default banner.
- Treat `VOD`, `STREAM`, `VIDEO`, `APP`, and similar labels as an experiment or metadata, not as the banner's recognition mechanism.
- If the current kicker wins a real-device A/B test, keep it only if it stays visually subordinate and does not cause colour-sampling errors.
- If it loses, remove it from the banner generator only after catalogue/generator review; do not hand-edit outputs.

### 4.2 TV quality guidance supports high-resolution assets but not static-art perfection

Google's 2026 TV quality tiers include a TV-4K criterion for high-resolution assets including app icons and banners. This supports Core's vector master + 320 × 180 output and high-resolution square companion. It does not prescribe a particular line weight, colour, opacity, or opaque background.

The official TV focus system lists scale, border, glow, and colour as focus indicators, with common scale values of 1.025, 1.05, and 1.1×. These are host-UI mechanisms. Core should test the art under them, but should not bake a 1.1× focus state into a static glyph.

Sources: [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality), [focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system), [color on TV](https://developer.android.com/design/ui/tv/guides/foundations/color-on-tv), accessed 25 September 2026.

### 4.3 Fire TV is a separate compatibility decision

Fire TV's design guidance also uses a 5% outer safe zone and asks for clear focus indication. However, Fire TV/store and sideload behaviour differs from Android TV icon-pack behaviour:

- Amazon store imagery can use a 1280 × 720 opaque marketplace icon;
- community/device reports show sideloaded Fire TV launchers may use `android:icon` rather than `android:banner`;
- Fire TV custom-launcher users often solve missing icons through launcher replacement or dummy shortcut apps rather than appfilter packs.

Therefore, do not advertise “Fire TV compatible” merely because a banner is 16:9. If Fire TV becomes a goal, define a separate acceptance track: launcher, sideload/store path, manifest resource, and manual custom-banner workflow.

Sources: [Amazon Fire TV design guidelines](https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html), [Fire TV sideload/banner investigation](https://github.com/matthias-ennen/movie-hub/issues/202), [AFTVnews missing icons](https://www.aftvnews.com/solutions-for-missing-broken-app-icons-on-fire-tvs-fire-tv-cubes-firesticks-and-fire-tv-smart-tvs/), accessed 25 September 2026.

## 5. Design science applied to Core glyphs

### 5.1 Optical size is a first-class variable

Material 3's icon guidance is not a mandate for Core's custom art, but it confirms the design principle: icons use different optical sizes, live areas, and weights for 20, 24, 40, and 48 dp; complex shapes may need optical correction; consistent stroke weight and keylines matter. Google's Material Symbols also exposes weight, grade, fill, and optical-size axes.

Core currently has a 512 grid, 432 safe area, fitting, and three source weights. That is a good foundation. The missing proof is a **display-size matrix**:

- 512 source review;
- 256/192/128 rendered preview;
- 96 px Monet-style tile;
- 64 px small tile;
- 48 px aggressive reduction;
- 320 × 180 banner output;
- banner glyph at the actual card size after host scaling.

At each size record visible ink bbox, centroid, minimum gap, minimum surviving detail, and perceived stroke weight. Do not force the same raw bbox across a circle, wordmark-like mark, tall mark, and wide mark.

### 5.2 Small lines must be judged on luminance, not only hue

The icon research literature and WCAG agree on a relevant point: colour difference alone is a weak guarantee of fast perception. A cyan and blue can have a noticeable hue difference but insufficient luminance separation when downsampled or shown through a tinted launcher surface.

For Core:

- test each mark in grayscale;
- test against the least-contrasting part of a gradient/photo surface;
- test accent-only, light-ink, and keyline-assisted variants;
- avoid a criterion that accepts a high CIEDE2000 colour difference when luminance contrast is poor;
- do not rely on the category colour or rail as the only cue.

Sources: [W3C icon contrast technique G207](https://www.w3.org/WAI/WCAG21/Techniques/general/G207), [Material icons](https://m3.material.io/styles/icons/designing-icons), [icon-size/visual-search study](https://www.sciencedirect.com/science/article/abs/pii/S0141938203000350), accessed 25 September 2026. The 2003 visual-search study is general UI research, not Android TV-specific; use it as supporting evidence, not a TV certification rule.

### 5.3 Current accent audit reveals an important surface limitation

Using the current 206 source accents and Core's `display_accent()` policy:

- 202 distinct rendered accents remain after normalisation;
- 53 catalogue entries currently render with sanctioned light ink because their source accent is achromatic/too dark;
- the rendered accents meet the existing 3:1 floor against the default dark `#0D1117` card;
- many original brand accents will not meet 3:1 against a white card or bright photographic surface.

This is not evidence that all brand colours should be recoloured. It is evidence that the transparent pack cannot promise every accent is readable on every launcher-owned surface. The host configuration and the static presence keyline are part of the rendering contract.

**Recommendation:** publish and test two supported host profiles:

1. **Core dark profile:** dark card/transparent tile, original or display-normalized accent, intended and best-supported.
2. **Host-tinted/light profile:** launcher owns tint/background; glyph must remain recognizable in grayscale/light ink, and gradient/low-luminance brands are flagged for review.

Do not add white outlines or opaque tiles to every glyph merely to make the light profile pass; that would change the identity and conflict with transparent launcher composition.

## 6. User feedback synthesis across TV and phone markets

### Repeated positive signals

Across Projectivy users, Android TV pack READMEs, current phone-pack listings, and issue queues, users reward:

- “no rogue icons” / high coverage;
- automatic mapping and one-click apply;
- consistent visual language;
- transparent art that works with a chosen launcher card/background;
- recognizable line/silhouette art rather than random generic blobs;
- regular updates and requests that are actually acted on;
- clear manual-assignment fallback when mapping cannot be automatic;
- a preview/search dashboard and easy recovery after updates.

### Repeated negative signals

- manually assigned icons are not overridden by packs;
- activity changes or regional variants break automatic mapping;
- stale caches make an update appear not to work;
- a new launcher version can reset or lose settings;
- too many similar icons make app finding slower even when the aesthetic is clean;
- premium/setup/device-transfer friction is noticeable;
- phone users will accept a generic mask for an unthemed app, but TV users often notice a mismatched banner or square tile in a sparse dock.

Current adjacent evidence includes [Arcticons' Play listing and reviews](https://play.google.com/store/apps/details?id=com.donnnno.arcticons&hl=en-US), [Projectivy's pack discussion](https://www.reddit.com/r/Projectivy_Launcher/comments/1mtmall/introducing_the_projectivy_icon_pack/), [Minimal TV's curated philosophy](https://github.com/Mortisshadow/minimal-tv-icons), and [Monet's current Play reviews](https://play.google.com/store/apps/details?id=com.klevico.monet&hl=en-US). Phone-market numbers and reviews are not TV-market measurements, but the service expectations—coverage, requests, update cadence, recognizable art—transfer well.

## 7. What “best on market” should mean for Core

A single “number of icons” ranking would reward unverified Blackshield-style claims and generic fallbacks. Use a weighted scorecard instead:

| Dimension | Weight | Pass evidence | Core target |
| --- | ---: | --- | --- |
| TV format/interoperability | 20% | 320 × 180 banner, 512 square, transparent alpha, valid appfilter, both package/activity forms | Keep green; add launcher matrix |
| Recognition at distance | 20% | blinded mark recognition at 96/64/48 px and 3 m; labels hidden | Improve high-use fallback/generic collisions |
| Surface robustness | 15% | dark/light/photo/tinted/themed/focused screenshots | Add current Monet/Projectivy evidence |
| Optical consistency | 15% | bbox/centroid/stroke/detail report across sizes | Add automated receipt, then gate |
| Mapping/coverage quality | 15% | auditable component evidence, auto/manual classification, top-app hit rate | Report 961/1,179/1,807 separately |
| Update/request experience | 10% | apply, update, re-apply/cache recovery, request triage, release cadence | Productize existing strengths |
| Distribution/trust | 5% | discoverable TV install path, accurate marketplace listing, licence/provenance | Update Projectivy listing; consider Play distribution |

**Proposed goal:** Core should not need to win every style preference. It should be the pack with the best combined **coverage + TV-specific format + recognizability + launcher resilience + honest maintenance**.

## 8. Revised roadmap

### P0 — evidence and interoperability, no art redesign

1. Refresh the Monet probe to the current Play build or an installed APK; record version/date and settings.
2. Capture Projectivy 4.71 in 16:9, 1:1, side-panel/folder, transparent, tinted, focused, and glow states.
3. Add `safe_area`, `alpha_bbox`, `centroid`, `circle_spill`, `minimum_detail`, and `rendered_accent` receipts to validation output.
4. Publish the two-package choice: Projectivy → Banners; Monet/1:1 → Glyphs.
5. Create a generated manifest that separates art coverage, component coverage, physical files, aliases, and mapping confidence.
6. Correct the official Projectivy plugin listing's stale statement that only its own pack supports TV cards; Core now ships 16:9 assets.

### P1 — high-value generated-art experiments

7. A/B current banner vs mark + name without category kicker. Keep the current default until 3 m/device evidence supports a change.
8. Add a top-use recognition tranche for repeated letter tokens and generic marks. Use a stable cue, not a traced vendor logo.
9. Add display-size/optical-size review at 96, 64, and 48 px. Simplify details that collapse; do not blindly increase all stroke widths.
10. Test a flat sampling-safe version of gradient entries. Retain the existing resolved accent source; do not invent new catalogue colours.
11. Add current Projectivy internal-activity mappings only from ADB/device evidence.
12. Create generated manual-export metadata and instructions for LTvLauncher/Arc/Tiny-style workflows.

### P2 — product/distribution

13. Match Projectivy/Blueprint-level discovery: accurate Play/TV listing or trusted install path, screenshots on actual TV rows, and a clear change log.
14. Add a visible in-app “why didn’t this apply?” flow with reset-manual-override, re-apply, cache-refresh, component-report, and launcher limitation branches.
15. Maintain a coverage ledger with response-time/request status and last tested build.
16. Run a small recognition study with anonymous participants at 3 m; use results to choose art work, not taste debates.

### Experimental / explicitly not approved

17. Opaque full-card banner set.
18. APNG/animated glow set.
19. `iconback`/`iconmask`/`iconupon` fallback furniture in the default transparent pack.
20. Android TV monochrome/adaptive expansion.
21. Fire TV compatibility claim without a separate store/sideload acceptance matrix.

## 9. Source register, dates, and confidence

Accessed **25 September 2026** unless a publication/update date is shown.

### Official / platform

- [TV app icon design guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines), updated 27 Jun 2024.
- [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality), updated 29 Jun 2026.
- [Design for TV](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv), updated 8 May 2023.
- [Navigation on TV](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv), updated 9 May 2025.
- [Focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system), updated 21 Mar 2024.
- [Color on TV](https://developer.android.com/design/ui/tv/guides/foundations/color-on-tv), updated 4 Jun 2023.
- [TV layouts/overscan](https://developer.android.com/design/ui/tv/guides/styles/layouts), updated 9 May 2025.
- [Material 3 icon design](https://m3.material.io/styles/icons/designing-icons), accessed 25 Sep 2026.
- [Material Symbols variable axes](https://developers.google.com/fonts/docs/material_symbols), accessed 25 Sep 2026.
- [WCAG G207 icon contrast](https://www.w3.org/WAI/WCAG21/Techniques/general/G207), accessed 25 Sep 2026.
- [Amazon Fire TV design and UX](https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html), accessed 25 Sep 2026.

### Launchers, packs, and tooling

- [Projectivy 4.70 release](https://github.com/spocky/miproja1/releases/tag/4.70), 26 Jun 2026.
- [Projectivy 4.71 release](https://github.com/spocky/miproja1/releases/tag/4.71), 13 Jul 2026.
- [Projectivy Launcher Play listing](https://play.google.com/store/apps/details?id=com.spocky.projengmenu&hl=en-US), updated 12 Jul 2026.
- [Projectivy Icon Pack 1.1.9](https://github.com/SicMundus86/ProjectivyIconPack/releases/tag/1.1.9), 5 Apr 2026.
- [Android TV Minimalist](https://github.com/hqn-scl/android-tv-minimalist-icon-pack), repository updated 22 Sep 2026; current issue queue checked 25 Sep 2026.
- [Minimal TV Icons](https://github.com/Mortisshadow/minimal-tv-icons), repository updated 23 Sep 2026.
- [Blackshield](https://github.com/Blackshield-Company/blackshield-icon-pack), repository updated 19 Sep 2026; coverage numbers are unverified claims.
- [Monet Play listing](https://play.google.com/store/apps/details?id=com.klevico.monet&hl=en-US), updated 19 Sep 2026.
- [LTvLauncher](https://github.com/hamishakl/LtvLauncher), repository updated 25 Sep 2026.
- [Arc Launcher](https://github.com/meddouribadis/arclauncher), accessed 25 Sep 2026.
- [Liquid Launcher TV](https://github.com/FirefliesStudios/liquid-launcher-tv), accessed 25 Sep 2026.
- [BareLauncher icon-pack PR](https://github.com/namillis/barelauncher/pull/33), open and updated 25 Sep 2026.
- [TV assets / Projectivy export](https://github.com/cgio/tv-assets), repository updated 23 Sep 2026.
- [Tiny Launcher](https://github.com/wyattberry-org/Tiny-launcher-), accessed 25 Sep 2026.
- [Lawnchair ADW specification](https://github.com/LawnchairLauncher/lawnchair/wiki/Icon-pack-support), accessed 25 Sep 2026.
- [Blueprint dashboard](https://github.com/jahirfiquitiva/Blueprint), accessed 25 Sep 2026.

### User/market evidence

- [Projectivy Icon Pack Reddit announcement](https://www.reddit.com/r/Projectivy_Launcher/comments/1mtmall/introducing_the_projectivy_icon_pack/), 22 Nov 2025.
- [Projectivy banner/custom-icon request](https://www.reddit.com/r/Projectivy_Launcher/comments/1icfbn7/how_to_i_install_icon_pack_also_can_i_make_my/), 28 Jan 2025.
- [Projectivy transparent glow discussion](https://www.reddit.com/r/Projectivy_Launcher/comments/1n22koa/getting-the-transparent-icons-to-glow/), 28 Aug 2025.
- [Arcticons Play listing/reviews](https://play.google.com/store/apps/details?id=com.donnnno.arcticons&hl=en-US), updated 15 Aug 2026.
- [Viewing-distance/icon-search study](https://www.sciencedirect.com/science/article/abs/pii/S0141938203000350), published 2003.
- [TV reading-distance survey](https://doi.org/10.1145/3132129.3132133), published 2017.

**Confidence labels:** Android TV dimensions, 4K asset guidance, focus-system primitives, and themed-icon limitation are high confidence. Current Play listing numbers are store claims/signals, not independent audits. GitHub star/update counts are reproducible as of the date but time-sensitive. Reddit and Play reviews are anecdotal. Monet's icon parser, sampled-pixel algorithm, current themed-mode recolour implementation, and internal component names remain unverified without an installed current build.
