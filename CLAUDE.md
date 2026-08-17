# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

An Android TV icon pack for [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu), built to the [Core Builds Brand & Style Guide v1.0](https://github.com/brevityA/Core-Builds). Ships **40 icons** covering **78 launcher components** as transparent, brand-coloured glyphs.

Sibling project to [`brevityA/Core-Builds`](https://github.com/brevityA/Core-Builds) (AIOStreams templates). Same brand, same voice, same standards of proof.

## The one rule that governs everything

**`tools/catalog.json` is the single source of truth. Never hand-edit generated files.**

Generated (do not touch directly):

```
app/src/main/res/xml/appfilter.xml       app/src/main/res/values/icon_pack.xml
app/src/main/res/xml/drawable.xml        app/src/main/res/drawable-nodpi/*.png
app/src/main/res/xml/iconpack.xml        app/src/main/res/mipmap-*/ic_launcher*.png
assets/svg/*.svg                         docs/IconPackList.md
docs/preview.svg  docs/preview.png       docs/brand-preview.png  docs/banner.png
```

CI fails the build if committed text assets differ from generator output. If you edit XML by hand, the next `build_icons.py` silently reverts you and CI flags the diff.

## Commands

```bash
pip install -r tools/requirements.txt   # cairosvg, pinned; needs libcairo2

python tools/build_icons.py         # catalog → SVG, PNG, appfilter, docs, preview
python tools/build_branding.py      # launcher icon + Leanback banner
python tools/build_brand_preview.py # branding preview sheet
python tools/validate.py            # 409 coherence checks — run before every commit

./gradlew assembleDebug             # needs JDK 17 + Android SDK
```

Run all three generators plus the validator before committing. Paste the validator's final line into the PR — that's the receipt.

## Architecture

| File | Role |
| --- | --- |
| `tools/catalog.json` | every icon: name, drawable, colour, glyph, components |
| `tools/glyphs.py` | 33 glyph primitives — pure SVG path geometry, no I/O |
| `tools/build_icons.py` | validates the catalog, then writes every icon artifact |
| `tools/build_branding.py` | the pack's own mark, from `Core-Builds/Assets/core_icon.svg` geometry |
| `tools/validate.py` | checks PNG dimensions/alpha, appfilter integrity, manifest intents, palette |
| `app/` | the Android module (Kotlin, AppCompat, RecyclerView) |

`build_icons.py` rejects the catalog before writing anything if a drawable name is malformed, a glyph is unknown, a colour isn't `#RRGGBB`, components are missing, or anything duplicates. Errors name the offending icon — never a generic failure.

## Adding icons

```jsonc
{
  "name": "Example TV",
  "drawable": "example_tv",           // [a-z][a-z0-9_]*, unique
  "color": "#00D4FF",
  "glyph": "play_round",              // must exist in glyphs.py GLYPHS
  "components": ["com.example.tv/.MainActivity"]
}
```

Then `python tools/build_icons.py && python tools/validate.py`.

Component names come from a real device — never guess:

```bash
adb shell cmd package resolve-activity --brief com.example.tv | tail -1
```

Multiple components per icon is normal (Fire TV, mobile variants, and regional forks expose different activities). If you cannot verify a component, say so in the PR rather than inventing one — a wrong mapping is worse than a missing icon, because it silently fails on the user's TV.

## Glyph constraints

Canvas 512 · safe area 432 · stroke 34 (never below 26) · round caps and joins · one flat accent colour, no gradients.

Helpers in `glyphs.py`: `_s(color, width)` for strokes, `_f(color)` for fills. Register new glyphs in the `GLYPHS` dict.

**Draw original geometry. Never trace a vendor logo.** Suggest the brand with simple shapes; the pack ships nothing it can't license. A new glyph that reads like an existing one at 96px is a duplicate, not a new icon.

## Brand rules (non-negotiable)

From the guide, enforced by `validate.py`:

- **Transparent backgrounds, always** — the launcher owns the card colour.
- **The point-up hexagon is never rotated.** The stance is load-bearing.
- **Palette locked** to guide §03. New accents need a meaning slot first.
- **Type split:** serif for human moments, sans for work, **mono for machine truth** (versions, counts, receipts).
- **`isShrinkResources = false`** — drawables resolve by name at runtime; shrinking silently deletes the pack.

## Voice

This repo writes like Core Builds. Copy the guide's §08 discipline:

- **Name the number.** "40 icons, 78 components" — not "lots of icons".
- **Past tense about what happened.** "Hub layouts written (5/5) — verified by re-read."
- **Never an unnamed error.** "Something went wrong" is the cardinal sin. Name the app, the component, the file.
- **State the mode.** "Ships disabled. Add your key, flip it on."
- No hype, no emoji gradients, no all-caps outside status chips.

Commit messages: imperative subject, then a paragraph on *why*, then what was verified. PRs carry a **"why this exists"** paragraph.

## Verification standard

Claims in this repo are earned. Before saying something works:

- Ran the validator? Quote the count.
- Changed a generator? Confirm output is still deterministic — run it twice and diff.
- Changed a glyph? Look at `docs/preview.png`. If it doesn't read at that size, it won't read across a room.
- Can't verify something (no device, no SDK)? **Say so plainly.** Flagging an unverified step is worth more than a confident guess.

## Known gaps

- **The APK has never been built.** Authored in a sandbox with JDK 11 and no Android SDK; CI is the first real Gradle run. If AGP 8.5.2 objects to something, that's where it surfaces.
- **Some component names are best-known, not device-verified** — TorBox, Weyd, AllDebrid, Premiumize, and a few AU apps. Correct them as reports arrive.
- **The CI drift gate covers text assets only** (XML/SVG/MD). PNG bytes vary with the runner's libcairo build, so diffing them would fail on a library bump rather than real drift.
- **Icons assume dark card backgrounds.** Light launcher themes wash them out.

## Scope

This repo is the icon pack. Template, configurator, and genie work belongs in `brevityA/Core-Builds`. Keep the brand consistent across both; keep the code separate.
