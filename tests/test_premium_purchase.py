#!/usr/bin/env python3
"""The one-off Core Builds Premium purchase, and what it must never break.

The Play listing is free with a single non-consumable purchase. That is a set
of promises, and each one has a test here because each one is easy to break:

- **Play build only.** The GitHub / Downloader APK — what most of the pack's
  users install — keeps every wallpaper and every loop free. Gating is
  `Premium.GATED = Distribution.PLAY`, never a plain `true`.
- **The free set is a decision, not an accident.** Three series are free and
  must exist in the manifest; every other series and every loop is premium.
  A new series therefore has to be placed deliberately.
- **One product, non-consumable, bought once.** Product id is a constant; no
  consumption anywhere; the purchase is acknowledged (Play refunds an
  unacknowledged purchase after three days).
- **The gate is where the art is spent.** The grid badge, the grid press, the
  bulk selection and the preview's Set/Save all consult the same rule.
- **Nothing is gated by hiding it.** A gated tile still shows its thumb; a
  locked press explains the purchase instead of failing silently.

Run: `python3 tests/test_premium_purchase.py`
"""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KT = ROOT / "app/src/main/java/tv/corebuilds/iconpack"
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
PLAY_MANIFEST = ROOT / "app/src/play/AndroidManifest.xml"


def read(p):
    return Path(p).read_text(encoding="utf-8")


class PremiumRules(unittest.TestCase):
    def test_gating_is_the_play_build_only(self):
        src = read(KT / "Premium.kt")
        self.assertIn("val GATED: Boolean = Distribution.PLAY", src)
        # A plain `true` here would paywall the sideload build, which is what
        # most installs are and what the owner never agreed to charge for.
        self.assertNotRegex(src, r"GATED: Boolean = true")
        self.assertIn("GATED && !unlocked(context) && !isFree(wallpaper)", src)

    def test_free_series_exist_in_the_manifest_and_three_is_the_floor(self):
        src = read(KT / "Premium.kt")
        block = src.split("val FREE_SERIES: Set<String> = setOf(", 1)[1].split(")", 1)[0]
        free = set(re.findall(r'"([^"]+)"', block))
        self.assertEqual(free, {"series-2-motion", "series-3-horizons", "series-6-circuit-core"})
        catalog = json.loads(read(ROOT / "app/src/main/assets/manifest/wallpapers.json"))
        present = {w["series"] for w in catalog["wallpapers"]}
        self.assertTrue(free <= present, f"free series missing from the manifest: {free - present}")
        # At least a fifth of the catalogue stays free: a pack that gives away
        # nothing is a pack nobody can evaluate before paying.
        free_walls = [w for w in catalog["wallpapers"] if w["series"] in free]
        self.assertGreaterEqual(len(free_walls) / len(catalog["wallpapers"]), 0.2)

    def test_every_loop_is_premium(self):
        src = read(KT / "Premium.kt")
        body = src.split("fun isFree(wallpaper: Wallpaper): Boolean = when {", 1)[1]
        self.assertIn("wallpaper.isLive -> false", body)

    def test_product_is_one_non_consumable_and_is_acknowledged(self):
        premium = read(KT / "Premium.kt")
        self.assertIn('const val PRODUCT = "premium_unlock"', premium)
        billing = read(KT / "Billing.kt")
        self.assertIn("BillingClient.ProductType.INAPP", billing)
        self.assertIn("acknowledgePurchase", billing)
        self.assertIn("AcknowledgePurchaseParams", billing)
        # Non-consumable: consuming it would delete the entitlement and make
        # the app charge twice on the next launch.
        self.assertNotIn("consumeAsync", billing)
        self.assertNotIn("ConsumeParams", billing)

    def test_entitlement_is_cached_so_offline_still_works(self):
        prefs = read(KT / "Prefs.kt")
        self.assertIn('const val KEY_PREMIUM_OWNED = "premium_owned"', prefs)
        self.assertIn("fun premiumOwned(", prefs)
        self.assertIn("fun setPremiumOwned(", prefs)
        billing = read(KT / "Billing.kt")
        self.assertIn("Prefs.setPremiumOwned", billing)
        # A Play hiccup on query must not revoke a purchase already cached.
        self.assertIn("if (Prefs.premiumOwned(ctx)) State.OWNED else State.NOT_OWNED", billing)

    def test_billing_permission_is_play_only(self):
        self.assertIn("com.android.vending.BILLING", read(PLAY_MANIFEST))
        self.assertNotIn("com.android.vending.BILLING", read(MANIFEST))


class PremiumGates(unittest.TestCase):
    def test_grid_tile_badge_and_press_share_one_rule(self):
        adapter = read(KT / "WallpaperAdapter.kt")
        self.assertIn("val locked = Premium.needsPurchase(holder.itemView.context, item)", adapter)
        self.assertIn("holder.premiumBadge.visibility = if (locked) View.VISIBLE else View.GONE", adapter)
        press = adapter.split("holder.itemView.setOnClickListener {", 1)[1].split("}", 1)[0]
        self.assertIn("Premium.needsPurchase(holder.itemView.context, item) -> onLocked(item)", press)
        # Bulk save is the export list; it may not carry a wall nobody bought.
        self.assertIn("selected.addAll(items.filterNot { needsPurchase(it) }.map { it.cacheName })", adapter)
        self.assertIn("items.filter { selected.contains(it.cacheName) && !needsPurchase(it) }", adapter)

    def test_preview_set_and_save_are_gated(self):
        preview = read(KT / "WallpaperPreviewActivity.kt")
        self.assertIn("private fun lockedHere(): Boolean", preview)
        apply = preview.split("private fun applyNow() {", 1)[1].split("val file", 1)[0]
        save = preview.split("private fun saveNow() {", 1)[1].split("val file", 1)[0]
        for block, name in ((apply, "Set"), (save, "Save")):
            self.assertIn("if (lockedHere()) {", block, name)
            self.assertIn("openPremium()", block, name)

    def test_the_purchase_screen_exists_and_says_what_stays_free(self):
        self.assertIn(".PremiumActivity", read(MANIFEST))
        self.assertIn('android:exported="false"', read(MANIFEST))
        strings = read(STRINGS)
        for key in ("premium_title", "premium_included_fmt", "premium_buy",
                    "premium_restore", "premium_note", "premium_free_note"):
            self.assertRegex(strings, rf'<string name="{key}">', key)
        self.assertIn("nothing renews", strings.lower())
        # The receipt is Play's, said on the screen where money changes hands.
        self.assertIn("Google Play handles the payment", strings)

    def test_the_settings_row_is_hidden_off_play(self):
        layout = read(ROOT / "app/src/main/res/layout/activity_settings.xml")
        row = layout.split('android:id="@+id/set_premium_row"', 1)[1].split(">", 1)[0]
        self.assertIn('android:visibility="gone"', row)
        settings = read(KT / "SettingsActivity.kt")
        blocked = settings.split("if (Premium.GATED) {", 1)[1]
        self.assertIn("findViewById<View>(R.id.set_premium_row).visibility = View.VISIBLE", blocked)
        self.assertIn("PremiumActivity::class.java", blocked)

    def test_the_screen_counts_its_goods_from_the_catalogue(self):
        # A hand-typed "96 wallpapers" goes stale the next time a series ships
        # and would misdescribe what the purchase buys.
        activity = read(KT / "PremiumActivity.kt")
        self.assertIn("val premiumWalls = walls.count { !Premium.isFree(it) }", activity)
        self.assertIn("LiveLoop.LOOPS.size", activity)
        self.assertNotRegex(activity, r"\b(132|96|48)\b")


if __name__ == "__main__":
    unittest.main()
