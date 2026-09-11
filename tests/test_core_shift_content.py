"""Source-level contracts for the Core Shift content update path."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHIFT = ROOT / "shift" / "app" / "src" / "main"


def read(relative: str) -> str:
    return (SHIFT / relative).read_text(encoding="utf-8")


def test_shift_merges_remote_prequels_with_bundled_fallback():
    catalog = read("java/dev/corebuilds/shift/LiveEntry.kt")
    remote = read("java/dev/corebuilds/shift/RemoteLiveCatalog.kt")
    activity = read("java/dev/corebuilds/shift/MainActivity.kt")
    assert "loadCachedPrequels" in catalog
    assert "fun merge" in catalog
    assert "PREQUEL_FEED_URL" in remote
    assert "prequel_feed_json" in catalog
    assert "LiveCatalog.merge" in activity
    assert "refreshContent()" in activity


def test_remote_content_is_separate_from_apk_update():
    activity = read("java/dev/corebuilds/shift/MainActivity.kt")
    remote = read("java/dev/corebuilds/shift/RemoteLiveCatalog.kt")
    assert "UpdateChecker.check" in activity
    assert "networkSucceeded" in remote
    # The version pair is checked as agreement with the registry, not against a
    # literal. A test that names "the current release" has to be edited on every
    # release, and it fails for shipping a newer one — which is exactly how this
    # one ended up stale (2.3.4/10, while shift shipped 2.3.5/11) invisible: the
    # file had no runner. suite.json is the registry, and
    # tools/check_suite_truth.py already holds Gradle to it.
    suite = json.loads((ROOT / "suite.json").read_text())["apps"]["shift"]
    gradle = (ROOT / "shift/app/build.gradle.kts").read_text()
    assert f'versionName = "{suite["versionName"]}"' in gradle, \
        "shift Gradle versionName disagrees with suite.json"
    assert f'versionCode = {suite["versionCode"]}' in gradle, \
        "shift Gradle versionCode disagrees with suite.json"
    update = (ROOT / "Latestrelease/shift-version.json").read_text()
    assert f'"versionName": "{suite["versionName"]}"' in update, \
        "shift update manifest is not on the shipped version — users are told they are current"
    assert f'"versionCode": {suite["versionCode"]}' in update, \
        "shift update manifest code is not on the shipped version"
    assert "content_banner" in (SHIFT / "res/layout/activity_main.xml").read_text()
    assert "motion-prequels/prequel-feed.json" in remote


def test_app_has_immediate_procedural_seed_catalog_and_stage():
    seeds = SHIFT / "assets/manifest/prequels.json"
    stage = read("java/dev/corebuilds/shift/CoreMotionPreviewView.kt")
    activity = read("java/dev/corebuilds/shift/ProceduralPreviewActivity.kt")
    layout = (SHIFT / "res/layout/activity_main.xml").read_text()
    import json
    data = json.loads(seeds.read_text())
    assert len(data) == 16
    assert {entry["scene"] for entry in data} == set(range(16))
    assert "drawOrbitals" in stage and "drawHorizon" in stage
    assert "setScene" in activity
    assert "motion_stage" in layout
    assert "CORE PREQUEL ENGINE" in (SHIFT / "res/values/strings.xml").read_text()


def test_library_focus_path_is_explicit():
    activity = read("java/dev/corebuilds/shift/MainActivity.kt")
    layout = (SHIFT / "res/layout/activity_main.xml").read_text()
    assert "requestInitialFocus" in activity
    assert "requestFocus" in activity
    assert "nextFocusDown=\"@id/live_list\"" in layout
    assert "descendantFocusability=\"afterDescendants\"" in layout


def test_all_preview_and_quality_buttons_are_wired():
    activity = read("java/dev/corebuilds/shift/MainActivity.kt")
    procedural = read("java/dev/corebuilds/shift/ProceduralPreviewActivity.kt")
    for token in ("speed_slow", "speed_normal", "speed_fast", "length_10", "length_20", "length_30", "quality_1080", "quality_4k"):
        assert token in activity
    for token in ("proc_speed_slow", "proc_speed_normal", "proc_speed_fast", "proc_length_10", "proc_length_20", "proc_length_30"):
        assert token in procedural
    assert "setControls" in procedural and "setPreviewMotion" in activity


def test_quality_selector_is_wired_to_both_delivery_tiers():
    quality = read("java/dev/corebuilds/shift/QualityTier.kt")
    adapter = read("java/dev/corebuilds/shift/LiveAdapter.kt")
    downloader = read("java/dev/corebuilds/shift/LiveDownloader.kt")
    layout = (SHIFT / "res/layout/activity_main.xml").read_text()
    seeds = (SHIFT / "assets/manifest/prequels.json").read_text()
    assert "HD_1080" in quality and "UHD_4K" in quality
    assert "url4k" in quality and "urlFor" in downloader
    assert "setQuality" in adapter and "quality_1080" in layout and "quality_4k" in layout
    assert seeds.count('"url_4k"') == 16


def test_remote_posters_are_bounded_and_recycled_safely():
    loader = read("java/dev/corebuilds/shift/RemoteThumbLoader.kt")
    adapter = read("java/dev/corebuilds/shift/LiveAdapter.kt")
    assert "MAX_BYTES" in loader
    assert "readBounded" in loader or "total <= MAX_BYTES" in loader
    assert "RemoteThumbLoader.load" in adapter
    assert "holder.thumb.tag == loadedUrl" in adapter


def test_downloads_have_progress_bounded_integrity_and_retry_states():
    downloader = read("java/dev/corebuilds/shift/LiveDownloader.kt")
    adapter = read("java/dev/corebuilds/shift/LiveAdapter.kt")
    activity = read("java/dev/corebuilds/shift/MainActivity.kt")
    assert "MAX_VIDEO_BYTES" in downloader
    assert "StatFs" in downloader
    assert "isMp4" in downloader
    assert "onProgress" in downloader and "download_progress_fmt" in activity
    assert "retrying" in adapter and "R.string.retry" in adapter


def test_video_preview_uses_tv_transport_controls():
    activity = read("java/dev/corebuilds/shift/PreviewActivity.kt")
    layout = (SHIFT / "res/layout/activity_preview.xml").read_text()
    gradle = (ROOT / "shift/app/build.gradle.kts").read_text()
    assert "ExoPlayer" in activity and "MediaSession" in activity
    assert "KEEP_SCREEN_ON" in activity
    assert "PlayerView" in layout and "use_controller" in layout
    assert "media3-exoplayer" in gradle and "media3-session" in gradle


def test_feed_and_asset_hosts_are_not_arbitrary_relays():
    remote = read("java/dev/corebuilds/shift/RemoteLiveCatalog.kt")
    loader = read("java/dev/corebuilds/shift/RemoteThumbLoader.kt")
    for source in (remote, loader):
        assert "ALLOWED_" in source
        assert "protocol == \"https\"" in source
        assert "allowlisted" in source
def main() -> int:
    """Run every ``test_*`` in this module and report.

    This file used to be a list of bare test functions with no runner, so
    `python tests/<this file>` exited 0 having asserted nothing — every
    expectation inside it could rot silently, and some of them had.
    Discovering the functions here means a new test cannot escape the run.
    """
    tests = [(n, f) for n, f in sorted(globals().items())
             if n.startswith("test_") and callable(f)]
    failed = 0
    for name, fn in tests:
        try:
            fn()
            print(f"ok  {name}")
        except Exception as exc:
            failed += 1
            print(f"FAIL {name}: {exc!r}")
    print(f"{len(tests) - failed}/{len(tests)} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
