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
    assert 'versionName = "2.2.0"' in gradle
    assert 'versionCode = 5' in gradle
    update = (ROOT / "Latestrelease/shift-version.json").read_text()
    assert '"versionName": "2.2.0"' in update
    assert '"versionCode": 5' in update
    assert "content_banner" in (SHIFT / "res/layout/activity_main.xml").read_text()
    assert "motion-prequels/prequel-feed.json" in remote


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
