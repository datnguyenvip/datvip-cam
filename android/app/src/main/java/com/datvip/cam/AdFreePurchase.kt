package com.datvip.cam

import android.app.Activity
import android.content.Context
import android.util.Base64
import com.android.billingclient.api.*
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** A non-consumable entitlement: pending purchases never unlock it; no local paid toggle. */
class AdFreePurchase(private val activity: Activity, private val changed: () -> Unit) : PurchasesUpdatedListener {
    companion object { const val PRODUCT = "remove_ads" }
    private val preferences = activity.getSharedPreferences("billing", Context.MODE_PRIVATE)
    private val client = BillingClient.newBuilder(activity).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    var owned = false; private set
    var checked = false; private set
    var details: ProductDetails? = null; private set
    var message = ""; private set
    var busy = false; private set
    private fun ui(action: () -> Unit) = activity.runOnUiThread { action(); changed() }
    private fun verified(purchase: Purchase): Boolean = runCatching {
        val publicKey = activity.getString(R.string.play_license_key)
        if (publicKey.isBlank() || purchase.packageName != activity.packageName || PRODUCT !in purchase.products || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT)))
        Signature.getInstance("SHA1withRSA").run { initVerify(key); update(purchase.originalJson.toByteArray(Charsets.UTF_8)); verify(Base64.decode(purchase.signature, Base64.DEFAULT)) }
    }.getOrDefault(false)
    fun start() {
        val json = preferences.getString("receipt", null)
        val signature = preferences.getString("signature", null)
        if (json != null && signature != null) owned = runCatching { verified(Purchase(json, signature)) }.getOrDefault(false)
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { if (result.responseCode == BillingClient.BillingResponseCode.OK) { restore(false); loadProduct() } else ui { message = result.debugMessage } }
            override fun onBillingServiceDisconnected() { ui { busy = false } }
        })
    }
    private fun loadProduct() {
        val query = QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.INAPP).build())).build()
        client.queryProductDetailsAsync(query) { result, products -> ui { details = products.productDetailsList.firstOrNull { it.productId == PRODUCT }; if (result.responseCode != BillingClient.BillingResponseCode.OK) message = result.debugMessage } }
    }
    fun restore(userRequested: Boolean = true) {
        if (!client.isReady) { ui { message = Language.text(activity, "Cửa hàng chưa sẵn sàng") }; return }
        ui { busy = true }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val valid = purchases.firstOrNull { verified(it) }
                ui {
                    owned = valid != null; checked = true; busy = false
                    if (valid == null) preferences.edit().remove("receipt").remove("signature").apply()
                    else preferences.edit().putString("receipt", valid.originalJson).putString("signature", valid.signature).apply()
                    if (userRequested) message = Language.text(activity, if (owned) "Đã khôi phục mua hàng" else "Không tìm thấy giao dịch bỏ quảng cáo")
                }
                valid?.let(::acknowledge)
            } else ui { busy = false; message = result.debugMessage }
        }
    }
    fun price() = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
    fun buy() {
        if (owned || busy) return
        val product = details
        val offer = product?.oneTimePurchaseOfferDetailsList?.firstOrNull()
        if (product == null || offer == null || activity.getString(R.string.play_license_key).isBlank()) { ui { message = Language.text(activity, "Sản phẩm chưa được cửa hàng cung cấp") }; return }
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.offerToken).build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item)).build())
        ui { busy = result.responseCode == BillingClient.BillingResponseCode.OK; if (!busy) message = result.debugMessage }
    }
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        ui { busy = false }
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }) ui { message = Language.text(activity, "Giao dịch đang chờ thanh toán") }
            restore(false)
        } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) restore()
        else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) ui { message = result.debugMessage }
    }
    private fun acknowledge(purchase: Purchase) {
        if (!purchase.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) ui { message = Language.text(activity, "Cửa hàng chưa xác nhận giao dịch. Hãy khôi phục mua hàng khi có mạng.") }
        }
    }
    fun close() = client.endConnection()
}
