# Core Line — the VPN dot, over every app, on Google TV

Researched 2026-09-29, and the feature built from it in the same pass (see §8).

The request was to research "how a VPN dot displays over all applications on
Android TV" and to build it. That sentence covers three different things, so
this document answers all three, then records what Core Line actually shipped
and what remains unproven.

1. **How the platform itself shows VPN state on a TV** — and why that is the
   reason a dot is needed at all (§3).
2. **How a third-party app draws a dot over every other app** — the mechanism,
   the permission, the devices where it is impossible, and the ones where it
   needs ADB (§2, §6, §7).
3. **Who already ships one, and what they get right or wrong** (§4), plus the
   detection API limits that decide what a truthful dot can even claim (§5).

Two limits on everything below, stated up front because they bound what this
document can prove:

- **No device, no emulator, and no Android SDK in this sandbox.** Nothing here
  has been seen on a panel, and the APK was not compiled here (no JDK). Every
  on-device behaviour is marked `[UNVERIFIED]` and left on the §9 checklist. A
  named unverified step is better than a confident guess.
- **The sandbox shell has no outbound network.** Every live endpoint below was
  reached through search tooling only, on the dates cited, and community
  evidence (forums, vendor help centres) is labelled as such rather than
  reported as platform documentation.

---

## 1. The short answer

Android TV has no status bar over third-party apps, so a VPN leaves no mark on
screen: the viewer who set one up in Settings → Network & Internet → VPN has no
way to tell, mid-match, that it is still up. A third-party app can fix that by
holding `SYSTEM_ALERT_WINDOW` and adding one `TYPE_APPLICATION_OVERLAY` window
that draws above every other app — which is exactly what IPVanish, VPN Safety
Dot, VPN Monitor Dot and VPN Privacy Dot all do (§4).

What none of them can do is *name* the VPN app or prove that traffic is
flowing; both are outside what public API allows (§5). The honest dot says
three things and refuses to say a fourth, and that is what shipped here.

---

## 2. Does Google TV let an app draw over other apps?

Yes, with one special-access permission, and the answer differs by device era —
which is where the repo's own earlier research went wrong.

The mechanism is unchanged since Android 8: declare
`android.permission.SYSTEM_ALERT_WINDOW`, be granted it by the user (it is a
*special app access* permission, never a runtime dialog), and add a window with
`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`. Anything targeting API
23+ cannot hold the permission without the user's explicit toggle
([askvg][askvg], [Grokipedia][grokipedia] — the latter mirrors the platform's
`signature|setup|appop|installer|pre23|development` protection level).

The part that is device-dependent is the *toggle*:

| Platform | Can the user grant it from the TV UI? | Evidence |
|---|---|---|
| Google TV (Android 12/14 era) | **Yes** — Settings → Privacy → Special app access → Display over other apps | Community report on Google TV 14, including the kids-profile failure mode ([r/AndroidTV][gtvaccess], 2025-10) |
| NVIDIA Shield (Android 11 era) | **Yes** — Apps → Special app access → Display over other apps | [r/TiviMate][shieldpath], 2023-10 |
| Stock Android TV 9/10 builds | **Often no screen at all** — the app can only be granted over ADB | [Beebom][beebom] (2020-12) documents Google removing the user-facing page on Android TV, and the `appops` workaround |
| Fire TV / Fire OS | **No** — ADB was the only route, and Amazon has since removed that route on current builds | [IPVanish support][ipvanish], which says the indicator "can no longer be turned on for Fire devices that don't already have it enabled" |

### The correction to our own record

`ticker/VERIFICATION.md` §8.4 (2026-09-04) states that "Android TV has no
SYSTEM_ALERT_WINDOW for a sideloaded app" and that a TV ticker over other apps
is "not supported". That was true of the Android TV 9/10 era the [Beebom][beebom]
write-up describes and is **not** true of Google TV as it ships now: the special
access screen exists on Google TV and Shield, and an app that holds the
permission draws over every other app on both. The web-side hint in
`ticker/public/js/ui/settings.js` had already been softened to match; the
verification log had not, and now points at this document. Fire TV remains
genuinely blocked at the OS level, which is why the app still refuses there.

The practical consequence for Core Line: the overlay surfaces are offered on
Google TV with a permission prompt, and the ADB commands in §9 exist for the
stock builds where the screen is missing.

---

## 3. How the OS itself signals VPN on a TV

This is the gap the dot fills, so it is worth being precise about what the
platform does show.

- **No status bar over apps.** Android TV's layout has no persistent system bar
  in the phone sense; the icon row that does exist (next to the clock) and the
  notifications card live on the *launcher*, not over whatever is playing
  ([r/AndroidTV][tvstatusbar], 2021-05). The VPN "key" icon everyone recognises
  from a phone is a phone status-bar artefact and does not appear on the TV
  build at all ([technerdiness][keyicon] describes it as the generic Android
  status-bar symbol).
- **Settings is the only readout.** Google TV exposes VPN configuration under
  Settings → Network & Internet → VPN; the system knows the tunnel is up and
  will tell you *there*, which requires leaving what you are watching.
- **Vendor VPN apps only report inside their own UI.** ExpressVPN and Surfshark
  both show connected state on their own screens, which is precisely the screen
  a viewer is not looking at mid-match ([ExpressVPN][expressvpn],
  [Top10VPN][top10vpn]).

So the platform answer to "is my VPN up?" is: *go and look somewhere else*. On
a phone that is a status-bar glance; on a TV it is a menu excursion that
interrupts playback. Every dot in §4 exists because of that asymmetry.

---

## 4. Prior art: four dots, and the one complaint they share

| Product | Shape of the indicator | Notable behaviour |
|---|---|---|
| **IPVanish VPN Status Indicator** ([support][ipvanish]) | Coloured dot, bottom-right, over every app including while streaming | Choose "hide indicator when connected" or "always show"; adjustable transparency with a live preview; on Fire TV the dot can only render in greys, "a limitation imposed by Amazon" |
| **VPN Safety Dot** ([help][safetydot], [websafetytips][wstdot]) | Green blink = tunnel up, red = not; top-right; works "whether you're watching Netflix or doing something else" | Autostart, blink rate, transparency; now paid |
| **VPN Monitor Dot / VPN Privacy Dot** ([max-soft][maxsoft], [HoBSoft][hobsoft]) | Same idea, green/red, adjustable brightness *per state* and separate blink rates | Advertises auto-run on boot, so the dot survives a power cycle; free |
| **TiviMate reminders** ([r/TiviMate][tivimate]) | Not a dot — but the same permission, with the same error message | Useful mainly because its community threads contain the most reliable, repeated field reports of how the permission is granted and when it is greyed out |

Design lessons worth taking, all of them repeated across sources:

- **Position and discretion matter.** Bottom-right (IPVanish) or top-right
  (the others); every implementation offers transparency, because a dot that
  is too loud over a film gets switched off, and a dot that is switched off
  protects nobody.
- **Blinking is reserved for failure.** A still dot means healthy; movement
  means look at me.
- **Autostart is part of the feature.** A dot that disappears on reboot is
  discovered at the worst moment. VPN Monitor Dot advertises boot restore
  explicitly.
- **The known failure is a false green.** In the TROYPOINT forum thread
  *"VPN Dots not working correctly"* ([forum][troy]), users report dots staying
  green with the VPN disconnected because a **kill switch** leaves the tunnel
  interface up while blocking traffic — and one reply works through exactly
  that reasoning. This is a detection limit, not a bug in those apps: see §5.
  It is also the single most useful thing to design around, and the reason the
  Core Line dot does not claim more than the transport plane can prove.

Not covered by any of them: naming the VPN provider on screen, or proving
throughput. §5 says why.

---

## 5. Detection: what the API will and will not tell you

**The supported read is `NetworkCapabilities.TRANSPORT_VPN`**, added in API 21.
Two sensible ways to sample it — `ConnectivityManager.getAllNetworks()` for
"is any network a VPN", and `getActiveNetwork()` +
`getNetworkCapabilities()` for "is the VPN my route" — appear in every
respectable answer and sample on the subject ([SO 28386553][so1],
[SO 29419175][so2], [Tarka Labs][tarka]).

That distinction is the whole state model, and it matters more on TV than on a
phone because **per-app VPN (split tunnelling) is a first-class feature on TV
VPN clients** — ExpressVPN documents selecting which apps go through the tunnel
([ExpressVPN][expressvpn]). So:

- *some* VPN network exists → "a VPN is connected" (what the VPN app's own
  screen shows);
- *the active network* is the VPN → "Core Line's traffic is inside it".

Those can disagree, and a dot that collapses them is wrong in exactly the case
someone configured deliberately.

### Three things public API will not give you

1. **Which app owns the VPN.** `NetworkCapabilities.getOwnerUid()` is only
   populated for the network's own owner, and for a VPN network the caller must
   be that owner ([API reference][nc]). Another app's tunnel cannot be
   attributed. Enumerating installed VPN clients instead would need
   `QUERY_ALL_PACKAGES`, which is a Play-restricted permission and, for a
   status dot, indefensible.
2. **That traffic is flowing.** `NET_CAPABILITY_VALIDATED` looks like the
   answer and is not: under local VPNs (NetGuard, AdGuard) it has been reported
   `true` with the device in **airplane mode**, and with no upstream connection
   at all ([Farbklex gist][gist], [SO 66350798][so3]). Treating it as a fault
   would paint a permanent amber dot on working setups; treating it as proof
   would repeat the false-green complaint in §4. Neither is acceptable, so it
   is **reported as text and never used as a colour**.
3. **That a kill switch is holding traffic.** Related to (2) and just as
   unprovable: a tunnel interface up with a blocked path looks identical to a
   healthy tunnel. The honest options are to say nothing, or to test something
   end-to-end — for Core Line that would be a fetch through its own data path,
   which is a real option for a future pass and deliberately not claimed now.

### Reading it live without burning a TV stick

Two registrations, deliberately: a VPN-transport request (`TRANSPORT_VPN`,
minus `NET_CAPABILITY_NOT_VPN`) fires only when a tunnel appears or vanishes
and can never see the *underlying* network change beneath it, so a
default-network callback covers that. Both recompute the whole snapshot from
`ConnectivityManager` rather than trusting callback arguments, because
callbacks say *when*, not *what*, and can arrive out of order. A slow timer
(15 s) sits under both as a floor: vendor ROMs dropping a callback is a
documented-enough failure that a frozen dot is a design risk, not a
theoretical one. Details in §8.

---

## 6. Window mechanics on a television

- **Type and flags.** `TYPE_APPLICATION_OVERLAY` (API 26+; `TYPE_PHONE` for
  older) with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_NOT_TOUCHABLE`
  and `FLAG_LAYOUT_IN_SCREEN`. Non-focusable matters more on TV than anywhere
  else: the D-pad must walk straight past the dot.
- **Z-order ceiling.** An application overlay sits *below* system windows —
  status bar, IME, system dialogs ([iTechGuides][itech] states the phone
  version of this). On TV the dot will therefore not cover the quick-settings
  panel or a system dialog, and no amount of flag work changes that.
- **DRM is not the obstacle people assume.** Protected playback blocks *screen
  capture*, not compositing: every peer indicator is advertised as visible while
  streaming ([IPVanish][ipvanish], [VPN Safety Dot][safetydot]), and the
  Android TV screen-recording guides confirm the failure is in MediaProjection,
  not in overlays ([Beebom][beebom]). The dot will draw over Netflix; it cannot
  be recorded, and that is a different feature.
- **Safe area.** Panels crop 2.5–5%, which Core Line already models with its
  overscan calibration. A dot pinned 8dp from the physical corner disappears
  into the crop on those sets, so the dot's margin is derived from the viewer's
  own calibration (§8).
- **Legibility on any video.** Two rings, not one: a dark keyline under the
  colour is what keeps a green dot visible against a bright sports app and a
  black one against end credits. Halo, keyline and core are drawn in one
  `onDraw`.
- **Fire TV greyscale.** IPVanish reports that Amazon forces shade-of-grey
  indicators on Fire devices ([IPVanish][ipvanish]). Moot for Core Line, which
  refuses to draw on Fire TV, but recorded so no future port promises colour
  there.

---

## 7. Lifecycle, background rules and policy

- **One foreground service, one notification.** An overlay window owned by a
  background service is killed; the service therefore runs in the foreground
  with `foregroundServiceType="specialUse"`, which Core Line already declared
  for the ticker. The dot shares that service rather than adding a second
  notification, and the notification text now names which surfaces are up.
- **Android 14:** every foreground service needs a declared type, and
  `specialUse` needs no runtime prerequisite ([service types][fgstypes]).
- **Android 15 narrows two things** ([behavior changes][a15],
  [FGS changes][fgschange]):
  - BOOT_COMPLETED receivers may not launch `dataSync`, `camera`,
    `mediaPlayback`, `mediaProjection`, `phoneCall` or `microphone` foreground
    services. `specialUse` is **not** on that list, so boot restore stays legal
    for this shape.
  - Apps holding `SYSTEM_ALERT_WINDOW` may only start a foreground service from
    the background when they *already have a visible overlay window* (or meet
    another exemption). Order of operations therefore matters: show the window,
    then start the service — which is exactly what a user-initiated toggle
    does. Core Line targets API 34 today, so neither rule binds yet, and the
    refusal is caught **where it is actually thrown**: on Android 12+ the
    platform throws `ForegroundServiceStartNotAllowedException` from the
    *service's own* `startForeground()`, not at the caller's
    `startForegroundService()`, so a try/catch in a boot receiver cannot see it
    — `OverlayService` catches it there and stops itself. A boot-time refusal
    then loses the dot instead of crashing the app at boot.
- **Play policy.** `specialUse` is the type Play reviewers ask you to justify
  at submission ([foreground service types][fgstypes]), and Core Line declares
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` on the service with the surfaces' purpose
  in it. `SYSTEM_ALERT_WINDOW` itself is special-access, granted in Settings,
  and Play expects the permission to be core to the app's function — a status
  dot the viewer explicitly enables is defensible; auto-granting anything is not.

---

## 8. What Core Line shipped

Everything below is in this repository, on the branch this document lives on.

**Native (Kotlin, `ticker/android/app/src/main/java/dev/corebuilds/line/`)**

| File | Role |
|---|---|
| `VpnState.kt` | The snapshot (`tunnelUp`, `covering`, `validated`, `transport`, `state`) and the callback watcher described in §5. Never names the provider; never turns `validated` into a fault. |
| `VpnDotView.kt` | The dot: keyline, halo, core; blink only while faulted; `hideWhenOk`; alpha from the drawer's brightness setting. |
| `VpnDotWindow.kt` | One small non-focusable, touch-through `TYPE_APPLICATION_OVERLAY` window, corner-addressable with absolute gravity (so an RTL locale cannot mirror "bottom right"), margin in dp. |
| `DotConfig.kt` | The drawable shape, sanitised on every path in (`corner`, `opacity`, `hideWhenOk`, `blink`, `marginDp`). |
| `OverlayPrefs.kt` | Native memory for one purpose: letting `BootReceiver` restore the dot before any WebView exists. |
| `OverlayService.kt` | Owns both surfaces — the ticker WebView and the dot — as one foreground service, with the notification naming what is running. |
| `BootReceiver.kt` | Restores the dot after `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`, inside a wrapper that swallows a refused background start (§7). The ticker is deliberately *not* restored: it is a WebView, and spinning one up from a boot broadcast is not a thing to do behind a viewer's back. |
| `MainActivity.kt`, `LineBridge.kt` | Six bridge calls: `vpnStatus`, `vpnDotActive`, `vpnDotPlatform`, `startVpnDot`, `setVpnDotConfig`, `stopVpnDot`. |

**Web (`ticker/`)**

- `lib/vpn.mjs` — the model, kept pure so it can be tested without a DOM: state
  derivation, visibility, tone, headline, detail text, and the native config
  payload with the panel's overscan folded into dp.
- `public/js/ui/settings.js` + `public/index.html` — a **VPN status dot** block
  inside the existing native overlay block: on/off, corner, brightness (with
  the same stepper grammar the overscan control uses), hide-while-healthy,
  pulse-on-fault, and a live status line with a colour-matched swatch. The line
  is re-read on open and on a 2 s timer while the drawer is visible, because a
  status readout that only updates on demand is the thing the dot replaces.
- `public/js/state.js` — five persisted keys, sanitised on every load.

**State model, as drawn**

| Dot | Meaning | Drawn when |
|---|---|---|
| Green | A tunnel is up **and** Core Line's traffic is in it | `covering` |
| Amber | A tunnel is up, but this app is outside it (per-app VPN) | `tunnelUp && !covering` |
| Red | No VPN tunnel on the device | `!tunnelUp` |
| (nothing) | `hideWhenOk` and healthy | viewer's choice |

**Tests and gates added**

- `ticker/tests/vpn.test.mjs` — 18 cases over the model: state derivation from
  partial payloads, the split-tunnel split, "string booleans are not true",
  `validated` never becoming a colour, clamps, and the overscan→dp arithmetic.
- `ticker/tests/vpn-ui.test.mjs` — 5 seam tests: every id the drawer touches
  exists in the markup, the dot controls sit inside the native overlay block so
  Fire TV and browsers cannot offer a dead switch, the config payload comes
  from the tested module, every bridge call exists on **both** Kotlin sides,
  and every persisted key is both declared and sanitised.

---

## 9. Verification status

**Run here, green:** `cd ticker && npm test` → 166 tests, 0 failures (143
pre-existing + 18 model + 5 seam). The web app was served from `server.mjs` and
the modified modules fetched back over HTTP to confirm they are served with the
right MIME type and contain the new wiring.

**Run in CI, green (PR #210):** `Core Line build lint tests` compiles the module
against API 34 and runs Android lint over it, and `Assemble APK` builds the
debug APK. Both pass on this branch's head. CI is the compiler for the native
half — and the first run of `:app:compileDebugKotlin` rejected
`WindowManager.LayoutParams` margin fields, which is exactly the class of
mistake no amount of reading catches and no sandbox without a JDK can find.

**Not run anywhere:** no device and no emulator (no `/dev/kvm` here), so nothing
in this document has been seen on a television. The Kotlin was not compiled *in
the sandbox* — no JDK, no Android SDK, no outbound network for Gradle — and the
device-level behaviour in §2, §6 and §7 is still inference from documentation
and field reports rather than observation.

**On-device checklist — the steps that would move this from reasoned to
proven:**

1. Install the debug APK on a Google TV device or Shield, and on a stock
   Android TV build if one is available.
2. Grant the overlay permission from the TV UI
   (Settings → Privacy → Special app access → Display over other apps → Core
   Line). `[UNVERIFIED]` — the screen's existence and wording differ by build.
3. If the screen does not exist, grant it over ADB and re-check:
   ```bash
   adb connect <tv-ip>:5555
   adb shell appops set dev.corebuilds.line SYSTEM_ALERT_WINDOW allow
   # the older spelling, if the above is refused:
   adb shell pm grant dev.corebuilds.line android.permission.SYSTEM_ALERT_WINDOW
   ```
   The app-ops form is the one that has survived recent Android TV and Fire OS
   builds in the field ([appchoose][appchoose], [r/TiviMate][tivimate]).
4. Switch the dot on in Settings → Ticker & display, then start a stream
   (ideally DRM-protected) and confirm the dot is visible over it — this is the
   claim the whole feature rests on.
5. Toggle the VPN from the VPN app's own screen and confirm green→red within a
   couple of seconds; toggle a per-app VPN that excludes Core Line and confirm
   amber.
6. Step through all four corners and confirm the dot sits fully on screen in
   each, inside the calibrated safe area. `WindowManager.LayoutParams` has no
   margin fields — the inset rides on `x`/`y`, documented as offsets from the
   edge the gravity anchors to, so a positive value should place the window
   inward. If any panel anchors it outward, the fix is a sign flip in
   `VpnDotWindow.layoutParams()`; this step is what would catch that.
7. Reboot and confirm the dot comes back without opening Core Line.
8. Confirm the D-pad walks past the dot untouched while it is up.

---

## 10. Sources

Platform:

- [Behavior changes: apps targeting Android 15][a15] — BOOT_COMPLETED FGS
  restrictions; the narrowed SYSTEM_ALERT_WINDOW exemption ("make sure your app
  already has an active overlay window before it attempts to start a foreground
  service from the background").
- [Changes to foreground services][fgschange] — the same two rules, phrased for
  earlier targets.
- [Foreground service types][fgstypes] — `specialUse` has no runtime
  prerequisite and is the type that needs a Play justification.
- [Read network state][readns] — INTERNET without VALIDATED as the captive
  portal signal; capabilities change at any time.
- [NetworkCapabilities][nc] — `TRANSPORT_VPN`, and `getOwnerUid()` being
  populated only for the owner of a VPN network.
- [VpnService.Builder.setUnderlyingNetworks][vpnsvc] — how a VPN is *supposed*
  to publish the network beneath it, and why the label cannot be read from it
  reliably across clients.

Detection practice:

- [SO 28386553 — Check if a VPN connection is active in Android][so1],
  [SO 29419175 — NetworkInfo for VPN on API 21+][so2],
  [Tarka Labs — the ultimate VPN detection guide][tarka] — the
  `TRANSPORT_VPN` consensus.
- [SO 66350798 — ConnectivityManager missing when a VPN is connected][so3] and
  the [Farbklex gist][gist] — `activeNetwork` returns the VPN, and
  INTERNET/VALIDATED are unreliable under local VPNs, including in airplane
  mode.

Overlay mechanics and permission paths:

- [Beebom — screen recording on Android TV][beebom] — Google removed the
  user-facing overlay page on Android TV; `appops set … SYSTEM_ALERT_WINDOW
  allow` is the workaround.
- [appchoose — allowing SYSTEM_ALERT_WINDOW on TV][appchoose] — the ADB recipe
  as documented by an overlay app's own help page.
- [r/AndroidTV — Display over other apps, kids profile on Google TV 14][gtvaccess]
  — the modern Google TV path, and the profile limitation.
- [r/TiviMate — "please allow TiviMate to display over other apps"][tivimate] —
  the Shield path and repeated field reports of the app-ops fix.
- [iTechGuides — how display over other apps works][itech] — what an
  application overlay can and cannot cover.
- [Grokipedia — SYSTEM_ALERT_WINDOW][grokipedia] — protection level and history.

Prior art:

- [IPVanish — VPN Status Indicator on Android TV and Fire TV][ipvanish] — the
  closest implementation to this feature, including hide-on-connect,
  transparency and the Fire TV grey/ADB limits.
- [VPN Safety Dot — FAQs][safetydot], [Web Safety Tips][wstdot] — green/red
  blink, works over Netflix.
- [max-soft — VPN Monitor Dot][maxsoft], [HoBSoft][hobsoft] — per-state
  brightness, blink rates, autostart.
- [TROYPOINT forum — "VPN Dots not working correctly"][troy] — the kill-switch
  false-green complaint, and the community's own reasoning about it.
- [r/AndroidTV — VPN/status over the home screen][redditdot] — what users ask
  for when no such app is installed yet.

Platform context for the TV indicator gap:

- [r/AndroidTV — does Android TV have a status bar?][tvstatusbar] — the icon row
  and notification card live on the launcher.
- [ExpressVPN — set up on Android TV][expressvpn],
  [Top10VPN — best VPNs for Google TV][top10vpn] — vendor UX and per-app
  tunnelling on TV.

[askvg]: https://www.askvg.com/fix-display-over-other-apps-feature-not-available-on-android-smartphones/
[grokipedia]: https://grokipedia.com/page/SYSTEM_ALERT_WINDOW
[gtvaccess]: https://www.reddit.com/r/AndroidTV/comments/1nyv19u/permission_display_over_other_apps_kids_profile/
[shieldpath]: https://www.reddit.com/r/TiviMate/comments/175q9u7/odd_message/
[beebom]: https://beebom.com/record-screen-on-android-tv/
[ipvanish]: https://support.ipvanish.com/hc/en-us/articles/13357715408795-How-to-use-the-VPN-Status-Indicator-on-Android-TV-and-Fire-TV-devices
[tvstatusbar]: https://www.reddit.com/r/AndroidTV/comments/n7ac19/does_android_tv_os_have_a_status_bar_if_not_what/
[keyicon]: https://www.technerdiness.com/android/android-status-bar-icons-symbols-meaning/
[expressvpn]: https://www.expressvpn.com/support/vpn-setup/setup-android-tv/
[top10vpn]: https://www.top10vpn.com/best-vpn/google-tv/
[safetydot]: https://help.vpnsafetydot.com/kb/faqs/
[wstdot]: https://www.websafetytips.com/how-to-install-vpnsafetydot-on-firestick/
[maxsoft]: https://max-soft.com/vpndot.asp
[hobsoft]: https://hobsoft.com/resources/vpn-monitor-dot/
[troy]: https://troypointinsider.com/t/vpn-dots-not-working-correctly/83845
[redditdot]: https://www.reddit.com/r/AndroidTV/comments/jua2cj/any_way_to_display_public_ip_address_or_vpn/
[tivimate]: https://www.reddit.com/r/TiviMate/comments/184jrf1/please_allow_tivimate_to_display_over_other_apps/
[appchoose]: https://appchoose.blogspot.com/p/time-netspeed-overlay-adb-help-for.html
[itech]: https://www.itechguides.com/draw-display-over-other-apps-on-android-how-it-works-and-how-to-enable-it/
[a15]: https://developer.android.com/about/versions/15/behavior-changes-15
[fgschange]: https://developer.android.com/develop/background-work/services/fgs/changes
[fgstypes]: https://developer.android.com/develop/background-work/services/fgs/service-types
[readns]: https://developer.android.com/develop/connectivity/network-ops/reading-network-state
[nc]: https://developer.android.com/reference/android/net/NetworkCapabilities
[vpnsvc]: https://developer.android.com/reference/android/net/VpnService.Builder
[so1]: https://stackoverflow.com/questions/28386553/check-if-a-vpn-connection-is-active-in-android
[so2]: https://stackoverflow.com/questions/29419175/networkinfo-for-vpn-on-android-api-21
[so3]: https://stackoverflow.com/questions/66350798/connectivitymanager-missing-when-a-vpn-is-connected
[gist]: https://gist.github.com/Farbklex/f84029889444ee9c52a331a7e2bd10d2
[tarka]: https://tarkalabs.com/blogs/vpn-detection-guide-ios-android/
