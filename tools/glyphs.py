import re

from typeface import monogram_body, monogram_text, monogram_scaled
"""
Core Builds Icon Pack — glyph library.

Every glyph is original geometry written in the Core Builds icon language
(Brand Guide v1.0 §07): simple geometry, rounded ends, flat fills, one accent
colour per meaning. Nothing here traces a third-party logo; marks are
suggestive silhouettes drawn on our own 512 grid.

Grid:   512 x 512
Safe:   432 (40px margin all sides)
Stroke: 34 default (heavy enough to survive 10-foot UI downscaling)
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
    """Kodi's boxed 'K': upright stem plus the open wedge, inside the frame."""
    return (f'<rect x="64" y="64" width="384" height="384" rx="72" {_s(c, 30)}/>'
            f'<path d="M 168 138 L 168 374" {_s(c, 38)}/>'
            f'<path d="M 336 138 L 232 256 L 336 374" {_s(c, 38)}/>')


def jellyfin_chevrons(c):
    return (f'<path d="M 256 120 L 396 350 L 116 350 Z" {_s(c, 34)}/>'
            f'<path d="M 256 226 L 316 320 L 196 320 Z" {_s(c, 30)}/>')


def emby_shield(c):
    return (f'<path d="M 256 92 L 404 152 L 404 268 C 404 350 336 400 256 424 '
            f'C 176 400 108 350 108 268 L 108 152 Z" {_s(c, 34)}/>'
            f'<path d="M 226 200 L 316 258 L 226 316 Z" {_s(c, 30)}/>')


def plex_chevron(c):
    """Official silhouette: one chevron inside a rounded square."""
    return (f'<rect x="76" y="76" width="360" height="360" rx="76" {_s(c, 34)}/>'
            f'<path d="M 214 152 L 318 256 L 214 360" {_s(c, 42)}/>')


def nuvio_wave(c):
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 34)}/>'
            f'<path d="M 156 276 C 196 196 236 196 256 256 '
            f'C 276 316 316 316 356 236" {_s(c, 34)}/>')


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
    return (f'<path d="M 88 116 L 424 116 L 424 320 L 328 320 L 256 392 '
            f'L 256 320 L 88 320 Z" {_s(c, 34)}/>'
            f'<path d="M 208 186 L 208 258" {_s(c, 32)}/>'
            f'<path d="M 304 186 L 304 258" {_s(c, 32)}/>')


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


def tools_wrench(c):
    """Utilities / tweaks — wrench."""
    return (f'<path d="M 352 96 C 300 82 250 118 250 174 C 250 190 254 204 '
            f'260 216 L 116 360 C 98 378 98 404 116 422 C 134 440 160 440 '
            f'178 422 L 322 278 C 334 284 348 288 364 288 C 420 288 456 238 '
            f'442 186 L 392 236 L 342 226 L 332 176 Z" {_s(c, 30)}/>')


def send_arrow(c):
    """File transfer — paper-plane."""
    return (f'<path d="M 428 96 L 84 246 L 214 292 L 260 422 Z" {_s(c, 32)}/>'
            f'<path d="M 428 96 L 214 292" {_s(c, 28)}/>')


def broom(c):
    """Cleaner / maintenance."""
    return (f'<path d="M 384 96 L 236 244" {_s(c, 34)}/>'
            f'<path d="M 268 208 L 176 300 L 246 370 L 338 278 Z" {_s(c, 30)}/>'
            f'<path d="M 176 300 L 96 416 L 246 370" {_s(c, 30)}/>')


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
    "tools_wrench": tools_wrench, "send_arrow": send_arrow,
    "broom": broom, "shield_key": shield_key, "automation": automation,
    "home_button": home_button, "tv_stack": tv_stack,
    "play_hex": play_hex, "play_round": play_round, "play_rect": play_rect,
    "kodi_box": kodi_box, "jellyfin_chevrons": jellyfin_chevrons,
    "emby_shield": emby_shield, "plex_chevron": plex_chevron,
    "nuvio_wave": nuvio_wave, "projector_beam": projector_beam,
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


def render_svg(glyph_name, color, glow=False):
    """
    Render a glyph, lit.

    The halo is applied here rather than inside each of the 40+ glyph
    functions: one treatment, one place to tune, and no glyph can forget it.
    """
    body = monoline(GLYPHS[glyph_name](color))
    if glow:
        body = lit(body, color)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {GRID} {GRID}" '
            f'width="{GRID}" height="{GRID}">\n  {body}\n</svg>\n')


# ==========================================================================
# Distinct, recognisable marks.
#
# Written because 81 icons were sharing 44 glyphs — six apps rendered the same
# play-in-a-rectangle, which is what made the set feel like a generic rebrand.
# Each mark below is drawn to the silhouette users actually recognise, in our
# geometry: rounded ends, flat fill, one accent.
# ==========================================================================

def yt_play(c):
    """YouTube: the solid rounded-rect badge with a filled white play.

    The play is cut as a filled notch (background colour) so the badge reads as
    the classic solid red YouTube button rather than an outline.
    """
    return (f'<rect x="40" y="124" width="432" height="264" rx="74" {_f(c)}/>'
            f'<path d="M 214 196 L 214 316 L 336 256 Z" fill="#0d1117" '
            f'stroke="none"/>')


def smarttube_play(c):
    """SmartTube: YouTube silhouette with a corner cut — the fork tell."""
    return (f'<path d="M 110 118 L 402 118 C 444 118 478 152 478 194 '
            f'L 478 318 C 478 360 444 394 402 394 L 110 394 '
            f'C 68 394 34 360 34 318 L 34 194 C 34 152 68 118 110 118 Z" '
            f'{_s(c, 34)}/>'
            f'<path d="M 218 200 L 218 312 L 326 256 Z" {_s(c, 30)}/>'
            f'<path d="M 388 150 L 458 150" {_s(c, 22)}/>'
            f'<path d="M 388 190 L 458 190" {_s(c, 22)}/>')


def tizen_play(c):
    """TizenTube: play inside a soft square, ad-blocked slash."""
    return (f'<rect x="70" y="112" width="372" height="288" rx="64" {_s(c, 34)}/>'
            f'<path d="M 216 196 L 216 316 L 326 256 Z" {_s(c, 30)}/>'
            f'<path d="M 118 372 L 396 132" {_s(c, 30)}/>')


def film_reel(c):
    """Cinema HD: film strip — perforated frame."""
    return (f'<rect x="74" y="126" width="364" height="260" rx="34" {_s(c, 32)}/>'
            f'<path d="M 74 190 L 438 190" {_s(c, 24)}/>'
            f'<path d="M 74 322 L 438 322" {_s(c, 24)}/>'
            f'<circle cx="132" cy="158" r="15" {_f(c)}/>'
            f'<circle cx="222" cy="158" r="15" {_f(c)}/>'
            f'<circle cx="312" cy="158" r="15" {_f(c)}/>'
            f'<circle cx="392" cy="158" r="15" {_f(c)}/>'
            f'<circle cx="132" cy="354" r="15" {_f(c)}/>'
            f'<circle cx="222" cy="354" r="15" {_f(c)}/>'
            f'<circle cx="312" cy="354" r="15" {_f(c)}/>'
            f'<circle cx="392" cy="354" r="15" {_f(c)}/>')


def flix_f(c):
    """Streamflix: bold F with a play notch."""
    return (f'<path d="M 168 396 L 168 128 L 356 128" {_s(c, 46)}/>'
            f'<path d="M 168 256 L 316 256" {_s(c, 42)}/>')


def yinyang_play(c):
    """WuPlay: yin-yang drawn as contour, with two small play marks."""
    return (f'<circle cx="256" cy="256" r="186" {_s(c, 32)}/>'
            f'<path d="M 256 70 A 93 93 0 0 1 256 256 A 93 93 0 0 0 256 442" '
            f'{_s(c, 32)}/>'
            f'<path d="M 226 140 L 226 196 L 274 168 Z" {_s(c, 22)}/>'
            f'<path d="M 286 316 L 286 372 L 238 344 Z" {_s(c, 22)}/>')


def stremio_square(c):
    """Stremio: rounded square with the play as outline (monoline)."""
    return (f'<rect x="66" y="66" width="380" height="380" rx="102" {_s(c, 34)}/>'
            f'<path d="M 212 178 L 212 334 L 340 256 Z" {_s(c, 32)}/>')


def arvio_a(c):
    """Arvio: an A built from a play wedge."""
    return (f'<path d="M 132 396 L 256 116 L 380 396" {_s(c, 42)}/>'
            f'<path d="M 190 300 L 322 300" {_s(c, 34)}/>')


def lumera_beam(c):
    """Lumera: a lit lamp/prism — the 'lumen' idea."""
    return (f'<path d="M 256 96 L 372 300 L 140 300 Z" {_s(c, 34)}/>'
            f'<path d="M 186 300 L 186 372 C 186 410 218 436 256 436 '
            f'C 294 436 326 410 326 372 L 326 300" {_s(c, 32)}/>'
            f'<path d="M 256 156 L 256 300" {_s(c, 24)}/>')


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
    """StreamVault: a vault door."""
    return (f'<rect x="76" y="76" width="360" height="360" rx="48" {_s(c, 32)}/>'
            f'<circle cx="256" cy="256" r="104" {_s(c, 30)}/>'
            f'<path d="M 256 152 L 256 118" {_s(c, 24)}/>'
            f'<path d="M 256 360 L 256 394" {_s(c, 24)}/>'
            f'<path d="M 152 256 L 118 256" {_s(c, 24)}/>'
            f'<path d="M 360 256 L 394 256" {_s(c, 24)}/>')


def equalizer(c):
    """Poweramp EQ / music: slider bars."""
    return (f'<path d="M 130 108 L 130 404" {_s(c, 32)}/>'
            f'<path d="M 256 108 L 256 404" {_s(c, 32)}/>'
            f'<path d="M 382 108 L 382 404" {_s(c, 32)}/>'
            f'<circle cx="130" cy="188" r="34" fill="#0d1117" stroke="{c}" '
            f'stroke-width="24"/>'
            f'<circle cx="256" cy="310" r="34" fill="#0d1117" stroke="{c}" '
            f'stroke-width="24"/>'
            f'<circle cx="382" cy="232" r="34" fill="#0d1117" stroke="{c}" '
            f'stroke-width="24"/>')


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
    """Launchers: a card grid."""
    return (f'<rect x="72" y="96" width="164" height="140" rx="24" {_s(c, 30)}/>'
            f'<rect x="276" y="96" width="164" height="140" rx="24" {_s(c, 30)}/>'
            f'<rect x="72" y="276" width="164" height="140" rx="24" {_s(c, 30)}/>'
            f'<rect x="276" y="276" width="164" height="140" rx="24" {_s(c, 30)}/>')


def rocket(c):
    """AT4K / performance launchers."""
    return (f'<path d="M 256 68 C 320 130 348 218 340 306 L 172 306 '
            f'C 164 218 192 130 256 68 Z" {_s(c, 32)}/>'
            f'<circle cx="256" cy="196" r="40" {_s(c, 26)}/>'
            f'<path d="M 172 268 L 108 330 L 156 342" {_s(c, 26)}/>'
            f'<path d="M 340 268 L 404 330 L 356 342" {_s(c, 26)}/>'
            f'<path d="M 216 348 L 256 444 L 296 348" {_s(c, 28)}/>')


def droplet(c):
    """Monet: a colour droplet — dynamic theming."""
    return (f'<path d="M 256 82 C 256 82 380 218 380 300 '
            f'C 380 372 324 428 256 428 C 188 428 132 372 132 300 '
            f'C 132 218 256 82 256 82 Z" {_s(c, 32)}/>'
            f'<path d="M 316 236 C 344 272 352 306 340 340" {_s(c, 26)}/>')


GLYPHS.update({
    "yt_play": yt_play, "smarttube_play": smarttube_play,
    "tizen_play": tizen_play, "film_reel": film_reel, "flix_f": flix_f,
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
    """
    TV Bro — a globe inside a rounded screen.

    Its own icon is neon 'TV BRO' lettering wrapping a remote and a wire
    globe. A wordmark cannot survive the downscale, so the globe carries it:
    it is the browser idea, and it is what stays legible small.
    """
    return (f'<rect x="52" y="96" width="408" height="284" rx="72" {_s(c, 32)}/>'
            f'<circle cx="256" cy="238" r="104" {_s(c, 28)}/>'
            f'<path d="M 152 238 L 360 238" {_s(c, 24)}/>'
            f'<path d="M 256 134 C 300 172 300 304 256 342" {_s(c, 24)}/>'
            f'<path d="M 256 134 C 212 172 212 304 256 342" {_s(c, 24)}/>'
            # stand
            f'<path d="M 176 434 L 336 434" {_s(c, 28)}/>'
            f'<path d="M 256 380 L 256 434" {_s(c, 24)}/>')


GLYPHS.update({"stadium": stadium, "browser_globe": browser_globe})


# ==========================================================================
# Bespoke marks for apps seen on the user's real device row.
#
# Each replaces a shared glyph: Janky was one of five apps on play_round,
# TiviMate one of twenty-one on monogram_T. A mark shared twenty-one ways
# is not a mark, it is a placeholder.
# ==========================================================================

def janky_play(c):
    """
    Janky Player — a play triangle with a deliberate stagger.

    The name is the idea: the wedge is split and offset, so it reads as a
    play mark that is slightly out of joint. Distinguishes it from the four
    other players that were all sharing play_round.
    """
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 32)}/>'
            f'<path d="M 210 168 L 330 232 L 210 254 Z" {_s(c, 26)}/>'
            f'<path d="M 222 272 L 342 294 L 222 358 Z" {_s(c, 26)}/>')


def tivimate_grid(c):
    """
    TiviMate — an EPG grid: the programme guide is the app.

    A screen split into channel rows with a highlighted 'now' cell. Says
    IPTV guide rather than generic player, and is nothing like the twenty
    other apps that were sharing monogram_T.
    """
    return (f'<rect x="52" y="104" width="408" height="268" rx="40" {_s(c, 32)}/>'
            # channel column divider
            f'<path d="M 158 104 L 158 372" {_s(c, 24)}/>'
            # programme rows
            f'<path d="M 52 192 L 460 192" {_s(c, 22)}/>'
            f'<path d="M 52 284 L 460 284" {_s(c, 22)}/>'
            # 'now' cell, filled to read as the highlight
            f'<rect x="196" y="212" width="128" height="52" rx="12" {_f(c)}/>'
            # stand
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


def localsend_nodes(c):
    """LocalSend — two devices passing a packet across the LAN."""
    return (
        f'<rect x="64" y="150" width="148" height="212" rx="36" {_s(c, 32)}/>'
        f'<rect x="300" y="150" width="148" height="212" rx="36" {_s(c, 32)}/>'
        f'<path d="M 228 220 L 284 220" {_s(c, 28)}/>'
        f'<path d="M 256 198 L 284 220 L 256 242" {_s(c, 28)}/>'
        f'<path d="M 284 292 L 228 292" {_s(c, 28)}/>'
        f'<path d="M 256 270 L 228 292 L 256 314" {_s(c, 28)}/>'
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
    """Synology / NAS — three stacked drive bays. The disk shelf is the product."""
    return (
        f'<rect x="86" y="92" width="340" height="92" rx="22" {_s(c, 30)}/>'
        f'<rect x="86" y="210" width="340" height="92" rx="22" {_s(c, 30)}/>'
        f'<rect x="86" y="328" width="340" height="92" rx="22" {_s(c, 30)}/>'
        f'<circle cx="138" cy="138" r="14" {_f(c)}/>'
        f'<circle cx="138" cy="256" r="14" {_f(c)}/>'
        f'<circle cx="138" cy="374" r="14" {_f(c)}/>'
        f'<path d="M 178 138 L 372 138" {_s(c, 22)}/>'
        f'<path d="M 178 256 L 372 256" {_s(c, 22)}/>'
        f'<path d="M 178 374 L 372 374" {_s(c, 22)}/>'
    )


def nas_play(c):
    """DS video — drive shelf with a play wedge."""
    return (
        f'<rect x="72" y="118" width="368" height="276" rx="36" {_s(c, 32)}/>'
        f'<path d="M 72 210 L 440 210" {_s(c, 24)}/>'
        f'<path d="M 72 302 L 440 302" {_s(c, 24)}/>'
        f'<path d="M 214 168 L 214 344 L 348 256 Z" {_s(c, 30)}/>'
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
    """DS finder — a dish sweeping for a NAS on the LAN."""
    return (
        f'<path d="M 96 392 C 96 250 210 136 352 136" {_s(c, 34)}/>'
        f'<circle cx="124" cy="368" r="28" {_s(c, 26)}/>'
        f'<path d="M 124 368 L 256 236" {_s(c, 26)}/>'
        f'<path d="M 300 96 C 372 96 448 172 448 244" {_s(c, 26)}/>'
        f'<path d="M 324 148 C 368 148 412 192 412 236" {_s(c, 26)}/>'
    )


GLYPHS.update({
    "localsend_nodes": localsend_nodes,
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
    """Netflix: the ribbon 'N' with a fold — a near-solid N with depth.

    Built as two heavy slanted stems plus a diagonal that reads as the folded
    ribbon; the leading edge is drawn thicker to suggest the 2016 ribbon depth.
    """
    return (f'<path d="M 216 104 L 184 408" {_s(c, 52)}/>'
            f'<path d="M 368 104 L 336 408" {_s(c, 52)}/>'
            f'<path d="M 216 104 L 336 408" {_s(c, 46)}/>'
            f'<path d="M 216 104 L 348 380" {_s(c, 20)}/>')


def crunchyroll_eye(c):
    """Crunchyroll: the orange eye/sushi-roll — an outer ring with an off-centre
    crescent pupil, matching the brand's asymmetric eye."""
    import math
    # outer ellipse
    body = f'<path d="M 84 256 C 84 206 162 174 256 174 C 350 174 428 206 ' \
            f'428 256 C 428 306 350 338 256 338 C 162 338 84 306 84 256 Z" ' \
            f'{_s(c, 30)}/>'
    # off-centre crescent: a thick C-like arc inside, on the right
    body += f'<path d="M 206 210 C 300 190 340 240 322 286 C 306 326 236 330 ' \
            f'210 300" {_s(c, 24)}/>'
    # small pupil dot in the counter
    body += f'<circle cx="262" cy="272" r="14" {_f(c)}/>'
    return body


def paramount_peak(c):
    """Paramount+: the mountain peak ringed by stars inside an arc.

    The majestic mountain with a snowline; a subtle arc of small stars cradles
    the peak, echoing the 22-star ring of the classic mark.
    """
    import math
    out = f'<path d="M 256 78 L 418 344 L 94 344 Z" {_s(c, 32)}/>'
    out += f'<path d="M 256 78 L 300 168" {_s(c, 22)}/>'
    out += f'<path d="M 170 268 L 340 268" {_s(c, 22)}/>'
    # arc of stars around the peak
    for i in range(7):
        a = math.radians(140 + i * (100 / 6))
        x = 256 + 250 * math.cos(a)
        y = 120 + 250 * math.sin(a)
        if 100 < x < 412:
            out += f'<circle cx="{x:.0f}" cy="{y:.0f}" r="12" {_f(c)}/>'
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
    """Deezer: the staircase of audio bars — verticals of growing height."""
    bars = []
    tops = [(150, 330), (188, 292), (226, 254), (264, 216), (302, 292), (340, 254)]
    for i, (x, top) in enumerate(tops):
        y2 = 384 if i % 2 == 0 else 312
        bars.append(f'<path d="M {x} {top} L {x} {y2}" {_s(c, 26)}/>')
    return "".join(bars)


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
    """BBC iPlayer: a rounded play frame with a notched play — 'on demand'."""
    return (f'<rect x="72" y="72" width="368" height="368" rx="92" {_s(c, 32)}/>'
            f'<path d="M 216 176 L 216 336 L 348 256 Z" {_s(c, 32)}/>')


def tubi_mark(c):
    """Tubi: a rounded 'T' that reads as the library gate — T over a base."""
    return (f'<path d="M 168 128 L 344 128" {_s(c, 44)}/>'
            f'<path d="M 256 128 L 256 384" {_s(c, 44)}/>'
            f'<path d="M 150 432 L 362 432" {_s(c, 30)}/>')


def justwatch_finder(c):
    """JustWatch: the streaming search — magnifier with a play inside."""
    return (f'<circle cx="232" cy="236" r="132" {_s(c, 34)}/>'
            f'<path d="M 214 186 L 214 286 L 300 236 Z" {_s(c, 28)}/>'
            f'<path d="M 322 322 L 428 428" {_s(c, 40)}/>')


def acorn_mark(c):
    """Acorn TV: the acorn — cap roundel over a tapered nut."""
    return (f'<path d="M 256 96 L 256 150" {_s(c, 28)}/>'
            f'<path d="M 148 150 L 364 150" {_s(c, 32)}/>'
            f'<path d="M 148 150 A 108 108 0 0 1 364 150" {_s(c, 30)}/>'
            f'<path d="M 176 232 C 176 300 216 356 256 392 '
            f'C 296 356 336 300 336 232 Z" {_s(c, 32)}/>')


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
    """MUBI: seven dots in a 3-3-1 arrangement — cinema, the 7th art.

    Simplified to the printed mark: three dots on the top line, three on the
    middle, and one below, so it reads at 512 without the wordmark.
    """
    import math
    pts = []
    rows = [(256, 128, 3), (256, 256, 3), (256, 384, 1)]
    for cx, cy, n in rows:
        for i in range(n):
            x = cx + (i - (n - 1) / 2) * 92
            pts.append(f'<circle cx="{x:.0f}" cy="{cy}" r="26" {_f(c)}/>')
    return "".join(pts)


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
    "tubi_mark": tubi_mark,
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
    """SBS (Australia): the five 'Mercator' globe splices.

    Five curved splices broadly resembling the Mercator globe's continents and
    the tilt of the Earth's axis, gathered around a shared midpoint.
    """
    # five curved splices: three crests up top, two set lower, all sweeping
    # around a common centre to evoke the folded Mercator globe.
    return (f'<path d="M 130 180 C 176 120 232 150 216 218" {_s(c, 26)}/>'
            f'<path d="M 210 180 C 244 130 300 140 292 210" {_s(c, 24)}/>'
            f'<path d="M 296 186 C 332 146 372 168 366 216" {_s(c, 22)}/>'
            f'<path d="M 156 268 C 200 300 300 300 356 268" {_s(c, 26)}/>'
            f'<path d="M 116 318 C 170 356 342 356 396 318" {_s(c, 26)}/>')


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
    """Instagram: the camera — rounded body, lens, and a top flash dot."""
    return (f'<rect x="96" y="122" width="320" height="300" rx="96" {_s(c, 34)}/>'
            f'<circle cx="256" cy="272" r="92" {_s(c, 32)}/>'
            f'<circle cx="256" cy="272" r="34" {_s(c, 24)}/>'
            f'<circle cx="340" cy="182" r="20" {_f(c)}/>')


def amazon_smile(c):
    """Amazon Music: the signature smile arrow — a rising curve with an arrow."""
    return (f'<path d="M 120 320 C 200 396 330 396 400 300" {_s(c, 36)}/>'
            f'<path d="M 400 300 L 398 250 L 352 268" {_s(c, 24)}/>')


def sling_s(c):
    """Sling TV: the slanted S — one sheared S sweep, open ends."""
    return (f'<path d="M 286 128 C 216 128 196 176 226 214 C 268 262 306 300 '
            f'276 366 C 256 404 208 410 172 392" {_s(c, 42)}/>')


def pluto_planet(c):
    """Pluto TV: the 'planetary echo' — a large planet ring around a tiny core.

    Reads as the planet Pluto (a small, bright world) orbited by a bold ring,
    matching the 'planetary echo' device around the tv wordmark.
    """
    return (f'<circle cx="256" cy="256" r="160" {_s(c, 30)}/>'
            f'<path d="M 96 180 C 140 138 372 138 416 180 C 372 222 140 222 '
            f'96 180 Z" {_s(c, 22)}/>'
            f'<circle cx="150" cy="256" r="22" {_f(c)}/>'
            f'<circle cx="256" cy="300" r="14" {_f(c)}/>')


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
    """YouTube Music: the note — a play circle with a stem flag."""
    return (f'<circle cx="256" cy="256" r="178" {_s(c, 34)}/>'
            f'<path d="M 230 200 L 230 312 L 300 256 Z" {_s(c, 30)}/>')


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
    out = f'<circle cx="256" cy="240" r="150" {_s(c, 22)}/>'
    # radiating sun bars
    for i in range(8):
        a = math.radians(-160 + i * 46)
        x1 = 256 + 168 * math.cos(a)
        y1 = 240 - 168 * math.sin(a)
        x2 = 256 + 212 * math.cos(a)
        y2 = 240 - 212 * math.sin(a)
        out += f'<path d="M {x1:.0f} {y1:.0f} L {x2:.0f} {y2:.0f}" {_s(c, 22)}/>'
    # left bull (lean head + horn)
    out += (f'<path d="M 150 300 C 160 250 180 226 214 218 C 190 244 190 286 '
            f'214 310 C 234 328 278 328 300 310" {_s(c, 24)}/>')
    # right bull (mirror)
    out += (f'<path d="M 362 300 C 352 250 332 226 298 218 C 322 244 322 286 '
            f'298 310 C 278 328 234 328 212 310" {_s(c, 24)}/>')
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
    "pluto_planet": pluto_planet,
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
    """NFL: the shield — an NFL-style crest with a ball at its heart."""
    return (f'<path d="M 256 92 L 388 148 L 388 268 C 388 354 336 406 256 428 '
            f'C 176 406 124 354 124 268 L 124 148 Z" {_s(c, 30)}/>'
            f'<ellipse cx="256" cy="276" rx="70" ry="44" {_s(c, 24)}/>'
            f'<path d="M 200 150 L 312 150 M 200 192 L 312 192" {_s(c, 20)}/>')


def nba_ball(c):
    """NBA: the red/blue shield with the silhouette — a crest + an arc player."""
    return (f'<path d="M 188 160 L 324 160 L 324 290 L 256 420 L 188 290 Z" '
            f'{_s(c, 30)}/>'
            f'<circle cx="256" cy="256" r="42" {_s(c, 22)}/>'
            f'<path d="M 256 214 L 256 298" {_s(c, 18)}/>')


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
    """Nebula: the cosmic dot — a circle with a ringed orbit."""
    return (f'<circle cx="256" cy="256" r="120" {_s(c, 32)}/>'
            f'<ellipse cx="256" cy="256" rx="180" ry="66" {_s(c, 26)}/>'
            f'<circle cx="256" cy="256" r="26" {_f(c)}/>')


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
    """NordVPN: the shield-arrow — a shield with an upward chevron."""
    return (f'<path d="M 256 84 L 392 140 L 392 268 C 392 352 336 400 256 424 '
            f'C 176 400 120 352 120 268 L 120 140 Z" {_s(c, 32)}/>'
            f'<path d="M 180 268 L 256 190 L 332 268" {_s(c, 32)}/>')


def proton_shield(c):
    """Proton: the shield — a shield with a key cut."""
    return (f'<path d="M 256 92 L 384 148 L 384 270 C 384 356 330 404 256 428 '
            f'C 182 404 128 356 128 270 L 128 148 Z" {_s(c, 32)}/>'
            f'<circle cx="256" cy="250" r="40" {_s(c, 24)}/>'
            f'<path d="M 256 290 L 256 360" {_s(c, 24)}/>')


def expressvpn_mark(c):
    """ExpressVPN: the key-lock — a rounded shield with a keyhole."""
    return (f'<path d="M 226 92 L 286 92 L 286 150 C 340 170 380 210 380 268 '
            f'C 380 350 324 410 256 428 C 188 410 132 350 132 268 '
            f'C 132 210 172 170 226 150 Z" {_s(c, 30)}/>'
            f'<circle cx="256" cy="262" r="52" {_s(c, 22)}/>'
            f'<path d="M 256 314 L 256 384" {_s(c, 22)}/>')


def wireguard_mark(c):
    """WireGuard: the wave-key — a coil of curved lines.

    Three interlocking S-curved lines spiral around a centre, evoking the
    WireGuard wave-key (a key whose teeth are curved waves) rather than a set
    of separate dots.
    """
    return (f'<path d="M 150 170 C 130 220 382 220 362 270 C 342 320 130 320 '
            f'150 270 C 170 220 382 220 362 270" {_s(c, 24)}/>'
            f'<path d="M 196 120 C 176 170 428 170 408 220" {_s(c, 24)}/>'
            f'<circle cx="256" cy="256" r="26" {_f(c)}/>')


def mullvad_shield(c):
    """Mullvad: the duck head — a rounded bird profile in a rounded square.

    The Mullvad brand mark is a stylised duck/bird head; simplified to a titled
    circle head with a beak and eye inside a soft tile.
    """
    return (f'<rect x="80" y="90" width="352" height="352" rx="92" {_s(c, 30)}/>'
            f'<path d="M 150 320 C 130 250 170 200 250 200 C 330 200 360 250 '
            f'340 320 C 320 372 200 372 150 320 Z" {_s(c, 24)}/>'
            f'<path d="M 150 320 L 128 334" {_s(c, 20)}/>'
            f'<circle cx="250" cy="266" r="15" {_f(c)}/>')


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
    """RetroArch: the game pad — a controller with a d-pad."""
    return (f'<path d="M 130 200 C 130 150 190 130 230 160 L 280 200 C 296 214 '
            f'330 214 346 200 L 396 160 C 436 130 496 150 496 200 '
            f'C 496 268 470 360 420 372 C 386 380 360 340 352 310 '
            f'C 340 284 316 270 256 270 C 196 270 172 284 160 310 '
            f'C 152 340 126 380 92 372 C 42 360 16 268 16 200 Z" {_s(c, 28)}/>')


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
    """Sirius: the satellite — a dish with radiating orbit."""
    return (f'<path d="M 104 396 C 104 300 180 224 276 224" {_s(c, 32)}/>'
            f'<circle cx="128" cy="372" r="26" {_f(c)}/>'
            f'<path d="M 300 160 C 360 160 420 220 420 280" {_s(c, 26)}/>'
            f'<path d="M 300 104 C 388 104 476 192 476 280" {_s(c, 26)}/>')


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
    """NHL: the puck — an ellipse puck with a star notch."""
    return (f'<ellipse cx="256" cy="282" rx="150" ry="88" {_s(c, 32)}/>'
            f'<path d="M 256 150 L 280 170 L 256 190 L 232 170 Z" {_s(c, 22)}/>')


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
    """RuStore: the store tile — a stacked tile with a spark."""
    return (f'<rect x="110" y="150" width="130" height="130" rx="24" {_s(c, 28)}/>'
            f'<rect x="272" y="150" width="130" height="130" rx="24" {_s(c, 28)}/>'
            f'<rect x="110" y="312" width="292" height="80" rx="24" {_s(c, 26)}/>')


def obtainium_mark(c):
    """Obtainium: the gear-box — a box with a cog."""
    return (f'<rect x="110" y="150" width="292" height="212" rx="36" {_s(c, 30)}/>'
            f'<circle cx="256" cy="256" r="40" {_s(c, 24)}/>')


def iflix_mark(c):
    """iflix: the play ribbon — a play with a ribbon tail."""
    return (f'<path d="M 200 200 L 200 312 L 310 256 Z" {_s(c, 34)}/>'
            f'<path d="M 200 312 L 340 360" {_s(c, 26)}/>')


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
    """YouTube Kids: the play chair — a play flipped with a smile."""
    return (f'<path d="M 96 140 C 96 100 150 92 172 120 L 200 160" {_s(c, 28)}/>'
            f'<rect x="40" y="124" width="432" height="264" rx="74" {_s(c, 30)}/>'
            f'<path d="M 200 250 L 200 330 L 290 290 Z" {_s(c, 26)}/>')


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
    """Mi TV Plus: the Mi TV — a rounded screen with the Mi."""
    return (f'<rect x="80" y="140" width="352" height="220" rx="40" {_s(c, 30)}/>'
            f'<path d="M 210 320 L 210 180 M 256 320 L 256 180 M 302 320 L 302 180" '
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
