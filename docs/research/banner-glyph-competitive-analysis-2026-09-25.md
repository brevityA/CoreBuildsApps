# Core Builds banners and glyphs — competitive research and upgrade plan

> **Status on `main` (2026-09-25):** the accent-coloured category kicker this document describes was proposed in Arena's commit `f9d3028` and was **not** merged. `main` still draws every kicker in Signal cyan `#00d4ff`, from one `ACCENT` constant in `tools/build_banners.py`. Read statements that the fixed-colour kicker is "corrected" as a proposal awaiting a device A/B test, not as shipped behaviour.

**Research date:** 25 September 2026 (Sydney)  
**Status:** research only. No catalogue, glyph, generator, or generated asset was changed for this report.

This report consolidates the current Core Builds asset audit with Android TV guidance, launcher documentation, open-source packs, competitor repositories, and user feedback. It is deliberately about **both** asset families:

- the 16:9 banner mapped by the default `Core Builds Icon Pack`; and
- the square glyph mapped by `Core Builds Glyphs`, the companion package.

It supersedes neither the generator contract in [`AGENTS.md`](../../AGENTS.md) nor the existing research documents. In particular, the style constraints in [`docs/BRAND-GUIDE.md`](../BRAND-GUIDE.md) and `tools/catalog.json` remain authoritative.

## 1. Executive conclusion

Core Builds is already unusually strong on the things that make an Android TV icon pack usable:

- **961 catalogue icons**, **1,179 catalogue component strings**, and **1,807 generated appfilter rows** after the two component spellings are emitted;
- a generated **512 × 512 transparent square** asset and a generated **320 × 180 transparent banner** for every catalogue entry, with duplicate byte-identical art collapsed by aliases;
- a coherent original monoline system, light app-name ink, per-app accent, standard appfilter packaging, a Banners/Glyphs split, an in-app updater, previews, and an established icon-request/mapping-issue workflow.

The strongest competitors do not beat Core Builds across that whole surface. They beat it on narrower axes:

| Axis | Current leader or evidence | Core Builds position | Consequence |
| --- | --- | --- | --- |
| TV-specific reputation and distribution | Projectivy Icon Pack: GitHub, Play distribution, Downloader flow, request/mapping documentation | Strong artefact, weaker discovery/distribution | Fix listing, screenshots, and install guidance before redesigning art |
| Transparent 16:9 card composition | Projectivy and Core Builds | Core now has the same important format, with broader catalogue | Do not copy opaque full-card competitors by default |
| Curated bespoke recognition | Projectivy and Mortisshadow's curated pack | Core has 427 glyph tokens and 451 letter-tile entries, so recognition is uneven | Spend the next art budget on high-use letter/fallback entries, not on a new global style |
| Unified full-canvas presentation | Android TV Minimalist and Minimal TV Icons | Core intentionally delegates the surface to the launcher | Test as an experiment only; it conflicts with transparent/tinted/tile modes |
| Coverage/fallback narrative | Blackshield repository claims aggressive coverage and generic fallbacks | Core's catalogue and mappings are auditable but no ADW fallback furniture is shipped | Improve honest mapping/fallback handling; do not copy unverified marketing claims |
| Dynamic-colour compatibility | Monet and Projectivy recolour or sample icon/tile surfaces | The recent fixed-colour rail/kicker bug is corrected; gradient and themed-mode behaviour remain unverified | Test the resolved accent on real Monet builds, especially themed tiles |

**Recommendation:** keep the transparent, one-accent, original Core Builds language as the default. Make it stand out by improving the *signal inside that language*: one unmistakable focal silhouette, optical size, safe-area discipline, controlled accent sampling, and excellent apply/update feedback. A full-bleed opaque banner variant and animated glow set are experiments, not safe replacements.

## 2. What is authoritative and what is preference

### 2.1 Platform and interoperability requirements

These are requirements or strong compatibility guidance, not opinions about Core Builds' aesthetic:

1. **Android TV uses both square and 16:9 identity surfaces.** Google's TV icon guide describes `android:icon` as 1:1 and `android:banner` as 16:9. It recommends adaptive assets and gives legacy xhdpi banner resources as **320 × 180 px**. Core's two generated dimensions are therefore defensible; the 320 × 180 banner should remain a release gate. [Android TV app icon design guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines) (updated 27 June 2024; accessed 25 September 2026).
2. **Safe geometry matters.** Google says to keep banner/logo content in the safe area, avoid cropping or spill, and provides a Figma template. For adaptive square icons it documents a 72 × 72 safe zone in the template. That adaptive safe zone is not a licence to reinterpret the 40 px Core square pad as a platform-mandated number; the exact Core 432 px safe area is a project contract and should be validated separately.
3. **Android TV does not support themed icons.** A monochrome layer is not required for the Android TV platform. Blackshield's adaptive/monochrome packaging may be useful for phone launchers, but it is not a reason to add Android-TV-only monochrome art. [Android TV icon guide, adaptive icon section](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines#adaptive-icon).
4. **Overscan is a real TV concern.** Google's TV layout guidance recommends a 5% horizontal margin and 48 dp / 27 dp guidance for important layout content, while backgrounds may fill the screen. This is layout guidance rather than a direct icon-pack crop rule; use it as a conservative test condition, not as a false exact banner formula. [TV layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts) (updated 9 May 2025; accessed 25 September 2026).
5. **Contrast is an accessibility floor, not a brand palette.** WCAG 2.2 provides 4.5:1 for normal text, 3:1 for large text, and 3:1 for meaningful non-text UI boundaries. Launcher-owned card colours are not a single background, so these must be measured on representative light, dark, tinted, and photographic surfaces. [WCAG 2.2, contrast minimum](https://www.w3.org/TR/WCAG22/#contrast-minimum) and [non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) (Recommendation 5 October 2023; accessed 25 September 2026).
6. **The icon pack must not assume stock Google TV can apply it.** Google TV/stock Android TV does not expose a general third-party icon-pack engine. Projectivy is the principal full icon-pack target; other launchers vary between standard appfilter support and per-app custom images. Treat compatibility as a matrix to verify, not an Android-wide guarantee.

### 2.2 Subjective but useful design preferences

These are conclusions from competitor practice, TV-distance design, and the current Core style—not platform mandates:

- one dominant focal mark beats a collection of equally loud details;
- one stable visual hierarchy beats decorative information in a narrow card;
- a recognizable silhouette or negative-space cue beats a generic letter when a good cue is available;
- optical sizing by visible ink beats equal SVG viewBox sizing;
- transparent art is preferable when the launcher owns card colour, tint, corner radius, and focus treatment;
- filled shared backgrounds are preferable only when the product intentionally owns the whole card and can test it against the host launcher;
- colour can aid recognition, but a user must still distinguish icons when a launcher tints or desaturates them;
- a short, high-quality request/mapping loop is a product advantage, not administrative polish.

Apple's tvOS guidance is useful comparative design research—especially its preference for recognizable symbols and restraint with words—but it is not an Android TV requirement. [Apple Human Interface Guidelines: icons](https://developer.apple.com/design/human-interface-guidelines/icons) (accessed 25 September 2026).

## 3. Current Core Builds audit

### 3.1 Catalogue and package facts

Measured from the current checkout and generated resources on 25 September 2026:

| Measure | Result | Interpretation |
| --- | ---: | --- |
| Catalogue entries | 961 | Source-of-truth app coverage |
| Component strings in catalogue | 1,179 | Mappings before generated dual-form expansion |
| Generated appfilter rows | 1,807 | Both full and short activity spellings |
| Physical catalogue-backed square WebP files | 945 at 512 × 512 | `aliases.xml` adds 16 drawable names pointing to canonical art; 12 non-catalogue branding WebPs are excluded |
| Physical catalogue-backed banner WebP files | 957 at 320 × 180 | `banner_aliases.xml` adds 4 banner names pointing to canonical art |
| Unique glyph tokens in catalogue | 427 | 26 letter tokens plus 401 non-letter tokens |
| Entries using `app_A`…`app_Z` | 451 / 961 (46.9%) | A meaningful fallback layer, not the former majority-two-thirds claim in older notes |
| Distinct catalogue accent values | 206 | Rich brand colour variation; needs sampling discipline |
| Banner style | 961 standard generated lockups | No current full-bleed/opaque banner variant |

The square scan must exclude `*_banner.webp` and non-catalogue branding resources; mixing the two dimensions or counting branding assets produces a false square result. The corrected scan is:

| Resource family | Median non-transparent coverage | Median fully opaque coverage | Median file size |
| --- | ---: | ---: | ---: |
| Core square WebP, 512 × 512 | 32.25% | 22.58% | 8.0 KiB |
| Core banner WebP, 320 × 180 | 7.09% | 4.86% | 2.1 KiB |

The low banner coverage is not automatically a defect. It confirms that Core banners are **transparent overlays intended to sit on launcher cards**, not complete card paintings.

### 3.2 Current banner grammar

`tools/build_banners.py` currently produces:

1. one Core monoline glyph;
2. the app name as light `#E6EDF3` Outfit paths;
3. an uppercase category/kicker label such as `VOD`, `STREAM`, `VIDEO`, or `APP`;
4. an accent-coloured left rail and kicker, using the icon's already-resolved accent;
5. a transparent 16:9 output, with the 1280 × 720 master downsampled to 320 × 180.

The recent Monet correction is the right direction: the former fixed cyan signal was a poor colour-sampling anchor for red, green, violet, or amber entries. The rail and category now agree with the resolved icon accent; the app-name text remains light ink for readability. Do not “fix” this by changing the app name to brand colour or by inventing new per-app hex values.

**Current risks to test, not assumptions to encode:**

- the rail is a broad, saturated sampling region, so a launcher that samples the largest or most saturated pixel cluster may still privilege the rail over the glyph;
- the category label is useful hierarchy for a human scanning a catalogue, but Google explicitly says to avoid text/graphics that communicate additional information on an app banner. A category is not part of the app's logo; it may be judged as extra information and it is too small to be a reliable 10-foot label;
- two-line long names can preserve minimum type size, but they need a physical-distance test against short names in the same row;
- Projectivy, Monet, and other launchers may apply their own tint, glow, card opacity, or focus animation. The generator cannot infer those results from a static SVG.

These are reasons for a controlled A/B test, not permission to hand-edit generated art.

### 3.3 Current square glyph grammar

The square contract is strong and should be preserved while it is measured more rigorously:

- 512 × 512 transparent canvas;
- 432 px project safe area / 40 px pad;
- rounded monoline weights of 32, 26.2, and 21.8 px in the source system;
- one main accent with transparent interiors rather than a vendor-logo slab;
- raster-only presence keyline/bloom for dark/tinted launcher surfaces;
- glyph fit and centring performed by tooling, not hand-edited WebP files.

The current 46.9% letter-tile share is not a reason to replace the style. It is a prioritisation signal: the next recognition pass should convert the most-used generic letters into specific Core line cues where a cue can be drawn without turning the pack into traced vendor logos. Maintain a letter fallback where an app genuinely has no stable, legally/visually appropriate symbol.

## 4. What the market is doing

### 4.1 Competitor matrix

Counts below are repository/readme observations, not normalized commercial audits. A repository's “icons” number can mean names, drawable files, resolutions, or marketing coverage.

| Pack / host | What it does well | What it does worse or leaves uncertain | Evidence checked |
| --- | --- | --- | --- |
| **Projectivy Icon Pack** | TV-specific reputation; transparent custom icons; automatic assignment; one-click apply/update narrative; dark-card guidance; request and mapping troubleshooting; Play/GitHub/Downloader distribution | Primarily square-first in its public identity; mapping can require re-apply/cache reset after updates; coverage and version claims change over time | [README](https://github.com/SicMundus86/ProjectivyIconPack), accessed 25 Sep 2026; the repo advertises 800+ icons and says mobile variants may expose different activities |
| **Android TV Minimalist** | Explicitly TV/Google TV focused; coherent shared background; “icon-first” symbols; avoids wordmarks where possible; vector reconstruction; visible package-name request process | Smaller curated scope; full/shared background is less compatible with launcher-owned transparent/tint modes; repository is not a standard Core-style generated dual package | [README](https://github.com/hqn-scl/android-tv-minimalist-icon-pack), accessed 25 Sep 2026; scan found 54 base marks at each of 1280 × 720, 1920 × 1080, and 3840 × 2160 |
| **Minimal TV Icons / Apple TV style** | Strong restraint; soft and solid variants; four display resolutions; words only when essential; clear refusal to become an unlimited request service | Curated scope intentionally excludes utilities/system tools; per-app manual workflow rather than a broad automatic pack; opaque canvases are not drop-in transparent glyphs | [README](https://github.com/Mortisshadow/minimal-tv-icons), accessed 25 Sep 2026; scan found 84 base marks per main resolution/variant family |
| **Blackshield Icon Pack** | Ambitious coverage/fallback story; generic/letter fallback tiers; adaptive and monochrome resource narrative; launcher-specific filters; `iconback`/`iconmask`/`iconupon` treatment | Claims are repository/marketing claims, not independently audited here; broad phone-launcher strategy is not evidence that full-card TV banners look better; official brand tracing and fallback furniture may conflict with Core identity | [Blackshield repository](https://github.com/Blackshield-Company/blackshield-icon-pack), accessed 25 Sep 2026. Its README claims 1,189 drawables, 48,120 components, 13,767 apps, and 20 launchers; treat all four numbers as unverified claims |
| **Arcticons / standard phone packs** | Broad ecosystem compatibility and mature Blueprint/ADW conventions; useful reference for packaging and fallback behaviour | Not designed around a 16:9 TV card; Projectivy itself recommends a coloured background for standard square packs | [Arcticons](https://github.com/Arcticons-Team/Arcticons), accessed 25 Sep 2026; [Blueprint](https://github.com/jahirfiquitiva/Blueprint), accessed 25 Sep 2026 |
| **Monet Launcher** | Dynamic wallpaper-derived colour; 7 icon shapes; 4 tile styles including themed/transparent; per-app image/colour/name overrides; adjustable tile size; focus animation | It is a host, not a competing pack; exact icon parser, sampled-pixel algorithm, shape masks, and all internal HDMI/Live TV component names are not publicly documented | [Google Play listing](https://play.google.com/store/apps/details?id=com.klevico.monet&hl=en-US), live listing accessed 25 Sep 2026; existing local probe is v1.0.84 and must not be treated as proof of current behaviour |
| **Projectivy Launcher** | The main TV icon-pack market: 16:9 cards, square/custom images, tint/transparent cards, focus glow, appfilter packs, manual picker, active community | Premium/features and version behaviour vary; transparent glow recipes in user posts are anecdotal and device-dependent | [Icon-pack plugins](https://projectivylauncher.com/plugins.html), [launcher site](https://projectivylauncher.com/), and [release history](https://github.com/spocky/miproja1/releases), accessed 25 Sep 2026 |

A small but important quantitative finding: a local Pillow scan of the sampled competitors found the Android TV Minimalist and Minimal TV assets occupy a substantial 16:9 canvas; the Minimal TV samples were fully opaque, while Minimalist samples used a substantial shared background layer. Core's banners are sparse transparent overlays. This is a **product-context distinction**, not evidence that Core's alpha coverage is too low.

### 4.2 What the strongest packs teach us

**Copy these behaviours:**

- Projectivy's direct apply, explicit troubleshooting, automatic mapping, and user-facing request loop;
- Minimalist's insistence on a symbol before a wordmark;
- Minimal TV's restraint: do not force an icon for an app whose identity cannot survive the format;
- Blackshield's concept of tiered fallback and launcher-specific packaging, but only after validating the target launchers and preserving the Core style;
- all successful packs' use of a preview that shows a **row of cards**, not only isolated artwork.

**Do not copy these by default:**

- opaque full-card canvases into a launcher that can already draw card colour, radius, tint, and focus;
- generic coverage numbers without an auditable app/component table;
- adaptive/monochrome layers as a response to Android TV, where themed icons are not supported;
- vendor-logo tracing, wordmark-only art, or a second unrelated geometry language;
- a large decorative rail or category colour that can cause the launcher to sample the wrong brand hue.

## 5. Design recommendations by surface

### 5.1 16:9 banners

#### Preserve

1. **Keep 320 × 180 as the release asset size.** It matches Google's legacy TV banner guidance and Core's Projectivy-first contract. Keep 1280 × 720 as a master for clean downsampling.
2. **Keep transparent backgrounds.** This lets Projectivy/Monet own card colour, corner radius, tint, focus glow, and tile style. It is also the strongest compatibility choice for the current default pack.
3. **Keep the full app-name lockup.** Google cautions against extra information but also says a banner should show the full logo, icon plus text. The app name is identity; the category kicker is optional metadata.
4. **Keep app names light ink.** The recent category-colour correction must not be inverted into light/dark app-name changes based on brand accent.
5. **Keep a single resolved accent.** The rail, kicker, and glyph should never disagree about the dominant hue. For gradient catalog entries, test a flat sampling-safe fallback rather than asking Monet to interpret an unknown gradient algorithm.

#### Change or test

1. **Run a current-vs-no-kicker A/B test.** Variant A is the current mark + rail + category + light name. Variant B is mark + rail + light name, with category retained in pack metadata/UI only. Compare at 320 × 180, the actual launcher card size, and a 3 m viewing distance. This is the highest-value banner experiment because Google treats extra information as a risk and the kicker is likely too small to read at distance. Do not remove it from the default until the result is reviewed.
2. **Make the focal order unambiguous:** mark first, app name second, rail as a restrained accent cue, category last or absent. A banner should be recognized when the text is temporarily occluded; a category should never carry recognition.
3. **Add a measured type gate.** Proposed Core criterion, not a platform rule: at the 320 × 180 output, the app-name cap-height should remain at least 16 px for two-line names and 18 px for one-line names; longer names should wrap before becoming visibly smaller than neighbouring cards. Record the actual raster measurement, not the SVG font size.
4. **Add a safe-area gate.** Proposed Core criterion: no visible logo/text pixels in the Google-template danger region; maintain at least 5% lateral and 7.5% vertical breathing room in a conservative overscan simulation. The 5%/7.5% numbers are test settings derived from TV layout guidance, not Android banner law.
5. **Test sampling, not just colour values.** For each banner, proxy a family of possible samplers: most saturated connected component, largest chroma-weighted area, median hue of alpha-bearing pixels, and dominant hue after 2×/4× downsample. The result should remain near the catalogue's resolved accent. A launcher algorithm is unknown, so these are risk probes, not compatibility guarantees.
6. **Do not add a border.** Google's icon examples specifically warn that borders can crop poorly. The existing rail is an internal accent element, not a canvas border; keep it inset and verify it cannot be mistaken for one.

### 5.2 Square glyphs

#### Preserve

- 512 × 512 transparent output and 432 px project safe area;
- 32 / 26.2 / 21.8 rounded source weights;
- one accent and transparent interiors;
- generator-based optical fitting and recentring;
- the existing dark presence keyline/bloom only where the launcher surface needs it;
- the distinction between a square glyph for tile/icon mode and a 16:9 banner for card mode.

#### Change or test

1. **Add a circle/mask safety audit before changing art.** Monet advertises seven icon shapes, including a circle according to the current local research. Recompute the current 945 catalogue-backed square resources rather than carrying forward the older 933-file/122-spill number or the directory-level 957 count that included 12 branding resources. Fail new out-of-safe-area glyphs in validation; only shrink existing offenders after a visual review, because shrinking can erase brand cues.
2. **Measure visible ink, not viewBox occupancy.** Proposed Core criterion: compare visible alpha bounding-box height/width, centroid offset, and primary-stroke width at 512, 256, 128, 96, 64, and 48 px. Use relative bands rather than one forced size: a very wide wordmark-like mark and a compact circle should not occupy identical raw boxes, but their perceived weight should sit within a reviewed band.
3. **Set a small-size detail floor.** Proposed starting point: no secondary gap or stroke that collapses below roughly 2 device pixels at the 96 px test tile; simplify geometry when it does. This is a test heuristic, not a Material or Android TV requirement.
4. **Prioritize recognition by use, not by logo count.** The 451 letter entries are the largest pool. First target the most-installed/most-visible letter fallbacks and category collisions (`T`, `F`, `M`, `A`, `S` are the most reused current letter tokens), then create a cue that remains Core monoline rather than tracing a full vendor logo. Keep letters for low-confidence brands.
5. **Test tint and desaturation.** Render each glyph in original accent, light ink, launcher-tinted monochrome, and a low-saturation theme. A glyph that only works because of brand hue is not robust in Monet themed mode or Projectivy tint mode.
6. **Test focus double-effects.** Compare the static raster presence ring with Projectivy glow and Monet focus animations. If the combination creates a fuzzy halo or a false rectangle, lower the raster presence rather than adding more glow to the art.

## 6. Monet and launcher rendering contexts

### 6.1 What is known

The Monet Play listing describes wallpaper-derived dynamic colour, 22 accent palettes plus a custom colour picker, light/dark modes, icon-pack support, seven icon shapes, four tile styles including themed and transparent, per-app overrides, and adjustable tile size. Projectivy documents icon packs, transparent/tinted cards, square icons/custom alignment, and a community focus-glow workflow.

The local probe under `docs/research/monet-probe/` is a decompilation record for **Monet v1.0.84**. It is useful evidence of that build only. It is not proof of the current Play build's pixel sampler, default tile mode, parser behaviour, or component names. Those claims are marked **unverified** until a current APK or device capture is available.

### 6.2 Practical Core rules

- Use the icon's resolved accent consistently in glyph, rail, and category; the fixed global cyan is retired.
- Keep app names light ink; names are not Monet's colour swatch.
- Treat gradient entries as a special risk class and generate a flat-accent comparison for testing. Do not invent a new catalogue colour just to satisfy a launcher sampler.
- Treat the launcher as the owner of tile surface, corner radius, focus, and glow. Do not bake a square tile or full-card background into the default transparent assets.
- In Monet, recommend **Core Builds Glyphs** for 1:1/tile/icon-shape use and the default **Core Builds Icon Pack** for 16:9 Projectivy cards. The existing companion split is a product advantage; document it prominently instead of making one appfilter serve mutually incompatible surfaces.
- Do not guess Monet HDMI/Live TV component names. Add them only from an ADB scan of the exact launcher/device build.

### 6.3 Sampling proxy and acceptance proposal

Because Monet's algorithm is not public, use a proxy matrix:

1. original square glyph and banner;
2. alpha-bearing pixels only, then composited against dark, light, and photographic surfaces;
3. 1×, 2×, and 4× downsampled forms;
4. original, grayscale, and a simulated wallpaper tint;
5. compare dominant hue, chroma, and luminance against the catalogue accent.

Proposed Core-only review bands:

- dominant intended accent within 15° hue of the catalogue accent for flat-colour entries;
- no unexpected fixed hue contributing more saturated area than the intended accent;
- the app remains identifiable in grayscale or launcher-tinted mode;
- no single broad rail swamps a small but distinctive glyph mark.

These are engineering heuristics, not Monet compatibility guarantees. Do not publish them as platform requirements.

## 7. Coverage, fallback, mapping, and product workflow

### 7.1 Coverage and fallback

Core's coverage is a strength only when it remains honest:

- publish the auditable catalogue count and generated mapping count separately;
- distinguish “catalogue icon”, “mapped component”, “physical unique art”, and “fallback letter/generic glyph” in release notes;
- keep a generic letter/category fallback where evidence for a bespoke cue is weak;
- promote a fallback to bespoke only when the mark survives the small-size and tint tests;
- record regional/device activity variants in mapping issues rather than guessing package names.

The current pack does **not** ship `iconback`, `iconmask`, or `iconupon` furniture. That is a deliberate style choice for the transparent classic pack. The ADW convention is documented by [Lawnchair's icon-pack support notes](https://github.com/LawnchairLauncher/lawnchair/wiki/Icon-pack-support) (accessed 25 September 2026), but Projectivy support for all fallback tags is not independently verified. Do not add fallback backgrounds to the default pack until a test APK proves that they do not turn unthemed apps into visually inconsistent tiles.

A safe experiment is a temporary validation build containing the tags, tested on Projectivy, Monet, Lawnchair, Nova, and one stock-TV-like picker. It must be rejected if it changes the default transparent appearance or causes fallback icons to appear as mismatched surfaces.

### 7.2 Mapping and internal launcher surfaces

Projectivy 4.70 release notes describe mapping support for internal activities such as settings, category/channel shortcuts, and HDMI/AV inputs. This is a real opportunity because users have asked for banner-shaped SmartTube/TV and HDMI icons, but activity names are version-sensitive.

Priority:

1. map known Projectivy internal activities from the existing companion research and a device scan;
2. add both activity spelling forms through the generator;
3. add device/build evidence to the catalogue or a mapping evidence file;
4. never infer Monet's closed-source internal components from labels alone.

### 7.3 Apply, preview, update, request

Core already has the foundations that the leading packs use:

- standard appfilter and launcher intent filters;
- one-click apply paths and a Banners/Glyphs choice;
- an in-app updater;
- generated preview boards and an in-app browser;
- separate issue forms for new icons and mapping failures.

The next usability improvement is not another art style. It is a clear first-run explanation:

> **Projectivy 16:9 cards:** apply Core Builds Icon Pack.  
> **Monet/1:1 tiles:** apply Core Builds Glyphs.  
> **If an icon is missing:** re-apply after an update, reset manual overrides, then report the app's actual component name.

Competitor user reports repeatedly mention manual overrides, stale caches, changed launcher activities, and needing to re-apply after updates. These are anecdotal but operationally important: [Projectivy pack discussion](https://www.reddit.com/r/Projectivy_Launcher/comments/1mtmall/introducing_the_projectivy_icon_pack/) and [Projectivy transparent glow discussion](https://www.reddit.com/r/Projectivy_Launcher/comments/1n22koa/getting-the-transparent-icons-to-glow/) (both accessed 25 September 2026). Core should make the recovery path visible in-app and in the README.

The official Projectivy plugin page currently says that Projectivy is the only pack supporting TV cards (16:9) while also listing Core Builds as a 900+ pack. That statement is stale now that this checkout ships generated 16:9 banners. Updating the Projectivy listing and the Core README/release screenshots is a high-impact, zero-art-risk task. [Projectivy plugins page](https://projectivylauncher.com/plugins.html), accessed 25 September 2026.

## 8. Objective validation plan

No visual claim should be accepted because it looks good in an isolated preview. Use a small, repeatable lab.

### 8.1 Asset-level gates

| Gate | Proposed measurement | Status |
| --- | --- | --- |
| Dimensions/alpha | Exact 512 × 512 square and 320 × 180 banner; no accidental opaque canvas | Already generator-controlled; retain |
| Safe area | Alpha bbox fully inside the project safe area; separate banner overscan simulation | Add explicit receipt/gate |
| Circle/mask | Ink radius under current tested circle mask, with offender list | Recompute current 945 catalogue-backed squares; directory-level 957 includes 12 branding resources |
| Optical fit | Alpha bbox width/height, centroid, primary/detail stroke widths at six raster sizes | Add report first; gate after review |
| Contrast | WCAG luminance contrast for name text; non-text contrast on representative surfaces | Add dark/light/photo matrix |
| Colour difference | CIEDE2000 or OKLab distance between intended accent and sampled accent proxy | Proxy only; Monet algorithm unverified |
| Raster sharpness | SVG-to-WebP reference diff, edge halo scan, 1×/0.5× inspection, SSIM/edge preservation where useful | Add as a regression report, not a blind pass/fail initially |
| Transparency | No hidden full-canvas fill, no accidental border, no alpha rectangle | Already scanned; retain |
| Mapping | Every appfilter drawable exists; every catalogue component has both intended forms | Already generator/validator-controlled |

### 8.2 Hardware/viewing matrix

Capture real screenshots with `adb exec-out screencap` where possible. The minimum matrix should include:

- Projectivy 16:9 banner cards, square cards, transparent card opacity, tinted card, focused glow on/off;
- Monet transparent and themed tiles, at least rounded-square and circle shapes, focused/unfocused, dark/light wallpaper, a photographic wallpaper, and one gradient-mark entry;
- one additional standard appfilter consumer and one manual custom-banner launcher, if hardware is available;
- 55-inch or larger 1080p/4K TV at approximately 1.5 m, 2.5 m, and 3.5 m viewing distances;
- short names, long names, two-line names, letter fallbacks, highly saturated accents, light-ink fallback entries, and the widest/tallest glyphs;
- at least one update/re-apply cycle, one manually overridden app, one regional activity variant, and one uncatalogued app.

Record device, Android TV version, launcher version, theme/tile settings, card pixel size, and whether the result was automatically mapped. “Looks correct on Android TV” without these fields is not reproducible evidence.

### 8.3 Recognition study

For a small human check, show cropped glyphs and banners with app labels hidden for 500 ms and ask which app/category is intended. Use a mixed set of bespoke marks, letters, repeated generic glyphs, and competitor samples. The goal is not a market-scientific score; it is to identify failures by visibility and usage.

Proposed review thresholds:

- 100% of assets pass geometry/transparency/dimension gates;
- no new safe-area or circle-mask regressions;
- no app-name contrast failure on the default dark card and a light-card test;
- at least 80% correct recognition for the top-use bespoke candidates at the selected 96 px glyph test size, with a documented reason for exceptions;
- mapping success above 95% for a fixed top-100 installed-app sample on each tested launcher, with failures classified as missing mapping, stale cache, manual override, regional activity, or launcher limitation.

The 80% and 95% numbers are proposed Core release criteria, not Android, Google, Monet, or Projectivy requirements.

## 9. Prioritized roadmap

### P0 — high impact, low risk; do before any broad redesign

1. **Freeze the current evidence baseline.** Add a corrected square/banner scan receipt, current counts, safe-area metrics, and a current 20–40 asset review board. Keep banners excluded from square statistics.
2. **Run the real Monet/Projectivy matrix.** Refresh the v1.0.84 Monet probe against the installed/current build; verify themed sampling, circle clipping, focus double-glow, and Banners/Glyphs choice.
3. **Add circle-safe and banner-safe validation.** Gate future regressions first; do not mass-shrink current glyphs before review.
4. **Update the distribution truth.** Correct the Projectivy plugin listing and Core screenshots/README so the market sees that Core ships TV-card banners, square glyphs, standard mappings, and the companion choice.
5. **Improve apply/update guidance.** Put the Projectivy-vs-Monet recommendation, re-apply/cache recovery, manual-override warning, and request link directly in the app's first-run/help surface.

### P1 — high impact, moderate risk; review generated previews before shipping

6. **A/B test the category/kicker.** Current default stays intact until the no-kicker result is reviewed. If the kicker loses at TV distance or violates the “no extra information” reading, retain the category in metadata and simplify the banner lockup.
7. **Recognition tranche.** Replace the highest-use generic letter fallbacks with Core monoline cues, starting with the most common reused letters and the top installed apps. Preserve the original geometry rules and source-of-truth workflow.
8. **Optical-size and small-raster tranche.** Use measured visible ink and stroke floors, not subjective equal boxes. Review actual 48/64/96 px renders beside established neighbours.
9. **Sampling-safe accent pass.** Flatten or simplify only the gradient risk entries if the proxy/device test shows Monet picking an unintended hue. Continue resolving accents from existing catalogue data; do not invent replacement hex values.
10. **Projectivy internal-activity mappings.** Add only scan-backed components, including both activity spellings, through `tools/catalog.json` and regeneration.

### P2 — useful product advantages; moderate cost

11. **Public distribution.** Match the strongest competitor's discovery surface with a maintained Play listing or equivalent trusted TV install path if the product decision permits; keep GitHub/Downloader as documented fallback.
12. **Coverage quality loop.** Publish a small coverage ledger: requested, mapped, art complete, tested, released. Keep issue forms and generated prefills synchronized.
13. **Preview lab.** Ship row-level screenshots for Projectivy 16:9 and Monet 1:1, including focused and tinted states, rather than only an isolated art wall.

### Experimental; do not ship as the default without an owner decision

14. **Opaque/full-canvas 16:9 variant.** Competitor packs prove the look can be coherent, but it fights launcher-owned card colour and Monet tile modes. Test as a non-default prototype only.
15. **APNG focus/glow pack.** Projectivy supports animated image formats and the community shares glowing APNGs, but decoding and APK size are device risks. Pilot roughly 20 icons on the target hardware first; do not generate hundreds of files from an unverified assumption.
16. **ADW fallback furniture.** Test `iconback`/`iconmask`/`iconupon` in a disposable build on every target launcher. Reject it if it makes the transparent pack visually inconsistent or if Projectivy ignores it.
17. **Adaptive/monochrome resource expansion.** Only pursue for a separately demonstrated launcher need; it is not an Android TV requirement and must not displace square/banner validation.

## 10. Source register and confidence notes

All web sources below were accessed on **25 September 2026** unless a publication/update date is shown. Local repository paths are evidence from this checkout, not independent market sources.

### Authoritative / platform

- Android TV app icon design guidelines, updated 27 June 2024: <https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines>
- Android TV layout and overscan guidance, updated 9 May 2025: <https://developer.android.com/design/ui/tv/guides/styles/layouts>
- Android TV colour system: <https://developer.android.com/design/ui/tv/guides/styles/color-system>
- WCAG 2.2 contrast minimum, W3C Recommendation 5 October 2023: <https://www.w3.org/TR/WCAG22/#contrast-minimum>
- WCAG 2.2 non-text contrast: <https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html>
- Material 3 icon guidance: <https://m3.material.io/styles/icons/overview>

### Launcher and pack ecosystem

- Projectivy icon-pack/plugin listing: <https://projectivylauncher.com/plugins.html>
- Projectivy launcher feature page: <https://projectivylauncher.com/>
- Projectivy launcher release history: <https://github.com/spocky/miproja1/releases>
- Projectivy Icon Pack repository: <https://github.com/SicMundus86/ProjectivyIconPack>
- Android TV Minimalist Icon Pack: <https://github.com/hqn-scl/android-tv-minimalist-icon-pack>
- Minimal TV Icons: <https://github.com/Mortisshadow/minimal-tv-icons>
- Blackshield Icon Pack: <https://github.com/Blackshield-Company/blackshield-icon-pack>
- Blueprint icon-pack template: <https://github.com/jahirfiquitiva/Blueprint>
- Arcticons: <https://github.com/Arcticons-Team/Arcticons>
- Lawnchair icon-pack support / ADW fallback conventions: <https://github.com/LawnchairLauncher/lawnchair/wiki/Icon-pack-support>
- Monet Launcher Play listing: <https://play.google.com/store/apps/details?id=com.klevico.monet&hl=en-US>

### User feedback and comparative guidance

- Projectivy Icon Pack announcement and user discussion (apply, transparency, mapping): <https://www.reddit.com/r/Projectivy_Launcher/comments/1mtmall/introducing_the_projectivy_icon_pack/>
- Transparent-icon glow setup discussion (anecdotal, device-dependent): <https://www.reddit.com/r/Projectivy_Launcher/comments/1n22koa/getting-the-transparent-icons-to-glow/>
- Projectivy banner/HDMI request discussion cited in local research: <https://www.reddit.com/r/Projectivy_Launcher/comments/1icfbn7/how_to_i_install_icon_pack_also_can_i_make_my/>
- Apple HIG icon comparison: <https://developer.apple.com/design/human-interface-guidelines/icons>

### Local evidence and caveats

- Source of truth: `tools/catalog.json`.
- Banner generator and colour-sampling correction: `tools/build_banners.py`.
- Square/banners package split: `AGENTS.md`, `tools/build_icons.py`, `tools/build_banners_pack.py`.
- Current launcher style audit: [`launcher-style-audit-2026-09.md`](launcher-style-audit-2026-09.md).
- Existing format/coverage research: [`android-tv-icon-packs.md`](android-tv-icon-packs.md).
- Existing Monet v1.0.84 probe: [`monet-probe/README.md`](monet-probe/README.md).

**Confidence:** platform dimensions and Android TV themed-icon limitation are high confidence. Competitor repository contents and README claims are medium confidence and date-sensitive. Monet pixel sampling, exact internal component names, and third-party launcher handling of every ADW fallback tag are unverified until tested on the current installed builds. All numerical thresholds introduced as “proposed Core criteria” are engineering heuristics, not platform mandates.
