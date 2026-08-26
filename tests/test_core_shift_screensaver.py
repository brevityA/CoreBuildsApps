"""Contracts for Core Shift 2.3.5 screensaver + Overflight feed."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHIFT = ROOT / "shift" / "app" / "src" / "main"


def test_dream_service_is_exported_with_bind_permission():
    manifest = (SHIFT / "AndroidManifest.xml").read_text(encoding="utf-8")
    assert 'android:name=".CoreDreamService"' in manifest
    assert "android.service.dreams.DreamService" in manifest
    assert "android.permission.BIND_DREAM_SERVICE" in manifest
    assert '@xml/dream' in manifest
    dream = (SHIFT / "res/xml/dream.xml").read_text(encoding="utf-8")
    assert "dev.corebuilds.shift/.MainActivity" in dream


def test_playlist_prefers_local_then_cache_then_catalog():
    playlist = (SHIFT / "java/dev/corebuilds/shift/DreamPlaylist.kt").read_text(
        encoding="utf-8"
    )
    assert "listMovies" in playlist
    assert "listCache" in playlist
    assert "LiveCatalog.load" in playlist
    assert "Movies/CoreBuilds" in playlist


def test_dream_has_no_media_session():
    service = (SHIFT / "java/dev/corebuilds/shift/CoreDreamService.kt").read_text(
        encoding="utf-8"
    )
    assert "MediaSession.Builder" not in service
    assert "volume = 0f" in service
    assert "REPEAT_MODE_ALL" in service
    assert "useController = false" in service


def test_screensaver_button_is_wired():
    activity = (SHIFT / "java/dev/corebuilds/shift/MainActivity.kt").read_text(
        encoding="utf-8"
    )
    layout = (SHIFT / "res/layout/activity_main.xml").read_text(encoding="utf-8")
    assert "btn_screensaver" in activity
    assert "openScreensaverSettings" in activity
    assert "ACTION_DREAM_SETTINGS" in activity
    assert 'android:id="@+id/btn_screensaver"' in layout


def test_version_is_235():
    gradle = (ROOT / "shift/app/build.gradle.kts").read_text(encoding="utf-8")
    assert 'versionName = "2.3.5"' in gradle
    assert "versionCode = 11" in gradle


def test_overflight_feed_merges_and_stays_https():
    feed_path = ROOT / "Motion" / "overflight-feed.json"
    assert feed_path.exists(), "run python tools/build_overflight_feed.py"
    feed = json.loads(feed_path.read_text(encoding="utf-8"))
    assert isinstance(feed, list) and len(feed) >= 10
    urls = [e.get("url_1080p") or e.get("url_4k") for e in feed]
    assert all(u and u.startswith("https://") for u in urls)
    assert len(urls) == len(set(urls))
    live = json.loads((ROOT / "Motion" / "live-feed.json").read_text(encoding="utf-8"))
    live_urls = {e["url_1080p"] for e in live if e.get("url_1080p")}
    combined = set(urls)
    assert live_urls <= combined


def main() -> int:
    tests = [
        test_dream_service_is_exported_with_bind_permission,
        test_playlist_prefers_local_then_cache_then_catalog,
        test_dream_has_no_media_session,
        test_screensaver_button_is_wired,
        test_version_is_235,
        test_overflight_feed_merges_and_stays_https,
    ]
    failed = 0
    for test in tests:
        try:
            test()
            print(f"ok  {test.__name__}")
        except Exception as exc:
            failed += 1
            print(f"FAIL {test.__name__}: {exc}")
    print(f"{len(tests) - failed}/{len(tests)} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
