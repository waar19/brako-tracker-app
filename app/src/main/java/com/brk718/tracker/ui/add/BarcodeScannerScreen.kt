package com.brk718.tracker.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Pantalla de escaneo de código de barras / QR.
 * Usa CameraX + ML Kit para detectar números de rastreo automáticamente.
 *
 * @param onBarcodeDetected Callback con el valor del código detectado.
 * @param onBack            Callback para cerrar la pantalla sin resultado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onBarcodeDetected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var isScanning by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Escanear código",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                !hasCameraPermission -> {
                    // ── Sin permiso de cámara ──────────────────────────────
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            "Se necesita acceso a la cámara para escanear códigos de barras.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Conceder permiso")
                        }
                    }
                }
                else -> {
                    // ── Vista de cámara ───────────────────────────────────
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        lifecycleOwner = lifecycleOwner,
                        isScanning = isScanning,
                        onBarcodeDetected = { value ->
                            if (isScanning) {
                                isScanning = false  // evitar disparar múltiples veces
                                onBarcodeDetected(value)
                            }
                        }
                    )

                    // Overlay con visor
                    ScannerOverlay()
                }
            }
        }
    }
}

// ─── Visor visual centrado ─────────────────────────────────────────────────────

@Composable
private fun ScannerOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo semitransparente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f))
        )
        // Marco de escaneo centrado
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(Color.Transparent)
        )
        // Instrucción debajo del visor
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 300.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Apunta la cámara al código de barras",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

// ─── Vista de cámara con análisis de imágenes ─────────────────────────────────

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isScanning: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (!isScanning) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstOrNull { barcode ->
                                    // Aceptar cualquier tipo que tenga texto
                                    barcode.rawValue != null &&
                                        (barcode.format == Barcode.FORMAT_CODE_128 ||
                                         barcode.format == Barcode.FORMAT_CODE_39 ||
                                         barcode.format == Barcode.FORMAT_CODE_93 ||
                                         barcode.format == Barcode.FORMAT_EAN_13 ||
                                         barcode.format == Barcode.FORMAT_EAN_8 ||
                                         barcode.format == Barcode.FORMAT_QR_CODE ||
                                         barcode.format == Barcode.FORMAT_DATA_MATRIX ||
                                         barcode.format == Barcode.FORMAT_ITF ||
                                         barcode.format == Barcode.FORMAT_PDF417 ||
                                         barcode.format == Barcode.FORMAT_AZTEC ||
                                         barcode.format == Barcode.FORMAT_UPC_A ||
                                         barcode.format == Barcode.FORMAT_UPC_E)
                                }?.rawValue?.let { value ->
                                    onBarcodeDetected(value)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    android.util.Log.e("BarcodeScanner", "Error binding camera", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
