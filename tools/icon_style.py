"""Classic pack colour policy shared by squares, banners and previews.

The catalog retains the source brand accent (also used by Pop and Pixel Neon).
On Classic's recommended dark cards, very dark accents use a consistent light
ink instead of vanishing or being randomly brightened to a different hue.
This is a legibility adaptation, not a claim that a brand changed its colour.
"""
from __future__ import annotations

CARD = "#0D1117"
LIGHT_INK = "#E6EDF3"
MIN_CONTRAST = 3.0


def luminance(colour: str) -> float:
    channels = [int(colour[pos:pos + 2], 16) / 255 for pos in (1, 3, 5)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
              for v in channels]
    return sum(v * weight for v, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(a: str, b: str = CARD) -> float:
    high, low = sorted((luminance(a), luminance(b)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def display_accent(accent: str) -> str:
    colour = accent.upper()
    return colour if contrast(colour) >= MIN_CONTRAST else LIGHT_INK
