# Icon fidelity and real requests — 8 September 2026

Scope: the **Classic Icon Pack after v1.8.6**, with shared coverage regenerated
for Pop and Pixel Neon. This is a targeted correction, **not a claim that all
925 catalog entries now use verified official logos**. There are still 614
letter-tile entries. Counts include regional/build variants, not 925 unique
services.

![Fidelity pass and NoBuffr preview](../icon-fidelity-preview.png)

## What was checked and changed

### 1. Use real silhouettes where there is a trustworthy source

Sixteen existing brands now render the source silhouette rather than a loose
invention or a letter in a box:

| Brand | Correction |
|---|---|
| Netflix | Filled ribbon silhouette instead of slanted monoline sticks; monochrome, not the vendor's multi-red shading |
| Spotify | Filled disc with three transparent curved counters; logo green `#1ED760` |
| Kodi | Diamond/K construction, not a K in a rounded square; `#17B2E7` |
| Jellyfin | Rounded triangular construction, not two straight outlined triangles |
| Stremio | Diamond/play silhouette, not a rounded-square play; `#685CEE` |
| Crunchyroll | Circular asymmetric crescent, not a horizontal eye; `#FF5E00` |
| Twitch | Glitch/speech-mark silhouette and counters |
| NordVPN | Mountain/dome silhouette, not an invented shield/arrow |
| MUBI | Seven dots in **2–3–2 rows**, not 3–3–1; visible light-ink treatment on dark cards |
| Deezer | Current heart/equaliser silhouette, not the older generic columns; `#A238FF` |
| Proton VPN | Triangular ribbon mark, not a generic shield |
| Plex | Actual wordmark rather than a boxed chevron; banner uses the wordmark alone |
| Paramount+ | Mountain/star silhouette; Canada/TVE variants no longer get unrelated letter tiles |
| YouTube | Source button silhouette with a **transparent** play counter, not a dark painted plug |
| YouTube Kids | Distinct slanted button silhouette |
| YouTube Music | Disc/ring/play silhouette |

These are locally pinned Simple Icons vectors, checked against the upstream
brand-reference URLs recorded in the catalog. The individual source URL,
immutable upstream revision, SHA-256, review date and treatment are in
[`tools/catalog.json` → `artwork`](../../tools/catalog.json). Builds never fetch
logos from the network. Rights and adaptations are explicit in
[`THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md).

**Do not treat the old `docs/logo-research/` text as authority.** It is a dated
research snapshot and contains mistaken descriptions (MUBI's dot arrangement
is one example). This pass checks source geometry, not that prose alone.

### 2. Correct iPlayer, including the previous release's mistaken colour claim

BBC's 2021 service refresh introduced three blocks/beams in a play formation
**in shades of pink**. The v1.8.6 claim that BBC iPlayer should be BBC News red
is not supported by that reference. All three iPlayer variants now share
`#FF4C98` and an original, single-pink three-beam construction. This is a
reference-informed adaptation, not an imported/officially approved BBC asset.
[1](https://www.designweek.co.uk/issues/18-24-october-2021/bbc-logos-update/)

The same-identity groups enforced by the generator and tests are:

- BBC iPlayer / Freeview / TV
- 9Now / 9Now CTV
- Paramount+ / Canada / TVE
- Syncler / alternate package / Beta
- Weyd / Weyd Player
- SmartTube / SmartTube Next
- tvQuickActions / free build

The last four and 9Now are **consistency fixes to the existing primary entry**,
not a claim of newly verified vendor artwork or colours. Separate products and
forks are not automatically merged just because their names resemble each
other. Existing drawable names and launcher mappings are retained.

### 3. Keep dark colours readable without randomly changing brand hue

Classic's recommended card is `#0D1117`. Source accents below a **3:1** contrast
ratio use one light ink, `#E6EDF3`, in squares, banners and the preview. This
currently affects 79 entries, including black/near-black MUBI, Apple TV and
Pluto TV artwork. The source accent stays in the catalog for provenance and
for the sibling packs; this is a display treatment, not a rewritten brand
colour. The generated app list exposes both values.

This is a practical dark-card floor, **not** a guarantee of contrast on every
wallpaper/card colour, nor a claim of accessibility certification. Pop and
Pixel Neon retain their deliberately different palettes. YouTube's counter
and the equaliser faders now use real alpha, so they do not show a dark plug
when the user changes their card colour.

### 4. Add NoBuffr from the actual APK supplied

NoBuffr is an M3U/XTREAM player whose vendor lists Android and Android TV among
its supported platforms. This task is a direct user request, not an inference
that it has the highest market share. [1](https://nobuffr.com/)

The supplied link was successfully fetched on a GitHub Actions runner after
the workspace could not reach its host directly. The APK was **statically
inspected, not executed**:

- URL: <https://downloads.nobuffr.com/android/nobuffr.apk>
- Observed version: `1.0.0` / `210246`; 33,628,207 bytes.
- SHA-256: `ec835a672087b5cb56700ddc3ed4e050519f5829d10e86800d5506d79afda5bf`.
- Actual component: **`com.nobuffr.app/tv.tivitime.compose.app.AppActivity`**.
- Its launcher activity declares MAIN intents with `LAUNCHER` and `LEANBACK_LAUNCHER`.
- White stacked lettering and the interrupted underline come from the APK's
  adaptive foreground, not an invented N/play icon. The source gradient is
  intentionally flattened to a cyan underline in Classic.
- Square and banner resources, browser arrays and auto-assignment mappings
  exist in all three packs. The generated Classic app list also links directly
  to the supplied download.

Reproducible receipt, artwork resources and method:
[`tools/reference/nobuffr/`](../../tools/reference/nobuffr/). The activity is in a
different namespace: inventing `com.nobuffr.app/.MainActivity` would silently
fail. No APK is bundled in this repository or in an icon-pack APK. The
one-off branch fetch trigger has been removed.

## Which icons do people actually need next?

### Method and limitations

This is a **qualitative request audit**, not a popularity poll. I checked named
requests in public Projectivy threads, a maintainer discussion, official store
links and the live catalog by **name, drawable and package**. A missing word in
a README is not evidence that an icon is absent. The reference pack's GitHub
issue tracker was disabled when checked, so it cannot supply a reliable live
ranking; no issue counts or download-based demand rankings are invented here.

Community requests explicitly call out full transparent coverage and working
automatic mapping: a mixture of themed and unthemed apps is the complaint, not
simply wanting a larger advertised number.
[1](https://www.reddit.com/r/Projectivy_Launcher/comments/1mjkdhs/request_transparent_icon_pack/)

### Prioritised queue

| Priority | Request | What the evidence says | Our status / next action |
|---|---|---|---|
| P0 | **NoBuffr** | Direct request in this task; vendor Android/TV support [1](https://nobuffr.com/) | **Added** with APK-verified component and source-derived mark |
| P1 | **TDUK Cache Cleaner** | Explicit request, including alongside Unlinked and Analiti [3](https://www.reddit.com/r/Projectivy_Launcher/comments/1j3848y/borderless_transparent_icons/) | Genuine catalog gap. Obtain a legitimate APK/device launcher dump and current logo before adding |
| P1 | **TDUK App Killer** | Named with Cache Cleaner in a separate launch thread [2](https://www.reddit.com/r/Projectivy_Launcher/comments/1mtmall/introducing_the_projectivy_icon_pack/) | Genuine gap; do not confuse it with the already covered TDUK Screensaver Manager |
| P1 | **Tata Play Binge** | Explicit request with a Play link [1](https://www.reddit.com/r/Projectivy_Launcher/comments/1j3848y/borderless_transparent_icons/) | Genuine gap. Verify the actual TV build/activity, not just mobile package `com.tataskymore.open.uat` |
| P1 | **Wholphin artwork** | Dedicated icon request; maintainer points to the app's source artwork [1](https://github.com/damontecres/Wholphin/discussions/312) | Already mapped as `damontecres_2`, but still a D tile. **Fidelity fix**, not an extra icon count |
| P2 | **Beacon Game Launcher** | Explicit Projectivy request [2](https://www.reddit.com/r/Projectivy_Launcher/comments/1mtmall/introducing_the_projectivy_icon_pack/); official store identifies it as a launcher, not an emulator [2](https://play.google.com/store/apps/details?id=com.radikal.gamelauncher&hl=en_US) | Genuine gap. Verify TV/controller use and a legitimate installed build's component; do not use a cracked APK |
| P2 | **Nuvio TV artwork** | Explicit request in the transparent-icon thread [1](https://www.reddit.com/r/Projectivy_Launcher/comments/1j3848y/borderless_transparent_icons/) | Already `nuvio`; next source/fidelity pass, not new coverage |
| P2 | **ZEE5 current logo** | The request specifically asks for the new logo [1](https://www.reddit.com/r/Projectivy_Launcher/comments/1j3848y/borderless_transparent_icons/) | Already `graymatrix`; check current identity before another redraw |
| P3 | **ActionDash / Wavelet** | Named in the same request list [1](https://www.reddit.com/r/Projectivy_Launcher/comments/1j3848y/borderless_transparent_icons/) | Not present. Establish TV relevance/launchability first; phone-list requests alone do not outrank TV gaps |

**Why Cache Cleaner is not an automatic addition yet:** the reference pack's
own historical release labels it and App Killer with an asterisk meaning
**manual assignment only**. Copying that pack's existence is not evidence of a
working component. [1](https://newreleases.io/project/github/SicMundus86/ProjectivyIconPack/release/1.0.1)

### Common requests with existing mappings

The public thread also requests NordVPN, UniFi Protect, Tubi, Pluto TV, S0undTV,
PBS, tvQuickActions, GeForce Now, File Manager+, Button Mapper, PrivadoVPN,
Background Apps and Process List, Aerial Views, Proton VPN and others.
[1](https://www.reddit.com/r/Projectivy_Launcher/comments/1j3848y/borderless_transparent_icons/)

Catalog examples worth checking before counting another “new” icon:

| Requested name | Existing drawable |
|---|---|
| ZEE5 | `graymatrix` |
| Analiti | `fastest` |
| GeForce Now | `tegrazone3` |
| File Manager+ | `cxinventor` (its package shares the CX File Explorer entry; identity review needed) |
| Wholphin | `damontecres_2` |
| S0undTV | `s0undtv` |
| Nuvio TV | `nuvio` |
| Kayo / Kick / Unlinked | `kayo` / `kick` / `unlinked` |

Mappings are established by the local catalog, **not by this external thread**.
File Manager+ is an important exception to treating a mapping as finished
coverage: `com.alphainventor.filemanager` currently shares the CX File Explorer
entry, so a separate, source-checked identity is a follow-up, not a reason to
claim that its requested logo is already correct.
For an already-covered app, diagnose activity/package drift or logo quality
before creating another drawable. In particular, this pass changes **924 →
925**, not an inflated count including existing aliases.

## Admission and regression rules

1. A request needs an official store/download link and, preferably, a TV
   launcher component. A real APK can be statically inspected when the requester
   cannot supply the component. Never invent a package/activity to make a count.
2. Record source, version/hash, observed launcher categories and artwork
   provenance. “Manifest verified” and “tested on a physical TV” are distinct.
3. Check current catalog names, drawables and packages, including regional
   variants and existing apps whose drawables are named after developers.
4. Use a source-checked mark or explicitly retain an original fallback. Do not
   describe a generated monogram as the official logo.
5. Regenerate Classic, Pop and Pixel Neon together and test their mappings.

`tests/test_icon_identity.py` covers the colour policy, brand groups, source
checksums, 432px proportional fitting, transparent counters, MUBI's dot layout,
NoBuffr's source silhouette and mapping in all three packs, and safe static
inspection of launcher activities/aliases. The Pixel Neon validator now derives
coverage from the catalog/suite instead of pinning yesterday's icon count; it
also checks the exact component-to-drawable map, not just the number of rows.

**Not yet verified:** physical Projectivy auto-assignment on a phone/TV, tinted
card behaviour on several real panels, and any unresearched long-tail logo.
Android compilation/lint results must be recorded separately from the Python
asset checks; a successful asset generator is not an APK build.
