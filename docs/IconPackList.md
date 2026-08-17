# Supported applications

`40` icons · `78` mapped components · `20` unconfirmed · pack v1.0.0

Every app below auto-assigns in Projectivy. If one doesn't, the app ships a different launcher activity on your device — open an issue with the component name and it gets added.

Components marked ⚠ are **best-known, not device-confirmed** (20 of 78, across 12 apps). They were written from package documentation rather than read off a device with `adb shell cmd package resolve-activity`. A wrong component fails silently — the icon simply never assigns — so if one of these doesn't work, the component name from your device is the single most useful thing you can put in an issue.

| App | Drawable | Accent | Components |
| --- | --- | --- | --- |
| 10 Play | `tenplay` | `#E9142B` | `au.com.tenplay/.MainActivity` ⚠<br>`com.network10.tenplay/.MainActivity` ⚠ |
| 7plus | `sevenplus` | `#0072CE` | `au.com.seven.inferno/.MainActivity` ⚠ |
| 9Now | `ninenow` | `#00A0DC` | `com.mi9.gomi/.MainActivity` ⚠<br>`au.com.nine.now.tv/.MainActivity` ⚠ |
| ABC iview | `abciview` | `#00B6E4` | `au.net.abc.iview/.MainActivity` ⚠<br>`au.net.abc.iview.tv/.MainActivity` ⚠ |
| AllDebrid | `alldebrid` | `#FF7A00` | `com.alldebrid.app/.MainActivity` ⚠ |
| Apple TV | `appletv` | `#E6EDF3` | `com.apple.atve.androidtv.appletv/.MainActivity`<br>`com.apple.atve.amazon.appletv/.MainActivity` |
| Binge | `binge` | `#E6007E` | `au.com.streamotion.ares/.MainActivity` ⚠<br>`au.com.binge.tv/.MainActivity` ⚠ |
| Core Builds | `corebuilds` | `#00D4FF` | `tv.corebuilds.iconpack/.MainActivity` |
| Disney+ | `disneyplus` | `#1F80E0` | `com.disney.disneyplus/.MainActivity`<br>`com.disney.disneyplus.tv/.MainActivity` |
| Downloader | `downloader` | `#F0913A` | `com.esaba.downloader/.MainActivity`<br>`com.esaba.downloader/com.esaba.downloader.MainActivity` |
| Emby | `emby` | `#52B54B` | `tv.emby.embyatv/.startup.StartupActivity`<br>`com.mb.android/.MainActivity` |
| Files | `files` | `#4FACFE` | `com.google.android.documentsui/.files.FilesActivity`<br>`nextapp.fx/.FX`<br>`com.mixplorer/.activities.BrowseActivity` |
| Jellyfin | `jellyfin` | `#AA5CC3` | `org.jellyfin.androidtv/.ui.startup.StartupActivity`<br>`org.jellyfin.androidtv/org.jellyfin.androidtv.ui.startup.StartupActivity`<br>`org.jellyfin.mobile/.MainActivity` |
| Just Player | `justplayer` | `#7EEEFF` | `com.brouken.player/.MainActivity` |
| Kayo Sports | `kayo` | `#00E676` | `au.com.kayosports.tv/.MainActivity` ⚠<br>`au.com.streamotion.hyperion/.MainActivity` ⚠ |
| Kodi | `kodi` | `#3EBBF0` | `org.xbmc.kodi/.Splash`<br>`org.xbmc.kodi/org.xbmc.kodi.Splash`<br>`org.xbmc.kodi_touch/.Splash` |
| Kore | `kore` | `#3EBBF0` | `org.xbmc.kore/.ui.sections.hosts.AddHostActivity`<br>`org.xbmc.kore/.ui.sections.remote.RemoteActivity` |
| Max | `max` | `#0046FF` | `com.wbd.stream/.MainActivity`<br>`com.hbo.hbonow/.MainActivity` |
| MX Player | `mxplayer` | `#3AB4F2` | `com.mxtech.videoplayer.ad/.ActivityMediaList`<br>`com.mxtech.videoplayer.pro/.ActivityMediaList`<br>`com.mxtech.videoplayer.tv/.ActivityMediaList` |
| Netflix | `netflix` | `#E50914` | `com.netflix.ninja/.MainActivity`<br>`com.netflix.mediaclient/.ui.launch.UIWebViewActivity` |
| Nuvio TV | `nuvio` | `#F472B6` | `com.nuvio.tv/.MainActivity`<br>`com.nuvio.tv/com.nuvio.tv.MainActivity`<br>`com.nuviodebug.com/com.nuvio.tv.MainActivity` |
| Plex | `plex` | `#E5A00D` | `com.plexapp.android/com.plexapp.plex.activities.SplashActivity`<br>`com.plexapp.mediaserver.smb/.MainActivity` |
| Premiumize | `premiumize` | `#4FACFE` | `me.premiumize.app/.MainActivity` ⚠ |
| Prime Video | `primevideo` | `#00A8E1` | `com.amazon.amazonvideo.livingroom/.ui.LauncherActivity`<br>`com.amazon.avod.thirdpartyclient/.LauncherActivity` |
| Projectivy Launcher | `projectivy` | `#00D4FF` | `com.spocky.projengmenu/.ui.tvinput.TvInputActivity`<br>`com.spocky.projengmenu/com.spocky.projengmenu.ui.MainActivity` |
| Real-Debrid | `realdebrid` | `#7BC144` | `debrid.real.app/.MainActivity`<br>`com.realdebrid.app/.MainActivity` |
| SBS On Demand | `sbs` | `#F0A500` | `au.com.sbs.ondemand.tv/.MainActivity` ⚠<br>`au.com.sbs.ondemand/.MainActivity` ⚠ |
| Settings | `settings` | `#8B949E` | `com.android.tv.settings/.MainSettings`<br>`com.android.settings/.Settings` |
| Smart Tube | `smarttube` | `#FF0033` | `com.teamsmart.videomanager.tv/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity` |
| SmartTube Next | `smarttubenext` | `#FF3B30` | `com.liskovsoft.smarttubetv.beta/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity` |
| Spotify | `spotify` | `#1DB954` | `com.spotify.tv.android/.SpotifyTVActivity`<br>`com.spotify.music/.MainActivity` |
| Stan | `stan` | `#0091EA` | `au.com.stan.and/.MainActivity` ⚠<br>`au.com.stan.and.tv/.MainActivity` ⚠ |
| Stremio | `stremio` | `#7B5BF5` | `com.stremio.one/com.stremio.one.MainActivity`<br>`com.stremio.one/.MainActivity`<br>`com.stremio.one/com.stremio.tv.MainActivity` |
| Syncler | `syncler` | `#00BFA5` | `com.wolfpack.syncler/.ui.SplashActivity`<br>`com.wolfpack.syncler/com.wolfpack.syncler.MainActivity` |
| TorBox | `torbox` | `#00E5FF` | `com.torbox.app/.MainActivity` ⚠<br>`app.torbox.android/.MainActivity` ⚠ |
| Trakt | `trakt` | `#ED1C24` | `tv.trakt.trakt/.MainActivity` |
| Twitch | `twitch` | `#9146FF` | `tv.twitch.android.app/.core.LandingActivity`<br>`tv.twitch.android.viewer/.MainActivity` |
| VLC | `vlc` | `#FF8800` | `org.videolan.vlc/.StartActivity`<br>`org.videolan.vlc/org.videolan.vlc.gui.MainActivity`<br>`org.videolan.vlc/.gui.tv.MainTvActivity` |
| Weyd | `weyd` | `#8A4890` | `com.weyd.app/.MainActivity` ⚠ |
| YouTube | `youtube` | `#FF0033` | `com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity`<br>`com.google.android.youtube/.HomeActivity` |
