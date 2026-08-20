# Publishing to GitHub

Everything below is copy-paste. The repo is already initialised with two commits and a clean working tree.

---

## 1. Create the repo

Either on github.com (**New repository** → name it `CoreBuildsIconPack` → **don't** initialise with README/licence/gitignore), or with the CLI:

```bash
gh repo create brevityA/CoreBuildsIconPack --public \
  --description "Transparent Android TV icon pack for Projectivy Launcher, built to the Core Builds brand guide. 40 icons, 78 mapped components."
```

## 2. Push

```bash
cd CoreBuildsIconPack
git remote add origin https://github.com/brevityA/CoreBuildsIconPack.git
git push -u origin main
```

CI runs immediately. On this first push it will **regenerate assets, verify no drift, run the validator, and build the APK** — download it from the run's **Artifacts** section.

> Signing is optional for ordinary pushes. Without a keystore the release APK builds **unsigned** and the artifact still uploads; it just can't be installed until signed. Set up signing (step 4) before cutting a public release.
>
> Tagged builds are stricter: the workflow refuses to publish an unsigned APK, because an unsigned APK cannot be installed on Android at all, and a release nobody can install is worse than no release. If a `v*` tag fails at **Verify the APK is signed before releasing**, `KEYSTORE_BASE64` is missing or failed to decode — fix step 4 and re-tag.

## 3. Repo settings

- **About** → description above; topics: `android-tv`, `icon-pack`, `projectivy`, `google-tv`, `kotlin`, `core-builds`
- **Settings → Actions → General → Workflow permissions** → *Read and write* (needed for the release job to attach the APK)
- Social preview image: upload `docs/banner.png`

## 4. Signing (before your first public release)

Generate a keystore — **keep it forever**, Android requires the same key for every future update:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias corebuilds
```

Add four repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` (macOS: `base64 -i release.jks`) |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `corebuilds` |
| `KEY_PASSWORD` | the key password |

`release.jks` is gitignored. **Back it up somewhere off this machine.** Losing it means you can never update the installed app — users must uninstall and reinstall.

## 5. Cut a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The tag triggers a build that publishes a GitHub Release with the APK attached and auto-generated notes.

## 6. After releasing

- Update `Latestrelease/version.json` if you wire up an in-app updater — it points at the floating `iconpack/iconpack-release.apk` release URL.
- Get a [Downloader](https://www.aftvnews.com/downloader/) code for the release URL so TV users can sideload without a keyboard. Add it to the README's install section.

---

## Local build

Needs **JDK 17+** and the Android SDK. Both variants have been built and verified: Gradle 8.7 / AGP 8.5.2 / JDK 21 / compileSdk 34, no AGP complaints. `aapt2 dump resources` confirms all 40 drawables and all three XML resources in each — debug 3,897 KB, release 2,964 KB.

```bash
./gradlew assembleDebug     # unsigned, installable immediately
./gradlew assembleRelease   # signed when the keystore env vars are set
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Asset work needs no JDK at all:

```bash
pip install -r tools/requirements.txt
python tools/build_icons.py && python tools/validate.py
```
