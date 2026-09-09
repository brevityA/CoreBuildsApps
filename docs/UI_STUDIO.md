# Core Builds UI Studio

A quick way to design Android TV screens for the icon packs — with correct
D-pad wiring and smooth animations baked in, not bolted on.

Two doors into the same generator:

| Door | How | Best for |
|---|---|---|
| **Visual Studio** | open `tools/ui/studio.html` in any browser (double-click works; no server, no build) | arranging blocks, previewing focus + motion, exporting code |
| **CLI** | `python tools/build_ui.py --preset browser --name Showcase` | repeatable generation, specs in git, `--apply` install |

Both produce the same files: a layout, an Activity, an adapter + item layout
for custom grids, new strings, and a manifest snippet. Both refuse broken
output — every generation is audited before it is written.

## 5-minute quickstart

**In the browser:**

1. Open `tools/ui/studio.html`.
2. Pick a preset (top bar), click blocks on the left to add them.
3. Drive the preview with your **arrow keys** — that is the actual generated
   focus graph, simulated tile-for-tile. The readout under the screen shows
   where each direction goes.
4. Tune zoom + enter motion in the **Motion** tab, check the **Checks** tab is
   green, then copy code from the **Code** tab — or hit **Export spec** and run:

```bash
python tools/build_ui.py --spec showcase_spec.json --apply
```

**In the terminal:**

```bash
python tools/build_ui.py --list-presets
python tools/build_ui.py --preset browser --name Showcase        # stage to tools/ui/out/
python tools/build_ui.py --preset browser --name Showcase --apply  # install into app/
```

`--apply` copies the layout + Kotlin into `app/`, merges new strings into
`values/strings.xml`, declares the Activity in the manifest, and refreshes the
`app/ → pop/` mirror — so the new screen compiles in **both** packs. It
refuses to overwrite existing files unless you pass `--force`.

## The presets

| Preset | Screen | Shape |
|---|---|---|
| `browser` | icon browser | header + targets + update bar + chips/search + empty state + icon grid |
| `wallpapers` | wallpaper browser | header + selection bar + series chips + wallpaper grid |
| `details` | item details | header + hero + actions + related grid |
| `settings` | settings | header + settings rows + done button |
| `onboarding` | first-run | header + hero + actions |

`browser` mirrors the real `MainActivity` structure; `wallpapers` mirrors
`WallpapersActivity`. Generate one with `--name` set to something new
(`Showcase`, `WallBrowser2`) and diff the output against the shipped screen to
see what the wiring buys you.

## D-pad wiring: what "correct" means here

Every generated screen guarantees:

- **Explicit `nextFocus*` on every focusable** — no reliance on spatial
  search, which varies by panel geometry and OEM focus quirks.
- **A declared initial focus** that is visible when the screen opens, requested
  via `post { requestFocus() }` (plain `requestFocus()` in `onCreate` runs
  before attach and silently no-ops — the classic empty-focus bug).
- **First-row grid escape**: UP from any tile in row 0 lands on the row above,
  via `DpadNav.installTopEdge`, with any adapter and any span count.
- **Hidden bars stay out of the graph** while `GONE`, and generated
  `set…Visible()` helpers re-chain neighbours through them when revealed, then
  move focus onto the bar.
- **Stable ids + `DiffUtil`** in every adapter, so filtering never drops the
  highlight mid-keystroke.
- **`focusableInTouchMode`** everywhere and the 48dp target floor, so the same
  screen behaves on touch, mouse, and keyboard.

`tools/validate_ui.py` proves all of it statically, for generated screens and
hand-written ones alike:

```bash
python tools/validate_ui.py app/src/main/res/layout/activity_main.xml
python tools/validate_ui.py --all            # every app/ layout
```

It fails on duplicate ids, dangling `nextFocus` targets, isolated views, and
missing initial focus, and warns where a view can only be reached by spatial
search. Run it after any restyle.

## Motion: what you get

All motion lives in two runtime helpers both packs compile
(`tv.corebuilds.iconpack.ui`, dependency-free, minSdk 21):

- **`TvFocus.attach(view)`** — 1.06× zoom + 8dp lift in 160ms on focus gain,
  120ms on loss, in-flight animation cancelled first so held-down D-pad input
  never queues a backlog.
- **`TvFocus.zoomItems(recycler)`** — the same zoom for grid/chip tiles at a
  smaller 1.04×, attached via attach/detach listeners so the shared adapters
  stay fork-free.
- **`TvFocus.playEnter(root)`** — staggered fade-and-rise for the screen's
  children (220ms, 28ms stagger); **`playListEnter`** does the visible grid
  rows once, without fighting `DiffUtil`.
- **`TvFocus.reveal(bar)`** — contextual bars fade/slide in instead of popping.

Generated Activities call all of these; generated adapters zoom per
view-holder. The Studio Motion tab edits the same numbers that land in the
Kotlin (`scale = 1.08f`, `staggerMs = …`), and non-default values are the only
ones spelled out — defaults stay bare `TvFocus.attach(view)` calls.

## The blocks

| Block | Renders | Focus |
|---|---|---|
| `header` | kicker + title + count + stacked side buttons | side buttons |
| `actions` | horizontal button row (`cta` / `ghost` / `chip` / `toggle`) | each button |
| `hero` | 160×90 art + kicker + title + meta | none (presentational) |
| `chips` / `search` / `filterrow` | filter controls, alone or paired | container / field |
| `grid` | `RecyclerView` grid: `icons`, `walls`, or `custom` source | tiles (runtime) |
| `settings` | stacked label + value rows | each row |
| `targets` / `updatebar` / `selectionbar` / `empty` | contextual rows/bars, `GONE` until shown | their buttons |
| `hint` | one cyan helper | none |

A spec is JSON — see `tools/ui/screens/browser.json`. Literal text is
auto-extracted into generated strings (layouts never carry hardcoded copy);
`@string/…` references pass through. Buttons take an optional `"go":
"SomeActivity"` for real navigation; chips take `keys`/`labels`, or
`"fromCatalog": true` to build series chips from the wallpaper catalog at
runtime. Full validation messages come from the CLI — it rejects duplicate
ids, unknown blocks, bad initial focus, and chips/grid mismatches in plain
language.

## Files

```text
tools/ui/studio.html        the visual designer (offline, dependency-free)
tools/ui/screens/*.json     the five starter specs
tools/ui/spec.py            spec model + focus-graph computation
tools/ui/render.py          layout / Kotlin / strings / manifest renderers
tools/ui/generate.py        staging + --apply (app tree, strings, manifest, mirror)
tools/ui/out/               staging dir (git-ignored)
tools/build_ui.py           CLI: --preset | --spec, --apply, --force, --no-mirror
tools/validate_ui.py        D-pad + motion auditor for any layout
tests/test_ui_generator.py  9 regressions (presets, auditor, validation, merge)
app/.../iconpack/ui/        TvFocus.kt + DpadNav.kt runtime (compiled by both packs)
docs/UI_STUDIO.md           this file
```

`tools/build_pop.py --mirror-only` refreshes just the `app/ → pop/` resource
mirror (layouts, strings, manifest) in seconds instead of re-rendering the
pack — `--apply` runs it automatically. The full render path is untouched.

## Troubleshooting

- **`dpad.initialFocus … sits inside a hidden bar`** — initial focus must be
  visible at open. Point it at a header button, the chips, or the grid.
- **`refusing to overwrite existing files`** — the Activity, layout, or
  adapter name already exists. Pick a new `--name`, or re-run with `--force`.
- **Pop CI drift after `--apply`** — you passed `--no-mirror`. Run
  `python tools/build_pop.py --mirror-only` to sync `pop/` from `app/`.
- **Focus visually stuck after showing a bar by hand** — call
  `DpadNav.chainVertical(above, barButton, below)` on every toggle (generated
  `set…Visible()` helpers already do).
- **Tiles zoom but clip at the row edge** — the auditor flags
  `RecyclerView`s without `clipToPadding="false"`. Generated layouts set it.
