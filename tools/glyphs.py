import re
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
    """YouTube: rounded rect and play, both outlined (monoline)."""
    return (f'<rect x="40" y="124" width="432" height="264" rx="74" {_s(c, 34)}/>'
            f'<path d="M 218 198 L 218 314 L 330 256 Z" {_s(c, 32)}/>')


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
# Written because a pack this size needs a distinct fallback: a repeated
# generic glyph across dozens of apps is what made the earlier set read as a
# rebrand. An app's own initial is always specific to it.
#
# Original stroke geometry on the 512 grid - single weight, rounded
# terminals, matching the monoline language. Not traced from any typeface.
# ==========================================================================


def monogram_0(c):
    """Monogram 0 — original stroke geometry, monoline weight."""
    return (f'<path d="M 256 116 C 322 116 356 176 356 256 C 356 336 322 396 256 396 C 190 396 156 336 156 256 C 156 176 190 116 256 116 Z" {_s(c, 40)}/>')


def monogram_1(c):
    """Monogram 1 — original stroke geometry, monoline weight."""
    return (f'<path d="M 186 176 L 256 116 L 256 396" {_s(c, 40)}/>'
            f'<path d="M 190 396 L 326 396" {_s(c, 40)}/>')


def monogram_2(c):
    """Monogram 2 — original stroke geometry, monoline weight."""
    return (f'<path d="M 172 176 C 196 128 300 112 336 160 C 372 208 320 258 268 300 L 168 396 L 348 396" {_s(c, 40)}/>')


def monogram_3(c):
    """Monogram 3 — original stroke geometry, monoline weight."""
    return (f'<path d="M 176 162 C 210 116 320 118 340 172 C 356 218 306 254 266 254" {_s(c, 40)}/>'
            f'<path d="M 266 254 C 320 254 360 286 348 340 C 332 398 208 402 172 350" {_s(c, 40)}/>')


def monogram_4(c):
    """Monogram 4 — original stroke geometry, monoline weight."""
    return (f'<path d="M 300 396 L 300 116 L 148 306 L 356 306" {_s(c, 40)}/>')


def monogram_5(c):
    """Monogram 5 — original stroke geometry, monoline weight."""
    return (f'<path d="M 340 116 L 196 116 L 180 250 C 232 214 318 226 342 282 C 366 342 314 396 250 396 C 212 396 182 382 164 358" {_s(c, 40)}/>')


def monogram_6(c):
    """Monogram 6 — original stroke geometry, monoline weight."""
    return (f'<path d="M 330 140 C 262 108 178 152 168 246 C 158 340 210 396 268 396 C 322 396 356 356 356 312 C 356 262 316 230 268 230 C 222 230 186 258 174 292" {_s(c, 40)}/>')


def monogram_8(c):
    """Monogram 8 — original stroke geometry, monoline weight."""
    return (f'<path d="M 256 116 C 306 116 336 148 336 184 C 336 220 306 246 256 246 C 206 246 176 220 176 184 C 176 148 206 116 256 116 Z" {_s(c, 40)}/>'
            f'<path d="M 256 246 C 314 246 350 282 350 322 C 350 364 312 396 256 396 C 200 396 162 364 162 322 C 162 282 198 246 256 246 Z" {_s(c, 40)}/>')


def monogram_A(c):
    """Monogram A — original stroke geometry, monoline weight."""
    return (f'<path d="M 138 396 L 256 116 L 374 396" {_s(c, 40)}/>'
            f'<path d="M 190 300 L 322 300" {_s(c, 40)}/>')


def monogram_B(c):
    """Monogram B — original stroke geometry, monoline weight."""
    return (f'<path d="M 176 116 L 176 396" {_s(c, 40)}/>'
            f'<path d="M 176 116 L 286 116 C 342 116 342 246 286 246 L 176 246" {_s(c, 40)}/>'
            f'<path d="M 176 246 L 296 246 C 354 246 354 396 296 396 L 176 396" {_s(c, 40)}/>')


def monogram_C(c):
    """Monogram C — original stroke geometry, monoline weight."""
    return (f'<path d="M 360 176 C 322 130 250 116 202 152 C 148 192 140 320 202 360 C 250 392 322 382 360 336" {_s(c, 40)}/>')


def monogram_D(c):
    """Monogram D — original stroke geometry, monoline weight."""
    return (f'<path d="M 178 116 L 178 396 L 262 396 C 356 396 384 330 384 256 C 384 182 356 116 262 116 Z" {_s(c, 40)}/>')


def monogram_E(c):
    """Monogram E — original stroke geometry, monoline weight."""
    return (f'<path d="M 350 116 L 172 116 L 172 396 L 350 396" {_s(c, 40)}/>'
            f'<path d="M 172 256 L 320 256" {_s(c, 40)}/>')


def monogram_F(c):
    """Monogram F — original stroke geometry, monoline weight."""
    return (f'<path d="M 350 116 L 172 116 L 172 396" {_s(c, 40)}/>'
            f'<path d="M 172 256 L 316 256" {_s(c, 40)}/>')


def monogram_G(c):
    """Monogram G — original stroke geometry, monoline weight."""
    return (f'<path d="M 360 176 C 322 130 250 116 202 152 C 148 192 140 320 202 360 C 258 396 344 380 362 320 L 362 262 L 282 262" {_s(c, 40)}/>')


def monogram_H(c):
    """Monogram H — original stroke geometry, monoline weight."""
    return (f'<path d="M 168 116 L 168 396" {_s(c, 40)}/>'
            f'<path d="M 344 116 L 344 396" {_s(c, 40)}/>'
            f'<path d="M 168 256 L 344 256" {_s(c, 40)}/>')


def monogram_I(c):
    """Monogram I — original stroke geometry, monoline weight."""
    return (f'<path d="M 256 116 L 256 396" {_s(c, 40)}/>'
            f'<path d="M 190 116 L 322 116" {_s(c, 40)}/>'
            f'<path d="M 190 396 L 322 396" {_s(c, 40)}/>')


def monogram_J(c):
    """Monogram J — original stroke geometry, monoline weight."""
    return (f'<path d="M 330 116 L 330 320 C 330 384 256 404 206 372" {_s(c, 40)}/>')


def monogram_K(c):
    """Monogram K — original stroke geometry, monoline weight."""
    return (f'<path d="M 176 116 L 176 396" {_s(c, 40)}/>'
            f'<path d="M 344 116 L 200 258" {_s(c, 40)}/>'
            f'<path d="M 244 222 L 352 396" {_s(c, 40)}/>')


def monogram_L(c):
    """Monogram L — original stroke geometry, monoline weight."""
    return (f'<path d="M 180 116 L 180 396 L 348 396" {_s(c, 40)}/>')


def monogram_M(c):
    """Monogram M — original stroke geometry, monoline weight."""
    return (f'<path d="M 150 396 L 150 116 L 256 254 L 362 116 L 362 396" {_s(c, 40)}/>')


def monogram_N(c):
    """Monogram N — original stroke geometry, monoline weight."""
    return (f'<path d="M 168 396 L 168 116 L 344 396 L 344 116" {_s(c, 40)}/>')


def monogram_O(c):
    """Monogram O — original stroke geometry, monoline weight."""
    return (f'<path d="M 256 116 C 330 116 372 176 372 256 C 372 336 330 396 256 396 C 182 396 140 336 140 256 C 140 176 182 116 256 116 Z" {_s(c, 40)}/>')


def monogram_P(c):
    """Monogram P — original stroke geometry, monoline weight."""
    return (f'<path d="M 180 396 L 180 116 L 282 116 C 344 116 358 168 358 202 C 358 236 344 288 282 288 L 180 288" {_s(c, 40)}/>')


def monogram_Q(c):
    """Monogram Q — original stroke geometry, monoline weight."""
    return (f'<path d="M 256 116 C 330 116 372 176 372 256 C 372 336 330 396 256 396 C 182 396 140 336 140 256 C 140 176 182 116 256 116 Z" {_s(c, 40)}/>'
            f'<path d="M 296 320 L 380 412" {_s(c, 40)}/>')


def monogram_R(c):
    """Monogram R — original stroke geometry, monoline weight."""
    return (f'<path d="M 180 396 L 180 116 L 282 116 C 344 116 358 168 358 200 C 358 232 344 282 282 282 L 180 282" {_s(c, 40)}/>'
            f'<path d="M 268 282 L 366 396" {_s(c, 40)}/>')


def monogram_S(c):
    """Monogram S — original stroke geometry, monoline weight."""
    return (f'<path d="M 348 172 C 314 128 218 124 194 176 C 170 228 246 254 288 266 C 336 280 356 314 330 350 C 300 392 210 386 172 342" {_s(c, 40)}/>')


def monogram_T(c):
    """Monogram T — original stroke geometry, monoline weight."""
    return (f'<path d="M 148 116 L 364 116" {_s(c, 40)}/>'
            f'<path d="M 256 116 L 256 396" {_s(c, 40)}/>')


def monogram_U(c):
    """Monogram U — original stroke geometry, monoline weight."""
    return (f'<path d="M 168 116 L 168 300 C 168 366 210 396 256 396 C 302 396 344 366 344 300 L 344 116" {_s(c, 40)}/>')


def monogram_V(c):
    """Monogram V — original stroke geometry, monoline weight."""
    return (f'<path d="M 152 116 L 256 396 L 360 116" {_s(c, 40)}/>')


def monogram_W(c):
    """Monogram W — original stroke geometry, monoline weight."""
    return (f'<path d="M 128 116 L 184 396 L 256 208 L 328 396 L 384 116" {_s(c, 40)}/>')


def monogram_X(c):
    """Monogram X — original stroke geometry, monoline weight."""
    return (f'<path d="M 168 116 L 344 396" {_s(c, 40)}/>'
            f'<path d="M 344 116 L 168 396" {_s(c, 40)}/>')


def monogram_Y(c):
    """Monogram Y — original stroke geometry, monoline weight."""
    return (f'<path d="M 160 116 L 256 262 L 352 116" {_s(c, 40)}/>'
            f'<path d="M 256 262 L 256 396" {_s(c, 40)}/>')


def monogram_Z(c):
    """Monogram Z — original stroke geometry, monoline weight."""
    return (f'<path d="M 168 116 L 348 116 L 168 396 L 348 396" {_s(c, 40)}/>')


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
