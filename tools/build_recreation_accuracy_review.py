#!/usr/bin/env python3
"""Build a visual cue-retention audit for pinned brand references.

The board compares 17 source-hashed reference vectors with their current Core
square glyphs. It does not measure pixel similarity: the pack intentionally
redraws cues in its own geometry. Only the review PNG is written.

Run with the dependencies in tools/requirements.txt:
    python tools/build_recreation_accuracy_review.py
"""
from __future__ import annotations

import hashlib
import io
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(TOOLS))

from svg_renderer import svg2png  # noqa: E402

OUT = ROOT / "docs/research/recreation-accuracy-review-2026-10.png"
CATALOG = TOOLS / "catalog.json"
FONTS = TOOLS / "fonts"

WIDTH, HEIGHT = 1536, 2100
BACKGROUND = "#0D1117"
PANEL = "#111822"
CARD = "#151923"
LINE = "#2A3543"
INK = "#E6EDF3"
MUTED = "#95A3B5"
CYAN = "#00D4FF"

COLS, CARD_W, CARD_H = 3, 462, 270
CARD_X = (50, 537, 1024)
ROW_START, ROW_STEP = 270, 282
THUMB = 138

# Scores are a manual, cue-based review of the source/current silhouettes.
# They are not a legal opinion or a claim of user recognition.
AUDIT = {
    "netflix_ribbon": ("Netflix", "PARTIAL", "N silhouette kept; folded ribbon and variable-width red planes omitted."),
    "spotify_arcs": ("Spotify", "STRONG", "Ring and three rising sound arcs kept; vendor weight is simplified."),
    "kodi_box": ("Kodi", "PARTIAL", "Split diamond/K arrangement kept; facets and original proportions simplified."),
    "jellyfin_chevrons": ("Jellyfin", "STRONG", "Nested rounded-triangle cue kept; see the brand-use caution in the report."),
    "plex_chevron": ("Plex", "MAPPING GAP", "Pinned vector is the full Plex wordmark; Core is a chevron. Find a source for this cue."),
    "crunchyroll_eye": ("Crunchyroll", "PARTIAL", "Circular curl/eye cue retained; the original spiral detail is reduced."),
    "chat_screen": ("Twitch", "STRONG", "Stepped chat contour and twin bars retained in rounded linework."),
    "nordvpn_arrow": ("NordVPN", "PARTIAL", "Dome/mountain arrangement retained; exact filled arrow silhouette is abstracted."),
    "mubi_mark": ("MUBI", "STRONG", "Seven dots and the 2–3–2 arrangement are preserved."),
    "deezer_columns": ("Deezer", "PARTIAL", "Waveform rhythm retained; multicolour stepped bars become uniform strokes."),
    "proton_shield": ("Proton VPN", "PARTIAL", "Folded triangular/shield cue retained; ribbon folds are simplified."),
    "yt_play": ("YouTube", "STRONG", "Rounded play-button plus triangle retained; see the alteration guidance in the report."),
    "yt_music": ("YouTube Music", "STRONG", "Disc and play cue retained; secondary concentric detail is intentionally omitted."),
    "yt_kids": ("YouTube Kids", "PARTIAL", "Slanted button/play retained; the child-focused wordmark and colour cues are omitted."),
    "paramount_peak": ("Paramount+", "PARTIAL", "Mountain retained; star forms/count and circular seal are simplified or omitted."),
    "stremio_square": ("Stremio", "STRONG", "Diamond and play are retained; solid source planes become open monoline."),
    "nobuffr_mark": ("NoBuffr", "PARTIAL", "‘no’ and interrupted underline retained; the source’s ‘buffr’ wordmark is omitted."),
}


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def label(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str,
          size: int, color: str = INK, *, mono: bool = False,
          regular: bool = False) -> None:
    filename = "DejaVuSansMono.ttf" if mono else (
        "Outfit-Bold.ttf" if regular else "Outfit-ExtraBold.ttf"
    )
    draw.text(xy, text, font=font(filename, size), fill=color)


def fit_square(image: Image.Image, size: int) -> Image.Image:
    image = image.convert("RGBA")
    image.thumbnail((size, size), Image.Resampling.LANCZOS)
    tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    tile.alpha_composite(image, ((size - image.width) // 2,
                                 (size - image.height) // 2))
    return tile


def reference_shape(path: Path, size: int, expected_hash: str) -> Image.Image:
    data = path.read_bytes()
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected_hash:
        raise SystemExit(f"Pinned source changed for {path.name}: {actual}")
    png = svg2png(bytestring=data, output_width=size, output_height=size,
                  background_color=None)
    with Image.open(io.BytesIO(png)) as source:
        alpha = source.convert("RGBA").getchannel("A")
    # Render every source as the same neutral silhouette so this board compares
    # geometry, not the official source's palette or its Core accent.
    neutral = Image.new("RGBA", (size, size), (230, 237, 243, 0))
    neutral.putalpha(alpha)
    return neutral


def current_vector(path: Path, size: int) -> Image.Image:
    png = svg2png(url=path, output_width=size, output_height=size,
                  background_color=None)
    with Image.open(io.BytesIO(png)) as source:
        return fit_square(source, size)


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    glyph_rows: dict[str, list[dict]] = {}
    for icon in catalog["icons"]:
        glyph_rows.setdefault(icon["glyph"], []).append(icon)

    entries = []
    for key, (name, rating, note) in AUDIT.items():
        spec = catalog["artwork"].get(key)
        rows = glyph_rows.get(key, [])
        if not spec or not rows:
            raise SystemExit(f"Missing pinned reference or catalog glyph: {key}")
        icon = rows[0]
        source = ROOT / spec["file"]
        vector = ROOT / "assets/svg" / f"{icon['drawable']}.svg"
        if not vector.is_file():
            raise SystemExit(f"Missing current Core vector: {vector}")
        reference = reference_shape(source, THUMB, spec["sha256"])
        core = current_vector(vector, THUMB)
        entries.append((key, name, rating, note, icon, reference, core))

    if len(entries) != 17:
        raise SystemExit(f"Expected 17 pinned references, found {len(entries)}")

    canvas = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(canvas)
    label(draw, (50, 34), "CORE BUILDS  /  RECREATION ACCURACY", 14, CYAN, mono=True)
    label(draw, (50, 70), "Do the defining cues survive the redraw?", 38)
    label(draw, (50, 128),
          "17 source-hashed references vs current square glyphs · cue retention, not pixel matching",
          17, MUTED, regular=True)
    draw.rounded_rectangle((1170, 36, 1485, 78), radius=20,
                           fill="#101923", outline="#234455", width=1)
    label(draw, (1190, 51), "REFERENCE AUDIT  ·  01", 11, CYAN, mono=True)
    draw.line((50, 180, 1485, 180), fill=LINE, width=1)

    for index, item in enumerate(entries):
        key, name, rating, note, icon, reference, core = item
        col, row = index % COLS, index // COLS
        x, y = CARD_X[col], ROW_START + row * ROW_STEP
        accent = CYAN if rating == "STRONG" else "#F6C85F"
        draw.rounded_rectangle((x, y, x + CARD_W, y + CARD_H), radius=14,
                               fill=PANEL, outline=LINE, width=1)
        label(draw, (x + 18, y + 13), name, 17)
        label(draw, (x + CARD_W - 118, y + 17), rating, 10, accent, mono=True)
        label(draw, (x + 40, y + 46), "PINNED SOURCE", 9, MUTED, mono=True)
        label(draw, (x + 272, y + 46), "CORE GLYPH", 9, MUTED, mono=True)

        ref_x, core_x = x + 24, x + 262
        image_y = y + 65
        for ix in (ref_x, core_x):
            draw.rounded_rectangle((ix, image_y, ix + THUMB, image_y + THUMB),
                                   radius=10, fill=CARD, outline=LINE, width=1)
        canvas.paste(reference, (ref_x, image_y), reference)
        canvas.paste(core, (core_x, image_y), core)

        # Short notes are deliberately concise so the board remains legible.
        lines = [note[i:i + 60] for i in range(0, len(note), 60)]
        label(draw, (x + 18, y + 211), "\n".join(lines[:2]), 10, MUTED,
              regular=True, mono=False)

    footer_y = ROW_START + ((len(entries) + COLS - 1) // COLS) * ROW_STEP + 2
    draw.rounded_rectangle((50, footer_y, 1485, footer_y + 110), radius=14,
                           fill=PANEL, outline=LINE, width=1)
    label(draw, (72, footer_y + 18), "HOW TO READ THIS", 12, CYAN, mono=True)
    label(draw, (72, footer_y + 44),
          "Strong = distinctive shape cues survive; partial = one or more cues are simplified or missing.",
          14, INK, regular=True)
    label(draw, (72, footer_y + 72),
          "Mapping gap = source and Core cue are not like-for-like. No score implies approval or exact artwork.",
          12, MUTED, regular=True)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT, optimize=True)
    print(f"Wrote {OUT.relative_to(ROOT)} ({WIDTH}×{HEIGHT}); "
          f"{len(entries)} hash-verified source/current pairs")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
