# Contributing

Everything in this repo generates from one file: **`tools/catalog.json`**. You never hand-edit XML — the build writes `appfilter.xml`, `drawable.xml`, the PNGs, the docs, and the previews. CI fails if committed files drift from what the generator produces.

## Setup

```bash
pip install -r tools/requirements.txt   # needs libcairo2 on Linux
```

That's the whole toolchain for asset work. Building the APK additionally needs JDK 17 + the Android SDK.

**Codespaces / Dev Containers.** Two configs live in [`.devcontainer/`](.devcontainer/README.md). The default (**Python**) is python 3.12, Node 22, GitHub CLI, and Cairo — enough for the catalog, validators, and `cd ticker && npm test`. Switch to **Android** for JDK 17 + the command-line SDK (`assembleDebug`). There is no emulator; `device-check.yml` stays in Actions. Do not copy the release keystore into a codespace.

## Adding an icon

1. Find the component name on a device that has the app:

   ```bash
   adb shell cmd package resolve-activity --brief com.example.tv | tail -1
   ```

2. Add an entry to `tools/catalog.json`:

   ```jsonc
   {
     "name": "Example TV",
     "drawable": "example_tv",        // [a-z][a-z0-9_]* , unique
     "color": "#00D4FF",              // the app's accent colour
     "glyph": "play_round",           // a key from tools/glyphs.py
     "components": [
       "com.example.tv/.MainActivity" // add every variant you can confirm
     ]
   }
   ```

3. Regenerate and verify:

   ```bash
   python tools/build_icons.py
   python tools/validate.py
   ```

4. Look at `docs/preview.png`. If the glyph doesn't read at that size, it won't read on a TV across the room.

Multiple components per icon is normal and encouraged — Fire TV, mobile variants, and regional forks often expose different activities.

## Requesting an icon instead

An app you cannot map yourself belongs in an issue form, not a PR. Both forms are
deep-linked in the README — right template, title prefix and label, plus field values —
and those links are generated, so never hand-edit an `issues/new?...` URL in
`README.md`:

```bash
python tools/build_issue_prefills.py            # rewrite the README block
python tools/build_issue_prefills.py --check     # what Suite CI runs
python tools/build_issue_prefills.py --app Stremio --template 2.icon_not_applying.yml \
    --component com.stremio.one/com.stremio.tv.MainActivity
```

The generator reads `.github/ISSUE_TEMPLATE/*.yml`, so a new field shows up in the
README table by itself. GitHub prefills `input` and `textarea` fields only, and the
tool refuses to promise anything more: a required dropdown or a confirm box cannot be
answered from a URL, so no link pretends to have answered it.

## Adding a glyph

Add a function to `tools/glyphs.py` and register it in the `GLYPHS` dict:

```python
def my_shape(c):
    return (f'<circle cx="256" cy="256" r="180" {_s(c, 34)}/>'
            f'<path d="M 200 200 L 320 256 L 200 312 Z" {_f(c)}/>')
```

Constraints, all enforced by review:

| Rule | Value |
| --- | --- |
| Canvas | 512 × 512 |
| Safe area | 432 (40px margin) |
| Default stroke | 34 (never below 26) |
| Caps and joins | round |
| Fill | one flat accent colour, no gradients |
| Geometry | **original** — never trace a vendor logo |

`_s(color, width)` gives a stroke, `_f(color)` a fill. Keep glyphs distinct from existing ones: a new mark that reads like `play_rect` at 96px is a duplicate, not a new icon.

## Design rules

Inherited from the [Core Builds Brand & Style Guide v1.0](https://github.com/brevityA/Core-Builds):

- Transparent backgrounds, always — the launcher owns the card colour.
- The point-up hexagon stance is never rotated.
- Palette is locked to the guide's swatches; new accents need a meaning slot first.
- Original artwork only. This pack ships nothing it can't license.

## Before you open a PR

```bash
python tools/build_icons.py
python tools/build_branding.py
python tools/build_brand_preview.py
python tools/validate.py
```

Commit the regenerated output. Paste the validator's last line into the PR — that's the receipt.

PRs carry a **"why this exists"** paragraph. Not "adds icons" — what problem it solves.

## Core Line (the second app)

`ticker/` — the sports & channel RSS ticker — is self-contained and follows its own rules; read [`ticker/HANDOVER.md`](ticker/HANDOVER.md) before editing it (product decisions there are locked). Its CI is `.github/workflows/core-line-apk.yml`:

```bash
cd ticker && npm test               # parser/SSRF tests — keep green
cd ticker/android && ./gradlew :app:assembleDebug
```

Icon-pack work must never touch `ticker/`, and Core Line must never import from `tools/` or `app/`.
