# Android TV UX/UI research for Core Shift

Research date: 2026-08-24.

## Official Android TV guidance

- [TV navigation](https://developer.android.com/training/tv/get-started/navigation)
  says every visible control must be reachable with the D-pad, scrolling lists
  must scroll with Up/Down while an item has focus, and an actionable item must
  be focused when the app opens or is idle.
- [Focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system)
  defines focused/pressed/disabled states and recommends consistent focus
  indicators using scale, outline, glow, tonal surface and content color.
  Android TV's documented scale examples are approximately 1.025–1.1x.
- [Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts)
  recommends designing for 16:9/1080p, using safe margins, and using a browse
  structure where vertical movement changes rows and horizontal movement browses
  items within a row. It warns against excessive panels/cognitive overload.
- [Immersive list](https://developer.android.com/design/ui/tv/guides/components/immersive-list)
  recommends a large dynamic preview that updates with the focused card, a
  16:9 background, scrimmed content information, and a clear focused-card state.
- [Typography](https://developer.android.com/design/ui/tv/guides/styles/typography)
  recommends large, legible type and avoiding decorative fonts for utilitarian
  TV UI.
- [Color on TV](https://developer.android.com/design/ui/tv/guides/foundations/color-on-tv)
  recommends strong contrast, readable fonts, not relying on color alone, and
  testing in standard SDR picture mode across TV technologies.
- [Accessibility/TalkBack evaluation](https://developer.android.com/training/tv/accessibility/talkback)
  recommends navigating every page/row end-to-end, checking that all controls
  are reachable/clickable, and confirming Back returns to the previous focus.

## What the screenshot revealed

The screenshot was Core Shift 2.3.1. The hero preview worked, but the stacked
hero + controls + content banner pushed the motion library below the viewport.
The D-pad path also did not explicitly enter the RecyclerView, so Preview and
Download were effectively unreachable.

This is a navigation/layout defect, not a missing prequel-media defect. The
prequel release now contains both 1080p and 4K assets.

## Recommended Core Shift pattern

Use a three-level hierarchy:

1. **Hero / immersive preview** — one full-bleed 16:9-ish animated stage with a
   scrimmed title and short metadata.
2. **Motion shelf** — a visible horizontal row of focused cards for Series 2,
   Series 3 and Core Motion. Up/Down changes shelf; Left/Right changes item.
3. **Selected actions** — Preview, Download and quality choice in the selected
   detail area rather than duplicating many action buttons in every row.

The current mixed implementation is a transitional version of this pattern:
it keeps the existing RecyclerView row design but reduces the hero height,
adds an initial focused Preview button, and declares explicit focus paths into
the list. A future card shelf would be the cleaner end state.

## Required interaction contract

At app start:

```text
first library Preview is focused
```

D-pad:

```text
Down       browse to the next library item/row
Up         return to the content/library controls
Right      move from Preview to Download
Left       move back from Download to Preview
OK         activate the focused action
Back       return from preview and restore prior library focus
```

Quality:

```text
1080p default
4K only when url_4k exists
Download uses the selected URL, never a mislabeled fallback
```

Motion preview:

```text
0.5x / 1x / 2x speed
10s / 20s / 30s preview length
```

## Current implementation/release plan

- PR #42 addresses the screenshot's reachability defect and bumps Core Shift to
  2.3.2/versionCode 8.
- The prequel release is complete with 16 1080p and 16 4K MP4s.
- Merge PR #42 only after the APK build is green.
- Publish `shift-v2.3.2`.
- Test with a physical remote, because Android's own guidance requires actual
  D-pad testing rather than relying on layout inference.
