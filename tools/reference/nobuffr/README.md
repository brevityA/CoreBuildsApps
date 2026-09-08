# NoBuffr: verified APK source, Core Builds-styled output

Source supplied by the user:
<https://downloads.nobuffr.com/android/nobuffr.apk>

Inspected on **2026-09-08** without installing or executing the APK:

| Field | Observed value |
|---|---|
| App | NoBuffr |
| Package | `com.nobuffr.app` |
| Version | `1.0.0` / code `210246` |
| Launcher component | `com.nobuffr.app/tv.tivitime.compose.app.AppActivity` |
| Categories | `LAUNCHER` and `LEANBACK_LAUNCHER`, each with MAIN |
| APK bytes | `33,628,207` |
| APK SHA-256 | `ec835a672087b5cb56700ddc3ed4e050519f5829d10e86800d5506d79afda5bf` |

The activity lives in **another namespace**. Neither
`com.nobuffr.app/.MainActivity` nor `com.nobuffr.app/.AppActivity` is an equivalent
component. The literal above remains the only added mapping; the style
correction does not alter it or introduce speculative variants.

`receipt.json` records every exported resource and its checksum. Resource names
are obfuscated by the vendor; do not rename them in the receipt:

- `res/BW.xml`: decoded adaptive-icon XML.
- `res/5c.webp`: highest-density foreground, **432 × 432**, with alpha.
- `res/ak.webp`: corresponding background, **432 × 432**; not used in Classic.
- `res/aC.png`: vendor's **320 × 180** TV banner; reference only.
- Other WebP files: density variants referenced by the same resource IDs.

## Source reference is not the pack's style

The APK shows white stacked **no / buffr** lettering and an interrupted blue
underline. The first pass vectorised this lettering with VTracer 0.6.12 and
rendered it as a standalone wordmark. **The user rejected that treatment:** it
broke the uniform Core Builds identity. The traced SVG is retained only as
`tools/brandmarks/nobuffr.svg`, explicitly marked `usage: reference-only` in the
catalog. It cannot replace the active glyph.

The active `nobuffr_mark()` in `tools/glyphs.py` is now an **original Core Builds
interpretation** of the observed lowercase **no** and interrupted-underline
cue. It uses the same rounded 32px main stroke, subordinate detail strokes,
one accent and transparent interiors as the other geometric icons. The full
NoBuffr name is rendered in the **same Outfit label + PLAYER category +
cyan/violet rail** banner as neighbouring apps. There is no fixed-white vendor
wordmark or private mark-only banner.

Regression tests retain the APK/hash/resource and cross-pack mapping checks,
and now enforce the Core Builds line/colour/banner contract. The old test
requiring a near-exact vendor-wordmark silhouette was deliberately replaced:
that was the wrong acceptance criterion for an icon pack with its own identity.

## Reproduce inspection of a future APK

```bash
mkdir -p build/icon-source
curl --fail --location --proto '=https' --proto-redir '=https' \
  'https://downloads.nobuffr.com/android/nobuffr.apk' \
  -o build/icon-source/nobuffr.apk
# Optional research dependency only; normal icon builds are offline.
pip install androguard==4.1.4
python tools/inspect_icon_apk.py build/icon-source/nobuffr.apk \
  --source-url https://downloads.nobuffr.com/android/nobuffr.apk \
  --output build/icon-source/inspection
```

Review new evidence before replacing this receipt: the URL is mutable.
The source was fetched/inspected on a GitHub Actions runner because the
workspace could not reach its download host. That temporary branch-only fetch
trigger was removed; no normal build fetches or executes NoBuffr.
Static manifest verification is not physical-device Projectivy auto-assignment.

Original/reference artwork remains NoBuffr's property; see
[`THIRD_PARTY_NOTICES.md`](../../../THIRD_PARTY_NOTICES.md).
