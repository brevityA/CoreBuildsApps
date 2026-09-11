# Android TV UI research — design language, components & implementation

Research date: 2026-09-10.
Compiled from four parallel research streams ("agents"):

1. **Official design system** — the current Android TV / Google TV design
   guidance on `developer.android.com/design/ui/tv` (foundation, styles,
   components), fetched page by page.
2. **Implementation/tooling** — Leanback vs. Compose for TV, AndroidX
   release notes, official samples, and practitioner D-pad/focus write-ups.
3. **10-foot UX craft** — overscan, typography, color, and TV quality
   checklists from Google, Amazon Fire TV, and TV-app engineering firms.
4. **Visual teardowns** — live UI references for Google TV, Netflix's TV
   redesign, Disney+, and the Projectivy Launcher this suite ships into.

This extends, and deliberately does not repeat, the Shift-specific findings in
[`../ANDROID_TV_UX_RESEARCH.md`](../ANDROID_TV_UX_RESEARCH.md) (2026-08-24) and
[`../ANDROID_TV_GAP_AUDIT.md`](../ANDROID_TV_GAP_AUDIT.md). Curated reference
images are in [`android-tv-ui/`](android-tv-ui/).

---

## 0. TL;DR

- Google's **current TV design language** (launched I/O 2023, Material 3-based)
  is essentially "the Google TV home screen as a kit": rounded cards in
  horizontal shelves, a left navigation rail or top tabs, cinematic featured
  carousels, and prominent scale/glow focus states. It ships as an official
  **Figma kit** (`goo.gle/tv-desing-kit`) plus the stable **`androidx.tv:tv-material`** Compose library [4][6].
- **Leanback is deprecated** in Google's own docs ("Use Jetpack Compose for
  Android TV OS instead"). Compose for TV's `tv-material` hit **1.0 stable in
  Sept 2024** (current stable **1.0.1**, July 2025; **1.1.0-alpha01**). The
  old `TvLazyRow`/`TvLazyColumn` were removed — their pivot-scroll behavior now
  lives in plain Compose Foundation `LazyRow`/`LazyColumn` (foundation 1.7+) [2][3].
- Design canvas is **960×540 dp (mdpi)**, assets target 1080p; overscan safe
  margins are **48 dp left/right, 27 dp top/bottom**; the content grid is
  **12 columns × 52 dp, 20 dp gutters, 58 dp outer side margins** [9].
- **Focus is the TV equivalent of hover + click combined.** Every screen needs
  a default-focused element on launch, and every interactive element needs
  distinct default/focused/pressed states. Official focus scale presets are
  **1.025 / 1.05 / 1.10**, combinable with border, glow, and tonal surface
  change [focus-system].
- Card width tokens for shelves: **844 / 412 / 268 / 196 dp** for 1/2/3/4-up
  layouts, with **20 dp** spacing and edge-peek [9][cards].
- Default typeface is **Roboto**; body text should stay at/bove roughly
  **18–24 sp** depending on role, headings 48 sp+, no decorative fonts for UI
  chrome [typography][7].
- Dark, low-saturation surfaces (Material 700–900 ranges / 2–3 shades darker
  than mobile), sRGB/Standard picture-mode targeting, scrims over imagery, and
  never color-alone as a state signal [color-on-tv][10].
- The canonical app skeleton is: **top tabs or left nav rail → featured
  carousel/immersive hero → vertically stacked horizontal shelves (rows) →
  detail view → player**, with search kept one click away and text entry
  avoided (voice + Gboard TV) [design-for-tv][10].

---

## 1. Platform context

### 1.1 What you are designing for

- **Android TV** is the OS/API; **Google TV** is the Google-curated
  launcher/experience on top of it. Apps target both with one APK and the same
  design language [4][6].
- Fixed **16:9 landscape** canvas, 720p/1080p/4K output, no touchscreen
  requirement, primarily **D-pad remotes** (up/down/left/right/OK/Back, plus
  Home, Play/Pause, media keys) [design-for-tv][5].
- Viewing distance ≈ **3 m / 10 ft**, lean-back posture, low-light rooms,
  shared/communal device → low information density, privacy considerations,
  and content-first screens [design-for-tv][7][10].
- TV is a **navigation graph, not a set of screens**: every focusable must
  define where focus comes from and goes to. Missing links create unreachable
  UI — Play's automated pre-launch review rejects apps without defined initial
  focus or with touch assumptions [8].

### 1.2 The 2023 TV design language

Introduced at I/O 2023 alongside the Compose for TV alpha, the guidance covers
buttons, cards, featured carousels, immersive lists, text lists, navigation
drawer, and tabbed interfaces — "the modern Material 3 look" modeled on the
Google TV home screen. SoundCloud reported moving "much faster than the old
Leanback View APIs would have ever allowed" on Compose for TV [4][6].

---

## 2. Layout system

Source: [Layouts — Android TV design guides](https://developer.android.com/design/ui/tv/guides/styles/layouts) [9].

| Token | Value |
|---|---|
| Design canvas | **960 × 540 px @ mdpi** (1 px = 1 dp); export assets at 1080p, system downscales to 720p |
| Aspect ratio | Fixed **16:9**, landscape only |
| Overscan safe margin | **48 dp left/right, 27 dp top/bottom** (~5%); background/bleed artwork may extend past it (partial offscreen items are expected) |
| Grid | **12 columns, 52 dp wide; 20 dp gutters**; 58 dp side space; 4 dp vertical line spacing |
| Shelf card spacing | **20 dp**, with offscreen cards peeking to signal scrollability ("peaking") |
| Card widths | 1-up **844 dp**, 2-up **412 dp**, 3-up **268 dp**, 4-up **196 dp** |
| Layout patterns | Horizontal stack, vertical stack, grid; single-pane preferred to avoid cognitive overload |
| Structures | Single-pane; **left overlay** (nav panel over content); divided panes sparingly |

Rules of thumb:

- Never clip/letterbox the background to the safe area; let artwork bleed and
  offscreen shelf cards show partially.
- Account for the **focus scale-up inside spacing** — a card scaled to 1.1 must
  not overlap or clip its neighbors [9].
- Optimize along **axes**: vertical D-pad movement changes rows/sections,
  horizontal movement browses items within a row. One direction = one meaning.
- Do not put navigation controls along the top eating vertical space; Leanback
  traditionally puts nav on the left [Leanback layouts].

Third-party TV guidance converges on slightly larger margins on real hardware:
Amazon Fire TV recommends keeping all UI inside the inner **90%** of the
screen; cross-platform practitioners use ~90 px side / 60 px top-bottom on a
1920×1080 canvas, and ~100 px on older sets with real overscan cropping
[Fire TV UX](https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html) [7][10].

## 3. Focus system

Source: [Focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system).

- Only **one** element is focused at any moment. State matrix for every
  interactive component: **enabled/disabled × default/focused/pressed**
  (chips add a selected variant).
- Focusable **groups** nest focusable elements to give predictable navigation
  (a shelf is a group; each card an element).
- Four composable indicators, meant to be combined:
  1. **Scale** — presets **1.025 / 1.05 / 1.10**;
  2. **Border/outline** — high-contrast ring;
  3. **Glow** — shadow under the element, the default card treatment;
  4. **Color** — surface (background stays static; *surface* can change) and
     content color swap, i.e. tonal elevation.
- Pressed = momentary state while the center button is held.
- Disabled items remain visually distinct when focused so users aren't unsure
  whether the remote responded.
- Immersive-list focused card: scale **1.1** + border + elevation, and the
  title inside the thumbnail must become more legible [immersive-list].
- Practical field rules: focus feedback must be **instant and distinct**;
  indicators need to survive varied TV picture modes (color alone is never
  enough); keep equal spacing between focusables so directional moves are
  predictable; every screen declares an initial focus target [7][8].

## 4. Component catalog (current official kit)

All components have Figma specs and Compose implementations under
`androidx.tv.material3` [components index](https://developer.android.com/design/ui/tv/guides/components).

### 4.1 Cards — the basic building block

Source: [Cards](https://developer.android.com/design/ui/tv/guides/components/cards).

- **Six variants**: standard, classic, compact, inset, wide standard, wide
  classic (Compose: `Card`, `ClassicCard`, `CompactCard`,
  `WideClassicCard`, `StandardCardContainer`, `WideCardContainer`) [cards][3].
- Anatomy: image + content block (title, subtitle, description, extra text).
- Aspect ratios: **16:9** (default; movies/video), **1:1** (cast, channel/team
  logos), **2:3** (posters, emphasis/grid-breaking).
- Compact card content padding is **12 dp** all around, fixed width, auto
  height — see [compact-card-spec.webp](android-tv-ui/compact-card-spec.webp).
- A card holds one topic; cards never merge or split.
- Shelves implement peaking with **20 dp** spacing and the width tokens in §2.

### 4.2 Buttons & chips

Source: [Buttons](https://developer.android.com/design/ui/tv/guides/components/buttons).

- Variants: **filled, outlined, icon button, outlined icon button, wide
  button, image button** (`Button`, `OutlinedButton`, `IconButton`,
  `WideButton` in Compose).
- Button groups: communicate hierarchy (one primary filled, rest outlined),
  lay out **linearly** for D-pad traversal, use variants consistently.
- Buttons use the **label-large** type role; wide buttons anchor hero
  call-to-action rows (see Netflix/Disney teardowns).
- Chips carry default/focused/pressed × selected/non-selected × enabled states.

### 4.3 Featured carousel

Source: [Featured carousels](https://developer.android.com/design/ui/tv/guides/components/featured-carousel).

- Two variants: **immersive** (full-bleed background) and **card** (contained
  hero card). Compose: `Carousel` with `CarouselState`, auto-advance interval
  and slide transforms.
- Content block anatomy: overline, title, description, button.
- Includes pagination dots (background track, active vs inactive elements).
- Background imagery rules: high resolution, **no text baked into images**,
  always a **cinematic scrim** behind the text block; subject aligned
  top-right, never cropped under content.

### 4.4 Immersive list

Source: [Immersive list](https://developer.android.com/design/ui/tv/guides/components/immersive-list)
— anatomy image: [immersive-list-anatomy.webp](android-tv-ui/immersive-list-anatomy.webp),
specs: [immersive-list-spec.webp](android-tv-ui/immersive-list-spec.webp).

- A shelf row where the **background preview updates as focus moves** — the
  Google/Android TV answer to Netflix's hero billboard.
- Anatomy: image background (cinematic scrim + poster + background color),
  content block, focused card, content grid.
- Progressive disclosure: when the row is focused it expands in height to
  reveal title/description.
- 16:9 backgrounds; focused card scales 1.1 with border; use for featured/new/
  exclusive content only.

### 4.5 Navigation drawer (rail)

Source: [Navigation drawer](https://developer.android.com/design/ui/tv/guides/components/navigation-drawer).

- TV-specific: unlike mobile, a collapsed **navigation rail is always
  visible**; it expands in place. **3–7 destinations** in the rail.
- Two variants: **standard** (pushes content) and **modal** (overlays with a
  gradient or solid scrim). Compose: `NavigationDrawer`,
  `ModalNavigationDrawer`, `NavigationDrawerItem`.
- Anatomy: top section (logo/profile/search; icon-only collapsed), nav items
  (icon + label), optional dividers/badges (use sparingly), bottom section
  with 1–3 actions (settings/help/profile).
- Destinations ordered by importance; an **active indicator** (background
  shape) marks the current destination — must be distinguishable without
  color.

### 4.6 Tabs

Source: [Tabs](https://developer.android.com/design/ui/tv/guides/components/tabs).

- Two indicators: **pill** (top-level page destinations, e.g. Home/Shows/
  Movies) and **underline/bar** (in-content hierarchy). Compose: `TabRow` +
  `Tab`.
- Horizontally scrollable, any number of tabs; TV convention: the panel loads
  when the tab receives **focus**, not only on click.
- Moving tabs slides the content below left/right.

### 4.7 Lists, grids, and controls

- [Lists](https://developer.android.com/design/ui/tv/guides/components/lists):
  one-/two-/three-line items; anatomy icon, overline, title, subtitle, trailing
  control (checkbox/radio/switch). **List items are not buttons** — no
  containers by default; never nest a button inside a list item (focus
  ambiguity); don't indicate selection by background color alone.
- Grids use standard `LazyVerticalGrid`/horizontal grid equivalents; same
  card tokens apply.
- Also available in tv-material: `ListItem`, `Switch`, `Checkbox`,
  `RadioButton`, `Surface`, `Text`, `Icon`, dialogs [3][4].

## 5. Typography

Source: [Typography](https://developer.android.com/design/ui/tv/guides/styles/typography).

- Default typeface **Roboto**, tuned for TV legibility; brand typefaces
  allowed for display/headline if counters are large and optical sizes work at
  a glance. Avoid decorative/handwriting fonts for body; pair sans body with
  expressive display.
- **15-style M3 type scale** across five roles:
  - **Display** (L/M/S) — screen-main heading only, not row titles;
  - **Headline** — featured carousel / immersive cluster titles;
  - **Title** — cards, list items, secondary region headings;
  - **Body** — longer passages (keep these short on TV);
  - **Label** — buttons use label-large; captions use the smallest sizes.
- External practitioner baselines (Google recommends ~**24 sp body minimum**,
  headings **48 sp+**; absolute floor cited 22 px at 1080p; rule of thumb
  2.5–3× mobile sizes): line spacing +20–30%, slightly wider tracking,
  substantial weights, no ultra-light fonts [7][10]. Old TV guidance set
  18–32 sp for primary content — treat 18 sp as a hard floor, not a target [8].
- Buttons/labels never below a size readable at 3 m; test on a real 55" panel —
  emulators don't reproduce viewing-distance legibility.

## 6. Color on TV

Source: [Color on TV](https://developer.android.com/design/ui/tv/guides/foundations/color-on-tv).

- Target **sRGB** (universally supported); DCI-P3 only if you accept advanced
  panels as the audience. Design for the **Standard picture mode**; users may
  switch to Vivid/Dynamic which shifts saturation/contrast.
- TVs differ wildly from monitors: calibrate on actual sets in a dim room.
- Dark themes are standard for media (eye strain, blending with black
  letterboxing); practitioners pick colors **2–3 shades darker** than mobile
  (Material **700–900** ranges), avoid pure `#FFFFFF` text in favor of light
  grey (~`#EEEEEE`), and lean on cool/low-saturation tones [10].
- Contrast is the single biggest quality lever on HDR panels; never convey
  state by color alone (pair with scale/border/icon).
- **Banding**: subtle large-area gradients band on TV panels; dither or avoid
  fine multi-stop dark gradients (relevant to Core Shift's motion-shader
  backgrounds).
- Scrims over key art are mandatory for text legibility in carousel/immersive
  components.

## 7. Navigation & input model

- **Axes rule**: Up/Down moves between shelves/groups, Left/Right browses a
  shelf, OK activates, Back returns — and Back must ultimately return to the
  TV home screen; no reliance on a Menu key [5][8].
- Every screen has an **initial focus target** (Play review enforces this);
  focused lists must scroll themselves to keep the focused item visible
  (pivot offsets) [5].
- Leanback-era browse rule: **categories on the vertical axis, items within a
  category on the horizontal axis**; only one row scrolls horizontally at a
  time [10].
- Keep the **default action one click away**; fewest possible screens between
  launch and content; search one click away but text entry minimized —
  prefer voice; the system Gboard TV (text/email/numeric layouts) handles
  entry: [atv-gboard-text.png](android-tv-ui/atv-gboard-text.png)
  ([docs](https://developer.android.com/training/tv/get-started/onscreen-keyboard)) [10].
- Remote fragmentation: gamepads, colored keys, Magic-remote-style pointers,
  vendor quirks — map D-pad first, treat the rest as enhancement [8][10].
- Playback contract: center button toggles play/pause during media; left/right
  rewind/fast-forward; video pauses when the app is backgrounded; Now Playing
  card only for audio; PiP requires explicit user action and no focusable UI
  inside the PiP window [5].
- Ambient/screensaver: suppress only during user-inititated active video.
- Ads must be D-pad navigable and instantly dismissible; never link to
  non-TV apps/web URLs [5].

## 8. Canonical screen inventory

| Screen | Pattern | Compose for TV | Leanback (legacy) |
|---|---|---|---|
| Home/browse | Tab row or nav rail + featured carousel + shelves (`LazyColumn` of `LazyRow`s of cards) | `TabRow`, `Carousel`, `Lazy*`, cards | `BrowseSupportFragment` (= `RowsSupportFragment` + `HeadersSupportFragment`) |
| Hero-to-detail | Immersive list: shelf drives full-bleed background + scrimmed meta + actions | `ImmersiveList` | custom Rows + `BackgroundManager` |
| Details | Poster, meta, action buttons (Play/Add), cast/related shelves | cards, `ListItem`, buttons | `DetailsSupportFragment` |
| Grid browse | Vertical poster/grid catalog | `LazyVerticalGrid` | `VerticalGridSupportFragment` |
| Search | Voice-first, title/leanback results rows as you type | custom + keyboard | `SearchSupportFragment` |
| Player | Full-bleed video, center default Play/Pause, auto-hiding controls | Compose + Media3 | `PlaybackSupportFragment` / `VideoSupportFragment` |
| Wizards/settings/login | Guided steps, wide buttons, switches; code-based TV login preferred | custom | `GuidedStepSupportFragment` |
| Dialogs | Centered surface, D-pad trapped until choice | tv-material dialogs | `GuidedStep`/alert fragments |

Legacy Leanback data plumbing for reference: `ArrayObjectAdapter` +
`Presenter` + `ListRow`/`HeaderItem`; activities must extend
`FragmentActivity` with a `Theme.Leanback` descendant (no action bar).
Leanback fragments already bake in overscan margins — don't double-apply them
[Leanback layouts](https://developer.android.com/training/tv/playback/leanback/layouts) [1].

## 9. Implementation: Leanback → Compose for TV

### 9.1 Status & dependencies

- **Leanback (`androidx.leanback:leanback`) is deprecated** per current
  Android docs; Compose for TV is the recommended path, works on
  **Android 5.0 / API 21+** [Leanback layouts][compose-tv].
- Dependencies (from current official setup docs) [compose-tv]:

  ```kotlin
  val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
  implementation(composeBom)
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.tv:tv-material:1.0.0") // 1.0.1 current; 1.1.0-alpha01
  implementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")
  ```

- Use **`androidx.tv.material3.*`** versions of components; do not mix the
  mobile `androidx.compose.material3.MaterialTheme` with the TV one (colors/
  type/shapes diverge) [compose-tv].
- **`tv-foundation` lazy layouts are gone**: `TvLazyRow/Column/Grid` and
  `PivotOffsets` moved into Compose Foundation 1.7's `LazyRow`/`LazyColumn`;
  migrate by deleting the dependency and dropping the `Tv` prefix. Only
  `TvImeOptions` (now `PlatformImeOptions`) remains in tv-foundation alpha [2].
- Official code: [android/tv-samples](https://github.com/android/tv-samples) —
  **TvMaterialCatalog** (component kitchen sink), **JetStreamCompose**
  (full streaming app: TabRow, Carousel, ImmersiveList, shelves, chips,
  dialogs), plus the
  [Compose for TV codelab](https://developer.android.com/codelabs/compose-for-tv-introduction) [compose-tv][4].

### 9.2 Focus & D-pad in Compose (the core skill)

- Every interactive element: `Modifier.focusable()` (or tv-material
  components, which include focus handling) +
  `onFocusChanged { }` to drive scale/elevation; `animateFloatAsState`
  for the 1.05/1.1 scale [D-pad part 5].
- Programmatic focus: `FocusRequester` + `focusRequester(...)`, request in a
  `LaunchedEffect(Unit)` for the **initial target**; restore prior focus on
  return with a remembered requester and `focusProperties { enter = { ... } }`.
- Key handling: `onPreviewKeyEvent`/`onKeyEvent` watching
  `KEYCODE_DPAD_*` and `KEYCODE_ENTER`/`Key.DirectionCenter`; consume (`true`)
  when implementing wrapping, row skipping, or custom scroll. Activate on
  **key up** of center/enter.
- Grouping: nested focus groups come from layout; wrap shelves so Up/Down
  jumps rows instead of traversing every card. For edge-of-row wrapping use a
  remembered array of `FocusRequester`s.
- Pivot scrolling: with Foundation lazy layouts the focused item is kept at a
  stable screen position; tune via `PivotOffsets(parentFraction, childFraction)`
  (e.g. `0.03f` to keep the first item pinned, `0.15f`–`0.5f` for centered
  shelves/immersive rows).
- Card recipe seen across practitioner samples:

  ```kotlin
  var focused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "card")
  Card(
    modifier = Modifier
      .width(268.dp)                 // 3-up token
      .onFocusChanged { focused = it.isFocused }
      .scale(scale)
      .focusable()
      .onPreviewKeyEvent { e -> /* center-key up -> onClick */ },
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.colors(containerColor = /* dark surface */),
  ) { /* 16:9 art + title block */ }
  ```
- Tabs load content **on focus**: `Tab(selected = i == current, onFocus = { current = i })`.
- Hybrid migration is supported and recommended for existing View apps:
  extract a design-system module first, replace leaf components (cards,
  buttons) via `ComposeView` inside existing screens, migrate
  Browse → Details → Search → Player, switch navigation layer last, then drop
  Leanback. Wrap Compose surfaces in the TV theme to avoid XML-theme clashes [1].

### 9.3 Manifest / launcher requirements

Source: [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality) [5],
[TV app icon guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines).

- Launcher intent: `ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER`.
- Declare `android.hardware.touchscreen` (and sensor hardware) as not
  required; landscape only; non-transparent full-screen background; no
  letterboxing (black bars only for native video).
- **Banner: 16:9, 320×180 @ xhdpi** (mdpi 160×90 … xxxhdpi 640×360); launcher
  icon 80×80 mdpi series; adaptive icons recommended; banner must contain
  the app name (localized); no themed icons on TV; no borders on icon art.
- Android App Bundle mandatory; min SDK ≤ 31; from 2026-08-01 both 32/64-bit
  and 16 KB page compatibility; provide TV screenshots and, where login is
  required, review credentials.

## 10. Visual teardowns of shipping UIs

Reference crops live in [`android-tv-ui/`](android-tv-ui/).

### 10.1 Google TV home / Projectivy Launcher shell

[googletv-home-rows.png](android-tv-ui/googletv-home-rows.png) (captured from a
Projectivy-driven home) and
[projectivy-your-apps.jpg](android-tv-ui/projectivy-your-apps.jpg):

- Rounded-rectangle cards (~12–16 dp radius) in labeled horizontal rows;
  row labels pair a thin monochrome outline icon with the title.
- Focus = card scaled up with a bright rounded outline and a soft outer glow;
  ambient background is tinted by hero artwork (warm dark red here).
- Shelf items peek past the screen edge; the launcher chrome is near-black
  with light-grey labels.
- Projectivy's "Your apps" row mixes circular launcher icons and 16:9 banners,
  a persistent rounded **search pill**, and large category chips
  (Entertainment, Music & Audio, …) — the exact surface the Core Builds icon
  packs and banners render into. This validates the README's dark-card
  recommendation (`#0d1117`): the launcher environment is dark, and glyph
  contrast is judged against it.

### 10.2 Netflix TV redesign (2024–2025)

[netflix-hero-redesign.jpg](android-tv-ui/netflix-hero-redesign.jpg):

- Top-center **pill tab bar** (Home, Shows, Movies, Games, My Netflix) with
  profile switcher left and search right — tabs-as-primary-nav.
- Full-bleed cinematic hero bounded by a large rounded **card frame** with
  focus border; left-bottom content block: overline ("SERIES"), giant
  wordmark/headline, metadata line (Show • Fantasy • 2022 • TV-14), a 2-line
  capped description, then a **white filled wide pill primary action**
  ("Remind Me") and a **translucent grey secondary pill** ("More Info").
- The first content shelf ("Your Next Watch") peeks below the hero, implying
  the axis model: Down moves hero → shelf.
- Big takeaway: one primary action, secondary as tonal surface, two lines of
  description max, art composition leaves the lower-left clear for text.

### 10.3 Disney+

[disney-tile-wall.jpg](android-tv-ui/disney-tile-wall.webp) (signup/welcome):

- Full-bleed wall of **2:3 posters** dimmed under a dark scrim — the same
  immersive-background technique as the official kit, plus Disney's signature
  brand-color focus outlines (cyan/pink/blue) around each poster tile.
- One extra-wide filled primary button centered, log-in as a low-emphasis
  text button beneath; pricing copy small and centered. Focus hierarchy with
  exactly one loud element.

## 11. Common failure modes (from reviews and field guides)

1. **No initial focus** or focus traps/dead ends between custom views — Play
   rejects; fix with declared requesters and focus groups [5][8].
2. Dense, mobile-port layouts: small text, small touch-height targets, more
   than ~5 choices competing at once [7][10].
3. Applying overscan margins to Leanback fragments (double margins) or
   putting essential UI at the very edge [Leanback layouts][7].
4. Rebuilding fragments/rows on every navigation move, refetching images;
   cache aggressively (Glide/Coil) and retain adapters/state [1].
5. Relying on subtle color differences that vanish in Vivid/Dynamic picture
   modes; pure white blocks in dark rooms; gradient banding [color-on-tv].
6. Text-heavy hero cards with baked-in text on background art; missing
   scrims [featured-carousel].
7. Text-entry flows without voice/TV keyboard support; on-screen keyboards of
   the app's own making [10].
8. Mixing mobile `material3` and TV `tv.material3` themes in one composition
   [compose-tv].
9. Testing only the emulator: overscan cropping, focus latency (target
   < 250 ms transitions), and 10-ft legibility only surface on hardware
   [8][10].
10. Fragment-host theme errors (`AppCompat` + Leanback `Theme.Leanback` crash)
    — use `FragmentActivity` for legacy Leanback [Leanback layouts].

## 12. Implications for the Core Builds suite

- **Core Shift** is the suite's main bespoke full-screen TV UI. The
  end-state already prescribed by the 2026-08 research — hero / immersive
  preview → motion shelf (horizontal cards, vertical rows) → selected-item
  actions — is precisely Google's **Immersive list + shelves** pattern, and
  this doc supplies the tokens to build it: 16:9 hero with scrim,
  268 dp 3-up or 196 dp 4-up cards at 20 dp spacing, 1.1 + border + glow
  focus, label-large wide buttons for Preview/Download, safe margins
  48/27 dp, dark surfaces.
- When Shift moves off RecyclerView/Views, follow the official hybrid path:
  tv-material cards/buttons inside `ComposeView` first, `ImmersiveList` for
  the hero shelf, Foundation `LazyColumn { item { LazyRow { … } } }` for
  shelves, focus requesters for the "first Preview focused" contract [1][2].
- Brand chrome is already night-oriented (`#0d1117` card backgrounds); define
  **focused-state colors and a glow** for the cyan/violet accents, and pair
  color with outline/scale so state survives bad picture settings. Verify
  Outfit legibility at label/body sizes on a real TV; Roboto remains the safe
  utilitarian fallback for UI chrome while Outfit stays in brand/banner
  contexts.
- Watch **gradient banding** in motion-engine/shader backgrounds on real
  panels; dither large dark gradients.
- The icon pack **banners (320×180) and adaptive launcher icons already
  match** the launcher-asset spec; the app-row screenshots show exactly how
  banners vs round icons are consumed in Projectivy/Google TV rows.
- **Core Line** (chyron overlay), **Core Motion** (plugin, no UI), and
  **Core Doctor** (phone) are largely unaffected; any future Shift-like TV
  surface in the suite should adopt this token set so the seven apps share
  more than branding.
- Pre-launch: run the TV quality checklist (initial focus, full D-pad
  reachability, Back-to-home, no touch assumption, banners) and test on
  physical remotes — already a standing action item from the Shift gap audit.

## 13. Sources

Official (fetched directly):

- [Design for TV — foundations](https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv)
- [Color on TV](https://developer.android.com/design/ui/tv/guides/foundations/color-on-tv)
- [Typography](https://developer.android.com/design/ui/tv/guides/styles/typography)
- [Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts)
- [Focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system)
- [Cards](https://developer.android.com/design/ui/tv/guides/components/cards)
- [Buttons](https://developer.android.com/design/ui/tv/guides/components/buttons)
- [Featured carousels](https://developer.android.com/design/ui/tv/guides/components/featured-carousel)
- [Immersive list](https://developer.android.com/design/ui/tv/guides/components/immersive-list)
- [Navigation drawer](https://developer.android.com/design/ui/tv/guides/components/navigation-drawer)
- [Tabs](https://developer.android.com/design/ui/tv/guides/components/tabs)
- [Lists](https://developer.android.com/design/ui/tv/guides/components/lists)
- [Use Jetpack Compose on Android TV](https://developer.android.com/training/tv/playback/compose)
- [Leanback layouts (deprecation notice)](https://developer.android.com/training/tv/playback/leanback/layouts)
- [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality)
- [TV app icon design guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines)
- [On-screen keyboard (TV)](https://developer.android.com/training/tv/get-started/onscreen-keyboard)
- [AndroidX `androidx.tv` release notes](https://developer.android.google.cn/jetpack/androidx/releases/tv)

Web search-derived:

1. [Migrating from Leanback to Jetpack Compose in Android TV — ToTheNew](https://www.tothenew.com/blog/migrating-from-leanback-to-jetpack-compose-in-android-tv/)
2. [Migrating Compose for TV from alpha to stable — Android Developers (Medium)](https://medium.com/androiddevelopers/migrating-compose-for-tv-from-alpha-to-stable-b0074d6fd350)
3. [androidx.tv release components (NavigationDrawerItem, TabRow, buttons, cards stable)](https://developer.android.google.cn/jetpack/androidx/releases/tv)
4. [Google introduces new design language for Android TV & Google TV — FlatPanelsHD](https://www.flatpanelshd.com/news.php?subaction=showfull&id=1683799895)
5. [TV app quality checklist — Android Developers](https://developer.android.com/docs/quality-guidelines/tv-app-quality)
6. [Google TV and Android TV apps get a new design language — 9to5Google](https://9to5google.com/2023/05/10/google-tv-apps-design/)
7. [8 UX/UI best practices for TV apps — Spyrosoft](https://spyro-soft.com/blog/media-and-entertainment/8-ux-ui-best-practices-for-designing-user-friendly-tv-apps)
8. [Android TV App Development Guide — Oxagile](https://www.oxagile.com/article/android-tv-app-development-guide/)
9. [Layouts spec — Android TV design guides](https://developer.android.com/design/ui/tv/guides/styles/layouts)
10. [How to create a Smart TV UI design — Purrweb](https://www.purrweb.com/blog/how-to-design-an-app-for-smart-tvs/)
11. [10 Tips for UI/UX Design on Smart TV — Norigin Media](https://noriginmedia.com/10-tips-for-ui-ux-design-on-smart-tv/)
12. [Design and User Experience Guidelines — Amazon Fire TV](https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html)
13. [Android TV D-Pad Navigation in Jetpack Compose (Part 5)](https://medium.com/@prahaladsharma4u/android-tv-d-pad-navigation-handling-jetpack-compose-part-5-2293feb4565c)
14. [Building for Android TV prototype/guide — NitishGadangi (GitHub)](https://github.com/NitishGadangi/Android-TV-Prototpe)
15. [Re-thinking UI design for the TV platform — You.i TV](https://medium.com/you-i-tv/designing-for-10ft-ceeb202c1315)
16. [dpad-compose tutorial (custom focus modifier)](https://github.com/thesauri/dpad-compose)
17. [Netflix overhauls its TV app UI — Engadget](https://www.engadget.com/entertainment/streaming/netflix-overhauls-its-tv-app-with-a-fresh-ui-and-responsive-recommendations-121511958.html)

Local, related:

- [`../ANDROID_TV_UX_RESEARCH.md`](../ANDROID_TV_UX_RESEARCH.md) — Core Shift focus/hero contract (2026-08-24)
- [`../ANDROID_TV_GAP_AUDIT.md`](../ANDROID_TV_GAP_AUDIT.md) — Shift quality-gap checklist
- Official Figma kit: `https://goo.gle/tv-desing-kit`
- Samples: `https://github.com/android/tv-samples` (TvMaterialCatalog, JetStreamCompose)
