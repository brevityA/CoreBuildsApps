# Banner and glyph forensic/platform pass — third addendum

**Date:** 25 September 2026 (Sydney)  
**Purpose:** third research pass after [`banner-glyph-competitive-analysis-2026-09-25.md`](banner-glyph-competitive-analysis-2026-09-25.md) and [`banner-glyph-deep-dive-addendum-2026-09-25.md`](banner-glyph-deep-dive-addendum-2026-09-25.md).  
**Scope:** distinguish store and runtime artwork, re-check the generated resource population, interpret the local alpha audit against real host masks, and turn current user complaints into acceptance tests.  
**Implementation status:** research only. No catalogue, generator, mapping, SVG, WebP, or production asset changes are approved by this document.

## 1. Executive finding

Core Builds is making two useful visual products, but they are currently easy to describe as one product:

1. a **transparent 16:9 runtime/custom-card banner** for TV launchers and appfilter-aware hosts; and
2. a **transparent square glyph** for tile, icon-pack, and themed/tinted launcher surfaces.

A third file may sometimes be useful, but it is not the same asset:

3. an **opaque 1280 × 720 Google Play Android TV preview banner** for store metadata.

Google's Play Console requirement for the third item must not be used as evidence that the first item should become opaque. Conversely, Android TV's runtime `android:banner` guidance must not be represented as a Play preview upload rule. The strongest near-term competitive advantage is therefore an explicit, generated, auditable **surface contract**, not a mass redesign.

The local audit supports caution rather than a shrink-everything decision. All 945 catalogue-backed square resources remain inside Core's current 432 px square safe extent. A hypothetical circle test reports 762/945 assets with alpha beyond a 216 px radius, but that radius is not an official universal Android TV, Projectivy, or Monet mask. It is a diagnostic for a possible host clip. Actual host captures and mask geometry must decide whether any art changes are justified.

## 2. Evidence labels used in this pass

| Label | Meaning |
| --- | --- |
| **Platform requirement** | Stated by Google, Android, Amazon, or a launcher/packaging specification. It is not an aesthetic preference. |
| **Observed competitor behaviour** | A current repository, release, listing, implementation, or user-visible workflow. It may change and is not a universal requirement. |
| **Repository fact** | Measured from this checkout's catalogue/generated resources. |
| **Anecdotal user signal** | A review, issue, forum, or Reddit comment. It is useful for hypotheses, not a controlled usability result. |
| **Core recommendation** | A proposed product or engineering decision that still requires review. It is not a platform rule. |

This separation is important for the category labels (`VOD`, `STREAM`, `VIDEO`, and similar): their naming/mapping belongs to the catalogue and generator, not to the app name `Monet`. The app-name text remains light ink. Monet's category-colour correction continues to use each icon's existing resolved accent; this pass does not propose a new colour or a catalogue rename.

## 3. The platform matrix: six surfaces, six contracts

| Surface | What supplies it | Current evidence | Core interpretation |
| --- | --- | --- | --- |
| **Android TV / Google TV runtime launcher icon** | The app's manifest `android:icon`, usually adaptive or legacy density resources | Google identifies a 1:1 launcher icon and a 72 × 72 adaptive safe zone; Android/Google TV does not support themed icons | A square pack glyph cannot force stock-launcher behaviour. Test the glyph as a possible host input, but do not claim that an icon pack changes an app's manifest. |
| **Android TV / Google TV runtime banner** | The app's manifest `android:banner` on the application or TV activity, associated with `CATEGORY_LEANBACK_LAUNCHER` | Google recommends an adaptive 16:9 banner and retains a 320 × 180 xhdpi legacy option; the full logo is icon plus text and text is localized in the image | Core's 320 × 180 transparent banner is a custom/icon-pack asset, not proof that a stock app manifest has a valid banner. Keep the light app name and test the category kicker as optional metadata. |
| **Google Play Android TV preview banner** | Play Console store listing | Android TV listings require a TV screenshot and banner preview; the preview banner is 1280 × 720 JPEG or 24-bit PNG with no alpha | This is a separate opaque store deliverable. Generate only as an explicitly named, optional output when distribution needs it. Never replace the transparent runtime banner with it. |
| **Projectivy 16:9 card/banner** | Projectivy icon pack, custom image picker, or manual assignment | Projectivy supports custom banners/cards, tint, glow, wallpaper/palette surfaces, changing card sizes, and manual overrides; user reports commonly discuss 320 × 180 and spacing | Judge Core banners under the host's actual crop, backdrop, focus, tint, and spacing. Transparent art is a feature here, not a Play-store defect. |
| **Monet square/tile glyph** | Monet's icon-pack/custom-icon path and its tile renderer | The current listing advertises multiple shapes, tile styles, accent/palette behaviour, per-app overrides, profiles, and tile-size controls | Treat Monet as a versioned host matrix. A glyph that passes a square source-grid test can still fail a circle mask, tint sampler, small dock, or focus state. |
| **Fire TV runtime/store/sideload artwork** | Fire OS launcher, app manifest/package manager, Amazon Appstore metadata, or a replacement launcher | Fire TV has its own launcher and 720p/1080p display guidance; Amazon documents package-manager banners for relevant TV surfaces, but this does not establish appfilter support in the stock launcher | Do not advertise Fire TV support from a 16:9 file alone. Define the launcher, manifest/store path, and manual workflow before making a compatibility claim. |

### 3.1 Runtime Android TV is not Play preview metadata

**Platform requirements.** Google's TV documentation describes three manifest-facing identity forms: a square launcher icon, a round/square launcher presentation, and a 16:9 banner. The current guidance recommends adaptive resources, gives a 72 × 72 safe zone for adaptive icon/foreground content, retains the legacy xhdpi 320 × 180 resource, and says the banner should show the complete logo, including icon and text. Google's publishing checklist also asks for a 320 × 180 banner with localized identifying text for the legacy path. A TV app is surfaced through a `CATEGORY_LEANBACK_LAUNCHER` activity, and `android:banner` can be attached at application or activity level.

**Platform requirements.** Play Console treats a store banner as a preview asset. The Android TV-enabled listing requires at least one Android TV screenshot and a TV banner; the banner is 1280 × 720 and must be JPEG or 24-bit PNG without alpha. This is an opaque store/listing constraint, not a statement about `android:banner`, Projectivy custom cards, or Core's transparent WebP.

**Core recommendation.** Keep these names and outputs distinct in documentation and any future generator receipt:

- `runtime_banner_320x180.webp` or the existing generated banner resource;
- `glyph_512x512.webp` or the existing square resource;
- `play_android_tv_preview_1280x720.jpg` or `...png`, generated only when a listing needs it and explicitly marked as opaque/store-only.

Do not add a store-preview file to the runtime appfilter by accident. Do not make a transparent custom-card banner opaque merely to satisfy a store upload form.

### 3.2 Full-logo text and category kickers have different evidence

Google's runtime guidance strengthens the case for the current app-name lockup: the banner's identity is expected to contain the icon plus text, and localized text belongs in localized banner variants. It does **not** establish a need for a second metadata label such as `VOD`, `STREAM`, or `VIDEO`; the same guidance cautions against adding graphics that communicate extra information.

That creates a clear review boundary:

- **Required/strongly supported:** recognizable icon plus app name; the current app-name/font text remains light ink for readability.
- **Experimental:** the category kicker and its resolved accent. Keep the existing resolved accent in the current implementation unless a separately reviewed A/B result changes the decision.
- **Not supported by platform evidence:** treating a category kicker as the app's primary identity, or assuming that it should be present in every store/runtime variant.

No category-label or Monet mapping change is made by this research pass.

## 4. Current user and launcher signals

### 4.1 Projectivy spacing complaints are actionable, but not a shrink command

A Projectivy Icon Pack discussion contains a positive report alongside a designer complaint that icons are too close together and should be about 20% smaller, plus a separate request that generic black/white category icons be smaller because they appear huge beside app-specific icons. The reply clarifies that the generic category icons were designed for category headers, not ordinary app tiles. Other Projectivy discussions recommend 320 × 180 for manually supplied banners and report pixelation, glow, or crop problems when users use oversized files.

**Evidence classification:** anecdotal user feedback and launcher-specific explanation, not a controlled measurement and not an Android TV requirement.

**What it does establish as a hypothesis:**

- apparent icon size is a property of the glyph, host tile, row spacing, and assignment context together;
- a category/header glyph can be optically wrong when assigned as a normal app glyph even if its source geometry is internally consistent;
- “generic” and “app-specific” art must be compared at the same host size, not only at 512 px in an asset browser;
- manual 320 × 180 exports and generated banners need exact-size and resampling checks.

**Core recommendation:** add a context label to visual reviews: `app_tile`, `category_header`, `banner_card`, `manual_image`. Do not shrink all generic or monogram art until the same glyph has been rendered in the same Projectivy/Monet slot and the host spacing is recorded.

### 4.2 Manual overrides and update recovery are part of the product

Across Projectivy, Monet, and ADW-style pack workflows, a user can have a manually selected image, a cached pack result, a changed activity, or a launcher-specific override. A new mapping does not necessarily displace that state. The competitive advantage of a pack is therefore partly operational:

- show the current pack/version and mapping evidence;
- distinguish “art exists” from “this component maps automatically”;
- state when manual overrides win;
- provide re-apply/cache-refresh/reset guidance;
- preserve a stable filename and a manual export path for launchers that do not consume the pack format.

These are observed workflow risks, not claims that every launcher implements the same precedence order. Core should document the precedence per tested host rather than promise a universal update behaviour.

### 4.3 Dynamic wallpapers, tint, and focus change perceived geometry

Projectivy release notes and the current Monet listing show the ecosystem moving toward wallpaper-derived palettes, global or per-app tint, focus glow/animation, transparent tile modes, custom tile size, and user profiles. A static transparent glyph may be recoloured, surrounded by a presence layer, enlarged at focus, or composited on a photo. A raw source-grid bbox cannot predict all of those states.

**Core recommendation:** visual acceptance must include unfocused and focused states, dark/light/photo surfaces, tinted and untinted modes, and at least one small tile/dock configuration. Do not bake a focus glow, tile background, or wallpaper into the default transparent art.

## 5. Repository forensic audit

### 5.1 Correct population counts

The previous broad directory scan counted every non-banner WebP, which includes 12 non-catalogue branding resources. The source-of-truth and alias files give a more useful count:

| Measured item | Result | Interpretation |
| --- | ---: | --- |
| `tools/catalog.json` entries | 961 | Catalogue app entries; source of truth |
| Catalogue component strings | 1,179 | Before generated dual-form appfilter expansion |
| Generated appfilter rows | 1,807 | Generated mapping rows, including both activity spellings where applicable |
| Physical catalogue-backed square WebP files | 945 at 512 × 512 | `aliases.xml` contains 16 additional drawable names pointing to canonical square art |
| Physical catalogue-backed banner WebP files | 957 at 320 × 180 | `banner_aliases.xml` contains 4 additional banner names pointing to canonical art |
| Non-catalogue square branding WebP files | 12 | Must not be counted as app glyph coverage |
| Catalogue-backed square resources audited | 945 | The correct denominator for the local alpha/bounds audit |

The two alias sets are not interchangeable. The physical-file count is not the catalogue-entry count, and neither is the same thing as mapped component coverage.

### 5.2 Alpha/bounds results

A local Pillow/NumPy audit was run against the 945 physical catalogue-backed square WebPs at 512 × 512. It used alpha threshold `>= 8` for visible-pixel bounds and did not modify repository files.

| Metric | Result | Interpretation |
| --- | ---: | --- |
| Median visible bbox | 426 × 426 px | Many glyphs use most of the existing 432 px project safe extent |
| Minimum visible bbox | 180 × 112 px | Wide/tall and compact marks need optical-size review rather than a single raw-box rule |
| Approximate 5th-percentile bbox | 278 × 270 px | Small marks are a minority but are relevant at the smallest tile sizes |
| Median absolute centroid offset | 1.7 px horizontal / 1.9 px vertical | The population is broadly centred, while outliers still need an offender list |
| Maximum absolute centroid offset | 72.2 px horizontal / 93.9 px vertical | These are candidates for visual review, not automatic corrections |
| Inside existing 432 px square safe extent | 945 / 945 | Source-grid compliance is currently good |

The result supports a **receipt-and-review** change, not a blanket shrink. A 426 px bbox is not automatically too large: some transparent glyphs intentionally use the host's available mark area, while a category/header mark may need a different optical contract.

### 5.3 Circle-mask diagnostic, explicitly not a defect count

For a hypothetical centred circle, the audit found the following catalogue-backed files with any alpha-bearing pixel outside the radius:

| Hypothetical radial boundary | Files outside at alpha `>= 8` | Meaning |
| ---: | ---: | --- |
| 216 px | 762 / 945 | Circle inscribed in the current 432 px square safe extent; useful stress test only |
| 256 px | 472 / 945 | Larger hypothetical host circle; still not a launcher-specific result |
| 288 px | 0 / 945 | Every audited mark stays inside this larger radial boundary |

The 216 px test is deliberately conservative and produces a large number because a square safe extent has corners outside its inscribed circle. It does **not** prove that 762 production glyphs are defective. A host may use a rounded rectangle, an adaptive foreground/background layer, a mask with a different scale, a presence layer, or no mask at all. Anti-aliased alpha, transparent glow/presence pixels, and the host's crop policy also matter.

**Acceptance decision:** retain the existing art until the actual Monet, Projectivy, and Android/Google TV mask/surface renders are captured. For each host, record the mask shape and scale, classify the pixels as `critical_ink`, `presence`, or `decorative`, and only then decide whether an asset is clipped in a user-visible way.

### 5.4 What the audit cannot prove

The local files do not prove:

- Monet's current sampler or exact recolouring algorithm;
- which of its advertised shapes apply to a specific icon-pack path;
- Projectivy's exact crop/scale at every card size or version;
- whether a generic category glyph was assigned to a header or app tile by a user;
- stock Google TV's treatment of a third-party appfilter pack;
- Fire TV stock-launcher consumption of ADW resources;
- recognition, readability, or search time at a real viewing distance.

These require versioned host screenshots, APK/ADB inspection, or a controlled user test. The report must not convert any of them into an undocumented platform fact.

## 6. Proposed generated receipts

The existing deterministic pipeline is a strength because `tools/catalog.json` remains the source of truth. The missing layer is a generated receipt that makes visual and mapping claims inspectable. A future approved implementation could emit one machine-readable record per catalogue entry and one summary record per build.

### 6.1 Square glyph receipt

Proposed fields:

- catalogue name, drawable, canonical drawable, alias status, category, glyph token, mark/fallback class;
- source accent, resolved/display accent, ink colour, gradient flag;
- pixel dimensions, alpha-channel presence, visible coverage at documented thresholds;
- alpha bbox, centroid, optical offset, and safe-extent result;
- rendered measurements at 256, 128, 96, 64, and 48 px;
- minimum surviving gap, connected-component count, smallest meaningful detail, and a reviewed stroke proxy;
- host-mask results for each tested Monet/Projectivy/Android TV mask, with `critical_ink` separated from `presence`;
- composite contrast results on dark, light, photo, tint, grayscale, and focus samples;
- component count, TV-specific/generic/ambiguous/manual evidence class, and last mapping audit.

### 6.2 Banner receipt

Proposed fields:

- exact output dimensions and alpha status;
- icon, app-name, category-kicker, rail, and presence-layer bboxes where the generator can identify them;
- app-name light-ink bbox and minimum rendered cap-height at 320 × 180;
- overscan/crop stress result and logo completeness result;
- transparent runtime/custom-card classification;
- optional store-preview relation, explicitly marked as a separate opaque derivative rather than the runtime source;
- resolved accent and sampled-colour proxy results, with the warning that the actual host sampler is unknown.

### 6.3 Mapping/distribution receipt

A release summary should report at least:

- 961 catalogue entries;
- 1,179 source component strings;
- 1,807 generated appfilter rows;
- 945 physical square app resources plus 16 square aliases;
- 957 physical banner resources plus 4 banner aliases;
- auto-mapped, manual-only, ambiguous, TV-specific, generic-launcher, and unverified components;
- release/version date and known host builds tested.

This prevents “icon count”, “file count”, and “component coverage” from being used as interchangeable marketing numbers.

## 7. Host-specific acceptance tests

The following are **Core quality gates**, not platform requirements, unless marked otherwise.

### A. Source and packaging gates

1. Every square runtime glyph is exactly 512 × 512 with the intended alpha semantics.
2. Every runtime/custom-card banner is exactly 320 × 180 and remains a transparent overlay unless its host contract explicitly says otherwise.
3. Every appfilter drawable resolves to a real drawable or generated alias.
4. `drawable.xml`, `appfilter.xml`, metadata, aliases, docs, SVGs, and raster outputs are generated from `tools/catalog.json`; none is hand-edited.
5. Store-preview output, when approved, is exactly 1280 × 720, opaque, JPEG or 24-bit PNG, and is never referenced as the runtime banner.

### B. Android TV / Google TV runtime gates

1. Test the square glyph through the official adaptive-icon template/safe zone and at the actual host mask; no critical brand ink is clipped.
2. Test the 16:9 banner at 320 × 180 and any adaptive density variants that Core explicitly claims; the full logo remains inside the host-safe region.
3. Keep the app name in the banner; verify localized text if a localized output is ever claimed.
4. Treat themed-icon support as unavailable on Android/Google TV unless a future platform update is explicitly verified. Do not ship a monochrome-only assumption as a TV requirement.

### C. Projectivy banner/card gates

1. Render transparent banners in the main row, side panel, folder/side-panel context, 1:1 grid if the host permits the asset there, manual image picker, and at least one small card-size setting.
2. Capture unfocused, focused, glow, tinted, dark, light, and photographic surfaces.
3. Record card size and spacing with each screenshot so “too large” and “too close” are not attributed to the asset without context.
4. Verify that a manual image override remains understandable after a pack update and document the reset/re-apply path.
5. Check exact 320 × 180 files for blur or unintended resampling; do not use a larger image solely because it looks sharper in an asset browser.

### D. Monet square/tile gates

1. Verify the current host version, then test the documented square glyph path at every advertised tile shape/style that is available to the pack.
2. Capture transparent, themed/tinted, focused, unfocused, dark, light, photo, gradient-accent, and smallest dock/tile states.
3. Run the actual mask against `critical_ink`, not merely a raw alpha bbox. Presence/glow pixels must be reported separately.
4. Compare a generic/monogram glyph with an app-specific glyph in the same slot. A generic fallback must not be materially larger merely because it was intended for a category header.
5. Keep per-app custom image/name/colour override precedence visible in the recovery documentation.

### E. Fire TV gates

1. Name the target Fire OS version/device and whether the claim is stock launcher, Amazon Appstore listing, sideload, replacement launcher, or manual image assignment.
2. Verify the resource actually consumed by that path (`android:icon`, `android:banner`, package-manager banner, store image, or custom launcher file).
3. Test 720p and 1080p presentation and focus/crop behaviour before using “Fire TV compatible”.
4. Do not infer ADW/Projectivy icon-pack support from Android compatibility alone.

### F. Distance and contrast gates

1. Render a blinded recognition sheet at the smallest intended tile sizes and at a TV-distance simulation. Include repeated letter tokens, wide marks, tall marks, gradients, and generic fallbacks.
2. Measure visible ink, not viewBox occupancy. Compare optical size, centroid, stroke proxy, and minimum surviving detail across 96, 64, and 48 px tests.
3. Check meaningful non-text contrast against representative surfaces. WCAG's 3:1 icon-contrast technique is a useful reference, not a TV-specific certification for this pack.
4. Pre-register the pass threshold for any human recognition test before looking at the results. Aesthetic preference and source-grid compliance are not substitutes for recognition evidence.

## 8. Competitive implications

### 8.1 Advantages Core can defend

- **Dual-format TV focus:** a generated square glyph and a generated 16:9 banner, instead of treating phone icon-pack output as sufficient.
- **Auditable source of truth:** catalogue, component mappings, deterministic generators, aliases, and tests are inspectable.
- **Transparent compositing:** a banner can survive different launcher backgrounds better than a baked opaque card when its alpha and presence layers are controlled.
- **Resolved accent discipline:** category labels and banner accents can follow the icon's existing resolved accent without inventing another colour system.
- **Potential quality evidence:** generated optical-size, mask, contrast, and mapping receipts would be a stronger differentiator than another raw icon-count claim.

### 8.2 Weaknesses to close

- Runtime, custom-card, and store-preview contracts are currently easy for users to conflate.
- The physical square count was previously easy to overstate by including non-catalogue branding files; alias populations differ between square and banner output.
- Source-grid compliance has not yet been paired with actual Monet, Projectivy, or stock-TV mask screenshots.
- Category kickers may be useful visual metadata but have weaker official support than full app-name identity.
- Fallback/monogram optical parity is not yet proven at the host's smallest tile sizes.
- Mapping, cache, and manual-override recovery are user-visible product behaviour, not merely XML correctness.
- Fire TV and stock Google TV claims would be risky without a path-specific test.

### 8.3 Ecosystem opportunities

1. **Distance-ready release receipts:** make the quality layer searchable and reproducible by users and maintainers.
2. **Surface-aware distribution:** label Projectivy Banners, Monet/1:1 Glyphs, and optional Play preview output separately.
3. **Mapping confidence:** publish manual-only and unverified status rather than implying every catalogue entry is automatic.
4. **Open-launcher compatibility:** keep standard ADW `appfilter.xml`/`drawable.xml` foundations while documenting full component strings, aliases, and manual picker behaviour for newer TV launchers.
5. **Request/recovery loop:** combine current app requests with a clear rule for reviewed brand marks, Core monograms, and unresolved fallbacks.
6. **Optional store asset generation:** produce an opaque listing image only when a distribution target needs it, without polluting the transparent runtime set.

### 8.4 Implementation risks

- A global shrink to answer one Projectivy complaint could make sparse or tall glyphs weaker and would not fix host row spacing.
- A circle-spill count could cause false positives if alpha presence/glow is treated as critical brand ink.
- Removing all category kickers could improve platform restraint but reduce the current Core navigation cue; it needs an A/B/device review.
- Increasing opacity or adding a background could improve one host while breaking transparent glow, wallpaper integration, or another launcher.
- Adding `iconback`, `iconmask`, `iconupon`, adaptive, or monochrome furniture could improve phone fallback coverage while creating a second visual system and a TV-specific mask risk.
- Store-preview generation could be mistaken for a new runtime asset unless filenames, package contents, and documentation are explicit.
- Updating components without versioned device evidence can make an appfilter look more complete while reducing real auto-assignment accuracy.

## 9. Recommended sequence — still no production regeneration

### P0: document and measure

1. Adopt the five-surface matrix in pack documentation and release notes.
2. Correct count terminology: catalogue entries, component strings, generated rows, physical canonical resources, aliases, and non-catalogue branding resources.
3. Add a generated receipt design to the implementation plan; begin with a report-only mode.
4. Record host build/version, row/card size, spacing, tint, focus, and mask settings with every screenshot.

### P1: verify hosts before altering art

5. Refresh a current Monet probe and capture its actual square/tile masks and smallest layout.
6. Capture Projectivy main-row, side-panel, folder, manual-image, focus/glow, tint, and spacing states.
7. Test the Android/Google TV official template/safe zone and a real 320 × 180 banner surface where available.
8. Keep the current app-name light ink and resolved-accent correction while running the category-kicker A/B review.
9. Compare generic/header and app-specific art in the same slot before considering any optical shrink.

### P2: approved generated outputs only

10. Add optical/mask/display-surface receipts to the deterministic generator after schema review.
11. Generate an optional, separately named opaque 1280 × 720 Play preview only if a store/distribution task requires it.
12. Add mapping-confidence and recovery documentation, including manual override/cache behaviour observed on each host.
13. Only after review, regenerate catalogue-derived SVG, WebP, aliases, XML, docs, or previews from `tools/catalog.json`.

### Explicitly not approved by this pass

- changing Monet's catalogue name or component mapping;
- replacing the transparent runtime banner with an opaque Play preview;
- mass-shrinking the 945 glyphs from the circle diagnostic;
- removing or recolouring category labels without an approved experiment;
- claiming stock Google TV, Fire TV, or Monet mask compatibility from dimensions alone;
- adding adaptive/monochrome/fallback furniture to the default TV pack;
- hand-editing generated mappings or production assets.

## 10. Sources and confidence

Accessed **25 September 2026** unless a publication/update date is shown.

### Platform and packaging

- [TV app icon design guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines) — runtime 1:1/16:9 surfaces, adaptive safe zone, legacy 320 × 180 banner, full logo/text, and no Android/Google TV themed icons.
- [Create and run a TV app](https://developer.android.com/training/tv/start/start) — `CATEGORY_LEANBACK_LAUNCHER`, `android:banner`, and TV launcher visibility.
- [`<application>` manifest element](https://developer.android.com/guide/topics/manifest/application-element) — application-level banner semantics; page current as of August 2026 in the search result.
- [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality) — TV-LB 320 × 180 banner and 160 × 160 xhdpi icon criteria, plus 2026 TV quality tiers.
- [TV app publishing checklist](https://developer.android.com/training/tv/publishing/checklist.html) — legacy 320 × 180 banner and localized identifying text.
- [Play Console preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en) — Android TV screenshot/banner requirement and 1280 × 720 opaque TV preview banner.
- [Lawnchair icon-pack support](https://github.com/LawnchairLauncher/lawnchair/wiki/Icon-pack-support) — ADW `appfilter.xml`, `drawable.xml`, component mapping, and optional fallback furniture semantics.
- [Blueprint icon-pack setup](https://github.com/jahirfiquitiva/Blueprint/wiki/Setting-up-icon-pack-(Part-1)) — duplicated assets/res XML conventions used by older and newer launchers.
- [Fire TV design and UX guidelines](https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html) — separate 10-foot Fire TV design context.
- [Fire TV display and layout](https://developer.amazon.com/docs/fire-tv/display-and-layout.html) — 720p/1080p display contexts.
- [Fire TV live-TV resources](https://developer.amazon.com/docs/fire-tv/live-tv-resources.html) — package-manager application-banner example for a Fire TV surface.

### Launchers, packs, and user signals

- [Projectivy 4.70 release](https://github.com/spocky/miproja1/releases/tag/4.70) and [4.71 release](https://github.com/spocky/miproja1/releases/tag/4.71) — custom banner/icon, tint/glow, sizing, palette, mapping, refresh, and reliability behaviour.
- [Projectivy Icon Pack discussion](https://www.reddit.com/r/Projectivy_Launcher/comments/1mtmall/introducing_the_projectivy_icon_pack/) — spacing, smaller-icon, and generic category/header-size feedback; anecdotal.
- [Projectivy manual icon discussion](https://www.reddit.com/r/Projectivy_Launcher/comments/1m61wy2/how_do_u_install_icon_packs_and_also_sort/) — manual image assignment and 320 × 180 user guidance; anecdotal.
- [Monet Play listing](https://play.google.com/store/apps/details?id=com.klevico.monet&hl=en-US) — current listing claims for palettes, shapes, tile styles, overrides, and profiles; vendor/store signal, not a host implementation audit.
- [LTvLauncher](https://github.com/hamishakl/LtvLauncher), [Arc Launcher](https://github.com/meddouribadis/arclauncher), [Liquid Launcher TV](https://github.com/FirefliesStudios/liquid-launcher-tv), [Tiny Launcher](https://github.com/wyattberry-org/Tiny-launcher-) — current custom-banner, wallpaper, card, and icon-pack ecosystem signals; compatibility claims remain launcher-specific.
- [Android TV Minimalist](https://github.com/hqn-scl/android-tv-minimalist-icon-pack), [Minimal TV Icons](https://github.com/Mortisshadow/minimal-tv-icons), and [Projectivy Icon Pack](https://github.com/SicMundus86/ProjectivyIconPack) — curated alternatives, requests, update and manual-mapping expectations.

### Visual research

- [Material 3 icon design](https://m3.material.io/styles/icons/designing-icons) and [Material Symbols axes](https://developers.google.com/fonts/docs/material_symbols) — optical size, live area, and weight principles; not TV pack requirements.
- [WCAG G207](https://www.w3.org/WAI/WCAG21/Techniques/general/G207) — 3:1 meaningful non-text/icon contrast reference; not a complete TV certification.
- [The effect of icon spacing and size on visual search](https://doi.org/10.1016/S0141-9382(03)00035-0) — 0.72° small-icon/search-time finding at a 40 cm test distance; useful design research, not a direct 10-foot-TV threshold.

**Confidence:** Play's 1280 × 720 opaque preview rule, Android TV's runtime 320 × 180/full-logo guidance, manifest/banner semantics, and local file counts are high confidence. Fire TV path behaviour, current Monet mask/sampler implementation, Projectivy's exact geometry in every state, and all Reddit/Play-review observations remain versioned or anecdotal. The 216/256 circle numbers are intentionally diagnostic, not defect counts. Human recognition and distance claims still require a controlled test.
