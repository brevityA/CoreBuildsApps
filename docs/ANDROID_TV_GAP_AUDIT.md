# Core Shift / Core Motion Android TV gap audit

Audit date: 2026-08-24.

This review compares the current Core Shift implementation with Android's TV
UX, playback, accessibility and memory guidance. It is intentionally separate
from the procedural-rendering audit: the MP4 content release is complete, but a
successful APK build does not prove the remote-control UX is complete.

## What is already in good shape

- Core Shift declares a leanback launcher and no required touchscreen.
- The main stage is full-bleed/cropped rather than letterboxed.
- There is an initial focus helper and explicit focus-down paths.
- The prequel release has 16 1080p and 16 4K MP4s.
- The feed contains both quality URLs.
- Speed/loop controls are present for procedural preview.
- APK and content updates are separated.

## P0 — verify/fix before calling the app done

### 1. Real D-pad test on the shipped APK

The automated build and source checks are not enough. Android requires testing
that all visible controls are reachable, focused lists scroll with Up/Down,
Select activates an item, and Back returns to the previous context.

Source: [TV navigation](https://developer.android.com/training/tv/get-started/navigation).

Test matrix on a physical Android TV/Fire TV remote:

```text
Launch → first Preview focused and visibly outlined
Down   → library entry/next row becomes focused and scrolls into view
Right  → Download action becomes focused
OK     → download starts and shows progress
Up     → return to the previous control group
Back   → return from preview and restore the prior library focus
```

### 2. Prevent refresh from stealing focus

`MainActivity` currently calls the initial-focus routine after every successful
remote catalog refresh. If the user is already browsing when that callback
arrives, focus can jump back to item 0. Only request initial focus when the
screen has no focused actionable descendant; never reset a live user's focus.

Acceptance: start refresh, move to item 5, allow refresh to complete; focus must
remain on item 5.

### 3. Download must show state and failures

The current download flow is asynchronous but the UI does not provide a real
progress surface, cancel action, free-space check or a clear destination before
completion. Add at minimum:

- Queued / downloading / verifying / saved / failed states;
- received bytes and percentage when Content-Length is available;
- selected tier in the status (`1080p` or `4K`);
- retry action with the same tier;
- temporary-file cleanup on every failure;
- free-space check before starting a 4K download;
- an explicit `Movies/CoreBuilds/<filename>` confirmation.

Also add a hard maximum response size and verify the downloaded file before
publishing it to MediaStore. A trusted GitHub host is not a substitute for
bounded I/O.

## P1 — accessibility and playback correctness

### 4. Custom procedural view is not TalkBack-addressable

`CoreMotionPreviewView` is a custom Canvas view. Android documents that custom
views do not automatically expose their virtual content to accessibility
services. If the view remains decorative only, mark it non-actionable and put a
meaningful content description on the stage. If it contains selectable motion
scenes/actions, expose virtual nodes with `ExploreByTouchHelper` and click
actions.

Sources: [custom view accessibility](https://developer.android.com/training/tv/accessibility/custom-views),
[custom view sample](https://developer.android.com/training/tv/accessibility/custom-views-sample).

### 5. Full-screen video preview needs TV playback controls

`PreviewActivity` currently uses a `VideoView` with looping playback but no
play/pause, seek, quality indicator, retry/cancel, MediaSession or screen-on
handling. Android's TV checklist calls out media sessions, screen-on while
user-initiated video is playing, and consistent playback controls.

Source: [TV apps checklist](https://developer.android.com/training/tv/publishing/checklist).

At minimum, add:

- visible Play/Pause;
- Back that reliably returns to the previous focused row;
- selected quality label;
- retry on media error;
- `FLAG_KEEP_SCREEN_ON` while playing;
- a MediaSession or migrate playback to Media3;
- no autoplay when TalkBack/accessibility settings require user initiation.

### 6. Motion accessibility / reduced motion

The main screen starts a continuously animating custom view. Add a Pause/Play or
Reduce Motion control and respect the system animation scale where practical.
Pause the ticker when the Activity is paused or the view is not visible. This
also reduces battery, UI-thread work and OLED exposure.

The TalkBack guidance explicitly recommends checking that autoplay is not
intrusive and that users can pause/stop it: [TalkBack evaluation](https://developer.android.com/training/tv/accessibility/talkback).

## P1 — performance and memory

### 7. Canvas animation runs on the UI thread

`CoreMotionPreviewView.onDraw()` redraws approximately every 33 ms and performs
a large amount of Canvas path/gradient work. This can jank D-pad focus,
MediaPlayer callbacks and scrolling on low-RAM TV hardware.

Measure on at least one 1 GB device. Android recommends profiling graphics and
anonymous memory, using downscaled images, avoiding intermediate renders and
checking `ActivityManager.isLowRamDevice()`.

Source: [Optimize memory usage](https://developer.android.com/training/tv/playback/memory).

Preferred options:

1. use a GPU-backed `GLSurfaceView` for the procedural stage;
2. pause the stage whenever the list/preview is not visible;
3. lower the stage to 15 fps on low-RAM devices;
4. avoid allocating `LinearGradient`, `RadialGradient` and new `Path` objects
   inside every frame; reuse or precompute them.

### 8. Remote poster cache needs an eviction policy

`RemoteThumbLoader` bounds each response but does not enforce an aggregate cache
size or TTL. Add a small LRU/byte budget and remove stale `.part` files. This
matters when future collections grow beyond 16 prequels.

## P1 — content/update correctness

### 9. Use stable content IDs, not only URLs

`LiveCatalog.merge()` uses the MP4 URL as its identity. If a future release tag
or CDN path changes, the same wallpaper can appear twice. Use a stable ID such
as `coremotion-prequel-25-orbitals` or `scene=0 + series`, then let the newest
manifest replace metadata/URLs.

### 10. Add manifest freshness and integrity

The content updater should expose:

- feed version and published timestamp;
- last successful refresh time;
- whether the device is using cached content;
- ETag/Last-Modified conditional requests;
- optional SHA-256 and byte size per MP4.

The app can then say `Library updated` or `Using last known catalog` instead of
silently doing nothing on a 404/network error.

### 11. Make single-tier and dual-tier feeds explicit

The app should not enable a 4K selector just because a seed has a future URL.
Seeds should show `Preview only`; remote entries should unlock only after the
feed and a HEAD/range probe confirm the asset exists. The combined release
currently has both tiers, but this guard prevents future partial releases from
creating dead Download buttons.

## P2 — visual/design improvements

### 12. Migrate from row actions to card + detail actions

The current transitional UI has Preview/Download buttons in every list row.
The cleaner Android TV end state is:

```text
Hero / immersive preview
↓
Series 2 horizontal shelf
↓
Series 3 horizontal shelf
↓
Core Motion horizontal shelf
↓
Selected detail actions: Preview · 1080p/4K · Download
```

This matches Android's immersive-list and browse patterns and reduces focus
ambiguity. Use a visible 1.025–1.1x focus scale, outline/glow and tonal change.

Sources: [Immersive list](https://developer.android.com/design/ui/tv/guides/components/immersive-list),
[Focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system),
[Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts).

### 13. Keep TV-safe margins and type hierarchy

Design to Android's 960×540 MDPI TV canvas, keep important content inside the
recommended safe area, use 16:9 preview assets, and avoid explanatory paragraphs
on the main screen. Keep the stage title short and the delivery state obvious.

### 14. Do not use color alone

The Core Cyan focus treatment is on-brand, but selected/disabled/available must
also differ by label, outline, icon or opacity. Android's color guidance warns
against relying on color alone and recommends testing standard SDR and multiple
TV technologies: [Color on TV](https://developer.android.com/design/ui/tv/guides/foundations/color-on-tv).

## Release gate

Before publishing another APK, require:

- PR checks green;
- physical D-pad test matrix passed;
- TalkBack pass over all controls;
- one 1080p and one 4K download/playback pass;
- a 4K failure/partial-release simulation;
- low-RAM stage/profile run;
- `shift-v2.3.3` or later contains the focus/download/playback hardening;
- no workflow is publishing to a partial feed.
