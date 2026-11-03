# Core Builds Launcher — prototype research and scope

**Research date:** 2026-11-03
**Status:** Core Builds visual pass, conditional local Recent shelf, and self-contained offline HTML illustration are implemented; Android build and hardware validation remain pending because this sandbox has no JDK/Android SDK or attached TV.

## Recommendation

Prototype a narrow Core Builds home surface, not a full Google TV replacement. A generic app grid and favourites shelf are already common; the plausible differentiator is a fast, low-noise, local-first Core Builds home that complements the suite's icon pack, wallpapers, Core Line and Core Shift / Core Motion work.

The current deliverable is deliberately a private, side-loadable experiment. It is not in `suite.json` and has only a build/lint CI check—no release lane, store listing, or public APK distribution. It makes no promise that every manufacturer permits a third-party default launcher.

## Findings

### TV navigation is a D-pad contract, not just a large phone layout

The repository's [Android TV UX research](ANDROID_TV_UX_RESEARCH.md) and [gap audit](ANDROID_TV_GAP_AUDIT.md) summarize Android's TV guidance:

- Every visible action must be reachable with the D-pad, and focus needs an obvious visual state.
- Up/Down should move between browse rows; Left/Right should traverse items in a row.
- Focus must remain stable after refreshes and return from another activity.
- Important content needs safe margins, legible TV-scale type and clear contrast; do not use colour alone to indicate state.
- A build or screenshot cannot replace a real remote/TalkBack pass.

The prototype therefore uses an app shelf plus app grid, a persistent cyan focus outline/scale, large hit areas, explicit descriptions, and focus restoration by package after a changed app scan. Its flat Void canvas has no animated background; the app artwork and restrained focus treatment stay legible.

Official references already cited in the repository's TV UX research:

- [Android TV navigation](https://developer.android.com/training/tv/get-started/navigation)
- [TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system)
- [TV layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts)
- [TalkBack evaluation](https://developer.android.com/training/tv/accessibility/talkback)

### App discovery should be narrow and launchable

Android 11+ package visibility filters which installed packages an app can inspect. The existing icon-pack manifest already demonstrates the suite's preferred approach: query the `MAIN` + `LEANBACK_LAUNCHER` and `MAIN` + `LAUNCHER` activities rather than asking for `QUERY_ALL_PACKAGES` (`app/src/main/AndroidManifest.xml`). The prototype uses those same two intent queries, de-duplicates by package, and prefers the TV-specific activity when one exists.

The catalog is refreshed on return to the home screen, which covers the common install/uninstall flow without keeping a background service or package observer alive. Its render signature includes the app label, TV-launcher capability and package update timestamp, so updated app artwork can replace stale cards without losing package-based focus. Saved favourites are kept through a temporary uninstall but hidden from the shelf and count until the app is launchable again.

### Default HOME selection is a user choice with device-specific friction

A launcher registers a `MAIN` / `HOME` / `DEFAULT` entry; that alone does not guarantee a manufacturer will let the user choose it or that its Home key will always route there. The repo's [Monet integration notes](MONET_LAUNCHER.md) record that even an established launcher uses accessibility and ADB-granted `WRITE_SECURE_SETTINGS` for some Home-key/default-launcher edge cases.

For a private prototype, bypassing that friction would be the wrong first step. The app opens Android's Home-app settings and does not request ADB, root, `WRITE_SECURE_SETTINGS`, or an accessibility service. Device-specific interception is explicitly out of scope.

### Existing launchers establish the baseline, not the opportunity

- This suite already targets [Projectivy Launcher](apps/icon-pack.md) with its icon pack and wallpaper/provider integrations.
- The repo's [Monet research](MONET_LAUNCHER.md) documents an established alternative and its integration boundaries.
- Public reference projects reviewed on 2026-11-03: [nielsvanvelzen/tv-launcher](https://github.com/nielsvanvelzen/tv-launcher) describes itself as an experimental large-screen launcher; [rharter/Android-TV-Launcher](https://github.com/rharter/Android-TV-Launcher) lists favourites, channels and an all-apps grid, and documents platform limitations such as wallpaper support; [JackyAndroid/AndroidTVLauncher](https://github.com/JackyAndroid/AndroidTVLauncher) describes a Leanback-style app list and media features.

The common surface is app discovery + favourites. We should not attempt to clone streaming recommendations or assume that apps expose a consistent channel/catalog API. The first prototype instead tests whether users value the Core Builds visual system and direct integration enough to choose a separate home screen.

## UI design research and treatment

The visual pass combines the repository's [Core Builds brand guide](BRAND-GUIDE.md), [icon pack](apps/icon-pack.md), [Android TV UX research](ANDROID_TV_UX_RESEARCH.md), and [launcher-style audit](research/launcher-style-audit-2026-09.md). I also reviewed public source/UI references on 2026-11-03: [Plume](https://github.com/aradhyas8/Plume_Launcher) (GPL-3.0; commit `b18b15f58af8`), [LTvLauncher](https://github.com/leanbitlab-org/LtvLauncher) (GPL-3.0 FLauncher fork; commit `64247d9a51b8`), and [Tigris](https://github.com/lesnolie/Tigris) (MIT; commit `7697955ec483`). The comparison was used for interaction patterns only; no code or branded assets were copied. Visual references reviewed: [Plume dark home](https://github.com/aradhyas8/Plume_Launcher/blob/b18b15f58af8/docs/home-dark.jpg), [LTv home](https://github.com/leanbitlab-org/LtvLauncher/blob/64247d9a51b8/docs/images/screenshot_1.png), and [Tigris home](https://github.com/lesnolie/Tigris/blob/7697955ec483/docs/screenshots/home.jpg).

### What the comparison says

- **Plume:** its README keeps the launcher job focused on apps and recently watched items; app-name-on-focus and icon/banner choices are optional. Its [`SnapScrollView`](https://github.com/aradhyas8/Plume_Launcher/blob/b18b15f58af8/app/src/main/java/dev/plume/SnapScrollView.kt) lets focused cards lift beyond the row's child clip and scrolls only enough to bring the whole item into view. Takeaway: make app identity readable on focus and never clip the focused card. A media/Watch Next row is not adopted here because it would add content permissions and broaden this private prototype.
- **LTvLauncher / FLauncher:** [`AppsGrid`](https://github.com/leanbitlab-org/LtvLauncher/blob/64247d9a51b8/lib/widgets/apps_grid.dart) renders 16:9 cards; its [`RowByRowTraversalPolicy`](https://github.com/leanbitlab-org/LTvLauncher/blob/64247d9a51b8/lib/custom_traversal_policy.dart) makes vertical movement deterministic and the reorder path guards row/column edges. Takeaway: remote navigation should be tested as a stateful grid, not assumed correct from screen geometry. The reference is GPL-3.0, so only the behaviour was studied.
- **Tigris:** its [`TileAdapter`](https://github.com/lesnolie/Tigris/blob/7697955ec483/app/src/main/java/com/leslie/tvhome/TileAdapter.kt) combines a raised focused tile, a legible selected-app label and a motion cue, while the broader app layers on dock, settings and wallpaper systems. Takeaway: a selected app should be unmistakable, but animation and system-level features must stay restrained for a low-noise, low-permission experiment.
- **Projectivy / Monet / AT4K:** the local [style audit](research/launcher-style-audit-2026-09.md) documents banner cards, configurable focus/glow and tile treatments. These validate 16:9 art and a strong selection state, not a need to clone their feeds, controls or theming.

### Decisions implemented

- **Browse structure:** a large app spotlight, a horizontal favourites shelf, an optional local Recent shelf, then the complete app grid. Recent stays hidden until a non-favourite app is opened; it shows at most five launchable packages, newest first, without duplicating favourites. Left/Right browses within a row; Up/Down moves between rows. The spotlight follows focus before activation.
- **Selected-app hierarchy:** the hero follows the icon pack's banner lockup: package artwork leads on the left, followed by a category line and a large 30sp app name. It uses the installed TV banner when available and the installed app icon otherwise. The favourites shelf uses wide mark/category/name lockups built from each installed package icon and label; All apps remains a scannable icon grid.
- **Focus that survives edges:** controls keep the Night card dark and use a clear cyan outline plus modest 1.025–1.055 scale; focus is never signalled by colour alone. The shelf and grid allow focused tiles to lift without clipping. The app label and action are exposed as one spoken description.
- **Brand and distance:** the launcher now uses the pack's own generated Core Builds hex/diamond mark and banner, with the Brand Guide's Void `#04070F`, Night `#0D1117`, Panel Ink `#151923`, and Signal Cyan `#00D4FF`. The UI keeps bold sans app names, mono utility labels, safe margins, short copy, and high contrast. Installed package labels, icons, and banners remain the source of app identity; the launcher does not invent or redraw vendor marks.
- **Private-use clarity:** the running launcher uses installed app labels and package artwork, not fabricated vendor marks. Favourites and at most five recent package IDs stay in local `SharedPreferences`; history is filtered to launchable packages and is never synced. There is no network permission, online feed, ad surface, account, or Home-key interception.

### Follow-up launcher research: local-first patterns

On 2026-11-03 I reviewed four more public launcher projects through their READMEs and selected source at pinned repository snapshots. This was interaction research only; no code, artwork, or UI screenshots were copied into the prototype.

- [Lumen](https://github.com/thecristopher/lumen-launcher/tree/86af11cb6b6fba1aea22f7f0af1ae06a94ab8028) — MIT, commit `86af11cb6b6fba1aea22f7f0af1ae06a94ab8028`. Its small five-app dock and locally stored, deduplicated recent-app row support low-friction reuse. It also makes focus/scroll anchoring explicit so a focused card remains whole. Its EPG-backed Top Shelf adds TV-provider permission, which is unnecessary for this prototype.
- [Flix](https://github.com/HatriGt/flix-launcher/tree/22c45cad751ffcfacc6963abefaf6bf041fda080) — MIT, commit `22c45cad751ffcfacc6963abefaf6bf041fda080`. The app row/grid and category ideas are useful; its selected `RowRecyclerView` source also illustrates avoiding per-frame relayout during focus animation. Catalogues, accounts, and weather are outside this local launcher’s scope.
- [Aerial](https://github.com/pengchujin/aerial-tv/tree/83e904326867131148880286e5e39e8c3320b5b7) — MIT, commit `83e904326867131148880286e5e39e8c3320b5b7`. Its app shelf/grid and simple organization actions make a useful reference, while folders, hide/uninstall controls, a Control Center, and device-takeover behavior are larger scope than this private prototype.
- [HomeBoard](https://github.com/fplanque/HomeBoard/tree/8cc8551829f636da49ee977dabe4c5521f5a24af) — Apache-2.0, commit `8cc8551829f636da49ee977dabe4c5521f5a24af`. It shows a local-first app surface can be kept distinct from optional smart-home/camera integrations. Those integrations and provider-based media rows are intentionally not adopted.

The only new behavior carried forward is a conditional Recent shelf: after Android accepts an app launch intent, store its package ID locally; keep five unique launchable entries, newest first; hide pinned apps from this shelf; and show the shelf only when it has something useful. This avoids a feed, EPG access, network traffic, and a permanent extra row on first run. Full app discovery stays alphabetical and package-provided artwork remains the runtime source of truth.

The [offline HTML illustration](mockups/core-launcher-offline.html) is a single self-contained file generated by `python3 tools/build_launcher_offline_preview.py`. Inline CSS/JavaScript and embedded repository artwork make it usable without network access. Its sample catalog is illustrative; browser favorites/recents use browser-local storage and do not connect to the Android app. It is clearly disclosed as a design illustration, not an APK or device screenshot.

This direction follows Android's [TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system), [layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts), [immersive list](https://developer.android.com/design/ui/tv/guides/components/immersive-list), [typography](https://developer.android.com/design/ui/tv/guides/styles/typography), [colour guidance](https://developer.android.com/design/ui/tv/guides/foundations/color-on-tv), and [TalkBack evaluation](https://developer.android.com/training/tv/accessibility/talkback), already summarized in the repository's TV research. The Core Builds-inspired, visibly labelled design illustrations are in [`docs/mockups`](mockups/README.md); they are not APK or device screenshots, and their example app inventory is not a scan of a TV. Remaining evidence gate: test safe-area placement, actual focus routing, banner crop, TalkBack and focus animation on the target TV with its remote; source review is not a substitute for that pass.

## Implemented v0.1 boundary

- Native Android app, API 26 minimum, built against the suite's API 37 toolchain.
- HOME, standard launcher, and Leanback launcher entry points.
- App catalog from visible launcher activities; no network calls and no broad package permission.
- App launch, local favourites and a conditional last-five local Recent shelf, default-home settings handoff, clock/date, Core Builds icon-pack branding and a focus-driven app spotlight with installed-banner/icon fallback.
- Private debug application ID `tv.corebuilds.launcher.prototype.debug`; no public release or suite registry update.

Not included: recommendation channels, media playback, custom system wallpaper, search, account/cloud sync, weather, sports feeds, key remapping, accessibility-based Home interception, vendor-specific setup, or a public distribution path.

## Validation gate before wider use

1. Build and install the debug APK on physical hardware.
2. Verify default-HOME selection and recovery on each target device family.
3. Run the D-pad, Back, TalkBack, install/uninstall and pin-persistence checklist in [`launcher/README.md`](../launcher/README.md).
4. Only expand scope if private testers keep it selected as HOME and can complete the core actions without setup help.

No physical device was available here, and the sandbox also lacks a JDK/Android SDK. The app's Android build and all hardware behaviors must be verified on a machine with the required toolchain before installation.
