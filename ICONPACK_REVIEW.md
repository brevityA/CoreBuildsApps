# Icon Pack Consistency Review

**Repository:** `CoreBuildsApps` (icon pack)  
**Total icons:** 924 SVG files (under `assets/svg/`)

## Overview

The icon pack generally uses a 512×512 viewBox, which is consistent across all files. However, a detailed attribute analysis reveals several stylistic inconsistencies that may affect visual harmony when displayed together.

## Findings

### 1. Stroke Width Variation

| Stroke Width | Number of Icons | Percentage |
|--------------|----------------|------------|
| `32.0`       | 903            | 97.5 % |
| `26.2`       | 16             | 1.7 % |
| `21.8`       | 3              | 0.3 % |
| `2` (unusual)| —              | — |

**Impact:** The majority (903) of icons use a stroke width of **32.0 px**, which appears to be the intended base size. A small group (19 icons) deviates with `26.2` or `21.8`, creating a noticeably thinner line weight in those assets.

**Icons with non‑standard stroke widths (26.2 or 21.8):**
- `androidtv_2.svg`, `deezer.svg`, `disneyplus.svg`, `hd_streamz.svg`
- `launcher.svg`, `launcher_manager.svg`, `moviesanywhere.svg`, `nasa.svg`
- `nbc_news.svg`, `nbcsports.svg`, `nousguide.svg`, `retroarch.svg`
- `rustore.svg`, `sbs.svg`, `tasker.svg`, `tvquickactions.svg`
- `uefa_tv.svg`, `wireguard.svg`, `youtube_2.svg`

### 2. Fill Presence

| Fill Value | Number of Icons |
|------------|-----------------|
| `none`     | 922 |
| `#FF0000`  | 4 (youtube, smarttube, tvmusic, youtube_2) |
| `#001529`  | 1 (mubi) |

**Impact:** Almost all icons (`922`) have `fill="none"` and rely on stroke for visibility. However, **five icons** use an explicit fill color:

| Icon | Fill Color | Visual Effect |
|------|------------|---------------|
| `youtube.svg` | `#FF0000` | Filled solid red (no stroke) |
| `smarttube.svg` | `#FF0000` | Filled solid red |
| `tvmusic.svg` | `#FF0000` | Filled solid red |
| `youtube_2.svg` | `#FF0000` | Filled solid red |
| `mubi.svg` | `#001529` | Filled dark‑almost‑black circles |

Three of the filled icons (`smarttube`, `tvmusic`, `youtube_2`) use a **red stroke** (`stroke="#FF0000"`) while having `fill="none"`, making them red outlines. The `youtube.svg` is completely filled red with `stroke="none"`. The `mubi.svg` uses filled circles with a dark color and no stroke.

### 3. Stroke Color (observed samples)

- Most outline icons use a stroke color drawn from the app's accent palette (e.g., `#00B6E4` for ABC iview, `#43AA8B` for Android, `#B5179E` for App Store).  
- A few icons deviate: `youtube.svg` has `stroke="none"` (filled red), `smarttube.svg` and `tvmusic.svg` have `stroke="#FF0000"` (red outline), and `mubi.svg` has `stroke="none"` (filled dark).

## Recommendations

1. **Unify stroke width** to `32.0` for all icons.  
   - Update the 19 icons that currently use `26.2` or `21.8`.  
   - This will ensure consistent line weight across the entire pack.

2. **Standardize fill behavior**  
   - Decide whether icons should be *outline only* (`fill="none"`) or *filled* with the brand’s primary color.  
   - If the pack intends to be outline‑style, remove the `fill` attribute (or set it to `none`) from the five icons that currently have a fill color.  
   - If some icons are meant to be filled, apply the same fill rule consistently and remove stray strokes.

3. **Harmonize stroke color**  
   - Define a primary stroke color (or use the accent color per‑app as already done for most icons).  
   - Fix the three red‑outline icons (`smarttube`, `tvmusic`, `youtube_2`) to either have `fill="none"` with a consistent stroke, or be filled solid like `youtube.svg`.  
   - Ensure `mubi.svg` follows the same fill/stroke pattern as the rest of the pack.

4. **Automate a final check**  
   - After applying the fixes, re‑run the attribute extraction script to confirm:  
     - All stroke‑width values are `32.0`.  
     - Fill is either omitted or uniformly `none`.  
     - Stroke colors follow the intended palette.

## Next Steps

- Apply the changes to the affected SVG files (stroke‑width and fill adjustments).  
- Run a quick visual diff (e.g., using `diff` on the generated SVG files) to confirm consistency.  
- Consider adding a `CONTRIBUTING.md` or `README` snippet that documents the expected SVG style guidelines for future contributors.

---
*Review performed on 2026‑09‑05 using automated attribute extraction from 924 SVG files.*