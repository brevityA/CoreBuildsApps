# Building the icon list from your own device

The pack only auto-assigns when a mapped component matches the **exact activity
your device launches**. Guessing that string is the single biggest source of
"the icon didn't apply" — so read it off the hardware instead.

## 1. Connect

**Network ADB (easiest for a TV):**

On the TV: Settings → System → About → click *Android TV OS build* seven times
to unlock Developer options, then Settings → System → Developer options →
**Network debugging** (or *USB debugging* if you're plugging in).

Find the IP under Settings → Network → About, then:

```bash
adb connect 192.168.1.50:5555     # your TV's IP
adb devices                       # accept the on-screen prompt
```

**Don't have adb?**

```bash
brew install --cask android-platform-tools   # macOS
sudo apt install adb                         # Debian/Ubuntu
# Windows: https://developer.android.com/tools/releases/platform-tools
```

## 2. Scan

```bash
./tools/scan_device.sh                  # third-party apps
./tools/scan_device.sh --all            # include system apps
./tools/scan_device.sh --serial ABC123  # when several devices are attached
```

Writes `tools/device_scan.json` (machine-readable) and `tools/device_scan.txt`
(the report), and prints a summary:

```
launchable apps scanned : 63
covered by the pack     : 28
MISMATCHED components   : 3
no icon yet             : 32
```

### What the three buckets mean

- **COVERED** — the pack maps this exact component. It will apply.
- **MISMATCHED** — ⚠️ *the important one.* The pack has the app, but this device
  launches a **different activity**, so the icon silently never applies. Fix by
  adding the device's component to that icon's `components` array.
- **NO ICON YET** — candidates for new icons, with paste-ready catalog entries.

## 3. Feed the gaps back

The report ends with a JSON block you can paste into `tools/catalog.json` after
setting a real `name`, `color`, and `glyph` for each. Then:

```bash
python tools/build_icons.py
python tools/validate.py
```

Re-report later without re-scanning:

```bash
python tools/import_scan.py --report
```

---

## Manual one-liners

**One app's launcher component** — the single most useful command:

```bash
adb shell cmd package resolve-activity --brief com.example.tv | tail -1
```

**Every TV (leanback) app with its component:**

```bash
adb shell cmd package query-activities --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.LEANBACK_LAUNCHER
```

**Every third-party package:**

```bash
adb shell pm list packages -3 | sed 's/^package://' | sort
```

**Is the pack installed and visible to launchers?**

```bash
adb shell pm list packages | grep corebuilds
adb shell dumpsys package tv.corebuilds.iconpack | grep -i -A2 "action"
```

**Install the pack:**

```bash
adb install -r app-release.apk
```

**Force Projectivy to re-read icons** (after applying, if nothing changes):

```bash
adb shell am force-stop com.spocky.projengmenu
```

---

## Why leanback matters

Some apps expose a *different* activity to TV launchers than to phone
launchers. `resolve-activity` returns the phone one; the TV launcher starts the
leanback one. Map the wrong variant and the icon quietly fails.

`scan_device.sh` queries both and prefers the leanback activity when present —
which is exactly the class of bug the MISMATCHED bucket surfaces.
