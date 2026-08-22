# Core Motion + Core Shift — ship plan & risk register

Locked product decision (reconfirmed): **Core Shift remains a standalone app**
(`dev.corebuilds.shift`) — it has simply been *replaced* by the live-wallpaper
browser (v2.0.0), not removed. Core Motion (`tv.corebuilds.motion`) is the
Projectivy plugin. Same content, two delivery surfaces.

---

## Phase 1 — the plan (remaining work to ship)

Everything is code-complete; what's left is **verify → release**. In order:

1. **Unblock CI.** Push `.github/workflows/core-motion-plugin.yml`. *Status:
   blocked — the session's GitHub App token still lacks `workflows: write` even
   after the permission toggle; the App must be **re-installed/re-authorized**
   on the repo. The file is on disk, complete and YAML-validated.*
2. **Compile both apps** (first real build) — `motion-plugin/` and `shift/`,
   via CI or a JDK-17 + SDK-34 machine. Fix any compile errors and report them
   by file.
3. **Render the shaders** (first real run) — `tools/build_shaders.py` under
   Mesa software GL (`LIBGL_ALWAYS_SOFTWARE=1 EGL_PLATFORM=surfaceless
   xvfb-run -a …`). Validate output, append to `live-feed.json`.
4. **Validate the feed** — `tools/validate_motion_feed.py` (currently 10 OK).
5. **Device-verify** the two open facts: plugin appears in Projectivy (Premium)
   and the loops/Lottie render; Core Shift downloads + previews + Monet plays
   them.
6. **Release** — tag `motion-v0.1.0` and `shift-v2.0.0`, generate Downloader
   codes, update the root `README.md` app table.

### Deliverables (already in PR #33)

- `motion-plugin/` — Projectivy wallpaper provider (video feed + Lottie).
- `shift/` — rebuilt live-wallpaper browser (preview + download).
- `motion-shaders/` + `tools/build_shaders.py` — GLSL pipeline.
- `Motion/live/` (10 loops) + `Motion/live-feed.json`.
- `tools/build_motion_feed.py`, `tools/validate_motion_feed.py`.

---

## Phase 2 — risk register (most → least risky)

1. **Nothing has compiled or rendered (HIGH).** Both apps are unbuilt; the three
   shaders have never executed (no GL/SDK in the authoring sandbox). *Mitigation:
   CI build + software-GL render jobs are the first gate; fix by file; the AIDL
   is verbatim so contract risk is low, toolchain risk is the open part.*

2. **Monet re-themes from a downloaded video — unverified (HIGH).** The whole
   Monet path (Core Shift → `Movies/CoreBuilds` → Monet "your videos") is
   unverified; Monet may not play or re-theme these MP4s. *Mitigation: this is
   exactly the device test in step 5; if it fails, the honest fallback is the
   image-folder route, and the copy already says "set it in Monet".*

3. **Preview streaming (HIGH, now fixed).** `VideoView` streaming straight from
   `raw.githubusercontent.com` could fail on content-type/range. *Mitigation:
   PreviewActivity now downloads to cache and plays the local file.*

4. **Lottie `ty:"sr"` star-path unverified (MED).** Hand-authored JSON, valid but
   never rendered; the star-shape type may not be supported by Projectivy's
   Lottie engine. *Mitigation: ship the 4-point diamond first; fall back to an
   explicit `ty:"sh"` Bezier hexagon if `sr` is rejected.*

5. **Video reliability on weak hardware (MED).** 1080p default, silent, short
   loops already mitigate; the Mandelbrot "deep zoom" is the heaviest clip.

6. **Plugin discovery (MED-LOW).** The contract is byte-identical to the
   template and the manifest metadata is correct; residual risk is only a
   Projectivy-version / Premium gate.

7. **`workflows` permission recurrence (MED-LOW).** Even after this push, the
   App token can't write workflow files; any future CI edit needs a user token
   or the App re-authorized.

---

## Phase 3 — code review (done this pass)

Findings, most → least critical, all fixed except the unverifiable ones:

1. **`PreviewActivity` streamed from raw.githubusercontent.com** — unreliable
   content-type/range → now downloads to cache and plays locally. *(fixed)*
2. **Redundant loop wiring** — `isLooping` + `onCompletionListener{start}` +
   silent `onError` → cleaned up; failures now show text. *(fixed)*
3. **`LiveDownloader.fetchToCache` was private** — PreviewActivity couldn't reuse
   the cache; exposed as public `fetch`. *(fixed)*
4. **`LiveEntry.thumbAsset` crash risk** — a feed entry with no `url_img` built
   an invalid asset path → now nullable, guarded in the adapter. *(fixed)*
5. **CI shader job lacked `EGL_PLATFORM=surfaceless`** — headless Mesa may need
   it → added to the (still-unpushed) workflow. *(fixed on disk)*
6. **Shaders + `build_shaders.py` unverified** — cannot be fixed here (no GL);
   the CI render job is the verification. *(deferred to CI — flagged, not
   guessed)*
