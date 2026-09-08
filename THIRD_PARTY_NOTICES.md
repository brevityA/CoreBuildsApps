# Icon artwork sources and rights

The code and original Core Builds artwork remain under the repository's MIT
licence. **That licence does not grant rights to third-party brands or imply
endorsement.** Brand names, logos and trademarks belong to their owners and are
used here to identify the installed apps they represent.

## Source-checked silhouettes

The `artwork` section of [`tools/catalog.json`](tools/catalog.json) records the
source URL, immutable upstream revision (where available), file checksum,
review date, licence description and intentional adaptation for every sourced
mark. It is the source registry for the renderer, not a live download list.

The following files in `tools/brandmarks/` are taken from **Simple Icons**,
which dedicates its vector contributions under **CC0 1.0 Universal**:

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
Upstream: <https://github.com/simple-icons/simple-icons>. CC0 for those vector
contributions does **not** waive an owner's trademark or other brand rights.
The catalog links the underlying brand reference as well as the upstream file.

These are **flat-colour adaptations**, not certifications of brand-guideline
compliance. Paths keep their proportions and transparent counters; Classic
uses a light-ink alternative when the catalog accent is unreadable on its
recommended dark cards. Pop and Pixel Neon deliberately use their own palettes
and treatments. In particular, Netflix's monochrome silhouette does not
reproduce the original multi-red shading.

## NoBuffr

[`tools/reference/nobuffr/`](tools/reference/nobuffr/) contains the small
launcher-artwork resources extracted from the publicly supplied vendor APK,
plus a SHA-256 receipt and the actual launcher component. No APK is included,
installed, executed, or redistributed as part of this pack.

`tools/brandmarks/nobuffr.svg` is vectorised from the alpha silhouettes of that
APK's 432px adaptive foreground. It preserves the stacked lettering and the
interrupted underline. Classic keeps the white lettering and uses a flat cyan
sample from the source underline rather than claiming to reproduce its gradient.
The NoBuffr artwork remains its owner's property; it is **not** offered here
under MIT or Simple Icons' CC0 dedication. Source:
<https://downloads.nobuffr.com/android/nobuffr.apk>.

## Original reference-informed geometry

The remaining functions in `tools/glyphs.py` are original Core Builds geometry.
The BBC iPlayer three-beam construction is a single-pink interpretation of its
service icon, not an imported BBC asset. Unresearched long-tail glyphs remain
labelled as such in the fidelity/demand notes; having a catalog entry does not
mean the official logo has been verified.
