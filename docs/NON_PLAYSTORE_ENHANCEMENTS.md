# Non-Play Store Superpowers: High-Impact Enhancements for Core Builds Icon Pack

> Provenance: enhancement proposal pasted into the repo chat by the owner on
> 2026-09-19 and committed verbatim so the triage in
> `NON_PLAYSTORE_TRIAGE.md` can be checked against exactly what was proposed.
> Feature numbering below is the original's.

Because the **sideloaded / Downloader version** (`iconpack-release.apk`) is not
subject to Google Play Store restrictions, you have complete architectural
freedom. You can access device-level APIs, self-update cryptographically,
inspect installed packages, and provide direct living-room utilities that the
Play Store would reject.

Here are the highest-impact features you can add to the non-Play Store version
right now to make it the ultimate enthusiast TV icon pack.

---

## 1. On-Device "Missing App Auditor" with Instant QR Code Generator

* **The Sideload Advantage:** Google Play heavily scrutinizes `QUERY_ALL_PACKAGES` and installed-package queries. In your sideloaded APK, you can query every installed TV app without limitation.
* **How It Works on TV:**
  1. Add a **"Scan My TV"** or **"Audit Installed Apps"** button in `MainActivity` or `SettingsActivity`.
  2. The app queries all installed packages with `Intent.CATEGORY_LEANBACK_LAUNCHER` and `Intent.CATEGORY_LAUNCHER`.
  3. It diffs them in real-time against `appfilter.xml` and lists every installed app lacking a custom icon.
  4. When the user selects an unmapped app, the TV renders a **large QR Code on screen**.
  5. The user scans the TV screen with their phone camera. The QR code deep-links directly to your prefilled GitHub issue URL (generated via your existing `tools/build_issue_prefills.py` logic) with:
     * App Label (e.g., `TiviMate IPTV`)
     * Component Name (e.g., `ar.tvplayer.tv/ar.tvplayer.tv.ui.MainActivity`)
     * Device Model & Android OS version
* **Why This Wins:** It solves the #1 barrier to user icon requests. Couch users never have to touch ADB, copy/paste terminal commands, or hunt for package names.

---

## 2. In-App Launcher "Cache Buster" & Force Refresh

* **The Living-Room Problem:** When a TV icon pack updates, Android TV launchers (especially Projectivy) aggressively cache rendered card bitmaps in memory and disk. Users constantly complain: *"I updated the icon pack, but the old icons are still showing!"*
* **The Solution:** Add a dedicated **"Force Refresh Launcher"** button in the app:
  * Re-fires the `com.spocky.projengmenu.APPLY_ICONPACK` intent.
  * Optionally invokes `activityManager.killBackgroundProcesses("com.spocky.projengmenu")` or opens the system App Info page directly focused on Projectivy so the user can press "Force Stop" with one click.
  * Clears the launcher's bitmap cache without requiring a full TV reboot.

---

## 3. Rich "What's New" Release Highlights in the Sideload Updater

* **The Existing Foundation:** Core Builds already has an exceptionally well-engineered updater (`UpdateChecker.kt` and `UpdateInstaller.kt`) that verifies SHA-256 hashes and APK signing certificates.
* **The Polish Upgrade:**
  * Update `Latestrelease/version.json` on GitHub to include structured release highlights:
    ```json
    {
      "version": "1.8.21",
      "versionCode": 89,
      "url": "https://github.com/.../iconpack-release.apk",
      "highlights": [
        "Added 38 new streaming & IPTV icons",
        "Fixed Stremio and SmartTube activity mappings",
        "Redrawn MUBI glyph for 48px tile clarity",
        "Added 6 new 4K living-room wallpapers"
      ]
    }
    ```
  * In `MainActivity`, when `UpdateChecker` detects a newer build, replace the generic update bar with an interactive **"What's New in v1.8.x"** card showing the highlights before triggering the download.

---

## 4. Universal Dynamic Icon Masking for the Unmapped Long Tail

* **The Problem:** Even with 940+ icons, users will install niche regional IPTV apps, sports APKs, and emulators. When an unmapped app sits next to Core Builds icons, the jarring stock icon breaks the aesthetic.
* **The Solution:** Add `<iconback>`, `<iconmask>`, and `<iconupon>` tags to `appfilter.xml`:
  ```xml
  <resources>
      <!-- Base dark card matching Core Builds #0D1117 chrome -->
      <iconback img="core_card_back" />
      <!-- Soft mask that rounds standard rectangular APK icons -->
      <iconmask img="core_card_mask" />
      <!-- 32px keyline outline consistent with Core Builds stroke geometry -->
      <iconupon img="core_card_keyline" />
      <scale factor="0.75" />
  </resources>
  ```
* **Why This Wins:** Even unmapped sideloaded apps will automatically be framed in a sleek, dark Core Builds container.

---

## 5. Couch-Friendly In-App FAQ & Troubleshooting Guide

* **The Problem:** Valuable troubleshooting information (caching quirks, manual override precedence, Monet wallpaper workarounds) is currently stored in GitHub Markdown files (`docs/DEVICE_FINDINGS.md`). A user on the sofa cannot easily access them.
* **The Solution:** Add an interactive **"Troubleshooting & FAQ"** screen in `SettingsActivity`:
  * **Card 1: Icons Not Updating?** Explains how to force-stop Projectivy or use the Cache Buster.
  * **Card 2: Manual Icon Overrides:** Explains that Projectivy preserves manual icon choices and shows how to reset them to default.
  * **Card 3: Monet Launcher Setup:** Step-by-step guide on how "Send to Monet" works.
  * **Card 4: Sideloaded / Mobile Apps:** Explains why apps with different mobile vs. Leanback activities may need manual assignment.

---

## 6. Interactive Icon Inspector & PNG / Banner Exporter

* **The Current State:** Clicking an icon in `MainActivity` only triggers a transient Android Toast (`toast(getString(R.string.icon_selected_fmt, ...))`).
* **The Upgrade:** Tapping an icon opens an **Inspector Dialog**:
  * Large, high-resolution rendering of the vector mark and its accent glow.
  * Human Name, Category, Drawable ID, and mapped Component Name.
  * **"Export PNG to Pictures"**: Saves the high-res 512×512 PNG or 320×180 banner to `/sdcard/Pictures/CoreBuilds/Icons/` for use in Home Assistant, custom widgets, or alternative launchers.
  * **"Launch App" Button**: If the mapped app is installed on the TV, launch it directly to test the mapping!

---

## 7. Core Builds Living-Room Suite Hub

* **The Sideload Synergy:** You have a suite of 7 specialized Android TV apps (`Core Shift`, `Core Line`, `Core Motion`, `Core Doctor`, `Pop`, `Pixel Neon`).
* **The Feature:** Add a **"Core Builds Suite"** drawer or tab in the app:
  * Shows the status of each companion app:
    * **Core Shift** (Screensaver): Installed (v2.3.5) / Update Available / Not Installed.
    * **Core Line** (Sports Ticker): Installed / Launch.
    * **Core Doctor** (Diagnostics): Installed / Launch.
  * For apps not installed, display their Downloader code (`8829421` for Shift, `7375676` for Line) with a one-click intent to open Downloader directly to that code!

---

## 8. D-Pad Fast Navigation (Bumper Alphabet Skips & Category Badges)

* **Alphabet Remote Bumper Skipping:** In `MainActivity`, intercept `KEYCODE_CHANNEL_UP` / `KEYCODE_CHANNEL_DOWN` (or `KEYCODE_MEDIA_FAST_FORWARD` / `KEYCODE_MEDIA_REWIND`) on the TV remote to jump forward and backward by letter (A–Z) across the 940 icons.
* **Category Badges:** Display item counts on the category chips in `ChipAdapter`:
  * `Streaming (184)` · `Media (92)` · `Tools (76)` · `Debrid (24)` · `Games (48)`

---

## Recommended Implementation Order

| Feature | User Impact | Implementation Effort |
| :--- | :---: | :---: |
| **1. In-App FAQ & Troubleshooting** | High | Low (1–2 hours) |
| **2. Interactive Icon Inspector Dialog** | High | Low (2–3 hours) |
| **3. Structured "What's New" Changelog** | High | Low (2 hours) |
| **4. On-Device Missing App Scanner + QR Generator** | Very High | Medium (1 day) |
| **5. Universal Icon Masking (`iconback`/`iconmask`)** | Very High | Medium (half day) |
| **6. Launcher Cache Buster / Force Refresh** | Medium | Low (2 hours) |
| **7. Core Builds Suite Hub Launcher** | Medium | Low (half day) |
