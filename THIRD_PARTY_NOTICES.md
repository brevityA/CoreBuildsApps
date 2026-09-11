# Brand references and rights

The code and original Core Builds artwork remain under the repository's MIT
licence. **That licence does not grant rights to third-party brands or imply
endorsement.** App names, logos and trademarks belong to their respective
owners and are used to identify the apps represented by the pack.

## Core Builds renders its own icon language

The Classic icons are **Core Builds-authored geometric interpretations**, not
vendor silhouettes pasted into an icon pack. The reviewed glyphs use rounded
monoline strokes, one accent, transparent interiors and the same Outfit /
category / cyan-violet-rail banner as their neighbours. The original fallback
letters remain the pack's bundled, path-outlined Outfit typography.

The `artwork` section of [`tools/catalog.json`](tools/catalog.json) is a
**reference registry**, not a rendering registry. Each entry has
`usage: reference-only`, a source URL, local file checksum, review date, rights
notice and treatment. `tools/brandmarks.py` can inspect these references but
cannot register them as pack glyphs. Actual icon geometry is in
`tools/glyphs.py`.

## Simple Icons reference files

The following files in `tools/brandmarks/` are retained as **offline reference
material** from Simple Icons, whose vector contributions are dedicated under
**CC0 1.0 Universal**:

- `crunchyroll.svg`
- `deezer.svg`
- `jellyfin.svg`
- `kodi.svg`
- `mubi.svg`
- `netflix.svg`
- `nordvpn.svg`
- `paramountplus.svg`
- `plex.svg`
- `protonvpn.svg`
- `spotify.svg`
- `stremio.svg`
- `twitch.svg`
- `youtube.svg`
- `youtubekids.svg`
- `youtubemusic.svg`

The full CC0 text is in
[`tools/brandmarks/LICENSE-CC0.md`](tools/brandmarks/LICENSE-CC0.md).
Upstream: <https://github.com/simple-icons/simple-icons>. The immutable upstream
file and underlying brand reference are linked in the catalog. CC0 for the
vector contribution does **not** waive an owner's trademark/brand rights.
These files are not rendered directly or bundled as vendor logos in our APKs.

## NoBuffr

[`tools/reference/nobuffr/`](tools/reference/nobuffr/) retains the small original
artwork resources extracted from the supplied vendor APK, plus its SHA-256
receipt and the actual launcher component. No APK is committed, installed,
executed or redistributed with this pack.

`tools/brandmarks/nobuffr.svg` is the previous vectorised lettering/underline
reference. It is **not the rendered icon**. The rejected standalone white
wordmark has been removed from the icon and banner rendering route.

The actual Classic glyph is an original Core Builds monoline construction:
the observed lowercase **no** cue and interrupted buffer underline, in one
accent. The full **NoBuffr** name is supplied by the standard Outfit banner
label, not vendor typography. This is an explicit stylistic interpretation,
not a claim to reproduce every letter of the vendor logo.

NoBuffr's original/reference artwork remains its owner's property and is not
covered by MIT or Simple Icons' CC0 dedication. Source:
<https://downloads.nobuffr.com/android/nobuffr.apk>.

## Interpretation is not endorsement

The sources establish recognisable cues and colours; they are not permission
to claim official approval. Classic's light-ink fallback is a readability
treatment for dark cards, not a brand-colour change. Pop and Pixel Neon retain
their separate, deliberately stylised treatments. Source/reference materials
are distinguished from the Core Builds-authored output throughout the catalog
and fidelity notes.
