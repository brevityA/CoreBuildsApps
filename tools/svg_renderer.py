"""Deterministic SVG rasterizer adapter.

resvg-py ships self-contained native wheels, so local builds and CI render the
same bytes without relying on whichever libcairo happens to be installed.
CairoSVG remains a compatibility fallback for unsupported platforms.
"""
from pathlib import Path


def svg2png(*, url=None, bytestring=None, write_to=None, output_width=None,
            output_height=None, background_color=None):
    try:
        from resvg_py import svg_to_bytes

        if bytestring is not None:
            svg = (bytestring.decode("utf-8") if isinstance(bytestring, bytes)
                   else bytestring)
            png = svg_to_bytes(svg_string=svg, width=output_width,
                               height=output_height, background=background_color)
        else:
            png = svg_to_bytes(svg_path=str(url), width=output_width,
                               height=output_height, background=background_color,
                               resources_dir=str(Path(url).resolve().parent))
        if write_to is not None:
            Path(write_to).write_bytes(png)
            return None
        return png
    except (ImportError, OSError):
        import cairosvg
        return cairosvg.svg2png(
            url=url, bytestring=bytestring, write_to=write_to,
            output_width=output_width, output_height=output_height,
            background_color=background_color)
