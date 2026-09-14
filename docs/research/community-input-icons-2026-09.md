# Community gap audit — Nuvio / Stremio stacks and input icons

Date: 2026-09-14 · Status: research only, nothing implemented
Question (owner): *"Deeply research what else we maybe lacking. Based on the Nuvio or
Stremio community, should we also add more input icons?"*

## 1. Answer

**Yes — but the gap is narrower and more specific than "more input icons".**

1. **A flagship community app (Nuvio TV) is very likely not themed on real devices at
   all** — our mapping targets its deep-link activity, not its launcher activity
   (§6). Fix is 12 components on the existing entry, zero new art. **P0.**
2. **Classic is missing the launcher's internal cards** that Pop already ships:
   Settings, Categories, Channels, and the **AV input** (`SourceAVActivity`).
   Zero new glyphs; ≤3 new drawable files (banner pairs) rendered from existing
   glyphs (§4). **P1** — with a ratchet trade-off the owner must accept (§7).
3. **Numbered HDMI input cards (HDMI 1–4 as four distinguishable marks)** are the
   literal community complaint, but ship only after one on-device check
   (does Projectivy print the input name on the card?). If it does, numbering is
   unnecessary and the unified `hdmi_source` mark stays. **P2**, decision-gated.
4. Everything else in the Nuvio/Stremio community stack is **already covered**
   (§3). Explicit non-recommendations in §8.

## 2. Method and sources

- Local: catalog (933 icons) cross-referenced against the community app lists below;
  `tools/build_pop.py:274-322` (Pop internal cards); both appfilters (Projectivy
  component coverage); `tools/glyphs.py` (glyph availability);
  `docs/research/iconpack-design-upgrade-2026-09.md` §7/§9.
- Web (2026-09-14):
  - r/Nuvio "Android TV BETA out" — https://www.reddit.com/r/Nuvio/comments/1q5xf3f/
  - r/AndroidTV "best apps for watching movies and TV" — https://www.reddit.com/r/AndroidTV/comments/1u5eyfq/
  - r/firestick "New streaming app Nuvio" — https://www.reddit.com/r/firestick/comments/1rh5ual/
  - r/Nuvio "Which streaming app is actually the best?" — https://www.reddit.com/r/Nuvio/comments/1s684n0/
  - r/Stremio "Since Stremio is removed from the Google Play Store…" — https://www.reddit.com/r/Stremio/comments/1px29cb/
  - r/Projectivy_Launcher "How do I install icon packs? Also can I make my own"
    (the request thread cited by the design-upgrade doc; launcher dev **Spocky_12
    answers in-thread**) — https://www.reddit.com/r/Projectivy_Launcher/comments/1icfbn7/
  - Projectivy Launcher GitHub (spocky/miproja1) + Google Play listing — feature list
    and activity naming.
  - Nuvio TV official repo (NuvioMedia/NuvioTV, `dev` branch, last commit 2026-09-12):
    `app/build.gradle.kts` + `app/src/main/AndroidManifest.xml` (read raw).

## 3. Findings A+B — the two communities and what they actually run

### What Nuvio is

Nuvio (NuvioMedia, dev "tapframe"/CrissZollo) is the debrid-first successor to the
DebridStream ecosystem: Stremio-addon-compatible media hub, mobile + Android TV,
open source, 2.6k GitHub stars, `com.nuvio.tv` version **0.9.2-beta** with near-daily
commits. Community signal (verbatim, 2026): *"Nuvio is the top dog"* (r/AndroidTV),
*"Torbox + nuvio = end game"*, *"We've all jumped ship to Nuvio"* (r/firestick),
*"Nuvio has the most options… playback seems better"* (r/Nuvio). Its TV build is
exactly the Android TV / Projectivy-launcher user base our pack serves.

### The canonical stack, cross-referenced against our catalog

| Community app (Nuvio + Stremio circles) | In catalog? |
|---|---|
| Nuvio TV | ✅ "Nuvio TV" (gradient, `nuvio_plays`) — **but mapping bug, §6** |
| Stremio (`com.stremio.one`) | ✅ |
| TorBox | ✅ |
| Real-Debrid + Real-Debrid app | ✅ |
| AllDebrid | ✅ |
| Premiumize (+ TV build `pmtvfire`) | ✅ |
| Trakt | ✅ |
| Downloader (AFTVnews, `com.esaba.downloader`) | ✅ — the sideload path after Stremio left Play |
| Wuplay, Arvio, STRMR, Wako, Debrid Stream | ✅ all |
| Syncler (+ beta) | ✅ |
| Kodi, VLC, MX Player (+ TV), Nova Video Player | ✅ |
| TiViMate (IPTV), Plex, PlexAmp, Jellyfin (+ Enhanced), Emby | ✅ |
| Add-ons: Torrentio, Comet, Meteor, MediaFusion, AIOStreams, CinemaHD | n/a — they live **inside** Nuvio/Stremio; not apps, no launcher icon |
| "Streamio" (several r/AndroidTV + r/Stremio mentions) | = community typo for Stremio (see r/Stremio install thread); `streamio.click` is an unrelated IPTV service. Nothing to add. |
| EasyNews | n/a — used as a provider *inside* AIOStreams/Nuvio; no meaningful standalone TV app found. No icon. |
| Vanced/ReVanced | no community signal in either stack; skip. |

**Conclusion: the app-coverage side of the question is essentially done.** The two
stacks are ~100% covered at app level; the residual gaps are (a) the Nuvio mapping
bug and (b) the launcher's *internal* input/shortcut cards — which is precisely the
"input icons" question.

Context worth recording: Stremio was **removed from Google Play (Dec 2025)**; the
community now sideloads via Downloader (mapped ✅) or Obtainium. Nothing actionable
beyond the coverage already in place.

## 4. Findings C — the input-card gap (the actual "input icons" question)

### 4.1 The demand is real, old, and still unfilled

The request thread behind the design-upgrade doc is
r/Projectivy_Launcher `1icfbn7` (Jan 2025). The user's words:

> "I don't even want anything that crazy, just a banner shaped icon for SmartTube
> and TV (which are both currently very ugly) **and my HDMI outputs so I don't have
> the square HDMI icon that Projectivy use which don't fit with the other app
> icons**."

The launcher dev's answer was "sideload Arcticons or drop 16:9 PNGs via change-icon
→ from picture". The thread is from **18 months ago and nothing has shipped** — as
of April 2026 the sub is still trading the same packs.

### 4.2 The launcher advertises input cards as a core feature

Projectivy Launcher (Play Store + repo README): *"Input Source Shortcuts: Direct
access to HDMI, AV, and other input sources"*, plus *"HDMI inputs can be renamed and
hidden"*, *"launcher shortcuts (hdmi inputs, media explorer) can now be hidden if not
used"*. Anyone who replaces the stock launcher **loses the stock input widget** and
relies on these internal cards — so input cards appear on a large fraction of real
Projectivy home screens.

### 4.3 No competing pack covers them (the white space)

- **Projectivy Icon Pack** (SicMundus86 — the community default; its decoded
  appfilter is our reference pack): maps exactly **one** Projectivy component
  (`ui.home.MainActivity` → launcher icon). No inputs, no internal cards.
- **Arcticons** (the pack the launcher dev recommends): mobile-oriented; no
  Projectivy-internal coverage.
- **miityharu's high-res set** (Google Drive PNGs, not an APK pack): user requests
  are app names only (Kick, TDUK Cache Cleaner, …); no input cards.

Whoever ships correct banner-shaped input cards first owns a documented,
long-unmet request from the launcher's own community — with the launcher dev
visible in-thread. This is territory Core Builds pioneers; it cannot be inherited
from the reference pack (§4.4).

### 4.4 What we have today (local evidence)

- **Classic** (`app/…/appfilter.xml`): `hdmi_source` → all 8 `SourceHDMI{1-4}Activity`
  forms (plain + `ui.guidedActions` × short + fully-qualified), all 8 **unverified**
  (in the 69 ratchet). No AV, no settings/categories/channels cards.
- **Pop** (`tools/build_pop.py:274-322`): ships `pl_gear` (Settings —
  `ui.settings.SettingsActivity` + `ui.guidedActions.activities.settings.AppSettingsActivity`),
  `pl_folder` (Categories — `CategoryShortcutActivity` both name forms),
  `pl_tv_stack` (Channels — `ChannelShortcutActivity` both forms), `pl_av`
  (AV input — `ui.guidedActions.activities.input.SourceAVActivity`). Pop's
  numbered `hdmi1-4` tiles are **dormant** — the catalog's unified `hdmi_source`
  claims the same components first, so the numbered cards never emit.
- Glyphs: `gear`, `folder`, `tv_stack`, `monitor_wave`, `hdmi_connector`,
  `home_button`, `remote` all exist in `tools/glyphs.py`; `monitor_wave` is already
  in Pixel-Neon's special-case set (build_pixel_neon.py:1432). `settings.png`
  (gear) exists in `app/src/main/res/drawable-nodpi/` for reuse.

### 4.5 Naming risk (must keep)

The dev, in the XDA thread, on the `guidedActions` namespace: *"it might change
again in the future as I've just realized they shouldn't be classified in the
guidedActions"*. Our dual-form mapping (plain + `guidedActions`, short + fully-qualified)
is the correct hedge — every new internal entry should ship in the same 2-form (or
4-form, for HDMI-style families) pattern, and `docs/ADB_SCANNING.md` should note a
rescan after each launcher major version.

## 5. Recommendation detail

### P0 — Fix Nuvio TV's launcher activity (zero new art, zero ratchet cost)

Official `com.nuvio.tv` manifest (`dev`, read 2026-09-14) declares **six** launcher
activities, five of them disabled alternate icons the user can switch to in-app
(commit `fa8e726`, "add custom launcher icon and banner", 2026-08-29):

- `.launcher.AppIconDefault` (enabled)
- `.launcher.AppIconArcticBlue` / `AppIconEmerald` / `AppIconRoseGold` /
  `AppIconCopper` / `AppIconGraphite` (disabled until selected)

`.MainActivity` (what we currently map) carries **only** `nuvio://` + `stremio://`
deep-link filters — it is not a launcher activity. A launcher resolves the app icon
from the LAUNCHER/LEANBACK_LAUNCHER activity, so **the Nuvio icon in our pack very
likely never appears on a real device** — on the exact app that is the current
community flagship. Fix: add all six `AppIcon*` activities (short + fully-qualified
= 12 components) to the existing "Nuvio TV" entry. Existence is **manifest-verified**
(stronger than the guessed forms behind the HDMI unverifieds) → no `unverified`
flags, no ratchet movement. Receipt: mixed row on the next receipt sheet (Nuvio
already has one).

### P1 — Classic internal cards: AV input + Settings/Categories/Channels (zero new glyphs)

New catalog entries, SYSTEM category, input-family graphite `#333A4B` for inputs:

| Entry | Drawable (new file?) | Glyph (exists) | Components |
|---|---|---|---|
| AV Source | `av_source` (new) | `monitor_wave` | `.ui.guidedActions.activities.input.SourceAVActivity` (+ plain form, guessed) |
| Projectivy Settings | reuse `settings` (gear, exists) | `gear` | `ui.settings.SettingsActivity`, `ui.guidedActions…settings.AppSettingsActivity` (2 forms each) |
| Projectivy Categories | `projectivy_categories` (new) | `folder` | `CategoryShortcutActivity` ×2 name forms (4 components) |
| Projectivy Channels | `projectivy_channels` (new) | `tv_stack` | `ChannelShortcutActivity` ×2 name forms (4 components) |

All eight catalog components are launcher-internal guesses (no public source, no
device) → **+8 unverified → ratchet ceiling 69 → 77** (the appfilter emits both
name forms, but the ratchet counts catalog components) unless the owner prefers
option B below. The design-upgrade doc already scoped this as "pure mapping work,
zero new art" (Step 1); it simply never shipped.

Option A: ship now with the +8 unverified bump, clear at the next ADB device scan.
Option B: owner runs `tools/scan_device.sh` on a Projectivy device first (the
workflow exists — `docs/ADB_SCANNING.md`), ship verified, no ratchet movement.
**My recommendation: A** — the AV activity at least has Pop-build corroboration,
and leaving the ratchet flat by deferring the cards keeps a real, documented
community request unfilled another cycle.

### P2 — Numbered HDMI 1–4 input marks (4 new glyphs, decision-gated)

The literal complaint ("can't tell HDMI 2 from HDMI 3 at a glance") wants four
distinguishable input marks. Design constraint: Classic has a no-`tile_*` test, and
`tile_1..4`-style numbering is exactly the generic pattern the diversity gate
(`.*_[A-Z0-9]`) penalises — so any numbering must be **bespoke** (e.g. the HDMI
connector mark with an integrated numeral), not letter/number tiles. Cost: 4 new
glyphs × icon+banner, measure_pop_glyphs pass, new receipt row.

**Decision gate first (5 minutes on a device):** does Projectivy 4.70 print the
input name ("HDMI 2") on the card itself? Inputs are renameable per the changelog;
if the card is labelled, the unified `hdmi_source` mark is already distinguishable
and numbering adds nothing. If unlabelled, the 4-mark family is the correct
response to the quoted complaint.

## 6. Findings D — why the Nuvio bug matters (evidence trail)

- `app/build.gradle.kts`: `applicationId = "com.nuvio.tv"`, `versionName = "0.9.2-beta"`.
- Manifest: `.launcher.AppIconDefault` = `MAIN`/`LAUNCHER`/`LEANBACK_LAUNCHER`;
  `.MainActivity` = `VIEW`/`BROWSABLE` on `nuvio` + `stremio` schemes only.
- Our catalog entry maps `com.nuvio.tv/.MainActivity`, `com.nuvio.tv/com.nuvio.tv.MainActivity`,
  `com.nuvio.app/…`, `com.nuviodebug.com/…` — none is a launcher activity.
- Repo history: alternate-icon launcher activities added 2026-08-29, i.e. **before**
  our v1.8.18 mapping (2026-09-14), so the bug predates nothing and was present
  when the entry was written.
- Same latent risk class may affect other fast-moving community apps; the ADB scan
  workflow is the standing check.

## 7. Ratchet and process impact (what the owner must sign off)

- P0: no test changes.
- P1 option A: `MappingHygieneTests` ceiling 69 → 77 (documented as a deliberate,
  device-clearable batch — same shape as the 533→69 verification tranche, which
  cleared 464 via scan).
- P1 option B: no test changes, cards ship after scan.
- P2: 4 new glyphs → `measure_pop_glyphs.py` before `build_pop.py` (standing rule),
  suite counts in `tests/test_pop.py` + CHANGELOG bumped in sync, mixed-row receipt
  with 48 px dock strip.
- If P0/P1 land: version-stamp surfaces only move at release (unchanged flow).

## 8. Explicit non-recommendations

- **EasyNews, AIOStreams, Torrentio, Comet, Meteor, MediaFusion, CinemaHD** —
  services/add-ons living inside Nuvio/Stremio; not launcher apps.
- **Streamio** — community spelling of Stremio (already mapped); do not create a
  duplicate entry.
- **Vanced/ReVanced** — no signal in either community's stack lists.
- **Console *input* activities** (dedicated Xbox/PlayStation source activities) —
  no evidence they exist as separate launcher activities; consoles are already
  mapped as companion apps (xbox, playstation, nintendo_switch).
- **APNG/glowing icons** — visible culture in r/Projectivy_Launcher (miityharu's
  APNG folder), but a format change, out of scope for a gap audit.

## 9. Open questions (need a device or the owner)

1. Does Projectivy 4.70 label input cards with the input name? (gates P2)
2. Do `SourceAVActivity`/`SettingsActivity`/`CategoryShortcutActivity`/
   `ChannelShortcutActivity` resolve on a real 4.70 build, in which name form?
   (clears the +8)
3. Confirm Nuvio icon applies after the P0 fix on-device (single screenshot).
4. ~~Owner preference on option A/B~~ resolved: option A shipped 2026-09-14.
