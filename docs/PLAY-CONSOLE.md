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
| Core Builds Plus product ID | `core_builds_plus` (`PlusStore.PRODUCT_ID`, and the broker's `PLUS_PRODUCT_ID`) |

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
| **Content rating** | IARC questionnaire: category *Utility / Productivity*, then "No" to violence, sexuality, language, controlled substances, gambling, user-generated content, location sharing and ads. **Purchases**: copy `listing.json → content_rating.purchases` — "no" until Core Builds Plus is on sale (§8), "yes" from then on. Expect **Everyone / PEGI 3**. Answers come from `listing.json → content_rating` |
| **Target audience** | 13+ / adults. Do **not** select an audience including children: it drags in the Families policy for no benefit to a wallpaper pack |
| **Data safety** | Copy `listing.json → data_safety`. Without the request broker: collects no data, shares no data, no ads, encrypted in transit. With it (§8): collects three optional types listed under `collected`, shares none. It carries the rationale and the one user-triggered request payload |
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
- **Content rating says "no purchases"** while nothing is for sale. Core Builds
  Plus goes on sale in the same build that bakes the request broker in (§8), and
  `tools/build_play_listing.py` moves three things with that one switch: the
  purchases answer, the Data safety answers, and a CORE BUILDS PLUS paragraph
  in the full description. `--check` fails if the broker is baked in and the
  listing was not regenerated, so the form cannot keep last release's answers.

## 3. Store listing

Play Console → **Grow → Store presence → Main store listing**.

- **App name**: `Core Builds Icon Pack`
- **Short description**: paste `copy.short_description` from `listing.json`
  (80-character limit; currently 71)
- **Full description**: paste `copy.full_description` (4000 limit; currently
  1448)
- **App icon**: `docs/play/icon-512.png`
- **Feature graphic**: `docs/play/feature-graphic-1024x500.png`
- **TV banner**: `docs/play/tv-banner-1280x720.png` — required for a TV listing
- **TV screenshots**: `docs/play/tv-*.png` (Play wants at least one; the
  six committed files are 1920×1080, named by the frame they depict)

Phone and tablet screenshots are deliberately empty: the app is listed for TV
only, and there is no tested phone UI to photograph. See §4.

## 4. Store settings

Play Console → **Store settings**.

- **App category**: *Tools* (or *Personalisation* if Console offers it for
  this app type)
- **Contact email**: a real address — Play shows it publicly
- **Website**: optional; `https://github.com/brevityA/CoreBuildsApps` works
- **Form factors**: **TV only**, matching `listing.json → form_factors`.
  Listing for phones without phone screenshots is a review rejection, and the
  app's layouts have not been tested on a phone. Console alone cannot make a
  phone-compatible bundle TV-only: the Play manifests require
  `android.software.leanback`, so Play never offers this build to a phone.
  Add the form factor under *Test and release → Advanced settings → Form
  factors → Android TV*; TV releases get an extra quality review.

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
own listing and **both go out in the same window with matched rollouts** — see
`docs/RELEASE-INFRA.md`, because the pack refuses a companion whose
`versionCode` is behind its own.

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

Release notes are per track and per language (500 characters): keep them plain
and factual — what changed, and anything a user must do.

## 8. Core Builds Plus

Plus is one managed product, bought once: priority icon requests, *My
requests*, live loop rotation and Spectra (12 walls). Every icon, the main
wallpaper collection and every screen ship to everyone. It is on sale only in
a build with the request broker baked in, because priority requests are what
it adds; until then the Play build sells nothing and the steps below wait.

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
   code, and an ID can never be reused. Name `Core Builds Plus`. One purchase
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
   reports `"plus": true`.
6. **Bake the endpoint in** (repo):
   `python tools/build_issue_prefills.py --endpoint=https://<broker URL>`,
   then `python tools/build_play_listing.py`, and commit both. From that
   build on, Settings has a CORE BUILDS PLUS group and the listing, Data
   safety and content rating all say Plus.
7. **Re-answer Console in the same release**: paste the new full
   description, set Content rating → purchases to yes, and copy the
   `collected` types into Data safety, from the regenerated `listing.json`.
8. **Internal test before anyone pays.** On a TV, as a license tester: each
   extra opens the Plus card before buying. Buy. Then a missing-icon press
   files an issue labelled `priority`; *My requests* lists it; *Rotate live
   loops* set to every hour moves the live wallpaper after an hour with two
   or more loops downloaded; the Plus chip's Spectra walls open and download.

None of this has run on a TV yet: no device has bought Plus. Step 8 is the
first real proof of the purchase sheet, the acknowledge call and the broker's
label.

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
