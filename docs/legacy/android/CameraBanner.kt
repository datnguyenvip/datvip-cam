package com.datvip.cam

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.ump.*

/** A separate 50dp slot below camera controls; never part of a saved frame. */
class CameraBanner(private val activity: Activity, private val slot: FrameLayout, private val purchase: AdFreePurchase) {
    private var ad: AdView? = null
    private var consentStarted = false
    private var consentReady = false
    private var closed = false
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    fun refresh() {
        if (closed) return
        if (purchase.owned || !purchase.checked) { ad?.destroy(); ad = null; slot.removeAllViews(); slot.visibility = View.GONE; return }
        if (!consentStarted) {
            consentStarted = true
            consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    consentReady = error == null && consent.canRequestAds(); refresh()
                }
            }, { consentReady = consent.canRequestAds(); if (consentReady) refresh() })
            return
        }
        if (!consentReady || !consent.canRequestAds() || ad != null) return
        MobileAds.setRequestConfiguration(MobileAds.getRequestConfiguration().toBuilder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .setPublisherPrivacyPersonalizationState(RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED)
            .build())
        MobileAds.initialize(activity) { activity.runOnUiThread {
            if (closed || purchase.owned || ad != null || !consent.canRequestAds()) return@runOnUiThread
            ad = AdView(activity).also { banner ->
                banner.adUnitId = activity.getString(R.string.admob_banner_id)
                banner.setAdSize(AdSize.BANNER)
                banner.adListener = object : AdListener() {
                    override fun onAdLoaded() { if (!closed && !purchase.owned) slot.visibility = View.VISIBLE }
                    override fun onAdFailedToLoad(error: LoadAdError) { slot.visibility = View.GONE }
                }
                slot.addView(banner, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
                banner.loadAd(AdRequest.Builder().build())
            }
        } }
    }
    fun privacyOptions() { UserMessagingPlatform.showPrivacyOptionsForm(activity) { consentReady = consent.canRequestAds(); ad?.destroy(); ad = null; slot.removeAllViews(); refresh() } }
    fun pause() { ad?.pause() }
    fun resume() { ad?.resume() }
    fun close() { closed = true; ad?.destroy(); ad = null }
}
