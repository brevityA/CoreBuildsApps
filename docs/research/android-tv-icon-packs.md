# Android TV icon packs — landscape, spec gaps, and how to win

Field research, September 2026. Follow-up to
[`iconpack-demand-2026.md`](iconpack-demand-2026.md), which answered *what style
to build*. This one answers *what makes a TV icon pack good*, and where Core
Builds is leaving value on the table.

Everything below is sourced. Where something needs a real device to confirm, it
is labelled **UNVERIFIED** rather than asserted.

---

## 1. The launcher landscape (this is the whole addressable market)

An icon pack on TV is worth exactly as much as the launchers that can apply it.

| Launcher | Icon-pack support | Status | Notes |
|---|---|---|---|
| **Projectivy** | ✅ full ADW appfilter, since 4.60 | Active, v4.71 (Jul 2026), 1.8k stars | **Premium-gated.** Also: per-icon tinting, card ratio 16:9 or 1:1, APNG icons |
| **ATV Launcher Pro** | ⚠️ per-app custom banner only | Active, paid (~£2.79) | No pack format — user picks images one by one |
| **Wolf Launcher** | ⚠️ per-app custom banner only | **Dead** — no longer updated; fork of ATV | Troypoint now steers users to ATV |
| **FLauncher / LTvLauncher** | ⚠️ custom banner support | Active, open source, Downloader `7259827` | Fork adds "Custom Banner Support" |
| **HALauncher / Sideload Launcher** | ❌ | Maintenance | |
| **Google TV stock** | ❌ | — | No icon pack support at all, ever |

**Conclusion: Projectivy is the market.** It is the only TV launcher with a real
icon-pack engine. Everything we do should be tuned to it first and standards-
compliant second. That is already how the pack is built — but it means
Projectivy's *release notes* are our roadmap, and we have not been reading them.

### What Projectivy shipped recently that we have not reacted to

From [spocky/miproja1 releases](https://github.com/spocky/miproja1/releases):

- **4.70 (Jun 2026): "Added ability to map Projectivy's internal activities in
  Icon Packs"** ([issue #512](https://github.com/spocky/miproja1/issues/512)).
  Settings, category shortcuts, channel shortcuts, and **HDMI/AV input**
  shortcuts can now be themed by an icon pack. This is three months old and, as
  far as I can find, **no pack ships it**.
- **4.70: theme support** (`.pltheme`, a ZIP stored uncompressed) with a
  wallpaper provider that reads wallpapers out of the applied theme.
- **4.70: font customization** and **wallpaper tinting**.
- **4.70: "immediate refresh of the launcher screen when an icon pack is
  updated/uninstalled"** — the long-standing "restart your device after
  updating the pack" complaint is fixed upstream.

The internal-activities one matters most because there is a **documented,
unanswered user request for exactly it**:

> "I don't even want anything that crazy, just a banner shaped icon for
> SmartTube and TV (which are both currently very ugly) **and my HDMI outputs
> so I don't have the square HDMI icon that Projectivy use which don't fit with
> the other app icons**"
> — jirojiz, [r/Projectivy_Launcher](https://www.reddit.com/r/Projectivy_Launcher/comments/1icfbn7/how_to_i_install_icon_packs_also_can_i_make_my/)

That user is describing a hole nobody has filled, in a feature that now exists.

---

## 2. Competitors, current state

| Pack | Icons | Distribution | Notable |
|---|---:|---|---|
| [SicMundus86 ProjectivyIconPack](https://github.com/SicMundus86/ProjectivyIconPack) | 800+ | GitHub + **Play Store** + Downloader `9257057` | 445 stars. Built on Blueprint. Transparent. **Structured GitHub issue templates** for icon requests *and* mapping bugs. Ships a "latest additions" section. v1.0.9, 14 MB |
| [hqn-scl minimalist](https://github.com/hqn-scl/android-tv-minimalist-icon-pack) | — | GitHub + Downloader `7305118` | Unified background colour, symbols over wordmarks |
| Mortisshadow Minimal TV | — | — | The original |
| Apple TV-style pack | — | GitHub, posted to r/Projectivy_Launcher | New entrant, free |
| **Core Builds Icon Pack** | **924** | GitHub + Downloader `5270601` | Largest. Ships banners *and* squares, 70 wallpapers, in-app updater |
| **Core Builds Pop** | **924** | not yet released | This branch |

We are the biggest pack on the platform by a comfortable margin, and the only
one with an in-app wallpaper browser and a self-updater. SicMundus86 beats us on
**distribution and community process**, not on the artefact.

---

## 3. What we already get right (do not regress these)

Worth stating, because several are non-obvious and were expensive:

1. **We ship 320×180 banners.** This is the single biggest thing. The community
   consensus is unambiguous — *"320x180 that's the official size for TV
   banners"*, and *"Anything smaller gets scaled up and anything bigger gets
   scaled down and look shitty. Also take up extra memory."* Our banners are
   exactly 320×180. Meanwhile users are actively asking where to find
   banner-shaped packs: *"all square format which I don't want. So there isn't
   anywhere that has pre made banners?"*
2. **`appfilter.xml` maps to the banner form**, so auto-assign produces 16:9
   cards, which is Projectivy's default card ratio.
3. **Both name forms per component** (`com.foo/com.foo.MainActivity` and
   `com.foo/.MainActivity`) — 1,660 entries for 1,098 components. This is why
   our auto-mapping hit rate is high.
4. **14 launcher intent-filters** including both of Projectivy's
   (`com.spocky.projengmenu.APPLY_ICONPACK` and `…icons.ACTION_PICK_ICON`), plus
   Nova, ADW, Apex, Lawnchair, Sony, OnePlus, Turbo, GO.
5. **`<queries>` for 17 launchers**, so one-click apply survives Android 11+
   package-visibility filtering without `QUERY_ALL_PACKAGES`.
6. **`drawable.xml` lists all 1,848** (square *and* banner), so a user who sets
   1:1 cards can hand-pick the square form from Projectivy's own picker.

---

## 4. Gaps, prioritised

### P0 — Fallback masking for unthemed apps (`iconback` / `iconmask` / `iconupon` / `scale`)

**We ship none of these.** Confirmed: `grep -c "iconback\|iconmask\|iconupon"`
returns 0 in both packs' appfilter.

This is the ADW standard's answer to "the pack doesn't cover my app". The
launcher composites the app's own icon onto pack-supplied furniture: a
background, an optional clip mask, an optional overlay, and a scale factor.
[Lawnchair's spec](https://github.com/LawnchairLauncher/lawnchair/wiki/Icon-pack-support)
documents it; Nova, Apex, ADW, Lawnchair and Blueprint-based packs all support it.

```xml
<iconback img1="iconback_red" img2="iconback_blaze" ... />
<iconmask img1="iconmask" />
<iconupon img1="iconupon" />
<scale factor="0.62" />
```

Why this is a bigger deal for **Pop** than for the classic pack:

- The classic pack is deliberately transparent — the launcher owns the card
  colour. There is no container to give an unthemed app, so `iconback` would
  contradict the design.
- **Pop's entire thesis is one container.** An unthemed app currently breaks
  that thesis on sight. With `iconback`, every app the user has — not just our
  924 — gets the Pop field, the halftone and the ink keyline. Coverage goes
  from "924 icons" to **"every app on your device"**.

This is precisely what the leading commercial competitor advertises: Comics —
Cartoon Icon Pack sells "auto-masking for unthemed apps" as a headline feature.

Multiple `iconback` images are allowed and the launcher picks one per app, so
Pop can ship **all 16 swatches** as backs and unthemed apps land across the full
palette. Cost: 16 small PNGs.

> **UNVERIFIED:** whether Projectivy honours `iconback`/`iconmask`/`iconupon`.
> Its engine reads appfilter, but I found no confirmation either way. The tags
> are optional and ignored by launchers that don't implement them, so shipping
> them is free insurance — and they definitely work in Nova/Lawnchair/Apex,
> which our manifest already advertises support for.
>
> **UNVERIFIED:** `iconmask` alpha polarity differs between implementations.
> Ship it, then check on hardware.

### P0 — Theme Projectivy's own internal activities (new in 4.70)

Free, zero competition, and answers a real posted request. Targets from
[issue #512](https://github.com/spocky/miproja1/issues/512), both name forms
each (22 entries):

| Activity | Icon |
|---|---|
| `…ui.settings.SettingsActivity` | `gear` |
| `…AppSettingsActivity` | `gear` |
| `…CategoryShortcutActivity` (×2 paths) | `folder` |
| `…ChannelShortcutActivity` (×2 paths) | `tv_stack` |
| `…input.SourceHDMI1/2/3Activity` | HDMI marks |
| `…input.SourceAVActivity` | AV mark |

We already have `gear`, `folder`, `tv_stack`, `launcher_grid`, `home_button`
and `remote` in `tools/glyphs.py`, so this is mapping work, not art work.

We currently map only 3 `projengmenu` activities (6 entries) — the launcher
itself, its TV-input activity, and its main activity.

### P1 — Community process: icon requests and mapping bugs

SicMundus86 has **structured GitHub issue templates** for "request an icon" and
"report a mapping issue", and documents the exact failure modes. We have
neither. This is the cheapest quality multiplier available: mapping bugs are
the #1 complaint in every TV pack thread, and they are *reported by users* if
you make it easy.

Their troubleshooting doc is worth copying wholesale, because every one of these
will land in our issues too:

- Manually-assigned icons are **never** overridden by a pack — user must reset
  each one first.
- After updating a pack, you must **re-apply** it for new icons to attach.
  (Mitigated upstream in 4.70's "immediate refresh", but not for older builds.)
- Launcher icon cache can serve stale icons — restart the device.
- **If an app changes its launcher activity, the card breaks and the app won't
  open.** Worth monitoring; our 1,660 dual-form entries make us more resilient
  here than a single-form pack, not less.

### P1 — The 1:1 card ratio question

Projectivy lets users set card ratio to 1:1, and at least one popular pack
recommendation on r/Projectivy_Launcher tells users to do exactly that. Our
`appfilter.xml` maps every component to the `_banner` (16:9) form, and appfilter
allows **one drawable per component** — there is no way to express "square for
1:1 cards, banner for 16:9".

So a 1:1 user who applies our pack gets 924 letterboxed or cropped banners, and
their only remedy is to hand-pick 924 square icons from `drawable.xml`.

> **UNVERIFIED:** whether Projectivy letterboxes or centre-crops. Centre-crop
> would be mostly fine (our marks are centred); letterbox would look bad.
> One screenshot on real hardware settles it.

Options if it turns out to be letterbox:
- Ship a square-mapped variant APK (cheap — it is one line in the generator).
- Or make the banner composition safe under a 1:1 centre crop, which it broadly
  already is.

### P2 — Dynamic calendar

`<calendar prefix="calendar_"/>` plus 31 drawables gives a date-aware calendar
icon. Standard, widely supported, and a visible "this pack is thorough" signal.
Low value on TV specifically (few people put a calendar on their TV home
screen), so it is genuinely P2 — but it is ~40 lines in the generator.

### P2 — A Projectivy `.pltheme`

4.70 added a theme format: a ZIP (stored, uncompressed) that can carry
wallpapers, and 4.70 also added a wallpaper provider that pulls from the applied
theme. Core Builds Pop already generates 12 matching wallpapers. Packaging them
as a `.pltheme` would make "install pack → install theme → done" a single
coherent experience, which no other pack offers.

Needs the format documented or reverse-engineered first.

### P2 — Themed icons / `grayscale_icon_map.xml`

Lawnchair and Google's Themed Icons standard. Near-zero value on Android TV
(Material You theming isn't a TV surface) but it is the single most requested
feature on the phone side, and our pack does declare phone-launcher intent
filters. Only worth it if we ever target phones.

### P3 — Distribution

This is where SicMundus86 genuinely beats us:

- **They are on the Play Store.** We are GitHub + Downloader only.
- Their launch was posted to r/Projectivy_Launcher, r/ShieldAndroidTV **and**
  the XDA Projectivy thread, and picked up by all three.
- Downloader codes are the standard install path on TV — a code is table stakes.
  **Pop does not have one yet** (`suite.json` says `[USER TO SUPPLY]`).

Also worth noting: Projectivy's icon-pack feature is **premium-only**, so every
user of our pack has already paid for Projectivy. That is a small, motivated,
high-intent audience — the right place to spend effort is depth per user, not
reach.

---

## 5. Size and memory

Community guidance is explicit that oversized icons cost memory on TV boxes.
Current state:

| | Square PNG | Banner PNG | Total drawables |
|---|---:|---:|---:|
| Classic | 512×512 RGBA, ~8.9 KB | 320×180, — | 17 MB / 1,849 files |
| Pop | 512×512 indexed, ~11.6 KB | 320×180 indexed, ~5.5 KB | 16 MB / 1,849 files |

Both are fine. One observation: the classic pack's squares are **RGBA
truecolour** while Pop's are indexed. Running the classic pack's PNGs through
the same 64-colour quantisation Pop uses would likely cut several MB with no
visible change — the art is flat vector output either way. Worth measuring.

---

## 6. Scorecard

| Capability | Classic | Pop | Best competitor |
|---|:--:|:--:|:--:|
| Icon count | **924** | **924** | 800 |
| 320×180 banners | ✅ | ✅ | ❌ (most are square) |
| Both component name forms | ✅ | ✅ | ? |
| Projectivy intent filters | ✅ | ✅ | ✅ |
| `<queries>` for Android 11+ | ✅ | ✅ | ? |
| One-click apply | ✅ | ✅ | ✅ |
| In-app icon browser | ✅ | ✅ | ✅ |
| In-app updater | ✅ | ✅ | ❌ |
| Bundled wallpapers | ✅ 70 | ✅ 12 | ❌ |
| Uniform by construction | ❌ | ✅ | partial |
| **Fallback masking** | n/a by design | ❌ | ❌ |
| **Projectivy internal activities** | ❌ | ❌ | ❌ |
| **Icon-request process** | ❌ | ❌ | ✅ |
| Dynamic calendar | ❌ | ❌ | ❌ |
| Play Store listing | ❌ | ❌ | ✅ |
| Downloader code | ✅ | ❌ | ✅ |

Three of the four bolded rows are things **nobody** has. That is the shortest
path to "best on the platform".

---

## 7. Recommended sequence

1. **Fallback masking for Pop** — 16 `iconback` swatches + mask + upon + scale.
   Turns "924 icons" into "every app you own".
2. **Projectivy internal activities** — both packs. Answers a posted request,
   nobody else has it.
3. **Issue templates** for icon requests and mapping bugs, plus a
   troubleshooting section in the README.
4. **Get a Downloader code for Pop** and post the release to
   r/Projectivy_Launcher, r/ShieldAndroidTV and the XDA thread.
5. **Device pass**: verify mask polarity, 1:1 card behaviour, and that
   `iconback` is honoured. One evening with a TV settles all three.
6. Then consider: `.pltheme` bundle, dynamic calendar, classic-pack PNG
   quantisation.

**Items 1 and 2 are implemented on this branch, for Pop only.** The classic
pack is transparent by design, so `iconback` would contradict it; the
Projectivy internal cards *should* be back-ported to classic and are the
obvious next commit. The rest need either a device or a decision.

### What shipping items 1 and 2 turned up

Adding the internal cards surfaced a latent bug in `popart.snap()`: it was not
idempotent. `snap(SWATCHES["pop_slate"])` returned `pop_marine`, because the two
neutral swatches are selected by saturation/value thresholds that their own hex
values do not satisfy. Nothing in the icon pipeline ever snapped twice, so it
was invisible — until something asked for a specific swatch *by value* and
silently got a different colour. The internal cards came out blue.

Fixed by making `snap` exact-match its own palette first. Verified that no
catalog accent equals a swatch hex, so none of the 924 icons moved. The
invariant is now checked in `validate_pop.py` and `tests/test_pop.py`:

> A palette you cannot round-trip through your own mapper is a trap.
