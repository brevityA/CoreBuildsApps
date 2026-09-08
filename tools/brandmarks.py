"""Offline, provenance-checked brand silhouettes from tools/catalog.json.

Brand sources are deliberately a tiny SVG subset: a viewBox and filled paths,
no scripts, text/fonts, image links, transforms or network access. Their paths
are fitted mathematically to the shared 432px safe area (without stretching).
Flattening into the 512 grid also lets Pop apply its post-scale ink weight
without a nested source transform multiplying that weight by 18.
"""
from __future__ import annotations

import hashlib
import json
import math
import re
from pathlib import Path
import xml.etree.ElementTree as ET

from fontTools.misc.transform import Transform
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.svgLib.path import parse_path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "tools" / "brandmarks"
PAINT = re.compile(r"^(?:currentColor|#[0-9a-fA-F]{6})$")


def _number(value: float) -> str:
    return f"{value:.4f}".rstrip("0").rstrip(".") or "0"


def load_source(spec: dict) -> tuple[tuple[str, str, str], ...]:
    path = (ROOT / spec["file"]).resolve()
    if path.parent != SOURCE_DIR.resolve() or path.suffix != ".svg":
        raise ValueError(f"brand source outside tools/brandmarks/: {spec['file']}")
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != spec["sha256"]:
        raise ValueError(f"brand source checksum changed: {spec['file']}")
    if b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
        raise ValueError(f"brand sources cannot declare entities: {spec['file']}")
    root = ET.fromstring(raw)
    if root.tag.split("}")[-1] != "svg":
        raise ValueError(f"brand source is not SVG: {spec['file']}")
    bounds = BoundsPen(None)
    paths = []
    for el in root:
        tag = el.tag.split("}")[-1]
        if tag == "title":
            continue
        if tag != "path" or len(el) or set(el.attrib) - {"d", "fill", "fill-rule"}:
            raise ValueError(f"brand source must contain only filled paths: {spec['file']}")
        fill = el.get("fill", "currentColor")
        rule = el.get("fill-rule", "nonzero")
        if not PAINT.fullmatch(fill) or rule not in {"nonzero", "evenodd"}:
            raise ValueError(f"unsupported brand paint: {spec['file']}")
        d = el.attrib["d"]
        parse_path(d, bounds)
        paths.append((d, fill, rule))
    if not bounds.bounds or not all(math.isfinite(v) for v in bounds.bounds):
        raise ValueError(f"brand source has no finite ink bounds: {spec['file']}")
    left, top, right, bottom = bounds.bounds
    span = max(right - left, bottom - top)
    if span <= 0:
        raise ValueError(f"brand source has no area: {spec['file']}")
    scale = 432 / span
    xf = Transform(scale, 0, 0, scale,
                   256 - (left + right) * scale / 2,
                   256 - (top + bottom) * scale / 2)
    fitted = []
    for d, fill, rule in paths:
        pen = SVGPathPen(None, ntos=_number)
        parse_path(d, TransformPen(pen, xf))
        fitted.append((pen.getCommands(), fill, rule))
    return tuple(fitted)


def glyph_from_source(spec: dict):
    paths = load_source(spec)

    def render(accent: str) -> str:
        return "".join(
            f'<path d="{d}" fill="{accent if fill == "currentColor" else fill}" '
            f'fill-rule="{rule}" stroke="none"/>'
            for d, fill, rule in paths)
    bounds = BoundsPen(None)
    for d, _, _ in paths:
        parse_path(d, bounds)
    render.ink_bounds = bounds.bounds
    return render


def catalog_glyphs() -> dict:
    catalog = json.loads((ROOT / "tools" / "catalog.json").read_text(encoding="utf-8"))
    return {name: glyph_from_source(spec)
            for name, spec in catalog.get("artwork", {}).items()}
