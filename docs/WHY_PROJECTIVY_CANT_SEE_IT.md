# Why Projectivy couldn't see the pack — diagnosed and fixed

The APK installed fine, but the pack never appeared in
**Projectivy Settings → Appearance → Cards → Icon Pack**.

That is a *discovery* failure, not an install failure. Projectivy builds its
icon-pack list by scanning installed apps for specific **intent actions**. If a
pack doesn't declare the action a launcher scans for, the launcher never learns
it exists — the app is installed and invisible at the same time.

## Root cause

Decompiled our shipped **v1.0.0** APK and compared its manifest against the
established Projectivy pack:

| Action | Their pack | Our v1.0.0 |
| --- | --- | --- |
| `com.spocky.projengmenu.icons.ACTION_PICK_ICON` | ✅ | ❌ **missing** |
| `com.novalauncher.THEME` | ✅ | ✅ |
| `org.adw.launcher.THEMES` | ✅ | ✅ |
| `<queries>` block | — | ❌ **missing** |

We declared the generic ADW/Nova actions but **not Projectivy's own**. The one
launcher the pack is named for was the one that couldn't find it.

Two defects, both now fixed:

1. **Missing `com.spocky.projengmenu.icons.ACTION_PICK_ICON`** — Projectivy's
   own discovery/picker action. This is why the pack was invisible.
2. **Missing `<queries>`** — with `targetSdk 34`, Android 11+ package-visibility
   filtering made every launcher lookup return "not installed", so the in-app
   apply button could never have worked either. Silent, because nothing throws.

Also added the remaining discovery actions the reference pack declares (Sony,
Fede, Lawnchair `PICK_ICON`, OnePlus, Turbo, and Nova's
`CUSTOM_ICON_PICKER` category). They cost nothing and each one is a launcher
that can now list the pack.

## What to do

The fix is committed but **not yet released**. The published v1.0.0 APK still
has the bug — reinstalling it changes nothing.

Ship **v1.0.1** (version already bumped, `versionCode 2`):

```bash
git push
git tag -a v1.0.1 -m "Fix Projectivy discovery + Android 11 package visibility"
git push origin v1.0.1
```

Then reinstall on the TV. The pack should appear in Projectivy's icon-pack list,
and the in-app **Apply to Projectivy Launcher** button should work in one press.

If it still doesn't appear after installing v1.0.1, capture this on-device and
open an issue with the output:

```bash
adb shell dumpsys package tv.corebuilds.iconpack | grep -A2 -i "action"
adb shell pm list packages | grep projengmenu
```

## Guarded

`tools/validate.py` now asserts every discovery action and `<queries>` entry —
419 checks. Verified they fail by name when the block is removed, so this class
of bug cannot ship silently again.
