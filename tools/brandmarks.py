"""Offline, hash-checked brand REFERENCES, never the Classic rendering registry.

The final icons are Core Builds-authored monoline geometry in glyphs.py.
This parser only validates and normalises the reference paths for audit/tests;
it intentionally cannot register a vendor SVG as a renderable pack glyph.
"""
from __future__ import annotations

import hashlib
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
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as exc:
        raise ValueError(f"invalid reference SVG: {spec['file']}") from exc
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
