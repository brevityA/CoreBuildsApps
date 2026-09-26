# Modern generic Glyphs design addendum — 25 September 2026

**Scope:** design research for the square Glyphs pack, with the 16:9 Banners pack as the paired output surface.  
**Status:** research and design recommendation only. No catalogue entries, component mappings, generators, SVGs, rasters, previews, release assets, or launcher code were changed.

This addendum follows the global-toggle feasibility report in [`glyphs-global-toggle-feasibility-2026-09-25.md`](glyphs-global-toggle-feasibility-2026-09-25.md). The toggle/package decision remains separate from this art decision: a launcher selects one complete pack, while both packs should continue to derive from the same identity and mapping contract.

## 1. Decision in one paragraph

The next generic-Glyph direction should be **symbol-first semantic fallback**: draw a small, disciplined set of recognisable functional symbols before falling back to a category shell plus an app mark, and use a neutral monogram only when neither a function nor a defensible brand cue is known. This is more modern and more useful at TV distance than expanding the alphabet of letter tiles, but it must not become a second collection of traced vendor logos or a new catalogue/mapping system.

The recommended order is:

1. **A real, recognisable public cue** interpreted in Core Builds geometry when one exists and is strong at TV size.
2. **A semantic symbol** when the app's function is known but the brand has no useful emblem.
3. **A category shell plus adaptive mark** for a known family where a symbol would be too broad or misleading.
4. **A neutral monogram** for genuinely unknown or wordmark-only long-tail entries.

The existing Banners/Glyphs global selection, package split, catalogue, mappings, and generated-asset pipeline do not need to change for this direction.

## 2. What “generic” means here

“Generic” has two different meanings in the repository and in competitor packs:

| Term | Meaning | Design consequence |
|---|---|---|
| **Generic semantic glyph** | A function symbol such as a folder, globe, controller, waveform, shield, or download arrow. | It should communicate the app family before the user reads the label. |
| **Generic identity fallback** | A letter or short mark used because the app has no reliable symbol or public emblem. | It should be deliberately systematic, not presented as a bespoke logo. |

A symbol-first system is not a requirement to make every app look like a vendor logo. It is a way to give the long tail useful visual information without inventing brand identity. It also avoids the opposite failure: replacing every app with the same play button, screen, shield, or equalizer.

The square Glyph is the binding surface for this decision. A 16:9 Banner can carry an app name and category lockup, but a square card often has only the mark and the launcher-supplied background. A symbol must therefore survive without the banner's text. Conversely, the Banner should remain a layout derived from the same identity rather than receiving a second, unrelated generic drawing.

## 3. Confirmed current Core Builds implementation

### 3.1 The repository already has a useful intermediate strategy

The current `tools/glyphs.py` is not only a letter-tile registry. It contains:

- semantic primitives such as `stream_tower`, `gamepad`, `tools_wrench`, `send_arrow`, `broom`, `shield_key`, `automation`, `home_button`, `tv_stack`, `folder`, `gear`, `remote`, and `waves_circle`;
- more specialised symbols such as `stream_wave`, `folder_tree`, `drive_sync`, `ip_globe`, `net_pulse`, `input_screen`, `hdmi_connector`, `usb_media`, and `tv_antenna`;
- a large family-shell registry (`broadcast`, `app`, `tool`, `sport`, `music`, `gaming`, `vpn`, `film`, `store`, `photos`, `debrid`, `browser`, `anime`, `kids`, and `files`) that puts an adaptive app mark inside a function-shaped outline;
- a neutral app shell for cases where the function cannot be read defensibly; and
- brand-informed constructions that leave the generic fallback only when a public cue is strong enough.

This means the practical next step is not to invent a new icon-pack architecture. It is to decide which existing and future generic primitives are strong enough to replace a family shell or monogram, then apply the decision consistently.

The family-shell approach is a meaningful improvement over a plain letter in a box: the silhouette can say “browser,” “game,” “file,” or “music” before the letter is read. It is still **letter-led**, however. It should be treated as an intermediate fallback, not as the final symbol-first answer for every known app family.

The comment near the family registry records a historical “549 icons” state. That is useful evidence for why the work began, but it is not a current catalogue census and should not be reused as a present percentage without rerunning the measurement method.

### 3.2 Current visual contract is a constraint, not a limitation

`tools/icon_style.py`, `tools/glyphs.py`, and the existing validators establish the Classic contract:

- transparent artwork; the launcher card or tile supplies the surface;
- one resolved display accent per icon, with the existing contrast-preserving colour policy;
- a shared 512 × 512 construction grid and 432 px safe area;
- rounded caps and joins;
- the Core monoline weights 32, 26.2, and 21.8 after normalisation;
- no fixed container, vendor fill, private transform, filter, or fixed-white wordmark for a Core monoline construction; and
- the app name/category text remains a banner concern and remains light ink where the existing readability policy requires it.

These rules are valuable on TV. They prevent a generic symbol study from drifting into a collection of unrelated phone-style logos. A modern generic glyph should be a better information shape **inside** this contract, not a reason to discard it.

### 3.3 Existing fit and review tooling points in the right direction

The repository already has `tools/glyph_metrics.json`, `tools/classic_glyph_fit.json`, the Classic fit generator, presence checks, and identity review tooling. The merged icon-tranche work also introduced objective checks for counters, safe area, ink coverage, and nearest-neighbour collision at small sizes.

These checks answer whether a drawing survives reduction. They do not answer whether it communicates the intended cue. Both gates are required. A valid SVG that looks like an equalizer when it was meant to be a waveform is still a failed Glyph.

### 3.4 Variants should remain renderers, not competing identities

The Core Builds design history treats Classic, Pop, Pixel Neon, square outputs, and Banner outputs as sibling representations of the same catalogue identity. Merged PR #135 regenerated the affected Classic, Pop, Pixel Neon, square, and Banner resources together while keeping mappings unchanged. That is the correct precedent for generic work:

- maintain one identity/cue decision;
- render that decision in each style's own grammar;
- regenerate every derived surface; and
- do not fork component mappings merely because a glyph renderer changes.

The current checkout should be treated as the source of truth for what is present on this branch. The Pop/Pixel Neon and tranche statements above are claims about merged repository history and its review precedent, not permission to hand-copy assets from another commit or to assume that every historical variant directory is present locally.

## 4. What current and adjacent Core Builds work teaches us

### 4.1 PR #135: semantic cue beats geometric validity

Merged PR #135 is the strongest internal precedent for this research. It redrew eight generic or weak constructions using original geometry and recorded the identifying cue beside each one. It also required small-size review and nearest-neighbour comparison.

The review corrections are more important than the individual marks:

- **Blokada:** horizontal bands technically formed a valid shield, but contradicted the documented descending-diagonal cue and read as a generic striped shield. The bands were redesigned on the correct slope.
- **Audiomack:** evenly spaced bars passed geometry checks but became a generic equalizer. The replacement uses an intentionally asymmetric waveform with unequal spacing and a dominant pulse.
- **Crossy Road:** the first whole-bird abstraction became a blocky figure at 96 px, so the identifying head/profile cue was prioritised.
- **Kinopoisk:** detached ray ticks became noise at 48 px, so the rays were made continuous with the letter arms.
- **mpv:** `mpv_play` remained close to the generic `play_round` neighbour even after passing the objective threshold. The PR explicitly records that it still needs real launcher-size comparison.

The general rule is therefore:

> **A generic glyph needs one clear identifying cue, and that cue must survive the TV review size. Path validity, safe area, and low collision alone are not sufficient.**

This is also why the proposed direction is not “replace every letter with a random pictogram.” A weak pictogram can reduce identity more than a disciplined fallback letter.

### 4.2 PR #134: isolated testing is a design tool

Merged PR #134 added the isolated candidate APK path so redraws could be reviewed on a real launcher without replacing the production package. Its four first-group marks were original constructions, and the PR required review at launcher/card size and at 320 × 180 banner size before promotion.

That distribution separation is useful for a future generic-symbol tranche. Generic symbols are especially vulnerable to looking clear in a contact sheet but collapsing beside real TV neighbours. A test package and a regenerated mixed row are safer than changing the production catalogue while the direction is still being judged.

### 4.3 Earlier Core Builds research: consistency is the moat

The existing design research compared Core Builds with Projectivy's much more bespoke per-app art. Its durable conclusion was not to copy filled vendor logos. The Core Builds advantage is enforceable consistency: transparent marks, one-accent treatment, a shared line language, generated banners, and repeatable validation.

The correct response to a long tail of generic marks is therefore **more information per mark while preserving the system**, not a switch to full-card vendor artwork or arbitrary logo tracing.

### 4.4 The other Core Builds repository: useful motif language, different surface

The separate [`Core-Builds`](https://github.com/brevityA/Core-Builds) repository is an AIOStreams/configurator project, not a second Android icon-pack repository. It does contain adjacent Core Builds visual assets:

- `Assets/Discord-Emojis/` includes `cb_stream`, `cb_gear`, `cb_repo`, `cb_4k`, `cb_cached`, `cb_bolt`, `cb_chat`, `cb_info`, `cb_support`, and related utility symbols;
- `Assets/core_icon.svg` and the documentation logo assets use a dark circular presentation, a hex/diamond motif, cyan-to-blue or cyan-to-violet accents, and glow/gradient treatments; and
- the documentation CSS uses a cyan accent and dark surfaces as interface chrome.

This repository is useful evidence of **semantic motif vocabulary**: stream, gear, repository/files, display/4K, cache/storage, support, information, and performance are already legible parts of the wider Core Builds language.

It is not a direct Classic Glyph recipe. Those assets intentionally use dark circular badges, filled shapes, gradients, and glow because they are Discord/documentation badges. Importing those surfaces into the transparent, launcher-card-controlled Classic pack would reintroduce the very fixed-container and effect problems that the icon contract removed.

## 5. External inspiration: what to borrow, and what not to copy

The following sources are separated from repository facts. They are design references, not platform requirements and not evidence that Core Builds should change its mappings.

### 5.1 Android TV Minimalist Icon Pack — symbol-first and TV-specific

The [`android-tv-minimalist-icon-pack`](https://github.com/hqn-scl/android-tv-minimalist-icon-pack) project is the clearest TV-focused reference for this direction. Its stated approach is to redraw a recognisable symbol in a unified neutral presentation, target dark backgrounds, use vector-first artwork, and use a wordmark only when no meaningful symbol exists.

**Borrow:** prioritise the symbol, use a small number of strong primitives, and judge the result on a dark TV card rather than on a large design canvas.  
**Do not copy:** its neutral/background treatment as a fixed Core Builds container, or its exact geometry and vendor marks.

### 5.2 Minimal TV Icons — curation is a quality feature

[`minimal-tv-icons`](https://github.com/Mortisshadow/minimal-tv-icons) uses an Apple TV-inspired composition, a curated active-app scope, and a reluctance to force weak text-only or utility representations.

**Borrow:** a smaller high-quality semantic set is better than claiming exhaustive but low-information coverage; a symbol can be rejected when it does not earn a place in the primary TV row.  
**Do not copy:** the curated scope as a reason to remove Core Builds coverage. Core Builds has a large catalogue and needs a graceful fallback for the long tail.

### 5.3 Arcticons — formalise the stroke system

[`Arcticons`](https://github.com/Arcticons-Team/Arcticons) demonstrates the value of a large handcrafted monotone line system with explicit consistency across caps, joins, outline behaviour, and style variants.

**Borrow:** write down construction rules and keep variants as renderer changes over one identity registry.  
**Do not copy:** mobile line density, tiny details, or phone-oriented optical assumptions into a TV card. A dense 24–48 px phone icon can become noise on a television viewed from several metres away.

### 5.4 Lawnicons — modern outlined and themed variants

[`Lawnicons`](https://github.com/LawnchairLauncher/lawnicons) provides a useful reference for rounded outlined geometry, Material You-aware monochrome treatment, and multiple icon variants.

**Borrow:** formal stroke/corner rules and a clear distinction between the source identity and the theme/renderer applied by the launcher.  
**Do not copy:** assumptions about Lawnchair's implementation to every ADW-compatible launcher, or Material You's mobile-sized detail density to TV Glyphs.

### 5.5 iOSIconPack — one mapping, multiple visual representations

[`SysAdminDoc/iOSIconPack`](https://github.com/SysAdminDoc/iOSIconPack) explicitly separates monochrome vector and transparent-glyph variants while preserving the same component identity/mapping model.

**Borrow:** style variants should share identity and mapping contracts. This supports Core Builds' Banners/Glyphs split and future Classic/Pop/Pixel Neon renderers.  
**Do not copy:** the visual treatment or assume a launcher can select two variants inside one standard appfilter. The global-toggle report's two-package conclusion still applies.

### 5.6 Projectivy Icon Pack — bespoke identity is a benchmark, not a generic recipe

The [`Projectivy Icon Pack`](https://github.com/SicMundus86/ProjectivyIconPack) remains a useful benchmark for per-app recognisability, transparent backgrounds, and dark-card presentation. It is not proof that Core Builds should abandon a generated system for a hand-painted, one-off mark for every long-tail app.

**Borrow:** ask whether a user can identify a high-priority app by shape before reading the label.  
**Do not copy:** its per-app art process as a requirement for every Core Builds mapping, or its launcher-specific assumptions as platform rules.

## 6. Recommended generic direction

### 6.1 A constrained primitive registry

Create a design registry in a future approved implementation, not in this research-only change. It should document each primitive's intended cue, permitted secondary detail, and rejection conditions. It should be small enough to remain coherent:

| Family | Preferred primitive cues | TV failure to reject |
|---|---|---|
| Video / streaming | open play, screen, clapper, broadcast waves | every service becoming the same screen-plus-play; dense waves turning into stripes |
| Audio / music | waveform, speaker, note, record dot | evenly spaced bars becoming an equalizer when the intended cue is a waveform |
| Files / storage | folder, document, cloud, database, sync arrows | cloud/folder outlines closing into an unreadable blob |
| Network / privacy | globe, connected nodes, lock, shield | every network or VPN app becoming the same shield |
| Games | controller, cross-shaped D-pad, trophy, pixel creature | controller used for unrelated game services; tiny button counters merging |
| Browser / search | globe, browser window, compass, magnifier | a generic circle with no visible browser/search cue |
| Tools / system | gear, wrench, sliders, download, terminal, information | too many teeth, rails, or small settings knobs at 48 px |
| Remote / input | remote, HDMI plug, source arrow, TV antenna | an input mark that resembles a normal media player |
| Store / install | bag, package, box, download tray | bag/box becoming a generic rounded rectangle |
| Photo / gallery | print/frame, mountain-and-sun, camera only when its silhouette survives | camera hump or tiny landscape detail reading as a briefcase/noise |
| Kids / family | simple friendly spark, ears, rounded companion shape | decorative ears or stars becoming unexplained noise |

The registry is a construction guide, not an instruction to add one new glyph for every row of the catalogue. A single primitive may serve a genuinely shared function; unrelated apps must not be grouped solely because they have similar names or a shared package prefix.

### 6.2 The selection ladder

For each future candidate, use this order:

1. **Brand cue:** use a Core Builds construction if a public emblem has a meaningful silhouette and a human review can identify it at TV size.
2. **Semantic symbol:** use a symbol if the product's function is known from reliable evidence and the symbol is more informative than its current letter/category shell.
3. **Category shell plus adaptive mark:** keep the current intermediate fallback when the family is known but the symbol would erase the app's identity or overclaim its function.
4. **Neutral monogram:** retain the neutral fallback for uncertain, wordmark-only, or unverified cases.

This ladder prevents two common regressions:

- inventing a symbol from an app name when the function is not verified; and
- replacing a distinctive brand cue with a generic pictogram merely because the pictogram is easier to draw.

### 6.3 Construction rules for TV Glyphs

A future symbol candidate should satisfy all of the following before it is promoted:

- one primary semantic cue, with at most one supporting detail;
- open counters and negative spaces large enough to survive the existing small-size review;
- no more than a few strong primitives; remove detail before reducing the primary cue;
- a silhouette that remains distinct from adjacent common primitives such as `play_round`, `screen`, `shield_key`, `folder`, and `gamepad`;
- no fixed background or decorative container in Classic Glyphs;
- one resolved accent, using the existing contrast policy rather than inventing a new hex for the generic family;
- the existing 512-grid, safe-area, caps, joins, and stroke hierarchy; and
- a Banner rendering that preserves the same identity while respecting the 320 × 180 layout and light-ink text policy.

The generic family must stay visually compatible with the existing Classic pack. “Modern” here means clearer information architecture and better optical decisions, not a switch to filled Material icons, emoji badges, or full-card artwork.

### 6.4 What happens in Pop and Pixel Neon

The future identity decision should be shared, but rendering should remain style-specific:

- **Classic:** transparent, open, rounded monoline semantic construction.
- **Pop:** the same semantic silhouette interpreted through Pop's container, palette, and optical-fit rules; do not paste Classic strokes into Pop or introduce a second catalogue mapping.
- **Pixel Neon:** the same cue rebuilt as intentional pixel geometry with its own pixel-safe counters; do not merely rasterise the Classic SVG.
- **Banners:** the paired 16:9 composition derived from the same square identity and category policy.
- **Glyphs companion:** the square representation selected globally by the launcher-aware package flow, not manually assigned app by app.

This is a renderer contract. It does not authorize changes to `tools/catalog.json`, component lists, package names, or generated assets.

## 7. Review and acceptance gates for a future tranche

### 7.1 Evidence gate

Before drawing a generic or brand-informed candidate, record separately:

- **confirmed platform/repository fact:** what the launcher, generator, or source repository actually states;
- **observed user or device evidence:** a real screenshot, installed component, or hardware-size review;
- **design recommendation:** the Core Builds interpretation of that evidence; and
- **unresolved uncertainty:** anything that still needs a device test or owner decision.

An app name alone is not evidence of a function, and a reference logo alone is not permission to trace it.

### 7.2 Automated gate

Reuse and extend the existing generated checks rather than inventing parallel asset logic:

- SVG validity and expected catalogue/generator parity;
- one-accent/monoline contract for Classic constructions;
- safe-area and presence bounds;
- counter survival at the repository's small review sizes;
- optical ink coverage and fit; and
- nearest-neighbour comparison, with review of close scores rather than treating one threshold as proof of identity.

A deliberate generic symbol should have a recorded cue and a reason for any close neighbour. The objective gate can identify a collision risk; it cannot approve cue fidelity by itself.

### 7.3 Human TV gate

Review a mixed row, not an isolated gallery, at:

- the 512 px master for construction errors;
- the repository's generated review sizes (PR #135 records 160/80/48); 
- a 96 px view where it matches the device's launcher card; and
- the actual 320 × 180 Banner beside unchanged neighbours.

The reviewer should answer:

1. What does the symbol communicate in under a second?
2. Does it still communicate that at 48–96 px?
3. Is it more useful than the current family shell or monogram?
4. Does it collide with a common neighbouring glyph?
5. Does it still look like Core Builds when the launcher changes the card shape, colour, tint, or glow?
6. Does the Banner remain legible without turning the glyph into a wordmark?

A failed answer to the third question means the candidate should not ship, even if all geometry checks pass.

### 7.4 Variant/release gate

Only after the square design passes should the generators produce Classic, Pop, Pixel Neon, Banner, preview, and any release resources. The release gate should compare mapping/component parity and generated output, not just whether each image file exists.

The isolated candidate APK precedent from PR #134 is preferred for a first TV review. No production release or global-toggle UX change is implied by a design candidate.

## 8. Candidate study list — not a catalogue change

The following are design studies, not assignments to current apps:

1. **Open broadcast wave:** a small asymmetric wave around a centre signal, deliberately not equal-spaced bars. Compare with the existing `stream_wave` and the PR #135 `audiomack_wave` lesson.
2. **Screen/source:** a screen with an incoming arrow or source notch for input/media tools; compare with `input_screen`, `tv_stack`, and `hdmi_connector` so the cue does not collapse into “another player.”
3. **Folder/sync:** folder or open tray plus one sync arrow for file and cloud tools; compare with `folder_tree`, `drive_sync`, and `debrid_cloud_link`.
4. **Globe/nodes:** a globe or three-node graph for network tools; keep `ip_globe`, `net_pulse`, and `automation` distinct by function rather than reusing one privacy shield.
5. **Tool/system:** wrench, sliders, download, terminal, and information marks with deliberately large openings; compare with `tools_wrench`, `tv_sliders`, `download_arrow`, and `dev_gear`.
6. **Game:** one controller with a clear open D-pad and widely spaced solid buttons; compare with `gamepad`, `retro_pad`, and `artemis_pad` without making every game-related app identical.
7. **Store/install:** open bag or package with a separate handle/tray cue; compare with `store_bag`, `store_globe`, and `install_box`.
8. **Photo/gallery:** a print/frame with one caption band or a large sun/mountain cue; avoid the small camera hump that the existing notes say reads as a briefcase.

The correct output of this study may be to keep an existing primitive, reject a candidate, or promote one shared symbol. It is not automatically a request for eight new catalogue glyph names.

## 9. Recommendations separated by evidence level

| Level | Finding | Consequence |
|---|---|---|
| **Confirmed in current checkout** | Core already has semantic primitives, category shells, monogram fallbacks, shared fit/contrast/style helpers, and generated validation. | Improve the selection and cue quality before adding another architecture. |
| **Confirmed in merged Core Builds history** | PR #134/#135 use isolated candidate testing, original cue-driven geometry, regenerated sibling styles, and small-size plus human review. | Reuse this workflow for any future generic tranche. |
| **External design evidence** | TV-focused packs prefer symbol-first, unified/dark-card-readable marks; mobile packs formalise stroke/variant systems; curated TV packs reject weak forced text. | Borrow the decision principles, not the exact visual treatment or density. |
| **Design recommendation** | Use semantic symbols as a constrained fallback tier, with category shells and neutral monograms retained where symbols overclaim or collide. | A staged generic-Glyph program can improve recognition without changing mappings. |
| **Still unverified** | Which specific generic symbol wins on each launcher, tint mode, card shape, and physical TV distance. | Use the candidate APK/device gate before promoting any study. |

## 10. Final recommendation

Approve a future **generic semantic-Glyph study**, not a production redraw tranche yet. The brief should:

1. keep `tools/catalog.json` and all component mappings unchanged;
2. treat the current category-shell-plus-mark system as an intermediate fallback, not as a failure to be deleted wholesale;
3. define a small primitive registry with an identifying cue and rejection condition for each symbol;
4. prefer open, low-density silhouettes that survive 48–96 px TV review;
5. keep brand-specific constructions only when their cue is materially stronger than the generic symbol;
6. render the same semantic identity through Classic, Pop, Pixel Neon, Banners, and Glyphs without forking mappings;
7. require automated fit/collision checks plus human cue-fidelity review in a mixed row; and
8. use the isolated candidate APK and real TV review before any catalogue or generated output change is approved.

No catalogue, mapping, or generated-asset change is recommended from this research alone.

## 11. Current census and the next study boundary

A fresh read of the current branch's `tools/catalog.json` on 25 September 2026 gives this snapshot:

| Measure | Current result | Interpretation |
|---|---:|---|
| Catalogue icon rows | **961** | The unit counted here is a catalogue row, not a generated drawable or emitted appfilter entry. |
| Listed catalogue components | **1,179** | This is the sum of component lists in the catalogue; generated alias expansion can emit more rows. |
| Distinct glyph names used | **427** | Reuse is intentional, but repeated generic marks need cue review. |
| Neutral `app_*` family rows | **452 (47.0%)** | The largest remaining letter-led fallback surface. |
| Named non-`app` family-shell rows | **120 (12.5%)** | Broadcast, tool, sport, music, gaming, VPN, film, store, debrid, and browser shells. |
| Family-path rows including `app_*` | **572 (59.5%)** | Current category-shell strategy, before counting standalone semantic and brand marks. |
| Other standalone/brand/specialised rows | **389 (40.5%)** | Includes both useful symbols and reviewed brand constructions; it is not a pure bespoke count. |

The 452 `app_*` rows are the clearest boundary for a future generic study, but they are not automatically candidates for conversion. The current catalogue does not contain reliable function evidence for every row, and a guessed symbol would be worse than an honest fallback. A future tranche should therefore select rows from the `app_*` population only after package/function evidence and a TV cue review.

The current repeated standalone symbols also identify where collision and over-generalisation should be watched:

- `iptv_player`: 13 catalogue rows;
- `folder`: 10;
- `play_round`: 5;
- `comedy_mark`: 5;
- `automation`, `launcher_grid`, `monitor_wave`, `sync_ring`, `tools_wrench`, and `tv_stack`: 3 each.

Reuse is not itself a defect. The question is whether each reused primitive expresses a truly shared function, or whether it hides meaningful differences between apps that have only been grouped for convenience. `play_round` and `iptv_player` deserve particular scrutiny because they are broad media shapes and are likely collision neighbours for future streaming studies.

Some registered family shells currently have no catalogue rows on this branch (`photos`, `anime`, `kids`, and `files`). That is a useful warning against adding empty visual taxonomy for its own sake. A family should be promoted when the catalogue has evidence and a symbol earns its place, not because the renderer can generate a complete A–Z matrix.

### 11.1 Material 3 as a systems reference, not a source-art library

Google's current [Material 3 icon guidance](https://m3.material.io/styles/icons/designing-icons) reinforces several principles that fit this measured problem:

- icons identify actions and categories, so simplification and legibility matter more than literal detail;
- a coherent set uses consistent visual style, keyline shapes, stroke weight, and optical corrections;
- a live area and trim area should be explicit; and
- a complex icon may receive optical correction, but the correction should preserve the underlying geometric forms.

Material Symbols also formalises variation along weight, fill, grade, and optical-size axes. Core Builds should borrow the idea of explicit optical tokens, not the Material Symbols artwork or its mobile 20–48 dp assumptions. The Core contract remains original rounded geometry, transparent presentation, and the existing 512-grid/small-TV review. Material's recommendation for some squared interior corners also does not override Core's established rounded caps and joins.

A useful Core-specific interpretation is:

- define a small set of semantic keyline families (circle, square, horizontal/vertical rectangle, shield/cloud, and open path);
- define optical-fit tokens for the 512 master, generated review sizes, and the 48–96 px TV decision range; and
- allow a symbol's counter or stroke to receive a measured optical correction without changing its semantic cue.

This is a design-system refinement, not a request to add a font dependency or paste Material glyphs into the pack.

### 11.2 Proposed next study tranche

Before any catalogue assignment is approved, build a review-only sheet with one candidate per function family:

1. open broadcast wave;
2. screen/source input;
3. folder/sync;
4. globe/nodes;
5. tool/sliders;
6. controller/D-pad;
7. store/install;
8. photo/print; and
9. family/kids only if a real TV-use case is documented.

Compare each candidate against its current `app_*` fallback **and** against the nearest existing semantic mark. The study should answer whether the symbol gives the viewer more information in under a second. It should not silently assign a symbol to all apps sharing a category label, and it should not modify `tools/catalog.json` until the owner approves the reviewed rows.

## 12. Source register

### Current repository evidence

- [`tools/glyphs.py`](../../tools/glyphs.py) — semantic primitives, family shells, adaptive marks, fit notes, and reviewed constructions.
- [`tools/icon_style.py`](../../tools/icon_style.py) — Classic monoline, contrast, and allowed paint/stroke contract.
- [`tools/catalog.json`](../../tools/catalog.json) — source of truth for identities, mappings, colours, and artwork provenance.
- [`tools/classic_glyph_fit.json`](../../tools/classic_glyph_fit.json) and [`tools/glyph_metrics.json`](../../tools/glyph_metrics.json) — committed fit/measurement references.
- [`tests/test_glyphs_pack.py`](../../tests/test_glyphs_pack.py) — Banners/Glyphs package and mapping-parity tests.
- [`docs/research/iconpack-design-upgrade-2026-09.md`](iconpack-design-upgrade-2026-09.md) — earlier Projectivy benchmark and Core design-gap analysis; historical counts are snapshot-specific.
- [`docs/research/icon-fidelity-and-demand-2026-09.md`](icon-fidelity-and-demand-2026-09.md) — corrected Core Builds identity contract and cue-driven acceptance rules.
- [`docs/research/icon-craft-2026-09.md`](icon-craft-2026-09.md) — shared pack grammar, launcher-badge consistency, and input-symbol precedent.
- [Merged PR #134](https://github.com/brevityA/CoreBuildsApps/pull/134) — isolated candidate APK and review gate precedent.
- [Merged PR #135](https://github.com/brevityA/CoreBuildsApps/pull/135) — eight original cue-driven redraws, sibling regeneration, and small-size/human review corrections.
- [`Core-Builds` adjacent assets](https://github.com/brevityA/Core-Builds/tree/main/Assets/Discord-Emojis) — related Core Builds semantic motifs; not an Android icon-pack source.
- [`tools/catalog.json`](../../tools/catalog.json) — current 25 September 2026 census source for the measured 961 rows, 1,179 listed components, and 427 glyph names.

### External design references

- [`android-tv-minimalist-icon-pack`](https://github.com/hqn-scl/android-tv-minimalist-icon-pack) — TV-focused symbol-first/vector redraw and dark-card presentation.
- [`minimal-tv-icons`](https://github.com/Mortisshadow/minimal-tv-icons) — curated Apple TV-inspired scope and quality gate against weak text-only marks.
- [`Arcticons`](https://github.com/Arcticons-Team/Arcticons) — open-source monotone line system and style variants.
- [`Lawnicons`](https://github.com/LawnchairLauncher/lawnicons) — outlined Material You-era marks and launcher-applied theme variants.
- [`iOSIconPack`](https://github.com/SysAdminDoc/iOSIconPack) — monochrome-vector and transparent-glyph representations sharing mappings.
- [`Projectivy Icon Pack`](https://github.com/SicMundus86/ProjectivyIconPack) — transparent dark-card per-app recognisability benchmark.
- [`Material 3 icon design guidance`](https://m3.material.io/styles/icons/designing-icons) and [`Material Symbols overview`](https://m3.material.io/styles/icons/overview) — external keyline, simplification, optical-correction, and style-axis references; not source artwork for Core Builds.

**Confidence:** high for the current repository contracts and the PR-derived workflow; medium for the external design conclusions because they are references rather than controlled TV usability studies; low for any individual proposed primitive until it is reviewed on real launcher hardware.
