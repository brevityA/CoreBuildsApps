## App

- [ ] Icon pack
- [ ] Core Line
- [ ] Core Shift
- [ ] Core Doctor
- [ ] Core Motion
- [ ] Docs / CI / suite

## Why this exists


## What changed (name the number)


## Verification

- [ ] `python tools/check_suite_truth.py`
- [ ] `python tools/audit_contract.py`

Icon pack bump:
- [ ] `app/build.gradle.kts` versionName + versionCode
- [ ] `tools/catalog.json` meta.version
- [ ] `Latestrelease/version.json`
- [ ] `python tools/build_icons.py`
- [ ] `python tools/build_banners.py`
- [ ] `python tools/build_branding.py`
- [ ] `python tools/build_brand_preview.py`
- [ ] `python tools/validate.py` — paste last line:

Core Line:
- [ ] `cd ticker && npm test`

Core Shift:
- [ ] `python tools/validate_motion_feed.py`

Core Doctor:
- [ ] `cd doctor && ./gradlew test`

Core Motion:
- [ ] `python tools/validate_projectivy_plugin.py`

## Notes / unverified

