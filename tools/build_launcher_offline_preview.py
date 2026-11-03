#!/usr/bin/env python3
"""Build the self-contained, offline Core Builds launcher design preview.

Only repository-owned launcher/icon-pack artwork is embedded. The generated
HTML has no external fonts, stylesheets, scripts, image URLs, or network calls.
"""
from __future__ import annotations

import argparse
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ICON_DIR = ROOT / "app/src/main/res/drawable-nodpi"
LAUNCHER_ASSET_DIR = ROOT / "launcher/app/src/main/res/drawable-nodpi"
OUTPUT = ROOT / "docs/mockups/core-launcher-offline.html"

APP_SOURCES = [
    ("core-builds", "Core Builds", "corebuilds", True, True),
    ("core-doctor", "Core Doctor", "coredoctor", True, True),
    ("core-eq", "Core EQ", "coreeq", True, True),
    ("core-line", "Core Line", "coreline", True, True),
    ("core-shift", "Core Shift", "coreshift", True, True),
    ("disney-plus", "Disney+", "disneyplus", True, False),
    ("jellyfin", "Jellyfin", "jellyfin", True, False),
    ("kodi", "Kodi", "kodi", True, False),
    ("netflix", "Netflix", "netflix", True, False),
    ("plex", "Plex", "plex", True, False),
    ("smarttube", "SmartTube", "smarttube", True, False),
    ("spotify", "Spotify", "spotify", True, False),
    ("stan", "Stan", "stan", True, False),
    ("stremio", "Stremio", "stremio", True, False),
    ("vlc", "VLC", "vlc", True, False),
    ("youtube", "YouTube", "youtube", True, False),
]

HTML_TEMPLATE = r'''<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="dark">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; base-uri 'none'; form-action 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'none'; font-src 'none'">
  <title>Core Builds Launcher — Offline Design Illustration</title>
  <style>
    :root {
      color-scheme: dark;
      --void: #04070F;
      --night: #0D1117;
      --panel: #151923;
      --panel-raised: #1B2230;
      --line: #2B3443;
      --cyan: #00D4FF;
      --text: #F1F5F9;
      --muted: #9AA7B8;
      --good: #8FE7F8;
    }
    * { box-sizing: border-box; }
    html { background: #03050A; }
    body {
      margin: 0;
      color: var(--text);
      background:
        radial-gradient(ellipse at 12% 0%, rgba(0, 212, 255, .075), transparent 32rem),
        linear-gradient(180deg, #080C14 0%, #03050A 100%);
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      min-height: 100vh;
    }
    button { font: inherit; }
    .page { width: min(100% - 40px, 1640px); margin: 0 auto; padding: 28px 0 42px; }
    .page-heading {
      display: flex; align-items: flex-end; justify-content: space-between; gap: 24px;
      margin: 0 auto 20px; max-width: 1600px;
    }
    .eyebrow, .badge, .micro, .section-note, .category, .count, .privacy, .tile-kind {
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      letter-spacing: .09em; text-transform: uppercase;
    }
    .eyebrow { color: var(--cyan); font-size: 11px; font-weight: 800; }
    h1 { margin: 5px 0 5px; font-size: clamp(23px, 3vw, 38px); letter-spacing: -.04em; line-height: 1.1; }
    .page-heading p { color: #AAB5C4; font-size: 14px; margin: 0; max-width: 810px; line-height: 1.55; }
    .badge {
      flex: 0 0 auto; border: 1px solid rgba(0, 212, 255, .36); color: #A7F0FF;
      border-radius: 999px; padding: 10px 13px; font-size: 10px; font-weight: 800;
      background: rgba(0, 212, 255, .06);
    }
    .screen-wrap { width: 100%; max-width: 1600px; margin: 0 auto; }
    .tv-screen {
      container-type: inline-size;
      position: relative; width: 100%; aspect-ratio: 16 / 9; min-height: 560px;
      overflow: auto; scrollbar-width: none; overscroll-behavior: contain;
      border: 1px solid rgba(130, 154, 183, .28); border-radius: 18px;
      background:
        radial-gradient(ellipse at 85% 11%, rgba(0, 212, 255, .075), transparent 29%),
        radial-gradient(ellipse at 9% 86%, rgba(19, 36, 62, .48), transparent 40%),
        var(--void);
      box-shadow: 0 38px 100px rgba(0, 0, 0, .48), 0 0 0 6px rgba(255, 255, 255, .018);
      scroll-behavior: smooth;
    }
    .tv-screen::-webkit-scrollbar { display: none; }
    .screen-content {
      min-height: 100%; padding: 2.4cqw 4.4cqw 2.1cqw;
      display: flex; flex-direction: column; gap: 1.12cqw;
    }
    .topbar { display: flex; align-items: center; min-height: 4.6cqw; gap: 1.4cqw; }
    .brand { display: flex; align-items: center; gap: .9cqw; min-width: 0; }
    .brand img { width: 3.7cqw; height: 3.7cqw; object-fit: contain; flex: 0 0 auto; }
    .brand-copy { display: flex; flex-direction: column; gap: .22cqw; }
    .brand-name { font-size: 1.25cqw; font-weight: 850; line-height: 1.1; letter-spacing: .04em; }
    .brand-tag { color: var(--cyan); font-size: .59cqw; font-weight: 800; letter-spacing: .16em; }
    .top-spacer { flex: 1; }
    .clock { text-align: right; margin-right: .7cqw; }
    .clock-time { font: 700 1.25cqw/1.1 ui-monospace, SFMono-Regular, Menlo, monospace; }
    .clock-date { margin-top: .28cqw; color: var(--muted); font: 600 .6cqw/1.1 ui-monospace, SFMono-Regular, Menlo, monospace; letter-spacing: .08em; }
    .header-action, .small-action {
      border: 1px solid #344052; border-radius: .52cqw; color: #DAE4F1; background: #101620;
      min-height: 2.65cqw; padding: 0 .95cqw; font-size: .62cqw; font-weight: 800; letter-spacing: .08em;
      cursor: pointer; white-space: nowrap; transition: border-color .16s ease, background .16s ease, transform .16s ease;
    }
    .header-action:hover, .small-action:hover { border-color: var(--cyan); background: #12202B; }
    .header-action:focus-visible, .small-action:focus-visible, .app-tile:focus-visible, .favorite-tile:focus-visible,
    .remote button:focus-visible, .dialog button:focus-visible { outline: .18cqw solid var(--cyan); outline-offset: .17cqw; }
    .header-action.primary { border-color: rgba(0, 212, 255, .48); color: #B5F2FF; }
    .hero {
      display: grid; grid-template-columns: 21.4cqw minmax(0, 1fr); align-items: center; gap: 2.1cqw;
      min-height: 15.1cqw; padding: 1.12cqw 1.3cqw; border-radius: 1.05cqw;
      background: linear-gradient(110deg, rgba(17, 24, 37, .98), rgba(11, 15, 24, .94));
      border: 1px solid rgba(124, 147, 176, .19); box-shadow: inset 0 1px 0 rgba(255,255,255,.025);
    }
    .hero-art-wrap {
      display: grid; place-items: center; height: 12.4cqw; overflow: hidden; border-radius: .76cqw;
      background: #080C14; border: 1px solid #293446;
    }
    .hero-art { display: block; width: 100%; height: 100%; object-fit: contain; }
    .hero-art.icon { width: 55%; height: 75%; }
    .hero-copy { min-width: 0; }
    .category { color: var(--cyan); font-size: .65cqw; font-weight: 850; }
    .hero-title { margin: .55cqw 0 .38cqw; font-size: 2.38cqw; line-height: 1.08; letter-spacing: -.045em; font-weight: 850; overflow-wrap: anywhere; }
    .hero-subtitle { margin: 0; color: var(--muted); font-size: .82cqw; }
    .hero-hint { margin-top: 1.35cqw; color: #647386; font: 650 .58cqw/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; letter-spacing: .06em; }
    .section-heading { display: flex; align-items: end; gap: .7cqw; margin: .1cqw 0 .5cqw; }
    .section-title { margin: 0; font-size: .96cqw; line-height: 1.1; font-weight: 850; letter-spacing: .08em; }
    .section-note { color: #8492A4; font-size: .53cqw; line-height: 1.1; }
    .section-spacer { flex: 1; }
    .count { color: var(--cyan); font-size: .58cqw; font-weight: 800; white-space: nowrap; }
    .shelf-scroll { overflow-x: auto; overflow-y: visible; scrollbar-width: none; padding: .3cqw .35cqw .48cqw; margin: -.25cqw -.35cqw; }
    .shelf-scroll::-webkit-scrollbar { display: none; }
    .shelf { display: flex; align-items: stretch; gap: .86cqw; min-width: max-content; }
    .favorite-tile {
      display: flex; align-items: center; gap: 1cqw; width: 16.9cqw; min-height: 8.55cqw;
      padding: .85cqw 1cqw; border: 1px solid #293342; border-radius: .88cqw;
      color: var(--text); text-align: left; background: linear-gradient(145deg, #151B26, #10151E);
      cursor: pointer; transition: transform .16s ease, border-color .16s ease, background .16s ease;
    }
    .favorite-tile:hover, .favorite-tile:focus-visible { transform: translateY(-.12cqw) scale(1.018); border-color: var(--cyan); background: #17202B; }
    .favorite-icon { width: 5.3cqw; height: 5.3cqw; flex: 0 0 auto; object-fit: contain; border-radius: .5cqw; }
    .tile-copy { min-width: 0; display: flex; flex-direction: column; gap: .32cqw; }
    .tile-kind { color: var(--cyan); font-size: .49cqw; font-weight: 850; }
    .tile-label { font-size: .88cqw; font-weight: 760; line-height: 1.2; overflow-wrap: anywhere; }
    .empty-favorites { min-width: 14cqw; min-height: 4cqw; border: 1px dashed #445167; border-radius: .7cqw; background: transparent; color: var(--muted); padding: .7cqw 1cqw; cursor: pointer; }
    .app-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: .72cqw; }
    .app-tile {
      display: flex; flex-direction: column; align-items: center; justify-content: center; gap: .43cqw;
      min-width: 0; min-height: 8.8cqw; padding: .65cqw .35cqw .52cqw;
      color: var(--text); border: 1px solid #202A39; border-radius: .76cqw;
      background: rgba(13, 17, 25, .86); cursor: pointer;
      transition: transform .16s ease, border-color .16s ease, background .16s ease;
    }
    .app-tile:hover, .app-tile:focus-visible { transform: translateY(-.1cqw) scale(1.035); border-color: var(--cyan); background: #151D29; }
    .app-icon { width: 4.35cqw; height: 4.35cqw; object-fit: contain; }
    .app-label { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: .65cqw; font-weight: 700; }
    .privacy {
      margin-top: .45cqw; padding-top: .95cqw; border-top: 1px solid rgba(108, 126, 151, .17);
      color: #738195; font-size: .52cqw; line-height: 1.45;
    }
    .privacy strong { color: #9CAABD; font-weight: 750; }
    .preview-caption { display: flex; justify-content: space-between; gap: 20px; max-width: 1600px; margin: 14px auto 0; color: #8E9BAC; font-size: 12px; line-height: 1.5; }
    .preview-caption strong { color: #C8D3E1; }
    .interaction { display: flex; align-items: center; justify-content: space-between; gap: 20px; max-width: 1600px; margin: 18px auto 0; }
    .key-hint { color: #91A0B4; font-size: 12px; line-height: 1.6; }
    .key-hint kbd { display: inline-block; padding: 2px 6px; margin: 0 2px; border: 1px solid #364254; border-bottom-width: 2px; border-radius: 4px; color: #E5EDF7; background: #111823; font: 700 11px ui-monospace, monospace; }
    .remote { display: flex; align-items: center; gap: 5px; }
    .remote button { width: 35px; height: 32px; border: 1px solid #354154; border-radius: 7px; color: #DCE7F3; background: #111823; cursor: pointer; }
    .remote button.ok { width: 55px; color: var(--cyan); font-weight: 800; font-size: 10px; }
    .toast {
      position: fixed; z-index: 10; left: 50%; bottom: 28px; transform: translate(-50%, 16px);
      max-width: min(90vw, 560px); padding: 12px 16px; border: 1px solid rgba(0,212,255,.4); border-radius: 10px;
      color: #E9F8FB; background: rgba(12, 20, 29, .97); box-shadow: 0 18px 55px #0009;
      font-size: 13px; opacity: 0; pointer-events: none; transition: opacity .2s ease, transform .2s ease;
    }
    .toast.show { opacity: 1; transform: translate(-50%, 0); }
    dialog {
      width: min(680px, calc(100vw - 32px)); max-height: min(78vh, 760px); padding: 0;
      color: var(--text); border: 1px solid #354154; border-radius: 16px; background: #10151E;
      box-shadow: 0 28px 100px #000B;
    }
    dialog::backdrop { background: rgba(0, 0, 0, .76); backdrop-filter: blur(4px); }
    .dialog { padding: 20px; }
    .dialog-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
    .dialog h2 { margin: 0; font-size: 22px; }
    .dialog p { margin: 7px 0 16px; color: var(--muted); font-size: 13px; line-height: 1.5; }
    .dialog-list { display: grid; gap: 8px; max-height: 56vh; overflow: auto; }
    .dialog-row { display: flex; align-items: center; gap: 12px; min-height: 58px; padding: 8px 10px; border: 1px solid #293342; border-radius: 9px; background: #0B1018; }
    .dialog-row img { width: 40px; height: 40px; object-fit: contain; }
    .dialog-row span { flex: 1; font-size: 14px; font-weight: 700; }
    .dialog-row button, .dialog-close { min-height: 34px; padding: 0 12px; border: 1px solid #405067; border-radius: 7px; color: #E9F6FA; background: #141D29; font-size: 11px; font-weight: 800; letter-spacing: .06em; cursor: pointer; }
    .dialog-row button.pinned { border-color: rgba(0,212,255,.46); color: #B6F2FF; background: rgba(0,212,255,.08); }
    @media (max-width: 800px) {
      .page { width: min(100% - 22px, 720px); padding-top: 16px; }
      .page-heading { display: block; }
      .badge { display: inline-block; margin-top: 12px; }
      .tv-screen { min-height: 760px; aspect-ratio: auto; border-radius: 12px; }
      .screen-content { padding: 22px 18px 18px; gap: 14px; }
      .topbar { min-height: 52px; gap: 8px; }
      .brand { gap: 8px; }
      .brand img { width: 38px; height: 38px; }
      .brand-name { font-size: 14px; }
      .brand-tag { font-size: 8px; }
      .clock { display: none; }
      .header-action { min-height: 34px; padding: 0 9px; border-radius: 6px; font-size: 8px; }
      .hero { grid-template-columns: 35% minmax(0,1fr); gap: 13px; min-height: 120px; padding: 10px; border-radius: 10px; }
      .hero-art-wrap { height: auto; aspect-ratio: 16/9; border-radius: 8px; }
      .category { font-size: 8px; }
      .hero-title { margin: 7px 0 4px; font-size: clamp(19px, 5vw, 30px); }
      .hero-subtitle { font-size: 11px; }
      .hero-hint { display: none; }
      .section-heading { margin: 3px 0 6px; gap: 8px; }
      .section-title { font-size: 12px; }
      .section-note, .count { font-size: 8px; }
      .shelf-scroll { padding: 5px 4px 8px; margin: -3px -4px; }
      .shelf { gap: 8px; }
      .favorite-tile { width: 205px; min-height: 102px; gap: 10px; padding: 10px; border-radius: 9px; }
      .favorite-icon { width: 58px; height: 58px; }
      .tile-kind { font-size: 7px; }
      .tile-label { font-size: 12px; }
      .app-grid { grid-template-columns: repeat(3, minmax(0,1fr)); gap: 8px; }
      .app-tile { min-height: 104px; gap: 5px; padding: 8px 4px; border-radius: 8px; }
      .app-icon { width: 48px; height: 48px; }
      .app-label { font-size: 10px; }
      .privacy { margin-top: 6px; padding-top: 12px; font-size: 8px; }
      .preview-caption, .interaction { display: block; }
      .remote { margin-top: 14px; }
    }
    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; }
    }
  </style>
</head>
<body>
  <main class="page">
    <header class="page-heading">
      <div>
        <div class="eyebrow">Core Builds · private launcher prototype</div>
        <h1>Offline home-screen preview</h1>
        <p>A self-contained design illustration you can open without internet. Use the arrow keys and Enter, or click the tiles. The Android launcher remains local-only and keeps each installed app's own identity.</p>
      </div>
      <div class="badge">DESIGN ILLUSTRATION · NOT A SCREENSHOT</div>
    </header>

    <div class="screen-wrap">
      <section class="tv-screen" id="tv-screen" aria-label="Interactive Core Builds Android TV launcher design illustration">
        <div class="screen-content">
          <header class="topbar">
            <div class="brand">
              <img id="brand-mark" alt="Core Builds mark">
              <div class="brand-copy">
                <div class="brand-name">CORE BUILDS</div>
                <div class="brand-tag">YOUR TV · YOUR APPS</div>
              </div>
            </div>
            <div class="top-spacer"></div>
            <div class="clock" aria-label="Local preview clock">
              <div class="clock-time" id="clock-time">--:--</div>
              <div class="clock-date" id="clock-date"></div>
            </div>
            <div class="actions" aria-label="Launcher actions">
              <button type="button" class="header-action" data-group="actions" data-action-index="0" id="edit-favorites">EDIT FAVOURITES</button>
              <button type="button" class="header-action primary" data-group="actions" data-action-index="1" id="set-home">SET AS HOME</button>
            </div>
          </header>

          <section class="hero" aria-live="polite" aria-label="Focused app spotlight">
            <div class="hero-art-wrap"><img class="hero-art" id="hero-art" alt=""></div>
            <div class="hero-copy">
              <div class="category" id="hero-category">TV APP · READY TO OPEN</div>
              <h2 class="hero-title" id="hero-title">Core Builds</h2>
              <p class="hero-subtitle" id="hero-subtitle">Press OK to open.</p>
              <div class="hero-hint">FOCUS PREVIEW · D-PAD NAVIGATION</div>
            </div>
          </section>

          <section aria-labelledby="favorites-title">
            <div class="section-heading">
              <h2 class="section-title" id="favorites-title">FAVOURITES</h2>
              <span class="section-note">Quick access to the apps you choose.</span>
              <span class="section-spacer"></span>
              <span class="count" id="favorite-count"></span>
            </div>
            <div class="shelf-scroll" id="favorites-scroll"><div class="shelf" id="favorites-row"></div></div>
          </section>

          <section id="recent-section" hidden aria-labelledby="recent-title">
            <div class="section-heading">
              <h2 class="section-title" id="recent-title">RECENTLY OPENED</h2>
              <span class="section-note">Fast access · saved on this TV only.</span>
              <span class="section-spacer"></span>
              <span class="count">LOCAL · LAST 5</span>
            </div>
            <div class="shelf-scroll"><div class="shelf" id="recent-row"></div></div>
          </section>

          <section aria-labelledby="apps-title">
            <div class="section-heading">
              <h2 class="section-title" id="apps-title">ALL APPS</h2>
              <span class="section-note">Installed-app catalogue · alphabetical.</span>
              <span class="section-spacer"></span>
              <span class="count" id="app-count"></span>
            </div>
            <div class="app-grid" id="app-grid"></div>
          </section>

          <footer class="privacy"><strong>PRIVATE ·</strong> no ads or account; recent app history stays on this device. <strong>DESIGN ILLUSTRATION · NOT A SCREENSHOT.</strong> The real launcher uses installed package icons and banners.</footer>
        </div>
      </section>
    </div>

    <div class="preview-caption">
      <div><strong>Offline by design.</strong> CSS, JavaScript, and repository-owned icon-pack artwork are embedded in this HTML; it makes no network requests.</div>
      <div>Illustrative app inventory · no vendor marks fabricated</div>
    </div>
    <div class="interaction">
      <div class="key-hint"><kbd>←</kbd><kbd>↑</kbd><kbd>→</kbd><kbd>↓</kbd> navigate · <kbd>Enter</kbd> preview launch · <kbd>F</kbd> toggle favourite · browser state stays local</div>
      <div class="remote" aria-label="On-screen remote controls">
        <button type="button" data-move="left" aria-label="Move left">←</button>
        <button type="button" data-move="up" aria-label="Move up">↑</button>
        <button type="button" data-move="down" aria-label="Move down">↓</button>
        <button type="button" data-move="right" aria-label="Move right">→</button>
        <button type="button" class="ok" id="remote-ok" aria-label="OK">OK</button>
      </div>
    </div>
  </main>

  <dialog id="favorites-dialog" aria-labelledby="dialog-title">
    <div class="dialog">
      <div class="dialog-header">
        <h2 id="dialog-title">Choose favourites</h2>
        <button type="button" class="dialog-close" id="close-dialog">DONE</button>
      </div>
      <p>Pin or unpin apps for this illustration. The preview saves its sample state only in this browser; the Android app saves preferences on the TV.</p>
      <div class="dialog-list" id="dialog-list"></div>
    </div>
  </dialog>
  <div class="toast" id="toast" role="status" aria-live="polite"></div>

  <script>
    const ASSETS = __ASSETS__;
    const APPS = __APPS__;
    const DEFAULT_FAVORITES = APPS.filter(app => app.pinned).map(app => app.id);
    const STORAGE_FAVORITES = "corebuilds-launcher-preview-favorites-v1";
    const STORAGE_RECENTS = "corebuilds-launcher-preview-recents-v1";
    const screen = document.getElementById("tv-screen");
    const dialog = document.getElementById("favorites-dialog");
    const state = {
      favorites: loadList(STORAGE_FAVORITES, DEFAULT_FAVORITES),
      recents: loadList(STORAGE_RECENTS, []),
      selected: "core-builds",
      navGroup: "favorites",
      navIndex: 0,
      toastTimer: 0
    };
    const byId = new Map(APPS.map(app => [app.id, app]));
    const sortedApps = [...APPS].sort((a, b) => a.label.localeCompare(b.label, undefined, {sensitivity: "base"}));

    function loadList(key, fallback) {
      try {
        const value = JSON.parse(localStorage.getItem(key));
        return Array.isArray(value) ? value.filter(item => typeof item === "string") : [...fallback];
      } catch (_) { return [...fallback]; }
    }
    function storeList(key, value) {
      try { localStorage.setItem(key, JSON.stringify(value)); } catch (_) { /* file:// storage may be disabled */ }
    }
    function appFor(id) { return byId.get(id); }
    function visibleGroups() {
      const groups = ["actions", "favorites"];
      if (visibleRecents().length) groups.push("recents");
      groups.push("apps");
      return groups;
    }
    function groupItems(group) {
      return Array.from(screen.querySelectorAll(`[data-group="${group}"]`));
    }
    function focusItem(group, index) {
      const items = groupItems(group);
      if (!items.length) return;
      const nextIndex = Math.max(0, Math.min(index, items.length - 1));
      state.navGroup = group;
      state.navIndex = nextIndex;
      items[nextIndex].focus({preventScroll: true});
      items[nextIndex].scrollIntoView({block: "nearest", inline: "nearest", behavior: "smooth"});
    }
    function setSelected(id) {
      const app = appFor(id);
      if (!app) return;
      state.selected = id;
      document.getElementById("hero-category").textContent = app.tv ? "TV APP · READY TO OPEN" : "APP · READY TO OPEN";
      document.getElementById("hero-title").textContent = app.label;
      const heroImage = document.getElementById("hero-art");
      heroImage.src = ASSETS[app.banner] || ASSETS[app.icon];
      heroImage.classList.toggle("icon", !app.banner);
      heroImage.alt = `${app.label} icon-pack artwork`;
    }
    function createTile(app, group, wide) {
      const button = document.createElement("button");
      button.type = "button";
      button.dataset.group = group;
      button.dataset.appId = app.id;
      button.className = wide ? "favorite-tile" : "app-tile";
      button.setAttribute("aria-label", `${app.label}, ${app.tv ? "TV app" : "app"}. Press Enter to preview launch.`);
      const image = document.createElement("img");
      image.src = ASSETS[app.icon];
      image.alt = "";
      image.className = wide ? "favorite-icon" : "app-icon";
      image.draggable = false;
      button.append(image);
      if (wide) {
        const copy = document.createElement("span");
        copy.className = "tile-copy";
        const kind = document.createElement("span");
        kind.className = "tile-kind";
        kind.textContent = app.tv ? "TV APP" : "APP";
        const label = document.createElement("span");
        label.className = "tile-label";
        label.textContent = app.label;
        copy.append(kind, label);
        button.append(copy);
      } else {
        const label = document.createElement("span");
        label.className = "app-label";
        label.textContent = app.label;
        button.append(label);
      }
      button.addEventListener("focus", () => {
        state.navGroup = group;
        state.navIndex = groupItems(group).indexOf(button);
        setSelected(app.id);
      });
      button.addEventListener("click", () => {
        setSelected(app.id);
        button.focus();
        simulateLaunch(app.id);
      });
      return button;
    }
    function visibleRecents() {
      const favorites = new Set(state.favorites);
      return state.recents.filter(id => byId.has(id) && !favorites.has(id)).slice(0, 5);
    }
    function renderFavorites() {
      const row = document.getElementById("favorites-row");
      row.replaceChildren();
      const favorites = sortedApps.filter(app => state.favorites.includes(app.id));
      state.favorites = favorites.map(app => app.id);
      storeList(STORAGE_FAVORITES, state.favorites);
      document.getElementById("favorite-count").textContent = `${favorites.length} PINNED`;
      if (!favorites.length) {
        const empty = document.createElement("button");
        empty.type = "button";
        empty.className = "empty-favorites";
        empty.dataset.group = "favorites";
        empty.textContent = "CHOOSE YOUR FAVOURITES";
        empty.addEventListener("focus", () => { state.navGroup = "favorites"; state.navIndex = 0; });
        empty.addEventListener("click", openFavorites);
        row.append(empty);
        return;
      }
      favorites.forEach(app => row.append(createTile(app, "favorites", true)));
    }
    function renderRecents() {
      const section = document.getElementById("recent-section");
      const row = document.getElementById("recent-row");
      const recentIds = visibleRecents();
      section.hidden = recentIds.length === 0;
      row.replaceChildren();
      recentIds.forEach(id => row.append(createTile(appFor(id), "recents", true)));
    }
    function renderGrid() {
      const grid = document.getElementById("app-grid");
      grid.replaceChildren();
      sortedApps.forEach(app => grid.append(createTile(app, "apps", false)));
      document.getElementById("app-count").textContent = `${APPS.length} APPS · ${APPS.filter(app => app.tv).length} TV READY`;
    }
    function render() {
      renderFavorites();
      renderRecents();
      renderGrid();
      setSelected(state.selected);
      renderDialog();
    }
    function toggleFavorite(id) {
      if (!byId.has(id)) return;
      const previousGroup = state.navGroup;
      const previousIndex = state.navIndex;
      if (state.favorites.includes(id)) state.favorites = state.favorites.filter(item => item !== id);
      else state.favorites = [...state.favorites, id];
      state.favorites = sortedApps.filter(app => state.favorites.includes(app.id)).map(app => app.id);
      storeList(STORAGE_FAVORITES, state.favorites);
      render();
      if (!dialog.open) {
        requestAnimationFrame(() => {
          const fallbackGroup = groupItems(previousGroup).length ? previousGroup : (groupItems("favorites").length ? "favorites" : "apps");
          focusItem(fallbackGroup, fallbackGroup === previousGroup ? previousIndex : 0);
        });
      }
      showToast(`${appFor(id).label} ${state.favorites.includes(id) ? "pinned to" : "removed from"} favourites.`);
    }
    function simulateLaunch(id) {
      const app = appFor(id);
      if (!app) return;
      const previousGroup = state.navGroup;
      const previousIndex = state.navIndex;
      state.selected = app.id;
      state.recents = [app.id, ...state.recents.filter(item => item !== app.id)].slice(0, 5);
      storeList(STORAGE_RECENTS, state.recents);
      renderRecents();
      setSelected(app.id);
      requestAnimationFrame(() => {
        const items = groupItems(previousGroup);
        const selectedIndex = items.findIndex(item => item.dataset.appId === app.id);
        const group = items.length ? previousGroup : (groupItems("favorites").length ? "favorites" : "apps");
        focusItem(group, selectedIndex >= 0 ? selectedIndex : previousIndex);
      });
      showToast(`${app.label}: launch preview only — no app was opened.`);
    }
    function openFavorites() {
      renderDialog();
      if (typeof dialog.showModal === "function") dialog.showModal();
      else dialog.setAttribute("open", "open");
      document.getElementById("close-dialog").focus();
    }
    function renderDialog() {
      const list = document.getElementById("dialog-list");
      list.replaceChildren();
      sortedApps.forEach(app => {
        const row = document.createElement("div");
        row.className = "dialog-row";
        const icon = document.createElement("img");
        icon.src = ASSETS[app.icon];
        icon.alt = "";
        const name = document.createElement("span");
        name.textContent = app.label;
        const button = document.createElement("button");
        const pinned = state.favorites.includes(app.id);
        button.className = pinned ? "pinned" : "";
        button.textContent = pinned ? "PINNED · REMOVE" : "ADD";
        button.setAttribute("aria-pressed", String(pinned));
        button.addEventListener("click", () => {
          toggleFavorite(app.id);
          renderDialog();
          const replacement = Array.from(list.querySelectorAll("button"))[sortedApps.indexOf(app)];
          if (replacement) replacement.focus();
        });
        row.append(icon, name, button);
        list.append(row);
      });
    }
    function showToast(message) {
      const toast = document.getElementById("toast");
      toast.textContent = message;
      toast.classList.add("show");
      clearTimeout(state.toastTimer);
      state.toastTimer = setTimeout(() => toast.classList.remove("show"), 2300);
    }
    function move(direction) {
      const groups = visibleGroups();
      const active = document.activeElement;
      if (active && active.dataset && active.dataset.group) {
        state.navGroup = active.dataset.group;
        state.navIndex = groupItems(state.navGroup).indexOf(active);
      }
      let group = state.navGroup;
      if (!groups.includes(group)) group = groups[0];
      let index = state.navIndex;
      const items = groupItems(group);
      if (!items.length) return;
      if (group === "apps" && (direction === "up" || direction === "down")) {
        const next = index + (direction === "up" ? -6 : 6);
        if (next >= 0 && next < items.length) focusItem(group, next);
        return;
      }
      if (direction === "left" || direction === "right") {
        const delta = direction === "left" ? -1 : 1;
        focusItem(group, index + delta);
        return;
      }
      const groupIndex = groups.indexOf(group);
      const nextGroupIndex = groupIndex + (direction === "up" ? -1 : 1);
      if (nextGroupIndex < 0 || nextGroupIndex >= groups.length) return;
      const nextGroup = groups[nextGroupIndex];
      focusItem(nextGroup, Math.min(index, groupItems(nextGroup).length - 1));
    }
    function focusSelected() {
      const items = groupItems(state.navGroup);
      if (items[state.navIndex]) items[state.navIndex].click();
      else if (state.selected) simulateLaunch(state.selected);
    }
    function updateClock() {
      const now = new Date();
      document.getElementById("clock-time").textContent = new Intl.DateTimeFormat(undefined, {hour: "numeric", minute: "2-digit"}).format(now);
      document.getElementById("clock-date").textContent = new Intl.DateTimeFormat(undefined, {weekday: "short", month: "short", day: "numeric"}).format(now).toUpperCase();
    }

    document.getElementById("edit-favorites").addEventListener("click", openFavorites);
    document.getElementById("set-home").addEventListener("click", () => showToast("On a TV, this button opens Android's Home-app settings. This preview changes no system setting."));
    document.getElementById("close-dialog").addEventListener("click", () => dialog.close());
    document.getElementById("remote-ok").addEventListener("click", focusSelected);
    document.querySelectorAll("[data-move]").forEach(button => button.addEventListener("click", () => move(button.dataset.move)));
    dialog.addEventListener("close", () => {
      const item = groupItems(state.navGroup)[state.navIndex];
      if (item) item.focus({preventScroll: true});
    });
    document.addEventListener("keydown", event => {
      if (dialog.open) {
        if (event.key === "Escape") dialog.close();
        return;
      }
      if (["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key)) {
        event.preventDefault();
        move(event.key.slice(5).toLowerCase());
      } else if (event.key === "Enter") {
        const active = document.activeElement;
        if (active && active.dataset && active.dataset.appId) {
          event.preventDefault();
          simulateLaunch(active.dataset.appId);
        }
      } else if (event.key.toLowerCase() === "f") {
        const active = document.activeElement;
        const id = active && active.dataset ? active.dataset.appId : state.selected;
        if (id) { event.preventDefault(); toggleFavorite(id); }
      } else if (event.key === "Home") {
        event.preventDefault();
        const first = groupItems("favorites").length ? "favorites" : "apps";
        focusItem(first, 0);
      }
    });

    document.getElementById("brand-mark").src = ASSETS.brandMark;
    render();
    updateClock();
    setInterval(updateClock, 30000);
    requestAnimationFrame(() => focusItem("favorites", Math.max(0, state.navIndex)));
  </script>
</body>
</html>
'''


def data_uri(path: Path) -> str:
    encoded = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:image/webp;base64,{encoded}"


def build_html() -> str:
    assets: dict[str, str] = {
        "brandMark": data_uri(LAUNCHER_ASSET_DIR / "core_builds_mark.webp"),
    }
    apps: list[dict[str, object]] = []
    for app_id, label, slug, tv, pinned in APP_SOURCES:
        icon_path = ICON_DIR / f"{slug}.webp"
        if not icon_path.is_file():
            raise FileNotFoundError(f"Missing icon-pack sample art: {icon_path}")
        icon_key = f"{slug}Icon"
        assets[icon_key] = data_uri(icon_path)
        banner_path = ICON_DIR / f"{slug}_banner.webp"
        banner_key: str | None = None
        if banner_path.is_file():
            banner_key = f"{slug}Banner"
            assets[banner_key] = data_uri(banner_path)
        apps.append({
            "id": app_id,
            "label": label,
            "icon": icon_key,
            "banner": banner_key,
            "tv": tv,
            "pinned": pinned,
        })
    html = HTML_TEMPLATE.replace("__ASSETS__", json.dumps(assets, separators=(",", ":")))
    html = html.replace("__APPS__", json.dumps(apps, separators=(",", ":")))
    return html


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if the checked-in preview is stale")
    args = parser.parse_args()
    expected = build_html()
    if args.check:
        if not OUTPUT.is_file() or OUTPUT.read_text(encoding="utf-8") != expected:
            print(f"Offline preview is missing or stale: {OUTPUT.relative_to(ROOT)}")
            return 1
        print(f"Offline preview is current: {OUTPUT.relative_to(ROOT)}")
        return 0
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(expected, encoding="utf-8")
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size:,} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
