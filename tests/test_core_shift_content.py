"""Source-level contracts for the Core Shift content update path."""

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
    gradle = (ROOT / "shift/app/build.gradle.kts").read_text()
    assert 'versionName = "2.3.2"' in gradle
    assert 'versionCode = 8' in gradle
    update = (ROOT / "Latestrelease/shift-version.json").read_text()
    assert '"versionName": "2.3.2"' in update
    assert '"versionCode": 8' in update
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


def test_feed_and_asset_hosts_are_not_arbitrary_relays():
    remote = read("java/dev/corebuilds/shift/RemoteLiveCatalog.kt")
    loader = read("java/dev/corebuilds/shift/RemoteThumbLoader.kt")
    for source in (remote, loader):
        assert "ALLOWED_" in source
        assert "protocol == \"https\"" in source
        assert "allowlisted" in source
