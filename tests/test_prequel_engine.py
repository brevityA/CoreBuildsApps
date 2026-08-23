"""Contract tests for the Series 2/3 procedural prequel engine."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "motion-engine" / "prequels.json"
SHADER = ROOT / "motion-shaders" / "prequel_engine.frag"


def presets():
    data = json.loads(CATALOG.read_text())
    return [p for group in data["series"] for p in group["presets"]]


def test_catalog_covers_series_2_and_3_without_duplicates():
    data = json.loads(CATALOG.read_text())
    assert {g["id"] for g in data["series"]} == {"series-2-motion", "series-3-horizons"}
    items = presets()
    assert len(items) == 16
    assert [p["number"] for p in items] == list(range(25, 41))
    assert len({p["slug"] for p in items}) == len(items)
    assert all(p["source"].startswith("Wallpapers/series-") for p in items)


def test_scene_ids_and_palette_values_are_bounded():
    items = presets()
    assert {p["scene"] for p in items} == set(range(16))
    assert all(0.0 < p["intensity"] <= 1.0 for p in items)
    assert all(re.fullmatch(r"#[0-9a-fA-F]{6}", p["accent"]) for p in items)


def test_shader_has_real_scene_dispatch_and_periodic_clock():
    shader = SHADER.read_text()
    assert "uniform int u_scene" in shader
    assert "fract(u_time / max(u_loop, 0.001))" in shader
    assert "uniform float u_loop" in shader
    assert "sceneOrbitals" in shader and "sceneHorizon" in shader
    assert all(f"u_scene == {i}" in shader for i in range(8))
    assert "u_scene - 8" in shader
    # Guard against regressing to a still-image transform-only renderer.
    for token in ("segmentDistance", "fbm", "fract", "sin(", "cos("):
        assert token in shader
