package tv.corebuilds.iconpack

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
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
 * Core Builds Plus over Google Play Billing (play build type only).
 *
 * One managed product, [PRODUCT_ID], bought once and never consumed. Owning it
 * stores the purchase token on this TV; the auditor sends that token with an
 * icon request, and the request broker asks Google whether it is a real,
 * unrefunded purchase before it labels the issue priority. The app itself
 * grants nothing on the token's word, so a copied token buys nothing either.
 *
 * Every app start ([sync]) and every Support visit re-read Play's own list
 * of purchases, so a refund or a reinstall settles the stored token without
 * anyone opening Support.
 */
object PlusStore {
    const val AVAILABLE = true

    /** The one-time product in Play Console (Monetize > Products > In-app products). */
    const val PRODUCT_ID = "core_builds_plus"

    private const val PREFS = "plus"
    private const val KEY_TOKEN = "purchase_token"

    /** The token of an owned, confirmed Plus purchase, or null. */
    fun purchaseToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    private fun storeToken(context: Context, token: String?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (token == null) edit.remove(KEY_TOKEN) else edit.putString(KEY_TOKEN, token)
        edit.apply()
    }

    fun session(activity: Activity, onState: (PlusState) -> Unit): PlusSession =
        BillingSession(activity, onState).also { it.start() }

    @Volatile private var synced = false

    /**
     * Once per process, quietly: ask Play what this account owns and make the
     * stored token agree. A purchase made on another TV, one that cleared
     * after being pending, and a refund all land here. Any failure leaves the
     * token as it was; Support's own refresh is the fallback.
     */
    fun sync(context: Context) {
        if (synced) return
        synced = true
        val app = context.applicationContext
        val client = BillingClient.newBuilder(app)
            .setListener { _, _ -> }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingResponseCode.OK) {
                    client.endConnection()
                    return
                }
                val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
                client.queryPurchasesAsync(params) { answer, purchases ->
                    if (answer.responseCode != BillingResponseCode.OK) {
                        client.endConnection()
                        return@queryPurchasesAsync
                    }
                    val plus = purchases.firstOrNull {
                        PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    storeToken(app, plus?.purchaseToken)
                    if (plus != null && !plus.isAcknowledged) {
                        val ack = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(plus.purchaseToken).build()
                        client.acknowledgePurchase(ack) { client.endConnection() }
                    } else {
                        client.endConnection()
                    }
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private class BillingSession(
        private val activity: Activity,
        private val onState: (PlusState) -> Unit,
    ) : PlusSession, PurchasesUpdatedListener {

        private val main = Handler(Looper.getMainLooper())
        private val client: BillingClient = BillingClient.newBuilder(activity.applicationContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        private var details: ProductDetails? = null
        @Volatile private var closed = false

        fun start() {
            emit(PlusState.Loading)
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingResponseCode.OK) refresh() else fail(result)
                }

                // enableAutoServiceReconnection reconnects on the next call.
                override fun onBillingServiceDisconnected() = Unit
            })
        }

        /** Play's list of this account's purchases is the truth; the stored token follows it. */
        private fun refresh() {
            val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
            client.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingResponseCode.OK) {
                    fail(result)
                    return@queryPurchasesAsync
                }
                val plus = purchases.firstOrNull { PRODUCT_ID in it.products }
                if (plus == null) {
                    // Never bought, or refunded since: nothing to send with requests.
                    storeToken(activity, null)
                    loadPrice()
                } else {
                    apply(plus)
                }
            }
        }

        private fun apply(purchase: Purchase) {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    storeToken(activity, purchase.purchaseToken)
                    // Unacknowledged purchases are refunded after three days.
                    if (!purchase.isAcknowledged) {
                        val ack = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken).build()
                        client.acknowledgePurchase(ack) { }
                    }
                    emit(PlusState.Owned)
                }
                Purchase.PurchaseState.PENDING -> emit(PlusState.Pending)
                else -> loadPrice()
            }
        }

        private fun loadPrice() {
            val query = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(ProductType.INAPP)
                .build()
            val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(query)).build()
            client.queryProductDetailsAsync(params) { result, found ->
                if (result.responseCode != BillingResponseCode.OK) {
                    fail(result)
                    return@queryProductDetailsAsync
                }
                val product = found.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
                val price = product?.oneTimePurchaseOfferDetails?.formattedPrice
                details = product
                if (price == null) {
                    emit(PlusState.Unavailable("Plus is not on sale in this country"))
                } else {
                    emit(PlusState.ForSale(price))
                }
            }
        }

        override fun buy() {
            val product = details ?: return
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product).build()))
                .build()
            val result = client.launchBillingFlow(activity, params)
            if (result.responseCode != BillingResponseCode.OK) fail(result)
        }

        override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
            when (result.responseCode) {
                BillingResponseCode.OK ->
                    purchases?.firstOrNull { PRODUCT_ID in it.products }?.let { apply(it) }
                // Owned on this account already (another TV): read it back.
                BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                // Backed out of Google Play's sheet: the screen is as it was.
                BillingResponseCode.USER_CANCELED -> Unit
                else -> fail(result)
            }
        }

        private fun fail(result: BillingResult) {
            val reason = result.debugMessage.takeIf { it.isNotBlank() }
                ?: "Google Play answered ${result.responseCode}"
            emit(PlusState.Unavailable(reason))
        }

        /** Billing answers on its own threads; the screen hears on the main one. */
        private fun emit(state: PlusState) {
            main.post { if (!closed) onState(state) }
        }

        override fun close() {
            closed = true
            client.endConnection()
        }
    }
}
