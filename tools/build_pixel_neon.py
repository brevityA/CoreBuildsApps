#!/usr/bin/env python3
"""Build the alternate Core Builds Pixel Neon icon pack.

The base catalog remains the source of truth for names, components, and glyphs.
This renderer deliberately gives that same coverage a different personality:
icons are rasterised on a 64x64 arcade grid, snapped to hard pixels, layered
with a violet/cyan neon bloom, then scaled with nearest-neighbour sampling.

Generated files live under ``pixel-neon/`` and are never hand-edited:
  pixel-neon/app/src/main/res/drawable-nodpi/<drawable>.png
  pixel-neon/app/src/main/res/drawable-nodpi/<drawable>_banner.png
  pixel-neon/app/src/main/res/{xml,assets}/...
  pixel-neon/app/src/main/res/values/icon_pack.xml
  pixel-neon/docs/IconPackList.md, preview.png, and build-receipt.json

Run after the regular icon generators have produced assets/svg and
assets/banners:

    python tools/build_pixel_neon.py

The renderer is intentionally a treatment, not a second catalog. Adding or
fixing a component therefore happens once in tools/catalog.json and both packs
stay in lockstep.
"""
from __future__ import annotations

import colorsys
import hashlib
import io
import json
import sys
from pathlib import Path
from xml.sax.saxutils import escape as xml_escape

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
BASE_SVG = ROOT / "assets" / "svg"
BASE_BANNERS = ROOT / "assets" / "banners"
OUT = ROOT / "pixel-neon"
PNG_DIR = OUT / "app" / "src" / "main" / "res" / "drawable-nodpi"
XML_DIR = OUT / "app" / "src" / "main" / "res" / "xml"
ASSETS_DIR = OUT / "app" / "src" / "main" / "assets"
VAL_DIR = OUT / "app" / "src" / "main" / "res" / "values"
DOC_DIR = OUT / "docs"

PIXEL_GRID = 64
ICON_SIZE = 512
BANNER_W, BANNER_H = 320, 180
DARK_PIXEL = "#080A19"
VOID = "#070916"
INK = "#E8FDFF"

# Make the repository's deterministic SVG adapter importable from any cwd.
sys.path.insert(0, str(ROOT / "tools"))


def esc(value: str) -> str:
    return xml_escape(str(value), {"'": "&apos;", '"': "&quot;"})


def color_tuple(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def rgb_hex(rgb: tuple[int, int, int]) -> str:
    return "#%02X%02X%02X" % tuple(max(0, min(255, c)) for c in rgb)


def neon_color(source: str, ordinal: int) -> str:
    """Make the catalog accent vivid without erasing meaningful hue changes."""
    rgb = color_tuple(source)
    h, s, v = colorsys.rgb_to_hsv(*(x / 255 for x in rgb))
    digest = hashlib.sha1(f"{source}:{ordinal}".encode()).digest()
    # Greys/near-black source colours need an arcade hue. Coloured source
    # accents keep their hue, but get a reliable saturation/value lift.
    if s < 0.16 or v < 0.18:
        h = (digest[0] / 255.0) * 0.95
    else:
        h = (h + ((digest[0] % 9) - 4) / 360.0) % 1.0
    s = max(0.78, min(1.0, s + 0.24))
    v = max(0.88, min(1.0, v + 0.18))
    out = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v))
    return rgb_hex(out)


def load_renderer():
    from svg_renderer import svg2png  # type: ignore
    return svg2png


def source_png(svg_path: Path, size: int, svg2png) -> "Image.Image":
    return source_raster(svg_path, size, size, svg2png)


def source_raster(svg_path: Path, width: int, height: int, svg2png) -> "Image.Image":
    from PIL import Image

    raw = svg2png(url=str(svg_path), output_width=width, output_height=height,
                  background_color=None)
    if raw is None:
        raw = svg_path.with_suffix(".png").read_bytes()
    return Image.open(io.BytesIO(raw)).convert("RGBA")


def solid_layer(color: str, mask, alpha=None):
    from PIL import Image

    rgb = color_tuple(color)
    layer = Image.new("RGBA", mask.size, (*rgb, 0))
    layer.putalpha(mask if alpha is None else alpha)
    return layer


def shift_mask(mask, dx: int, dy: int):
    """Shift a mask without wrapping pixels across the opposite edge."""
    from PIL import Image

    out = Image.new("L", mask.size, 0)
    width, height = mask.size
    src_left = max(0, -dx)
    src_top = max(0, -dy)
    src_right = min(width, width - dx) if dx >= 0 else width
    src_bottom = min(height, height - dy) if dy >= 0 else height
    if src_right <= src_left or src_bottom <= src_top:
        return out
    crop = mask.crop((src_left, src_top, src_right, src_bottom))
    out.paste(crop, (src_left + dx, src_top + dy))
    return out


def hard_mask(source):
    from PIL import Image

    alpha = source.getchannel("A")
    # A single 64px tile is an intentional design constraint. Thresholding
    # removes anti-aliased vector edges so every visible edge is a real pixel.
    return alpha.point(lambda p: 255 if p >= 72 else 0, mode="L")


def pixel_icon(source, accent: str):
    """Return a 512px transparent pixel/neon icon and its 64px master mask."""
    from PIL import Image, ImageChops, ImageFilter

    mask = hard_mask(source)
    expanded = mask.filter(ImageFilter.MaxFilter(5))
    shadow_ring = ImageChops.subtract(expanded, mask)

    # Bloom at the low resolution first. Scaling it later keeps the glow
    # chunky instead of turning a pixel icon into a soft vector icon.
    bloom_wide = mask.filter(ImageFilter.GaussianBlur(3.0)).point(
        lambda p: min(150, round(p * 0.60)), mode="L")
    bloom_core = mask.filter(ImageFilter.GaussianBlur(1.25)).point(
        lambda p: min(215, round(p * 0.86)), mode="L")

    out = Image.new("RGBA", (PIXEL_GRID, PIXEL_GRID), (0, 0, 0, 0))
    out.alpha_composite(solid_layer("#512B9B", bloom_wide))
    out.alpha_composite(solid_layer(accent, bloom_core))
    out.alpha_composite(solid_layer(DARK_PIXEL, shadow_ring))
    out.alpha_composite(solid_layer(accent, mask))

    # One-pixel top-left glint and a darker bottom-right edge give the mark
    # the readable bevel of a CRT sprite without adding a second accent colour
    # to the catalog entry itself.
    top_left = ImageChops.subtract(mask, shift_mask(mask, 1, 1))
    bottom_right = ImageChops.subtract(mask, shift_mask(mask, -1, -1))
    out.alpha_composite(solid_layer(INK, top_left.point(lambda p: round(p * .78))))
    out.alpha_composite(solid_layer("#24134D", bottom_right.point(lambda p: round(p * .55))))

    # Nearest-neighbour is the important part: no smoothing at the final
    # 512px boundary, so the APK carries a genuinely pixelated mark.
    final = out.resize((ICON_SIZE, ICON_SIZE), Image.Resampling.NEAREST)
    return final, mask


def pixel_banner(source, accent: str):
    """Pixelate an existing transparent banner while preserving its layout."""
    from PIL import Image, ImageEnhance, ImageFilter

    # The base banner is already authored for Projectivy's 16:9 card. Render
    # it at half-size then scale by exactly two, which gives a legible chunky
    # wordmark and retains the established component coverage.
    source = source.resize((160, 90), Image.Resampling.LANCZOS).convert("RGBA")
    alpha = source.getchannel("A")
    rgb = ImageEnhance.Color(source.convert("RGB")).enhance(1.65)
    rgb = ImageEnhance.Contrast(rgb).enhance(1.12)
    vivid = rgb.convert("RGBA")
    vivid.putalpha(alpha)

    # A narrow accent bloom sits behind only the non-transparent artwork. It
    # stays transparent outside the mark so the launcher's card still owns the
    # background colour.
    glow = alpha.filter(ImageFilter.GaussianBlur(1.8)).point(
        lambda p: min(105, round(p * .42)), mode="L")
    glow_layer = solid_layer(accent, glow)
    canvas = Image.new("RGBA", (160, 90), (0, 0, 0, 0))
    canvas.alpha_composite(glow_layer)
    canvas.alpha_composite(vivid)
    return canvas.resize((BANNER_W, BANNER_H), Image.Resampling.NEAREST)


def write(path: Path, data: str | bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, bytes):
        path.write_bytes(data)
    else:
        path.write_text(data, encoding="utf-8")


def expand(component: str) -> list[str]:
    pkg, _, activity = component.partition("/")
    if not activity:
        return [component]
    if activity.startswith("."):
        return [f"{pkg}/{pkg}{activity}", component]
    if activity.startswith(pkg + "."):
        return [component, f"{pkg}/{activity[len(pkg):]}"]
    return [component]


def appfilter(icons: list[dict]) -> tuple[str, int, int]:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/build_pixel_neon.py. Do not edit by hand. -->',
        '<resources>',
        '    <!-- The pack maps its own launcher tile as well as catalog apps. -->',
        '    <item component="ComponentInfo{tv.corebuilds.pixelneon/tv.corebuilds.pixelneon.MainActivity}" drawable="corebuilds_banner"/>',
    ]
    source_components = sum(len(i["components"]) for i in icons)
    seen: set[str] = set()
    emitted = 1
    for icon in icons:
        lines.append(f'    <!-- {esc(icon["name"])} -->')
        for component in icon["components"]:
            for variant in expand(component):
                if variant in seen:
                    continue
                seen.add(variant)
                lines.append(
                    f'    <item component="ComponentInfo{{{esc(variant)}}}" '
                    f'drawable="{icon["drawable"]}_banner"/>')
                emitted += 1
    lines.append('</resources>')
    return "\n".join(lines) + "\n", source_components, emitted


def drawable_xml(icons: list[dict]) -> str:
    labels = {
        "STREAM": "Streaming", "MEDIA": "Media centres", "VOD": "On demand",
        "LIVE": "Live TV", "PLAYER": "Players", "VIDEO": "Video",
        "MUSIC": "Music", "SPORT": "Sport", "GAMING": "Gaming",
        "DEBRID": "Debrid", "FILES": "Files", "TOOL": "Tools",
        "STORE": "Stores", "LAUNCHER": "Launchers", "VPN": "VPN",
        "BROWSER": "Browsers", "REMOTE": "Remote", "SYSTEM": "System",
        "TRACK": "Tracking", "CORE": "Core Builds", "APP": "Apps",
    }
    preferred = ["CORE", "STREAM", "MEDIA", "VOD", "LIVE", "PLAYER", "VIDEO",
                 "MUSIC", "SPORT", "GAMING", "DEBRID", "FILES", "TOOL",
                 "STORE", "LAUNCHER", "VPN", "BROWSER", "REMOTE", "SYSTEM",
                 "TRACK", "APP"]
    by_category: dict[str, list[dict]] = {}
    for icon in icons:
        by_category.setdefault(icon.get("category") or "APP", []).append(icon)
    categories = [c for c in preferred if c in by_category]
    categories += [c for c in by_category if c not in categories]
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/build_pixel_neon.py. Do not edit by hand. -->',
        '<resources>',
    ]
    for prefix in ("Banner", "Square"):
        suffix = "_banner" if prefix == "Banner" else ""
        for category in categories:
            lines.append(f'    <category title="{prefix} · {esc(labels.get(category, category.title()))}" />')
            for icon in by_category[category]:
                lines.append(f'    <item drawable="{icon["drawable"]}{suffix}" />')
    lines.append('</resources>')
    return "\n".join(lines) + "\n"


def values_xml(icons: list[dict]) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/build_pixel_neon.py. Do not edit by hand. -->',
        '<resources>',
        '    <string-array name="icon_pack">',
    ]
    lines += [f'        <item>{esc(i["drawable"])}</item>' for i in icons]
    lines += ['    </string-array>', '    <string-array name="icon_names">']
    lines += [f'        <item>{esc(i["name"])}</item>' for i in icons]
    lines += ['    </string-array>', '    <string-array name="icon_categories">']
    lines += [f'        <item>{esc(i.get("category") or "APP")}</item>' for i in icons]
    lines += ['    </string-array>', f'    <integer name="icon_count">{len(icons)}</integer>', '</resources>']
    return "\n".join(lines) + "\n"


def docs_list(icons: list[dict], source_components: int) -> str:
    lines = [
        "# Core Builds Pixel Neon · supported applications", "",
        f"`{len(icons)}` pixel-neon icons · `{source_components}` catalog components · pack v0.1.0", "",
        "This is the alternate 8-bit neon treatment of the Core Builds catalog. "
        "Mappings and coverage are shared with the original pack; only the art changes.", "",
        "| App | Drawable | Neon source accent | Components |", "| --- | --- | --- | --- |",
    ]
    for icon in icons:
        comps = "<br>".join(f"`{c}`" for c in icon["components"])
        lines.append(f'| {icon["name"]} | `{icon["drawable"]}` | `{icon["color"]}` | {comps} |')
    return "\n".join(lines) + "\n"


def preview(icons: list[dict], rendered: dict[str, "Image.Image"], source_components: int):
    """Make a compact review sheet instead of a 17,000px-tall all-icons wall."""
    from PIL import Image, ImageDraw, ImageFont

    sample = icons[:48]
    cols, cell, top = 8, 150, 112
    rows = (len(sample) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, top + rows * cell), color_tuple(VOID) + (255,))
    draw = ImageDraw.Draw(sheet)
    try:
        title_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf", 28)
        sub_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 14)
        label_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 12)
    except OSError:
        title_font = sub_font = label_font = ImageFont.load_default()
    draw.text((24, 18), "CORE BUILDS / PIXEL NEON", fill=INK, font=title_font)
    draw.text((24, 57), f"{len(icons)} icons · {source_components} catalog components · 64px arcade grid", fill="#9AA8C7", font=sub_font)
    draw.text((24, 78), "cyan bloom · hard pixels · transparent background", fill="#00E5FF", font=sub_font)
    for n, icon in enumerate(sample):
        x, y = (n % cols) * cell, top + (n // cols) * cell
        draw.rounded_rectangle((x + 8, y + 5, x + cell - 8, y + cell - 28), radius=12,
                               fill="#10142A", outline="#252A4D", width=2)
        mark = rendered[icon["drawable"]].resize((92, 92), Image.Resampling.NEAREST)
        sheet.alpha_composite(mark, (x + (cell - 92) // 2, y + 10))
        label = icon["name"][:18]
        bbox = draw.textbbox((0, 0), label, font=label_font)
        draw.text((x + (cell - (bbox[2] - bbox[0])) / 2, y + cell - 20), label,
                  fill="#9AA8C7", font=label_font)
    return sheet.convert("RGB")


def brand_assets(core_icon, core_mask, svg2png) -> None:
    from PIL import Image, ImageDraw, ImageFont

    res = OUT / "app" / "src" / "main" / "res"
    mipmap_icon = core_icon.resize((512, 512), Image.Resampling.NEAREST)
    # A dark arcade cabinet tile is appropriate for the launcher icon; the
    # per-app icons themselves remain transparent.
    cabinet = Image.new("RGBA", (512, 512), color_tuple(VOID) + (255,))
    cabinet.alpha_composite(mipmap_icon)
    # Subtle scanline only on the cabinet background, kept faint at TV size.
    drawer = ImageDraw.Draw(cabinet)
    for y in range(8, 512, 16):
        drawer.line((0, y, 512, y), fill=(0, 229, 255, 12), width=2)
    for folder, size in (("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144)):
        write(res / folder / "ic_launcher.png", _png_bytes(cabinet.resize((size, size), Image.Resampling.LANCZOS)))

    # Adaptive foreground: transparent neon mark, safe inside OEM masks.
    fg = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    mark = core_icon.resize((318, 318), Image.Resampling.NEAREST)
    fg.alpha_composite(mark, ((512 - 318) // 2, (512 - 318) // 2))
    for folder, size in (("mipmap-xhdpi", 216), ("mipmap-xxhdpi", 324)):
        write(res / folder / "ic_launcher_foreground.png", _png_bytes(fg.resize((size, size), Image.Resampling.NEAREST)))

    adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/cb_night" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
'''
    write(res / "mipmap-anydpi-v26" / "ic_launcher.xml", adaptive)
    write(res / "mipmap-anydpi-v26" / "ic_launcher_round.xml", adaptive)

    # Leanback banner is composed at a small resolution then doubled, keeping
    # its label as the same pixel type as the icons.
    small = Image.new("RGBA", (160, 90), color_tuple(VOID) + (255,))
    small.alpha_composite(core_icon.resize((68, 68), Image.Resampling.NEAREST), (12, 11))
    try:
        bold = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf", 9)
        mono = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 7)
    except OSError:
        bold = mono = ImageFont.load_default()
    d = ImageDraw.Draw(small)
    d.text((88, 28), "CORE BUILDS", font=bold, fill=INK)
    d.text((88, 41), "PIXEL NEON", font=bold, fill="#00E5FF")
    d.text((88, 56), "ICON PACK / ATV", font=mono, fill="#9AA8C7")
    banner = small.resize((640, 360), Image.Resampling.NEAREST)
    write(res / "drawable-nodpi" / "cb_banner.png", _png_bytes(banner))


def _png_bytes(image) -> bytes:
    stream = io.BytesIO()
    image.save(stream, format="PNG", optimize=False)
    return stream.getvalue()


def main() -> int:
    from PIL import Image

    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    svg2png = load_renderer()
    for path in (PNG_DIR, XML_DIR, ASSETS_DIR, VAL_DIR, DOC_DIR):
        path.mkdir(parents=True, exist_ok=True)

    rendered: dict[str, Image.Image] = {}
    masks: dict[str, object] = {}
    for ordinal, icon in enumerate(icons):
        base = BASE_SVG / f"{icon['drawable']}.svg"
        if not base.exists():
            raise SystemExit(f"missing {base}; run python tools/build_icons.py first")
        source = source_png(base, PIXEL_GRID, svg2png)
        accent = neon_color(icon["color"], ordinal)
        image, mask = pixel_icon(source, accent)
        rendered[icon["drawable"]] = image
        masks[icon["drawable"]] = mask
        write(PNG_DIR / f"{icon['drawable']}.png", _png_bytes(image))

        base_banner = BASE_BANNERS / f"{icon['drawable']}.svg"
        if not base_banner.exists():
            raise SystemExit(f"missing {base_banner}; run python tools/build_banners.py first")
        banner_source = source_raster(base_banner, 320, 180, svg2png)
        banner = pixel_banner(banner_source, accent)
        write(PNG_DIR / f"{icon['drawable']}_banner.png", _png_bytes(banner))

    filter_text, source_components, emitted = appfilter(icons)
    write(XML_DIR / "appfilter.xml", filter_text)
    write(ASSETS_DIR / "appfilter.xml", filter_text)
    draw_text = drawable_xml(icons)
    write(XML_DIR / "drawable.xml", draw_text)
    write(ASSETS_DIR / "drawable.xml", draw_text)
    write(XML_DIR / "iconpack.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<iconpack>\n" +
          "\n".join(f'    <item drawable="{i["drawable"]}" />' for i in icons) +
          "\n</iconpack>\n")
    write(VAL_DIR / "icon_pack.xml", values_xml(icons))
    write(DOC_DIR / "IconPackList.md", docs_list(icons, source_components))

    # Brand the app with the catalog's own core mark treatment.
    brand_assets(rendered["corebuilds"], masks["corebuilds"], svg2png)
    sheet = preview(icons, rendered, source_components)
    write(DOC_DIR / "preview.png", _png_bytes(sheet))

    # A small machine-readable receipt keeps manual reviews honest.
    receipt = {
        "pack": "Core Builds Pixel Neon",
        "sourceCatalog": "tools/catalog.json",
        "version": "0.1.0",
        "icons": len(icons),
        "catalogComponents": source_components,
        "appfilterEntries": emitted,
        "pixelGrid": PIXEL_GRID,
        "generatedBy": "tools/build_pixel_neon.py",
    }
    write(DOC_DIR / "build-receipt.json", json.dumps(receipt, indent=2) + "\n")
    print(f"Pixel Neon complete — {len(icons)} icons, {source_components} catalog components, "
          f"{emitted} appfilter entries, {PIXEL_GRID}px source grid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
