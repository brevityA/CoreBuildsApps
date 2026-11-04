# TVLok — verified launchers and visual reference

Inspected **26 October 2026**, without installing or executing the APK.
The official [download page](https://tvlok.com/download) linked this version
as its previous Android/TV build:

<https://downloads.tvlok.com/releases/android/tvlok-1.0.192-1129-A627B2A4F6A3E09CF17549F2407DE3D04B226B44AAB6B3375E53E7760AB47B63.apk>

| Field | Observed value |
|---|---|
| App / package | TVLok / `com.tvlok.app` |
| Version | `1.0.192` / code `1129` |
| APK bytes | `40,205,039` |
| SHA-256 | `a627b2a4f6a3e09cf17549f2407de3d04b226b44aab6b3375e53e7760ab47b63` |
| Phone launcher | `com.tvlok.app/com.tvlok.app.ui.MainActivity` — activity, MAIN + LAUNCHER |
| TV launcher | `com.tvlok.app/com.tvlok.app.ui.TvLauncherActivity` — activity-alias, MAIN + LEANBACK_LAUNCHER |
| TV alias target | `com.tvlok.app.ui.MainActivity` |

The TV alias **is not interchangeable with its target** in an icon-pack
appfilter. Both real launch components are mapped. The old
`com.tvlok.app/com.tvlok.app.MainActivity` and
`com.tvlok.tv/com.tvlok.tv.MainActivity` entries were guesses, explicitly
flagged as such; they are removed rather than carried forward as evidence.
This resolves the missing-component blocker in
[#352](https://github.com/brevityA/CoreBuildsApps/issues/352) independently of
its malformed component field. It does not claim a physical-device test or
identify which APK version the reporter had installed.

## Evidence and reproduction

- `receipt.json` is the output of `tools/inspect_icon_apk.py`, with the APK
  digest, launcher categories and original referenced-resource digests.
- `visual-references.json` pins the **downsized reference images**, including
  the original resource paths/hashes, original dimensions, thumbnail sizes,
  transformation, image hashes and colour-sample rectangles.
- `preview/Jt.png`: 192×192 reference of the APK's 512×512 launcher icon.
- `preview/E8.png`: 192×108 reference of its 1280×720 TV banner.

These thumbnails are intentionally **not** described as byte-identical to
those APK resources. The original and derivative hashes are separate.

The APK was fetched and inspected in a temporary, branch-only, read-only
[Actions job](https://github.com/brevityA/CoreBuildsApps/actions/runs/40303327290),
because this workspace cannot reach the vendor's download host. Its SHA-256
was checked against the hash in the official download filename. Small
reference images were transported in size-bounded check annotations and
reassembled only after their hashes matched. The temporary workflow is
removed from the final patch. No normal build downloads TVLok.

To reproduce on a machine with access to the vendor:

```sh
# Keep the downloaded APK outside tracked source.
# Optional research dependencies, not runtime/generator requirements:
python -m pip install androguard==4.1.4 Pillow==10.4.0
python tools/inspect_icon_apk.py build/icon-source/tvlok.apk \
  --source-url 'https://downloads.tvlok.com/releases/android/tvlok-1.0.192-1129-A627B2A4F6A3E09CF17549F2407DE3D04B226B44AAB6B3375E53E7760AB47B63.apk' \
  --output build/icon-source/tvlok-inspection
```

## Core Builds interpretation

The official art has a tilted violet/magenta tile behind a cyan/blue play
tile. `tvlok_play_tiles()` redraws those cues with rounded monoline geometry,
not the generic `TL` broadcast shell the catalog previously used.

The two gradients are simplified to two sampled flat accents, **#17A5DF**
(front) and **#8E46AE** (rear), using the recorded median sample rectangles.
The play triangle becomes a cyan outline under the pack's linework rules.
The common contrast treatment applies equally to both paints. The normal
banner supplies the TVLok name; no vendor wordmark is pasted into it.

Reference artwork remains TVLok's property and is not covered by the repo's
MIT licence. No APK, vendor pixel art or vendor paths are shipped in the
pack. See [THIRD_PARTY_NOTICES.md](../../../THIRD_PARTY_NOTICES.md).
