# SPORT FM TV — request #382

Reviewed **26 October 2026**.

## Mapping evidence

The in-app scanner report in
[#382](https://github.com/brevityA/CoreBuildsApps/issues/382) supplies:

`gr.sportfmtv.app/com.yinzcam.connectedtv.MainActivity`

Device: SHIELD Android TV, Android 11, pack 2.5.1, Projectivy Launcher.
The installed package is not the Java activity namespace. Only the reported
component is added; `gr.sportfmtv.app/.MainActivity` is not an equivalent
spelling and is not invented.

## Visual evidence

- [Google Play listing for the same package](https://play.google.com/store/apps/details?id=gr.sportfmtv.app)
  identifies SPORTFM TV and links the publisher's sports service.
- [Official SPORT FM TV site](https://www.sportfmtv.gr/) links that same Play
  package and shows the red crest, light Greek lettering and red TV tag.
- `launcher-icon.png` is the search-provided thumbnail of the Google-hosted
  Play icon shown by [AppBrain's listing mirror](https://www.appbrain.com/app/sportfm-tv/gr.sportfmtv.app),
  **not an extracted APK resource**. Its red TV tag supplies the recorded
  **#CE1039** pixel sample.
- `official-channel-reference.webp` is a search-provided thumbnail from the
  official site, corroborating the crest. Its **channel number 1 is not part
  of the app glyph**.
- The publisher's [app announcement](https://www.skai.gr/news/sports/to-neo-app-tou-spor-fm-tv-einai-gegonos-diathesimo-gia-android-ios)
  shows the same crest and TV tag.

`visual-references.json` records source pages, rights, SHA-256 checksums and
the exact colour-sampling rectangle/method. These are visual reference
materials only, not a registry of artwork to bundle into an APK.

## Core Builds interpretation

`sportfm_crest()` keeps the crest, **FM** lettering and **TV** tag, redrawn
with the common rounded monoline strokes and stroke-letter alphabet.
The vendor's compressed Greek lettering is deliberately not traced into
a dense miniature. The full **SPORT FM TV** name appears in the normal
banner layout, with the **SPORT** category.

The crest and TV tag use the sampled red through the shared contrast policy.
FM uses the observed light lettering, declared as a secondary paint; white
renders as the pack's off-white ink. There is no private wordmark-only banner,
extra background, imported vector or glow.

Original reference artwork and trademarks remain their owners' property;
no MIT or CC0 licence is implied. See
[THIRD_PARTY_NOTICES.md](../../../THIRD_PARTY_NOTICES.md).
