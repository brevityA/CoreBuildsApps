# Animated launcher icons — research for the morph test (2026-10-10)

What the platform and Projectivy actually do with animated icons, gathered so
the device test below can be read as a confirmation or a refutation rather
than a fishing trip. Sources are linked inline and collected at the end.

The test's questions:

1. **Icon pack route.** Does Projectivy animate an animated drawable that an
   icon pack's appfilter maps, or only a custom icon picked from a file?
2. **Focus.** Does the animation play when the card gains focus, and does the
   card return to frame 1 (the glyph) when focus leaves?
3. **Loop.** Does the launcher honour the file's loop count, or loop every
   animated icon until its own setting says otherwise?

## TL;DR

| # | Device result so far | Research prediction | Confidence |
|---|---|---|---|
| 1 | No animation via the icon pack (tested 2026-09-25, Projectivy 4.71) | Confirmed expected. Android has no standard for animated icon-pack icons; launchers render pack drawables to static bitmaps, and Projectivy's animation support is documented for *custom icons* (local files) only | High |
| 2 | Not yet tested on the manual route | Animation starts on focus, stops on focus loss, and since 4.70 the card **resets to frame 1** on stop — which for these files is the glyph. The morph's "glyph at rest" design matches Projectivy ≥ 4.70 exactly | High |
| 3 | Not yet tested | Before 4.72, animated icons loop unbounded (4.72 added "ability to limit animated icons looping count"). Whether the file's own loop count is honoured is exactly what the play-once vs ping-pong pair measures | Medium |

## Why the icon-pack route cannot animate (question 1)

An icon pack is a bag of resources plus an `appfilter.xml` that maps app
components to drawable names. The launcher loads those drawables through the
pack's `Resources` and caches them as bitmaps for its rows. Two framework
facts close the door:

- Android has **no standard for animated launcher icons**. "Some home screen
  implementations might offer developers an API to have animated launcher
  icons… There is nothing standard in Android for this"
  ([Stack Overflow](https://stackoverflow.com/questions/27463068/is-it-possible-to-have-animated-launcher-icons-for-android-apps)).
- The framework's only animated decode path is
  [`ImageDecoder.decodeDrawable`](https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable),
  which returns an `AnimatedImageDrawable` for animated GIF and WebP (API
  28+). The classic resource path launchers use for pack drawables decodes
  the first frame only. `AnimatedImageDrawable` also "will only animate while
  it is being displayed" — it is a view-bound animation, not a launcher-icon
  mechanism.

Projectivy's own changelog scopes animation to the **custom icon** route:
"customize cards/category icon individually [premium only]: choose from icon
pack or local image (with support for animated gif and partial support for
animated webp)" (4.60). Nothing in the Projectivy issue tracker asks for
animated *icon-pack* drawables; every animated-icon issue is about custom
icons picked from files. The device test agrees: the icon-pack route shows
frame 1, static.

Consequence for the test: on the icon-pack route the card shows frame 1 — the
centred glyph on a transparent card. That is the answer to question 1, and it
is also a usable static icon (a bigger glyph than the banner's lockup).

## What Projectivy does with animated custom icons

From the [public changelog](https://github.com/spocky/miproja1/releases) and
issue tracker (`spocky/miproja1`):

| Version | Date | Animated-icon change |
|---|---|---|
| 4.60 | 2025-01-22 | Per-card custom icons **[premium]**: local image or icon-pack pick, "with support for animated gif and partial support for animated webp" |
| 4.65 | 2025-07-14 | "On animated gif cards, the animation is now stopped (instead of paused) on focus lost" — implements [#300](https://github.com/spocky/miproja1/issues/300) |
| 4.66/4.67 | 2025-08 | Reverted the stop-on-first-frame hack after crashes; [#324](https://github.com/spocky/miproja1/issues/324) ("gif keeps looping after deselection — stop call failing") closed by the rollback |
| 4.70-beta01 | 2026-04-02 | New decode library; "improved support for animated images format (gif, apng, webp) as wallpapers and custom icons" ([#322](https://github.com/spocky/miproja1/issues/322), the APNG request); "animated cards (gif, apng…) now reset to their 1st frame on stop (instead of pausing)"; animated cards stop when the launcher goes idle; the initially selected card animates |
| 4.70 | 2026-06-26 | Stable; APNG crash fixes ([#459](https://github.com/spocky/miproja1/issues/459) "apng's do not show correctly in All Apps menu" fixed in the betas) |
| 4.72 | 2026-10-03 | "Added ability to limit animated icons looping count"; Core Builds Icon Pack added to Projectivy's installable third-party packs |

The behaviour model that falls out of this (issue #300's reporter describes it
directly: *"The animation start when the Icon is in focus. But when I scroll
to the next Icon the Gif animation gets paused"*):

- **Starts on focus.** The animation begins when the card gains focus.
- **Stops on focus lost** (4.65+), and **resets to frame 1 on stop** (4.70+).
  Before 4.70 the card froze on whatever frame it was on — the exact defect
  #300 requested fixed, because a half-morphed card looks broken.
- **Stops when the launcher goes idle** (4.70+), so a row of animated cards
  does not decode forever.
- **Loops.** Pre-4.72 the loop is unbounded; 4.72 added a user setting to
  limit it. Whether the file's own loop count is honoured is not documented —
  question 3 exists to measure it.

Two practical notes:

- **Custom card icons are a Premium feature** (4.60's "[premium only]";
  Projectivy's FAQ lists "Card icon customization (custom images or icon
  packs)" among the one-time-purchase features). The manual route of this test
  needs it.
- **Format support is Projectivy's, not the framework's.** Projectivy decodes
  the file itself: GIF since 4.60, WebP partial in 4.60 and full in 4.70, APNG
  since 4.70 (APNG is not in the Android framework at all — the platform's
  `AnimatedImageDrawable` covers GIF and WebP only,
  [developer.android.com](https://developer.android.com/social-and-messaging/guides/media-animated-gif)).
  The community's animated icons are GIFs — the XDA thread's makers describe
  building them in PowerPoint and exporting GIF
  ([XDA](https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/)).

## What this means for the morph design

- **Frame 1 is the glyph — that is the whole design, and 4.70+ makes it
  correct.** Projectivy stops the animation on focus loss and shows frame 1.
  A morph file whose first frame is the centred glyph gets "glyph at rest,
  banner on focus" for free on Projectivy ≥ 4.70. Before 4.70 the card would
  freeze mid-morph or on the banner — upgrade or accept it.
- **The last frame is the shipped banner, held.** While focused (and if the
  launcher honours play-once), the card rests on the banner — the pack's
  normal look.
- **Play-once vs ping-pong covers both loop behaviours.** If the launcher
  honours the file's loop count (loop 1 = play once), the play-once file is
  the ideal shape. If it loops every animated icon (pre-4.72 behaviour), the
  play-once file would cut from banner back to glyph each cycle; the
  ping-pong file (`*_morph_loop.*`, 30 frames, loop 0) eases back instead.
  On 4.72+ the loop-count setting is a third way to get play-once.
- **Format guidance — try WebP first.** Animated WebP is the smallest file
  with full-alpha lossless frames (16–57 KB here), decodes through the same
  platform path (`ImageDecoder`) on Android 9+, and Projectivy supports it
  for custom icons since 4.70 (partially since 4.60). GIF is the compatibility
  fallback (every Projectivy that animates anything animates GIF, and it is
  what the community ships), at the cost of 256 colours and 1-bit alpha.
  APNG is lossless like WebP but needs Projectivy ≥ 4.70's own decoder and had
  display bugs fixed during the 4.70 betas — last resort, and proof that
  "APNG is what the community uses" is out of date: the community uses GIF.
- **Cost is small.** 320×180 RGBA × 16 frames is ~3.7 MB decoded per icon;
  only the focused card animates and idle cards stop, so a row of ten morphs
  is a rounding error. 4.72 also caps Projectivy's image cache at 50 MB.

## Device test plan — what to record

For each format (WebP, GIF, APNG) and each flavour (play-once, ping-pong),
set as a manual custom icon on one card and note:

| Observation | Play-once file | Ping-pong file |
|---|---|---|
| Animation starts on focus gain? | | |
| Plays once, or loops? | | |
| On focus loss: resets to glyph (frame 1), freezes, or keeps playing? | | |
| Stops when the launcher goes idle? | | |

Version notes for reading the results:

- **Projectivy < 4.65**: card pauses on focus loss (no reset) — the morph will
  freeze mid-flight. Expected, not a pack defect.
- **4.65–4.69**: the stop call is unreliable after changing icons or returning
  from settings ([#324](https://github.com/spocky/miproja1/issues/324)) —
  animated cards may keep looping until the launcher restarts. Expected.
- **≥ 4.70**: reset to frame 1 on stop is the designed behaviour.
- **≥ 4.72**: try the animated-icons loop-count setting as a third variable.
- **Icon-pack route**: re-confirm static on the Projectivy version under test
  (4.72 improved icon-pack detection and can map Projectivy's internal
  activities in packs — worth one re-run even though 4.71 already showed
  frame 1).

Decision table:

| Outcome | Meaning |
|---|---|
| Icon-pack route animates | Report upstream — that would be a first; re-check the pack's drawable actually shipped in the test APK |
| Custom icon animates, honours loop count | Ship-shape: play-once files are the recommendation |
| Custom icon animates but loops the play-once file | Pre-4.72 Projectivy; recommend the ping-pong files or the 4.72 loop setting |
| No reset to frame 1 on focus loss | Projectivy < 4.70; recommend 4.70+ for the morph experience |
| APNG broken, GIF/WebP fine | Projectivy < 4.70 or an APNG regression; use WebP |

## Sources

- Projectivy Launcher — features ("Custom Icons: Use your (animated) images or
  icon packs") and FAQ (Premium): <https://projectivylauncher.com/>
- Projectivy Launcher — plugins (icon-pack support):
  <https://projectivylauncher.com/plugins.html>
- `spocky/miproja1` release notes, versions 4.60–4.72:
  <https://github.com/spocky/miproja1/releases>
- `spocky/miproja1` issue #300 — "[Request] Reset Gif icons when not longer
  focused" (focus-starts-animation report; fixed in 4.65):
  <https://github.com/spocky/miproja1/issues/300>
- `spocky/miproja1` issue #322 — "[Request] animated pngs (.apng)" (implemented
  in 4.70): <https://github.com/spocky/miproja1/issues/322>
- `spocky/miproja1` issue #324 — "[Bug] Animated Gif icons keeps looping after
  deselection" (stop-call bug behind the 4.66/4.67 revert; decode-library
  motivation for 4.70): <https://github.com/spocky/miproja1/issues/324>
- `spocky/miproja1` issue #459 — "[Bug] apng's do not show correctly in 'All
  Apps' menu" (fixed in the 4.70 betas):
  <https://github.com/spocky/miproja1/issues/459>
- Android — `AnimatedImageDrawable` (API 28+; GIF and WebP; animates only while
  displayed): <https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable>
- Android — "Display animated GIFs" (platform support from API 28):
  <https://developer.android.com/social-and-messaging/guides/media-animated-gif>
- Stack Overflow — "Is it possible to have animated launcher icons for android
  apps?" (no Android standard):
  <https://stackoverflow.com/questions/27463068/is-it-possible-to-have-animated-launcher-icons-for-android-apps>
- XDA — "[APP][ANDROID TV] Projectivy Launcher" (community-made animated GIF
  card icons): <https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/>
- Android Police — "This Android TV launcher fixes everything I dislike about
  the default one" (custom icons behind Premium):
  <https://www.androidpolice.com/projectivy-android-tv-launcher-fixes-everything/>
