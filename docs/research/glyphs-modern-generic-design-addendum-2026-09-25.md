# Modern generic Glyphs design addendum — 25 September 2026

**Scope:** design research for the square Glyphs pack, with the 16:9 Banners pack as the paired output surface.
**Status:** research plus the 26 September 2026 modern-shell implementation pass. The catalogue, component mappings, package names, Monet identity/mapping, and Arena4Viewer verification status remain unchanged; derived art is regenerated only through the project generators.

This addendum follows the global-toggle feasibility report in [`glyphs-global-toggle-feasibility-2026-09-25.md`](glyphs-global-toggle-feasibility-2026-09-25.md). The toggle/package decision remains separate from the art decision: a launcher selects one complete pack, while both packs derive from the same identity and mapping contract.

## 1. Decision in one paragraph

The generic-Glyph direction is **symbol-first semantic fallback**: draw a small, disciplined set of recognisable functional symbols before falling back to a category shell plus an adaptive app mark, and use a neutral monogram only when neither a function nor a defensible brand cue is known. This is more useful at TV distance than expanding the alphabet of letter tiles, but it must not become a second collection of traced vendor logos or a new catalogue/mapping system.

The selection ladder is:

1. **Strong Core-authored brand cue**, interpreted in Core Builds geometry when one exists and is identifiable at TV size.
2. **Clear semantic symbol** when the function is known and the symbol is more informative than a letter tile.
3. **Functional family shell plus adaptive mark** when a symbol would be too broad, misleading, or destructive of identity.
4. **Neutral monogram** only when function and identity are genuinely uncertain.

The existing Banners/Glyphs package selection, catalogue, mappings, and generated-asset pipeline do not need to change for this direction.

## 2. What generic means here

| Term | Meaning | Design consequence |
|---|---|---|
| **Generic semantic glyph** | A function symbol such as a folder, globe, controller, waveform, shield, or download arrow. | It should communicate the app family before the user reads the label. |
| **Generic identity fallback** | A letter or short mark used because the app has no reliable symbol or public emblem. | It should be deliberately systematic, not presented as a bespoke logo. |

A symbol-first system is not a requirement to make every app look like a vendor logo. It gives the long tail useful visual information without inventing brand identity. It also avoids the opposite failure: replacing every app with the same play button, screen, shield, or equalizer.

The square Glyph is the binding surface. A 16:9 Banner can carry an app name and category lockup, but a square card often has only the mark and launcher-supplied background. The Banner must therefore remain a layout derived from the same identity rather than receiving an unrelated generic drawing.

## 3. Current Core Builds implementation

### 3.1 Existing intermediate strategy

`tools/glyphs.py` already contains semantic primitives such as `stream_tower`, `gamepad`, `tools_wrench`, `send_arrow`, `broom`, `shield_key`, `automation`, `home_button`, `tv_stack`, `folder`, `gear`, `remote`, and `waves_circle`. It also contains specialised marks such as `stream_wave`, `folder_tree`, `drive_sync`, `ip_globe`, `net_pulse`, `input_screen`, `hdmi_connector`, `usb_media`, and `tv_antenna`.

Its family-shell registry (`broadcast`, `app`, `tool`, `sport`, `music`, `gaming`, `vpn`, `film`, `store`, `photos`, `debrid`, `browser`, `anime`, `kids`, and `files`) puts an adaptive app mark inside a function-shaped outline. This is a meaningful improvement over a plain letter in a box, but it remains letter-led and is an intermediate fallback, not the final answer for every known app family.

The current branch's large neutral `app_*` population is the clearest boundary for a future semantic study, but rows must not be converted from names alone. App names, package/component names, and catalogue mappings are separate evidence. A guessed symbol is worse than an honest fallback.

### 3.2 Classic visual contract

`tools/icon_style.py`, `tools/glyphs.py`, and the validators establish these constraints:

- transparent artwork; the launcher card or tile supplies the surface;
- one resolved display accent per icon, with the existing contrast-preserving colour policy;
- a shared 512 × 512 construction grid and 432 px safe area;
- rounded caps and joins;
- Core monoline weights 32, 26.2, and 21.8 after normalisation;
- no fixed container, vendor fill, private transform, filter, or fixed-white wordmark for a Core monoline construction; and
- app name/category text remains a Banner concern and follows the existing light-ink readability policy.

These rules are a constraint, not a limitation. “Modern” means clearer information architecture and better optical decisions inside this contract, not a switch to filled Material icons, emoji badges, full-card artwork, or imported vendor artwork.

### 3.3 Research lessons

The strongest internal lessons from the cue-driven redraw work are:

- A valid shield can still fail if its bands do not communicate the intended cue.
- Evenly spaced bars can pass geometry checks while reading as a generic equalizer; an asymmetric waveform with one dominant pulse communicates more.
- A whole-character abstraction can collapse at 96 px; prioritise the one identifying profile or silhouette cue.
- Detached decorative rays can become noise at 48 px; connect them to the primary form or remove them.
- A candidate can pass a collision threshold and still need a real launcher-size comparison.

The general rule is: **a generic glyph needs one clear identifying cue, and that cue must survive the TV review size.** Path validity, safe area, and low collision alone are not sufficient.

## 4. External references and boundaries

External sources are design references, not platform requirements and not evidence that Core Builds should change mappings.

- [Material 3 icon guidance](https://m3.material.io/styles/icons/designing-icons) reinforces a 24dp baseline, 20dp live area, keyline shapes, optical correction, restrained complexity, and consistent stroke systems. Core Builds borrows the system principle, not Material artwork.
- [Material Symbols](https://github.com/google/material-design-icons) demonstrates explicit weight, fill, grade, and optical-size axes. These are useful design-system precedents, not source art.
- [Android TV layout guidance](https://stuff.mit.edu/afs/sipb/project/android/docs/training/tv/optimizing-layouts-tv.html) reinforces large, high-contrast, simple graphics and avoidance of delicate or overly narrow strokes at 10-foot distance.
- [Android TV minimalist icon pack](https://github.com/hqn-scl/android-tv-minimalist-icon-pack) and [minimal-tv-icons](https://github.com/Mortisshadow/minimal-tv-icons) reinforce symbol-first curation and real TV-card review. Their backgrounds, exact geometry, and scope are not Core Builds requirements.
- [Arcticons](https://github.com/Arcticons-Team/Arcticons) and [Lawnicons](https://github.com/LawnchairLauncher/lawnicons) demonstrate formal line systems and renderer/theme separation. Their mobile density and launcher assumptions are not automatically applicable here.
- [Projectivy Icon Pack](https://github.com/SicMundus86/ProjectivyIconPack) is a benchmark for per-app recognisability, not a mandate to hand-paint every long-tail mapping.

Do not import, trace, or raster-copy vendor SVGs, bitmaps, logos, Material artwork, or competitor assets. All new geometry remains Core-authored.

## 5. Constrained primitive registry

The registry documents the intended cue, permitted supporting detail, and rejection condition. It is not an instruction to add one new glyph for every catalogue row.

| Family | Preferred cue | Reject when |
|---|---|---|
| Video/streaming | open play, screen, clapper, broadcast wave | repeated screens become indistinguishable or waves become stripes |
| Audio/music | waveform, speaker, note, record dot | evenly spaced bars become an unintended equalizer |
| Files/storage | folder, document, cloud, database, sync arrow | the outline closes into a blob |
| Network/privacy | globe, connected nodes, lock, shield | every network app becomes the same shield |
| Games | controller, open D-pad, trophy, pixel creature | tiny buttons merge or controller is used for unrelated apps |
| Browser/search | globe, browser window, compass, magnifier | a generic circle has no browser/search cue |
| Tools/system | gear, wrench, sliders, download, terminal, information | too many teeth, rails, or knobs fail at 48 px |
| Remote/input | remote, HDMI plug, source arrow, TV antenna | the input mark resembles a normal media player |
| Store/install | open bag, package, box, download tray | bag/box becomes a generic rounded rectangle |
| Photo/gallery | print/frame, large mountain-and-sun, camera only if its silhouette survives | a small camera hump reads as a briefcase/noise |
| Kids/family | simple spark, ears, rounded companion shape | decorative detail becomes unexplained noise |

## 6. Construction rules for TV Glyphs

Every promoted generic candidate should satisfy all of these:

- one primary semantic cue and at most one supporting detail;
- familiar silhouette before decorative detail;
- front-facing, sturdy, geometric forms rather than tilted or dimensional art;
- open counters and negative spaces that survive 48 px and 96 px review;
- a few strong primitives, with detail removed before reducing the primary cue;
- a silhouette distinct from common neighbours such as `play_round`, `screen`, `shield_key`, `folder`, and `gamepad`;
- no fixed background or decorative container in Classic Glyphs;
- one resolved accent using the existing contrast policy;
- the existing grid, safe-area, rounded caps/joins, and stroke hierarchy; and
- a Banner rendering that preserves the same identity within the 320 × 180 layout.

Use keyline families and optical corrections rather than mechanically scaling every shape. Keep negative spaces open. Adaptive marks may preserve app identity where useful, but no family should become a letter inside a heavy container merely because the shell is easy to generate.

### 6.1 Platform/repository requirements versus aesthetic recommendations

| Type | Requirement or recommendation |
|---|---|
| **Platform/repository requirement** | `tools/catalog.json` remains the source of truth; XML, SVG, raster art, aliases, docs, previews, and mockups are rebuilt through project tools. |
| **Platform/repository requirement** | Preserve transparent square/banner outputs, the existing package/mapping contract, the Classic monoline and safe-area checks, and component parity. |
| **Platform/repository requirement** | Keep Monet's name and mapping unchanged. Keep Arena4Viewer mappings marked unverified until a local APK or device scan confirms launcher components. |
| **Aesthetic recommendation** | Prefer symbol-first cues, one primary cue, sturdy silhouettes, optical sizing, and open negative space at 48–96 px. |
| **Aesthetic recommendation** | Reduce heavy nested containers and use adaptive marks only when they add identity without weakening the functional cue. |
| **Aesthetic recommendation** | Review a mixed row and the paired Banner on a dark TV-like surface; reject marks that are technically valid but semantically unclear. |

The first group is release and evidence policy. The second group is design judgement; it should be validated visually and must not be mistaken for an Android platform mandate or competitor-specific behaviour.

## 7. Implemented modern-shell pass — 26 September 2026

This pass improves shared family geometry without changing catalogue identity or mapping:

- **Tool shell:** replaced the shield-like point-up hexagon with an original, low-frequency face-on gear keyline. The adaptive mark remains centred and the teeth are deliberately broad enough to survive 48 px.
- **Sport shell:** retained the circle but added two open curved seam cues outside the mark. The old plain ring was too category-neutral; the seam carries the function without becoming a detailed ball illustration.
- **Browser shell:** retained the front-facing window/title-bar silhouette and added three restrained title controls. The cue now reads as a window before the letter is read.
- **Shared optical treatment:** the new points, seams, and controls use the existing `monoline()` normalisation and accent handling. They are not a second style or an imported icon set.

The pass intentionally does not convert every family shell to a symbol, delete all adaptive marks, alter `tools/catalog.json`, or infer function from a loose name match. It is a measured geometry improvement while the selection ladder remains available for future evidence-backed rows.

## 8. Review and acceptance gates

### 8.1 Evidence gate

For every future candidate, record separately:

- confirmed platform/repository fact;
- observed user or device evidence;
- Core Builds design recommendation; and
- unresolved uncertainty needing a device test or owner decision.

An app name alone is not evidence of a function, and a reference logo is not permission to trace it.

### 8.2 Automated gate

Reuse the generated checks rather than inventing parallel asset logic:

- SVG validity and catalogue/generator parity;
- one-accent/monoline contract;
- safe-area and presence bounds;
- counter survival at small review sizes;
- optical ink coverage and fit; and
- nearest-neighbour comparison, with close scores receiving human review.

The objective gate can identify collision risk; it cannot approve cue fidelity by itself.

### 8.3 Human TV gate

Review a mixed row at the 512 px master, generated 160/80/48 px review sizes, a 96 px launcher-card view, and the actual 320 × 180 Banner beside unchanged neighbours. Ask:

1. What does the symbol communicate in under a second?
2. Does it communicate that at 48–96 px?
3. Is it more useful than the current family shell or monogram?
4. Does it collide with a common neighbour?
5. Does it still look like Core Builds when the launcher changes card shape, colour, tint, or glow?
6. Does the Banner remain legible without turning the glyph into a wordmark?

A failed answer to question three means the candidate should not ship, even if the geometry checks pass. An isolated candidate APK and real TV review remain preferred before a catalogue assignment.

### 8.4 Variant/release gate

Only after the square design passes should the generators produce Classic, Pop, Pixel Neon, Banner, preview, and release resources. The release gate compares mapping/component parity and generated output, not only file existence.

Classic, Pop, and Pixel Neon are sibling renderers of one identity decision. Do not paste Classic strokes into Pop, rasterise Classic as Pixel Neon, or fork mappings because a renderer changes.

## 9. Current study boundary

A fresh catalogue census remains a measurement of the current branch, not permission to redraw all rows. The large `app_*` population is the likely future study boundary, but each row needs package/function evidence and a TV cue review. Named family shells without current catalogue rows are not justification for adding taxonomy.

Compare a candidate against both its current fallback and the nearest existing semantic mark. The possible outcome is to keep the existing primitive, reject the candidate, or promote one shared symbol. Do not silently assign a symbol to every app sharing a category label, and do not modify the catalogue until the reviewed rows are approved.

### 9.1 Census boundary and snapshot note

The original 25 September research snapshot counted **961 catalogue rows**, **1,179 listed components**, **427 distinct glyph names**, **452 neutral `app_*` rows (47.0%)**, and **120 named family-shell rows (12.5%)**. Those figures are snapshot-specific evidence for where a future study might start; they are not a current release count and do not justify loose name-based reassignment.

The current regenerated receipt is **962 catalogue icons**, **1,183 listed components**, **1,811 expanded component entries**, and **962 drawable outputs**. Generated-resource counts differ from the research snapshot because the catalogue and generated alias/component expansion are separate measurements. The current branch's release gates, not a copied historical table, are authoritative.

## 10. Final recommendation

Continue with a staged generic semantic-Glyph program that:

1. keeps `tools/catalog.json` and all component mappings unchanged unless separately approved;
2. treats family-shell-plus-mark as an intermediate fallback, not a failure to delete wholesale;
3. defines a small primitive registry with an identifying cue and rejection condition;
4. prefers open, low-density silhouettes surviving 48–96 px TV review;
5. keeps strong Core-authored brand cues when they are materially better than a generic symbol;
6. renders one semantic identity through Classic, Pop, Pixel Neon, Banners, and Glyphs;
7. requires automated fit/collision checks plus human cue-fidelity review in a mixed row; and
8. uses isolated candidate APK/device review before catalogue or generated-output promotion.

The 26 September shell pass applies this direction to shared geometry only. It does not change package mappings, Monet, or Arena4Viewer's unverified status.

## 11. Source register

### Repository evidence

- [`tools/glyphs.py`](../../tools/glyphs.py) — semantic primitives, family shells, adaptive marks, and fit notes.
- [`tools/icon_style.py`](../../tools/icon_style.py) — Classic monoline, contrast, and allowed paint/stroke contract.
- [`tools/catalog.json`](../../tools/catalog.json) — source of truth for identities, mappings, colours, and artwork provenance.
- [`tools/classic_glyph_fit.json`](../../tools/classic_glyph_fit.json) and [`tools/glyph_metrics.json`](../../tools/glyph_metrics.json) — committed fit/measurement references.
- [`tests/test_glyphs_pack.py`](../../tests/test_glyphs_pack.py) and [`tests/test_icon_identity.py`](../../tests/test_icon_identity.py) — generated package and identity checks.
- [`docs/research/generic-glyph-audit-2026-09-26.md`](generic-glyph-audit-2026-09-26.md) — current generic-family audit and evidence boundary.
- [Merged PR #134](https://github.com/brevityA/CoreBuildsApps/pull/134) — isolated candidate APK and review gate precedent.
- [Merged PR #135](https://github.com/brevityA/CoreBuildsApps/pull/135) — original cue-driven redraws, sibling regeneration, and small-size/human review corrections.

### External design references

- [`Material 3 icon design guidance`](https://m3.material.io/styles/icons/designing-icons) and [`Material Symbols`](https://github.com/google/material-design-icons) — keyline, simplification, optical-correction, and style-axis references; not source artwork.
- [`Android TV layout guidance`](https://stuff.mit.edu/afs/sipb/project/android/docs/training/tv/optimizing-layouts-tv.html) — large, high-contrast, simple TV graphics.
- [`android-tv-minimalist-icon-pack`](https://github.com/hqn-scl/android-tv-minimalist-icon-pack) — TV-focused symbol-first/vector redraw reference.
- [`minimal-tv-icons`](https://github.com/Mortisshadow/minimal-tv-icons) — curated TV-icon quality reference.
- [`Arcticons`](https://github.com/Arcticons-Team/Arcticons) — monotone line-system reference.
- [`Lawnicons`](https://github.com/LawnchairLauncher/lawnicons) — outlined launcher/theme-variant reference.
- [`Projectivy Icon Pack`](https://github.com/SicMundus86/ProjectivyIconPack) — per-app recognisability benchmark.

**Confidence:** high for current repository contracts and the generated workflow; medium for external design conclusions because they are references rather than controlled TV usability studies; low for any individual proposed primitive until reviewed on real launcher hardware.
