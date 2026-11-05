# Core Builds launcher — design illustrations

These files are **design illustrations, not screenshots from an APK or Android TV device**. The PNGs are 1920×1080 static 0.1-era concepts; both HTML files are interactive, self-contained browser illustrations. Each is labelled **“DESIGN ILLUSTRATION · NOT A SCREENSHOT”** on the page and inside the TV-style frame.

The visual direction starts from the Core Builds icon pack: Void/Night surfaces, the cyan Core Builds hex-and-diamond mark, transparent 16:9 banner lockups, and a restrained cyan focus outline. The Core Builds marks and banners shown are the suite's own assets from `app/src/main/res/drawable-nodpi/`. Named third-party examples use this repository's generated Core Builds icon-pack glyphs for those exact catalog entries—not vendor logos or invented substitutes. The app inventory is illustrative, not a device scan.

On a real device, the launcher reads each installed package's own label, icon, and optional TV banner and displays those assets without redrawing them. If an app does not publish a TV banner, its package icon is used instead.

- `core-launcher-home.png` — home screen concept with a Core Shift spotlight and icon-pack-style favourites.
- `core-launcher-all-apps.png` — same design language with remote focus moved into the All Apps grid.
- `core-launcher-offline.html` — historical 0.1-era offline home-screen illustration. Use the arrow keys and Enter, click the tiles, press **F** to toggle a favourite, or use **EDIT FAVOURITES**. A simulated launch updates a conditional Recent row; it never opens another app or changes Android settings. Browser preview state is stored only in local browser storage.
- `core-launcher-polish.html` — 0.4 connected-TV interaction study: Full/Calm/Off motion, a focus-following backdrop, a Suggested row, local ranked search, A–Z/Most used/TV first/Newest install ordering, current-value settings, quick-settings handoff notes, and a browser-only versioned backup/restore simulation. Customise also illustrates Usage access, Network wallpaper, Launch on boot and Status notification as selected values and permission handoff notes; these connected features are simulated only, never executed in the browser. Arrows and Enter navigate; **F** toggles a favourite, **H** hides an app, **S** opens search, **C** opens Customise, and **Esc** goes back. It never contacts a network, opens Android settings or changes a device.

The original preview's Up/Down app-grid movement follows the rendered column count; Up from the first app row returns to the preceding visible shelf. The 0.4 preview uses each rendered row's geometry for D-pad movement and shows motion changes, search result ranking, selected values, owner-granted permission states and connected-feature explanations. Neither HTML emulates the native long-press app-actions dialog, Android App info handoff, or TalkBack. This browser focus model is an illustration only, not evidence of Android remote behavior; the [physical-device checklist](../../launcher/README.md#required-physical-device-validation-merge-gate) remains authoritative.

The HTML embeds its styles, script, and WebP artwork as data URIs. A restrictive Content Security Policy blocks network connections and allows only the inline code and embedded images; there are no external fonts, stylesheets, scripts, or assets. It can be opened directly from disk. Rebuild it after changing the referenced artwork with:

```bash
python3 tools/build_launcher_offline_preview.py
python3 tools/build_launcher_offline_preview.py --check
python3 tools/build_launcher_polish_preview.py
python3 tools/build_launcher_polish_preview.py --check
```
