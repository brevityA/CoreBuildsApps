#!/usr/bin/env python3
"""
The Play listing is an artefact with hard limits and a truth requirement.

Two different failures are worth a gate here, and they fail differently:

  * **Platform limits.** The icon is exactly 512x512 32-bit PNG under 1 MB, the
    feature graphic exactly 1024x500 and the TV banner exactly 1280x720 with no
    alpha, screenshots 320-3840 px a side and no more than 2:1. Play rejects a
    listing for a transparent channel or a dimension off by one, and it does so
    at upload, after review has already cost days.
  * **Truth.** The copy quotes how many icons, wallpapers and loops the pack
    ships. A listing that says 1012 icons while the build maps 1003 is a
    metadata problem and a support problem at once, and unlike a bad pixel
    nobody can see it from the outside.

So the gate reads the committed assets off disk, re-derives every count from
the same sources the app reads, and holds the copy's claims to them.
"""
from __future__ import annotations

import json
import re
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import build_play_listing as listing  # noqa: E402

PLAY = ROOT / "docs" / "play"
SPEC = json.loads((PLAY / "listing.json").read_text(encoding="utf-8"))
GLYPHS = PLAY / "glyphs"
GLYPHS_SPEC = json.loads((GLYPHS / "listing.json").read_text(encoding="utf-8"))


class AssetLimits(unittest.TestCase):
    """Play's published requirements, checked against the files themselves."""

    def test_icon_is_512_square_32_bit_png_under_a_megabyte(self):
        path = PLAY / SPEC["assets"]["icon"]["file"]
        self.assertLessEqual(path.stat().st_size, 1024 * 1024,
                             "Play's icon limit is 1024 KB")
        with Image.open(path) as im:
            self.assertEqual((im.width, im.height), (512, 512))
            # 32-bit PNG means RGBA (or LA); an RGB icon is rejected as 24-bit.
            self.assertIn(im.mode, ("RGBA", "LA"))
            self.assertTrue(path.read_bytes().startswith(b"\x89PNG"))

    def test_icon_is_not_pre_rounded(self):
        # Play applies its own ~30% corner mask. A hand-rounded icon comes out
        # double-cropped, so the corners must still carry the pack's own art.
        with Image.open(PLAY / SPEC["assets"]["icon"]["file"]) as im:
            im = im.convert("RGBA")
            corner = im.getpixel((2, 2))
            centre = im.getpixel((256, 256))
            self.assertNotEqual(corner[3], 0,
                                "icon has transparent corners: do not pre-round it")
            self.assertEqual(corner[3], centre[3],
                             "icon ground must be opaque edge to edge")

    def test_feature_graphic_is_exactly_1024x500_without_alpha(self):
        path = PLAY / SPEC["assets"]["feature_graphic"]["file"]
        with Image.open(path) as im:
            self.assertEqual((im.width, im.height), (1024, 500))
            self.assertEqual(im.mode, "RGB", "Play requires 24-bit PNG, no alpha")

    def test_tv_banner_is_exactly_1280x720_without_alpha(self):
        path = PLAY / SPEC["assets"]["tv_banner"]["file"]
        with Image.open(path) as im:
            self.assertEqual((im.width, im.height), (1280, 720))
            self.assertEqual(im.mode, "RGB")

    def test_tv_banner_is_the_same_drawing_as_the_on_device_banner(self):
        # The store banner and the Leanback banner must be one composition at
        # two scales. Wordmarks are outlined to paths - there is no <text> to
        # grep for - so this compares the committed 1280x720 raster against a
        # fresh raster of the on-device composition. Renderer and machine are
        # the same for both sides, so this is exact; if either banner is
        # redrawn by hand, the pixels stop matching.
        import tempfile
        import build_branding
        from svg_renderer import svg2png

        with tempfile.TemporaryDirectory() as tmp:
            fresh = Path(tmp) / "banner.png"
            svg2png(bytestring=build_branding.banner_svg().encode(),
                    write_to=str(fresh), output_width=1280, output_height=720,
                    background_color=None)
            with Image.open(fresh) as rendered, \
                    Image.open(PLAY / SPEC["assets"]["tv_banner"]["file"]) as committed:
                self.assertEqual(rendered.size, committed.size)
                self.assertEqual(rendered.convert("RGB").tobytes(),
                                 committed.convert("RGB").tobytes(),
                                 "the store banner drifted from the on-device banner")

    def test_there_is_at_least_one_tv_screenshot(self):
        shots = SPEC["assets"]["tv_screenshots"]
        self.assertGreaterEqual(len(shots), 1,
                                "Play will not publish a TV listing without one")
        self.assertLessEqual(len(shots), 8, "Play allows at most 8 per form factor")

    def test_each_screenshot_is_1080p_16_by_9_without_alpha(self):
        for entry in SPEC["assets"]["tv_screenshots"]:
            path = PLAY / entry["file"]
            with Image.open(path) as im:
                self.assertEqual((im.width, im.height), (1920, 1080), path.name)
                self.assertEqual(im.mode, "RGB", f"{path.name} has an alpha channel")
                for side in (im.width, im.height):
                    self.assertGreaterEqual(side, listing.SHOT_MIN)
                    self.assertLessEqual(side, listing.SHOT_MAX)
                self.assertLessEqual(max(im.width, im.height) / min(im.width, im.height),
                                     2.0, "Play caps the aspect ratio at 2:1")

    def test_screenshots_have_no_caption_band(self):
        # The mockups carry a strip under the panel naming the values they
        # invent. That belongs in documentation, not in a store screenshot.
        for entry in SPEC["assets"]["tv_screenshots"]:
            source = ROOT / "docs" / f"app-ui-{entry['frame']}.png"
            with Image.open(source) as src, Image.open(PLAY / entry["file"]) as out:
                self.assertGreaterEqual(src.height, out.height,
                                        f"{source.name} is smaller than its crop")
                self.assertEqual(out.height, 1080)


class TheWalkthroughAgreesWithTheAssets(unittest.TestCase):
    """docs/PLAY-CONSOLE.md is what someone types into Console.

    A walkthrough quoting yesterday's app name, package or file path is worse
    than none: it is confidently wrong at the exact moment a human is copying
    values into a form that cannot be edited later (the package name).
    """

    DOC = ROOT / "docs/PLAY-CONSOLE.md"

    def setUp(self):
        self.doc = self.DOC.read_text(encoding="utf-8")

    def test_it_quotes_the_listing_package(self):
        self.assertIn(SPEC["package"], self.doc)
        if SPEC["package"] != SPEC["sideload_package"]:
            self.assertIn(SPEC["sideload_package"], self.doc,
                          "the walkthrough must name the sideload id too, so the "
                          "wrong one can never be pasted")

    def test_it_quotes_the_shipped_title_and_limits(self):
        self.assertIn(SPEC["copy"]["title"], self.doc)
        self.assertIn("80-character limit", self.doc)
        self.assertIn("4000 limit", self.doc)

    def test_every_asset_it_names_exists(self):
        named = set(re.findall(r"docs/play/[A-Za-z0-9._\-*/]*[A-Za-z0-9._\-*]", self.doc))
        self.assertTrue(named)
        for entry in sorted(named):
            if "*" in entry:
                # Globs are written relative to the repo root; PLAY already
                # points at docs/play, so only the tail belongs in the pattern.
                pattern = entry.split("/", 2)[2]
                self.assertTrue(list(PLAY.glob(pattern)),
                                f"{entry} matches no committed asset")
                continue
            self.assertTrue((ROOT / entry).exists(), f"{entry} does not exist")

    def test_the_form_factor_it_tells_you_to_pick_is_the_listed_one(self):
        if SPEC["form_factors"] == ["tv"]:
            self.assertIn("TV only", self.doc)
            self.assertIn("Form factors", self.doc)

    def test_it_does_not_promise_purchases_the_listing_denies(self):
        if not SPEC["content_rating"]["purchases"]:
            self.assertIn("no purchases", self.doc)

    def test_it_names_the_plus_product_the_code_sells(self):
        # A product ID can never be reused or renamed in Console; the one typed
        # there must be the one PlusStore queries and the broker checks.
        store = (ROOT / "app/src/play/java/tv/corebuilds/iconpack/PlusStore.kt"
                 ).read_text(encoding="utf-8")
        product = re.search(r'PRODUCT_ID = "([a-z_]+)"', store).group(1)
        broker = (ROOT / "tools/icon_request_broker/constants.mjs").read_text(encoding="utf-8")
        self.assertIn(f'"{product}"', broker)
        self.assertIn(f"`{product}`", self.doc)
        self.assertIn("License testing", self.doc)
        self.assertIn("ICONREQ_GOOGLE_SERVICE_ACCOUNT_JSON", self.doc)

    def test_it_names_the_privacy_url_and_the_pages_step(self):
        self.assertIn("brevityA.github.io/CoreBuildsApps/privacy.html", self.doc)
        self.assertIn("Settings → Pages", self.doc)
        self.assertTrue((ROOT / "docs/privacy.html").exists())

    def test_it_states_the_twelve_tester_rule(self):
        # The longest item in the launch; a walkthrough that omits it invites
        # someone to perfect store copy for two weeks and then discover the wait.
        self.assertIn("12 testers", self.doc)
        self.assertIn("14", self.doc)

    def test_it_names_the_secrets_the_bundle_build_needs(self):
        for secret in ("PLAY_KEYSTORE_BASE64", "PLAY_KEYSTORE_PASSWORD",
                       "PLAY_KEY_ALIAS", "PLAY_KEY_PASSWORD"):
            self.assertIn(secret, self.doc)

    def test_it_answers_the_rating_questions_the_listing_answers(self):
        # Console's questionnaire is category "All Other App Types" with an
        # Online Content question; "No to everything" got that one wrong.
        # Each answer is read from its own app's section, so swapping the
        # two apps' answers fails instead of matching somewhere in the page.
        sections = {s.split("\n", 1)[0]: " ".join(s.split())
                    for s in re.split(r"(?m)^## ", self.doc)}
        pack_section = next(v for k, v in sections.items() if k.startswith("2. App content"))
        glyphs_section = next(v for k, v in sections.items()
                              if k.startswith("9. Core Builds Glyphs Pack"))
        self.assertIn(f"category **{SPEC['content_rating']['category']}**", pack_section)
        pack = "Yes" if SPEC["content_rating"]["online_content"] else "No"
        glyphs = "yes" if GLYPHS_SPEC["content_rating"]["online_content"] else "no"
        self.assertIn(f"**Online Content: {pack}**", pack_section)
        self.assertIn(f"**Online Content: {glyphs}**", glyphs_section)

    def test_it_walks_the_glyphs_pack_listing_too(self):
        # The second app is a second package name typed into a form that can
        # never be edited; both of its ids and its own asset folder are named.
        self.assertIn(f"`{GLYPHS_SPEC['package']}`", self.doc)
        self.assertIn(f"`{GLYPHS_SPEC['sideload_package']}`", self.doc)
        self.assertIn(GLYPHS_SPEC["copy"]["title"], self.doc)
        self.assertIn("docs/play/glyphs/listing.json", self.doc)
        self.assertIn("glyphs/build/outputs/bundle/play/", self.doc)

    def test_the_numbers_it_quotes_are_the_current_ones(self):
        # "currently 1449" is copied next to a form field; a stale one sends
        # someone looking for text that is not in the paste.
        flat = " ".join(self.doc.split())
        for spec in (SPEC, GLYPHS_SPEC):
            copy = spec["copy"]
            self.assertIn(f"this is {len(copy['title'])})", flat)
            self.assertIn(f"currently {len(copy['short_description'])})", flat)
            self.assertIn(f"currently {len(copy['full_description'])})", flat)
        n = GLYPHS_SPEC["counts"]
        self.assertIn(f"same {n['components']} launcher components", flat)
        self.assertIn(f"{n['icons']} square glyphs", flat)
        # The broker-on Data safety count, as a word in both documents.
        word = ("one", "two", "three", "four")[len(listing.BROKER_COLLECTED) - 1]
        self.assertIn(f"collects {word} optional types", flat)
        changelog = " ".join((ROOT / "CHANGELOG.md").read_text(encoding="utf-8").split())
        self.assertIn(f"Data safety ({word} optional types collected", changelog)


class CopyLimits(unittest.TestCase):
    """Play's field lengths, and that the text is the generated text."""

    def test_title_short_and_full_description_fit(self):
        copy = SPEC["copy"]
        self.assertLessEqual(len(copy["title"]), 30)
        self.assertLessEqual(len(copy["short_description"]), 80)
        self.assertLessEqual(len(copy["full_description"]), 4000)
        self.assertGreaterEqual(len(copy["short_description"]), 1)
        self.assertGreaterEqual(len(copy["full_description"]), 100)

    def test_copy_matches_the_generator(self):
        # The committed listing is the generated listing, not a hand-edited one.
        self.assertEqual(SPEC["copy"], listing.copy(),
                         "docs/play/listing.json is stale: run tools/build_play_listing.py")

    def test_title_names_what_the_app_is(self):
        self.assertIn("Icon Pack", SPEC["copy"]["title"])

    def test_square_glyphs_are_credited_to_the_app_that_applies_them(self):
        # The pack's appfilter maps every app to its 16:9 banner; square
        # glyphs auto-apply only from the Glyphs Pack. "One install covers
        # either" was true for single picks, not for the whole launcher.
        full = SPEC["copy"]["full_description"]
        self.assertNotIn("one install covers", full.lower())
        self.assertIn(GLYPHS_SPEC["copy"]["title"], full)
        self.assertIn("16:9 banner, which this pack applies", full)

    def test_copy_avoids_the_claims_play_rejects(self):
        # Metadata policy: no rankings, no price bait, no other product names,
        # no promises of improvement over a competitor.
        text = " ".join(SPEC["copy"].values()).lower()
        for banned in ("best ", "#1", "number one", "top rated", "free!!",
                       "award winning", "download now"):
            self.assertNotIn(banned, text, f"metadata policy: {banned!r}")


class CountsAreDerived(unittest.TestCase):
    """Every number in the copy comes from the build, not from a typist."""

    def test_counts_match_the_tree(self):
        self.assertEqual(SPEC["counts"], listing.counts())

    def test_icon_count_matches_the_generated_resource(self):
        root = ET.parse(ROOT / "app/src/main/res/values/icon_pack.xml").getroot()
        declared = int(root.find("integer[@name='icon_count']").text)
        self.assertEqual(SPEC["counts"]["icons"], declared)

    def test_wall_count_matches_the_shipped_catalog(self):
        catalog = json.loads(
            (ROOT / "app/src/main/assets/manifest/wallpapers.json").read_text(
                encoding="utf-8"))["wallpapers"]
        self.assertEqual(SPEC["counts"]["walls"], len(catalog))
        self.assertEqual(SPEC["counts"]["series"],
                         len({w["series"] for w in catalog}))

    def test_loop_count_matches_the_live_loop_module(self):
        src = (ROOT / "app/src/main/java/tv/corebuilds/iconpack/LiveLoop.kt"
               ).read_text(encoding="utf-8")
        self.assertEqual(SPEC["counts"]["loops"],
                         len(re.findall(r"\n            stillSeries = ", src)))

    def test_the_numbers_in_the_copy_are_the_derived_ones(self):
        n = SPEC["counts"]
        full = SPEC["copy"]["full_description"]
        self.assertIn(f"{n['icons']} icons", full)
        self.assertIn(f"{n['walls']} stills", full)
        self.assertIn(f"{n['series']} series", full)
        self.assertIn(f"{n['loops']} animated wallpapers", full)


class StoreTruth(unittest.TestCase):
    """Answers given to Play must match what the code does."""

    def test_data_safety_names_the_only_payload_the_app_posts(self):
        # If IconRequestSender grows a field, this fails before the Data safety
        # form becomes a false statement on the store page.
        sender = (ROOT / "app/src/main/java/tv/corebuilds/iconpack/IconRequestSender.kt"
                  ).read_text(encoding="utf-8")
        # "apps" is Request all's list of per-app entries: the same fields,
        # several apps (tests/test_batch_requests.py holds it to that).
        posted = set(re.findall(r'[.\s]put\("([a-z_]+)"', sender)) - {"mapped", "apps"}
        # The sender only runs with the broker baked in, so its whole payload
        # is the broker-on answer; the committed answer is this build's.
        declared = set(listing.data_safety(plus=True)
                       ["user_initiated_icon_request"]["fields"])
        self.assertEqual(posted, declared,
                         "the icon request payload moved; Data safety must follow")
        self.assertEqual(SPEC["data_safety"], listing.data_safety())

    def test_data_safety_claims_no_collection_and_no_sdks(self):
        # The claim is only defensible while it is true: no analytics, ads or
        # crash SDK in any Gradle root the icon pack ships from.
        for gradle in (ROOT / "app/build.gradle.kts", ROOT / "glyphs/build.gradle.kts"):
            text = gradle.read_text(encoding="utf-8")
            for sdk in ("firebase", "play-services-ads", "appsflyer", "sentry",
                        "amplitude", "mixpanel", "segment"):
                self.assertNotIn(sdk, text.lower(),
                                 f"{gradle.name} links {sdk}; Data safety says no data")
        # "Collects" follows the broker: without it the app transmits nothing.
        self.assertEqual(SPEC["data_safety"]["collects_user_data"],
                         listing.answers_cover_broker())
        self.assertFalse(SPEC["data_safety"]["shares_user_data"])

    def test_online_content_is_declared_exactly_where_the_app_downloads(self):
        # IARC's "content that isn't part of the initial app download": the
        # pack fetches full-size walls and live loops after install; the
        # Glyphs Pack cannot fetch anything at all.
        walls = json.loads((ROOT / "app/src/main/assets/manifest/wallpapers.json"
                            ).read_text(encoding="utf-8"))["wallpapers"]
        loop = (ROOT / "app/src/main/java/tv/corebuilds/iconpack/LiveLoop.kt"
                ).read_text(encoding="utf-8")
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        downloads = (all(w["url"].startswith("https://") for w in walls)
                     and '"https://' in loop
                     and "android.permission.INTERNET" in manifest)
        self.assertEqual(SPEC["content_rating"]["online_content"], downloads)
        self.assertTrue(downloads, "the pack no longer downloads anything: re-answer IARC")
        for glyphs in (ROOT / "glyphs/src").glob("*/AndroidManifest.xml"):
            self.assertNotIn("android.permission.INTERNET",
                             glyphs.read_text(encoding="utf-8"))
        self.assertFalse(GLYPHS_SPEC["content_rating"]["online_content"])

    def test_form_factors_are_tv_only_and_say_why(self):
        # No phone screenshots exist because no phone UI has been tested.
        # Listing for phones on the strength of TV frames would be fiction.
        self.assertEqual(SPEC["form_factors"], ["tv"])
        doc = Path(listing.__file__).read_text(encoding="utf-8")
        self.assertIn("no phone screenshots", doc.lower())

    def test_listing_package_is_the_play_build_not_the_sideload_one(self):
        self.assertEqual(SPEC["package"], "tv.corebuilds.iconpack.play")
        self.assertNotEqual(SPEC["package"], SPEC["sideload_package"])

    def test_plus_is_claimed_exactly_while_it_is_on_sale(self):
        # Plus.shown is true on Play exactly when the broker URL is baked in,
        # and only then may the listing, the rating and Data safety say so.
        sale = listing.broker_enabled()
        plus = (ROOT / "app/src/main/java/tv/corebuilds/iconpack/Plus.kt"
                ).read_text(encoding="utf-8")
        self.assertIn("audit_request_endpoint", plus)
        text = " ".join(SPEC["copy"].values())
        self.assertEqual("CORE BUILDS PLUS" in SPEC["copy"]["full_description"], sale)
        self.assertEqual(SPEC["content_rating"]["purchases"], listing.answers_cover_broker())
        for claim in ("premium", "unlock", "upgrade"):
            self.assertNotIn(claim, text.lower(), f"listing mentions {claim!r}")

    def test_both_states_of_the_switch_are_honest(self):
        # The committed tree is one state; render the other so a broker-on
        # release cannot be the first time its answers are read.
        n = listing.counts()
        off, on = listing.copy(n, plus=False), listing.copy(n, plus=True)
        self.assertNotIn("PLUS", off["full_description"])
        self.assertIn("CORE BUILDS PLUS", on["full_description"])
        self.assertIn(f"Spectra adds {n['plus_walls']} more wallpapers", on["full_description"])
        self.assertIn(f"all {n['walls']} stills", on["full_description"])
        self.assertLessEqual(len(on["full_description"]), listing.FULL_MAX)
        self.assertEqual(off["short_description"], on["short_description"])
        safety = listing.data_safety(plus=True)
        self.assertTrue(safety["collects_user_data"])
        self.assertFalse(safety["shares_user_data"])
        self.assertTrue(all(c["optional"] and not c["shared"] for c in safety["collected"]))
        types = {c["type"] for c in safety["collected"]}
        self.assertEqual(types, {"App activity > Installed apps",
                                 "Financial info > Purchase history"})
        # The device note's model and Android version fit no listed type
        # (they identify no one); they are named, not filed under the wrong one.
        self.assertFalse(any("performance" in t.lower() for t in types))
        self.assertIn("TV model", safety["not_a_listed_type"])
        self.assertTrue(listing.content_rating(plus=True)["purchases"])
        self.assertFalse(listing.content_rating(plus=False)["purchases"])
        self.assertFalse(listing.data_safety(plus=False)["collects_user_data"])

    def test_answers_keep_covering_broker_builds_after_the_broker_goes(self):
        # One form covers every version on a track: once a broker build has
        # shipped, dropping the broker must not flip the answers back.
        original = listing.broker_enabled, listing.BROKER_SHIPPED
        listing.broker_enabled = lambda: False
        listing.BROKER_SHIPPED = True
        try:
            self.assertTrue(listing.data_safety()["collects_user_data"])
            self.assertTrue(listing.content_rating()["purchases"])
            self.assertNotIn("PLUS", listing.copy()["full_description"])
        finally:
            listing.broker_enabled, listing.BROKER_SHIPPED = original

    def test_check_refuses_a_broker_build_without_the_latch(self):
        import contextlib, io
        original = listing.broker_enabled
        listing.broker_enabled = lambda: True
        out = io.StringIO()
        try:
            self.assertFalse(listing.BROKER_SHIPPED)
            with contextlib.redirect_stdout(out):
                self.assertEqual(listing.check(), 1)
        finally:
            listing.broker_enabled = original
        self.assertIn("BROKER_SHIPPED is False", out.getvalue())

    def test_check_catches_a_broker_baked_in_without_new_answers(self):
        original = listing.broker_enabled
        listing.broker_enabled = lambda: not original()
        try:
            self.assertEqual(listing.check(), 1)
        finally:
            listing.broker_enabled = original


class GlyphsPackListing(unittest.TestCase):
    """docs/play/glyphs/: the second Play app, held to the pack's own gates.

    The Glyphs Pack is the store page the icon pack's Art style switch opens
    on Play, so it fails in the same two ways - an asset Play rejects at
    upload, or a count the build does not ship - plus one of its own: it is
    easy to dress a companion in the main app's art, and then the store
    banner is not the banner the TV shows.
    """

    def asset(self, key):
        return GLYPHS / GLYPHS_SPEC["assets"][key]["file"]

    def fresh(self, svg_text, size, background=None):
        import tempfile
        from svg_renderer import svg2png

        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        out = Path(tmp.name) / "fresh.png"
        svg2png(bytestring=svg_text.encode(), write_to=str(out),
                output_width=size[0], output_height=size[1],
                background_color=background)
        return Image.open(out)

    def test_assets_are_inside_plays_limits(self):
        icon = self.asset("icon")
        self.assertLessEqual(icon.stat().st_size, 1024 * 1024)
        with Image.open(icon) as im:
            self.assertEqual(im.size, (512, 512))
            self.assertEqual(im.mode, "RGBA")
            # Opaque edge to edge: Play rounds it, so it must not be pre-rounded.
            self.assertEqual(im.getpixel((2, 2))[3], 255)
        for key, size in (("feature_graphic", (1024, 500)), ("tv_banner", (1280, 720))):
            with Image.open(self.asset(key)) as im:
                self.assertEqual(im.size, size, key)
                self.assertEqual(im.mode, "RGB", f"{key} has an alpha channel")
        shots = GLYPHS_SPEC["assets"]["tv_screenshots"]
        self.assertTrue(1 <= len(shots) <= 8)
        for entry in shots:
            with Image.open(GLYPHS / entry["file"]) as im:
                self.assertEqual(im.size, (1920, 1080), entry["file"])
                self.assertEqual(im.mode, "RGB", entry["file"])

    def test_icon_and_banner_are_the_ones_the_tv_shows(self):
        # Same drawing, store size: the launcher icon and the Leanback banner
        # glyphs/ ships come from these two compositions, and the store's must
        # be fresh renders of them, not the icon pack's art.
        import build_glyphs_branding as glyphs_branding

        with self.fresh(glyphs_branding.icon_svg(), (512, 512)) as rendered, \
                Image.open(self.asset("icon")) as committed:
            self.assertEqual(rendered.convert("RGBA").tobytes(),
                             committed.convert("RGBA").tobytes(),
                             "the store icon drifted from the companion's launcher icon")
        banner = glyphs_branding.banner_svg(glyphs_branding.COMPANION_SUBTITLE)
        with self.fresh(banner, (1280, 720)) as rendered, \
                Image.open(self.asset("tv_banner")) as committed:
            self.assertEqual(rendered.convert("RGB").tobytes(),
                             committed.convert("RGB").tobytes(),
                             "the store banner drifted from the companion's banner")
        with Image.open(self.asset("tv_banner")) as glyphs_banner, \
                Image.open(PLAY / SPEC["assets"]["tv_banner"]["file"]) as pack_banner:
            self.assertNotEqual(glyphs_banner.tobytes(), pack_banner.tobytes(),
                                "the Glyphs Pack is listed with the icon pack's banner")

    def test_screenshot_is_the_glyphs_home_frame_without_its_caption(self):
        (entry,) = GLYPHS_SPEC["assets"]["tv_screenshots"]
        self.assertEqual(entry["frame"], "glyphs-home")
        with Image.open(ROOT / "docs/app-ui-glyphs-home.png") as src, \
                Image.open(GLYPHS / entry["file"]) as out:
            self.assertEqual(src.convert("RGB").crop((0, 0, 1920, 1080)).tobytes(),
                             out.tobytes())

    def test_copy_fits_and_is_the_generated_copy(self):
        copy = GLYPHS_SPEC["copy"]
        self.assertLessEqual(len(copy["title"]), 30)
        self.assertLessEqual(len(copy["short_description"]), 80)
        self.assertLessEqual(len(copy["full_description"]), 4000)
        self.assertEqual(copy, listing.glyphs_copy(),
                         "docs/play/glyphs/listing.json is stale: run "
                         "tools/build_play_listing.py")
        text = " ".join(copy.values()).lower()
        for banned in ("best ", "#1", "number one", "top rated", "award winning",
                       "download now", "premium", "unlock"):
            self.assertNotIn(banned, text, f"metadata policy: {banned!r}")

    def test_title_is_the_label_launchers_list(self):
        # Someone told "choose Core Builds Glyphs Pack" must find that string
        # both on the store page and in the launcher's icon pack list.
        root = ET.parse(ROOT / "glyphs/src/main/res/values/strings.xml").getroot()
        label = root.find("string[@name='app_name']").text
        self.assertEqual(GLYPHS_SPEC["copy"]["title"], label)
        self.assertIn(f"Choose {label}", GLYPHS_SPEC["copy"]["full_description"])

    def test_counts_are_the_ones_its_resources_carry(self):
        res = ROOT / "glyphs/src/main/res/xml"

        def items(name, attribute):
            return [el.get(attribute) for el in ET.parse(res / name).getroot().iter("item")
                    if el.get(attribute) is not None]

        n = GLYPHS_SPEC["counts"]
        self.assertEqual(n, listing.glyph_counts())
        self.assertEqual(n["icons"], len(items("drawable.xml", "drawable")))
        self.assertEqual(n["components"], len(items("appfilter.xml", "component")))
        full = GLYPHS_SPEC["copy"]["full_description"]
        self.assertIn(f"{n['icons']} icons", full)
        self.assertIn(f"same {n['components']} launcher components", full)
        self.assertIn(f"{n['icons']} square glyph icons",
                      GLYPHS_SPEC["copy"]["short_description"])

    def test_same_components_claim_holds(self):
        # "Switching changes what an icon looks like, never which apps get
        # one" is true only while both appfilters map the same set.
        def components(path):
            return {el.get("component") for el in ET.parse(path).getroot().iter("item")
                    if el.get("component")}

        pack = components(ROOT / "app/src/main/res/xml/appfilter.xml")
        glyphs = components(ROOT / "glyphs/src/main/res/xml/appfilter.xml")
        self.assertEqual(glyphs, pack)
        self.assertEqual(GLYPHS_SPEC["counts"]["components"], len(pack))

    def test_no_data_and_no_purchases_because_it_has_no_way_to_send_any(self):
        # The fixed answers hold only while the app has no permission, no
        # library and no network code. Any of those appearing fails here first.
        for manifest in sorted((ROOT / "glyphs/src").glob("*/AndroidManifest.xml")):
            self.assertNotIn("uses-permission", manifest.read_text(encoding="utf-8"),
                             f"{manifest.relative_to(ROOT)} asks for a permission")
        gradle = (ROOT / "glyphs/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIsNone(re.search(r"^\s*dependencies\s*\{", gradle, re.M),
                          "glyphs/ links a library; Data safety says it links none")
        for src in (ROOT / "glyphs/src").rglob("*.java"):
            text = src.read_text(encoding="utf-8")
            for net in ("java.net", "HttpURLConnection", "okhttp"):
                self.assertNotIn(net, text, f"{src.name} has network code")
        safety = GLYPHS_SPEC["data_safety"]
        self.assertFalse(safety["collects_user_data"])
        self.assertFalse(safety["shares_user_data"])
        self.assertFalse(safety["has_ads"])
        self.assertFalse(GLYPHS_SPEC["content_rating"]["purchases"])
        self.assertIn("no internet permission", GLYPHS_SPEC["copy"]["full_description"].lower())

    def test_package_ids_are_the_ones_gradle_builds(self):
        gradle = (ROOT / "glyphs/build.gradle.kts").read_text(encoding="utf-8")
        base = re.search(r'applicationId = "([a-z.]+)"', gradle).group(1)
        suffix = re.search(r'create\("play"\) \{\s*initWith\(getByName\("release"\)\)'
                           r'\s*applicationIdSuffix = "([a-z.]+)"', gradle).group(1)
        self.assertEqual(GLYPHS_SPEC["sideload_package"], base)
        self.assertEqual(GLYPHS_SPEC["package"], base + suffix)
        # The pack's Art style switch sends Play users to this id.
        self.assertEqual(GLYPHS_SPEC["package"].replace(".glyphs", ""), SPEC["package"])
        self.assertEqual(GLYPHS_SPEC["form_factors"], SPEC["form_factors"])

    def test_check_catches_a_stale_glyphs_listing(self):
        import tempfile, shutil
        with tempfile.TemporaryDirectory() as tmp:
            copy = Path(tmp) / "play"
            shutil.copytree(PLAY, copy)
            spec = json.loads((copy / "glyphs/listing.json").read_text(encoding="utf-8"))
            spec["counts"]["components"] -= 1
            (copy / "glyphs/listing.json").write_text(json.dumps(spec), encoding="utf-8")
            original = listing.PLAY
            listing.PLAY = copy
            try:
                self.assertIn("glyphs counts moved", " ".join(listing.check_glyphs()))
                self.assertEqual(listing.check(), 1)
            finally:
                listing.PLAY = original


class RepeatedGeneration(unittest.TestCase):
    """The generator is deterministic in everything but antialiasing."""

    def test_rerendering_produces_the_same_spec(self):
        before = (PLAY / "listing.json").read_text(encoding="utf-8")
        glyphs_before = (GLYPHS / "listing.json").read_text(encoding="utf-8")
        listing.render_all()
        after = (PLAY / "listing.json").read_text(encoding="utf-8")
        self.assertEqual(before, after, "the listing spec is not stable across runs")
        self.assertEqual(glyphs_before, (GLYPHS / "listing.json").read_text(encoding="utf-8"),
                         "the Glyphs Pack listing spec is not stable across runs")

    def test_check_passes_on_the_committed_tree(self):
        self.assertEqual(listing.check(), 0)

    def test_check_catches_a_stale_source(self):
        import tempfile, shutil
        with tempfile.TemporaryDirectory() as tmp:
            copy = Path(tmp) / "play"
            shutil.copytree(PLAY, copy)
            spec = json.loads((copy / "listing.json").read_text(encoding="utf-8"))
            spec["sources"]["wallpapers_json"] = "0" * 64
            (copy / "listing.json").write_text(json.dumps(spec), encoding="utf-8")
            original = listing.PLAY
            listing.PLAY = copy
            try:
                self.assertEqual(listing.check(), 1,
                                 "a changed source must fail --check")
            finally:
                listing.PLAY = original


if __name__ == "__main__":
    unittest.main(verbosity=2)
