package com.brk718.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.brk718.tracker.ui.App
import com.brk718.tracker.ui.ads.AdManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var adManager: AdManager

    // shipmentId a abrir directamente (desde notificación)
    private var initialShipmentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initialShipmentId = intent?.getStringExtra("shipmentId")
        setContent {
            App(adManager = adManager, initialShipmentId = initialShipmentId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Cuando la app ya está abierta y llega una notificación
        intent.getStringExtra("shipmentId")?.let { id ->
            initialShipmentId = id
            // Recrear para que App() reciba el nuevo initialShipmentId
            recreate()
        }
    }
}
