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

Icon pack bump — every file that carries the version, because `AGENTS.md` is the
seventh registry and the one nobody remembers (see issue #71):
- [ ] `app/build.gradle.kts` versionName + versionCode
- [ ] `tools/catalog.json` meta.version
- [ ] `app/src/main/assets/version.json` (NOT `Latestrelease/version.json` — the tag build publishes that one, so users never see an unreleased version)
- [ ] `suite.json` — versionName, versionCode
- [ ] `AGENTS.md` — suite table version
- [ ] `README.md` — headline + release note (NOT covered by
      `tools/build_readme_badge.py`, which only rewrites the stamped block
      between the `suite-stamp` markers)
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
- [ ] `python tools/verify_motion_plugin.py`

## Notes / unverified

