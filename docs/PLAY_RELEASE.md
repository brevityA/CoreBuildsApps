# Releasing Core Builds on Google Play

The icon pack goes to Google Play as two apps, separate from the sideload
builds so neither ever updates the other:

| | Google Play | Sideload (GitHub, Downloader `5270601`) |
|---|---|---|
| Core Builds Icon Pack | `tv.corebuilds.iconpack.play` | `tv.corebuilds.iconpack` |
| Core Builds Glyphs Pack | `tv.corebuilds.iconpack.glyphs.play` | `tv.corebuilds.iconpack.glyphs` |
| Price | free, with Core Builds Plus at $2.99 once | free |
| Updates | Google Play | in-app, from GitHub |
| Support screen | Core Builds Plus (Play Billing) | GitHub Sponsors and Ko-fi QR codes |
| Devices | Android TV only (`leanback` required) | any |

**Core Builds Plus** is one managed product, `core_builds_plus`, bought once.
Every icon, the 132-wall collection and every screen ship to everyone, on
Play and GitHub alike. Plus adds four things, on Google Play only:

| Extra | Where | How it works |
|---|---|---|
| Priority requests | Missing icons | The one-press request carries the Play purchase token; the broker checks it with Google and labels the issue `priority` |
| My requests | Settings | Each request's issue state, read from GitHub's API by number |
| Live loop rotation | Settings | The wallpaper engine moves to the next downloaded loop as the home screen comes back (hourly, 6-hourly or daily) |
| Spectra (series 15) | Wallpapers, Plus chip | 12 4K walls, indexed by `Wallpapers/plus-manifest.json`, thumbs bundled into the Play build only |

Only priority is checked off the TV, because only priority reaches other
people. The other three unlock on the TV's own Play-confirmed purchase,
which the app re-reads from Play at every start.

This page is the order to do things in. Steps marked **repo** are done in
this repository; everything else is the owner's, in Play Console and
Google Cloud. Facts that can change are dated and sourced at the bottom.

## Where things stand (2026-10-19)

| Piece | State |
|---|---|
| `play` build type, both apps, no self-update, no APK installs | done, `tests/test_play_build.py` |
| TV-only Play manifests (`leanback` required) | done; see step 7 for why |
| Target API | `targetSdk = 34`: the floor for Android TV apps. Phones would need 36 |
| Play Billing 8.0.0, Plus screen, token on requests | done, `tests/test_play_build.py` (`Plus`) |
| Plus extras: My requests, loop rotation, Spectra | done, `tests/test_play_build.py` (`PlusExtras`), `tests/test_wallpapers.py` (`PlusCollectionTests`) |
| Broker checks Plus with Google, labels `priority` | done, `tools/icon_request_broker/` (50 tests) |
| Broker deploy pushes the Plus key | done in Core-Builds `deploy-icon-request-broker.yml` |
| `play.yml` builds both signed bundles | done; hand-run from `main` |
| Privacy policy | [`PRIVACY.md`](../PRIVACY.md) |
| Request broker deployed, endpoint baked into the app | **not yet**: Plus is not on sale until it is (step 10) |
| Play developer account, listing, testing | **owner** |
| A Plus purchase on a real TV | **not yet verified**: no build has bought Plus on a device |

## 1. Developer account

1. Create the account at <https://play.google.com/console/signup>: a $25
   one-time fee and identity verification.
2. **Personal or organisation.** A personal account created after
   2023-11-13 must run a closed test with **at least 12 testers opted in
   for 14 days in a row** before it can apply for production (step 12).
   An organisation account skips that, but needs a D-U-N-S number for the
   organisation.
3. Turn on 2-Step Verification for the Google account that owns it.

## 2. Payments profile (needed before Plus can exist)

1. *Play Console → Setup → Payments profile*: create the merchant payments
   profile, with bank account and tax details. In-app products cannot be
   created without it.
2. Fees on Plus. For the first $1M of earnings a year, Google keeps about
   15%: a 10% service fee plus a 5% billing fee in the US, EEA and UK
   since 2026-06-30, and the 15% service-fee tier elsewhere, which needs
   the account group set up in Play Console. On a $2.99 sale that leaves
   about $2.54 before tax. Check the rate card in Play Console: it is the
   authority.

## 3. Upload key and GitHub secrets (one time)

The Play build is signed with its own **upload key**, never the sideload
release key. Play App Signing re-signs what users install, so a lost upload
key can be reset through Play support; a leaked one is replaced the same way.

```bash
keytool -genkeypair -v -keystore play-upload.jks -alias play-upload \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 play-upload.jks > play-upload.jks.b64
```

Add these **CoreBuildsApps** Actions secrets (*Settings → Secrets and
variables → Actions*), then delete `play-upload.jks.b64` and keep the
`.jks` and its passwords in a password manager:

| Secret | Value |
|---|---|
| `PLAY_KEYSTORE_BASE64` | contents of `play-upload.jks.b64` |
| `PLAY_KEYSTORE_PASSWORD` | the keystore password |
| `PLAY_KEY_ALIAS` | `play-upload` |
| `PLAY_KEY_PASSWORD` | the key password |

## 4. Build the bundles (repo)

*Actions → Build Google Play bundles → Run workflow* on `main`. It runs the
Play policy tests, lints and builds both bundles, refuses any branch but
`main`, fails if a bundle comes out unsigned while the key is set, and
uploads the artifact `core-builds-play-bundles`:

- `app-banners-play.aab`: Core Builds Icon Pack
- `glyphs-play.aab`: Core Builds Glyphs Pack

It also warns `audit_request_endpoint is empty` while the broker is not
baked in: such bundles are valid, but Plus is not on sale in them.

## 5. Create the two apps

For each app: *Play Console → Create app*, default language English (United
States), **App**, **Free**: Plus is an in-app product, and Play never lets
a free app become paid, so this choice is permanent. Names:
`Core Builds Icon Pack` and `Core Builds Glyphs Pack` (30 characters max).

The package name is fixed by the first bundle uploaded to each app, so
upload `app-banners-play.aab` to the Icon Pack and `glyphs-play.aab` to
the Glyphs Pack. Never the other way round. Accept **Play App Signing**
when asked (new apps cannot opt out).

## 6. App content (*Policy and programs → App content*)

| Section | Answer |
|---|---|
| Privacy policy | `https://github.com/brevityA/CoreBuildsApps/blob/main/PRIVACY.md` |
| Ads | No ads |
| App access | All functionality is available without special access |
| Content rating | IARC questionnaire, category *All other app types* (utility). No violence, no user interaction, no location sharing; **yes** to digital purchases. Expect Everyone / PEGI 3 |
| Target audience | 13 and over (not 12 or under: that brings in the Families policy) |
| News app, government app, financial features, health | No |
| Advertising ID | Not used. The merged Play manifest has no `AD_ID` permission (checked 2026-10-19) |
| Data safety | below |

**Data safety**, for a build with the request broker baked in (step 10).
With an empty `audit_request_endpoint` the icon pack sends neither, and the
honest answer is "no data collected"; change the form in the same release
that bakes the endpoint in.

| Data type | Collected | Shared | Why | Notes |
|---|---|---|---|---|
| App activity → **Installed apps** | Yes, optional | No | App functionality | Only the one app the user presses under *Missing icons*, to file its icon request. Filing it as a public GitHub issue is the user-initiated action the user asked for, which Google's form exempts from "shared" |
| Financial info → **Purchase history** | Yes, optional | No | App functionality | Only from Plus owners: the purchase token, checked with Google. Not stored; a SHA-256 of it is cached for at most six hours |
| Everything else | No | No | | No account, analytics, location or identifiers |

*My requests* reads public GitHub issues by number, the same kind of
public-content fetch as a wallpaper download, so it adds no data type.

Also answer: data is **encrypted in transit** (HTTPS everywhere), and users
**can ask for deletion** (an issue on the repo; the cached data expires by
itself). The TV model and Android version sent with a request match no
type in Google's list; if a reviewer asks, declare them under *App info and
performance → Other*. The broker keeps a caller's IP address for up to an
hour for rate limiting only, which `PRIVACY.md` states.

The Glyphs Pack has no network code: answer "no data collected".

## 7. Android TV

- *Play Console → Test and release → Advanced settings → Form factors →
  Add form factor → Android TV*, accept, and add a TV screenshot. TV
  releases go through an extra Android TV quality review, which can take
  longer than a phone review.
- **Why the Play builds require leanback.** With `leanback` optional (as in
  the sideload manifest) Play also lists the app for phones, and since
  2026-08-31 new phone apps and updates must target API 36. Android TV apps
  may target 34, which this app does. `app/src/play/AndroidManifest.xml`
  and `glyphs/src/play/AndroidManifest.xml` make leanback required, and
  `tests/test_play_build.py` pins both and the target. Raising `targetSdk`
  means re-reading Play's rules first; the test says so.
- Already true, and what the TV review checks: a `LEANBACK_LAUNCHER` entry
  and a 320 × 180 banner (`drawable-xhdpi/cb_banner.png`) in each app,
  `touchscreen` not required, every screen driven by the D-pad, no phone-only
  permissions.

## 8. Store listing (*Grow users → Store presence → Main store listing*)

| Asset | Spec |
|---|---|
| App name | ≤ 30 characters |
| Short description | ≤ 80 characters |
| Full description | ≤ 4,000 characters |
| App icon | 512 × 512 PNG, 32-bit |
| Feature graphic | 1024 × 500, JPEG or 24-bit PNG, required |
| Android TV banner | 1280 × 720, JPEG or 24-bit PNG |
| Android TV screenshots | at least 1, up to 8, 16:9 (1920 × 1080). Take real ones: `adb exec-out screencap -p > shot.png` |
| Category | Personalization |

Draft copy for the icon pack (house voice: numbers, no adjectives):

> **Short:** 1,012 icons and 132 wallpapers for Projectivy Launcher on Android TV.
>
> **Full:** Core Builds Icon Pack draws 1,012 original-geometry icons for the
> apps on your Android TV, mapped to 1,933 launcher components, plus 132
> wallpapers and 48 live loops. Built for Projectivy Launcher; every screen
> runs on a remote.
>
> Missing icons lists the apps on your TV the pack does not draw yet. Press
> one to request it.
>
> Core Builds Plus (optional, one purchase) adds priority icon requests,
> My requests, live loop rotation and Spectra, 12 more wallpapers. Every
> icon, the 132-wall collection and every screen ship to everyone either
> way.
>
> No account, no ads, no analytics. About lists everything the app sends.

Recount before each upload: `icon_count` in `app/src/main/res/values/icon_pack.xml`,
`<item>`s in `app/src/main/assets/appfilter.xml`, and the wallpaper and live
loop manifests.

> **Note:** keep other companies' app names and logos out of the listing
> text, icon and screenshots. Naming the apps an icon pack covers reads as
> keyword spam to Play's metadata policy, and a screenshot full of
> recognisable brand marks invites an intellectual-property complaint.
> Screenshots of the pack's own screens and of Projectivy are fine.

## 9. Core Builds Plus product

Play Console only offers in-app products once a bundle with the
`com.android.vending.BILLING` permission is uploaded to the app (Billing
8.0.0 adds it to the Play build), so upload to the internal track first
(step 11).

1. *Monetize with Play → Products → One-time products → Create*.
2. Product ID **`core_builds_plus`**: it must match `PlusStore.PRODUCT_ID`
   and the broker's `PLUS_PRODUCT_ID`, and an ID can never be reused.
3. Name `Core Builds Plus`; description (200 characters max):
   `Priority icon requests, My requests, live loop rotation and the 12
   Spectra wallpapers. One purchase, no subscription.`
4. One purchase option, **Buy**, $2.99 USD, local prices filled in by Play.
   No offers: the app reads the default option's price.
5. Activate.
6. *Settings → License testing*: add the Google accounts of whoever tests
   purchases. Their purchases use test cards and are never charged, and
   cover the success, declined and slow (pending) paths.

## 10. Request broker and endpoint (owner + repo)

Plus is on sale only in a build with the broker URL baked in, because
priority requests are the whole of what it adds.

1. Deploy the broker with a filing sink:
   `tools/icon_request_broker/README.md` (GitHub token or App, set as
   Core-Builds `ICONREQ_*` secrets, deployed by Core-Builds' *Deploy
   icon-request broker* workflow, staging first).
2. Give it the Plus check: the service-account steps in the same README,
   section *Core Builds Plus*, ending with the Core-Builds secret
   `ICONREQ_GOOGLE_SERVICE_ACCOUNT_JSON`. The deploy then smokes with
   `--require-plus`, and `/healthz` reports `"plus": true`.
3. **repo:** bake the production URL in and release it as a normal
   sideload release first:
   `python tools/build_issue_prefills.py --endpoint=https://corebuilds-icon-request.<subdomain>.workers.dev`,
   commit the regenerated `issue_prefill.xml`. From that build on, both
   builds' About screens list request line 3, and the Play build's Support
   screen sells Plus.
4. Update the Data safety form (step 6) in the same Play release.

## 11. Testing tracks

1. **Internal testing** (up to 100 testers, available within minutes, no
   review): upload both bundles, add yourself, install on a TV from the
   Play Store link. Buy Plus as a license tester, then check each extra:
   a request is filed with `priority`; *My requests* lists it; *Rotate
   live loops* set to every hour moves the live wallpaper after an hour
   (with two or more loops downloaded); the Plus chip's walls open and
   download. Before buying, each of those must open the Plus card instead.
2. **Closed testing** (personal accounts: the 12 × 14 rule). Make a Google
   Group, share its opt-in link (r/CoreBuilds is the obvious place), and
   keep more than 12 opted in: testers who leave can break the 14-day run.
   Testers install from the Play Store on their TV once opted in with the
   same Google account.

## 12. Production

1. Apply for production access from the dashboard (personal accounts, after
   step 11.2). Google asks how the test went; answer with what changed.
2. Release to production with a **staged rollout** (start at 20%) and
   watch *Android vitals* and reviews before 100%.
3. A first TV release can take days to review. Later updates are usually
   faster.

## Every later release

1. Tag and ship the sideload release as usual (`tools/prepare_release.py`).
2. Run *Build Google Play bundles* on `main` at that commit.
3. Upload both bundles to their apps: internal track, then production.
   `versionCode` is shared with the sideload build, which is fine because
   they are different apps.
4. Re-read the target API rules each spring: Google announces each year's
   floor ahead of its 31 August deadline.

## Not decided or not verified here

- No device has bought Plus. Internal testing (step 11.1) is the first
  real proof of the purchase sheet on a TV and of the broker's `priority`
  label. Loop rotation, My requests and the Spectra lock have only been
  compiled and source-tested, not run on a TV.
- The Data safety answers above are our reading of Google's form; a
  reviewer has the last word.
- Fees, target API floors and tester rules are Google's and change. They
  were read on 2026-10-19 from the sources below.

## Sources

- Target API: <https://support.google.com/googleplay/android-developer/answer/11926878>
  and <https://developer.android.com/google/play/requirements/target-sdk>
- Testing for new personal accounts: <https://support.google.com/googleplay/android-developer/answer/14151465>
- Service fees: <https://support.google.com/googleplay/android-developer/answer/112622>
- Play Developer API purchase check: <https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.products/get>
