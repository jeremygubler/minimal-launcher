package dev.minimal.launcher.pro

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Play-Version: Pro wird per Einmalkauf „pro_lifetime“ über Google Play Billing freigeschaltet.
 * Der letzte bekannte Status wird lokal gemerkt, damit Pro auch offline aktiv bleibt.
 */
object Pro {
    const val PRODUCT_ID = "pro_lifetime"

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var appContext: Context? = null
    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    fun init(context: Context) {
        if (client != null) return
        val app = context.applicationContext
        appContext = app
        _isPro.value = app.getSharedPreferences("pro", Context.MODE_PRIVATE).getBoolean("pro", false)
        client = BillingClient.newBuilder(app)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.forEach(::handle)
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        if (c.isReady) {
            refresh()
            return
        }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun refresh() {
        val c = client ?: return
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                setPro(purchases.any { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED })
                purchases.forEach(::handle)
            }
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { _, list ->
            details = list.firstOrNull()
            _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    private fun handle(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        setPro(true)
        if (!purchase.isAcknowledged) {
            client?.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            ) { }
        }
    }

    private fun setPro(value: Boolean) {
        _isPro.value = value
        appContext?.getSharedPreferences("pro", Context.MODE_PRIVATE)?.edit()?.putBoolean("pro", value)?.apply()
    }

    fun purchase(activity: Activity) {
        val d = details
        if (d == null) {
            Toast.makeText(activity, "Pro ist gerade nicht verfügbar – bitte später erneut versuchen", Toast.LENGTH_SHORT).show()
            connect()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        client?.launchBillingFlow(activity, params)
    }

    /** Käufe erneut abfragen (z. B. nach Neuinstallation). */
    fun restore() {
        connect()
        appContext?.let { Toast.makeText(it, "Käufe werden geprüft …", Toast.LENGTH_SHORT).show() }
    }
}
