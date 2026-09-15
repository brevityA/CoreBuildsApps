# Next icon tranche: demand, mapping, and identity research

**Research date:** 2026-09-15; visual-source update 2026-09-16 (Australia/Sydney)
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

The implementation-facing source ledger is committed as [`icon-visual-source-matrix-2026-09-15.csv`](icon-visual-source-matrix-2026-09-15.csv). It covers the initial top 22 plus promoted fallback AIDA64 (overall rank 24); rank 23 FIFA+ remains excluded by its rights gate. It pins package-specific official references where available, separates source readiness from demand, and records rights/design blockers. In particular, it prevents “high priority” from being misread as “safe to trace immediately.”

### Visual verification pass 1 (2026-09-16)

Six package-specific corrections sharpen the first implementation tranche:

- **AirScreen (`com.ionitech.airscreen`)** uses a custom `AS` monogram across the current exact-package listing and product screens, not a nested-screen/cast symbol.
- **Angel (`com.angel.tv`)** is currently wordmark-led: spaced uppercase `ANGEL` with a distinctive peaked/wing-like initial `A`. No independent wing emblem is established, so this requires a 10-foot compact-wordmark test rather than an invented monogram.
- **Euronews (`com.alteox.euronews`)** currently uses stacked lowercase `euro` / heavier `news.` with a terminal dot. The historic circle/sun should not be revived for this package.
- **Blokada (`org.blokada.fyra`)** is specifically legacy Blokada 4, whose last indexed release is 4.15.0 (`415000000`, 5 April 2022). Its reproducible cue is the orange/red shield outline split into three descending diagonal bands. Blokada 5 source corroborates the stable shield family but does not prove the `fyra` manifest; package and visual evidence remain explicitly separate.
- **ATRESplayer (`com.antena3.atresplayer.tv`)** now uses two nested angular right-facing chevron/play outlines, replacing the older circular/radiating treatment in the draft.
- **Kinopoisk (`ru.kinopoisk.tv`)** now uses an upright `K` whose right arms expand into multiple tapered radial rays, not the older faceted/ribbon shorthand.

These observations authorize only Core-style redraw prototypes and small-size testing. They do not authorize copying Play artwork, changing production assets, or releasing v1.8.20.

A same-day exact-package source sweep cleared several provenance blockers without pretending that a store page proves an exported activity. France.tv, CANAL+, Bloomberg, HGTV GO, and TIMVISION all retain live official Play pages for their catalog package; CANAL+, Bloomberg, and HGTV explicitly expose the TV form factor in current Play output. RUTUBE's catalog package is the reverse case: its Google Play URL is gone, but the official RuStore Android TV catalog currently publishes `ru.rutube.app.tv` as version `31.14.2.TV-rustore` (11 August 2026). The visual matrix now points to that current TV-specific source and warns against transferring assets from either current mobile package.

### Top-22 visual gate disposition

The source pass now gives every initial top-22 row one explicit implementation gate rather than an open-ended research label. AIDA64 is included as the first promoted fallback:

| Final gate | Count | Entries | Meaning |
|---|---:|---|---|
| **Redraw-ready** | 12 | AirScreen, Aerial Views, AnyDesk, DW, Crossy Road, Blokada, Private Internet Access, MPV, Audiomack, ATRESplayer, Kinopoisk, AIDA64 | Independent identifying geometry and reproducible sources are sufficient for a Core-style prototype. Small-size tests still apply. |
| **Testing-gated** | 5 | France.tv, Angel Studios, CANAL+, Euronews, HGTV | Source identity is clear, but the compact wordmark or fine geometry must pass a 10-foot launcher-card test before selection. |
| **Rights-gated** | 2 | ARTE, F-Droid | Do not prototype until the stated authorization or derivative-license treatment is resolved. F-Droid officially dual-licenses its logo under CC-BY-SA-3.0 or GPLv3+; the repository needs an explicit attribution/share-alike decision. |
| **Source/activity-gated** | 4 | ITVX, Bloomberg, RUTUBE, TIMVISION | Exact product/package evidence exists, but current launcher foreground and/or exported TV activity is not yet strong enough for production planning. |

This is a selection boundary, not permission to ship all twelve redraw-ready entries. It also exposes two key consequences. First, the ledger contains **21 generic entries plus one stale bespoke entry (ITV Hub → ITVX)**, so even approving every row could not itself produce 22 generic conversions. Second, twelve are redraw-ready today after promoting AIDA64. Reaching the sub-55% milestone therefore requires both passing appropriate gated rows and promoting at least one additional high-confidence generic candidate from ranks 23–30; no count should be padded to compensate.

The final source checks behind this disposition found that DW and Audiomack both have inspectable vectors in pinned Simple Icons commit `4ba19240849175ab4b855a732ab98c0f87cfb714`, linked there to the official DW site and Audiomack style guide. Crossy Road has an official developer press kit and its exact package currently exposes TV compatibility. Private Internet Access likewise retains TV compatibility for its exact package. France.tv publishes the current black wordmark SVG directly from its own domain. RUTUBE publishes an official brandbook with primary, compact, and icon forms, but the TV launcher must still be matched to one of those forms.

**AIDA64 is the first promoted fallback.** The exact `com.finalwire.aida64` Play listing was updated 19 August 2026, explicitly exposes TV compatibility, and matches the catalog package. It does not by itself prove either catalog activity; that remains manifest/ADB evidence. Current package imagery and FinalWire's own support description agree on the identifying cue: a bold interlocked white `64` on red. The Core redraw should retain the diagonal upper stroke and sharply cut digit counters, not the vendor tile or shadow. This adds the twenty-second generic candidate needed to make the numerical milestone possible without pretending stale bespoke ITVX is a generic conversion.

### Source/activity gate findings

The four source/activity-gated rows now have bounded next checks:

- **ITVX:** APKMirror indexes the ITV PLC-signed Android TV 1.25.0 bundle (version code 83, 8 June 2026) under the exact `air.ITVMobilePlayer` package and marks it as requiring Android TV. This proves that the TV package remains current, but not that 2026 still exports the catalog's legacy-named `com.itv.tenft.itvhub.MainActivity`; inspect that exact bundle or capture ADB before renaming the production identity.
- **Bloomberg:** current Play output proves `com.bloomberg.btva`, Bloomberg publication, and TV form factor. The remaining visual question is whether its launcher uses only the Bloomberg wordmark or a compact app-specific foreground. The existing `tv.accedo.one.app.bootstrap.BootstrapActivity` must not be inferred from the icon page.
- **RUTUBE:** official RuStore proves current Android TV package `ru.rutube.app.tv`, while the official brandbook proves three logo forms. One current RuStore manifest or device capture must match both the launcher activity and the actual compact/icon form; neither the current mobile package nor the historical Google Play package may be substituted.
- **TIMVISION:** the current `it.telecomitalia.cubovision` Play copy is phone/tablet-led and did not expose a current TV form-factor marker in the captured output. A historical signed sample exposes `HomeActivity` strings, not proof of the catalog's inherited `com.canal.ui.tv.TvMainActivity`. Obtain the current TV/OEM delivery before retaining that activity or treating the 2026 `VISION` lockup as its launcher foreground.

| Rank | Existing entry | Current generic | Why visible/relevant | Reproducible visual cue to validate | Evidence/confidence |
|---:|---|---|---|---|---|
| 1 | AirScreen | `broadcast_A` | Casting receiver with very large Android install base and direct TV use | Custom open `AS` monogram, corroborated by the exact-package listing and product screens | Official/current exact package; high |
| 2 | Aerial Views | `tool_A` | Popular open-source Android TV screensaver; current 2026 releases | Pinned developer asset shows a low sun emerging behind two asymmetric overlapping mountain/dune silhouettes | Source-reproducible; high |
| 3 | ARTE | `broadcast_A` | Direct request; major Franco-German broadcaster | Slanted compact `ARTE` mark from official corporate ZIP | Official asset; high |
| 4 | Francetv | `app_F` | Direct request; major French service | Current lowercase `france.tv` wordmark with centered dot | Official source SVG plus exact-package listing; testing-gated |
| 5 | ITV Hub → ITVX | bespoke but stale | Direct request; top UK service | Current `ITVX` identity, replacing obsolete Hub treatment | Official Play/service; high |
| 6 | Angel Studios | `broadcast_A` | Recognisable streaming service | Current spaced `ANGEL` wordmark with a peaked/wing-like initial `A`; no standalone wing assumed | Exact-package official listing; high identity confidence, wordmark-size risk |
| 7 | AnyDesk | `tool_A` | Common remote-support tool on TV boxes | Two opposed red diamond/chevrons | Stable public brand mark; high |
| 8 | CANAL+ | `broadcast_C` | Major European broadcaster | Black/white CANAL+ compact wordmark; no invented C | Official service; high |
| 9 | Bloomberg TV+ | `broadcast_B` | Global business-news TV app | Bloomberg wordmark is primary; assess compact `B` legitimacy | Official listing; medium |
| 10 | DW | `broadcast_D` | Global public broadcaster | Overlapping circular bodies containing `D` and cut-out `W` | Pinned vector tied to official broadcaster; redraw-ready |
| 11 | Euronews | wrongly `sport_E` | Global news app and category error | Current exact-package icon is stacked lowercase `euro` / bold `news.` with a terminal dot, not the historic circle/sun | Exact-package official listing; high |
| 12 | Crossy Road | `gaming_C` | Highly recognisable Android/TV game | Pixel chicken head/silhouette, redrawn minimally | Official press kit and exact TV-compatible package; trademark caution |
| 13 | Blokada | `vpn_B` | Direct community icon mention; TV-network utility use | `org.blokada.fyra` is legacy Blokada 4: orange/red shield outline split into three descending diagonal bands | Exact-package 4.15.0 release plus official-project corroboration; high identity confidence |
| 14 | Private Internet Access | `vpn_P` | Prominent Android TV VPN | Robot/lock-head silhouette | Official VPN brand; high |
| 15 | MPV | `app_M` | Widely used open-source media player | Stepped circular play mechanism | Official open-source icon; high |
| 16 | F-Droid | wrongly `broadcast_F` | Common sideload/open-source store | Robot head with antenna inside bag/device | Official open-source brand; high |
| 17 | Audiomack | `music_A` | Recognisable music service | Rising asymmetric waveform with leading dots, dominant downstroke, and terminal pulse | Official style guide plus pinned vector; redraw-ready |
| 18 | ATRESplayer | `broadcast_A` | Major Spanish-language service | Current exact-package icon uses two nested angular right-facing chevrons/play outlines, not the older circular/radiating treatment | Exact-package official listing; high region relevance |
| 19 | HGTV | `broadcast_H` | Major US factual/home channel | Roofline over HGTV wordmark; compact roof cue | Official network; high |
| 20 | Kinopoisk | `broadcast_K` | Current Projectivy addition; large regional service | Upright `K` whose right arms expand into tapered radial rays; preserve the asymmetric ray silhouette | Exact TV-package official listing; high |
| 21 | Rutube | `broadcast_R` | Current Projectivy addition; regional video service | Capture current TV-store foreground; do not transpose either mobile-package icon | Exact-package official RuStore Android TV listing; geometry pending |
| 22 | TIMVISION | `broadcast_T` | Current Projectivy addition; Italian TV service | Verify the 2026 split-bar plus `VISION` rebrand against the exact launcher foreground | Exact-package official listing; rebrand capture pending |
| 23 | FIFA+ | `sport_F` | Global football streaming relevance | Do **not** reproduce protected FIFA official IP without rights review; use only an allowed app-identifying treatment | Visual clarity high; legal clearance required |
| 24 | AIDA64 | `tool_A` | Common diagnostics utility on TV boxes | Bold interlocked `64` with diagonal upper stroke and sharply cut counters | Exact package is current and TV-compatible; official-product corroboration; high |
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

### Verification pass 1 results (2026-09-15)

The complete 83-row working disposition is committed as [`unverified-mapping-disposition-2026-09-15.csv`](unverified-mapping-disposition-2026-09-15.csv). It records the component, evidence class, current status, proposed disposition, and evidence note rather than reducing the audit to app-level totals.

Results after the first source/manifest pass:

- **5 rows confirmed:** Core Doctor, Core Line, Core Shift, and both equivalent spellings of Material Files' `FileListActivity`. Material Files commit `fc1250038496ebf4d4c139f62d16f0071f2c995a` declares the activity exported with `MAIN`, `LAUNCHER`, and `LEANBACK_LAUNCHER`.
- **2 rows historically confirmed:** both equivalent Ghost Commander `FileCommander` spellings exist in source, but the accessible Git mirror stops in 2019. F-Droid's current suggested build is 1.64.2b4/479 from December 2025, so its corresponding current source/APK must still be checked before clearing.
- **7 Projectivy rows present in a 4.66 activity inventory:** the guided-actions HDMI 1–4 activities, guided-actions AV activity, `ui.settings.SettingsActivity`, and guided-actions `AppSettingsActivity`.
- **7 Projectivy rows use an obsolete pre-4.0 namespace:** the four `.activities.input.SourceHDMI*` rows, `.activities.input.SourceAVActivity`, `.activities.input.SourceTVActivity`, and `.activities.input.SourceActivity`. The Projectivy developer publicly documented the 4.0.1 namespace refactor.
- **8 further Projectivy rows were absent or conflict with actual nearby class names** in the 4.66 inventory. For example, the observed classes are `InternalTvActivity`, `SourcePopupActivity`, and `MediaExplorerActivity`, not the catalog's `SourceTVActivity`, `SourceActivity`, or `MediaExplorerShortcutActivity`. Category/Channel shortcut names were also absent.
- **4 console/phone companion rows initially failed package/form-factor review.** Pass 3 below separates outright identity errors from TV products published under different package IDs.
- **50 rows were unresolved after pass 1** pending a current legitimate APK, current source, OEM firmware, or ADB capture; passes 2 and 3 narrow that backlog further.

The Projectivy evidence is version 4.66, not permission to clear against 4.71. GitHub's official release API identifies `ProjectivyLauncher-4.71-c95-xda-release.apk` (version code 95, 11,316,610 bytes; published 13 July 2026), and APKMirror independently indexes three version-95 TV bundles. Both GitHub release-assets and Aptoide's pool still terminate at TLS/EOF in this environment, including an authenticated `gh release download`; no binary was silently substituted with a modified mirror build. Reattempt the official asset from a normal network or capture `dumpsys package com.spocky.projengmenu` on a 4.71 device before changing metadata.

### Verification pass 2: Synology's 22 rows

The Synology block is no longer an undifferentiated APK backlog:

- **DS audio (3 rows): remove.** Android package names are case-sensitive. The official package is `com.synology.DSaudio`, while all catalog rows use nonexistent/wrong-case `com.synology.dsaudio`. The official listing exposes phone, Chromebook, and tablet—not TV.
- **DS file (4 rows): remove automatic mappings.** The official `com.synology.DSfile` listing is phone/Chromebook/tablet only. Three activities are speculative aliases and the fourth package, `com.hisona.dsfile`, is a different publisher identity folded into the Synology icon.
- **DS finder (3 rows): remove.** Its current official listing was updated in May 2026 and exposes phone/tablet only; there is no native-TV launcher evidence.
- **DS get (3 rows): remove.** The official historic package is `com.synology.DSdownload`, not `com.synology.DSget`; the catalog rows therefore do not identify the published app.
- **Synology Drive (3 rows): remove legacy mappings.** The current official package is `com.synology.dsdrive`; catalog packages `com.synology.server.SynologyDrive` and `com.synology.dscloud` are superseded identities. The current product is phone/Chromebook/tablet, so do not replace them with another automatic TV mapping.
- **DS photo (3 rows): inspect before deciding.** It is a DSM 6.2 legacy product superseded by Synology Photos, but the official Play listing still reports TV compatibility. Exact exported activity evidence is required.
- **DS video (3 rows): correct rather than blindly clear.** A genuine Android TV build 1.1.8 exists from June 2024. A documented decompilation and successful ADB launch of 1.1.7 identify `com.synology.dsvideo.ui.WelcomeActivity`; none of the catalog's guessed `SplashActivity`/`MainActivity` rows match it. Confirm that 1.1.8 retained `WelcomeActivity`, then replace the three guesses with that one component. Video Station was removed from DSM 7.2.2 in 2024, so label support as legacy.

This pass yields **16 removal-ready Synology rows**, three DS photo rows needing APK/ADB, and three DS video rows needing replacement with one version-confirmed TV activity.

### Verification pass 3: branded mobile/TV package splits

Official Play listings expose another class of false mapping: the brand is TV-relevant, but the catalog points at its mobile package or an obsolete identity and guesses `.MainActivity`. These must be replaced from a TV APK, not merely unflagged:

- **Nintendo Music / “Nintendo Switch” (1 row): remove and correct the identity.** `com.nintendo.znba` is Nintendo Music, not Nintendo Switch. Nintendo's separate Switch companion is `com.nintendo.znca`; both are smartphone-oriented, and the catalog row has neither the right name nor TV evidence.
- **PS Remote Play (1 row): remove the mobile component.** `com.playstation.remoteplay` is the mobile app. Sony publishes the Android TV OS 12+ client separately as `com.playstation.remoteplay.tv`. Inspect that TV delivery for its exported launcher before adding it.
- **PlayStation App (1 row): inspect the delivered split.** Google Play form-factor output has varied by locale, but the catalog's `com.scee.psxandroid/.MainActivity` remains an unsupported guess. Do not conflate it with Remote Play's separate TV package.
- **Xbox / Game Pass (1 row): remove.** No authoritative current product matches `com.microsoft.xboxone.gamepass`. Current official identities include Game Pass `com.gamepass` and Xbox `com.microsoft.xboxone.smartglass`; neither licenses a guessed activity under the catalog package.
- **BINGE (2 rows): replace only after TV-manifest proof.** The official current TV package is `au.com.streamotion.ares.tv`, updated 3 September 2026. Catalog identities `au.com.streamotion.ares` and `au.com.binge.tv` are not that TV package; both reuse the same unsupported `au.com.foxsports.martian.tv.main.MainActivity` guess.
- **Kayo (1 row): replace only after TV-manifest proof.** The official current product is `au.com.kayosports.tv`, explicitly titled “Kayo Sports - for Android TV.” Catalog `au.com.streamotion.hyperion` is not the current TV identity.
- **Vidio (2 rows): replace only after TV-manifest proof.** The current TV-only listing is `com.vidio.android.tv`, updated 15 September 2026. Catalog rows instead target mobile `com.vidio.android` with two activity guesses.

This pass makes **8 additional rows deletion-ready as written**; the PlayStation App row remains inspection-gated. Four high-value replacement-package audits remain: PS Remote Play TV, BINGE TV, Kayo TV, and Vidio TV. It does not claim launcher components for those packages; signed manifests or ADB remain mandatory.

### Verification pass 4: file managers and TV utilities

Fourteen more rows now have a narrower disposition than “unresolved”:

- **AOSP/Google DocumentsUI (1 row): real exported class, package still device-dependent.** At AOSP DocumentsUI tree `8532101fc5f21be25618cd6afca9f125c3276a94`, `com.android.documentsui.files.FilesActivity` is exported and handles `MAIN`; the public launcher alias is separately named `LauncherActivity`. The catalog's package is the OEM-renamed `com.google.android.documentsui`, which an AML DocumentsUI sample corroborates, but AOSP source cannot prove that package on every TV firmware. Retain the flag until an Android TV firmware dump or ADB capture proves the exact package/component pair.
- **Files by Google (1 row): remove from automatic TV mappings.** `.home.HomeActivity` is a real class, but the official listing updated 14 September 2026 exposes phone, Chromebook, and tablet only. Class existence does not overcome the no-phone-padding rule.
- **HiBrowser (3 rows): TV relevance confirmed, activity unresolved.** The current `com.hisense.odinbrowser` listing explicitly describes a remote-optimized Android TV browser and exposes TV compatibility. None of the three catalog `MainActivity` variants has reliable manifest evidence, so retain the flag and inspect one current delivery.
- **Screen Recording App (3 rows): TV relevance confirmed, activity unresolved.** Both the official listing and developer site describe a D-pad-native Android TV layout under `de.twokit.screen.recording.app`. They do not identify whether the launcher is root `MainActivity`, `ui.MainActivity`, or `tv.TvMainActivity`; one signed manifest can collapse the three guesses to one truth.
- **RS File Manager (2 rows): TV relevance confirmed, activity unresolved.** The official listing, updated 9 September 2026, exposes TV compatibility. The two catalog spellings are equivalent guesses for the same root `MainActivity`; retain pending current manifest proof.
- **Solid Explorer (2 rows): remove both catalog guesses, then inspect current 3.x.** Both catalog spellings resolve to `pl.solidexplorer2.SolidExplorer`. An inspected 2.6.0 manifest and Android 12 runtime component reporting instead identify `pl.solidexplorer.SolidExplorer` under package `pl.solidexplorer2`. That is strong conflict evidence, but a current 3.x APK is still required before adding the likely replacement.
- **Ultimate File Manager Pro (2 rows): replace with a source-confirmed component.** Official source commit `7a13adca49832778b42df9164828a6f380e4044c` sets application ID `za.kilowatch.ultimatefilemanager`; exported `.onboarding.LanguageWelcomeActivity` has `MAIN`/`LAUNCHER`, and the TV flavor adds `LEANBACK_LAUNCHER` to that activity. Neither catalog `MainActivity` guess is the launcher.

This pass reduces the ledger's generic `unresolved` bucket from **23 to 9**. Five rows are removable as written in this subset (Files by Google, two Solid Explorer guesses, and two UFM guesses); UFM also has a directly source-proven one-row replacement. The remaining nine rows in this pass have sharply specified APK/firmware checks rather than open-ended research.

### Verification pass 5: close the generic unresolved bucket

The final nine generic rows now have explicit dispositions:

- **Launcher Manager (2 rows): remove.** No reliable release identity was found for catalog package `com.wolf.lms`. Documented Launcher Manager generations use `com.wolf.lm`, `com.wolf.google.lm`, or the current Mini package `com.wolf.minilm`. Activities cannot be transposed between those package families.
- **BitTV (2 rows): remove and reassess the icon itself.** The official Play URL for `com.bittv.androiddigitaltvapp` now returns Not Found. Indexed 2026 metadata describes a newly published, roughly 35-download TMDB trailer/details companion rather than a native Android TV service. Neither catalog `MainActivity` guess has provenance, and unrelated “BitTV” products use other packages.
- **7plus (2 rows): current TV service, launcher unresolved.** `com.swm.live` is the current official Australian package and its listing explicitly includes Smart TV use. Neither `au.com.seven.inferno.MainActivity` nor root `.MainActivity` has current manifest proof; inspect the current Australian Play delivery and retain one exact launcher only.
- **Stremize (2 rows): current TV app, launcher unresolved.** The official site states that the single `com.stremize.player` Google Play package supports Android TV, and the listing was updated 13 September 2026. Both catalog strings resolve to the same root `MainActivity` guess; inspect current 3.2 and collapse them if confirmed.
- **Twitch (1 row): legacy TV component, current boundary unclear.** `tv.twitch.android.app` remains Twitch's current Android package, but current Play form-factor output omits TV while prior native Android TV releases and the later wrapped-TV client are documented. Treat `tv.twitch.android.apps.TwitchActivity` as a legacy lead, not a current confirmation; inspect the last/current TV split before clearing or pruning.

The 83-row ledger now has **zero rows left under the generic `unresolved` status**. That does not mean all 83 are verified: every row is now instead assigned to confirmed, removal/replacement-ready, version-gated, firmware-gated, or exact-APK/ADB-gated evidence classes. Four of these final nine rows are deletion-ready as written; five retain precise binary checks.

### Recommended ratchet sequence

1. Clear the three locally proven Core mappings and two Material Files spellings: 83 → 78.
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
- Nintendo Music: <https://play.google.com/store/apps/details?id=com.nintendo.znba>
- Nintendo Switch App: <https://play.google.com/store/apps/details?id=com.nintendo.znca>
- PS Remote Play mobile: <https://play.google.com/store/apps/details?id=com.playstation.remoteplay>
- PS Remote Play for TV: <https://play.google.com/store/apps/details?id=com.playstation.remoteplay.tv>
- Sony's Android TV Remote Play support: <https://www.playstation.com/en-us/support/games/remote-play-android-tv/>
- PlayStation App: <https://play.google.com/store/apps/details?id=com.scee.psxandroid>
- Xbox mobile app: <https://play.google.com/store/apps/details?id=com.microsoft.xboxone.smartglass>
- BINGE for Android TV: <https://play.google.com/store/apps/details?id=au.com.streamotion.ares.tv>
- Kayo Sports for Android TV: <https://play.google.com/store/apps/details?id=au.com.kayosports.tv>
- Vidio mobile: <https://play.google.com/store/apps/details?id=com.vidio.android>
- Vidio TV: <https://play.google.com/store/apps/details?id=com.vidio.android.tv>
- AOSP DocumentsUI pinned tree/manifest: <https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/8532101fc5f21be25618cd6afca9f125c3276a94/AndroidManifest.xml>
- Files by Google: <https://play.google.com/store/apps/details?id=com.google.android.apps.nbu.files>
- HiBrowser: <https://play.google.com/store/apps/details?id=com.hisense.odinbrowser>
- Screen Recording App: <https://play.google.com/store/apps/details?id=de.twokit.screen.recording.app>
- Screen Recording App Android TV guide: <https://screenrecording.app/android-tv-screen-recording>
- RS File Manager: <https://play.google.com/store/apps/details?id=com.rs.explorer.filemanager>
- Solid Explorer: <https://play.google.com/store/apps/details?id=pl.solidexplorer2>
- Ultimate File Manager Pro source at inspected commit: <https://github.com/Kilowatch/ultimate-file-manager-pro/tree/7a13adca49832778b42df9164828a6f380e4044c>
- Ultimate File Manager Pro: <https://play.google.com/store/apps/details?id=za.kilowatch.ultimatefilemanager>
- 7plus: <https://play.google.com/store/apps/details?id=com.swm.live>
- Stremize: <https://play.google.com/store/apps/details?id=com.stremize.player>
- Stremize platform/download statement: <https://stremize.com/download>
- Twitch: <https://play.google.com/store/apps/details?id=tv.twitch.android.app>
- Current Launcher Manager Mini package documentation: <https://www.aftvnews.com/fire-tv-home-screen-replacement-is-possible-again-with-new-launcher-manager-mini-release/>
- Aerial Views official source: <https://github.com/theothernt/AerialViews>
- Aerial Views Play listing: <https://play.google.com/store/apps/details?id=com.neilturner.aerialviews>

- AirScreen exact package: <https://play.google.com/store/apps/details?id=com.ionitech.airscreen>
- Angel exact package: <https://play.google.com/store/apps/details?id=com.angel.tv>
- Euronews TV exact package: <https://play.google.com/store/apps/details?id=com.alteox.euronews>
- Bloomberg TV exact package: <https://play.google.com/store/apps/details?id=com.bloomberg.btva>
- ATRESplayer exact TV package: <https://play.google.com/store/apps/details?id=com.antena3.atresplayer.tv>
- Kinopoisk exact TV package: <https://play.google.com/store/apps/details?id=ru.kinopoisk.tv>
- France.tv exact package: <https://play.google.com/store/apps/details?id=fr.francetv.pluzz>
- CANAL+ exact package: <https://play.google.com/store/apps/details?id=com.canal.android.canal>
- HGTV GO exact package: <https://play.google.com/store/apps/details?id=com.hgtv.watcher>
- RUTUBE exact Android TV package in official RuStore: <https://apps.rustore.ru/app/ru.rutube.app.tv>
- TIMVISION exact package: <https://play.google.com/store/apps/details?id=it.telecomitalia.cubovision>
- France.tv official current black SVG: <https://www.france.tv/images/france-tv-black.svg>
- Audiomack official style guide: <https://styleguide.audiomack.com/>
- DW official site: <https://www.dw.com/>
- Crossy Road official classic press kit: <https://www.crossyroad.com/crossy-road-classic-press-kit>
- RUTUBE official brandbook: <https://rutube.ru/brand/>
- F-Droid official logo licensing: <https://f-droid.org/en/docs/Licenses/>
- Private Internet Access exact package: <https://play.google.com/store/apps/details?id=com.privateinternetaccess.android>
- AIDA64 exact package: <https://play.google.com/store/apps/details?id=com.finalwire.aida64>
- AIDA64 official Android downloads: <https://aida64.com/downloads/OWRhOTcwNjg=>
- Blokada official Android source inspected at commit: <https://github.com/blokadaorg/five-android/tree/518306f5c74516b48b646fa9362c98156ce4be2a>

### Cautionary/secondary package evidence

- Blokada 4.15.0 exact-package release index: <https://www.apkmirror.com/apk/blokada/blokada-3/blokada-3-4-15-0-release/blokada-4-15-0-android-apk-download/>
- ITVX Android TV 1.25.0 exact-package signed-bundle index: <https://www.apkmirror.com/apk/itv-plc/itv-hub-your-tv-player-watch-live-on-demand-android-tv/itvx-android-tv-1-25-0-release/itvx-android-tv-1-25-0-android-apk-download/>
- Historical TIMVISION signed-sample analysis (conflict lead only): <https://hybrid-analysis.com/sample/b2db2a06a54db03c5106958d904f952bdd6960ff1969746cbea395f492503dfa/62a9f645a51af52e800f797c>
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
