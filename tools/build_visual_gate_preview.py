#!/usr/bin/env python3
"""Build a research-only 10-foot stress sheet for wordmark-gated icons.

This does not touch catalog.json, glyphs.py, Android resources, or release output.
The marks are deliberately set in the pack's bundled Outfit face and reduced to
observed geometry; they are not copies of vendor artwork or vendor typography.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/research/icon-visual-gate-preview-2026-09-16.png"
FONTS = ROOT / "tools/fonts"
CARD = "#0A0E14"
PANEL = "#111822"
LINE = "#293545"
INK = "#EEF5FF"
MUTED = "#91A1B5"
ACCENT = "#56C8F0"


def font(size: int, *, regular: bool = False) -> ImageFont.FreeTypeFont:
    # The repository intentionally bundles only its production Bold weights.
    name = "Outfit-Bold.ttf" if regular else "Outfit-ExtraBold.ttf"
    return ImageFont.truetype(str(FONTS / name), size)


def fit_text(draw: ImageDraw.ImageDraw, text: str, max_width: int, start: int,
             *, spacing: int = 0, regular: bool = False) -> ImageFont.FreeTypeFont:
    for size in range(start, 7, -1):
        f = font(size, regular=regular)
        if spacing:
            width = sum(draw.textlength(ch, font=f) for ch in text) + spacing * (len(text) - 1)
        else:
            width = draw.textlength(text, font=f)
        if width <= max_width:
            return f
    return font(7, regular=regular)


def centred(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], text: str,
            start: int, *, spacing: int = 0, regular: bool = False, fill: str = INK) -> None:
    x0, y0, x1, y1 = box
    f = fit_text(draw, text, x1 - x0, start, spacing=spacing, regular=regular)
    if spacing:
        widths = [draw.textlength(ch, font=f) for ch in text]
        width = sum(widths) + spacing * (len(text) - 1)
        x = x0 + (x1 - x0 - width) / 2
        for ch, width in zip(text, widths):
            draw.text((x, y0 + (y1 - y0 - f.size) / 2 - 3), ch, font=f, fill=fill)
            x += width + spacing
    else:
        bounds = draw.textbbox((0, 0), text, font=f)
        width, height = bounds[2] - bounds[0], bounds[3] - bounds[1]
        draw.text((x0 + (x1 - x0 - width) / 2,
                   y0 + (y1 - y0 - height) / 2 - bounds[1]), text, font=f, fill=fill)


def mark(name: str, size: int) -> Image.Image:
    scale = size / 160
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = round(12 * scale)
    box = (pad, pad, size - pad, size - pad)

    if name == "France.tv":
        centred(d, box, "france.tv", round(35 * scale), regular=True)
    elif name == "Angel Studios":
        centred(d, box, "ANGEL", round(35 * scale), spacing=max(1, round(4 * scale)))
    elif name == "CANAL+":
        centred(d, box, "CANAL+", round(42 * scale))
    elif name == "Euronews":
        upper = (pad, round(35 * scale), size - pad, round(84 * scale))
        lower = (pad, round(73 * scale), size - pad, round(127 * scale))
        centred(d, upper, "euro", round(39 * scale), regular=True)
        centred(d, lower, "news.", round(42 * scale))
    elif name == "HGTV":
        stroke = max(2, round(7 * scale))
        d.line((round(34 * scale), round(69 * scale), round(80 * scale), round(31 * scale),
                round(126 * scale), round(69 * scale)), fill=INK, width=stroke, joint="curve")
        centred(d, (pad, round(65 * scale), size - pad, round(132 * scale)), "HGTV", round(43 * scale))
    else:
        raise KeyError(name)
    return img


def main() -> int:
    names = ["France.tv", "Angel Studios", "CANAL+", "Euronews", "HGTV"]
    canvas = Image.new("RGB", (1440, 1080), CARD)
    d = ImageDraw.Draw(canvas)
    d.text((54, 35), "RESEARCH ONLY / WORDMARK GATE", font=font(16), fill=ACCENT)
    d.text((54, 69), "10-foot stress test", font=font(48), fill=INK)
    d.text((54, 130), "Core-authored Outfit constructions at 160, 80 and 48 px — no catalog or production assets changed.",
           font=font(20, regular=True), fill=MUTED)

    x_positions = (500, 760, 970)
    headings = (("160 px", "review"), ("80 px", "launcher"), ("48 px", "stress"))
    for x, (a, b) in zip(x_positions, headings):
        d.text((x, 184), a, font=font(18), fill=INK)
        d.text((x, 208), b, font=font(14, regular=True), fill=MUTED)

    for row, name in enumerate(names):
        y = 248 + row * 154
        d.rounded_rectangle((42, y - 12, 1398, y + 128), radius=18, fill=PANEL, outline=LINE, width=2)
        d.text((68, y + 22), name, font=font(27), fill=INK)
        state = "wordmark" if name != "HGTV" else "roof + wordmark"
        d.text((68, y + 61), state, font=font(15, regular=True), fill=MUTED)
        for x, size in zip(x_positions, (160, 80, 48)):
            tile = Image.new("RGBA", (size, size), "#0B1018")
            td = ImageDraw.Draw(tile)
            td.rounded_rectangle((1, 1, size - 2, size - 2), radius=max(7, size // 10), outline=LINE, width=max(1, size // 80))
            tile.alpha_composite(mark(name, size))
            canvas.paste(tile, (x, y + (112 - size) // 2), tile)
        # Simulated launcher card keeps the standard name outside the mark.
        cx = 1100
        d.rounded_rectangle((cx, y + 2, cx + 270, y + 114), radius=14, fill="#0B1018", outline=LINE, width=2)
        icon = mark(name, 72)
        canvas.paste(icon, (cx + 10, y + 22), icon)
        d.text((cx + 92, y + 34), name, font=fit_text(d, name, 158, 22), fill=INK)
        d.text((cx + 92, y + 68), "APP", font=font(13), fill=ACCENT)

    fy = 1012
    d.line((54, fy - 20, 1386, fy - 20), fill=LINE, width=2)
    d.text((54, fy), "Decision rule: reject a compact mark if its defining punctuation, spacing, counter or roof cue disappears at 48 px.",
           font=font(17, regular=True), fill=MUTED)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT, optimize=True)
    print(f"Wrote {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
