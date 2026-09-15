# Next icon tranche: demand, mapping, and identity research

**Research date:** 2026-09-15 (Australia/Sydney)  
**Catalog measured:** `tools/catalog.json` on branch `arena/01a0a36a-corebuildsapps`  
**Decision boundary:** research only. This document does not authorize v1.8.20, artwork changes, mappings, or a round-number admission target.

## Executive finding

The pack does **not** have a package/component coverage deficit against Projectivy Icon Pack 1.1.9. It has an identity-quality and evidence-quality backlog.

Core Builds currently has 940 icons, including 1,123 distinct normalized package/activity identities. Projectivy 1.1.9 has 870 automatically mapped drawables and 953 distinct normalized identities. Every one of those 953 identities already exists verbatim in Core Builds. Core Builds adds 170 identities beyond that reference. The apparent name-list gaps are therefore mostly manual-only icons, aliases, renamed apps, hardware shortcuts, or names that Core labels differently.

The strongest next tranche is:

1. correct two identity/name collisions;
2. add a small number of directly requested, TV-proven apps only after activity evidence exists;
3. upgrade at least 22 high-visibility generic constructions to move below 55% generic at the current catalog size; and
4. clear or prune the 83 honestly marked unverified mappings by evidence class, rather than blindly blessing them.

## 1. Reproducible baseline

| Measure | Result |
|---|---:|
| Icons | 940 |
| Bespoke marks | 402 |
| Generic constructions | 538 (57.23%) |
| Unverified component rows | 83 |
| Core normalized component identities | 1,123 |
| Core component packages | 976 |
| Projectivy 1.1.9 mapped rows | 959 |
| Projectivy distinct normalized identities | 953 |
| Projectivy mapped packages | 885 |
| Projectivy automatically mapped drawables | 870 |
| Exact identity intersection | **953** |
| Projectivy identities missing in Core | **0** |
| Core identities not in Projectivy | 170 |

Normalization expands an activity beginning with `.` against its package and compares `package/fully.qualified.Activity`. It does not use icon names.

At 940 icons, below 55% generic requires no more than 516 generic icons. Converting **22 existing generic entries** is sufficient; adding filler is neither necessary nor desirable.

## 2. Evidence standard

### Admission evidence, strongest first

1. The current app's inspected, signature-consistent Android TV APK/AAB manifest.
2. The developer's source manifest or official TV-specific Play listing.
3. A current Play listing plus independent package corroboration; activity remains unverified.
4. A known-good `cmd package resolve-activity --brief` or `dumpsys package` capture from the target TV device.
5. Another maintained icon pack's mapping, clearly labeled as inherited rather than device-verified.
6. Community posts and download sites: useful for demand and leads, never sufficient alone to invent an activity.

### Visual evidence, strongest first

1. Official public brand kit or developer repository assets.
2. Current Play app icon/banner from the official listing.
3. Current official service site/social avatar.
4. Reproducible geometric observation from more than one current source.

Do not paste source art. Record the mark's identifying geometry, then redraw it in the Core Builds monochrome/transparent style. Wordmark-only brands require special restraint: a generic monogram can be less faithful than a compact wordmark or an honest category symbol.

## 3. Projectivy 1.1.9 comparison

### 3.1 Package/component result

`tools/reference/projectivy-1.1.9-appfilter.xml` is the full decompiled 1.1.9 mapping reference. The APK's `assets/appfilter.xml` is only a 1.4 KB launcher-template sample and is not the compiled production mapping. Comparing against that asset would have produced a false result.

The full comparison found:

- 953/953 Projectivy normalized identities already in Core;
- 885/885 Projectivy packages already in Core;
- no shared package where the two packs have disjoint activities;
- 170 additional normalized identities in Core.

This invalidates the preliminary 308-name “gap” list as an admission queue.

### 3.2 Important alias and collision results

| Public/requested name | Component truth | Core state | Conclusion |
|---|---|---|---|
| Beacon Game Launcher | `com.radikal.gamelauncher/com.radikal.gamelauncher.MainActivity` | Present as **Game Launcher**, `gamelauncher_mark` | Not missing. Rename display metadata to Beacon Game Launcher after visual revalidation; do not add a duplicate. |
| ITVX | package `air.ITVMobilePlayer`; known TV activity `com.itv.tenft.itvhub.MainActivity` | Present as **ITV Hub**, bespoke `itv_hub` | Not missing. It is stale naming/identity artwork and should become ITVX after confirming the current TV build still exports the same activity. |
| XCIPTV | `com.nathnetwork.xciptv/...SplashVideoActivity` | Present as **XC IPTV** | Not missing; spelling variant only. |
| France.tv | `fr.francetv.pluzz/fr.francetv.androidtv.main.MainActivity` | Present as **Francetv**, generic `app_F` | Not missing; a high-priority identity upgrade. |
| Sideload Launcher | `eu.chainfire.tv.sideloadlauncher/...MainActivity` | Correctly mapped, but shares an icon entry with SideApps | Split required. |
| SideApps | `net.easyjoin.applauncher/net.easyjoin.applauncher.activity.MainActivityTV` | Incorrectly folded into Sideload Launcher | Real separate app by EasyJoin; give its own icon/name and retain the component. |

The Sideload Launcher complaint is substantiated. Chainfire's Sideload Launcher and EasyJoin's SideApps are separate products with separate packages and current public identities. They must not share one catalog identity merely because both expose sideloaded apps.

### 3.3 Projectivy 1.1.9's latest 20 icons

Core already represents 11 of the 20 latest-release names or direct equivalents: Debrify, FCast Receiver, IP Address, Kinopoisk, One Play, Rutube, Shark TV, TIMVISION, Top Radio, UGREEN NAS, and Wink. The nine clear name-list omissions are mostly entries Projectivy itself marks manual-only: Browkorf TV, Ebox, Magma Player, MXL IPTV, NESN 360, Plezy, ServusTV On, Telly Latino, and UP Faith & Family.

These are **not automatically high-priority omissions**. Manual-only status means package/activity evidence was unavailable to that maintainer. Admit only when TV relevance and a reproducible source mark are independently established. Projectivy's April 2026 release notes explicitly say starred entries have no automatic mapping.

## 4. Direct-demand candidate matrix

Priority meanings: **P0** identity/correction candidate; **P1** strong admission lead; **P2** hold for evidence; **Reject** violates the TV-first admission rule in its currently evidenced form.

| Candidate | Demand signal | Package | TV launcher activity | TV/region relevance | Current mark cue and source plan | Verdict |
|---|---|---|---|---|---|---|
| Beacon Game Launcher | Explicit Projectivy community request | `com.radikal.gamelauncher` | `com.radikal.gamelauncher.MainActivity`, corroborated by Projectivy mapping | Game-library frontend; TV/handheld use | Revalidate current Beacon app icon/YouTube channel and redraw its beacon/game motif | **P0 rename/revalidate; already mapped** |
| ITVX | Explicit community request | `air.ITVMobilePlayer` | Core currently maps `com.itv.tenft.itvhub.MainActivity`; current export needs APK/ADB confirmation | Major UK TV service; official Android TV build exists | Current official ITVX wordmark/app tile, not legacy ITV Hub | **P0 identity refresh; already mapped** |
| Sideload Launcher / SideApps | Direct mapping complaint naming both products | Chainfire: `eu.chainfire.tv.sideloadlauncher`; EasyJoin: `net.easyjoin.applauncher` | Both activities already in Core | Highly TV-relevant utility category | Chainfire's grid/rocket-era identity versus SideApps' current EasyJoin listing icon | **P0 split one catalog identity into two** |
| MyAEW | Two explicit community requests after its March 2026 launch | `com.kiswe.androidtv.myaew` | Unknown; inspect current connected-TV bundle or obtain ADB | Officially supports Android TV 7.1+, Fire TV, Apple TV and Roku; global wrestling relevance | Current MyAEW/AEW connected-TV icon from official Play listing/support site | **P1 add after activity evidence** |
| TDUK App Killer | Repeated Android TV request/recommendation; current official developer listing | `com.tduk.appklr` | Unknown; Projectivy has no automatic mapping | Purpose-built one-click Android TV utility, updated Jan 2026 | Current official Play icon; extract simple TDUK/tool cue, no screenshot tracing | **P1 add after APK/ADB** |
| TDUK App Cache Cleaner | Explicit request and repeated TV recommendations; current official listing | `com.tduk.cacheclean` | Unknown; Projectivy has no automatic mapping | Purpose-built Android TV utility, 5K+ niche installs in secondary telemetry | Current official Play icon; distinguish clearly from App Killer | **P1 add after APK/ADB** |
| Tata Play Binge | Explicit community request with Play link | `com.tataskymore.open.uat` | Unknown; inspect Android TV bundle | Official Play copy supports Android Smart TV and Fire TV; strong India relevance; active 2026 TV releases | Current Tata Play Binge symbol from official Play listing | **P1 add after activity evidence** |
| Telia Play Norway TV | Explicit request cited the phone package | TV-specific listing is `no.get.play.tv`; `no.get.play` is phone/tablet | Unknown | Official Telia page supports Android TV; specific TV package has 50K+ installs | Current Telia Play mark, ensure Norwegian variant is distinct only if mark differs | **P1 investigate the TV package, not requested phone package** |
| ARTE | Explicit community request; existing component | Existing `arte` entry/components | Existing mapping | France/Germany public-service TV | Official requester supplied ARTE corporate logo ZIP; compact slanted ARTE wordmark is reproducible | **P1 generic-to-bespoke upgrade** |
| France.tv | Explicit community request; existing component | `fr.francetv.pluzz` | `fr.francetv.androidtv.main.MainActivity` | Major French public broadcaster | Current France.tv dot/wordmark system from official service | **P1 generic-to-bespoke upgrade** |
| Cloudflare 1.1.1.1 + WARP | Explicit request | `com.cloudflare.onedotonedotonedotone` | No native TV launcher activity evidenced | Current official listing is phone-oriented; TV users sideload, need portrait UI/mouse, and report VPN-profile failures | Cloudflare orange cloud/`1.1.1.1` identity is clear but admission is the issue | **Reject automatic TV admission; optional manual-only discussion** |
| TV 2 Sport | Community request supplied package | `com.mobilefootie.tv2` | No TV activity evidenced | Current Play listing exposes phone/tablet only; separate TV 2 Play service exists | TV 2 Sport mark available, but wrong form factor | **Reject until native TV evidence** |
| Obtainium | Explicit request | `dev.imranr.obtainium` (verify current source) | Phone utility; no native TV activity evidenced | Useful for sideload users, but not TV-native | Official open-source repository provides reproducible mark | **P2 manual-only; do not pad automatic coverage** |
| ActionDash | Explicit request | `com.actiondash.playstore` | No TV activity evidenced | Phone digital-wellbeing app | Official Play icon exists | **Reject for TV-first pack** |
| Wavelet | Explicit request | `com.pittvandewitt.wavelet` | No TV activity evidenced | Primarily phone headphone EQ | Official app mark exists | **Reject for TV-first pack** |
| Debrid Stream | Explicit request with Play link | `com.debridstream.tv` | Needs current APK/ADB | Name and package indicate TV; niche but directly requested | Current official Play icon; avoid conflating with other debrid players | **P2 verify legality, listing continuity, activity and mark** |
| AirReceiverLite | Explicit request | `com.softmedia.receiver.lite` | Needs APK/ADB | TV casting receiver use case | Official Play icon; distinguish from AirScreen/AirReceiver variants | **P2 verify current TV availability/activity** |

### Required activity capture for P1 additions

Record all of the following before adding an automatic component:

```text
adb shell cmd package resolve-activity --brief <package>
adb shell dumpsys package <package> | sed/grep around MAIN and LEANBACK_LAUNCHER
versionName/versionCode
installer/source store
device model + Android TV/Google TV version
APK signing certificate digest when an APK was inspected
```

A package name alone is not an exported launcher component.

## 5. Existing generic icons: ranked recognisability upgrades

The table deliberately contains more than the 22 needed for the sub-55% milestone. Final selection should favor a clear independent emblem and current TV visibility, not alphabetic convenience.

| Rank | Existing entry | Current generic | Why visible/relevant | Reproducible visual cue to validate | Evidence/confidence |
|---:|---|---|---|---|---|
| 1 | AirScreen | `broadcast_A` | Casting receiver with very large Android install base and direct TV use | Current official app's nested screen/cast form | Official/current Play icon needed; high demand confidence |
| 2 | Aerial Views | `tool_A` | Popular open-source Android TV screensaver; current 2026 releases | Developer repository launcher asset; aerial/mountain aperture cue | Source-reproducible; high |
| 3 | ARTE | `broadcast_A` | Direct request; major Franco-German broadcaster | Slanted compact `ARTE` mark from official corporate ZIP | Official asset; high |
| 4 | Francetv | `app_F` | Direct request; major French service | France.tv dot/wordmark geometry | Official service asset; high |
| 5 | ITV Hub → ITVX | bespoke but stale | Direct request; top UK service | Current `ITVX` identity, replacing obsolete Hub treatment | Official Play/service; high |
| 6 | Angel Studios | `broadcast_A` | Recognisable streaming service | Current angel-wing/`A` app emblem, if still used | Official listing; medium-high |
| 7 | AnyDesk | `tool_A` | Common remote-support tool on TV boxes | Two opposed red diamond/chevrons | Stable public brand mark; high |
| 8 | CANAL+ | `broadcast_C` | Major European broadcaster | Black/white CANAL+ compact wordmark; no invented C | Official service; high |
| 9 | Bloomberg TV+ | `broadcast_B` | Global business-news TV app | Bloomberg wordmark is primary; assess compact `B` legitimacy | Official listing; medium |
| 10 | DW | `broadcast_D` | Global public broadcaster | Interlocked `D/W` circles | Official broadcaster identity; high |
| 11 | Euronews | wrongly `sport_E` | Global news app and category error | Current ring/circle-plus-wordmark treatment | Official broadcaster; high |
| 12 | Crossy Road | `gaming_C` | Highly recognisable Android/TV game | Pixel chicken head/silhouette, redrawn minimally | Official app art; high recognisability |
| 13 | Blokada | `vpn_B` | Direct community icon mention; TV-network utility use | Current shield/hexagon flame treatment, version-specific | Official open-source project; medium-high |
| 14 | Private Internet Access | `vpn_P` | Prominent Android TV VPN | Robot/lock-head silhouette | Official VPN brand; high |
| 15 | MPV | `app_M` | Widely used open-source media player | Stepped circular play mechanism | Official open-source icon; high |
| 16 | F-Droid | wrongly `broadcast_F` | Common sideload/open-source store | Robot head with antenna inside bag/device | Official open-source brand; high |
| 17 | Audiomack | `music_A` | Recognisable music service | Interlocked waveform/`A` treatment | Official current listing; medium-high |
| 18 | ATRESplayer | `broadcast_A` | Major Spanish-language service | Current ATRESplayer circular play/radiating mark | Official service; high region relevance |
| 19 | HGTV | `broadcast_H` | Major US factual/home channel | Roofline over HGTV wordmark; compact roof cue | Official network; high |
| 20 | Kinopoisk | `broadcast_K` | Current Projectivy addition; large regional service | Current geometric K/gradient mark reduced to silhouette | Official service/current listing; medium-high |
| 21 | Rutube | `broadcast_R` | Current Projectivy addition; regional video service | Rounded play/R mark, version-check required | Official listing; medium-high |
| 22 | TIMVISION | `broadcast_T` | Current Projectivy addition; Italian TV service | TIM bar motif plus compact play/TV cue | Official service; medium-high |
| 23 | FIFA+ | `sport_F` | Global football streaming relevance | Do **not** reproduce protected FIFA official IP without rights review; use only an allowed app-identifying treatment | Visual clarity high; legal clearance required |
| 24 | AIDA64 | `tool_A` | Common diagnostics utility on TV boxes | Red circuit/chip `64` app cue | Official FinalWire listing; medium |
| 25 | APK Updater | `store_A` | Sideload ecosystem utility | Verify which APK Updater project/package; use its repository asset only | Identity collision risk; medium-low until resolved |
| 26 | BrowseHere | `browser_B` | Browser commonly bundled/on TV | Current compass/browser emblem | Vendor/Play evidence needed; medium |
| 27 | One Play | `broadcast_O` | Current competitor addition, already mapped | Validate which regional One Play service before drawing | Name collision risk; low until resolved |
| 28 | Wink | `broadcast_W` | Current competitor addition, already mapped | Validate current service/version and region | Name collision risk; low until resolved |
| 29 | Top Radio | `music_T` | Current competitor addition, already mapped | Validate package-specific station mark | Generic name collision risk; low |
| 30 | Bitdefender | `vpn_B` | Security/VPN category visibility | Current shield/B form; verify TV product identity | Official brand clear; TV-product fit medium |

### Exclusions from the first 22

- **Amazon Freevee:** the standalone brand was phased into Prime Video; do not invest in a dead identity without device evidence of a still-installed launcher component.
- **Bravia Core:** investigate the Sony Pictures Core rename first.
- Apps with only a wordmark and no compact symbol should not receive an invented one-letter tile merely to satisfy the metric.
- FIFA+ requires a rights check because FIFA's published IP guidance restricts unauthorized commercial use and confusing adaptations of official marks.

## 6. The 83 unverified mappings: defensible clearing plan

No unverified row is corroborated verbatim by the committed Projectivy 1.1.9 reference. That is expected: all inherited Projectivy identities are already verified; these 83 are Core-added guesses, aliases, local apps, internal shortcuts, or device-specific activities.

| Evidence class | Rows | Entries | Action |
|---|---:|---|---|
| Local source manifests | 3 | Core Doctor, Core Line, Core Shift | **Can clear from repository evidence**: each Gradle application ID and exported `.MainActivity` is present locally. Do in a later metadata change with a test ratchet from 83 to 80. |
| Mainstream TV streaming APKs | 8 | Binge (2), Kayo (1), Seven Plus (2), Twitch (1), Vidio (2) | Inspect current signed TV APKs or capture ADB. Keep only current launcher activity; remove speculative `.MainActivity` fallbacks. |
| Public-source file managers | 4 | Ghost Commander (2), Material Files (2) | Pin a source commit/tag and compare exported MAIN/LEANBACK activities. Clear only exact current components. |
| Synology legacy/mobile suite | 22 | DS audio/file/finder/get/photo/video, Synology Drive | High prune risk. Most are mobile/legacy and include three speculative activity shapes each. Verify maintained status and TV launchability; otherwise remove unsupported component aliases rather than clear them. |
| Projectivy internal shortcuts | 22 | HDMI 1–4, AV, TV, Source, Settings, Categories, Channels, Media Explorer | These are not ordinary apps. Verify against installed Projectivy version(s) by `dumpsys package` and exported status. Version-gate mentally: old and new namespace paths may coexist in catalog only with evidence. |
| Proprietary/system file managers | 8 | Google Files/DocumentsUI, RS, Solid Explorer, Ultimate File Manager Pro | APK/firmware inspection or device ADB required. DocumentsUI is firmware-dependent; never generalize one OEM's exported activity globally. |
| Phone/console companion mappings | 4 | Nintendo Switch, PlayStation (2), Xbox | Review for TV relevance. These look like mobile companion/Remote Play assumptions and generic `.MainActivity` guesses. Prune unless a launcher-tested TV/sideload case is an explicit supported policy exception. |
| Obscure/device-specific | 12 | Hi Browser (3), Launcher Manager (2), BitTV (2), Screen Recording App (3), Stremize (2) | Require the exact APK and/or ADB. Three guessed activity variants are not evidence. If the app cannot be sourced legitimately, remove the speculative rows and leave a manual icon only if policy allows. |
| **Total** | **83** |  |  |

### Recommended ratchet sequence

1. Clear the three locally proven Core mappings: 83 → 80.
2. Verify public-source manifests: target 76 or fewer, with a URL and commit/tag in provenance.
3. Inspect five mainstream TV apps: target 68 or fewer after retaining only real exports.
4. Treat Synology and console rows as a deletion audit, not a verification exercise.
5. Test Projectivy internal shortcuts on at least one current and one older supported Projectivy build before clearing; record version/device evidence.
6. Never bulk-clear based on package existence. An activity must be exported and appropriate for launch.

## 7. Proposed tranche, without a release date

### Phase A — identity correctness

1. Split Chainfire Sideload Launcher from EasyJoin SideApps.
2. Rename Game Launcher metadata to Beacon Game Launcher after current-mark review; preserve its already-correct component.
3. Refresh ITV Hub to ITVX only after current TV activity confirmation.
4. Correct category mistakes including Euronews (`sport_E`) and F-Droid (`broadcast_F`).

### Phase B — evidence capture

Acquire activity evidence for MyAEW, TDUK App Killer, TDUK Cache Cleaner, Tata Play Binge, and Telia Play Norway TV. A candidate with no exported TV launcher remains unadmitted or manual-only.

### Phase C — recognisability milestone

Select 22 or more of the high-confidence existing generics. Prefer source-reproducible geometry and direct demand. Run side-by-side 10-foot tests at launcher card size; reject marks that collapse into an indistinct blob.

### Phase D — unverified ratchet

Land verification/pruning independently from artwork so review can distinguish mapping truth from visual preference.

## 8. Source registry

### Direct demand and launcher behavior

- Borderless Transparent Icons request thread: <https://www.reddit.com/r/Projectivy_Launcher/comments/1j3848y/borderless_transparent_icons/>
- Projectivy Icon Pack launch/request thread: <https://www.reddit.com/r/Projectivy_Launcher/comments/1mtmall/introducing_the_projectivy_icon_pack/>
- Projectivy pack repository and mapping caveats: <https://github.com/SicMundus86/ProjectivyIconPack>
- Projectivy 1.1.9 release notes: <https://github.com/SicMundus86/ProjectivyIconPack/releases/tag/1.1.9>
- Android TV launcher/icon-pack context: <https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/>

### Official/current listings and support

- TechDoctorUK Play catalog (four current TV utilities and package IDs): <https://play.google.com/store/apps/developer?id=TechDoctorUK>
- Tata Play Binge: <https://play.google.com/store/apps/details?id=com.tataskymore.open.uat>
- MyAEW Play listing: <https://play.google.com/store/apps/details?id=com.kiswe.androidtv.myaew>
- MyAEW connected-TV support: <https://support.myaew.com/hc/en-us/articles/38617315167383-Watching-on-your-Connected-TV-Device>
- Beacon developer channel/Play link: <https://www.youtube.com/@BeaconLauncher>
- ITVX: <https://play.google.com/store/apps/details?id=air.ITVMobilePlayer>
- SideApps: <https://play.google.com/store/apps/details?id=net.easyjoin.applauncher>
- Cloudflare 1.1.1.1: <https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone>
- Telia Play phone listing: <https://play.google.com/store/apps/details?id=no.get.play>
- Telia Play Android TV listing: <https://play.google.com/store/apps/details?id=no.get.play.tv>
- TV 2 Sport form-factor listing: <https://play.google.com/store/apps/details?id=com.mobilefootie.tv2>
- Aerial Views official source: <https://github.com/theothernt/AerialViews>
- Aerial Views Play listing: <https://play.google.com/store/apps/details?id=com.neilturner.aerialviews>

### Cautionary/secondary package evidence

- Tata Play Binge TV signed-build history: <https://www.apkmirror.com/apk/tata-sky-ltd/tata-play-binge-22-otts-in-1-android-tv/>
- ITVX Android TV signed-build example: <https://www.apkmirror.com/apk/itv-plc/itv-hub-your-tv-player-watch-live-on-demand-android-tv/>
- Cloudflare community thread confirming no native TV experience: <https://community.cloudflare.com/t/when-is-1-1-1-1-app-coming-to-android-tv/236033>
- FIFA IP guidance: <https://www.fifadigitalarchive.com/welcome_old/markrequest/Common/documents/FIFA_World_Cup_26tm_IP_Guidelines_English_version_2_0_June_2024.pdf>

## 9. Stop conditions before production work

Do not start artwork or version work until a proposed tranche identifies, for every entry:

- whether it is new, renamed, split, corrected, or visually upgraded;
- exact package and exported TV activity evidence, or explicit manual-only status;
- current source mark and date checked;
- regional/form-factor relevance;
- provenance URL and confidence;
- collision check against all 940 existing names, drawables, packages, and glyph identities.

A release count is an output of that process, never its target.
