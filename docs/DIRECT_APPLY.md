# Direct apply — how it works

Findings from decompiling **Projectivy Icon Pack 1.1.9**
(`Projectivy_Icon_Pack_1.1.9.apk`, 17.1 MB) and cross-checking against the
[Blueprint](https://github.com/jahirfiquitiva/Blueprint) source it is built on.

## The mechanism

An icon pack cannot apply itself — the launcher owns that decision. Applying is
just firing the launcher's documented intent with **our own package name** as
the payload.

Projectivy's contract, extracted from `classes.dex`:

```
action   com.spocky.projengmenu.APPLY_ICONPACK
package  com.spocky.projengmenu
extra    com.spocky.projengmenu.extra.ICONPACK_PACKAGENAME  =  <our package>
flags    FLAG_ACTIVITY_NEW_TASK
```

Dispatched with **`startActivity`**, not `sendBroadcast`. Blueprint's
`executeProjectivyLauncherIntent()` confirms it:

```kotlin
Intent("com.spocky.projengmenu.APPLY_ICONPACK").apply {
    `package` = "com.spocky.projengmenu"
    putExtra("com.spocky.projengmenu.extra.ICONPACK_PACKAGENAME", packageName)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
```

That's the whole trick. One intent, one extra.

## Two things the decompile revealed that we were missing

### 1. `<queries>` — the silent killer

We target SDK 34. Since Android 11 (API 30), `getPackageInfo()` and
`resolveActivity()` return **nothing** for apps not declared in `<queries>`.
Without it, launcher detection reports "not installed" on every modern TV and
direct apply never fires — with no error, because nothing threw.

Now declared for each supported launcher plus the `HOME` intent (used to detect
the *active* launcher). Deliberately not `QUERY_ALL_PACKAGES`, which Play treats
as a sensitive permission needing justification.

### 2. Projectivy's own picker action

```
com.spocky.projengmenu.icons.ACTION_PICK_ICON
```

Separate from the generic ADW/Nova picker actions we already declared. Without
it the pack is invisible to Projectivy's **per-app "choose icon"** browser — the
manual override users reach for when an icon doesn't auto-assign.

## What we implemented

`ApplyIconPack.kt` — five launchers, Projectivy first:

| Launcher | Action | Extra |
| --- | --- | --- |
| Projectivy | `com.spocky.projengmenu.APPLY_ICONPACK` | `...extra.ICONPACK_PACKAGENAME` |
| Nova | `com.teslacoilsw.launcher.APPLY_ICON_THEME` | `...extra.ICON_THEME_PACKAGE` (+ type `GO`) |
| Lawnchair | `ch.deletescape.lawnchair.APPLY_ICONS` | `packageName` |
| Apex | `com.anddoes.launcher.SET_THEME` | `...THEME_PACKAGE_NAME` |
| ADW | `org.adw.launcher.SET_THEME` | `org.adw.launcher.theme.NAME` |

Behaviour, following Brand Guide §05/§08:

- The button **names the target before it's pressed** — "Apply to Projectivy
  Launcher", not a bare "Apply".
- The intent is **resolved before starting**, so a launcher that dropped support
  yields a named manual path instead of an uncatchable crash.
- Three explicit outcomes — `Applied`, `NotInstalled`, `Manual` — each naming the
  launcher. Never "something went wrong".
- On `Manual`, we open the launcher *and* state the exact menu path.
- Detection prefers the launcher currently set as HOME, falling back to the
  first supported one installed.

## Why our APK is 3 MB and theirs is 17 MB

Theirs bundles the full Blueprint framework — wallpapers, Muzei integration,
request system, licence checking, Play Billing. We ship the icons and the apply
contract. Nothing else is needed to be a well-behaved icon pack.

## Guarded by the validator

Ten new checks assert the exact intent strings and every `<queries>` entry.
Verified they fail correctly: deleting the `<queries>` block produces four named
failures rather than a silent pass.
