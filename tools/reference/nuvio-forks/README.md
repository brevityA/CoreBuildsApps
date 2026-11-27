# Nuvio ReSeq and Nuvio Legacy — launcher components from each fork's own manifest

Read **27 November 2026** from each fork's source at a pinned commit, without
building, installing or running either. `receipt.json` records each file's size
and SHA-256 and every launcher entry found in it.

| | Nuvio ReSeq ([#509](https://github.com/brevityA/CoreBuildsApps/issues/509)) | Nuvio Legacy ([#498](https://github.com/brevityA/CoreBuildsApps/issues/498)) |
|---|---|---|
| Source | [abbaskip/Nuvio-ReSeq](https://github.com/abbaskip/Nuvio-ReSeq) | [iqui27/nuvio-native-legacy](https://github.com/iqui27/nuvio-native-legacy) |
| What it says it is | "an independent Android TV fork of Nuvio" | "an unofficial fork" of Nuvio, not affiliated with NuvioMedia |
| Installed package | `io.github.abbaskip.nuvioreseq` | `space.nuvio.nativelegacy` |
| Launcher entries | six activities, `com.nuvio.tv.launcher.AppIcon{Default,ArcticBlue,Emerald,RoseGold,Copper,Graphite}`, as upstream Nuvio | ten activity-aliases, `.Icone_{original,fenix,nverde,tvlaranja,npixel,tricolor,arco,tvviva,cluberetro,arcaden}` |
| Enabled at install | `AppIconDefault` | `Icone_original` |
| Device-reported | `AppIconDefault` (Smart TV, Android 12) | `Icone_original` (Google TV Streamer, Android 14) |

Each app chooses one launcher entry at a time, so every entry maps to one
icon whichever the user picked. Legacy's ten map to Nuvio TV's icon, as Nuvio +
IPTV's twelve do (`tools/reference/nuvio-iptv/`). ReSeq's six map to its own
icon, Nuvio ReSeq (2.7.1), drawn from its logo (`tools/reference/nuvio-reseq/`),
since ReSeq has a mark of its own. `maps_to` in `receipt.json` names the icon.
The fourteen no TV has reported rest on the fork's own manifest, the evidence
the original Nuvio TV set rests on, so they carry no `unverified` flag.

What this does **not** claim: that any of the fourteen has been seen on a TV,
or that a later version keeps these names. A renamed launcher shows up in the
Missing icons scan with its new component.

The forks' files are not copied here; `receipt.json` holds their hashes and the
facts read from them.
