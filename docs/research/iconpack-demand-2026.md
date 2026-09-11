# What icon-pack users are actually asking for — 2026 field notes

Research behind **Core Builds Pop**. Written to answer one question: if the
suite ships a second icon pack, what should it be, and why that rather than
another minimal outline set?

Sources are linked inline. Where a claim comes from a vendor's own store
listing it is labelled as such — those are marketing numbers, not audits.

---

## 1. The market is two markets

Phone theming and TV theming look like the same hobby and are not.

**Phone theming** is mature and crowded. The long-running favourites lists on
r/androidthemes and r/androidapps are dominated by the same names for years:
Whicons, Delta, Borealis, Reev Pro, LineX, Arcticons, CandyCons, Viral. The
recurring asks are *coverage* ("20k icons", "covers almost everything in my app
drawer") and *consistency of shape*, not novelty of style.
[[androidapps: Fav android Icon Packs]](https://www.reddit.com/r/androidapps/comments/mwb1y5/fav_android_icon_packs/)
[[androidthemes: can you suggest some icon packs]](https://www.reddit.com/r/androidthemes/comments/1oiu5e0/help_can_you_guys_suggest_some_icon_packs/)

**Android TV theming** is small, young, and under-served. Projectivy only
gained icon-pack support relatively recently, and the packs that exist are
counted in single digits, not hundreds:

| Pack | Icons | Note |
|---|---:|---|
| [Projectivy Icon Pack](https://github.com/SicMundus86/ProjectivyIconPack) (SicMundus86) | 800+ | 445 GitHub stars. The reference implementation. |
| [Android TV Minimalist](https://github.com/hqn-scl/android-tv-minimalist-icon-pack) (hqn-scl) | — | Explicitly built on the two above |
| Mortisshadow's Minimal TV Icons | — | The original |
| **Core Builds Icon Pack** | 924 | This repo |

That is close to the entire field. A new *style* on TV competes against three
packs; a new style on phones competes against three hundred.

**Implication for us:** coverage is already won — 924 icons and 1,098 mapped
components is at or above every pack in the table. The open axis is style.

---

## 2. What the TV packs agree on

Read the three TV packs' own descriptions and the same five requirements fall
out, independently arrived at:

1. **One-click apply.** "One-click to apply the entire icon pack — no tedious
   one-by-one icon assignments."
   [[XDA, Projectivy thread]](https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/page-93)
2. **Automatic mapping.** Icons must auto-assign by component, or nobody uses
   them.
3. **Uniformity, named explicitly.** "Custom-made, uniform, sleek and
   minimalist icons." "All icons are custom-made to deliver a modern and
   **consistent** look."
4. **Transparent backgrounds** so the launcher owns the card colour — *or* a
   deliberate unifying background. hqn-scl's pack chose the second: "instead
   with a unifying background color similar to SicMundus86's work."
5. **Dark-first.** All three tell you to use a dark card background.

Point 3 is the interesting one. Every pack in this space claims uniformity as
its headline feature, which means uniformity is table stakes, and also means
nobody is differentiating on it. Point 4 says a unifying container is already
accepted practice on TV — it is not a violation of the norm, it is one of the
two norms.

---

## 3. Where the classic Core Builds pack actually sits

Measured from `tools/catalog.json`, not from the README:

| Property | Value | Reading |
|---|---:|---|
| Icons | 924 | Best-in-class coverage for TV |
| Distinct glyph shapes | 267 | Genuinely original geometry |
| Distinct accent colours | **169** | Effectively unbounded |
| Icons using a letter tile | **601 (65%)** | A letter in a rounded box |
| Container | none (transparent) | Launcher owns the card |

So the classic pack is *high quality and not uniform*, in a specific and
measurable way: it has 169 colours and mark sizes that vary by more than 2x,
and two thirds of it is monograms nested inside a box that Projectivy then
draws inside another box.

That is the gap Pop fills. Not "a second look for variety" — a second look
that is uniform by construction, from the same catalog, so coverage is
identical and can never diverge.

---

## 4. Which style

Trend reporting for 2025–26 is consistent across sources, and mostly points
away from another minimal line pack:

- **Bold gradients** are the single most common treatment in top-chart app
  icons; roughly 40% by one count.
  [[iconikai, 2026]](https://www.iconikai.com/blog/app-icon-design-trends-2026)
- **Neo-brutalism** — "thick black borders and harsh shadows, clashing colours
  that demand attention" — is called out as the reaction against the polished
  2020s look.
  [[iconmaker, 2025]](https://iconmaker.studio/blog/app-icon-design-trends-2025)
- **3D clay / illustration** performs specifically in *entertainment and
  gaming* categories, which is exactly what a TV home screen is.
  [[iconikai, 2026]](https://www.iconikai.com/blog/app-icon-design-trends-2026)
- **Glassmorphism** is rising but "can appear muddy at very small sizes" —
  disqualifying for a 10-foot UI.
- **Minimal line art** is still cited, and is what all three existing TV packs
  already do.

And there is direct commercial evidence for the pop-art/comic style
specifically. Creativepixels' **Comics — Cartoon Icon Pack** advertises
7,850+ icons, "thick black outlines, punchy flat colours, and a hand-drawn
cartoon aesthetic", plus "10+ exclusive matching comic-style and halftone
wallpapers", at $0.99.
[[Play Store listing]](https://play.google.com/store/apps/details?id=com.creativepixels.comics.iconpack.app)
A second, unrelated "Comics Icon Pack" runs the same concept with a dark
halftone screen and 4,200+ icons. Reviews on the first are unusually strong
for the category, and they name the reason: *"both comic book art style but
also ridiculously vibrant and colourful without being gaudy."*

Two independent developers shipping thousands of icons in this style, on
phones, with matching halftone wallpapers, is a demand signal. **Nobody is
doing it on Android TV.**

---

## 5. The decision

**Pop art cartoon, executed with the discipline of a uniform system.**

The two halves of the brief — "uniform but high quality" and "pop art cartoon"
— are usually in tension. Pop art is loud; uniform systems are quiet. They
reconcile if the *loudness is the constant*: identical container, identical
keyline, identical halftone screen, identical optical mark size, and a palette
locked to sixteen swatches. Then the pack is as consistent as Whicons while
looking nothing like it.

Concretely, five invariants, each enforced by `tools/popart.py` and checked by
`tools/validate_pop.py` rather than left to a style guide:

| # | Invariant | Replaces |
|---|---|---|
| 1 | One superellipse container on all 924 | no container, launcher-dependent |
| 2 | 169 accents → **16 locked swatches**, snapped by hue | 169 unbounded accents |
| 3 | Every mark optically normalised to one ink box | 2x+ size variation |
| 4 | Every line snapped to one weight *after* scaling | weight varies with scale |
| 5 | One halftone screen at one angle on all 924 | n/a |

Rule 2 keeps colour-as-language: Netflix stays red, Spotify stays green, Plex
stays amber. Only saturation and value are taken away, and those were never
carrying meaning.

Rule 3 is the one that matters most and is the easiest to skip. 601 icons were
a letter inside a rounded box; Pop's container *is* that box, so the box is
dropped and the letter is scaled to the same ink box as every other mark. The
letters roughly double in size, which is the single biggest legibility gain in
the pack.

### What we deliberately did not do

- **Another minimal outline pack.** Three of them already exist for this
  launcher, and Whicons/Arcticons own the phone side.
- **Glassmorphism.** Muddy at card size; the sources say so and a 10-foot UI
  makes it worse.
- **Material You / dynamic colour.** Projectivy already tints cards; a
  dynamic-colour pack on TV fights the launcher for the same job.
- **Wordmarks on banners.** `tools/build_variant_markonly.py` measured a real
  TCL Google TV row: a wordmark lockup renders the glyph at 30–39px against
  neighbours at 48–100px, and drops the category kicker to 6.4px. Projectivy
  draws the app name under the card already.

---

## 6. Open questions worth testing

1. **Does the container hurt on tinted cards?** Projectivy can tint icons and
   set card colour. A full-bleed field ignores both. Worth a screenshot test
   on a real device before v1.1.
2. **Sixteen or twelve swatches?** `pop_marine` currently carries 5 apps and
   `pop_jade` carries 113. A tighter hue map would even that out but would move
   some brands further from their real colour. See `docs/pop-palette.png`.
3. **A monochrome Pop variant.** Cream-on-ink with no colour field would be a
   third pack for ~40 lines of code, and AMOLED/monochrome is the single most
   requested phone style in every thread cited above.
4. **Downloader code.** Not yet allocated — `suite.json` carries
   `[USER TO SUPPLY]`. Generate one at <https://go.aftvnews.com/> once the
   first `pop-v*` release is cut.
