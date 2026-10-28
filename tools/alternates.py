"""Alternative icons: owner-supplied art a user picks by hand.

An alternate is extra art for an app the catalog already covers. It is
listed in both icon browsers (drawable.xml) and in the in-app grid the
launcher's per-app "choose icon" picker opens, right after the icon it is an
alternative to, and it is never in an appfilter, so a launcher never applies
it on its own. The catalog icon stays the default.

Each one is the art its owner supplied, resampled and nothing else: no
glyph, no accent, no presence keyline. That is the one exception to the
pack's rule that brand artwork is reference-only (catalog "artwork"), so it
is held to its own contract here:

  * the source file is committed under tools/alternates/ and pinned by
    sha256, so the art cannot change without the catalog saying so;
  * the drawable is named after its icon, `<of>_alt` (then `_alt2`, ...),
    and no catalog icon may use that suffix;
  * counts of icons ("1022 icons") are counts of apps, so alternates are
    left out of them — see is_alternate().

catalog.json:

  "alternates": {
    "wuplay_alt": {
      "of": "wuplay",                  the catalog icon it stands beside
      "name": "WuPlay (alternative)",  shown in the in-app grid
      "file": "tools/alternates/wuplay.png",
      "sha256": "...",
      "square": [x0, y0, x1, y1],      source box holding the square mark
      "clear_from_x": 1673,            source columns cleared before the crop
      "source": "...", "usage": "manual-only"
    }
  }

The source is a 16:9 lockup: its banner is the whole image, its square the
`square` box (cleared from `clear_from_x` on, where a wordmark overlaps the
box), scaled to fill the 432px safe area and centred on the 512 canvas.
"""
from __future__ import annotations

import hashlib
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

GRID = 512
SAFE = 432
BANNER = (320, 180)
ALT_RE = re.compile(r"_alt\d*$")


def is_alternate(drawable: str) -> bool:
    """True for an alternate's square or banner drawable name."""
    return bool(ALT_RE.search(drawable.removesuffix("_banner")))


def entries(data: dict) -> list[tuple[str, dict]]:
    """(drawable, spec) pairs in catalog order."""
    return list((data.get("alternates") or {}).items())


def errors(data: dict) -> list[str]:
    """Named problems with the alternates block; empty when it is sound."""
    out = []
    icons = {i["drawable"]: i for i in data["icons"]}
    for d in icons:
        if is_alternate(d):
            out.append(f"{d}: catalog icons may not end in _alt — that suffix "
                       f"marks a manual-only alternate")
    for d, spec in entries(data):
        of = spec.get("of")
        if of not in icons:
            out.append(f"{d}: alternate of unknown icon {of!r}")
            continue
        if not re.fullmatch(rf"{re.escape(of)}_alt\d*", d):
            out.append(f"{d}: an alternate of {of} is named {of}_alt (or _alt2, ...)")
        if spec.get("usage") != "manual-only":
            out.append(f"{d}: alternates are manual-only — never auto-applied")
        if not isinstance(spec.get("name"), str) or not spec["name"].strip():
            out.append(f"{d}: alternate needs a name for the in-app grid")
        if not spec.get("source"):
            out.append(f"{d}: alternate carries no source — who supplied it?")
        box = spec.get("square")
        if not (isinstance(box, list) and len(box) == 4
                and all(isinstance(v, int) for v in box)
                and box[2] - box[0] == box[3] - box[1] > 0):
            out.append(f"{d}: square must be a square [x0, y0, x1, y1] box")
        path = ROOT / spec.get("file", "")
        if not spec.get("file") or not path.is_file():
            out.append(f"{d}: source file {spec.get('file')!r} is missing")
            continue
        data_bytes = path.read_bytes()
        digest = hashlib.sha256(data_bytes).hexdigest()
        if digest != spec.get("sha256"):
            out.append(f"{d}: {spec['file']} does not match its sha256 — the "
                       f"supplied art changed; update the hash on purpose")
        if data_bytes[:8] != b"\x89PNG\r\n\x1a\n":
            out.append(f"{d}: {spec['file']} must be a PNG")
            continue
        w, h = struct.unpack(">II", data_bytes[16:24])
        if w * 9 != h * 16:
            out.append(f"{d}: {spec['file']} is {w}x{h}; the banner is the whole "
                       f"source, so it must be 16:9")
        if isinstance(box, list) and len(box) == 4 and not (
                0 <= box[0] < box[2] <= w and 0 <= box[1] < box[3] <= h):
            out.append(f"{d}: square box {box} falls outside the {w}x{h} source")
    return out


def _source(spec: dict):
    from PIL import Image
    with Image.open(ROOT / spec["file"]) as im:
        return im.convert("RGBA")


def square(spec: dict):
    """The square: the supplied mark scaled into the safe area, centred."""
    from PIL import Image
    src = _source(spec)
    clear = spec.get("clear_from_x")
    if clear is not None:
        src.paste((0, 0, 0, 0), (clear, 0, src.width, src.height))
    mark = src.crop(tuple(spec["square"])).resize((SAFE, SAFE), Image.LANCZOS)
    tile = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    pad = (GRID - SAFE) // 2
    tile.paste(mark, (pad, pad))
    return tile


def banner(spec: dict):
    """The banner: the whole supplied lockup at 320x180."""
    from PIL import Image
    return _source(spec).resize(BANNER, Image.LANCZOS)


def after_parent(order: list[str], data: dict) -> list[str]:
    """`order` (catalog drawables, browser order) with each alternate placed
    straight after the icon it stands beside."""
    by_parent: dict[str, list[str]] = {}
    for d, spec in entries(data):
        by_parent.setdefault(spec["of"], []).append(d)
    out = []
    for d in order:
        out.append(d)
        out.extend(by_parent.get(d, []))
    return out
