# Icon Pack: what it is lacking (2026-10, against 2.3.0 / 984 icons)

Method: read against the current tree, not the 2026-09 study. Counts come from
`tools/catalog.json`, `app/src/main/res`, the appfilter, and GitHub issue
search. This supersedes `iconpack-size-and-gaps-2026-09.md` where they differ.

## Already done since the 2026-09 study

- **PNG to lossless WebP.** `drawable-nodpi` is now 1,958 WebP files. Only the
  launcher `mipmap` PNGs and `cb_banner.png` remain PNG, which matches the
  study's own caveat. Its item 1 is closed.

## Still missing (verified against the tree)

| Gap | Evidence | Why it matters |
|---|---|---|
| **No monochrome (themed-icon) layer** | `mipmap-anydpi-v26/ic_launcher.xml` has only `background` and `foreground`. No `<monochrome>`. | Android 13+ themed icons ignore the pack's art. Cheap to add, and the pack already has a launcher mark to derive it from. |
| **No wallpaper favourites** | No favourite or star code in `app/src/main/java`. | 102 wallpapers and no way to keep one. |
| **No dynamic calendar or clock icons** | No `calendar` entries in `appfilter.xml`. | Phone-launcher feature only. Projectivy does not use it, so only worth doing if phone users matter. |
| **No grayscale map for Lawnchair** | No `grayscale` in `app/src/main`. | Same phone-only scope as above. |

## Coverage and provenance

| Measure | Count | Note |
|---|---:|---|
| Icons | 984 | up from 961 in the 2026-09 study |
| Components mapped | 1,218 | 822 icons map exactly one component |
| Colour with a review date | 672 | reviewed against a brand source |
| Colour on the palette fallback | 129 | by design, no published brand colour |
| Colour with no review date and not on the palette | 183 | e.g. `brand reference (pre-provenance, unverified)` |
| Icons with **no `mapping_source`** | 531 | provenance missing, not necessarily wrong |
| Icons mapped from device-reported issues | 24 | |
| Icon requests closed | 27 new-icon, 7 mapping reports | |
| Icon requests open | 5 | #280, #282, #283, #284, and the standing #72 |

## Accent variety

757 distinct accent colours across 984 icons. `#00D4FF` is used 16 times,
`#4FACFE` 13 times, and `#000000` 11 times. The pack has no palette ceiling,
so near-duplicate accents are common. Tightening this is a slow piece of work,
not a single change.

## Ranked

1. **Themed icons.** Add a monochrome launcher layer. Small, and it fixes the
   launcher icon itself on Android 13+.
2. **Provenance.** Backfill `mapping_source` for the 531 icons that have none,
   and review the 183 unverified colours. This is data work, not art.
3. **Coverage.** Keep working the open requests. The real gap is a steady
   stream of new apps, not a missing set.
4. **Wallpaper favourites.** Smallest feature with a visible payoff.
5. **Calendar and clock.** Only if phone-launcher users matter.

## Not gaps

Checked and already present: launcher intent filters for the major launchers,
TV banners for every icon, the square glyph flavor and its companion, the QR
icon-request flow, and the update and install checks.
