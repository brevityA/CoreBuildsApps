# Icon reference research, pass 2 (2026-09-26)

Pass 1 looked for official launcher icons only for apps on the fallback palette, and only on Google Play, F-Droid and IzzyOnDroid. That left 238 generic letter tiles with no reference icon to draw, colour or letter from. Pass 2 looked for all 238 again and added the APKCombo mirror of each app's Play listing as a fourth source. A match counts only when the page's package name is exactly the one in the catalogue's component.

## Result

- **136 verified references**: 80 Google Play, 55 APKCombo, 1 F-Droid.
- **13 rejected**: the listing for the catalogue's package is a different app, or only the stock Android placeholder icon (below).
- **89 not found** on any of the four sources (below). Most are sideloaded apps.

From the verified references:
- 70 apps that used a fallback palette colour now use their sampled icon colour. APK Updater and SomaFM kept the palette, because their samples came from a placeholder and from a photo.
- 10 letter tiles take their letters from the logotype.
- The rest stay as they are for now. The symbol icons among them are candidates for the next round of redrawn marks.

## Rejected: the package's listing is a different app

These rows need an owner check. Either the component is mis-mapped, or the package was reused or renamed. **Mappings were not changed.**

| Catalogue name | Package | What the listing is |
|---|---|---|
| AIMI TV | `com.aimitv.wplg.news` | Local 10+ |
| Aplicativo X | `com.aplicativox.supremetv` | Supreme TV |
| Better XC | `com.redphx.betterxc` | Better xCloud (stock Android placeholder icon) |
| BP Box | `com.bp.box` | İnat BOX (stock Android placeholder icon) |
| Brkchen Music | `com.brkchen.music` | Radon Tunes for Apple Music |
| Freevee | `com.amazon.spiderpork` | Amazon Luna (Fire TV) (Android TV) |
| GuidePlus | `com.guideplus.co` | All Weather |
| IPTV3u | `com.iptv3u` | Dimplay: Live Player |
| Kemo IPTV | `de.cyberdream.iptv.tv.player` | dream Player IPTV for TV |
| Launcher Manager | `com.wolf.lm` | Launcher Manager (stock Android placeholder icon) |
| RAM TV | `com.ram.tv` | RAM Cleaner- Cache Cleaner |
| Simple Player | `com.drama.simpleplayer` | Drama Player |
| Swampdog Media | `com.semperpax.eumc16` | EUMC |

## Not found on Google Play, F-Droid, IzzyOnDroid or APKCombo

AK47Sports, Air Attack 2, AllSaves Social, AniLab, AnikenTV, Anime Cast, Anime One, Anten TV, Användarmanual, Aparat Sport, Award VPN, Cafe Bazaar, Chebut TV, Cinemahd Stable, Clip TV, Clone Hero, CoreELEC Helper, Cricfy, Cyberflix, Damonte, Dansk Filmskat, Elefin, Es De Frontend, Eternal TV, Eternal TV (Nath), FANE TV, FC TV, Falcon Cast, Filimo, Filmnet TV, FindLink, Firedown, Five TV, Flix TV, Football 360, GenPlay, HDRezka, Hdo Box, Jellyfin Enhanced, Jojoy, Kennytv, Lazy IPTV Deluxe, MGS TV, Mediaspelare, Mi Gallery, Miracast, Movie HD, Myiptv, NetMirror, NetMirror TV, Notubetv, Nxsha, OK TV, OTT Navigator, Ocean Streamz, Offshore, OnePix, Onstream, Otf TV, Perfect Player, Perfect TV, PlayLatin, PlayNet, Polygon Player, Premiumize TV, RB Live, RB Main, Rapid Streamz, Rezka, Rutube, Screenscape, Shark TV, Sports Everywhere, Sportzx, Stream Fire, TV, TV Garden, TVLok, Televizo, Tflix, Thunder TV, Ukiku, VPN Dot, Vibra, WeatherBug, Works with Alexa, Xtream Player, Yacine TV, Youcine.

They keep their generic tiles. An owner screenshot of the launcher icon on a device is the remaining honest source.

## Pass 3 (2026-09-27): Aptoide

The 89 apps that were not found were looked up again in Aptoide's app API by exact package name
(`ws75.aptoide.com/api/7/app/getMeta?package_name=…`), under the same rule: a result counts only
if the listing's package is the catalogue's package. APKMirror was also tried, but it was not used,
because a search with no match returns unrelated popular apps instead of an empty result.

- **12 verified.** 11 now use their sampled icon colour; NoTubeTV keeps the palette because its
  icon is a multicolour gradient. Four got redrawn marks: CinemaHD (ticket), HDO Box (H with a play),
  NetMirror (ribbon N) and Perfect Player (P with a play in its bowl). RB Live's tile now reads
  "RB", as its logo does.
- **6 rejected as placeholders.** Aparat Sport, Cafe Bazaar, Cricfy, FindLink, Rutube and Yacine TV
  return only Aptoide's stock Android icon.
- **2 more suspected mismatches** for the owner review above. They keep their tiles, and their
  mappings were not changed:

| Catalogue name | Package | What the listing is |
|---|---|---|
| FindLink | `com.findlink` | Flixoid |
| Polygon Player | `com.polygon.videoplayer` | NovaTV |

**Still not found anywhere (70):** AK47Sports, Air Attack 2, AllSaves Social, AniLab, AnikenTV, Anime One, Anten TV, Användarmanual, Award VPN, Chebut TV, Clip TV, Clone Hero, CoreELEC Helper, Cyberflix, Damonte, Dansk Filmskat, Elefin, Eternal TV, Eternal TV (Nath), FANE TV, FC TV, Falcon Cast, Filimo, Filmnet TV, Firedown, Five TV, Flix TV, Football 360, GenPlay, HDRezka, Jellyfin Enhanced, Jojoy, Kennytv, MGS TV, Mediaspelare, Mi Gallery, Miracast, Myiptv, NetMirror TV, Nxsha, OK TV, OTT Navigator, Offshore, OnePix, Onstream, Otf TV, Perfect TV, PlayLatin, PlayNet, Premiumize TV, RB Main, Rapid Streamz, Rezka, Screenscape, Shark TV, Sports Everywhere, Sportzx, Stream Fire, TV, TV Garden, TVLok, Televizo, Tflix, Ukiku, VPN Dot, Vibra, WeatherBug, Works with Alexa, Xtream Player, Youcine.
