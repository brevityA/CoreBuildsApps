import re

from typeface import monogram_body, monogram_text, monogram_scaled
from icon_style import display_accent
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


def render_svg(glyph_name, color, glow=False, *, monochrome=False):
    """
    Render the transparent Classic glyph in the common monoline treatment.

    Glow is opt-in for legacy experiments, never used by the pack generators.
    """
    color = display_accent(color, monochrome=monochrome)
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
    """TizenTube: play inside a soft square, ad-blocked slash.

    The slash crossed the wedge and closed it. Running the slash clear of the
    play keeps both marks whole.
    """
    return (f'<rect x="70" y="112" width="372" height="288" rx="64" {_s(c, 34)}/>'
            f'<path d="M 226 192 L 226 320 L 340 256 Z" {_s(c, 30)}/>'
            f'<path d="M 108 398 L 196 310" {_s(c, 30)}/>')


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


GLYPHS.update({"stadium": stadium, "browser_globe": browser_globe})


# ==========================================================================
# Bespoke marks for apps seen on the user's real device row.
#
# Each replaces a shared glyph: Janky was one of five apps on play_round,
# TiviMate one of twenty-one on monogram_T. A mark shared twenty-one ways
# is not a mark, it is a placeholder.
# ==========================================================================

def janky_play(c):
    """Janky Player - the hamster, face on, inside its wheel.

    Fourth composition, and the first three failed for reasons worth keeping:

      split play wedge    reads as a letter B at every size
      wedge tilted 16deg  reads as a play button; Janky is not one
      wheel + profile     three elements competing - a rung texture that
                          reads as a clock bezel, a hamster too small to be
                          the subject, and a ring scaled for neither

    The marks that work in this pack (Trakt, Weyd, MUBI) commit to one idea
    at generous scale, so this does too: the animal is the subject and the
    ring is just the wheel around it. A face survives the downscale where a
    body profile cannot - two ears and a muzzle are three bold shapes, and a
    silhouette is one lumpy one.

    Ear proportion is deliberate. Set large and high the face reads as a
    bear; small, wide and low at 30 degrees it reads as a rodent.

    Stroke weights account for monoline() snapping 24 to 26.2 and 30 to 32.
    """
    return (f'<circle cx="256" cy="256" r="186" {_s(c, 30)}/>'
            f'<circle cx="142" cy="210" r="24" {_s(c, 24)}/>'
            f'<circle cx="370" cy="210" r="24" {_s(c, 24)}/>'
            f'<path d="M 256 153 C 393 162 393 367 256 383 C 119 367 119 162 256 153 Z" {_s(c, 30)}/>'
            f'<circle cx="256" cy="318" r="20" {_s(c, 22)}/>')


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


def tubi_mark(c):
    """Tubi: a rounded 'T' that reads as the library gate — T over a base."""
    return (f'<path d="M 168 128 L 344 128" {_s(c, 44)}/>'
            f'<path d="M 256 128 L 256 384" {_s(c, 44)}/>'
            f'<path d="M 150 432 L 362 432" {_s(c, 30)}/>')


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


def pluto_planet(c):
    """Pluto TV: the planetary echo - a world crossed by its ring.

    Two filled satellites sat inside the disc and closed the ring gap. The
    planet and the ring alone carry the device.
    """
    return (f'<circle cx="256" cy="256" r="164" {_s(c, 30)}/>'
            f'<path d="M 84 186 C 140 136 372 136 428 186" {_s(c, 24)}/>')


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


GLYPHS.update({
    "coreline_ticker": coreline_ticker,
    "coreshift_frames": coreshift_frames,
    "coredoctor_pulse": coredoctor_pulse,
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


def shell_broadcast(c):
    """A screen on legs with an aerial: any channel, network or catch-up app."""
    return (f'<rect x="72" y="128" width="368" height="252" rx="44" {_s(c, 30)}/>'
            f'<path d="M 196 128 L 148 72" {_s(c, 22)}/>'
            f'<path d="M 316 128 L 364 72" {_s(c, 22)}/>'
            f'<path d="M 180 428 L 332 428" {_s(c, 26)}/>')


def shell_app(c):
    """The neutral squircle, kept for apps whose function cannot be read."""
    return _tile(c)


def shell_tool(c):
    """A nut seen face on: utilities, remotes, system tweaks."""
    return f'<polygon points="{_hexpts(256, 256, 196)}" {_s(c, 30)}/>'


def shell_sport(c):
    """A ball."""
    return f'<circle cx="256" cy="256" r="188" {_s(c, 30)}/>'


def shell_music(c):
    """A tile with sound leaving it: radio, music, podcasts.

    The outer arc reached x=495 on the 512 grid; both arcs are pulled inside
    the 40px margin.
    """
    return (f'<rect x="56" y="96" width="300" height="320" rx="70" {_s(c, 30)}/>'
            f'<path d="M 392 196 C 420 230 420 282 392 316" {_s(c, 24)}/>'
            f'<path d="M 428 158 C 470 212 470 300 428 354" {_s(c, 20)}/>')


def shell_gaming(c):
    """A gamepad: body with two grips hanging below it.

    The first attempt was a single lobed outline that read as goggles rather
    than a controller, and its interior left the monogram too small to tell a
    C from a G. Splitting the grips off the body gives the letter the whole
    body to sit in and makes the silhouette unambiguous.
    """
    return (f'<rect x="76" y="112" width="360" height="196" rx="58" {_s(c, 30)}/>'
            f'<path d="M 132 302 C 104 372 120 430 164 430 '
            f'C 202 430 214 388 210 330" {_s(c, 26)}/>'
            f'<path d="M 380 302 C 408 372 392 430 348 430 '
            f'C 310 430 298 388 302 330" {_s(c, 26)}/>')


def shell_vpn(c):
    """A shield: VPN, proxy, privacy."""
    return (f'<path d="M 256 68 L 424 132 L 424 268 C 424 360 352 418 256 444 '
            f'C 160 418 88 360 88 268 L 88 132 Z" {_s(c, 30)}/>')


def shell_film(c):
    """A film frame with its sprocket lanes: cinema and movie VOD."""
    return (f'<rect x="64" y="112" width="384" height="288" rx="36" {_s(c, 30)}/>'
            f'<path d="M 124 148 L 164 148 M 224 148 L 264 148 '
            f'M 324 148 L 364 148" {_s(c, 20)}/>'
            f'<path d="M 124 364 L 164 364 M 224 364 L 264 364 '
            f'M 324 364 L 364 364" {_s(c, 20)}/>')


def shell_store(c):
    """A shopping bag: stores, installers, sideload managers."""
    return (f'<path d="M 92 168 L 420 168 L 400 424 C 398 438 388 446 374 446 '
            f'L 138 446 C 124 446 114 438 112 424 Z" {_s(c, 30)}/>'
            f'<path d="M 176 208 L 176 136 C 176 100 212 72 256 72 '
            f'C 300 72 336 100 336 136 L 336 208" {_s(c, 24)}/>')


def shell_photos(c):
    """A print with its caption band: photo, gallery and slideshow apps.

    A camera body with a viewfinder hump read as a briefcase at tile size -
    the hump was too small to carry the meaning. A print is a plainer idea and
    survives the downscale: the band under the image is the whole tell.
    """
    return (f'<rect x="76" y="72" width="360" height="368" rx="40" {_s(c, 30)}/>'
            f'<path d="M 76 348 L 436 348" {_s(c, 24)}/>')


def shell_debrid(c):
    """A cloud: debrid, remote storage, torrent and usenet clients.

    Narrowed from a 482px right edge so the stroke stays inside SAFE.
    """
    return (f'<path d="M 158 392 C 108 392 76 354 76 308 C 76 260 114 226 158 230 '
            f'C 174 174 220 136 276 136 C 344 136 396 190 398 258 '
            f'C 430 268 450 298 450 332 C 450 364 426 392 392 392 Z" {_s(c, 30)}/>')


def shell_browser(c):
    """A window with a title bar."""
    return (f'<rect x="64" y="104" width="384" height="304" rx="48" {_s(c, 30)}/>'
            f'<path d="M 64 180 L 448 180" {_s(c, 22)}/>')


def shell_anime(c):
    """A tile with a spark: anime and manga services.

    Two passes to fit: the spark's right arm first put ink at x=489, and
    shifting the tile to x=46 then put its left wall at x=31. At x=58 with a
    30 stroke the wall lands on 43 and the spark ends on 465 - both inside
    the 40px margin.
    """
    return (_tile(c, x=58, y=92, w=330, h=340, rx=80) +
            f'<path d="M 424 122 L 424 182 M 394 152 L 454 152" {_s(c, 22)}/>')


def shell_kids(c):
    """A tile with ears: kids and family services.

    Ears were clipped at y=35; the whole lockup sits 12px lower.
    """
    return (_tile(c, x=64, y=126, w=384, h=330, rx=82) +
            f'<circle cx="144" cy="102" r="38" {_s(c, 24)}/>'
            f'<circle cx="368" cy="102" r="38" {_s(c, 24)}/>')


def shell_files(c):
    """A folder."""
    return (f'<path d="M 64 148 L 212 148 L 252 200 L 448 200 L 448 400 '
            f'C 448 422 430 440 408 440 L 104 440 C 82 440 64 422 64 400 Z" '
            f'{_s(c, 30)}/>')


# Cap height and optical centre per shell: each interior is a different shape,
# and a monogram sized for the squircle either overflows the cloud or floats
# in the shield. Values are tuned against the 48px check, not by eye.
FAMILY_SHELLS = {
    "broadcast": (shell_broadcast, 150, 254),
    "app":       (shell_app,       200, 256),
    "tool":      (shell_tool,      200, 264),
    "sport":     (shell_sport,     210, 256),
    "music":     (shell_music,     200, 256),
    "gaming":    (shell_gaming,    150, 240),
    "vpn":       (shell_vpn,       190, 250),
    "film":      (shell_film,      170, 256),
    "store":     (shell_store,     180, 320),
    "photos":    (shell_photos,    160, 290),
    "debrid":    (shell_debrid,    150, 290),
    "browser":   (shell_browser,   180, 300),
    "anime":     (shell_anime,     190, 262),
    "kids":      (shell_kids,      210, 296),
    "files":     (shell_files,     180, 330),
}


def _mk_family(family, letter):
    shell, cap_h, cy = FAMILY_SHELLS[family]
    return lambda c: _fam(letter, c, shell(c), cap_h=cap_h, cy=cy)


_family_names = {}
for _fam_key in FAMILY_SHELLS:
    for _ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789":
        _family_names[f"{_fam_key}_{_ch}"] = _mk_family(_fam_key, _ch)
GLYPHS.update(_family_names)


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
