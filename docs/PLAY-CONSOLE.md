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
| **Content rating** | IARC questionnaire: category *Utility / Productivity*, then "No" to violence, sexuality, language, controlled substances, gambling, user-generated content, location sharing, purchases and ads. Expect **Everyone / PEGI 3**. Answers come from `listing.json → content_rating` |
| **Target audience** | 13+ / adults. Do **not** select an audience including children: it drags in the Families policy for no benefit to a wallpaper pack |
| **Data safety** | Collects no data, shares no data, no ads, encrypted in transit. `listing.json → data_safety` carries the rationale and the one user-triggered request payload |
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
- **Content rating says "no purchases"** because nothing is gated yet. When the
  premium unlock (PR #360) ships, that answer changes to yes and the listing
  copy has to say what the purchase buys. `test_play_listing.py` fails on prem
  claims in the copy before that change lands, on purpose.

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
only, and there is no tested phone UI to photograph. See §5.

## 4. Store settings

Play Console → **Store settings**.

- **App category**: *Tools* (or *Personalisation* if Console offers it for
  this app type)
- **Contact email**: a real address — Play shows it publicly
- **Website**: optional; `https://github.com/brevityA/CoreBuildsApps` works
- **Form factors**: **TV only**, matching `listing.json → form_factors`.
  Listing for phones without phone screenshots is a review rejection, and the
  app's layouts have not been tested on a phone.

## 5. Build the bundle

The bundle is built by **Actions → "Build Google Play bundles"**
(`.github/workflows/play.yml`). It runs from `main` only.

Before the first run, add four repository secrets (Settings → Secrets and
variables → Actions):

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
