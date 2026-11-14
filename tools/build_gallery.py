#!/usr/bin/env python3
"""
Builds the Core Builds icon gallery: one searchable page of every icon in
tools/catalog.json, each with PNG downloads, for GitHub Pages.

The gallery is generated at deploy time (.github/workflows/pages.yml) and never
committed: 1031 icons are about 2,000 PNGs, and committing them would rewrite
megabytes of history every time an icon changes.

    python3 tools/build_gallery.py --out _site/gallery
    python3 tools/build_gallery.py --out /tmp/g --limit 40      # quick look
    python3 tools/build_gallery.py --out /tmp/g --no-render     # page only

Writes, under --out:
    index.html                    the page (data inline, no external requests)
    icons.json                    the same data, for anyone who wants it
    preview/<drawable>.webp       the shipped 320x180 banner, for the grid
    png/<drawable>_banner.png     1280x720 banner, from assets/banners/
    png/<drawable>.png            512x512 icon, the shipped WebP as PNG
    fonts/outfit_*.ttf            the app's own Outfit cuts, and their licence

Downloads are same-origin, so the browser saves them under the app's name
instead of opening them.
"""
from __future__ import annotations

import argparse
import functools
import html
import io
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
CATALOG = ROOT / "tools" / "catalog.json"
BANNER_SVG = ROOT / "assets" / "banners"
ICON_ART = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
FONT_DIR = ROOT / "app" / "src" / "main" / "res" / "font"
FONT_LICENCE = ROOT / "app" / "src" / "main" / "assets" / "licenses" / "Outfit-OFL.txt"
REPO = "https://github.com/brevityA/CoreBuildsApps"

BANNER_W, BANNER_H = 1280, 720

MAIN_ACTIVITY = ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds" / "iconpack" / "MainActivity.kt"


def chip_order() -> list[tuple[str, str]]:
    """The category chips exactly as the app's own grid filter orders and names them."""
    source = MAIN_ACTIVITY.read_text(encoding="utf-8")
    block = re.search(r"CHIP_ORDER\s*=\s*listOf\((.*?)\n\s*\)", source, re.S)
    if not block:
        raise SystemExit("MainActivity.CHIP_ORDER not found")
    return re.findall(r'"([A-Z_]+)"\s+to\s+"([^"]+)"', block.group(1))


def file_stem(name: str) -> str:
    """'Disney+ Hotstar' -> 'Disney+ Hotstar', minus characters filesystems refuse."""
    stem = re.sub(r'[\\/:*?"<>|]+', "", name).strip()
    return stem or "icon"


def entries(catalog: dict) -> list[dict]:
    """One gallery entry per catalog icon, in catalog order."""
    out = []
    for icon in catalog["icons"]:
        drawable = icon["drawable"]
        packages = sorted({c.split("/", 1)[0] for c in icon.get("components", [])})
        out.append({
            "n": icon["name"],
            "d": drawable,
            "c": icon.get("category", ""),
            "p": packages,
            "k": icon.get("color", ""),
        })
    out.sort(key=lambda e: e["n"].casefold())
    return out


@functools.lru_cache(maxsize=1)
def _aliases() -> dict[str, str]:
    from drawable_art import read_aliases
    return read_aliases(ROOT / "app" / "src" / "main" / "res" / "values")


def shipped_art(drawable: str) -> Path:
    """The WebP the APK ships for a drawable, through the pack's alias files:
    icons whose art is identical to another's share that one file."""
    from drawable_art import art_path
    return art_path(ICON_ART, drawable, _aliases())


def missing_sources(items: list[dict]) -> list[str]:
    problems = []
    for e in items:
        if not (BANNER_SVG / f"{e['d']}.svg").is_file():
            problems.append(f"assets/banners/{e['d']}.svg")
        for name in (e["d"], f"{e['d']}_banner"):
            if not shipped_art(name).is_file():
                problems.append(f"drawable-nodpi art for {name}")
    return problems


def render(items: list[dict], out: Path) -> None:
    from PIL import Image
    from svg_renderer import svg2png

    (out / "png").mkdir(parents=True, exist_ok=True)
    (out / "preview").mkdir(parents=True, exist_ok=True)
    for i, e in enumerate(items, 1):
        d = e["d"]
        png = svg2png(url=str(BANNER_SVG / f"{d}.svg"), output_width=BANNER_W,
                      output_height=BANNER_H, background_color=None)
        (out / "png" / f"{d}_banner.png").write_bytes(png)
        with Image.open(shipped_art(d)) as art:
            buf = io.BytesIO()
            art.convert("RGBA").save(buf, "PNG", optimize=True)
        (out / "png" / f"{d}.png").write_bytes(buf.getvalue())
        shutil.copyfile(shipped_art(f"{d}_banner"), out / "preview" / f"{d}.webp")
        if i % 200 == 0:
            print(f"  rendered {i}/{len(items)}")


def page(items: list[dict], meta: dict) -> str:
    version = meta.get("version", "")
    present = {e["c"] for e in items}
    order = [(code, label) for code, label in chip_order() if code in present]
    chips = "\n".join(
        f'<button type="button" class="chip" data-cat="{html.escape(c)}" aria-pressed="false">'
        f'{html.escape(label)}</button>' for c, label in order)
    data = json.dumps(items, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    labels = json.dumps(dict(order), ensure_ascii=False)
    return TEMPLATE.format(
        count=f"{len(items):,}", version=html.escape(version), chips=chips,
        data=data, labels=labels, repo=REPO)


TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Core Builds Icons</title>
<meta name="description" content="Search and download all {count} Core Builds Android TV icons and banners as PNG.">
<style>
  @font-face {{ font-family: Outfit; font-weight: 400; src: url(fonts/outfit_regular.ttf) format("truetype"); font-display: swap; }}
  @font-face {{ font-family: Outfit; font-weight: 500; src: url(fonts/outfit_medium.ttf) format("truetype"); font-display: swap; }}
  @font-face {{ font-family: Outfit; font-weight: 600; src: url(fonts/outfit_semibold.ttf) format("truetype"); font-display: swap; }}
  :root {{
    color-scheme: dark;
    --night: #0d1117; --panel: #151923; --line: #232a36; --text: #e6edf3;
    --muted: #8b949e; --cyan: #00d4ff; --blue: #4facfe;
  }}
  * {{ box-sizing: border-box; }}
  /* Filtering sets `hidden`; an author `display` (the cards' flex) would otherwise beat it. */
  [hidden] {{ display: none !important; }}
  body {{ margin: 0; background: var(--night); color: var(--text);
         font: 16px/1.5 Outfit, -apple-system, "Segoe UI", Roboto, sans-serif; }}
  a {{ color: var(--blue); }}
  :focus-visible {{ outline: 3px solid var(--cyan); outline-offset: 2px; }}
  header {{ max-width: 1240px; margin: 0 auto; padding: 40px 16px 8px; }}
  .eyebrow {{ font-weight: 600; font-size: .75rem; letter-spacing: .16em; text-transform: uppercase; color: var(--cyan); margin: 0 0 6px; }}
  h1 {{ font-weight: 600; font-size: clamp(1.8rem, 4vw, 2.6rem); line-height: 1.1; margin: 0 0 10px; }}
  .lede {{ color: var(--muted); max-width: 62ch; margin: 0; }}
  .tools {{ position: sticky; top: 0; z-index: 2; background: color-mix(in srgb, var(--night) 92%, transparent);
            backdrop-filter: blur(8px); border-bottom: 1px solid var(--line); }}
  .tools-inner {{ max-width: 1240px; margin: 0 auto; padding: 14px 16px; display: grid; gap: 12px; }}
  .search {{ display: flex; gap: 10px; align-items: center; }}
  .search input {{ flex: 1; min-width: 0; font: inherit; color: var(--text); background: var(--panel);
                  border: 1px solid var(--line); border-radius: 999px; padding: 11px 18px; }}
  .search input::placeholder {{ color: var(--muted); }}
  .count {{ color: var(--muted); font-size: .9rem; white-space: nowrap; font-variant-numeric: tabular-nums; }}
  .chips {{ display: flex; gap: 8px; overflow-x: auto; padding-bottom: 2px; scrollbar-width: thin; }}
  .chip {{ font: 500 .85rem Outfit, sans-serif; color: var(--muted); background: transparent;
           border: 1px solid var(--line); border-radius: 999px; padding: 6px 14px; cursor: pointer; white-space: nowrap; }}
  .chip[aria-pressed="true"] {{ color: var(--night); background: var(--cyan); border-color: var(--cyan); }}
  main {{ max-width: 1240px; margin: 0 auto; padding: 18px 16px 64px; }}
  .grid {{ display: grid; gap: 14px; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); list-style: none; margin: 0; padding: 0; }}
  .card {{ background: var(--panel); border: 1px solid var(--line); border-radius: 14px; overflow: hidden; display: flex; flex-direction: column; }}
  .card img {{ display: block; width: 100%; aspect-ratio: 16 / 9; background: var(--night); }}
  .body {{ padding: 10px 12px 12px; display: grid; gap: 2px; }}
  .name {{ font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }}
  .meta {{ color: var(--muted); font-size: .8rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }}
  .dl {{ display: flex; gap: 8px; margin-top: 8px; }}
  .dl a {{ flex: 1; text-align: center; text-decoration: none; font-weight: 500; font-size: .82rem; color: var(--text);
           border: 1px solid var(--line); border-radius: 999px; padding: 6px 8px; }}
  .dl a:hover {{ border-color: var(--cyan); color: var(--cyan); }}
  .empty {{ color: var(--muted); padding: 40px 0; text-align: center; }}
  footer {{ max-width: 1240px; margin: 0 auto; padding: 0 16px 48px; color: var(--muted); font-size: .85rem; }}
  @media (max-width: 480px) {{ .grid {{ grid-template-columns: 1fr 1fr; gap: 10px; }} .dl {{ flex-direction: column; gap: 6px; }} }}
  @media (prefers-reduced-motion: reduce) {{ * {{ scroll-behavior: auto !important; }} }}
</style>
</head>
<body>
<header>
  <p class="eyebrow">Core Builds Icon Pack {version}</p>
  <h1>Every icon, searchable</h1>
  <p class="lede">{count} Android TV icons and banners. Search by app or package name, then download a
  banner (1280 × 720) or square icon (512 × 512) as a transparent PNG. Want them all applied at once?
  <a href="{repo}#install-any-app">Install the pack</a>.</p>
</header>
<div class="tools">
  <div class="tools-inner">
    <div class="search">
      <input id="q" type="search" placeholder="Search: TiviMate, Plex, com.netflix…" autocomplete="off" aria-label="Search icons by app or package name">
      <span class="count" id="count" aria-live="polite"></span>
    </div>
    <div class="chips" role="group" aria-label="Filter by category">
<button type="button" class="chip" data-cat="" aria-pressed="true">All</button>
{chips}
    </div>
  </div>
</div>
<main>
  <ul class="grid" id="grid"></ul>
  <p class="empty" id="empty" hidden>No icon matches. <a href="{repo}/issues/new/choose">Request it</a>.</p>
</main>
<footer>
  Original Core Builds artwork under the <a href="{repo}/blob/main/LICENSE">MIT licence</a>. App names and brands belong to their
  owners and are used only to identify the apps (<a href="{repo}/blob/main/THIRD_PARTY_NOTICES.md">notices</a>).
  Set in Outfit (SIL OFL 1.1, <a href="fonts/Outfit-OFL.txt">licence</a>).
</footer>
<script>
const ICONS = {data};
const LABELS = {labels};
const grid = document.getElementById("grid"), q = document.getElementById("q");
const count = document.getElementById("count"), empty = document.getElementById("empty");
const fold = s => s.normalize("NFKD").replace(/[\\u0300-\\u036f]/g, "").toLowerCase();
let cat = "";
const cards = ICONS.map(e => {{
  const li = document.createElement("li");
  li.className = "card";
  const file = e.n.replace(/[\\\\/:*?"<>|]+/g, "").trim() || "icon";
  li.innerHTML = `<img loading="lazy" decoding="async" width="320" height="180" alt="" src="preview/${{e.d}}.webp">
    <div class="body"><span class="name"></span><span class="meta"></span>
    <div class="dl"><a href="png/${{e.d}}_banner.png">Banner PNG</a><a href="png/${{e.d}}.png">Icon PNG</a></div></div>`;
  li.querySelector(".name").textContent = e.n;
  li.querySelector(".name").title = e.n;
  const meta = li.querySelector(".meta");
  meta.textContent = [LABELS[e.c] || e.c, e.p[0]].filter(Boolean).join(" · ");
  meta.title = e.p.join("\\n");
  const [banner, icon] = li.querySelectorAll(".dl a");
  banner.download = `${{file}} banner.png`; banner.setAttribute("aria-label", `${{e.n}} banner PNG`);
  icon.download = `${{file}} icon.png`; icon.setAttribute("aria-label", `${{e.n}} icon PNG`);
  grid.appendChild(li);
  return {{ li, e, hay: fold([e.n, e.d, ...e.p].join(" ")) }};
}});
function apply() {{
  const terms = fold(q.value).split(/\\s+/).filter(Boolean);
  let shown = 0;
  for (const c of cards) {{
    const ok = (!cat || c.e.c === cat) && terms.every(t => c.hay.includes(t));
    c.li.hidden = !ok; if (ok) shown++;
  }}
  count.textContent = shown === cards.length ? `${{cards.length.toLocaleString()}} icons`
                                             : `${{shown.toLocaleString()}} of ${{cards.length.toLocaleString()}}`;
  empty.hidden = shown !== 0;
  const url = new URL(location.href);
  q.value ? url.searchParams.set("q", q.value) : url.searchParams.delete("q");
  cat ? url.searchParams.set("cat", cat) : url.searchParams.delete("cat");
  history.replaceState(null, "", url);
}}
document.querySelectorAll(".chip").forEach(b => b.addEventListener("click", () => {{
  cat = b.dataset.cat;
  document.querySelectorAll(".chip").forEach(x => x.setAttribute("aria-pressed", String(x === b)));
  apply();
}}));
q.addEventListener("input", apply);
const params = new URLSearchParams(location.search);
q.value = params.get("q") || "";
const start = params.get("cat") || "";
const chip = [...document.querySelectorAll(".chip")].find(b => b.dataset.cat === start);
if (chip) chip.click(); else apply();
</script>
</body>
</html>
"""


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    ap.add_argument("--out", required=True, type=Path, help="output directory (e.g. _site/gallery)")
    ap.add_argument("--limit", type=int, help="first N icons only, for a quick local look")
    ap.add_argument("--no-render", action="store_true", help="write the page and data, skip the PNGs")
    args = ap.parse_args(argv)

    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    items = entries(catalog)
    if args.limit:
        items = items[: args.limit]
    unlabelled = sorted({e["c"] for e in items} - {code for code, _ in chip_order()})
    if unlabelled:
        print(f"categories with no chip in MainActivity.CHIP_ORDER: {unlabelled}", file=sys.stderr)
        return 1
    problems = missing_sources(items)
    if problems:
        print("missing source art:\n  " + "\n  ".join(problems[:20]), file=sys.stderr)
        return 1

    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)
    (out / "index.html").write_text(page(items, catalog.get("meta", {})), encoding="utf-8")
    (out / "icons.json").write_text(json.dumps(items, ensure_ascii=False, indent=0) + "\n", encoding="utf-8")
    fonts = out / "fonts"
    fonts.mkdir(exist_ok=True)
    for cut in ("regular", "medium", "semibold"):
        shutil.copyfile(FONT_DIR / f"outfit_{cut}.ttf", fonts / f"outfit_{cut}.ttf")
    shutil.copyfile(FONT_LICENCE, fonts / "Outfit-OFL.txt")
    if not args.no_render:
        render(items, out)
    print(f"gallery: {len(items)} icons -> {out}" + (" (page only)" if args.no_render else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
