# v1.5.1 robustness tests

```bash
python3 tests/test_v151_robustness.py
```

See CHANGELOG.md for what v1.5.1 added.

## Brand identity and NoBuffr

`python tests/test_icon_identity.py` runs 26 offline regressions after all three
icon packs are generated: canonical brand groups, dark-card contrast parity,
source-file hashes and safe SVG fitting, real alpha counters, the seven MUBI
dots, the NoBuffr APK-derived silhouette and component mapping in all three
packs, and static APK activity/alias/path handling. No APK or Android SDK is
needed for this suite.
