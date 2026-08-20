# Projectivy parity and Android launcher matching research

Research refreshed **20 August 2026** against Projectivy Icon Pack **1.1.9**
(the latest published release at the time). The decoded mapping snapshot used by
our regression checks is committed at
`tools/reference/projectivy-1.1.9-appfilter.xml`.

## Measured coverage

Marketing “icon count” and automatic coverage are not the same metric. An APK
can contain launcher branding, UI art, unmapped picker icons, and separate art
for mobile/TV variants. We report each layer instead of presenting one inflated
number.

| Metric | Projectivy 1.1.9 | Core Builds 1.6.0 |
| --- | ---: | ---: |
| Selectable/supported app names | ~980 | **917** |
| Distinct mapped art IDs | 872 | **917** |
| Source component mappings | 962 (955 canonical identities) | **1,090** |
| Unique mapped packages | 887 | **958** |
| Generated appfilter rows | 962 | **1,646** |
| 16:9 320×180 banners | yes | **917** |
| Square 512×512 icons | yes | **917** |

Core Builds is now in the same coverage class as Projectivy rather than roughly
half its size. More importantly, it covers every canonical component identity
in the Projectivy 1.1.9 mapping snapshot, plus components gathered from device
scans and newer community reports. The 1,646-row number is not 1,646 apps: it
includes both full and short spellings for compatibility.

The Projectivy repository describes the pack as “over 800 icons” and publishes
its current supported-app list and releases here:

- <https://github.com/SicMundus86/ProjectivyIconPack>
- <https://github.com/SicMundus86/ProjectivyIconPack/releases/tag/1.1.9>

## How Android launchers actually match an icon

The common contract is the ADW icon-pack format:

1. A launcher discovers a pack through one of the theme/picker intent actions
   declared in `AndroidManifest.xml`.
2. It loads `appfilter.xml` from `res/xml` (recommended), `res/raw`, or
   `assets`, depending on the launcher implementation.
3. Each `<item>` maps an Android **component**—package plus activity class—to a
   drawable resource name.
4. The launcher resolves that drawable by name from the icon-pack APK.
5. If no exact component mapping exists, it uses the app's own icon or an
   optional generated fallback. A same-package mapping to the wrong activity
   does not count.

References:

- Lawnchair ADW icon-pack support: <https://docs.lawnchair.app/>
- Kvaesitso icon-pack integration:
  <https://kvaesitso.mm20.de/docs/developer-guide/integrations/icon-packs>
- Android `ComponentName` API:
  <https://developer.android.com/reference/android/content/ComponentName>
- Android TV icon guidance:
  <https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines>

### Canonical identity versus spelling

These strings identify the same Android component:

```text
com.example.tv/.MainActivity
com.example.tv/com.example.tv.MainActivity
```

`ComponentName.unflattenFromString()` expands the leading-dot form. A launcher
that parses the value into a `ComponentName` therefore treats both spellings as
equivalent. Older and custom launchers sometimes key raw strings instead. Core
Builds emits the fully qualified spelling—the unambiguous Android form—and its
short twin when one exists. The validator compares canonical identities and
also verifies both emitted spellings, preventing silent compatibility drift.

Activities in another namespace have no legal short twin:

```text
au.com.tenplay/tv.youi.networktentv.MainActivity
```

Those are emitted once, exactly as reported by the package manager.

## TV-specific matching traps

### `LAUNCHER` and `LEANBACK_LAUNCHER` may differ

A TV build can expose one activity for the normal launcher category and another
for `android.intent.category.LEANBACK_LAUNCHER`. Projectivy launches the latter,
so mapping only a phone activity can appear correct in an app database and
still fail on the TV. `tools/scan_device.sh` queries both categories and prefers
the Leanback result.

### Package names are insufficient

Several products ship separate Google TV, Fire TV, phone, beta, regional, or
white-label packages. Others retain the package but change their launcher
activity in an update. Matching by app label (“Netflix”) or package alone is a
heuristic for finding a candidate, never the final key. The exact resolved
component from the target device is the evidence.

### Manual overrides win

Projectivy preserves icons a user selected manually. Applying or updating a
pack does not necessarily replace those per-app choices. Reset the app's icon
to automatic/default before diagnosing the appfilter.

### Cache invalidation matters

Launchers cache parsed mappings and rendered bitmaps. After reinstalling a pack,
reselect the pack or force-stop Projectivy before treating an unchanged card as
a mapping failure.

## Compatibility choices in Core Builds

| Capability | Projectivy 1.1.9 | Core Builds |
| --- | --- | --- |
| `res/xml/appfilter.xml` | yes | yes |
| Byte-identical `assets/appfilter.xml` fallback | no useful mapping there | **yes** |
| Projectivy `ACTION_PICK_ICON` discovery | yes | yes |
| Nova/ADW/Apex/Lawnchair discovery actions | yes | yes |
| `LEANBACK_LAUNCHER` and TV banner | yes | yes |
| Android 11+ `<queries>` for direct-apply detection | absent | **yes** |
| Full + short component spellings | mostly full | **both when legal** |
| Canonical reference coverage regression | no | **yes** |
| Resource shrinking disabled | framework-dependent | **yes** |

The assets copy is generated from the same string as `res/xml`; validation
fails if the two differ. This supports older integrations without creating a
second source of truth.

## Verification workflow

For an installed app, hardware remains authoritative:

```bash
# Normal launcher activity
adb shell cmd package resolve-activity --brief com.example.tv

# Every TV launcher activity
adb shell cmd package query-activities --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.LEANBACK_LAUNCHER

# Full pack-vs-device report
./tools/scan_device.sh --all
```

A trustworthy icon request should include the command output, device/OS, app
version, and whether the APK is Play, Amazon, or sideloaded. New mappings are
marked unverified until hardware evidence is available; inherited 1.1.9
mappings retain explicit `mapping_source` provenance.

## Artwork sizing

Android TV's legacy xhdpi banner size is **320×180** and the launcher icon is
square. Core Builds therefore ships both 320×180 transparent banners (the
automatic Projectivy default) and 512×512 transparent picker icons. Every
resource name used by appfilter is validated, and shrinking is disabled because
launchers resolve those names dynamically.
