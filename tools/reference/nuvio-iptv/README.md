# Nuvio + IPTV — launcher components from the app's own manifests

Read **14 November 2026** from the fork's source, without building, installing
or running it: [homelessbrian/NuvioTV](https://github.com/homelessbrian/NuvioTV)
at `e4d3d6e`. `receipt.json` records each file's size and SHA-256, and every
launcher entry found in it.

| Field | Observed value |
|---|---|
| Namespace | `com.nuvio.tv` (`app/build.gradle.kts`) |
| Installed package | `com.nuvio.livetv`: every release that is not the Play flavor; `com.nuvio.livetv.test` only with `-PlivetvTestBuild=true` |
| "+ IPTV" icons | six activities in `app/src/full/AndroidManifest.xml`: `com.nuvio.tv.livetv.branding.IptvIcon{Default,ArcticBlue,Emerald,RoseGold,Copper,Graphite}` |
| Upstream icons | six activities in `app/src/main/AndroidManifest.xml`: `.launcher.AppIcon{Default,ArcticBlue,Emerald,RoseGold,Copper,Graphite}` |
| Enabled at install | `IptvIconDefault`. The full flavor turns `AppIconDefault` off (`tools:replace="android:enabled"`), and the other ten start disabled. The app switches between them under Settings → Appearance → App icon. |
| Device-reported | `IptvIconDefault` ([#358](https://github.com/brevityA/CoreBuildsApps/issues/358)), `IptvIconGraphite` ([#484](https://github.com/brevityA/CoreBuildsApps/issues/484)) |

All twelve components exist in the installed app under `com.nuvio.livetv`, and
all twelve map to Nuvio TV's icon. This is the evidence the original Nuvio TV
mapping rests on (the official `com.nuvio.tv` manifest, read 2026-09-14), so the
ten that no TV has reported yet carry no `unverified` flag. Two device reports
using the same naming back the reading.

What this does **not** claim: that any of the ten has been seen on a TV, or that
a later version of the fork keeps these names. If a release renames them, the
scanner's Missing icons row will report the new component.

The fork's files are not copied here; `receipt.json` holds their hashes and the
facts read from them.
