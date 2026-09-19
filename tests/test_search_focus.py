#!/usr/bin/env python3
"""Catch the search field losing the keyboard, and the keyboard losing focus.

Why this exists
---------------
A tester reported "weird focus behavior for the keyboard" while searching the
icon pack. Reading the search path end to end turned that into five separate
defects, none of which any existing gate could see — `check_ui_resources.py`
resolves R.* references and reports focusable views a `nextFocus` chain hops
over, and `test_tv_layout_fit.py` measures layouts against a TV viewport. Both
are static and both are about *reachability*. Nothing asked who owns the cursor
while the user types:

1. `showUpdateAvailable()` ended in an unconditional `button.requestFocus()`.
   It runs on `UpdateChecker`'s network callback, which resolves whenever it
   likes — commonly a second or so after launch, which is exactly when someone
   has reached the search field and started typing. The grab took the cursor out
   of the field mid-word and the IME followed it down. 1.8.11 fixed the bar's
   *reachability* and deliberately kept the grab; the grab was the bug.
2. `afterTextChanged` filtered inline. One character meant one pass over 943
   icons, one `DiffUtil.calculateDiff` — with move detection on, which cannot
   report a move for an order-preserving subset — and one full grid layout, all
   on the main thread inside the text watcher.
3. The keyboard's Search key was dead. The field declares
   `imeOptions="actionSearch"` and nothing registered an
   `OnEditorActionListener`, so the one key that means "I have finished typing"
   fell through to TextView's default: hide the IME, leave focus where it was.
4. Nothing decided where focus goes when the focused icon is filtered out, or
   when `bindEmptyState` hides the container the cursor is sitting in — the grid
   when the last result goes, the empty state when results come back, which is
   what pressing its own Clear filter does. Android either drops focus outright
   and restores the window default on the next traversal (the Apply button at the
   top of the screen, two stops from the results being read), or leaves it on a
   view that is no longer shown.
5. The three chip rows on this screen kept RecyclerView's default item animator.
   `ChipAdapter.select()` answers a press with two `notifyItemChanged` calls, and
   the default change animation swaps the pressed chip for a fresh ViewHolder,
   dropping the highlight on the press that moved it. `WallpapersActivity`
   already guarded its own chip row — with a comment claiming the main screen's
   did too, which it did not.

What it checks
--------------
Scoped to `app/src/main/java`, which Pop compiles as its own source set, so one
fix covers both packs. Pixel Neon keeps a deliberate fork of this Kotlin under
its own package and is *not* covered here; its `MainActivity` still carries
(1), (2), (3) and (5), and has no empty-state or focus-restore logic at all, so
(4) has nothing there to catch yet.

Run against the pre-fix source, 14 of the 15 checks fail, so the gate reproduces
the report rather than describing it. The 15th,
`test_search_field_keeps_its_focus_routes`, is a lock on routes the layouts
already had (`imeOptions`, and the `nextFocus` edges between field, grid and
tile) — the Kotlin half depends on them, and nothing else in the repo would
notice one being edited away.

Wiring: this file has test_* functions but no TestCase, so `unittest discover`
skips it the same way it skips test_tv_layout_fit.py and
test_resource_parity.py. CI runs the Python suites by naming each file, so this
one is listed explicitly in build.yml and suite-ci.yml. Add it there, or it is
not a gate.
"""
from __future__ import annotations

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

KOTLIN = ROOT / "app/src/main/java/tv/corebuilds/iconpack"

# Pop compiles ../app/src/main/java, so this Kotlin runs against Pop's mirrored
# resources too. Both layouts are checked; they are byte-identical in the
# search row and CI diffs the mirror.
MODULES = {
    "app": ROOT / "app/src/main/res/layout",
    "pop": ROOT / "pop/src/main/res/layout",
}


def source(name: str) -> str:
    path = KOTLIN / name
    assert path.exists(), f"{path.relative_to(ROOT)} is gone"
    return path.read_text(encoding="utf-8")


def function_body(text: str, name: str) -> str:
    """The brace-matched body of `fun name(...)`, checks scoped to one function.

    Scoping matters: "does the file mention currentFocus somewhere" passes for
    the pre-fix source, which read it in an unrelated function while grabbing
    focus unconditionally in this one.
    """
    match = re.search(rf"\bfun\s+{re.escape(name)}\s*\(", text)
    assert match, f"no block-bodied fun {name}() left in this source"
    open_brace = text.index("{", match.end())
    between = text[match.end():open_brace]
    # An expression body (`fun x() = when (...) {`) has no braces of its own, so
    # the first `{` after the signature belongs to something else entirely and
    # the measurement would be quietly wrong.
    assert "=" not in between, (
        f"fun {name}() looks like an expression body; this helper measures "
        f"block bodies only"
    )
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace:index + 1]
    raise AssertionError(f"unbalanced braces after fun {name}()")


def indent_of(line: str) -> int:
    return len(line) - len(line.lstrip())


def code_lines(body: str) -> list[str]:
    """A function body's code lines: no blanks, no braces, no prose.

    Comments in this codebase name the bug they prevent, which means they quote
    the call they replaced. A check that scans raw lines matches the story
    instead of the source — it flagged the sentence "the unconditional
    requestFocus() this replaces" as an unguarded grab.
    """
    out = []
    for line in body.splitlines()[1:]:
        stripped = line.strip()
        if not stripped or stripped in ("}", "/*", "*/"):
            continue
        if stripped.startswith(("//", "*", "/*")):
            continue
        out.append(line)
    return out


def body_indent(body: str) -> int:
    """The indentation of a function body's own statements (not nested blocks)."""
    lines = code_lines(body)
    assert lines, "empty function body"
    return min(indent_of(line) for line in lines)


def code_of(body: str) -> str:
    """A function body's code as one string, for `x in code` checks."""
    return "\n".join(code_lines(body))


def by_id(root: ET.Element, view_id: str) -> ET.Element | None:
    for element in root.iter():
        if element.get(ANDROID + "id") in (f"@+id/{view_id}", f"@id/{view_id}"):
            return element
    return None


# ---------------------------------------------------------------------------
# 1. A bar that appears must not take the cursor from whoever already has it.
# ---------------------------------------------------------------------------

def test_update_bar_reveals_without_grabbing_focus():
    body = function_body(source("MainActivity.kt"), "showUpdateAvailable")
    code = code_of(body)
    grabs = [line for line in code_lines(body) if "requestFocus()" in line]
    assert grabs, (
        "showUpdateAvailable no longer offers the Download button as the initial "
        "focus at all; a screen nobody has touched should still land on it"
    )
    assert "currentFocus" in code, (
        "showUpdateAvailable grabs focus without reading currentFocus. The check "
        "resolves on a network callback, which lands whenever it likes — "
        "including mid-keystroke in the search field, taking the cursor and the "
        "keyboard with it."
    )
    base = body_indent(body)
    for line in grabs:
        assert indent_of(line) > base, (
            f"showUpdateAvailable calls requestFocus() at the top level of its "
            f"body ({line.strip()!r}); it has to sit inside the currentFocus "
            f"guard"
        )


def test_initial_focus_does_not_override_a_cursor_already_placed():
    code = code_of(function_body(source("MainActivity.kt"), "onCreate"))
    assert "window.decorView.post" in code, "the startup focus grab is gone"
    startup = code[code.index("window.decorView.post"):]
    assert "currentFocus" in startup, (
        "the startup focus grab runs on a post, i.e. after the first traversal, "
        "and must not pull the cursor off a control the platform or the user "
        "already focused"
    )


# ---------------------------------------------------------------------------
# 2. Typing is a burst; filtering is one pass at the end of it.
# ---------------------------------------------------------------------------

def test_keystrokes_are_debounced():
    code = code_of(function_body(source("MainActivity.kt"), "bindSearch"))
    assert "scheduleFilter()" in code, (
        "the text watcher does not schedule; see applyFilter's cost per character"
    )
    assert "applyFilter()" not in code, (
        "bindSearch filters inline from afterTextChanged: one character, one "
        "943-icon filter, one DiffUtil pass and one full grid layout on the main "
        "thread"
    )


def test_debounce_constant_is_one_key_repeat_long():
    text = source("MainActivity.kt")
    match = re.search(r"FILTER_DEBOUNCE_MS\s*=\s*(\d+)L", text)
    assert match, "the debounce interval is no longer a named constant"
    millis = int(match.group(1))
    # A remote repeats a held key at roughly 50ms. Anything shorter filters per
    # character again; anything much longer puts the results behind the caret.
    assert 50 <= millis <= 250, (
        f"FILTER_DEBOUNCE_MS is {millis}ms: below ~50ms a held key still filters "
        f"per repeat, and past ~250ms the grid visibly trails the caret"
    )


def test_pending_filter_is_cancelled_on_destroy():
    code = code_of(function_body(source("MainActivity.kt"), "onDestroy"))
    assert "removeCallbacksAndMessages(null)" in code, (
        "a queued filter survives the activity: a Runnable holding it, and a "
        "pass over a dead view tree"
    )


def test_grid_diff_skips_move_detection():
    code = code_of(function_body(source("IconAdapter.kt"), "submit"))
    assert re.search(r"calculateDiff\([\s\S]*?\},\s*false\s*\)", code), (
        "IconAdapter.submit calls the single-argument DiffUtil.calculateDiff, "
        "which turns move detection on. A filter is an order-preserving subset "
        "of the catalogue, so the second pass over matched items can only come "
        "back empty — on every keystroke of a 943 icon search."
    )


def test_focus_owner_is_read_not_scanned_for():
    code = code_of(function_body(source("MainActivity.kt"), "applyFilter"))
    assert "focusedChild" in code, (
        "applyFilter no longer reads the grid's focused child; the loop it "
        "replaced asked every one of 943 view holders whether it had focus, per "
        "keystroke, to find the one that did"
    )


# ---------------------------------------------------------------------------
# 3. The keyboard's action key means "I am done typing".
# ---------------------------------------------------------------------------

def test_search_action_is_consumed():
    text = source("MainActivity.kt")
    bound = code_of(function_body(text, "bindSearch"))
    assert "setOnEditorActionListener" in bound, (
        "nothing consumes the keyboard's Search key, so imeOptions=actionSearch "
        "falls through to TextView's default: hide the IME, move nothing"
    )
    assert "IME_ACTION_SEARCH" in bound, (
        "the listener does not name IME_ACTION_SEARCH, which is the action the "
        "field's imeOptions=actionSearch and a hardware Enter both arrive as"
    )
    code = code_of(function_body(text, "onSearchAction"))
    assert "hideIme(" in code, "Search must close the keyboard"
    assert "applyFilterNow()" in code, (
        "Search must flush a pending debounce before moving focus, or the filter "
        "lands afterwards and re-decides focus from inside the grid"
    )
    assert "requestFocus()" in code, "Search must put the cursor on a result"


def test_keyboard_follows_the_field():
    code = code_of(function_body(source("MainActivity.kt"), "bindSearch"))
    assert "setOnFocusChangeListener" in code, (
        "the search field does not react to its own focus; a D-pad focus does not "
        "reliably open the IME, and a keyboard left open over the results covers "
        "what the cursor moved into"
    )
    assert "showSoftInput" in code, "focus in must ask for the keyboard"
    assert "hideIme(" in code or "hideSoftInputFromWindow" in code, (
        "focus out must put the keyboard away"
    )


# ---------------------------------------------------------------------------
# 4. Focus always has somewhere to live after a filter.
# ---------------------------------------------------------------------------

def test_filtered_out_icon_keeps_the_cursor_nearby():
    text = source("MainActivity.kt")
    keep = code_of(function_body(text, "keepGridCursor"))
    assert "nearestSurvivor(" in keep, (
        "keepGridCursor has no fallback for an icon the filter dropped. Left to "
        "the platform, RecyclerView's preserve-focus-after-layout pass finds the "
        "remembered item id gone and hands focus to its first focusable child: "
        "the ring teleports to the top-left tile."
    )
    nearest = code_of(function_body(text, "nearestSurvivor"))
    assert "catalogOrder" in nearest, (
        "nearness has to be measured in catalogue order; positions in the "
        "filtered list are not comparable across two different lists"
    )


def test_dropped_focus_is_adopted_not_left_to_the_window_default():
    text = source("MainActivity.kt")
    settle = code_of(function_body(text, "settleFilterFocus"))
    assert "adoptDroppedFocus(" in settle, (
        "nothing catches focus the window has dropped"
    )
    assert settle.count("adoptDroppedFocus(") >= 2, (
        "settleFilterFocus only repairs one of its branches; both the empty "
        "result set and the returning one can strand the cursor"
    )
    adopt = code_of(function_body(text, "adoptDroppedFocus"))
    assert "currentFocus" in adopt, (
        "adoptDroppedFocus must act only when focus really was lost, or it takes "
        "the cursor from a user who is still typing"
    )
    assert "decorView" in adopt, (
        "a window with focus on its decor view has no cursor either"
    )
    assert "isStranded(" in adopt, (
        "adoptDroppedFocus ignores a cursor left on a view the filter just hid. "
        "Pressing the empty state's Clear filter brings the results back, which "
        "hides the button the cursor is on."
    )
    stranded = code_of(function_body(text, "isStranded"))
    assert "isShown" in stranded, "nothing asks whether the focused view is shown"
    assert "empty_clear_search" in stranded and "empty_clear_filter" in stranded, (
        "the empty state's undo buttons are the views a returning result set hides"
    )
    assert "findContainingViewHolder" in stranded, (
        "a tile hidden with its grid has to count as stranded too"
    )
    empty = code_of(function_body(text, "emptyActionView"))
    assert "empty_clear_search" in empty and "empty_clear_filter" in empty, (
        "when the grid is GONE the cursor belongs on whichever undo the empty "
        "state is actually offering"
    )


def test_queued_filter_does_not_land_behind_another_window():
    code = code_of(function_body(source("MainActivity.kt"), "onPause"))
    assert "applyFilterNow()" in code, (
        "a debounce queued by the last keystroke fires after the activity is "
        "gone from the screen, and decides focus against a window nobody is "
        "looking at"
    )
    assert "filterPending" in code, (
        "onPause must flush only when something is actually queued, or every "
        "pause pays for a 943 icon filter and a diff"
    )


def test_every_chip_row_disables_the_change_animation():
    """A chip press must not cost the chip its highlight.

    `ChipAdapter.select()` answers a press with two `notifyItemChanged` calls.
    RecyclerView's default change animation replaces the changed chip with a
    fresh ViewHolder and cross-fades the pair, which drops focus from the chip
    the user just pressed: on a D-pad the highlight vanishes on the press that
    was supposed to move it, and the window then restores its own default — the
    Apply button. `WallpapersActivity` already carried the guard, with a comment
    claiming the main screen's chip row carried it too. It did not.
    """
    rows = {
        "MainActivity.kt": ("bindChips", "bindPickShape", "bindApplyButton"),
        "WallpapersActivity.kt": ("bindChips",),
    }
    checked = 0
    for name, functions in sorted(rows.items()):
        text = source(name)
        for function in functions:
            code = code_of(function_body(text, function))
            assert "itemAnimator = null" in code, (
                f"{name}: {function}() hosts a ChipAdapter row without "
                f"itemAnimator = null, so the default change animation drops "
                f"focus from the chip the user pressed"
            )
            checked += 1
    assert checked == 4, f"expected 4 chip rows, checked {checked}"


def test_filter_runs_before_focus_is_settled():
    """Focus is decided after the layout pass the diff scheduled, not during it."""
    code = code_of(function_body(source("MainActivity.kt"), "applyFilter"))
    assert "settleFilterFocus(" in code, "applyFilter no longer settles focus"
    submit_at = code.index("adapter.submit(")
    settle_at = code.index("settleFilterFocus(")
    assert submit_at < settle_at, "focus is settled before the filter is submitted"
    posted = code.rindex(".post", 0, settle_at)
    assert posted > submit_at, (
        "settleFilterFocus is not called from a post: a filtered-out tile is "
        "detached during the layout pass submit() schedules, so the drop has not "
        "happened yet when the decision is taken inline"
    )


# ---------------------------------------------------------------------------
# The layout half: the escape routes between field, chips and grid.
# ---------------------------------------------------------------------------

def test_search_field_keeps_its_focus_routes():
    checked = 0
    for module, layout_dir in MODULES.items():
        root = ET.parse(layout_dir / "activity_main.xml").getroot()
        search = by_id(root, "search")
        assert search is not None, f"{module}: no @+id/search in activity_main.xml"
        assert search.get(ANDROID + "imeOptions") == "actionSearch", (
            f"{module}: the search field's imeOptions is not actionSearch, so the "
            f"keyboard offers no Search key for onSearchAction to consume"
        )
        assert search.get(ANDROID + "nextFocusDown") == "@id/grid", (
            f"{module}: Down from the search field no longer names the grid"
        )
        assert search.get(ANDROID + "focusable") == "true", (
            f"{module}: the search field must stay focusable for a D-pad"
        )
        grid = by_id(root, "grid")
        assert grid is not None, f"{module}: no @+id/grid"
        assert grid.get(ANDROID + "nextFocusUp") == "@id/search", (
            f"{module}: Up from the grid no longer names the search field, so the "
            f"keyboard cannot be returned to once focus is in the results"
        )
        tile_root = ET.parse(layout_dir / "item_icon.xml").getroot()
        assert tile_root.get(ANDROID + "nextFocusUp") == "@id/search", (
            f"{module}: item_icon.xml lost nextFocusUp=@id/search. The tile is "
            f"inside the RecyclerView, so the reference only resolves once "
            f"RecyclerView.focusSearch delegates to its parent — which is exactly "
            f"the top-row case it exists for."
        )
        checked += 1
    assert checked == len(MODULES), f"only {checked} of {len(MODULES)} modules checked"


if __name__ == "__main__":
    for name, layout_dir in MODULES.items():
        main = ET.parse(layout_dir / "activity_main.xml").getroot()
        field = by_id(main, "search")
        print(
            f"{name + '/activity_main.xml':40s} search: "
            f"imeOptions={field.get(ANDROID + 'imeOptions')} "
            f"nextFocusDown={field.get(ANDROID + 'nextFocusDown')}"
        )
    tests = [value for key, value in sorted(globals().items()) if key.startswith("test_")]
    for test in tests:
        test()
        print(f"  ok  {test.__name__}")
    print(f"{len(tests)} search-focus checks passed")
