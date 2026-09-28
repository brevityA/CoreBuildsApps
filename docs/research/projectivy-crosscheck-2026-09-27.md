# Projectivy Icon Pack cross-check (2026-09-27)

Many catalogue mappings came from Projectivy Icon Pack 1.1.9
([SicMundus86/ProjectivyIconPack](https://github.com/SicMundus86/ProjectivyIconPack)).
Its appfilter is kept at `tools/reference/projectivy-1.1.9-appfilter.xml`, and its artwork was
read from the published 1.1.9 APK. The artwork was used **only as a reference**: nothing from it
ships in this pack. Every mark is still redrawn in Core monoline geometry.

## Method

- **Names.** 875 catalogue entries share a component with Projectivy. For each, the catalogue name
  was compared with the drawable name Projectivy gives that component, and the artwork was checked
  by eye. 115 names disagreed. About 40 are the same app spelled differently ("10 Play" and `ten`,
  "F1 TV" and `formula1`); those are unchanged. The rest were names rebuilt from a package or
  developer name, or an app's old name, and now carry the name the artwork shows.
- **Decoy packages.** Some sideloaded apps install under a package whose store listing shows a
  different, harmless app. Projectivy's artwork, made from the installed app, is the better evidence
  for these. BeeTV (`com.bweather.forecast`, listed as a weather app) and TeaTV
  (`com.oe.photocollage`, listed as a photo collage maker) had marks drawn from the decoy listings,
  and they are redrawn from the real apps. FilmPlus (`com.guideplus.co`, listed as All Weather) and
  Pikashow (`com.offshore.pikachu`) take their real names and colours.
- **Colours.** Entries still on a palette colour, among the renamed apps and the 66 that no store
  had, take the dominant colour of their Projectivy logo. Logos without one clear colour (at least
  8% of the logo's pixels) keep the palette.

## Renamed (84)

| Was | Now | Package | Projectivy drawable |
|---|---|---|---|
| 3Player | Virgin Media Play | `com.axonista.threeplayer` | `virgin_media_play` |
| ADB WiFi | Remote ADB | `com.dinhlap.adb` | `remote_adb` |
| AIMI TV | Local 10+ | `com.aimitv.wplg.news` | `local_10_plus` |
| AllSaves Social | VivaTV | `com.allsaversocial.gl` | `vivatv` |
| AMC | Watch Free UK | `com.amcnetworks.cbscatchup` | `watch_free_uk` |
| Anime One | AnimeTV | `com.dev.anime.one` | `anime_tv` |
| Aplicativo X | Supreme TV | `com.aplicativox.supremetv` | `supreme_tv` |
| App Generation | myTuner Radio | `com.appgeneration.itunerfree` | `my_tuner_radio` |
| Avoid | Void | `com.hritwik.avoid` | `void_client` |
| BP Box | İnat Box | `com.bp.box` | `inat_box` |
| Brkchen Music | Radon Tunes | `com.brkchen.music` | `radon_tunes` |
| Canal Digital | Allente | `com.canaldigital.go` | `allente` |
| CGTN America | CGTN Now | `com.cgtnamericanow` | `cgtn_now` |
| Chebut TV | Chebur TV | `com.chebut.tv` | `chebur_tv` |
| Cue New | CUE Broadcast | `com.cuenew2` | `cue_broadcasts` |
| D-Smart | puhutv | `com.dogusdigital.puhutv` | `puhu_tv` |
| Damonte | Stash | `com.github.damontecres.stashapp` | `stashappandroidtv` |
| DCS IPTV | NexTV | `com.dcsapp.iptv` | `nextv` |
| DevInterest | StreamShow | `com.devinterestdev.streamshow` | `streamshow` |
| Ds TV Mobile | DStv Stream | `com.dstvmobile.android` | `dstv_stream` |
| Epic Channel | EPIC ON | `com.epicchannel.epicon` | `epic_on` |
| FC TV | FCTV33 | `com.fctv77.tv` | `fctv33_tv` |
| Feeln | Hallmark+ | `com.feeln.androidapp` | `hallmark` |
| FindLink | Flixoid | `com.findlink` | `flixoid` |
| FITE | TrillerTV | `com.flipps.fitetv` | `trillertv` |
| Flix TV | Flik TV | `com.tvflix.ippflixtvbox` | `flix_tv` |
| Forecast | BeeTV | `com.bweather.forecast` | `beetv` |
| Fox Sports Go | FanDuel Sports | `com.foxsports.videogo` | `fanduel_sports` |
| Freevee | Amazon Luna | `com.amazon.spiderpork` | `amazon_luna` |
| Full Episodes | The CW | `com.cw.fullepisodes.android` | `cw` |
| Good Tools | Zeus Browser | `com.goodtoolapps.zeus` | `zeus_internet_browser` |
| GuidePlus | FilmPlus | `com.guideplus.co` | `filmplus` |
| Hippos | Unchained | `com.github.livingwithhippos.unchained` | `unchained` |
| Ignite TV | Rogers Xfinity Stream | `com.rogers.ignitetv` | `rogers_xfinity_stream` |
| InstantBits | Web Video Caster Receiver | `com.instantbits.cast.receiver` | `web_video_caster_receiver` |
| IPTV3u | Dimplay | `com.iptv3u` | `dimplay` |
| ITV Hub | ITVX | `air.ITVMobilePlayer` | `itvx` |
| Jawwy TV | stc tv | `net.intigral.jawwytv` | `jawwy_tv` |
| Jawwy TV | stc tv | `net.intigral.jawwytv` | `jawwy_tv` |
| Jellyfin Enhanced | Dune | `Dune.enhanced.tv` | `dune` |
| Jojoy | RedBox TV | `com.reddish.apples` | `redboxtv` |
| Kamal TV | One TV | `com.kamal.androidtv` | `one_tv` |
| Karaoke | KaraFun | `com.recisio.kfandroid` | `karafun` |
| Launch Sounds | BBC Sounds | `com.nvidia.bbciplayer.launchsounds` | `bbcsounds` |
| LazyCat | Lazy Media Deluxe | `com.lazycatsoftware.lmd` | `lazy_media` |
| Liverpool FC | All Red Video | `com.liverpoolfc.goapp` | `all_red_video` |
| Maz TV | Rover's Morning Glory | `com.maz.combo2254` | `rover_radio` |
| Maz TV 2 | RiseTV | `com.maz.combo3403` | `risetv` |
| Maz TV 3 | RSBN | `com.maz.combo3578` | `rsbn` |
| Maz TVOD | UStreme | `com.maz.tvod169` | `ustreme` |
| Media Browser | PhotoGuru | `com.cmpsoft.MediaBrowser` | `photoguru` |
| Media Hub | VidHub | `com.oumi.utility.media.hub` | `vidhub` |
| MGS TV | Magis TV | `com.android.mgstv` | `magis` |
| Mi TV Plus | Xiaomi TV+ | `com.mitv.tvhome.mitvplus` | `xiaomi_tv` |
| Movideo | Danet | `com.movideo.whitelabel` | `danet` |
| Myiptv | MYiptv 4K | `com.iptv.myiptv` | `my_iptv_4k` |
| Offshore | Pikashow | `com.offshore.pikachu` | `pikashow` |
| OnePix | 1Pix Media | `app1.onepixmedia` | `a_1_pix_media` |
| OnTV | TV+ | `com.andevapps.ontv` | `tv_plus` |
| Overseas App Store | Emotn Store | `com.overseas.store.appstore` | `emotn_store` |
| Photo Collage | TeaTV | `com.oe.photocollage` | `teatv` |
| PlayNet | LiveNetTV Pro | `com.playnet.androidtv.pro` | `livenettv_pro` |
| Plus Messenger | Tevegram | `cassian.telegram.ooa.pro` | `tevegram_telegram_for_tv` |
| Polygon Player | NovaTV | `com.polygon.videoplayer` | `novatv` |
| RAM TV | RAM Cleaner | `com.ram.tv` | `ramcachecleaner` |
| RB Live | RBTV+ | `com.rblive.app` | `rbtv` |
| RB Main | Rubika TV | `app.rbmain.tv` | `rubika_tv` |
| Remote Capture | PCAPdroid | `com.emanuelef.remote_capture` | `pcapdroid` |
| RlaXX TV | Whale TV | `com.rlaxxtv.tvapp.atv` | `whale_tv` |
| RLC+ | Super League+ | `com.rlcplus.app` | `super_league_plus` |
| Simple Player | Drama Player | `com.drama.simpleplayer` | `drama_player` |
| SIPTV | Smart IPTV | `app.siptv.android` | `smart_iptv` |
| Speaker Boost | Volume Booster | `com.goodev.volume.booster` | `volume_booster_goodev` |
| Sportscaster | CBS Sports | `com.handmark.sportscaster.androidtv` | `cbs_sports` |
| TFC | iWant | `com.absi.tfctv` | `iwant` |
| TNA Wrestling | TNA+ | `com.fight.tna` | `tna_plus` |
| TV Everywhere | Kable One | `com.kableone.tveverywhere` | `kable_one` |
| TV Unplugged | YouTube TV | `com.google.android.youtube.tvunplugged` | `youtube_tv` |
| Ucom TV | Uplay | `am.ucom.smarttvapp` | `uplay_armenia` |
| Ve Plus | Venevision Play | `com.cisneros.venevision.app` | `venevisionplay` |
| VPN Dot | Monitor Dot | `com.omnisoft.vpndot` | `monitor_dot` |
| Xtream Player | 9Xtream | `com.divergentftb.xtreamplayeranddownloader` | `a_9xtream` |
| Eternal TV | Eternal TV Immortal | `com.eternaltv.eternaltviptvbox` | `eternal_tv_immortal` |
| Eternal TV (Nath) | Eternal TV Divine | `com.nathnetwork.eternaltv` | `eternal_tv_divine` |

## Left unchanged on purpose

- **WeatherBug** (`com.weatherbug.firetv`): Projectivy shows the Streamflix logo, but it is unclear
  whether that is a disguised Streamflix build or a reused icon. It needs a device check.
- **OTTplay** (`com.ottplay.ottplay`): Projectivy shows Televizo; the Play listing is OTTplay.
- **Sports Everywhere** (`com.Arena4Viewer.Sportseverywhere`): Projectivy shows Arena4Viewer, which
  already has its own entry.
- **Alias spellings** such as 10 Play, 9Now, A&E, F1 TV, SRF Play, RSI Play, Vimu Player.

Recoloured from the artwork: 53 entries. 14 renamed entries also moved category
(for example, Amazon Luna to Gaming and KaraFun to Music).
