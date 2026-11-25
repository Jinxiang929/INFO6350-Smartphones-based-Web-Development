package com.example.qrcode.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewOutlineProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun QrCameraSection(
    scannedText: String?,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val cameraPermission = Manifest.permission.CAMERA
    var showCamera by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showCamera = granted
        permissionDenied = !granted
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (showCamera) {
                    showCamera = false
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        cameraPermission
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        permissionDenied = false
                        showCamera = true
                    } else {
                        requestPermission.launch(cameraPermission)
                    }
                }
            }
        ) {
            Text(if (showCamera) "Stop Camera" else "Scan with Camera")
        }

        if (permissionDenied) {
            Text("Camera access is required for live QR scanning.")
        }

        scannedText?.let { text ->
            Text("Scan result: $text")
        }

        if (showCamera) {
            QrCameraPreview(onQrScanned = onQrScanned)
        }
    }
}

@Composable
private fun QrCameraPreview(onQrScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnQrScanned by rememberUpdatedState(onQrScanned)
    val preview = remember { Preview.Builder().build() }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context, lifecycleOwner, preview) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val analyzer = MlKitAnalyzer(
            listOf(scanner),
            ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
            mainExecutor
        ) { result ->
            if (!disposed) {
                val text = result.getValue(scanner)
                    ?.firstNotNullOfOrNull { barcode -> barcode.rawValue }

                if (text != null) {
                    latestOnQrScanned(text)
                }
            }
        }

        imageAnalysis.setAnalyzer(mainExecutor, analyzer)

        providerFuture.addListener(
            {
                if (!disposed) {
                    try {
                        val provider = providerFuture.get()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        cameraProvider = provider
                    } catch (error: Exception) {
                        Log.e("QrCamera", "Could not start QR scanning", error)
                        cameraError = "Could not open the back camera."
                    }
                }
            },
            mainExecutor
        )

        onDispose {
            disposed = true
            imageAnalysis.clearAnalyzer()
            cameraProvider?.unbind(preview, imageAnalysis)
            scanner.close()
        }
    }

    val message = cameraError
    if (message != null) {
        Text(message)
    } else {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    outlineProvider = ViewOutlineProvider.BOUNDS
                    clipToOutline = true
                    preview.setSurfaceProvider(surfaceProvider)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )
    }
}