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
## Vendored code: Nayuki QR Code generator (Java)

`app/src/main/java/io/nayuki/qrcodegen/QrCode.java`,
`app/src/main/java/io/nayuki/qrcodegen/QrSegment.java` and
`app/src/main/java/io/nayuki/qrcodegen/BitBuffer.java` and
`app/src/main/java/io/nayuki/qrcodegen/DataTooLongException.java` (the
encoder's compile surface; the optional `QrSegmentAdvanced` kanji optimiser
and `package-info` are not on the encode path and stay upstream) are
vendored verbatim
from the **QR Code generator library** by Project Nayuki, under the
**MIT License** (copyright Project Nayuki,
<https://www.nayuki.io/page/qr-code-generator-library>), fetched from upstream
commit `3c6d0b3cefb4e049dc337e82237c9644399716a8` on 2026-09-19. SHA-256 at
vendoring: `QrCode.java`
`01715baeb383ec26f7c38138299a2b6e5bbfa36fa607441c79dc9cc76b107c6b`,
`BitBuffer.java`
`d5496452b435423beead30aa356e10a7c0ba4790b7da37ee4a1b80b4df136447`,
`QrSegment.java`
`70f10e518d3a8f1a1862e598a249abaac036a9c6fae4e5892c4b6d24995bb8d7`,
`DataTooLongException.java`
`7661186dde4b27334fd94f4950f58522eaab256274388152e5cb8e35ef463e1a`.

The on-device auditor renders its prefilled-issue QR codes with this encoder
(`QrBitmap.kt` is the Core Builds wrapper: error correction M, four-module
quiet zone, black-on-white modules). It is vendored rather than pulled as a
dependency because the pack ships with no runtime libraries at all, and the
two files are the encoder's complete Java surface.
