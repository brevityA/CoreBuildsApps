# Dev containers

Two configs. Pick one when you create a Codespace (or reopen a local
Dev Container). Default is **Python**.

| Config | Features | For |
|---|---|---|
| [devcontainer.json](devcontainer.json) **Python** | python 3.12, node 22, github-cli, local `cairo` | catalog, generators, validators, `cd ticker && npm test` |
| [android/devcontainer.json](android/devcontainer.json) **Android** | the above + java 17 (Temurin) + local `android-sdk` | `./gradlew :app:assembleDebug` and the other Gradle roots |

There is **no emulator**. Codespaces does not expose `/dev/kvm`. The TV
install/launch check stays in `.github/workflows/device-check.yml`. Do not
put the release keystore in a codespace.

## Official Features

Pinned by major version from [devcontainers/features](https://github.com/devcontainers/features):

- `ghcr.io/devcontainers/features/common-utils:2`
- `ghcr.io/devcontainers/features/python:1` (`3.12`, `installTools: false`)
- `ghcr.io/devcontainers/features/node:1` (`22`, Core Line)
- `ghcr.io/devcontainers/features/github-cli:1`
- `ghcr.io/devcontainers/features/java:1` (`17`, `jdkDistro: tem`, no extra Gradle — the repo wrappers own that)

Python's default `toolsToInstall` (pylint, flake8, …) is off. This repo's
linters are the Python validators, not that stack.

## Local Features

Official collections have no Cairo and no Android SDK.

| Feature | What it installs | What it does not |
|---|---|---|
| [features/cairo](features/cairo) | `libcairo2` + headers + pkg-config | Python wheels (`tools/requirements.txt` in postCreate) |
| [features/android-sdk](features/android-sdk) | cmdline-tools, platform-tools, platforms 34 and 35, build-tools 34.0.0 / 35.0.0 | emulator, system images, NDK, signing keys |

Android SDK packages match the suite: Classic / Pop / Neon / Line / Shift /
Motion compile against 34; Doctor is 35.

## After create

Python:

```bash
python tools/validate.py
python tools/check_ui_resources.py
python tests/test_wallpaper_export.py
cd ticker && npm test
```

Android, additionally:

```bash
./gradlew :app:assembleDebug :pop:assembleDebug
```

PNG bytes still move with libcairo. Commit XML/SVG/MD from here; let CI
(or a machine whose Cairo matches `build.yml`) be the PNG oracle, same
contract as the drift gate.

## Prebuilds

Enable a prebuild on `main` for the Python config in the repo's Codespaces
settings so the Features layer is not rebuilt on every open. The Android
config is heavier (SDK zip); prebuild that too if people assemble locally.
