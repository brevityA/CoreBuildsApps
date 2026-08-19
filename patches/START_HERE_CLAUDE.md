# Start here — apply Core Builds Icon Pack v1.5.0

You are updating **github.com/brevityA/CoreBuildsIconPack** from **v1.4.0** (`4a8837f`) to **v1.5.0**.

The user will give you **two zip files**. Extract both over the repo root, then regenerate PNGs if needed, then validate.

## The one rule

`tools/catalog.json` is the single source of truth. Do not hand-edit generated XML. After extracting, run the generators so CI drift checks pass.

## What these zips contain

| Zip | Extract over | What it is |
| --- | --- | --- |
| `corebuilds-v1.5.0-part1-source.zip` | repo root | Source: catalog, glyphs, Outfit typeface, generators, Android UI, version, this brief |
| `corebuilds-v1.5.0-part2-generated.zip` | repo root | Generated: appfilter / drawable / iconpack, SVG masters + banners, new-icon PNGs, docs |

Part 2 does **not** include every regenerated monogram/banner PNG (500+ files, cairo-dependent). Those must be rebuilt locally so they match the new Outfit outlines.

## Do this, in order

```bash
# 0. Confirm you are on the pack repo, on v1.4.0 / main
git status
test -f tools/catalog.json && test -f tools/glyphs.py

# 1. Extract both zips over the repo root (part1 then part2)
unzip -o corebuilds-v1.5.0-part1-source.zip
unzip -o corebuilds-v1.5.0-part2-generated.zip

# 2. Install render deps (needs libcairo2)
pip install -r tools/requirements.txt

# 3. Regenerate everything from the catalog
python tools/build_icons.py
python tools/build_banners.py
python tools/build_branding.py
python tools/build_brand_preview.py

# 4. Validate — quote the final line back to the user
python tools/validate.py
```

**Expected receipt:**

```
Validated 515 icons · 948 components · 12341 checks run
✓ 12341 passed
```

If the check count drifts by a few, name the failures. Do not ship a failing validator.

## What changed (so you do not undo it)

- **Outfit Bold / ExtraBold** in `tools/fonts/` (SIL OFL). Banner wordmarks and square monograms are path-outlined from that family via `tools/typeface.py`. Do not go back to DejaVu `<text>` or hand-drawn monogram strokes.
- **14 new icons:** LocalSend, RS File Manager, Sparkle TV, DS file / video / photo / audio / finder / get, Synology Drive, FX File Explorer, Solid Explorer, Material Files, Ghost Commander.
- **218 display names** were package slugs. Keep the human names in `tools/catalog.json`.
- **Files** no longer steals CX / MiXplorer / FX components. Each file manager has its own icon. X-plore is `FILES`, not `GAMING`.
- **In-app browser:** category chips, search, labeled tiles. Grid is not inside a NestedScrollView. D-pad: apply → chips → search → grid.
- Version is **1.5.0 / versionCode 7**. Keep `app/build.gradle.kts` and `Latestrelease/version.json` in agreement (`iconCount` must equal catalog length).

## Constraints (do not violate)

- Original geometry only. Never trace a vendor logo.
- Do not invent component names. New Synology / LocalSend / RS / Sparkle mappings are already marked `unverified` in the catalog. If you cannot confirm a component with `adb`, leave it unverified rather than guessing a different activity.
- Transparent backgrounds. 512 grid, 432 safe, monoline stroke ≤ 34.
- `isShrinkResources = false` stays false.
- Brand Guide type split: serif is for display copy, not card wordmarks. Wordmarks stay Outfit Bold paths.

## If unzip paths look wrong

Both zips store files at **repo-relative** paths (`tools/catalog.json`, `app/src/main/...`). Run unzip from the repository root, not from a parent folder.

## When you are done

1. Quote the validator's final line.
2. `git status` — expect catalog, tools, app UI, generated XML/SVG/PNG, version files.
3. Do not create a tag unless the user asks. Tag would be `v1.5.0` and must match `versionName`.
