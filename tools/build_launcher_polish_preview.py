#!/usr/bin/env python3
"""Build the self-contained 0.3 Core Builds Launcher design preview.

All artwork comes from this repository and is embedded as a data URI. The HTML
uses no external script, font, image or network request. It is an interactive
design illustration, not an APK capture or evidence of Android remote behavior.
"""

from __future__ import annotations

import argparse
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs/mockups/core-launcher-polish.html"

ASSET_PATHS = {
    "mark": ROOT / "launcher/app/src/main/res/drawable-nodpi/core_builds_mark.webp",
    "launcher_banner": ROOT / "launcher/app/src/main/res/drawable-nodpi/core_builds_banner.webp",
    "core_builds": ROOT / "app/src/main/res/drawable-nodpi/corebuilds.webp",
    "core_builds_banner": ROOT / "app/src/main/res/drawable-nodpi/corebuilds_banner.webp",
    "core_line": ROOT / "app/src/main/res/drawable-nodpi/coreline.webp",
    "core_line_banner": ROOT / "app/src/main/res/drawable-nodpi/coreline_banner.webp",
    "core_shift": ROOT / "app/src/main/res/drawable-nodpi/coreshift.webp",
    "core_shift_banner": ROOT / "app/src/main/res/drawable-nodpi/coreshift_banner.webp",
    "core_eq": ROOT / "app/src/main/res/drawable-nodpi/coreeq.webp",
    "core_eq_banner": ROOT / "app/src/main/res/drawable-nodpi/coreeq_banner.webp",
    "abc_news": ROOT / "app/src/main/res/drawable-nodpi/abcnews.webp",
    "abc_news_banner": ROOT / "app/src/main/res/drawable-nodpi/abcnews_banner.webp",
    "bravia": ROOT / "app/src/main/res/drawable-nodpi/bravia_core.webp",
    "bravia_banner": ROOT / "app/src/main/res/drawable-nodpi/bravia_core_banner.webp",
    "aerial": ROOT / "app/src/main/res/drawable-nodpi/aerialviews.webp",
    "aerial_banner": ROOT / "app/src/main/res/drawable-nodpi/aerialviews_banner.webp",
    "aurora": ROOT / "app/src/main/res/drawable-nodpi/aurora_player.webp",
    "aurora_banner": ROOT / "app/src/main/res/drawable-nodpi/aurora_player_banner.webp",
    "scorebox": ROOT / "app/src/main/res/drawable-nodpi/scorebox.webp",
    "scorebox_banner": ROOT / "app/src/main/res/drawable-nodpi/scorebox_banner.webp",
    "iplayer": ROOT / "app/src/main/res/drawable-nodpi/bbc_iplayer.webp",
    "iplayer_banner": ROOT / "app/src/main/res/drawable-nodpi/bbc_iplayer_banner.webp",
    "adb": ROOT / "app/src/main/res/drawable-nodpi/adbappcontrol.webp",
    "adb_banner": ROOT / "app/src/main/res/drawable-nodpi/adbappcontrol_banner.webp",
    "aiostreams": ROOT / "app/src/main/res/drawable-nodpi/aiostreams.webp",
    "aiostreams_banner": ROOT / "app/src/main/res/drawable-nodpi/aiostreams_banner.webp",
    "sai": ROOT / "app/src/main/res/drawable-nodpi/sai.webp",
    "sai_banner": ROOT / "app/src/main/res/drawable-nodpi/sai_banner.webp",
    "apktime": ROOT / "app/src/main/res/drawable-nodpi/apktime.webp",
    "apktime_banner": ROOT / "app/src/main/res/drawable-nodpi/apktime_banner.webp",
}

APPS = [
    {"label": "Core Builds Icon Pack", "package": "tv.corebuilds.iconpack", "icon": "core_builds", "banner": "core_builds_banner", "tv": True, "usage": 9, "hue": 190, "age": 12},
    {"label": "Core Line", "package": "tv.corebuilds.line", "icon": "core_line", "banner": "core_line_banner", "tv": True, "usage": 7, "hue": 205, "age": 10},
    {"label": "Core Shift", "package": "tv.corebuilds.shift", "icon": "core_shift", "banner": "core_shift_banner", "tv": True, "usage": 5, "hue": 280, "age": 8},
    {"label": "Core EQ", "package": "tv.corebuilds.eq", "icon": "core_eq", "banner": "core_eq_banner", "tv": True, "usage": 8, "hue": 155, "age": 6},
    {"label": "ABC News", "package": "au.net.abc.iview", "icon": "abc_news", "banner": "abc_news_banner", "tv": True, "usage": 4, "hue": 18, "age": 4},
    {"label": "Bravia Core", "package": "com.sony.bravia.core", "icon": "bravia", "banner": "bravia_banner", "tv": True, "usage": 3, "hue": 230, "age": 3},
    {"label": "Aerial Views", "package": "com.neilturner.aerialviews", "icon": "aerial", "banner": "aerial_banner", "tv": True, "usage": 6, "hue": 200, "age": 2},
    {"label": "Aurora Player", "package": "com.aurora.player", "icon": "aurora", "banner": "aurora_banner", "tv": False, "usage": 2, "hue": 265, "age": 1},
    {"label": "Scorebox", "package": "tv.corebuilds.scorebox", "icon": "scorebox", "banner": "scorebox_banner", "tv": False, "usage": 2, "hue": 35, "age": 9},
    {"label": "BBC iPlayer", "package": "com.bbc.iplayer", "icon": "iplayer", "banner": "iplayer_banner", "tv": True, "usage": 4, "hue": 340, "age": 7},
    {"label": "ADB App Control", "package": "com.cgutman.androidremotedebugger", "icon": "adb", "banner": "adb_banner", "tv": False, "usage": 1, "hue": 95, "age": 5},
    {"label": "AIOStreams", "package": "com.aiostreams.tv", "icon": "aiostreams", "banner": "aiostreams_banner", "tv": False, "usage": 5, "hue": 175, "age": 11},
    {"label": "SAI", "package": "com.aefyr.sai", "icon": "sai", "banner": "sai_banner", "tv": False, "usage": 1, "hue": 120, "age": 13},
    {"label": "APKTime", "package": "com.apktime.tv", "icon": "apktime", "banner": "apktime_banner", "tv": False, "usage": 2, "hue": 45, "age": 14},
]

TEMPLATE = r'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'none'; form-action 'none'; object-src 'none'; base-uri 'none'">
<title>Core Builds Launcher · 0.3 design preview</title>
<style>
:root {
  --void: #04070F;
  --night: #0D1117;
  --panel: #151923;
  --line: #2B3948;
  --cyan: #00D4FF;
  --ink: #E6EDF3;
  --muted: #8B949E;
  --accent: rgba(0, 212, 255, .24);
  --focus-ms: 150ms;
  --reveal-ms: 170ms;
  --scroll-ms: 220ms;
  --backdrop-ms: 280ms;
  --panel-ms: 200ms;
  --dim: .72;
}
* { box-sizing: border-box; }
html, body { height: 100%; }
body {
  margin: 0;
  background: radial-gradient(circle at 50% 10%, #16202e 0, #070a11 45%, #02040a 100%);
  color: var(--ink);
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  display: grid;
  place-items: center;
  overflow: hidden;
}
.stage { width: min(96vw, 1600px); }
.stage-label {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 0 4px 10px;
  color: #91a1b2;
  font-size: 12px;
  letter-spacing: .08em;
  text-transform: uppercase;
}
.tv {
  position: relative;
  aspect-ratio: 16 / 9;
  width: 100%;
  overflow: hidden;
  border: 1px solid #263241;
  border-radius: 22px;
  background: var(--void);
  box-shadow: 0 36px 90px rgba(0,0,0,.55), 0 0 0 1px rgba(255,255,255,.03) inset;
  isolation: isolate;
}
.focus-backdrop {
  position: absolute;
  inset: -12%;
  z-index: -3;
  background:
    radial-gradient(circle at 74% 30%, var(--accent) 0, transparent 46%),
    radial-gradient(circle at 22% 60%, rgba(0,212,255,.09) 0, transparent 38%),
    linear-gradient(120deg, #060b14, #101927 55%, #050810);
  transition: background var(--backdrop-ms) ease, filter var(--backdrop-ms) ease;
}
body[data-background="core"] .focus-backdrop { filter: hue-rotate(42deg) saturate(1.15); }
body[data-background="void"] .focus-backdrop { background: var(--void); }
.scrim {
  position: absolute;
  inset: 0;
  z-index: -2;
  background:
    linear-gradient(90deg, rgba(4,7,15,.96) 0, rgba(4,7,15,var(--dim)) 46%, rgba(4,7,15,.18) 100%),
    linear-gradient(0deg, rgba(4,7,15,.98) 0, rgba(4,7,15,.74) 25%, transparent 62%);
  transition: opacity var(--backdrop-ms) ease;
}
header {
  position: relative;
  z-index: 2;
  height: 11%;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 3.2%;
}
.brand { display: flex; align-items: center; gap: 12px; min-width: 25%; }
.brand img { width: 30px; height: 30px; object-fit: contain; filter: drop-shadow(0 0 10px rgba(0,212,255,.35)); }
.brand strong { font-size: clamp(14px, 1.35vw, 22px); letter-spacing: .16em; text-transform: uppercase; }
.clock { margin-left: auto; text-align: right; line-height: 1.15; }
.clock b { font-size: clamp(18px, 1.8vw, 30px); letter-spacing: .04em; }
.clock small { display: block; color: var(--muted); font-size: clamp(9px, .85vw, 14px); letter-spacing: .08em; }
.header-actions { display: flex; align-items: center; gap: 10px; }
button { font: inherit; color: inherit; }
.pill {
  border: 1px solid #344456;
  background: rgba(13,17,23,.72);
  border-radius: 999px;
  padding: .55em 1.15em;
  font-size: clamp(9px, .82vw, 14px);
  letter-spacing: .12em;
  text-transform: uppercase;
  cursor: pointer;
  transition: transform var(--focus-ms) ease, border-color var(--focus-ms) ease, box-shadow var(--focus-ms) ease, background var(--focus-ms) ease;
}
.pill.primary { border-color: var(--cyan); color: #041018; background: var(--cyan); font-weight: 750; }
.pill:focus-visible, .pill:hover {
  outline: none;
  transform: scale(1.06);
  border-color: var(--cyan);
  box-shadow: 0 0 0 3px rgba(0,212,255,.26), 0 0 28px rgba(0,212,255,.28);
}
.hero {
  position: relative;
  z-index: 1;
  height: 26%;
  padding: 0 3.2%;
  display: flex;
  align-items: center;
  gap: 2.4%;
}
.hero-art {
  width: 18%;
  height: 78%;
  flex: none;
  border: 1px solid rgba(255,255,255,.12);
  border-radius: 14px;
  background: rgba(13,17,23,.68);
  display: grid;
  place-items: center;
  overflow: hidden;
  box-shadow: 0 16px 44px rgba(0,0,0,.32);
}
.hero-art img { max-width: 82%; max-height: 82%; object-fit: contain; }
.hero-copy { min-width: 0; }
.eyebrow { color: var(--cyan); font-size: clamp(9px, .8vw, 13px); letter-spacing: .18em; text-transform: uppercase; margin-bottom: .65em; }
.hero h1 {
  margin: 0;
  font-size: clamp(24px, 3.1vw, 52px);
  line-height: 1.03;
  letter-spacing: -.025em;
  max-width: 82%;
  text-shadow: 0 4px 22px rgba(0,0,0,.45);
}
.hero p { margin: .7em 0 0; color: #b8c4d0; font-size: clamp(10px, .95vw, 16px); }
.hero-copy.reveal { animation: reveal var(--reveal-ms) ease-out both; }
@keyframes reveal { from { opacity: .35; transform: translateY(8px); } to { opacity: 1; transform: none; } }
#shelves {
  position: relative;
  z-index: 1;
  height: 54%;
  padding: 0 3.2% 2.6%;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #344456 transparent;
}
#shelves::-webkit-scrollbar { width: 6px; }
#shelves::-webkit-scrollbar-thumb { background: #344456; border-radius: 99px; }
.shelf { margin-bottom: 1.5%; }
.shelf-head { display: flex; align-items: baseline; gap: 16px; margin: 0 0 8px; }
.shelf-head h2 { margin: 0; font-size: clamp(12px, 1.1vw, 18px); letter-spacing: .04em; }
.shelf-head h2 span { color: var(--muted); font-weight: 450; }
.shelf-head .note { color: var(--muted); font-size: clamp(8px, .72vw, 12px); }
.card-row, .app-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.app-card, .add-card, .result-row, .setting-row {
  position: relative;
  border: 1px solid #263241;
  background: linear-gradient(145deg, rgba(21,25,35,.94), rgba(9,13,20,.94));
  border-radius: 12px;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: transform var(--focus-ms) ease, border-color var(--focus-ms) ease, box-shadow var(--focus-ms) ease, background var(--focus-ms) ease;
}
.app-card { padding: 0; min-height: 0; aspect-ratio: 16 / 9; }
.app-card img { width: 100%; height: 100%; object-fit: contain; padding: 8%; filter: saturate(.94); }
.app-card[data-tv="false"] img { width: 56%; height: 56%; position: absolute; left: 8%; top: 22%; padding: 0; }
.app-card[data-tv="false"] .card-label { position: absolute; left: 40%; right: 5%; top: 50%; transform: translateY(-50%); font-size: clamp(9px, .8vw, 13px); }
.card-label { position: absolute; left: 0; right: 0; bottom: 0; padding: 18% 7% 6%; background: linear-gradient(0deg, rgba(4,7,15,.9), transparent); font-size: clamp(8px, .68vw, 11px); letter-spacing: .02em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.app-card[data-tv="true"] .card-label { opacity: .72; }
.app-card:focus-visible, .add-card:focus-visible, .result-row:focus-visible, .setting-row:focus-visible {
  outline: none;
  transform: scale(1.06);
  border-color: var(--cyan);
  box-shadow: 0 0 0 3px rgba(0,212,255,.24), 0 0 26px rgba(0,212,255,.26), inset 0 0 0 2px rgba(0,212,255,.62);
  z-index: 3;
}
.add-card { aspect-ratio: 16 / 9; display: grid; place-items: center; border-style: dashed; color: var(--cyan); font-size: clamp(18px, 2vw, 34px); background: rgba(0,212,255,.035); }
.add-card span { font-size: clamp(8px, .7vw, 12px); letter-spacing: .1em; text-transform: uppercase; margin-left: .8em; }
footer {
  position: absolute;
  z-index: 2;
  left: 0; right: 0; bottom: 0;
  height: 7%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 3.2%;
  background: rgba(4,7,15,.76);
  border-top: 1px solid rgba(139,148,158,.16);
  color: #8292a3;
  font-size: clamp(8px, .68vw, 12px);
  letter-spacing: .08em;
}
footer kbd { font: inherit; border: 1px solid #3a4a5c; border-radius: 4px; padding: .1em .4em; color: var(--ink); }
.panel, .search-overlay {
  position: absolute;
  z-index: 8;
  visibility: hidden;
  pointer-events: none;
}
.panel {
  inset: 0 0 0 auto;
  width: min(32%, 420px);
  background: rgba(13,17,23,.97);
  border-left: 1px solid #344456;
  box-shadow: -28px 0 64px rgba(0,0,0,.5);
  transform: translateX(105%);
  opacity: .6;
  transition: transform var(--panel-ms) ease, opacity var(--panel-ms) ease, visibility var(--panel-ms);
  display: flex;
  flex-direction: column;
}
.panel.open, .search-overlay.open { visibility: visible; pointer-events: auto; opacity: 1; }
.panel.open { transform: none; }
.panel-head { padding: 5% 6% 3%; border-bottom: 1px solid #263241; }
.panel-head .eyebrow { margin-bottom: .4em; }
.panel-head h2 { margin: 0; font-size: clamp(18px, 1.8vw, 30px); }
.panel-head p { color: var(--muted); font-size: clamp(9px, .78vw, 13px); line-height: 1.45; margin: .6em 0 0; }
.settings-list { padding: 2% 6% 6%; overflow-y: auto; scrollbar-width: thin; scrollbar-color: #344456 transparent; }
.setting-group { margin-top: 3%; }
.setting-group h3 { margin: 0 0 6px; color: var(--cyan); font-size: clamp(9px, .72vw, 12px); letter-spacing: .12em; text-transform: uppercase; display: flex; justify-content: space-between; }
.setting-group h3 span { color: var(--muted); letter-spacing: .03em; text-transform: none; }
.setting-row { width: 100%; display: block; margin: 5px 0; padding: 8px 12px; border-radius: 9px; }
.setting-row strong { display: block; font-size: clamp(10px, .82vw, 14px); }
.setting-row small { display: block; color: var(--muted); font-size: clamp(8px, .68vw, 11px); margin-top: 2px; line-height: 1.35; }
.setting-row .value { float: right; color: var(--cyan); font-size: clamp(8px, .68vw, 11px); letter-spacing: .08em; text-transform: uppercase; }
.setting-row[aria-pressed="true"] { border-color: rgba(0,212,255,.62); background: rgba(0,212,255,.08); }
.setting-row[aria-pressed="true"] strong::after { content: " ✓"; color: var(--cyan); }
.search-overlay {
  inset: 0;
  background: rgba(4,7,15,.9);
  backdrop-filter: blur(10px);
  opacity: 0;
  transform: scale(.985);
  transition: opacity var(--panel-ms) ease, transform var(--panel-ms) ease, visibility var(--panel-ms);
  display: grid;
  place-items: center;
}
.search-overlay.open { transform: none; }
.search-box { width: min(66%, 760px); }
.search-box .eyebrow { margin: 0 0 10px; }
.search-box h2 { margin: 0 0 14px; font-size: clamp(22px, 2.4vw, 40px); }
.search-input {
  width: 100%;
  border: 1px solid var(--cyan);
  border-radius: 12px;
  background: rgba(13,17,23,.94);
  color: var(--ink);
  padding: 14px 16px;
  font-size: clamp(13px, 1.2vw, 20px);
  outline: none;
  box-shadow: 0 0 0 4px rgba(0,212,255,.12);
}
.search-hint { color: var(--muted); font-size: clamp(9px, .75vw, 12px); margin: 10px 0 14px; }
.search-results { display: grid; gap: 8px; max-height: 34vh; overflow-y: auto; }
.result-row { min-height: 56px; display: flex; align-items: center; gap: 14px; padding: 8px 12px; border-radius: 10px; }
.result-row img { width: 46px; height: 46px; object-fit: contain; }
.result-row strong { display: block; font-size: clamp(11px, .95vw, 16px); }
.result-row small { display: block; color: var(--muted); font-size: clamp(8px, .7vw, 12px); margin-top: 2px; }
.empty { color: var(--muted); padding: 14px 4px; font-size: clamp(10px, .85vw, 14px); }
.toast {
  position: absolute;
  z-index: 12;
  left: 50%;
  bottom: 9%;
  transform: translate(-50%, 16px);
  background: #e6edf3;
  color: #071018;
  border-radius: 999px;
  padding: .7em 1.2em;
  font-size: clamp(9px, .8vw, 13px);
  font-weight: 650;
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--panel-ms) ease, transform var(--panel-ms) ease;
  box-shadow: 0 12px 32px rgba(0,0,0,.35);
}
.toast.show { opacity: 1; transform: translate(-50%, 0); }
.disclaimer {
  position: absolute;
  z-index: 3;
  left: 3.2%;
  bottom: 8%;
  color: rgba(230,237,243,.42);
  font-size: clamp(7px, .62vw, 10px);
  letter-spacing: .1em;
  text-transform: uppercase;
  pointer-events: none;
}
body[data-motion="off"] .hero-copy.reveal { animation: none; }
body[data-motion="off"] * { transition-duration: 0ms !important; animation-duration: 0ms !important; }
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { transition-duration: .01ms !important; animation-duration: .01ms !important; animation-iteration-count: 1 !important; scroll-behavior: auto !important; }
}
@media (max-aspect-ratio: 4/3) { .stage { width: 99vw; } .panel { width: 44%; } }
</style>
</head>
<body data-motion="full" data-background="focus">
<div class="stage">
  <div class="stage-label"><span>Core Builds Launcher · 0.3 interaction study</span><span>Design illustration · no network requests</span></div>
  <main class="tv" id="tv" aria-label="Core Builds Launcher design illustration">
    <div class="focus-backdrop" id="focus-backdrop"></div>
    <div class="scrim"></div>
    <header>
      <div class="brand"><img src="__MARK_URI__" alt=""><strong>Core Builds</strong></div>
      <div class="clock"><b>10:10</b><small>THU. NOV 5</small></div>
      <div class="header-actions" data-nav-group="top">
        <button class="pill" id="search-button" data-nav aria-label="Search installed apps">⌕ Search</button>
        <button class="pill primary" id="customise-button" data-nav aria-label="Open Customise">Customise</button>
      </div>
    </header>
    <section class="hero" aria-live="polite">
      <div class="hero-art"><img id="hero-art" src="__LAUNCHER_BANNER_URI__" alt=""></div>
      <div class="hero-copy" id="hero-copy">
        <div class="eyebrow" id="hero-eyebrow">FAVOURITE</div>
        <h1 id="hero-title">Core Builds Icon Pack</h1>
        <p id="hero-meta">TV APP · PRESS OK TO OPEN · F FAVOURITE · S SEARCH</p>
      </div>
    </section>
    <div id="shelves" data-nav-group="shelves">
      <section class="shelf">
        <div class="shelf-head"><h2>Favourites</h2><span class="note" id="favourite-note">4 picked · your order</span></div>
        <div class="card-row" id="favourites-row"></div>
      </section>
      <section class="shelf" id="suggested-section">
        <div class="shelf-head"><h2 id="suggested-title">Suggested now</h2><span class="note" id="suggested-note">From the last 30 days on this TV · never sent anywhere</span></div>
        <div class="card-row" id="suggested-row"></div>
      </section>
      <section class="shelf">
        <div class="shelf-head"><h2>All apps <span id="all-count">· 14</span></h2><span class="note" id="sort-note">A–Z · local only</span></div>
        <div class="app-grid" id="app-grid"></div>
      </section>
    </div>
    <footer>
      <span>SMART · PRIVATE · SMOOTH</span>
      <span><kbd>↑</kbd><kbd>↓</kbd><kbd>←</kbd><kbd>→</kbd> move · <kbd>OK</kbd> open · <kbd>F</kbd> favourite · <kbd>H</kbd> hide · <kbd>S</kbd> search · <kbd>C</kbd> customise · <kbd>Esc</kbd> back</span>
    </footer>
    <div class="disclaimer">DESIGN ILLUSTRATION · NOT A SCREENSHOT · SAMPLE LOCAL DATA IN THIS BROWSER ONLY</div>

    <aside class="panel" id="panel" aria-hidden="true" aria-label="Customise">
      <div class="panel-head">
        <div class="eyebrow">HOME SCREEN</div>
        <h2>Customise</h2>
        <p>Every row shows its current value. Changes are local to this browser preview; the launcher keeps its own data on the TV.</p>
      </div>
      <div class="settings-list" id="settings-list" data-nav-group="panel"></div>
    </aside>

    <section class="search-overlay" id="search-overlay" aria-hidden="true" aria-label="Find an app">
      <div class="search-box">
        <div class="eyebrow" id="search-eyebrow">LOCAL SEARCH</div>
        <h2 id="search-title">Find an app</h2>
        <input class="search-input" id="search-input" type="text" autocomplete="off" spellcheck="false" placeholder="Type an app name…" aria-label="Search installed apps">
        <p class="search-hint" id="search-hint">Exact names, prefixes, initials and package names · up to 8 results · no network request</p>
        <div class="search-results" id="search-results" data-nav-group="results"></div>
      </div>
    </section>
    <div class="toast" id="toast" role="status" aria-live="polite"></div>
  </main>
</div>
<script>
"use strict";
const ASSETS = __ASSETS_JSON__;
const APPS = __APPS_JSON__;
const STORAGE_SETTINGS = "core-launcher-polish-settings";
const STORAGE_LAYOUT = "core-launcher-polish-layout";
const STORAGE_BACKUP = "core-launcher-polish-backup";
const FORMAT = "core-builds-launcher-layout";
const VERSION = 1;
const DEFAULTS = {
  favourites: ["tv.corebuilds.iconpack", "tv.corebuilds.line", "tv.corebuilds.shift", "tv.corebuilds.eq"],
  hidden: [],
  recents: ["com.aiostreams.tv", "com.aurora.player", "com.neilturner.aerialviews", "com.sony.bravia.core", "au.net.abc.iview"],
  settings: { motion: "full", smart: true, sort: "a_z", cardSize: "standard", background: "focus", dim: "medium", artwork: "app", showSuggested: true, showClock: true }
};
let state = loadState();
let currentApp = APPS[0];
let searchMode = "launch";
let resetArmed = false;
let toastTimer = 0;

function loadState() {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_LAYOUT) || "null");
    if (stored && Array.isArray(stored.favourites)) {
      return Object.assign({}, DEFAULTS, stored, { settings: Object.assign({}, DEFAULTS.settings, stored.settings || {}) });
    }
  } catch (_) { /* A damaged sample layout resets to defaults. */ }
  return JSON.parse(JSON.stringify(DEFAULTS));
}
function saveState() {
  try { localStorage.setItem(STORAGE_LAYOUT, JSON.stringify(state)); } catch (_) { /* Storage may be disabled. */ }
}
function $(id) { return document.getElementById(id); }
function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function uri(key) { return ASSETS[key] || ASSETS.mark; }
function app(pkg) { return APPS.find(item => item.package === pkg) || APPS[0]; }
function visibleApps() { return APPS.filter(item => !state.hidden.includes(item.package)); }
function accent(appItem) { return "hsla(" + appItem.hue + ", 92%, 55%, .26)"; }
function normalize(text) { return String(text).toLowerCase().replace(/[^a-z0-9]+/g, " ").trim(); }
function initials(label) { return normalize(label).split(" ").filter(Boolean).map(word => word[0]).join(""); }
function isSubsequence(needle, haystack) {
  let i = 0;
  for (const char of haystack.replace(/\s/g, "")) if (i < needle.length && char === needle[i]) i++;
  return i === needle.length;
}
function scoreApp(appItem, query, preferred) {
  const label = normalize(appItem.label);
  const pkg = normalize(appItem.package);
  const needle = normalize(query);
  if (!needle) return (preferred.has(appItem.package) ? 120 : 0) + appItem.usage;
  let score = 0;
  if (label === needle) score = Math.max(score, 120);
  if (label.startsWith(needle)) score = Math.max(score, 100);
  if (label.split(" ").some(word => word.startsWith(needle))) score = Math.max(score, 85);
  if (initials(appItem.label).startsWith(needle.replace(/\s/g, ""))) score = Math.max(score, 70);
  if (pkg.replace(/\s/g, "").includes(needle.replace(/\s/g, ""))) score = Math.max(score, 50);
  if (isSubsequence(needle.replace(/\s/g, ""), label.replace(/\s/g, ""))) score = Math.max(score, 25);
  if (!score) return 0;
  if (preferred.has(appItem.package)) score += 6;
  score += Math.min(appItem.usage, 9) / 10;
  return score;
}
function rankApps(query) {
  const preferred = new Set([...state.favourites, ...state.recents]);
  return visibleApps()
    .map(item => ({ item, score: scoreApp(item, query, preferred) }))
    .filter(entry => entry.score > 0)
    .sort((a, b) => b.score - a.score || a.item.label.localeCompare(b.item.label))
    .slice(0, 8)
    .map(entry => entry.item);
}
function orderedApps() {
  const items = visibleApps().slice();
  const byName = (a, b) => a.label.localeCompare(b.label);
  switch (state.settings.sort) {
    case "most_used": return items.sort((a, b) => b.usage - a.usage || byName(a, b));
    case "tv_first": return items.sort((a, b) => Number(b.tv) - Number(a.tv) || byName(a, b));
    case "newest": return items.sort((a, b) => a.age - b.age || byName(a, b));
    default: return items.sort(byName);
  }
}
function suggestedApps() {
  const fallback = state.recents.filter(pkg => !state.favourites.includes(pkg) && !state.hidden.includes(pkg));
  if (!state.settings.smart) return fallback.slice(0, 5);
  const hour = new Date().getHours();
  const slot = Math.floor(hour / 4);
  const counts = new Map();
  APPS.forEach(item => {
    const n = item.usage;
    const recency = Math.max(0, 15 - item.age);
    const context = (item.age + slot) % 3 === 0 ? 3 : 0;
    counts.set(item.package, n * 5 + recency + context);
  });
  return visibleApps()
    .filter(item => !state.favourites.includes(item.package))
    .map(item => ({ item, score: counts.get(item.package) + (state.recents.includes(item.package) ? 8 : 0) }))
    .sort((a, b) => b.score - a.score || a.item.label.localeCompare(b.item.label))
    .slice(0, 5)
    .map(entry => entry.item.package);
}
function cardHtml(appItem, eyebrow) {
  return '<button class="app-card" type="button" data-nav data-package="' + escapeHtml(appItem.package) + '" data-tv="' + appItem.tv + '" aria-label="' + escapeHtml(appItem.label + ". " + eyebrow + ". Press OK to open") + '">' +
    '<img src="' + uri(appItem.banner) + '" alt="" loading="lazy">' +
    '<span class="card-label">' + escapeHtml(appItem.label) + '</span></button>';
}
function renderCards(keepFocus) {
  const activePackage = keepFocus && document.activeElement && document.activeElement.dataset ? document.activeElement.dataset.package : null;
  const favouriteItems = state.favourites.map(pkg => app(pkg)).filter(item => !state.hidden.includes(item.package));
  $("favourites-row").innerHTML = favouriteItems.map(item => cardHtml(item, "Favourite")).join("") +
    '<button class="add-card" type="button" id="add-card" data-nav aria-label="Add a favourite">+<span>Add</span></button>';
  const suggested = suggestedApps().map(pkg => app(pkg));
  $("suggested-section").style.display = state.settings.showSuggested ? "block" : "none";
  $("suggested-row").innerHTML = suggested.map(item => cardHtml(item, state.settings.smart ? "Suggested" : "Recently opened")).join("");
  const ordered = orderedApps();
  $("app-grid").innerHTML = ordered.map(item => cardHtml(item, "All apps")).join("");
  $("favourite-note").textContent = favouriteItems.length + " picked · your order";
  $("all-count").textContent = "· " + ordered.length;
  $("suggested-title").textContent = state.settings.smart ? "Suggested now" : "Recently opened";
  $("suggested-note").textContent = state.settings.smart ? "From the last 30 days on this TV · never sent anywhere" : "Last five local launches · newest first";
  $("sort-note").textContent = ({ a_z: "A–Z", most_used: "Most used", tv_first: "TV first", newest: "Newest install" })[state.settings.sort] + " · local only";
  const columns = ({ compact: 6, standard: 5, large: 4 })[state.settings.cardSize] || 5;
  document.querySelectorAll(".card-row, .app-grid").forEach(row => row.style.gridTemplateColumns = "repeat(" + columns + ", minmax(0, 1fr))");
  $("add-card").addEventListener("click", () => openSearch("favourite"));
  if (activePackage) focusPackage(activePackage);
}
function focusPackage(pkg) {
  const target = [...document.querySelectorAll(".app-card[data-package]")].find(el => el.dataset.package === pkg);
  if (target) target.focus();
}
function setSelected(appItem, eyebrow) {
  if (!appItem || appItem === currentApp && $("hero-title").textContent === appItem.label) return;
  currentApp = appItem;
  $("hero-art").src = uri(appItem.tv ? appItem.banner : appItem.icon);
  $("hero-eyebrow").textContent = eyebrow || (state.favourites.includes(appItem.package) ? "FAVOURITE" : "ALL APPS");
  $("hero-title").textContent = appItem.label;
  $("hero-meta").textContent = (appItem.tv ? "TV APP" : "APP") + " · PRESS OK TO OPEN · F FAVOURITE · H HIDE";
  $("focus-backdrop").style.setProperty("--accent", accent(appItem));
  $("hero-copy").classList.remove("reveal");
  void $("hero-copy").offsetWidth;
  $("hero-copy").classList.add("reveal");
}
function eyebrowFor(card) {
  if (card.closest("#suggested-section")) return state.settings.smart ? "SUGGESTED NOW" : "RECENTLY OPENED";
  if (card.closest("#favourites-row")) return "FAVOURITE";
  return "ALL APPS";
}
document.addEventListener("focusin", event => {
  const card = event.target.closest(".app-card");
  if (card) setSelected(app(card.dataset.package), eyebrowFor(card));
});
document.addEventListener("click", event => {
  const card = event.target.closest(".app-card");
  if (card && !card.closest("#search-results")) useApp(card.dataset.package, "launch");
});
function useApp(pkg, mode) {
  const item = app(pkg);
  if (mode === "favourite") {
    toggleFavourite(pkg);
    closeSearch();
    return;
  }
  state.recents = [pkg, ...state.recents.filter(entry => entry !== pkg)].slice(0, 5);
  saveState();
  renderCards(true);
  toast("Preview: " + item.label + " would open · launch recorded locally");
}
function toggleFavourite(pkg) {
  const item = app(pkg);
  if (state.favourites.includes(pkg)) {
    state.favourites = state.favourites.filter(entry => entry !== pkg);
    toast(item.label + " removed from Favourites");
  } else {
    state.favourites.push(pkg);
    toast(item.label + " added to Favourites");
  }
  saveState();
  renderCards(true);
  renderSettings();
}
function toggleHidden(pkg) {
  const item = app(pkg);
  if (state.hidden.includes(pkg)) {
    state.hidden = state.hidden.filter(entry => entry !== pkg);
    toast(item.label + " is visible in All apps");
  } else {
    state.hidden.push(pkg);
    toast(item.label + " hidden from All apps");
  }
  saveState();
  renderCards(true);
  renderSettings();
}
function toast(message) {
  const node = $("toast");
  node.textContent = message;
  node.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => node.classList.remove("show"), 2200);
}
function motionScale() {
  const settingsScale = ({ full: 1, calm: .55, off: 0 })[state.settings.motion];
  const systemScale = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 1;
  return settingsScale * systemScale;
}
function ms(base) { return Math.round(base * motionScale()) + "ms"; }
function applySettings() {
  const root = document.documentElement.style;
  root.setProperty("--focus-ms", ms(150));
  root.setProperty("--reveal-ms", ms(170));
  root.setProperty("--scroll-ms", ms(220));
  root.setProperty("--backdrop-ms", ms(280));
  root.setProperty("--panel-ms", ms(200));
  root.setProperty("--dim", ({ light: .5, medium: .72, strong: .88 })[state.settings.dim]);
  document.body.dataset.motion = state.settings.motion;
  document.body.dataset.background = state.settings.background;
  $(".clock").style.visibility = state.settings.showClock ? "visible" : "hidden";
  saveState();
  renderCards(true);
}
const GROUPS = [
  { id: "artwork", title: "Artwork", note: "Card art source", choices: [
    { id: "app", label: "App artwork", meta: "Each app's own banner or icon." },
    { id: "core", label: "Core Builds banners", meta: "Installed Core Builds pack art." },
    { id: "glyphs", label: "Core Builds glyphs", meta: "Monoline glyph pack art." } ] },
  { id: "background", title: "Background", note: "Focus preview", choices: [
    { id: "focus", label: "Follows focus", meta: "A softened glow from the focused app." },
    { id: "core", label: "Core Builds", meta: "A fixed brand-toned surface." },
    { id: "void", label: "Void", meta: "No backdrop artwork." } ] },
  { id: "dim", title: "Background dim", note: "Text protection", choices: [
    { id: "light", label: "Light", meta: "More artwork behind the text." },
    { id: "medium", label: "Medium", meta: "Balanced contrast for a TV." },
    { id: "strong", label: "Strong", meta: "Maximum text protection." } ] },
  { id: "cardSize", title: "Card size", note: "All apps grid", choices: [
    { id: "compact", label: "Compact", meta: "6 cards per row." },
    { id: "standard", label: "Standard", meta: "5 cards per row." },
    { id: "large", label: "Large", meta: "4 cards per row." } ] },
  { id: "motion", title: "Motion", note: "Also honours Android's Remove animations", choices: [
    { id: "full", label: "Full", meta: "150 ms focus, 280 ms backdrop." },
    { id: "calm", label: "Calm", meta: "All transitions at 55 percent." },
    { id: "off", label: "Off", meta: "Instant changes; focus stays visible." } ] },
  { id: "smart", title: "Smart suggestions", note: "Local data only", choices: [
    { id: true, label: "Learn my routine", meta: "Use the last 30 days and 96 events on this TV." },
    { id: false, label: "Recent only", meta: "Show the last five launches in order." } ] },
  { id: "sort", title: "App order", note: "All apps", choices: [
    { id: "a_z", label: "A–Z", meta: "Alphabetical by app label." },
    { id: "most_used", label: "Most used", meta: "Local launch count, then A–Z." },
    { id: "tv_first", label: "TV first", meta: "Leanback entries, then A–Z." },
    { id: "newest", label: "Newest install", meta: "Install time, then A–Z." } ] },
  { id: "showSuggested", title: "Suggested row", note: "Home layout", choices: [
    { id: true, label: "Show", meta: "Keep the second shelf visible." },
    { id: false, label: "Hide", meta: "Leave Favourites and All apps." } ] },
  { id: "showClock", title: "Clock", note: "Top bar", choices: [
    { id: true, label: "Show", meta: "Time and date in the top bar." },
    { id: false, label: "Hide", meta: "More room for the top bar actions." } ] },
];
const ACTIONS = [
  { id: "find", title: "Find an app", meta: "Search every installed, visible app by name.", value: "OPEN" },
  { id: "quick", title: "Quick settings", meta: "Network, display, sound, accessibility and Home handoffs in the launcher.", value: "6" },
  { id: "backup", title: "Back up layout", meta: "Save a versioned layout to this browser preview only.", value: "SAVE" },
  { id: "restore", title: "Restore layout", meta: "Replace settings from a format/version-checked local snapshot.", value: "LOAD" },
  { id: "hidden", title: "Hidden apps", meta: "Bring every hidden sample app back.", value: String(state.hidden.length) },
  { id: "home", title: "Set as Home app", meta: "Hands off to Android settings; not available in a browser preview.", value: "OPEN" },
  { id: "about", title: "About this Home", meta: "Version 0.3.0 · no account, no network, no analytics.", value: "0.3.0" },
  { id: "reset", title: "Reset home screen", meta: "Press twice to restore the sample layout.", value: "TWICE" },
];
function currentValue(group) {
  const found = group.choices.find(choice => choice.id === state.settings[group.id]);
  return found ? found.label : "—";
}
function renderSettings() {
  const html = GROUPS.map(group =>
    '<section class="setting-group"><h3>' + escapeHtml(group.title) + '<span>' + escapeHtml(currentValue(group)) + '</span></h3>' +
    group.choices.map(choice =>
      '<button class="setting-row" type="button" data-nav data-group="' + group.id + '" data-choice="' + String(choice.id) + '" aria-pressed="' + (state.settings[group.id] === choice.id) + '">' +
      '<span class="value">' + (state.settings[group.id] === choice.id ? "SELECTED" : "OK") + '</span>' +
      '<strong>' + escapeHtml(choice.label) + '</strong><small>' + escapeHtml(choice.meta) + '</small></button>'
    ).join("") + '</section>'
  ).join("") + '<section class="setting-group"><h3>Functions<span>LOCAL</span></h3>' +
    ACTIONS.map(action =>
      '<button class="setting-row" type="button" data-nav data-action="' + action.id + '" aria-pressed="false">' +
      '<span class="value">' + escapeHtml(action.id === "hidden" ? String(state.hidden.length) : action.value) + '</span>' +
      '<strong>' + escapeHtml(action.title) + '</strong><small>' + escapeHtml(action.meta) + '</small></button>'
    ).join("") + '</section>';
  $("settings-list").innerHTML = html;
  $("settings-list").querySelectorAll("[data-group]").forEach(button => {
    button.addEventListener("click", () => chooseSetting(button.dataset.group, button.dataset.choice));
  });
  $("settings-list").querySelectorAll("[data-action]").forEach(button => {
    button.addEventListener("click", () => runAction(button.dataset.action));
  });
}
function coerceChoice(value) {
  if (value === "true") return true;
  if (value === "false") return false;
  return value;
}
function chooseSetting(group, choice) {
  const index = [...$("settings-list").querySelectorAll("[data-nav]")].indexOf(document.activeElement);
  state.settings[group] = coerceChoice(choice);
  resetArmed = false;
  applySettings();
  renderSettings();
  const buttons = [...$("settings-list").querySelectorAll("[data-nav]")];
  (buttons[index] || buttons[0]).focus();
  toast("Preview: " + group + " set to " + currentValue(GROUPS.find(item => item.id === group)));
}
function runAction(action) {
  switch (action) {
    case "find": closePanel(); openSearch("launch"); break;
    case "quick": toast("Quick settings hands off to Android in the launcher; this browser preview has no TV settings"); break;
    case "backup": backupLayout(); break;
    case "restore": restoreLayout(); break;
    case "hidden": state.hidden = []; saveState(); renderCards(true); renderSettings(); toast("All sample apps are visible again"); break;
    case "home": toast("Browser preview: the launcher opens Android's Home-app settings here"); break;
    case "about": toast("Core Builds Launcher 0.3.0 · local SharedPreferences only · zero permissions"); break;
    case "reset":
      if (!resetArmed) { resetArmed = true; toast("Press Reset once more to confirm"); }
      else { state = JSON.parse(JSON.stringify(DEFAULTS)); resetArmed = false; saveState(); applySettings(); renderSettings(); closePanel(); toast("Sample layout reset"); }
      break;
  }
}
function backupLayout() {
  const snapshot = { format: FORMAT, version: VERSION, favourites: state.favourites, hidden: state.hidden, recents: state.recents, settings: state.settings };
  try { localStorage.setItem(STORAGE_BACKUP, JSON.stringify(snapshot)); toast("Layout saved in this browser only"); }
  catch (_) { toast("Browser storage is unavailable; no file was written"); }
}
function restoreLayout() {
  try {
    const snapshot = JSON.parse(localStorage.getItem(STORAGE_BACKUP) || "null");
    if (!snapshot || snapshot.format !== FORMAT || snapshot.version !== VERSION) { toast("No compatible local layout backup found"); return; }
    state.favourites = Array.isArray(snapshot.favourites) ? snapshot.favourites : DEFAULTS.favourites;
    state.hidden = Array.isArray(snapshot.hidden) ? snapshot.hidden : [];
    state.recents = Array.isArray(snapshot.recents) ? snapshot.recents : DEFAULTS.recents;
    state.settings = Object.assign({}, DEFAULTS.settings, snapshot.settings || {});
    saveState(); applySettings(); renderSettings(); toast("Versioned local layout restored");
  } catch (_) { toast("Restore failed: local snapshot was not valid JSON"); }
}
function openPanel() {
  closeSearch();
  $("panel").classList.add("open");
  $("panel").setAttribute("aria-hidden", "false");
  const first = $("settings-list").querySelector("[data-nav]");
  if (first) first.focus();
}
function closePanel() {
  $("panel").classList.remove("open");
  $("panel").setAttribute("aria-hidden", "true");
}
function openSearch(mode) {
  closePanel();
  searchMode = mode || "launch";
  $("search-title").textContent = searchMode === "favourite" ? "Pick a favourite" : "Find an app";
  $("search-eyebrow").textContent = searchMode === "favourite" ? "FAVOURITES" : "LOCAL SEARCH";
  $("search-hint").textContent = searchMode === "favourite"
    ? "Pick an installed app to add to Favourites · OK adds · Esc returns"
    : "Exact names, prefixes, initials and package names · up to 8 results · no network request";
  $("search-input").value = "";
  renderSearchResults();
  $("search-overlay").classList.add("open");
  $("search-overlay").setAttribute("aria-hidden", "false");
  $("search-input").focus();
}
function closeSearch() {
  $("search-overlay").classList.remove("open");
  $("search-overlay").setAttribute("aria-hidden", "true");
  if (currentApp) focusPackage(currentApp.package);
}
function renderSearchResults() {
  const query = $("search-input").value;
  const results = rankApps(query);
  $("search-results").innerHTML = results.length
    ? results.map(item =>
        '<button class="result-row" type="button" data-nav data-package="' + escapeHtml(item.package) + '" aria-label="' + escapeHtml(item.label + ". Press OK to " + (searchMode === "favourite" ? "add to favourites" : "open")) + '">' +
        '<img src="' + uri(item.icon) + '" alt=""><span><strong>' + escapeHtml(item.label) + '</strong><small>' +
        (item.tv ? "TV app" : "App") + " · " + (state.favourites.includes(item.package) ? "Favourite" : "All apps") + '</small></span></button>'
      ).join("")
    : '<div class="empty">No local app matches that name.</div>';
  $("search-results").querySelectorAll("[data-package]").forEach(button => {
    button.addEventListener("click", () => useApp(button.dataset.package, searchMode));
  });
}
function panelOpen() { return $("panel").classList.contains("open"); }
function searchOpen() { return $("search-overlay").classList.contains("open"); }
function panelItems() { return [...$("settings-list").querySelectorAll("[data-nav]")]; }
function searchItems() { return [...$("search-results").querySelectorAll("[data-nav]")]; }
function topItems() { return [...document.querySelectorAll("header [data-nav]")]; }
function shelfItems() { return [...document.querySelectorAll("#shelves [data-nav]")]; }
function moveWithin(items, direction) {
  const index = items.indexOf(document.activeElement);
  if (index < 0) { items[0] && items[0].focus(); return; }
  const step = direction === "up" || direction === "left" ? -1 : 1;
  const next = Math.max(0, Math.min(items.length - 1, index + step));
  items[next].focus();
}
function moveSearch(direction) {
  moveWithin(searchItems(), direction);
}
function movePanel(direction) {
  moveWithin(panelItems(), direction);
}
function move(direction) {
  if (searchOpen()) { moveSearch(direction); return; }
  if (panelOpen()) { movePanel(direction); return; }
  const top = topItems();
  const items = shelfItems();
  const active = document.activeElement;
  if (top.includes(active)) {
    if (direction === "left" || direction === "right") moveWithin(top, direction);
    else if (direction === "down" && items.length) items[0].focus();
    return;
  }
  if (!items.length) return;
  const index = items.indexOf(active);
  if (index < 0) { items[0].focus(); return; }
  // Rendered row geometry decides the column count; no fixed six-column assumption.
  const tops = [...new Set(items.map(item => item.offsetTop))].sort((a, b) => a - b);
  const firstTop = tops[0];
  const columns = items.filter(item => item.offsetTop === items[0].offsetTop).length || 1;
  const next = index + (direction === "up" ? -columns : columns);
  const currentTop = active.offsetTop;
  const row = items.filter(item => item.offsetTop === currentTop);
  const col = row.indexOf(active);
  if (direction === "left" || direction === "right") {
    const target = direction === "left" ? row[Math.max(0, col - 1)] : row[Math.min(row.length - 1, col + 1)];
    (target || active).focus();
    return;
  }
  if (direction === "up" && currentTop === firstTop) { top[top.length - 1].focus(); return; }
  const rowIndex = tops.indexOf(currentTop) + (direction === "up" ? -1 : 1);
  if (rowIndex < 0 || rowIndex >= tops.length) return;
  const targetRow = items.filter(item => item.offsetTop === tops[rowIndex]);
  (targetRow[Math.min(col, targetRow.length - 1)] || active).focus();
  void next;
}
function goTop() {
  if (searchOpen()) { closeSearch(); return; }
  if (panelOpen()) { closePanel(); return; }
  const first = shelfItems()[0] || topItems()[0];
  if (first) first.focus();
}
$("search-button").addEventListener("click", () => openSearch("launch"));
$("customise-button").addEventListener("click", openPanel);
$("search-input").addEventListener("input", renderSearchResults);
document.addEventListener("keydown", event => {
  const key = event.key;
  if (searchOpen()) {
    if (key === "Escape" || key === "Home") { event.preventDefault(); closeSearch(); return; }
    if (key === "ArrowUp" || key === "ArrowDown") { event.preventDefault(); moveSearch(key === "ArrowUp" ? "up" : "down"); return; }
    if (key === "Enter" && document.activeElement === $("search-input")) {
      const first = searchItems()[0];
      if (first) { event.preventDefault(); first.click(); }
      return;
    }
    return;
  }
  if (panelOpen()) {
    if (key === "Escape" || key === "Backspace" || key === "Home") { event.preventDefault(); closePanel(); if (currentApp) focusPackage(currentApp.package); return; }
    if (["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"].includes(key)) {
      event.preventDefault();
      movePanel({ ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right" }[key]);
      return;
    }
    return;
  }
  const arrows = { ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right" };
  if (arrows[key]) { event.preventDefault(); move(arrows[key]); return; }
  if (key === "Escape" || key === "Backspace" || key === "Home") { event.preventDefault(); goTop(); return; }
  if (key === "s" || key === "S" || key === "/") { event.preventDefault(); openSearch("launch"); return; }
  if (key === "c" || key === "C") { event.preventDefault(); openPanel(); return; }
  if (key === "f" || key === "F") { event.preventDefault(); if (currentApp) toggleFavourite(currentApp.package); return; }
  if (key === "h" || key === "H") { event.preventDefault(); if (currentApp) toggleHidden(currentApp.package); return; }
});
renderSettings();
applySettings();
setSelected(APPS[0], "FAVOURITE");
requestAnimationFrame(() => { const first = shelfItems()[0]; if (first) first.focus(); });
</script>
</body>
</html>
'''


def data_uri(path: Path) -> str:
    if not path.is_file():
        raise FileNotFoundError(f"Missing preview artwork: {path}")
    encoded = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:image/webp;base64,{encoded}"


def render() -> str:
    assets = {key: data_uri(path) for key, path in ASSET_PATHS.items()}
    html = TEMPLATE
    html = html.replace("__ASSETS_JSON__", json.dumps(assets, separators=(",", ":")))
    html = html.replace("__APPS_JSON__", json.dumps(APPS, separators=(",", ":")))
    html = html.replace("__MARK_URI__", assets["mark"])
    html = html.replace("__LAUNCHER_BANNER_URI__", assets["launcher_banner"])
    return html


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify the checked-in HTML is current")
    args = parser.parse_args()
    html = render()
    if args.check:
        current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.is_file() else ""
        if current != html:
            print(f"Stale preview: regenerate {OUTPUT.relative_to(ROOT)}")
            return 1
        print(f"Preview current: {OUTPUT.relative_to(ROOT)}")
        return 0
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(html, encoding="utf-8")
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({len(html):,} bytes, {len(ASSET_PATHS)} embedded assets)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
