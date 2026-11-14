#!/usr/bin/env python3
"""
The icon gallery (tools/build_gallery.py, published by .github/workflows/pages.yml)
is held to the catalog, the shipped art and the live site it replaces:

  * one entry per catalog icon, each with its banner master and shipped art;
  * the category chips are the app's own (MainActivity.CHIP_ORDER), and cover
    every category the catalog uses;
  * the page makes no third-party request and cannot be broken out of by an
    app name;
  * two icons really render: a 1280x720 banner and a 512x512 icon, both RGBA;
  * the workflow builds docs/ the way the branch deploy did, refuses a site
    missing any docs/ page (docs/privacy.html is the Play listing's policy
    URL), and pins the renderer the way tools/requirements.txt does;
  * the README links the gallery.

Run: python3 tests/test_icon_gallery.py
"""
from __future__ import annotations

import importlib.util
import json
import re
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "pages.yml"
GALLERY_URL = "https://brevitya.github.io/CoreBuildsApps/gallery/"

spec = importlib.util.spec_from_file_location("build_gallery", ROOT / "tools" / "build_gallery.py")
gallery = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(gallery)


def catalog() -> dict:
    return json.loads((ROOT / "tools" / "catalog.json").read_text(encoding="utf-8"))


class IconGallery(unittest.TestCase):

    def test_one_entry_per_icon_with_its_art(self) -> None:
        cat = catalog()
        items = gallery.entries(cat)
        self.assertEqual(len(cat["icons"]), len(items))
        self.assertEqual(cat["meta"]["count"], len(items))
        drawables = [e["d"] for e in items]
        self.assertEqual(len(drawables), len(set(drawables)), "a drawable appears twice")
        self.assertEqual([], gallery.missing_sources(items))

    def test_chips_are_the_apps_own(self) -> None:
        order = gallery.chip_order()
        self.assertGreaterEqual(len(order), 10)
        self.assertEqual(("APP", "Apps"), order[-1], "the app lists Apps last")
        used = {i.get("category", "") for i in catalog()["icons"]}
        self.assertEqual(set(), used - {code for code, _ in order},
                         "a catalog category has no chip in MainActivity.CHIP_ORDER")

    def test_page_builds_and_requests_nothing_third_party(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "gallery"
            self.assertEqual(0, gallery.main(["--out", str(out), "--no-render"]))
            page = (out / "index.html").read_text(encoding="utf-8")
            data = json.loads((out / "icons.json").read_text(encoding="utf-8"))
            self.assertEqual(len(catalog()["icons"]), len(data))
            for cut in ("regular", "medium", "semibold"):
                self.assertTrue((out / "fonts" / f"outfit_{cut}.ttf").is_file())
            self.assertTrue((out / "fonts" / "Outfit-OFL.txt").is_file(), "OFL needs its licence alongside")
        self.assertIn('aria-label="Search icons by app or package name"', page)
        self.assertIn("<title>Core Builds Icons</title>", page)
        self.assertNotRegex(page, r'<(script|img|link)[^>]+(src|href)="https?://',
                            "the page must load nothing from another host")
        self.assertNotRegex(page, r"url\(\s*['\"]?https?://")
        # Search hides cards with the `hidden` attribute. The cards' own
        # `display: flex` beats the browser's default for it, so the page has
        # to restate it, or every card stays on screen whatever is typed.
        self.assertRegex(page, r"\[hidden\]\s*\{\s*display:\s*none\s*!important;\s*\}")
        self.assertIn("c.li.hidden = !ok", page)
        self.assertIn('download = `${file} banner.png`', page)
        self.assertIn('download = `${file} icon.png`', page)

    def test_an_app_name_cannot_close_the_script(self) -> None:
        hostile = [{"n": "</script><img src=x onerror=alert(1)>", "d": "x", "c": "APP", "p": [], "k": ""}]
        page = gallery.page(hostile, {"version": "0"})
        self.assertNotIn("</script><img", page)
        self.assertEqual(1, page.count("</script>"), "only the page's own script tag closes")

    def test_two_icons_render(self) -> None:
        try:
            from PIL import Image
            import resvg_py  # noqa: F401
        except ImportError as missing:
            self.skipTest(f"renderer not installed: {missing}")
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "gallery"
            self.assertEqual(0, gallery.main(["--out", str(out), "--limit", "2"]))
            first = json.loads((out / "icons.json").read_text(encoding="utf-8"))[0]["d"]
            with Image.open(out / "png" / f"{first}_banner.png") as banner:
                self.assertEqual(((1280, 720), "RGBA"), (banner.size, banner.mode))
            with Image.open(out / "png" / f"{first}.png") as icon:
                self.assertEqual(((512, 512), "RGBA"), (icon.size, icon.mode))
            self.assertTrue((out / "preview" / f"{first}.webp").is_file())

    def test_workflow_keeps_docs_and_pins_the_renderer(self) -> None:
        wf = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("uses: actions/jekyll-build-pages@v1", wf)
        self.assertIn("source: ./docs", wf)
        self.assertIn("python tools/build_gallery.py --out _site/gallery", wf)
        self.assertIn("find docs -name '*.html'", wf, "the build must refuse a site missing a docs/ page")
        self.assertRegex(wf, r"upload-pages-artifact@v\d+\s+with:\s+path: _site")
        self.assertIn("privacy.html", wf.split("deploy:", 1)[1], "the deploy job reads the policy back")
        requirements = (ROOT / "tools" / "requirements.txt").read_text(encoding="utf-8")
        for package in ("resvg-py", "Pillow"):
            pin = re.search(rf"^{package}==([\d.]+)$", requirements, re.M)
            self.assertIsNotNone(pin, f"tools/requirements.txt pins {package}")
            assert pin is not None
            self.assertIn(f'"{package}=={pin.group(1)}"', wf, f"pages.yml pins {package} as requirements.txt does")
        for path in ("tools/catalog.json", "assets/banners/**", "tools/build_gallery.py", "docs/**"):
            self.assertIn(f"'{path}'", wf, f"a change to {path} must republish")

    def test_readme_links_the_gallery(self) -> None:
        self.assertIn(GALLERY_URL, (ROOT / "README.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
