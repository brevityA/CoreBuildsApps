# Core Builds Brand Guide (apps extract)

Living extract of **Core Builds Brand Guide v1.0** (September 2026) for
`brevityA/CoreBuildsApps`. The generators, validators and `suite.json` remain
the source of truth. If this page and a contract disagree, the contract wins.

Master line: **Build streams with intent.**
Functional line: **Frictionless streaming.**
Suite line: **Seven Android apps. Same brand, same living-room bar.**

## Pillars

- Dark-first premium. OLED-friendly. Colour arrives as light.
- Intent and control. Users pick device, service, profile and rules.
- Readable at TV distance. Design for the shelf, not isolation.
- System over mood. Catalogs, generators and validators are brand.
- Honest utility. Name local, offline, cached, premium-gated, unverified.

## Mark

Point-up hexagon, centered diamond. Never rotate the hex. Glow is a local
signal, not fog over the canvas. Protect negative space.

## Palette

| Token | Hex | Role |
|---|---|---|
| Night | `#0D1117` | Default canvas / card |
| Void | `#04070F` | Deepest motion / hero field |
| Signal Cyan | `#00D4FF` | Active, focus, Builds emphasis |
| Electric Cyan | `#00E5FF` | Edge / glow line |
| Build Blue | `#4FACFE` | Secondary cool stop |
| Violet | `#8A4890` | Depth companion |
| Ember | `#C03A20` | Diamond / motion heat |
| Light Text | `#E6EDF3` | Primary readable light |
| Muted UI | `#8B949E` | Captions / diagnostics |
| Panel Ink | `#151923` | Raised cards |

Pop snaps brand accents to the 16 locked swatches in `tools/popart.py`.
Do not invent a 17th.

## Type

Serif for editorial display. Bold sans for product names and TV UI. Mono for
versions, rails, receipts. Do not put serif on dense controls.

## Classic Icon Pack

| Spec | Contract |
|---|---|
| Canvas | 512×512, 432 safe area (40px pad) |
| Stroke | **32 / 26.2 / 21.8** rounded monoline |
| Background | Transparent. The launcher owns the card |
| Composition | One accent, Core Builds linework, vendor logos are cues only |
| Banners | 320×180 transparent Projectivy cards |
| Contrast | Accents that fail 3:1 on `#0D1117` render as `#E6EDF3` |

The v1.0 PDF lists Classic stroke as 34px. That page is wrong. `monoline()`
and `tests/test_icon_identity.py` own the weights.

### Presence (raster only)

Square Classic PNGs get a night keyline and a short accent bloom *after*
`svg2png` (`tools/presence.py`). Both layers are rings around existing ink.
Interiors stay alpha. SVG masters stay style-AA. Banners, Pop and Pixel Neon
do not take this pass.

Identity ceilings after presence: PNG coverage `< 0.40`, PNG margin 24px.
Vectors still have to clear the 40px SAFE area.

## Sibling expressions

- **Pixel Neon** — 32×32 logical sprites, nearest-neighbour to 512, compact
  halo, transparent outside the art. Not a filter on Classic.
- **Pop** — one superellipse, cream mark on near-black ink, 45° halftone,
  locked swatches, pack-owned field.
- **Motion / Shift** — slow, dark, OLED-safe, same geometry and palette.
  Say when a loop needs Monet Premium, Projectivy Premium or a local file.

## Voice

Precise, intent-led, operationally honest. State the job, the audience, the
constraint and the proof. No “best ever”, no hidden gates.

## Governance

1. Change `tools/catalog.json` or the generator, never a generated file.
2. Run the four Classic generators plus validators before merge.
3. Rebuild Pop when the catalog or shared `app/` resources move.
4. Ship receipts: counts, versions, check totals.
5. TV focus must not depend on colour alone.

Full original: the attached Brand Guide v1.0 PDF and the Core-Builds guide
linked from the README.
