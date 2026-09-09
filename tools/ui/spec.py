"""Screen spec model for the Core Builds UI generator.

A spec is plain JSON (see tools/ui/screens/*.json):

  {
    "screen": "Details",            # -> DetailsActivity + activity_details.xml
    "package": "tv.corebuilds.iconpack",
    "blocks": [ {"type": "header", ...}, ... ],
    "dpad": {"initialFocus": "action_primary"},
    "anim": {"focusScale": 1.06, "enter": true}
  }

Literal text in text slots ("title": "Details") is auto-extracted into the
generated strings file so layouts never carry hardcoded copy (lint-clean).
References ("title": "@string/wp_title") pass through untouched.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

SCREENS_DIR = Path(__file__).resolve().parent / "screens"

SCREEN_RE = re.compile(r"^[A-Za-z][A-Za-z0-9]*$")
ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")

DEFAULT_PACKAGE = "tv.corebuilds.iconpack"

# Block types the renderer knows. Studio's palette mirrors this list.
BLOCK_TYPES = (
    "header", "actions", "targets", "updatebar", "chips", "search",
    "filterrow", "grid", "hero", "settings", "empty", "selectionbar", "hint",
)

# Slots whose literal values become generated strings: {block: (slot, ...)}.
# Button/row texts are handled separately (see normalise).
TEXT_SLOTS: dict[str, tuple[str, ...]] = {
    "header": ("kicker", "title"),
    "actions": (),
    "targets": (),
    "updatebar": ("label", "sub", "button"),
    "chips": (),
    "search": ("hint",),
    "filterrow": ("hint",),
    "grid": (),
    "hero": ("title", "meta", "kicker"),
    "settings": (),
    "empty": ("title", "body", "primary", "secondary"),
    "selectionbar": ("count", "selectAll", "clear", "export"),
    "hint": ("text",),
}

BUTTON_STYLES = ("cta", "ghost", "chip", "toggle")
GRID_SOURCES = ("icons", "walls", "custom")


def snake(name: str) -> str:
    out = re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()
    return re.sub(r"__+", "_", out)


def is_ref(value: object) -> bool:
    return isinstance(value, str) and value.startswith("@")


@dataclass
class Spec:
    screen: str
    package: str
    blocks: list[dict]
    dpad: dict
    anim: dict
    extra_strings: dict[str, str]
    # literal text extracted during normalise: string name -> value
    extracted: dict[str, str] = field(default_factory=dict)

    @property
    def activity(self) -> str:
        return f"{self.screen}Activity"

    @property
    def layout(self) -> str:
        return f"activity_{snake(self.screen)}"

    @property
    def prefix(self) -> str:
        return snake(self.screen)


def _defaults(spec: Spec, block: dict) -> None:
    """Fill missing optional keys so hand-written specs stay small."""
    t = block["type"]
    if t == "header":
        block.setdefault("kicker", "@string/kicker")
        block.setdefault("title", spec.screen)
        block.setdefault("showCount", True)
        block.setdefault("count_id", f"{spec.prefix}_count")
    elif t in ("search", "filterrow"):
        block.setdefault("hint", "@string/search_hint")
    elif t == "grid":
        block.setdefault("source", "custom")
        block.setdefault("cellDp", 148)
    elif t == "hero":
        block.setdefault("title", spec.screen)
        block.setdefault("art", "@drawable/cb_banner")
    elif t == "updatebar":
        block.setdefault("label", "Update available")
        block.setdefault("sub", "A newer build is ready to download.")
        block.setdefault("button", "Download")
        block.setdefault("button_id", f"{block.get('id', 'update_bar')}_button")
    elif t == "empty":
        block.setdefault("title", "No results")
        block.setdefault("body", "Nothing matches this filter yet.")
        block.setdefault("primary", "Clear search")
        block.setdefault("secondary", "Show all")
    elif t == "selectionbar":
        block.setdefault("count", "0 selected")
        block.setdefault("selectAll", "Select all")
        block.setdefault("clear", "Clear")
        block.setdefault("export", "Export")
    elif t == "hint":
        block.setdefault("text", "Tip")
    elif t in ("chips", "filterrow"):
        block.setdefault("keys", ["ALL"])
        block.setdefault("labels", ["All"])
    if t in ("actions", "selectionbar"):
        for btn in block.get("buttons") or []:
            btn.setdefault("style", "ghost")
            btn.setdefault("text", "Button")


def normalise(raw: dict) -> Spec:
    """Validate a raw spec dict and return a normalised Spec.

    Raises ValueError with a plain-language message on any problem — the CLI
    prints it without a traceback.
    """
    if not isinstance(raw, dict):
        raise ValueError("spec must be a JSON object")
    screen = raw.get("screen", "")
    if not isinstance(screen, str) or not SCREEN_RE.match(screen):
        raise ValueError(
            f'bad "screen" {screen!r}: use PascalCase letters/digits, e.g. "Details"'
        )
    package = raw.get("package", DEFAULT_PACKAGE)
    if not isinstance(package, str) or "." not in package:
        raise ValueError(f'bad "package" {package!r}')

    blocks = raw.get("blocks")
    if not isinstance(blocks, list) or not blocks:
        raise ValueError('"blocks" must be a non-empty list')
    if not any(b.get("type") == "header" for b in blocks if isinstance(b, dict)):
        raise ValueError('spec needs a "header" block (every TV screen has one)')

    spec = Spec(
        screen=screen,
        package=package,
        blocks=[],
        dpad=dict(raw.get("dpad") or {}),
        anim={
            "focusScale": 1.06, "itemScale": 1.04,
            "focusMs": 160, "enter": True, "enterMs": 220, "staggerMs": 28,
        },
        extra_strings=dict(raw.get("strings") or {}),
    )
    anim = raw.get("anim") or {}
    if not isinstance(anim, dict):
        raise ValueError('"anim" must be an object')
    for key in ("focusScale", "itemScale", "focusMs", "enter", "enterMs", "staggerMs"):
        if key in anim:
            spec.anim[key] = anim[key]

    seen_ids: set[str] = set()

    def claim(block_id: str, where: str) -> str:
        if not isinstance(block_id, str) or not ID_RE.match(block_id):
            raise ValueError(
                f'{where}: bad id {block_id!r} (lowercase letters, digits, _)')
        if block_id in seen_ids:
            raise ValueError(f'{where}: duplicate id "{block_id}"')
        seen_ids.add(block_id)
        return block_id

    def extract(block: dict, slot: str, owner: str) -> None:
        value = block.get(slot)
        if value is None or is_ref(value):
            return
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f'{owner}: "{slot}" must be text or a @string ref')
        name = re.sub(r"[^a-z0-9_]", "_", f"{spec.prefix}_{owner}_{slot}".lower())
        spec.extracted[name] = value
        block[slot] = f"@string/{name}"

    def claim_buttons(btns: list, where: str) -> None:
        for n, btn in enumerate(btns):
            if "id" not in btn:
                raise ValueError(f"{where}[{n}]: buttons need an id")
            btn["id"] = claim(btn["id"], f"{where}[{n}]")
            if btn.get("style", "ghost") not in BUTTON_STYLES:
                raise ValueError(
                    f"{where}[{n}]: bad style {btn.get('style')!r} "
                    f"{BUTTON_STYLES}")

    def extract_button_text(btns: list) -> None:
        for btn in btns:
            if btn.pop("_shares", None):
                continue  # text already points at the shared slot ref
            if "text" in btn and not is_ref(btn["text"]):
                name = f"{spec.prefix}_{btn['id']}_label"
                spec.extracted[name] = btn["text"]
                btn["text"] = f"@string/{name}"
            if "sub" in btn and not is_ref(btn["sub"]):
                name = f"{spec.prefix}_{btn['id']}_sub"
                spec.extracted[name] = btn["sub"]
                btn["sub"] = f"@string/{name}"

    for index, raw_block in enumerate(blocks):
        if not isinstance(raw_block, dict):
            raise ValueError(f"blocks[{index}] must be an object")
        btype = raw_block.get("type")
        if btype not in BLOCK_TYPES:
            raise ValueError(
                f"blocks[{index}]: unknown type {btype!r} "
                f"(known: {', '.join(BLOCK_TYPES)})"
            )
        block = json.loads(json.dumps(raw_block))  # deep copy
        where = f'blocks[{index}] ({btype})'
        _defaults(spec, block)

        # Phase 1: claim ids.
        if btype == "filterrow":
            block["chips_id"] = claim(
                block.get("chips_id", "chips"), where + ".chips_id")
            block["search_id"] = claim(
                block.get("search_id", "search"), where + ".search_id")
        elif btype == "header":
            if block.get("showCount", True):
                block["count_id"] = claim(block["count_id"], where + ".count_id")
            claim_buttons(block.get("side") or [], where + ".side")
        elif btype == "actions":
            if not block.get("buttons"):
                raise ValueError(f"{where}: actions needs a buttons list")
            claim_buttons(block["buttons"], where + ".buttons")
        elif btype == "selectionbar":
            block["id"] = claim(block.get("id", "selection_bar"), where)
            if not block.get("buttons"):
                # Synthesised from the text slots (extracted in phase 2);
                # _shares points each button at its slot's string ref.
                block["buttons"] = [
                    {"id": f"{block['id']}_all", "style": "toggle",
                     "_shares": "selectAll"},
                    {"id": f"{block['id']}_clear", "style": "toggle",
                     "_shares": "clear"},
                    {"id": f"{block['id']}_export", "style": "cta",
                     "_shares": "export"},
                ]
            claim_buttons(block["buttons"], where + ".buttons")
        elif btype == "settings":
            rows = block.get("rows")
            if not isinstance(rows, list) or not rows:
                raise ValueError(f"{where}: settings needs a non-empty rows list")
            for n, row in enumerate(rows):
                if "id" not in row:
                    raise ValueError(f"{where}.rows[{n}]: rows need an id")
                row["id"] = claim(row["id"], f"{where}.rows[{n}]")
        elif btype == "empty":
            block["id"] = claim(block.get("id", "empty_state"), where)
            block["primary_id"] = claim(
                block.get("primary_id", f"{block['id']}_primary"),
                where + ".primary_id")
            block["secondary_id"] = claim(
                block.get("secondary_id", f"{block['id']}_secondary"),
                where + ".secondary_id")
        elif btype == "updatebar":
            block["id"] = claim(block.get("id", "update_bar"), where)
            block["button_id"] = claim(block["button_id"], where + ".button_id")
        elif btype == "grid":
            block["id"] = claim(block.get("id", "grid"), where)
            if block["source"] not in GRID_SOURCES:
                raise ValueError(
                    f"{where}: bad source {block['source']!r} {GRID_SOURCES}")
        elif btype in ("chips", "search", "targets"):
            block["id"] = claim(block.get("id", btype), where)
        elif btype in ("hero", "hint") and block.get("id"):
            block["id"] = claim(block["id"], where)

        # Phase 2: literal text -> generated strings.
        for slot in TEXT_SLOTS[btype]:
            extract(block, slot, block.get("id") or btype)
        if btype == "selectionbar":
            for btn in block["buttons"]:
                share = btn.get("_shares")
                if share:
                    btn["text"] = block[share]  # now a @string ref
        extract_button_text(list(block.get("side") or [])
                            + list(block.get("buttons") or []))
        for row in block.get("rows") or []:
            for slot in ("label", "value"):
                if slot in row and not is_ref(row[slot]):
                    name = f"{spec.prefix}_{row['id']}_{slot}"
                    spec.extracted[name] = row[slot]
                    row[slot] = f"@string/{name}"

        spec.blocks.append(block)

    # Chips need parallel keys/labels.
    for block in spec.blocks:
        if block["type"] in ("chips", "filterrow") and not block.get("fromCatalog"):
            keys, labels = block.get("keys") or [], block.get("labels") or []
            if not keys or len(keys) != len(labels):
                raise ValueError(
                    f'chips "{block.get("chips_id", block.get("id"))}": '
                    '"keys" and "labels" must be parallel non-empty lists')
    # fromCatalog chips need a walls grid to read series from.
    if any(b.get("fromCatalog") for b in spec.blocks) and not any(
            b["type"] == "grid" and b.get("source") == "walls"
            for b in spec.blocks):
        raise ValueError('"fromCatalog" chips need a grid with source "walls"')

    # The layout root carries an id for the enter animation; claim it so a
    # block can never collide with it.
    claim(f"{spec.prefix}_root", "layout root")

    # Initial focus must name a focusable id on a VISIBLE level — focusing a
    # GONE bar's button silently fails and the screen opens with no focus.
    focusables = collect_focusable_ids(spec)
    initial = spec.dpad.get("initialFocus")
    if initial is not None:
        if initial not in focusables:
            raise ValueError(
                f'dpad.initialFocus "{initial}" is not a focusable id '
                f'(focusable: {", ".join(sorted(focusables)) or "none"})'
            )
        if initial not in visible_focusable_ids(spec):
            raise ValueError(
                f'dpad.initialFocus "{initial}" sits inside a hidden bar — '
                "initial focus must be visible when the screen opens"
            )
    elif not visible_focusable_ids(spec):
        raise ValueError("spec has no visible focusable view for initial focus")
    return spec


def collect_focusable_ids(spec: Spec) -> set[str]:
    """Every id that renders focusable=true (static views + containers)."""
    out: set[str] = set()
    for block in spec.blocks:
        t = block["type"]
        if t == "header":
            out.update(b["id"] for b in block.get("side") or [])
        elif t in ("actions", "selectionbar"):
            out.update(b["id"] for b in block.get("buttons") or [])
        elif t == "settings":
            out.update(r["id"] for r in block.get("rows") or [])
        elif t == "empty":
            out.update([block["primary_id"], block["secondary_id"]])
        elif t == "updatebar":
            out.add(block["button_id"])
        elif t == "filterrow":
            out.update([block["chips_id"], block["search_id"]])
        elif t in ("chips", "search", "targets", "grid"):
            out.add(block["id"])
    return out


def focus_levels(spec: Spec) -> list[dict]:
    """Focus rows in top-to-bottom order.

    Each level is {"anchors": [ids...], "gone": bool}. Single-anchor levels
    chain 1:1; multi-anchor levels (button rows, chips+search) fan out: the
    row above points at the first anchor, the row below is pointed at from
    the last. Gone levels (update/empty/selection bars) are wired to their
    neighbours but excluded from the static chain — generated Kotlin
    re-chains through them when they appear.
    """
    levels: list[dict] = []
    for block in spec.blocks:
        t = block["type"]
        if t == "header":
            for btn in block.get("side") or []:
                levels.append({"anchors": [btn["id"]], "gone": False})
        elif t == "actions":
            ids = [b["id"] for b in block.get("buttons") or []]
            if ids:
                levels.append({"anchors": ids, "gone": False})
        elif t == "targets":
            levels.append({"anchors": [block["id"]],
                           "gone": not block.get("visible", False)})
        elif t == "updatebar":
            levels.append({"anchors": [block["button_id"]], "gone": True})
        elif t in ("chips", "search", "grid"):
            levels.append({"anchors": [block["id"]], "gone": False})
        elif t == "filterrow":
            levels.append({"anchors": [block["chips_id"], block["search_id"]],
                           "gone": False})
        elif t == "settings":
            for row in block.get("rows") or []:
                levels.append({"anchors": [row["id"]], "gone": False})
        elif t == "empty":
            levels.append({"anchors": [block["primary_id"], block["secondary_id"]],
                           "gone": True})
        elif t == "selectionbar":
            ids = [b["id"] for b in block.get("buttons") or []]
            if ids:
                levels.append({"anchors": ids, "gone": True})
        # hero / hint: presentational, no focus
    return levels


def visible_focusable_ids(spec: Spec) -> set[str]:
    out: set[str] = set()
    for level in focus_levels(spec):
        if not level["gone"]:
            out.update(level["anchors"])
    return out


def compute_links(spec: Spec) -> dict[str, dict[str, str]]:
    """id -> {direction: target id} for every focusable id."""
    levels = focus_levels(spec)
    links: dict[str, dict[str, str]] = {}

    def link(vid: str, direction: str, target: str) -> None:
        links.setdefault(vid, {})[direction] = target

    visible = [lv for lv in levels if not lv["gone"]]
    for i, level in enumerate(visible):
        up = visible[i - 1]["anchors"][-1] if i > 0 else None
        down = visible[i + 1]["anchors"][0] if i < len(visible) - 1 else None
        for anchor in level["anchors"]:
            if up:
                link(anchor, "up", up)
            if down:
                link(anchor, "down", down)

    # Gone bars: wire to the visible neighbours they sit between. Harmless
    # while GONE (an unfocusable view's links are never followed), correct
    # the moment Kotlin reveals the bar and re-chains its neighbours to it.
    for i, level in enumerate(levels):
        if not level["gone"]:
            continue
        prev = next((lv for lv in reversed(levels[:i]) if not lv["gone"]), None)
        nxt = next((lv for lv in levels[i + 1:] if not lv["gone"]), None)
        for anchor in level["anchors"]:
            if prev:
                link(anchor, "up", prev["anchors"][-1])
            if nxt:
                link(anchor, "down", nxt["anchors"][0])

    # Horizontal pairs inside button rows.
    def chain_row(ids: list[str]) -> None:
        for a, b in zip(ids, ids[1:]):
            link(a, "right", b)
            link(b, "left", a)

    for block in spec.blocks:
        t = block["type"]
        if t in ("actions", "selectionbar"):
            chain_row([b["id"] for b in block.get("buttons") or []])
        elif t == "empty":
            chain_row([block["primary_id"], block["secondary_id"]])
    return links


def default_initial_focus(spec: Spec) -> str:
    for level in focus_levels(spec):
        if not level["gone"] and level["anchors"]:
            return level["anchors"][0]
    raise ValueError("spec has no visible focusable view for initial focus")


def load_preset(name: str) -> dict:
    path = SCREENS_DIR / f"{name}.json"
    if not path.exists():
        known = sorted(p.stem for p in SCREENS_DIR.glob("*.json"))
        raise ValueError(f'unknown preset "{name}" (known: {", ".join(known)})')
    return json.loads(path.read_text(encoding="utf-8"))


def list_presets() -> list[str]:
    return sorted(p.stem for p in SCREENS_DIR.glob("*.json"))
