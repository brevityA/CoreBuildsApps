# Core Builds Icon Pack v1.5.0 — patches

Apply these **in order** on top of `main` at `4a8837f` (v1.4.0).

```bash
git apply patches/0001-outfit-wordmarks-and-generators.patch
git apply patches/0002-catalog-names-matching-new-icons.patch
git apply patches/0003-in-app-navigation.patch
git apply patches/0004-generated-xml-docs-version.patch
git apply patches/0005-new-icon-svg-masters.patch
```

Then regenerate PNGs (not in the patches — they are 30 MB of cairo output and CI rebuilds them):

```bash
pip install -r tools/requirements.txt
python tools/build_icons.py
python tools/build_banners.py
python tools/validate.py
```

Expected receipt: `Validated 515 icons · 948 components · 12341 checks run` / `12341 passed`.

| Patch | What it is |
| --- | --- |
| `0001` | Outfit fonts, `typeface.py`, monogram + banner generators, new glyphs |
| `0002` | `catalog.json` — 218 renamed apps, rematched file managers, 14 new icons |
| `0003` | In-app chips, search, labeled tiles, D-pad layout |
| `0004` | Generated `appfilter` / `drawable` / `icon_pack`, docs, version 1.5.0 |
| `0005` | SVG masters + banners for the 14 new icons |

PNGs for the new icons are **not** in these patches. `build_icons.py` + `build_banners.py` write them to `app/src/main/res/drawable-nodpi/`.
