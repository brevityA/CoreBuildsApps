import json
import os
import math
import re

from typeface import adaptive_lockup, lockup_cap, monogram_body, monogram_text, monogram_scaled
from icon_style import OFFWHITE_INK, display_accent
"""
Core Builds Icon Pack — glyph library.

Core Builds comes first: original geometric constructions, rounded ends,
one accent and a canonical 32px primary line. Brand references inform the
recognisable cue; they never replace our linework with a vendor silhouette
or custom wordmark. Reference hashes/provenance live in catalog.json artwork.

Grid:   512 x 512
Safe:   432 (40px margin all sides)
Stroke: 32 primary after normalisation; 26.2 / 21.8 subordinate detail
"""

GRID = 512
C = GRID / 2          # 256 centre
SAFE = 432
STROKE = 34


# --------------------------------------------------------------------------
# The Core Builds flare.
#
# Brand Guide §02 construction constants: a blurred halo at opacity .30 sits
# BEHIND the stroke, and "the halo is part of the mark" — the lit look is the
# brand's "it runs" tell (§06: cyan-first lighting, violet ambient).
#
# Applied here per-icon in that app's accent colour rather than always cyan,
# because §03 reserves the cyan gradient for action + truth surfaces (install
# buttons, verified stamps) — not for third-party app art. The treatment is
# the brand signature; the hue stays the app's.
#
# Blur is expressed as a fraction of the 512 grid so it survives the downscale
# to 320x180 and to small square icons without smearing.
# --------------------------------------------------------------------------
GLOW_ID = 0


def lit(body, color, spread=13, opacity=0.34):
    """Wrap glyph geometry in its own halo. Returns defs + layered output."""
    global GLOW_ID
    GLOW_ID += 1
    fid = f"cbGlow{GLOW_ID}"
    return (
        f'<defs><filter id="{fid}" x="-45%" y="-45%" width="190%" height="190%">'
        f'<feGaussianBlur stdDeviation="{spread}"/></filter></defs>'
        f'<g filter="url(#{fid})" opacity="{opacity}">{body}</g>'
        f'{body}'
    )


def _s(color, w=STROKE):
    return (f'fill="none" stroke="{color}" stroke-width="{w}" '
            f'stroke-linecap="round" stroke-linejoin="round"')


def _f(color):
    return f'fill="{color}" stroke="none"'


# --------------------------------------------------------------------------
# monogram strokes — drawn as geometry, never as <text>, so rendering is
# font-independent and identical on every machine and in CI.
# --------------------------------------------------------------------------
def _mono(color, paths, w=40):
    return "".join(f'<path d="{d}" {_s(color, w)}/>' for d in paths)








def monogram_9(c):
    return _mono(c, [
        "M 316 236 C 316 288 274 316 234 316 C 194 316 164 286 164 244 "
        "C 164 200 196 170 240 170 C 292 170 320 206 320 262 "
        "C 320 320 296 358 236 366"])


def monogram_7(c):
    return _mono(c, ["M 176 168 L 344 168 L 244 356"])


def monogram_10(c):
    return _mono(c, [
        "M 150 200 L 186 176 L 186 356",
        "M 300 176 C 348 176 366 214 366 266 C 366 318 348 356 300 356 "
        "C 252 356 234 318 234 266 C 234 214 252 176 300 176 Z"], w=36)


# --------------------------------------------------------------------------
# shape glyphs
# --------------------------------------------------------------------------
def _hexpts(cx, cy, r):
    # point-up hexagon, matching the brand mark stance (§02: never rotate)
    import math
    pts = []
    for i in range(6):
        a = math.radians(60 * i - 90)
        pts.append(f"{cx + r * math.cos(a):.1f},{cy + r * math.sin(a):.1f}")
    return " ".join(pts)


def _gearpts(cx, cy, outer, inner, teeth=8):
    """Return a restrained face-on gear keyline for utility shells.

    The alternating radii make the tool cue survive at 48px without turning
    the family shell into a busy illustration. A tooth is centred on each
    cardinal/diagonal axis, so the adaptive mark remains optically centred.
    """
    import math
    pts = []
    step = math.pi / teeth
    for i in range(teeth * 2):
        radius = outer if i % 2 == 0 else inner
        angle = i * step - math.pi / 2
        pts.append(f"{cx + radius * math.cos(angle):.1f},{cy + radius * math.sin(angle):.1f}")
    return " ".join(pts)


def play_hex(c):
    """Stremio: rounded square with the play as an outline (monoline)."""
    return (f'<rect x="70" y="70" width="372" height="372" rx="96" {_s(c, 34)}/>'
            f'<path d="M 212 176 L 340 256 L 212 336 Z" {_s(c, 32)}/>')


def play_round(c):
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 34)}/>'
            f'<path d="M 220 182 L 336 256 L 220 330 Z" {_s(c, 32)}/>')


def play_rect(c):
    return (f'<rect x="72" y="118" width="368" height="276" rx="66" {_s(c, 36)}/>'
            f'<path d="M 220 190 L 336 256 L 220 322 Z" {_f(c)}/>')


def kodi_box(c):
    """Kodi's split diamond/K, reconstructed in the pack's rounded line weight.

    The small left diamond was 42px across a 32 stroke and closed at tile
    size. Enlarging it keeps the four-part split intact.
    """
    return (f'<path d="M 238 72 L 322 156 L 178 300 V 132 Z" {_s(c, 32)}/>'
            f'<path d="M 362 184 L 434 256 L 362 328 L 290 256 Z" {_s(c, 32)}/>'
            f'<path d="M 256 310 L 328 382 L 256 454 L 184 382 Z" {_s(c, 32)}/>'
            f'<path d="M 134 190 L 68 256 L 134 322 Z" {_s(c, 32)}/>')


def jellyfin_chevrons(c):
    """Rounded nested triangles: Jellyfin's cue, not its filled vendor artwork."""
    return (f'<path d="M 256 76 C 222 76 82 406 103 426 '
            f'C 142 450 370 450 409 426 C 430 406 290 76 256 76 Z" {_s(c, 32)}/>'
            f'<path d="M 256 214 C 240 214 180 325 190 340 '
            f'C 208 350 304 350 322 340 C 332 325 272 214 256 214 Z" {_s(c, 26)}/>')


def emby_shield(c):
    """Emby: the shield with its play.

    The play wedge sat close enough to the shield wall to close the gap on one
    side. Shrinking it and centring it keeps a clear margin all round.
    """
    return (f'<path d="M 256 88 L 408 150 L 408 268 C 408 352 338 404 256 428 '
            f'C 174 404 104 352 104 268 L 104 150 Z" {_s(c, 34)}/>'
            f'<path d="M 224 208 L 306 258 L 224 308 Z" {_s(c, 30)}/>')


def plex_chevron(c):
    """Plex's chevron as an open-ink ribbon, not a vendor-font wordmark."""
    return (f'<path d="M 166 100 H 262 L 362 256 L 262 412 H 166 '
            f'L 266 256 Z" {_s(c, 32)}/>')


def nuvio_plays(c):
    """Nuvio: the gradient wedge and its inner play.

    The official mark is a rounded play triangle lit cyan at the top and
    violet at the bottom, holding a dark knock-out triangle with a light
    play inside. The silhouette and the play are drawn flat here; the
    Classic render runs them through the catalog's cyan→violet gradient
    (see render_svg `gradient`), and on a dark card the knock-out centre
    reads as the card itself, exactly as the logo does.
    """
    return (f'<path d="M 148 96 L 428 256 L 148 416 Z" {_s(c, 34)}/>'
            f'<path d="M 212 208 L 304 256 L 212 304 Z" {_s(c, 26)}/>')


def tubi_mono(c):
    """Tubi: the brand's lowercase t as an Outfit ExtraBold monogram.

    Wordmarks are banner business; the icon carries a single glyph set in
    the pack's own typeface, in the wordmark yellow. A lone t reads as a
    cross, so the wordmark's detached shoulder dot sits at the crossbar —
    the brand quirk, kept as a glyph cue.
    """
    return (monogram_body("t", c) +
            f'<circle cx="396" cy="204" r="34" fill="{c}" stroke="none"/>')


def vidio_mono(c):
    """Vidio: the lowercase v as an Outfit ExtraBold monogram."""
    return monogram_body("v", c)


def bit_tv_mono(c):
    """BitTV: the lowercase b as an Outfit ExtraBold monogram."""
    return monogram_body("b", c)


def projector_beam(c):
    return (f'<rect x="84" y="188" width="212" height="150" rx="40" {_s(c, 34)}/>'
            f'<circle cx="190" cy="263" r="42" {_s(c, 28)}/>'
            f'<path d="M 336 200 L 420 156" {_s(c, 30)}/>'
            f'<path d="M 344 263 L 436 263" {_s(c, 30)}/>'
            f'<path d="M 336 326 L 420 370" {_s(c, 30)}/>')


def download_arrow(c):
    return (f'<path d="M 256 108 L 256 306" {_s(c, 40)}/>'
            f'<path d="M 166 226 L 256 316 L 346 226" {_s(c, 40)}/>'
            f'<path d="M 122 384 L 390 384" {_s(c, 40)}/>')


def cloud_box(c):
    return (f'<path d="M 168 350 C 112 350 84 310 96 268 C 106 232 142 216 '
            f'170 220 C 182 160 240 130 292 148 C 336 162 356 202 352 236 '
            f'C 400 240 424 274 418 312 C 412 342 386 350 352 350 Z" {_s(c, 34)}/>'
            f'<path d="M 256 262 L 256 380" {_s(c, 32)}/>'
            f'<path d="M 208 336 L 256 384 L 304 336" {_s(c, 32)}/>')


def link_chain(c):
    return (f'<path d="M 214 298 L 298 214" {_s(c, 36)}/>'
            f'<path d="M 190 214 L 150 254 C 106 298 172 364 216 320 L 256 280" '
            f'{_s(c, 36)}/>'
            f'<path d="M 322 298 L 362 258 C 406 214 340 148 296 192 L 256 232" '
            f'{_s(c, 36)}/>')


def sync_ring(c):
    # concentric tracking ring, open at the top for the arrow head
    return (f'<path d="M 256 92 A 164 164 0 1 1 140 140" {_s(c, 36)}/>'
            f'<path d="M 188 78 L 136 138 L 198 184" {_s(c, 32)}/>'
            f'<circle cx="256" cy="256" r="86" {_s(c, 32)}/>'
            f'<circle cx="256" cy="256" r="26" {_f(c)}/>')


def cone(c):
    # traffic cone: tapered body with base plate and reflective bands
    return (f'<path d="M 238 94 L 274 94 L 348 356 L 164 356 Z" {_s(c, 32)}/>'
            f'<path d="M 196 232 L 316 232" {_s(c, 26)}/>'
            f'<path d="M 180 292 L 332 292" {_s(c, 26)}/>'
            f'<path d="M 116 390 L 396 390" {_s(c, 34)}/>')


def remote(c):
    return (f'<rect x="176" y="72" width="160" height="368" rx="72" {_s(c, 34)}/>'
            f'<circle cx="256" cy="160" r="30" {_f(c)}/>'
            f'<path d="M 216 262 L 296 262" {_s(c, 26)}/>'
            f'<path d="M 216 336 L 296 336" {_s(c, 26)}/>')


def smile_arrow(c):
    # wide screen + the upward swoosh underneath
    return (f'<rect x="78" y="112" width="356" height="212" rx="44" {_s(c, 34)}/>'
            f'<path d="M 222 176 L 322 218 L 222 260 Z" {_f(c)}/>'
            f'<path d="M 116 372 C 216 424 316 424 404 366" {_s(c, 32)}/>'
            f'<path d="M 356 372 L 404 364 L 396 412" {_s(c, 30)}/>')


def plus_star(c):
    """Castle silhouette with the trailing plus, as in the Disney+ lockup."""
    return (f'<path d="M 116 392 L 116 258 L 166 206 L 216 258 L 216 392" '
            f'{_s(c, 28)}/>'
            f'<path d="M 216 392 L 216 186 L 268 124 L 320 186 L 320 392" '
            f'{_s(c, 28)}/>'
            f'<path d="M 96 392 L 340 392" {_s(c, 30)}/>'
            f'<path d="M 268 108 L 268 82" {_s(c, 20)}/>'
            f'<path d="M 404 214 L 404 306" {_s(c, 30)}/>'
            f'<path d="M 358 260 L 450 260" {_s(c, 30)}/>')


def apple_tv(c):
    return (f'<rect x="76" y="126" width="360" height="228" rx="44" {_s(c, 34)}/>'
            f'<path d="M 176 412 L 336 412" {_s(c, 34)}/>'
            f'<path d="M 256 354 L 256 412" {_s(c, 34)}/>'
            f'<circle cx="256" cy="240" r="46" {_s(c, 30)}/>')


def eye(c):
    return (f'<path d="M 76 256 C 150 156 362 156 436 256 '
            f'C 362 356 150 356 76 256 Z" {_s(c, 34)}/>'
            f'<circle cx="256" cy="256" r="58" {_s(c, 30)}/>')


def waves_circle(c):
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 34)}/>'
            f'<path d="M 158 200 C 220 178 300 182 356 208" {_s(c, 32)}/>'
            f'<path d="M 168 268 C 224 248 292 252 340 274" {_s(c, 30)}/>'
            f'<path d="M 182 330 C 228 314 282 318 322 334" {_s(c, 26)}/>')


def chat_screen(c):
    """Twitch's stepped chat/twin-bar cue with Core Builds' rounded joins."""
    return (f'<path d="M 124 84 H 428 V 294 L 324 398 H 228 '
            f'L 156 454 V 398 H 84 V 136 Z" {_s(c, 32)}/>'
            f'<path d="M 230 168 V 266 M 324 168 V 266" {_s(c, 32)}/>')


def gear(c):
    import math
    teeth = []
    for i in range(8):
        a = math.radians(45 * i)
        x1, y1 = 256 + 150 * math.cos(a), 256 + 150 * math.sin(a)
        x2, y2 = 256 + 196 * math.cos(a), 256 + 196 * math.sin(a)
        teeth.append(f'<path d="M {x1:.1f} {y1:.1f} L {x2:.1f} {y2:.1f}" {_s(c, 34)}/>')
    return (f'<circle cx="256" cy="256" r="128" {_s(c, 36)}/>'
            f'<circle cx="256" cy="256" r="46" {_s(c, 30)}/>' + "".join(teeth))


def folder(c):
    return (f'<path d="M 80 152 L 216 152 L 258 206 L 432 206 L 432 372 '
            f'C 432 384 422 394 410 394 L 102 394 C 90 394 80 384 80 372 Z" '
            f'{_s(c, 34)}/>')


def core_mark(c):
    """Parent brand mark, monoline. Hex stance is load-bearing (§02)."""
    return (f'<polygon points="{_hexpts(256, 256, 196)}" {_s(c, 34)}/>'
            f'<polygon points="256,166 346,256 256,346 166,256" {_s(c, 32)}/>')


def store_bag(c):
    """App stores — shopping bag with a download arc."""
    return (f'<path d="M 118 172 L 394 172 L 372 404 L 140 404 Z" {_s(c, 32)}/>'
            f'<path d="M 196 224 L 196 148 C 196 108 226 84 256 84 '
            f'C 286 84 316 108 316 148 L 316 224" {_s(c, 30)}/>')


def install_box(c):
    """Sideload / installer — package with an inbound arrow."""
    return (f'<path d="M 96 186 L 256 108 L 416 186 L 416 350 L 256 428 '
            f'L 96 350 Z" {_s(c, 32)}/>'
            f'<path d="M 96 186 L 256 264 L 416 186" {_s(c, 28)}/>'
            f'<path d="M 256 264 L 256 428" {_s(c, 28)}/>')


def stream_tower(c):
    """IPTV / live TV — broadcast tower radiating."""
    return (f'<path d="M 200 424 L 256 208 L 312 424" {_s(c, 32)}/>'
            f'<path d="M 218 340 L 294 340" {_s(c, 26)}/>'
            f'<circle cx="256" cy="152" r="34" {_s(c, 28)}/>'
            f'<path d="M 150 96 C 116 130 116 174 150 208" {_s(c, 26)}/>'
            f'<path d="M 362 96 C 396 130 396 174 362 208" {_s(c, 26)}/>')


def gamepad(c):
    """Game streaming — controller silhouette."""
    return (f'<path d="M 168 176 L 344 176 C 400 176 428 232 436 296 '
            f'C 444 352 412 380 380 380 C 348 380 330 336 300 336 '
            f'L 212 336 C 182 336 164 380 132 380 C 100 380 68 352 76 296 '
            f'C 84 232 112 176 168 176 Z" {_s(c, 30)}/>'
            f'<path d="M 148 236 L 148 292" {_s(c, 24)}/>'
            f'<path d="M 120 264 L 176 264" {_s(c, 24)}/>'
            f'<circle cx="348" cy="248" r="18" {_f(c)}/>'
            f'<circle cx="384" cy="290" r="18" {_f(c)}/>')


def retro_pad(c):
    """Straight-sided home-console pad, retired as Artemis's mark.

    Drawn for Artemis in 1.8.20 as "a controller, but not Moonlight's".
    The tester on the user's Discord (BinoSchmino, 2026-09-18) then chose
    between the rendered candidates twice - "I like the one on the right
    a bit better", then "Let's go with the controller" - and both times
    the right-hand candidate was the organic winged pad, so Artemis moved
    to artemis_pad and this shell retired. Two design notes survive here
    because artemis_pad inherits them: a d-pad drawn as a cross of two
    strokes, never an outlined plus (at 48px an outlined cross closes its
    four counters and reads as a blob), and button centres kept further
    apart than their combined ink radius or the pair fuses into a figure
    of eight on the downscale.
    """
    return (
        f'<rect x="88" y="158" width="336" height="196" rx="92" {_s(c, 32)}/>'
        f'<path d="M 128 256 L 208 256" {_s(c, 26)}/>'
        f'<path d="M 168 216 L 168 296" {_s(c, 26)}/>'
        f'<circle cx="302" cy="224" r="26" {_s(c, 26)}/>'
        f'<circle cx="360" cy="290" r="26" {_s(c, 26)}/>'
    )


def artemis_pad(c):
    """Artemis - the winged controller the tester actually picked.

    BinoSchmino's verdict on the 1.8.20 retro shell, over two rounds of
    rendered candidates: the controller on the right, both times, which
    was the organic winged pad of the gamepad family - "I think the
    controller of Moonlight looks better than this, so maybe you could
    do something similar. The original looks a bit generic." Similar, not
    identical: Moonlight and Daijishou already carry `gamepad` in this
    pack, two apps that sit in the same launcher row, and the twin check
    rejects a near-copy anyway. So artemis_pad is the same family with
    its own grammar - a compact winged body (about 6 percent narrower
    than gamepad's, deeper waist notch) and a start/select dash across
    the middle that gamepad does not carry. Buttons stay solid dots, the
    house button grammar: a ring button's counter closes at 96px (measured
    4 -> 1 -> 1 on the first pass), and a closed counter is a blob, not a
    button. The second pass still read 2 -> 1 -> 1, but that closing
    counter was not geometry at all - a 3px LANCZOS ringing sliver inside
    the left wall's inner edge at the 256px measure, one resampling phase
    artifact; widening the body two units per side moved the phase off it
    and the counters settled at 1 -> 1 -> 1. Cross d-pad and the
    button-spacing rule inherited from retro_pad's notes above. Paints:
    the catalog's declared violet, flat, like every GAMING mark.
    """
    return (
        f'<path d="M 176 164 L 336 164 C 388 164 416 214 424 276 '
        f'C 432 332 404 362 374 362 C 344 362 330 322 300 322 '
        f'L 212 322 C 182 322 168 362 138 362 C 108 362 80 332 88 276 '
        f'C 96 214 124 164 176 164 Z" {_s(c, 30)}/>'
        f'<path d="M 152 232 L 152 288" {_s(c, 24)}/>'
        f'<path d="M 124 260 L 180 260" {_s(c, 24)}/>'
        f'<path d="M 240 246 L 264 246" {_s(c, 20)}/>'
        f'<circle cx="336" cy="240" r="18" {_f(c)}/>'
        f'<circle cx="372" cy="284" r="18" {_f(c)}/>'
    )


def tools_wrench(c):
    """Utilities / tweaks - the ring spanner.

    The open-jaw wrench had one counter, the jaw notch, and it pinched shut at
    tile size; widening the jaw pushed ink 22px outside SAFE. A ring spanner
    carries the same idea with a socket that cannot close, and it sits inside
    the margin.
    """
    return (f'<circle cx="332" cy="180" r="88" {_s(c, 30)}/>'
            f'<circle cx="332" cy="180" r="46" {_s(c, 22)}/>'
            f'<path d="M 272 242 L 140 374 C 122 392 122 420 140 438 '
            f'C 158 456 186 456 204 438 L 336 306" {_s(c, 30)}/>')


def send_arrow(c):
    """File transfer — paper-plane."""
    return (f'<path d="M 428 96 L 84 246 L 214 292 L 260 422 Z" {_s(c, 32)}/>'
            f'<path d="M 428 96 L 214 292" {_s(c, 28)}/>')


def broom(c):
    """Cleaner / maintenance.

    The head and the fan shared an edge, and the wedge between them closed.
    Separating the fan from the head keeps both shapes readable.
    """
    return (f'<path d="M 396 88 L 258 226" {_s(c, 34)}/>'
            f'<path d="M 286 194 L 180 300 L 254 374 L 360 268 Z" {_s(c, 30)}/>'
            f'<path d="M 166 314 L 96 424 L 208 388" {_s(c, 30)}/>')


def shield_key(c):
    """Permissions / privileged access."""
    return (f'<path d="M 256 84 L 404 144 L 404 262 C 404 344 336 396 256 420 '
            f'C 176 396 108 344 108 262 L 108 144 Z" {_s(c, 32)}/>'
            f'<circle cx="256" cy="228" r="42" {_s(c, 26)}/>'
            f'<path d="M 256 270 L 256 336" {_s(c, 26)}/>'
            f'<path d="M 256 306 L 296 306" {_s(c, 22)}/>')


def automation(c):
    """Automation / scripting — node graph."""
    return (f'<circle cx="140" cy="150" r="46" {_s(c, 28)}/>'
            f'<circle cx="372" cy="150" r="46" {_s(c, 28)}/>'
            f'<circle cx="256" cy="372" r="46" {_s(c, 28)}/>'
            f'<path d="M 186 150 L 326 150" {_s(c, 24)}/>'
            f'<path d="M 158 192 L 232 332" {_s(c, 24)}/>'
            f'<path d="M 354 192 L 280 332" {_s(c, 24)}/>')


def home_button(c):
    """Remote / button remapper."""
    return (f'<path d="M 96 250 L 256 108 L 416 250" {_s(c, 32)}/>'
            f'<path d="M 148 236 L 148 404 L 364 404 L 364 236" {_s(c, 30)}/>'
            f'<circle cx="256" cy="318" r="40" {_s(c, 26)}/>')


def tv_stack(c):
    """Generic media/IPTV client — screen with stacked layers."""
    return (f'<rect x="72" y="112" width="368" height="240" rx="40" {_s(c, 32)}/>'
            f'<path d="M 176 412 L 336 412" {_s(c, 28)}/>'
            f'<path d="M 256 352 L 256 412" {_s(c, 28)}/>'
            f'<path d="M 148 190 L 300 190" {_s(c, 24)}/>'
            f'<path d="M 148 250 L 364 250" {_s(c, 24)}/>')


GLYPHS = {
    "store_bag": store_bag, "install_box": install_box,
    "stream_tower": stream_tower, "gamepad": gamepad,
    "retro_pad": retro_pad,
    "tools_wrench": tools_wrench, "send_arrow": send_arrow,
    "broom": broom, "shield_key": shield_key, "automation": automation,
    "home_button": home_button, "tv_stack": tv_stack,
    "play_hex": play_hex, "play_round": play_round, "play_rect": play_rect,
    "kodi_box": kodi_box, "jellyfin_chevrons": jellyfin_chevrons,
    "emby_shield": emby_shield, "plex_chevron": plex_chevron,
    "nuvio_plays": nuvio_plays, "projector_beam": projector_beam,
    "download_arrow": download_arrow, "cloud_box": cloud_box,
    "link_chain": link_chain, "sync_ring": sync_ring, "cone": cone,
    "remote": remote, "smile_arrow": smile_arrow, "plus_star": plus_star,
    "apple_tv": apple_tv, "eye": eye, "waves_circle": waves_circle,
    "chat_screen": chat_screen, "gear": gear, "folder": folder,
    "core_mark": core_mark,
    "monogram_9": monogram_9, "monogram_7": monogram_7,
    "monogram_10": monogram_10,
}



# --------------------------------------------------------------------------
# Monoline normalisation (style AA).
#
# The chosen direction is monoline: one uniform stroke weight, no fill, no
# glow, no container. Google's TV icon guidance is explicit that borders
# around a logo "get cropped and create unpolished visuals", so AA drops the
# hex host entirely.
#
# The glyphs were authored with weights from 20 to 46px. Rather than rewrite
# 71 functions by hand, normalise at render time: snap every stroke to the
# monoline weight, scaled by how heavy the original was so genuinely fine
# detail (film-reel perforations, equaliser knobs) stays subordinate.
# --------------------------------------------------------------------------
MONOLINE = 32          # the single canonical weight on the 512 grid
_SW_RE = re.compile(r'stroke-width="(\d+(?:\.\d+)?)"')


def monoline(body, weight=MONOLINE):
    """Snap all stroke widths in `body` to one monoline weight."""
    def repl(m):
        w = float(m.group(1))
        # Original weights clustered 20-46. Anything at or above the old
        # default reads as "primary" and takes the full weight; lighter
        # strokes keep their relative subordination, floored so they survive
        # the downscale to 320x180.
        if w >= 30:
            out = weight
        elif w >= 24:
            out = weight * 0.82
        else:
            out = weight * 0.68
        return f'stroke-width="{out:.1f}"'
    return _SW_RE.sub(repl, body)


def render_svg(glyph_name, color, glow=False, *, monochrome=False,
               gradient=None, mark=None, style=None, secondary=None):
    """
    Render the transparent Classic glyph in the common monoline treatment.

    Glow is opt-in for legacy experiments, never used by the pack generators.
    `gradient` is a pair of hexes: the rendered stroke then runs through a
    vertical userSpace linear gradient instead of the flat accent. It is a
    Classic-render treatment (catalog `gradient` field).

    `mark` is the catalog's adaptive wordmark token: on a category monogram
    it replaces the lone letter (family_body adapts the type to the shell);
    `style` is its brand-informed treatment. On any other glyph both are
    ignored.

    `secondary` is the catalog's duotone declaration: the named parts take
    the brand's second colour (see apply_secondary). Monochrome icons stay
    one paint.
    """
    color = display_accent(color, monochrome=monochrome)
    body = monoline(family_body(glyph_name, color, mark, style))
    if secondary and not monochrome:
        body = apply_secondary(body, color, secondary)
    if glow:
        body = lit(body, color)
    if gradient and not monochrome:
        body = apply_gradient(body, color, gradient)
    body = classic_fit(glyph_name, body)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {GRID} {GRID}" '
            f'width="{GRID}" height="{GRID}">\n  {body}\n</svg>\n')


_CLASSIC_FIT = None


def classic_fit(glyph_name, body):
    """Centre and, if undersized, scale a square glyph by its committed fit.

    tools/classic_glyph_fit.json (written by tools/fit_classic_glyphs.py from
    committed metrics) holds [scale, ink cx, ink cy] for the glyphs that need
    it; every other glyph is returned untouched. Stroke widths are divided by
    the scale first, so the monoline weight is the same after the transform.
    """
    global _CLASSIC_FIT
    if _CLASSIC_FIT is None:
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            "classic_glyph_fit.json")
        try:
            with open(path, encoding="utf-8") as fh:
                _CLASSIC_FIT = json.load(fh)["fits"]
        except FileNotFoundError:
            _CLASSIC_FIT = {}
    fit = _CLASSIC_FIT.get(glyph_name)
    if not fit:
        return body
    scale, cx, cy = fit
    if scale != 1:
        body = _SW_RE.sub(
            lambda m: f'stroke-width="{float(m.group(1)) / scale:.2f}"', body)
    half = GRID / 2
    return (f'<g transform="translate({half:g} {half:g}) scale({scale:g}) '
            f'translate({-cx:g} {-cy:g})">{body}</g>')


_PRIMITIVE_RE = re.compile(
    r"<(?:path|circle|rect|line|polyline|polygon|ellipse)\b[^>]*>")


def secondary_paint(secondary):
    """The drawn colour of a duotone declaration, through the same dark-card
    ramp as every accent so a dark second colour cannot vanish."""
    return display_accent(secondary["color"])


def apply_secondary(body, color, secondary):
    """Duotone: repaint the declared parts with the brand's second colour.

    `secondary` is the catalog entry {"color", "parts", "source"}; `parts`
    are 0-based indexes into the glyph's drawn primitives, in drawing order
    (YouTube's play is part 1 of rect + play). Parts are named, not inferred
    from stroke weight, because weight does not separate them: YouTube's
    frame and play are both 32. Only strokes and fills of the accent are
    repainted, so a part's `fill="none"` survives.
    """
    parts = set(secondary["parts"])
    paint = secondary_paint(secondary)
    index = iter(range(10_000))

    def repaint(match):
        tag = match.group(0)
        if next(index) not in parts:
            return tag
        return (tag.replace(f'stroke="{color}"', f'stroke="{paint}"')
                .replace(f'fill="{color}"', f'fill="{paint}"'))
    return _PRIMITIVE_RE.sub(repaint, body)


def primitive_count(body):
    """How many drawn primitives a glyph body has (bounds `parts`)."""
    return len(_PRIMITIVE_RE.findall(body))


def secondary_errors(icon):
    """Catalog contract for a duotone declaration; [] when absent or sound.

    A second colour is brand identity, so it carries its provenance like the
    primary does: no `source`, no duotone. It never combines with a gradient
    (one extra paint story per mark) or a monochrome entry, and its parts
    must name real primitives of every glyph the icon renders.
    """
    sec = icon.get("secondary")
    if sec is None:
        return []
    if not isinstance(sec, dict):
        return ["secondary must be {color, parts, source}"]
    errors = []
    if set(sec) - {"color", "parts", "source"}:
        errors.append(f"secondary has unknown keys {sorted(set(sec) - {'color', 'parts', 'source'})}")
    if not re.fullmatch(r"#[0-9A-Fa-f]{6}", str(sec.get("color", ""))):
        return errors + ["secondary color must be #RRGGBB"]
    if not str(sec.get("source", "")).strip():
        errors.append("secondary carries no source - where was the second brand colour seen?")
    if icon.get("gradient"):
        errors.append("secondary and gradient cannot both be declared")
    if icon.get("color_note") == "monochrome":
        errors.append("a monochrome icon cannot declare a secondary colour")
    parts = sec.get("parts")
    if (not isinstance(parts, list) or not parts
            or not all(isinstance(k, int) and not isinstance(k, bool) for k in parts)
            or len(set(parts)) != len(parts)):
        return errors + ["secondary parts must be a non-empty list of distinct part indexes"]
    accent = display_accent(icon.get("color", "#000000"))
    if secondary_paint(sec).upper() == accent.upper():
        errors.append("secondary renders the same as the accent - nothing is two-tone")
    for glyph in {icon.get("glyph"), icon.get("banner_glyph") or icon.get("glyph")}:
        if glyph not in GLYPHS:
            continue
        count = primitive_count(monoline(family_body(glyph, accent, icon.get("mark"),
                                                     icon.get("mark_style"))))
        if min(parts) < 0 or max(parts) >= count:
            errors.append(f"secondary parts {parts} out of range for '{glyph}' "
                          f"({count} parts)")
        elif len(parts) >= count:
            errors.append(f"secondary repaints every part of '{glyph}' - "
                          "that is a recolour, not a duotone")
    return errors


def secondary_color(icon):
    """The drawn second paint an icon declares, or None."""
    sec = icon.get("secondary")
    if not sec or secondary_errors(icon) or icon.get("color_note") == "monochrome":
        return None
    return secondary_paint(sec)


def gradient_defs(stops, y0=80, y1=432, gid="cbGrad"):
    """Vertical userSpace gradient spanning the glyph ink box."""
    return (f'<defs><linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
            f'x1="256" y1="{y0}" x2="256" y2="{y1}">'
            f'<stop offset="0" stop-color="{stops[0]}"/>'
            f'<stop offset="1" stop-color="{stops[-1]}"/>'
            f'</linearGradient></defs>')


def apply_gradient(body, color, stops):
    """Repaint every flat stroke (and fill) of `color` with the gradient
    reference. Fills join the ramp so accent dots ride the same paint as
    the strokes (TizenTube's tip dot); stroke-only glyphs are untouched."""
    return (gradient_defs(stops)
            + body.replace(f'stroke="{color}"', 'stroke="url(#cbGrad)"')
            .replace(f'fill="{color}"', 'fill="url(#cbGrad)"'))


# ==========================================================================
# Distinct, recognisable marks.
#
# Written because 81 icons were sharing 44 glyphs — six apps rendered the same
# play-in-a-rectangle, which is what made the set feel like a generic rebrand.
# Each mark below is drawn to the silhouette users actually recognise, in our
# geometry: rounded ends, flat fill, one accent.
# ==========================================================================

def yt_play(c):
    """YouTube button/play in uniform linework; the entire interior is alpha."""
    return (f'<rect x="64" y="128" width="384" height="256" rx="64" {_s(c, 32)}/>'
            f'<path d="M 216 192 L 328 256 L 216 320 Z" {_s(c, 32)}/>')


def smarttube_play(c):
    """SmartTube: YouTube silhouette with a corner cut — the fork tell.

    Body narrowed from 444 to 400 wide: the 32 stroke on the old box put ink
    at x=18 and x=494, outside SAFE. The corner-cut rules move with the right
    edge so the tell stays on the corner it cuts.
    """
    return (f'<path d="M 132 118 L 380 118 C 422 118 456 152 456 194 '
            f'L 456 318 C 456 360 422 394 380 394 L 132 394 '
            f'C 90 394 56 360 56 318 L 56 194 C 56 152 90 118 132 118 Z" '
            f'{_s(c, 34)}/>'
            f'<path d="M 218 200 L 218 312 L 326 256 Z" {_s(c, 30)}/>'
            f'<path d="M 366 150 L 436 150" {_s(c, 22)}/>'
            f'<path d="M 366 190 L 436 190" {_s(c, 22)}/>')


def tizen_play(c):
    """TizenTube — the exact emblem, inside the YouTube frame.

    2026-09-15 direction (owner, round 7): "use the exact TizenTube logo
    within the box with the pack's style" — the whole official emblem
    (reisxd/TizenTube standalone banner, art by @Zyborg777), measured off
    the banner and rebuilt in the pack's monoline language inside the
    normal YouTube frame.

    The emblem is the two-arc globe — the main circle's arc running top,
    right and bottom, meeting the left circle's arc at the rim
    intersections — holding the open play: the left edge, the struck
    top chord running rim to rim, and the bottom edge ending in its free
    rounded cap, with the tip dot below the chord, right of the free
    cap, as in the art.

    Round-9 refinement (2026-09-15): the round-7 circles were refitted
    against clean per-arc sample sets (outer-edge rays, junctions and
    the wordmark excluded). The old main circle was ~12px too large and
    the left one ~25px too large, which stretched the globe and pushed
    the chord rim too far right. Verified geometry off the banner
    (strokes 34, overlay mismatch 3.5%): main circle c(372.2,434.2)
    r202.8, left circle c(314.7,442.9) r177.5, rim intersections
    (236.4,283.6) and (286.8,618.1); vertical x=242.5 from the chord
    (y=261.5) to the left arc (y=605.1); chord (242.5,261.5) to
    (574.5,447.1) at the right rim; bottom edge caps (289,595.5) to
    (462,495.5); dot an ellipse c(518.9,459.3) a=22.9 b=16.6 rotated
    -28.9 deg. Scaled 0.4570x about the frame centre (same optical size
    as round 7); emblem strokes carry the lightest monoline weight
    (author 20, 21.8 after normalisation), and the dot is held ~5px off
    the chord so the heavier stroke does not close the near-touching
    gap the art has open.
    """
    return (f'<rect x="64" y="128" width="384" height="256" rx="64" {_s(c, 32)}/>'
            f'<path d="M 201.2 187.2 A 92.7 92.7 0 1 1 224.5 340.2" {_s(c, 20)}/>'
            f'<path d="M 201.2 187.2 A 81.1 81.1 0 0 0 224.5 340.2" {_s(c, 20)}/>'
            f'<path d="M 204.1 177.1 L 204.1 334.1" {_s(c, 20)}/>'
            f'<path d="M 204.1 177.1 L 355.8 261.9" {_s(c, 20)}/>'
            f'<path d="M 225.3 329.7 L 304.4 284.0" {_s(c, 20)}/>'
            f'<ellipse cx="328.0" cy="271.9" rx="10.5" ry="7.6" '
            f'transform="rotate(-28.9 328.0 271.9)" {_f(c)}/>')


def tizen_play_dot(c):
    """TizenTube banner mark — identical to the square from round 7 on.

    The mark IS the exact emblem, and the emblem already carries its
    tip dot, so banner and icon share one construction.
    """
    return tizen_play(c)


def film_reel(c):
    """Cinema HD: film strip - perforated frame.

    The eight perforations were solid dots inside the sprocket lanes, and two
    of them closed the lane they sat in. Cutting them to four larger openings
    per lane keeps the perforated read and stops the lanes filling.
    """
    return (f'<rect x="74" y="126" width="364" height="260" rx="34" {_s(c, 32)}/>'
            f'<path d="M 74 196 L 438 196" {_s(c, 24)}/>'
            f'<path d="M 74 316 L 438 316" {_s(c, 24)}/>'
            f'<path d="M 140 161 L 180 161 M 236 161 L 276 161 '
            f'M 332 161 L 372 161" {_s(c, 22)}/>'
            f'<path d="M 140 351 L 180 351 M 236 351 L 276 351 '
            f'M 332 351 L 372 351" {_s(c, 22)}/>')


def flix_f(c):
    """Streamflix: bold F with a play notch."""
    return (f'<path d="M 168 396 L 168 128 L 356 128" {_s(c, 46)}/>'
            f'<path d="M 168 256 L 316 256" {_s(c, 42)}/>')


def yinyang_play(c):
    """WuPlay: yin-yang drawn as contour, with two play marks.

    The two play wedges were 56px tall against a 22 stroke, so both closed at
    tile size. Larger wedges sit in the same positions and stay open.
    """
    return (f'<circle cx="256" cy="256" r="186" {_s(c, 32)}/>'
            f'<path d="M 256 70 A 93 93 0 0 1 256 256 A 93 93 0 0 0 256 442" '
            f'{_s(c, 32)}/>'
            f'<path d="M 216 124 L 216 212 L 290 168 Z" {_s(c, 22)}/>'
            f'<path d="M 296 300 L 296 388 L 222 344 Z" {_s(c, 22)}/>')


def stremio_square(c):
    """Stremio's diamond/play motif, kept in Core Builds' monoline language."""
    return (f'<path d="M 256 76 L 436 256 L 256 436 L 76 256 Z" {_s(c, 32)}/>'
            f'<path d="M 214 182 L 328 256 L 214 330 Z" {_s(c, 32)}/>')


def arvio_a(c):
    """Arvio: an A built from a play wedge."""
    return (f'<path d="M 132 396 L 256 116 L 380 396" {_s(c, 42)}/>'
            f'<path d="M 190 300 L 322 300" {_s(c, 34)}/>')


def lumera_beam(c):
    """Lumera: a lit lamp/prism - the 'lumen' idea.

    A filament line ran the full height of the prism and split it into two
    slivers, one of which closed. A shorter filament leaves the prism open.
    """
    return (f'<path d="M 256 96 L 372 300 L 140 300 Z" {_s(c, 34)}/>'
            f'<path d="M 186 300 L 186 372 C 186 410 218 436 256 436 '
            f'C 294 436 326 410 326 372 L 326 300" {_s(c, 32)}/>'
            f'<path d="M 256 214 L 256 262" {_s(c, 24)}/>')


def debrid_bolt(c):
    """Real-Debrid: unlocked bolt — instant, unlocked."""
    return (f'<path d="M 288 68 L 152 286 L 250 286 L 224 444 L 366 214 '
            f'L 264 214 Z" {_s(c, 32)}/>')


def alldebrid_infinity(c):
    """AllDebrid: infinity loop — 'all'."""
    return (f'<path d="M 176 256 C 176 200 120 200 120 256 C 120 312 176 312 '
            f'176 256 C 176 200 232 312 292 312 C 380 312 380 200 292 200 '
            f'C 232 200 176 312 176 256 Z" {_s(c, 32)}/>')


def unlinked_break(c):
    """Unlinked: a broken chain."""
    return (f'<path d="M 190 214 L 150 254 C 106 298 172 364 216 320 L 246 290" '
            f'{_s(c, 34)}/>'
            f'<path d="M 322 298 L 362 258 C 406 214 340 148 296 192 L 266 222" '
            f'{_s(c, 34)}/>'
            f'<path d="M 200 128 L 224 172" {_s(c, 22)}/>'
            f'<path d="M 312 384 L 288 340" {_s(c, 22)}/>')


def debrify_d(c):
    """Debrify: D with a download arrow."""
    return (f'<path d="M 168 120 L 168 392 L 258 392 C 356 392 400 330 400 256 '
            f'C 400 182 356 120 258 120 Z" {_s(c, 34)}/>')


def spotify_arcs(c):
    """Spotify: ring with three arcs — outlined, not a filled disc."""
    return (f'<circle cx="256" cy="256" r="188" {_s(c, 34)}/>'
            f'<path d="M 152 192 C 228 166 320 174 378 204" {_s(c, 32)}/>'
            f'<path d="M 164 264 C 232 242 306 248 356 274" {_s(c, 28)}/>'
            f'<path d="M 178 332 C 234 316 288 320 328 338" {_s(c, 24)}/>')


def play_store_tri(c):
    """Google Play: the sideways triangle built from folded planes."""
    return (f'<path d="M 118 70 L 118 442 L 400 256 Z" {_s(c, 34)}/>'
            f'<path d="M 118 70 L 306 196" {_s(c, 26)}/>'
            f'<path d="M 118 442 L 306 316" {_s(c, 26)}/>')


def torbox_cube(c):
    """TorBox: a box with a down arrow through it."""
    return (f'<path d="M 256 88 L 424 180 L 424 332 L 256 424 L 88 332 '
            f'L 88 180 Z" {_s(c, 32)}/>'
            f'<path d="M 88 180 L 256 272 L 424 180" {_s(c, 26)}/>'
            f'<path d="M 256 272 L 256 424" {_s(c, 26)}/>')


def premiumize_p(c):
    """Premiumize: P in a rounded square."""
    return (f'<rect x="76" y="76" width="360" height="360" rx="84" {_s(c, 32)}/>'
            f'<path d="M 202 366 L 202 152 L 282 152 C 340 152 366 186 366 224 '
            f'C 366 262 340 296 282 296 L 202 296" {_s(c, 36)}/>')


def monitor_wave(c):
    """Live TV / IPTV: screen with a signal wave."""
    return (f'<rect x="60" y="106" width="392" height="252" rx="34" {_s(c, 32)}/>'
            f'<path d="M 168 412 L 344 412" {_s(c, 30)}/>'
            f'<path d="M 130 232 C 168 190 216 190 256 232 '
            f'C 296 274 344 274 382 232" {_s(c, 28)}/>')


def satellite(c):
    """StreamVault / OwnTV: dish + signal."""
    return (f'<path d="M 96 416 C 96 288 208 176 336 176" {_s(c, 34)}/>'
            f'<circle cx="120" cy="392" r="30" {_f(c)}/>'
            f'<path d="M 300 118 C 348 118 394 164 394 212" {_s(c, 26)}/>'
            f'<path d="M 296 62 C 372 62 450 140 450 216" {_s(c, 26)}/>')


def vault_lock(c):
    """StreamVault: a vault door.

    Four spokes radiating from the dial into the door wall closed the gap
    between them. The dial inside the door is the vault.
    """
    return (f'<rect x="76" y="76" width="360" height="360" rx="52" {_s(c, 32)}/>'
            f'<circle cx="256" cy="256" r="112" {_s(c, 30)}/>'
            f'<path d="M 256 144 L 256 196" {_s(c, 24)}/>')


def equalizer(c):
    """Three faders with genuinely transparent centres, not night-colour plugs."""
    out = []
    for x, knob in ((130, 188), (256, 310), (382, 232)):
        out.append(f'<path d="M {x} 108 V {knob - 34} M {x} {knob + 34} V 404" {_s(c, 32)}/>')
        out.append(f'<circle cx="{x}" cy="{knob}" r="34" {_s(c, 24)}/>')
    return "".join(out)


def music_note(c):
    """Metrolist: a note."""
    return (f'<path d="M 196 372 L 196 128 L 384 96 L 384 340" {_s(c, 32)}/>'
            f'<ellipse cx="150" cy="372" rx="52" ry="42" {_s(c, 30)}/>'
            f'<ellipse cx="338" cy="340" rx="52" ry="42" {_s(c, 30)}/>')


def bag_play(c):
    """Aptoide: bag with a play mark."""
    return (f'<path d="M 118 172 L 394 172 L 372 404 L 140 404 Z" {_s(c, 32)}/>'
            f'<path d="M 196 224 L 196 148 C 196 108 226 84 256 84 '
            f'C 286 84 316 108 316 148 L 316 224" {_s(c, 30)}/>')


def aurora_a(c):
    """Aurora Store: an A over a download arc."""
    return (f'<path d="M 140 350 L 256 110 L 372 350" {_s(c, 38)}/>'
            f'<path d="M 194 288 L 318 288" {_s(c, 30)}/>'
            f'<path d="M 120 412 L 392 412" {_s(c, 30)}/>')


def launcher_grid(c):
    """Launchers: a card grid.

    Four cards at 164x140 with 40px channels put 37.8 percent ink on the tile.
    Smaller cards on wider channels read as a grid at a third less ink.
    """
    return (f'<rect x="86" y="106" width="148" height="122" rx="26" {_s(c, 30)}/>'
            f'<rect x="278" y="106" width="148" height="122" rx="26" {_s(c, 30)}/>'
            f'<rect x="86" y="284" width="148" height="122" rx="26" {_s(c, 30)}/>'
            f'<rect x="278" y="284" width="148" height="122" rx="26" {_s(c, 30)}/>')


def rocket(c):
    """AT4K / performance launchers.

    The two fins folded back against the body and the notch inside each one
    closed. Opening the fins away from the hull keeps them readable.
    """
    return (f'<path d="M 256 68 C 320 130 348 218 340 306 L 172 306 '
            f'C 164 218 192 130 256 68 Z" {_s(c, 32)}/>'
            f'<circle cx="256" cy="196" r="44" {_s(c, 26)}/>'
            f'<path d="M 170 258 L 96 340" {_s(c, 26)}/>'
            f'<path d="M 342 258 L 416 340" {_s(c, 26)}/>'
            f'<path d="M 212 350 L 256 444 L 300 350" {_s(c, 28)}/>')


def droplet(c):
    """Monet: a colour droplet — dynamic theming."""
    return (f'<path d="M 256 82 C 256 82 380 218 380 300 '
            f'C 380 372 324 428 256 428 C 188 428 132 372 132 300 '
            f'C 132 218 256 82 256 82 Z" {_s(c, 32)}/>'
            f'<path d="M 316 236 C 344 272 352 306 340 340" {_s(c, 26)}/>')


def fandango_ticket(c):
    """Fandango at Home: the notched ticket stub with its cut F.

    Vudu relaunched as Fandango at Home on an orange ticket stub — a
    rounded square with semicircular notch cuts in the side edges and a
    knocked-out F. Drawn upright: the notched stub outline carries the
    primary stroke, the F sits subordinate inside it.
    """
    return (f'<path d="M 152 116 H 360 C 380 116 396 132 396 152 V 220 '
            f'A 36 36 0 0 0 396 292 V 360 C 396 380 380 396 360 396 H 152 '
            f'C 132 396 116 380 116 360 V 292 A 36 36 0 0 0 116 220 V 152 '
            f'C 116 132 132 116 152 116 Z" {_s(c, 34)}/>'
            f'<path d="M 216 176 L 216 336" {_s(c, 26)}/>'
            f'<path d="M 216 176 L 308 176" {_s(c, 26)}/>'
            f'<path d="M 216 256 L 288 256" {_s(c, 26)}/>')


def hi_browser_ring(c):
    """Hi Browser: the globe crossed by its orbit ring.

    Hisense's Android TV browser mark is a globe with one orbit swoosh
    running in front low and behind high. A single tilted ellipse keeps
    both passes of the ring readable without fills to hide behind.
    """
    return (f'<circle cx="256" cy="256" r="140" {_s(c, 34)}/>'
            f'<path d="M 62 319 A 204 64 -18 0 1 450 193 '
            f'A 204 64 -18 0 1 62 319 Z" {_s(c, 26)}/>')


def screen_record_mark(c):
    """Screen Recording App: a screen holding the record target.

    2kit's Screen Recording App is the TV-first recorder (D-pad native,
    internal audio). The universal recorder cue is the record target —
    ring and centre dot — so it sits inside a rounded screen frame.
    """
    return (f'<rect x="96" y="128" width="320" height="256" rx="48" {_s(c, 34)}/>'
            f'<circle cx="256" cy="256" r="64" {_s(c, 26)}/>'
            f'<path d="M 254 256 L 258 256" {_s(c, 26)}/>')


def bit_tv_mark(c):
    """BitTV: the 'bit' wordmark closing on its play-tile mark.

    BitTV (Duktek's Android digital TV, sideloaded and on Play as
    'BitTV: Android Digital TV') sets a white lowercase 'bit' plus a
    stemmed play wedge on its blue tile. Drawn as monoline type: b, i,
    t, then the stem-and-wedge mark; the vendor's trailing tick is
    dropped rather than crowding the safe area.
    """
    return (f'<path d="M 76 140 L 76 336" {_s(c, 34)}/>'
            f'<circle cx="116" cy="296" r="40" {_s(c, 34)}/>'
            f'<path d="M 200 208 L 200 336" {_s(c, 34)}/>'
            f'<path d="M 200 150 L 200 156" {_s(c, 34)}/>'
            f'<path d="M 252 140 L 252 336" {_s(c, 34)}/>'
            f'<path d="M 244 208 L 284 208" {_s(c, 34)}/>'
            f'<path d="M 332 150 L 332 336" {_s(c, 34)}/>'
            f'<path d="M 332 176 L 412 250 L 332 324" {_s(c, 34)}/>')


def vidio_wordmark(c):
    """Vidio: the lowercase wordmark as monoline type.

    Indonesia's Vidio is wordmark-first — a script lockup on the brand
    pink-red, no standalone emblem. The shared play-banner glyph it
    borrowed read as Megogo, not Vidio, so the five letters are drawn at
    pack proportions in the sampled lockup red instead.
    """
    return (f'<path d="M 56 208 L 80 336 L 104 208" {_s(c, 34)}/>'
            f'<path d="M 148 208 L 148 336" {_s(c, 34)}/>'
            f'<path d="M 148 150 L 148 156" {_s(c, 34)}/>'
            f'<circle cx="232" cy="296" r="40" {_s(c, 34)}/>'
            f'<path d="M 272 140 L 272 336" {_s(c, 34)}/>'
            f'<path d="M 316 208 L 316 336" {_s(c, 34)}/>'
            f'<path d="M 316 150 L 316 156" {_s(c, 34)}/>'
            f'<circle cx="408" cy="272" r="48" {_s(c, 34)}/>')


GLYPHS.update({
    "yt_play": yt_play, "smarttube_play": smarttube_play,
    "tizen_play": tizen_play, "tizen_play_dot": tizen_play_dot,
    "film_reel": film_reel, "flix_f": flix_f,
    "yinyang_play": yinyang_play, "stremio_square": stremio_square,
    "arvio_a": arvio_a, "lumera_beam": lumera_beam,
    "debrid_bolt": debrid_bolt, "alldebrid_infinity": alldebrid_infinity,
    "unlinked_break": unlinked_break, "debrify_d": debrify_d,
    "spotify_arcs": spotify_arcs, "play_store_tri": play_store_tri,
    "torbox_cube": torbox_cube, "premiumize_p": premiumize_p,
    "monitor_wave": monitor_wave, "satellite": satellite,
    "vault_lock": vault_lock, "equalizer": equalizer, "music_note": music_note,
    "bag_play": bag_play, "aurora_a": aurora_a, "launcher_grid": launcher_grid,
    "rocket": rocket, "droplet": droplet,
    "fandango_ticket": fandango_ticket, "hi_browser_ring": hi_browser_ring,
    "screen_record_mark": screen_record_mark,
    "bit_tv_mark": bit_tv_mark, "vidio_wordmark": vidio_wordmark,
    "tubi_mono": tubi_mono, "vidio_mono": vidio_mono,
    "bit_tv_mono": bit_tv_mono,
})


# ==========================================================================
# Monogram set A-Z and 0-9.
#
# Driven from Outfit ExtraBold (tools/typeface.py) so every letter shares
# the same weight, contrast and optical box. Hand-drawn strokes drifted
# letter-to-letter; a row of fallback icons then read as mixed alphabets.
# Outlines are converted to paths — nothing ships as <text>.
# ==========================================================================


def monogram_0(c):
    return monogram_body("0", c)


def monogram_1(c):
    return monogram_body("1", c)


def monogram_2(c):
    return monogram_body("2", c)


def monogram_3(c):
    return monogram_body("3", c)


def monogram_4(c):
    return monogram_body("4", c)


def monogram_5(c):
    return monogram_body("5", c)


def monogram_6(c):
    return monogram_body("6", c)


def monogram_8(c):
    return monogram_body("8", c)


def monogram_A(c):
    return monogram_body("A", c)


def monogram_B(c):
    return monogram_body("B", c)


def monogram_C(c):
    return monogram_body("C", c)


def monogram_D(c):
    return monogram_body("D", c)


def monogram_E(c):
    return monogram_body("E", c)


def monogram_F(c):
    return monogram_body("F", c)


def monogram_G(c):
    return monogram_body("G", c)


def monogram_H(c):
    return monogram_body("H", c)


def monogram_I(c):
    return monogram_body("I", c)


def monogram_J(c):
    return monogram_body("J", c)


def monogram_K(c):
    return monogram_body("K", c)


def monogram_L(c):
    return monogram_body("L", c)


def monogram_M(c):
    return monogram_body("M", c)


def monogram_N(c):
    return monogram_body("N", c)


def monogram_O(c):
    return monogram_body("O", c)


def monogram_P(c):
    return monogram_body("P", c)


def monogram_Q(c):
    return monogram_body("Q", c)


def monogram_R(c):
    return monogram_body("R", c)


def monogram_S(c):
    return monogram_body("S", c)


def monogram_T(c):
    return monogram_body("T", c)


def monogram_U(c):
    return monogram_body("U", c)


def monogram_V(c):
    return monogram_body("V", c)


def monogram_W(c):
    return monogram_body("W", c)


def monogram_X(c):
    return monogram_body("X", c)


def monogram_Y(c):
    return monogram_body("Y", c)


def monogram_Z(c):
    return monogram_body("Z", c)


GLYPHS.update({
    "monogram_0": monogram_0,
    "monogram_1": monogram_1,
    "monogram_2": monogram_2,
    "monogram_3": monogram_3,
    "monogram_4": monogram_4,
    "monogram_5": monogram_5,
    "monogram_6": monogram_6,
    "monogram_8": monogram_8,
    "monogram_A": monogram_A,
    "monogram_B": monogram_B,
    "monogram_C": monogram_C,
    "monogram_D": monogram_D,
    "monogram_E": monogram_E,
    "monogram_F": monogram_F,
    "monogram_G": monogram_G,
    "monogram_H": monogram_H,
    "monogram_I": monogram_I,
    "monogram_J": monogram_J,
    "monogram_K": monogram_K,
    "monogram_L": monogram_L,
    "monogram_M": monogram_M,
    "monogram_N": monogram_N,
    "monogram_O": monogram_O,
    "monogram_P": monogram_P,
    "monogram_Q": monogram_Q,
    "monogram_R": monogram_R,
    "monogram_S": monogram_S,
    "monogram_T": monogram_T,
    "monogram_U": monogram_U,
    "monogram_V": monogram_V,
    "monogram_W": monogram_W,
    "monogram_X": monogram_X,
    "monogram_Y": monogram_Y,
    "monogram_Z": monogram_Z,
})



# ==========================================================================
# Marks for apps seen on a real device row that the pack had missed.
# Original geometry, monoline weight.
# ==========================================================================

def stadium(c):
    """
    SYNC — stadium bowl in perspective.

    Two earlier attempts added floodlight pylons; at 100px they read first as
    pot handles and then as antennae on a face. Removed. The concentric
    tilted ovals plus the halfway line are enough to say "arena", and they
    survive the downscale, which the masts never did.
    """
    return (
        f'<path d="M 56 262 C 56 190 146 138 256 138 C 366 138 456 190 456 262 '
        f'C 456 334 366 386 256 386 C 146 386 56 334 56 262 Z" {_s(c, 32)}/>'
        f'<path d="M 132 262 C 132 220 188 194 256 194 C 324 194 380 220 380 262 '
        f'C 380 304 324 330 256 330 C 188 330 132 304 132 262 Z" {_s(c, 26)}/>'
        f'<path d="M 256 194 L 256 330" {_s(c, 20)}/>')


def browser_globe(c):
    """TV Bro - a globe inside a rounded screen.

    Its own icon is neon 'TV BRO' lettering wrapping a remote and a wire
    globe. A wordmark cannot survive the downscale, so the globe carries it.

    The two meridians plus the equator cut the globe into six cells, four of
    which closed at tile size and took the mark to 36 percent ink. One
    meridian and the equator keep the wire-globe read.
    """
    return (f'<rect x="56" y="96" width="400" height="284" rx="72" {_s(c, 32)}/>'
            f'<circle cx="256" cy="238" r="108" {_s(c, 28)}/>'
            f'<path d="M 148 238 L 364 238" {_s(c, 24)}/>'
            f'<path d="M 256 130 C 306 172 306 304 256 346" {_s(c, 24)}/>'
            f'<path d="M 176 434 L 336 434" {_s(c, 28)}/>'
            f'<path d="M 256 380 L 256 434" {_s(c, 24)}/>')


GLYPHS.update({"stadium": stadium, "browser_globe": browser_globe,
                  "artemis_pad": artemis_pad})


# ==========================================================================
# Bespoke marks for apps seen on the user's real device row.
#
# Each replaces a shared glyph: Janky was one of five apps on play_round,
# TiviMate one of twenty-one on monogram_T. A mark shared twenty-one ways
# is not a mark, it is a placeholder.
# ==========================================================================

def janky_play(c):
    """Janky Player - the shipped app's own mark: a J-hook cradling a play.

    Three sources, in order of authority. The app has no public footprint:
    on 2026-09-19 there was no Play, APKPure, Uptodown or F-Droid listing and
    a GitHub code search for com.player.janky returned only this repository's
    generated files. Late that day the user supplied the app's home anyway:
    github.com/jankyapp/jankyapp, a readme stub whose fourteen beta releases
    (v0.95.47-beta shipped the same day) carry the APKs - unreachable from
    this sandbox, as is the org avatar, so the release stream proves the app
    is alive without feeding pixels. The vendor wordmark the user
    photographed - a grey anarchy A with a hamster sitting inside the ring -
    is BANNER art; it is what the leanback row shows, not what the launcher
    tile shows. The tile is the mark on the user-supplied rebuild sheet: a
    heavy white J whose bowl cradles a play triangle in the app's accent,
    apex welded to the stem's inner edge. J for Janky, play for player, and
    the sheet also records what the application is - a user-themable slate
    aggregator - the only functional statement any source makes about it.

    core_monoline translates the sheet twice: fills are forbidden, so the
    shipped solid triangle becomes an outline, and one accent per glyph, so
    the sheet's two-tone white-hook-plus-accent-triangle renders as the
    single accent the user-themable app ships by default, #1E88E5.

    Measured with tools/check_glyph.py at 96px (a real Projectivy tile),
    48px and 32px: see the receipt in the commit message.

    Revised once more on tester feedback (RB3, via the user's Discord,
    2026-09-19): "smaller J, circle around it, bigger play sign". So the
    hook now sits at the detail weight inside its own ring - the circle
    the vendor's anarchy A always wore, moved from the banner art to the
    tile - hanging from the ring's inner arc so it is never an island, and
    the play sign stands 200px tall against the previous mark's 136, 24px
    clear of the ring so badge and play read as one lockup rather than a
    weld. RB3's "bigger" is size, not weight: core_monoline's own rule -
    keep the two detail weights subordinate rather than flattening every
    level to 32 - keeps the ring the sole primary, the way browser_globe
    and play_hex hierarchise their containers, and the thinner play stroke
    opens its counter earlier on the downscale. The lockup sits centred on
    the 256 axis. At 32px the ring interior and the J merge into a single
    hooked disc and the triangle keeps its counter: the same convergence
    the shipped sheet's own 24px row shows.

    The user closed the design on 2026-09-19 from the banner render they
    approved: the ring-and-J badge with the play sign beside it, and no
    receiver furniture - the stand and rabbit-ear aerials of the IPTV-family
    draft are retired here, the approved composition is the floating lockup.
    Two paints they asked for by name. The J in off-white ink: the shipped
    tile is a white hook cradling an accent triangle, and core_monoline used
    to flatten that two-tone into one accent - the catalog's declared `ink`
    now honours the hook as paint, in the Brand Guide's off-white. And a
    gradient: the banner's cyan-to-violet rail (build_banners' ACCENT to
    VIOLET, the ramp the approved render wears on its left edge) extended
    into the ring and the play through the catalog's declared `gradient`,
    the same mechanism nuvio and tizentube ship - so tile, banner and rail
    share one paint story. core_monoline grew two declared extensions for
    this (catalog `gradient`, catalog `ink`); undeclared whites still fail.
    Geometry is the approved banner's: ring primary at 32, hook and play
    subordinate at 26.2, the lockup centred on the 256 axis. At 32px the
    ring interior and the J merge into a single hooked disc and the
    triangle keeps its counter: the same convergence the shipped sheet's
    own 24px row shows.

    RB4 (same day) reacted to the user's Projectivy screenshots by
    promoting the ring to a full-size container with the hook and play
    inside it. The user rejected that build: "the version before look
    better then this current one. I just needed some weight fixing etc."
    RB5 therefore restores this composition - the ring-and-J badge with
    the play sign beside it - and fixes what the screenshots actually
    measured, which was weight, not composition. Research agrees on the
    two levers: stroke is mass (Material's icon metrics call a thicker
    stroke "a sense of heaviness and mass"), and uniformity is optical
    area, not bounding box (the optical grid gives a horizontal rectangle
    a wider but shorter box than the square, never a squarer one).
    Measured against this pack, RB3's fault was relative stroke: 32px on
    a 240px ring is a 0.133 stroke ratio where the pack's container rings
    (mpv 32/388, stremio 32/389) sit at 0.082 - the small ring read one
    and a half steps chunkier than every ring beside it, which is what
    "sized wrong" looks like at a 100px tile. Ink coverage was never the
    problem: RB3 measured 0.152 against a pack median of 0.173, lighter
    than 290 of 477 tiles. So RB5 keeps the lockup and re-weights it:
    ring 26 on a 264px outer (ratio 0.098, inside the container family's
    band), hook 22, play 24 - one step down the house weight vocabulary,
    hierarchy intact - and the ring grows 240 to 264 outer so the ink box
    rises from 0.47 to 0.52 of the grid, towards the optical grid's
    horizontal-rectangle proportion instead of a flat band. Width stays
    at 0.82 inside SAFE; coverage lands near 0.14, lighter still, because
    a wide lockup that sheds stroke reads the same mass as a square mark
    that keeps it. Paints untouched: off-white hook, cyan-to-violet ring
    and play.
    """
    return (f'<circle cx="178" cy="256" r="119" {_s(c, 26)}/>'
            f'<path d="M 212 200 L 212 268 A 44 44 0 0 1 124 268" {_s(OFFWHITE_INK, 22)}/>'
            f'<path d="M 345 161 L 345 351 L 455 256 Z" {_s(c, 24)}/>')


def tivimate_grid(c):
    """TiviMate - an EPG grid: the programme guide is the app.

    The filled 'now' cell was a solid block inside an already dense grid and
    took the mark to 35 percent ink. Drawing the cell as an outline keeps the
    highlight and opens the row back up - and it drops the one solid fill in
    the mark, so the glyph is now stroke-only like the rest.
    """
    return (f'<rect x="56" y="104" width="400" height="268" rx="40" {_s(c, 32)}/>'
            f'<path d="M 158 104 L 158 372" {_s(c, 24)}/>'
            f'<path d="M 158 238 L 456 238" {_s(c, 22)}/>'
            f'<rect x="196" y="142" width="146" height="62" rx="16" {_s(c, 22)}/>'
            f'<path d="M 176 428 L 336 428" {_s(c, 28)}/>'
            f'<path d="M 256 372 L 256 428" {_s(c, 24)}/>')


def downloader_arrow(c):
    """
    Downloader — arrow into a tray, inside a rounded frame.

    Replaces the bare download_arrow with something that reads as an app
    rather than a system glyph: the frame gives it presence at 100px.
    """
    return (f'<rect x="62" y="62" width="388" height="388" rx="96" {_s(c, 32)}/>'
            f'<path d="M 256 132 L 256 300" {_s(c, 34)}/>'
            f'<path d="M 180 232 L 256 308 L 332 232" {_s(c, 34)}/>'
            f'<path d="M 156 366 L 356 366" {_s(c, 30)}/>')


GLYPHS.update({
    "janky_play": janky_play,
    "tivimate_grid": tivimate_grid,
    "downloader_arrow": downloader_arrow,
})


# ==========================================================================
# File transfer, file managers, Synology NAS, Sparkle TV.
# Original geometry — suggest the app, never trace a vendor mark.
# ==========================================================================


def localsend_sun(c):
    """LocalSend: the hub disc ringed by eight beam dashes.

    The official mark is a centre disc inside a dashed ring of eight
    rounded beams — one device talking to every neighbour at once. The
    old two-phone packet read as a transfer between two devices, not the
    broadcast the brand draws.
    """
    return (
        f'<circle cx="256" cy="256" r="80" {_s(c, 26)}/>'
        f'<path d="M 420 221 A 168 168 0 0 1 420 291" {_s(c, 34)}/>'
        f'<path d="M 397 348 A 168 168 0 0 1 348 397" {_s(c, 34)}/>'
        f'<path d="M 291 420 A 168 168 0 0 1 221 420" {_s(c, 34)}/>'
        f'<path d="M 165 397 A 168 168 0 0 1 115 348" {_s(c, 34)}/>'
        f'<path d="M 92 291 A 168 168 0 0 1 92 221" {_s(c, 34)}/>'
        f'<path d="M 115 165 A 168 168 0 0 1 165 115" {_s(c, 34)}/>'
        f'<path d="M 221 92 A 168 168 0 0 1 291 92" {_s(c, 34)}/>'
        f'<path d="M 348 115 A 168 168 0 0 1 397 164" {_s(c, 34)}/>'
    )


def sparkle_burst(c):
    """Sparkle TV — a 4-point sparkle over a screen. Extra playlists, live."""
    return (
        f'<rect x="72" y="128" width="368" height="232" rx="40" {_s(c, 32)}/>'
        f'<path d="M 176 420 L 336 420" {_s(c, 28)}/>'
        f'<path d="M 256 360 L 256 420" {_s(c, 24)}/>'
        # 4-point sparkle, original geometry
        f'<path d="M 256 86 L 276 196 L 386 216 L 276 236 L 256 346 '
        f'L 236 236 L 126 216 L 236 196 Z" {_s(c, 28)}/>'
    )


def nas_stack(c):
    """Synology / NAS - the drive shelf.

    At 53 percent ink this was the densest mark in the pack: three bays, three
    status dots and three rules stacked into one box. The bays alone are the
    product, and one status dot per bay is enough to say 'drive'.
    """
    return (
        f'<rect x="86" y="100" width="340" height="96" rx="24" {_s(c, 30)}/>'
        f'<rect x="86" y="220" width="340" height="96" rx="24" {_s(c, 30)}/>'
        f'<rect x="86" y="340" width="340" height="96" rx="24" {_s(c, 30)}/>'
    )


def nas_play(c):
    """DS video - drive shelf with a play wedge.

    Two shelf rules plus a wedge left three narrow bands. One rule under the
    wedge keeps the shelf read at a third of the ink.
    """
    return (
        f'<rect x="72" y="118" width="368" height="276" rx="36" {_s(c, 32)}/>'
        f'<path d="M 72 190 L 440 190" {_s(c, 24)}/>'
        f'<path d="M 206 232 L 206 352 L 330 292 Z" {_s(c, 30)}/>'
    )


def nas_image(c):
    """DS photo — drive shelf framing a landscape."""
    return (
        f'<rect x="72" y="118" width="368" height="276" rx="36" {_s(c, 32)}/>'
        f'<path d="M 118 318 L 196 232 L 256 286 L 318 214 L 394 318 Z" {_s(c, 28)}/>'
        f'<circle cx="168" cy="186" r="22" {_s(c, 24)}/>'
    )


def folder_rs(c):
    """RS File Manager — folder with a file-index list, not a letter."""
    return (
        f'<path d="M 80 152 L 216 152 L 258 206 L 432 206 L 432 372 '
        f'C 432 384 422 394 410 394 L 102 394 C 90 394 80 384 80 372 Z" '
        f'{_s(c, 32)}/>'
        f'<path d="M 156 262 L 356 262" {_s(c, 24)}/>'
        f'<path d="M 156 308 L 356 308" {_s(c, 24)}/>'
        f'<path d="M 156 354 L 300 354" {_s(c, 24)}/>'
    )


def folder_wifi(c):
    """WiFi File Explorer — folder radiating a short-range arc."""
    return (
        f'<path d="M 80 168 L 216 168 L 258 222 L 432 222 L 432 388 '
        f'C 432 400 422 410 410 410 L 102 410 C 90 410 80 400 80 388 Z" '
        f'{_s(c, 32)}/>'
        f'<path d="M 176 118 C 216 86 296 86 336 118" {_s(c, 26)}/>'
        f'<path d="M 204 148 C 228 128 284 128 308 148" {_s(c, 26)}/>'
        f'<circle cx="256" cy="172" r="10" {_f(c)}/>'
    )


def folder_fx(c):
    """FX File Explorer — folder with a crossed tab."""
    return (
        f'<path d="M 80 152 L 216 152 L 258 206 L 432 206 L 432 372 '
        f'C 432 384 422 394 410 394 L 102 394 C 90 394 80 384 80 372 Z" '
        f'{_s(c, 32)}/>'
        f'<path d="M 176 250 L 336 346" {_s(c, 28)}/>'
        f'<path d="M 336 250 L 176 346" {_s(c, 28)}/>'
    )


def folder_solid(c):
    """Solid Explorer — folder with a filled inner plate."""
    return (
        f'<path d="M 80 152 L 216 152 L 258 206 L 432 206 L 432 372 '
        f'C 432 384 422 394 410 394 L 102 394 C 90 394 80 384 80 372 Z" '
        f'{_s(c, 32)}/>'
        f'<rect x="148" y="248" width="216" height="96" rx="18" {_s(c, 26)}/>'
    )


def radar_dish(c):
    """DS finder - a dish sweeping for a NAS on the LAN.

    The pivot was radius 28 under a 26.2 stroke: a 15px counter, which is
    under three pixels on a real Projectivy tile and closes into a dot.
    Radius 42 on a lighter stroke gives a 31px counter that survives. Found
    by tools/check_glyph.py, not by looking at the 512px master, where it
    reads perfectly well.
    """
    return (
        f'<path d="M 96 392 C 96 250 210 136 352 136" {_s(c, 34)}/>'
        f'<circle cx="128" cy="364" r="42" {_s(c, 22)}/>'
        f'<path d="M 158 334 L 256 236" {_s(c, 26)}/>'
        f'<path d="M 300 96 C 372 96 448 172 448 244" {_s(c, 26)}/>'
        f'<path d="M 324 148 C 368 148 412 192 412 236" {_s(c, 26)}/>'
    )


GLYPHS.update({
    "localsend_sun": localsend_sun,
    "sparkle_burst": sparkle_burst,
    "nas_stack": nas_stack,
    "nas_play": nas_play,
    "nas_image": nas_image,
    "folder_rs": folder_rs,
    "folder_wifi": folder_wifi,
    "folder_fx": folder_fx,
    "folder_solid": folder_solid,
    "radar_dish": radar_dish,
})


# ==========================================================================
# Tier 1 — signature marks for symbol-first brands.
#
# Written because a bare monogram read as "unfinished" beside the bespoke
# marks. Each app below has an iconic, single-reference silhouette, so it gets
# a designed mark instead of a letter. Same geometry (512 grid, rounded ends,
# flat fill, one accent). Nothing traces a vendor logo — these are the
# pack's own constructed evocations, drawn to be recognised at 10 feet.
# ==========================================================================
def _tile(c, x=64, y=64, w=384, h=384, rx=88, sw=30):
    """The contained-mark tile: a rounded squircle the other boxed glyphs use."""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" {_s(c, sw)}/>'


def netflix_ribbon(c):
    """Upright ribbon-N cue in one clean rounded line, not a solid logo slab."""
    return f'<path d="M 154 416 V 96 L 358 416 V 96" {_s(c, 32)}/>'


def crunchyroll_eye(c):
    """Crunchyroll: the curl inside its ring.

    The old mark traced the curl twice, and the tail hugged the ring closely
    enough to close the gap between them. One sweeping curl, held clear of the
    ring, keeps the tell.
    """
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 32)}/>'
            f'<path d="M 344 168 C 258 132 166 196 166 278 '
            f'C 166 344 220 386 284 386 C 330 386 368 364 392 330" {_s(c, 26)}/>')


def paramount_peak(c):
    """Mountain, snow fold and seven readable star glints, not a filled seal."""
    import math
    out = (f'<path d="M 118 386 L 256 172 L 394 386 Z" {_s(c, 32)}/>'
           f'<path d="M 208 250 L 236 238 L 256 260 L 276 242 L 306 274" {_s(c, 26)}/>')
    for degrees in (200, 223, 246, 270, 294, 317, 340):
        a = math.radians(degrees)
        x, y = 256 + 172 * math.cos(a), 272 + 172 * math.sin(a)
        out += (f'<path d="M {x - 7:.1f} {y:.1f} H {x + 7:.1f} '
                f'M {x:.1f} {y - 7:.1f} V {y + 7:.1f}" {_s(c, 20)}/>')
    return out


def peacock_fan(c):
    """Peacock: the wide six-feather fan over a base — the NBC peacock.

    Six feathers radiating in a semicircle from a central pin; the short base
    stem and dot-tipped feathers read as the colour-coded feather fan.
    """
    import math
    out = ''
    # base stem
    out += f'<path d="M 256 400 L 256 440" {_s(c, 30)}/>'
    # six feathers at even angles across the top semicircle
    for i in range(6):
        a = math.radians(-150 + i * 24)
        x1 = 256 + 36 * math.cos(a)
        y1 = 400 + 36 * math.sin(a)
        xm = 256 + 130 * math.cos(a)
        ym = 400 + 130 * math.sin(a)
        xt = 256 + 176 * math.cos(a)
        yt = 400 + 176 * math.sin(a)
        out += f'<path d="M {x1:.0f} {y1:.0f} L {xm:.0f} {ym:.0f}" {_s(c, 22)}/>'
        out += f'<circle cx="{xt:.0f}" cy="{yt:.0f}" r="15" {_f(c)}/>'
    return out


def discovery_sunburst(c):
    """Discovery: the globe with a rising sunburst crown."""
    return (f'<circle cx="256" cy="276" r="150" {_s(c, 34)}/>'
            f'<path d="M 106 276 L 406 276" {_s(c, 26)}/>'
            f'<path d="M 166 216 L 346 216" {_s(c, 22)}/>'
            f'<path d="M 256 208 L 256 64" {_s(c, 22)}/>'
            f'<path d="M 168 216 L 256 96" {_s(c, 22)}/>'
            f'<path d="M 344 216 L 256 96" {_s(c, 22)}/>')


def steam_mark(c):
    """Steam: the valve piston — a ring with the offset crank and rod."""
    return (f'<circle cx="256" cy="256" r="162" {_s(c, 34)}/>'
            f'<circle cx="298" cy="298" r="66" {_s(c, 30)}/>'
            f'<path d="M 150 138 L 244 232" {_s(c, 34)}/>'
            f'<circle cx="150" cy="138" r="20" {_f(c)}/>')


def deezer_columns(c):
    """Deezer's heart waveform in separated round-ended strokes, not a solid heart."""
    bars = ((84, 204, 246), (128, 144, 300), (172, 112, 340),
            (216, 132, 384), (260, 184, 414), (304, 132, 384),
            (348, 112, 340), (392, 144, 300), (436, 204, 246))
    return "".join(f'<path d="M {x} {top} V {bottom}" {_s(c, 32)}/>'
                   for x, top, bottom in bars)


def soundcloud_cloud(c):
    """SoundCloud: the cloud with rising sound bars."""
    return (f'<path d="M 172 386 C 132 386 112 356 120 322 C 126 296 150 282 '
            f'172 286 C 180 240 222 218 262 232 C 298 244 318 292 318 316" '
            f'{_s(c, 32)}/>'
            f'<path d="M 318 386 L 318 316" {_s(c, 26)}/>'
            f'<path d="M 318 316 L 318 240" {_s(c, 22)}/>'
            f'<path d="M 352 386 L 352 268" {_s(c, 26)}/>'
            f'<path d="M 386 386 L 386 292" {_s(c, 26)}/>')


def iplayer_play(c):
    """iPlayer's three-beam play cue, pink and in the canonical rounded line weight."""
    return (f'<path d="M 100 160 V 384 M 208 84 L 424 208 '
            f'M 424 304 L 208 428" {_s(c, 32)}/>')


def tubi_wordmark(c):
    """Tubi: the lowercase wordmark drawn as monoline type.

    Tubi has no emblem — the identity is the rounded lowercase wordmark
    with a shortened t-bar. A bare T over a base invented a gate the brand
    never had, so the four letters are drawn at Outfit proportions on the
    pack grid: t, u, b, i, ascenders and baseline shared.
    """
    return (f'<path d="M 100 140 L 100 336" {_s(c, 34)}/>'
            f'<path d="M 68 208 L 132 208" {_s(c, 34)}/>'
            f'<path d="M 184 208 L 184 300 C 184 336 248 336 248 300 '
            f'L 248 208" {_s(c, 34)}/>'
            f'<path d="M 296 140 L 296 336" {_s(c, 34)}/>'
            f'<circle cx="336" cy="296" r="40" {_s(c, 34)}/>'
            f'<path d="M 436 208 L 436 336" {_s(c, 34)}/>'
            f'<path d="M 436 150 L 436 156" {_s(c, 34)}/>')


def justwatch_finder(c):
    """JustWatch: the streaming search - magnifier with a play inside.

    The play filled most of the lens, leaving a ring of background too thin to
    survive. A smaller play inside a larger lens keeps both shapes open.
    """
    return (f'<circle cx="228" cy="230" r="146" {_s(c, 34)}/>'
            f'<path d="M 200 180 L 200 280 L 286 230 Z" {_s(c, 28)}/>'
            f'<path d="M 336 338 L 424 426" {_s(c, 40)}/>')


def acorn_mark(c):
    """Acorn TV: the acorn — cap roundel over a tapered nut.

    Sat 39px high on the grid: the cap arc bulges to y=42, and the 32 stroke
    put ink at y=26 against a SAFE floor of 40. Shifted down to centre the
    mark; the construction is otherwise unchanged.
    """
    return (f'<path d="M 256 135 L 256 189" {_s(c, 28)}/>'
            f'<path d="M 148 189 L 364 189" {_s(c, 32)}/>'
            f'<path d="M 148 189 A 108 108 0 0 1 364 189" {_s(c, 30)}/>'
            f'<path d="M 176 271 C 176 339 216 395 256 431 '
            f'C 296 395 336 339 336 271 Z" {_s(c, 32)}/>')


def tidal_wave(c):
    """TIDAL: fidelity wave — three synced crests over a baseline."""
    return (f'<path d="M 88 240 C 150 180 200 180 256 240 C 312 300 362 300 424 240" '
            f'{_s(c, 32)}/>'
            f'<path d="M 88 336 C 150 276 200 276 256 336 C 312 396 362 396 424 336" '
            f'{_s(c, 32)}/>')


def hulu_mark(c):
    """Hulu: the 'ulu' — an H tilted into a flowing slab."""
    return (f'<path d="M 148 140 L 148 372" {_s(c, 46)}/>'
            f'<path d="M 364 140 L 364 372" {_s(c, 46)}/>'
            f'<path d="M 148 256 L 364 256" {_s(c, 44)}/>')


def max_wave(c):
    """Max: the Max wave — a bold falling wave, open at the tail."""
    return (f'<path d="M 132 176 C 176 132 244 132 286 176 C 328 220 328 292 '
            f'286 336 C 268 354 244 362 220 362 L 156 362" {_s(c, 40)}/>')


def mubi_mark(c):
    """Seven round outlines in MUBI's 2-3-2 arrangement, in the pack's linework.

    Radius 34 against a 32 stroke left an 18px counter - under two pixels on a
    Projectivy tile, so all seven filled in and the mark read as a solid block.

    Radius alone was not enough: presence.py dilates every edge by the keyline
    radius on the shipped PNG, so at the old 136px spacing the fattened rings
    bridged into one shape. Widening the spacing to 146 keeps the seven dots
    seven separate components after the keyline, with a 30px counter each.
    """
    points = ((110, 110), (256, 110), (110, 256), (256, 256),
              (402, 256), (110, 402), (256, 402))
    return "".join(f'<circle cx="{x}" cy="{y}" r="46" {_s(c, 32)}/>'
                   for x, y in points)


def pandora_halo(c):
    """Pandora: the 'P' pearl — a P in a halo ring."""
    return (f'<circle cx="256" cy="256" r="178" {_s(c, 30)}/>'
            f'<path d="M 202 372 L 202 150 L 268 150 C 316 150 340 176 340 210 '
            f'C 340 244 316 270 268 270 L 202 270" {_s(c, 46)}/>')


def vudu_mark(c):
    """Vudu: the streaming 'V' — a W built from a play wedge."""
    return (f'<path d="M 150 150 L 256 350 L 362 150" {_s(c, 42)}/>'
            f'<path d="M 256 150 L 256 350" {_s(c, 30)}/>')


def kanopy_mark(c):
    """Kanopy: the learning 'K' — upright stem with a book-spine wedge."""
    return (f'<path d="M 178 128 L 178 384" {_s(c, 44)}/>'
            f'<path d="M 178 256 L 340 128" {_s(c, 38)}/>'
            f'<path d="M 190 344 L 348 384" {_s(c, 30)}/>')


# --------------------------------------------------------------------------
# Contained monograms.
#
# A bare letter read as "unfinished"; a letter inside the shared tile reads as
# a designed mark. Every remaining letter-first icon routes through this so the
# pack is one family: tile outline + filled letter + a small accent notch.
# --------------------------------------------------------------------------
def monogram_tile(letter, color):
    lh = monogram_scaled(letter, color, cap_h=250)
    notch = f'<circle cx="392" cy="120" r="18" {_f(color)}/>'
    return _tile(color) + lh + notch


# letters A-Z and digits, each as a contained monogram tile
def _mk_tile(letter):
    return lambda c: monogram_tile(letter, c)


def two_char_tile(text, color):
    """Multi-character lockup (e.g. '10') inside the shared tile."""
    from typeface import monogram_text
    body = monogram_text(text, color)
    return _tile(color) + body


_tile_names = {}
for _ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789":
    _tile_names[f"tile_{_ch}"] = _mk_tile(_ch)
_tile_names["tile_10"] = lambda c: two_char_tile("10", c)

GLYPHS.update({
    "netflix_ribbon": netflix_ribbon,
    "crunchyroll_eye": crunchyroll_eye,
    "paramount_peak": paramount_peak,
    "peacock_fan": peacock_fan,
    "discovery_sunburst": discovery_sunburst,
    "steam_mark": steam_mark,
    "deezer_columns": deezer_columns,
    "soundcloud_cloud": soundcloud_cloud,
    "iplayer_play": iplayer_play,
    "tubi_wordmark": tubi_wordmark,
    "justwatch_finder": justwatch_finder,
    "acorn_mark": acorn_mark,
    "tidal_wave": tidal_wave,
    "hulu_mark": hulu_mark,
    "max_wave": max_wave,
    "mubi_mark": mubi_mark,
    "pandora_halo": pandora_halo,
    "vudu_mark": vudu_mark,
    "kanopy_mark": kanopy_mark,
})
GLYPHS.update(_tile_names)

# ==========================================================================
# Tier 1 (batch 2) — more signature marks for recognisable brands.
# ==========================================================================
def espn_e(c):
    """ESPN: the 'E' — a heavy round-ended E in a block, with a cut notch.

    Drawn as a single monoline E whose three arms spring from a common spine,
    giving the filled-block character of the ESPN mark while staying monoline.
    """
    return (f'<path d="M 186 140 L 186 372 M 186 140 L 342 140 M 186 256 '
            f'L 342 256 M 186 372 L 342 372" {_s(c, 40)}/>'
            f'<path d="M 300 150 L 300 244" {_s(c, 22)}/>')


def dazn_bars(c):
    """DAZN: the bold D — a D with a lightning spike on its stem."""
    return (f'<path d="M 168 128 L 168 384 L 268 384 C 356 384 396 326 396 256 '
            f'C 396 186 356 128 268 128 Z" {_s(c, 34)}/>'
            f'<path d="M 168 250 L 306 250" {_s(c, 30)}/>'
            f'<path d="M 168 128 L 236 128 L 236 180" {_s(c, 22)}/>')


def ufc_octagon(c):
    """UFC: the octagon — an eight-sided cage ring crossed by seam lines."""
    return (f'<path d="M 256 78 L 404 156 L 404 314 L 256 434 L 108 314 '
            f'L 108 156 Z" {_s(c, 30)}/>'
            f'<path d="M 108 314 L 404 314" {_s(c, 22)}/>'
            f'<path d="M 108 156 L 404 156" {_s(c, 22)}/>'
            f'<path d="M 256 78 L 256 434" {_s(c, 22)}/>')


def f1_wing(c):
    """F1 TV: the racing wing — a swept chevron with a ground line."""
    return (f'<path d="M 120 168 L 392 168 L 320 300 L 192 300 Z" {_s(c, 34)}/>'
            f'<path d="M 120 168 L 192 300" {_s(c, 26)}/>'
            f'<path d="M 392 168 L 320 300" {_s(c, 26)}/>'
            f'<path d="M 132 396 L 380 396" {_s(c, 30)}/>')


def britbox_mark(c):
    """BritBox: the Brit box with a Union-flag cross.

    A rounded square (the 'box') with a horizontal bar and a diagonal cross,
    evoking the Union-Jack motif of the BritBox mark.
    """
    return (f'<rect x="92" y="150" width="328" height="212" rx="44" {_s(c, 30)}/>'
            f'<path d="M 120 256 L 392 256" {_s(c, 22)}/>'
            f'<path d="M 120 150 L 392 362 M 392 150 L 120 362" {_s(c, 18)}/>')


def qobuz_note(c):
    """Qobuz: the fused hi-fi note — a note with a Q counter."""
    return (f'<path d="M 210 362 L 210 138 L 330 112 L 330 322" {_s(c, 34)}/>'
            f'<ellipse cx="166" cy="362" rx="50" ry="40" {_s(c, 30)}/>'
            f'<ellipse cx="286" cy="322" rx="50" ry="40" {_s(c, 30)}/>')


def curiosity_eye(c):
    """Curiosity Stream: the observatory — a dome over an arched frame."""
    return (f'<path d="M 148 320 A 108 108 0 0 1 364 320" {_s(c, 30)}/>'
            f'<path d="M 148 320 L 148 380 L 364 380 L 364 320" {_s(c, 30)}/>'
            f'<path d="M 256 212 L 256 320" {_s(c, 24)}/>'
            f'<circle cx="256" cy="212" r="16" {_f(c)}/>')


def kayo_bolt(c):
    """Kayo: the catch bolt — a K sheared into a lightning.""" 
    return (f'<path d="M 176 380 L 176 132" {_s(c, 42)}/>'
            f'<path d="M 176 256 L 336 132" {_s(c, 34)}/>'
            f'<path d="M 188 344 L 344 380" {_s(c, 28)}/>')


def stan_wave(c):
    """Stan: the Stan 'S' — a clean double-backed S sweep, open ends."""
    return (f'<path d="M 300 128 C 226 128 200 178 232 214 C 268 254 300 292 '
            f'268 356 C 248 394 206 400 176 380" {_s(c, 40)}/>')


def sbs_tile_word(c):
    """SBS (Australia): the Mercator splices, thinned to three.

    Five splices packed into the upper half left three sub-30px wedges that
    closed at tile size. Three crests over one sweep keeps the folded-globe
    read with counters that survive.
    """
    return (f'<path d="M 118 206 C 174 122 246 160 226 244" {_s(c, 26)}/>'
            f'<path d="M 242 196 C 296 128 362 158 350 240" {_s(c, 26)}/>'
            f'<path d="M 120 306 C 190 360 322 360 392 306" {_s(c, 26)}/>')


def binge_wave(c):
    """Binge: the play-wave — a bold play flagged with a ripple."""
    return (f'<path d="M 180 176 L 180 336 L 300 256 Z" {_s(c, 36)}/>'
            f'<path d="M 340 176 L 340 336" {_s(c, 30)}/>')


def foxsports_mark(c):
    """Fox Sports: the broadcast F — an F with a satellite sweep."""
    return (f'<path d="M 176 376 L 176 136" {_s(c, 44)}/>'
            f'<path d="M 176 250 L 320 250" {_s(c, 34)}/>'
            f'<path d="M 176 136 L 322 136" {_s(c, 34)}/>')


def iheart_mark(c):
    """iHeartRadio: the heart-pulse — a heart with a beat line."""
    return (f'<path d="M 256 392 C 132 314 132 190 214 158 C 256 142 288 166 '
            f'302 194 C 316 166 348 142 390 158 C 472 190 472 314 348 392 '
            f'C 310 414 288 398 256 380 C 224 398 202 414 216 392 Z" '
            f'{_s(c, 30)}/>'
            f'<path d="M 216 256 L 244 256 L 256 216 L 268 256 L 300 256" '
            f'{_s(c, 24)}/>')


GLYPHS.update({
    "espn_e": espn_e,
    "dazn_bars": dazn_bars,
    "ufc_octagon": ufc_octagon,
    "f1_wing": f1_wing,
    "britbox_mark": britbox_mark,
    "qobuz_note": qobuz_note,
    "curiosity_eye": curiosity_eye,
    "kayo_bolt": kayo_bolt,
    "stan_wave": stan_wave,
    "sbs_bars": sbs_tile_word,
    "binge_wave": binge_wave,
    "foxsports_mark": foxsports_mark,
    "iheart_mark": iheart_mark,
})

# ==========================================================================
# Tier 3 — another wave of recognisable signature marks.
# ==========================================================================
def instagram_camera(c):
    """Instagram: the camera - rounded body, lens, and a flash dot.

    Body, lens and inner lens were three concentric contours plus a solid
    flash, at 34 percent ink. Body and lens carry the camera; the flash is an
    outline so the glyph is stroke-only.
    """
    return (f'<rect x="96" y="122" width="320" height="300" rx="96" {_s(c, 34)}/>'
            f'<circle cx="256" cy="276" r="88" {_s(c, 32)}/>'
            f'<circle cx="348" cy="186" r="20" {_s(c, 22)}/>')


def amazon_smile(c):
    """Amazon Music: the signature smile arrow — a rising curve with an arrow."""
    return (f'<path d="M 120 320 C 200 396 330 396 400 300" {_s(c, 36)}/>'
            f'<path d="M 400 300 L 398 250 L 352 268" {_s(c, 24)}/>')


def sling_s(c):
    """Sling TV: the slanted S — one sheared S sweep, open ends."""
    return (f'<path d="M 286 128 C 216 128 196 176 226 214 C 268 262 306 300 '
            f'276 366 C 256 404 208 410 172 392" {_s(c, 42)}/>')


def pluto_echo(c):
    """Pluto TV: the disc with its planetary echo arcs.

    The 2020 lockup is a solid disc trailed by concentric echo crescents;
    the 2024 lockup keeps the disc and knocks the wordmark out of it. A
    lone orbit arc over a circle read as a generic planet, so the echo
    returns as two subordinate arcs hugging the disc's left rim, in the
    brand's yellow.
    """
    return (f'<circle cx="316" cy="256" r="132" {_s(c, 34)}/>'
            f'<path d="M 158 322 A 132 132 0 0 1 158 190" {_s(c, 26)}/>'
            f'<path d="M 110 322 A 132 132 0 0 1 110 190" {_s(c, 20)}/>')


def nvidia_eye(c):
    """GeForce Now / NVIDIA: the eye — almond, iris, pupil."""
    return (f'<path d="M 108 256 C 168 166 344 166 404 256 C 344 346 168 346 '
            f'108 256 Z" {_s(c, 32)}/>'
            f'<circle cx="256" cy="256" r="78" {_s(c, 28)}/>'
            f'<circle cx="256" cy="256" r="24" {_f(c)}/>')


def sky_swoosh(c):
    """Sky News: the broadcast cloud — a cloud with a skyline ray."""
    return (f'<path d="M 150 350 C 96 350 72 318 82 286 C 90 260 118 248 '
            f'144 252 C 152 196 206 170 254 184 C 296 196 314 236 310 268 '
            f'C 354 272 376 300 370 330 C 364 348 342 350 312 350 Z" '
            f'{_s(c, 32)}/>'
            f'<path d="M 256 184 L 256 110" {_s(c, 22)}/>')


def yt_music(c):
    """YouTube Music's disc and play.

    Three concentric contours around a play wedge put 39 percent ink on the
    tile and closed the inner ring. The outer disc plus the play is the mark.
    """
    return (f'<circle cx="256" cy="256" r="190" {_s(c, 32)}/>'
            f'<path d="M 212 176 L 212 336 L 344 256 Z" {_s(c, 30)}/>')


def wetv_w(c):
    """WeTV: the play-W — a W with a notch central peak."""
    return (f'<path d="M 150 160 L 210 360 L 256 250 L 302 360 L 362 160" '
            f'{_s(c, 40)}/>')


def iqiyi_q(c):
    """iQIYI: the green Q — a circle with Q tail and a dot."""
    return (f'<circle cx="256" cy="256" r="120" {_s(c, 34)}/>'
            f'<path d="M 334 334 L 402 400" {_s(c, 28)}/>'
            f'<circle cx="206" cy="150" r="16" {_f(c)}/>')


def viu_v(c):
    """Viu: the V — a bold V with a dot at its point."""
    return (f'<path d="M 158 150 L 256 350 L 354 150" {_s(c, 44)}/>'
            f'<circle cx="256" cy="404" r="16" {_f(c)}/>')


def redbull_sun(c):
    """Red Bull TV: two charging bulls in a sun.

    A yellow sun disc behind two simplified charging-bull profiles (horns and
    leaning bodies) — the two-bulls-into-the-sun device.
    """
    import math
    # The mark was authored about cy=240, 16px above the grid centre, which
    # put the topmost sun bar at y=30 — inside the 40px SAFE margin. Centred.
    out = f'<circle cx="256" cy="256" r="150" {_s(c, 22)}/>'
    # radiating sun bars
    for i in range(8):
        a = math.radians(-160 + i * 46)
        x1 = 256 + 168 * math.cos(a)
        y1 = 256 - 168 * math.sin(a)
        x2 = 256 + 212 * math.cos(a)
        y2 = 256 - 212 * math.sin(a)
        out += f'<path d="M {x1:.0f} {y1:.0f} L {x2:.0f} {y2:.0f}" {_s(c, 22)}/>'
    # left bull (lean head + horn)
    out += (f'<path d="M 150 316 C 160 266 180 242 214 234 C 190 260 190 302 '
            f'214 326 C 234 344 278 344 300 326" {_s(c, 24)}/>')
    # right bull (mirror)
    out += (f'<path d="M 362 316 C 352 266 332 242 298 234 C 322 260 322 302 '
            f'298 326 C 278 344 234 344 212 326" {_s(c, 24)}/>')
    return out


def c4_block(c):
    """Channel 4: the block 4 — a diagonal stem into a crossbar."""
    return (f'<path d="M 170 300 L 300 150 L 300 380" {_s(c, 40)}/>'
            f'<path d="M 190 300 L 300 300" {_s(c, 32)}/>')


def plexamp_mark(c):
    """Plexamp: the Plex chevron in a circle — the music Plex."""
    return (f'<circle cx="256" cy="256" r="178" {_s(c, 32)}/>'
            f'<path d="M 218 168 L 330 256 L 218 344" {_s(c, 38)}/>')


def nova_play(c):
    """Nova Video Player: a rounded play flag — a play over a horizontal bar."""
    return (f'<rect x="78" y="150" width="356" height="212" rx="60" {_s(c, 34)}/>'
            f'<path d="M 224 200 L 224 312 L 332 256 Z" {_s(c, 28)}/>')


def tunein_circle(c):
    """TuneIn Radio: the radio — dial circle with a tuning dot."""
    return (f'<circle cx="256" cy="256" r="178" {_s(c, 34)}/>'
            f'<path d="M 120 256 L 216 256" {_s(c, 26)}/>'
            f'<circle cx="256" cy="256" r="26" {_f(c)}/>')


GLYPHS.update({
    "instagram_camera": instagram_camera,
    "amazon_smile": amazon_smile,
    "sling_s": sling_s,
    "pluto_echo": pluto_echo,
    "nvidia_eye": nvidia_eye,
    "sky_swoosh": sky_swoosh,
    "yt_music": yt_music,
    "wetv_w": wetv_w,
    "iqiyi_q": iqiyi_q,
    "viu_v": viu_v,
    "redbull_sun": redbull_sun,
    "c4_block": c4_block,
    "plexamp_mark": plexamp_mark,
    "nova_play": nova_play,
    "tunein_circle": tunein_circle,
})

# ==========================================================================
# Tier 4 — sports, news, and more recognisable marks.
# ==========================================================================
def nbc_peacock(c):
    """NBC: the peacock — six feathers fanning off a circular head.

    Drawn as a clean fan: a centre pin-feather plus two angled pairs, each
    a short stem ending in a dot, all rising from a common base notch.
    """
    import math
    feathers = []
    # (angle from base, length, whether it gets a tip dot)
    tips = []
    for i, ang in enumerate([-52, -34, -17, 0, 17, 34, 52]):
        a = math.radians(-90 + ang)
        x1 = 256 + 30 * math.cos(a)
        y1 = 386 + 30 * math.sin(a)
        x2l = 256 + 120 * math.cos(a)
        y2l = 386 + 120 * math.sin(a)
        x2 = 256 + 150 * math.cos(a)
        y2 = 386 + 150 * math.sin(a)
        feathers.append(f'<path d="M {x1:.0f} {y1:.0f} L {x2l:.0f} {y2l:.0f}" '
                        f'{_s(c, 20)}/>')
        tips.append((x2, y2))
    for (tx, ty) in tips:
        feathers.append(f'<circle cx="{tx:.0f}" cy="{ty:.0f}" r="14" {_f(c)}/>')
    feathers.append(f'<path d="M 256 386 L 256 424" {_s(c, 26)}/>')
    return "".join(feathers)


def mlb_homeplate(c):
    """MLB: the rounded diamond with the batter silhouette."""
    return (f'<path d="M 256 92 L 380 262 L 256 432 L 132 262 Z" {_s(c, 30)}/>'
            f'<circle cx="256" cy="240" r="26" {_s(c, 20)}/>'
            f'<path d="M 256 268 L 256 372" {_s(c, 20)}/>'
            f'<path d="M 230 372 L 282 372" {_s(c, 20)}/>')


def nfl_ball(c):
    """NFL: the shield with a ball at its heart.

    Two lace rules ran across the top of the shield and closed the band they
    sat in. The ball carries the mark without them.
    """
    return (f'<path d="M 256 88 L 392 146 L 392 268 C 392 356 338 408 256 430 '
            f'C 174 408 120 356 120 268 L 120 146 Z" {_s(c, 30)}/>'
            f'<ellipse cx="256" cy="262" rx="80" ry="52" {_s(c, 24)}/>')


def nba_ball(c):
    """NBA: the crest with a player at its heart.

    The seam line bisected a 42px circle and closed both halves. The circle
    alone, larger, keeps the crest reading as a crest.
    """
    return (f'<path d="M 176 148 L 336 148 L 336 292 L 256 424 L 176 292 Z" '
            f'{_s(c, 30)}/>'
            f'<circle cx="256" cy="250" r="56" {_s(c, 22)}/>')


def foxnews_mark(c):
    """Fox News: the network K — an upright stem with a flag spar."""
    return (f'<path d="M 178 120 L 178 392" {_s(c, 42)}/>'
            f'<path d="M 178 250 L 350 120" {_s(c, 36)}/>'
            f'<path d="M 190 340 L 342 392" {_s(c, 30)}/>')


def cbs_eye(c):
    """CBS: the eye — an almond lens with dot pupil."""
    return (f'<path d="M 110 256 C 170 168 342 168 402 256 C 342 344 170 344 '
            f'110 256 Z" {_s(c, 34)}/>'
            f'<circle cx="256" cy="256" r="42" {_f(c)}/>')


def sky_glass(c):
    """Sky+ / Sky: the rounded glass — a channel block marked N/E."""
    return (f'<path d="M 110 130 L 340 130 C 372 130 396 154 396 186 '
            f'L 396 260 L 110 260 Z" {_s(c, 34)}/>'
            f'<path d="M 110 260 L 396 260 L 396 330 C 396 362 372 386 '
            f'340 386 L 110 386 Z" {_s(c, 34)}/>')


def fox_network(c):
    """Foxtel / Fox: the fox head — a pointed muzzle with two ears."""
    return (f'<path d="M 256 140 L 366 84 L 330 210" {_s(c, 30)}/>'
            f'<path d="M 256 140 L 146 84 L 182 210" {_s(c, 30)}/>'
            f'<path d="M 146 230 C 146 340 200 400 256 400 C 312 400 366 340 '
            f'366 230" {_s(c, 30)}/>'
            f'<path d="M 256 360 L 256 416" {_s(c, 24)}/>')


def rakuten_r(c):
    """Rakuten TV: the R play block — an R over a play notch."""
    return (f'<path d="M 176 150 L 176 380" {_s(c, 38)}/>'
            f'<path d="M 176 258 L 330 150" {_s(c, 32)}/>'
            f'<path d="M 176 258 L 232 258" {_s(c, 24)}/>'
            f'<path d="M 176 380 L 288 380" {_s(c, 30)}/>')


def starz_mark(c):
    """STARZ: the starburst — a star with a burst of rays."""
    return (f'<path d="M 256 96 L 300 206 L 416 206 L 322 292 L 356 408 '
            f'L 256 332 L 156 408 L 190 292 L 96 206 L 212 206 Z" {_s(c, 30)}/>')


def shudder_mark(c):
    """Shudder: the goosebump S — a throat S with a ruffle."""
    return (f'<path d="M 282 128 C 218 128 198 176 228 214 C 268 262 306 300 '
            f'276 366 C 256 406 200 408 170 386" {_s(c, 40)}/>')


def sonyliv_mark(c):
    """SonyLIV: the LIV play — an L with a flag leaf."""
    return (f'<path d="M 170 150 L 170 386" {_s(c, 40)}/>'
            f'<path d="M 170 386 L 330 386" {_s(c, 34)}/>'
            f'<path d="M 170 250 L 344 150" {_s(c, 30)}/>')


GLYPHS.update({
    "nbc_peacock": nbc_peacock,
    "mlb_homeplate": mlb_homeplate,
    "nfl_ball": nfl_ball,
    "nba_ball": nba_ball,
    "foxnews_mark": foxnews_mark,
    "cbs_eye": cbs_eye,
    "sky_glass": sky_glass,
    "fox_network": fox_network,
    "rakuten_r": rakuten_r,
    "starz_mark": starz_mark,
    "shudder_mark": shudder_mark,
    "sonyliv_mark": sonyliv_mark,
})

# ==========================================================================
# Tier 5 — more recognizable streaming/media marks.
# ==========================================================================
def fubo_mark(c):
    """Fubo: the fubo f — a bold f with a play wedge."""
    return (f'<path d="M 196 388 L 196 132 L 320 132" {_s(c, 40)}/>'
            f'<path d="M 196 256 L 316 256" {_s(c, 32)}/>'
            f'<path d="M 316 256 L 316 132" {_s(c, 24)}/>')


def showmax_eye(c):
    """Showmax: the eye — a lens over a play triangle."""
    return (f'<path d="M 112 256 C 172 168 340 168 400 256 C 340 344 172 344 '
            f'112 256 Z" {_s(c, 34)}/>'
            f'<path d="M 224 216 L 224 296 L 296 256 Z" {_f(c)}/>')


def nebula_dot(c):
    """Nebula: a world inside its ring.

    The filled core sat inside two concentric contours and closed the space
    between them. Dropping it leaves the ring crossing the disc, which is the
    part that reads as an orbit.
    """
    return (f'<circle cx="256" cy="256" r="128" {_s(c, 32)}/>'
            f'<ellipse cx="256" cy="256" rx="192" ry="74" {_s(c, 26)}/>')


def roku_house(c):
    """The Roku Channel: the Roku house — a peaked house with a door."""
    return (f'<path d="M 120 230 L 256 120 L 392 230" {_s(c, 34)}/>'
            f'<path d="M 150 230 L 150 386 L 362 386 L 362 230" {_s(c, 34)}/>'
            f'<rect x="222" y="286" width="68" height="100" {_s(c, 28)}/>')


def amc_a(c):
    """AMC: the AMC A — an A with a film-strip crossbar."""
    return (f'<path d="M 150 380 L 256 120 L 362 380" {_s(c, 38)}/>'
            f'<path d="M 196 296 L 316 296" {_s(c, 30)}/>'
            f'<rect x="222" y="176" width="68" height="36" rx="10" {_s(c, 22)}/>')


def history_h(c):
    """HISTORY: the block H — an upright H with a serifed top."""
    return (f'<path d="M 178 120 L 178 388" {_s(c, 44)}/>'
            f'<path d="M 334 120 L 334 388" {_s(c, 44)}/>'
            f'<path d="M 178 256 L 334 256" {_s(c, 40)}/>'
            f'<path d="M 178 120 L 334 120" {_s(c, 24)}/>')


def itv_hub(c):
    """ITV Hub: the ITV gate — a play wedge over a letterform block."""
    return (f'<path d="M 128 140 L 128 372" {_s(c, 40)}/>'
            f'<path d="M 128 256 L 384 122" {_s(c, 30)}/>'
            f'<path d="M 128 316 L 304 388" {_s(c, 26)}/>')


def abcnews_mark(c):
    """ABC News: the broadcast A — an A with a wave."""
    return (f'<path d="M 150 380 L 256 110 L 362 380" {_s(c, 40)}/>'
            f'<path d="M 200 292 L 312 292" {_s(c, 30)}/>')


def skyline_mark(c):
    """Skyshowtime: a rounded skyline — showtime block with a flag."""
    return (f'<path d="M 118 386 L 118 240 M 178 386 L 178 160 M 238 386 '
            f'L 238 300 M 298 386 L 298 190 M 358 386 L 358 260" {_s(c, 30)}/>')


def comedy_mark(c):
    """Comedy: the laugh — a smile with a wink."""
    return (f'<path d="M 150 300 C 190 362 322 362 362 300" {_s(c, 34)}/>'
            f'<circle cx="200" cy="220" r="16" {_f(c)}/>'
            f'<circle cx="312" cy="220" r="16" {_f(c)}/>')


GLYPHS.update({
    "fubo_mark": fubo_mark,
    "showmax_eye": showmax_eye,
    "nebula_dot": nebula_dot,
    "roku_house": roku_house,
    "amc_a": amc_a,
    "history_h": history_h,
    "itv_hub": itv_hub,
    "abcnews_mark": abcnews_mark,
    "skyline_mark": skyline_mark,
    "comedy_mark": comedy_mark,
})

# ==========================================================================
# Tier 6 — VPN, browsers, files and gaming marks.
# ==========================================================================
def nordvpn_arrow(c):
    """NordVPN's mountain/dome, not a generic shield; all ink is monoline."""
    return (f'<path d="M 112 364 A 180 180 0 1 1 400 364" {_s(c, 32)}/>'
            f'<path d="M 112 364 L 220 202 L 258 266 L 286 166 L 400 364" {_s(c, 32)}/>')


def proton_shield(c):
    """Proton VPN's folded triangle, as one contour plus one fold.

    The old inner fold traced most of the outer contour a stroke's width
    inside it, which is the classic way to lose a counter: eleven of thirteen
    closed at tile size. The fold now crosses the interior instead of
    shadowing the edge, so there is one large opening either side of it.
    """
    return (f'<path d="M 104 118 L 408 156 Q 436 160 418 192 '
            f'L 258 410 Q 248 428 234 406 L 90 150 Q 78 124 104 118 Z" {_s(c, 32)}/>'
            f'<path d="M 168 208 L 330 230" {_s(c, 24)}/>')


def expressvpn_mark(c):
    """ExpressVPN: the key-lock — a rounded shield with a keyhole."""
    return (f'<path d="M 226 92 L 286 92 L 286 150 C 340 170 380 210 380 268 '
            f'C 380 350 324 410 256 428 C 188 410 132 350 132 268 '
            f'C 132 210 172 170 226 150 Z" {_s(c, 30)}/>'
            f'<circle cx="256" cy="262" r="52" {_s(c, 22)}/>'
            f'<path d="M 256 314 L 256 384" {_s(c, 22)}/>')


def wireguard_mark(c):
    """WireGuard: the wave-key.

    The old construction doubled back on itself three times and carried a
    filled core, which closed the loops it was meant to sit inside. Two clean
    waves and an open eye say the same thing and survive the downscale.
    """
    return (f'<circle cx="256" cy="256" r="58" {_s(c, 32)}/>'
            f'<path d="M 96 168 C 150 112 206 224 260 168 C 314 112 370 224 '
            f'424 168" {_s(c, 26)}/>'
            f'<path d="M 96 366 C 150 310 206 422 260 366 C 314 310 370 422 '
            f'424 366" {_s(c, 26)}/>')


def mullvad_shield(c):
    """Mullvad: the bird head.

    The head sat inside a rounded tile, and the band between the two closed at
    tile size. Dropping the tile lets the head fill the safe area, which is
    also how the brand mark is actually used.
    """
    return (f'<path d="M 134 348 C 106 244 166 168 256 168 C 346 168 406 244 '
            f'378 348 C 348 424 164 424 134 348 Z" {_s(c, 30)}/>'
            f'<path d="M 134 348 L 92 372" {_s(c, 24)}/>'
            f'<circle cx="256" cy="256" r="26" {_s(c, 24)}/>')


def dropbox_boxes(c):
    """Dropbox: the open diamond boxes."""
    return (f'<path d="M 160 140 L 256 196 L 160 252 L 64 196 Z" {_s(c, 30)}/>'
            f'<path d="M 352 140 L 448 196 L 352 252 L 256 196 Z" {_s(c, 30)}/>'
            f'<path d="M 160 252 L 256 308 L 352 252 L 256 196 Z" {_s(c, 28)}/>'
            f'<path d="M 160 340 L 256 396 L 352 340" {_s(c, 28)}/>')


def dolfin_mark(c):
    """Dolphin: the leaping dolphin."""
    return (f'<path d="M 120 300 C 160 220 240 180 320 210 C 380 232 410 290 '
            f'382 330 C 350 374 300 376 270 342 C 228 292 160 296 150 350" '
            f'{_s(c, 34)}/>'
            f'<path d="M 150 350 L 160 392" {_s(c, 28)}/>')


def pacman_mark(c):
    """Pac-Man: the chomp — a circle with a wedge mouth opening right."""
    return (f'<path d="M 256 108 A 148 148 0 1 0 256 404 A 148 148 0 0 0 '
            f'236 162 L 336 256 L 236 350 A 148 148 0 0 0 256 108 Z" '
            f'{_s(c, 32)}/>')


def retroarch_mark(c):
    """RetroArch: the game pad — a controller with a d-pad.

    Drawn to a 406-wide body, not 480: at the old width the 26.2 stroke put
    ink at x=2.9 and x=509.1 on a 512 grid, 3px of margin where SAFE promises
    40. Uniformly rescaled about the grid centre so the pad keeps its
    proportions and the stroke keeps its monoline weight.
    """
    return (f'<path d="M 150 210 C 150 167 200 150 234 176 L 276 210 C 290 221 '
            f'319 221 332 210 L 374 176 C 408 150 459 167 459 210 '
            f'C 459 267 437 345 395 355 C 366 362 344 328 337 302 '
            f'C 327 281 307 269 256 269 C 205 269 185 281 175 302 '
            f'C 168 328 146 362 117 355 C 75 345 53 267 53 210 Z" {_s(c, 28)}/>')


def sideload_mark(c):
    """Sideload: the package — a box with an up arrow."""
    return (f'<path d="M 128 132 L 384 132 L 384 388 L 128 388 Z" {_s(c, 30)}/>'
            f'<path d="M 128 132 L 256 212 L 384 132" {_s(c, 24)}/>'
            f'<path d="M 256 380 L 256 268" {_s(c, 26)}/>'
            f'<path d="M 302 300 L 256 254 L 210 300" {_s(c, 26)}/>')


GLYPHS.update({
    "nordvpn_arrow": nordvpn_arrow,
    "proton_shield": proton_shield,
    "expressvpn_mark": expressvpn_mark,
    "wireguard_mark": wireguard_mark,
    "mullvad_shield": mullvad_shield,
    "dropbox_boxes": dropbox_boxes,
    "dolfin_mark": dolfin_mark,
    "pacman_mark": pacman_mark,
    "retroarch_mark": retroarch_mark,
    "sideload_mark": sideload_mark,
})

# ==========================================================================
# Tier 7 — music and tool marks.
# ==========================================================================
def sirius_satellite(c):
    """Sirius: the satellite — a dish with radiating orbit.

    Shifted 20px left. The outer wave arc reached x=476, which the 26.2
    stroke pushed to 489 — outside SAFE — while the dish left only 88px of
    margin on the other side. The diagonal composition is unchanged.
    """
    return (f'<path d="M 84 396 C 84 300 160 224 256 224" {_s(c, 32)}/>'
            f'<circle cx="108" cy="372" r="26" {_f(c)}/>'
            f'<path d="M 280 160 C 340 160 400 220 400 280" {_s(c, 26)}/>'
            f'<path d="M 280 104 C 368 104 456 192 456 280" {_s(c, 26)}/>')


def podcast_mic(c):
    """Podcast Addict: the microphone — a rounded mic on a stand."""
    return (f'<rect x="200" y="110" width="112" height="196" rx="56" {_s(c, 32)}/>'
            f'<path d="M 156 256 C 156 310 200 350 256 350 C 312 350 356 310 '
            f'356 256" {_s(c, 30)}/>'
            f'<path d="M 256 350 L 256 410" {_s(c, 28)}/>'
            f'<path d="M 200 410 L 312 410" {_s(c, 28)}/>')


def termux_mark(c):
    """Termux: the terminal — a prompt block with an angled caret."""
    return (f'<path d="M 116 170 L 268 246 L 116 322" {_s(c, 34)}/>'
            f'<path d="M 292 322 L 396 322" {_s(c, 30)}/>')


def speedtest_gauge(c):
    """Speedtest TV: the gauge — a dial with a needle."""
    return (f'<path d="M 116 348 A 160 160 0 0 1 396 348" {_s(c, 32)}/>'
            f'<path d="M 152 162 L 256 348" {_s(c, 22)}/>'
            f'<path d="M 360 162 L 256 348" {_s(c, 22)}/>'
            f'<circle cx="256" cy="348" r="20" {_f(c)}/>')


def adb_robot(c):
    """ADB: the android — a head with antennae and eyes."""
    return (f'<path d="M 166 160 L 346 160 L 346 300 C 346 348 300 380 256 380 '
            f'C 212 380 166 348 166 300 Z" {_s(c, 32)}/>'
            f'<path d="M 200 140 L 176 100" {_s(c, 24)}/>'
            f'<path d="M 312 140 L 336 100" {_s(c, 24)}/>'
            f'<circle cx="216" cy="236" r="18" {_f(c)}/>'
            f'<circle cx="296" cy="236" r="18" {_f(c)}/>')


def aosp_robot(c):
    """AOSP / Android: the full android head."""
    return (f'<path d="M 150 176 L 362 176 L 362 300 C 362 356 314 396 256 396 '
            f'C 198 396 150 356 150 300 Z" {_s(c, 34)}/>'
            f'<path d="M 150 198 L 96 156" {_s(c, 26)}/>'
            f'<path d="M 362 198 L 416 156" {_s(c, 26)}/>'
            f'<circle cx="216" cy="246" r="22" {_f(c)}/>'
            f'<circle cx="296" cy="246" r="22" {_f(c)}/>')


def easter_island(c):
    """Easter Island: an alternate adb robot head."""
    return (f'<path d="M 160 176 L 352 176 L 352 300 C 352 356 308 392 256 392 '
            f'C 204 392 160 356 160 300 Z" {_s(c, 32)}/>'
            f'<circle cx="218" cy="244" r="20" {_f(c)}/>'
            f'<circle cx="294" cy="244" r="20" {_f(c)}/>')


GLYPHS.update({
    "sirius_satellite": sirius_satellite,
    "podcast_mic": podcast_mic,
    "termux_mark": termux_mark,
    "speedtest_gauge": speedtest_gauge,
    "adb_robot": adb_robot,
    "aosp_robot": aosp_robot,
})

# ==========================================================================
# Tier 8 — more recognizable global brand marks.
# ==========================================================================
def vimeo_mark(c):
    """Vimeo: the V-play — a sheared V with a play flyaway."""
    return (f'<path d="M 158 150 L 256 306 L 354 150" {_s(c, 42)}/>')


def duckdg_egg(c):
    """DuckDuckGo: the duck head inside a rounded square.

    A bean/egg-shaped duck head with a bill notch and an eye, in the brand's
    simple flat-line style.
    """
    return (f'<rect x="86" y="96" width="340" height="340" rx="96" {_s(c, 30)}/>'
            f'<path d="M 150 300 C 130 220 180 176 250 184 C 336 186 330 270 '
            f'300 300 C 262 322 196 322 150 300 Z" {_s(c, 26)}/>'
            f'<path d="M 300 300 L 344 316 C 352 320 348 332 336 330 L 302 322" '
            f'{_s(c, 20)}/>'
            f'<circle cx="250" cy="250" r="16" {_f(c)}/>')


def gt_tv(c):
    """Google TV: the play-on-a-screen — a screen with a play wedge."""
    return (f'<rect x="76" y="130" width="360" height="220" rx="40" {_s(c, 32)}/>'
            f'<path d="M 222 180 L 222 300 L 330 240 Z" {_s(c, 30)}/>'
            f'<path d="M 180 400 L 332 400" {_s(c, 28)}/>')


def tnt_mark(c):
    """TNT: the block TNT — a bold T over a crash bar."""
    return (f'<path d="M 160 128 L 352 128" {_s(c, 40)}/>'
            f'<path d="M 256 128 L 256 388" {_s(c, 40)}/>')


def rumble_mark(c):
    """Rumble: the fist bolt — a vertical bolt with a notched direction."""
    return (f'<path d="M 178 150 L 178 380" {_s(c, 42)}/>'
            f'<path d="M 178 256 L 330 150" {_s(c, 34)}/>'
            f'<path d="M 178 256 L 262 256" {_s(c, 28)}/>')


def dailymotion_mark(c):
    """Dailymotion: the d — the ascender is on the right, the bowl opens left,
    and a dot sits in the counter, so it clearly reads as a lowercase d."""
    return (f'<path d="M 302 388 L 302 128" {_s(c, 38)}/>'
            f'<path d="M 302 388 C 302 300 268 258 216 258 C 164 258 134 298 '
            f'134 322 C 134 360 164 388 212 388 Z" {_s(c, 32)}/>')


def ted_mark(c):
    """TED: the block TED — stacked letters on a frame."""
    return (f'<path d="M 160 128 L 352 128 L 256 388 Z" {_s(c, 34)}/>'
            f'<path d="M 160 128 L 352 128" {_s(c, 28)}/>')


def nasa_mark(c):
    """NASA: the meatball orbit — a wing over a globe."""
    return (f'<path d="M 148 210 C 216 140 360 150 396 220" {_s(c, 28)}/>'
            f'<circle cx="250" cy="286" r="96" {_s(c, 30)}/>'
            f'<path d="M 250 190 L 250 382" {_s(c, 20)}/>'
            f'<path d="M 154 286 L 346 286" {_s(c, 20)}/>')


def parsec_mark(c):
    """Parsec: the arrow-bolt — a forward chevron with a tail."""
    return (f'<path d="M 150 150 L 300 250 L 150 350" {_s(c, 38)}/>'
            f'<path d="M 300 150 L 300 350" {_s(c, 30)}/>')


def kick_mark(c):
    """Kick: the kick bolt — a lightning bolt."""
    return (f'<path d="M 300 90 L 200 270 L 268 270 L 212 422 L 344 230 '
            f'L 276 230 Z" {_s(c, 32)}/>')


def sofascore_mark(c):
    """SofaScore: the score board — a box with two clean score bars."""
    return (f'<rect x="92" y="176" width="328" height="192" rx="46" {_s(c, 34)}/>'
            f'<path d="M 172 316 L 172 228 L 212 272 L 256 228 L 256 316" '
            f'{_s(c, 22)}/>'
            f'<path d="M 292 316 L 292 228 L 340 316" {_s(c, 22)}/>')


def wondery_mark(c):
    """Wondery: the wonder wave — a chunky double-chevron wave."""
    return (f'<path d="M 116 276 L 190 178 L 256 276 L 322 178 L 396 276" '
            f'{_s(c, 40)}/>')


GLYPHS.update({
    "vimeo_mark": vimeo_mark,
    "duckdg_egg": duckdg_egg,
    "gt_tv": gt_tv,
    "tnt_mark": tnt_mark,
    "rumble_mark": rumble_mark,
    "dailymotion_mark": dailymotion_mark,
    "ted_mark": ted_mark,
    "nasa_mark": nasa_mark,
    "parsec_mark": parsec_mark,
    "kick_mark": kick_mark,
    "sofascore_mark": sofascore_mark,
    "wondery_mark": wondery_mark,
})

# ==========================================================================
# Tier 9 — IPTV players, browsers and more regional brands.
# ==========================================================================
def iptv_smarters(c):
    """IPTV Smarters: the smart tv — a tv with a play and signal."""
    return (f'<rect x="70" y="120" width="372" height="240" rx="40" {_s(c, 32)}/>'
            f'<path d="M 222 168 L 222 312 L 340 240 Z" {_s(c, 28)}/>'
            f'<path d="M 170 400 L 342 400" {_s(c, 28)}/>')


def ott_navigator(c):
    """OTT Navigator: the compass nav — a compass with a needle."""
    return (f'<circle cx="256" cy="256" r="156" {_s(c, 32)}/>'
            f'<path d="M 256 130 L 300 256 L 256 382" {_s(c, 26)}/>'
            f'<path d="M 256 130 L 212 256 L 256 382" {_s(c, 26)}/>')


def molotov_mark(c):
    """Molotov: the flame — a flame with a base."""
    return (f'<path d="M 256 96 C 320 176 350 250 340 320 C 334 372 296 404 256 404 '
            f'C 216 404 178 372 172 320 C 162 250 192 176 256 96 Z" {_s(c, 30)}/>')


def megogo_mark(c):
    """MEGOGO: the film play — a film strip with a play."""
    return (f'<rect x="86" y="150" width="340" height="212" rx="36" {_s(c, 32)}/>'
            f'<path d="M 222 200 L 222 312 L 330 256 Z" {_s(c, 28)}/>')


def shahid_mark(c):
    """Shahid: the viewing eye — a lens with a notch."""
    return (f'<path d="M 116 256 C 176 170 336 170 396 256 C 336 342 176 342 '
            f'116 256 Z" {_s(c, 32)}/>'
            f'<circle cx="256" cy="256" r="40" {_s(c, 22)}/>')


def browser_globe2(c):
    """Puffin / browsers: a globe with an orbit."""
    return (f'<circle cx="256" cy="256" r="150" {_s(c, 30)}/>'
            f'<path d="M 256 106 L 256 406" {_s(c, 20)}/>'
            f'<path d="M 106 256 L 406 256" {_s(c, 20)}/>'
            f'<ellipse cx="256" cy="256" rx="150" ry="56" {_s(c, 20)}/>')


def globoplay_mark(c):
    """Globoplay: the G-globe — a globe with a G."""
    return (f'<circle cx="256" cy="256" r="150" {_s(c, 30)}/>'
            f'<path d="M 300 176 C 200 160 150 240 180 300 C 204 352 300 352 '
            f'316 306" {_s(c, 28)}/>')


def megogo_zone(c):
    """Vidio / Viet: a play zone — a rounded play banner."""
    return (f'<rect x="86" y="150" width="340" height="212" rx="64" {_s(c, 32)}/>'
            f'<path d="M 222 200 L 222 312 L 330 256 Z" {_s(c, 28)}/>')


def zee5_mark(c):
    """ZEE5: the Z play — a Z with a play notch."""
    return (f'<path d="M 150 150 L 362 150 L 150 362 L 362 362" {_s(c, 40)}/>')


def youku_mark(c):
    """Youku: the play circle — a circle with a bold play."""
    return (f'<circle cx="256" cy="256" r="156" {_s(c, 30)}/>'
            f'<path d="M 222 196 L 222 316 L 322 256 Z" {_s(c, 30)}/>')


def newpipe_mark(c):
    """NewPipe: the pipe — a Tube silhouette with a corner cut."""
    return (f'<path d="M 130 120 L 382 120 C 424 120 452 148 452 190 '
            f'L 452 322 C 452 364 424 392 382 392 L 130 392 C 88 392 60 364 '
            f'60 322 L 60 190 C 60 148 88 120 130 120 Z" {_s(c, 30)}/>'
            f'<path d="M 206 198 L 206 314 L 310 256 Z" {_s(c, 26)}/>')


GLYPHS.update({
    "iptv_smarters": iptv_smarters,
    "ott_navigator": ott_navigator,
    "molotov_mark": molotov_mark,
    "megogo_mark": megogo_mark,
    "shahid_mark": shahid_mark,
    "browser_globe2": browser_globe2,
    "globoplay_mark": globoplay_mark,
    "megogo_zone": megogo_zone,
    "zee5_mark": zee5_mark,
    "youku_mark": youku_mark,
    "newpipe_mark": newpipe_mark,
})

# ==========================================================================
# Tier 10 — news / cable / sports networks.
# ==========================================================================
def cnn_mark(c):
    """CNN: the network — a bold flag bar over a square."""
    return (f'<path d="M 118 120 L 118 392" {_s(c, 40)}/>'
            f'<path d="M 118 256 L 394 256" {_s(c, 34)}/>'
            f'<path d="M 118 120 L 180 120" {_s(c, 26)}/>'
            f'<path d="M 118 392 L 180 392" {_s(c, 26)}/>')


def tbs_mark(c):
    """TBS: the swoosh — a T with a curved accent."""
    return (f'<path d="M 150 140 L 362 140" {_s(c, 38)}/>'
            f'<path d="M 256 140 L 256 388" {_s(c, 38)}/>')


def syfy_mark(c):
    """SYFY: the sci-fi slash — two diagonal slabs."""
    return (f'<path d="M 150 380 L 330 132" {_s(c, 40)}/>'
            f'<path d="M 256 380 L 386 208" {_s(c, 28)}/>')


def usanet_mark(c):
    """USA Network: the USA shield — a rounded shield with an S."""
    return (f'<path d="M 256 90 L 392 148 L 392 268 C 392 354 336 404 256 428 '
            f'C 176 404 120 354 120 268 L 120 148 Z" {_s(c, 30)}/>'
            f'<path d="M 286 180 C 220 180 200 230 230 266 C 268 310 296 340 '
            f'266 376" {_s(c, 30)}/>')


def hallmark_mark(c):
    """Hallmark: the crown — a crown with three points."""
    return (f'<path d="M 140 150 L 140 340 M 256 150 L 256 340 M 372 150 '
            f'L 372 340" {_s(c, 30)}/>'
            f'<path d="M 140 150 L 200 210 L 256 150 L 312 210 L 372 150" '
            f'{_s(c, 30)}/>')


def sportsnet_mark(c):
    """Sportsnet: the S-net — an S over a net grid."""
    return (f'<path d="M 286 128 C 220 128 200 178 230 214 C 268 254 300 292 '
            f'270 356" {_s(c, 40)}/>'
            f'<path d="M 150 300 L 362 300 M 150 340 L 362 340" {_s(c, 20)}/>')


def nhl_mark(c):
    """NHL: the puck - an ellipse puck with a star notch.

    The notch was 48px wide under a 22 stroke and closed. A larger diamond
    keeps the notch legible above the puck.
    """
    return (f'<ellipse cx="256" cy="288" rx="150" ry="88" {_s(c, 32)}/>'
            f'<path d="M 256 112 L 300 156 L 256 200 L 212 156 Z" {_s(c, 22)}/>')


def tsn_mark(c):
    """TSN: the score — a bold boxed T."""
    return (f'<rect x="110" y="150" width="292" height="212" rx="40" {_s(c, 30)}/>'
            f'<path d="M 168 190 L 344 190" {_s(c, 32)}/>'
            f'<path d="M 256 190 L 256 322" {_s(c, 32)}/>')


GLYPHS.update({
    "cnn_mark": cnn_mark,
    "tbs_mark": tbs_mark,
    "syfy_mark": syfy_mark,
    "usanet_mark": usanet_mark,
    "hallmark_mark": hallmark_mark,
    "sportsnet_mark": sportsnet_mark,
    "nhl_mark": nhl_mark,
    "tsn_mark": tsn_mark,
})

# ==========================================================================
# Tier 11 — more streaming brands.
# ==========================================================================
def mgm_mark(c):
    """MGM+: the film-reel M — a block M over a rewind bar."""
    return (f'<path d="M 170 300 L 170 180 L 256 260 L 342 180 L 342 300" '
            f'{_s(c, 36)}/>')


def criterion_mark(c):
    """Criterion: the C disc — a bold C with a dot counter."""
    return (f'<circle cx="256" cy="256" r="150" {_s(c, 30)}/>'
            f'<path d="M 322 180 C 220 150 150 240 180 312 C 204 366 288 380 '
            f'330 338" {_s(c, 30)}/>')


def dropout_mark(c):
    """Dropout: the D gap — a D with a play cut."""
    return (f'<path d="M 136 336 L 136 176 L 220 176 C 280 176 312 210 312 256 '
            f'C 312 302 280 336 220 336 Z" {_s(c, 34)}/>'
            f'<path d="M 200 212 L 200 300 L 272 256 Z" {_s(c, 24)}/>')


def pbs_mark(c):
    """PBS: the P head — a P with the plate face on the right."""
    return (f'<path d="M 190 366 L 190 148 L 268 148 C 320 148 348 178 348 226 '
            f'C 348 274 320 304 268 304 L 190 304" {_s(c, 38)}/>')


def fite_mark(c):
    """FITE: the fight belt — a centred buckle strip on a belt."""
    return (f'<path d="M 130 220 L 382 220" {_s(c, 30)}/>'
            f'<path d="M 130 300 L 382 300" {_s(c, 30)}/>'
            f'<path d="M 216 220 L 216 300 L 296 300 L 296 220" {_s(c, 26)}/>')


def kocowa_mark(c):
    """Kocowa: the play gate — a rounded play with a base."""
    return (f'<path d="M 210 170 L 210 342 L 330 256 Z" {_s(c, 34)}/>')


GLYPHS.update({
    "mgm_mark": mgm_mark,
    "criterion_mark": criterion_mark,
    "dropout_mark": dropout_mark,
    "pbs_mark": pbs_mark,
    "fite_mark": fite_mark,
    "kocowa_mark": kocowa_mark,
})

# ==========================================================================
# Tier 12 — misc streaming / content marks.
# ==========================================================================
def xumo_mark(c):
    """Xumo: the X play — an X woven from a play."""
    return (f'<path d="M 150 150 L 362 362 M 362 150 L 150 362" {_s(c, 38)}/>')


def philo_mark(c):
    """Philo: the play wave — a play in a circle with a wave."""
    return (f'<circle cx="256" cy="256" r="150" {_s(c, 30)}/>'
            f'<path d="M 224 200 L 224 312 L 322 256 Z" {_s(c, 26)}/>')


def hdstreamz_mark(c):
    """HD Streamz: the stream bolt — three stream lines."""
    return (f'<path d="M 150 150 C 240 210 272 210 362 150" {_s(c, 28)}/>'
            f'<path d="M 150 256 C 240 316 272 316 362 256" {_s(c, 28)}/>'
            f'<path d="M 150 362 C 240 422 272 422 362 362" {_s(c, 28)}/>')


GLYPHS.update({
    "xumo_mark": xumo_mark,
    "philo_mark": philo_mark,
    "hdstreamz_mark": hdstreamz_mark,
})

# ==========================================================================
# Tier 13 — more VPNs, app stores, and streaming.
# ==========================================================================
def ipvanish_mark(c):
    """IPVanish: the vanish — a shield with a fast-forward."""
    return (f'<path d="M 256 92 L 384 148 L 384 268 C 384 352 334 400 256 424 '
            f'C 178 400 128 352 128 268 L 128 148 Z" {_s(c, 32)}/>'
            f'<path d="M 206 200 L 206 312 L 250 256 Z" {_s(c, 26)}/>'
            f'<path d="M 262 200 L 262 312 L 306 256 Z" {_s(c, 26)}/>')


def surfshark_mark(c):
    """Surfshark: the shark fin — a curved fin over a wave."""
    return (f'<path d="M 150 340 C 150 220 200 150 256 150 C 200 180 220 250 '
            f'280 250 C 330 250 356 290 356 340 Z" {_s(c, 30)}/>'
            f'<path d="M 120 340 L 392 340" {_s(c, 28)}/>')


def cyberghost_mark(c):
    """CyberGhost: the ghost — a rounded ghost with arms."""
    return (f'<path d="M 176 180 L 336 180 L 336 300 C 336 360 300 396 256 396 '
            f'C 212 396 176 360 176 300 Z" {_s(c, 32)}/>'
            f'<path d="M 176 216 L 150 200 M 336 216 L 362 200" {_s(c, 20)}/>'
            f'<circle cx="216" cy="250" r="14" {_f(c)}/>'
            f'<circle cx="296" cy="250" r="14" {_f(c)}/>')


def windscribe_mark(c):
    """Windscribe: the wind — three sweeping lines."""
    return (f'<path d="M 150 160 C 260 120 320 130 362 170" {_s(c, 30)}/>'
            f'<path d="M 110 240 C 230 200 300 210 356 250" {_s(c, 30)}/>'
            f'<path d="M 150 320 C 250 280 310 288 362 328" {_s(c, 30)}/>')


def apkpure_d(c):
    """APKPure: the pure bag — a shopping bag with a play."""
    return (f'<path d="M 118 176 L 394 176 L 372 404 L 140 404 Z" {_s(c, 32)}/>'
            f'<path d="M 196 228 L 196 150 C 196 110 226 86 256 86 '
            f'C 286 86 316 110 316 150 L 316 228" {_s(c, 28)}/>')


def apkmirror_mark(c):
    """APKMirror: the mirror box — a box with an R."""
    return (f'<rect x="96" y="130" width="320" height="260" rx="40" {_s(c, 32)}/>'
            f'<path d="M 210 340 L 210 214 L 256 214 C 300 214 316 286 316 320" '
            f'{_s(c, 28)}/>')


def rustore_mark(c):
    """RuStore: the store tiles.

    The lower bar sat 32px under the upper tiles with a 26 stroke between
    them, so the channel closed. Wider gaps, same arrangement.
    """
    return (f'<rect x="104" y="132" width="138" height="138" rx="26" {_s(c, 28)}/>'
            f'<rect x="278" y="132" width="138" height="138" rx="26" {_s(c, 28)}/>'
            f'<rect x="104" y="316" width="312" height="88" rx="26" {_s(c, 26)}/>')


def obtainium_mark(c):
    """Obtainium: the gear-box — a box with a cog."""
    return (f'<rect x="110" y="150" width="292" height="212" rx="36" {_s(c, 30)}/>'
            f'<circle cx="256" cy="256" r="40" {_s(c, 24)}/>')


def iflix_mark(c):
    """iflix: the play ribbon.

    The wedge was 112px across a 34 stroke, so its single counter shut at tile
    size. A larger wedge keeps an open triangle.
    """
    return (f'<path d="M 176 160 L 176 352 L 336 256 Z" {_s(c, 34)}/>'
            f'<path d="M 176 352 L 356 406" {_s(c, 26)}/>')


GLYPHS.update({
    "ipvanish_mark": ipvanish_mark,
    "surfshark_mark": surfshark_mark,
    "cyberghost_mark": cyberghost_mark,
    "windscribe_mark": windscribe_mark,
    "apkpure_d": apkpure_d,
    "apkmirror_mark": apkmirror_mark,
    "rustore_mark": rustore_mark,
    "obtainium_mark": obtainium_mark,
    "iflix_mark": iflix_mark,
})

# ==========================================================================
# Tier 14 — more sport and misc marks.
# ==========================================================================
def peacock_check(c):
    """Peacock-style fan for sports wraps (helper, not used directly)."""
    return _tile(c)


def motogp_swoosh(c):
    """MotoGP: the speed swoosh — a tire with a speed arc."""
    return (f'<circle cx="256" cy="296" r="110" {_s(c, 30)}/>'
            f'<path d="M 120 150 C 200 120 320 120 400 150" {_s(c, 26)}/>')
 

def laliga_mark(c):
    """LaLiga: the L — a bold slab L with a ball notch."""
    return (f'<path d="M 196 128 L 196 384 L 196 384" {_s(c, 46)}/>'
            f'<path d="M 196 384 L 366 384" {_s(c, 34)}/>'
            f'<circle cx="300" cy="200" r="26" {_s(c, 22)}/>')


def uefa_star(c):
    """UEFA: the trophy star — a star over a base."""
    return (f'<path d="M 256 110 L 288 190 L 374 190 L 306 242 L 330 326 '
            f'L 256 276 L 182 326 L 206 242 L 138 190 L 224 190 Z" {_s(c, 28)}/>')


def tennis_mark(c):
    """Tennis Channel: the ball — a circle with curved seams."""
    return (f'<circle cx="256" cy="256" r="140" {_s(c, 30)}/>'
            f'<path d="M 150 200 C 210 230 210 290 150 320" {_s(c, 24)}/>'
            f'<path d="M 362 200 C 302 230 302 290 362 320" {_s(c, 24)}/>')


def flosports_mark(c):
    """FloSports: the F whip — an F with a whip tail."""
    return (f'<path d="M 176 368 L 176 128 L 340 128" {_s(c, 40)}/>'
            f'<path d="M 176 250 L 330 250" {_s(c, 32)}/>')


def premier_mark(c):
    """Premier Sports: the P shield — a P in a shield."""
    return (f'<path d="M 256 92 L 386 148 L 386 268 C 386 352 336 402 256 426 '
            f'C 176 402 126 352 126 268 L 126 148 Z" {_s(c, 30)}/>'
            f'<path d="M 214 356 L 214 200 L 268 200 C 304 200 322 222 322 250 '
            f'C 322 278 304 300 268 300 L 214 300" {_s(c, 30)}/>')


def bally_mark(c):
    """Bally: the B double-bowl — a B with two rounded bowls."""
    return (f'<path d="M 186 128 L 186 384" {_s(c, 42)}/>'
            f'<path d="M 186 128 C 250 128 286 156 286 206 C 286 256 250 284 186 284 '
            f'C 250 284 300 314 300 366 C 300 384 280 384 236 384 L 186 384" '
            f'{_s(c, 34)}/>')


def gotham_mark(c):
    """Gotham: the bat win — a W with bat wings."""
    return (f'<path d="M 120 240 C 160 180 200 180 240 240 C 250 250 262 250 '
            f'272 240 C 312 180 352 180 392 240" {_s(c, 32)}/>')


GLYPHS.update({
    "motogp_swoosh": motogp_swoosh,
    "laliga_mark": laliga_mark,
    "uefa_star": uefa_star,
    "tennis_mark": tennis_mark,
    "flosports_mark": flosports_mark,
    "premier_mark": premier_mark,
    "bally_mark": bally_mark,
    "gotham_mark": gotham_mark,
})

# ==========================================================================
# Tier 15 — remaining recognizable brand marks.
# ==========================================================================
def gplay_games(c):
    """Google Play Games: the play controller — a gamepad in G."""
    return (f'<circle cx="256" cy="256" r="150" {_s(c, 30)}/>'
            f'<path d="M 190 196 C 190 168 232 168 232 196 L 232 316 '
            f'C 232 344 190 344 190 316 Z" {_s(c, 26)}/>')


def google_tv(c):
    """Google TV: the play screen — a screen with rounded corners."""
    return (f'<rect x="70" y="130" width="372" height="220" rx="44" {_s(c, 32)}/>'
            f'<path d="M 222 182 L 222 300 L 330 240 Z" {_s(c, 28)}/>')


def yt_kids(c):
    """Slanted Kids button/play, with the same stroke and no extra cartoon appendage."""
    return (f'<path d="M 104 142 L 370 104 Q 412 100 418 142 '
            f'L 444 328 Q 450 370 408 380 L 150 414 Q 108 420 102 378 '
            f'L 76 194 Q 70 154 104 142 Z" {_s(c, 32)}/>'
            f'<path d="M 216 206 L 330 254 L 232 320 Z" {_s(c, 32)}/>')


def adguard_shield(c):
    """AdGuard: the shield with a pin."""
    return (f'<path d="M 256 90 L 390 148 L 390 268 C 390 356 336 406 256 430 '
            f'C 176 406 122 356 122 268 L 122 148 Z" {_s(c, 32)}/>'
            f'<circle cx="256" cy="270" r="64" {_s(c, 26)}/>'
            f'<path d="M 256 270 L 256 344" {_s(c, 22)}/>')


def directv_arrow(c):
    """DIRECTV: the satellite sign — an upward satellite beam."""
    return (f'<path d="M 256 92 L 374 200 L 256 300 L 138 200 Z" {_s(c, 30)}/>'
            f'<path d="M 256 300 L 374 404 L 256 420 L 138 404 Z" {_s(c, 26)}/>')


def dish_mark(c):
    """Dish Anywhere: the dish — a dish antenna with a beam."""
    return (f'<path d="M 120 380 C 120 300 180 230 260 220" {_s(c, 32)}/>'
            f'<circle cx="140" cy="360" r="24" {_f(c)}/>'
            f'<path d="M 290 170 C 350 180 396 240 396 300" {_s(c, 24)}/>')


def hoopla_mark(c):
    """Hoopla: the hoop — a circle over a bounce."""
    return (f'<circle cx="256" cy="210" r="104" {_s(c, 30)}/>'
            f'<path d="M 150 360 C 200 420 312 420 362 360" {_s(c, 28)}/>')


def ncbc_mark(c):
    """NBC News: the peacock note."""
    return nbc_peacock(c)


def tailscale_mark(c):
    """Tailscale: the hex — a hexagon with a wave."""
    return (f'<path d="M 256 106 L 380 168 L 380 344 L 256 406 L 132 344 '
            f'L 132 168 Z" {_s(c, 30)}/>'
            f'<path d="M 160 256 C 200 220 240 292 296 256 C 332 232 352 240 372 256" '
            f'{_s(c, 26)}/>')


def norton_mark(c):
    """Norton: the check — a shield with a check."""
    return (f'<path d="M 256 92 L 386 148 L 386 268 C 386 352 336 402 256 426 '
            f'C 176 402 126 352 126 268 L 126 148 Z" {_s(c, 32)}/>'
            f'<path d="M 192 266 L 236 310 L 330 214" {_s(c, 30)}/>')


def openvpn_mark(c):
    """OpenVPN: the lock — a lock with a key notch."""
    return (f'<rect x="120" y="200" width="272" height="200" rx="44" {_s(c, 30)}/>'
            f'<path d="M 190 200 L 190 150 C 190 110 220 90 256 90 '
            f'C 292 90 322 110 322 150 L 322 200" {_s(c, 30)}/>')


GLYPHS.update({
    "gplay_games": gplay_games,
    "google_tv": google_tv,
    "yt_kids": yt_kids,
    "adguard_shield": adguard_shield,
    "directv_arrow": directv_arrow,
    "dish_mark": dish_mark,
    "hoopla_mark": hoopla_mark,
    "tailscale_mark": tailscale_mark,
    "norton_mark": norton_mark,
    "openvpn_mark": openvpn_mark,
    "ncbc_mark": ncbc_mark,
})

# ==========================================================================
# Tier 16 — more streaming/player marks.
# ==========================================================================
def moviesanywhere_mark(c):
    """Movies Anywhere: the four-step — a stack of play tiles."""
    return (f'<rect x="100" y="120" width="140" height="140" rx="26" {_s(c, 28)}/>'
            f'<rect x="272" y="120" width="140" height="140" rx="26" {_s(c, 28)}/>'
            f'<rect x="100" y="292" width="140" height="140" rx="26" {_s(c, 28)}/>'
            f'<rect x="272" y="292" width="140" height="140" rx="26" {_s(c, 28)}/>')


def mxplayer_mark(c):
    """MX Player: the play arrow — a bold play on a flag."""
    return (f'<path d="M 170 180 L 170 332 L 292 256 Z" {_s(c, 40)}/>')


def boosteroid_mark(c):
    """Boosteroid: the cloud drop — a cloud on a drop."""
    return (f'<path d="M 150 330 C 96 330 70 296 80 260 C 88 232 118 218 146 222 '
            f'C 154 170 204 146 250 160 C 292 172 310 216 306 250 '
            f'C 350 254 372 288 366 318 C 360 336 334 330 306 330 Z" {_s(c, 30)}/>')


def a_e_mark(c):
    """A&E: the ampersand — a stylised A&E."""
    return (f'<path d="M 170 340 L 256 130 L 342 340" {_s(c, 36)}/>'
            f'<path d="M 210 280 L 302 280" {_s(c, 28)}/>')


def lifetime_mark(c):
    """Lifetime: the heart-l — an L with a heart."""
    return (f'<path d="M 200 140 L 200 356 L 332 356" {_s(c, 38)}/>'
            f'<circle cx="320" cy="200" r="20" {_f(c)}/>')


def pureflix_mark(c):
    """Pure Flix: the cross play — a play in a cross."""
    return (f'<path d="M 210 180 L 210 332 L 330 256 Z" {_s(c, 34)}/>'
            f'<path d="M 256 120 L 256 392" {_s(c, 24)}/>')


def filmrise_mark(c):
    """FilmRise: the rising film — a film strip rising."""
    return (f'<path d="M 150 330 L 250 300 L 200 260 L 300 220 L 250 180 '
            f'L 350 140" {_s(c, 30)}/>')


def videoland_mark(c):
    """Videoland: the play land — a play over a base."""
    return (f'<path d="M 210 180 L 210 330 L 320 255 Z" {_s(c, 34)}/>'
            f'<path d="M 140 360 L 372 360" {_s(c, 28)}/>')


GLYPHS.update({
    "moviesanywhere_mark": moviesanywhere_mark,
    "mxplayer_mark": mxplayer_mark,
    "boosteroid_mark": boosteroid_mark,
    "a_e_mark": a_e_mark,
    "lifetime_mark": lifetime_mark,
    "pureflix_mark": pureflix_mark,
    "filmrise_mark": filmrise_mark,
    "videoland_mark": videoland_mark,
})

# ==========================================================================
# Tier 17 — files, launchers and tools.
# ==========================================================================
def esfile_mark(c):
    """ES File Explorer: the folder with a file."""
    return (f'<path d="M 130 160 L 220 160 L 250 200 L 382 200 L 382 380 '
            f'L 130 380 Z" {_s(c, 30)}/>'
            f'<path d="M 130 160 L 130 380" {_s(c, 22)}/>')


def kde_mark(c):
    """KDE Connect: the smile-device — a device with a smile."""
    return (f'<rect x="120" y="150" width="272" height="212" rx="40" {_s(c, 30)}/>'
            f'<path d="M 180 150 L 180 110" {_s(c, 22)}/>'
            f'<path d="M 180 260 C 210 300 302 300 332 260" {_s(c, 28)}/>')


def flauncher_mark(c):
    """FLauncher: the F-card launcher — an F on a card."""
    return (f'<rect x="120" y="130" width="272" height="252" rx="36" {_s(c, 28)}/>'
            f'<path d="M 200 320 L 200 168 L 312 168" {_s(c, 34)}/>'
            f'<path d="M 200 250 L 296 250" {_s(c, 28)}/>')


def gamelauncher_mark(c):
    """Game Launcher: the box — an open game box."""
    return (f'<path d="M 140 160 L 140 380 L 372 380 L 372 160" {_s(c, 30)}/>'
            f'<path d="M 140 160 L 256 120 L 372 160" {_s(c, 26)}/>'
            f'<path d="M 256 120 L 256 220" {_s(c, 26)}/>')


def mitv_mark(c):
    """Mi TV Plus: a rounded screen with the Mi bars.

    Bars were 46px apart carrying a 26px stroke, so the gaps were 20px and
    closed at tile size. Three bars at 88px centres keep a 62px channel.
    """
    return (f'<rect x="80" y="140" width="352" height="228" rx="44" {_s(c, 30)}/>'
            f'<path d="M 168 316 L 168 192 M 256 316 L 256 192 M 344 316 L 344 192" '
            f'{_s(c, 26)}/>')


def iptv_player(c):
    """IPTV player: a rounded screen with a play."""
    return (f'<rect x="70" y="130" width="372" height="230" rx="44" {_s(c, 32)}/>'
            f'<path d="M 224 184 L 224 306 L 332 245 Z" {_s(c, 28)}/>')


GLYPHS.update({
    "esfile_mark": esfile_mark,
    "kde_mark": kde_mark,
    "flauncher_mark": flauncher_mark,
    "gamelauncher_mark": gamelauncher_mark,
    "mitv_mark": mitv_mark,
    "iptv_player": iptv_player,
})


# ==========================================================================
# Core family — bespoke marks for the three sibling apps.
#
# All share the brand hex (§02 point-up) as the outer frame and use the same
# accent cyan. Inner geometry differentiates each product's function.
# ==========================================================================
def coreline_ticker(c):
    """Core Line: a scrolling ticker strip inside the brand hex.

    The hex frame contains two horizontal crawl lines, suggesting the
    chyron/lower-third that defines the app. A small dot-pair at the left
    edge implies the live bug.
    """
    return (
        f'<polygon points="{_hexpts(256, 256, 196)}" {_s(c, 34)}/>'
        f'<path d="M 140 236 L 372 236" {_s(c, 28)}/>'
        f'<path d="M 140 280 L 340 280" {_s(c, 24)}/>'
        f'<circle cx="156" cy="236" r="10" {_f(c)}/>'
    )


def coreshift_frames(c):
    """Core Shift: two offset frames inside the brand hexagon.

    The frames overlapped, and the sliver where they crossed closed at tile
    size. Offsetting them so they touch corner to corner keeps the shift read
    and leaves both openings whole.
    """
    return (
        f'<polygon points="{_hexpts(256, 256, 198)}" {_s(c, 34)}/>'
        f'<rect x="150" y="166" width="128" height="104" rx="18" {_s(c, 26)}/>'
        f'<rect x="234" y="246" width="128" height="104" rx="18" {_s(c, 26)}/>'
    )


def coredoctor_pulse(c):
    """Core Doctor: a diagnostic pulse line inside the brand hex.

    The hex frame contains a heartbeat-style pulse waveform — a flat
    baseline with a sharp spike — suggesting health monitoring.
    """
    return (
        f'<polygon points="{_hexpts(256, 256, 196)}" {_s(c, 34)}/>'
        f'<path d="M 140 264 L 196 264 L 224 196 L 256 332 '
        f'L 284 220 L 308 264 L 372 264" {_s(c, 28)}/>'
    )


def coreeq_faders(c):
    """Core EQ: three equaliser faders inside the brand hex.

    Three vertical tracks, each with a knob at a different height - the
    correction curve as a mixing desk sets it. Tracks sit 80 apart so the
    knobs never touch at tile size.
    """
    return (
        f'<polygon points="{_hexpts(256, 256, 196)}" {_s(c, 34)}/>'
        f'<path d="M 176 176 V 336 M 256 176 V 336 M 336 176 V 336" {_s(c, 20)}/>'
        f'<path d="M 150 292 H 202 M 230 212 H 282 M 310 262 H 362" {_s(c, 30)}/>'
    )


def maxplayer_tri(c):
    """MaxPlayer: the rounded play triangle from its store tile, redrawn.

    The launcher icon is one soft-cornered play triangle on a red-to-pink
    tile (catalog gradient), so the mark is exactly that outline in Core
    monoline geometry - corner arcs authored, not traced - with nothing
    else: no circle, no box, no letter.
    """
    return (f'<path d="M 172 117.8 L 339.8 214.4 A 48 48 0 0 1 339.8 297.6 '
            f'L 172 394.2 A 48 48 0 0 1 148 380.3 L 148 131.7 '
            f'A 48 48 0 0 1 172 117.8 Z" {_s(c, 32)}/>')


GLYPHS.update({
    "coreline_ticker": coreline_ticker,
    "coreshift_frames": coreshift_frames,
    "coredoctor_pulse": coredoctor_pulse,
    "coreeq_faders": coreeq_faders,
    "maxplayer_tri": maxplayer_tri,
})


# NoBuffr uses an observed cue from the APK, not a pasted white vendor wordmark.
# Keep the full name in the same Outfit-labelled banner as every neighbouring app.
def nobuffr_mark(c):
    """Lowercase 'no' + interrupted buffer line, authored in Core Builds linework."""
    return (f'<path d="M 104 292 V 148 M 104 208 '
            f'C 104 128 232 128 232 208 V 292" {_s(c, 32)}/>'
            f'<ellipse cx="352" cy="220" rx="64" ry="80" {_s(c, 32)}/>'
            f'<path d="M 104 360 V 380 M 142 360 V 380 M 180 360 V 380" {_s(c, 20)}/>'
            f'<path d="M 232 370 H 416" {_s(c, 26)}/>')


GLYPHS["nobuffr_mark"] = nobuffr_mark


def wholphin_arc(c):
    """Wholphin: whale-back arc and an eye. Original, not a vendor mark.

    The eye was radius 16 under a 21.8 stroke - a five-pixel counter that
    closed into a dot. Radius 28 keeps it an eye.
    """
    return (
        f'<path d="M 88 300 C 120 168 200 120 256 120 '
        f'C 360 120 430 200 440 312" {_s(c, 32)}/>'
        f'<path d="M 88 300 C 150 372 220 400 300 392 '
        f'C 360 386 400 350 428 312" {_s(c, 26.2)}/>'
        f'<circle cx="350" cy="212" r="28" {_s(c, 21.8)}/>'
    )


def stream_window(c):
    """Tanasi Streamflix fork: flow bars and a play wedge. Not the reborn F."""
    return (
        f'<path d="M 112 176 H 268" {_s(c, 32)}/>'
        f'<path d="M 112 256 H 236" {_s(c, 32)}/>'
        f'<path d="M 112 336 H 200" {_s(c, 32)}/>'
        f'<path d="M 300 176 L 300 336 L 424 256 Z" {_s(c, 32)}/>'
    )


GLYPHS["wholphin_arc"] = wholphin_arc
GLYPHS["stream_window"] = stream_window


# ==========================================================================
# Recognisable brand marks — researched emblems, drawn in Core Builds linework.
#
# Cues researched in docs/logo-research/ICON_LOGO_RESEARCH.md and
# docs/research/iconpack-design-upgrade-2026-09.md. Each mark keeps the pack's
# contract: rounded monoline strokes, one accent, transparent interiors, no
# vendor silhouette or wordmark. CBC Gem's "exploding pizza" and CNBC's
# peacock are public emblems; Al Jazeera's flame and France 24's cyan square
# are the cues the logo audit flagged as missing from the letter tiles.
# ==========================================================================

def _arc(c, cx, cy, r, a0, a1, w):
    """A stroked circular arc segment centred on (cx, cy)."""
    import math
    a0, a1 = math.radians(a0), math.radians(a1)
    x0, y0 = cx + r * math.cos(a0), cy + r * math.sin(a0)
    x1, y1 = cx + r * math.cos(a1), cy + r * math.sin(a1)
    large = 1 if (a1 - a0) > math.pi else 0
    return (f'<path d="M {x0:.1f} {y0:.1f} A {r:.1f} {r:.1f} 0 {large} 1 '
            f'{x1:.1f} {y1:.1f}" {_s(c, w)}/>')


def aljazeera_flame(c):
    """Al Jazeera: gold rounded square + the calligraphic flame/teardrop.

    The logo audit records the mark as an orange square with a flowing
    stylised flame/water-drop. Interpreted as one open teardrop with an inner
    curl, in the pack's single accent — not an imported calligraphic trace.
    """
    return (f'<rect x="88" y="88" width="336" height="336" rx="80" {_s(c, 30)}/>'
            f'<path d="M 256 150 C 204 214 192 260 214 300 '
            f'C 230 328 282 328 298 300 '
            f'C 320 260 308 214 256 150 Z" {_s(c, 28)}/>'
            f'<path d="M 256 200 C 238 234 232 258 238 278" {_s(c, 20)}/>')


def france24_mark(c):
    """France 24: the round-the-clock '24', drawn large enough to stay a '24'.

    The enclosing rounded square was the problem, not the digits. It added a
    fourth concentric contour around numerals whose own counters were already
    the smallest shapes in the mark, and at tile size twelve of sixteen
    counters closed - the icon read as a filled square. The container carried
    no information the digits did not, so it is gone and the numerals take the
    whole safe area.
    """
    from typeface import monogram_outline
    return monogram_outline("24", c, cap_h=300, weight=32, max_width=392)


def cbc_gem(c):
    """CBC Gem: the 'exploding pizza' — a ring with arches and semi-arches.

    Burton Kramer's 1974 mark: a wide-open ring with arches, semi-arches and
    smaller fragments radiating around it. Rendered as concentric stroked arcs
    of graduated weight, one accent.
    """
    import math
    out = f'<circle cx="256" cy="256" r="96" {_s(c, 30)}/>'
    for centre in (0, 90, 180, 270):
        out += _arc(c, 256, 256, 162, centre - 30, centre + 30, 26)
    for centre in (45, 135, 225, 315):
        out += _arc(c, 256, 256, 130, centre - 21, centre + 21, 20)
    return out


def cnbc_peacock(c):
    """CNBC: the NBC peacock fan — six feathers over a stem, stroke-only.

    CNBC's brand is the peacock (shared with NBCUniversal). A clean fan of
    six tapering strokes reads as the peacock without the filled dot tips the
    standalone peacock_fan uses, so this mark stays inside the monoline
    contract.
    """
    import math
    out = ''
    for i in range(6):
        a = math.radians(-168 + i * 26)
        bx, by = 256 + 20 * math.cos(a), 424 + 20 * math.sin(a)
        tx, ty = 256 + 174 * math.cos(a), 424 + 174 * math.sin(a)
        w = 32 if i % 2 == 0 else 26
        out += f'<path d="M {bx:.1f} {by:.1f} L {tx:.1f} {ty:.1f}" {_s(c, w)}/>'
    out += f'<path d="M 256 404 L 256 454" {_s(c, 28)}/>'
    return out


def mgm_reel(c):
    """MGM+: a film reel - ring, sprocket perforations and hub.

    MGM+ has no standalone 'M' emblem; the brand's recognisable device is the
    lion in a film-reel ring, and the audit asks for the reel as the shared
    cue. Eight perforations at radius 13 under a 21.8 stroke left a two-pixel
    counter each and all eight closed - the reel became a dotted ring of
    blobs. Five larger perforations keep the reel read and survive.
    """
    import math
    out = (f'<circle cx="256" cy="256" r="164" {_s(c, 30)}/>'
           f'<circle cx="256" cy="256" r="54" {_s(c, 26)}/>')
    for i in range(5):
        a = math.radians(i * 72 - 90)
        x, y = 256 + 110 * math.cos(a), 256 + 110 * math.sin(a)
        out += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="26" {_s(c, 18)}/>'
    return out


GLYPHS.update({
    "aljazeera_flame": aljazeera_flame,
    "france24_mark": france24_mark,
    "cbc_gem": cbc_gem,
    "cnbc_peacock": cnbc_peacock,
    "mgm_reel": mgm_reel,
})


# ==========================================================================
# Ecosystem marks.
#
# 608 of 926 icons fell back to a letter tile, and because accents come from
# a cycled palette that produced 429 icons byte-identical to another icon. A
# letter carries no app identity, so the ones a Core Builds user actually has
# on their home screen - debrid clients, players, launchers, file and network
# tools - are drawn here instead.
#
# Utilities are drawn by what they DO. Nobody recognises "Dr Nettools" by its
# logo, but a network tool reads as a node graph with a pulse, and that is the
# cue that survives a 48px tile. Where a brand does have a device (Arrow's
# arrow, Streamyfin's fin) the device wins.
#
# Stroke-only throughout, so every mark here satisfies core_monoline.
# ==========================================================================

def _browser_frame(c, w=32):
    """Shared chrome: a screen with a title bar. The browsers differ below it."""
    return (f'<rect x="64" y="104" width="384" height="304" rx="48" {_s(c, w)}/>'
            f'<path d="M 64 186 L 448 186" {_s(c, 24)}/>')


def coji_browser(c):
    """Coji TV Browser - the browser with a face; 'coji' is the emoji tell."""
    return (_browser_frame(c)
            + f'<circle cx="200" cy="272" r="22" {_s(c, 22)}/>'
            + f'<circle cx="312" cy="272" r="22" {_s(c, 22)}/>'
            + f'<path d="M 186 330 C 216 366 296 366 326 330" {_s(c, 26)}/>')


def tv_browser_bar(c):
    """TV Browser - the address bar is the app."""
    return (_browser_frame(c)
            + f'<rect x="112" y="240" width="288" height="76" rx="38" {_s(c, 26)}/>'
            + f'<path d="M 168 278 L 300 278" {_s(c, 20)}/>')


def tv_web_browser(c):
    """TV Web Browser - a page with lines of text under the chrome."""
    return (_browser_frame(c)
            + f'<path d="M 128 254 L 384 254" {_s(c, 24)}/>'
            + f'<path d="M 128 318 L 384 318" {_s(c, 24)}/>'
            + f'<path d="M 128 372 L 280 372" {_s(c, 24)}/>')


def vewd_browser(c):
    """Vewd - the forward chevron inside the chrome."""
    return (_browser_frame(c)
            + f'<path d="M 206 240 L 296 300 L 206 360" {_s(c, 30)}/>')


def debrid_cloud_play(c):
    """Debrid Stream - the cloud that plays. Unrestricting, then streaming."""
    return (f'<path d="M 158 348 C 104 348 70 310 70 264 C 70 218 108 184 '
            f'154 188 C 168 136 216 100 272 100 C 340 100 392 152 394 218 '
            f'C 428 228 448 258 448 292 C 448 324 424 348 388 348 Z" {_s(c, 32)}/>'
            f'<path d="M 216 388 L 216 456 L 296 422 Z" {_s(c, 26)}/>')


def debrid_cloud_link(c):
    """Real-Debrid - the cloud plus the link it unrestricts."""
    return (f'<path d="M 158 322 C 104 322 70 284 70 238 C 70 192 108 158 '
            f'154 162 C 168 110 216 74 272 74 C 340 74 392 126 394 192 '
            f'C 428 202 448 232 448 266 C 448 298 424 322 388 322 Z" {_s(c, 32)}/>'
            f'<path d="M 196 420 L 162 420 C 130 420 104 400 104 372 '
            f'C 104 344 130 324 162 324" {_s(c, 26)}/>'
            f'<path d="M 316 420 L 350 420 C 382 420 408 400 408 372 '
            f'C 408 344 382 324 350 324" {_s(c, 26)}/>'
            f'<path d="M 206 372 L 306 372" {_s(c, 24)}/>')


def _folder(c, w=32):
    return (f'<path d="M 68 156 L 210 156 L 248 206 L 444 206 L 444 386 '
            f'C 444 408 428 424 406 424 L 106 424 C 84 424 68 408 68 386 Z" '
            f'{_s(c, w)}/>')


def folder_tree(c):
    """Anexplorer - the file tree."""
    return (f'<path d="M 96 118 L 96 372 C 96 392 112 406 132 406 L 210 406" '
            f'{_s(c, 28)}/>'
            f'<path d="M 96 262 L 210 262" {_s(c, 24)}/>'
            f'<rect x="226" y="88" width="196" height="76" rx="24" {_s(c, 30)}/>'
            f'<rect x="226" y="224" width="196" height="76" rx="24" {_s(c, 30)}/>'
            f'<rect x="226" y="368" width="196" height="76" rx="24" {_s(c, 30)}/>')


def folder_sync_one(c):
    """FileSynced - a folder under a sync arc."""
    return (_folder(c)
            + f'<path d="M 176 320 C 176 276 214 244 258 244 C 296 244 328 '
              f'266 340 298" {_s(c, 24)}/>'
            + f'<path d="M 300 298 L 344 298 L 344 254" {_s(c, 22)}/>')


def folder_sync_two(c):
    """Folder Sync - two folders and the round trip between them."""
    return (f'<path d="M 56 118 L 148 118 L 176 154 L 250 154 L 250 258 '
            f'C 250 274 238 286 222 286 L 84 286 C 68 286 56 274 56 258 Z" '
            f'{_s(c, 30)}/>'
            f'<path d="M 262 226 L 354 226 L 382 262 L 456 262 L 456 366 '
            f'C 456 382 444 394 428 394 L 290 394 C 274 394 262 382 262 366 Z" '
            f'{_s(c, 30)}/>'
            f'<path d="M 108 340 C 108 384 146 416 190 416" {_s(c, 24)}/>'
            f'<path d="M 160 396 L 190 416 L 160 440" {_s(c, 22)}/>')


def ftp_server(c):
    """Ftp Server - a rack that sends and receives."""
    return (f'<rect x="96" y="96" width="320" height="112" rx="30" {_s(c, 30)}/>'
            f'<rect x="96" y="252" width="320" height="112" rx="30" {_s(c, 30)}/>'
            f'<path d="M 190 418 L 190 460" {_s(c, 22)}/>'
            f'<path d="M 322 418 L 322 460" {_s(c, 22)}/>'
            f'<path d="M 190 460 L 322 460" {_s(c, 22)}/>')


def folder_grid(c):
    """Mecool File Manager - a folder full of tiles."""
    return (_folder(c)
            + f'<rect x="126" y="256" width="112" height="72" rx="18" {_s(c, 22)}/>'
            + f'<rect x="274" y="256" width="112" height="72" rx="18" {_s(c, 22)}/>')


def package_find(c):
    """Package Explorer - the parcel, inspected."""
    return (f'<path d="M 96 176 L 256 96 L 416 176 L 416 336 L 256 416 '
            f'L 96 336 Z" {_s(c, 32)}/>'
            f'<path d="M 96 176 L 256 256 L 416 176" {_s(c, 24)}/>'
            f'<path d="M 256 256 L 256 416" {_s(c, 24)}/>')


def nas_tower(c):
    """Ugreen NAS - the tower on the network."""
    return (f'<rect x="150" y="80" width="212" height="300" rx="42" {_s(c, 32)}/>'
            f'<path d="M 200 152 L 312 152" {_s(c, 22)}/>'
            f'<path d="M 200 226 L 312 226" {_s(c, 22)}/>'
            f'<path d="M 256 380 L 256 432" {_s(c, 24)}/>'
            f'<path d="M 152 432 L 360 432" {_s(c, 26)}/>')


def catch_hook(c):
    """Catch-On TV - the screen and the catch."""
    return (f'<rect x="64" y="88" width="384" height="268" rx="52" {_s(c, 32)}/>'
            f'<path d="M 256 152 L 256 244 C 256 280 224 300 194 284" {_s(c, 28)}/>'
            f'<path d="M 176 412 L 336 412" {_s(c, 28)}/>'
            f'<path d="M 256 356 L 256 412" {_s(c, 24)}/>')


def cinema_glow(c):
    """CinemaGlow - the lit screen."""
    return (f'<rect x="96" y="150" width="320" height="230" rx="44" {_s(c, 32)}/>'
            f'<path d="M 256 60 L 256 108" {_s(c, 24)}/>'
            f'<path d="M 128 82 L 156 122" {_s(c, 22)}/>'
            f'<path d="M 384 82 L 356 122" {_s(c, 22)}/>'
            f'<path d="M 224 226 L 224 306 L 296 266 Z" {_s(c, 26)}/>')


def overflight(c):
    """Projectivy Overflight - the aerial horizon the screensaver flies over."""
    return (f'<path d="M 64 356 L 176 232 L 262 320 L 340 238 L 448 356 Z" '
            f'{_s(c, 30)}/>'
            f'<circle cx="358" cy="132" r="44" {_s(c, 26)}/>'
            f'<path d="M 84 160 C 148 108 236 108 300 160" {_s(c, 22)}/>')


def media_wave_play(c):
    """Xiaomi Media Player - the play riding a waveform."""
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 32)}/>'
            f'<path d="M 214 184 L 214 328 L 330 256 Z" {_s(c, 28)}/>'
            f'<path d="M 130 256 L 168 256" {_s(c, 22)}/>')


def store_globe(c):
    """Overseas App Store - the bag from abroad."""
    return (f'<path d="M 106 176 L 406 176 L 386 408 C 384 424 372 434 356 434 '
            f'L 156 434 C 140 434 128 424 126 408 Z" {_s(c, 32)}/>'
            f'<path d="M 186 224 L 186 148 C 186 108 218 78 256 78 '
            f'C 294 78 326 108 326 148 L 326 224" {_s(c, 26)}/>')


def net_screen(c):
    """eTVnet - the screen on a network."""
    return (f'<rect x="64" y="118" width="384" height="252" rx="48" {_s(c, 32)}/>'
            f'<circle cx="256" cy="244" r="42" {_s(c, 24)}/>'
            f'<path d="M 176 180 C 132 224 132 264 176 308" {_s(c, 22)}/>'
            f'<path d="M 336 180 C 380 224 380 264 336 308" {_s(c, 22)}/>'
            f'<path d="M 176 428 L 336 428" {_s(c, 28)}/>')


def arrow_mark(c):
    """Arrow (Arrow Films) - the arrow, which is the whole brand."""
    return (f'<path d="M 256 78 L 256 434" {_s(c, 34)}/>'
            f'<path d="M 142 192 L 256 78 L 370 192" {_s(c, 34)}/>')


def corridor_mark(c):
    """Corridor - the receding corridor."""
    return (f'<rect x="70" y="70" width="372" height="372" rx="56" {_s(c, 32)}/>'
            f'<rect x="188" y="188" width="136" height="136" rx="28" {_s(c, 26)}/>'
            f'<path d="M 70 70 L 188 188" {_s(c, 20)}/>'
            f'<path d="M 442 70 L 324 188" {_s(c, 20)}/>'
            f'<path d="M 70 442 L 188 324" {_s(c, 20)}/>'
            f'<path d="M 442 442 L 324 324" {_s(c, 20)}/>')


def dev_play(c):
    """DevInterest - brackets around a play."""
    return (f'<path d="M 158 148 L 82 256 L 158 364" {_s(c, 32)}/>'
            f'<path d="M 354 148 L 430 256 L 354 364" {_s(c, 32)}/>'
            f'<path d="M 214 190 L 214 322 L 318 256 Z" {_s(c, 28)}/>')


def ertflix_mark(c):
    """ERTFLIX - a play inside the broadcaster's rounded block."""
    return (f'<rect x="72" y="120" width="368" height="272" rx="72" {_s(c, 32)}/>'
            f'<path d="M 216 186 L 216 326 L 330 256 Z" {_s(c, 30)}/>'
            f'<path d="M 118 392 L 118 120" {_s(c, 24)}/>')


def film_play(c):
    """Filmzie - a film frame with a play."""
    return (f'<rect x="80" y="128" width="352" height="256" rx="40" {_s(c, 32)}/>'
            f'<path d="M 80 196 L 432 196" {_s(c, 22)}/>'
            f'<path d="M 224 246 L 224 344 L 310 296 Z" {_s(c, 26)}/>')


def heart_play(c):
    """Lifetime - the drama channel; a heart that plays."""
    return (f'<path d="M 256 424 C 130 336 76 268 76 196 C 76 140 120 100 174 100 '
            f'C 208 100 238 118 256 148 C 274 118 304 100 338 100 '
            f'C 392 100 436 140 436 196 C 436 268 382 336 256 424 Z" {_s(c, 32)}/>'
            f'<path d="M 224 176 L 224 264 L 302 220 Z" {_s(c, 26)}/>')


def movie_box(c):
    """MovieBox Pro - the box of films."""
    return (f'<rect x="76" y="140" width="360" height="268" rx="40" {_s(c, 32)}/>'
            f'<path d="M 76 216 L 436 216" {_s(c, 24)}/>'
            f'<path d="M 150 140 L 200 216" {_s(c, 20)}/>'
            f'<path d="M 280 140 L 330 216" {_s(c, 20)}/>'
            f'<path d="M 216 286 L 216 358 L 292 322 Z" {_s(c, 24)}/>')


def movie_lab(c):
    """MovieLab - the flask; an experiment in film."""
    return (f'<path d="M 208 80 L 208 206 L 110 380 C 96 404 112 434 140 434 '
            f'L 372 434 C 400 434 416 404 402 380 L 304 206 L 304 80 Z" '
            f'{_s(c, 32)}/>'
            f'<path d="M 186 80 L 326 80" {_s(c, 26)}/>'
            f'<path d="M 160 306 L 352 306" {_s(c, 22)}/>')


def star_play(c):
    """Movies By Fawesome - the star, with a play at its heart."""
    return (f'<path d="M 256 74 L 312 190 L 440 208 L 348 298 L 370 426 '
            f'L 256 366 L 142 426 L 164 298 L 72 208 L 200 190 Z" {_s(c, 30)}/>'
            f'<path d="M 228 216 L 228 296 L 300 256 Z" {_s(c, 22)}/>')


def projector(c):
    """Old Movies - the projector; the classic-cinema tell."""
    return (f'<circle cx="196" cy="184" r="94" {_s(c, 30)}/>'
            f'<circle cx="346" cy="214" r="64" {_s(c, 26)}/>'
            f'<rect x="86" y="298" width="340" height="110" rx="32" {_s(c, 30)}/>'
            f'<path d="M 150 408 L 150 444" {_s(c, 22)}/>'
            f'<path d="M 362 408 L 362 444" {_s(c, 22)}/>')


def signal_play(c):
    """Realstream TV - the live signal, playing.

    The outer arcs originally swung to x=20 and x=492, well outside SAFE.
    Both pairs now bulge inside the 40px margin and still read as broadcast.
    """
    return (f'<path d="M 224 200 L 224 312 L 318 256 Z" {_s(c, 28)}/>'
            f'<path d="M 154 176 C 116 216 116 296 154 336" {_s(c, 26)}/>'
            f'<path d="M 358 176 C 396 216 396 296 358 336" {_s(c, 26)}/>'
            f'<path d="M 106 124 C 48 200 48 312 106 388" {_s(c, 22)}/>'
            f'<path d="M 406 124 C 464 200 464 312 406 388" {_s(c, 22)}/>')


def series_calendar(c):
    """SeriesGuide - the episode calendar, ticked off."""
    return (f'<rect x="72" y="110" width="368" height="316" rx="46" {_s(c, 32)}/>'
            f'<path d="M 72 200 L 440 200" {_s(c, 24)}/>'
            f'<path d="M 160 68 L 160 140" {_s(c, 24)}/>'
            f'<path d="M 352 68 L 352 140" {_s(c, 24)}/>'
            f'<path d="M 168 314 L 226 370 L 344 250" {_s(c, 28)}/>')


def stream_wave(c):
    """Sstream - the double wave."""
    return (f'<path d="M 64 200 C 112 148 176 148 224 200 '
            f'C 272 252 336 252 384 200" {_s(c, 30)}/>'
            f'<path d="M 128 312 C 176 260 240 260 288 312 '
            f'C 336 364 400 364 448 312" {_s(c, 30)}/>')


def fin_wave(c):
    """Streamyfin - the fin above the water; a Jellyfin client."""
    return (f'<path d="M 132 330 C 200 190 300 116 396 104 '
            f'C 388 200 330 296 216 330 Z" {_s(c, 32)}/>'
            f'<path d="M 72 396 C 120 358 176 358 224 396 '
            f'C 272 434 328 434 376 396" {_s(c, 26)}/>')


def bolt_screen(c):
    """Streamz - the bolt on the screen."""
    return (f'<rect x="64" y="104" width="384" height="276" rx="48" {_s(c, 32)}/>'
            f'<path d="M 280 150 L 202 262 L 262 262 L 232 336 L 314 222 '
            f'L 252 222 Z" {_s(c, 26)}/>'
            f'<path d="M 176 434 L 336 434" {_s(c, 28)}/>')


def chevron_plus(c):
    """Ve Plus - the chevron and the plus.

    The plus reached x=477, five pixels past SAFE. Pulled inside the margin.
    """
    return (f'<path d="M 84 148 L 200 372 L 316 148" {_s(c, 34)}/>'
            f'<path d="M 386 172 L 386 284" {_s(c, 26)}/>'
            f'<path d="M 330 228 L 442 228" {_s(c, 26)}/>')


def weyd_mark(c):
    """Weyd - the way marker; a compass rose set for the route."""
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 32)}/>'
            f'<path d="M 330 182 L 284 284 L 182 330 L 228 228 Z" {_s(c, 28)}/>')


def begin_play(c):
    """begin - the play leaving the starting line."""
    return (f'<path d="M 118 96 L 118 416" {_s(c, 32)}/>'
            f'<path d="M 210 172 L 210 340 L 388 256 Z" {_s(c, 32)}/>')


def adb_wifi(c):
    """ADB WiFi - the debug bridge, over the air."""
    return (f'<path d="M 96 250 C 184 162 328 162 416 250" {_s(c, 30)}/>'
            f'<path d="M 152 312 C 212 252 300 252 360 312" {_s(c, 26)}/>'
            f'<circle cx="256" cy="392" r="30" {_s(c, 24)}/>'
            f'<path d="M 186 118 L 186 74" {_s(c, 22)}/>'
            f'<path d="M 326 118 L 326 74" {_s(c, 22)}/>')


def analiti_meter(c):
    """Analiti - the signal meter."""
    return (f'<path d="M 110 400 L 110 318" {_s(c, 30)}/>'
            f'<path d="M 208 400 L 208 254" {_s(c, 30)}/>'
            f'<path d="M 306 400 L 306 188" {_s(c, 30)}/>'
            f'<path d="M 404 400 L 404 118" {_s(c, 30)}/>')


def dev_gear(c):
    """Developer Tools - brackets and a gear."""
    return (f'<path d="M 166 130 L 84 256 L 166 382" {_s(c, 30)}/>'
            f'<path d="M 346 130 L 428 256 L 346 382" {_s(c, 30)}/>'
            f'<circle cx="256" cy="256" r="64" {_s(c, 26)}/>')


def download_navi(c):
    """Download Navi - the download, steered."""
    return (f'<path d="M 256 96 L 256 306" {_s(c, 32)}/>'
            f'<path d="M 166 220 L 256 306 L 346 220" {_s(c, 32)}/>'
            f'<path d="M 104 370 C 152 418 360 418 408 370" {_s(c, 26)}/>')


def net_pulse(c):
    """Dr Nettools - the network, with a pulse across it."""
    return (f'<circle cx="118" cy="150" r="46" {_s(c, 26)}/>'
            f'<circle cx="394" cy="150" r="46" {_s(c, 26)}/>'
            f'<circle cx="256" cy="382" r="46" {_s(c, 26)}/>'
            f'<path d="M 160 172 L 352 172" {_s(c, 22)}/>'
            f'<path d="M 144 192 L 226 344" {_s(c, 22)}/>'
            f'<path d="M 368 192 L 286 344" {_s(c, 22)}/>')


def cast_receiver(c):
    """FCast Receiver - the screen receiving a cast.

    The source dot was radius 22 under a 21.8 stroke - an eleven-pixel
    counter that shut at tile size. Radius 34 keeps it an open ring.
    """
    return (f'<rect x="150" y="104" width="298" height="222" rx="44" {_s(c, 32)}/>'
            f'<path d="M 96 316 C 128 316 156 344 156 376" {_s(c, 26)}/>'
            f'<path d="M 96 244 C 168 244 228 304 228 376" {_s(c, 26)}/>'
            f'<circle cx="90" cy="386" r="34" {_s(c, 22)}/>')


def toolbox(c):
    """Good Tools - the toolbox."""
    return (f'<rect x="68" y="186" width="376" height="228" rx="42" {_s(c, 32)}/>'
            f'<path d="M 180 186 L 180 144 C 180 118 200 98 226 98 '
            f'L 286 98 C 312 98 332 118 332 144 L 332 186" {_s(c, 26)}/>'
            f'<path d="M 68 282 L 444 282" {_s(c, 24)}/>')


def ip_globe(c):
    """IP Tools - the network, addressed."""
    return (f'<circle cx="256" cy="256" r="180" {_s(c, 32)}/>'
            f'<path d="M 76 256 L 436 256" {_s(c, 24)}/>'
            f'<path d="M 256 76 C 312 136 312 376 256 436" {_s(c, 24)}/>'
            f'<path d="M 256 76 C 200 136 200 376 256 436" {_s(c, 24)}/>')


def ip_tag(c):
    """Ip Address - the address label."""
    return (f'<path d="M 78 198 L 250 198 L 250 314 L 78 314 Z" {_s(c, 30)}/>'
            f'<path d="M 250 198 L 434 198 C 448 198 448 314 434 314 L 250 314" '
            f'{_s(c, 30)}/>'
            f'<path d="M 128 256 L 200 256" {_s(c, 22)}/>'
            f'<path d="M 300 256 L 372 256" {_s(c, 22)}/>')


def server_play(c):
    """Jtv Go Server - the server that streams."""
    return (f'<rect x="86" y="88" width="340" height="116" rx="32" {_s(c, 30)}/>'
            f'<path d="M 216 266 L 216 400 L 348 334 Z" {_s(c, 30)}/>'
            f'<path d="M 146 146 L 190 146" {_s(c, 22)}/>')


def cursor_mark(c):
    """Matvt Mouse - the pointer the app puts on the television."""
    return (f'<path d="M 140 84 L 140 388 L 222 310 L 278 428 L 344 396 '
            f'L 288 282 L 396 268 Z" {_s(c, 32)}/>')


def bell(c):
    """Notifications for Android TV."""
    return (f'<path d="M 130 344 C 160 314 168 282 168 226 '
            f'C 168 152 208 108 256 108 C 304 108 344 152 344 226 '
            f'C 344 282 352 314 382 344 Z" {_s(c, 32)}/>'
            f'<path d="M 256 108 L 256 66" {_s(c, 24)}/>'
            f'<path d="M 212 344 C 212 384 230 406 256 406 '
            f'C 282 406 300 384 300 344" {_s(c, 26)}/>')


def home_bar(c):
    """Quickbars for Home Assistant - the house, with its quick bar."""
    return (f'<path d="M 78 250 L 256 96 L 434 250" {_s(c, 32)}/>'
            f'<path d="M 130 252 L 130 400 C 130 418 144 430 162 430 '
            f'L 350 430 C 368 430 382 418 382 400 L 382 252" {_s(c, 30)}/>'
            f'<path d="M 190 344 L 322 344" {_s(c, 24)}/>')


def capture_dot(c):
    """Remote Capture - the screen, recording."""
    return (f'<rect x="64" y="112" width="384" height="272" rx="48" {_s(c, 32)}/>'
            f'<circle cx="256" cy="248" r="54" {_s(c, 26)}/>'
            f'<path d="M 176 434 L 336 434" {_s(c, 28)}/>')


def remote_play(c):
    """Remote Starter for Yatse - the handset that starts playback."""
    return (f'<rect x="164" y="64" width="184" height="384" rx="60" {_s(c, 32)}/>'
            f'<path d="M 230 150 L 230 226 L 296 188 Z" {_s(c, 24)}/>'
            f'<circle cx="256" cy="318" r="28" {_s(c, 22)}/>')


def tv_sliders(c):
    """TV Manager - the screen and its settings.

    The knobs sat on the rails they controlled, and each one pinched its rail
    shut at tile size. Lifting the knobs clear of the rails keeps four
    separate shapes.
    """
    return (f'<rect x="64" y="104" width="384" height="276" rx="48" {_s(c, 32)}/>'
            f'<path d="M 128 186 L 384 186" {_s(c, 22)}/>'
            f'<path d="M 128 298 L 384 298" {_s(c, 22)}/>'
            f'<circle cx="214" cy="242" r="30" {_s(c, 22)}/>'
            f'<path d="M 176 434 L 336 434" {_s(c, 28)}/>')


def screensaver_moon(c):
    """Tduk Screensaver Manager - the screen at rest."""
    return (f'<rect x="64" y="104" width="384" height="276" rx="48" {_s(c, 32)}/>'
            f'<path d="M 306 160 C 250 174 212 222 212 280 '
            f'C 212 306 222 330 238 348 C 172 342 128 292 128 234 '
            f'C 128 178 182 140 246 148 C 268 150 290 154 306 160 Z" {_s(c, 26)}/>'
            f'<path d="M 176 434 L 336 434" {_s(c, 28)}/>')


def usb_plug(c):
    """Virtualhere USB Server - the shared USB device.

    The connector block was 72x60 under a 26 stroke, so its opening shut. A
    larger block and a wider branch keep both ends readable.
    """
    return (f'<path d="M 256 440 L 256 128" {_s(c, 32)}/>'
            f'<path d="M 192 192 L 256 128 L 320 192" {_s(c, 32)}/>'
            f'<circle cx="128" cy="330" r="40" {_s(c, 26)}/>'
            f'<path d="M 128 290 L 128 258 L 256 258" {_s(c, 24)}/>'
            f'<rect x="326" y="288" width="94" height="84" rx="20" {_s(c, 26)}/>'
            f'<path d="M 326 330 L 256 330" {_s(c, 24)}/>')


def remote_pad(c):
    """Zank Remote - the handset with a D-pad."""
    return (f'<rect x="152" y="56" width="208" height="400" rx="64" {_s(c, 32)}/>'
            f'<circle cx="256" cy="200" r="66" {_s(c, 26)}/>'
            f'<path d="M 200 350 L 312 350" {_s(c, 22)}/>'
            f'<path d="M 200 412 L 312 412" {_s(c, 22)}/>')


# --------------------------------------------------------------------------
# Source & input marks (v1.8.17).
#
# Consoles reach the TV through HDMI, so on a Projectivy home they exist as
# input tiles, not apps. These marks give those tiles — and the companion
# apps users keep beside them — the same rounded-line identity as everything
# else. The constructions are ours: the recognisable cue (an orb crossed
# by flowing lines, detached rails, the four button shapes, the plug face)
# is drawn as open linework, never as a vendor silhouette.
# --------------------------------------------------------------------------
def xbox_orb(c):
    """Xbox - the orb, read by its crossed-flow interior."""
    return (f'<circle cx="256" cy="256" r="176" {_s(c, 32)}/>'
            f'<path d="M 150 150 Q 256 280 362 362" {_s(c, 26)}/>'
            f'<path d="M 362 150 Q 256 280 150 362" {_s(c, 26)}/>')


def switch_joycons(c):
    """Nintendo Switch - the two detached rails, sticks on opposite corners."""
    return (f'<rect x="96" y="96" width="128" height="320" rx="64" {_s(c, 32)}/>'
            f'<rect x="288" y="96" width="128" height="320" rx="64" {_s(c, 32)}/>'
            f'<circle cx="160" cy="176" r="26" {_s(c, 26)}/>'
            f'<circle cx="352" cy="336" r="26" {_s(c, 26)}/>')


def playstation_shapes(c):
    """PlayStation - the four button shapes, one per quadrant."""
    return (f'<path d="M 160 100 L 224 214 L 96 214 Z" {_s(c, 26)}/>'
            f'<circle cx="352" cy="157" r="62" {_s(c, 26)}/>'
            f'<path d="M 112 304 L 208 400" {_s(c, 26)}/>'
            f'<path d="M 208 304 L 112 400" {_s(c, 26)}/>'
            f'<rect x="292" y="292" width="120" height="120" rx="14" {_s(c, 26)}/>')


def hdmi_connector(c):
    """HDMI - the plug face: square shoulders, cut lower corners, pin ticks."""
    return (f'<path d="M 104 156 L 408 156 L 408 262 L 362 356 '
            f'L 150 356 L 104 262 Z" {_s(c, 32)}/>'
            f'<path d="M 200 208 L 200 264" {_s(c, 22)}/>'
            f'<path d="M 256 208 L 256 264" {_s(c, 22)}/>'
            f'<path d="M 312 208 L 312 264" {_s(c, 22)}/>')


def tv_antenna(c):
    """TV input — a screen with rabbit-ear antennae: the tuner source
    (Projectivy pins the TV input as a home-row card; the community's
    "TV is very ugly" complaint, docs/research/community-input-icons)."""
    return (f'<rect x="96" y="196" width="320" height="220" rx="36" {_s(c, 32)}/>'
            f'<path d="M 226 196 L 150 92" {_s(c, 32)}/>'
            f'<path d="M 286 196 L 362 92" {_s(c, 32)}/>')


def input_screen(c):
    """Source — the launcher's choose-source menu: a screen with the
    signal entering it (one generic mark for the input selector)."""
    return (f'<rect x="150" y="146" width="270" height="220" rx="36" {_s(c, 32)}/>'
            f'<path d="M 64 256 L 206 256" {_s(c, 30)}/>'
            f'<path d="M 174 212 L 222 256 L 174 300" {_s(c, 30)}/>')


def usb_media(c):
    """Media explorer — a USB stick with a play mark: play from the drive
    (Projectivy's shortcut to the stock media explorer)."""
    return (f'<rect x="204" y="76" width="104" height="88" rx="16" {_s(c, 30)}/>'
            f'<path d="M 234 108 L 234 132" {_s(c, 18)}/>'
            f'<path d="M 278 108 L 278 132" {_s(c, 18)}/>'
            f'<rect x="172" y="164" width="168" height="252" rx="30" {_s(c, 32)}/>'
            f'<path d="M 222 252 L 306 290 L 222 328 Z" {_s(c, 30)}/>')


def stremize_z(c):
    """Stremize - the Z of the all-in-one debrid/playlist player."""
    return (f'<path d="M 104 140 L 408 140 L 104 372 L 408 372" {_s(c, 32)}/>')


GLYPHS.update({
    "coji_browser": coji_browser,
    "tv_browser_bar": tv_browser_bar,
    "tv_web_browser": tv_web_browser,
    "vewd_browser": vewd_browser,
    "debrid_cloud_play": debrid_cloud_play,
    "debrid_cloud_link": debrid_cloud_link,
    "folder_tree": folder_tree,
    "folder_sync_one": folder_sync_one,
    "folder_sync_two": folder_sync_two,
    "ftp_server": ftp_server,
    "folder_grid": folder_grid,
    "package_find": package_find,
    "nas_tower": nas_tower,
    "catch_hook": catch_hook,
    "cinema_glow": cinema_glow,
    "overflight": overflight,
    "media_wave_play": media_wave_play,
    "store_globe": store_globe,
    "net_screen": net_screen,
    "arrow_mark": arrow_mark,
    "corridor_mark": corridor_mark,
    "dev_play": dev_play,
    "ertflix_mark": ertflix_mark,
    "film_play": film_play,
    "heart_play": heart_play,
    "movie_box": movie_box,
    "movie_lab": movie_lab,
    "star_play": star_play,
    "projector": projector,
    "signal_play": signal_play,
    "series_calendar": series_calendar,
    "stream_wave": stream_wave,
    "fin_wave": fin_wave,
    "bolt_screen": bolt_screen,
    "chevron_plus": chevron_plus,
    "weyd_mark": weyd_mark,
    "begin_play": begin_play,
    "adb_wifi": adb_wifi,
    "analiti_meter": analiti_meter,
    "dev_gear": dev_gear,
    "download_navi": download_navi,
    "net_pulse": net_pulse,
    "cast_receiver": cast_receiver,
    "toolbox": toolbox,
    "ip_globe": ip_globe,
    "ip_tag": ip_tag,
    "server_play": server_play,
    "cursor_mark": cursor_mark,
    "bell": bell,
    "home_bar": home_bar,
    "capture_dot": capture_dot,
    "remote_play": remote_play,
    "tv_sliders": tv_sliders,
    "screensaver_moon": screensaver_moon,
    "usb_plug": usb_plug,
    "remote_pad": remote_pad,
    "xbox_orb": xbox_orb,
    "switch_joycons": switch_joycons,
    "playstation_shapes": playstation_shapes,
    "hdmi_connector": hdmi_connector,
    "tv_antenna": tv_antenna, "input_screen": input_screen,
    "usb_media": usb_media,
    "stremize_z": stremize_z,
})


# ==========================================================================
# Category containers.
#
# 549 icons still fell back to `tile_X`: one letter in one rounded box,
# separated only by accent. The accent spread in v1.8.14 made them distinct
# files, but not distinguishable at a glance - a home screen of 549 identical
# squircles is scanned letter by letter, which is exactly the work an icon is
# supposed to save.
#
# These replace the single squircle with a container per FUNCTION, so the
# silhouette answers "what kind of app is this" before the letter is read.
# The category comes from the package id and name (tools/classify_families.py),
# not from a guess at the brand: nothing here invents a vendor mark, and an
# app whose function cannot be read keeps the neutral squircle.
#
# The monogram stays the identity. Cap heights are set per container from the
# interior actually available, and every one is checked at 48px - a letter
# whose counters close is worse than the tile it replaced.
# ==========================================================================

def _fam(letter, color, shell, cap_h=210, cy=GRID / 2):
    """A category shell with the app's monogram inside it."""
    return shell + monogram_scaled(letter, color, cap_h=cap_h, cy=cy)


# Modern pass (2026-09-26): one open-corner tile for every family, with the
# function carried by a badge in the top-right corner instead of by the
# container's silhouette. The earlier per-family containers (a CRT on legs, a
# ball with a seam, a cloud, a gamepad) made the monogram fight the outline -
# seams and grips crossed the letters, and the sport seam read as a "no entry"
# sign. Here the letters own the whole interior and never meet a line; the
# badge answers "what kind of app" at a glance, the way current TV and mobile
# icon systems pair a mark with a small status glyph. The tile's top-right
# corner is left open so the badge is framed by it rather than stacked on it.

BX, BY, R = 394, 118, 64      # badge centre and radius on the 512 grid
BW = 24                        # badge stroke


def P(x, y):
    """Badge-local unit coords (-1..1) -> grid coords."""
    return f"{BX + x * R:.1f} {BY + y * R:.1f}"


def open_tile(c):
    # Tile x 72..440, y 120..448; the top-right corner stays open for the badge.
    return (f'<path d="M 300 120 L 148 120 C 106 120 72 154 72 196 L 72 372 '
            f'C 72 414 106 448 148 448 L 364 448 C 406 448 440 414 440 372 '
            f'L 440 216" {_s(c, 30)}/>')


def closed_tile(c):
    return f'<rect x="72" y="120" width="368" height="328" rx="76" {_s(c, 30)}/>'


def b_live(c):
    """TV & video: a flat screen on a short stand.

    The first badge here was a live 'on air' signal, but the broadcast family
    holds 320 rows - channels, yes, and also VOD services (Viaplay, Youku,
    VidAngel) and players (IB Player, Lampa). A live sign told most of them
    something false; a screen is true for all of them.
    """
    return (f'<rect x="{BX - R:.1f}" y="{BY - R*.72:.1f}" width="{2*R:.1f}" '
            f'height="{R*1.22:.1f}" rx="{R*.22:.1f}" {_s(c, BW)}/>'
            f'<path d="M {P(-.36,.9)} L {P(.36,.9)}" {_s(c, BW)}/>')


def b_gear(c):
    return (f'<polygon points="{_gearpts(BX, BY, R, R * .7, teeth=6)}" {_s(c, BW)}/>'
            f'<circle cx="{BX}" cy="{BY}" r="{R * .22:.1f}" {_f(c)}/>')


def b_ball(c):
    """A trophy cup: the sport cue that cannot be misread as a prohibition."""
    return (f'<path d="M {P(-.55,-.85)} L {P(.55,-.85)} L {P(.55,-.25)} '
            f'C {P(.55,.2)} {P(.25,.4)} {P(0,.4)} C {P(-.25,.4)} {P(-.55,.2)} {P(-.55,-.25)} Z" {_s(c, BW)}/>'
            f'<path d="M {P(-.55,-.62)} C {P(-.95,-.62)} {P(-.95,-.05)} {P(-.5,.02)}" {_s(c, 20)}/>'
            f'<path d="M {P(.55,-.62)} C {P(.95,-.62)} {P(.95,-.05)} {P(.5,.02)}" {_s(c, 20)}/>'
            f'<path d="M {P(0,.42)} L {P(0,.72)} M {P(-.4,.85)} L {P(.4,.85)}" {_s(c, BW)}/>')


def b_note(c):
    return (f'<circle cx="{BX - R*.35:.1f}" cy="{BY + R*.55:.1f}" r="{R*.3:.1f}" {_f(c)}/>'
            f'<path d="M {P(-.07,.55)} L {P(-.07,-.9)} C {P(.35,-.75)} {P(.75,-.5)} {P(.7,-.05)}" {_s(c, BW)}/>')


def b_pad(c):
    return (f'<rect x="{BX - R:.1f}" y="{BY - R*.55:.1f}" width="{2*R:.1f}" height="{1.1*R:.1f}" rx="{R*.55:.1f}" {_s(c, BW)}/>'
            f'<circle cx="{BX - R*.4:.1f}" cy="{BY}" r="{R*.14:.1f}" {_f(c)}/>'
            f'<circle cx="{BX + R*.4:.1f}" cy="{BY}" r="{R*.14:.1f}" {_f(c)}/>')


def b_lock(c):
    return (f'<rect x="{BX - R*.62:.1f}" y="{BY - R*.05:.1f}" width="{1.24*R:.1f}" height="{.95*R:.1f}" rx="{R*.2:.1f}" {_s(c, BW)}/>'
            f'<path d="M {P(-.36,-.05)} L {P(-.36,-.45)} C {P(-.36,-1.05)} {P(.36,-1.05)} {P(.36,-.45)} L {P(.36,-.05)}" {_s(c, BW)}/>')


def b_play(c):
    return (f'<circle cx="{BX}" cy="{BY}" r="{R * .9:.1f}" {_s(c, BW)}/>'
            f'<path d="M {P(-.22,-.38)} L {P(.4,0)} L {P(-.22,.38)} Z" {_f(c)}/>')


def b_down(c):
    return (f'<path d="M {P(0,-.95)} L {P(0,.3)}" {_s(c, BW)}/>'
            f'<path d="M {P(-.45,-.12)} L {P(0,.33)} L {P(.45,-.12)}" {_s(c, BW)}/>'
            f'<path d="M {P(-.8,.82)} L {P(.8,.82)}" {_s(c, BW)}/>')


def b_photo(c):
    """A skyline under a sun, left open so no small counter closes at 96px."""
    return (f'<path d="M {P(-.95,.75)} L {P(-.3,-.1)} L {P(.1,.4)} L {P(.42,.08)} L {P(.95,.75)}" {_s(c, BW)}/>'
            f'<circle cx="{BX + R*.5:.1f}" cy="{BY - R*.55:.1f}" r="{R*.22:.1f}" {_f(c)}/>')


def b_cloud(c):
    return (f'<path d="M {P(-.55,.6)} C {P(-.95,.6)} {P(-1,.05)} {P(-.55,-.02)} '
            f'C {P(-.45,-.55)} {P(.25,-.75)} {P(.45,-.2)} '
            f'C {P(1,-.25)} {P(1.05,.6)} {P(.55,.6)} Z" {_s(c, BW)}/>')


def b_globe(c):
    """The web: a pointer. Two globe drafts closed their meridian slivers at
    96px; a solid pointer has no counter to close and reads as 'browse'."""
    return (f'<path d="M {P(-.5,-.9)} L {P(-.5,.62)} L {P(-.14,.3)} L {P(.1,.88)} '
            f'L {P(.34,.78)} L {P(.1,.22)} L {P(.55,.22)} Z" {_s(c, 16)} '
            f'fill="{c}"/>'.replace('fill="none" ', ''))


def b_spark(c):
    return (f'<path d="M {P(0,-1)} C {P(.08,-.3)} {P(.3,-.08)} {P(1,0)} '
            f'C {P(.3,.08)} {P(.08,.3)} {P(0,1)} C {P(-.08,.3)} {P(-.3,.08)} {P(-1,0)} '
            f'C {P(-.3,-.08)} {P(-.08,-.3)} {P(0,-1)} Z" {_s(c, 22)}/>')


def b_heart(c):
    return (f'<path d="M {P(0,.85)} C {P(-.7,.35)} {P(-.95,.0)} {P(-.95,-.3)} '
            f'C {P(-.95,-.72)} {P(-.4,-.9)} {P(0,-.42)} '
            f'C {P(.4,-.9)} {P(.95,-.72)} {P(.95,-.3)} C {P(.95,0)} {P(.7,.35)} {P(0,.85)} Z" {_s(c, BW)}/>')


def b_folder(c):
    return (f'<path d="M {P(-.95,-.6)} L {P(-.35,-.6)} L {P(-.15,-.35)} L {P(.95,-.35)} '
            f'L {P(.95,.7)} L {P(-.95,.7)} Z" {_s(c, BW)}/>')



_BADGES = {
    "broadcast": b_live, "tool": b_gear, "sport": b_ball, "music": b_note,
    "gaming": b_pad, "vpn": b_lock, "film": b_play, "store": b_down,
    "photos": b_photo, "debrid": b_cloud, "browser": b_globe,
    "anime": b_spark, "kids": b_heart, "files": b_folder,
}


def _badge_shell(family):
    badge = _BADGES[family]
    return lambda c: open_tile(c) + badge(c)


def shell_app(c):
    """The neutral tile, closed, for apps whose function cannot be read."""
    return closed_tile(c)


shell_broadcast = _badge_shell("broadcast")
shell_tool = _badge_shell("tool")
shell_sport = _badge_shell("sport")
shell_music = _badge_shell("music")
shell_gaming = _badge_shell("gaming")
shell_vpn = _badge_shell("vpn")
shell_film = _badge_shell("film")
shell_store = _badge_shell("store")
shell_photos = _badge_shell("photos")
shell_debrid = _badge_shell("debrid")
shell_browser = _badge_shell("browser")
shell_anime = _badge_shell("anime")
shell_kids = _badge_shell("kids")
shell_files = _badge_shell("files")


# Cap height, optical centre and mark width per shell. Every family now shares
# one interior (the tile, x 72..440 / y 120..448), so every family shares one
# budget: the letters sit a little below the tile's centre, clear of the badge.
_TILE_BUDGET = (170, 298, 240)
FAMILY_SHELLS = {
    name: (fn, *_TILE_BUDGET) for name, fn in (
        ("broadcast", shell_broadcast), ("app", shell_app), ("tool", shell_tool),
        ("sport", shell_sport), ("music", shell_music), ("gaming", shell_gaming),
        ("vpn", shell_vpn), ("film", shell_film), ("store", shell_store),
        ("photos", shell_photos), ("debrid", shell_debrid),
        ("browser", shell_browser), ("anime", shell_anime), ("kids", shell_kids),
        ("files", shell_files))
}


def _mk_family(family, letter):
    shell, cap_h, cy, _max_w = FAMILY_SHELLS[family]
    return lambda c: _fam(letter, c, shell(c), cap_h=cap_h, cy=cy)


def _fam_mark(mark, color, shell, cap_h, cy, max_w, style=None):
    """A category shell with the app's short mark inside it."""
    return shell + adaptive_lockup(mark, color, cap_h, max_w, cy, style)


def family_body(glyph_name, color, mark=None, style=None):
    """Resolve a category monogram, swapping its lone letter for the app mark.

    The catalog's `mark` field is 2-4 uppercase chars derived from the app
    name (multi-word: up to three initials; one word: first two letters).
    `style` is the catalog's brand-informed treatment of that mark
    (tranche 1: `lower`) — a cue from the app's logotype, set in the pack's
    own face; literal vendor logotype reproduction stays off-limits. Only
    <family>_<L> shells adapt; every other glyph resolves to its registered
    body untouched.
    """
    if mark:
        fam, _, letter = glyph_name.rpartition("_")
        if fam in FAMILY_SHELLS and len(letter) == 1 and letter.isalnum():
            shell, cap_h, cy, max_w = FAMILY_SHELLS[fam]
            return _fam_mark(mark, color, shell(color),
                             cap_h=cap_h, cy=cy, max_w=max_w, style=style)
    return GLYPHS[glyph_name](color)


def family_glyph_for(glyph_name):
    """The (shell, cap_h, cy, max_w) budget a <family>_<L> glyph offers, or
    None when the glyph is not a category monogram. validators import this so
    they judge a mark against exactly the numbers the renderer will use."""
    fam, _, letter = glyph_name.rpartition("_")
    if fam in FAMILY_SHELLS and len(letter) == 1 and letter.isalnum():
        return FAMILY_SHELLS[fam]
    return None


_family_names = {}
for _fam_key in FAMILY_SHELLS:
    for _ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789":
        _family_names[f"{_fam_key}_{_ch}"] = _mk_family(_fam_key, _ch)
GLYPHS.update(_family_names)


# Every glyph that is a letter in a container rather than a drawn brandmark:
# the 15 FAMILY_SHELLS crossed with A-Z0-9, plus the retired tile_* set that
# preceded them. Exported because three generators and the grid badge all need
# the same answer to "is this icon bespoke", and the only alternative is a
# regex over glyph names, which silently misclassifies anything that happens to
# start with a family word. Derived from the registries above, so a new family
# joins it by existing.
MONOGRAM_GLYPHS = frozenset(_family_names) | frozenset(_tile_names)


def is_monogram(glyph: str) -> bool:
    """True when this glyph is a letter tile, not a drawn mark."""
    return glyph in MONOGRAM_GLYPHS


def trakt_mark(c):
    """Trakt: the ring with its 't'.

    Trakt was in the pack but shared `sync_ring` with Syncler x3 and Synology
    Drive - a generic tracking arrow, so the one app whose whole job is
    tracking had no mark of its own. The brand cue is a circle carrying a
    lowercase t; drawn here in our linework rather than lifted.
    """
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 32)}/>'
            f'<path d="M 226 136 L 226 306 C 226 336 246 352 276 352 '
            f'L 312 352" {_s(c, 30)}/>'
            f'<path d="M 168 198 L 292 198" {_s(c, 26)}/>')


def drive_sync(c):
    """Synology Drive: a disc platter under sync arrows.

    Split off `sync_ring` so it no longer shares a picture with Syncler and
    Trakt. The platter says storage; the arrows say sync.
    """
    return (f'<ellipse cx="256" cy="300" rx="168" ry="96" {_s(c, 30)}/>'
            f'<circle cx="256" cy="300" r="42" {_s(c, 24)}/>'
            f'<path d="M 150 148 C 200 104 312 104 362 148" {_s(c, 26)}/>'
            f'<path d="M 318 116 L 368 152 L 330 190" {_s(c, 22)}/>')


GLYPHS.update({"trakt_mark": trakt_mark, "drive_sync": drive_sync})


# ==========================================================================
# Recognisability tranche 2 (2026-09).
#
# Twelve entries leave category shells and letter tiles for constructions:
# seven network numerals and the two Australian free-to-air pairs get marks
# built from the brand's actual device (the 7, the 9, the 10, the ABC
# lollipops, the koru), Hotstar gets its literal name, Magenta Sport gets
# the Telekom t with its dot.
#
# Owner direction (2026-09-14): a single letter is only carried when the
# letter itself is part of the original logo (network numerals; the
# Telekom t; a wordmark's signature letterform). Wordmark-only brands whose
# initial has no device get a construction instead: Crave sets the
# wordmark's leading c in its case, Hayu carries the y with its sweeping
# descender, and Neon draws the N as the app mark's own neon-tube segments.
#
# All of these constructions are monoline: flat primitives, one accent,
# rounded caps, weights 30/26/24 so normalisation lands on 32/26.2/21.8.
# ==========================================================================

def seven_plusmark(c):
    """7plus / Seven Plus — the Seven Network's 7, carrying the plus.

    A bold 7 (crossbar + diagonal leg) with the small plus sitting in the
    open space the leg clears: the network's numeral plus the service's
    name, no container.
    """
    return (f'<path d="M 108 146 L 352 146 L 184 390" {_s(c, 30)}/>'
            f'<path d="M 372 266 L 372 370" {_s(c, 26)}/>'
            f'<path d="M 320 318 L 424 318" {_s(c, 26)}/>')


def ninenow_mark(c):
    """9Now / 9Now CTV — the nine, and the play that says "now".

    The bowl and stem of the 9 hold the upper field; the play triangle
    lands lower right, where the brand's Now wordmark sits in the
    real lockup.
    """
    return (f'<circle cx="204" cy="196" r="108" {_s(c, 30)}/>'
            f'<path d="M 300 248 C 316 316 312 366 286 404" {_s(c, 30)}/>'
            f'<path d="M 348 306 L 348 374 L 408 340 Z" {_s(c, 24)}/>')


def ten_mark(c):
    """10 Play — the numeral is the brand: 1 and 0 as one lockup.

    France24's precedent: when the digits are the identity, they take the
    whole safe area. The 1 keeps its flag, the 0 is a tall ring, and no
    container carries the pair — this retires the last tile_*.
    """
    return (f'<path d="M 104 168 L 168 120 L 168 408" {_s(c, 30)}/>'
            f'<ellipse cx="316" cy="264" rx="100" ry="144" {_s(c, 30)}/>')


def abc_lollipops(c):
    """ABC iview — the lollipops: centre seed with its petal ring.

    The ABC identity is a cluster of circles, recognisable in silhouette.
    Seven stroked circles in one accent; the iview wordmark stays off the
    tile per the monogram rule.
    """
    import math
    out = f'<circle cx="256" cy="256" r="52" {_s(c, 30)}/>'
    for i in range(6):
        a = math.radians(90 + i * 60)
        x = 256 + 148 * math.cos(a)
        y = 256 + 148 * math.sin(a)
        out += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="44" {_s(c, 30)}/>'
    return out


def maori_koru(c):
    """Māori+ — the koru, the unfurling fern frond.

    One stroke from the frond head tightening into a two-turn spiral; the
    rounded cap at the head is the frond's bud. Turn count is set so the
    counters stay open at 48px.
    """
    import math
    pts = []
    turns, steps = 2.0, 120
    for i in range(steps + 1):
        t = i / steps
        theta = math.radians(115) - t * turns * 2 * math.pi
        r = 190 - 126 * t
        x = 256 + r * math.cos(theta)
        y = 256 + r * math.sin(theta)
        pts.append(f"{'M' if i == 0 else 'L'} {x:.1f} {y:.1f}")
    return f'<path d="{" ".join(pts)}" {_s(c, 30)}/>'


def hotstar_spark(c):
    """JioHotstar — the hot star: five points with the glint.

    The star is the brand's literal name; the four-ray spark upper right
    is the "hot" tell, two crossed strokes so it stays monoline.
    """
    import math
    cx, cy, R, r = 236, 284, 148, 72
    pts = []
    for i in range(10):
        a = math.radians(-90 + i * 36)
        rad = R if i % 2 == 0 else r
        x = cx + rad * math.cos(a)
        y = cy + rad * math.sin(a)
        pts.append(f"{'M' if i == 0 else 'L'} {x:.1f} {y:.1f}")
    return (f'<path d="{" ".join(pts)} Z" {_s(c, 30)}/>'
            f'<path d="M 396 84 L 396 168" {_s(c, 24)}/>'
            f'<path d="M 354 126 L 438 126" {_s(c, 24)}/>')


def magenta_t(c):
    """Magenta Sport — Telekom's t with its dot, the parent brand's mark.

    The crossbar bows upward like a broadcast signal and the stem drops
    from its crest; the round-capped dot at the right shoulder is the
    Telekom t-dot that also closes the Magenta Sport wordmark, so the
    mark quotes the parent brand's actual device rather than a bare T.
    """
    return (f'<path d="M 108 172 C 176 140 336 140 404 172" {_s(c, 30)}/>'
            f'<path d="M 256 152 L 256 404" {_s(c, 30)}/>'
            f'<path d="M 382 106 L 382.5 106" {_s(c, 30)}/>')


def crave_c(c):
    """Crave — the wordmark's leading c, lowercase, in the pack's own face.

    Crave's 2018 identity (Ronald Ruiz) is a purely geometric lowercase
    wordmark in CraveBlue — there is no emblem or device to carry, so the
    icon sets the mark's opening letter in the wordmark's case and in the
    pack's own face, in the canonical wordmark blue. The uppercase C tile
    was an invention and is retired.
    """
    return monogram_body("c", c)


def hayu_y(c):
    """Hayu — the wordmark's y and its long sweeping descender.

    The 2022 rebrand wordmark is a bold lowercase hayu in pink-red; the
    y's curved tail is the one letterform that identifies it, so the icon
    carries that letterform alone. The word has no capital H, so the old
    H tile was an invention and is retired.
    """
    return (f'<path d="M 198 146 L 267 322" {_s(c, 30)}/>'
            f'<path d="M 342 138 L 264 330 C 246 388 192 424 116 408" {_s(c, 30)}/>')


def neon_tube_n(c):
    """Neon — an N built from bent-tube segments, the app mark's own construction.

    The NEON app icon sets its letters in acid green as separate
    neon-tube strokes with rounded ends and open gaps at the joints; the
    icon excerpt is the brand's construction language, not an initial
    tile, so the N reads as a lit tube rather than a letter.
    """
    return (f'<path d="M 140 140 L 140 408" {_s(c, 30)}/>'
            f'<path d="M 202 172 L 310 376" {_s(c, 30)}/>'
            f'<path d="M 372 140 L 372 408" {_s(c, 30)}/>')


GLYPHS.update({
    "seven_plusmark": seven_plusmark,
    "ninenow_mark": ninenow_mark,
    "ten_mark": ten_mark,
    "abc_lollipops": abc_lollipops,
    "maori_koru": maori_koru,
    "hotstar_spark": hotstar_spark,
    "magenta_t": magenta_t,
    "crave_c": crave_c,
    "hayu_y": hayu_y,
    "neon_tube_n": neon_tube_n,
})

# --------------------------------------------------------------------------
# 2026-09 redraw tranche: original Core Builds constructions for reviewed
# brand cues. These deliberately avoid importing or tracing vendor artwork.
# --------------------------------------------------------------------------
def airscreen_as(c):
    """AirScreen: a compact open A flowing into a rounded S."""
    return (
        f'<path d="M 108 366 L 204 134 L 292 366" {_s(c, 34)}/>'
        f'<path d="M 158 268 H 253" {_s(c, 26)}/>'
        f'<path d="M 286 178 C 338 142 402 168 402 216 '
        f'C 402 258 372 270 330 282 C 288 294 270 316 284 346 '
        f'C 302 384 370 386 410 344" {_s(c, 34)}/>'
    )


def aerial_views_sun_dunes(c):
    """Aerial Views: low sun behind two asymmetric landscape layers."""
    return (
        f'<circle cx="256" cy="160" r="58" {_s(c, 30)}/>'
        f'<path d="M 86 360 C 142 292 188 248 238 270 '
        f'C 286 292 318 250 426 356" {_s(c, 34)}/>'
        f'<path d="M 78 398 C 144 342 198 326 246 344 '
        f'C 302 366 344 316 434 386" {_s(c, 34)}/>'
    )


def anydesk_chevrons(c):
    """AnyDesk: opposed linked diamond-chevron forms with an open gap."""
    return (
        f'<path d="M 64 256 L 150 170 L 236 256 L 150 342 '
        f'L 116 308 L 168 256 L 116 204 Z" {_s(c, 34)}/>'
        f'<path d="M 276 256 L 362 170 L 448 256 L 362 342 '
        f'L 328 308 L 380 256 L 328 204 Z" {_s(c, 34)}/>'
    )


def dw_circles(c):
    """DW: two overlapping circular bodies carrying a D and a W.

    The first version filled the right body solid and cut the W out of it with
    an SVG mask. That reproduced the brand faithfully but broke the pack two
    ways: <defs>, <mask> and three fill attributes are all forbidden under
    core_monoline, and two of its four counters closed by 48px. Ink measured
    30.8 per cent at 48px, inside the 34 per cent slab ceiling but with no
    headroom left to thicken anything. It could not be opted into the monoline
    contract as drawn.

    Redrawn as strokes only. The identity that survives reduction is the pair
    of overlapping circles with a letter in each, not the figure-ground
    inversion, so the W is now drawn rather than subtracted. The bodies are
    also pushed further apart - centres 152 apart instead of 106 - because two
    outlines overlapping as heavily as two filled shapes did produces a busy
    lens that reads as neither letter.
    """
    return (
        f'<circle cx="180" cy="256" r="112" {_s(c, 32)}/>'
        f'<circle cx="332" cy="256" r="112" {_s(c, 32)}/>'
        f'<path d="M 142 190 L 142 322" {_s(c, 26)}/>'
        f'<path d="M 142 190 L 178 190 C 214 190 232 216 232 256 '
        f'C 232 296 214 322 178 322 L 142 322" {_s(c, 26)}/>'
        f'<path d="M 282 196 L 302 320 L 332 242 L 362 320 L 382 196" '
        f'{_s(c, 26)}/>'
    )


GLYPHS.update({
    "airscreen_as": airscreen_as,
    "aerial_views_sun_dunes": aerial_views_sun_dunes,
    "anydesk_chevrons": anydesk_chevrons,
    "dw_circles": dw_circles,
})


# ---------------------------------------------------------------------------
# Tranche 2026-09-16 — eight redraw-ready candidates.
#
# Geometry cues come from docs/research/icon-tranche-research-2026-09-15.md
# (ranks 12, 13, 14, 15, 17, 18, 20, 24). Each mark is redrawn from the
# recorded *identifying geometry* in Core Builds linework. No source art is
# traced, and no solid fills or containers are used: the pack's identity takes
# precedence over literal vendor reproduction (AGENTS.md).
# ---------------------------------------------------------------------------


def crossy_chicken(c):
    """Crossy Road - the blocky fowl in profile, above a lane stripe.

    The cue is a pixel chicken silhouette. A first pass drew the whole bird as
    stepped orthogonal segments and it failed its own review: at 96px it read
    as an abstract blocky figure, not a chicken, and the free-floating lane
    stripes read as three unrelated marks.

    A head in profile carries the identity at tile size where a body cannot -
    comb, beak and eye are the features a viewer actually resolves. The
    staircase edge is kept on the comb and nape so the pixel origin still
    shows. Deliberately minimal, which is also the right answer to the
    trademark caution the research attaches to this row.

    A second pass was needed after measurement: the eye at r=15 and the boxed
    beak both closed into blobs by 48px. The eye is now large enough to hold a
    counter all the way down, and the beak is an open chevron rather than a
    rectangle, so it has no counter to lose in the first place.
    """
    return (f'<path d="M 200 148 L 200 112 L 236 112 L 236 148 L 272 148 '
            f'L 272 112 L 308 112 L 308 180" {_s(c, 26)}/>'
            f'<path d="M 200 148 L 168 148 L 168 304 C 168 348 204 380 '
            f'248 380 L 292 380 C 322 380 344 358 344 328 L 344 188 '
            f'L 308 180" {_s(c, 30)}/>'
            f'<path d="M 344 202 L 408 232 L 344 262" {_s(c, 24)}/>'
            f'<circle cx="264" cy="236" r="36" {_s(c, 20)}/>')


def blokada_shield(c):
    """Blokada 4 (org.blokada.fyra) - shield split by descending bands.

    The legacy v4 mark is a shield whose face carries three diagonal bands
    stepping down to the right. The pack forbids solid fills, so the bands are
    drawn as strokes inside the shield outline; that keeps the split-face
    read, which is the part that distinguishes Blokada from every other
    generic shield in the vpn family.

    The first version of this drew the bands HORIZONTAL while this docstring
    already said diagonal - the code contradicted its own documentation, and
    a horizontal split reads as a generic striped shield rather than as
    Blokada. Review caught it. The bands now descend left-to-right on a
    common slope, which is the cue the research actually records.
    """
    return (f'<path d="M 256 88 L 404 148 L 404 262 C 404 344 336 396 256 422 '
            f'C 176 396 108 344 108 262 L 108 148 Z" {_s(c, 32)}/>'
            f'<path d="M 150 186 L 326 246" {_s(c, 24)}/>'
            f'<path d="M 142 258 L 340 326" {_s(c, 24)}/>'
            f'<path d="M 172 330 L 310 378" {_s(c, 24)}/>')


def pia_robot(c):
    """Private Internet Access - the robot head, squared with a lock jaw.

    PIA's mark is a robot/lock head silhouette. The identifying features are
    the flat-topped head with a stub antenna, two wide-set eyes, and the
    keyhole-ish mouth slot; those survive reduction, the fine bezel detail of
    the real mark does not, so it is dropped rather than rendered as mush.
    """
    return (f'<path d="M 256 84 L 256 122" {_s(c, 24)}/>'
            f'<path d="M 140 122 L 372 122 C 392 122 404 136 404 156 '
            f'L 404 334 C 404 354 392 368 372 368 L 140 368 '
            f'C 120 368 108 354 108 334 L 108 156 '
            f'C 108 136 120 122 140 122 Z" {_s(c, 32)}/>'
            f'<circle cx="196" cy="212" r="26" {_s(c, 24)}/>'
            f'<circle cx="316" cy="212" r="26" {_s(c, 24)}/>'
            f'<path d="M 196 300 L 316 300" {_s(c, 26)}/>'
            f'<path d="M 168 368 L 168 416 M 344 368 L 344 416" {_s(c, 24)}/>')


def mpv_play(c):
    """mpv - the stepped circular play mechanism.

    mpv's icon reads as a play triangle sitting inside a ring whose rim is
    stepped rather than smooth. The steps are the distinguishing cue against
    the several other ring-plus-triangle marks in the pack, so they are drawn
    as four short chords cut across the ring at the diagonals.
    """
    return (f'<circle cx="256" cy="256" r="178" {_s(c, 32)}/>'
            f'<path d="M 214 174 L 342 256 L 214 338 Z" {_s(c, 28)}/>'
            f'<path d="M 122 186 L 168 210 M 390 186 L 344 210 '
            f'M 122 326 L 168 302 M 390 326 L 344 302" {_s(c, 22)}/>')


def audiomack_wave(c):
    """Audiomack - the rising asymmetric waveform.

    The recorded cue is leading dots, a rising asymmetric waveform with one
    dominant downstroke, and a terminal pulse.

    The first version drew this as bars on a baseline, evenly spaced 48px
    apart. That is an equaliser, which is exactly the generic construction
    already in the music family and exactly what this redraw exists to
    replace - and the original docstring claimed an off-centre tall bar
    prevented it, which it did not. Review caught it.

    It is now an actual traced waveform: a single polyline whose oscillations
    start small and grow, spike to a sharp peak, and fall through the
    dominant downstroke before settling. Spacing is deliberately unequal, so
    no reading of it recovers a bar chart.
    """
    return (f'<circle cx="98" cy="256" r="11" {_s(c, 20)}/>'
            f'<circle cx="134" cy="256" r="11" {_s(c, 20)}/>'
            f'<path d="M 168 256 L 192 226 L 212 286 L 240 190 L 266 318 '
            f'L 296 122 L 320 390 L 348 232 L 372 278" {_s(c, 26)}/>'
            f'<path d="M 406 214 L 406 298" {_s(c, 22)}/>')


def atres_chevrons(c):
    """ATRESplayer - two nested right-facing chevrons.

    The research explicitly supersedes the older circular/radiating treatment:
    the current exact-package icon is two nested angular play outlines. Drawn
    as open chevrons, not filled triangles, so it stays inside the monoline
    contract and stays distinct from the pack's ordinary play glyphs.
    """
    return (f'<path d="M 150 120 L 286 256 L 150 392" {_s(c, 32)}/>'
            f'<path d="M 268 168 L 356 256 L 268 344" {_s(c, 26)}/>')


def kinopoisk_k(c):
    """Kinopoisk - the K whose right arms open into tapered rays.

    The asymmetric ray silhouette is the whole identity here, so the upright
    stem stays plain and the three right-hand strokes fan at unequal angles
    and unequal lengths. A symmetric fan would read as a generic burst and
    lose the brand.

    The first pass set the rays as separate ticks floating off the arms. They
    read as noise at 48px rather than as rays, so the arms now simply run
    long: the fan is continuous with the letter, which is what makes it a K
    with rays instead of a K beside some marks.
    """
    return (f'<path d="M 156 100 L 156 412" {_s(c, 32)}/>'
            f'<path d="M 156 264 L 372 96" {_s(c, 28)}/>'
            f'<path d="M 156 264 L 404 232" {_s(c, 24)}/>'
            f'<path d="M 156 264 L 356 336" {_s(c, 26)}/>'
            f'<path d="M 156 264 L 300 420" {_s(c, 28)}/>')


def aida_sixty_four(c):
    """AIDA64 - interlocked 6 and 4 with sharply cut counters.

    The cue is a bold, tightly interlocked '64' with a diagonal upper stroke.
    The two numerals are drawn as open forms sharing a tight gutter; their
    counters are large enough to survive the 96px tile, which is the binding
    constraint on a two-numeral mark at this size.
    """
    return (f'<path d="M 212 118 C 158 118 128 176 128 256 '
            f'C 128 344 168 396 212 396 C 256 396 284 356 284 312 '
            f'C 284 266 254 232 212 232 C 172 232 140 262 134 300" '
            f'{_s(c, 32)}/>'
            f'<path d="M 386 118 L 306 306 L 446 306" {_s(c, 32)}/>'
            f'<path d="M 400 216 L 400 396" {_s(c, 32)}/>')


GLYPHS.update({
    "crossy_chicken": crossy_chicken,
    "blokada_shield": blokada_shield,
    "pia_robot": pia_robot,
    "mpv_play": mpv_play,
    "audiomack_wave": audiomack_wave,
    "atres_chevrons": atres_chevrons,
    "kinopoisk_k": kinopoisk_k,
    "aida_sixty_four": aida_sixty_four,
})


# Brand-informed marks, batch 1 (2026-09-26): long-tail apps whose official
# launcher icon is a symbol rather than a wordmark. Each is the defining shape
# of that icon redrawn in Core monoline - the WuPlay / Nuvio method - not a
# trace: the official icon was the reference, the geometry is original.
# Reference icons: the app's Google Play listing (see each row's
# color_source in tools/catalog.json).
# ==========================================================================

def zeus_bolt(c):
    """Zeus: the solid lightning bolt of its red tile. Monoline normalises
    every stroke to 32 at most, so a stroked bolt read as a scribble; the
    bolt is a filled shape, wide enough that no sliver closes at 48px."""
    return ('<path d="M 300 60 L 128 300 L 246 300 L 206 452 L 390 196 '
            'L 270 196 L 332 60 Z" stroke="' + c + '" stroke-width="18" '
            'stroke-linecap="round" stroke-linejoin="round" fill="' + c + '"/>')


def ard_one(c):
    """ARD Mediathek: the ring-and-1 of the ARD lockup."""
    return (f'<circle cx="256" cy="256" r="164" {_s(c, 32)}/>'
            f'<path d="M 214 196 L 272 164 L 272 352" {_s(c, 34)}/>')


def dr_play(c):
    """DRTV: DR's open ring with a play inside it."""
    return (f'<path d="M 256 104 A 156 156 0 1 1 166 132" {_s(c, 32)}/>'
            f'<path d="M 256 84 L 256 150" {_s(c, 30)}/>'
            f'<path d="M 222 196 L 330 260 L 222 324 Z" {_s(c, 28)}/>')


def cinemaghar_arrow(c):
    """Cinemaghar TV: its notched play arrow."""
    return (f'<path d="M 144 92 L 414 256 L 144 420 L 236 256 Z" {_s(c, 32)}/>')


def appnotifier_check(c):
    """App Notifier: the store triangle with a tick through it."""
    return (f'<path d="M 132 96 L 412 256 L 132 416 Z" {_s(c, 32)}/>'
            f'<path d="M 196 262 L 250 318 L 372 176" {_s(c, 30)}/>')


def apk_installer_robot(c):
    """APK Installer: the robot head above a download wedge."""
    return (f'<path d="M 150 238 A 106 106 0 0 1 362 238 Z" {_s(c, 28)}/>'
            f'<path d="M 196 152 L 170 108 M 316 152 L 342 108" {_s(c, 22)}/>'
            f'<circle cx="214" cy="202" r="13" {_f(c)}/>'
            f'<circle cx="298" cy="202" r="13" {_f(c)}/>'
            f'<path d="M 150 282 L 362 282 L 256 420 Z" {_s(c, 28)}/>')


def torrent_search_lens(c):
    """Torrent Search: a lens holding a double download chevron."""
    return (f'<circle cx="276" cy="222" r="130" {_s(c, 32)}/>'
            f'<path d="M 184 316 L 104 400" {_s(c, 38)}/>'
            f'<path d="M 230 168 L 276 212 L 322 168 M 230 226 L 276 270 L 322 226" '
            f'{_s(c, 26)}/>')


def speaker_boost(c):
    """Speaker Boost: a speaker sending two waves."""
    return (f'<path d="M 96 206 L 164 206 L 250 132 L 250 380 L 164 306 L 96 306 Z" '
            f'{_s(c, 30)}/>'
            f'<path d="M 310 196 C 340 226 340 286 310 316" {_s(c, 28)}/>'
            f'<path d="M 364 142 C 424 204 424 308 364 370" {_s(c, 28)}/>')


def snapcast_ring(c):
    """Snapcast: a speaker broadcasting both ways inside its disc."""
    return (f'<circle cx="256" cy="256" r="190" {_s(c, 30)}/>'
            f'<path d="M 206 228 L 236 228 L 276 196 L 276 316 L 236 284 L 206 284 Z" '
            f'{_s(c, 24)}/>'
            f'<path d="M 318 214 C 336 236 336 276 318 298" {_s(c, 22)}/>'
            f'<path d="M 156 214 C 138 236 138 276 156 298" {_s(c, 22)}/>'
            f'<path d="M 364 178 C 400 222 400 290 364 334" {_s(c, 22)}/>'
            f'<path d="M 110 178 C 74 222 74 290 110 334" {_s(c, 22)}/>')


def hubitat_home(c):
    """Hubitat: the house with its chimney and a tablet inside."""
    return (f'<path d="M 88 250 L 256 104 L 424 250" {_s(c, 32)}/>'
            f'<path d="M 136 216 L 136 416 L 376 416 L 376 216" {_s(c, 30)}/>'
            f'<path d="M 344 176 L 344 120" {_s(c, 30)}/>'
            f'<rect x="192" y="278" width="128" height="80" rx="16" {_s(c, 24)}/>')


def twilight_sunset(c):
    """Twilight: the half sun on the horizon, with its reflection."""
    return (f'<path d="M 150 272 A 106 106 0 0 1 362 272 Z" {_s(c, 30)}/>'
            f'<path d="M 88 272 L 424 272" {_s(c, 30)}/>'
            f'<path d="M 164 336 L 348 336 M 208 396 L 304 396" {_s(c, 26)}/>')


def yowindow_sun(c):
    """YoWindow: the winking sun."""
    import math
    rays = "".join(
        f'M {256 + 150 * math.cos(a):.0f} {256 + 150 * math.sin(a):.0f} '
        f'L {256 + 196 * math.cos(a):.0f} {256 + 196 * math.sin(a):.0f} '
        for a in (k * math.pi / 4 for k in range(8)))
    return (f'<circle cx="256" cy="256" r="104" {_s(c, 30)}/>'
            f'<path d="{rays}" {_s(c, 28)}/>'
            f'<path d="M 206 236 C 216 222 232 222 242 236" {_s(c, 20)}/>'
            f'<circle cx="300" cy="232" r="12" {_f(c)}/>'
            f'<path d="M 214 282 C 236 312 276 312 298 282" {_s(c, 22)}/>')


def dropsync_cube(c):
    """Dropsync: the cube inside its hexagon."""
    import math
    hexp = " ".join(f"{256 + 196 * math.cos(math.radians(-90 + 60 * k)):.0f},"
                    f"{256 + 196 * math.sin(math.radians(-90 + 60 * k)):.0f}"
                    for k in range(6))
    return (f'<polygon points="{hexp}" {_s(c, 30)}/>'
            f'<path d="M 256 164 L 350 214 L 350 316 L 256 366 L 162 316 L 162 214 Z" '
            f'{_s(c, 26)}/>'
            f'<path d="M 162 214 L 256 264 L 350 214 M 256 264 L 256 366" {_s(c, 24)}/>')


def scholastic_book(c):
    """Scholastic: the open book."""
    return (f'<path d="M 256 150 C 210 118 144 114 84 130 L 84 392 '
            f'C 144 378 210 382 256 414 C 302 382 368 378 428 392 L 428 130 '
            f'C 368 114 302 118 256 150 Z" {_s(c, 30)}/>'
            f'<path d="M 256 150 L 256 414" {_s(c, 26)}/>')


def zapp_tv(c):
    """Zapp: the rounded retro set with its aerial and feet."""
    return (f'<rect x="92" y="170" width="328" height="226" rx="70" {_s(c, 32)}/>'
            f'<path d="M 212 170 L 176 106 M 300 170 L 336 106" {_s(c, 24)}/>'
            f'<circle cx="172" cy="96" r="16" {_f(c)}/>'
            f'<circle cx="340" cy="96" r="16" {_f(c)}/>'
            f'<path d="M 150 396 L 128 440 M 362 396 L 384 440" {_s(c, 26)}/>')


def mango_m(c):
    """Mango TV: the M set in its rounded screen."""
    return (f'<path d="M 360 96 L 170 96 C 124 96 96 124 96 170 L 96 342 '
            f'C 96 388 124 416 170 416 L 342 416 C 388 416 416 388 416 342 '
            f'L 416 190" {_s(c, 32)}/>'
            f'<path d="M 180 356 L 180 204 L 256 292 L 332 204 L 332 356" {_s(c, 32)}/>')


def youku_play(c):
    """Youku: the two-piece play - a long upper blade and a short lower one.
    Drawn solid (as strokes they read as a '>' sign); the catalog's duotone
    paints part 1, the lower blade, in the brand's orange."""
    def blade(d):
        return ('<path d="' + d + '" stroke="' + c + '" stroke-width="18" '
                'stroke-linecap="round" stroke-linejoin="round" fill="' + c + '"/>')
    return (blade("M 150 104 L 404 238 C 422 248 422 272 404 282 L 364 304 "
                  "L 150 188 Z") +
            blade("M 150 404 L 150 322 L 286 250 L 350 290 Z"))


def tving_tv(c):
    """TVING: the T whose stem opens into a V."""
    return (f'<path d="M 108 132 L 404 132" {_s(c, 34)}/>'
            f'<path d="M 184 132 L 256 400 L 328 132" {_s(c, 34)}/>')


def flextv_cat(c):
    """Flex TV: the set with cat ears and a solid play - the icon's inner
    ring closed three counters at 48px, the play alone survives."""
    play = (f'<path d="M 226 240 L 306 285 L 226 330 Z" stroke="{c}" '
            f'stroke-width="16" stroke-linecap="round" stroke-linejoin="round" '
            f'fill="{c}"/>')
    return (f'<rect x="92" y="150" width="328" height="270" rx="84" {_s(c, 30)}/>'
            f'<path d="M 150 156 L 176 108 L 214 152 Z M 298 152 L 336 108 L 362 156 Z" '
            f'stroke="{c}" stroke-width="18" stroke-linecap="round" '
            f'stroke-linejoin="round" fill="{c}"/>' + play)


def kreate_k(c):
    """Kreate: the K inside its ring."""
    return (f'<circle cx="256" cy="256" r="186" {_s(c, 30)}/>'
            f'<path d="M 204 156 L 204 356" {_s(c, 34)}/>'
            f'<path d="M 326 156 L 214 262 L 326 356" {_s(c, 34)}/>')


GLYPHS.update({
    "zeus_bolt": zeus_bolt, "ard_one": ard_one, "dr_play": dr_play,
    "cinemaghar_arrow": cinemaghar_arrow, "appnotifier_check": appnotifier_check,
    "apk_installer_robot": apk_installer_robot,
    "torrent_search_lens": torrent_search_lens, "speaker_boost": speaker_boost,
    "snapcast_ring": snapcast_ring, "hubitat_home": hubitat_home,
    "twilight_sunset": twilight_sunset, "yowindow_sun": yowindow_sun,
    "dropsync_cube": dropsync_cube, "scholastic_book": scholastic_book,
    "zapp_tv": zapp_tv, "mango_m": mango_m, "youku_play": youku_play,
    "tving_tv": tving_tv, "flextv_cat": flextv_cat, "kreate_k": kreate_k,
})


# Brand-informed marks, batch 2 (2026-09-26). Same method as batch 1.

def _solid(d, c, sw=16):
    """A filled shape with a rounded edge, for parts that must read solid."""
    return (f'<path d="{d}" stroke="{c}" stroke-width="{sw}" stroke-linecap="round" '
            f'stroke-linejoin="round" fill="{c}"/>')


def aicam_camera(c):
    """AI Cam View: the video camera - body and lens horn."""
    return (f'<rect x="80" y="166" width="262" height="180" rx="42" {_s(c, 32)}/>'
            f'<path d="M 342 226 L 428 178 L 428 334 L 342 286" {_s(c, 30)}/>')


def aircast_screen(c):
    """Aircast: a screen whose corner opens onto cast waves."""
    return (f'<path d="M 96 250 L 96 158 C 96 136 114 118 136 118 L 376 118 '
            f'C 398 118 416 136 416 158 L 416 334 C 416 356 398 374 376 374 L 290 374" '
            f'{_s(c, 32)}/>'
            f'<path d="M 96 312 A 88 88 0 0 1 184 400" {_s(c, 28)}/>'
            f'<path d="M 96 380 A 20 20 0 0 1 116 400" {_s(c, 28)}/>')


def airplay_screen(c):
    """AirPlay Receiver: the screen with the AirPlay wedge rising into it."""
    return (f'<path d="M 204 330 L 136 330 C 114 330 96 312 96 290 L 96 150 '
            f'C 96 128 114 110 136 110 L 376 110 C 398 110 416 128 416 150 L 416 290 '
            f'C 416 312 398 330 376 330 L 308 330" {_s(c, 32)}/>'
            + _solid("M 256 296 L 340 410 L 172 410 Z", c))


def audials_radio(c):
    """Audials: the radio - body, handle, speaker and tuning lines."""
    return (f'<rect x="84" y="164" width="344" height="236" rx="46" {_s(c, 32)}/>'
            f'<path d="M 170 164 L 330 100" {_s(c, 26)}/>'
            f'<circle cx="330" cy="282" r="58" {_s(c, 28)}/>'
            f'<path d="M 138 240 L 222 240 M 138 324 L 222 324" {_s(c, 26)}/>')


def bstation_tv(c):
    """Bstation: the TV face - aerial ears, slanted eyes, a small mouth."""
    return (f'<rect x="84" y="152" width="344" height="262" rx="66" {_s(c, 32)}/>'
            f'<path d="M 184 152 L 150 98 M 328 152 L 362 98" {_s(c, 28)}/>'
            f'<path d="M 168 252 L 222 276 M 344 252 L 290 276" {_s(c, 28)}/>'
            f'<path d="M 222 334 L 240 350 L 256 334 L 272 350 L 290 334" {_s(c, 22)}/>')


def canal_plus(c):
    """CANAL+: the plus that is the brand's whole icon."""
    return f'<path d="M 256 96 L 256 416 M 96 256 L 416 256" {_s(c, 32)}/>'


def capsule_mic(c):
    """Capsule: the studio microphone in its cradle."""
    return (f'<rect x="196" y="80" width="120" height="212" rx="60" {_s(c, 30)}/>'
            f'<path d="M 142 244 C 142 326 196 368 256 368 C 316 368 370 326 370 244" '
            f'{_s(c, 28)}/>'
            f'<path d="M 256 368 L 256 424 M 196 428 L 316 428" {_s(c, 28)}/>')


def cpu_chip(c):
    """CPU Info: the chip with its die and pins."""
    pins = " ".join(f"M {x} 136 L {x} 88 M {x} 376 L {x} 424" for x in (196, 256, 316))
    pins += " " + " ".join(f"M 136 {y} L 88 {y} M 376 {y} L 424 {y}" for y in (196, 256, 316))
    return (f'<rect x="136" y="136" width="240" height="240" rx="32" {_s(c, 30)}/>'
            f'<rect x="210" y="210" width="92" height="92" rx="14" {_s(c, 24)}/>'
            f'<path d="{pins}" {_s(c, 22)}/>')


def drm_lock(c):
    """DRM Info: the padlock with its keyhole."""
    return (f'<rect x="124" y="226" width="264" height="204" rx="42" {_s(c, 32)}/>'
            f'<path d="M 180 226 L 180 166 C 180 72 332 72 332 166 L 332 226" {_s(c, 30)}/>'
            + _solid("M 256 290 L 256 358", c, 30))


def epsxe_pad(c):
    """ePSXe: the controller - grips, d-pad and face buttons."""
    return (f'<path d="M 164 140 L 348 140 C 410 140 440 214 440 296 C 440 364 414 404 380 404 '
            f'C 346 404 330 370 314 334 L 198 334 C 182 370 166 404 132 404 '
            f'C 98 404 72 364 72 296 C 72 214 102 140 164 140 Z" {_s(c, 30)}/>'
            f'<path d="M 162 204 L 162 280 M 124 242 L 200 242" {_s(c, 24)}/>'
            f'<circle cx="344" cy="214" r="15" {_f(c)}/>'
            f'<circle cx="384" cy="256" r="15" {_f(c)}/>')


def flickfolio_grid(c):
    """Flickfolio: the three-by-three photo grid, as small solid tiles."""
    tiles = ""
    for y in (104, 224, 344):
        for x in (104, 224, 344):
            tiles += _solid(f"M {x} {y} L {x + 64} {y} L {x + 64} {y + 64} L {x} {y + 64} Z",
                            c, 12)
    return tiles


def flicky_butterfly(c):
    """Flicky: the butterfly - two broad upper wings, two small lower ones."""
    return (f'<path d="M 256 250 C 226 150 132 104 104 170 C 84 222 150 262 256 250 '
            f'C 362 262 428 222 408 170 C 380 104 286 150 256 250 Z" {_s(c, 28)}/>'
            f'<path d="M 256 262 C 196 282 158 350 196 386 C 232 414 254 340 256 262 '
            f'C 258 340 280 414 316 386 C 354 350 316 282 256 262 Z" {_s(c, 26)}/>')


def geticon_lens(c):
    """Get Icon: a picture with a magnifier on its corner."""
    return (f'<path d="M 272 344 L 132 344 C 110 344 92 326 92 304 L 92 132 '
            f'C 92 110 110 92 132 92 L 304 92 C 326 92 344 110 344 132 L 344 272" '
            f'{_s(c, 30)}/>'
            f'<path d="M 132 300 L 196 224 L 240 272 L 266 246" {_s(c, 24)}/>'
            f'<circle cx="352" cy="352" r="58" {_s(c, 28)}/>'
            f'<path d="M 394 394 L 436 436" {_s(c, 32)}/>')


def hue_bulb(c):
    """Hue Shortcuts: the bulb with its S-shaped filament and screw collar."""
    return (f'<path d="M 204 336 C 158 306 138 258 138 216 C 138 146 192 92 256 92 '
            f'C 320 92 374 146 374 216 C 374 258 354 306 308 336 Z" {_s(c, 30)}/>'
            f'<path d="M 286 150 C 226 140 214 196 256 210 C 298 224 290 282 226 274" '
            f'{_s(c, 22)}/>'
            f'<path d="M 206 380 L 306 380 M 226 424 L 286 424" {_s(c, 28)}/>')


def pikpak_robot(c):
    """PikPak: the round robot face - aerials, two eyes, a smile."""
    return (f'<rect x="100" y="158" width="312" height="252" rx="84" {_s(c, 32)}/>'
            f'<path d="M 196 158 L 196 108 M 316 158 L 316 108" {_s(c, 26)}/>'
            f'<circle cx="208" cy="266" r="20" {_f(c)}/>'
            f'<circle cx="304" cy="266" r="20" {_f(c)}/>'
            f'<path d="M 214 330 C 238 352 274 352 298 330" {_s(c, 24)}/>')


def quicksupport_arrows(c):
    """QuickSupport: the two-way arrow inside its ring."""
    return (f'<circle cx="256" cy="256" r="184" {_s(c, 30)}/>'
            f'<path d="M 150 256 L 362 256" {_s(c, 30)}/>'
            f'<path d="M 198 206 L 148 256 L 198 306 M 314 206 L 364 256 L 314 306" '
            f'{_s(c, 30)}/>')


def tabii_star(c):
    """Tabii: the eight-point burst with its open centre."""
    import math
    pts = " ".join(
        f"{256 + (190 if k % 2 == 0 else 128) * math.cos(math.pi * k / 8 - math.pi / 2):.0f},"
        f"{256 + (190 if k % 2 == 0 else 128) * math.sin(math.pi * k / 8 - math.pi / 2):.0f}"
        for k in range(16))
    return (f'<polygon points="{pts}" {_s(c, 30)}/>'
            f'<circle cx="256" cy="256" r="54" {_s(c, 26)}/>')


def unifi_camera(c):
    """UniFi Protect: the upright camera with its lens and stand."""
    return (f'<rect x="176" y="84" width="160" height="268" rx="80" {_s(c, 32)}/>'
            f'<circle cx="256" cy="180" r="38" {_s(c, 26)}/>'
            f'<path d="M 256 352 L 256 404 M 186 424 L 326 424" {_s(c, 28)}/>')


def vidangel_halo(c):
    """VidAngel: the screen with a halo above it and a play inside."""
    return (f'<rect x="100" y="188" width="312" height="228" rx="46" {_s(c, 32)}/>'
            f'<path d="M 150 132 C 190 86 322 86 362 132" {_s(c, 28)}/>'
            + _solid("M 230 256 L 300 302 L 230 348 Z", c, 16))


def myradar_pin(c):
    """MyRadar: the map pin."""
    return (f'<path d="M 256 440 C 196 360 136 300 136 218 C 136 146 192 92 256 92 '
            f'C 320 92 376 146 376 218 C 376 300 316 360 256 440 Z" {_s(c, 32)}/>'
            f'<circle cx="256" cy="218" r="48" {_s(c, 28)}/>')


def thmanyah_arrow(c):
    """Thmanyah: the rising arrowhead."""
    return (f'<path d="M 256 92 L 420 412 L 256 324 L 92 412 Z" {_s(c, 32)}/>')


GLYPHS.update({
    "aicam_camera": aicam_camera, "aircast_screen": aircast_screen,
    "airplay_screen": airplay_screen, "audials_radio": audials_radio,
    "bstation_tv": bstation_tv, "canal_plus": canal_plus, "capsule_mic": capsule_mic,
    "cpu_chip": cpu_chip, "drm_lock": drm_lock, "epsxe_pad": epsxe_pad,
    "flickfolio_grid": flickfolio_grid, "flicky_butterfly": flicky_butterfly,
    "geticon_lens": geticon_lens, "hue_bulb": hue_bulb, "pikpak_robot": pikpak_robot,
    "quicksupport_arrows": quicksupport_arrows, "tabii_star": tabii_star,
    "unifi_camera": unifi_camera, "vidangel_halo": vidangel_halo,
    "myradar_pin": myradar_pin, "thmanyah_arrow": thmanyah_arrow,
})


# --------------------------------------------------------------------------
# Brand-informed marks, batch 3 (2026-09-26). Same contract as batches 1-2:
# each is the defining shape of the app's own launcher icon, redrawn on the
# Core grid; the reference listing is recorded in the catalogue row.

def _polar(cx, cy, r, deg):
    import math
    a = math.radians(deg)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def amnis_ff(c):
    """Amnis: the fast-forward pair inside its disc."""
    return (f'<circle cx="256" cy="256" r="172" {_s(c, 32)}/>'
            + _solid("M 184 196 L 250 256 L 184 316 Z", c, 18)
            + _solid("M 262 196 L 328 256 L 262 316 Z", c, 18))


def buttons_swap(c):
    """Buttons Remapper: stacked windows and the two swap arrows."""
    return (f'<rect x="96" y="140" width="122" height="84" rx="16" {_s(c, 26)}/>'
            f'<path d="M 136 104 L 238 104 C 250 104 256 112 256 122 L 256 186" {_s(c, 22)}/>'
            f'<path d="M 300 118 C 362 112 404 150 404 210" {_s(c, 28)}/>'
            f'<path d="M 370 184 L 404 218 L 436 182" {_s(c, 28)}/>'
            f'<path d="M 212 394 C 150 400 108 362 108 302" {_s(c, 28)}/>'
            f'<path d="M 76 330 L 108 294 L 142 328" {_s(c, 28)}/>'
            f'<path d="M 414 404 L 414 364 C 414 340 398 326 374 326 L 298 326" {_s(c, 26)}/>'
            f'<path d="M 330 294 L 298 326 L 330 358" {_s(c, 26)}/>')


def dsmart_ring(c):
    """D-Smart: the heavy ring with its broad tail running out to the left."""
    return (f'<circle cx="304" cy="226" r="116" {_s(c, 32)}/>'
            f'<path d="M 190 206 L 72 316 L 72 390 L 222 318" {_s(c, 30)}/>')


def debridemall_magnet(c):
    """Debrid Em All: the U magnet inside its six-sided badge."""
    return (f'<path d="M 156 112 L 356 112 L 440 256 L 356 400 L 156 400 L 72 256 Z" {_s(c, 30)}/>'
            f'<path d="M 196 186 L 196 262 C 196 342 316 342 316 262 L 316 186" {_s(c, 30)}/>'
            f'<path d="M 172 186 L 220 186 M 292 186 L 340 186" {_s(c, 26)}/>')


def feeln_crown(c):
    """Feeln: the crown on its halo, with the plus beside it."""
    tips = "".join(f'<circle cx="{x}" cy="{y}" r="16" {_f(c)}/>'
                   for x, y in ((84, 150), (196, 118), (308, 150)))
    return (f'<path d="M 108 318 L 88 176 L 150 236 L 196 146 L 242 236 L 304 176 '
            f'L 284 318 Z" {_s(c, 28)}/>' + tips
            + f'<ellipse cx="196" cy="378" rx="112" ry="30" {_s(c, 24)}/>'
            f'<path d="M 400 196 L 400 304 M 346 250 L 454 250" {_s(c, 30)}/>')


def fpb_ball(c):
    """FP Basquetebol: the basketball with its dotted progress arc."""
    dots = ""
    for deg in (-56, -28, 0, 28, 56):
        x, y = _polar(212, 256, 206, deg)
        dots += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="15" {_f(c)}/>'
    return (f'<circle cx="212" cy="256" r="146" {_s(c, 32)}/>'
            f'<path d="M 212 110 L 212 402 M 66 256 L 358 256" {_s(c, 22)}/>'
            f'<path d="M 116 146 C 166 196 166 316 116 366" {_s(c, 22)}/>'
            f'<path d="M 308 146 C 258 196 258 316 308 366" {_s(c, 22)}/>' + dots)


def immich_petals(c):
    """Immich: the five-petal pinwheel flower."""
    out = ""
    for k in range(5):
        base = -90 + 72 * k
        x0, y0 = _polar(256, 256, 34, base)
        x3, y3 = _polar(256, 256, 196, base + 10)
        x1, y1 = _polar(256, 256, 150, base - 30)
        x2, y2 = _polar(256, 256, 170, base + 44)
        out += (f'<path d="M {x0:.1f} {y0:.1f} C {x1:.1f} {y1:.1f} {x3:.1f} {y3:.1f} '
                f'{x3:.1f} {y3:.1f} C {x3:.1f} {y3:.1f} {x2:.1f} {y2:.1f} {x0:.1f} {y0:.1f} Z" '
                f'{_s(c, 24)}/>')
    return out


def kijk_eye(c):
    """KIJK: the almond eye with its solid pupil."""
    return (f'<path d="M 84 256 C 150 96 362 96 428 256 C 362 416 150 416 84 256 Z" {_s(c, 32)}/>'
            f'<circle cx="256" cy="256" r="64" {_f(c)}/>')


def livechannels_tv(c):
    """Live Channels: the rounded set with its two antennae."""
    return (f'<rect x="84" y="166" width="344" height="240" rx="48" {_s(c, 32)}/>'
            f'<path d="M 192 92 L 256 150 L 320 92" {_s(c, 28)}/>')


def moonfin_wave(c):
    """Moonfin: the crescent fin rising out of the waves."""
    return (_solid("M 348 88 C 196 96 104 232 150 342 L 236 318 "
                   "C 198 240 238 140 348 88 Z", c, 16)
            + f'<path d="M 96 382 C 160 346 224 410 288 378 C 340 352 392 372 432 350" {_s(c, 26)}/>'
            f'<path d="M 132 438 C 196 406 260 460 324 430 C 364 412 398 420 424 410" {_s(c, 22)}/>')


def netzkino_leader(c):
    """Netzkino: the film-leader countdown ring with its 1."""
    return (f'<circle cx="256" cy="256" r="176" {_s(c, 30)}/>'
            f'<path d="M 80 256 L 188 256 M 324 256 L 432 256 '
            f'M 256 80 L 256 132 M 256 380 L 256 432" {_s(c, 22)}/>'
            f'<path d="M 222 180 L 268 150 L 268 356" {_s(c, 32)}/>')


def photocollage_ring(c):
    """Photo Collage: the folded ribbon ring around a hexagonal window."""
    outer = [_polar(256, 256, 190, -90 + 60 * k) for k in range(6)]
    inner = [_polar(256, 256, 84, -60 + 60 * k) for k in range(6)]
    o = " L ".join(f"{x:.1f} {y:.1f}" for x, y in outer)
    i = " L ".join(f"{x:.1f} {y:.1f}" for x, y in inner)
    spokes = " ".join(f"M {inner[k][0]:.1f} {inner[k][1]:.1f} "
                      f"L {outer[(k + 1) % 6][0]:.1f} {outer[(k + 1) % 6][1]:.1f}"
                      for k in range(6))
    return (f'<path d="M {o} Z" {_s(c, 30)}/>'
            f'<path d="M {i} Z" {_s(c, 26)}/>'
            f'<path d="{spokes}" {_s(c, 22)}/>')


def ppsspp_pad(c):
    """PPSSPP: the four twisted paddles of its X-shaped pad."""
    out = ""
    for k in range(4):
        d = -90 + 90 * k
        pts = [_polar(256, 256, 62, d - 42), _polar(256, 256, 186, d - 6),
               _polar(256, 256, 190, d + 34), _polar(256, 256, 78, d + 22)]
        p = " L ".join(f"{x:.1f} {y:.1f}" for x, y in pts)
        out += f'<path d="M {p} Z" {_s(c, 24)}/>'
    return out


def seerr_eye(c):
    """SeerrTV: the lens ring, its iris and the catch-light."""
    return (f'<circle cx="256" cy="256" r="172" {_s(c, 32)}/>'
            f'<circle cx="256" cy="256" r="84" {_s(c, 28)}/>'
            f'<circle cx="318" cy="186" r="20" {_f(c)}/>')


def setedit_gear(c):
    """SetEdit: the six-tooth settings cog with its round hub."""
    pts = []
    for k in range(6):
        m = -90 + 60 * k
        pts += [_polar(256, 256, 146, m - 22), _polar(256, 256, 192, m - 12),
                _polar(256, 256, 192, m + 12), _polar(256, 256, 146, m + 22)]
    p = " L ".join(f"{x:.1f} {y:.1f}" for x, y in pts)
    return (f'<path d="M {p} Z" {_s(c, 30)}/>'
            f'<circle cx="256" cy="256" r="66" {_s(c, 28)}/>')


def sooner_rings(c):
    """Sooner: the two open double rings facing each other."""
    out = ""
    for cx, a0, a1 in ((178, 40, 320), (334, 220, 500)):
        for r, w in ((116, 26), (72, 22)):
            x0, y0 = _polar(cx, 256, r, a0)
            x1, y1 = _polar(cx, 256, r, a1)
            out += (f'<path d="M {x0:.1f} {y0:.1f} A {r} {r} 0 1 1 {x1:.1f} {y1:.1f}" '
                    f'{_s(c, w)}/>')
    return out


def unrealdebrid_magnet(c):
    """Unreal Debrid: the horseshoe magnet, tilted so its poles point down-left."""
    import math

    def q(x, y):
        k = math.sqrt(0.5)
        return f"{300 + (x - y) * k:.1f} {206 + (x + y) * k:.1f}"
    body = (f"M {q(-112, 170)} L {q(-112, 0)} A 112 112 0 0 1 {q(112, 0)} "
            f"L {q(112, 170)} L {q(44, 170)} L {q(44, 0)} A 44 44 0 0 0 {q(-44, 0)} "
            f"L {q(-44, 170)} Z")
    bands = f"M {q(-112, 104)} L {q(-44, 104)} M {q(44, 104)} L {q(112, 104)}"
    return (f'<path d="{body}" {_s(c, 28)}/>'
            f'<path d="{bands}" {_s(c, 22)}/>')


def rlc_shield(c):
    """RLC+: the shield with the plus inside its eye."""
    return (f'<path d="M 256 76 L 424 136 L 424 250 C 424 350 350 412 256 444 '
            f'C 162 412 88 350 88 250 L 88 136 Z" {_s(c, 30)}/>'
            f'<path d="M 256 212 L 256 316 M 204 264 L 308 264" {_s(c, 30)}/>')


def unext_shield(c):
    """U-NEXT: the U-shaped crest."""
    return (f'<path d="M 128 104 L 384 104 L 384 290 C 384 370 326 420 256 420 '
            f'C 186 420 128 370 128 290 Z" {_s(c, 32)}/>'
            f'<path d="M 204 164 L 204 290 C 204 340 308 340 308 290 L 308 164" {_s(c, 28)}/>')


GLYPHS.update({
    "amnis_ff": amnis_ff, "buttons_swap": buttons_swap, "dsmart_ring": dsmart_ring,
    "debridemall_magnet": debridemall_magnet, "feeln_crown": feeln_crown,
    "fpb_ball": fpb_ball, "immich_petals": immich_petals, "kijk_eye": kijk_eye,
    "livechannels_tv": livechannels_tv, "moonfin_wave": moonfin_wave,
    "netzkino_leader": netzkino_leader, "photocollage_ring": photocollage_ring,
    "ppsspp_pad": ppsspp_pad, "seerr_eye": seerr_eye, "setedit_gear": setedit_gear,
    "sooner_rings": sooner_rings, "unrealdebrid_magnet": unrealdebrid_magnet,
    "rlc_shield": rlc_shield, "unext_shield": unext_shield,
})


# --------------------------------------------------------------------------
# Brand-informed marks, batch 4 (2026-09-26). Same contract as batches 1-3.

def dish_d(c):
    """Dish Home: the rounded D with a play cut into its counter."""
    return (f'<path d="M 112 96 L 244 96 C 352 96 424 168 424 256 C 424 344 352 416 244 416 '
            f'L 112 416 Z" {_s(c, 32)}/>'
            + _solid("M 190 190 L 300 256 L 190 322 Z", c, 18))


def gymondo_g(c):
    """Gymondo: the looping G drawn as one continuous line."""
    return (f'<path d="M 372 150 C 320 90 204 88 144 152 C 84 216 90 330 158 384 '
            f'C 226 438 344 420 384 346 C 408 300 400 250 350 236 C 296 222 252 262 262 316 '
            f'C 272 368 336 372 370 336" {_s(c, 32)}/>')


def hippos_loop(c):
    """Hippos: the large ring and the small ring, with the bar slanting across."""
    return (f'<circle cx="310" cy="222" r="116" {_s(c, 30)}/>'
            f'<circle cx="146" cy="352" r="60" {_s(c, 28)}/>'
            f'<circle cx="146" cy="352" r="16" {_f(c)}/>'
            f'<path d="M 176 92 L 362 422" {_s(c, 34)}/>')


def juuno_j(c):
    """Juuno: the round dot over the block j with its curved foot."""
    return (f'<circle cx="300" cy="112" r="46" {_f(c)}/>'
            + _solid("M 258 196 L 342 196 L 342 330 C 342 390 300 428 242 428 "
                     "L 170 428 L 170 344 L 258 344 Z", c, 14))


def mediahub_play(c):
    """Media Hub: the play folded from two chevron ribbons."""
    return (f'<path d="M 128 92 L 300 256 L 128 420" {_s(c, 32)}/>'
            f'<path d="M 214 164 L 396 256 L 214 348" {_s(c, 30)}/>')


def movideo_doc(c):
    """Movideo: the page with its folded corner and a play."""
    return (f'<path d="M 120 76 L 318 76 L 398 156 L 398 436 L 120 436 Z" {_s(c, 30)}/>'
            f'<path d="M 318 76 L 318 156 L 398 156" {_s(c, 24)}/>'
            + _solid("M 212 214 L 312 276 L 212 338 Z", c, 16))


def nfb_eye(c):
    """NFB: the eye whose pupil is a person, head and shoulders."""
    return (f'<path d="M 60 232 C 140 118 372 118 452 232" {_s(c, 32)}/>'
            f'<path d="M 60 232 C 100 290 150 322 190 334" {_s(c, 28)}/>'
            f'<path d="M 452 232 C 412 290 362 322 322 334" {_s(c, 28)}/>'
            f'<circle cx="256" cy="244" r="54" {_f(c)}/>'
            + _solid("M 170 432 C 170 356 206 318 256 318 C 306 318 342 356 342 432 Z", c, 16))


def ondamedia_figure(c):
    """Ondamedia: the round head over its M-shaped stride."""
    return (f'<circle cx="256" cy="150" r="74" {_s(c, 30)}/>'
            f'<path d="M 128 424 L 196 262 L 256 352 L 316 262 L 384 424" {_s(c, 30)}/>')


def synology_rosette(c):
    """Synology Photos: the six-petal rosette of overlapping rings."""
    out = ""
    for k in range(6):
        x, y = _polar(256, 256, 88, -90 + 60 * k)
        out += f'<circle cx="{x:.1f}" cy="{y:.1f}" r="96" {_s(c, 22)}/>'
    return out


def tvoverlay_cards(c):
    """TvOverlay: the notification card floating over the screen behind it."""
    return (f'<path d="M 172 132 L 96 132 C 84 132 76 140 76 152 L 76 364 C 76 376 84 384 96 384 '
            f'L 150 384" {_s(c, 24)}/>'
            f'<rect x="172" y="170" width="264" height="190" rx="28" {_s(c, 30)}/>'
            f'<circle cx="236" cy="236" r="24" {_f(c)}/>'
            f'<path d="M 292 236 L 382 236 M 228 300 L 382 300" {_s(c, 24)}/>')


def veezie_play(c):
    """Veezie: the open play outline with its upright bar inside."""
    return (f'<path d="M 124 82 L 424 256 L 124 430 Z" {_s(c, 32)}/>'
            f'<path d="M 200 220 L 200 340" {_s(c, 30)}/>')


def wako_tv(c):
    """Wako: the retro set with its V antenna and two side knobs."""
    return (f'<rect x="64" y="162" width="384" height="256" rx="44" {_s(c, 30)}/>'
            f'<rect x="104" y="202" width="228" height="176" rx="26" {_s(c, 24)}/>'
            f'<circle cx="394" cy="244" r="20" {_f(c)}/>'
            f'<circle cx="394" cy="324" r="20" {_f(c)}/>'
            f'<path d="M 190 88 L 256 156 L 322 88" {_s(c, 26)}/>')


def instantbits_cast(c):
    """Web Video Cast (InstantBits): the set with cast waves and a play badge."""
    return (f'<rect x="72" y="166" width="300" height="236" rx="36" {_s(c, 30)}/>'
            f'<path d="M 150 102 L 206 160 M 294 102 L 238 160" {_s(c, 24)}/>'
            f'<path d="M 124 330 C 164 330 180 346 180 372" {_s(c, 24)}/>'
            f'<path d="M 124 264 C 204 264 246 306 246 372" {_s(c, 24)}/>'
            f'<circle cx="376" cy="170" r="72" {_s(c, 26)}/>'
            + _solid("M 358 136 L 408 170 L 358 204 Z", c, 12))


def couchpuzzle_tiles(c):
    """Couch Puzzles: the sliding-tile board, one tile sliding into the empty slot."""
    tiles = ""
    for r, y in enumerate((72, 208, 344)):
        for q, x in enumerate((72, 208, 344)):
            if (r, q) == (2, 2):
                continue
            tiles += f'<rect x="{x}" y="{y}" width="96" height="96" rx="20" {_s(c, 22)}/>'
    return tiles + (f'<path d="M 436 392 L 360 392 M 390 362 L 360 392 L 390 422" '
                    f'{_s(c, 22)}/>')


def torrserve_bolt(c):
    """TorrServe: the bolt striking through its ring."""
    return (f'<circle cx="256" cy="256" r="172" {_s(c, 30)}/>'
            + _solid("M 300 72 L 176 276 L 262 276 L 212 440 L 344 222 L 256 222 Z", c, 14))


def avoid_play(c):
    """Avoid: three nested play outlines."""
    return (f'<path d="M 108 76 C 88 64 72 74 72 98 L 72 414 C 72 438 88 448 108 436 '
            f'L 380 278 C 400 266 400 246 380 234 Z" {_s(c, 28)}/>'
            f'<path d="M 144 168 L 144 344 L 296 256 Z" {_s(c, 24)}/>'
            f'<path d="M 196 232 L 196 280 L 236 256 Z" {_s(c, 20)}/>')


def hueessentials_lamp(c):
    """Hue Essentials: the tall tapered lamp with its capped base."""
    return (f'<path d="M 160 84 L 352 84 L 318 318 L 194 318 Z" {_s(c, 30)}/>'
            f'<path d="M 214 318 L 214 380 L 298 380 L 298 318" {_s(c, 26)}/>'
            f'<path d="M 234 432 L 278 432" {_s(c, 26)}/>')


GLYPHS.update({
    "dish_d": dish_d, "gymondo_g": gymondo_g, "hippos_loop": hippos_loop, "juuno_j": juuno_j,
    "mediahub_play": mediahub_play, "movideo_doc": movideo_doc, "nfb_eye": nfb_eye,
    "ondamedia_figure": ondamedia_figure, "synology_rosette": synology_rosette,
    "tvoverlay_cards": tvoverlay_cards, "veezie_play": veezie_play, "wako_tv": wako_tv,
    "instantbits_cast": instantbits_cast, "couchpuzzle_tiles": couchpuzzle_tiles,
    "torrserve_bolt": torrserve_bolt, "avoid_play": avoid_play,
    "hueessentials_lamp": hueessentials_lamp,
})


# --------------------------------------------------------------------------
# Brand-informed marks, batch 5 (2026-09-26). Same contract as batches 1-4.

def _scaled(d, s, cx=256, cy=256):
    """Scale a path made only of M/L/C/Z pairs about (cx, cy)."""
    nums = iter(re.findall(r"-?\d+(?:\.\d+)?|[MLCZ]", d))
    out = []
    for tok in nums:
        if tok in "MLCZ":
            out.append(tok)
            continue
        x, y = float(tok), float(next(nums))
        out.append(f"{cx + (x - cx) * s:.1f} {cy + (y - cy) * s:.1f}")
    return " ".join(out)


def bitdefender_shield(c):
    """Bitdefender: the shield with the chain link across it."""
    link = _scaled("M 238 290 L 208 320 C 188 340 158 340 140 322 C 122 304 122 274 142 254 "
                   "L 172 224 M 274 222 L 304 192 C 324 172 354 172 372 190 "
                   "C 390 208 390 238 370 258 L 340 288 M 226 286 L 286 226", 0.72, 256, 262)
    return (f'<path d="M 256 72 L 420 128 L 420 246 C 420 346 350 410 256 444 '
            f'C 162 410 92 346 92 246 L 92 128 Z" {_s(c, 30)}/>'
            f'<path d="{link}" {_s(c, 26)}/>')


def clashmeta_cat(c):
    """Clash Meta: the M with a cat's whiskers and nose."""
    return (f'<path d="M 136 390 L 136 112 L 256 262 L 376 112 L 376 390" {_s(c, 32)}/>'
            f'<path d="M 72 330 L 110 324 M 76 378 L 110 364 '
            f'M 440 330 L 402 324 M 436 378 L 402 364" {_s(c, 22)}/>'
            + _solid("M 240 336 L 272 336 L 256 356 Z", c, 12))


def firesend_plane(c):
    """FireSend: the paper plane with its flame trail."""
    return (f'<path d="M 108 250 L 430 92 L 336 420 L 254 302 Z" {_s(c, 30)}/>'
            f'<path d="M 254 302 L 430 92" {_s(c, 24)}/>'
            f'<path d="M 86 420 C 70 380 100 354 124 334 C 128 364 150 374 146 408" {_s(c, 22)}/>')


def homeworkout_plank(c):
    """Home Workout: the figure holding a plank, facing out."""
    return (f'<circle cx="256" cy="140" r="44" {_s(c, 28)}/>'
            f'<path d="M 256 214 C 206 214 164 228 146 262 L 118 420 '
            f'M 256 214 C 306 214 348 228 366 262 L 394 420" {_s(c, 30)}/>'
            f'<path d="M 256 214 L 256 330" {_s(c, 30)}/>')


def lampa_rings(c):
    """Lampa: the solid eye inside its sweeping rings."""
    arcs = ""
    for r, a0, a1, w in ((188, 200, 340, 26), (132, 205, 335, 24), (132, 25, 155, 24)):
        x0, y0 = _polar(256, 262, r, a0)
        x1, y1 = _polar(256, 262, r, a1)
        sweep = 1
        arcs += (f'<path d="M {x0:.1f} {y0:.1f} A {r} {r} 0 0 {sweep} {x1:.1f} {y1:.1f}" '
                 f'{_s(c, w)}/>')
    return arcs + f'<circle cx="256" cy="262" r="58" {_s(c, 30)}/>'


def launchbox_cube(c):
    """LaunchBox: the puzzle cube, each face split into four."""
    top, rt, bot, lb, lt, ctr = (256, 72), (420, 166), (256, 440), (92, 346), (92, 166), (256, 260)
    rb = (420, 346)
    mid = lambda a, b: ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    seg = lambda a, b: f"M {a[0]:.1f} {a[1]:.1f} L {b[0]:.1f} {b[1]:.1f} "
    grid = (seg(mid(lt, top), mid(ctr, rt)) + seg(mid(top, rt), mid(lt, ctr))
            + seg(mid(lt, ctr), mid(lb, bot)) + seg(mid(lt, lb), mid(ctr, bot))
            + seg(mid(ctr, rt), mid(bot, rb)) + seg(mid(ctr, bot), mid(rt, rb)))
    return (f'<path d="M 256 72 L 420 166 L 420 346 L 256 440 L 92 346 L 92 166 Z" {_s(c, 28)}/>'
            f'<path d="M 92 166 L 256 260 L 420 166 M 256 260 L 256 440" {_s(c, 26)}/>'
            f'<path d="{grid}" {_s(c, 20)}/>')


def lemuroid_face(c):
    """Lemuroid: the two button eyes and the small nose of its face."""
    dots = "".join(f'<circle cx="{348 + dx}" cy="{232 + dy}" r="13" {_f(c)}/>'
                   for dx, dy in ((0, -28), (28, 0), (0, 28), (-28, 0)))
    return (f'<circle cx="164" cy="232" r="76" {_s(c, 26)}/>'
            f'<circle cx="348" cy="232" r="76" {_s(c, 26)}/>'
            f'<path d="M 164 200 L 164 264 M 132 232 L 196 232" {_s(c, 22)}/>' + dots
            + _solid("M 226 344 L 286 344 L 256 384 Z", c, 16))


def peloton_p(c):
    """Peloton: the P whose open bowl is cut by its slanted stem."""
    x0, y0 = _polar(284, 222, 106, 208)
    x1, y1 = _polar(284, 222, 106, 128)
    return (f'<path d="M {x0:.1f} {y0:.1f} A 106 106 0 1 1 {x1:.1f} {y1:.1f}" {_s(c, 32)}/>'
            f'<path d="M 338 84 L 206 424" {_s(c, 32)}/>')


def privado_keyhole(c):
    """Privado VPN: the keyhole at the centre of its broken rings."""
    arcs = ""
    for r, gaps in ((184, (60, 150, 250)), (132, (20, 200))):
        pts = sorted(gaps)
        for k, g in enumerate(pts):
            a0 = g + 12
            a1 = pts[(k + 1) % len(pts)] - 12 + (360 if k == len(pts) - 1 else 0)
            x0, y0 = _polar(256, 256, r, a0)
            x1, y1 = _polar(256, 256, r, a1)
            large = 1 if (a1 - a0) % 360 > 180 else 0
            arcs += (f'<path d="M {x0:.1f} {y0:.1f} A {r} {r} 0 {large} 1 {x1:.1f} {y1:.1f}" '
                     f'{_s(c, 22)}/>')
    return (arcs + f'<circle cx="256" cy="236" r="34" {_f(c)}/>'
            + _solid("M 240 250 L 272 250 L 282 318 L 230 318 Z", c, 12))


def psiphon_p(c):
    """Psiphon: the blocky P built from two offset slabs."""
    return (f'<path d="M 150 96 L 334 96 C 378 96 404 122 404 166 L 404 208 '
            f'C 404 252 378 278 334 278 L 254 278 L 254 396 C 254 408 246 416 234 416 '
            f'L 170 416 C 158 416 150 408 150 396 Z" {_s(c, 30)}/>'
            f'<path d="M 254 188 L 318 188" {_s(c, 26)}/>')


def purevpn_drop(c):
    """PureVPN: the rounded downward wedge inside its disc."""
    return (f'<circle cx="256" cy="256" r="176" {_s(c, 28)}/>'
            f'<path d="M 176 200 L 336 200 L 256 334 Z" {_s(c, 30)}/>')


def retrox_stick(c):
    """RetroX: the arcade panel with its stick and button."""
    return (f'<rect x="84" y="262" width="344" height="150" rx="40" {_s(c, 30)}/>'
            f'<path d="M 196 262 L 196 186" {_s(c, 26)}/>'
            f'<circle cx="196" cy="146" r="46" {_s(c, 28)}/>'
            f'<circle cx="332" cy="316" r="30" {_f(c)}/>')


def smugmug_smile(c):
    """SmugMug: the two eyes over the wide grin."""
    return (f'<circle cx="186" cy="152" r="26" {_f(c)}/>'
            f'<circle cx="326" cy="152" r="26" {_f(c)}/>'
            f'<path d="M 124 252 L 388 252 C 388 350 330 408 256 408 C 182 408 124 350 124 252 Z" '
            f'{_s(c, 30)}/>')


def symfonik_s(c):
    """Symfonik: the boxy S."""
    return (f'<path d="M 380 116 L 176 116 C 150 116 136 130 136 156 L 136 220 '
            f'C 136 246 150 256 176 256 L 336 256 C 362 256 376 266 376 292 L 376 356 '
            f'C 376 382 362 396 336 396 L 132 396" {_s(c, 32)}/>')


def torguard_lock(c):
    """TorGuard: the padlock whose body is drawn in swept cloud lines."""
    return (f'<path d="M 176 236 L 176 176 C 176 96 336 96 336 176 L 336 236" {_s(c, 30)}/>'
            f'<path d="M 108 440 L 108 292 C 108 256 128 236 164 236 L 348 236 '
            f'C 384 236 404 256 404 292 L 404 330" {_s(c, 30)}/>'
            f'<path d="M 170 324 L 340 324 C 372 324 388 352 360 368 L 170 368 '
            f'M 170 412 L 404 412" {_s(c, 24)}/>')


def viaplay_play(c):
    """Viaplay: the play arrow biting into its disc."""
    return (f'<path d="M 202 96 C 290 72 396 120 422 212 C 450 312 378 412 278 424 '
            f'C 220 432 174 410 146 380" {_s(c, 32)}/>'
            + _solid("M 90 164 L 308 256 L 90 348 Z", c, 16))


def weatheryou_sun(c):
    """WeatherYou: the sun rising behind its cloud."""
    return (f'<circle cx="200" cy="190" r="72" {_s(c, 28)}/>'
            f'<path d="M 146 410 C 88 410 72 336 126 318 C 128 262 206 242 236 290 '
            f'C 258 236 348 232 366 298 C 428 296 446 410 370 410 Z" {_s(c, 30)}/>')


def yoga_lotus(c):
    """Yoga Download: the lotus flower over its water line."""
    return (f'<path d="M 256 110 C 314 176 314 280 256 352 C 198 280 198 176 256 110 Z" {_s(c, 26)}/>'
            f'<path d="M 256 352 C 186 344 118 296 96 206 C 162 212 222 262 256 352" {_s(c, 24)}/>'
            f'<path d="M 256 352 C 326 344 394 296 416 206 C 350 212 290 262 256 352" {_s(c, 24)}/>'
            f'<path d="M 104 406 C 196 378 316 378 408 406" {_s(c, 26)}/>')


def zona_z(c):
    """Zona: the rounded Z of its bow-tie mark."""
    return (f'<path d="M 132 120 L 380 120 L 132 392 L 380 392" {_s(c, 32)}/>')


def buttonmapper_dpad(c):
    """Button Mapper: the d-pad with its centre button and four corner keys."""
    corners = "".join(f'<circle cx="{x}" cy="{y}" r="18" {_f(c)}/>'
                      for x, y in ((104, 104), (408, 104), (104, 408), (408, 408)))
    return (f'<path d="M 212 96 L 300 96 L 300 212 L 416 212 L 416 300 L 300 300 L 300 416 '
            f'L 212 416 L 212 300 L 96 300 L 96 212 L 212 212 Z" {_s(c, 28)}/>'
            f'<circle cx="256" cy="256" r="30" {_s(c, 22)}/>' + corners)


def galleri_cloud(c):
    """Galleri: the cloud with a mountain inside it."""
    return (f'<path d="M 150 392 C 86 392 68 314 124 294 C 124 226 206 200 244 254 '
            f'C 268 190 370 188 388 270 C 452 276 460 392 380 392 Z" {_s(c, 30)}/>'
            f'<path d="M 184 350 L 240 290 L 272 322 L 306 282 L 348 350" {_s(c, 24)}/>')


def meddelande_chat(c):
    """Meddelandelåda: the message bubble with its dots, a second behind it."""
    dots = "".join(f'<circle cx="{x}" cy="276" r="18" {_f(c)}/>' for x in (180, 238, 296))
    return (f'<path d="M 170 118 L 400 118 C 424 118 436 130 436 154 L 436 276" {_s(c, 24)}/>'
            f'<path d="M 112 180 L 356 180 C 380 180 392 192 392 216 L 392 336 '
            f'C 392 360 380 372 356 372 L 196 372 L 132 424 L 132 372 L 112 372 '
            f'C 88 372 76 360 76 336 L 76 216 C 76 192 88 180 112 180 Z" {_s(c, 30)}/>' + dots)


def pmx_gear(c):
    """PMX (Permission Manager X): the cog with a warning triangle on its shoulder."""
    pts = []
    for k in range(8):
        m = -90 + 45 * k
        pts += [_polar(210, 290, 118, m - 12), _polar(210, 290, 150, m - 7),
                _polar(210, 290, 150, m + 7), _polar(210, 290, 118, m + 12)]
    p = " L ".join(f"{x:.1f} {y:.1f}" for x, y in pts)
    return (f'<path d="M {p} Z" {_s(c, 24)}/>'
            f'<circle cx="210" cy="290" r="48" {_s(c, 24)}/>'
            f'<path d="M 370 70 L 456 222 L 284 222 Z" {_s(c, 26)}/>'
            f'<path d="M 370 124 L 370 168" {_s(c, 22)}/>'
            f'<circle cx="370" cy="198" r="10" {_f(c)}/>')


def getflix_popcorn(c):
    """Getflix: the striped popcorn box with kernels spilling over the rim."""
    kernels = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" {_s(c, 22)}/>'
                      for x, y, r in ((188, 166, 40), (256, 128, 46), (324, 166, 40)))
    return (kernels
            + f'<path d="M 132 214 L 380 214 L 340 440 L 172 440 Z" {_s(c, 28)}/>'
            f'<path d="M 214 214 L 226 440 M 298 214 L 286 440" {_s(c, 24)}/>')


def radioparadise_phones(c):
    """Radio Paradise: the headphones with sound between the cups."""
    return (f'<path d="M 104 300 C 104 112 408 112 408 300" {_s(c, 28)}/>'
            f'<rect x="80" y="276" width="64" height="130" rx="26" {_s(c, 26)}/>'
            f'<rect x="368" y="276" width="64" height="130" rx="26" {_s(c, 26)}/>'
            f'<path d="M 226 292 C 206 314 206 360 226 382 M 286 292 C 306 314 306 360 286 382 '
            f'M 190 312 C 180 328 180 346 190 362 M 322 312 C 332 328 332 346 322 362" {_s(c, 20)}/>')


GLYPHS.update({
    "bitdefender_shield": bitdefender_shield, "clashmeta_cat": clashmeta_cat,
    "firesend_plane": firesend_plane, "homeworkout_plank": homeworkout_plank,
    "lampa_rings": lampa_rings, "launchbox_cube": launchbox_cube,
    "lemuroid_face": lemuroid_face, "peloton_p": peloton_p,
    "privado_keyhole": privado_keyhole, "psiphon_p": psiphon_p,
    "purevpn_drop": purevpn_drop, "retrox_stick": retrox_stick,
    "smugmug_smile": smugmug_smile, "symfonik_s": symfonik_s,
    "torguard_lock": torguard_lock, "viaplay_play": viaplay_play,
    "weatheryou_sun": weatheryou_sun, "yoga_lotus": yoga_lotus, "zona_z": zona_z,
    "buttonmapper_dpad": buttonmapper_dpad, "galleri_cloud": galleri_cloud,
    "meddelande_chat": meddelande_chat, "pmx_gear": pmx_gear,
    "getflix_popcorn": getflix_popcorn, "radioparadise_phones": radioparadise_phones,
})


# --------------------------------------------------------------------------
# Brand-informed marks, batch 6 (2026-09-27): the last symbol icons that can
# be drawn without colliding with an existing mark. Each carries the one
# feature of its real icon the look-alike group does not have.

def hotspot_globe(c):
    """Hotspot Shield: the shield with its swirling globe inside."""
    return (f'<path d="M 256 72 L 420 128 L 420 246 C 420 346 350 410 256 444 '
            f'C 162 410 92 346 92 246 L 92 128 Z" {_s(c, 30)}/>'
            f'<circle cx="256" cy="252" r="84" {_s(c, 26)}/>'
            f'<path d="M 256 168 C 206 204 206 300 256 336 M 180 222 C 222 244 290 244 332 222" '
            f'{_s(c, 22)}/>')


def nfolio_grid(c):
    """Nfolio: the four-by-four wall of outlined frames."""
    tiles = ""
    for y in (92, 188, 284, 380):
        for x in (92, 188, 284, 380):
            tiles += f'<rect x="{x}" y="{y}" width="44" height="44" rx="10" {_s(c, 16)}/>'
    return tiles


def refreshrate_bars(c):
    """Refresh Rate: the list of blocks and lines, like a settings column."""
    rows = ""
    for y in (112, 208, 304, 400):
        rows += (f'<rect x="96" y="{y - 26}" width="136" height="52" rx="14" {_s(c, 22)}/>'
                 f'<path d="M 280 {y} L 416 {y}" {_s(c, 26)}/>')
    return rows


def scb_chevrons(c):
    """SCB Next: the X drawn as two pairs of nested chevrons."""
    return (f'<path d="M 108 108 L 234 256 L 108 404 M 404 108 L 278 256 L 404 404" {_s(c, 30)}/>'
            f'<path d="M 184 108 L 256 190 L 328 108 M 184 404 L 256 322 L 328 404" {_s(c, 26)}/>')


def sdmaid_robot(c):
    """SD Maid: the droid head wearing a maid's bow, over its apron."""
    return (f'<path d="M 104 262 C 104 150 408 150 408 262 Z" {_s(c, 28)}/>'
            f'<path d="M 170 132 L 148 96 M 342 132 L 364 96" {_s(c, 22)}/>'
            f'<circle cx="196" cy="216" r="16" {_f(c)}/>'
            f'<circle cx="316" cy="216" r="16" {_f(c)}/>'
            f'<path d="M 104 304 L 408 304 L 408 386 C 408 410 394 424 370 424 L 142 424 '
            f'C 118 424 104 410 104 386 Z" {_s(c, 28)}/>'
            + _solid("M 380 132 L 344 108 L 344 156 Z M 380 132 L 416 108 L 416 156 Z", c, 8))


def strongvpn_shield(c):
    """StrongVPN: the shield with a second shield nested inside."""
    return (f'<path d="M 256 64 L 424 124 L 424 244 C 424 344 352 410 256 448 '
            f'C 160 410 88 344 88 244 L 88 124 Z" {_s(c, 30)}/>'
            f'<path d="M 256 150 L 346 184 L 346 250 C 346 306 306 342 256 364 '
            f'C 206 342 166 306 166 250 L 166 184 Z" {_s(c, 26)}/>')


def sweettv_donut(c):
    """Sweet.tv: the wide screen on its stand, with the donut on it."""
    return (f'<rect x="64" y="112" width="384" height="236" rx="28" {_s(c, 28)}/>'
            f'<path d="M 196 412 L 316 412 M 256 348 L 256 412" {_s(c, 26)}/>'
            f'<circle cx="256" cy="230" r="62" {_s(c, 28)}/>'
            f'<circle cx="256" cy="230" r="14" {_f(c)}/>')


def vradio_dial(c):
    """vRadio: the radio with its long antenna, big dial and tuning scale."""
    return (f'<rect x="72" y="200" width="368" height="224" rx="36" {_s(c, 28)}/>'
            f'<path d="M 132 200 L 380 104" {_s(c, 22)}/>'
            f'<circle cx="392" cy="100" r="18" {_f(c)}/>'
            f'<circle cx="180" cy="312" r="62" {_s(c, 26)}/>'
            f'<path d="M 290 272 L 390 272 M 290 312 L 390 312" {_s(c, 20)}/>'
            f'<circle cx="340" cy="366" r="20" {_s(c, 20)}/>')


def zattoo_tv(c):
    """Zattoo: the round set with antennae and its tilted rounded screen."""
    return (f'<circle cx="256" cy="282" r="164" {_s(c, 30)}/>'
            f'<path d="M 196 126 L 150 70 M 316 126 L 362 70" {_s(c, 26)}/>'
            f'<path d="M 172 226 C 236 206 312 208 362 230 C 372 282 364 326 344 362 '
            f'C 284 346 212 344 164 356 C 148 314 150 262 172 226 Z" {_s(c, 26)}/>')


def magiconnect_screens(c):
    """MagiConnect: the phone casting onto the screen beside it."""
    return (f'<rect x="200" y="112" width="248" height="190" rx="24" {_s(c, 28)}/>'
            f'<path d="M 324 302 L 324 350 M 272 350 L 376 350" {_s(c, 22)}/>'
            f'<rect x="64" y="196" width="104" height="220" rx="22" {_s(c, 28)}/>'
            f'<path d="M 100 380 L 132 380" {_s(c, 20)}/>'
            + _solid("M 302 170 L 362 207 L 302 244 Z", c, 12))


GLYPHS.update({
    "hotspot_globe": hotspot_globe, "nfolio_grid": nfolio_grid,
    "refreshrate_bars": refreshrate_bars, "scb_chevrons": scb_chevrons,
    "sdmaid_robot": sdmaid_robot, "strongvpn_shield": strongvpn_shield,
    "sweettv_donut": sweettv_donut, "vradio_dial": vradio_dial,
    "zattoo_tv": zattoo_tv, "magiconnect_screens": magiconnect_screens,
})


# --------------------------------------------------------------------------
# Brand-informed marks, batch 7 (2026-09-27): references found in research
# pass 3 (Aptoide, exact package match).

def cinemahd_ticket(c):
    """CinemaHD: the admission ticket with its notched ends and stars."""
    stars = "".join(f'<circle cx="{x}" cy="190" r="12" {_f(c)}/>' for x in (196, 256, 316))
    return (f'<path d="M 96 136 L 416 136 L 416 206 C 390 214 380 236 380 256 '
            f'C 380 276 390 298 416 306 L 416 376 L 96 376 L 96 306 C 122 298 132 276 132 256 '
            f'C 132 236 122 214 96 206 Z" {_s(c, 30)}/>' + stars
            + f'<path d="M 176 262 L 336 262 M 196 318 L 316 318" {_s(c, 24)}/>')


def hdobox_hplay(c):
    """HDO Box: the H whose right stem turns into a play arrow."""
    return (f'<path d="M 112 104 L 112 408 M 112 256 L 232 256 M 232 104 L 232 408" {_s(c, 34)}/>'
            + _solid("M 286 136 L 420 256 L 286 376 Z", c, 18))


def netmirror_n(c):
    """NetMirror: the ribbon N, its diagonal sweeping between two curved stems."""
    return (f'<path d="M 136 420 L 136 196 C 136 120 196 92 246 150 L 332 286" {_s(c, 32)}/>'
            f'<path d="M 376 92 L 376 316 C 376 392 316 420 266 362 L 180 226" {_s(c, 32)}/>')


def perfectplayer_p(c):
    """Perfect Player: the P with a play arrow set into its bowl."""
    return (f'<path d="M 144 432 L 144 96 L 290 96 C 364 96 408 142 408 206 C 408 270 364 316 290 316 '
            f'L 144 316" {_s(c, 32)}/>'
            + _solid("M 224 158 L 314 206 L 224 254 Z", c, 14))


GLYPHS.update({
    "cinemahd_ticket": cinemahd_ticket, "hdobox_hplay": hdobox_hplay,
    "netmirror_n": netmirror_n, "perfectplayer_p": perfectplayer_p,
})


# --------------------------------------------------------------------------
# Brand-informed marks, batch 8 (2026-09-27): icons first set aside as too
# detailed, reduced to the one silhouette that still identifies each.

def dangbei_trend(c):
    """Dangbei: the rising trend arrow in its disc, signal arcs above."""
    return (f'<circle cx="256" cy="294" r="136" {_s(c, 30)}/>'
            f'<path d="M 184 340 L 240 284 L 276 318 L 334 256" {_s(c, 28)}/>'
            f'<path d="M 298 252 L 338 252 L 338 292" {_s(c, 26)}/>'
            f'<path d="M 150 110 C 214 70 298 70 362 110" {_s(c, 24)}/>')


def fladder_wing(c):
    """Fladder: the two stacked wing strokes, the lower one swept back."""
    return (f'<path d="M 120 108 C 250 84 380 132 404 206 C 336 196 250 204 176 232 '
            f'C 140 196 120 150 120 108 Z" {_s(c, 28)}/>'
            f'<path d="M 176 256 C 290 238 392 270 404 340 C 320 356 250 392 204 432 '
            f'C 176 380 170 312 176 256 Z" {_s(c, 28)}/>')


def fotoo_frame(c):
    """Fotoo: the tilted photo frame with its handwritten f."""
    fr = [_polar(256, 256, 188, a) for a in (-100, -10, 80, 170)]
    inner = [_polar(256, 256, 122, a) for a in (-100, -10, 80, 170)]
    p = lambda q: " L ".join(f"{x:.1f} {y:.1f}" for x, y in q)
    return (f'<path d="M {p(fr)} Z" {_s(c, 30)}/>'
            f'<path d="M {p(inner)} Z" {_s(c, 22)}/>'
            f'<path d="M 286 196 C 256 176 232 204 244 240 L 262 320 M 222 262 L 294 250" '
            f'{_s(c, 22)}/>')


def maze_square(c):
    """Maze: the square maze of broken rings with its crossing diagonal."""
    return (f'<path d="M 256 76 L 436 76 L 436 436 L 76 436 L 76 76 L 196 76" {_s(c, 26)}/>'
            f'<path d="M 316 140 L 372 140 L 372 372 L 140 372 L 140 140 L 256 140" {_s(c, 24)}/>'
            f'<path d="M 204 204 L 308 204 L 308 308 L 204 308 L 204 256" {_s(c, 22)}/>'
            f'<circle cx="256" cy="256" r="14" {_f(c)}/>')


def radioline_planet(c):
    """Radioline: the ringed planet with a sound wave through it."""
    return (f'<circle cx="256" cy="256" r="142" {_s(c, 30)}/>'
            f'<path d="M 128 330 C 60 382 70 420 150 398 C 230 376 350 300 420 222 '
            f'C 470 166 440 140 382 170" {_s(c, 24)}/>'
            f'<path d="M 162 262 L 194 222 L 222 300 L 256 196 L 290 316 L 318 232 L 350 262" '
            f'{_s(c, 22)}/>')


def smarttwitch_joystick(c):
    """SmartTwitchTV: the joystick on its base, inside a disc."""
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 28)}/>'
            f'<path d="M 150 314 L 256 264 L 368 314 L 262 366 Z" {_s(c, 26)}/>'
            f'<path d="M 258 292 L 258 132" {_s(c, 24)}/>'
            f'<circle cx="258" cy="122" r="20" {_f(c)}/>'
            f'<circle cx="198" cy="318" r="14" {_f(c)}/>')


def weathernetwork_globe(c):
    """The Weather Network: the wireframe globe of tilted rings."""
    out = f'<circle cx="256" cy="256" r="176" {_s(c, 26)}/>'
    out += f'<ellipse cx="256" cy="256" rx="96" ry="176" {_s(c, 22)}/>'
    out += (f'<path d="M 110 160 C 200 130 312 130 402 160 M 110 352 C 200 382 312 382 402 352" '
            f'{_s(c, 22)}/>')
    return out


GLYPHS.update({
    "dangbei_trend": dangbei_trend,
    "fladder_wing": fladder_wing, "fotoo_frame": fotoo_frame, "maze_square": maze_square,
    "radioline_planet": radioline_planet,
    "smarttwitch_joystick": smarttwitch_joystick, "weathernetwork_globe": weathernetwork_globe,
})


# --------------------------------------------------------------------------
# Brand marks batch 9 (2026-09-27): symbol icons redrawn from each app's own
# launcher icon.


def _arc_cw(cx, cy, r, a0, a1):
    """An SVG arc path clockwise from a0 to a1 degrees (y down)."""
    x0, y0 = _polar(cx, cy, r, a0)
    x1, y1 = _polar(cx, cy, r, a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f"M {x0:.1f} {y0:.1f} A {r} {r} 0 {large} 1 {x1:.1f} {y1:.1f}"


def _radio_body(c):
    return f'<rect x="72" y="188" width="368" height="236" rx="36" {_s(c, 28)}/>'


def bellfibe_play(c):
    """Bell Fibe: the outlined play arrow standing on the Bell bar."""
    return (f'<path d="M 170 92 L 394 222 L 170 352 Z" {_s(c, 30)}/>'
            f'<path d="M 170 424 L 342 424" {_s(c, 30)}/>')


def dramox_ring(c):
    """Dramox: a solid play inside a ring broken at both sides."""
    return (f'<path d="{_arc_cw(256, 256, 176, 205, 335)} {_arc_cw(256, 256, 176, 25, 155)}" '
            f'{_s(c, 30)}/>'
            + _solid("M 222 190 L 322 256 L 222 322 Z", c, 14))


def enjoytv_chevron(c):
    """Enjoy TV: the outlined play arrow chased by a chevron."""
    return (f'<path d="M 96 120 L 286 256 L 96 392 Z" {_s(c, 30)}/>'
            f'<path d="M 318 168 L 424 256 L 318 344" {_s(c, 30)}/>')


def ottplay_aperture(c):
    """OTTplay: the play arrow whose edges run on to the rim, like an aperture."""
    return (f'<circle cx="256" cy="256" r="180" {_s(c, 28)}/>'
            f'<path d="M 214 170 L 334 256 L 214 342 Z" {_s(c, 24)}/>'
            f'<path d="M 214 170 L 214 92 M 334 256 L 400 303 M 214 342 L 150 388" '
            f'{_s(c, 24)}/>')


def playnow_arrow(c):
    """Play Now: a notched arrowhead inside its disc."""
    return (f'<circle cx="256" cy="256" r="180" {_s(c, 28)}/>'
            + _solid("M 212 150 L 346 256 L 212 362 L 252 256 Z", c, 14))


def pathe_bubble(c):
    """Pathé Thuis: the speech bubble with a solid play inside."""
    return (f'<path d="M 136 96 L 376 96 C 408 96 424 112 424 144 L 424 344 '
            f'C 424 376 408 392 376 392 L 176 392 L 100 440 L 112 384 '
            f'C 96 376 88 364 88 344 L 88 144 C 88 112 104 96 136 96 Z" {_s(c, 28)}/>'
            + _solid("M 214 180 L 324 244 L 214 308 Z", c, 16))


def ocean_diamond(c):
    """Ocean Streamz: the rounded diamond with a play at its heart."""
    return (f'<path d="M 256 64 L 448 256 L 256 448 L 64 256 Z" {_s(c, 28)}/>'
            + _solid("M 222 196 L 318 256 L 222 316 Z", c, 14))


def xciptv_bars(c):
    """XC IPTV: two bars and a play arrow in a disc."""
    return (f'<circle cx="256" cy="256" r="180" {_s(c, 28)}/>'
            f'<path d="M 160 160 L 160 352 M 204 150 L 204 362" {_s(c, 24)}/>'
            + _solid("M 246 190 L 350 256 L 246 322 Z", c, 14))


def filmfriend_frame(c):
    """Filmfriend: a soft, deep screen frame with a small play in it."""
    return (f'<rect x="96" y="136" width="320" height="240" rx="64" {_s(c, 30)}/>'
            + _solid("M 228 214 L 300 256 L 228 298 Z", c, 14))


def orangetv_screen(c):
    """Orange TV Go: the screen on a deep bottom bezel with its power dot."""
    return (f'<rect x="88" y="112" width="336" height="288" rx="12" {_s(c, 28)}/>'
            f'<path d="M 88 336 L 424 336" {_s(c, 26)}/>'
            f'<circle cx="256" cy="370" r="14" {_f(c)}/>')


def stbemu_tv(c):
    """STB Emu: a wide flat panel floating over its floor shadow."""
    return (f'<rect x="64" y="128" width="384" height="220" rx="14" {_s(c, 28)}/>'
            f'<circle cx="256" cy="322" r="9" {_f(c)}/>'
            f'<path d="M 136 408 L 376 408" {_s(c, 22)}/>')


def tvapprepo_monitor(c):
    """TV App Repo: a monitor on a splayed foot."""
    return (f'<rect x="96" y="96" width="320" height="232" rx="12" {_s(c, 28)}/>'
            f'<path d="M 220 328 L 204 396 M 292 328 L 308 396 M 164 404 L 348 404" '
            f'{_s(c, 24)}/>')


def homeassist_bubble(c):
    """Home Automation TV Dashboard: a house in a square message bubble."""
    return (f'<path d="M 120 88 L 392 88 C 412 88 424 100 424 120 L 424 328 '
            f'C 424 348 412 360 392 360 L 188 360 L 120 428 L 120 360 '
            f'C 100 360 88 348 88 328 L 88 120 C 88 100 100 88 120 88 Z" {_s(c, 28)}/>'
            f'<path d="M 184 296 L 184 234 L 256 176 L 328 234 L 328 296 Z" {_s(c, 24)}/>')


def fmradio_knobs(c):
    """FM Radio: the portable set with a ball-tipped aerial, two knobs and a speaker."""
    return (_radio_body(c)
            + f'<path d="M 150 188 L 364 112" {_s(c, 22)}/>'
            f'<circle cx="382" cy="106" r="20" {_f(c)}/>'
            f'<path d="M 120 244 L 392 244" {_s(c, 22)}/>'
            f'<circle cx="152" cy="304" r="22" {_s(c, 20)}/>'
            f'<circle cx="152" cy="370" r="14" {_f(c)}/>'
            f'<circle cx="320" cy="338" r="58" {_s(c, 22)}/>')


def replaio_radio(c):
    """Replaio Radio: the set whose dial is a play button, with a display beside it."""
    return (_radio_body(c)
            + f'<path d="M 108 188 L 388 100" {_s(c, 22)}/>'
            f'<circle cx="176" cy="306" r="66" {_s(c, 22)}/>'
            + _solid("M 160 276 L 206 306 L 160 336 Z", c, 10)
            + f'<rect x="282" y="256" width="110" height="52" rx="12" {_s(c, 20)}/>'
            f'<path d="M 282 360 L 392 360" {_s(c, 22)}/>')


def worldradios_grille(c):
    """World Radios: the set with a slatted grille and a round tuning dial."""
    return (_radio_body(c)
            + f'<path d="M 186 188 L 330 112" {_s(c, 22)}/>'
            f'<path d="M 118 262 L 250 262 M 118 306 L 250 306 M 118 350 L 250 350" '
            f'{_s(c, 22)}/>'
            f'<circle cx="346" cy="306" r="50" {_s(c, 22)}/>')


def forecast_cloud(c):
    """Forecast: the sun peeking over a cloud, in a disc."""
    return (f'<circle cx="256" cy="256" r="190" {_s(c, 24)}/>'
            f'<path d="{_arc_cw(206, 214, 50, 150, 345)}" {_s(c, 22)}/>'
            f'<path d="M 170 356 C 124 356 118 296 164 286 C 170 238 236 222 266 262 '
            f'C 304 236 364 256 360 306 C 396 312 394 356 356 356 Z" {_s(c, 24)}/>')


def tennistv_ball(c):
    """Tennis TV: the tennis ball with its two seams."""
    return (f'<circle cx="256" cy="256" r="176" {_s(c, 28)}/>'
            f'<path d="M 146 124 C 222 196 222 316 146 388 M 366 124 C 290 196 290 316 366 388" '
            f'{_s(c, 22)}/>')


def etube_bars(c):
    """Etube: three slanted, offset bars stacked into an E, in a disc."""
    bars = ("M 214 162 L 344 162 L 326 200 L 196 200 Z",
            "M 182 236 L 330 236 L 312 274 L 164 274 Z",
            "M 190 310 L 306 310 L 288 348 L 172 348 Z")
    return (f'<circle cx="256" cy="256" r="176" {_s(c, 28)}/>'
            + "".join(_solid(d, c, 12) for d in bars))


def vpnunlimited_shield(c):
    """VPN Unlimited: the infinity sign on a shield."""
    return (f'<path d="M 256 60 L 424 118 L 424 250 C 424 350 350 418 256 452 '
            f'C 162 418 88 350 88 250 L 88 118 Z" {_s(c, 28)}/>'
            f'<path d="M 256 256 C 226 204 150 204 150 256 C 150 308 226 308 256 256 '
            f'C 286 204 362 204 362 256 C 362 308 286 308 256 256 Z" {_s(c, 24)}/>')


def hideme_cone(c):
    """hide.me: the rounded triangle with a spray of bubbles along its edge."""
    dots = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" {_f(c)}/>'
                   for x, y, r in ((332, 160, 11), (354, 206, 9), (318, 232, 11),
                                   (342, 276, 9), (306, 306, 11)))
    return f'<path d="M 88 132 L 428 88 L 300 432 Z" {_s(c, 30)}/>' + dots


def fasttask_cube(c):
    """Fast Task Killer: a cube set in a round bezel."""
    return (f'<circle cx="256" cy="256" r="190" {_s(c, 26)}/>'
            f'<path d="M 256 146 L 351 201 L 351 311 L 256 366 L 161 311 L 161 201 Z" '
            f'{_s(c, 24)}/>'
            f'<path d="M 161 201 L 256 256 L 351 201 M 256 256 L 256 366" {_s(c, 24)}/>')


GLYPHS.update({
    "bellfibe_play": bellfibe_play, "dramox_ring": dramox_ring,
    "enjoytv_chevron": enjoytv_chevron, "ottplay_aperture": ottplay_aperture,
    "playnow_arrow": playnow_arrow, "pathe_bubble": pathe_bubble,
    "ocean_diamond": ocean_diamond, "xciptv_bars": xciptv_bars,
    "filmfriend_frame": filmfriend_frame, "orangetv_screen": orangetv_screen,
    "stbemu_tv": stbemu_tv, "tvapprepo_monitor": tvapprepo_monitor,
    "homeassist_bubble": homeassist_bubble, "fmradio_knobs": fmradio_knobs,
    "replaio_radio": replaio_radio, "worldradios_grille": worldradios_grille,
    "forecast_cloud": forecast_cloud, "tennistv_ball": tennistv_ball,
    "etube_bars": etube_bars, "vpnunlimited_shield": vpnunlimited_shield,
    "hideme_cone": hideme_cone, "fasttask_cube": fasttask_cube,
})


# --------------------------------------------------------------------------
# Brand marks batch 10 (2026-09-27): the harder symbol icons, each reduced to
# the one silhouette that identifies its launcher icon.


def pigeoncast_bird(c):
    """PigeonCast: the bird in flight, its wing swept up and tail fanned low."""
    return (f'<path d="M 92 214 C 176 194 262 258 298 354 C 336 330 384 352 424 404" '
            f'{_s(c, 28)}/>'
            f'<path d="M 214 212 C 248 146 326 94 412 72 C 414 172 362 250 298 298" '
            f'{_s(c, 26)}/>')


def kernel_popcorn(c):
    """Kernel Media: the popcorn bucket, heaped over its rim, in its round badge."""
    return (f'<circle cx="256" cy="256" r="192" {_s(c, 24)}/>'
            f'<path d="M 192 236 C 170 196 206 164 232 184 C 240 144 290 140 292 184 '
            f'C 318 164 352 200 320 236" {_s(c, 22)}/>'
            f'<path d="M 184 244 L 328 244 L 308 372 L 204 372 Z" {_s(c, 22)}/>'
            f'<path d="M 232 244 L 238 372 M 280 244 L 274 372" {_s(c, 18)}/>')


def movieark_ship(c):
    """Movieark: the ship's hull running right under two peaked sails."""
    return (f'<path d="M 80 312 L 432 232 C 404 314 352 372 280 380 L 160 380 '
            f'C 124 380 96 350 80 312 Z" {_s(c, 26)}/>'
            f'<path d="M 170 290 L 204 150 L 232 222 L 266 132 L 286 262" {_s(c, 22)}/>')


def nzr_fern(c):
    """NZR+: the silver fern frond, with the plus at its foot."""
    import math
    p0, p1, p2, p3 = (112, 404), (190, 320), (300, 190), (404, 84)
    def at(t):
        u = 1 - t
        x = u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0]
        y = u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1]
        return x, y
    leaves = []
    for k in range(9):
        t = 0.16 + 0.095 * k
        x, y = at(t)
        x2, y2 = at(min(t + 0.02, 1))
        a = math.atan2(y2 - y, x2 - x)
        ln = 84 - 7 * k
        for s in (1, -1):
            b = a + s * 0.95
            leaves.append(f"M {x:.1f} {y:.1f} L {x + ln * math.cos(b):.1f} {y + ln * math.sin(b):.1f}")
    return (f'<path d="M {p0[0]} {p0[1]} C {p1[0]} {p1[1]} {p2[0]} {p2[1]} {p3[0]} {p3[1]}" '
            f'{_s(c, 24)}/>'
            f'<path d="{" ".join(leaves)}" {_s(c, 20)}/>'
            f'<path d="M 400 360 L 400 436 M 362 398 L 438 398" {_s(c, 22)}/>')


def haystack_blocks(c):
    """Haystack News: the H built from two stacked blocks and one tall slab."""
    return (f'<path d="M 128 104 L 232 104 L 232 240 L 128 248 Z '
            f'M 128 280 L 232 272 L 232 408 L 128 408 Z '
            f'M 280 104 L 384 88 L 384 424 L 280 408 Z" {_s(c, 26)}/>')


def rally_r(c):
    """Rally TV: the play arrow with a racing-line R inside it."""
    return (f'<path d="M 104 80 L 440 256 L 104 432 Z" {_s(c, 28)}/>'
            f'<path d="M 176 330 L 176 190 L 238 190 C 276 190 276 256 238 256 L 190 256 '
            f'M 228 256 L 264 322" {_s(c, 22)}/>')


def playsuisse_mark(c):
    """Play Suisse: the chevron and the Swiss plus."""
    return (f'<path d="M 104 192 L 192 256 L 104 320" {_s(c, 34)}/>'
            f'<path d="M 332 172 L 332 340 M 248 256 L 416 256" {_s(c, 34)}/>')


def telenet_face(c):
    """Telenet: the winking face in its rounded square."""
    return (f'<rect x="96" y="96" width="320" height="320" rx="76" {_s(c, 30)}/>'
            f'<circle cx="202" cy="222" r="18" {_f(c)}/>'
            f'<path d="M 290 222 L 330 222" {_s(c, 22)}/>'
            f'<path d="M 184 298 C 218 342 294 342 328 298" {_s(c, 24)}/>')


def wow_globe(c):
    """WOW Presents Plus: the globe with the plus badge on its shoulder."""
    return (f'<circle cx="228" cy="284" r="150" {_s(c, 26)}/>'
            f'<ellipse cx="228" cy="284" rx="64" ry="150" {_s(c, 20)}/>'
            f'<path d="M 78 284 L 378 284" {_s(c, 20)}/>'
            f'<circle cx="384" cy="128" r="64" {_s(c, 22)}/>'
            f'<path d="M 384 96 L 384 160 M 352 128 L 416 128" {_s(c, 22)}/>')


def mame_panel(c):
    """MAME4droid: the raked arcade panel with a ball-top stick and three fire buttons."""
    buttons = "".join(f'<circle cx="{x}" cy="{y}" r="22" {_s(c, 20)}/>'
                      for x, y in ((276, 356), (334, 340), (392, 324)))
    return (f'<path d="M 72 268 L 440 228 L 440 424 L 72 424 Z" {_s(c, 26)}/>'
            f'<path d="M 166 344 L 166 214" {_s(c, 24)}/>'
            f'<circle cx="166" cy="176" r="42" {_f(c)}/>' + buttons)


def nostalgia_tvplay(c):
    """Nostalgia TV: a retro set tucked inside the play arrow."""
    return (f'<path d="M 104 88 L 440 256 L 104 424 Z" {_s(c, 28)}/>'
            f'<rect x="136" y="214" width="156" height="88" rx="14" {_s(c, 18)}/>'
            f'<path d="M 162 236 L 236 236 L 236 280 L 162 280 Z" {_s(c, 14)}/>'
            f'<circle cx="264" cy="240" r="8" {_f(c)}/>'
            f'<circle cx="264" cy="274" r="8" {_f(c)}/>')


def talksport_ball(c):
    """talkSPORT: the football as a speech bubble."""
    import math
    cx, cy = 256, 232
    def pt(r, deg):
        return cx + r * math.cos(math.radians(deg)), cy + r * math.sin(math.radians(deg))
    pent = [pt(50, -90 + 72 * k) for k in range(5)]
    seams = []
    for k in range(5):
        a = -90 + 72 * k
        (x0, y0), (x1, y1) = pent[k], pt(104, a)
        seams.append(f"M {x0:.1f} {y0:.1f} L {x1:.1f} {y1:.1f}")
        for d in (-26, 26):
            x2, y2 = pt(160, a + d)
            seams.append(f"M {x1:.1f} {y1:.1f} L {x2:.1f} {y2:.1f}")
    pd = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in pent) + " Z"
    return (f'<path d="M 196 369 L 150 444 L 244 378" {_s(c, 24)}/>'
            f'<circle cx="{cx}" cy="{cy}" r="160" {_s(c, 26)}/>'
            + _solid(pd, c, 10)
            + f'<path d="{" ".join(seams)}" {_s(c, 18)}/>')


GLYPHS.update({
    "pigeoncast_bird": pigeoncast_bird, "kernel_popcorn": kernel_popcorn,
    "movieark_ship": movieark_ship, "nzr_fern": nzr_fern,
    "haystack_blocks": haystack_blocks, "rally_r": rally_r,
    "playsuisse_mark": playsuisse_mark, "telenet_face": telenet_face,
    "wow_globe": wow_globe, "mame_panel": mame_panel,
    "nostalgia_tvplay": nostalgia_tvplay, "talksport_ball": talksport_ball,
})


def award_vpn_check(c):
    """Award VPN: an A built from a chevron and a tick, under Wi-Fi arcs."""
    return (f'<path d="{_arc_cw(256, 150, 40, 225, 315)} {_arc_cw(256, 150, 78, 228, 312)}" '
            f'{_s(c, 22)}/>'
            f'<path d="M 84 190 L 132 258 M 108 418 L 256 196 L 300 262" {_s(c, 32)}/>'
            f'<path d="M 208 334 L 258 404 L 428 186" {_s(c, 32)}/>')


def firedown_flame(c):
    """Firedown: a flame that runs down into the download arrowhead."""
    return (f'<path d="M 256 404 C 186 330 196 196 296 76 C 284 176 340 250 256 404" {_s(c, 26)}/>'
            f'<path d="M 128 286 L 256 420 L 384 286" {_s(c, 30)}/>')


GLYPHS.update({"award_vpn_check": award_vpn_check, "firedown_flame": firedown_flame})


# --------------------------------------------------------------------------
# Projectivy cross-check (2026-09-27): two sideloaded apps install under a
# decoy package whose store listing shows a different app. These marks follow
# the app people actually see on their TV.


def beetv_bee(c):
    """BeeTV: the striped bee whose head is a play button."""
    return (f'<ellipse cx="214" cy="304" rx="128" ry="92" transform="rotate(-24 214 304)" '
            f'{_s(c, 28)}/>'
            f'<path d="M 150 238 L 206 378 M 214 212 L 268 350" {_s(c, 24)}/>'
            f'<path d="M 196 214 C 150 150 176 88 240 96 C 262 146 246 196 212 222 '
            f'M 250 206 C 262 138 322 116 356 158 C 334 204 292 222 256 222" {_s(c, 22)}/>'
            f'<circle cx="372" cy="250" r="58" {_s(c, 24)}/>'
            + _solid("M 356 222 L 398 250 L 356 278 Z", c, 10))


def teatv_tv(c):
    """TeaTV: the tilted TV on crooked antennae with a play button on screen."""
    return (f'<path d="M 196 170 L 150 102 M 296 150 L 318 76" {_s(c, 24)}/>'
            f'<circle cx="150" cy="102" r="15" {_f(c)}/>'
            f'<circle cx="318" cy="76" r="15" {_f(c)}/>'
            f'<rect x="96" y="166" width="316" height="240" rx="58" '
            f'transform="rotate(-10 254 286)" {_s(c, 32)}/>'
            + _solid("M 222 240 L 316 280 L 232 346 Z", c, 14))


GLYPHS.update({"beetv_bee": beetv_bee, "teatv_tv": teatv_tv})


# --------------------------------------------------------------------------
# Brand marks batch 11 (2026-09-27): letter tiles whose launcher icon, seen in
# the Projectivy Icon Pack 1.1.9 artwork (reference only), carries a symbol.


def anten_dots(c):
    """Anten TV: the three dots in a row over the name's baseline."""
    return (f'<circle cx="136" cy="222" r="54" {_s(c, 28)}/>'
            f'<circle cx="256" cy="222" r="54" {_s(c, 28)}/>'
            f'<circle cx="376" cy="222" r="54" {_s(c, 28)}/>'
            f'<path d="M 104 350 L 408 350" {_s(c, 30)}/>')


def inatbox_robot(c):
    """İnat Box: the TV-set robot on two feet, with ball-tipped antennae."""
    return (f'<path d="M 214 176 L 172 106 M 298 176 L 340 106" {_s(c, 24)}/>'
            f'<circle cx="172" cy="100" r="18" {_f(c)}/>'
            f'<circle cx="340" cy="100" r="18" {_f(c)}/>'
            f'<rect x="100" y="176" width="312" height="208" rx="52" {_s(c, 30)}/>'
            f'<circle cx="200" cy="268" r="26" {_s(c, 22)}/>'
            f'<circle cx="312" cy="268" r="26" {_s(c, 22)}/>'
            f'<path d="M 236 318 L 256 336 L 276 318" {_s(c, 20)}/>'
            f'<path d="M 176 384 L 164 428 M 336 384 L 348 428" {_s(c, 26)}/>')


def bazaar_bag(c):
    """Cafe Bazaar: the shopping bag with its handle and a smile."""
    return (f'<path d="{_arc_cw(256, 196, 70, 180, 360)}" {_s(c, 26)}/>'
            f'<path d="M 112 196 L 400 196 L 376 424 L 136 424 Z" {_s(c, 30)}/>'
            f'<path d="M 192 290 C 214 344 298 344 320 290" {_s(c, 24)}/>')


def coreelec_power(c):
    """CoreELEC Helper: the power symbol inside its ring."""
    return (f'<circle cx="256" cy="256" r="190" {_s(c, 24)}/>'
            f'<path d="{_arc_cw(256, 272, 96, 300, 240)}" {_s(c, 30)}/>'
            f'<path d="M 256 140 L 256 262" {_s(c, 30)}/>')


def cricfy_ball(c):
    """CricFy TV: the cricket ball and its seam, trailing speed lines."""
    return (f'<circle cx="300" cy="256" r="132" {_s(c, 28)}/>'
            f'<path d="M 214 164 C 278 204 324 262 354 364 M 244 142 C 310 186 356 246 388 334" '
            f'{_s(c, 18)}/>'
            f'<path d="M 80 196 L 136 196 M 64 256 L 136 256 M 80 316 L 136 316" {_s(c, 24)}/>')


def ramcleaner_rocket(c):
    """RAM Cleaner: the rocket lifting off on the diagonal."""
    return (f'<g transform="rotate(45 256 256)">'
            f'<path d="M 256 64 C 318 116 332 204 312 316 L 200 316 C 180 204 194 116 256 64 Z" '
            f'{_s(c, 28)}/>'
            f'<circle cx="256" cy="196" r="32" {_s(c, 22)}/>'
            f'<path d="M 200 256 L 150 340 L 204 332 M 312 256 L 362 340 L 308 332" {_s(c, 24)}/>'
            f'<path d="M 230 352 L 256 420 L 282 352" {_s(c, 24)}/>'
            f'</g>')


def shark_leap(c):
    """Shark TV: the shark breaching on the diagonal, fin up, tail forked."""
    return (f'<path d="M 440 120 C 380 118 250 170 150 290 C 232 326 368 286 440 120 Z" '
            f'{_s(c, 26)}/>'
            f'<path d="M 150 290 L 84 262 M 150 290 L 112 358" {_s(c, 26)}/>'
            f'<path d="M 276 196 L 272 84 L 372 150" {_s(c, 24)}/>'
            f'<path d="M 262 300 L 226 364 L 306 314" {_s(c, 22)}/>'
            f'<circle cx="398" cy="146" r="11" {_f(c)}/>'
            f'<path d="M 72 420 C 130 384 178 456 236 420 C 294 384 342 456 400 420 L 440 404" '
            f'{_s(c, 24)}/>')


def fdroid_robot(c):
    """F-Droid: the robot box with antennae and its round lens."""
    return (f'<path d="M 156 180 L 116 108 M 356 180 L 396 108" {_s(c, 24)}/>'
            f'<rect x="92" y="180" width="328" height="240" rx="40" {_s(c, 30)}/>'
            f'<circle cx="256" cy="300" r="80" {_s(c, 26)}/>'
            f'<path d="{_arc_cw(256, 300, 38, 200, 340)}" {_s(c, 20)}/>')


def iptvextreme_tv(c):
    """IPTV Extreme: the old set on rabbit ears, colour bars on screen, two knobs."""
    return (f'<path d="M 256 150 L 196 86 M 256 150 L 324 86" {_s(c, 22)}/>'
            f'<rect x="72" y="150" width="368" height="266" rx="36" {_s(c, 28)}/>'
            f'<rect x="108" y="186" width="228" height="194" rx="22" {_s(c, 22)}/>'
            f'<path d="M 162 222 L 162 344 M 222 222 L 222 344 M 282 222 L 282 344" {_s(c, 22)}/>'
            f'<circle cx="388" cy="236" r="16" {_f(c)}/>'
            f'<circle cx="388" cy="300" r="16" {_f(c)}/>')


def vodafone_quote(c):
    """Vodafone TV: the speech mark inside its circle."""
    return (f'<circle cx="256" cy="256" r="186" {_s(c, 26)}/>'
            f'<path d="M 318 150 C 244 150 180 206 180 286 C 180 332 212 364 254 364 '
            f'C 296 364 326 334 326 294 C 326 256 298 230 262 232 C 262 196 286 164 318 150 Z" '
            f'{_s(c, 26)}/>')


def wrestle_ring(c):
    """Wrestle Universe: the ring seen from a corner, four posts and two rope loops."""
    return (f'<path d="M 88 196 L 88 368 M 424 196 L 424 368 M 176 140 L 176 186 M 336 140 L 336 186" '
            f'{_s(c, 28)}/>'
            f'<path d="M 88 212 L 176 156 L 336 156 L 424 212 Z M 88 282 L 176 226 L 336 226 '
            f'L 424 282 Z" {_s(c, 20)}/>'
            f'<path d="M 56 368 L 456 368 L 420 424 L 92 424 Z" {_s(c, 24)}/>')


def pano_record(c):
    """Pano Scrobbler: a record under the tonearm, one groove marked."""
    return (f'<circle cx="224" cy="288" r="152" {_s(c, 26)}/>'
            f'<circle cx="224" cy="288" r="28" {_s(c, 20)}/>'
            f'<path d="{_arc_cw(224, 288, 92, 150, 260)}" {_s(c, 18)}/>'
            f'<circle cx="408" cy="100" r="22" {_s(c, 20)}/>'
            f'<path d="M 408 122 L 408 200 L 316 300" {_s(c, 24)}/>')


GLYPHS.update({
    "anten_dots": anten_dots, "inatbox_robot": inatbox_robot,
    "bazaar_bag": bazaar_bag, "coreelec_power": coreelec_power,
    "cricfy_ball": cricfy_ball, "ramcleaner_rocket": ramcleaner_rocket,
    "shark_leap": shark_leap, "fdroid_robot": fdroid_robot,
    "iptvextreme_tv": iptvextreme_tv, "vodafone_quote": vodafone_quote,
    "wrestle_ring": wrestle_ring, "pano_record": pano_record,
})


# --------------------------------------------------------------------------
# Brand marks batch 12 (2026-09-27): more letter tiles whose launcher icon,
# seen in the Projectivy Icon Pack 1.1.9 artwork (reference only), carries a
# symbol.


def _arrow_arc(cx, cy, r, a0, a1, head=34):
    """A clockwise arc from a0 to a1 degrees with an open arrowhead at a1."""
    import math
    x1, y1 = _polar(cx, cy, r, a1)
    t = math.radians(a1 + 90)
    tx, ty = math.cos(t), math.sin(t)
    nx, ny = math.cos(math.radians(a1)), math.sin(math.radians(a1))
    lx, ly = x1 - head * tx + head * 0.8 * nx, y1 - head * ty + head * 0.8 * ny
    rx, ry = x1 - head * tx - head * 0.8 * nx, y1 - head * ty - head * 0.8 * ny
    return (f"{_arc_cw(cx, cy, r, a0, a1)} "
            f"M {lx:.1f} {ly:.1f} L {x1:.1f} {y1:.1f} L {rx:.1f} {ry:.1f}")


def acestream_arrows(c):
    """Ace Stream: four arrows chasing each other round a ring."""
    d = " ".join(_arrow_arc(256, 256, 164, a, a + 62) for a in (200, 290, 20, 110))
    return f'<path d="{d}" {_s(c, 30)}/>'


def browsehere_planet(c):
    """Browse Here: the ringed planet with two eyes."""
    return (f'<path d="{_arc_cw(256, 236, 124, 196, 344)}" {_s(c, 28)}/>'
            f'<path d="{_arc_cw(256, 236, 124, 16, 164)}" {_s(c, 28)}/>'
            f'<ellipse cx="256" cy="244" rx="200" ry="58" transform="rotate(-16 256 244)" '
            f'{_s(c, 24)}/>'
            f'<circle cx="222" cy="168" r="17" {_f(c)}/>'
            f'<circle cx="286" cy="160" r="17" {_f(c)}/>')


def byedpi_dove(c):
    """ByeByeDPI: the dove in flight, wing raised, over a terminal sign."""
    return (f'<path d="M 96 352 C 170 318 250 300 326 290 C 352 252 396 248 420 270 '
            f'L 452 268 L 424 292 C 410 332 362 356 300 358 C 230 362 160 368 96 352 Z" '
            f'{_s(c, 26)}/>'
            f'<path d="M 232 300 C 214 214 168 146 104 104 C 184 104 270 170 300 290" '
            f'{_s(c, 24)}/>'
            f'<circle cx="398" cy="276" r="11" {_f(c)}/>'
            f'<rect x="232" y="382" width="164" height="78" rx="12" '
            f'transform="rotate(-8 314 421)" {_s(c, 20)}/>'
            f'<path d="M 268 406 L 294 422 L 270 440 M 312 440 L 356 434" {_s(c, 16)}/>')


def elefin_elephant(c):
    """Elefin: the elephant's broad head, tusk and curled trunk, a play button in its ear."""
    return (f'<path d="M 424 420 L 424 300 C 424 226 364 170 280 170 C 200 170 150 212 144 272 '
            f'C 138 332 124 374 94 402 C 76 420 102 444 124 422" {_s(c, 30)}/>'
            f'<path d="M 176 312 C 196 342 230 348 256 336" {_s(c, 22)}/>'
            f'<path d="M 262 228 C 352 212 392 280 360 344 C 330 380 272 366 262 334 Z" '
            f'{_s(c, 22)}/>'
            + _solid("M 292 262 L 334 288 L 292 314 Z", c, 10)
            + f'<circle cx="204" cy="244" r="13" {_f(c)}/>'
            f'<path d="M 290 172 L 344 90 L 378 190" {_s(c, 20)}/>')


def falconcast_bird(c):
    """Falcon Cast: the falcon's round head and hooked beak under cast arcs."""
    return (f'<path d="M 92 424 C 92 300 150 198 256 198 C 318 198 362 236 372 286 '
            f'L 330 300 C 322 350 284 380 240 380 C 200 380 170 404 160 440" {_s(c, 28)}/>'
            f'<circle cx="286" cy="262" r="14" {_f(c)}/>'
            f'<path d="{_arc_cw(330, 176, 70, 280, 350)} {_arc_cw(330, 176, 120, 280, 350)}" '
            f'{_s(c, 24)}/>')


def galaxyplay_sweep(c):
    """Galaxy Play: the play shape swept round from its curved left edge."""
    return (f'<path d="M 184 432 C 104 330 98 190 170 96 C 270 96 372 170 424 256 '
            f'C 356 300 270 330 200 330" {_s(c, 30)}/>'
            f'<path d="M 214 250 C 214 214 230 188 256 172" {_s(c, 22)}/>')


def gtshare_eagle(c):
    """GT Share: the eagle's head, hooked beak forward, feathers swept back."""
    return (f'<path d="M 96 150 C 200 104 330 110 392 152 C 432 180 446 220 434 262 '
            f'L 396 244 C 388 276 360 292 320 292 L 250 292 L 150 316 L 226 256 '
            f'L 116 240 L 210 204 Z" {_s(c, 26)}/>'
            f'<path d="M 334 240 L 396 244" {_s(c, 20)}/>'
            f'<circle cx="330" cy="188" r="14" {_f(c)}/>')


def hyperion_h(c):
    """Hyperion Grabber: the H on its screen, with the ambilight glow at its edges."""
    return (f'<rect x="136" y="136" width="240" height="240" rx="28" {_s(c, 26)}/>'
            f'<path d="M 200 196 L 200 316 M 312 196 L 312 316 M 200 256 L 312 256" '
            f'{_s(c, 30)}/>'
            f'<path d="M 88 176 L 88 336 M 424 176 L 424 336 M 176 88 L 336 88 M 176 424 L 336 424" '
            f'{_s(c, 22)}/>')


def shadow_ring(c):
    """Shadow: the thick ring holding a smaller disc and a crescent sweep."""
    return (f'<circle cx="256" cy="256" r="176" {_s(c, 30)}/>'
            f'<circle cx="244" cy="268" r="64" {_s(c, 26)}/>'
            f'<path d="{_arc_cw(244, 268, 112, 270, 20)}" {_s(c, 24)}/>')


def streamfire_tv(c):
    """Stream Fire: the striped set on antennae with a speech-bubble tail."""
    return (f'<path d="M 256 150 L 196 88 M 256 150 L 316 88" {_s(c, 22)}/>'
            f'<path d="M 112 150 L 400 150 C 424 150 440 166 440 190 L 440 340 '
            f'C 440 364 424 380 400 380 L 190 380 L 130 440 L 136 380 L 112 380 '
            f'C 88 380 72 364 72 340 L 72 190 C 72 166 88 150 112 150 Z" {_s(c, 28)}/>'
            f'<path d="M 184 190 L 184 340 M 256 190 L 256 340 M 328 190 L 328 340" '
            f'{_s(c, 30)}/>')


def playfy_magnifier(c):
    """PLAYFy TV: the set on its antennae with a magnifier held over its corner."""
    return (f'<path d="M 300 146 L 262 90 M 300 146 L 340 94" {_s(c, 22)}/>'
            f'<rect x="168" y="146" width="280" height="200" rx="30" {_s(c, 28)}/>'
            f'<circle cx="170" cy="332" r="72" {_s(c, 26)}/>'
            f'<path d="M 170 404 L 170 460" {_s(c, 30)}/>')


def zumba_dancer(c):
    """Zumba: the dancer's zigzag body with a raised arm, in a ring."""
    return (f'<circle cx="256" cy="256" r="184" {_s(c, 26)}/>'
            f'<circle cx="290" cy="150" r="24" {_f(c)}/>'
            f'<path d="M 170 208 L 262 196 L 214 290 L 318 290 L 250 390" {_s(c, 28)}/>'
            f'<path d="M 262 196 L 360 170" {_s(c, 24)}/>')


GLYPHS.update({
    "acestream_arrows": acestream_arrows, "browsehere_planet": browsehere_planet,
    "byedpi_dove": byedpi_dove, "elefin_elephant": elefin_elephant,
    "falconcast_bird": falconcast_bird, "galaxyplay_sweep": galaxyplay_sweep,
    "gtshare_eagle": gtshare_eagle, "hyperion_h": hyperion_h,
    "shadow_ring": shadow_ring, "streamfire_tv": streamfire_tv,
    "playfy_magnifier": playfy_magnifier, "zumba_dancer": zumba_dancer,
})


# --------------------------------------------------------------------------
# Brand marks batch 13 (2026-09-28): more letter tiles whose launcher icon,
# seen in the Projectivy Icon Pack 1.1.9 artwork (reference only), carries a
# symbol.


def uplay_swirl(c):
    """Uplay: the play arrow with a bowed back, a smaller play inside it."""
    return (f'<path d="M 112 96 C 250 110 384 184 436 256 C 384 328 250 402 112 416 '
            f'C 148 300 148 212 112 96 Z" {_s(c, 30)}/>'
            f'<path d="M 214 198 L 318 256 L 214 314 Z" {_s(c, 24)}/>')


def cast4k_tv(c):
    """Cast4K: the rounded shield on two antennae with the 4K on its face."""
    return (f'<path d="M 214 170 L 184 106 M 298 170 L 346 94" {_s(c, 22)}/>'
            f'<circle cx="346" cy="92" r="14" {_f(c)}/>'
            f'<path d="M 96 170 L 416 170 C 444 170 456 194 440 218 L 300 420 '
            f'C 280 448 232 448 212 420 L 72 218 C 56 194 68 170 96 170 Z" {_s(c, 28)}/>'
            f'<path d="M 214 216 L 160 290 L 232 290 M 212 250 L 212 334" {_s(c, 22)}/>'
            f'<path d="M 278 216 L 278 334 M 340 216 L 280 276 L 344 334" {_s(c, 22)}/>')


def dramalive_globe(c):
    """Drama Live: the globe crossed by a comet's sweep, two stars above."""
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 26)}/>'
            f'<path d="M 150 400 C 170 300 240 220 350 168" {_s(c, 30)}/>'
            f'<path d="M 214 404 C 236 330 290 270 384 232" {_s(c, 22)}/>'
            f'<circle cx="200" cy="146" r="12" {_f(c)}/>'
            f'<circle cx="262" cy="118" r="12" {_f(c)}/>')


def xstream_ribbon(c):
    """Xstream Play: a ribbon curling from a hook at its foot to a loop at its head."""
    return (f'<path d="M 188 432 C 110 404 104 318 186 296 C 282 270 364 236 360 156 '
            f'C 356 86 262 74 222 128 C 196 166 222 212 272 204" {_s(c, 32)}/>')


def mobily_screen(c):
    """Mobily TV: the screen whose lower corner runs out into a tail."""
    return (f'<path d="M 364 356 L 404 356 L 404 144 C 404 124 392 112 372 112 '
            f'L 140 112 C 120 112 108 124 108 144 L 108 324 C 108 344 120 356 140 356 '
            f'L 300 356 L 428 420" {_s(c, 30)}/>')


def rapidstreamz_tv(c):
    """Rapid Streamz: the play set on antennae, rushing forward on speed lines."""
    return (f'<path d="M 256 150 L 222 92 M 300 150 L 336 92" {_s(c, 22)}/>'
            f'<rect x="168" y="150" width="272" height="216" rx="26" {_s(c, 28)}/>'
            + _solid("M 268 212 L 356 258 L 268 304 Z", c, 14)
            + f'<path d="M 74 212 L 128 212 M 60 262 L 128 262 M 74 312 L 128 312 '
            f'M 212 366 L 212 410 M 396 366 L 396 410" {_s(c, 22)}/>')


def waveiptv_antenna(c):
    """Wave IPTV: the crossed antenna over three waves."""
    waves = " ".join(
        f"M 104 {y} C 160 {y - 40} 204 {y + 40} 256 {y} C 308 {y - 40} 352 {y + 40} 408 {y}"
        for y in (272, 332, 392))
    return (f'<path d="M 206 92 L 306 212 M 306 92 L 206 212" {_s(c, 24)}/>'
            f'<circle cx="206" cy="92" r="16" {_f(c)}/>'
            f'<circle cx="306" cy="92" r="16" {_f(c)}/>'
            f'<path d="{waves}" {_s(c, 24)}/>')


def snrt_star(c):
    """SNRT Live: the faceted five-point star."""
    import math
    outer = [_polar(256, 272, 196, -90 + 72 * k) for k in range(5)]
    inner = [_polar(256, 272, 84, -54 + 72 * k) for k in range(5)]
    pts = [p for pair in zip(outer, inner) for p in pair]
    d = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in pts) + " Z"
    facets = " ".join(f"M 256 272 L {x:.1f} {y:.1f}" for x, y in outer[::2])
    return f'<path d="{d}" {_s(c, 26)}/><path d="{facets}" {_s(c, 18)}/>'


def oilers_drop(c):
    """Oilers+: the oil drop under its arc, with the plus beside it."""
    return (f'<path d="{_arc_cw(236, 312, 156, 180, 360)}" {_s(c, 28)}/>'
            f'<path d="M 236 176 C 208 224 192 254 192 280 C 192 306 212 324 236 324 '
            f'C 260 324 280 306 280 280 C 280 254 264 224 236 176 Z" {_s(c, 24)}/>'
            f'<path d="M 412 330 L 412 418 M 368 374 L 456 374" {_s(c, 26)}/>')


def sportzx_s(c):
    """Sportz X: the play arrow folded into an S."""
    return (f'<path d="M 364 144 L 164 96 C 128 88 108 120 128 150 L 196 256 L 128 362 '
            f'C 108 392 128 424 164 416 L 408 356 C 440 348 444 316 416 296 L 300 220" '
            f'{_s(c, 30)}/>')


def redbox_box(c):
    """RedBox TV: the open box with its flaps up and a TV antenna rising out."""
    return (f'<path d="M 256 212 L 222 118 M 256 212 L 300 124" {_s(c, 22)}/>'
            f'<circle cx="222" cy="112" r="14" {_f(c)}/>'
            f'<circle cx="300" cy="118" r="14" {_f(c)}/>'
            f'<path d="M 120 232 L 392 232 L 392 424 L 120 424 Z" {_s(c, 28)}/>'
            f'<path d="M 120 232 L 64 300 M 392 232 L 448 300 M 120 232 L 170 176 '
            f'M 392 232 L 342 176" {_s(c, 24)}/>'
            f'<path d="M 120 300 L 392 300" {_s(c, 22)}/>')


def launchermanager_gear(c):
    """Launcher Manager: the eight-tooth gear with its round hub."""
    import math
    pts = []
    for k in range(8):
        a = math.radians(k * 45)
        for da, r in ((-15, 138), (-9, 184), (9, 184), (15, 138)):
            b = a + math.radians(da)
            pts.append((256 + r * math.cos(b), 256 + r * math.sin(b)))
    d = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in pts) + " Z"
    return f'<path d="{d}" {_s(c, 26)}/><circle cx="256" cy="256" r="56" {_s(c, 26)}/>'


GLYPHS.update({
    "uplay_swirl": uplay_swirl, "cast4k_tv": cast4k_tv,
    "dramalive_globe": dramalive_globe, "xstream_ribbon": xstream_ribbon,
    "mobily_screen": mobily_screen, "rapidstreamz_tv": rapidstreamz_tv,
    "waveiptv_antenna": waveiptv_antenna, "snrt_star": snrt_star,
    "oilers_drop": oilers_drop, "sportzx_s": sportzx_s,
    "redbox_box": redbox_box, "launchermanager_gear": launchermanager_gear,
})
def visionplus_vplus(c):
    """Vision+: the V drawn as one slanted wedge tapering to a rounded foot, and the plus."""
    return (_solid("M 84 138 L 170 138 L 298 380 C 310 404 296 422 272 416 "
                   "C 258 412 250 402 242 390 Z", c, 12)
            + f'<path d="M 364 90 L 364 250 M 284 170 L 444 170" {_s(c, 38)}/>')


GLYPHS.update({"visionplus_vplus": visionplus_vplus})


# --------------------------------------------------------------------------
# TCL system apps and UFM Pro (2026-09-27): drawn from a supporter's device
# screenshot of each launcher tile.


def tcl_quickpanel_layers(c):
    """TCL Quick Panel: the stack of three layers."""
    return (f'<path d="M 256 92 L 436 184 L 256 276 L 76 184 Z" {_s(c, 28)}/>'
            f'<path d="M 76 256 L 256 348 L 436 256 M 76 328 L 256 420 L 436 328" '
            f'{_s(c, 26)}/>')


def tcl_guard_shield(c):
    """TCL Safety Guard: the shield with a brush stroke across it."""
    return (f'<path d="M 256 64 C 314 96 368 108 420 108 L 420 250 C 420 344 350 414 256 452 '
            f'C 162 414 92 344 92 250 L 92 108 C 144 108 198 96 256 64 Z" {_s(c, 28)}/>'
            f'<path d="M 310 170 L 206 334" {_s(c, 32)}/>')


def tcl_exhibit_easel(c):
    """TCL T-Exhibition: a framed sunset standing on its easel legs."""
    return (f'<rect x="92" y="88" width="328" height="236" rx="22" {_s(c, 28)}/>'
            f'<path d="M 132 282 C 196 238 300 252 380 212" {_s(c, 22)}/>'
            f'<circle cx="326" cy="162" r="24" {_s(c, 20)}/>'
            f'<path d="M 180 324 L 146 436 M 332 324 L 366 436" {_s(c, 26)}/>')


def tcl_tsolo_note(c):
    """TCL T-Solo: the single music note with a hollow head."""
    return (f'<path d="M 300 92 L 300 336" {_s(c, 30)}/>'
            f'<path d="M 300 92 C 338 110 380 138 384 196" {_s(c, 28)}/>'
            f'<circle cx="236" cy="352" r="66" {_s(c, 30)}/>')


def ufm_folder_arrow(c):
    """Ultimate File Manager Pro: the folder with an arrow launching out of it."""
    return (f'<path d="M 72 150 L 200 150 L 236 190 L 300 190" {_s(c, 26)}/>'
            f'<path d="M 440 250 L 440 386 C 440 408 424 424 402 424 L 110 424 '
            f'C 88 424 72 408 72 386 L 72 150" {_s(c, 26)}/>'
            f'<path d="M 184 360 L 400 144 M 324 136 L 408 136 L 408 220" {_s(c, 28)}/>')


GLYPHS.update({
    "tcl_quickpanel_layers": tcl_quickpanel_layers, "tcl_guard_shield": tcl_guard_shield,
    "tcl_exhibit_easel": tcl_exhibit_easel, "tcl_tsolo_note": tcl_tsolo_note,
    "ufm_folder_arrow": ufm_folder_arrow,
})


def ak47_crest(c):
    """AK47Sports: two crossed cricket bats with the football at their crossing."""
    import math
    def bat(ang):
        ux, uy = math.cos(math.radians(ang)), math.sin(math.radians(ang))
        px, py = -uy, ux
        def at(t, w):
            return 256 + ux * t + px * w, 256 + uy * t + py * w
        blade = [at(40, 26), at(196, 26), at(212, 0), at(196, -26), at(40, -26)]
        d = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in blade) + " Z"
        h0, h1 = at(-200, 0), at(-60, 0)
        return (f'<path d="{d}" {_s(c, 22)}/>'
                f'<path d="M {h0[0]:.1f} {h0[1]:.1f} L {h1[0]:.1f} {h1[1]:.1f}" {_s(c, 24)}/>')
    return (bat(45) + bat(135)
            + f'<circle cx="256" cy="226" r="62" {_s(c, 24)}/>'
            + _solid("M 256 200 L 281 218 L 271 247 L 241 247 L 231 218 Z", c, 8))


def voltra_v(c):
    """Voltra TV: the V whose left arm is a lightning bolt."""
    return (_solid("M 94 104 L 178 104 L 230 244 L 202 244 L 256 404 L 168 232 L 196 232 Z", c, 12)
            + f'<path d="M 256 404 L 420 96" {_s(c, 32)}/>')


GLYPHS.update({"ak47_crest": ak47_crest, "voltra_v": voltra_v})


def tcl_guide_pages(c):
    """TCL User Guide (Användarmanual): a written page with a second page behind it."""
    return (f'<rect x="112" y="88" width="208" height="336" rx="24" {_s(c, 28)}/>'
            f'<path d="M 160 176 L 272 176 M 160 236 L 272 236 M 160 296 L 232 296" {_s(c, 22)}/>'
            f'<path d="M 324 150 L 376 150 C 396 150 404 160 404 178 L 404 396 '
            f'C 404 414 396 424 376 424 L 324 424" {_s(c, 24)}/>')


def tcl_media_folder(c):
    """TCL Media Player (Mediaspelare): the folder holding a play button."""
    return (f'<path d="M 100 172 L 100 132 L 206 132 L 238 164 L 412 164 L 412 172" {_s(c, 24)}/>'
            f'<rect x="80" y="176" width="352" height="232" rx="28" {_s(c, 28)}/>'
            + _solid("M 226 236 L 306 292 L 226 348 Z", c, 14))


def tcl_home_grid(c):
    """TCL Home: three squares of the dashboard grid, the fourth corner two bars."""
    boxes = "".join(f'<rect x="{x}" y="{y}" width="132" height="132" rx="14" {_s(c, 28)}/>'
                    for x, y in ((100, 100), (280, 100), (100, 280)))
    return boxes + f'<path d="M 284 326 L 412 326 M 284 386 L 380 386" {_s(c, 28)}/>'


GLYPHS.update({"tcl_guide_pages": tcl_guide_pages, "tcl_media_folder": tcl_media_folder,
               "tcl_home_grid": tcl_home_grid})


def tduk_killer_droid(c):
    """TDUK APP Killer: the Android robot with an X across its chest."""
    return (f'<path d="M 156 196 C 156 124 356 124 356 196 Z" {_s(c, 24)}/>'
            f'<path d="M 196 118 L 176 84 M 316 118 L 336 84" {_s(c, 20)}/>'
            f'<rect x="156" y="224" width="200" height="176" rx="24" {_s(c, 26)}/>'
            f'<path d="M 112 236 L 112 330 M 400 236 L 400 330 M 212 400 L 212 444 M 300 400 L 300 444" '
            f'{_s(c, 26)}/>'
            f'<path d="M 214 270 L 298 354 M 298 270 L 214 354" {_s(c, 26)}/>')


GLYPHS.update({"tduk_killer_droid": tduk_killer_droid})


def tubplayer_t(c):
    """TubPlayer: the T whose right arm folds down into a play arrow."""
    return (f'<path d="M 108 150 L 404 150 M 222 150 L 222 404" {_s(c, 36)}/>'
            + _solid("M 266 184 L 372 262 L 266 340 Z", c, 14))


def tduk_cleaner_droid(c):
    """TDUK APP Cache Cleaner: the Android robot's head over a big sweeping broom."""
    return (f'<path d="M 136 214 C 136 110 376 110 376 214 Z" {_s(c, 26)}/>'
            f'<path d="M 190 124 L 164 80 M 322 124 L 348 80" {_s(c, 22)}/>'
            f'<circle cx="208" cy="176" r="12" {_f(c)}/>'
            f'<circle cx="304" cy="176" r="12" {_f(c)}/>'
            f'<path d="M 360 262 L 246 360" {_s(c, 26)}/>'
            f'<path d="M 246 360 L 178 356 L 132 440 L 234 428 Z" {_s(c, 22)}/>')


GLYPHS.update({"tubplayer_t": tubplayer_t, "tduk_cleaner_droid": tduk_cleaner_droid})


def basis_b(c):
    """Basis: the B whose lower bowl holds a play arrow."""
    return (f'<path d="M 168 416 L 168 96 L 292 96 C 342 96 368 128 368 168 '
            f'C 368 206 344 230 306 236 C 364 244 400 286 400 330 C 400 382 362 416 304 416 Z" '
            f'{_s(c, 32)}/>'
            f'<path d="M 168 236 L 300 236" {_s(c, 26)}/>'
            f'<path d="M 234 284 L 316 328 L 234 372 Z" {_s(c, 22)}/>')


GLYPHS.update({"basis_b": basis_b})


# --------------------------------------------------------------------------
# Brand marks batch 14 (2026-09-28): more letter tiles whose launcher icon,
# seen in the Projectivy Icon Pack 1.1.9 artwork (reference only), carries a
# symbol.


def braviacore_lens(c):
    """Bravia Core: the lens, its rings swirling in toward the pupil."""
    return (f'<circle cx="256" cy="256" r="184" {_s(c, 28)}/>'
            f'<path d="{_arc_cw(256, 256, 124, 200, 470)}" {_s(c, 24)}/>'
            f'<path d="{_arc_cw(256, 256, 68, 20, 290)}" {_s(c, 22)}/>'
            f'<circle cx="256" cy="256" r="20" {_f(c)}/>')


def brollie_pencil(c):
    """Brollie: the pencil mascot, sharpened tip up, with its face, arms and feet."""
    return (f'<path d="M 196 184 L 256 72 L 316 184" {_s(c, 26)}/>'
            f'<rect x="196" y="184" width="120" height="206" rx="14" {_s(c, 28)}/>'
            f'<path d="M 196 344 L 316 344" {_s(c, 22)}/>'
            f'<circle cx="234" cy="244" r="12" {_f(c)}/>'
            f'<circle cx="278" cy="244" r="12" {_f(c)}/>'
            f'<path d="M 196 280 L 146 318 M 316 280 L 366 318 '
            f'M 232 390 L 216 436 M 280 390 L 296 436" {_s(c, 22)}/>')


def eon_horizon(c):
    """Eon TV: the ring rising through its horizon line."""
    return (f'<path d="M 56 256 L 160 256 M 352 256 L 456 256" {_s(c, 26)}/>'
            f'<circle cx="256" cy="256" r="96" {_s(c, 28)}/>'
            f'<path d="M 188 186 L 88 186 M 188 326 L 88 326 M 324 186 L 424 186 '
            f'M 324 326 L 424 326" {_s(c, 20)}/>')


def feb_pin(c):
    """Feb: the round head of a pin that curls down into a hooked tail."""
    return (f'<circle cx="244" cy="200" r="98" {_s(c, 32)}/>'
            f'<path d="M 342 200 L 342 318 C 342 390 290 432 226 424" {_s(c, 32)}/>'
            f'<circle cx="244" cy="200" r="24" {_f(c)}/>')


def fivetv_five(c):
    """Five TV: the 5 drawn as a flame, its flag flying off the top."""
    return (f'<path d="M 300 92 L 204 108 L 188 212 C 252 184 344 204 344 292 '
            f'C 344 380 250 414 174 368" {_s(c, 32)}/>'
            f'<path d="M 300 92 L 384 76 L 352 128" {_s(c, 26)}/>'
            f'<circle cx="236" cy="292" r="30" {_f(c)}/>')


def global_chevron(c):
    """Global TV: the bold chevron that ends its wordmark, as a pointer."""
    return (f'<path d="M 152 80 L 152 180 L 280 256 L 152 332 L 152 432 L 400 256 Z" '
            f'{_s(c, 30)}/>')


def migallery_peaks(c):
    """Mi Gallery: rounded peaks under a sun, in a photo frame."""
    return (f'<rect x="80" y="112" width="352" height="288" rx="40" {_s(c, 26)}/>'
            f'<circle cx="324" cy="196" r="34" {_s(c, 24)}/>'
            f'<path d="M 120 360 L 206 252 C 214 242 228 242 236 252 L 280 306 '
            f'L 316 270 C 324 262 336 262 344 270 L 392 330" {_s(c, 26)}/>')


def miracast_screen(c):
    """Miracast: the square screen with cast waves rising from its corner."""
    return (f'<rect x="96" y="96" width="320" height="320" rx="64" {_s(c, 28)}/>'
            f'<circle cx="178" cy="334" r="16" {_f(c)}/>'
            f'<path d="{_arc_cw(178, 334, 76, 270, 360)} {_arc_cw(178, 334, 136, 270, 360)}" '
            f'{_s(c, 26)}/>')


def onstream_bolt(c):
    """OnStream: the lightning bolt crossing an open ring."""
    return (f'<path d="{_arc_cw(256, 256, 176, 320, 580)}" {_s(c, 28)}/>'
            f'<path d="M 300 96 L 190 272 L 270 272 L 214 416 L 336 232 L 256 232 Z" '
            f'{_s(c, 26)}/>')


def swampdog_diamond(c):
    """Swampdog Media: four rounded squares set as a diamond, one standing apart."""
    def sq(cx, cy, r=62):
        return (f'<rect x="{cx - r}" y="{cy - r}" width="{2 * r}" height="{2 * r}" rx="18" '
                f'transform="rotate(45 {cx} {cy})" {_s(c, 24)}/>')
    return sq(256, 138, 56) + sq(138, 256, 56) + sq(256, 374, 56) + sq(386, 256, 48)


def talktv_bubble(c):
    """Talk TV: the round speech bubble with a small screen inside."""
    return (f'<path d="M 256 76 C 356 76 436 156 436 256 C 436 356 356 436 256 436 '
            f'C 156 436 76 356 76 256 C 76 156 156 76 256 76 Z M 400 364 L 448 440 L 356 412" '
            f'{_s(c, 28)}/>'
            f'<rect x="170" y="196" width="172" height="120" rx="18" {_s(c, 22)}/>'
            f'<path d="M 226 348 L 286 348" {_s(c, 20)}/>')


def uae4arm_stripes(c):
    """UAE4ARM: the Amiga check, three parallel stripes swept up to the right."""
    d = " ".join(f"M {80 + k * 52} 300 L {140 + k * 52} 404 L {340 + k * 52} 104" for k in range(3))
    return f'<path d="{d}" {_s(c, 28)}/>'


GLYPHS.update({
    "braviacore_lens": braviacore_lens, "brollie_pencil": brollie_pencil,
    "eon_horizon": eon_horizon, "feb_pin": feb_pin,
    "fivetv_five": fivetv_five, "global_chevron": global_chevron,
    "migallery_peaks": migallery_peaks, "miracast_screen": miracast_screen,
    "onstream_bolt": onstream_bolt, "swampdog_diamond": swampdog_diamond,
    "talktv_bubble": talktv_bubble, "uae4arm_stripes": uae4arm_stripes,
})


# --------------------------------------------------------------------------
# Brand marks batch 15 (2026-09-28): more letter tiles whose launcher icon,
# seen in the Projectivy Icon Pack 1.1.9 artwork (reference only), carries a
# symbol.


def tablo_bar(c):
    """Tablo: the long rounded bar with its TV tab hanging from the right."""
    return (f'<rect x="56" y="196" width="400" height="96" rx="48" {_s(c, 28)}/>'
            f'<path d="M 300 292 L 300 344 C 300 360 312 372 328 372 L 404 372 '
            f'C 420 372 432 360 432 344 L 432 292" {_s(c, 24)}/>'
            f'<path d="M 128 244 L 384 244" {_s(c, 20)}/>')


def radioontv_set(c):
    """Radio On TV: the set with its aerial, round speaker and keypad."""
    keys = "".join(f'<circle cx="{x}" cy="{y}" r="10" {_f(c)}/>'
                   for x in (310, 350, 390) for y in (312, 352))
    return (f'<path d="M 120 192 L 372 96" {_s(c, 22)}/>'
            f'<circle cx="380" cy="92" r="16" {_f(c)}/>'
            f'<rect x="72" y="192" width="368" height="220" rx="36" {_s(c, 28)}/>'
            f'<circle cx="178" cy="304" r="62" {_s(c, 22)}/>'
            f'<rect x="286" y="230" width="128" height="44" rx="10" {_s(c, 18)}/>' + keys)


def echogram_play(c):
    """EchoGram: a play arrow with its echo trailing behind it."""
    return (f'<path d="M 188 108 L 424 256 L 188 404 Z" {_s(c, 28)}/>'
            f'<path d="M 124 150 L 124 362 M 72 196 L 72 316" {_s(c, 26)}/>'
            + _solid("M 236 200 L 326 256 L 236 312 Z", c, 14))


def monitordot_panes(c):
    """Monitor Dot: two tall panes side by side, a split screen."""
    return (f'<rect x="80" y="112" width="160" height="288" rx="20" {_s(c, 28)}/>'
            f'<rect x="272" y="112" width="160" height="288" rx="20" {_s(c, 28)}/>'
            f'<circle cx="352" cy="256" r="26" {_f(c)}/>')


def screenscape_s(c):
    """Screenscape: the S cut into a coin."""
    return (f'<circle cx="256" cy="256" r="184" {_s(c, 28)}/>'
            f'<path d="M 346 170 L 208 170 C 170 170 150 196 150 222 C 150 250 170 266 208 266 '
            f'L 304 266 C 342 266 362 284 362 310 C 362 338 342 352 304 352 L 166 352" '
            f'{_s(c, 30)}/>')


def genplay_spiral(c):
    """GenPlay: a G that spirals in to a dot."""
    return (f'<path d="{_arc_cw(256, 256, 180, 330, 630)} L 436 256" {_s(c, 28)}/>'
            f'<path d="{_arc_cw(256, 256, 110, 30, 300)}" {_s(c, 24)}/>'
            f'<circle cx="256" cy="256" r="26" {_f(c)}/>')


def luna_figure(c):
    """Amazon Luna: three joined nodes, a head over two planted feet."""
    return (f'<path d="M 256 120 L 120 380 L 392 380 Z" {_s(c, 34)}/>'
            f'<circle cx="256" cy="120" r="48" {_s(c, 26)}/>'
            f'<circle cx="120" cy="380" r="40" {_f(c)}/>'
            f'<circle cx="392" cy="380" r="40" {_f(c)}/>')


def freshdrama_tri(c):
    """Fresh Drama: the downward triangle slashed through with a stroke."""
    return (f'<path d="M 72 112 L 440 112 L 256 432 Z" {_s(c, 28)}/>'
            f'<path d="M 150 250 L 360 170 M 190 312 L 316 264" {_s(c, 24)}/>')


def hdhomerun_box(c):
    """HDHomeRun: the rounded tuner box stamped with HD."""
    return (f'<rect x="72" y="120" width="368" height="272" rx="48" {_s(c, 28)}/>'
            f'<path d="M 144 184 L 144 328 M 232 184 L 232 328 M 144 256 L 232 256" '
            f'{_s(c, 26)}/>'
            f'<path d="M 284 184 L 284 328 L 320 328 C 368 328 384 296 384 256 '
            f'C 384 216 368 184 320 184 Z" {_s(c, 26)}/>')


def kpn_play(c):
    """KPN: the open play arrow, broken at its back, with a plus beside."""
    return (f'<path d="M 188 196 L 188 96 L 436 256 L 188 416 L 188 316" {_s(c, 30)}/>'
            f'<path d="M 128 208 L 128 304 M 80 256 L 176 256" {_s(c, 28)}/>')


def polsat_swirl(c):
    """Polsat Box Go: three rounded boxes, each turned a little further in."""
    return (f'<rect x="80" y="104" width="352" height="304" rx="88" '
            f'transform="rotate(-12 256 256)" {_s(c, 26)}/>'
            f'<rect x="150" y="160" width="232" height="196" rx="56" '
            f'transform="rotate(-24 266 258)" {_s(c, 24)}/>'
            f'<rect x="218" y="214" width="116" height="92" rx="26" '
            f'transform="rotate(-36 276 260)" {_s(c, 22)}/>')


def auvio_o(c):
    """RTBF Auvio: the heavy tilted O with its offset counter."""
    return (f'<ellipse cx="256" cy="256" rx="192" ry="136" transform="rotate(-24 256 256)" '
            f'{_s(c, 34)}/>'
            f'<ellipse cx="282" cy="240" rx="104" ry="58" transform="rotate(-24 282 240)" '
            f'{_s(c, 26)}/>')


GLYPHS.update({
    "tablo_bar": tablo_bar, "radioontv_set": radioontv_set,
    "echogram_play": echogram_play, "monitordot_panes": monitordot_panes,
    "screenscape_s": screenscape_s, "genplay_spiral": genplay_spiral,
    "luna_figure": luna_figure, "freshdrama_tri": freshdrama_tri,
    "hdhomerun_box": hdhomerun_box, "kpn_play": kpn_play,
    "polsat_swirl": polsat_swirl, "auvio_o": auvio_o,
})


def animetv_curl(c):
    """AnimeTV: one unbroken curl that winds in on itself, the swirl of its
    teal launcher mark redrawn as a single Core line. Two arcs share a
    tangent where they meet - the inner one's centre sits on the outer's
    radius - so the stroke turns inward without a corner, and it stops open
    rather than closing on a dot, which keeps it clear of GenPlay's spiral."""
    import math
    cx, cy, r_out, r_in, turn = 256, 256, 176, 104, 140
    ox, oy = _polar(cx, cy, r_out - r_in, turn)
    x0, y0 = _polar(cx, cy, r_out, 220)
    x1, y1 = _polar(cx, cy, r_out, turn)
    x2, y2 = _polar(ox, oy, r_in, turn + 250)
    d = (f"M {x0:.1f} {y0:.1f} A {r_out} {r_out} 0 1 1 {x1:.1f} {y1:.1f} "
         f"A {r_in} {r_in} 0 1 1 {x2:.1f} {y2:.1f}")
    return f'<path d="{d}" {_s(c, 34)}/>'


GLYPHS.update({"animetv_curl": animetv_curl})


# --------------------------------------------------------------------------
# Brand marks batch 16 (2026-09-28): more letter tiles whose launcher icon,
# seen in the Projectivy Icon Pack 1.1.9 artwork (reference only), carries a
# symbol.


def livenettv_badge(c):
    """LiveNetTV: the round badge holding a TV with a play and cast waves."""
    return (f'<circle cx="256" cy="256" r="188" {_s(c, 26)}/>'
            f'<rect x="146" y="188" width="196" height="140" rx="18" {_s(c, 22)}/>'
            + _solid("M 222 226 L 280 258 L 222 290 Z", c, 10)
            + f'<path d="M 214 360 L 274 360" {_s(c, 20)}/>'
            f'<path d="{_arc_cw(342, 188, 44, 270, 360)} {_arc_cw(342, 188, 80, 270, 360)}" '
            f'{_s(c, 18)}/>')


def dramaplayer_ring(c):
    """Drama Player: the ring broken at the top, a play inside, a dot at the break."""
    return (f'<path d="{_arc_cw(256, 256, 180, 300, 600)}" {_s(c, 28)}/>'
            f'<circle cx="{256 + 180 * 0.5:.0f}" cy="{256 - 180 * 0.866:.0f}" r="16" {_f(c)}/>'
            f'<path d="M 216 176 L 348 256 L 216 336 Z" {_s(c, 26)}/>')


def gallery3d_stack(c):
    """Gallery 3D: a photo with its mountain, stacked on a second print."""
    return (f'<path d="M 128 152 L 128 108 C 128 96 136 88 148 88 L 424 88 C 436 88 444 96 444 108 '
            f'L 444 320 C 444 332 436 340 424 340 L 392 340" {_s(c, 22)}/>'
            f'<rect x="68" y="152" width="324" height="272" rx="24" {_s(c, 28)}/>'
            f'<path d="M 100 392 L 196 280 L 256 344 L 296 304 L 360 392" {_s(c, 24)}/>'
            f'<circle cx="300" cy="222" r="26" {_s(c, 20)}/>')


def smartiptv_tv(c):
    """Smart IPTV: the tilted set under its three-line aerial."""
    return (f'<path d="M 196 92 L 316 84 M 208 124 L 304 118" {_s(c, 20)}/>'
            f'<rect x="96" y="164" width="320" height="244" rx="44" '
            f'transform="rotate(-5 256 286)" {_s(c, 30)}/>'
            f'<path d="M 172 270 L 340 256 M 176 330 L 300 320" {_s(c, 24)}/>')


def m3u_tv(c):
    """M3U IPTV: the flat screen on its stand, showing a playlist."""
    return (f'<rect x="72" y="112" width="368" height="248" rx="10" {_s(c, 28)}/>'
            f'<path d="M 152 408 L 360 408" {_s(c, 28)}/>'
            f'<path d="M 144 184 L 368 184 M 144 236 L 368 236 M 144 288 L 296 288" '
            f'{_s(c, 22)}/>')


def dixmax_d(c):
    """DixMax: a play arrow with a slanted spine, reading as a D."""
    return (f'<path d="M 132 84 L 436 256 L 172 436 Z" {_s(c, 30)}/>'
            f'<path d="M 212 196 L 226 330" {_s(c, 26)}/>')


def notube_n(c):
    """NoTube TV: an N whose diagonal sweeps out into a play arrow."""
    return (f'<path d="M 132 420 L 132 124 C 132 100 156 90 176 104 L 404 296 '
            f'C 424 312 414 344 388 344 L 300 344 L 300 420" {_s(c, 30)}/>')


def telia_play(c):
    """Telia Play: a play arrow in a tilted pebble."""
    return (f'<path d="M 120 176 C 136 100 240 72 344 104 C 432 132 452 226 428 306 '
            f'C 404 388 316 436 224 420 C 128 404 100 280 120 176 Z" {_s(c, 28)}/>'
            + _solid("M 228 188 L 344 262 L 228 336 Z", c, 16))


def yousee_disc(c):
    """YouSee Play: an outlined play set right of centre in its disc."""
    return (f'<circle cx="256" cy="256" r="180" {_s(c, 28)}/>'
            f'<path d="M 232 176 L 352 256 L 232 336 Z" {_s(c, 22)}/>'
            f'<path d="M 152 208 L 152 304" {_s(c, 22)}/>')


def ibplayer_ib(c):
    """IB Player: the i and b set against a play arrow."""
    return (f'<circle cx="120" cy="128" r="18" {_f(c)}/>'
            f'<path d="M 120 190 L 120 400 M 188 96 L 188 400" {_s(c, 30)}/>'
            f'<circle cx="252" cy="334" r="64" {_s(c, 26)}/>'
            f'<path d="M 300 136 L 436 216 L 336 276" {_s(c, 26)}/>')


def oblivion_face(c):
    """Oblivion: the spiky-haired face in profile-free front view."""
    return (f'<path d="M 108 236 L 132 132 L 188 186 L 220 96 L 268 170 L 320 92 L 346 184 '
            f'L 404 136 L 408 240" {_s(c, 26)}/>'
            f'<path d="M 124 236 C 124 350 190 416 256 416 C 322 416 392 350 392 236" '
            f'{_s(c, 28)}/>'
            f'<circle cx="208" cy="292" r="18" {_f(c)}/>'
            f'<circle cx="304" cy="292" r="18" {_f(c)}/>'
            f'<path d="M 224 356 C 244 370 268 370 288 356" {_s(c, 20)}/>')


def zen_z(c):
    """Zen IPTV: the Z swept round at its foot, with a play dot."""
    return (f'<path d="M 104 104 L 392 104 L 144 360 C 116 392 136 424 176 424 L 256 424" '
            f'{_s(c, 32)}/>'
            f'<circle cx="360" cy="384" r="56" {_s(c, 24)}/>'
            + _solid("M 344 358 L 384 384 L 344 410 Z", c, 8))


GLYPHS.update({
    "livenettv_badge": livenettv_badge, "dramaplayer_ring": dramaplayer_ring,
    "gallery3d_stack": gallery3d_stack, "smartiptv_tv": smartiptv_tv,
    "m3u_tv": m3u_tv, "dixmax_d": dixmax_d, "notube_n": notube_n,
    "telia_play": telia_play, "yousee_disc": yousee_disc,
    "ibplayer_ib": ibplayer_ib, "oblivion_face": oblivion_face, "zen_z": zen_z,
})


# --------------------------------------------------------------------------
# Brand marks batch 17 (2026-09-28): the last letter tiles whose launcher
# icon, seen in the Projectivy Icon Pack 1.1.9 artwork (reference only),
# carries a symbol beside its wordmark.


def aparat_ball(c):
    """Aparat Sport: the football, its centre panel and seams."""
    import math
    def pt(r, deg):
        return 256 + r * math.cos(math.radians(deg)), 256 + r * math.sin(math.radians(deg))
    pent = [pt(56, -90 + 72 * k) for k in range(5)]
    pd = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in pent) + " Z"
    seams = " ".join(f"M {pent[k][0]:.1f} {pent[k][1]:.1f} L {pt(118, -90 + 72 * k)[0]:.1f} "
                     f"{pt(118, -90 + 72 * k)[1]:.1f}" for k in range(5))
    rim = " ".join(f"M {pt(118, -90 + 72 * k)[0]:.1f} {pt(118, -90 + 72 * k)[1]:.1f} "
                   f"L {pt(176, -90 + 72 * k - 24)[0]:.1f} {pt(176, -90 + 72 * k - 24)[1]:.1f} "
                   f"M {pt(118, -90 + 72 * k)[0]:.1f} {pt(118, -90 + 72 * k)[1]:.1f} "
                   f"L {pt(176, -90 + 72 * k + 24)[0]:.1f} {pt(176, -90 + 72 * k + 24)[1]:.1f}"
                   for k in range(5))
    return (f'<circle cx="256" cy="256" r="184" {_s(c, 28)}/>' + _solid(pd, c, 10)
            + f'<path d="{seams} {rim}" {_s(c, 18)}/>')


def supreme_trident(c):
    """Supreme TV: the trident crown standing on its bar."""
    return (f'<path d="M 256 92 L 256 380 M 160 120 C 160 220 200 260 256 260 '
            f'C 312 260 352 220 352 120" {_s(c, 28)}/>'
            f'<path d="M 160 120 L 136 156 M 160 120 L 184 156 M 352 120 L 328 156 '
            f'M 352 120 L 376 156 M 256 92 L 232 128 M 256 92 L 280 128" {_s(c, 20)}/>'
            f'<path d="M 120 420 L 392 420 M 200 380 L 312 380" {_s(c, 26)}/>')


def betterxc_cubes(c):
    """Better xCloud: two stacks of blocks joined in the middle, an H set on edge."""
    def cube(x, y, s=76):
        h = s * 0.5
        return (f"M {x} {y} L {x + s} {y - h} L {x + 2 * s} {y} L {x + s} {y + h} Z "
                f"M {x} {y} L {x} {y + s} L {x + s} {y + s + h} L {x + s} {y + h} "
                f"M {x + 2 * s} {y} L {x + 2 * s} {y + s} L {x + s} {y + s + h}")
    d = " ".join(cube(x, y) for x, y in ((80, 120), (80, 304), (280, 212)))
    return f'<path d="{d}" {_s(c, 20)}/>'


def artlume_frame(c):
    """Artlume: the open frame sitting on a wedge of light."""
    return (f'<path d="M 176 96 L 416 96 L 416 316 L 176 316 Z" {_s(c, 30)}/>'
            f'<path d="M 256 176 L 336 176 L 336 244" {_s(c, 22)}/>'
            f'<path d="M 80 424 L 176 316 L 416 316 L 360 424 Z" {_s(c, 24)}/>')


def babyeinstein_sun(c):
    """Baby Einstein: the round face in glasses under a halo of wild hair."""
    import math
    rays = " ".join(
        f"M {256 + 150 * math.cos(math.radians(a)):.1f} {276 + 150 * math.sin(math.radians(a)):.1f} "
        f"L {256 + 200 * math.cos(math.radians(a)):.1f} {276 + 200 * math.sin(math.radians(a)):.1f}"
        for a in range(-165, -10, 22))
    return (f'<circle cx="256" cy="296" r="126" {_s(c, 28)}/>'
            f'<path d="{rays}" {_s(c, 20)}/>'
            f'<circle cx="208" cy="282" r="34" {_s(c, 20)}/>'
            f'<circle cx="304" cy="282" r="34" {_s(c, 20)}/>'
            f'<path d="M 242 282 L 270 282 M 208 350 C 236 376 276 376 304 350" {_s(c, 20)}/>')


def dansk_globe(c):
    """Dansk Filmskat: the globe with the heart beside it."""
    return (f'<circle cx="228" cy="292" r="140" {_s(c, 26)}/>'
            f'<ellipse cx="228" cy="292" rx="60" ry="140" {_s(c, 18)}/>'
            f'<path d="M 88 292 L 368 292 M 112 222 L 344 222 M 112 362 L 344 362" {_s(c, 18)}/>'
            f'<path d="M 384 176 C 344 148 330 110 354 92 C 372 80 384 92 384 104 '
            f'C 384 92 396 80 414 92 C 438 110 424 148 384 176 Z" {_s(c, 20)}/>')


def mst3k_moon(c):
    """MST3K: the cratered moon with a satellite crossing in front."""
    return (f'<circle cx="256" cy="256" r="180" {_s(c, 28)}/>'
            f'<circle cx="196" cy="188" r="40" {_s(c, 20)}/>'
            f'<circle cx="320" cy="224" r="26" {_s(c, 18)}/>'
            f'<circle cx="236" cy="326" r="32" {_s(c, 18)}/>'
            f'<path d="M 316 336 L 404 336 M 360 314 L 360 358" {_s(c, 22)}/>')


def yippee_confetti(c):
    """Yippee: a burst of confetti strips and dots."""
    strips = ((150, 150, 30), (256, 104, 90), (362, 150, 150), (406, 256, 0),
              (362, 362, 30), (256, 408, 90), (150, 362, 150), (106, 256, 0))
    d = " ".join(
        f"M {x - 22 * __import__('math').cos(__import__('math').radians(a)):.1f} "
        f"{y - 22 * __import__('math').sin(__import__('math').radians(a)):.1f} "
        f"L {x + 22 * __import__('math').cos(__import__('math').radians(a)):.1f} "
        f"{y + 22 * __import__('math').sin(__import__('math').radians(a)):.1f}"
        for x, y, a in strips)
    dots = "".join(f'<circle cx="{x}" cy="{y}" r="14" {_f(c)}/>'
                   for x, y in ((206, 196), (306, 196), (306, 316), (206, 316), (256, 256)))
    return f'<path d="{d}" {_s(c, 26)}/>' + dots


def bloomberg_tab(c):
    """Bloomberg TV+: the tab, its top corner folded back."""
    return (f'<path d="M 96 136 L 336 136 L 416 216 L 416 376 L 96 376 Z" {_s(c, 30)}/>'
            f'<path d="M 336 136 L 336 216 L 416 216" {_s(c, 24)}/>')


def mtvkatsomo_play(c):
    """MTV Katsomo: the m's arches perched above a ringed play button."""
    return (f'<path d="M 84 220 L 84 128 C 84 92 136 92 136 128 L 136 220 '
            f'M 136 128 C 136 92 188 92 188 128 L 188 220" {_s(c, 24)}/>'
            f'<circle cx="300" cy="300" r="132" {_s(c, 30)}/>'
            + _solid("M 262 236 L 362 300 L 262 364 Z", c, 14))


def kuku_k(c):
    """KUKU TV: the lowercase k and its dot in a rounded square."""
    return (f'<rect x="88" y="88" width="336" height="336" rx="64" {_s(c, 28)}/>'
            f'<path d="M 184 148 L 184 364 M 296 212 L 188 290 L 300 364" {_s(c, 28)}/>'
            f'<circle cx="340" cy="344" r="22" {_f(c)}/>')


def pepperbox_guns(c):
    """Pepperbox TV: two pepperbox pistols standing barrel-up, side by side."""
    def gun(x):
        return (f'<rect x="{x}" y="72" width="68" height="200" rx="14" {_s(c, 22)}/>'
                f'<path d="M {x} 172 L {x + 68} 172" {_s(c, 16)}/>'
                f'<path d="M {x + 10} 272 L {x - 20} 424 L {x + 44} 424 L {x + 60} 272 '
                f'M {x + 64} 296 C {x + 116} 296 {x + 116} 372 {x + 50} 372" {_s(c, 22)}/>')
    return gun(118) + gun(290)


GLYPHS.update({
    "aparat_ball": aparat_ball, "supreme_trident": supreme_trident,
    "betterxc_cubes": betterxc_cubes, "artlume_frame": artlume_frame,
    "babyeinstein_sun": babyeinstein_sun, "dansk_globe": dansk_globe,
    "mst3k_moon": mst3k_moon, "yippee_confetti": yippee_confetti,
    "bloomberg_tab": bloomberg_tab, "mtvkatsomo_play": mtvkatsomo_play,
    "kuku_k": kuku_k, "pepperbox_guns": pepperbox_guns,
})


def airpin_screen(c):
    """AirPin Pro: the screen with its crosshair and a clip hooked on the corner."""
    return (f'<rect x="96" y="112" width="336" height="228" rx="36" {_s(c, 28)}/>'
            f'<path d="M 264 164 L 264 196 M 264 228 L 264 260 M 264 292 L 264 300 '
            f'M 160 226 L 192 226 M 224 226 L 304 226 M 336 226 L 368 226" {_s(c, 16)}/>'
            f'<path d="M 96 400 L 176 400 C 208 400 208 348 176 348 L 132 348 '
            f'C 104 348 104 380 132 380 L 168 380" {_s(c, 22)}/>')


def apkupdater_thumb(c):
    """APK Updater: the thumbs-up inside the round badge."""
    return (f'<circle cx="256" cy="256" r="184" {_s(c, 28)}/>'
            f'<path d="M 156 250 L 204 250 L 204 362 L 156 362 Z" {_s(c, 22)}/>'
            f'<path d="M 204 262 L 250 168 C 262 144 300 150 294 186 L 284 236 '
            f'L 348 236 C 372 236 382 258 372 278 L 340 348 C 334 358 326 362 314 362 '
            f'L 204 362" {_s(c, 24)}/>')


def bapl_pulse(c):
    """Background Apps and Process List: a heartbeat trace running into a heart."""
    return (f'<path d="M 72 276 L 128 276 L 150 236 L 176 332 L 204 176 L 232 316 '
            f'L 252 276 L 280 276" {_s(c, 24)}/>'
            f'<path d="M 370 360 C 300 314 276 260 304 226 C 326 200 358 208 370 234 '
            f'C 382 208 414 200 436 226 C 464 260 440 314 370 360 Z" {_s(c, 24)}/>')


def directone_block(c):
    """Direct One: the diamond block with a square cut out of its heart."""
    return (f'<path d="M 256 76 L 436 256 L 256 436 L 76 256 Z" {_s(c, 30)}/>'
            f'<path d="M 208 208 L 304 208 L 304 304 L 208 304 Z" {_s(c, 26)}/>'
            f'<path d="M 256 76 L 256 208 M 436 256 L 304 256 M 256 436 L 256 304 '
            f'M 76 256 L 208 256" {_s(c, 18)}/>')


def premiumize_check(c):
    """Premiumize: the figure with the big tick sweeping past its shoulder."""
    return (f'<circle cx="200" cy="140" r="56" {_s(c, 26)}/>'
            f'<path d="M 96 420 C 96 300 140 240 200 240 C 240 240 268 256 284 284" {_s(c, 28)}/>'
            f'<path d="M 212 340 L 288 412 L 428 228" {_s(c, 36)}/>')


def radionet_o(c):
    """radio.net: the o with its antenna reaching out to a dot."""
    return (f'<circle cx="224" cy="300" r="132" {_s(c, 32)}/>'
            f'<path d="M 312 204 L 372 138" {_s(c, 22)}/>'
            f'<circle cx="392" cy="116" r="32" {_s(c, 22)}/>'
            f'<circle cx="392" cy="116" r="8" {_f(c)}/>')


def tele2_dots(c):
    """Tele2 Play: the loose cluster of seven dots."""
    dots = ((150, 110, 30), (150, 214, 30), (150, 318, 30), (150, 410, 30),
            (262, 166, 34), (262, 290, 34), (378, 236, 38))
    return "".join(f'<circle cx="{x}" cy="{y}" r="{r}" {_f(c)}/>' for x, y, r in dots)


def mo4media_camera(c):
    """MO4Media: the camera with its round lens and the note's hook on the body."""
    return (f'<path d="M 80 180 L 176 180 L 204 128 L 308 128 L 336 180 L 432 180 '
            f'L 432 392 L 80 392 Z" {_s(c, 28)}/>'
            f'<circle cx="256" cy="288" r="72" {_s(c, 26)}/>'
            f'<circle cx="256" cy="288" r="22" {_f(c)}/>'
            f'<circle cx="384" cy="232" r="14" {_f(c)}/>')


def universal_ring(c):
    """Play Universal: the globe ring split by its band, with the plus below."""
    return (f'<path d="{_arc_cw(256, 256, 176, 196, 344)}" {_s(c, 30)}/>'
            f'<path d="{_arc_cw(256, 256, 176, 16, 164)}" {_s(c, 30)}/>'
            f'<path d="M 96 256 L 416 256" {_s(c, 30)}/>'
            f'<path d="M 256 310 L 256 386 M 218 348 L 294 348" {_s(c, 26)}/>')


def leankey_play(c):
    """LeanKey Keyboard: the rounded play arrow with three key rows inside."""
    return (f'<path d="M 124 112 C 124 88 144 78 166 90 L 404 232 C 424 244 424 268 404 280 '
            f'L 166 422 C 144 434 124 424 124 400 Z" {_s(c, 28)}/>'
            f'<path d="M 180 204 L 276 204 M 180 256 L 316 256 M 180 308 L 276 308" {_s(c, 26)}/>')


def odido_tv(c):
    """Odido TV: the flat screen with its play wedge and a wide foot."""
    return (f'<rect x="72" y="108" width="368" height="244" rx="28" {_s(c, 28)}/>'
            + _solid("M 222 176 L 316 230 L 222 284 Z", c, 16)
            + f'<path d="M 176 412 L 336 412" {_s(c, 28)}/>')


def npostart_tiles(c):
    """NPO Start: two tilted tiles overlapping, the front one carrying the play."""
    return (f'<path d="M 176 104 L 272 168 L 208 264 L 112 200 Z" {_s(c, 24)}/>'
            f'<path d="M 312 160 L 432 280 L 312 400 L 192 280 Z" {_s(c, 30)}/>'
            + _solid("M 286 238 L 358 280 L 286 322 Z", c, 12))


GLYPHS.update({
    "airpin_screen": airpin_screen, "apkupdater_thumb": apkupdater_thumb,
    "bapl_pulse": bapl_pulse, "directone_block": directone_block,
    "premiumize_check": premiumize_check, "radionet_o": radionet_o,
    "tele2_dots": tele2_dots, "mo4media_camera": mo4media_camera,
    "universal_ring": universal_ring, "leankey_play": leankey_play,
    "odido_tv": odido_tv, "npostart_tiles": npostart_tiles,
})


def vibra_v(c):
    """Vibra: the V whose arms curl over into loops at the top."""
    return (f'<path d="M 256 424 L 156 196 C 128 132 176 88 212 110 C 246 132 222 190 180 176 '
            f'M 256 424 L 356 196 C 384 132 336 88 300 110 C 266 132 290 190 332 176" '
            f'{_s(c, 30)}/>')


def waipu_pills(c):
    """waipu.tv: two capsules leaning right, the front one overlapping the back."""
    import math
    def capsule(cx, w):
        a = math.radians(14)
        dx, dy = math.sin(a) * 130, -math.cos(a) * 130
        nx, ny = math.cos(a) * 52, math.sin(a) * 52
        x1, y1, x2, y2 = cx - dx, 256 - dy, cx + dx, 256 + dy
        return (f'<path d="M {x1 - nx:.1f} {y1 - ny:.1f} L {x2 - nx:.1f} {y2 - ny:.1f} '
                f'A 52 52 0 0 1 {x2 + nx:.1f} {y2 + ny:.1f} L {x1 + nx:.1f} {y1 + ny:.1f} '
                f'A 52 52 0 0 1 {x1 - nx:.1f} {y1 - ny:.1f} Z" {_s(c, w)}/>')
    return capsule(212, 24) + capsule(300, 30)


def tencent_play(c):
    """Tencent Video: the rounded play wedge wrapped by a sweeping outer arc."""
    return (f'<path d="M 128 136 C 128 104 152 88 180 104 L 380 220 C 408 236 408 276 380 292 '
            f'L 180 408 C 152 424 128 408 128 376" {_s(c, 30)}/>'
            + _solid("M 204 196 L 316 256 L 204 316 Z", c, 16)
            + f'<path d="M 128 196 L 128 316" {_s(c, 30)}/>')


def wink_chevron(c):
    """Wink: the fat chevron built from two bars meeting at a point."""
    return (f'<path d="M 152 92 L 280 92 L 424 256 L 280 420 L 152 420 L 296 256 Z" {_s(c, 28)}/>'
            f'<path d="M 96 256 L 216 256" {_s(c, 28)}/>')


def filimo_hex(c):
    """Filimo: the rounded hexagon cradling a play wedge."""
    import math
    pts = [(256 + 180 * math.cos(math.radians(a)), 256 + 180 * math.sin(math.radians(a)))
           for a in range(-90, 270, 60)]
    d = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in pts) + " Z"
    return (f'<path d="{d}" {_s(c, 30)}/>'
            + _solid("M 218 180 L 336 256 L 218 332 Z", c, 16))


def filmnet_flag(c):
    """Filmnet: the swept F, its two bars trailing off like a flag in the wind."""
    return (f'<path d="M 136 420 L 176 120 C 250 96 330 132 408 96" {_s(c, 32)}/>'
            f'<path d="M 160 268 C 220 248 290 280 360 252" {_s(c, 30)}/>')


def anilab_badge(c):
    """AniLab: the tilted rounded badge with its stacked s-strokes."""
    return (f'<path d="M 256 72 L 420 164 L 420 348 L 256 440 L 92 348 L 92 164 Z" {_s(c, 28)}/>'
            f'<path d="M 316 180 L 204 244 M 308 268 L 196 332" {_s(c, 36)}/>')


def nvplayer_p(c):
    """NV Player: a P assembled from a stem and a bowl that do not quite meet."""
    return (f'<path d="M 152 104 L 152 424" {_s(c, 36)}/>'
            f'<path d="M 200 104 L 272 104 C 368 104 392 164 392 204 C 392 244 368 304 272 304 '
            f'L 200 304" {_s(c, 32)}/>')


def otf_chevrons(c):
    """OTF TV: a play arrow folded from two overlapping blades."""
    return (f'<path d="M 128 96 L 408 256 L 128 416 Z" {_s(c, 28)}/>'
            f'<path d="M 128 96 L 264 256 L 128 416" {_s(c, 24)}/>')


def lemino_bubble(c):
    """Lemino: the speech bubble with its tail kicked out at the bottom right."""
    return (f'<path d="M 108 116 L 404 116 C 420 116 428 124 428 140 L 428 320 C 428 336 420 344 404 344 '
            f'L 356 344 L 340 412 L 288 344 L 108 344 C 92 344 84 336 84 320 L 84 140 '
            f'C 84 124 92 116 108 116 Z" {_s(c, 28)}/>'
            f'<path d="M 160 208 C 160 260 200 284 256 284 C 312 284 352 260 352 208" {_s(c, 26)}/>')


def unifi_smile(c):
    """unifi TV: the two eyes and the wide smile under them."""
    return (f'<circle cx="192" cy="176" r="40" {_f(c)}/>'
            f'<circle cx="320" cy="176" r="40" {_f(c)}/>'
            f'<path d="M 112 272 C 132 380 212 420 256 420 C 300 420 380 380 400 272" {_s(c, 32)}/>')


def saff_pixels(c):
    """SAFF: the stepped cluster of square pixels climbing to the corner."""
    sq = ((96, 320), (192, 320), (288, 320), (192, 224), (288, 224), (288, 128), (384, 128), (384, 32))
    return "".join(f'<rect x="{x - 8}" y="{y + 56}" width="72" height="72" rx="10" {_s(c, 22)}/>'
                   for x, y in sq)


GLYPHS.update({
    "vibra_v": vibra_v, "waipu_pills": waipu_pills, "tencent_play": tencent_play,
    "wink_chevron": wink_chevron, "filimo_hex": filimo_hex, "filmnet_flag": filmnet_flag,
    "anilab_badge": anilab_badge, "nvplayer_p": nvplayer_p, "otf_chevrons": otf_chevrons,
    "lemino_bubble": lemino_bubble, "unifi_smile": unifi_smile, "saff_pixels": saff_pixels,
})


def fpt_badge(c):
    """FPT Play: the soft-cornered square with a play wedge set inside."""
    return (f'<rect x="88" y="88" width="336" height="336" rx="104" {_s(c, 30)}/>'
            + _solid("M 214 170 L 350 256 L 214 342 Z", c, 18))


def cliptv_c(c):
    """Clip TV: the square tile with its C turned out of the middle."""
    return (f'<rect x="88" y="88" width="336" height="336" rx="44" {_s(c, 28)}/>'
            f'<path d="{_arc_cw(256, 256, 96, 40, 320)}" {_s(c, 34)}/>')


def svt_tplay(c):
    """SVT Play: the lowercase t running into a solid play arrow."""
    return (f'<path d="M 144 104 L 144 360 C 144 396 168 412 204 404 M 96 200 L 204 200" '
            f'{_s(c, 32)}/>'
            + _solid("M 256 176 L 416 272 L 256 368 Z", c, 22))


def rtp_wedge(c):
    """RTP Play: a narrow wedge chasing the play arrow in front of it."""
    return (f'<path d="M 104 136 L 176 256 L 104 376" {_s(c, 26)}/>'
            + _solid("M 216 136 L 408 256 L 216 376 Z", c, 20))


def rtlplay_prism(c):
    """RTL Play: the rounded play arrow cut into three facets."""
    return (f'<path d="M 132 112 C 132 88 152 78 172 90 L 400 230 C 420 242 420 270 400 282 '
            f'L 172 422 C 152 434 132 424 132 400 Z" {_s(c, 28)}/>'
            f'<path d="M 132 112 L 226 256 L 132 400 M 226 256 L 412 256" {_s(c, 20)}/>')


def thunder_bolt(c):
    """Thunder TV: the forked bolt with a spark flicking off its tip."""
    return (f'<path d="M 300 72 L 160 276 L 252 276 L 208 440 L 364 216 L 272 216 L 316 72 Z" '
            f'{_s(c, 28)}/>'
            f'<path d="M 96 176 L 140 196 M 380 336 L 424 356" {_s(c, 24)}/>')


def tim_bars(c):
    """TIM Vision: the TIM bars, three rows breaking around the middle."""
    return (f'<path d="M 88 152 L 224 152 M 288 152 L 424 152 '
            f'M 88 256 L 152 256 M 216 256 L 296 256 M 360 256 L 424 256 '
            f'M 88 360 L 224 360 M 288 360 L 424 360" {_s(c, 44)}/>')


def tvp_box(c):
    """TVP VOD: the small TVP tab sitting on a screen with a ringed play."""
    return (f'<rect x="176" y="72" width="160" height="72" rx="14" {_s(c, 22)}/>'
            f'<rect x="80" y="176" width="352" height="256" rx="36" {_s(c, 28)}/>'
            f'<circle cx="256" cy="304" r="76" {_s(c, 22)}/>'
            + _solid("M 236 268 L 292 304 L 236 340 Z", c, 12))


def yacine_set(c):
    """Yacine TV: the wide set with a V antenna and TV lettered across its screen."""
    return (f'<rect x="72" y="176" width="368" height="240" rx="32" {_s(c, 28)}/>'
            f'<path d="M 256 176 L 196 96 M 256 176 L 316 96" {_s(c, 22)}/>'
            f'<path d="M 144 236 L 236 236 M 190 236 L 190 356 M 272 236 L 314 356 L 356 236" '
            f'{_s(c, 26)}/>')


def tvgarden_sprout(c):
    """TV Garden: the rounded set on ball-tipped antennae, a sprout on its screen."""
    return (f'<rect x="88" y="160" width="336" height="264" rx="72" {_s(c, 28)}/>'
            f'<path d="M 204 160 L 164 104 M 308 160 L 348 104" {_s(c, 20)}/>'
            f'<circle cx="160" cy="96" r="20" {_f(c)}/><circle cx="352" cy="96" r="20" {_f(c)}/>'
            f'<path d="M 256 368 L 256 268 M 256 300 C 256 256 216 236 184 244 '
            f'C 188 280 216 300 256 300 M 256 284 C 256 244 296 224 328 232 '
            f'C 324 268 296 284 256 284" {_s(c, 20)}/>')


def zaap_set(c):
    """Zaap TV: the chunky framed set with its screen inset and two stubby feet."""
    return (f'<rect x="72" y="100" width="368" height="288" rx="40" {_s(c, 28)}/>'
            f'<rect x="124" y="148" width="264" height="192" rx="28" {_s(c, 22)}/>'
            f'<path d="M 144 388 L 144 428 M 368 388 L 368 428" {_s(c, 28)}/>')


def yettel_play(c):
    """Yettel TV: the thin ring with the play wedge leaning into it."""
    return (f'<circle cx="256" cy="256" r="176" {_s(c, 22)}/>'
            f'<path d="M 214 170 C 214 158 224 152 234 158 L 352 238 C 364 246 364 266 352 274 '
            f'L 234 354 C 224 360 214 354 214 342 Z" {_s(c, 26)}/>')


def streamlocator_pin(c):
    """Stream Locator: the map pin with a play arrow where the hole would be."""
    return (f'<path d="M 256 440 C 256 440 104 296 104 204 C 104 120 172 64 256 64 '
            f'C 340 64 408 120 408 204 C 408 296 256 440 256 440 Z" {_s(c, 28)}/>'
            + _solid("M 222 148 L 318 204 L 222 260 Z", c, 14))


def vieon_on(c):
    """VieON: the ring with its bright centre dot, and the N's bracket beside it."""
    return (f'<circle cx="224" cy="256" r="152" {_s(c, 28)}/>'
            f'<circle cx="224" cy="256" r="72" {_f(c)}/>'
            f'<path d="M 408 128 C 448 176 448 336 408 384" {_s(c, 26)}/>')


def hoichoi_bang(c):
    """hoichoi: the two leaning exclamation marks, one tall and one short."""
    return (f'<path d="M 212 88 L 188 324 M 332 168 L 316 324" {_s(c, 40)}/>'
            f'<circle cx="182" cy="400" r="26" {_f(c)}/><circle cx="312" cy="400" r="26" {_f(c)}/>')


def hyperspin_swirl(c):
    """HyperSpin: three arcs whirling round a hub, each a turn behind the last."""
    return (f'<path d="{_arc_cw(256, 256, 180, 200, 330)}" {_s(c, 30)}/>'
            f'<path d="{_arc_cw(256, 256, 180, 350, 480)}" {_s(c, 30)}/>'
            f'<path d="{_arc_cw(256, 256, 110, 60, 200)}" {_s(c, 26)}/>'
            f'<path d="{_arc_cw(256, 256, 110, 240, 380)}" {_s(c, 26)}/>'
            f'<circle cx="256" cy="256" r="28" {_f(c)}/>')


def topradio_cloud(c):
    """Top Radio: the puffed-up bubble with its tail hooked underneath."""
    return (f'<path d="M 148 344 C 88 344 72 272 120 244 C 108 180 172 140 220 168 '
            f'C 244 112 336 112 356 176 C 420 172 452 256 400 300 C 408 336 380 352 352 344 '
            f'L 260 344 L 220 416 L 212 344 Z" {_s(c, 28)}/>')


def tv4_four(c):
    """TV4 Play: the numeral 4 with a play arrow tucked under its arm."""
    return (f'<path d="M 232 80 L 96 300 L 280 300 M 232 80 L 232 432" {_s(c, 36)}/>'
            + _solid("M 324 200 L 428 264 L 324 328 Z", c, 16))


def sunnxt_x(c):
    """Sun NXT: the X with a play arrow in its crossing and the sun's rays above."""
    return (f'<path d="M 120 176 L 208 264 M 304 360 L 392 448 M 392 176 L 304 264 '
            f'M 208 360 L 120 448" {_s(c, 36)}/>'
            + _solid("M 228 272 L 300 312 L 228 352 Z", c, 12)
            + f'<path d="M 176 80 L 196 120 M 256 60 L 256 108 M 336 80 L 316 120" {_s(c, 22)}/>')


def tvnz_plus(c):
    """TVNZ+: the plus drawn as a hollow cross with rounded arms."""
    return (f'<path d="M 212 80 L 300 80 L 300 212 L 432 212 L 432 300 L 300 300 L 300 432 '
            f'L 212 432 L 212 300 L 80 300 L 80 212 L 212 212 Z" {_s(c, 28)}/>')


def tamasha_chevrons(c):
    """Tamashakhoneh TV: three chevrons marching right."""
    return (f'<path d="M 80 136 L 176 256 L 80 376 M 188 136 L 284 256 L 188 376 '
            f'M 296 136 L 392 256 L 296 376" {_s(c, 40)}/>')


def swac_star(c):
    """Swac TV: the five-point star with speed lines trailing off behind it."""
    import math
    pts = []
    for k in range(10):
        r = 168 if k % 2 == 0 else 72
        a = math.radians(-90 + 36 * k)
        pts.append((296 + r * math.cos(a), 272 + r * math.sin(a)))
    d = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in pts) + " Z"
    return (f'<path d="{d}" {_s(c, 26)}/>'
            f'<path d="M 72 232 L 132 232 M 88 300 L 140 300" {_s(c, 24)}/>')


def esde_es(c):
    """ES-DE: the rounded block with its E and S set side by side."""
    return (f'<rect x="72" y="104" width="368" height="304" rx="48" {_s(c, 28)}/>'
            f'<path d="M 232 176 L 144 176 L 144 336 L 232 336 M 144 256 L 216 256" {_s(c, 28)}/>'
            f'<path d="M 376 188 C 360 168 280 164 280 214 C 280 262 376 248 376 294 '
            f'C 376 344 296 344 276 320" {_s(c, 28)}/>')


def telequebec_cards(c):
    """Tele-Quebec: three tilted cards fanned one behind the other."""
    return (f'<path d="M 152 104 L 424 152 L 392 280 L 120 232 Z" {_s(c, 24)}/>'
            f'<path d="M 120 232 L 104 296 L 376 344 L 392 280" {_s(c, 24)}/>'
            f'<path d="M 104 296 L 88 360 L 360 408 L 376 344" {_s(c, 24)}/>')


def go3_three(c):
    """Go3: the three with its flat top and deep round belly."""
    return (f'<path d="M 144 104 L 360 104 L 248 216 C 352 208 400 264 400 320 '
            f'C 400 384 344 424 272 424 C 208 424 160 396 136 352" {_s(c, 40)}/>')


def football360_ring(c):
    """Football 360: the ball inside the arrowed ring that runs all the way round."""
    return (f'<circle cx="256" cy="256" r="104" {_s(c, 26)}/>'
            + _solid("M 256 212 L 298 242 L 282 290 L 230 290 L 214 242 Z", c, 10)
            + f'<path d="{_arc_cw(256, 256, 176, 120, 400)}" {_s(c, 26)}/>'
            + _solid("M 408 160 L 420 232 L 360 204 Z", c, 10))


def freeflix_play(c):
    """FreeFlix HQ: the play arrow with two speed slashes in front of it."""
    return (f'<path d="M 72 176 L 136 176 M 88 256 L 152 256 M 72 336 L 136 336" {_s(c, 24)}/>'
            f'<path d="M 196 112 L 428 256 L 196 400 Z" {_s(c, 32)}/>')


def my5_five(c):
    """My5: the five with its square shoulder and round belly."""
    return (f'<path d="M 360 96 L 176 96 L 160 240 C 196 216 232 208 264 208 '
            f'C 336 208 384 256 384 320 C 384 384 336 424 264 424 C 208 424 168 404 144 376" '
            f'{_s(c, 42)}/>')


def movistar_m(c):
    """Movistar+: the soft M of two rounded humps with its plus above."""
    return (f'<path d="M 88 408 L 124 196 C 132 152 180 148 196 188 L 256 336 L 316 188 '
            f'C 332 148 380 152 388 196 L 424 408" {_s(c, 36)}/>'
            f'<path d="M 376 64 L 376 144 M 336 104 L 416 104" {_s(c, 28)}/>')


def freetv_ring(c):
    """Free TV: the open ring that sweeps round a small tv set."""
    return (f'<path d="{_arc_cw(256, 256, 176, 250, 560)}" {_s(c, 28)}/>'
            f'<path d="M 192 208 L 256 208 M 224 208 L 224 316 M 280 208 L 304 316 L 328 208" '
            f'{_s(c, 28)}/>')


def delta_d(c):
    """Delta TV: the D with a delta cut into its heart."""
    return (f'<path d="M 104 96 L 240 96 C 352 96 416 168 416 256 C 416 344 352 416 240 416 '
            f'L 104 416 Z" {_s(c, 30)}/>'
            f'<path d="M 184 336 L 256 184 L 328 336 Z" {_s(c, 24)}/>')


def digi_dot(c):
    """Digi TV: the i's stem and dot followed by a small play arrow."""
    return (f'<path d="M 176 216 L 176 424" {_s(c, 44)}/>'
            f'<circle cx="176" cy="116" r="36" {_f(c)}/>'
            + _solid("M 272 208 L 408 304 L 272 400 Z", c, 18))


def mytv_wings(c):
    """MyTVOnline: three swept bands rising over the screen line."""
    return (f'<path d="M 72 304 C 180 192 300 140 440 136 M 104 360 C 204 260 316 216 440 212 '
            f'M 136 416 C 228 328 332 292 440 288" {_s(c, 30)}/>')


def tv2_disc(c):
    """TV 2 Play: the ring with the v and 2 set tightly inside."""
    return (f'<circle cx="256" cy="256" r="180" {_s(c, 28)}/>'
            f'<path d="M 128 208 L 172 312 L 216 208" {_s(c, 28)}/>'
            f'<path d="M 256 220 C 264 184 352 184 352 232 C 352 268 280 290 256 312 L 360 312" '
            f'{_s(c, 28)}/>')


def zdf_two(c):
    """ZDF: the big 2 whose foot runs out of the ring to the right."""
    return (f'<path d="{_arc_cw(232, 256, 168, 10, 345)}" {_s(c, 28)}/>'
            f'<path d="M 152 208 C 160 144 296 136 304 208 C 312 264 200 300 152 344 L 440 344" '
            f'{_s(c, 34)}/>')


def tflix_t(c):
    """Tflix: the T in its disc with a spray of sparks flying off the corner."""
    return (f'<circle cx="280" cy="288" r="148" {_s(c, 28)}/>'
            f'<path d="M 212 232 L 348 232 M 280 232 L 368 232 M 280 232 L 280 368" {_s(c, 30)}/>'
            f'<circle cx="112" cy="112" r="18" {_f(c)}/><circle cx="168" cy="80" r="12" {_f(c)}/>'
            f'<circle cx="92" cy="176" r="12" {_f(c)}/><circle cx="148" cy="144" r="10" {_f(c)}/>')


GLYPHS.update({
    "fpt_badge": fpt_badge, "cliptv_c": cliptv_c, "svt_tplay": svt_tplay,
    "rtp_wedge": rtp_wedge, "rtlplay_prism": rtlplay_prism, "thunder_bolt": thunder_bolt,
    "tim_bars": tim_bars, "tvp_box": tvp_box, "yacine_set": yacine_set,
    "tvgarden_sprout": tvgarden_sprout, "zaap_set": zaap_set, "yettel_play": yettel_play,
    "streamlocator_pin": streamlocator_pin, "vieon_on": vieon_on, "hoichoi_bang": hoichoi_bang,
    "hyperspin_swirl": hyperspin_swirl, "topradio_cloud": topradio_cloud, "tv4_four": tv4_four,
    "sunnxt_x": sunnxt_x, "tvnz_plus": tvnz_plus, "tamasha_chevrons": tamasha_chevrons,
    "swac_star": swac_star, "esde_es": esde_es, "telequebec_cards": telequebec_cards,
    "go3_three": go3_three, "football360_ring": football360_ring, "freeflix_play": freeflix_play,
    "my5_five": my5_five, "movistar_m": movistar_m, "freetv_ring": freetv_ring,
    "delta_d": delta_d, "digi_dot": digi_dot, "mytv_wings": mytv_wings,
    "tv2_disc": tv2_disc, "zdf_two": zdf_two, "tflix_t": tflix_t,
})


# --------------------------------------------------------------------------
# Wordmark-derived marks (1.9.7). For apps whose launcher icon is a logotype
# with no symbol in it, the mark is the logotype's cue: its short form and its
# case, drawn in the pack's own single-stroke monoline letters (below), plus
# the one device the logo hangs on the name - a plus, a dot, a play wedge, an
# underline, an overline, a ring or a frame. The vendor's letterforms are
# never reproduced; every letter here is the same Core stroke skeleton.
#
# Letters live on a unit grid: cap line 0, x-height 0.3, baseline 1,
# descender 1.34. "D x y" in a path is a dot (the i and j tittles).
# --------------------------------------------------------------------------
_STROKE_LETTERS = {
    "A": (.72, "M 0 1 L .36 0 L .72 1 M .13 .66 L .59 .66"),
    "B": (.58, "M 0 .5 L .3 .5 C .56 .5 .58 1 .3 1 L 0 1 L 0 0 L .28 0 C .52 0 .52 .5 .28 .5"),
    "C": (.68, "M .68 .18 C .56 .04 .46 0 .38 0 C .14 0 0 .22 0 .5 C 0 .78 .14 1 .38 1 C .46 1 .56 .96 .68 .82"),
    "D": (.66, "M 0 0 L 0 1 L .26 1 C .52 1 .66 .78 .66 .5 C .66 .22 .52 0 .26 0 Z"),
    "E": (.52, "M .52 0 L 0 0 L 0 1 L .52 1 M 0 .5 L .42 .5"),
    "F": (.5, "M .5 0 L 0 0 L 0 1 M 0 .5 L .4 .5"),
    "G": (.72, "M .68 .18 C .56 .04 .46 0 .38 0 C .14 0 0 .22 0 .5 C 0 .78 .14 1 .38 1 C .58 1 .72 .84 .72 .54 L .44 .54"),
    "H": (.64, "M 0 0 L 0 1 M .64 0 L .64 1 M 0 .5 L .64 .5"),
    "I": (0, "M 0 0 L 0 1"),
    "J": (.46, "M .46 0 L .46 .7 C .46 .92 .34 1 .22 1 C .1 1 0 .92 0 .8"),
    "K": (.6, "M 0 0 L 0 1 M .6 0 L 0 .62 M .2 .42 L .6 1"),
    "L": (.5, "M 0 0 L 0 1 L .5 1"),
    "M": (.8, "M 0 1 L 0 0 L .4 .62 L .8 0 L .8 1"),
    "N": (.66, "M 0 1 L 0 0 L .66 1 L .66 0"),
    "O": (.8, "M .4 0 C .62 0 .8 .22 .8 .5 C .8 .78 .62 1 .4 1 C .18 1 0 .78 0 .5 C 0 .22 .18 0 .4 0 Z"),
    "P": (.56, "M 0 1 L 0 0 L .28 0 C .56 0 .56 .54 .28 .54 L 0 .54"),
    "Q": (.84, "M .4 0 C .62 0 .8 .22 .8 .5 C .8 .78 .62 1 .4 1 C .18 1 0 .78 0 .5 C 0 .22 .18 0 .4 0 Z M .54 .72 L .84 1.02"),
    "R": (.6, "M 0 1 L 0 0 L .28 0 C .56 0 .56 .54 .28 .54 L 0 .54 M .28 .54 L .6 1"),
    "S": (.56, "M .54 .14 C .46 .04 .36 0 .28 0 C .12 0 .02 .1 .02 .25 C .02 .56 .56 .42 .56 .74 C .56 .9 .44 1 .28 1 C .18 1 .06 .96 0 .86"),
    "T": (.64, "M 0 0 L .64 0 M .32 0 L .32 1"),
    "U": (.64, "M 0 0 L 0 .66 C 0 .88 .14 1 .32 1 C .5 1 .64 .88 .64 .66 L .64 0"),
    "V": (.7, "M 0 0 L .35 1 L .7 0"),
    "W": (.96, "M 0 0 L .22 1 L .48 .3 L .74 1 L .96 0"),
    "X": (.66, "M 0 0 L .66 1 M .66 0 L 0 1"),
    "Y": (.68, "M 0 0 L .34 .52 L .68 0 M .34 .52 L .34 1"),
    "Z": (.6, "M 0 0 L .6 0 L 0 1 L .6 1"),
    "0": (.6, "M .3 0 C .48 0 .6 .22 .6 .5 C .6 .78 .48 1 .3 1 C .12 1 0 .78 0 .5 C 0 .22 .12 0 .3 0 Z"),
    "1": (.28, "M 0 .2 L .28 0 L .28 1"),
    "2": (.58, "M .02 .22 C .06 .08 .18 0 .3 0 C .46 0 .56 .12 .56 .28 C .56 .5 .2 .7 0 1 L .58 1"),
    "3": (.58, "M .04 .1 C .12 .03 .22 0 .3 0 C .46 0 .54 .12 .54 .25 C .54 .4 .42 .48 .26 .48 C .46 .48 .58 .6 .58 .74 C .58 .9 .46 1 .3 1 C .18 1 .08 .95 0 .86"),
    "4": (.62, "M .44 1 L .44 0 L 0 .7 L .62 .7"),
    "5": (.58, "M .52 0 L .1 0 L .06 .44 C .14 .4 .22 .38 .3 .38 C .46 .38 .58 .5 .58 .68 C .58 .88 .44 1 .28 1 C .16 1 .06 .96 0 .88"),
    "6": (.58, "M .5 .06 C .44 .02 .38 0 .32 0 C .12 0 0 .24 0 .56 C 0 .84 .12 1 .3 1 C .48 1 .58 .86 .58 .7 C .58 .52 .46 .42 .3 .42 C .16 .42 .04 .5 0 .62"),
    "7": (.56, "M 0 0 L .56 0 L .2 1"),
    "9": (.58, "M .08 .94 C .14 .98 .2 1 .26 1 C .46 1 .58 .76 .58 .44 C .58 .16 .46 0 .28 0 C .1 0 0 .14 0 .3 C 0 .48 .12 .58 .28 .58 C .42 .58 .54 .5 .58 .38"),
    "+": (.5, "M .25 .25 L .25 .75 M 0 .5 L .5 .5"),
    "a": (.5, "M .5 .3 L .5 1 M .5 .65 C .5 .45 .4 .3 .25 .3 C .1 .3 0 .45 0 .65 C 0 .85 .1 1 .25 1 C .4 1 .5 .85 .5 .65"),
    "b": (.5, "M 0 0 L 0 1 M 0 .65 C 0 .45 .1 .3 .25 .3 C .4 .3 .5 .45 .5 .65 C .5 .85 .4 1 .25 1 C .1 1 0 .85 0 .65"),
    "c": (.46, "M .46 .4 C .4 .33 .32 .3 .25 .3 C .1 .3 0 .45 0 .65 C 0 .85 .1 1 .25 1 C .32 1 .4 .97 .46 .9"),
    "d": (.5, "M .5 0 L .5 1 M .5 .65 C .5 .45 .4 .3 .25 .3 C .1 .3 0 .45 0 .65 C 0 .85 .1 1 .25 1 C .4 1 .5 .85 .5 .65"),
    "e": (.5, "M 0 .65 L .5 .65 C .5 .45 .4 .3 .25 .3 C .1 .3 0 .45 0 .65 C 0 .85 .1 1 .25 1 C .34 1 .42 .97 .48 .9"),
    "f": (.34, "M .34 .03 C .3 .01 .26 0 .22 0 C .12 0 .08 .06 .08 .18 L .08 1 M 0 .34 L .32 .34"),
    "g": (.5, "M .5 .3 L .5 1.1 C .5 1.26 .4 1.34 .26 1.34 C .16 1.34 .08 1.3 .02 1.24 M .5 .62 C .5 .44 .4 .3 .25 .3 C .1 .3 0 .44 0 .62 C 0 .8 .1 .94 .25 .94 C .4 .94 .5 .8 .5 .62"),
    "h": (.48, "M 0 0 L 0 1 M 0 .56 C 0 .4 .1 .3 .24 .3 C .38 .3 .48 .4 .48 .56 L .48 1"),
    "i": (0, "M 0 .36 L 0 1 D 0 .1"),
    "j": (.16, "M .16 .36 L .16 1.14 C .16 1.28 .08 1.34 0 1.34 D .16 .1"),
    "k": (.44, "M 0 0 L 0 1 M .44 .32 L 0 .72 M .14 .6 L .44 1"),
    "l": (0, "M 0 0 L 0 1"),
    "m": (.78, "M 0 1 L 0 .3 M 0 .52 C 0 .38 .08 .3 .2 .3 C .32 .3 .39 .38 .39 .52 L .39 1 M .39 .52 C .39 .38 .47 .3 .59 .3 C .71 .3 .78 .38 .78 .52 L .78 1"),
    "n": (.48, "M 0 1 L 0 .3 M 0 .56 C 0 .4 .1 .3 .24 .3 C .38 .3 .48 .4 .48 .56 L .48 1"),
    "o": (.52, "M .26 .3 C .42 .3 .52 .45 .52 .65 C .52 .85 .42 1 .26 1 C .1 1 0 .85 0 .65 C 0 .45 .1 .3 .26 .3 Z"),
    "p": (.5, "M 0 .3 L 0 1.34 M 0 .65 C 0 .45 .1 .3 .25 .3 C .4 .3 .5 .45 .5 .65 C .5 .85 .4 1 .25 1 C .1 1 0 .85 0 .65"),
    "q": (.5, "M .5 .3 L .5 1.34 M .5 .65 C .5 .45 .4 .3 .25 .3 C .1 .3 0 .45 0 .65 C 0 .85 .1 1 .25 1 C .4 1 .5 .85 .5 .65"),
    "r": (.32, "M 0 1 L 0 .3 M 0 .56 C 0 .4 .12 .3 .32 .3"),
    "s": (.42, "M .4 .38 C .34 .32 .28 .3 .2 .3 C .1 .3 .02 .36 .02 .46 C .02 .66 .42 .6 .42 .82 C .42 .94 .32 1 .2 1 C .12 1 .04 .96 0 .9"),
    "t": (.34, "M .1 .06 L .1 .86 C .1 .96 .16 1 .24 1 C .28 1 .32 .99 .34 .98 M 0 .34 L .32 .34"),
    "u": (.48, "M 0 .3 L 0 .74 C 0 .9 .1 1 .24 1 C .38 1 .48 .9 .48 .74 M .48 .3 L .48 1"),
    "v": (.5, "M 0 .3 L .25 1 L .5 .3"),
    "w": (.74, "M 0 .3 L .18 1 L .37 .46 L .56 1 L .74 .3"),
    "x": (.48, "M 0 .3 L .48 1 M .48 .3 L 0 1"),
    "y": (.5, "M 0 .3 L .25 1 M .5 .3 L .2 1.2 C .16 1.3 .1 1.34 .02 1.34"),
    "z": (.44, "M 0 .3 L .44 .3 L 0 1 L .44 1"),
}
_STROKE_TOKEN = re.compile(r"[MLCZD]|-?\d*\.?\d+")


_STROKE_GAP = 46      # px between letters, whatever the scale: strokes never touch


def _stroke_line(text, x0, y0, s):
    """One line of stroke letters at scale `s`, top-left of the cap box at x0,y0.
    Returns (path d, dots [(x, y)])."""
    d, dots, x = [], [], x0
    for ch in text:
        w, spec = _STROKE_LETTERS[ch]
        toks = _STROKE_TOKEN.findall(spec)
        k = 0
        while k < len(toks):
            t = toks[k]
            if t == "Z":
                d.append("Z"); k += 1; continue
            n = {"M": 1, "L": 1, "C": 3, "D": 1}[t]
            pts = [(float(toks[k + 1 + 2 * j]), float(toks[k + 2 + 2 * j])) for j in range(n)]
            k += 1 + 2 * n
            xy = [(x + px * s, y0 + py * s) for px, py in pts]
            if t == "D":
                dots.append(xy[0])
            else:
                d.append(t + " " + " ".join(f"{a:.1f} {b:.1f}" for a, b in xy))
        x += w * s + _STROKE_GAP
    return " ".join(d), dots


def _stroke_px(text, s):
    # math.fsum, not sum: CPython 3.12 made sum() compensated, so the two
    # disagree in the last bit and a .x5 coordinate rounds differently in CI.
    return math.fsum(_STROKE_LETTERS[ch][0] for ch in text) * s + _STROKE_GAP * (len(text) - 1)


def _stroke_text(text, c, box=(96, 136, 416, 376), weight=30):
    """Stroke-letter lockup centred in `box`; '/' splits it onto two lines."""
    lines = text.split("/")
    bx0, by0, bx1, by1 = box
    bw, bh = bx1 - bx0, by1 - by0
    desc = any(ch in "gjpqy" for ch in lines[-1])
    rows = len(lines)
    lead = 44
    units_h = rows + (.34 if desc else 0)
    s = (bh - lead * (rows - 1)) / units_h
    for line in lines:
        units_w = math.fsum(_STROKE_LETTERS[ch][0] for ch in line) or .01
        s = min(s, (bw - _STROKE_GAP * (len(line) - 1)) / units_w)
    ink_h = units_h * s + lead * (rows - 1)
    out, dots = [], []
    y = by0 + (bh - ink_h) / 2
    for line in lines:
        lw = _stroke_px(line, s)
        d, dd = _stroke_line(line, bx0 + (bw - lw) / 2, y, s)
        out.append(d); dots += dd
        y += s + lead
    body = f'<path d="{" ".join(out)}" {_s(c, weight)}/>'
    r = weight * .62
    return body + "".join(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}" {_f(c)}/>' for x, y in dots)


def _wm_cue(cue, c):
    if cue == "plus":
        return f'<path d="M 404 64 L 404 136 M 368 100 L 440 100" {_s(c, 28)}/>'
    if cue == "dot":
        return f'<circle cx="412" cy="400" r="24" {_f(c)}/>'
    if cue == "play":
        return _solid("M 386 68 L 440 100 L 386 132 Z", c, 12)
    if cue == "under":
        return f'<path d="M 120 432 L 392 432" {_s(c, 28)}/>'
    if cue == "over":
        return f'<path d="M 120 80 L 392 80" {_s(c, 28)}/>'
    if cue == "ring":
        return f'<circle cx="256" cy="256" r="196" {_s(c, 26)}/>'
    if cue == "frame":
        return f'<rect x="60" y="92" width="392" height="328" rx="52" {_s(c, 26)}/>'
    return ""


_WM_BOX = {
    None: (88, 120, 424, 392), "plus": (88, 144, 400, 408), "play": (88, 144, 400, 408),
    "dot": (88, 112, 392, 380), "under": (96, 112, 416, 384), "over": (96, 128, 416, 400),
    "ring": (152, 176, 360, 336), "frame": (120, 168, 392, 344),
}


def _wm_glyph(text, cue=None):
    def draw(c):
        return _stroke_text(text, c, _WM_BOX[cue]) + _wm_cue(cue, c)
    draw.__doc__ = f"Wordmark cue: '{text}'" + (f" with its {cue}" if cue else "") + "."
    return draw


# drawable -> (logotype cue, device). Each read off the app's launcher icon in
# the Projectivy Icon Pack 1.1.9 artwork (reference only): the short form and
# case the logo itself uses, and the one device it carries, if any.
_WM = {
    "airreceiverlite": ("Air", "play"), "aloulatv": ("AL", "under"),
    "amazing_classics": ("AC", None), "ant1": ("ANT1", "plus"), "arte": ("a", None),
    "bet_plus": ("BET", "plus"), "blip": ("blip", None), "byutv": ("byu/tv", None),
    "caixaforum_plus": ("CF", "plus"), "cda_pl": ("cda", None), "chaupal": ("CH", "under"),
    "clone_hero": ("C/H", None), "cosmote_tv": ("CTV", None), "dudeperfect": ("DP", "under"),
    "euronews": ("EN", "under"), "flix_tv": ("FLIK", "dot"), "flixnest": ("FN", "dot"),
    "fluffy": ("FL", "ring"), "forja_tv": ("For/ja", None), "formed": ("fo", "under"),
    "francetv": ("ftv", "dot"), "gain": ("GAiN", None), "gb_news": ("GBN", None),
    "goplay": ("GO", "play"), "rezka": ("HD", "ring"), "hdtv_player": ("HD/TV", None),
    "heatlive": ("HEAT", "play"), "hidive": ("HI/DIVE", None), "hrti": ("HRTi", None), "ici_tou_tv": ("tou/tv", "dot"),
    "iptv_pro": ("IPTV", None), "ivysiilani": ("iV", None), "jawwy_tv": ("stc/tv", None),
    "jiotvplus": ("jio", "ring"), "joyn": ("joyn", None), "kemo_iptv": ("KEMO", None),
    "kika": ("KiKA", None), "knowledge": ("KN", "under"), "l_equipe": ("LE", "over"),
    "localnow": ("LN", "under"), "loco": ("LOCO", None), "lrt": ("LRT", None),
    "m6_plus": ("M6", "plus"), "magellantv": ("MAG", "play"), "magio_tv": ("MG/TV", None),
    "mediaset_infinity_tv": ("inf", None), "mewatch": ("me", "play"), "namava": ("NMV", "under"),
    "netfly_tv": ("NET/FLY", None), "netmirrortv": ("NM", None), "nettv": ("NET/TV", None),
    "nhk_plus": ("NHK", "plus"), "nhk_world_japan": ("NHK", "under"), "njpw_world": ("NJ/PW", None),
    "nlziet": ("NLZ", "play"), "noovo": ("noo/vo", None), "nos": ("NOS", None),
    "nowo_tv": ("nowo", None), "nrk_tv": ("NRK", None), "nxsha": ("NXS", None),
    "one_play": ("one", "play"), "oqee_by_free": ("oq", None), "panda_plus": ("PA", "plus"),
    "perfecttv": ("PTV", None), "playkids": ("PK", "plus"), "put_io": ("put/io", None),
    "quasitv": ("QTV", None), "raiplay": ("Rai", "frame"), "redream": ("RD", None),
    "riks_tv": ("RIKS", None), "rtl": ("RTL", "plus"), "rtve_play": ("rt", "play"),
    "rugbypass_tv": ("RP", "ring"), "rutube": ("RU", "dot"), "sfjazz_at_home": ("SFJ", None),
    "sfr_tv": ("SFR", None), "shout_tv": ("ST", "under"), "sledovani": ("SL", "frame"),
    "sport_tv": ("sp", "dot"), "strim": ("strim", None), "stv_player": ("STV", "play"),
    "tbn_plus": ("TBN", "plus"), "tcl_channel": ("TCL", "under"), "tcn": ("TCN", None),
    "telewebion": ("TW", None), "telly": ("Telly", None), "texttv": ("TXT", "under"),
    "tf1": ("TF1", "plus"), "tivify": ("tiv/ify", None), "tod": ("TOD", None),
    "toggo": ("TOG/GO", None), "trilogy_plus": ("TRI", "plus"), "trublu": ("TRU/BLU", None),
    "tt": ("TT", "plus"), "tv_2_play": ("2", "play"), "tv_vlaanderen": ("TVV", None),
    "tv360": ("TV/360", None), "tvo_kids": ("tvo/kids", None), "tvo_today": ("tvo", "under"),
    "tvpsport": ("TVP", "over"), "vbtv": ("VB/TV", None), "victory_plus": ("VIC", "plus"),
    "viki": ("viki", None), "viva_one_tv": ("vi/va", None), "vix": ("ViX", None),
    "vmx": ("VMX", None), "voyo_sk": ("VOYO", None), "vtvcab_on_tv": ("VTV/cab", None),
    "vtvgo_tv": ("VTV/GO", None), "watcher_tv": ("WT", "over"), "watcho": ("wat/cho", None),
    "wow2": ("WOW", None), "x_tv": ("XTV", None), "yle_areena": ("yle", "under"),
    "youcine": ("YOU", "under"), "ziggo_go_tv": ("ZIG/GO", None),
}
GLYPHS.update({f"{d}_wm": _wm_glyph(t, cue) for d, (t, cue) in _WM.items()})


def mytuner_radio(c):
    """myTuner Radio: the old set with its grille, dial and carry handle."""
    return (f'<rect x="72" y="176" width="368" height="248" rx="40" {_s(c, 28)}/>'
            f'<path d="M 176 176 L 336 96" {_s(c, 22)}/>'
            f'<circle cx="184" cy="300" r="68" {_s(c, 24)}/>'
            f'<circle cx="184" cy="300" r="18" {_f(c)}/>'
            f'<path d="M 304 256 L 376 256 M 304 300 L 376 300 M 304 344 L 376 344" {_s(c, 22)}/>')


def radon_note(c):
    """Radon Tunes: the beamed quaver, drawn on the pack's line."""
    return (f'<circle cx="184" cy="364" r="56" {_s(c, 28)}/>'
            f'<path d="M 240 364 L 240 104 C 290 124 340 140 360 196" {_s(c, 30)}/>')


def channels_set(c):
    """Channels: the set on its antenna with test-card bars and a play across them."""
    return (f'<rect x="72" y="144" width="368" height="272" rx="36" {_s(c, 28)}/>'
            f'<path d="M 208 84 L 256 144 L 304 84" {_s(c, 22)}/>'
            f'<path d="M 136 204 L 136 356 M 184 204 L 184 356 M 328 204 L 328 356 M 376 204 L 376 356" {_s(c, 18)}/>'
            + _solid("M 228 228 L 296 280 L 228 332 Z", c, 12))


def filmplus_cloud(c):
    """FilmPlus: the sun peeking over a cloud on its horizon line."""
    return (f'<path d="{_arc_cw(196, 212, 72, 150, 330)}" {_s(c, 24)}/>'
            f'<path d="M 96 204 L 70 190 M 196 108 L 196 80 M 130 136 L 112 116 M 262 136 L 280 116" {_s(c, 20)}/>'
            f'<path d="M 152 356 C 108 356 96 312 128 292 C 128 244 188 228 216 264 '
            f'C 240 216 324 220 332 284 C 380 284 400 356 344 356 Z" {_s(c, 26)}/>'
            f'<path d="M 120 420 L 392 420" {_s(c, 22)}/>')


def dimplay_layers(c):
    """Dimplay: three play arrows stacked into a solid wedge, back to front."""
    return (f'<path d="M 104 120 L 104 392 L 352 256 Z" {_s(c, 26)}/>'
            f'<path d="M 172 164 L 172 348 L 340 256" {_s(c, 22)}/>'
            + _solid("M 236 204 L 332 256 L 236 308 Z", c, 12))


def usercenter_person(c):
    """User Center: head and shoulders inside the account ring."""
    return (f'<circle cx="256" cy="256" r="184" {_s(c, 28)}/>'
            f'<circle cx="256" cy="200" r="64" {_s(c, 26)}/>'
            f'<path d="M 140 396 C 156 320 204 292 256 292 C 308 292 356 320 372 396" {_s(c, 26)}/>')


def gridstreamr_grid(c):
    """GridStreamr: a three-by-three grid of tiles whose last cell is a play arrow."""
    cells = "".join(f'<rect x="{96 + 116 * col}" y="{96 + 116 * row}" width="72" height="72" rx="16" {_s(c, 22)}/>'
                    for row in range(3) for col in range(3) if (row, col) not in ((1, 2), (2, 2)))
    return cells + _solid("M 328 236 L 424 300 L 328 364 Z", c, 14)


def launchonboot_cycle(c):
    """Launch on Boot: two arrows chasing round a power switch."""
    return (f'<path d="{_arc_cw(256, 256, 176, 200, 340)}" {_s(c, 28)}/>'
            f'<path d="{_arc_cw(256, 256, 176, 20, 160)}" {_s(c, 28)}/>'
            + _solid("M 432 196 L 440 268 L 380 236 Z", c, 10)
            + _solid("M 80 316 L 72 244 L 132 276 Z", c, 10)
            + f'<path d="M 256 176 L 256 256 M 208 208 C 176 240 188 320 256 324 C 324 320 336 240 304 208" {_s(c, 24)}/>')


GLYPHS.update({
    "mytuner_radio": mytuner_radio, "radon_note": radon_note, "channels_set": channels_set,
    "filmplus_cloud": filmplus_cloud, "dimplay_layers": dimplay_layers,
    "usercenter_person": usercenter_person, "gridstreamr_grid": gridstreamr_grid,
    "launchonboot_cycle": launchonboot_cycle,
})


# The second wordmark pass: apps with no Projectivy artwork, read instead from
# their store-listing launcher icons (Play, Aptoide, Uptodown, APKCombo;
# reference only). Same cue rules as _WM.
_WM2 = {
    "threeplayer": ("VM", "play"), "acontraplus": ("ac", "plus"), "aimitv": ("10", "plus"),
    "livingroom": ("fv", None), "angel": ("AN", "under"), "beachbody": ("BO/Di", None),
    "canaldigital": ("all", None), "citytvplus": ("city", "plus"), "ctvgo": ("CTV", "ring"),
    "cuenew2": ("CUE", None), "digdroid": ("DIG", None), "dcsapp": ("Nex", None),
    "distroscale": ("DTV", "frame"), "dnschanger": ("DNS", None), "dstvmobile": ("DStv", None),
    "earthcamtv": ("EC", "ring"), "epicchannel": ("EPIC", "under"), "eros": ("EN", "ring"),
    "fcportotv": ("FCP", "dot"), "fifa": ("FIFA", "plus"), "findlink": ("FLX", None),
    "fizz_app": ("fizz", None), "foxnation": ("FOX", "under"), "foxone": ("FOX/ONE", None),
    "fullepisodes": ("CW", None), "great": ("GREAT", None), "heinetworktv": ("HEI", None),
    "watcher": ("HG/TV", None), "ignitetv": ("R", "play"), "myiptvonline": ("iM", None),
    "androidtv_7": ("OE", "ring"), "kfandroid": ("K", None), "streamingkemo": ("KS", "under"),
    "laughafterdark": ("LA", "under"), "launchsounds": ("BBC", "under"), "lazyiptvdeluxe": ("LAZY", None),
    "ligaportugal": ("LIGA", None), "ligueunpass": ("1", "plus"), "combo3403": ("RISE", None),
    "combo3578": ("RS/BN", None), "tvod169": ("U", "play"), "alticelabs": ("MEO", None),
    "mercado_play": ("MP", "play"), "moviehd": ("HD", "frame"), "msmvideo": ("MSM", "play"),
    "netfly": ("NF", "dot"), "nordiskfilmplus": ("NF", "plus"), "nowtv": ("NOW", None),
    "ntathome": ("NT", "under"), "appomroepbrabant": ("B", "plus"), "andevapps": ("TV", "plus"),
    "impresa": ("opto", None), "orftvthek": ("ORF/ON", None), "osn": ("osn", "plus"),
    "telegram": ("T", "dot"), "premierefc": ("P", "frame"), "radioplayer": ("R", "ring"),
    "rblive": ("RB", None), "americasvoicenews": ("AV", "under"), "rlaxxtv": ("WTV", "plus"),
    "nextinteractive": ("RMC", "plus"), "rsi": ("RSI", "play"), "minimal": ("RTE", "play"),
    "skymais": ("sky", "plus"), "radiomg": ("fm", "ring"), "sportscaster": ("CBS", "under"),
    "srfplayer": ("SRF", "play"), "tfctv": ("iw", None), "mediaworks": ("NOW", "plus"),
    "discovery_2": ("TLC", None), "fight": ("TN", "plus"), "tveverywhere": ("K1", None),
    "deadlyduck": ("TV/OS", None), "tvaplus": ("tva", "plus"), "twodf_tivi": ("tivi", None),
    "vrtnu": ("vrt/max", None), "zenderapp": ("VTM", None), "frograms": ("W", None),
    "linkplay": ("WiiM", None), "selfcare": ("Y", "play"), "nextplayer": ("NP", "play"),
    "tcl_home_passive": ("TCL", "frame"),
}
GLYPHS.update({f"{d}_wm": _wm_glyph(t, cue) for d, (t, cue) in _WM2.items()})


def applinked_ribbon(c):
    """AppLinked: the M folded from three parallel ribbons."""
    return (f'<path d="M 96 416 L 96 112 L 256 280 L 416 112 L 416 416" {_s(c, 26)}/>'
            f'<path d="M 160 416 L 160 232 L 256 336 L 352 232 L 352 416" {_s(c, 22)}/>')


def photogrid_tiles(c):
    """Photo Screensaver: the four-by-four wall of photo tiles, one of them missing."""
    return "".join(f'<rect x="{88 + 88 * col}" y="{88 + 88 * row}" width="64" height="64" rx="12" {_s(c, 20)}/>'
                   for row in range(4) for col in range(4) if (row, col) != (1, 2))


GLYPHS.update({"applinked_ribbon": applinked_ribbon, "photogrid_tiles": photogrid_tiles})
_WM2.update({"cgtnamericanow": ("CG/TN", None), "lazycatsoftware": ("LM", "frame"),
             "videoplayer_2": ("NOVA", None)})
GLYPHS.update({f"{d}_wm": _wm_glyph(*_WM2[d]) for d in ("cgtnamericanow", "lazycatsoftware", "videoplayer_2")})


def rubika_cube(c):
    """Rubika TV: the hexagonal cube seen corner-on, its three faces split by a Y."""
    import math
    pts = [(256 + 176 * math.cos(math.radians(a)), 256 + 176 * math.sin(math.radians(a)))
           for a in range(-90, 270, 60)]
    d = "M " + " L ".join(f"{x:.1f} {y:.1f}" for x, y in pts) + " Z"
    return (f'<path d="{d}" {_s(c, 28)}/>'
            f'<path d="M 256 256 L 256 432 M 256 256 L 104 168 M 256 256 L 408 168" {_s(c, 24)}/>')


GLYPHS["rubika_cube"] = rubika_cube

# Letter tiles whose Projectivy Icon Pack 1.1.9 artwork was missed by the
# first wordmark scan (its component index was incomplete); same cue rules.
_WM3 = {
    "airattack2tv": ("air/2", None), "allsaversocial": ("Viva", None), "anime": ("ani", None),
    "cybermedia": ("CF", "ring"), "damontecres": ("SH", "over"), "eternaltviptvbox": ("ET", "frame"),
    "nathnetwork": ("ET", "ring"), "fanetv": ("FANE", None), "fctv77": ("FC", "under"),
    "enhanced": ("DUNE", None), "mgstv": ("MG", "under"), "myiptv": ("MY", None),
    "oktv22": ("OK", None), "onepixmedia": ("PIX", "dot"), "apksrebrand": ("PL", "play"),
    "sportseverywhere": ("4V", "under"), "xtreamplayeranddownloader": ("9X", None),
}
GLYPHS.update({f"{d}_wm": _wm_glyph(t, cue) for d, (t, cue) in _WM3.items()})
