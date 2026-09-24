#!/usr/bin/env python3
"""
Contracts for the live wallpaper feature: Series 9 / Deep Space loops playing
as the system live wallpaper.

Plain unittest over the repo tree, no Android SDK (mirrors
tests/test_wallpaper_export.py). Guards the wiring that would otherwise rot
silently:

  * the loop catalogue is the three Deep Space companions, each with a real
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
        cls.loops_body = re.search(
            r"val LOOPS: List<Loop> = listOf\((.*?)\n    \)", cls.src, re.DOTALL
        ).group(1)
        cls.ids = re.findall(r'id = "([^"]+)"', cls.loops_body)
        cls.titles = re.findall(r'title = "([^"]+)"', cls.loops_body)
        cls.files = re.findall(r'fileName = "([^"]+)"', cls.loops_body)

    def test_three_loops_and_no_more(self):
        # A live wallpaper plays behind every app, forever: each entry is a
        # real choice, not a catalogue. Three Deep Space companions is the
        # shipped set - a fourth is a product decision, not a code change.
        self.assertEqual(len(re.findall(r"\bLoop\(", self.loops_body)), 3, "three Loop entries")
        self.assertEqual(len(self.ids), 3)
        self.assertEqual(len(self.titles), 3)
        self.assertEqual(len(self.files), 3)
        self.assertEqual(len(set(self.ids)), 3, "loop ids must be unique")

    def test_ids_are_stable_slugs(self):
        # The id is a persisted preference value. Renaming one silently
        # re-points every installed wallpaper back to the default.
        for loop_id in self.ids:
            self.assertRegex(loop_id, r"^[a-z0-9]+(-[a-z0-9]+)*$", loop_id)

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

    def test_loops_ride_the_deep_space_series(self):
        # They are the moving half of series 9, so they appear under that chip
        # rather than in a series of their own that splits one wallpaper.
        self.assertIn('const val SERIES = "series-9-deep-space"', self.src)
        self.assertIn("series = SERIES", self.src)

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
        self.assertIn("WallpaperCatalog.load(this) + LiveLoop.asWallpapers()", src)

    def test_export_filters_live_out_of_both_paths(self):
        src = kt("WallpapersActivity.kt")
        self.assertIn("adapter.selectedItems().filter { !it.isLive }", src)
        self.assertIn("val stills = wallpapers.filter { !it.isLive }", src)
        self.assertIn("if (stills.isEmpty()) return", src)

    def test_adapter_badges_and_describes_live_tiles(self):
        src = kt("WallpaperAdapter.kt")
        self.assertIn("holder.badge.visibility = if (item.isLive)", src)
        self.assertIn("R.string.wp_live_spoken", src)

    def test_adapter_keeps_live_out_of_selection(self):
        src = kt("WallpaperAdapter.kt")
        self.assertIn("if (item.isLive) return", src)
        self.assertIn("items.filter { !it.isLive }.map { it.cacheName }", src)

    def test_long_press_on_a_loop_previews_it(self):
        # A long-press that did nothing would read broken, and a loop in the
        # Pictures rotation folder would break the rotation.
        src = kt("WallpaperAdapter.kt")
        self.assertIn("if (item.isLive) {", src)
        self.assertIn("onSelect(item)", src)

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
