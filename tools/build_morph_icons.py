#!/usr/bin/env python3
"""
Core Builds morph icons — TEST SET (not shipped in the production pack).

A glyph at rest that becomes its banner when a launcher animates it: frame 1
is the square mark, large and centred on the 16:9 card; over ~0.5 s the mark
eases into its exact place in the banner lockup while the rail and the name
fade and slide in, and the last frame IS the shipped banner. Played once
(loop count 1), so a launcher that animates the focused card and resets to
frame 1 when focus moves away shows "glyph, then banner on hover".

Every frame is rendered from the same SVG tools/build_banners.py draws: the
banner's glyph group is moved and scaled, the rest of the lockup is faded.
Nothing is traced or hand-drawn, so the end state cannot drift from the
banner the pack ships.

Writes, for the apps in TEST_SET:
  app/src/candidate/res/drawable-nodpi/<d>_morph.webp   animated WebP 320x180
  app/src/candidate/res/xml/appfilter.xml (+ assets)     the pack's appfilter
      with those apps' components mapped to <d>_morph
  app/src/candidate/res/xml/drawable.xml (+ assets)      a "Morph test" section
      first, so the icons are pickable per card
  docs/morph-test/<d>_morph.webp                         the same files, for
      trying them as manual per-card custom icons
  docs/morph-test/preview.gif                            contact sheet, animated

Only the candidate build type (tv.corebuilds.iconpack.test) sees the
candidate/ source set; the production pack is unchanged.

    python tools/build_morph_icons.py
"""
from __future__ import annotations

import io
import json
import math
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from build_banners import H, W, recentre, render  # noqa: E402
from svg_renderer import svg2png  # noqa: E402
from PIL import Image  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
MAIN = ROOT / "app" / "src" / "main"
CAND = ROOT / "app" / "src" / "candidate"
DOCS = ROOT / "docs" / "morph-test"

TEST_SET = ["Netflix", "Prime Video", "YouTube", "Disney+", "Plex",
            "Crunchyroll", "Apple TV", "Max", "Stremio", "Spotify"]

OUT_W, OUT_H = 320, 180
FRAMES = 16                # the move itself
FRAME_MS = 32              # ~0.5 s morph
REST_SCALE = 560 / 512     # the mark at rest: 78% of the card's height

GLYPH_RE = re.compile(
    r'  <g transform="translate\(([-\d.]+),([-\d.]+)\) scale\(([\d.]+)\)">(.*?)</g>\n',
    re.S)
WRAP_RE = re.compile(r'<g transform="translate\(([-\d.]+),([-\d.]+)\)">')


def ease(t: float) -> float:
    """Smooth in and out (cosine), so the mark settles rather than stops."""
    return 0.5 - 0.5 * math.cos(math.pi * t)


def frames_for(icon: dict) -> list[Image.Image]:
    mono = icon.get("color_note") == "monochrome"
    flat = render(icon["name"], icon.get("banner_glyph", icon["glyph"]), icon["color"],
                  icon.get("category"), monochrome=mono,
                  gradient=icon.get("gradient"), mark=icon.get("mark"),
                  style=icon.get("mark_style"))
    final = recentre(flat)
    # recentre() wraps the lockup in one translate; apply the same to every
    # frame so the last one is pixel-for-pixel the shipped banner.
    wrap = WRAP_RE.search(final.split(">", 1)[1])
    dx, dy = (float(wrap.group(1)), float(wrap.group(2))) if wrap else (0.0, 0.0)

    m = GLYPH_RE.search(flat)
    if not m:
        raise SystemExit(f"{icon['name']}: no glyph group in the banner SVG")
    gx, gy, gs = float(m.group(1)), float(m.group(2)), float(m.group(3))
    glyph_body = m.group(4)
    head = flat.split(">", 1)[0] + ">"
    rest = flat.split(">", 1)[1].rsplit("</svg>", 1)[0].replace(m.group(0), "")

    # At rest the mark is centred on the card in the recentred frame.
    rs = REST_SCALE
    rx = W / 2 - dx - 256 * rs
    ry = H / 2 - dy - 256 * rs

    out = []
    steps = [0.0] + [ease(k / (FRAMES - 1)) for k in range(1, FRAMES)]
    for t in steps:
        s = rs + (gs - rs) * t
        x = rx + (gx - rx) * t
        y = ry + (gy - ry) * t
        # The name arrives in the second half, sliding in from the mark.
        a = max(0.0, (t - 0.35) / 0.65)
        slide = (1 - a) * -60
        svg = (f'{head}\n  <g transform="translate({dx:.1f},{dy:.1f})">'
               f'<g opacity="{a:.3f}" transform="translate({slide:.1f},0)">{rest}</g>'
               f'<g transform="translate({x:.1f},{y:.1f}) scale({s:.5f})">{glyph_body}</g>'
               f'</g>\n</svg>\n')
        png = svg2png(bytestring=svg.encode(), output_width=OUT_W,
                      output_height=OUT_H, background_color=None)
        out.append(Image.open(io.BytesIO(png)).convert("RGBA"))
    return out


def save_webp(frames: list[Image.Image], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    durations = [120] + [FRAME_MS] * (len(frames) - 2) + [1000]
    frames[0].save(path, "WEBP", save_all=True, append_images=frames[1:],
                   duration=durations, loop=1, lossless=True, quality=100,
                   method=6)


def overlay_appfilter(targets: dict[str, str]) -> str:
    """The pack's appfilter with the test apps' components re-pointed."""
    text = (MAIN / "res" / "xml" / "appfilter.xml").read_text(encoding="utf-8")
    text = text.replace("<!-- Generated by", "<!-- MORPH TEST OVERLAY (candidate build only) of: Generated by", 1)
    for banner, morph in targets.items():
        text, n = re.subn(rf'drawable="{re.escape(banner)}"', f'drawable="{morph}"', text)
        if not n:
            raise SystemExit(f"{banner} is not in the appfilter")
    return text


def overlay_drawable(morphs: list[str]) -> str:
    text = (MAIN / "res" / "xml" / "drawable.xml").read_text(encoding="utf-8")
    section = ['    <category title="Morph test" />'] + [
        f'    <item drawable="{m}" />' for m in morphs]
    return text.replace("<resources>\n", "<resources>\n" + "\n".join(section) + "\n", 1)


def preview(sets: list[list[Image.Image]], path: Path) -> None:
    cols = 2
    rows = (len(sets) + cols - 1) // cols
    n = max(len(s) for s in sets)
    sheet_frames = []
    for k in range(n):
        sheet = Image.new("RGB", (cols * OUT_W, rows * OUT_H), (21, 25, 35))
        for i, frames in enumerate(sets):
            f = frames[min(k, len(frames) - 1)]
            sheet.paste(f, ((i % cols) * OUT_W, (i // cols) * OUT_H), f)
        sheet_frames.append(sheet)
    durations = [700] + [FRAME_MS * 2] * (n - 2) + [1500]
    sheet_frames[0].save(path, save_all=True, append_images=sheet_frames[1:],
                         duration=durations, loop=0)


def main() -> int:
    icons = {i["name"]: i for i in json.loads(CATALOG.read_text(encoding="utf-8"))["icons"]}
    targets, morphs, sets = {}, [], []
    for name in TEST_SET:
        icon = icons[name]
        morph = f"{icon['drawable']}_morph"
        frames = frames_for(icon)
        for dest in (CAND / "res" / "drawable-nodpi" / f"{morph}.webp",
                     DOCS / f"{morph}.webp"):
            save_webp(frames, dest)
        size = (DOCS / f"{morph}.webp").stat().st_size
        print(f"  {name:12} {morph}.webp  {len(frames)} frames  {size / 1024:.0f} KB")
        targets[f"{icon['drawable']}_banner"] = morph
        morphs.append(morph)
        sets.append(frames)

    af = overlay_appfilter(targets)
    dx = overlay_drawable(morphs)
    for rel, text in ((("res", "xml", "appfilter.xml"), af), (("assets", "appfilter.xml"), af),
                      (("res", "xml", "drawable.xml"), dx), (("assets", "drawable.xml"), dx)):
        p = CAND.joinpath(*rel)
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")
    preview(sets, DOCS / "preview.gif")
    print(f"morph test set written - {len(morphs)} icons; candidate appfilter + "
          "drawable.xml overlays; docs/morph-test/preview.gif")
    return 0


if __name__ == "__main__":
    sys.exit(main())
