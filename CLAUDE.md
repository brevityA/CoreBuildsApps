# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

An Android TV icon pack for [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu), built to the [Core Builds Brand & Style Guide v1.0](https://github.com/brevityA/Core-Builds). Ships **40 icons** covering **78 launcher components** as transparent, brand-coloured glyphs.

Sibling project to [`brevityA/Core-Builds`](https://github.com/brevityA/Core-Builds) (AIOStreams templates). Same brand, same voice, same standards of proof.

**This repo also hosts three more apps:** **[Core Line](ticker/HANDOVER.md)** (Android / TV sports & channel RSS ticker, under `ticker/`), **[Core Shift](shift/HANDOVER.md)** (motion wallpaper delivery + static rotation for Monet Launcher, under `shift/`), and **[Core Doctor](doctor/SPEC.md)** (phone-only streaming diagnostics, under `doctor/`). All three are self-contained — Core Line has its own project root at `ticker/android/`, Core Shift at `shift/`, Core Doctor at `doctor/`. None are part of the icon-pack Gradle build. See their handover/spec docs before touching them; product decisions are locked.

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
python tools/validate.py            # 470 coherence checks — run before every commit

./gradlew assembleDebug             # needs JDK 17 + Android SDK
```

Run all three generators plus the validator before committing. Paste the validator's final line into the PR — that's the receipt.

### Core Line (second app)

```bash
cd ticker && npm test               # 24 tests, node:test, no deps
cd ticker && node server.mjs        # web ticker on 0.0.0.0:8787
cd ticker/android && ./gradlew :app:assembleDebug   # APK (wrapper jar committed)
```

Core Line CI lives in `.github/workflows/core-line-apk.yml` (tests + debug APK) and triggers only on `ticker/**` changes, so icon-pack work never waits on it. The root `./gradlew` does **not** build it — `ticker/android` is a standalone Gradle root.

### Core Shift (third app)

```bash
cd shift && ./gradlew :app:assembleDebug   # APK (wrapper jar committed)
python tools/validate_motion.py            # 112 motion asset coherence checks
```

Core Shift CI lives in `.github/workflows/core-shift-apk.yml` (validate + debug APK) and triggers only on `shift/**` and `Motion/**` changes. The root `./gradlew` does **not** build it — `shift/` is a standalone Gradle root.

## Architecture

| File | Role |
| --- | --- |
| `tools/catalog.json` | every icon: name, drawable, colour, glyph, components, unverified |
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
  "components": ["com.example.tv/.MainActivity"],
  "unverified": ["com.example.tv/.MainActivity"]  // omit once read off a device
}
```

Then `python tools/build_icons.py && python tools/validate.py`.

Component names come from a real device — never guess:

```bash
adb shell cmd package resolve-activity --brief com.example.tv | tail -1
```

Multiple components per icon is normal (Fire TV, mobile variants, and regional forks expose different activities). If you cannot verify a component, say so rather than inventing one — a wrong mapping is worse than a missing icon, because it silently fails on the user's TV.

**Record that in the catalog, not just the PR.** `"unverified"` lists the components on an icon that are best-known rather than device-confirmed; omitting it claims every component was read off a device. Both the builder and the validator reject an entry that isn't one of that icon's own components, and the validator prints the split:

```
Validated 40 icons · 78 components (58 device-confirmed, 20 best-known) · 470 checks run
```

`docs/IconPackList.md` marks those components ⚠, which is what turns "some mappings are guesses" from a line in this file into something a user can act on. Delete the entry when a component is confirmed — that is the whole lifecycle.

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

- **The APK now builds.** First real Gradle run: `assembleDebug` and `assembleRelease` both succeeded on Gradle 8.7 / AGP 8.5.2 / JDK 21 / compileSdk 34, with no AGP objections. Verified by `aapt2 dump resources` rather than by exit code — all 40 catalog drawables and all three XML resources (`appfilter`, `drawable`, `iconpack`) are present in **both** variants, so `isShrinkResources = false` is doing its job. Debug 3,897 KB, release 2,964 KB.
  - Release builds apply resource **path shortening** (`res/drawable-nodpi/torbox.png` → `res/-B.png`). This is harmless here — the pack resolves drawables by *name* at runtime, and names live in `resources.arsc`, which shortening does not touch. Do not check packaged icons by filename; it reports zero on a perfectly good APK. Check resource names.
  - Still unverified **here**: no build has run on a real device. `.github/workflows/device-check.yml` closes this in CI — it boots an Android TV emulator (API 34, `android-tv`, **x86**; there is no android-tv x86_64 image until API 36) and asserts the pack installs, resolves for the ADW and Nova launcher intents, launches without a crash, and renders its icon count from the generated string-array. That workflow has **never run** — it needs KVM, which GitHub's ubuntu-latest runners expose and the authoring container does not. Its first run is the real test of the workflow itself.
- **20 of 78 components are best-known, not device-verified** — now recorded as data in `tools/catalog.json` (`unverified`), counted by the validator and marked ⚠ in `docs/IconPackList.md`, rather than named only here. Covers TorBox, Weyd, AllDebrid, Premiumize and all eight AU broadcaster apps. **The AU set is marked conservatively:** this file previously said "a few AU apps" without naming which, so all eight are flagged. Narrow it as components are confirmed — over-marking invites a correction, under-marking ships a silent wrong mapping.
- **The CI drift gate covers text assets only** (XML/SVG/MD). PNG bytes vary with the runner's libcairo build, so diffing them would fail on a library bump rather than real drift.
- **Icons assume dark card backgrounds.** Light launcher themes wash them out.

## Scope

This repo hosts **four apps**: the icon pack (everything above), **Core Line** (`ticker/` — sports/channel RSS ticker with its own Android project and workflow), **Core Shift** (`shift/` — motion wallpaper delivery + static rotation for Monet Launcher, with its own Gradle root and workflow), and **Core Doctor** (`doctor/` — phone-only streaming diagnostics, with its own Gradle root and workflow). Template, configurator, and genie work belongs in `brevityA/Core-Builds`. Keep the brand consistent across all four; keep the code separate. `ticker/`, `shift/`, and `doctor/` are self-contained — they must never depend on `tools/` or `app/`, and the icon-pack build must never depend on them. `Motion/` holds the shared motion asset set used by Core Shift. See `shift/HANDOVER.md` and `doctor/SPEC.md` before touching those apps; product decisions are locked.
