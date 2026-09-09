# Suite tests

```bash
python3 tests/test_v151_robustness.py
python tests/test_ui_generator.py   # UI Studio: presets, D-pad audit, spec validation
```

See CHANGELOG.md for what v1.5.1 added.

## Core Builds identity and NoBuffr

`python tests/test_icon_identity.py` runs 35 offline regressions after the packs
are generated. It covers canonical brand groups, dark-card contrast parity,
reference-file hashes, proportional reference inspection, transparent counters,
MUBI's seven round elements, the verified NoBuffr component in all three packs,
and static APK activity/alias/path handling.

Style is now an acceptance criterion: every reviewed glyph must use the shared
rounded 32 / 26.2 / 21.8px line grammar, a single accent, transparent interiors
and the standard Outfit/category/rail banner. Mutant tests reject vendor fills,
fixed-white wordmarks, square caps, oversized strokes, rescaling and mark-only
banners. Raster checks enforce safe bounds and open ink rather than logo slabs.
The previous NoBuffr vendor-silhouette similarity test was replaced because it
rewarded the off-style result the user rejected; the APK evidence tests remain.
