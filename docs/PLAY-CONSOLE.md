# Play Console walkthrough — Core Builds Icon Pack

One page, in the order Play asks for things. Every value here is generated or
checked by the repo, so nothing on this page is guessed:

| Value | Source of truth |
|---|---|
| Title, short and full description | `docs/play/listing.json` → `copy` |
| Icon, feature graphic, TV banner, TV screenshots | `docs/play/` (regenerate with `tools/build_play_listing.py`) |
| Data safety and content-rating answers | `docs/play/listing.json` → `data_safety`, `content_rating` |
| Privacy policy | `docs/privacy.html`, served over GitHub Pages |
| Package name | `tv.corebuilds.iconpack.play` (the Play build type) |
| Core Premium product ID | `core_builds_plus` (`PlusStore.PRODUCT_ID`, and the broker's `PLUS_PRODUCT_ID`; the code name stays in the ID) |
| Core Builds Glyphs Pack, the second app | `docs/play/glyphs/` (§9) |

`tests/test_play_listing.py` keeps the copy's numbers equal to what the build
ships; `tests/test_privacy_policy.py` keeps the policy's claims equal to the
code. If either fails, the store page is about to become untrue — fix the gate
before touching Console.

---

## The clock that matters

A **new personal developer account** cannot publish to production until it has
run a closed test with **12 testers opted in for 14 continuous days**, then
applied for production access (review usually ≤ 7 days).

Internal testing does **not** count toward it, and the 14 days are the 14
*before* you apply — a tester who drops out and rejoins restarts the count.
That makes this the longest item in the whole launch, and the reason to get an
artifact into closed testing on day one rather than perfecting store copy first.

---

## 1. Create the app

Play Console → **All apps → Create app**.

| Field | Answer |
|---|---|
| App name | `Core Builds Icon Pack` (30-character limit; this is 21) |
| Default language | English (Australia) |
| App or game | App |
| Free or paid | **Free** — irreversible. Play never converts free → paid |
| Package name (if asked) | `tv.corebuilds.iconpack.play` — must match the bundle exactly, and it can never be changed after the first upload |
| Declarations | Accept the Developer Program Policies and US export declarations |

Then, because Play now requires it for every app: register the package name
under **Android developer verification** (Play Console → the app → status
check). A missing registration blocks review even when everything else is done.

## 2. App content — the forms, in this order

Play Console → **Policy and programs → App content**. Each of these can hold
review, and two of them are universal declarations that must be submitted even
though there is nothing to declare.

| Form | Answer |
|---|---|
| **Privacy policy** | `https://brevityA.github.io/CoreBuildsApps/privacy.html` (see §6) |
| **App access** | No restrictions — the app needs no login, and reviewers need no credentials |
| **Ads** | **No ads** |
| **Content rating** | IARC questionnaire: category **All Other App Types**. **Online Content: Yes**: the full-size wallpapers and the live loops download after install, from this repo (only thumbnails are bundled). To any follow-up asking whether that content has sex, violence, language, drugs or gambling, answer No. Then "No" to ratings-relevant content in the package, user content sharing, age-restricted products, location sharing, cash rewards / gift cards / NFTs, and ads. **Purchase digital goods**: copy `listing.json → content_rating.purchases` — "no" until Core Premium is on sale (§8), "yes" from then on. Expect **Everyone / PEGI 3**. Answers come from `listing.json → content_rating` |
| **Target audience** | 13+ / adults. Do **not** select an audience including children: it drags in the Families policy for no benefit to a wallpaper pack |
| **Data safety** | Copy `listing.json → data_safety`. Without the request broker: collects no data, shares no data, no ads, encrypted in transit. With it (§8): collects two optional types listed under `collected`, shares none. It carries the rationale and the one user-triggered request payload |
| **Government apps** | Not a government app |
| **Financial features** | No financial features |
| **Health apps** | No health features |
| **Advertising ID** | The app does not use the advertising ID |

Two of these deserve care rather than speed:

- **Data safety** is exempt only on internal testing. The moment you open a
  closed test it must be complete and accurate — it is a declaration under
  penalty of removal, and `tests/test_play_listing.py` reads the payload field
  names straight out of `IconRequestSender.kt` so the form cannot drift from
  the code.
- **Content rating says "no purchases"** while nothing is for sale. Core
  Premium goes on sale in the same build that bakes the request broker in (§8), and
  `tools/build_play_listing.py` moves three things with that one switch: the
  purchases answer, the Data safety answers, and a CORE PREMIUM paragraph
  in the full description. `--check` fails if the broker is baked in and the
  listing was not regenerated, so the form cannot keep last release's answers.

## 3. Store listing

Play Console → **Grow → Store presence → Main store listing**.

- **App name**: `Core Builds Icon Pack`
- **Short description**: paste `copy.short_description` from `listing.json`
  (80-character limit; currently 71)
- **Full description**: paste `copy.full_description` (4000 limit; currently
  2318)
- **App icon**: `docs/play/icon-512.png`
- **Feature graphic**: `docs/play/feature-graphic-1024x500.png`
- **TV banner**: `docs/play/tv-banner-1280x720.png` — required for a TV listing
- **TV screenshots**: `docs/play/tv-*.png` (Play wants at least one; the
  six committed files are 1920×1080, named by the frame they depict)

**Phone screenshots.** Console marks the phone section required (\*) on the
default listing even for a TV-only app (seen 2026-10-21, before the Android TV
form factor was added). Add Android TV first (§4). If the phone section is
still required, upload `docs/play/tv-1-*.png` … `docs/play/tv-4-*.png` there
too: they are real captures of the app, at the 16:9 the phone section
accepts, and the Play manifest still keeps the app off phones. Never render a
phone UI for it; there is no tested phone layout to show. Tablet, desktop and
XR stay empty.

## 4. Store settings

Play Console → **Store settings**.

- **App category**: App → *Personalization*
- **Contact email**: a real address — Play shows it publicly
- **Website**: optional; `https://github.com/brevityA/CoreBuildsApps` works
- **Form factors**: **TV only**, matching `listing.json → form_factors`.
  Listing for phones without phone screenshots is a review rejection, and the
  app's layouts have not been tested on a phone. Console alone cannot make a
  phone-compatible bundle TV-only: the Play manifests require
  `android.software.leanback`, so Play never offers this build to a phone.
  Add the form factor under *Test and release → Advanced settings → Form
  factors → Android TV*; TV releases get an extra quality review.
- **Dedicated Android TV track — before the first closed release.** A bundle
  that requires `android.software.leanback` is refused on the shared tracks:
  the closed release fails with *"APKs and bundles must not require following
  features: android.software.leanback. Some features can only be required in
  dedicated tracks."* (seen 2026-10-21). Under *Form factors*, choose
  **Manage** next to Android TV, then **Use a dedicated release track for
  Android TV**, **Opt in** and Save. Console needs TV screenshots on every
  store listing first, and says the choice **can't be changed**. It is the one
  we want: TV releases then live on their own tracks, and the shared tracks
  stay free for a phone build if one ever ships. Closed testing then has an
  Android TV track (create one with the Android TV form factor if it isn't
  listed); use it for §7. Testers on the TV track **do count** toward the
  12-tester / 14-day production gate: on 2026-11-14 the Dashboard's
  *Production* checklist ticked both *Publish a closed testing release* and
  *Have at least 12 testers opted in* for this app's Android TV closed track
  (§7a). No phone build is needed for it.

## 5. Build the bundle

The bundle is built by **Actions → "Build Google Play bundles"**
(`.github/workflows/play.yml`). It runs from `main` only.

The bundle is signed with its own **upload key**, never the sideload release
key. Play App Signing re-signs what users install, so a lost or leaked upload
key can be reset through Play support. Make one once:

```bash
keytool -genkeypair -v -keystore play-upload.jks -alias play-upload \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 play-upload.jks > play-upload.jks.b64
```

On Windows, in an ordinary (not Administrator) PowerShell, from a folder you
own rather than `C:\Windows\system32`. `keytool` ships with any JDK; if the
search prints nothing, `winget install Microsoft.OpenJDK.21`, open a new
window and search again:

```powershell
mkdir $HOME\play-keys; cd $HOME\play-keys
$kt = (Get-ChildItem "C:\Program Files","C:\Program Files (x86)","$env:LOCALAPPDATA" -Filter keytool.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1).FullName; $kt
& $kt -genkeypair -v -keystore play-upload.jks -alias play-upload -keyalg RSA -keysize 4096 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\play-upload.jks")) | Set-Clipboard
```

The last line puts the base64 on the clipboard for `PLAY_KEYSTORE_BASE64`,
so there is no `.b64` file to delete. It names `$PWD` because .NET resolves a
bare file name against its own working folder, not PowerShell's. A PKCS12
keystore, the default since JDK 9, has one password, so `PLAY_KEY_PASSWORD`
is the same as `PLAY_KEYSTORE_PASSWORD`.

Before the first run, add four repository secrets (Settings → Secrets and
variables → Actions), then delete the `.b64` file and keep the `.jks` and its
passwords in a password manager:

| Secret | What it is |
|---|---|
| `PLAY_KEYSTORE_BASE64` | the Play upload keystore, base64-encoded |
| `PLAY_KEYSTORE_PASSWORD` | keystore password |
| `PLAY_KEY_ALIAS` | key alias |
| `PLAY_KEY_PASSWORD` | key password |

The workflow produces `app/build/outputs/bundle/bannersPlay/*.aab` and
`glyphs/build/outputs/bundle/play/*.aab`, and fails loudly if either is
unsigned. Upload the icon pack's AAB for this listing; the Glyphs Pack is its
own listing (§9) and **both go out in the same window with matched rollouts** — see
`docs/RELEASE-INFRA.md`, because the pack refuses a companion whose
`versionCode` is behind its own.

**Before the first upload, switch off the Installer check.** Play turns on
*Automatic protection* for a new app, and its installer check injects code
that needs `minSdk` 24. Both bundles declare 21, so the upload is refused:
*"Play automatic protection requires a minimum SDK version of 24 or higher.
The uploaded app bundle has a minimum SDK version of 21."* (seen 2026-10-21).
Go to **Test and release → App integrity → Automatic protection**, turn
**Installer check** off, Save, and reload to confirm it stayed off. Then
remove the refused bundle from the release and upload it again. Do it in
both apps. That app-level switch is what cleared the refusal on 2026-10-21.
The release editor also offers **App bundle enhancements → Automatic
protection → Turn off protection for this release**, but Play re-applies
protection to the next upload, so on its own it would bring the refusal
back with every release.

This is a trade-off, not a free switch. With the check off, a copy of the
**Play build** installed from outside Play runs without Play's install
check. Keeping it on means `minSdk` 24, which drops TV boxes still on
Android 5 or 6 (Fire OS 5 is Android 5.1). The sideload build was never
under the check: it is unrestricted on purpose.

## 6. Turn on the privacy policy URL

The policy is a file in the repo (`docs/privacy.html`); Play needs a URL.

1. Merge the Play work to `main` — Pages cannot serve a file that is not on
   the branch it points at.
2. Repo → **Settings → Pages** → *Deploy from a branch*.
3. Branch **`main`**, folder **`/docs`** → Save.
4. The URL becomes
   `https://brevityA.github.io/CoreBuildsApps/privacy.html` — paste that into
   App content → Privacy policy.

`/docs` is shared with the mockups and store assets, so those become publicly
readable at the same host. That is harmless (the store assets are public
anyway); if you would rather not, move the policy to its own folder and point
Pages there instead.

## 7. Tracks, in this order

| Track | Purpose | Gate |
|---|---|---|
| **Internal testing** | Prove the bundle installs and launches on a real TV, in minutes | None — also the only track exempt from Data safety |
| **Closed testing** | Start the 12-tester / 14-day clock | Data safety complete |
| **Production** | Public listing | Closed test met, then apply for production access |

These are the **Android TV** tracks once §4's dedicated track is on. The
shared closed track refuses these bundles.

Release notes are per track and per language (500 characters): keep them plain
and factual — what changed, and anything a user must do.

## 7a. Production access: what Google reviews

**Where this app stands (2026-11-14).** The first application was reviewed on
14 November 2026 and answered *"Your app requires more testing to access
Google Play production"*. The checklist kept *Publish a closed testing
release* and *Have at least 12 testers opted in* ticked, and reset the third
step to *Run your closed test with at least 12 testers for 14 more days
starting from the review date*. So: apply again **no earlier than 28 November
2026**, and only if at least 12 testers stayed opted in on every one of those
days. Recruit 15 or more, so one tester leaving does not restart the count.

**Why Google says this happens.** Google's help page for new personal
accounts names two reasons: fewer than 12 opted-in testers, or *"insufficient
tester engagement during the testing period"*
([App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)).
The count was met, so engagement is the part to fix. The page recommends
clear instructions for testers, encouraging them to *"use as many features as
possible"*, a feedback channel with a record of what was said, fixing what
testers report, and updating the app in closed testing before production.

**The application asks eight questions.** Draft the answers from the test as
it runs, not on the last day:

| Part | Question (Google's wording) | What to have ready |
|---|---|---|
| Closed test | How easy it was to recruit testers | The honest choice. |
| Closed test | Tester engagement: *"whether testers used all available app features"* and *"whether tester usage matched expected production user behavior"* | Every tester had Core Premium through a code, so all six extras were open to them; the feature checklist below; what they actually used, from their reports. |
| Closed test | *"Summarize the feedback received from testers and describe how feedback was collected"* | About → **Send feedback** (Play build) files a GitHub issue labelled `feedback` with the build and TV filled in; count and summarise those issues, plus Discord and Reddit threads. |
| Your app | Target audience, *"as specific as possible"* | Android TV and Google TV owners who use Projectivy Launcher (or another launcher that reads icon packs) and want every app's icon and banner in one consistent style. |
| Your app | How the app provides value | 1037 icons and banners applied in one press, a Missing icons scan that files requests, 132 wallpapers and live loops; Core Premium optional. |
| Your app | Estimated installs in the first year | A realistic range. |
| Readiness | *"Describe any changes made … based on what you learned from your closed test"* | The changelog entries made during the test: 2.7.0 is the first. Name the issue numbers they answer. |
| Readiness | *"Describe how you determined that your app … was ready for production"* | Feedback issues closed or answered, CI green on the release, no crashes in Play's Android vitals for the test period, and the §8 step 8 purchase test done on a TV. |

**Give testers Premium without a test build.** Both of Google's routes work
with the app as it ships; neither needs a button in it.

1. **Promo codes, for testers.** *Monetize with Play → Promotions* (in
   current Console: *Promo codes*) → **Create promotion** → one-time codes
   for `core_builds_plus`. Play allows 500 codes a quarter across an app's
   one-time products, and unused codes do not carry over. Set an end date
   after the test, download the codes, and send each tester one.
   - The tester redeems it at `https://play.google.com/redeem`, on a phone
     or the web, signed in with the Google account their TV uses. It is a
     real purchase at no cost, on that account.
   - The TV picks it up without a restart: from 2.7.0, Home asks Google Play
     again each time it comes back (at most once a minute), and the Premium
     screen asks again when it is reopened. Before 2.7.0 it asked once per
     app start.
   - **Unverified:** redeeming on the TV's own Play Store app. The phone or
     web route is the one to tell testers.
2. **License testers, for you and anyone checking the purchase itself.**
   §8 step 4. They buy through *Get Premium*; Play's sheet offers test cards
   and nobody is charged.

**What to send testers on day one**: the opt-in link, their code, and this
checklist, each item one line:

- Apply the pack in Projectivy and check a dozen icons.
- Run **Missing icons** and request one icon.
- Open **Wallpapers**, download one, set it.
- Switch Art style to Glyphs and back.
- With Premium on: My requests, loop rotation, the night loop, Projectivy
  walls, Spectra and Aurum.
- Report anything, good or bad, from About → **Send feedback**.

Then ship an update to the closed track inside the 14 days (2.7.0), reply to
every report, and send one reminder half-way.

## 8. Core Premium

Core Premium (`Plus` in the code and the product ID) is one managed product,
bought once: priority icon requests, *My requests*, live loop rotation, the
night loop, the choice of what Projectivy rotates from the pack (by day and at
night) and two wallpaper series, Spectra and Aurum (24 walls). Every icon, the
main wallpaper collection (in
Projectivy's rotation too) and every screen ship to everyone. It is on sale
only in a build with the request broker baked in, because priority requests
are part of what it sells. The Play build has it baked in from the first
upload built after this change: versionCode 62 on main after 2.5.4 (if 62 is
already on a Play track, raise the version code first). Steps 5 and 6 are
done; the rest are Console's and the test on a TV.

Each TV can try it free for 24 hours, once, from the Premium card. The trial
is the app's own clock, not a Play offer (Play offers trials on subscriptions
only), so Console has nothing to set up for it. It switches on everything the
TV decides for itself; priority requests, *Request all* and saving a Premium
wall to Pictures stay with the purchase, because the broker checks the
purchase with Google and a saved copy would outlast the trial. Clearing the
app's data offers the trial again.

1. **Payments profile.** *Setup → Payments profile*: bank and tax details.
   Products cannot be created without it. Google keeps about 15% of the first
   $1M a year (in the US, EEA and UK a 10% service fee plus a 5% billing fee
   since 2026-06-30), so a $2.99 sale leaves about $2.54 before tax. The rate
   card in Console is the authority.
2. **Upload a bundle first.** Console offers in-app products only once a
   bundle carrying `com.android.vending.BILLING` is on a track (Billing 8.0.0
   adds it to the Play build): internal testing is enough.
3. **Create the product.** *Monetize with Play → Products → One-time
   products → Create*. Product ID `core_builds_plus` — it must match the
   code, and an ID can never be reused. Name `Core Premium`. One purchase
   option, **Buy**, $2.99 USD, local prices filled in by Play, no offers (the
   app shows the default option's price). Activate.
4. **License testing.** *Settings → License testing*: add the Google
   accounts that test purchases. They pay with test cards, are never
   charged, and can exercise the success, declined and pending paths.
5. **Deploy the broker with the Plus check.** `tools/icon_request_broker/README.md`
   covers both halves: the filing sink, then the service account that may
   call the Play Developer API, stored as the Core-Builds secret
   `ICONREQ_GOOGLE_SERVICE_ACCOUNT_JSON`. Core-Builds' *Deploy icon-request
   broker* workflow then smokes it with `--require-plus`, and `/healthz`
   reports `"plus": true`. Done: it did on 2026-10-29.
6. **Bake the endpoint in** (repo). Done, for every Play build from that
   upload on. The URL is in
   `app/src/play/res/values/request_service.xml`, which overrides the empty
   value `tools/build_issue_prefills.py` generates into main, so only the
   Play build posts to the service and the GitHub / Downloader build keeps
   the QR panel. (`build_issue_prefills.py --endpoint=URL` would bake it into
   every build instead.) `BROKER_SHIPPED = True` in
   `tools/build_play_listing.py` and the regenerated listing went in the same
   change, as they must. The latch keeps
   Data safety and the purchases answer covering broker builds for as long as
   any track still serves one, because Play keeps one form for every version
   on every track; `--check` refuses a broker build without it. From that
   build on, Settings has a CORE PREMIUM group and the listing, Data
   safety and content rating all say Core Premium.
7. **Re-answer Console in the same release**: paste the new full
   description, set Content rating → purchases to yes, and copy the
   `collected` types into Data safety, from the regenerated `listing.json`.
8. **Internal test before anyone pays.** On a TV, as a license tester: each
   extra opens the Premium card before buying. Press *Try free for 24
   hours*: the extras below unlock, Settings reads *Trial · 24 h*, while
   *Request all* and Save on a Spectra or Aurum wall still ask for the purchase. Buy. Then a missing-icon press
   files an issue labelled `priority`; *My requests* lists it; *Request all*,
   pressed twice, files the rest (up to 10) as `priority` and lists each; *Rotate live
   loops* set to every hour moves the live wallpaper after an hour with two
   or more loops downloaded; a *Night loop* plays from the start of *Night
   hours* with the home screen left on; with the pack as Projectivy's
   wallpaper source, *Projectivy walls* set to Aurum rotates only Aurum
   (`adb logcat -s CoreBuilds/Walls` shows each fetch and its count); the
   Premium chip lists Spectra then Aurum, 24 walls, and they open and
   download.

None of this has run on a TV yet: no device has bought Core Premium or run
its trial. Step 8 is the
first real proof of the purchase sheet, the acknowledge call and the broker's
label.

## 9. Core Builds Glyphs Pack: the second app

The Glyphs Pack (`glyphs/`) is the square companion: the same 1999 launcher
components, drawn as 1037 square glyphs. On Play, the icon pack's **Art
style** switch opens this app's listing when the pack is missing
(`GlyphsCompanion.ensure`), so it has to exist before anyone can pick Square.
In Console it is a separate app, with its own package, listing, forms and
tester clock. §1–§7 apply again; only the values below differ.

| Value | Source of truth |
|---|---|
| Title, short and full description | `docs/play/glyphs/listing.json` → `copy` |
| Icon, feature graphic, TV banner, TV screenshot | `docs/play/glyphs/` |
| Data safety and content-rating answers | `docs/play/glyphs/listing.json` → `data_safety`, `content_rating` |
| Package name | `tv.corebuilds.iconpack.glyphs.play`. The sideload build is `tv.corebuilds.iconpack.glyphs`; never paste that one |

1. **Create the app.** App name `Core Builds Glyphs Pack` (30-character
   limit; this is 23), the same launcher label the TV lists. **Free**,
   English (Australia), package `tv.corebuilds.iconpack.glyphs.play`. Register
   that package under Android developer verification, as in §1.
2. **App content.** The same privacy policy URL: `docs/privacy.html` already
   names both Glyphs package ids. No restrictions on access, no ads, 13+, no
   advertising ID. Content rating: the same IARC answers as §2, except
   **Online Content: no**, because the app has no internet access, and
   **purchases: no**, permanently. Core Premium is the icon pack's product, and this
   app sells nothing. Data safety: **collects no data, shares no data**. The
   app declares no permissions at all, `INTERNET` included, and links no
   library. `listing.json` leaves encryption and deletion `null` because
   nothing is sent.
3. **Store listing.** Paste `copy.short_description` (80-character limit;
   currently 69) and `copy.full_description` (4000 limit; currently 1028).
   - **App icon**: `docs/play/glyphs/icon-512.png`
   - **Feature graphic**: `docs/play/glyphs/feature-graphic-1024x500.png`
   - **TV banner**: `docs/play/glyphs/tv-banner-1280x720.png`
   - **TV screenshot**: `docs/play/glyphs/tv-1-glyphs-home-1920x1080.png`, its
     one screen

   The icon and banner are the companion's own violet glyph art
   (`tools/build_glyphs_branding.py`), the same drawings its launcher icon and
   Leanback banner use, not the icon pack's.
4. **Store settings.** The same category and contact email as the pack.
   **Form factors: TV only.** The Glyphs Play manifest requires
   `android.software.leanback`, as the pack's does, so this app needs its own
   **dedicated Android TV track** too (§4), opted in before its first closed
   release.
5. **Switch off the Installer check** (§5) before the first upload, then
   **upload only `glyphs-play.aab`** from the same `play.yml` run
   (`glyphs/build/outputs/bundle/play/*.aab`). Both bundles are signed with
   the same upload key. Play App Signing may give each app its own signing
   key, and the pack allows for that: on Play it trusts a companion that Play
   installed.
6. **Its own closed test.** On a new personal account, the 12-tester, 14-day
   rule applies to each app. Open this app's closed test **on the same day as
   the pack's, with the same testers** (one Google Group on both tracks).
   That way both clocks run together. A tester who flips Art style is sent to
   this listing, and a tester missing from its track cannot install from it.
7. **Every release after that, in step.** Both apps carry the same
   `versionCode`, so ship them in the same window with matched staged
   rollouts. If they must diverge, the Glyphs Pack goes first
   (`docs/RELEASE-INFRA.md`, "The two Play listings go out together").

`tools/build_play_listing.py --check` holds this listing to the same gates as
the pack's: Play's asset limits, the counts read from `glyphs/`'s own
`drawable.xml` and `appfilter.xml`, and copy equal to the generated copy.
`tests/test_play_listing.py` adds the rest: the icon and banner must be fresh
renders of the companion's art, the component set must equal the pack's, and
neither Glyphs manifest may ask for a permission.

## What this app will not do, and why that is deliberate

- **No update check on Play.** Play updates it; the Play build blanks the
  update manifest URL.
- **No install-package permission.** It is removed in
  `app/src/play/AndroidManifest.xml`; the GitHub/Downloader build keeps it for
  its self-update.
- **No route to a tip.** Support is hidden on Play — Play's Payments policy
  treats a non-charity donation link as circumvention.
- **No Downloader codes.** The Suite screen lists only what is already on the
  TV, with no invitation to install anything outside Play.

Each of those is a test, not a promise: `tests/test_play_build.py`.
