#!/usr/bin/env python3
"""Publish the Core Views catalogue as the feeds the suite already reads.

`views/catalog.json` is the source of truth for Core Views: the place, the
time of day, the scene, the captions and the credits of every aerial view. This
script validates it and projects it into two consumer formats:

    views/aerial-entries.json   Aerial Views "community" feed
    views/overflight-feed.json  Overflight / Projectivy feed

Why a projection rather than two hand-written files: Aerial Views parses its
feed with `ignoreUnknownKeys = true`, so a misspelled `url-4K-SDR` does not
error — the entry silently loses its 4K rendition, in a user's living room,
weeks later. The consumer cannot catch that. This script can, because it owns
the key names.

The Aerial Views contract (read from the app's `AbstractVideo.kt`):

    url-1080-H264, url-1080-SDR, url-1080-HDR, url-4K-SDR, url-4K-HDR,
    url-audio, accessibilityLabel, pointsOfInterest, timeOfDay, scene

`timeOfDay` is one of day / night / sunset / sunrise, `scene` one of nature /
beach / countryside / waterfall / city / sea / space / patterns / fire.
Anything else becomes UNKNOWN in the app, which quietly drops the entry from a
user's playlist filter.

Only views that have at least one rendition are published. A planned view is a
plan, not a 404 in somebody's screensaver.

Usage:
    python tools/build_views_feed.py            # write both feeds
    python tools/build_views_feed.py --check    # verify committed feeds (CI)
    python tools/build_views_feed.py --plan     # print the season, write nothing
    python tools/build_views_feed.py --examples # write views/examples/ready-view.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "views" / "catalog.json"
AERIAL_TARGET = ROOT / "views" / "aerial-entries.json"
OVERFLIGHT_TARGET = ROOT / "views" / "overflight-feed.json"
EXAMPLE_TARGET = ROOT / "views" / "examples" / "ready-view.json"

AUTHOR = "Core Builds"
SERIES = "Core Views"

# Aerial Views vocabularies. These are enums in the app, not suggestions.
TIME_OF_DAY = {"day", "night", "sunset", "sunrise"}
SCENES = {
    "nature", "beach", "countryside", "waterfall",
    "city", "sea", "space", "patterns", "fire",
}
# Rendition key -> Aerial Views feed key. The right-hand side is the contract.
RENDITIONS = {
    "1080-H264": "url-1080-H264",
    "1080-SDR": "url-1080-SDR",
    "1080-HDR": "url-1080-HDR",
    "4K-SDR": "url-4K-SDR",
    "4K-HDR": "url-4K-HDR",
}
# Overflight's own names for the same renditions (see docs/PLUGIN_RESEARCH.md).
OVERFLIGHT_KEYS = {
    "1080-SDR": "url_1080p",
    "4K-SDR": "url_4k",
    "1080-HDR": "url_1080p_hdr",
    "4K-HDR": "url_4k_hdr",
}
LANES = {"drone", "render", "archive"}
STATUSES = {"planned", "ready"}

ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")
MEDIA_RE = re.compile(r"^[a-z0-9][a-z0-9-]*\.(mp4|jpg)$")
MIN_SECONDS = 20
MAX_SECONDS = 40


class CatalogError(Exception):
    """A catalogue entry that would publish wrong, or not publish at all."""


def label(path: Path) -> str:
    """Repo-relative for messages, absolute for a path outside the tree.

    The check-mode test points the targets at a temp directory; `relative_to`
    raises rather than printing something useful, and a diagnostic that raises
    is not a diagnostic.
    """
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def load_catalog(path: Path = CATALOG) -> dict:
    if not path.exists():
        raise CatalogError(f"{path.relative_to(ROOT)} is missing")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise CatalogError(f"{path.relative_to(ROOT)} is not valid JSON: {exc}") from exc
    if not isinstance(data, dict):
        raise CatalogError("catalog.json must be a JSON object")
    return data


def validate(catalog: dict) -> list[dict]:
    """Return the views, or raise on the first thing that would ship wrong."""
    if catalog.get("schema") != 1:
        raise CatalogError("catalog schema must be 1")

    base = catalog.get("baseUrl", "")
    if not base.startswith("https://"):
        raise CatalogError("baseUrl must be an https:// URL")
    if not base.endswith("/"):
        raise CatalogError("baseUrl must end with '/' so filenames concatenate cleanly")

    views = catalog.get("views")
    if not isinstance(views, list) or not views:
        raise CatalogError("catalog has no views")

    seen_ids: set[str] = set()
    seen_files: set[str] = set()
    numbers: set[int] = set()

    for index, view in enumerate(views):
        where = f"views[{index}]"
        if not isinstance(view, dict):
            raise CatalogError(f"{where} is not an object")

        view_id = view.get("id", "")
        if not isinstance(view_id, str) or not ID_RE.match(view_id):
            raise CatalogError(f"{where}.id {view_id!r} is not a lowercase slug")
        if view_id in seen_ids:
            raise CatalogError(f"{view_id}: duplicate id (feeds key favourites off it)")
        seen_ids.add(view_id)
        where = view_id

        number = view.get("number")
        if not isinstance(number, int) or number < 1:
            raise CatalogError(f"{where}: number must be a positive integer")
        if number in numbers:
            raise CatalogError(f"{where}: duplicate number {number}")
        numbers.add(number)

        for field in ("title", "place", "region"):
            if not isinstance(view.get(field), str) or not view[field].strip():
                raise CatalogError(f"{where}: {field} is required")

        coords = view.get("coordinates")
        if coords is not None:
            if (not isinstance(coords, list) or len(coords) != 2
                    or not all(isinstance(c, (int, float)) for c in coords)):
                raise CatalogError(f"{where}: coordinates must be [lat, lon] or null")
            lat, lon = coords
            if not -90 <= lat <= 90 or not -180 <= lon <= 180:
                raise CatalogError(f"{where}: coordinates out of range: {coords}")
        elif view.get("lane") != "archive":
            raise CatalogError(
                f"{where}: a {view.get('lane')!r} view needs coordinates; only an "
                "archive view may omit them")

        if view.get("timeOfDay") not in TIME_OF_DAY:
            raise CatalogError(
                f"{where}: timeOfDay {view.get('timeOfDay')!r} is not one of "
                f"{sorted(TIME_OF_DAY)} — the app maps anything else to UNKNOWN")
        if view.get("scene") not in SCENES:
            raise CatalogError(
                f"{where}: scene {view.get('scene')!r} is not one of {sorted(SCENES)}")
        if view.get("lane") not in LANES:
            raise CatalogError(f"{where}: lane {view.get('lane')!r} is not one of {sorted(LANES)}")
        if view.get("status") not in STATUSES:
            raise CatalogError(f"{where}: status {view.get('status')!r} is not one of {sorted(STATUSES)}")

        duration = view.get("durationSeconds")
        if not isinstance(duration, int) or not MIN_SECONDS <= duration <= MAX_SECONDS:
            raise CatalogError(
                f"{where}: durationSeconds must be an integer in "
                f"{MIN_SECONDS}..{MAX_SECONDS} (a screensaver loop, not a film)")

        captions = view.get("captions", [])
        if not isinstance(captions, list):
            raise CatalogError(f"{where}: captions must be a list")
        last_at = -1
        for caption in captions:
            if not isinstance(caption, dict):
                raise CatalogError(f"{where}: every caption is an object")
            at = caption.get("at")
            text = caption.get("text")
            if not isinstance(at, int) or at < 0 or at >= duration:
                raise CatalogError(
                    f"{where}: caption at {at!r} must be an integer inside the clip "
                    f"(0..{duration - 1})")
            if at <= last_at:
                raise CatalogError(f"{where}: captions must ascend by time")
            last_at = at
            if not isinstance(text, str) or not text.strip():
                raise CatalogError(f"{where}: caption at {at} has no text")

        credits = view.get("credits")
        if not isinstance(credits, list) or not credits:
            raise CatalogError(
                f"{where}: credits are required — a view with no provenance cannot "
                "ship (see the licence table in docs/research/core-views-aerial-2026-10.md)")
        for credit in credits:
            if not isinstance(credit, dict) or not credit.get("source") or not credit.get("license"):
                raise CatalogError(f"{where}: every credit needs a source and a license")

        renditions = view.get("renditions")
        if not isinstance(renditions, dict):
            raise CatalogError(f"{where}: renditions must be an object")
        unknown = set(renditions) - set(RENDITIONS)
        if unknown:
            raise CatalogError(
                f"{where}: unknown rendition keys {sorted(unknown)}; the app ignores "
                f"unknown keys, so this would ship as a silent gap. Allowed: {sorted(RENDITIONS)}")

        files = {name: value for name, value in renditions.items() if value}
        for name, value in files.items():
            if not isinstance(value, str) or not MEDIA_RE.match(value):
                raise CatalogError(
                    f"{where}: rendition {name} value {value!r} is not a media filename")
            if not value.startswith(view_id):
                raise CatalogError(
                    f"{where}: rendition {name} filename {value!r} must start with the id")
            if value in seen_files:
                raise CatalogError(f"{where}: {value} is used by another view")
            seen_files.add(value)

        thumbnail = view.get("thumbnail")
        if thumbnail is not None:
            if not isinstance(thumbnail, str) or not MEDIA_RE.match(thumbnail):
                raise CatalogError(f"{where}: thumbnail {thumbnail!r} is not a jpg filename")
            if thumbnail in seen_files:
                raise CatalogError(f"{where}: {thumbnail} is used by another view")
            seen_files.add(thumbnail)

        if view["status"] == "ready" and not files:
            raise CatalogError(
                f"{where}: status is ready but no rendition is set — either render it "
                "or mark it planned")
        if view["status"] == "planned" and files:
            raise CatalogError(
                f"{where}: a rendition is set but status is planned — publishing is "
                "driven by status, so the file would be ignored")

    return views


def ready_views(views: list[dict]) -> list[dict]:
    return sorted((v for v in views if v["status"] == "ready"), key=lambda v: v["number"])


def url_for(base: str, filename: str) -> str:
    return f"{base}{filename}"


def aerial_entry(view: dict, base: str) -> dict:
    """One Aerial Views "community" feed entry."""
    entry: dict = {
        "id": view["id"],
        # The only credit the app shows on screen, so the series rides with it.
        "accessibilityLabel": f"{view['title']} — {view['place']} · {SERIES} by {AUTHOR}",
        "type": "aerial",
        "timeOfDay": view["timeOfDay"],
        "scene": view["scene"],
    }
    for name, key in RENDITIONS.items():
        filename = view["renditions"].get(name)
        if filename:
            entry[key] = url_for(base, filename)
    if view["captions"]:
        # Map<Int, String> in the app: seconds -> caption.
        entry["pointsOfInterest"] = {str(c["at"]): c["text"] for c in view["captions"]}
    return entry


def overflight_entry(view: dict, base: str) -> dict:
    """One Overflight / Projectivy entry, which is lossier by design."""
    entry = {
        "location": f"{SERIES} · {view['region']}",
        "title": view["title"],
        "author": AUTHOR,
    }
    thumbnail = view.get("thumbnail")
    if thumbnail:
        entry["url_img"] = url_for(base, thumbnail)
    for name, key in OVERFLIGHT_KEYS.items():
        filename = view["renditions"].get(name)
        if filename:
            entry[key] = url_for(base, filename)
    return entry


def build(catalog: dict) -> tuple[str, str, list[dict]]:
    views = validate(catalog)
    base = catalog["baseUrl"]
    ready = ready_views(views)
    aerial = {"assets": [aerial_entry(v, base) for v in ready]}
    overflight = [overflight_entry(v, base) for v in ready]
    aerial_text = json.dumps(aerial, indent=2, ensure_ascii=False) + "\n"
    overflight_text = json.dumps(overflight, indent=2, ensure_ascii=False) + "\n"
    return aerial_text, overflight_text, views


def example_document(catalog: dict, views: list[dict]) -> str:
    """What one view looks like once rendered — the shape nobody can see today.

    Season 1 is all `planned`, so both feeds are empty and the file shapes only
    exist in prose. This writes a worked example from a real catalogue entry:
    the entry as it would be with renditions filled, and the two feed rows the
    builders derive from it. It is never published and never merged into the
    feeds.
    """
    candidates = [v for v in views if v["lane"] == "render"] or views
    source = sorted(candidates, key=lambda v: v["number"])[0]
    ready = json.loads(json.dumps(source))
    ready["status"] = "ready"
    ready["thumbnail"] = f"{ready['id']}.jpg"
    ready["renditions"] = {
        "1080-SDR": f"{ready['id']}.mp4",
        "4K-SDR": f"{ready['id']}-4k.mp4",
        "1080-HDR": None,
        "4K-HDR": None,
    }
    document = {
        "note": ("Worked example, not published. This is what one view looks like "
                 "once it has been rendered and flipped to ready, and what the "
                 "builders emit for it. Generated - do not hand-edit; run "
                 "python tools/build_views_feed.py --examples."),
        "catalogEntry": ready,
        "aerialViewsEntry": aerial_entry(ready, catalog["baseUrl"]),
        "overflightEntry": overflight_entry(ready, catalog["baseUrl"]),
    }
    return json.dumps(document, indent=2, ensure_ascii=False) + "\n"


def print_plan(views: list[dict]) -> None:
    ready = {v["id"] for v in ready_views(views)}
    print(f"{SERIES}: {len(views)} view(s), {len(ready)} ready")
    for view in sorted(views, key=lambda v: v["number"]):
        mark = "ready" if view["id"] in ready else "planned"
        print(f"  {view['number']:>2}. {view['title']:<18} {view['region']:<16} "
              f"{view['timeOfDay']:<8} {view['lane']:<8} {mark}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="fail if the committed feeds differ from the catalog")
    parser.add_argument("--plan", action="store_true",
                        help="print the season and write nothing")
    parser.add_argument("--examples", action="store_true",
                        help="write the worked example at views/examples/ready-view.json")
    args = parser.parse_args(argv)

    try:
        catalog = load_catalog()
        aerial_text, overflight_text, views = build(catalog)
    except CatalogError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    if args.plan:
        print_plan(views)
        return 0

    example = example_document(catalog, views)
    if args.examples:
        EXAMPLE_TARGET.parent.mkdir(parents=True, exist_ok=True)
        EXAMPLE_TARGET.write_text(example, encoding="utf-8")
        print(f"wrote {label(EXAMPLE_TARGET)}")
        return 0

    targets = [(AERIAL_TARGET, aerial_text), (OVERFLIGHT_TARGET, overflight_text)]

    if not ready_views(views):
        # Honest state: a season that has not been rendered publishes nothing.
        # A feed of dead URLs is worse than no feed.
        if args.check:
            problems = []
            committed = [label(t) for t, _ in targets if t.exists()]
            if committed:
                problems.append("no view is ready, but a feed is committed: "
                                + ", ".join(committed)
                                + " — remove it, or mark the rendered views ready")
            current_example = (EXAMPLE_TARGET.read_text(encoding="utf-8")
                               if EXAMPLE_TARGET.exists() else None)
            if current_example != example:
                problems.append(label(EXAMPLE_TARGET)
                                + " is out of date — run "
                                  "tools/build_views_feed.py --examples")
            if problems:
                for problem in problems:
                    print(f"error: {problem}", file=sys.stderr)
                return 1
            print(f"ok: no view is ready, so no feed is published "
                  f"({len(views)} planned); worked example current")
            return 0
        print_plan(views)
        print("nothing to publish: mark a rendered view ready in views/catalog.json")
        return 0

    if args.check:
        stale = []
        for target, rendered in targets:
            if not target.exists() or target.read_text(encoding="utf-8") != rendered:
                stale.append(label(target))
        current_example = (EXAMPLE_TARGET.read_text(encoding="utf-8")
                           if EXAMPLE_TARGET.exists() else None)
        if current_example != example:
            stale.append(label(EXAMPLE_TARGET))
        if stale:
            print("error: " + ", ".join(stale)
                  + " out of sync with views/catalog.json — run "
                  "tools/build_views_feed.py", file=sys.stderr)
            return 1
        print(f"ok: both feeds in sync ({len(ready_views(views))} ready view(s))")
        return 0

    for target, rendered in targets:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(rendered, encoding="utf-8")
        print(f"wrote {target.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
