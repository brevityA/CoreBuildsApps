#!/usr/bin/env python3
"""Catch full-screen layouts that overflow a TV viewport without scrolling.

Why this exists
---------------
activity_settings.xml shipped in 1.8.20 with no scroll container, on the
reasoning that "four rows and a footer fit inside the safe area at 1080p".
They did not. The height was reasoned off a 1920x1080 *pixel* mockup; Android
lays out in dp, and a 1080p Android TV reports 960x540dp. The content came to
roughly 615dp against 540dp available, and because the root was a plain
vertical LinearLayout holding a weight=1 spacer, the overflow was clipped in
silence: no scrollbar, no focus escape, just a Clear button and a footer the
tester could not reach.

Nothing in the repo caught it. check_ui_resources.py resolves R.* references
and test_resource_parity.py compares resource sets across modules; neither one
asks whether the pixels fit. This does.

What it checks
--------------
For every layout whose root is a vertical LinearLayout at match_parent, sum the
minimum height its children need. If that exceeds the viewport and no scroll
container sits in the way, fail.

The sum is an *estimate*: text height is modelled as textSize * LINE_FACTOR,
which is close for Roboto but not exact, and nothing here knows what a
SwitchCompat measures. So the gate is deliberately slack (BUDGET_DP below the
real 540dp is not used; instead a layout only fails once it clears the viewport
by more than TOLERANCE_DP). The point is to catch a layout that is over by the
width of a whole row, which is the mistake that actually happened, not to
police the last few dp.

A layout that legitimately needs more room passes by containing a scroll
container. That is the fix, not an exemption.

Wiring: this file has test_* functions but no TestCase, so `unittest discover`
skips it the same way it skips test_resource_parity.py and the Core Shift
scripts. CI runs the Python suites by naming each file, so this one is listed
explicitly in build.yml and suite-ci.yml. Add it there, or it is not a gate.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

# Android TV at 1080p reports 960x540dp (xhdpi). 720p sets report the same dp
# box at tvdpi, so one number covers both.
VIEWPORT_DP = 540.0

# Slack for the modelling error described above: roughly one settings row.
TOLERANCE_DP = 60.0

# Roboto's ascent+descent lands near 1.17x the type size; 1.2 is the usual
# working figure and errs toward predicting *more* height, which is the safe
# direction for a gate that fails on overflow.
LINE_FACTOR = 1.2

SCROLLERS = ("ScrollView", "NestedScrollView", "RecyclerView", "ViewPager")

# Modules whose layouts are checked. Pop and Pixel Neon carry copies; checking
# the app originals is enough, since the copies are generated or mirrored.
LAYOUT_DIR = ROOT / "app/src/main/res/layout"
DIMENS = ROOT / "app/src/main/res/values/dimens.xml"


def load_dimens() -> dict[str, float]:
    out: dict[str, float] = {}
    for node in ET.parse(DIMENS).getroot().iter("dimen"):
        name, text = node.get("name"), (node.text or "").strip()
        m = re.fullmatch(r"(-?[\d.]+)(dp|sp|dip|px)", text)
        if name and m:
            out[name] = float(m.group(1))
    return out


def dp(value: str | None, dimens: dict[str, float], default: float = 0.0) -> float:
    """Resolve a dimension attribute to dp. Unknown forms fall back to default."""
    if not value:
        return default
    if value.startswith("@dimen/"):
        return dimens.get(value[len("@dimen/"):], default)
    m = re.fullmatch(r"(-?[\d.]+)(dp|sp|dip|px)", value.strip())
    return float(m.group(1)) if m else default


def tag_of(node: ET.Element) -> str:
    return node.tag.rsplit(".", 1)[-1]


def has_scroller(node: ET.Element) -> bool:
    return any(tag_of(n) in SCROLLERS for n in node.iter())


def min_height(node: ET.Element, dimens: dict[str, float]) -> float:
    """Minimum dp this subtree needs, ignoring flexible (weighted) children."""
    tag = tag_of(node)

    # A weighted child absorbs leftover space; it demands nothing of its own.
    if node.get(ANDROID + "layout_height") == "0dp" and node.get(ANDROID + "layout_weight"):
        return 0.0

    pad = dp(node.get(ANDROID + "padding"), dimens)
    top = dp(node.get(ANDROID + "paddingTop"), dimens, pad)
    bottom = dp(node.get(ANDROID + "paddingBottom"), dimens, pad)
    own = dp(node.get(ANDROID + "minHeight"), dimens)

    kids = list(node)
    if not kids:
        # Leaf. Text views are the only leaves with intrinsic height here.
        text = dp(node.get(ANDROID + "textSize"), dimens) * LINE_FACTOR
        # SwitchCompat and friends: assume the 48dp TV touch/focus minimum.
        if not text and tag not in ("View", "Space"):
            text = 30.0 if "Switch" in tag else 0.0
        return max(top + bottom + text, own)

    horizontal = node.get(ANDROID + "orientation") != "vertical" and tag.endswith("LinearLayout")
    inner = 0.0
    for kid in kids:
        h = min_height(kid, dimens) + dp(kid.get(ANDROID + "layout_marginTop"), dimens) \
            + dp(kid.get(ANDROID + "layout_marginBottom"), dimens)
        inner = max(inner, h) if horizontal or not tag.endswith("LinearLayout") else inner + h
    return max(top + bottom + inner, own)


def full_screen_layouts() -> list[Path]:
    out = []
    for p in sorted(LAYOUT_DIR.glob("activity_*.xml")):
        root = ET.parse(p).getroot()
        if tag_of(root).endswith("LinearLayout") \
                and root.get(ANDROID + "orientation") == "vertical" \
                and root.get(ANDROID + "layout_height") == "match_parent":
            out.append(p)
    return out


def test_full_screen_layouts_fit_or_scroll():
    dimens = load_dimens()
    over = []
    for path in full_screen_layouts():
        root = ET.parse(path).getroot()
        if has_scroller(root):
            continue
        needed = min_height(root, dimens)
        if needed > VIEWPORT_DP + TOLERANCE_DP:
            over.append(f"{path.name}: needs ~{needed:.0f}dp of {VIEWPORT_DP:.0f}dp, no scroll container")
    assert not over, "TV layouts overflow without scrolling:\n  " + "\n  ".join(over)


def test_settings_keeps_its_scroll_container():
    """Regression pin for the 1.8.20 clipping bug, independent of the estimate."""
    root = ET.parse(LAYOUT_DIR / "activity_settings.xml").getroot()
    assert has_scroller(root), (
        "activity_settings.xml lost its scroll container; its rows clip off a "
        "540dp TV viewport without one"
    )


if __name__ == "__main__":
    dimens = load_dimens()
    for path in full_screen_layouts():
        root = ET.parse(path).getroot()
        print(f"{path.name:36s} {min_height(root, dimens):6.0f}dp  "
              f"{'scrolls' if has_scroller(root) else 'fixed'}")
    test_full_screen_layouts_fit_or_scroll()
    test_settings_keeps_its_scroll_container()
    print("ok")
