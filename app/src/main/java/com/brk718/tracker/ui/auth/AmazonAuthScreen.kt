package com.brk718.tracker.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.brk718.tracker.data.local.AmazonSessionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AmazonAuthScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: AmazonAuthViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope() // Mantener scope por si acaso, aunque VM maneja coroutines

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conectar Amazon") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Botón manual por si la detección falla
                    IconButton(onClick = {
                        val url = "https://www.amazon.com/gp/your-account" // URL genérica para guardar cookies actuales
                        viewModel.saveCookies(url)
                        onLoginSuccess()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Listo")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                checkLogin(url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                checkLogin(url)
                            }
                            
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                checkLogin(url)
                            }
                            
                            private fun checkLogin(url: String?) {
                                if (url == null) return
                                // Detectar redirección a distintas variantes de "Mi Cuenta" o "Mis Pedidos"
                                if (url.contains("your-account") || 
                                    url.contains("order-history") ||
                                    url.contains("css/order-history") ||
                                    url.contains("/gp/aw/ya")) {
                                    
                                    // Guardamos las cookies de la URL actual
                                    viewModel.saveCookies(url)
                                    onLoginSuccess()
                                }
                            }
                        }
                        
                        loadUrl("https://www.amazon.com/gp/sign-in.html")
                    }
                }
            )
        }
    }
}
