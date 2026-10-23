# Premium features — launch review for the icon pack

Reviewed 2026-10-23 against `main` @ `e3d5a35` (2.5.0, versionCode 58).

| PR | What it is | State | Merge |
|---|---|---|---|
| **#360** | Core Builds Premium — one purchase, Play build only | open, **stale base** | **CONFLICTING** (9 files) |
| **#370** | Plus 2.6 — night loop, Projectivy walls | open | clean |
| **#368** | Play Console walkthrough: the two refusals the first upload hit | open | clean |

## Verdict

**#360 cannot ship as written.** Not because the design is wrong — the design is
good — but because the branch was cut **before Plus merged**, and merging it
today would (a) fail the suite's own Plus test, (b) leave the **Play bundle**
uncompilable, and (c) put **Billing 7.0.0** back into a build that already ships
Billing 8.0.0, nine days before Google's 1 November cutoff.

Two things are ready: the product shape (free listing + one non-consumable, gate
off one rule, GitHub build ungated) and the copy discipline (what stays free is
stated on the purchase screen, not buried). Both should survive the rebase.

---

## Blocker 1 — #360 predates Plus. The merge breaks the Play bundle, not the repo.

`feature/play-premium` is **2 commits ahead / 27 behind** `main`. Its base is
`fcbc9e43e` (#359, merged 2026-10-20 22:24). **#349 (Plus) merged 2026-10-21
07:39** — 9 hours later — and none of it is in the branch: no `PlusStore.kt`,
no `Plus` gate, no `plusWalls`.

### 1a. Two Billing dependencies after a text merge

| Tree | Declaration |
|---|---|
| `main` `app/build.gradle.kts:253` | `"playImplementation"("com.android.billingclient:billing:8.0.0")` |
| #360 `app/build.gradle.kts:240` | `implementation("com.android.billingclient:billing:7.0.0")` |

`app/build.gradle.kts` merges **without a textual conflict**, so both lines
survive. That trips two things:

- `tests/test_play_build.py` → `Plus.test_billing_ships_in_the_play_build_only`
  asserts **exactly one** `billingclient` line and that it starts with
  `"playImplementation"(`. Two lines → red suite.
- In the `play` variant Gradle resolves the pair to **8.0.0** (highest wins), and
  `Billing.kt:190` is written against 7's API:

  ```kotlin
  c.queryProductDetailsAsync(params) { result, productDetails ->
      details = productDetails.firstOrNull()      // List<ProductDetails> in 7
  ```

  PBL 8 changed `onProductDetailsResponse` to take **`QueryProductDetailsResult`**
  ([reference](https://developer.android.com/reference/com/android/billingclient/api/ProductDetailsResponseListener)).
  `firstOrNull()` does not exist on it. `main`'s `PlusStore.kt:175` already does
  it correctly: `found.productDetailsList.firstOrNull { … }`.

**So the one artifact you need — the `.aab` — is the one that won't compile**,
and CI never builds it: `play.yml` is manual and `main`-only, and
`build.yml` has no `bundlePlay` step (`test_play_build` asserts that).

### 1b. The conflicts are semantic, not whitespace

`git merge-tree` reports 9 conflicted files:

| File | Why |
|---|---|
| `WallpaperAdapter.kt` | #360 rewrote the constructor to `(all, onSelect, onLocked)`; `main` added the `Plus` gate and badge to the same block |
| `WallpapersActivity.kt` | `main` loads `plusWalls` + `Plus.unlocked`; #360 adds `onLocked = { openPremium() }` |
| `item_wallpaper.xml`, `strings.xml` | badge slot and `premium_*` strings vs 2.5.0 additions |
| `AndroidManifest.xml` (main + play) | `PremiumActivity` registration; `main`'s play manifest gained `leanback required="true"`, #360 adds `BILLING` |
| `CHANGELOG.md` | `[Unreleased]` vs the 2.5.0 entry |
| `docs/app-ui-mockups.json`, `tools/build_app_ui_mockups.py` | regenerate, don't hand-merge |

⚠️ **Adapters carry `item.plus`.** `Plus`'s 12 Spectra walls are `series-15-spectra`,
which is not in `Premium.FREE_SERIES`, so a naive merge makes a **Plus owner's
Spectra walls open the Premium paywall**. Whichever way the tiers resolve, `item.plus`
must be exempt from `Premium.needsPurchase` (or Plus must imply Premium).

---

## Blocker 2 — Billing 7.0.0 is past Google's cutoff. The extension ends **1 November 2026**.

| Billing | New apps / updates deadline | Extension until |
|---|---|---|
| 7 | **31 Aug 2026** (passed) | **1 Nov 2026** |
| 8 | 31 Aug 2027 | 1 Nov 2027 |

Sources: [deprecation FAQ](https://developer.android.com/google/play/billing/deprecation-faq),
[migration guide](https://developer.android.com/google/play/billing/migrate-gpblv8).

Your closed test ends around 4 November. **Any upload from 1 November must be
Billing 8+**, and the 31 August deadline has already passed, so today's uploads
need the extension form too. This is the one item on this list with a hard date.

`main` is already correct, and its comment explains why:

> 8.0.0 is the last release that declares minSdk 21: 8.1.0 through 9.x declare 23,
> and Fire TV needs 21. Play accepts 8.x until 2027-08-31.

#360's envelope reason ("Billing 8 … needs a minSdk the icon pack cannot take")
is **true of 8.1.0+, false of 8.0.0**. Do not carry that ceiling forward.

---

## Blocker 3 — Plus and Premium sell the same loops, with opposite promises

Both are one-time products, both Play-only, both sold in the same build:

| | **Plus** (`core_builds_plus`, shipped) | **Premium** (`premium_unlock`, #360) |
|---|---|---|
| Extras | priority icon requests, My requests, **live loop rotation**, Spectra (12 walls) | **all 48 live loops**, 8 still series (96 walls) |
| Listing copy | *"Every icon, all {walls} stills, **every loop** and every screen ship to everyone either way."* (`tools/build_play_listing.py:245`) | every loop and 96 of 132 stills are paid |

Three collisions:

1. **The listing contradicts the paywall.** Anyone who buys Plus under today's
   copy is promised every loop; Premium then locks all 48.
2. **Plus's "live loop rotation" sells a feature with nothing to rotate** if every
   loop needs Premium first. #370's night loop (Plus) has the same problem.
3. Two prices for overlapping value (`$2.99` Plus, `A$4.49` placeholder Premium),
   with no upgrade path.

Spectra itself is fine — `Wallpapers/plus-manifest.json`, not in the app catalogue.
The loops are the problem.

**Decision needed before the listing goes live** (no product exists in Console
yet, so this is free today and expensive after the first sale):

- **A. One product.** Fold Premium's content into Plus (raise Plus's price) or
  replace Plus with Premium and retire `core_builds_plus` unsold.
- **B. Two tiers, split by job.** Plus = service + Spectra; Premium = content
  (loops + 8 series). Requires rewriting `PLUS_COPY`, moving Plus's "loop
  rotation" and #370's night loop under Premium, and exempting `item.plus`.
- **C. Narrow Premium.** Stills only (96 walls), loops stay free — keeps Plus's
  existing promise intact but sells less.

My read: **A** (one purchase) is the least confusing for a TV audience and the
least code. **B** is defensible but doubles the store surface for a small pack.

---

## Verified defects in #360 — fix before any money moves

| # | Severity | Where | What happens | Fix |
|---|---|---|---|---|
| 1 | **High** | `Billing.kt:239` `owns()` | **Pending purchases unlock Premium.** Cash/carrier purchases arrive `PENDING` with the product id; `purchasesUpdated` (`:70-75`) and `queryOwned` (`:217`) both persist `premiumOwned = true` without checking `purchaseState`. `acknowledgeIfNeeded` correctly refuses it — so the code already knows it's unpaid. | `purchase.purchaseState == PURCHASED && products.contains(PRODUCT)`. Re-query on `ITEM_ALREADY_OWNED` rather than granting at `:85`. |
| 2 | **High** | `WallpaperPreviewActivity.kt:424` `onSetClicked()` | **Loops and Monet bypass the gate.** `lockedHere()` runs only in `applyNow()`/`saveNow()`. Set returns early to `onSetLiveClicked()` (`:574`, live loop) and `sendToMonet()` (`:547`, with Monet as HOME) *before* reaching them. Walk D-pad-right from a free still onto a loop in "All" → free loop. The loops are the headline good. | Gate once at the **top** of `onSetClicked()`, plus the permission callback at `:87` (`saveLiveForMonetNow`). |
| 3 | **Medium** | `app/build.gradle.kts:240` | **The GitHub/Downloader APK carries `com.android.vending.BILLING`.** The Billing AAR declares the permission in its own manifest, and `implementation` (not `playImplementation`) puts it in every variant — so no `tools:node="remove"` will help. Contradicts the PR body, `docs/RELEASE-INFRA.md:301`, and the PR's own test (which only reads local manifest files). | Use `"playImplementation"` like `main`, delete the manual `<uses-permission>` from `src/play`, and replace `test_billing_permission_is_play_only` with an assertion on the dependency line. |
| 4 | Low-Med | `Billing.kt:131` | **A paying owner is told "unavailable".** Setup failure reports `UNAVAILABLE` unconditionally; `PremiumActivity.render` overwrites `OWNED`. Content stays unlocked (Prefs), only the screen lies. Common on TV boxes without Play. | Fall back to `Prefs.premiumOwned` on setup failure, as `queryOwned` already does on a failed query. |
| 5 | Low | `WallpaperAdapter.kt:90-94` | Long-press on a **locked** tile enters selection mode first, then `toggle()` refuses — grid left in selection mode with 0 selected and the bar visible on return. | Check the lock in the long-click path before `enterSelectionMode()`. |
| 6 | Low | `WallpapersActivity.kt` (no `onResume`) | **PREMIUM badges survive a purchase** until the user scrolls or changes filter; content descriptions still say "locked". | Rebind on resume when the entitlement changed. |
| 7 | Low | `Premium.kt:66-72` | Comment says an unplaceable series "is free rather than locked"; `else -> false` **locks** it. A series added in a future release becomes paid by accident — the exact bug the comment warns about. | Make the code fail-open (`else -> true`) or correct the comment and add a test that a new series is a deliberate decision. |
| 8 | Trivial | `tests/test_premium_purchase.py:115` | `android:exported="false"` asserted against the whole manifest — passes for any activity. | Scope to the `.PremiumActivity` element. |
| 9 | Trivial | `.github/workflows/suite-ci.yml` | `test_premium_purchase.py` runs only in `build.yml`, which has path filters. | Add to `suite-ci.yml` (also CodeRabbit's finding). |

---

## When you can publish — the dates that actually bind

| Item | Date |
|---|---|
| Billing 7 extension ends | **1 Nov 2026** (9 days) |
| Closed test: 12 testers opted in **continuously** for 14 days; the **12th tester** sets the date, not the upload | if the closed track went live 21 Oct → earliest application **4–5 Nov** |
| Production access review after applying | usually ≤7 days → **live ~11 Nov** |
| New build into the same closed track | does **not** restart tester opt-in continuity (testers stay opted in); each release still needs review, ~1 day |

Sources: [Play testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).

Consequences for Premium:

- **Premium needs its own release: 2.6.0 / versionCode 59.** #360 bumps neither,
  and 58 is already uploaded.
- If you want Premium *tested* by the 12 testers, it has to be on the closed track
  **before 4 Nov** — which means rebased, on Billing 8.0.0, this week.
- **#368's open question is a schedule risk, not a doc nit:** whether testers on a
  *dedicated Android TV track* count toward the 12/14 gate is unverified. Check
  the Dashboard's production checklist; the fallback is a phone-compatible bundle
  on the shared closed track. Merge #368 first — both refusals it documents will
  hit the Premium upload too.

---

## Order of work

1. **Merge #368** (docs only, clean).
2. **Decide the tier question** (Blocker 3). Everything below depends on it.
3. **Rebase #360 onto `main`** and reconcile — do **not** keep its
   `implementation(billing:7.0.0)`, its own `BillingClient`, or its adapter
   constructor. Reuse `PlusStore`'s connection where the two products can share
   one.
4. **Fix defects 1 and 2** (pending purchase, Set bypass) — these are revenue,
   not polish.
5. **Bump to 2.6.0 / 59**, add `[Unreleased]` → `2.6.0` changelog, regenerate
   mockups (`build_app_ui_mockups.py`) and the Play listing if Plus copy changes.
6. **Add `:app:compileBannersPlayKotlin` to CI.** A green `build.yml` has never
   compiled the Play variant; that is why 1a is invisible.
7. **Play Console:** create `premium_unlock` (activate), license testers, set the
   price, answer *purchases = yes* in the content rating, and request the Billing
   extension **now** if any build before the rebase goes to a track.

## What this review did not do

- **No compile, lint or device run** — this sandbox has no Android SDK. The PBL 8
  break in 1a is from the published API signature, not from a build log.
- **No Play Console access** — product/tester state is unverified.
- **#370 was read, not reviewed line-by-line**; its conflict with #360 is at the
  tier level (loops, night loop) plus shared files, which is recorded above.
