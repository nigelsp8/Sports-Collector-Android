package com.nigelspeight.sportscollector.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.nigelspeight.sportscollector.BuildConfig

/// Owns the single preloaded interstitial ad slot for the app - mirrors
/// `AudioManager`'s singleton shape. Callers never juggle ad-load state
/// themselves: `showInterstitial` always resolves its completion exactly once,
/// whether or not an ad was actually ready to show, so navigation code can
/// treat "show an ad" as a step that just finishes.
object AdManager {
    private const val TAG = "AdManager"
    private const val AD_UNIT_ID = "ca-app-pub-4025266331039040/5279031730"

    /// Physical devices that should always get Google's test ads instead of
    /// live inventory from the real `AD_UNIT_ID` above - keeps real-device
    /// testing from counting as invalid traffic against the AdMob account.
    /// Emulators are already always treated as test devices and don't need
    /// listing here. To add a physical device: run on it once, find its ID in
    /// Logcat (search for "setTestDeviceIds"), and paste it below. Only
    /// applied to debug builds.
    private val testDeviceIds: List<String> = listOf()

    private lateinit var context: Context
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var pendingCompletion: (() -> Unit)? = null

    /// Starts the Google Mobile Ads SDK and kicks off the first preload - call
    /// once, from `AppServices.init`.
    fun start(context: Context) {
        this.context = context.applicationContext
        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build(),
            )
        }
        // Initialization does disk/network work, so keep it off the main thread.
        Thread { MobileAds.initialize(this.context) }.start()
        loadInterstitial()
    }

    private fun loadInterstitial() {
        if (isLoading) return
        isLoading = true
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    ad.fullScreenContentCallback = fullScreenCallback
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    Log.d(TAG, "Failed to load interstitial: $error")
                }
            },
        )
    }

    /// Presents the preloaded interstitial over `activity` if one is ready,
    /// then calls `completion` - immediately when no ad is available, or once
    /// the presented ad is dismissed/fails. Either way, a fresh ad starts
    /// preloading for next time.
    fun showInterstitial(activity: Activity, completion: () -> Unit = {}) {
        // An ad is already on screen (e.g. a double-tapped button) - its own
        // completion will handle navigation.
        if (pendingCompletion != null) return
        val ad = interstitialAd
        if (ad == null) {
            completion()
            loadInterstitial()
            return
        }
        pendingCompletion = completion
        ad.show(activity)
    }

    private val fullScreenCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            interstitialAd = null
            resolvePendingCompletion()
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.d(TAG, "Failed to present interstitial: $error")
            interstitialAd = null
            resolvePendingCompletion()
        }
    }

    private fun resolvePendingCompletion() {
        val completion = pendingCompletion
        pendingCompletion = null
        completion?.invoke()
        loadInterstitial()
    }
}
