#!/usr/bin/env python3
"""
Contracts for the live wallpaper feature: the Motion, Deep Space, Cinema and Icon
Echo loops playing as the system live wallpaper.

Plain unittest over the repo tree, no Android SDK (mirrors
tests/test_wallpaper_export.py). Guards the wiring that would otherwise rot
silently:

  * the loop catalogue is tools/live_roster.py's forty-eight loops, each with a real
    MP4 in Motion/live/ and a bundled still frame for the grid and the engine
    fallback - a loop with no file is a 404 in the app, a frame with no bundle
    is a blank home screen before the first download
  * the service is bound by the system only (BIND_WALLPAPER), and adds no new
    permission and no new dependency
  * the engine is muted, loops, pauses off-screen, and falls back to the still
  * the downloader is the stills downloader's discipline on a video payload
  * a motion loop can never reach the stills exporter or the Pictures rotation
    folder, while still previewing, badging and talking to TalkBack like every
    other tile
  * the Monet path saves the MP4 to Movies/CoreBuilds, where Monet's own video
    picker reads it
  * the gate itself is wired into both workflows

Run from anywhere:
    python3 tests/test_live_wallpaper.py
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app" / "src" / "main"
RES = MAIN / "res"
KT = MAIN / "java/tv/corebuilds/iconpack"
MANIFEST = MAIN / "AndroidManifest.xml"
STRINGS = RES / "values/strings.xml"
ASSETS = MAIN / "assets"
LIVE_THUMBS = ASSETS / "live_thumbs"
MOTION_LIVE = ROOT / "Motion" / "live"
MOTION_FEED = ROOT / "Motion" / "live-feed.json"
GRADLE = ROOT / "app" / "build.gradle.kts"
NS = "{http://schemas.android.com/apk/res/android}"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def kt(name: str) -> str:
    return read(KT / name)


class LoopCatalogueTests(unittest.TestCase):
    """The loops the grid, the picker and the engine all agree on."""

    @classmethod
    def setUpClass(cls):
        cls.src = kt("LiveLoop.kt")
        # The Motion line's list. Since the Core Views season shipped, that
        # is MOTION_LOOPS; LiveLoop.LOOPS is it plus CoreViewsLoop.LOOPS, and
        # tests/test_coreviews_wallpapers.py pins the other half.
        cls.loops_body = re.search(
            r"val MOTION_LOOPS: List<Loop> = listOf\((.*?)\n    \)", cls.src, re.DOTALL
        ).group(1)
        cls.ids = re.findall(r'id = "([^"]+)"', cls.loops_body)
        cls.titles = re.findall(r'title = "([^"]+)"', cls.loops_body)
        cls.files = re.findall(r'fileName = "([^"]+)"', cls.loops_body)

    def test_the_loops_are_exactly_the_roster(self):
        # One loop per roster clip, in roster order, and nothing else: the
        # app's list, the feed and tools/live_roster.py are one list, so a
        # loop the roster drops (or adds) cannot linger (or go missing) in
        # the picker. Every set is the moving form of a whole still series.
        import sys
        sys.path.insert(0, str(ROOT / "tools"))
        from live_roster import ROSTER, MOTION, DEEP_SPACE, CINEMA, ICON_ECHO
        feed = json.loads(read(MOTION_FEED))
        feed_files = [e["url_1080p"].rsplit("/", 1)[-1] for e in feed]
        roster_files = [f"{c.stem}.mp4" for c in ROSTER]
        self.assertEqual(feed_files, roster_files, "live-feed.json is not the roster")
        self.assertEqual(self.files, roster_files, "LiveLoop.kt is not the roster")
        self.assertEqual([len(MOTION), len(DEEP_SPACE), len(CINEMA), len(ICON_ECHO)],
                         [12, 12, 12, 12])
        self.assertEqual(len(re.findall(r"\bLoop\(", self.loops_body)), 48, "forty-eight Loop entries")
        self.assertEqual(len(self.ids), 48)
        self.assertEqual(len(set(self.ids)), 48, "loop ids must be unique")
        self.assertEqual(self.ids, [c.id for c in ROSTER])
        for c in ROSTER:
            self.assertTrue(c.still.is_file(), f"loop {c.number} has no still: {c.still.name}")
            self.assertEqual(c.location, next(
                e["location"] for e in feed if e["url_1080p"].endswith(f"{c.stem}.mp4")))

    def test_revisions_are_the_committed_mp4s(self):
        # The cache key's revision is the sha256 prefix of the file in
        # Motion/live/. A re-render that is committed without re-running
        # tools/build_live_loop_kt.py would ship the old key, and devices
        # would keep playing the old animation: this is what catches it.
        import sys
        sys.path.insert(0, str(ROOT / "tools"))
        from live_roster import ROSTER
        revisions = re.findall(r'revision = "([^"]*)"', self.loops_body)
        self.assertEqual(len(revisions), 48, "every loop carries a revision")
        for rev in revisions:
            self.assertRegex(rev, r"^[0-9a-f]{12}$")
        self.assertEqual(revisions, [c.revision for c in ROSTER],
                         "LiveLoop.kt revisions are stale: run tools/build_live_loop_kt.py")

    def test_cache_name_carries_the_revision_and_export_name_does_not(self):
        # Keyed on fileName alone, a loop downloaded before 2.4.0 re-rendered it
        # kept playing the old animation. The Movies export keeps fileName.
        self.assertIn('"$revision-$fileName"', self.src)
        self.assertIn("fun isCopy(name: String): Boolean", self.src)
        exporter = kt("WallpaperExporter.kt")
        self.assertIn("copyFileToMovies(context, file, loop.fileName)", exporter)
        self.assertNotIn("storeName", exporter)

    def test_no_test_pattern_clips_remain(self):
        # 01-10 were ffmpeg gradients/cellauto/mandelbrot/sierpinski with
        # Core Builds names on them; retired in 2.4.0 when Live became a
        # set of walls given time. Their numbers stay empty, never reused.
        strays = sorted(p.name for p in MOTION_LIVE.glob("coremotion-live-*.mp4")
                        if int(p.name.split("-")[2]) <= 10)
        self.assertEqual(strays, [])
        self.assertFalse((ROOT / "tools" / "build_icon_echo_loops.py").exists())

    def test_every_loop_is_60fps_1080p(self):
        # The quality bar the 2.4.0 rebuild set: no 30 fps clip, no odd size.
        import subprocess
        try:
            import imageio_ffmpeg
            ff = imageio_ffmpeg.get_ffmpeg_exe()
        except Exception:
            self.skipTest("imageio-ffmpeg not installed")
        for name in self.files:
            err = subprocess.run([ff, "-i", str(MOTION_LIVE / name)],
                                 capture_output=True, text=True).stderr
            self.assertIn("1920x1080", err, name)
            self.assertRegex(err, r"\b60 fps\b", f"{name} is not 60 fps")

    def test_no_stale_bundled_frames(self):
        # Every bundled frame belongs to a listed loop (the 1.9.4 placeholder
        # clips' frames went with them).
        expected = {name.rsplit(".", 1)[0] + ".jpg" for name in self.files}
        self.assertEqual({p.name for p in LIVE_THUMBS.glob("*.jpg")}, expected)

    # Every loop id a released build has written to prefs (2.3.3, the last
    # release before the 2.4.0 rebuild). LiveLoop.byId falls back to the first
    # loop for an unknown id, so dropping or renaming one of these resets that
    # user's wallpaper without a word; the 2.4.0 rebuild nearly shipped
    # `signal-sweep` as `signal-red`. Add to this list, never remove from it.
    SHIPPED_IDS = (
        "event-horizon", "nebula-drift", "starfield", "ringed-planet",
        "galaxy-spiral", "aurora-orbit", "comet-lane", "deep-field",
        "ember-nova", "hex-station", "cyan-supernova", "dark-side-moon",
        "marquee-lights", "velvet-curtain", "projector-beam", "neon-lounge",
        "late-rentals", "box-office", "signal-sweep", "green-current",
        "molten-orbit",
    )

    def test_ids_are_stable_slugs(self):
        # The id is a persisted preference value. Renaming one silently
        # re-points every installed wallpaper back to the default.
        for loop_id in self.ids:
            self.assertRegex(loop_id, r"^[a-z0-9]+(-[a-z0-9]+)*$", loop_id)
        missing = [i for i in self.SHIPPED_IDS if i not in self.ids]
        self.assertEqual(missing, [], "a released loop id was renamed or dropped")

    def test_deep_space_wrapper_never_renders_other_sets(self):
        # build_still_loops reads an empty --only as "everything", so a wall
        # filter that matched nothing (a typo, or a clip number given as a
        # wall) used to overwrite all 36 still loops across three sets.
        import sys
        sys.path.insert(0, str(ROOT / "tools"))
        import build_deep_space_loops as wrapper
        calls = []
        real = wrapper._main
        wrapper._main = lambda argv: calls.append(argv) or 0
        try:
            wrapper.main(["--only", "85"])
            self.assertEqual(calls.pop(), ["--set", "Deep Space", "--only", "11"])
            wrapper.main([])
            self.assertEqual(calls.pop()[:2], ["--set", "Deep Space"])
            with self.assertRaises(SystemExit):
                wrapper.main(["--only", "11"])
            self.assertEqual(calls, [])
        finally:
            wrapper._main = real

    def test_every_loop_has_its_mp4_in_motion_live(self):
        for name in self.files:
            path = MOTION_LIVE / name
            self.assertTrue(path.exists(), f"no MP4 for loop: {path}")

    def test_every_mp4_is_really_an_mp4(self):
        # LiveLoopDownloader validates the ftyp box before trusting the file;
        # the committed sources have to pass the same check.
        for name in self.files:
            with (MOTION_LIVE / name).open("rb") as fh:
                head = fh.read(12)
            self.assertEqual(head[4:8], b"ftyp", f"{name} is not an MP4")

    def test_every_loop_has_a_bundled_still_frame(self):
        # The grid renders offline and the engine draws this before the MP4
        # lands: a missing frame is a blank tile and a blank home screen.
        for name in self.files:
            frame = LIVE_THUMBS / (name.rsplit(".", 1)[0] + ".jpg")
            self.assertTrue(frame.exists(), f"no bundled frame for {name}: {frame}")
            self.assertLess(frame.stat().st_size, 120_000, f"{frame.name} is too heavy for a grid tile")

    def test_no_full_video_bundled_into_the_apk(self):
        # Loops download once and then run offline, like a full-size still.
        bundled = sorted(p.name for p in ASSETS.rglob("*.mp4"))
        self.assertEqual(bundled, [], f"MP4s bundled into the APK: {bundled}")

    def test_urls_are_https_raw_github(self):
        base = re.search(r'RAW_BASE\s*=\s*\n?\s*"([^"]+)"', self.src).group(1)
        self.assertTrue(base.startswith("https://raw.githubusercontent.com/"), base)
        for name in self.files:
            self.assertIn(f'RAW_BASE + "{name}"', self.src, f"{name} has no raw URL")
        for url in re.findall(r'thumbUrl = "([^"]+)"', self.src):
            self.assertTrue(url.startswith("https://raw.githubusercontent.com/"), url)

    def test_titles_match_the_motion_feed(self):
        # The feed is what the Core Motion plugin and Overflight read; the app
        # naming the same clips differently is a defect a user can see.
        feed = json.loads(read(MOTION_FEED))
        by_file = {
            entry["url_1080p"].rsplit("/", 1)[-1]: entry["title"]
            for entry in feed
        }
        for title, name in zip(self.titles, self.files):
            self.assertIn(name, by_file, f"{name} is not in live-feed.json")
            self.assertEqual(title, by_file[name], f"{name} is titled differently in the feed")

    def test_loops_are_a_set_of_their_own(self):
        # Since 2.4.0 the loops sit under the Live chip only; a still series'
        # chip shows stills. The grid key is not a manifest series folder,
        # and the browser derives its series chips from the stills alone.
        self.assertIn('const val SERIES = "series-live"', self.src)
        self.assertIn("val series: String = LiveLoop.SERIES", self.src)
        self.assertIn("val stillSeries: String", self.src)
        self.assertEqual(self.loops_body.count("stillSeries = MOTION_SERIES"), 12)
        self.assertEqual(self.loops_body.count("stillSeries = DEEP_SPACE_SERIES"), 12)
        self.assertEqual(self.loops_body.count("stillSeries = CINEMA_SERIES"), 12)
        self.assertEqual(self.loops_body.count("stillSeries = ICON_ECHO_SERIES"), 12)
        self.assertNotIn("series = CINEMA_SERIES", self.loops_body)
        browser = kt("WallpapersActivity.kt")
        # Plus walls (Google Play) have their own chip too; see test_play_build.
        self.assertIn("all.filter { !it.isLive && !it.plus }.map { it.series }.distinct()", browser)
        manifest = json.loads(read(ROOT / "Wallpapers" / "manifest.json"))
        series = {w["series"] for w in manifest["wallpapers"]}
        self.assertNotIn("series-live", series, "the Live set is not a stills folder")
        for still in ("series-2-motion", "series-9-deep-space", "series-10-cinema", "series-11-icon-echo"):
            self.assertIn(still, series, f"{still} stills must exist for their loops")

    def test_as_wallpapers_marks_them_live(self):
        self.assertIn("isLive = true", self.src)
        self.assertIn("fun asWallpapers()", self.src)
        # The grid label comes from the loop title; the badge does the rest.
        self.assertIn("name = loop.title", self.src)
        self.assertIn("resolution = \"1920x1080\"", self.src)

    def test_loop_for_recovers_the_loop_from_a_row(self):
        # The preview screen gets Parcelables, so the mapping back to a loop is
        # on the cache name (the MP4 file name) and the URL.
        self.assertIn("fun loopFor(wallpaper: Wallpaper): Loop?", self.src)
        self.assertIn("it.fileName == wallpaper.cacheName", self.src)
        self.assertIn("it.url == wallpaper.url", self.src)

    def test_by_id_never_returns_null(self):
        # The engine has no UI to report an error to, so an unknown id gets the
        # shipped default rather than a blank home screen.
        self.assertIn("fun byId(id: String?): Loop", self.src)
        self.assertIn("?: LOOPS[0]", self.src)

    def test_wallpaper_model_carries_the_flag_through_a_parcel(self):
        src = kt("WallpaperCatalog.kt")
        self.assertIn("val isLive: Boolean = false", src)
        self.assertIn("parcel.writeInt(if (isLive) 1 else 0)", src)
        self.assertIn("isLive = parcel.readInt() == 1", src)


class ManifestTests(unittest.TestCase):
    """The system binds this service; nothing else can."""

    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(MANIFEST).getroot()
        cls.xml = read(MANIFEST)
        cls.strings = read(STRINGS)

    def _service(self):
        for el in self.root.iter("service"):
            if el.get(NS + "name") == ".LiveWallpaperService":
                return el
        self.fail(".LiveWallpaperService is not declared in the manifest")

    def test_service_is_declared_exported_and_permission_guarded(self):
        svc = self._service()
        self.assertEqual(svc.get(NS + "exported"), "true")
        self.assertEqual(
            svc.get(NS + "permission"),
            "android.permission.BIND_WALLPAPER",
            "only the system may bind the wallpaper engine",
        )

    def test_service_declares_the_wallpaper_intent_filter_and_metadata(self):
        svc = self._service()
        actions = {a.get(NS + "name") for a in svc.iter("intent-filter") for a in a}
        self.assertIn("android.service.wallpaper.WallpaperService", actions)
        meta = {m.get(NS + "name"): m.get(NS + "resource") for m in svc.iter("meta-data")}
        self.assertEqual(meta.get("android.service.wallpaper"), "@xml/live_wallpaper")

    def test_service_label_resolves(self):
        svc = self._service()
        label = svc.get(NS + "label").split("/")[-1]
        self.assertRegex(self.strings, rf'<string name="{label}"')

    def test_feature_adds_no_permission(self):
        # The CHANGELOG's claim is a contract: playing a loop needs nothing the
        # pack did not already hold. SET_WALLPAPER is the only wallpaper
        # permission, and it predates this feature.
        perms = {e.get(NS + "name") for e in self.root.findall("uses-permission")}
        self.assertEqual(
            perms & {"android.permission.BIND_WALLPAPER", "android.permission.CAMERA"},
            set(),
            "a live wallpaper is bound by the system; the app must not request BIND_WALLPAPER",
        )
        self.assertIn("android.permission.SET_WALLPAPER", perms)


class EngineTests(unittest.TestCase):
    """The engine plays the loop without costing battery or making noise."""

    @classmethod
    def setUpClass(cls):
        cls.src = kt("LiveWallpaperService.kt")

    def test_extends_wallpaper_service(self):
        self.assertIn("class LiveWallpaperService : WallpaperService()", self.src)
        self.assertIn("override fun onCreateEngine(): Engine", self.src)

    def test_playback_is_muted_and_looping(self):
        self.assertIn("isLooping = true", self.src)
        self.assertIn("setVolume(0f, 0f)", self.src)

    def test_pauses_when_not_visible(self):
        self.assertIn("override fun onVisibilityChanged(shown: Boolean)", self.src)
        self.assertIn("if (shown) startPlayback() else pausePlayback()", self.src)
        self.assertIn("runCatching { existing.pause() }", self.src)

    def test_releases_the_player(self):
        self.assertIn("runCatching { existing.release() }", self.src)
        self.assertIn("override fun onDestroy()", self.src)

    def test_still_frame_fallback(self):
        # Before the MP4 is on the device, or if it can no longer be read, the
        # bundled frame is drawn: a live wallpaper must never be a blank or
        # black home screen.
        self.assertIn("private fun drawStill()", self.src)
        self.assertIn("loop.thumbAsset", self.src)
        self.assertIn("lockCanvas()", self.src)
        self.assertIn("unlockCanvasAndPost", self.src)

    def test_reads_the_choice_on_every_visibility_change(self):
        # Re-picking a loop re-points wallpaper that is already active.
        self.assertIn("Prefs.liveLoopId(applicationContext)", self.src)
        self.assertIn("LiveLoop.byId(", self.src)

    def test_keeps_the_old_render_until_the_new_one_lands(self):
        # The engine plays the current render or, failing that, an older copy
        # of the same loop - never the still frame just because an update
        # re-rendered the loop - and switches when a newer file appears.
        self.assertIn("LiveLoopDownloader.playable(applicationContext, loop)", self.src)
        self.assertNotIn("LiveLoopDownloader.cached(", self.src)
        self.assertIn("next?.path != file?.path", self.src)

    def test_the_engine_never_downloads(self):
        # Nothing runs without a user action: the refresh belongs to opening
        # Wallpapers, never to the wallpaper engine.
        self.assertNotIn("fetch(", self.src)
        self.assertNotIn("refreshActive", self.src)

    def test_no_dependency_needed(self):
        self.assertNotIn("com.google.android.exoplayer", self.src)
        self.assertNotIn("import com.google", self.src)


class DownloaderTests(unittest.TestCase):
    """Same discipline as the stills downloader, on a video payload."""

    @classmethod
    def setUpClass(cls):
        cls.src = kt("LiveLoopDownloader.kt")
        cls.stills = kt("WallpaperDownloader.kt")

    def test_https_only_against_the_github_allowlist(self):
        self.assertIn("raw.githubusercontent.com", self.src)
        self.assertIn('if (parsed.protocol != "https"', self.src)
        self.assertIn("Refusing non-GitHub URL", self.src)

    def test_caches_in_files_dir_not_cache_dir(self):
        # The system may reclaim the cache directory at any time; a wallpaper
        # that stopped playing because Android cleaned up would be a bug the
        # user cannot diagnose.
        self.assertIn("context.applicationContext.filesDir", self.src)
        self.assertNotIn("context.cacheDir", self.src)

    def test_atomic_write_and_size_ceiling(self):
        self.assertIn(".part", self.src)
        self.assertIn("MAX_BYTES", self.src)
        self.assertIn("MIN_BYTES", self.src)

    def test_validates_the_mp4_header(self):
        self.assertIn("ftyp", self.src)
        self.assertIn("Downloaded file is not an MP4", self.src)

    def test_never_evicts_the_active_loop(self):
        src = kt("LiveLoopDownloader.kt")
        trim = src.split("private fun trimCache(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("LiveLoop.byId(Prefs.liveLoopId(context))", trim)
        # Every copy of the active loop: the older render is what plays
        # until the current one has downloaded.
        self.assertIn("!active.isCopy(it.name)", trim)

    def test_current_render_is_the_cache_key(self):
        self.assertIn("cachedFile(context.applicationContext, loop.storeName)", self.src)
        self.assertIn("fetchUrl(context, loop.url, loop.storeName, callback)", self.src)

    def test_older_renders_are_dropped_once_the_current_one_lands(self):
        trim = self.src.split("private fun trimCache(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("if (loop.storeName !in names) continue", trim)
        self.assertIn("it != loop.storeName && loop.isCopy(it)", trim)

    def test_the_active_loop_refreshes_when_wallpapers_opens(self):
        refresh = self.src.split("fun refreshActive(", 1)[1].split("\n    }\n", 1)[0]
        # Only when this app's live wallpaper is the one set, and only to
        # replace an older copy that is actually playing.
        self.assertIn("wallpaperInfo?.packageName == app.packageName", refresh)
        self.assertIn("if (cached(app, loop) != null || playable(app, loop) == null) return", refresh)
        self.assertIn("LiveLoopDownloader.refreshActive(this)", kt("WallpapersActivity.kt"))

    def test_coalesces_in_flight_requests(self):
        self.assertIn("ConcurrentHashMap", self.src)
        self.assertIn("inFlight", self.src)

    def test_never_decodes_a_bitmap(self):
        # The reason this is not WallpaperDownloader: that path validates every
        # byte as a 16:9 image and would reject an MP4 as corrupt.
        self.assertNotIn("BitmapFactory", self.src)
        self.assertIn("Event.Progress", self.src)
        self.assertIn("Event.Ready", self.src)
        self.assertIn("Event.Failed", self.src)


class BrowserIntegrationTests(unittest.TestCase):
    """Loops ride the stills grid, and never the stills export path."""

    def test_browser_appends_the_loops_to_the_catalog(self):
        src = kt("WallpapersActivity.kt")
        self.assertIn("WallpaperCatalog.load(this) + plusWalls + LiveLoop.asWallpapers()", src)

    def test_bulk_save_routes_loops_to_movies(self):
        # A loop is saved like a still, but as video into Movies/CoreBuilds
        # through the loop downloader - never through the stills exporter's
        # PNG path, which would reject the MP4.
        src = kt("WallpaperExporter.kt")
        self.assertIn("if (wp.isLive) {", src)
        self.assertIn("LiveLoopDownloader.fetch(context, loop)", src)
        self.assertIn("WallpaperSetter.copyFileToMovies(context, file, loop.fileName)", src)
        self.assertIn("WallpaperSetter.alreadyExportedVideo(", src)
        act = kt("WallpapersActivity.kt")
        # Back from the storage prompt, through the same gate as the button.
        self.assertIn("if (granted) beginExport(adapter.selectedItems())", act)

    def test_adapter_badges_and_describes_live_tiles(self):
        src = kt("WallpaperAdapter.kt")
        self.assertIn("holder.badge.visibility = if (item.isLive || item.plus)", src)
        self.assertIn("R.string.wp_live_spoken", src)

    def test_loops_are_selectable_for_bulk_save(self):
        # Saving loops one preview at a time was the complaint: Select all and
        # long-press take loops like stills.
        src = kt("WallpaperAdapter.kt")
        self.assertNotIn("if (item.isLive) return", src)
        self.assertIn("selected.addAll(items.map { it.cacheName })", src)

    def test_live_chip_gathers_every_loop(self):
        src = kt("WallpapersActivity.kt")
        self.assertIn("R.string.chip_live", src)
        self.assertIn("LIVE_KEY -> all.filter { it.isLive }", src)
        self.assertRegex(read(STRINGS), r'<string name="chip_live">Live</string>')

    def test_receipt_names_the_movies_folder(self):
        src = kt("ExportProgressActivity.kt")
        self.assertIn("R.string.wp_export_loops_done_fmt", src)
        self.assertIn("R.string.wp_after_export_hint_live", src)

    def test_preview_hides_save_and_switches_the_label(self):
        src = kt("WallpaperPreviewActivity.kt")
        self.assertIn("saveButton.visibility = if (wp.isLive) View.GONE else View.VISIBLE", src)
        self.assertIn("if (currentIsLive) {", src)
        self.assertIn("R.string.wp_set_live", src)
        self.assertIn("R.string.wp_save_for_monet", src)

    def test_preview_downloads_the_loop_rather_than_a_bitmap(self):
        src = kt("WallpaperPreviewActivity.kt")
        self.assertIn("if (wp.isLive) beginLiveDownload(wp, gen) else beginDownload(wp, gen)", src)
        self.assertIn("LiveLoopDownloader.fetch(this, loop)", src)
        self.assertIn("LiveLoop.loopFor(wp)", src)

    def test_set_live_stores_the_choice_and_opens_the_picker(self):
        src = kt("WallpaperPreviewActivity.kt")
        self.assertIn("Prefs.setLiveLoopId(this, loop.id)", src)
        self.assertIn("WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER", src)
        self.assertIn("WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT", src)
        self.assertIn("ComponentName(this@WallpaperPreviewActivity, LiveWallpaperService::class.java)", src)
        # A device with no live picker is reported, not crashed on.
        self.assertIn("R.string.wp_live_no_picker", src)

    def test_set_live_waits_for_the_download(self):
        src = kt("WallpaperPreviewActivity.kt")
        self.assertIn("R.string.wp_live_download_needed", src)
        self.assertIn("val file = liveFile", src)

    def test_monet_path_saves_the_mp4_to_movies(self):
        src = kt("WallpaperPreviewActivity.kt")
        self.assertIn("saveLiveForMonet(", src)
        self.assertIn("WallpaperSetter.copyFileToMovies(", src)
        self.assertIn("R.string.wp_live_monet_hint", src)


class MoviesCopyTests(unittest.TestCase):
    """The video sibling of copyFileToPictures, on the video collection."""

    @classmethod
    def setUpClass(cls):
        cls.src = kt("WallpaperSetter.kt")

    def test_helper_is_public_and_streams_the_original_bytes(self):
        self.assertRegex(self.src, r"fun\s+copyFileToMovies\s*\(")
        self.assertIn("file.inputStream()", self.src)
        self.assertIn("copyTo(out", self.src)

    def test_writes_video_into_movies_corebuilds(self):
        self.assertIn("MediaStore.Video.Media.EXTERNAL_CONTENT_URI", self.src)
        self.assertIn('"video/mp4"', self.src)
        self.assertIn("Environment.DIRECTORY_MOVIES", self.src)
        self.assertIn('"${Environment.DIRECTORY_MOVIES}/CoreBuilds"', self.src)

    def test_honours_the_storage_permission_gate(self):
        self.assertIn("Result.NeedsPermission(perm)", self.src)

    def test_does_not_leave_a_pending_row_behind(self):
        self.assertIn("MediaStore.Video.Media.IS_PENDING, 0", self.src)
        self.assertIn("contentResolver.delete(uri, null, null)", self.src)

    def test_pictures_copy_is_untouched(self):
        # The stills path is unchanged; this is a sibling, not a replacement.
        self.assertIn("fun copyFileToPictures(", self.src)
        self.assertIn("MediaStore.Images.Media.EXTERNAL_CONTENT_URI", self.src)


class ResourceTests(unittest.TestCase):
    """Everything the live branch names resolves, and nothing new is needed."""

    @classmethod
    def setUpClass(cls):
        cls.strings = read(STRINGS)

    def test_live_strings_present(self):
        for key in (
            "wp_live_badge", "wp_live_spoken", "wp_set_live", "wp_save_for_monet",
            "wp_live_sub", "wp_live_set_done", "wp_live_no_picker",
            "wp_live_download_needed", "wp_live_monet_hint",
            "live_wallpaper_label", "live_wallpaper_desc",
        ):
            self.assertRegex(self.strings, rf'<string name="{key}"', f"string/{key} missing")

    def test_every_string_the_live_kotlin_uses_exists(self):
        declared = set(re.findall(r'<string name="([^"]+)"', self.strings))
        for name in ("LiveLoop.kt", "LiveLoopDownloader.kt", "LiveWallpaperService.kt",
                     "WallpaperPreviewActivity.kt", "WallpapersActivity.kt",
                     "WallpaperAdapter.kt", "WallpaperSetter.kt", "Prefs.kt"):
            for ref in re.findall(r"R\.string\.([A-Za-z0-9_]+)", kt(name)):
                self.assertIn(ref, declared, f"{name} uses R.string.{ref}, which is not declared")

    def test_badge_drawable_exists_and_is_a_shape(self):
        path = RES / "drawable" / "bg_live_badge.xml"
        self.assertTrue(path.exists(), "bg_live_badge.xml missing")
        root = ET.fromstring(read(path))
        self.assertEqual(root.tag, "shape")
        self.assertEqual(root.get(NS + "shape"), "rectangle")

    def test_badge_is_a_label_not_a_control(self):
        root = ET.fromstring(read(RES / "layout" / "item_wallpaper.xml"))
        badge = None
        for el in root.iter():
            if (el.get(NS + "id") or "").endswith("wp_live_badge"):
                badge = el
        self.assertIsNotNone(badge, "item_wallpaper.xml has no wp_live_badge")
        self.assertNotEqual(badge.get(NS + "focusable"), "true")
        self.assertNotEqual(badge.get(NS + "clickable"), "true")
        # Hidden until the adapter shows it, so the stills grid is unchanged.
        self.assertEqual(badge.get(NS + "visibility"), "gone")
        self.assertEqual(badge.get(NS + "importantForAccessibility"), "no")

    def test_wallpaper_metadata_xml_names_a_thumbnail(self):
        path = RES / "xml" / "live_wallpaper.xml"
        self.assertTrue(path.exists(), "res/xml/live_wallpaper.xml missing")
        root = ET.fromstring(read(path))
        self.assertEqual(root.tag, "wallpaper")
        self.assertEqual(root.get(NS + "label"), "@string/live_wallpaper_label")
        self.assertEqual(root.get(NS + "description"), "@string/live_wallpaper_desc")
        thumb = root.get(NS + "thumbnail").split("/")[-1]
        # The picker runs in the system process, so the preview tile has to be
        # a drawable rather than an asset.
        drawables = {
            p.stem
            for d in RES.glob("drawable*") if d.is_dir()
            for p in d.iterdir() if p.is_file()
        }
        self.assertIn(thumb, drawables, f"@drawable/{thumb} does not resolve")

    def test_module_gains_no_dependency(self):
        # "No new dependency" in the CHANGELOG is a contract: a looping video
        # wallpaper is framework MediaPlayer plus WallpaperService.
        deps = re.findall(r'implementation\("([^"]+)"\)', read(GRADLE))
        self.assertEqual(
            deps,
            ["androidx.appcompat:appcompat:1.7.0",
             "androidx.core:core-ktx:1.13.1",
             "androidx.recyclerview:recyclerview:1.3.2"],
            "the live wallpaper feature must not pull in a media library",
        )


class CiWiringTests(unittest.TestCase):
    """A gate nobody has watched fail in CI is a gate nobody can trust."""

    @staticmethod
    def workflows() -> dict[str, str]:
        return {
            p.name: read(p)
            for p in sorted((ROOT / ".github" / "workflows").glob("*.yml"))
        }

    def test_gate_runs_in_both_workflows(self) -> None:
        texts = self.workflows()
        for name in ("build.yml", "suite-ci.yml"):
            self.assertIn("python tests/test_live_wallpaper.py", texts[name],
                          f"{name} never runs this gate")


if __name__ == "__main__":
    unittest.main(verbosity=2)
