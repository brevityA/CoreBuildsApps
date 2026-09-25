# Global Glyphs toggle feasibility — deep research

**Date:** 25 September 2026 (Sydney)  
**Question:** Can Core Builds expose Glyphs as a setting that users turn on, causing the square Glyphs pack to replace Banners globally instead of making users assign square icons one at a time?  
**Status:** research and current-code audit only. No production assets, catalogue mappings, generators, or release files were changed.

## 1. Short answer

**Yes for icon-pack-aware launchers, and the repository already contains most of the correct architecture.** The launcher must be pointed at a package whose `appfilter.xml` maps every supported component to the chosen drawable family. Core Builds currently has exactly that split:

- `tv.corebuilds.iconpack` maps components to 16:9 banner drawables;
- `tv.corebuilds.iconpack.glyphs` maps the same components to square glyph drawables;
- the setting chooses which package `ApplyIconPack` hands to the launcher;
- the companion is installed only when Glyphs is first selected.

This is a **global pack selection**, not 961 individual icon assignments. Every mapped app changes together when the launcher accepts the pack. Unmapped activities and manual per-app overrides remain exceptions.

**It cannot be universal.** Stock Android TV/Google TV/Fire TV launchers do not expose a general third-party icon-pack selection contract. Monet currently discovers icon packs but, according to the local v1.0.84 probe and current app code, exposes no inbound apply action or exported settings deep link. Monet therefore requires one manual **whole-pack** selection in its own settings; it does not require users to assign every app individually.

## 2. What “separate Glyphs setting” should mean

There are two possible interpretations:

| Interpretation | Recommendation |
| --- | --- |
| A second independent preference that can disagree with the existing Banners preference | **Do not do this.** Two style states would drift and make the launcher target ambiguous. |
| A clearly named setting, `Use square Glyphs`, that globally selects the Glyphs pack instead of the default Banners pack | **Do this.** Keep one source of truth and make the positive Glyphs action obvious. |

The current UI has a home-screen Art style row and a Settings row, but its switch semantics are inverted from the requested wording: **switch on means Banners; switch off means Glyphs**. Internally this is preserved by `KEY_PICK_BANNERS`, whose default is `true` for backwards compatibility.

Recommended user-facing semantics:

```text
Use square Glyphs
Off: 16:9 Banners are applied (default)
On: square Glyphs are applied
```

The implementation can retain the existing stored key and migrate only the presentation/accessor:

```text
useGlyphs = !Prefs.pickerPrefersBanners(context)
```

That avoids breaking existing installs while matching the user's mental model. The setting should remain one mutually exclusive style choice, not two booleans.

## 3. Why two packages are technically necessary

### 3.1 The Android icon-pack contract is package-selected

The ADW/Blueprint family of conventions gives launchers:

- `appfilter.xml`: component/activity → drawable mapping;
- `drawable.xml`: drawable index for manual pickers;
- manifest intent filters: discovery and picker entry points;
- optional fallback furniture such as `iconback`, `iconmask`, and `iconupon`.

A launcher normally selects an icon-pack **package**, reads that package's mapping, and resolves a drawable for each component. It does not ask a pack's runtime UI which of two arbitrary drawable families should be used for every mapping.

A single APK containing both `foo` and `foo_banner` can support per-app manual picking, but standard launchers have no portable “switch every mapping from family A to family B” setting inside that one package. The reliable global variant mechanism is:

```text
Banners package → same components → *_banner drawables
Glyphs package  → same components → square drawables
```

The packages must have different application IDs because Android cannot install two variants with the same package name. The Glyphs companion must not have its own launcher entry, or it becomes a second visible app rather than a resource pack.

### 3.2 This is the same user experience users already understand

Projectivy's current documentation explains that icon packs automatically replace default card icons, while also allowing individual custom-icon picking. The Projectivy Icon Pack README describes automatic assignment for supported applications and manual assignment for package/activity variants that cannot be mapped.

That distinction is exactly what Core needs:

- **global toggle:** select the entire Banners or Glyphs package;
- **manual picker:** repair one missing/ambiguous component or deliberately override one app;
- **not promised:** a global switch that can override a user's manual icon choice or alter a stock launcher that does not consume icon packs.

## 4. Current Core implementation audit

The repository is already close to the requested feature.

| Current element | Location | Finding |
| --- | --- | --- |
| Persisted style state | `app/src/main/java/tv/corebuilds/iconpack/Prefs.kt` | `KEY_PICK_BANNERS`, default `true`; one setting controls catalogue, picker, and launcher target. |
| Banner pack | `app/` | Main package's generated `appfilter.xml` maps all generated rows to `_banner` drawables. |
| Glyph pack | `glyphs/` | Resource-only companion maps the same components to square glyphs and carries the same discovery filters. |
| Global target selection | `ApplyIconPack.kt` | `GlyphsCompanion.applyTarget()` selects the companion when Glyphs is wanted and ready; otherwise the main package is the Banner target. |
| Companion installation | `GlyphsCompanion.kt` + `UpdateInstaller.kt` | Downloads the matching release asset, verifies package/version/signature, invokes Android's installer, and records the launcher awaiting apply. |
| Failure rollback | `GlyphsCompanion.kt` | Missing install permission, failed download, installer failure, or declined install returns the stored style to Banners. |
| Home setting | `MainActivity.kt` | Art style row sits beside Apply and re-applies the selected target to the detected launcher. |
| Settings setting | `SettingsActivity.kt` | The same preference is exposed in Settings and invokes the same apply path. |
| Launcher handoff | `ApplyIconPack.kt` | Projectivy and standard apply contracts can receive a package; no-inbound launchers receive a named setup flow. |
| Monet path | `docs/MONET_LAUNCHER.md`, `LauncherSetupActivity.kt` | The app checks discovery, then asks the user to choose the selected one pack in Monet's own settings. |
| Picker behaviour | `MainActivity.kt`, companion `GlyphsActivity.java` | A picker opened from the Glyphs package returns square art; a picker opened from the Banner package returns banners. The global style setting is not changed by picking one app. |
| Contract tests | `tests/test_glyphs_pack.py` | Verifies same mappings, opposite drawable families, package identity, discovery parity, signed release asset naming, installer flow, and failure rollback. |

The current architecture is therefore not “assign 961 icons individually.” It is “apply one of two whole packs, each with 1,807 generated mapping rows.”

## 5. Launcher feasibility matrix

| Host | Can the toggle apply the whole Glyphs set? | What actually happens | Confidence |
| --- | --- | --- | --- |
| **Projectivy Launcher** | **Yes, intended path** | The app sends the selected package through the Projectivy apply contract. Supported components are auto-assigned by the selected pack. A real-device test is still required for current Projectivy 4.71. | High for architecture; device verification pending |
| **Nova / Lawnchair / ADW / Apex / GO-style launchers** | **Usually yes if the launcher accepts the standard contract** | The app sends the selected package using launcher-specific or standard theme intents. The launcher reads the selected package's `appfilter.xml`. Current code is best-effort and must be tested per target. | Medium; standards are documented, launcher implementations vary |
| **AT4K / L TV / ChillHub and similar TV launchers** | **Potentially** | Current code probes a standard apply action and falls back to a manual path if it does not resolve. A successful probe is not proof that the launcher applies every mapped component correctly. | Medium/low until device-tested |
| **Monet Launcher** | **Not silently/directly** | Monet lists and reads icon packs but has no inbound apply action in the local v1.0.84 probe. The user selects the one requested pack in Monet Settings → Apps → Icon pack; all mapped apps then use it. | High for current local probe; re-check future Monet builds |
| **Stock Android TV / Google TV launcher** | **No third-party pack toggle** | Stock app identity comes from each app's manifest `android:icon` and `android:banner`. Core's setting cannot change another app's manifest or force stock launcher theming. | High |
| **Stock Fire TV launcher** | **No claim** | Fire TV has its own launcher/store/runtime paths. A 16:9 asset does not establish icon-pack selection support. | High that dimensions alone are insufficient |
| **Manual custom icon assignment** | **No global effect** | The launcher stores a per-app override. Reapplying a pack generally does not remove that override. | High as a product risk; exact precedence is host-specific |

## 6. The exact user flows

### 6.1 Projectivy or a standard inbound-apply launcher

1. User turns **Use square Glyphs** on.
2. Core records the one style preference.
3. Core checks that the same-version, same-signature Glyphs companion is installed.
4. If not, Core downloads `iconpack-glyphs-release.apk` from the matching release.
5. Android may require the user to allow Core to request package installs and confirm the APK installation. A third-party app cannot silently install an unrelated package under normal Android rules.
6. After installation, Core re-sends the apply contract with `tv.corebuilds.iconpack.glyphs` as the package name.
7. The launcher reads Glyphs' `appfilter.xml` and applies square glyphs to every mapped component.
8. User turns the setting off to send the main `tv.corebuilds.iconpack` package and return to banners.

This is one global action, not an individual assignment loop.

### 6.2 Monet

1. User turns **Use square Glyphs** on.
2. Core downloads and installs the companion if necessary.
3. Core opens a setup screen explaining that Monet cannot be selected by an external app.
4. User opens Monet once and chooses **Core Builds Glyphs** in its icon-pack setting.
5. Monet reads the Glyphs pack's mappings and applies square glyphs to every supported app.
6. If the user later turns Glyphs off in Core, Core can tell the user to choose **Core Builds Icon Pack** in Monet; it cannot silently change Monet's private preference.

This is still a one-pack selection. It is not one icon at a time. The current `LauncherSetupActivity` already follows this model.

### 6.3 Stock Google TV/Android TV or Fire TV

The setting can change Core's own preview/picker and can explain that the stock launcher is not pack-aware, but it cannot make the stock launcher replace another app's manifest assets. The UI must not promise that every installed app will change on these hosts.

## 7. Important implementation risks

### 7.1 The current switch wording is backwards for the requested feature

The code is functionally correct but the UI says the switch is **Banners** when on and **Glyphs** when off. That is easy to misread as “Glyphs is disabled” rather than “Banners are the default.” A positive `Use square Glyphs` label is the clearest low-risk improvement.

Do not create a second independent preference. Preserve `KEY_PICK_BANNERS` for existing installs and expose a semantic `useGlyphs()` accessor.

### 7.2 Companion freshness is only checked when an apply is attempted

`GlyphsCompanion.ready()` correctly rejects a missing, stale, or differently signed companion. However, if a user has Glyphs selected and the main app updates, the launcher may continue showing the older companion until the user presses Apply/Refresh or changes the setting. The UI should detect on resume:

```text
Glyphs selected + companion missing/stale → “Update Glyphs to keep this style”
```

For an inbound launcher, the app can download and re-apply. For Monet, it must show the one-pack handoff again. This is a consistency/recovery problem, not a reason to abandon the two-package design.

### 7.3 Android installation is necessarily a user-visible step

The current release uses `REQUEST_INSTALL_PACKAGES` and the system package installer. Android documents that an app must check `canRequestPackageInstalls()` and that external-source installs may require user action. The first Glyphs activation cannot be made a completely silent toggle on ordinary sideloaded Android TV devices.

The product should say “Install Glyphs, then apply” during the first activation rather than making the switch appear to have changed the launcher before installation completes.

### 7.4 Mappings and overrides limit the meaning of “every app”

The selected pack can apply all **mapped** components. It cannot automatically invent a mapping for an app whose package/activity is absent or different on the user's device. Projectivy's own pack documentation calls out mobile/TV activity differences and manual assignment for such cases.

A manually selected app icon may also override the pack. The setting should say:

```text
Applies Glyphs to supported mapped apps. Manual overrides stay in control.
```

### 7.5 Monet profiles and host-private state are not controlled

Monet advertises multiple profiles and per-app overrides. The current Core code cannot write Monet's private profile or icon-pack preference, and there is no evidence that an external pack can switch all profiles through one public action. The acceptance test must identify which current profile is active and avoid claiming that one Core toggle updates every Monet profile.

### 7.6 Release coupling is a real product contract

The main APK and companion must remain paired:

- same version code/name;
- same signing certificate;
- same catalogue/component mapping;
- same GitHub release asset name;
- same release availability.

The current CI/test design already checks much of this. A missing companion asset or a mismatched signature turns a convenient toggle into an install failure, so the release workflow is part of the feature.

## 8. Competitive and ecosystem evidence

### Observed competitor behaviour

- Projectivy's official plugin page says icon packs exist specifically to avoid changing cards one by one and can automatically replace default card icons. It also distinguishes a global pack from using a pack as a manual icon library.
- The Projectivy Icon Pack README says supported apps are automatically assigned and documents manual assignment for regional/device activity differences.
- Blueprint and ADW conventions remain package/XML driven: `appfilter.xml` maps components, while `drawable.xml` supports the manual picker.
- Monet's current Play listing, updated 19 September 2026, advertises icon-pack support, seven icon shapes, four tile styles, per-app overrides, adjustable tile size, and multiple profiles. These features increase the value of a global pack, but also make a silent external style switch less likely without a public Monet API.
- A September 2026 Lawnicons discussion proposes a cleaner ContentProvider API for launcher-to-pack resource resolution, but it is an open proposal, not a current cross-launcher contract. It does not provide a practical replacement for the two-package approach today.

### Competitive advantage for Core

Core can offer a clearer TV-specific promise than a generic icon pack:

> “Choose Banners or square Glyphs once. Core applies the selected pack globally wherever your launcher supports icon packs, and tells you honestly when the launcher requires one manual pack selection.”

That is stronger than claiming every host supports a magic switch, and it directly addresses the user pain of assigning icons individually.

## 9. Recommended implementation plan

### P0 — clarify the existing feature

1. Rename the user-facing setting to **Use square Glyphs** or **Icon shape: Banners / Glyphs**, with Glyphs visibly on when selected.
2. Keep `KEY_PICK_BANNERS` as the compatibility storage key; add a semantic accessor rather than migrating stored values blindly.
3. Correct the explanatory copy from “every app” to “every supported/mapped app” and mention manual overrides.
4. Keep the current single preference shared by the home row, Settings, preview grid, inspector, picker, and launcher target.

### P1 — close lifecycle gaps

5. On app resume and after an app update, check whether selected Glyphs is missing/stale.
6. Show a focused recovery action: **Update Glyphs and re-apply** for inbound launchers, or **Set up Glyphs in Monet** for Monet.
7. Keep the existing rollback to Banners when installation fails or is declined.
8. Add an explicit “currently selected target” status: `Banners package`, `Glyphs companion`, or `Manual launcher selection required`.

### P2 — device acceptance matrix

9. Test Projectivy 4.71 on a real Android/Google TV device: Banners → Glyphs → Banners, first install, repeat toggle, launcher cache refresh, mapped/unmapped activity, and manual override.
10. Test Monet current build: companion install, one-time Glyphs selection, automatic application to multiple rows/profiles if supported, per-app override, and switch-back handoff.
11. Test one standard launcher with an inbound apply contract and one launcher that only supports manual selection.
12. Verify stock Google TV and Fire TV are described as unsupported for global icon-pack switching rather than treated as failures.

### Explicitly not recommended

- A second independent Glyphs preference;
- copying both banner and square mappings into one appfilter and expecting a launcher to choose between them;
- per-app shortcut generation as a substitute for an icon pack;
- modifying Monet's private preferences through reverse engineering;
- claiming stock Google TV/Fire TV support from the presence of 16:9 assets;
- silently installing the companion or hiding the Android confirmation;
- changing catalogue names, component mappings, or generated art as part of this UX decision.

## 10. Final feasibility decision

| Question | Decision |
| --- | --- |
| Can Glyphs be selected globally instead of Banners? | **Yes, on pack-aware launchers. Core already has the correct two-package model.** |
| Does this avoid assigning each app individually? | **Yes.** The selected package's appfilter applies all supported mappings together. |
| Can one APK expose two globally selectable standard icon-pack variants? | **Not reliably.** Separate packages are the portable mechanism. |
| Can Core silently switch Monet? | **No, based on the current local probe.** One manual whole-pack selection is required. |
| Can Core switch stock Android/Google TV/Fire TV app identity? | **No.** Those surfaces are controlled by app manifests/launcher policy. |
| Should the UI expose a positive Glyphs-on setting? | **Yes.** Preserve the existing stored preference but present a semantic `Use square Glyphs` control. |
| Should production assets change now? | **No.** The next work is UX wording, lifecycle consistency, and device acceptance testing. |

## 11. Source register

Accessed **25 September 2026** unless a publication/update date is shown.

### Current repository evidence

- [`GlyphsCompanion.kt`](../../app/src/main/java/tv/corebuilds/iconpack/GlyphsCompanion.kt) — companion download/install/readiness/rollback flow.
- [`ApplyIconPack.kt`](../../app/src/main/java/tv/corebuilds/iconpack/ApplyIconPack.kt) — launcher target selection, direct contracts, and Monet handoff.
- [`Prefs.kt`](../../app/src/main/java/tv/corebuilds/iconpack/Prefs.kt) — persisted Banners/Glyphs state.
- [`SettingsActivity.kt`](../../app/src/main/java/tv/corebuilds/iconpack/SettingsActivity.kt) and [`MainActivity.kt`](../../app/src/main/java/tv/corebuilds/iconpack/MainActivity.kt) — home/settings toggle wiring.
- [`tests/test_glyphs_pack.py`](../../tests/test_glyphs_pack.py) — generated-resource and companion contract tests.
- [`docs/MONET_LAUNCHER.md`](../MONET_LAUNCHER.md) — local Monet v1.0.84 probe and current handoff behaviour.
- [`docs/BANNERS.md`](../BANNERS.md) — two-package Banner/Glyph design rationale.

### External ecosystem and platform sources

- [Projectivy plugins](https://projectivylauncher.com/plugins.html) — global icon-pack replacement versus individual custom-card picking.
- [Projectivy Icon Pack README](https://github.com/SicMundus86/ProjectivyIconPack) — automatic assignment, manual mapping caveats, and setup path.
- [Lawnchair icon-pack support](https://github.com/LawnchairLauncher/lawnchair/wiki/Icon-pack-support) — ADW `appfilter.xml`, `drawable.xml`, full component mappings, and package-selected icon theming.
- [Blueprint icon-pack setup](https://github.com/jahirfiquitiva/Blueprint/wiki/Setting-up-icon-pack-(Part-1)) — resource/asset XML conventions.
- [Monet Launcher Play listing](https://play.google.com/store/apps/details?id=com.klevico.monet&hl=en-US) — current icon-pack, tile, override, and profile claims; vendor evidence, not proof of an apply API.
- [Android PackageManager reference](https://developer.android.com/reference/android/content/pm/PackageManager) — `canRequestPackageInstalls()` and user-controlled external APK installation flow.
- [Android package permissions](https://developer.android.com/reference/android/Manifest.permission) — `REQUEST_INSTALL_PACKAGES` requirement for `ACTION_INSTALL_PACKAGE` on current targets.
- [Open ContentProvider proposal for icon packs](https://github.com/LawnchairLauncher/lawnicons/issues/4094) — future ecosystem idea, currently not a standard.

**Confidence:** the two-package requirement and current Core wiring are high-confidence repository findings. Projectivy's global pack concept is high-confidence from its documentation. Monet's lack of inbound apply is high-confidence for the locally decompiled v1.0.84 build and should be rechecked on future versions. Direct apply behavior on each installed launcher, stale-cache precedence, Monet profile scope, and stock-TV behavior require device tests rather than static research alone.
