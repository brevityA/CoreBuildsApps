package tv.corebuilds.iconpack

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * The one Play Billing connection this app keeps, for one product.
 *
 * [Premium.PRODUCT] is a non-consumable, so the whole surface is small: connect
 * once, ask Play what this account owns, buy it, acknowledge it, and never
 * consume it. Play re-verifies the purchase server-side; the client's job is to
 * report what Play says and cache the answer in [Prefs] so the app works
 * offline.
 *
 * Every entry point is a user action — opening the Premium screen or pressing
 * its button. Nothing here dials out at app start, so About's list of what this
 * app sends stays true: billing traffic goes to Google Play, not to Core
 * Builds, and it happens because the user pressed something.
 */
object Billing {

    enum class State {
        /** No connection yet, or Play is not installed (most Android TV boxes). */
        UNAVAILABLE,
        CONNECTING,
        /** Connected and verified: the purchase is owned. */
        OWNED,
        /** Connected, Play has no record of this account owning it. */
        NOT_OWNED,
    }

    fun interface Listener {
        /** [price] is Play's own localised price string, or "" when unknown. */
        fun onState(state: State, price: String, reason: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var client: BillingClient? = null
    private var connected = false
    private var price = ""
    private var details: ProductDetails? = null
    private val listeners = mutableListOf<Listener>()

    /**
     * The application context captured on [refresh]. The purchase listener has
     * no Context of its own and must be able to cache an entitlement that
     * arrives asynchronously.
     */
    private var appContext: Context? = null

    /** What Play last told us, so a late callback cannot contradict it. */
    private var lastState: State = State.NOT_OWNED

    private val purchasesUpdated = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull { owns(it) }
                if (purchase == null) {
                    notify(State.NOT_OWNED, "", "")
                } else {
                    acknowledgeIfNeeded(purchase)
                    appContext?.let { Prefs.setPremiumOwned(it, true) }
                    notify(State.OWNED, price, "")
                }
            }
            // The user closed the Play sheet: not an error, just not a purchase.
            BillingClient.BillingResponseCode.USER_CANCELED ->
                notify(State.NOT_OWNED, price, "")
            // A pending purchase (cash, some carriers) resolves later and lands
            // here as PENDING, then arrives as a purchase update / query hit
            // once Play clears it. Nothing to unlock yet.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                appContext?.let { Prefs.setPremiumOwned(it, true) }
                notify(State.OWNED, price, "")
            }
            else -> notify(State.UNAVAILABLE, price, result.debugMessage.orEmpty())
        }
    }

    /**
     * Connect (if needed), verify what Play says, and report. Called from the
     * Premium screen and from Settings when it opens, so a restored purchase
     * unlocks without the user having to press anything twice.
     */
    fun refresh(context: Context, listener: Listener) {
        listen(listener)
        if (!Premium.GATED) {
            // Only the Play build has anything to verify.
            notify(State.OWNED, "", "")
            return
        }
        val ctx = context.applicationContext
        appContext = ctx
        if (client == null) {
            client = BillingClient.newBuilder(ctx)
                .setListener(purchasesUpdated)
                // Prepaid plans and cash purchases are pending for a while;
                // accepting them here means a TV with neither a card nor a
                // Play balance can still be bought for, and it arrives later.
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .build()
        }
        val c = client ?: return
        if (connected && c.isReady) {
            queryProduct(c)
            queryOwned(c, ctx)
            return
        }
        notify(State.CONNECTING, price, "")
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (!connected) {
                    // Most Android TV boxes have no Play Store at all. Say so
                    // instead of spinning: the caller names the reason.
                    notify(State.UNAVAILABLE, "", result.debugMessage.orEmpty())
                    return
                }
                queryProduct(c)
                queryOwned(c, ctx)
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    /** Open Play's purchase sheet for [Premium.PRODUCT]. */
    fun buy(activity: Activity, listener: Listener) {
        listen(listener)
        val c = client
        val product = details
        if (c == null || !c.isReady || product == null) {
            notify(State.UNAVAILABLE, price, "")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build()
                )
            )
            .build()
        val result = c.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            notify(State.UNAVAILABLE, price, result.debugMessage.orEmpty())
        }
    }

    /** Stop listening when a screen goes away. */
    fun forget(listener: Listener) {
        listeners.remove(listener)
    }

    fun close() {
        client?.endConnection()
        client = null
        connected = false
    }

    private fun queryProduct(c: BillingClient) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(Premium.PRODUCT)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        c.queryProductDetailsAsync(params) { result, productDetails ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = productDetails.firstOrNull()
                price = details?.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()
                // Re-state whatever Play last said, price included: this
                // callback and the purchases query race, and reporting a
                // default state here would flip a paid screen back to locked.
                notify(lastState, price, "")
            }
            // A missing product is a Play Console setup problem, not a client
            // one; the screen says "not available yet" and the runbook names
            // the fix. Nothing to retry here.
        }
    }

    private fun queryOwned(c: BillingClient, ctx: Context) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        c.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // Keep the cached answer: a Play hiccup must not take the
                // purchase away from someone who already owns it.
                notify(if (Prefs.premiumOwned(ctx)) State.OWNED else State.NOT_OWNED,
                       price, result.debugMessage.orEmpty())
                return@queryPurchasesAsync
            }
            val owned = purchases.firstOrNull { owns(it) }
            Prefs.setPremiumOwned(ctx, owned != null)
            owned?.let { acknowledgeIfNeeded(it) }
            notify(if (owned != null) State.OWNED else State.NOT_OWNED, price, "")
        }
    }

    /**
     * A non-consumable must be acknowledged within three days or Play refunds
     * it. Play's own clients usually acknowledge, but a purchase completed
     * while the app was killed can arrive here unacknowledged, and it is the
     * one call that must never be skipped.
     */
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client?.acknowledgePurchase(params) { /* verification is Play's record */ }
    }

    private fun owns(purchase: Purchase): Boolean =
        purchase.products.contains(Premium.PRODUCT)

    private fun listen(listener: Listener) {
        if (listeners.none { it === listener }) listeners.add(listener)
    }

    private fun notify(state: State, priceText: String, reason: String) {
        lastState = state
        main.post {
            for (l in listeners.toList()) l.onState(state, priceText, reason)
        }
    }
}
