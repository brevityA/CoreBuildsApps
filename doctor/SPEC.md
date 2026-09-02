# Core Doctor — Spec

**Version:** 0.1.0
**Status:** Beta

## What it is

A free, local-only Android phone app that runs 4 diagnostic checks against the user's streaming infrastructure. No backend, no persistence, no analytics. Credentials stay on-device and are never logged or transmitted beyond the provider APIs they belong to.

## Check taxonomy

| # | Check | Gate | Verdict range | Evidence |
|---|-------|------|---------------|----------|
| 1 | DNS resolution | Always | PASS / WARN (>2s) / FAIL | Resolves api.real-debrid.com, api.torbox.app, v6-4.aiostreams.elfhosted.com |
| 2 | VPN detection | Always | PASS / WARN | ConnectivityManager TRANSPORT_VPN |
| 3 | Addon manifest | Addon URL provided | PASS / WARN (unconfigured) / FAIL | HTTP GET {url}/manifest.json |
| 4 | Stream probe | Addon URL provided | PASS / WARN (empty) / FAIL | HTTP GET {url}/stream/movie/tt0133093.json |
| 5 | Real-Debrid | RD key provided | PASS / WARN (non-premium, expiring) / FAIL | GET api.real-debrid.com/rest/1.0/user |
| 6 | TorBox | TB key provided | PASS / WARN (free plan, expiring) / FAIL | GET api.torbox.app/v1/api/user/me |

## Trust architecture

- No server. All checks run client-side.
- API keys are passed as Bearer tokens only to the provider they belong to.
- Share reports are redacted by construction: ReportCard.render() emits verdicts and summaries only. Keys and URLs are structurally excluded.
- `allowBackup=false` in manifest.
- No persistence (no SharedPreferences, no Room, no files).
- Permissions: INTERNET, ACCESS_NETWORK_STATE. Nothing else.

## Stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose (BOM 2024.06.00) |
| HTTP | OkHttp 4.12.0 |
| JSON | kotlinx-serialization-json 1.6.3 |
| Build | AGP 8.5.2, Gradle 9.7.0 |
| Min API | 26 (Android 8.0) |
| Target/Compile | 35 |

## v2 parking lot

Ideas explicitly deferred. Do not implement without approval.

- Latency measurement to debrid CDN endpoints
- Stremio addon catalog enumeration
- WebSocket connectivity check
- Persistent history / trend tracking
- Auto-retry with exponential backoff
- Push notifications for status changes
- Widget for home screen status
