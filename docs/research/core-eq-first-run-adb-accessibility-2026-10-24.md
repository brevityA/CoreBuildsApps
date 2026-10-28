# Core EQ first-run and ADB accessibility research (2026-10-24)

## Summary

The largest non-technical-user win is to **separate getting useful room EQ from optional ADB setup**. ADB is not a prerequisite for measuring a room or selecting a mode manually. It is only an optional route to app-identity discovery and some TV permission operations. Android marks `DUMP` as a protected permission; Core EQ cannot grant it itself and some retail TV builds refuse it. No amount of UI polish can safely bypass an OS policy refusal.

Research sources:

- Android Developers, [Android Debug Bridge (adb)](https://developer.android.com/tools/adb): install/use ADB, list and authorize devices, connect by USB or Wi-Fi; wireless pairing on supported Android versions requires the displayed IP, pairing port and code, followed by the separate connection endpoint.
- Android Developers, [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools): official platform-tools downloads for users who need the command-line ADB client without Android Studio.
- Google Play listing, [ADB TV: App Manager](https://play.google.com/store/apps/details?id=com.cybercat.adbappcontrol.tv): an Android TV remote-friendly application manager that advertises an ADB console, but its listing itself says initial ADB setup is required. It is therefore not a reliable zero-setup answer to granting Core EQ's permissions.
- Official ADB AppControl site, [ADB AppControl](https://adbappcontrol.com/en/): a third-party desktop graphical app manager which advertises permission management. It is not an Android/Google-supported Core EQ path; its compatibility, source trust and exact command behavior must be independently assessed before recommending it.

## What makes ADB difficult for a TV owner

1. **The setup differs by firmware.** Menus are usually under About / Developer options, but manufacturers rename them. Developer options are typically unlocked by selecting the OS build entry repeatedly. Classic network debugging and Android's Wireless debugging pair flow are different connection protocols.
2. **There are multiple ports.** On classic Network debugging, use the endpoint the TV presents (often port 5555). On modern Wireless debugging, `adb pair` uses a temporary pairing port and code; after pairing, `adb connect` uses the separate connect port. Treating them as interchangeable is a common failure.
3. **The TV must authorize the client.** `adb devices -l` may show `unauthorized`; the user must accept the dialog on the TV. On a shared/public network, allowing a persistent client or leaving debugging enabled is unsafe.
4. **The router can block discovery or connection.** Guest Wi-Fi, client isolation, VLANs and some mesh/router settings can prevent same-LAN ADB or mDNS. Pairing by explicit IP/port may help discovery failures but cannot bypass network isolation.
5. **A successful connection does not guarantee permission support.** The command can still be rejected by a retail build. In particular, `android.permission.DUMP` is not a normal runtime permission. Core EQ must explain the refusal and leave its main functions usable.
6. **A terminal adds friction.** Windows PowerShell may need `./`-style executable qualification (`.\\adb.exe`) when the platform-tools directory is not on PATH; macOS/Linux may need `./adb`. “Open a terminal in the extracted folder” avoids teaching PATH configuration, but is still not point-and-click.

## Safer, lower-friction alternatives

| Need | Best non-technical path | Limits |
|---|---|---|
| Measure and apply room correction | Use the built-in remote-microphone flow; grant microphone access when prompted; select the actual TV/soundbar output and follow the quiet-room instructions. | No ADB required. Audio effects can still be bypassed by encoded passthrough or unsupported routes. |
| Choose movie / everyday / gaming modes | Select the mode manually from Home. | Best fallback when DUMP is refused. No automatic app switching. |
| Show that correction is on | Use the Home badge and the text fallback. Open Android's overlay setting only if the TV exposes it. | Overlay permission is optional; some TV firmware has no settings page or refuses it. |
| Copy ADB commands | Use the app's per-command copy button and paste into official Google Platform-Tools on a computer. | Still needs a computer and a TV-side authorization. Commands remain user-triggered and reviewed. |
| Use a GUI ADB manager | Advanced users may independently evaluate reputable desktop/TV ADB tools. | Third-party software has its own trust, version and support risks; it is not endorsed or needed for Core EQ's main path. Some TV-side apps require ADB to be set up first. |
| Get help | Copy the privacy-filtered setup report, review it, then share it with support. | Never include pairing codes, IPs, serial numbers, raw microphone data or an unreviewed logcat dump. |

There is no verified, official, universal one-tap/no-computer method to grant a protected permission on every Android TV. Do not promise one. The right accessibility improvement is a graceful manual path plus accurate guidance, not hiding a failing ADB command behind a button.

## Implemented in the next-version working tree

- A first-install-only welcome screen on Leanback TV, not triggered by app upgrades or phone companion launches.
- A one-button jump to room measurement and an alternate Setup & ADB help route; “Not now” is available.
- Clear, prominent warning that the first GREEN measurement may enable correction automatically.
- Explicit statement that ADB is optional for the main measurement/manual-mode path.
- A copyable setup screen linked to Google's official Platform-Tools page, with Windows/macOS/Linux terminal hints, modern pair/connect port distinction, authorization guidance, common `adb not found` / `unauthorized` / `offline` / refused-connection remedies, and a safe manual fallback when DUMP is rejected.
- No shell execution, root, hidden settings writes, third-party download or automatic debugging enablement.

## Validation still needed

- Fresh install on Android TV / Google TV: welcome screen appears once, buttons have sensible D-pad focus, mic permission follows the chosen measurement action.
- Update install from 1.6.0: no welcome interruption; existing user settings and profiles remain untouched.
- Test rendering at 720p and 1080p, overscan-safe bounds, large text and long localized strings.
- Exercise setup actions with no browser/clipboard, with classic ADB, with modern Wireless debugging, and with a retail TV that refuses DUMP.
- Verify that dismissing onboarding never auto-enables correction or changes an existing profile.
