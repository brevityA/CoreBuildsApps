#!/usr/bin/env python3
"""Offline regressions for Core Builds style, colour parity and APK evidence.

Run after the generators: python tests/test_icon_identity.py
No downloaded APK, device, network, or optional APK parser is needed.
"""
from __future__ import annotations

import copy
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import brandmarks
from build_banners import render, render_glyph_only
from build_icons import validate
from drawable_art import art_path, read_aliases
from glyphs import GLYPHS, monoline, render_svg, secondary_color, secondary_errors
from icon_style import (CARD, CORE_MONOLINE, CORE_STROKES, LIGHT_INK, MIN_CONTRAST,
                        core_monoline_errors, contrast, display_accent)
from inspect_icon_apk import activity_name, launcher_components, resource_path
from svg_renderer import svg2png
from PIL import Image
from fontTools.pens.boundsPen import BoundsPen
from fontTools.svgLib.path import parse_path

CATALOG = json.loads((ROOT / "tools/catalog.json").read_text())
ICONS = CATALOG["icons"]
NODPI = ROOT / "app/src/main/res/drawable-nodpi"
ALIASES = read_aliases(ROOT / "app/src/main/res/values")
BY_ID = {i["drawable"]: i for i in ICONS}
NS = "http://schemas.android.com/apk/res/android"


class IdentityTests(unittest.TestCase):
    def test_catalog_accepts_every_icon(self):
        self.assertEqual(validate(ICONS, CATALOG["artwork"]), [])

    def test_same_brand_must_not_drift_in_colour_or_glyph(self):
        for field, wrong in (("color", "#FF0000"), ("glyph", "tile_N")):
            rows = copy.deepcopy([BY_ID["ninenow"], BY_ID["ninenow_2"]])
            rows[1][field] = wrong
            self.assertTrue(any("inconsistent glyph/colour" in e for e in validate(rows)))

    def test_known_variants_are_in_the_same_identity_group(self):
        groups = [
            ("iplayer", "bbc_iplayer", "bbciplayer"),
            ("ninenow", "ninenow_2"),
            ("sevenplus", "seven_plus"),
            ("adguard", "adguard_2"),
            ("discovery", "discoveryplus"),
            ("syncler", "syncler_2", "syncler_beta"),
            ("weyd", "weyd_2"),
            ("smarttube", "smarttubenext"),
            ("ott", "cbs", "tve"),
            ("tvquickactions", "tv_quick_actions"),
            ("intigral", "jawwy_tv"),
            ("ertflix", "ertflix_2"),
        ]
        for group in groups:
            with self.subTest(group=group):
                rows = [BY_ID[d] for d in group]
                self.assertEqual(len({r["brand"] for r in rows}), 1)
                self.assertEqual(len({(r["glyph"], r["color"]) for r in rows}), 1)

    def test_unrelated_homonyms_keep_distinct_marks(self):
        """Same display name, different products → different (glyph, colour).
        Same-product package variants belong in a brand group instead."""
        from collections import defaultdict
        by_name = defaultdict(list)
        for icon in ICONS:
            by_name[icon["name"].casefold()].append(icon)
        for name, rows in by_name.items():
            brands = {r.get("brand") for r in rows}
            if len(brands) == 1 and None not in brands:
                continue
            marks = {(r["glyph"], r["color"]) for r in rows}
            self.assertEqual(len(marks), len(rows), name)

    def test_dig_is_not_labelled_daijishou(self):
        self.assertEqual(BY_ID["digdroid"]["name"], "DIG")
        self.assertEqual(BY_ID["magneticchen"]["name"], "Daijishou")
        self.assertNotEqual(BY_ID["digdroid"]["glyph"], BY_ID["magneticchen"]["glyph"])

    def test_wholphin_is_not_damontes_letter_tile(self):
        self.assertEqual(BY_ID["damontecres_2"]["name"], "Wholphin")
        self.assertEqual(BY_ID["damontecres_2"]["glyph"], "wholphin_arc")
        self.assertNotEqual(BY_ID["damontecres"]["glyph"], "wholphin_arc")
        self.assertEqual(BY_ID["damontecres_2"].get("style"), CORE_MONOLINE)

    def test_yettel_selfcare_is_not_yettel_tv(self):
        self.assertEqual(BY_ID["selfcare"]["name"], "Yettel Selfcare")
        self.assertEqual(BY_ID["yettel_tv"]["name"], "Yettel TV")

    def test_tanasi_streamflix_is_not_the_reborn_f(self):
        self.assertEqual(BY_ID["streamflix"]["glyph"], "flix_f")
        self.assertEqual(BY_ID["streamflix_2"]["glyph"], "stream_window")
        self.assertNotEqual(BY_ID["streamflix"]["color"], BY_ID["streamflix_2"]["color"])

    def test_redraw_tranche_uses_source_cues_without_mapping_changes(self):
        expected = {
            "airscreen": ("airscreen_as", "com.ionitech.airscreen/com.ionitech.airscreen.ui.activity.welcome.StreamAssistantIndexActivity"),
            "aerialviews": ("aerial_views_sun_dunes", "com.neilturner.aerialviews/com.neilturner.aerialviews.ui.MainActivity"),
            "anydeskandroid": ("anydesk_chevrons", "com.anydesk.anydeskandroid/com.anydesk.anydeskandroid.gui.activity.HubActivity"),
            "dw": ("dw_circles", "dw.com.androidtv.live/com.dw.app.dwforsmarttv.MainActivity"),
        }
        for drawable, (glyph, component) in expected.items():
            with self.subTest(drawable=drawable):
                self.assertEqual(BY_ID[drawable]["glyph"], glyph)
                self.assertIn(component, BY_ID[drawable]["components"])
                self.assertIn(glyph, GLYPHS)

    def test_reference_accents_are_not_random_palette_colours(self):
        expected = {"spotify": "#1ED760", "crunchyroid": "#FF5E00",
                    "netflix": "#E50914", "kodi": "#17B2E7",
                    "deezer": "#A238FF", "stremio": "#685CEE",
                    "iplayer": "#FF4C98", "bbc_iplayer": "#FF4C98",
                    "bbciplayer": "#FF4C98"}
        for name, colour in expected.items():
            self.assertEqual(BY_ID[name]["color"], colour, name)

    def test_every_classic_accent_is_legible_and_idempotent(self):
        for icon in ICONS:
            with self.subTest(icon=icon["name"]):
                colour = display_accent(icon["color"])
                self.assertGreaterEqual(contrast(colour, CARD), MIN_CONTRAST)
                self.assertEqual(display_accent(colour.lower()), colour)

    def test_source_accents_remain_unchanged_for_sibling_packs(self):
        before = copy.deepcopy(ICONS)
        self.assertEqual(display_accent("#000000"), LIGHT_INK)
        self.assertEqual(BY_ID["mubi"]["color"], "#000000")
        for icon in ICONS:
            display_accent(icon["color"])
        self.assertEqual(ICONS, before)

    def test_square_banner_and_mark_only_share_colour_policy(self):
        for icon in (BY_ID["mubi"], BY_ID["spotify"], BY_ID["nobuffr"]):
            mono = icon.get("color_note") == "monochrome"
            colour = display_accent(icon["color"], monochrome=mono)
            for svg in (render_svg(icon["glyph"], icon["color"], monochrome=mono),
                        render(icon["name"], icon["glyph"], icon["color"], monochrome=mono),
                        render_glyph_only(icon["glyph"], icon["color"], monochrome=mono)):
                self.assertIn(f'stroke="{colour}"', svg)

    def test_banner_glyph_override_carries_a_real_banner_mark(self):
        """banner_glyph (TizenTube) splits the square from the 16:9 card:
        the committed banner must be that mark, and the square keeps the
        icon glyph."""
        from build_banners import recentre
        for icon in ICONS:
            bg = icon.get("banner_glyph")
            if not bg:
                continue
            with self.subTest(icon=icon["name"]):
                self.assertIn(bg, GLYPHS, icon["name"])
                self.assertNotEqual(bg, icon["glyph"], icon["name"])
                expected = recentre(render(icon["name"], bg, icon["color"],
                                            icon.get("category", ""),
                                            gradient=icon.get("gradient"),
                                            mark=icon.get("mark"),
                                            style=icon.get("mark_style")))
                self.assertEqual(
                    (ROOT / "assets/banners" / f"{icon['drawable']}.svg").read_text(),
                    expected, icon["name"])
                self.assertEqual(
                    (ROOT / "assets/svg" / f"{icon['drawable']}.svg").read_text(),
                    render_svg(icon["glyph"], icon["color"],
                               gradient=icon.get("gradient"),
                               mark=icon.get("mark"),
                               style=icon.get("mark_style")), icon["name"])

    def test_adaptive_marks_fit_their_shell_and_counter_floor(self):
        """The adaptive font keeps its promise: marks only ever set on
        category monograms, inside that shell's own type budget, at or above
        the cap floor where a filled counter survives a 48dp tile. A styled
        mark is measured as it actually renders (lower, not its cap-token)."""
        from glyphs import family_glyph_for
        from typeface import MIN_LOCKUP_CAP, lockup_cap
        marked = 0
        for icon in ICONS:
            mark = icon.get("mark")
            if not mark:
                continue
            marked += 1
            with self.subTest(icon=icon["name"]):
                budget = family_glyph_for(icon["glyph"])
                self.assertIsNotNone(budget,
                                     f"mark '{mark}' sits on {icon['glyph']}")
                _, cap_h, _, max_w = budget
                shown = mark.lower() if icon.get("mark_style") == "lower" else mark
                self.assertGreaterEqual(lockup_cap(shown, cap_h, max_w),
                                        MIN_LOCKUP_CAP)
        self.assertGreater(
            marked, 0,
            "no adaptive wordmark monograms — the fallback voice reverted "
            "to one letter for every app")

    def test_mark_styles_are_shipped_researched_and_styled_as_rendered(self):
        """A mark_style is tranche work: it must name a shipped treatment,
        carry its research note, and only ever sit on a styled mark."""
        from build_icons import MARK_STYLES
        styled = 0
        for icon in ICONS:
            style = icon.get("mark_style")
            if style is None:
                continue
            styled += 1
            with self.subTest(icon=icon["name"]):
                self.assertIn(style, MARK_STYLES, icon["name"])
                self.assertTrue(icon.get("mark"), f"{icon['name']}: style without mark")
                self.assertTrue(icon.get("mark_style_source"),
                                f"{icon['name']}: '{style}' cue has no source note")
        self.assertGreater(
            styled, 0,
            "mark_style vocabulary is loaded but no icon ships a researched "
            "treatment — the tranche regressed")

    def test_tizentube_runs_the_measured_field_gradient(self):
        """The owner wants the real logo's ramp: field cyan to pale blue,
        painted at render time (the Nuvio pattern)."""
        square = (ROOT / "assets/svg" / "tizentube.svg").read_text()
        for stop in ("#47DDFF", "#C5E9FF"):
            self.assertIn(stop, square)
        self.assertIn("linearGradient", square)
        self.assertIn('stroke="url(#cbGrad)"', square)
        banner = (ROOT / "assets/banners" / "tizentube.svg").read_text()
        self.assertIn('fill="url(#cbGrad)"', banner)  # the banner dot rides the ramp

    def test_committed_square_vectors_match_the_generator(self):
        for icon in ICONS:
            path = ROOT / "assets/svg" / f"{icon['drawable']}.svg"
            mono = icon.get("color_note") == "monochrome"
            self.assertEqual(path.read_text(),
                             render_svg(icon["glyph"], icon["color"],
                                        monochrome=mono,
                                        gradient=icon.get("gradient"),
                                        mark=icon.get("mark"),
                                        style=icon.get("mark_style"),
                                        secondary=icon.get("secondary")),
                             icon["name"])

    def test_nuvio_square_runs_the_brand_gradient(self):
        """The owner asked for the real gradient, not a flat stand-in."""
        body = (ROOT / "assets/svg" / "nuvio.svg").read_text()
        self.assertIn("linearGradient", body)
        self.assertIn("#2FCCE6", body)
        self.assertIn("#A238F0", body)
        self.assertIn('stroke="url(#cbGrad)"', body)

    def test_nuvio_inner_play_is_centered_on_the_mark(self):
        body = (ROOT / "assets/svg" / "nuvio.svg").read_text()
        self.assertIn('M 212 208 L 304 256 L 212 304 Z', body)
        self.assertNotIn('M 232 208 L 324 256 L 232 304 Z', body)

    def test_no_icon_uses_a_wordmark_glyph(self):
        """Icons carry glyphs; wordmarks are banner business only."""
        for icon in ICONS:
            with self.subTest(icon=icon["name"]):
                self.assertNotIn("wordmark", icon["glyph"])

    def test_no_night_coloured_fake_holes_in_generated_squares(self):
        for icon in ICONS:
            mono = icon.get("color_note") == "monochrome"
            body = render_svg(icon["glyph"], icon["color"], monochrome=mono).lower()
            self.assertNotIn('fill="#0d1117"', body, icon["name"])

    def test_youtube_play_counter_is_really_transparent(self):
        image = Image.open(art_path(NODPI, "youtube", ALIASES)).convert("RGBA")
        self.assertEqual(image.getpixel((256, 256))[3], 0)
        self.assertEqual(image.getpixel((64, 256))[:3], (255, 0, 0))
        self.assertEqual(image.getpixel((0, 0))[3], 0)
        self.assertEqual(image.getpixel((100, 256))[3], 0)  # no solid button fill
        for background in ("#334155", "#7C3AED"):
            card = Image.new("RGBA", image.size, background)
            composited = Image.alpha_composite(card, image)
            self.assertEqual(composited.getpixel((256, 256)), card.getpixel((256, 256)))

    def test_no_glyph_puts_ink_outside_the_safe_area(self):
        """SAFE=432 was a constant nothing enforced.

        The glyphs place coordinates, not ink, so a path drawn to the safe
        edge still hangs its stroke half-width past it. Seven did: retroarch
        reached x=509 on a 512 grid, 3px of margin where SAFE promises 40.
        Harmless while the pack ships unmasked, and clipped the moment a
        launcher applies an iconmask. Measured on the rendered alpha, which
        is the only thing that knows where the stroke actually lands.
        """
        from glyphs import GRID, SAFE

        pad = (GRID - SAFE) / 2
        for name in sorted(GLYPHS):
            png = svg2png(bytestring=render_svg(name, "#00d4ff").encode(),
                          output_width=GRID, output_height=GRID)
            bbox = Image.open(io.BytesIO(png)).convert("RGBA").getchannel("A").getbbox()
            self.assertIsNotNone(bbox, f"{name}: renders no ink")
            x0, y0, x1, y1 = bbox
            margin = min(x0, y0, GRID - x1, GRID - y1)
            with self.subTest(glyph=name):
                self.assertGreaterEqual(
                    margin, pad,
                    f"{name}: ink {x1 - x0}x{y1 - y0} at {bbox} leaves "
                    f"{margin}px of margin, SAFE={SAFE} requires {pad:.0f}")

    def test_equalizer_centres_do_not_depend_on_card_background(self):
        png = svg2png(bytestring=render_svg("equalizer", "#FF6D00").encode(), output_width=512, output_height=512)
        alpha = Image.open(io.BytesIO(png)).convert("RGBA").getchannel("A")
        for x, y in ((130, 188), (256, 310), (382, 232)):
            self.assertEqual(alpha.getpixel((x, y)), 0)

    def test_mubi_has_seven_separate_dots_in_two_three_two_rows(self):
        im = Image.open(art_path(NODPI, "mubi", ALIASES)).convert("RGBA")
        ink = {(x, y) for y in range(im.height) for x in range(im.width)
               if im.getpixel((x, y))[3] > 128}
        components = []
        while ink:
            start = ink.pop()
            todo, points = [start], [start]
            while todo:
                x, y = todo.pop()
                for q in ((x-1, y), (x+1, y), (x, y-1), (x, y+1)):
                    if q in ink:
                        ink.remove(q)
                        todo.append(q)
                        points.append(q)
            components.append(round(sum(y for _, y in points) / len(points)))
        self.assertEqual(len(components), 7)
        rows = []
        for y in sorted(components):
            # Subpixel curve bounds can put two nominally aligned centres one
            # raster pixel apart; they are still the same visual row.
            if rows and y - rows[-1][0] <= 2:
                rows[-1][1] += 1
            else:
                rows.append([y, 1])
        self.assertEqual([count for _, count in rows], [2, 3, 2])


class CoreStyleTests(unittest.TestCase):
    def revised(self):
        return [i for i in ICONS if i.get("style") == CORE_MONOLINE]

    def test_every_reviewed_brand_is_opted_into_core_style(self):
        for icon in ICONS:
            if icon["glyph"] in CATALOG["artwork"] or icon["glyph"] == "iplayer_play":
                self.assertEqual(icon.get("style"), CORE_MONOLINE, icon["name"])

    def test_all_revised_glyphs_obey_the_same_rounded_line_contract(self):
        for icon in self.revised():
            with self.subTest(icon=icon["name"]):
                accent = display_accent(icon["color"])
                body = monoline(GLYPHS[icon["glyph"]](accent))
                self.assertEqual(core_monoline_errors(
                    body, accent, gradient=bool(icon.get("gradient")),
                    ink=icon.get("ink")), [])

    def test_subordinate_stroke_weights_survive_normalisation(self):
        body = '<path stroke-width="40"/><path stroke-width="26"/><path stroke-width="20"/>'
        root = ET.fromstring(f"<g>{monoline(body)}</g>")
        self.assertEqual([float(p.get("stroke-width")) for p in root], [32.0, 26.2, 21.8])

    def test_style_gate_rejects_fills_white_wordmarks_square_caps_and_rescaling(self):
        accent = "#56C8F0"
        valid = monoline(GLYPHS["nobuffr_mark"](accent))
        wrong = (
            valid.replace('fill="none"', f'fill="{accent}"', 1),
            valid.replace(f'stroke="{accent}"', 'stroke="#FFFFFF"', 1),
            valid.replace('stroke-width="32.0"', 'stroke-width="80"', 1),
            valid.replace('stroke-linecap="round"', 'stroke-linecap="square"', 1),
            f'<g transform="scale(2)">{valid}</g>',
            valid.replace('<path ', '<path style="fill:white" ', 1),
            '<image href="vendor-wordmark.png"/>',
        )
        for body in wrong:
            self.assertTrue(core_monoline_errors(body, accent), body[:160])

    def test_catalog_rejects_a_private_wordmark_only_banner(self):
        icon = copy.deepcopy(BY_ID["nobuffr"])
        icon["banner_style"] = "glyph"
        self.assertTrue(any("standard Outfit" in e for e in validate([icon])))

    def test_catalog_rejects_direct_rendering_of_reference_art(self):
        refs = copy.deepcopy(CATALOG["artwork"])
        refs["nobuffr_mark"]["usage"] = "render"
        self.assertTrue(any("reference-only" in e for e in validate(ICONS, refs)))

    def test_shipped_revised_vectors_have_not_bypassed_core_style(self):
        for icon in self.revised():
            svg = ET.parse(ROOT / "assets/svg" / f"{icon['drawable']}.svg").getroot()
            body = "".join(ET.tostring(node, encoding="unicode") for node in svg)
            self.assertEqual(core_monoline_errors(
                body, display_accent(icon["color"]),
                gradient=bool(icon.get("gradient")), ink=icon.get("ink"),
                secondary=secondary_color(icon)), [], icon["name"])

    def test_standard_banner_recipe_is_used_for_every_revised_app(self):
        from build_banners import recentre
        for icon in self.revised():
            actual = (ROOT / "assets/banners" / f"{icon['drawable']}.svg").read_text()
            expected = recentre(render(icon["name"], icon["glyph"], icon["color"],
                                       icon.get("category", ""),
                                       gradient=icon.get("gradient"),
                                       secondary=icon.get("secondary")))
            self.assertEqual(actual, expected, icon["name"])
            self.assertIn('id="cbRail"', actual)
            self.assertIn('fill="#E6EDF3"', actual)  # common Outfit label, not vendor type

    def test_revised_rasters_are_open_ink_and_clear_the_shared_safe_area(self):
        for icon in self.revised():
            with self.subTest(icon=icon["name"]):
                alpha = Image.open(art_path(NODPI, icon["drawable"], ALIASES)).convert("RGBA").getchannel("A")
                binary = alpha.point(lambda p: 255 if p >= 128 else 0)
                left, top, right, bottom = binary.getbbox()
                # Presence adds a ring outside the vector SAFE pad. Vectors
                # still have to clear 40px; committed rasters may bleed to 24.
                self.assertGreaterEqual(min(left, top), 24)
                self.assertLessEqual(max(right, bottom), 488)
                self.assertGreaterEqual(max(right-left, bottom-top), 320)
                coverage = binary.histogram()[255] / (512 * 512)
                self.assertLess(coverage, 0.40)  # ring + bloom, still not a slab
                self.assertGreater(coverage, 0.025)


class SourceTests(unittest.TestCase):
    def test_every_source_has_provenance_and_is_used(self):
        used = {i["glyph"] for i in ICONS}
        for name, spec in CATALOG["artwork"].items():
            self.assertIn(name, used)
            self.assertIn(name, GLYPHS)
            self.assertTrue(spec["source"].startswith("https://"))
            self.assertTrue(spec["license"])
            self.assertTrue(spec["treatment"])
            self.assertEqual(spec["usage"], "reference-only")
            self.assertEqual(GLYPHS[name].__module__, "glyphs")
            self.assertRegex(spec["reviewed"], r"^\d{4}-\d{2}-\d{2}$")
            self.assertTrue(brandmarks.load_source(spec))  # includes source hash check

    def test_reference_geometry_is_auditable_but_not_registered_as_live_art(self):
        for name, spec in CATALOG["artwork"].items():
            with self.subTest(glyph=name):
                pen = BoundsPen(None)
                for d, _, _ in brandmarks.load_source(spec):
                    parse_path(d, pen)
                l, t, r, b = pen.bounds
                self.assertAlmostEqual((l+r)/2, 256, places=3)
                self.assertAlmostEqual((t+b)/2, 256, places=3)
                self.assertAlmostEqual(max(r-l, b-t), 432, places=3)
                body = GLYPHS[name]("#FFFFFF")
                self.assertNotIn("transform=", body)
                self.assertNotIn("<text", body)
                self.assertNotIn("<image", body)

    def source_fixture(self, raw: bytes):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        directory = root / "tools/brandmarks"
        directory.mkdir(parents=True)
        (directory / "test.svg").write_bytes(raw)
        spec = {"file": "tools/brandmarks/test.svg", "sha256": hashlib.sha256(raw).hexdigest()}
        return root, directory, spec

    def test_checksum_changes_are_rejected(self):
        root, directory, spec = self.source_fixture(b'<svg><path d="M0 0H24V24H0Z"/></svg>')
        spec["sha256"] = "0" * 64
        with patch.object(brandmarks, "ROOT", root), patch.object(brandmarks, "SOURCE_DIR", directory):
            with self.assertRaisesRegex(ValueError, "checksum"):
                brandmarks.load_source(spec)

    def test_external_content_and_executable_svg_are_rejected(self):
        for content in ('<image href="https://example.org/icon.png"/>', '<script>alert(1)</script>',
                        '<path d="M0 0H24V24Z" fill="url(https://example.org/paint)"/>',
                        '<path d="M0 0H24V24Z" transform="scale(4)"/>'):
            root, directory, spec = self.source_fixture(f'<svg>{content}</svg>'.encode())
            with patch.object(brandmarks, "ROOT", root), patch.object(brandmarks, "SOURCE_DIR", directory):
                with self.assertRaises(ValueError):
                    brandmarks.load_source(spec)

    def test_source_paths_cannot_escape_source_directory(self):
        spec = {"file": "tools/../catalog.json", "sha256": "0" * 64}
        with self.assertRaisesRegex(ValueError, "outside"):
            brandmarks.load_source(spec)

    def test_nobuffr_receipt_and_resources_are_intact(self):
        directory = ROOT / "tools/reference/nobuffr"
        receipt = json.loads((directory / "receipt.json").read_text())
        self.assertEqual(receipt["source_url"], "https://downloads.nobuffr.com/android/nobuffr.apk")
        self.assertEqual(receipt["package"], "com.nobuffr.app")
        self.assertEqual(receipt["apk_sha256"], "ec835a672087b5cb56700ddc3ed4e050519f5829d10e86800d5506d79afda5bf")
        self.assertEqual(receipt["version_code"], 210246)
        self.assertEqual(BY_ID["nobuffr"]["components"], [r["component"] for r in receipt["launchers"]])
        self.assertEqual(set(receipt["launchers"][0]["categories"]),
                         {"android.intent.category.LAUNCHER", "android.intent.category.LEANBACK_LAUNCHER"})
        for item in receipt["resources"]:
            self.assertEqual(hashlib.sha256((directory / item["path"]).read_bytes()).hexdigest(), item["export_sha256"])
        self.assertEqual(list(directory.rglob("*.apk")), [])

    def test_nobuffr_uses_the_observed_cue_in_one_colour_linework(self):
        icon = BY_ID["nobuffr"]
        self.assertEqual(icon["style"], CORE_MONOLINE)
        self.assertNotEqual(icon.get("banner_style"), "glyph")
        accent = display_accent(icon["color"])
        body = monoline(GLYPHS[icon["glyph"]](accent))
        self.assertEqual(core_monoline_errors(body, accent), [])
        self.assertNotIn("#FFFFFF", body.upper())
        root = ET.fromstring(f"<g>{body}</g>")
        self.assertEqual(len(root.findall("ellipse")), 1)  # the lowercase o
        self.assertEqual(len(root.findall("path")), 3)  # n, three ticks, long underline
        self.assertNotEqual(body, monoline(GLYPHS["tile_N"](accent)))
        self.assertFalse(hasattr(brandmarks, "catalog_glyphs"))  # no vendor override route

    def test_nobuffr_maps_in_the_pack(self):
        expected = "ComponentInfo{com.nobuffr.app/tv.tivitime.compose.app.AppActivity}"
        # The icon pack maps it to its banner (the default since 1.9.5) and
        # Core Builds Glyphs to its square glyph; the art for both is the
        # icon pack's, which the Glyphs build copies.
        for pack, drawable in ((ROOT / "app", "nobuffr_banner"),
                               (ROOT / "glyphs", "nobuffr")):
            xml = pack / "src/main/res/xml/appfilter.xml"
            resources = ET.parse(xml).getroot()
            matches = [r for r in resources.findall("item") if r.get("component") == expected]
            self.assertEqual(len(matches), 1, str(xml))
            self.assertEqual(matches[0].get("drawable"), drawable)
            self.assertNotIn("com.nobuffr.app/.MainActivity", xml.read_text())
        nodpi = ROOT / "app/src/main/res/drawable-nodpi"
        mod_aliases = read_aliases(ROOT / "app/src/main/res/values")
        for suffix in ("", "_banner"):
            self.assertTrue(art_path(nodpi, f"nobuffr{suffix}", mod_aliases).is_file())

    def test_download_link_is_in_generated_catalog_documentation(self):
        self.assertIn("[NoBuffr](https://downloads.nobuffr.com/android/nobuffr.apk)",
                      (ROOT / "docs/IconPackList.md").read_text())


class DiversityTests(unittest.TestCase):
    """Recognisability can only move one way.

    docs/research/iconpack-design-upgrade-2026-09.md measures the pack at 66%
    generic letter tiles — the one axis where Projectivy Icon Pack still wins.
    A catalog addition lands on a letter tile by default, so without a gate
    the share ratchets up with every release.

    Tranche 2 (2026-09) widened the definition of "generic" from bare tile_*
    to any glyph that carries no brand device — a tile_*, or a category
    shell carrying a bare letter/digit (broadcast_M, sport_9, app_F, ...).
    Outfit monograms are the exception: since v1.8.18 a wordmark brand's
    letter is a reviewed, test-enforced construction (tools/typeface), not a
    fallback, so monogram_* stays on the bespoke side of this line. The gate
    re-based to 538/933 (57.7%) with ten_mark retiring the last tile_*; the
    numbers below may only move in the pack's favour. The researched-emblem
    set prevents a named brand from silently regressing to a generic glyph.
    """

    # Brands with a verified public emblem or a reviewed bespoke mark.
    # Grows as each logo audit lands; a regression here is a silent quality
    # loss, not a count change.
    RESEARCHED_EMBLEMS = {
        "al_jazeera", "androidapp_2",   # Al Jazeera, France 24
        "cbctv", "cnbc",                # CBC Gem, CNBC (NBC peacock)
        "sbs", "sbsondemand",           # SBS five-splice Mercator globe
        "epix",                         # MGM+ film-reel device
        # Tranche 2 (2026-09): the network numerals and brand devices
        "sevenplus", "seven_plus",      # Seven Network 7 + the 7plus mark
        "ninenow", "ninenow_2",         # the nine with the play
        "tenplay",                      # the 10 lockup — last tile_* retired
        "abciview",                     # the ABC lollipops
        "maoritelevision",              # the koru
        "jiohotstar",                   # the hot star
        "magenta_sport",                # the Telekom t with its dot
        # Letter-mark pass (2026-09): wordmark brands off invented initials
        "bellmedia",                    # Crave — the wordmark's leading c
        "hayu",                         # the wordmark's y and its tail
        "neon",                         # the app mark's neon-tube segments
        # TizenTube emblem pass (2026-09): rebuilt from the measured logo
        "tizentube",                    # globe, struck-through wedge, dot
    }

    @staticmethod
    def _is_generic(glyph: str) -> bool:
        import re
        # An Outfit monogram is a reviewed brand construction (the
        # test-enforced wordmark direction), not a letter fallback.
        if glyph.startswith("monogram_"):
            return False
        return bool(re.fullmatch(r"tile_.*|.*_[A-Z0-9]", glyph))

    def _generic(self):
        return [i for i in ICONS if self._is_generic(i["glyph"])]

    def test_generic_glyph_share_has_a_ceiling(self):
        # 57.7% -> 58.1% on purpose: twelve apps (Fox Nation, Hoichoi, Sun NXT,
        # Tencent Video, Viki, RaiPlay, Perfect Player, 3Player, Launcher
        # Manager, NT at Home, Movie HD, Zattoo) had been drawing *other*
        # companies' marks - Youku's, FloSports', OTT Navigator's - which
        # counted as bespoke and was false. They are on their own monograms
        # now. The ceiling still only ever comes down.
        share = len(self._generic()) / len(ICONS)
        self.assertLessEqual(share, 0.581,
                             f"{share:.2%} of icons are generic glyphs, above "
                             "the 58.1% ceiling")

    def test_bespoke_mark_count_has_a_floor(self):
        bespoke = len(ICONS) - len(self._generic())
        self.assertGreaterEqual(bespoke, 395,
                                f"only {bespoke} icons on non-generic marks, "
                                "below the 395 floor")

    def test_no_tile_glyphs_remain(self):
        tiles = [i["name"] for i in ICONS if i["glyph"].startswith("tile_")]
        self.assertEqual(tiles, [],
                         f"tile_* glyphs survived: {tiles[:5]} — the tranche "
                         "retired the last one")

    def test_researched_emblems_are_never_generic(self):
        for icon in ICONS:
            if icon["drawable"] in self.RESEARCHED_EMBLEMS:
                self.assertFalse(self._is_generic(icon["glyph"]),
                                 f"{icon['name']} regressed to a generic glyph")

    # Different apps whose Classic square PNGs are byte-identical, as of
    # 1.9.3: a two-letter monogram on the same category shell and one of the
    # three shared accents, so the launcher tile says nothing about which app
    # it is (Tablo and TV App Repo, Thmanyah and ThreeNow). Frozen here so the
    # count can only fall: a new collision fails, and a pair that has been
    # given distinct art must be deleted from this list.
    # Emptied in 1.9.4: the 22 groups frozen here in 1.9.3 were given distinct
    # accents, and build_icons.py's render gate now catches the cause (two
    # brandless icons used to excuse each other). The list stays so the
    # shrink-only contract below still has something to hold.
    KNOWN_IDENTICAL: list[set[str]] = []

    @staticmethod
    def _identical_across_apps() -> list[set[str]]:
        """Groups of different apps whose square art shares every byte.

        Entries of one `brand` are meant to look the same (AGENTS.md: brand
        groups share glyph and accent), so a group counts only when it spans
        more than one brand, an unbranded icon being its own brand.
        """
        by_hash: dict[str, list[dict]] = {}
        for icon in ICONS:
            art = art_path(NODPI, icon["drawable"], ALIASES)
            if art.is_file():
                digest = hashlib.sha256(art.read_bytes()).hexdigest()
                by_hash.setdefault(digest, []).append(icon)
        return [{i["drawable"] for i in group} for group in by_hash.values()
                if len({i.get("brand") or i["drawable"] for i in group}) > 1]

    def test_no_new_identical_icons_across_apps(self):
        known = [frozenset(g) for g in self.KNOWN_IDENTICAL]
        new = [sorted(g) for g in self._identical_across_apps()
               if frozenset(g) not in known]
        self.assertEqual(new, [],
                         "different apps now share byte-identical icons; give "
                         "each its own mark or accent (a two-letter monogram "
                         "on a shared shell and accent is the usual cause)")

    def test_known_identical_list_only_shrinks(self):
        current = {frozenset(g) for g in self._identical_across_apps()}
        fixed = [sorted(g) for g in self.KNOWN_IDENTICAL
                 if frozenset(g) not in current]
        self.assertEqual(fixed, [],
                         "these groups no longer collide as listed; delete "
                         "them from KNOWN_IDENTICAL so the list keeps "
                         "shrinking")


class DuotoneTests(unittest.TestCase):
    """Catalog `secondary`: a sourced second brand colour on named parts.

    The recommendation that shipped this: most brand marks are one colour and
    stay that way; a mark whose own logo is two-tone (YouTube's white play,
    VLC's white bands) reads truer with the second paint. Everything else in
    the pack must render byte-identically to single-accent.
    """

    DUO = [i for i in ICONS if i.get("secondary")]

    def paints(self, svg):
        root = ET.fromstring(svg)
        return [node.get("stroke") for node in root.iter()
                if node.tag.split("}")[-1] in
                {"path", "circle", "ellipse", "rect", "line", "polyline", "polygon"}
                and node.get("stroke") not in (None, "none")]

    def test_the_first_batch_is_declared_and_sourced(self):
        self.assertEqual({i["name"] for i in self.DUO},
                         {"YouTube", "VLC", "Emby", "Jellyfin"})
        for icon in self.DUO:
            self.assertEqual(secondary_errors(icon), [], icon["name"])
            self.assertTrue(icon["secondary"]["source"].strip(), icon["name"])

    def test_named_parts_take_the_second_colour_and_nothing_else_does(self):
        for icon in self.DUO:
            with self.subTest(icon=icon["name"]):
                accent = display_accent(icon["color"])
                paint = secondary_color(icon)
                drawn = self.paints(render_svg(icon["glyph"], icon["color"],
                                               secondary=icon["secondary"]))
                self.assertEqual(
                    [k for k, p in enumerate(drawn) if p == paint],
                    sorted(icon["secondary"]["parts"]))
                self.assertTrue(all(p in (accent, paint) for p in drawn), drawn)

    def test_shipped_square_and_banner_carry_the_duotone(self):
        for icon in self.DUO:
            paint = secondary_color(icon)
            for kind in ("svg", "banners"):
                with self.subTest(icon=icon["name"], kind=kind):
                    text = (ROOT / "assets" / kind / f"{icon['drawable']}.svg").read_text()
                    self.assertIn(f'stroke="{paint}"', text)

    def test_banner_rail_stays_the_primary_brand_colour(self):
        # Colour-sampling launchers (Monet) read the rail; the second colour
        # is detail inside the mark, never the card's headline hue.
        yt = next(i for i in self.DUO if i["name"] == "YouTube")
        banner = render("YouTube", yt["glyph"], yt["color"], yt.get("category"),
                        secondary=yt["secondary"])
        rail = banner.split('id="cbRail"', 1)[1].split("</linearGradient>", 1)[0]
        self.assertIn(display_accent(yt["color"]), rail)
        self.assertNotIn(secondary_color(yt), rail)

    def test_the_contract_passes_the_declared_paint_and_rejects_others(self):
        yt = next(i for i in self.DUO if i["name"] == "YouTube")
        accent = display_accent(yt["color"])
        body = monoline(GLYPHS[yt["glyph"]](accent))
        body = body.replace(f'stroke="{accent}"', 'stroke="#E6EDF3"', 1)
        self.assertEqual(core_monoline_errors(body, accent, secondary="#E6EDF3"), [])
        self.assertTrue(core_monoline_errors(body, accent))

    def test_unsound_declarations_are_refused(self):
        base = copy.deepcopy(next(i for i in self.DUO if i["name"] == "VLC"))
        cases = {
            "no source": {"source": ""},
            "out of range": {"parts": [9]},
            "every part": {"parts": [0, 1, 2, 3]},
            "empty parts": {"parts": []},
            "same as accent": {"color": base["color"]},
            "bad hex": {"color": "white"},
        }
        for label, change in cases.items():
            with self.subTest(case=label):
                icon = copy.deepcopy(base)
                icon["secondary"].update(change)
                self.assertTrue(secondary_errors(icon), label)
        with_gradient = dict(copy.deepcopy(base), gradient=["#FF8800", "#FFB000"])
        self.assertTrue(secondary_errors(with_gradient))
        mono = dict(copy.deepcopy(base), color_note="monochrome")
        self.assertTrue(secondary_errors(mono))

    def test_monochrome_rendering_ignores_a_secondary(self):
        yt = next(i for i in self.DUO if i["name"] == "YouTube")
        self.assertEqual(
            render_svg(yt["glyph"], yt["color"], monochrome=True, secondary=yt["secondary"]),
            render_svg(yt["glyph"], yt["color"], monochrome=True))


class ApkInspectorTests(unittest.TestCase):
    def manifest(self, activities: str):
        return ET.fromstring(f'<manifest xmlns:android="{NS}" package="com.test.app">'
                             f'<application>{activities}</application></manifest>')

    def intent(self, category="LEANBACK_LAUNCHER"):
        return ('<intent-filter><action android:name="android.intent.action.MAIN"/>'
                f'<category android:name="android.intent.category.{category}"/></intent-filter>')

    def test_external_activity_namespace_is_not_expanded_as_package(self):
        xml = self.manifest(f'<activity android:name="tv.vendor.AppActivity">{self.intent()}</activity>')
        self.assertEqual(launcher_components(xml)[0]["component"], "com.test.app/tv.vendor.AppActivity")
        self.assertEqual(activity_name("com.test.app", ".Main"), "com.test.app.Main")
        self.assertEqual(activity_name("com.test.app", "Main"), "com.test.app.Main")

    def test_alias_uses_alias_component_not_target(self):
        xml = self.manifest('<activity-alias android:name=".TvAlias" android:targetActivity=".Main">'
                            + self.intent() + '</activity-alias>')
        self.assertEqual(launcher_components(xml)[0]["component"], "com.test.app/com.test.app.TvAlias")

    def test_disabled_and_non_exported_activities_are_not_mapped(self):
        for attr in ('android:enabled="false"', 'android:exported="false"'):
            xml = self.manifest(f'<activity android:name=".Main" {attr}>{self.intent()}</activity>')
            self.assertEqual(launcher_components(xml), [])

    def test_main_and_launcher_must_be_in_the_same_filter(self):
        xml = self.manifest('<activity android:name=".Main"><intent-filter>'
                            '<action android:name="android.intent.action.MAIN"/></intent-filter>'
                            '<intent-filter><category android:name="android.intent.category.LAUNCHER"/>'
                            '</intent-filter></activity>')
        self.assertEqual(launcher_components(xml), [])

    def test_zip_resource_paths_cannot_traverse(self):
        for name in ("../bad.png", "/res/image.png", "res/../../bad", "assets/bad", "res\\bad"):
            with self.assertRaises(ValueError):
                resource_path(name)
        self.assertEqual(resource_path("res/icon.png"), Path("res/icon.png"))



class ShrinkKeepRules(unittest.TestCase):
    """Release builds shrink resources, and every icon drawable is resolved
    by name (appfilter strings, getIdentifier), so all of them are pinned by
    a generated keep file. The shrinker reads it only from res/raw/, and only
    the root <resources> element's tools:keep attribute: a child <keep>
    element is ignored (the APK silently loses the icons), and anything under
    res/values/ fails the resource merger."""

    RES = ROOT / "app/src/main/res"
    TOOLS = "{http://schemas.android.com/tools}"

    def test_keep_file_is_raw_with_the_attribute_on_the_root(self):
        self.assertFalse((self.RES / "values/keep.xml").exists(),
                         "keep rules under res/values/ break :app:merge*Resources")
        root = ET.parse(self.RES / "raw/keep.xml").getroot()
        self.assertEqual(root.tag, "resources")
        self.assertIn(f"{self.TOOLS}keep", root.attrib,
                      "tools:keep must sit on the root; a child <keep> is ignored")
        self.assertEqual(list(root), [], "keep rules are an attribute, not child elements")

    def test_no_values_file_carries_keep_elements(self):
        for path in (self.RES / "values").glob("*.xml"):
            with self.subTest(file=path.name):
                self.assertEqual(ET.parse(path).getroot().findall("keep"), [])

    def test_every_catalog_drawable_and_banner_is_pinned(self):
        root = ET.parse(self.RES / "raw/keep.xml").getroot()
        pinned = {v.removeprefix("@drawable/")
                  for v in root.get(f"{self.TOOLS}keep").split(",")}
        icons = json.loads((ROOT / "tools/catalog.json").read_text(encoding="utf-8"))["icons"]
        wanted = {i["drawable"] for i in icons} | {f"{i['drawable']}_banner" for i in icons}
        self.assertEqual(sorted(wanted - pinned), [])

if __name__ == "__main__":
    unittest.main()
