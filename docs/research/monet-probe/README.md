# Monet Launcher v1.0.84 probe

Reference output from decompiling Monet Launcher (`com.klevico.monet`,
versionCode 118, released 2026-09-10) with `androguard` in a throw-away GitHub
Actions job on 2026-09-12. The APK cannot be fetched from the agent sandbox,
so the job committed these files back; the workflow itself was removed once
the questions were answered. Conclusions live in `docs/MONET_LAUNCHER.md`.

| File | What |
|---|---|
| `monet-v1.0.84-manifest.txt` | `aapt`-style manifest dump: permissions, `<queries>`, exported components, intent filters. |
| `monet-v1.0.84-files.txt` | Top-level APK entries. |
| `monet-v1.0.84-classes.txt` | Un-obfuscated class names (everything under `com.klevico.monet.*`). |
| `monet-v1.0.84-dex-strings.txt` | Dex string pool hits for icon-pack, wallpaper, Projectivy and share terms. |
| `monet-v1.0.84-res-strings.txt` | Resource-table strings matching the same terms. |
| `monet-v1.0.84-strings-en.txt` | Full English `key = value` string table (settings labels, toasts). |

Bytecode excerpts (`WallpaperShareActivity.onCreate`, icon-pack discovery,
`iconPackVariant` handling) were read and summarised in the doc, then deleted
from the repo — they were 400 KB of R8-obfuscated smali with no value beyond
the summary.
