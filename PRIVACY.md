# Privacy policy: Core Builds Icon Pack and Core Builds Glyphs Pack

Effective 2026-10-19. This covers both apps in every build: the Google Play
apps (`tv.corebuilds.iconpack.play`, `tv.corebuilds.iconpack.glyphs.play`)
and the sideloaded ones from GitHub (`tv.corebuilds.iconpack`,
`tv.corebuilds.iconpack.glyphs`, and Core Builds Glyphs,
`tv.corebuilds.glyphs`, which is the icon pack drawn in glyph art and is
covered below wherever this page says "icon pack"). Core Builds is
maintained by brevityA:
<https://github.com/brevityA/CoreBuildsApps>.

The short version: no account, no analytics, no advertising, no tracking,
no identifier. Each app sends something only when you ask it to do a thing
that needs the network, and this page lists every one of those things. The
icon pack's About screen shows the same list, and that list changes with
the build you have installed.

## What the apps never do

- No analytics, crash-reporting or advertising SDKs, and no advertising ID.
- No account or sign-in of any kind.
- No list of your installed apps leaves the TV. The icon pack reads which
  apps are installed so it can show what has no icon yet; that scan stays
  on the TV.
- Nothing is sold, and nothing is shared for advertising.

## What each app sends, and when

### Core Builds Glyphs Pack

Nothing. It has no network code: its icons ship inside it.

### Core Builds Icon Pack

| When | What is sent | To |
|---|---|---|
| You download a wallpaper or a live loop | A request for that file | GitHub (`raw.githubusercontent.com`) |
| GitHub build only: update checks are on, or you press *Check for updates* | A request for the update manifest, and the update itself if you choose to download it | GitHub |
| GitHub build only: you switch the art style to Glyphs for the first time | A request for the Glyphs Pack APK | GitHub |
| You press an app under *Missing icons*, in a build with one-press requests switched on | That app's name and component name; your TV's model, Android version and icon pack version | The Core Builds request broker, which files a public GitHub issue |
| As above, on Google Play, if you own Core Builds Plus | Also your Google Play purchase token for Plus | The Core Builds request broker, which checks it with Google |

Every request travels over HTTPS. Requests to GitHub are subject to
[GitHub's privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement);
GitHub, like any web server, sees the IP address a request comes from.

In a build without one-press requests (the *Missing icons* screen then
shows a QR code instead), pressing an app sends nothing: your phone opens
a prefilled GitHub form, and you choose whether to submit it.

## Icon requests and the request broker

One-press requests go to a small service Core Builds runs on Cloudflare
Workers. It does three things with a request:

1. **Files it.** The app name, component name and device line become a
   **public** GitHub issue on `brevityA/CoreBuildsApps` (or a +1 comment on
   an open issue for the same app). They are public because the whole point
   is that anyone can see which apps need icons. Nothing else about you is
   in the issue.
2. **Rate-limits it.** Your IP address is kept for up to an hour, keyed by
   the hour, to stop one address filing more than a handful of requests.
   Then it expires. It is never written to the issue.
3. **Checks Plus, on Google Play.** If the request carries a Plus purchase
   token, the broker asks the Google Play Developer API whether it is a
   real purchase that has not been cancelled or refunded. If so, the issue
   gets a `priority` label. The token itself is never logged, filed or
   stored. A one-way SHA-256 hash of it is kept with the answer for up to
   six hours (ten minutes when the answer is no), so pressing several apps
   does not ask Google each time. Then it expires.

The broker's code is public: `tools/icon_request_broker/` in the repository.

## Core Builds Plus purchases

Plus is sold through Google Play Billing. Google handles the payment under
its [Payments privacy notice](https://payments.google.com/payments/apis-secure/get_legal_document?ldo=0&ldt=privacynotice);
Core Builds never sees your card details, and neither the apps nor the
request broker ever see your Google account name or e-mail address. Play
Console keeps an order record for developers (order ID, product, price,
date, country), which Core Builds uses only for refunds and tax.
Refunds go through Google Play.

## On the TV itself

The apps keep their settings, downloaded wallpapers and, on Google Play,
the Plus purchase token in their own app storage. Uninstalling an app
removes all of it.

## Children

The apps are not directed at children and collect no data about anyone's
age.

## Your choices

- Do not press *Missing icons* entries, and nothing is filed.
- Turn update checks off in Settings (GitHub build).
- To have an issue you filed removed, open a GitHub issue or use the
  contact below with the issue number. Issues carry no account or contact
  details, so we cannot look one up by person.

## Contact

Open an issue at <https://github.com/brevityA/CoreBuildsApps/issues>, or
write to the developer e-mail address on the apps' Google Play listing.

## Changes

Changes to this policy are made in this file, so every version and its date
are in the repository's history.
