package com.brk718.tracker.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.brk718.tracker.data.local.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "BillingRepository"
        // ID del producto de suscripción anual — crear en Google Play Console antes de publicar
        const val PRODUCT_PREMIUM_ANNUAL = "premium_annual"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "Compra cancelada por el usuario")
                _billingState.value = BillingState.Idle
            }
            else -> {
                Log.w(TAG, "Error en compra: ${billingResult.debugMessage}")
                _billingState.value = BillingState.Error(billingResult.debugMessage)
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    init {
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient conectado")
                    queryProductDetails()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "BillingClient desconectado — reintentando")
                connect()
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PREMIUM_ANNUAL)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = productDetailsList.firstOrNull()
                _productDetails.value = details
                Log.d(TAG, "Producto encontrado: ${details?.title}")
            } else {
                Log.w(TAG, "No se pudo obtener detalles: ${billingResult.debugMessage}")
            }
        }
    }

    /** Lanza el flujo de compra de Google Play */
    fun purchaseSubscription(activity: Activity) {
        val details = _productDetails.value ?: run {
            Log.w(TAG, "ProductDetails no disponible todavía")
            _billingState.value = BillingState.Error("Producto no disponible. Intenta más tarde.")
            return
        }

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            Log.w(TAG, "OfferToken no encontrado")
            _billingState.value = BillingState.Error("Error al obtener oferta. Intenta más tarde.")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        _billingState.value = BillingState.Loading
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    /** Restaura compras previas (útil al reinstalar la app) */
    fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryPurchasesAsync falló: ${billingResult.debugMessage}")
                return@queryPurchasesAsync
            }
            val activePurchase = purchases.firstOrNull {
                it.products.contains(PRODUCT_PREMIUM_ANNUAL) &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (activePurchase != null) {
                Log.d(TAG, "Suscripción activa encontrada — activando premium")
                scope.launch {
                    acknowledgePurchaseIfNeeded(activePurchase)
                    prefsRepo.setIsPremium(true)
                }
            } else {
                Log.d(TAG, "Sin suscripción activa")
                scope.launch { prefsRepo.setIsPremium(false) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            scope.launch {
                acknowledgePurchaseIfNeeded(purchase)
                prefsRepo.setIsPremium(true)
                _billingState.value = BillingState.PurchaseSuccess
                Log.d(TAG, "¡Compra completada! Premium activado.")
            }
        }
    }

    private suspend fun acknowledgePurchaseIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                Log.d(TAG, "acknowledgePurchase: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    /** Precio formateado del plan anual, ej: "$2.99"
     *  Omite la fase de prueba gratuita (priceAmountMicros == 0) y devuelve el precio real. */
    fun getPriceText(): String {
        return _productDetails.value
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull { it.priceAmountMicros > 0 }
            ?.formattedPrice
            ?: "—"
    }
}

sealed class BillingState {
    data object Idle : BillingState()
    data object Loading : BillingState()
    data object PurchaseSuccess : BillingState()
    data class Error(val message: String) : BillingState()
}
