package com.brk718.tracker.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.stringResource
import com.brk718.tracker.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
                title = { Text(stringResource(R.string.amazon_auth_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.amazon_auth_back))
                    }
                },
                actions = {
                    // Botón manual por si la detección falla
                    IconButton(onClick = {
                        val url = "https://www.amazon.com/gp/your-account" // URL genérica para guardar cookies actuales
                        viewModel.saveCookies(url)
                        onLoginSuccess()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.amazon_auth_done))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            var isLoading by remember { mutableStateOf(true) }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        
                        // Desactivar aceleración de hardware para evitar pantallazo blanco en Samsung/Adreno
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val url = request?.url?.toString()
                                if (checkLogin(url)) return true
                                return super.shouldOverrideUrlLoading(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                checkLogin(url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                checkLogin(url)
                            }
                            
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                checkLogin(url)
                            }
                            
                            private fun checkLogin(url: String?): Boolean {
                                if (url == null) return false
                                val lowerUrl = url.lowercase()
                                if (lowerUrl.contains("your-account") || 
                                    lowerUrl.contains("order-history") ||
                                    lowerUrl.contains("css/order-history") ||
                                    lowerUrl.contains("/gp/aw/ya")) {
                                    
                                    viewModel.saveCookies(url)
                                    onLoginSuccess()
                                    return true
                                }
                                return false
                            }
                        }
                        
                        loadUrl("https://www.amazon.com/gp/sign-in.html")
                    }
                }
            )
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
