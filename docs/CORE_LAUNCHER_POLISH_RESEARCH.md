# Core Builds Launcher — polish, motion and market research

**Research date:** 2026-11-05  
**Scope:** A fresh desk benchmark of current Android TV / Google TV launchers, Android's TV and performance guidance, and a concrete 0.3 polish plan for the private Core Builds launcher prototype. No competitor code, artwork or branding was copied. This is not an install-and-use, latency, memory, TalkBack or device-compatibility benchmark; those claims still require the physical-TV record in [`launcher/README.md`](../launcher/README.md#required-physical-device-validation-merge-gate).

## Executive decision

The best launchers do not win by adding every possible row. They win on a short path from **wake → focused app → open**, on predictable D-pad behaviour, on legible state, and on returning from standby without work. Projectivy shows how deep app/artwork management and recoverable settings build trust; Monet 1.1.0 explicitly prioritises faster standby return, lower CPU load and smoother menus; FLauncher and AT4K show the value of a restrained app-first surface. Google's TV guidance keeps the immersive preview, safe margins and a visible focus state central.

Core Builds 0.3 therefore adopts:

1. **A single motion system** with short, interruptible transitions, a user-selectable motion level, and automatic respect for Android's system animator scale ("Remove animations"). No continuously animating wallpaper, ticker or idle loop.
2. **Smarter local ranking** of the existing Recently opened shelf: recency, frequency and time-of-day/day-type patterns learned from a bounded event list in launcher-private preferences. No account, telemetry, `UsageStatsManager` or network.
3. **Find an app**: a local, ranked search overlay with prefix, word-prefix, initials and subsequence matching. It is available from Customise and the remote's Search key.
4. **Fast organisation**: configurable All apps order (A–Z, most used, TV apps first, newest install), plus the existing card sizes, favourites, Move, hidden apps and per-app artwork.
5. **Settings that explain themselves**: each row shows its current value, and the motion/suggestion/sort pages use one-line explanations. Quick settings provide permission-free Android system handoffs.
6. **Recoverability**: versioned JSON backup/restore through Android's document picker, with a bounded read and a known-key allowlist. Imported image files stay on the TV; pack-resource references are portable.
7. **Bounded performance**: a byte-capped `LruCache` for rendered card art, one `PackageInfo` lookup per app, no repeated icon-pack parsing on an unchanged TV, no new permission, and no background service.

Deliberately deferred: recommendation/channel providers, Watch Next, video/Reddit wallpapers, weather/sports widgets, HDMI-input integrations, user profiles, PIN locks, custom key interception, accessibility-service Home overrides and cloud sync. They need vendor contracts, broad permissions, network access, continuous work, or device-specific behaviour and would weaken the local-first core.

## Source matrix

| Source | Evidence reviewed | What is worth taking | Boundary |
|---|---|---|---|
| [Projectivy 4.72 release notes](https://github.com/spocky/miproja1/releases/tag/4.72) | Official GitHub release, 2026-10-03: image/icon-pack browser with filtering, wallpaper-provider preview, browsable automatic backups, per-channel fetch limits, animated-icon loop limits, an explicit wallpaper-transition `NONE` value and a 50 MB image-cache cap. | Preview what a setting will do, make recovery obvious, bound media work, and let users turn transitions down or off. | Feeds, wallpaper providers, HDMI shortcuts, parental controls and optional accessibility shortcuts are a larger integration surface. |
| [Monet 1.1.0 release notes](https://github.com/Klevico/Monet-Launcher/releases/tag/v1.1.0) | Official GitHub release, 2026-10-24: all-apps panel, action dock, redesigned settings with current values, hide/reorder inputs, smoother settings/overlays, standby return reduced from about six seconds to two, lower CPU while navigating and idle, and explicitly limited wallpaper rotation. | Resume quickly, keep idle cost low, put actions near the app, show setting state, and make menu motion smooth rather than constant. | Glass/video wallpapers, media channels, weather, profiles, key interception and an optional accessibility service are outside this prototype's permission and idle-cost budget. |
| [Projectivy official site](https://projectivylauncher.com/) | Product page: ad-free layout, animated/static custom wallpapers, adaptive colours, icon packs, backups, inputs, Watch Next and optional Premium personalisation. | Ad-free, user-owned layout and clear art customisation are market expectations. | Premium art/video features and vendor inputs are not prerequisites for a fast app launcher. |
| [FLauncher project](https://gitlab.com/flauncher/flauncher) and [2026 market overview](https://www.flatpanelshd.com/guide.php?subaction=showfull&id=1792051531) | Open-source Flutter launcher with categories, manual order, wallpaper, hidden apps and sideloaded-app visibility; the round-up treats upstream FLauncher as an older, simpler baseline. | A low-noise app-first grid, manual organisation and sideloaded-app parity. | Upstream update cadence and third-party forks must not be conflated; do not infer current compatibility from a feature list. |
| [AT4K](https://play.google.com/store/apps/details?id=com.overdevs.at4k&hl=en_US), [Kyu](https://play.google.com/store/apps/details?id=com.overdevs.kyulauncher&hl=en), [Sideload Channel Launcher](https://play.google.com/store/apps/details?id=com.spocky.scl3) | 2026 listings describe favourites, folders, wallpapers, widgets, tabs, profiles, custom tiles and Watch Next. | Predictable favourites, direct app management and optional grouping help large app catalogues. | Widgets, content providers and profiles add state, permissions and OEM variance. Core Builds uses search and sorting instead of a second folder/tab hierarchy. |
| [Couchy launcher](https://github.com/conreo/couchy-launcher) | Official repository: one-screen, remote-first, local sections/layouts, hide controls and explicit JSON configuration save/load; network off by default. | Keep configuration local and make backup/restore an explicit user action. | GPL code and its configuration schema were not copied. |
| [Google TV "Your apps" redesign coverage](https://9to5google.com/2024/02/28/google-tv-your-apps-redesign/) | Reorder and add-apps controls at the end of the apps row. | Place Add and Move at the point of use rather than in a distant settings tree. | Google TV's recommendation feed is not part of this app-first home. |
| Android TV [navigation](https://developer.android.com/training/tv/get-started/navigation), [focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system), [layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts), [immersive list](https://developer.android.com/design/ui/tv/guides/components/immersive-list), [TalkBack evaluation](https://developer.android.com/training/tv/accessibility/talkback) | D-pad reachability, predictable Back, 1.025–1.1x focus scale with border/glow, 960 × 540 dp and 48/27 dp safe margins, scrimmed preview, and real screen-reader testing. | Retain explicit arrow routing, focus restoration, card ring/lift/glow, scrims and labelled controls. | A Robolectric render is not a physical remote or TalkBack pass. |
| Android [slow rendering](https://developer.android.com/topic/performance/issues/render), [GPU rendering inspection](https://developer.android.com/topic/performance/rendering/inspect-gpu-rendering), [hardware acceleration](https://developer.android.com/topic/performance/views/hardware-accel-views) | 16.67 ms frame budget; avoid large bitmap uploads, repeated bitmap mutation, deep view trees, overdraw and unnecessary translucent full-screen layers. | Render art once at card size, cache it with a byte cap, keep full-screen redraws to short focus transitions, and avoid continuous animation. | Frame-time claims need on-device GPU profiling; source changes are not a measured benchmark. |
| Android [`Settings.Global.ANIMATOR_DURATION_SCALE`](https://developer.android.com/reference/android/provider/Settings.Global#ANIMATOR_DURATION_SCALE) | System scaling factor for animator durations; `0` means animations end immediately and backs the platform's "Remove animations" behaviour. | Multiply every launcher duration by this scale and skip transitions at zero. | Some OEMs expose additional scale keys; the launcher uses the documented animator scale as the portable signal. |

## Motion specification

| Interaction | Default duration | Behaviour |
|---|---:|---|
| Focus lift and panel-row cue | 150 ms | Decelerating scale to 1.1x for cards and 1.06x for the Customise pill; full-width panel rows slide 4 dp instead of scaling. Interrupted animations start from the current position. |
| Info-area reveal | 170 ms | A short fade + 6 dp rise only when the focused item's text actually changes. |
| Row/scroll alignment | 220 ms | One animator per scrolling view; a new target cancels the old one. |
| Backdrop crossfade | 280 ms | Two static layers crossfade; no blur or shader is recomputed per frame. At reduced motion the transition is instant. |
| Panel/search entrance | 200 ms | Right-hand sheet slides in once. Closing is immediate so Back and focus restoration never wait for an animation. |

Motion levels: **Full** (1.0x), **Calm** (0.55x) and **Off** (0x). The effective duration is `base × level × system animator scale`. Nothing animates while the activity is paused, and no continuous animator is left running behind the screen. These values stay inside Android's 16.67 ms-per-frame guidance by changing transform/alpha properties and short crossfades rather than re-rendering bitmaps; the 150–280 ms numbers are transition lengths, not per-frame work.

## Feature decisions

### Adopted in 0.3

| Competitor pattern | Core Builds implementation | Why it stays within the contract |
|---|---|---|
| Projectivy's bounded cache and transition `NONE` | Byte-capped card-art `LruCache`, `MotionMode.OFF`, and system reduced-motion support | No full-resolution bitmaps and no persistent animation work. |
| Monet's current-value settings overview and smoother menus | Every Customise row shows a value; focused panel rows get a small, short translation; page changes preserve the focused row | No overlay service or always-on compositing. |
| Monet's All Apps panel / action dock | `KEYCODE_SEARCH` or Customise › Find an app opens a ranked local search; app options stay on long OK / Menu | No key interception and no global overlay. |
| Monet/FLauncher/AT4K organisation | All apps sort A–Z, Most used, TV apps first or Newest install | Uses launcher-private launch events, `tvOptimized` and `firstInstallTime` only. |
| Projectivy/Monet backups | Versioned JSON export/import through `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` | User-chosen file, 256 KB cap, known-key allowlist, no storage or Internet permission. |
| Projectivy/Monet quick controls | Customise › Quick settings opens Wi‑Fi, Bluetooth, display, sound, accessibility, apps and Home settings through public `Settings` actions | Permission-free handoffs; unavailable screens fall back safely. |
| Adaptive personalisation without tracking | Smart Suggestions score recency, frequency and matching time-of-day/day-type from the last 30 days of local launches | No usage-stats permission, account, network sync or analytics; the row can be switched off and reset clears history. |
| Projectivy's artwork previews | Existing per-app artwork picker retains live card thumbnails and one-press pack suggestions | Already local; copy is clarified, not expanded into a network gallery. |

### Explicitly deferred

| Pattern | Reason |
|---|---|
| Watch Next, channel providers, weather/sports/Reddit rows | Require provider contracts, network, freshness/error states and potentially vendor-specific permissions. |
| Animated/video/GIF wallpapers and live widgets | Continuous GPU/CPU and memory cost conflicts with the fast-return goal and needs a per-device performance budget. |
| HDMI/AV input renaming and switching | Vendor-specific activities and TV-input framework integration; a private prototype cannot promise parity across TVs. |
| Parental PINs, child profiles, app locks | Security-sensitive state, threat modelling and recovery UX deserve a separate release, not a UI polish pass. |
| Accessibility-service Home override / global key capture | Broad privilege and OEM-dependent behaviour. The launcher still hands off to Android's Home-app settings. |
| Remote-button remapping | Competes with app/game key handling and needs real-remote matrices before it is safe. |

## Copy and accessibility rules

- Use verbs for actions ("Find an app", "Back up layout", "Open network settings"), never internal setting names.
- Every setting row shows its current state on the right; selected choices get both a state label and a non-colour indicator.
- Hints name the actual keys: "OK to open", "Hold OK or Menu for options", "Back closes".
- Explanations stay one sentence and say where data lives: "Learns your routine on this TV".
- Focus is never colour alone: card scale + inner ring + soft outer ring + elevation; panel rows use a cyan border and a small horizontal shift.
- Search result rows and the input have explicit content descriptions; the decorative scrim and thumbnails are hidden from accessibility.
- All new text avoids technical jargon in the user-facing UI; technical detail belongs in this document and the README.

## Implementation map

| File | Responsibility |
|---|---|
| `launcher/app/src/main/java/tv/corebuilds/launcher/Motion.kt` | Motion levels, Android reduced-motion scale, focus/reveal/scroll/sheet animations. |
| `LauncherPreferences.kt` | New settings, bounded local launch events, smart ranking, usage counts, backup allowlist. |
| `LauncherApps.kt` | Byte-capped artwork cache, one package-info lookup, install time for sorting. |
| `HomeViews.kt` | Two-layer focus ring, motion-aware backdrop crossfade, shelf notes. |
| `SidePanel.kt` | Motion-aware entrance and panel focus cue. |
| `SearchPanel.kt` | Local ranked app search and remote-friendly result navigation. |
| `LauncherBackup.kt` | Versioned, bounded JSON layout export/import. |
| `LauncherActivity.kt` | Sort/search/quick settings/backup pages, motion configuration, info reveal, intent handoffs. |
| `strings.xml` | Rewritten user-facing copy and accessibility descriptions. |
| `docs/mockups/core-launcher-polish.html` | New interactive, offline 0.3 design illustration of motion, search, settings and local-data copy. The 0.1-era offline preview is retained as historical context. |
| `launcher/README.md`, `CHANGELOG.md`, `docs/mockups/README.md` | Version, implementation/test maps, physical-device checklist and private-prototype changelog. |

## Verification boundary

- Python source/manifest tests pin zero permissions, narrow `<queries>`, the off-main-thread scan, system animation scale, bounded local history/backup/art cache, Back behaviour, and the original offline preview's D-pad/self-contained contract. New polish-specific assertions cover the source boundaries and test names.
- Robolectric opens Home on API 30–35 and exercises remote navigation, panels, settings, custom artwork, focus clipping and text fit. `HomeScreenTest` now covers motion/sort/suggestion settings and the local search-to-launch flow; `ModelTest` covers polished settings round-trip, bounded backup restore and search ranking.
- CI compiles, lints and assembles the debug APK; local sandbox has no JDK/Android SDK or attached TV.
- **Not proven here:** 60 fps, standby-return time, CPU use, TalkBack announcements, IME behaviour, HOME-role routing, OEM settings availability or any claim of being "best on the market". Those require the physical-device matrix and on-device GPU profiling. The result of this pass is a stronger design and implementation candidate, not a measured market win.
