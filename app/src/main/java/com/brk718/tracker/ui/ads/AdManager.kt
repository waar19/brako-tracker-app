package com.brk718.tracker.ui.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AdManager"
        private const val BANNER_AD_UNIT_ID       = "ca-app-pub-5629360571814219/4080460471"
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5629360571814219/4620775704"
        // Mostrar interstitial cada N aperturas de DetailScreen
        private const val INTERSTITIAL_FREQUENCY = 3
    }

    private var interstitialAd: InterstitialAd? = null
    private var detailOpenCount = 0

    init {
        loadInterstitial()
    }

    private fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial cargado")
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitial() // precargar el siguiente
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial falló al cargar: ${error.message}")
                    interstitialAd = null
                }
            }
        )
    }

    /**
     * Llamar cada vez que el usuario abre DetailScreen.
     * Muestra el interstitial cada INTERSTITIAL_FREQUENCY veces, solo si no es premium.
     */
    fun onDetailScreenOpened(activity: Activity, isPremium: Boolean) {
        if (isPremium) return
        detailOpenCount++
        Log.d(TAG, "Detail abierto #$detailOpenCount (umbral: $INTERSTITIAL_FREQUENCY)")
        if (detailOpenCount >= INTERSTITIAL_FREQUENCY) {
            showInterstitial(activity)
            detailOpenCount = 0
        }
    }

    private fun showInterstitial(activity: Activity) {
        val ad = interstitialAd
        if (ad != null) {
            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial no listo todavía")
        }
    }

    /**
     * Devuelve un AdView de banner configurado y listo para usar en un AndroidView.
     * Crear una nueva instancia cada vez (no reutilizar entre composables).
     */
    fun createBannerAdView(): AdView {
        return AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
            adListener = object : AdListener() {
                override fun onAdLoaded() { Log.d(TAG, "Banner cargado") }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Banner falló: ${error.message}")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
}
