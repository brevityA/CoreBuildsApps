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


def monogram_N(c):
    return _mono(c, ["M 168 356 L 168 156 L 344 356 L 344 156"])


def monogram_M(c):
    return _mono(c, ["M 150 356 L 150 156 L 256 286 L 362 156 L 362 356"])


def monogram_S(c):
    return _mono(c, [
        "M 340 190 C 300 148 200 148 186 200 C 172 252 250 258 290 268 "
        "C 336 280 356 316 330 348 C 300 384 206 380 172 336"])


def monogram_B(c):
    return _mono(c, [
        "M 180 156 L 180 356",
        "M 180 156 L 288 156 C 344 156 344 246 288 246 L 180 246",
        "M 180 246 L 300 246 C 358 246 358 356 300 356 L 180 356"])


def monogram_K(c):
    return _mono(c, [
        "M 176 156 L 176 356", "M 336 156 L 196 256", "M 240 224 L 344 356"])


def monogram_W(c):
    return _mono(c, ["M 132 156 L 186 356 L 256 214 L 326 356 L 380 156"])


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
    return (f'<polygon points="{_hexpts(256, 256, 190)}" {_s(c, 36)}/>'
            f'<path d="M 218 186 L 330 256 L 218 326 Z" {_f(c)}/>')


def play_round(c):
    return (f'<circle cx="256" cy="256" r="182" {_s(c, 36)}/>'
            f'<path d="M 216 176 L 340 256 L 216 336 Z" {_f(c)}/>')


def play_rect(c):
    return (f'<rect x="72" y="118" width="368" height="276" rx="66" {_s(c, 36)}/>'
            f'<path d="M 220 190 L 336 256 L 220 322 Z" {_f(c)}/>')


def kodi_box(c):
    # standing bar + forward wedge: the media-centre silhouette, no screen
    return (f'<path d="M 138 86 L 138 426" {_s(c, 46)}/>'
            f'<path d="M 214 122 L 402 256 L 214 390 Z" {_s(c, 38)}/>')


def jellyfin_chevrons(c):
    return (f'<path d="M 256 120 L 396 350 L 116 350 Z" {_s(c, 34)}/>'
            f'<path d="M 256 222 L 318 322 L 194 322 Z" {_f(c)}/>')


def emby_shield(c):
    return (f'<path d="M 256 92 L 404 152 L 404 268 C 404 350 336 400 256 424 '
            f'C 176 400 108 350 108 268 L 108 152 Z" {_s(c, 34)}/>'
            f'<path d="M 224 196 L 320 258 L 224 320 Z" {_f(c)}/>')


def plex_chevron(c):
    return (f'<path d="M 176 108 L 300 256 L 176 404" {_s(c, 44)}/>'
            f'<path d="M 300 108 L 380 256 L 300 404" {_s(c, 44)}/>')


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
    # three-turret castle skyline with a plus above the right tower
    return (f'<path d="M 112 400 L 112 250 L 160 196 L 208 250 L 208 400" '
            f'{_s(c, 30)}/>'
            f'<path d="M 208 400 L 208 176 L 262 112 L 316 176 L 316 400" '
            f'{_s(c, 30)}/>'
            f'<path d="M 316 400 L 316 250 L 360 200 L 404 250 L 404 400" '
            f'{_s(c, 30)}/>'
            f'<path d="M 86 400 L 430 400" {_s(c, 32)}/>'
            f'<path d="M 262 92 L 262 62" {_s(c, 24)}/>'
            f'<path d="M 400 92 L 400 148" {_s(c, 24)}/>'
            f'<path d="M 372 120 L 428 120" {_s(c, 24)}/>')


def apple_tv(c):
    return (f'<rect x="76" y="126" width="360" height="228" rx="44" {_s(c, 34)}/>'
            f'<path d="M 176 412 L 336 412" {_s(c, 34)}/>'
            f'<path d="M 256 354 L 256 412" {_s(c, 34)}/>'
            f'<circle cx="256" cy="240" r="46" {_f(c)}/>')


def eye(c):
    return (f'<path d="M 76 256 C 150 156 362 156 436 256 '
            f'C 362 356 150 356 76 256 Z" {_s(c, 34)}/>'
            f'<circle cx="256" cy="256" r="58" {_f(c)}/>')


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
    """The parent brand mark, glyph-weight. Hex stance is load-bearing (§02)."""
    return (f'<polygon points="{_hexpts(256, 256, 196)}" {_s(c, 34)}/>'
            f'<polygon points="256,166 346,256 256,346 166,256" {_f(c)}/>')


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
    "monogram_N": monogram_N, "monogram_M": monogram_M,
    "monogram_S": monogram_S, "monogram_B": monogram_B,
    "monogram_K": monogram_K, "monogram_W": monogram_W,
    "monogram_9": monogram_9, "monogram_7": monogram_7,
    "monogram_10": monogram_10,
}


def render_svg(glyph_name, color):
    body = GLYPHS[glyph_name](color)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {GRID} {GRID}" '
            f'width="{GRID}" height="{GRID}">\n  {body}\n</svg>\n')
