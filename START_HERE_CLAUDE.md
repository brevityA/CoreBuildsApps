# Start here — instructions for Claude

Paste the block below into Claude Code (or Claude in your browser, with the zip attached) as your first message. Everything after it is reference.

---

## The opening prompt

> This is the **Core Builds Icon Pack** — an Android TV icon pack for Projectivy Launcher, part of the Core Builds ecosystem (`github.com/brevityA/Core-Builds`).
>
> **Read `CLAUDE.md` first and follow it.** The critical rule: `tools/catalog.json` is the single source of truth. Every XML file, PNG, and doc is generated from it — never hand-edit generated output, or CI will flag the drift.
>
> Before you commit anything, run:
> ```bash
> pip install -r tools/requirements.txt
> python tools/build_icons.py
> python tools/build_branding.py
> python tools/build_brand_preview.py
> python tools/validate.py
> ```
> Quote the validator's final line back to me — that's the receipt.
>
> Two things I want you to be strict about: **draw original geometry, never trace a vendor logo**, and **never invent a component name**. If you can't verify a component with `adb shell cmd package resolve-activity --brief <package>`, tell me it's unverified rather than guessing — a wrong mapping fails silently on my TV, which is worse than a missing icon.
>
> Here's what I want to do first: **[describe your task]**

---

## Good first tasks

Pick one and drop it into the `[describe your task]` slot.

**Add more apps**
> Add icons for these apps: [list]. Find the component names, pick or draw appropriate glyphs, and regenerate. Flag any component you couldn't verify.

**Expand toward Projectivy-pack parity**
> The reference pack at `github.com/SicMundus86/ProjectivyIconPack` covers 800+ apps. Propose the next 40 by likely usage on Android TV, in waves of 10 so I can review the glyphs as they land.

**Fix a mapping report**
> A user reports [app] isn't auto-assigning on [device]. Their component is `[paste]`. Add it to that icon's components and regenerate.

**First real build**
> The Gradle build has never run — no JDK 17 or Android SDK in the environment where this was authored. Run `./gradlew assembleDebug`, fix whatever AGP objects to, and report what actually broke.

**Restyle**
> Switch the pack from brand-coloured glyphs to [monochrome cyan / hex-hosted] across all 40 icons. This is a `glyphs.py` and `catalog.json` change — the Android layer shouldn't need to move.

---

## What's already true (don't re-verify)

- 40 icons, 78 mapped components, 409 validator checks passing.
- Generator output is deterministic — two consecutive runs are byte-identical.
- All XML parses; all workflow YAML parses.
- Three git commits on `main`, clean working tree.

## What is NOT verified (be honest about these)

- **The APK has been built.** See CLAUDE.md — both variants build clean; not yet installed on a device.
- **Some component names are best-known, not device-confirmed** — TorBox, Weyd, AllDebrid, Premiumize, and several AU broadcaster apps.
- The CI drift gate covers text assets only; PNG bytes vary with the runner's libcairo build.

## Repo map

```
tools/catalog.json           ← the only file you edit to add an icon
tools/glyphs.py              33 glyph primitives, pure geometry
tools/build_icons.py         catalog → SVG, PNG, appfilter, docs, preview
tools/build_branding.py      launcher icon + Leanback banner
tools/build_brand_preview.py branding preview sheet
tools/validate.py            409 coherence checks
app/                         the Android module (Kotlin)
docs/preview.png             all 40 icons — check new glyphs here
docs/brand-preview.png       banner + launcher icon at true sizes
PUBLISHING.md                how to get this onto GitHub
CLAUDE.md                    the full working agreement
```
