# Core Builds Launcher — prototype research and scope

**Research date:** 2026-11-04
**Status:** The private launcher prototype and its Kotlin/XML source are included in this PR; CI is configured for the debug build/lint. The [launcher README](../launcher/README.md#required-physical-device-validation-merge-gate) is the single source of truth for hardware-validation status, owner/date, and results. This sandbox has no local JDK/Android SDK or attached TV. The permission surface remains deliberately minimal pending the target-device definition.

## Recommendation

Prototype a narrow Core Builds home surface, not a full Google TV replacement. A generic app grid and favourites shelf are already common; the plausible differentiator is a fast, low-noise, local-first Core Builds home that complements the suite's icon pack, wallpapers, Core Line and Core Shift / Core Motion work.

The current deliverable is deliberately a private, side-loadable experiment. It is not in `suite.json`; it has a build/lint CI check plus a dispatch-only pre-release lane that attaches the debug APK for a `launcher-v*` tag (no store listing, no Downloader channel, no update feed, no release signing). It makes no promise that every manufacturer permits a third-party default launcher.

**First device result (2026-11-04):** all eight physical checks were reported PASS by the repository owner on a Chromecast with Google TV against the `f01b519` debug build. That attestation, its gaps (OS build, remote model, APK hash not captured) and the exact scope are recorded in the [launcher README](../launcher/README.md#required-physical-device-validation-merge-gate); it is one device, not a compatibility claim.

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

### Current-market audit: Projectivy, Monet, and peers (2026-11-04)

This desk benchmark checks official Play listings, official release notes/repositories, and one independent market overview. It is a feature and policy comparison, **not** an install-and-use, accessibility, latency, memory, or device-compatibility benchmark. Product pages are developer claims; version/update dates below are what the linked pages showed on review. This covers the most relevant current alternatives, not every regional/OEM launcher. No source code, artwork, or branding was copied.

| Launcher | Current evidence reviewed | Useful lesson for this prototype | Boundary / caveat |
|---|---|---|---|
| [Projectivy](https://play.google.com/store/apps/details?id=com.spocky.projengmenu&hl=en) | Play listing showed an update on 2026-11-01; official [4.72 release](https://github.com/spocky/miproja1/releases/tag/4.72) (2026-10-03) adds a restorable automatic-backup browser, wallpaper preview, image/icon browsing, per-channel fetch limits, and a 50 MB image-cache cap. | Treat app and artwork management as first-class; make preview states clear; bound media/resource use; make recovery discoverable. | Projectivy also offers wallpaper plugins/providers, feeds/channels, icon customization and optional accessibility shortcuts. Those add integrations and settings complexity beyond this offline app-first prototype. |
| [Monet](https://play.google.com/store/apps/details?id=com.klevico.monet&hl=en) | Official [1.1.0 notes](https://github.com/Klevico/Monet-Launcher/releases/tag/v1.1.0) (2026-10-24) cover an all-apps panel and action dock over apps, per-app key pass-through, hide/reorder inputs, a redesigned settings overview, and developer-reported faster standby return/lower CPU use. Its [repository README](https://github.com/Klevico/Monet-Launcher) confirms that repository distributes signed APKs; source is not included. | Keep app actions close to the app; explain current settings/state; prioritize resume and idle cost rather than visual effects alone. | Overlays, key interception, widgets, profiles, live wallpapers and media rows are a much larger feature/permission/maintenance surface. Release-note performance claims are not independently comparable measurements. |
| [AT4K](https://play.google.com/store/apps/details?id=com.overdevs.at4k&hl=en_US) | Its Play listing showed an update on 2026-10-26 and describes a favourites row, app grid, long-press reordering, folders, wallpapers, widgets, Watch Next and per-app featured programs. | A predictable favourite shelf and direct app management make common actions quick. | Top-shelf/provider content, widgets, optional accessibility shortcuts and extensive customization are not needed to improve the core app-launch loop. The listing's speed claims are not a controlled benchmark. |
| [Kyu](https://play.google.com/store/apps/details?id=com.overdevs.kyulauncher&hl=en) | Play listing changelog identifies v0.98 (2026-07-03): editable tabs, themes, wallpaper picker, improved home detection and a Live tab; its description also lists folders, TV inputs, a watch tab and a to-do list. | Clear destinations and remote-friendly grouping can make an app-heavy screen easier to scan. | Watch/EPG, TV inputs, optional accessibility shortcuts and the larger tab system require separate scope and real-device validation. |
| [Couchy](https://github.com/conreo/couchy-launcher) | Official repository README/changelog identifies v1.0.6 (2026-08-01), GPL-3.0: one-screen remote-first UI, local sections/layouts, hide controls and JSON configuration save/load; network use is off by default. | Preserve a fast, local-first default and make any future configuration backup explicit and user-controlled. | Its wallpaper/layout options and config-import model are not automatically a fit for the branded, single-home-surface prototype; GPL code was not copied. |
| [FLauncher](https://play.google.com/store/apps/details?id=me.efesser.flauncher&hl=en) | The original Play listing describes categories, manual app ordering, App info/uninstall and wallpaper support, but still reports its last update as 2023-04-21. The independent [2026 market overview](https://www.flatpanelshd.com/guide.php?subaction=showfull&id=1792051531) likewise treats the upstream project as an older, less-polished baseline; community forks are separate products. | A clean, app-first interface and direct app management remain a useful counterpoint to feed-heavy products. | Do not infer current upstream maintenance from a fork or from feature similarity. |

The market converges on app discovery, favourites, organization, remote actions and optional content/customization. The current Core Builds differentiator should remain a restrained local-first surface, not a feature-count contest. Candidate ideas are accepted only when they improve a common task without new network access, broad package visibility, background work, Home interception, or a brittle app-provider contract.

#### Adopted in this pass

Long-press an app tile for a small action menu: **add/remove favourite** using the existing local preference and **App info** through Android Settings. Monet and Projectivy make app actions contextual; FLauncher and Couchy also document app-level menus. This is a bounded usability improvement over sending the user to a separate favourite editor, needs no new permission or network call, and uses the existing foreground refresh when returning from Settings. The tile's accessibility description advertises the long-press action, and it is added to the physical D-pad/TalkBack test procedure. Uninstall and force-stop actions are intentionally not added.

#### Deferred or rejected

- **Manual order, folders, hidden-app management and custom categories:** common and potentially valuable, but they introduce new persisted ordering/visibility semantics and migration/focus cases. The existing favourites editor and alphabetical full grid remain stable in this pass; revisit as a separately scoped, device-tested change rather than combining several data-model changes.
- **Backups/import-export:** useful, especially given Projectivy and Monet's release fixes, but introduce file handling, restore validation and data-loss cases. No backup format is added until the local preference model is stable.
- **Watch Next/EPG/recommendation rows, weather/sports, widgets, wallpaper providers/video, TV inputs, user profiles, or all-apps overlays over other apps:** these add network/provider contracts, foreground/idle work, permissions, input interception, or OEM-specific behavior; they do not improve the current no-feed app launch path enough to justify that cost.
- **Search, HOME interception, key remapping, broad package visibility and accessibility-service shortcuts:** remain out of scope under the existing prototype constraints. The launcher continues to hand off to Android's Home settings and query only launchable activities.

A 2026 independent [FlatpanelsHD market overview](https://www.flatpanelshd.com/guide.php?subaction=showfull&id=1792051531) helped identify the active alternatives above; it is context, not a technical benchmark. For real speed, idle-CPU, PSS, resume, D-pad and TalkBack comparisons, install competing launchers and this exact prototype build on the same named target and repeat the same tasks. No such hands-on comparison has been performed here.

This suite already targets [Projectivy Launcher](apps/icon-pack.md) with its icon pack and wallpaper/provider integrations, and the repo's [Monet research](MONET_LAUNCHER.md) records that integration boundary. Public code/UI references remain useful for interaction patterns: [nielsvanvelzen/tv-launcher](https://github.com/nielsvanvelzen/tv-launcher) calls itself experimental; [rharter/Android-TV-Launcher](https://github.com/rharter/Android-TV-Launcher) documents favourites, channels and all-apps; and [JackyAndroid/AndroidTVLauncher](https://github.com/JackyAndroid/AndroidTVLauncher) describes a Leanback-style app list and media features. None changes the no-feed scope above.

## UI design research and treatment

The visual pass combines the repository's [Core Builds brand guide](BRAND-GUIDE.md), [icon pack](apps/icon-pack.md), [Android TV UX research](ANDROID_TV_UX_RESEARCH.md), and [launcher-style audit](research/launcher-style-audit-2026-09.md). I also reviewed public source/UI references on 2026-11-03: [Plume](https://github.com/aradhyas8/Plume_Launcher) (GPL-3.0; commit `b18b15f58af8`), [LTvLauncher](https://github.com/leanbitlab-org/LtvLauncher) (GPL-3.0 FLauncher fork; commit `64247d9a51b8`), and [Tigris](https://github.com/lesnolie/Tigris) (MIT; commit `7697955ec483`). The comparison was used for interaction patterns only; no code or branded assets were copied. Visual references reviewed: [Plume dark home](https://github.com/aradhyas8/Plume_Launcher/blob/b18b15f58af8/docs/home-dark.jpg), [LTv home](https://github.com/leanbitlab-org/LtvLauncher/blob/64247d9a51b8/docs/images/screenshot_1.png), and [Tigris home](https://github.com/lesnolie/Tigris/blob/7697955ec483/docs/screenshots/home.jpg).

### What the comparison says

- **Plume:** its README keeps the launcher job focused on apps and recently watched items; app-name-on-focus and icon/banner choices are optional. Its [`SnapScrollView`](https://github.com/aradhyas8/Plume_Launcher/blob/b18b15f58af8/app/src/main/java/dev/plume/SnapScrollView.kt) lets focused cards lift beyond the row's child clip and scrolls only enough to bring the whole item into view. Takeaway: make app identity readable on focus and never clip the focused card. A media/Watch Next row is not adopted here because it would add content permissions and broaden this private prototype.
- **LTvLauncher / FLauncher:** [`AppsGrid`](https://github.com/leanbitlab-org/LtvLauncher/blob/64247d9a51b8/lib/widgets/apps_grid.dart) renders 16:9 cards; its [`RowByRowTraversalPolicy`](https://github.com/leanbitlab-org/LTvLauncher/blob/64247d9a51b8/lib/custom_traversal_policy.dart) makes vertical movement deterministic and the reorder path guards row/column edges. Takeaway: remote navigation should be tested as a stateful grid, not assumed correct from screen geometry. The reference is GPL-3.0, so only the behaviour was studied.
- **Tigris:** its [`TileAdapter`](https://github.com/lesnolie/Tigris/blob/7697955ec483/app/src/main/java/com/leslie/tvhome/TileAdapter.kt) combines a raised focused tile, a legible selected-app label and a motion cue, while the broader app layers on dock, settings and wallpaper systems. Takeaway: a selected app should be unmistakable, but animation and system-level features must stay restrained for a low-noise, low-permission experiment.
- **Projectivy / Monet / AT4K:** the local [style audit](research/launcher-style-audit-2026-09.md) documents banner cards, configurable focus/glow and tile treatments; the current [market audit above](#current-market-audit-projectivy-monet-and-peers-2026-11-04) adds their latest app-action, settings, backup and performance priorities. These support clear app actions and visible selection, not copying their feeds, overlays, wallpapers or theming systems.

### Decisions implemented

- **Browse structure:** a large app spotlight, a horizontal favourites shelf, an optional local Recent shelf, then the complete app grid. Recent stays hidden until a non-favourite app is opened; it shows at most five launchable packages, newest first, without duplicating favourites. Left/Right browses within a row; Up/Down moves between rows. The spotlight follows focus before activation.
- **Selected-app hierarchy:** the hero follows the icon pack's banner lockup: package artwork leads on the left, followed by a category line and a large 30sp app name. It uses the installed TV banner when available and the installed app icon otherwise. The favourites shelf uses wide mark/category/name lockups built from each installed package icon and label; All apps remains a scannable icon grid.
- **Focus that survives edges:** controls keep the Night card dark and use a clear cyan outline plus modest 1.025–1.055 scale; focus is never signalled by colour alone. The shelf and grid allow focused tiles to lift without clipping. The app label, launch action, and long-press hint are exposed as one spoken description.
- **Contextual app actions:** long-press a tile to open the D-pad/TalkBack-navigable menu, toggle its local favourite state, or open Android's App info screen. Returning from App info uses the existing foreground catalog refresh; no new permission, uninstall action, or network behavior is added.
- **Brand and distance:** the launcher now uses the pack's own generated Core Builds hex/diamond mark and banner, with the Brand Guide's Void `#04070F`, Night `#0D1117`, Panel Ink `#151923`, and Signal Cyan `#00D4FF`. The UI keeps bold sans app names, mono utility labels, safe margins, short copy, and high contrast. Installed package labels, icons, and banners remain the source of app identity; the launcher does not invent or redraw vendor marks.
- **Private-use clarity:** the running launcher uses installed app labels and package artwork, not fabricated vendor marks. Favourites and at most five recent package IDs stay in local `SharedPreferences`; history is filtered to launchable packages and is never synced. The app-action menu is local and hands App info to Android Settings. There is no network permission, online feed, ad surface, account, or Home-key interception.

### Follow-up launcher research: local-first patterns

On 2026-11-03 I reviewed four more public launcher projects through their READMEs and selected source at pinned repository snapshots. This was interaction research only; no code, artwork, or UI screenshots were copied into the prototype.

- [Lumen](https://github.com/thecristopher/lumen-launcher/tree/86af11cb6b6fba1aea22f7f0af1ae06a94ab8028) — MIT, commit `86af11cb6b6fba1aea22f7f0af1ae06a94ab8028`. Its small five-app dock and locally stored, deduplicated recent-app row support low-friction reuse. It also makes focus/scroll anchoring explicit so a focused card remains whole. Its EPG-backed Top Shelf adds TV-provider permission, which is unnecessary for this prototype.
- [Flix](https://github.com/HatriGt/flix-launcher/tree/22c45cad751ffcfacc6963abefaf6bf041fda080) — MIT, commit `22c45cad751ffcfacc6963abefaf6bf041fda080`. The app row/grid and category ideas are useful; its selected `RowRecyclerView` source also illustrates avoiding per-frame relayout during focus animation. Catalogues, accounts, and weather are outside this local launcher’s scope.
- [Aerial](https://github.com/pengchujin/aerial-tv/tree/83e904326867131148880286e5e39e8c3320b5b7) — MIT, commit `83e904326867131148880286e5e39e8c3320b5b7`. Its app shelf/grid and simple organization actions make a useful reference, while folders, hide/uninstall controls, a Control Center, and device-takeover behavior are larger scope than this private prototype.
- [HomeBoard](https://github.com/fplanque/HomeBoard/tree/8cc8551829f636da49ee977dabe4c5521f5a24af) — Apache-2.0, commit `8cc8551829f636da49ee977dabe4c5521f5a24af`. It shows a local-first app surface can be kept distinct from optional smart-home/camera integrations. Those integrations and provider-based media rows are intentionally not adopted.

The only new behavior carried forward is a conditional Recent shelf: after Android accepts an app launch intent, store its package ID locally; keep five unique launchable entries, newest first; hide pinned apps from this shelf; and show the shelf only when it has something useful. This avoids a feed, EPG access, network traffic, and a permanent extra row on first run. Full app discovery stays alphabetical and package-provided artwork remains the runtime source of truth.

The [offline HTML illustration](mockups/core-launcher-offline.html) is a single self-contained file generated by `python3 tools/build_launcher_offline_preview.py`. Inline CSS/JavaScript and embedded repository artwork make it usable without network access. Its sample catalog is illustrative; browser favorites/recents use browser-local storage and do not connect to the Android app. It does not emulate the new native long-press actions, Android App info handoff, or TalkBack, and is clearly disclosed as a design illustration, not an APK or device screenshot.

## Comparative audit: lightweight launchers and what to improve

Reviewed additional source snapshots on 2026-11-03. Size, memory, and coverage figures below are project-author claims unless explicitly tied to source inspection; they are not a common benchmark and are not directly comparable. No code or artwork was copied.

### Strong patterns worth learning from

- [Luncher](https://github.com/CVasilakis/luncher/tree/89e7939e1a00dc181e036d23799feb1fc888e392) — MIT, commit `89e7939e1a00dc181e036d23799feb1fc888e392`. Its README advertises Android TV 5.1+, roughly 310 KB, no permissions, and no background services. Its remote-first favorites support reorder/hide/rename; it keeps wallpaper and appearance optional. Best lesson: customization should not require an account or a permanently running service.
- [LightLauncher](https://github.com/shaxaydev/lightlauncher/tree/355d45216003a4cd91c1f4f52d4da5c268f27b2c) — MIT, commit `355d45216003a4cd91c1f4f52d4da5c268f27b2c`. It uses Android Views, has a favorites ribbon plus a separate all-apps view, keeps focus through list rebuilds, and documents bounded image caches with no idle animation or background service. Its README reports testing on one Android TV 9 Xiaomi model, not broad device coverage.
- [TV Home](https://github.com/LHesiquio/TV-Home/tree/eb66e0c0000d62e793baecd9c84310a626b61f9e) — MIT, commit `eb66e0c0000d62e793baecd9c84310a626b61f9e`. Its README describes a one-activity Java surface, app and TV-input rows, focus memory, live install/uninstall refresh, and author-reported 35 KB / ~22 MB figures across three tested devices. It also says it requests `QUERY_ALL_PACKAGES`; that is a deliberate coverage trade-off, not a requirement for listing launchable apps with narrow intent queries.
- [Czysty Launcher](https://github.com/PiotrKielis/Smart-TV-Launcher/tree/c41a24376ecce3ce4e1c4aece84ee04fbb62f86c) — repository commit `c41a24376ecce3ce4e1c4aece84ee04fbb62f86c`; GitHub reports no SPDX licence, so treat it as observation-only. Its README reports a Views-based, dependency-light launcher measured on a 1 GB stick, with app ordering, input controls and a screensaver. The inspected manifest includes `INTERNET`, `ACCESS_NETWORK_STATE`, and `KILL_BACKGROUND_PROCESSES` for its broader weather/booster feature set. That is more surface than the Core Builds home needs.
- [Glass Launcher](https://github.com/namillis/GlassLauncherTV/tree/c52c2cdedf9e295b53acbc05bf33e5e7017d7d0f) — commit `c52c2cdedf9e295b53acbc05bf33e5e7017d7d0f`, PolyForm Noncommercial 1.0.0. It keeps a favorites row and app grid simple while offering backup and deep customization. Its optional local-ADB Home takeover and photo-folder slideshow bring extra capability and risk; its license also rules out copying into this project.
- [dumbtv](https://github.com/humblemuzzu/dumbtv/tree/755991b17a66e753e82f7252368c8850c08fdc71) — MIT, commit `755991b17a66e753e82f7252368c8850c08fdc71`. The launcher README claims a 54 KB, no-permission home app, while the surrounding project makes ADB changes explicit and reversible. The useful lesson is consent and a tested restore path; its device-specific debloating scripts are not appropriate for this launcher.

Together with Lumen, Flix, and Aerial above, the strongest repeatable ideas are: one obvious launch surface; favorites you can order; a distinct complete app list; legible, stable D-pad focus; local state; and no permanent work while idle. Glass, blur, weather, streaming catalogs, custom wallpaper pipelines, app-killing, and system takeover are optional features—not proof of a better launcher.

### Audit of this Core Builds prototype

- **Already lean in permissions and dependencies:** the manifest declares no `uses-permission`, the Gradle app has no runtime dependency declarations, and app discovery asks only for `MAIN` + `LEANBACK_LAUNCHER` and `MAIN` + `LAUNCHER`. That covers packages which actually expose a TV or standard launch activity. It intentionally does not reveal hidden/system packages that have no user-launchable activity. The local Core Builds package list only seeds favourites when those apps are installed; runtime artwork still comes from each installed package.
- **The real performance question is not APK bytes alone:** the project has no external UI framework, background service, network client, or large app-art bundle; its two brand WebPs total about 9 KB. However, `LauncherAppCatalog.load()` currently loads each candidate icon and TV banner, and the non-virtualized `GridLayout` builds every app tile at once. That is the clearest source-level memory/startup risk to measure. A follow-up should load hero banners only when needed, bound icon retention, and consider view recycling only if measurements justify the added complexity/dependency.
- **Personalization is behind the references:** favorites are currently alphabetized and can be added/removed, but not reordered or hidden. A locally persisted order and a deliberate hide action would be a useful next feature with no new permission. Keep this to small preference state; do not add a settings database or framework for it.
- **The home surface can be shorter:** the current page scrolls from spotlight and favorites through Recents into the full grid. A Home shelf with a clear All Apps destination (Down/OK) would feel calmer and avoid constructing a long list at first focus. Keep Recents conditional; do not add a permanent feed.
- **No reliable size/RAM claim yet:** the APK was not built in this sandbox and there is no target TV attached. Report release APK size, cold-start time, idle CPU and PSS only after measuring the same build on a specified low-memory device; published numbers from different devices/builds are not comparable.

### Coverage and permission policy

“Full coverage” should mean every **user-launchable** app surfaced by the Android TV or standard launcher activity queries, on an explicitly supported device/API matrix—not every installed package and not a guarantee that every OEM honors the third-party HOME role. Since 0.2 the supported range is Android 11 (API 30) to the latest; 0.1 was API 26 (Android 8). Fire OS based on Android may run the APK, but OEM Home overrides vary; Vega OS is not an Android runtime.

| Need | Recommended approach | Permission cost |
|---|---|---|
| Discover launchable TV and sideloaded apps | Keep the two narrow manifest intent queries already present | No runtime permission; no `QUERY_ALL_PACKAGES` |
| Show hidden or non-launchable packages | Do not include in the base launcher; they cannot necessarily be opened | Broad package visibility adds policy/privacy burden and is not needed for normal app coverage |
| Set the default HOME | Hand off to Android Home-app settings, as now | No special permission; OEM selection may still block it |
| Let a user choose a local wallpaper later | Use Android's photo picker / Storage Access Framework | Per-item URI grant; avoid broad storage permission |
| Weather, remote artwork or recommendations | Keep out of the private lightweight v1 | Requires `INTERNET` and ongoing network behavior |
| Intercept HOME, remap keys, disable stock apps | Do not put in the base product | Accessibility, ADB or privileged/OEM-specific setup; device-specific failure/recovery risk |
| HDMI / TV inputs | Optional follow-up only after device testing | TV Input Framework exposure and permissions vary by vendor |

### Recommended next sequence

1. Agree the supported device/API list and what “full coverage” means before changing `minSdk` or package visibility.
2. Keep the current zero-permission, no-network, framework-Views baseline; measure release APK, cold start, idle CPU and PSS on at least one low-RAM TV.
3. If profiling confirms it, lazily load banners/icons and virtualize only the app surface that needs it.
4. After physical sign-off and tester feedback, decide whether manual favourite order or hiding apps merits a separate local-preference design; if approved, migrate state deliberately and repeat D-pad/TalkBack tests.
5. Grow Core Builds integration through explicit installed-package/launcher-entry contracts and first-party shortcuts—not another content feed or a cross-app permission.

This direction follows Android's [TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system), [layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts), [immersive list](https://developer.android.com/design/ui/tv/guides/components/immersive-list), [typography](https://developer.android.com/design/ui/tv/guides/styles/typography), [colour guidance](https://developer.android.com/design/ui/tv/guides/foundations/color-on-tv), and [TalkBack evaluation](https://developer.android.com/training/tv/accessibility/talkback), already summarized in the repository's TV research. The Core Builds-inspired, visibly labelled design illustrations are in [`docs/mockups`](mockups/README.md); they are not APK or device screenshots, and their example app inventory is not a scan of a TV. Remaining evidence gate: test safe-area placement, actual focus routing, banner crop, TalkBack and focus animation on the target TV with its remote; source review is not a substitute for that pass.

## Home screen survey and the 0.2 direction (2026-11-05)

The owner set three goals for 0.2: support Android 11 to the latest, clean up the UI, and make customisation better, in a polished style taken from how other launchers and home screens work. This desk survey covered official design guidance and published descriptions of the main home screens. It is a feature and pattern comparison, not a hands-on benchmark, and no code or artwork was copied.

| Source | What it shows | Taken into 0.2 |
|---|---|---|
| Android TV [layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts) | Design at 960 × 540 dp; keep content inside 48 dp side and 27 dp top/bottom overscan margins; let backgrounds bleed full screen; align the focus path with the hierarchy. | The 48/27 dp safe margins; the background runs edge to edge; rows read top to bottom in order. |
| Android TV [focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system) | Focus is the key TV state; default scales of 1.025, 1.05 and 1.1 by element size; glow for cards. | Cards lift 1.1x with a cyan ring and a cyan shadow (the glow); the Customise pill lifts 1.06x; full-width panel rows get a ring without scaling. |
| Android TV [immersive list](https://developer.android.com/design/ui/tv/guides/components/immersive-list) (already in [`ANDROID_TV_UX_RESEARCH.md`](ANDROID_TV_UX_RESEARCH.md)) | A large preview that updates with the focused card, a 16:9 background, scrimmed information. | The whole home: the focused app's art as the background, a scrimmed name and hint, shelves below. |
| Google TV's 2024 "Your apps" redesign ([9to5Google](https://9to5google.com/2024/02/28/google-tv-your-apps-redesign/), [PhoneArena](https://www.phonearena.com/news/google-tv-gets-design-tweak-that-makes-your-favorite-apps-more-accessible_id155821), [AFTVnews](https://www.aftvnews.com/?p=42535)) | Reorder and add-apps controls at the end of the apps row. | The Add card at the end of Favourites, and Move from the options panel. |
| Projectivy ([release notes via APKMirror](https://www.apkmirror.com/apk/spocky/projectivy-launcher-android-tv), [SourceForge listing](https://sourceforge.net/app/projectivy-launcher/), [Digitec overview](https://www.digitec.ch/en/page/a-tv-home-screen-without-ads-projectivy-launcher-spices-up-android-and-google-tv-38294); see also the [style audit](research/launcher-style-audit-2026-09.md)) | Reorder and hide apps, categories, card sizes, icon packs, wallpaper that reacts to the focused card, a wallpaper shadow-opacity setting. | Hide apps, card sizes, icon packs, the focused-app background and its dim setting. Categories, plugins and animated wallpapers were left out (below). |
| FLauncher ([XDA](https://www.xda-developers.com/flauncher-android-tv/)) | Categories, manual reordering within them, hidden apps, its own wallpaper handling because some TVs lack `WallpaperManager`. | Manual order and hidden apps; the background is drawn by the launcher itself, not `WallpaperManager`, for the same reason. |
| tvOS top shelf (summarised in [Reda Lemeden's HIG notes](https://redalemeden.com/blog/2015/tvos-hig-tldr/) and [tvOS-guidelines](https://github.com/BasThomas/tvOS-guidelines)) | Focused content in the top row drives a showcase above the apps; focus is shown by lift and depth. | The info area that names the focused app above the shelves. |
| Icon-pack contract ([Lawnchair developer docs](https://docs.lawnchair.app/developers/concepts/icon-packs)) | Packs are found by the `org.adw.launcher.THEMES` intent and ship `res/xml/appfilter.xml` (or `res/raw` / `assets`) mapping components to drawables. | Customise › Artwork reads any such pack; the [Core Builds Icon Pack](apps/icon-pack.md) maps every app to a 16:9 banner, so it is the default when installed. |
| Launcher round-ups ([MakeTechEasier](https://maketecheasier.com/best-android-tv-launchers/), [Technical Ustad](https://technicalustad.com/android-tv-launchers/)) | Projectivy as the general pick; ATV Launcher for tile size, row count and transparency control. | Card size as a first-class setting. |

### Decisions

- **Style: immersive list.** The pattern Android's own guidance and Google TV share, with Projectivy-style focused-app wallpaper and tvOS-style naming above the shelves. It replaces 0.1's boxed spotlight panel, which put a bordered box between the user and the apps.
- **Customisation that changes common tasks:** favourite order and Move, hidden apps, card size, background and dim, recently-opened and clock toggles, and icon packs. Each is local preference state with no new permission. These were deferred in 0.1 ("Manual order, folders, hidden-app management and custom categories") pending the data-model work 0.2 does: ordered favourites with a one-time migration, tested in `ModelTest`.
- **Icon packs through the open contract only.** Adds one `<queries>` intent (`org.adw.launcher.THEMES`) and no permission. The Core Builds banners pack is the default when installed; any other pack can be chosen; apps a pack does not cover keep their own art.
- **Still out:** categories and folders (a second navigation level for a private prototype), wallpaper plugins and video wallpapers (provider contracts and idle cost), feeds, search, inputs and widgets (network, providers or vendor variance), and anything that intercepts Home.
- **Android 11 floor.** `minSdk` moves from 26 to 30 as the owner asked; the pre-11 system-bar path is removed. Robolectric opens Home on API 30–35 on every launcher PR.

## Implemented v0.1 boundary

- Native Android app, API 26 minimum, built against the suite's API 37 toolchain.
- HOME, standard launcher, and Leanback launcher entry points.
- App catalog from visible launcher activities; no network calls and no broad package permission.
- App launch, local favourites and a conditional last-five local Recent shelf; long-press app actions for favourite toggling and Android App info; default-home settings handoff, clock/date, Core Builds icon-pack branding and a focus-driven app spotlight with installed-banner/icon fallback.
- Private debug application ID `tv.corebuilds.launcher.prototype.debug`; no public release or suite registry update.

The implementation is in [`LauncherActivity.kt`](../launcher/app/src/main/java/tv/corebuilds/launcher/LauncherActivity.kt), [`LauncherApps.kt`](../launcher/app/src/main/java/tv/corebuilds/launcher/LauncherApps.kt), [`LauncherPreferences.kt`](../launcher/app/src/main/java/tv/corebuilds/launcher/LauncherPreferences.kt), [`LauncherUi.kt`](../launcher/app/src/main/java/tv/corebuilds/launcher/LauncherUi.kt), [`ManageFavoritesActivity.kt`](../launcher/app/src/main/java/tv/corebuilds/launcher/ManageFavoritesActivity.kt), and [`AndroidManifest.xml`](../launcher/app/src/main/AndroidManifest.xml). [`tests/test_gradle_envelope.py`](../tests/test_gradle_envelope.py) has source/manifest contract assertions; those checks are not Android UI automation or physical-device evidence. The launcher README contains the [required hardware-validation record](../launcher/README.md#required-physical-device-validation-merge-gate).

Not included: recommendation channels, media playback, custom system wallpaper, search, account/cloud sync, weather, sports feeds, key remapping, accessibility-based Home interception, vendor-specific setup, or any public distribution path — the only artifact lane is a dispatch-only private pre-release of the debug APK.

### KDoc coverage for the private prototype

The 80% function-level KDoc threshold reported by CodeRabbit is explicitly waived for v0.1: this is a private application, not a reusable Kotlin library, and it exports no stable library API. Android entry-point classes are manifest components; short private view-building helpers are intentionally named by their UI role, while non-obvious lifecycle and focus behavior is documented at the relevant code and in the launcher README. Repository CI does not enforce a KDoc percentage. Revisit broader function-level documentation before exposing a reusable API or adding a public distribution lane. This documentation waiver does not waive the required physical-device validation gate below.

## Validation gate before merge and wider use

1. Require a green CI debug build/lint; it is separate from UX sign-off.
2. Install the debug APK on the declared physical target device(s) and record vendor/model, Android/Google TV version, API level, build/PR commit, tester, and date.
3. Complete every D-pad, HOME-role, Back, TalkBack, persistence/focus, and artwork check in the [launcher README validation record](../launcher/README.md#required-physical-device-validation-merge-gate). No physical-device result is claimed until that table is filled with PASS/FAIL evidence.
4. Only expand scope if private testers keep it selected as HOME and can complete the core actions without setup help.

This environment has no attached physical TV and no local JDK/Android SDK. The configured CI build/lint can verify compilation; it cannot validate device-specific HOME selection, remote focus, or TalkBack. See the launcher README for the authoritative hardware-validation status and sign-off record.
