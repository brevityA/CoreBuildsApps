# NoBuffr: verified APK source

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
component. The literal above is the only mapping added to the catalog; no
speculative variants were added.

`receipt.json` records every exported resource and its checksum. Resource names
are obfuscated by the vendor, so do not rename them in this receipt:

- `res/BW.xml`: decoded adaptive-icon XML.
- `res/5c.webp`: highest-density foreground, **432 × 432**, with alpha; this is
  the lettering/underline source, not a generated replacement.
- `res/ak.webp`: corresponding background, **432 × 432**; not used in Classic.
- `res/aC.png`: the vendor's **320 × 180** TV banner, retained as reference only.
- Other WebP files: density variants referenced by the same resource IDs.

The original foreground's lettering and underline were separately masked
(alpha ≥128, white type versus blue underline), vectorised with VTracer 0.6.12
(`binary`, `spline`, speckle 2, corner threshold 60, length threshold 3.5,
path precision 3), then translation transforms were flattened. The reviewed
result is `tools/brandmarks/nobuffr.svg`. Its hash is pinned in the catalog;
VTracer is **not a build dependency**. The regression test compares the final
mark's silhouette with this APK foreground rather than merely checking that
some non-empty icon exists.

## Reproduce inspection of a future vendor APK

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

Review the receipt before replacing committed evidence: the URL is mutable.
Static manifest verification is **not** a physical-device auto-assignment test.
The source URL was fetched and inspected on a GitHub Actions runner because
this workspace could not reach the vendor's host directly. The temporary
branch-only fetch workflow was removed after inspection; no CI build now
fetches NoBuffr or requires its download host.

Artwork remains NoBuffr's property; see
[`THIRD_PARTY_NOTICES.md`](../../../THIRD_PARTY_NOTICES.md).
