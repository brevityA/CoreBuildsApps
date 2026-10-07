#!/usr/bin/env python3
"""Render a purposive visual sample across catalog mark types.

This board shows current Core vector glyphs only. It is deliberately separate
from the 17 pinned-source comparison board: rows without pinned vectors have
not been scored against vendor art. Only the review PNG is written.

Run with the dependencies in tools/requirements.txt:
    python tools/build_recreation_accuracy_sample.py
"""
from __future__ import annotations

import io
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(TOOLS))
from svg_renderer import svg2png  # noqa: E402

OUT = ROOT / "docs/research/recreation-accuracy-sample-2026-10.png"
CATALOG = TOOLS / "catalog.json"
SVG_DIR = ROOT / "assets/svg"
FONTS = TOOLS / "fonts"

WIDTH, HEIGHT = 1536, 2100
BG, PANEL, THUMB_BG, BORDER = "#0D1117", "#111822", "#151923", "#2A3543"
INK, MUTED, CYAN, AMBER = "#E6EDF3", "#95A3B5", "#00D4FF", "#F6C85F"
CARD_W, CARD_H = 462, 270
CARD_X = (50, 537, 1024)
ROW_START, ROW_STEP = 270, 282
THUMB = 126

# name, drawable, tag, reviewer note. Entry names are checked against the
# current catalog so a rename or remap cannot silently produce a stale board.
SAMPLES = [
    ("AI Cam View", "aicamview", "BRAND-INFORMED", "Official Play-listing launcher icon is cited for the camera-in-A cue; no source vector is pinned."),
    ("Ace Stream", "ace_stream", "BRAND-INFORMED", "Projectivy Icon Pack 1.1.9 is the recorded reference (reference-only), not an independently checked official asset."),
    ("AniLab", "anilab", "BRAND-INFORMED", "Projectivy reference supplies the cue; exact current vendor-art comparison is not pinned."),
    ("stc tv", "intigral", "WORDMARK-DERIVED", "The ‘stc tv’ logotype cue is redrawn in Core letters; vendor typography is not copied."),
    ("Virgin Media Play", "threeplayer", "WORDMARK-DERIVED", "‘VM’ plus play is derived from the Google Play listing icon; the original letterforms are not traced."),
    ("Local 10+", "aimitv", "WORDMARK-DERIVED", "‘10’ plus is derived from an APKCombo mirror listing; identity/source confidence is lower than first-party evidence."),
    ("A 8k Player Vip", "a_8k_player_vip", "FUNCTIONAL / SHARED", "Generic IPTV-player cue is reused by 13 catalog rows; this is not a vendor-logo claim."),
    ("CX File Explorer", "cxinventor", "FUNCTIONAL / SHARED", "Generic folder glyph is reused by 9 entries; source art and brand identity are separate questions."),
    ("TV (letter-shell example)", "tcl_tv", "MONOGRAM / FALLBACK", "The glyph registry classifies this letter-in-broadcast-shell mark as a monogram, not a verified vendor logo."),
    ("HeatLive", "heatlive", "FAMILY GLYPH", "Flame-in-drop; the v2.0.0 square is the family baseline and is held unchanged."),
    ("HeatVod", "heatvod", "FAMILY GLYPH", "Same family drop with a play cue; color is based on a photographed launcher tile and is approximate."),
    ("HeatVod Ultra", "heatvod_ultra", "FAMILY GLYPH", "HeatVod play plus an Ultra corner cue; the family relationship is explicit, the source is a user photo."),
    ("HeatLive Backup", "heatlive_backup", "FAMILY GLYPH", "HeatLive flame plus a spare-drop cue; color is sampled from a photographed tile, so approximate."),
    ("BBC iPlayer ×3", "iplayer", "VARIANT / REUSE", "One play-beam glyph serves 3 package variants; mapping reuse is not 3 independent visual verifications."),
    ("9Now ×2", "ninenow", "VARIANT / REUSE", "One glyph serves the 9Now and CTV rows; both inherit the same mark and accent."),
    ("Syncler ×3", "syncler", "VARIANT / REUSE", "One ring glyph serves three builds; separate package mapping does not establish a fresh logo source."),
    ("Paramount+ ×3", "ott", "VARIANT / REUSE", "One mountain glyph serves three package/region rows; its source comparison is on the pinned-reference board."),
]


def get_font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def text(draw: ImageDraw.ImageDraw, xy: tuple[int, int], value: str,
         size: int, color: str = INK, *, mono: bool = False,
         regular: bool = False) -> None:
    file = "DejaVuSansMono.ttf" if mono else (
        "Outfit-Bold.ttf" if regular else "Outfit-ExtraBold.ttf"
    )
    draw.text(xy, value, font=get_font(file, size), fill=color)


def wrap_words(value: str, max_chars: int) -> list[str]:
    lines: list[str] = []
    line = ""
    for word in value.split():
        candidate = f"{line} {word}".strip()
        if len(candidate) > max_chars and line:
            lines.append(line)
            line = word
        else:
            line = candidate
    if line:
        lines.append(line)
    return lines


def render_current(drawable: str) -> Image.Image:
    svg = SVG_DIR / f"{drawable}.svg"
    if not svg.is_file():
        raise SystemExit(f"Missing current vector: {svg}")
    png = svg2png(url=svg, output_width=THUMB, output_height=THUMB,
                  background_color=None)
    with Image.open(io.BytesIO(png)) as source:
        current = source.convert("RGBA")
    current.thumbnail((THUMB, THUMB), Image.Resampling.LANCZOS)
    tile = Image.new("RGBA", (THUMB, THUMB), (0, 0, 0, 0))
    tile.alpha_composite(current, ((THUMB - current.width) // 2,
                                   (THUMB - current.height) // 2))
    return tile


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_drawable = {row["drawable"]: row for row in catalog["icons"]}
    canvas = Image.new("RGB", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(canvas)
    text(draw, (50, 34), "CORE BUILDS  /  CATALOGUE SAMPLE", 14, CYAN, mono=True)
    text(draw, (50, 70), "Different mark types need different accuracy tests", 34)
    text(draw, (50, 126),
         "17 purposive samples · current Core vectors only · not a random or statistically representative sample",
         15, MUTED, regular=True)
    text(draw, (50, 158),
         "Use source confidence and cue retention by type; never treat every glyph as a vendor-logo recreation.",
         14, MUTED, regular=True)
    draw.line((50, 202, 1485, 202), fill=BORDER, width=1)

    for index, (display, drawable, kind, note) in enumerate(SAMPLES):
        row = by_drawable.get(drawable)
        if row is None or row["name"] not in display:
            raise SystemExit(f"Sample no longer matches catalog: {display} / {drawable}")
        col, line = index % 3, index // 3
        x, y = CARD_X[col], ROW_START + line * ROW_STEP
        tint = CYAN if kind.startswith("BRAND") or kind.startswith("WORDMARK") else (
            AMBER if kind.startswith("FAMILY") else MUTED
        )
        draw.rounded_rectangle((x, y, x + CARD_W, y + CARD_H), radius=14,
                               fill=PANEL, outline=BORDER, width=1)
        text(draw, (x + 18, y + 13), display, 17)
        text(draw, (x + 18, y + 40), kind, 9, tint, mono=True)

        image = render_current(drawable)
        bx, by = x + 18, y + 65
        draw.rounded_rectangle((bx, by, bx + THUMB, by + THUMB), radius=10,
                               fill=THUMB_BG, outline=BORDER, width=1)
        canvas.paste(image, (bx, by), image)

        detail_x = x + 164
        text(draw, (detail_x, y + 70), f"GLYPH  {row['glyph']}", 9, CYAN, mono=True)
        text(draw, (detail_x, y + 93), f"TYPE  {row['category']}", 9, MUTED, mono=True)
        text(draw, (detail_x, y + 116), f"DRAWABLE  {drawable}", 9, MUTED, mono=True)
        note_lines = wrap_words(note, 43)
        text(draw, (detail_x, y + 145), "\n".join(note_lines[:5]), 10,
             INK, regular=True)

    footer_y = ROW_START + ((len(SAMPLES) + 2) // 3) * ROW_STEP + 2
    draw.rounded_rectangle((50, footer_y, 1485, footer_y + 110), radius=14,
                           fill=PANEL, outline=BORDER, width=1)
    text(draw, (72, footer_y + 18), "SCOPE NOTE", 12, CYAN, mono=True)
    text(draw, (72, footer_y + 44),
         "The 17 pinned references are side-by-side source comparisons. These rows check visual type, internal cue,",
         13, INK, regular=True)
    text(draw, (72, footer_y + 67),
         "and catalog mapping. A missing per-entry source is uncertainty, not proof of incorrect artwork.",
         13, MUTED, regular=True)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT, optimize=True)
    print(f"Wrote {OUT.relative_to(ROOT)} ({WIDTH}×{HEIGHT}); "
          f"{len(SAMPLES)} checked sample rows across 6 mark types")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
