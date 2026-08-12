package com.example.matterhome.camera

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun MatterQrCameraPreview(
    torchEnabled: Boolean,
    onQrCode: (String) -> Unit,
    onCameraReady: (hasFlash: Boolean) -> Unit,
    onCameraError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val scanner = remember {
        val options =
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        BarcodeScanning.getClient(options)
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    AndroidView(factory = { previewView }, modifier = modifier)

    LaunchedEffect(torchEnabled, camera) {
        camera?.cameraControl?.enableTorch(torchEnabled)
    }

    DisposableEffect(lifecycleOwner, previewView, scanner) {
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val disposed = AtomicBoolean(false)
        val analyzing = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null

        providerFuture.addListener(
            {
                if (disposed.get()) return@addListener
                runCatching {
                    provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    analysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage == null || !analyzing.compareAndSet(false, true)) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    @SuppressLint("UnsafeOptInUsageError")
                                    val input =
                                        InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees,
                                        )
                                    scan(scanner, input, onQrCode, onCameraError) {
                                        analyzing.set(false)
                                        imageProxy.close()
                                    }
                                }
                            }
                    provider?.unbindAll()
                    camera =
                        provider?.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    onCameraReady(camera?.cameraInfo?.hasFlashUnit() == true)
                }.onFailure(onCameraError)
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed.set(true)
            analysis?.clearAnalyzer()
            provider?.unbindAll()
            camera = null
            analyzerExecutor.shutdown()
            scanner.close()
        }
    }
}

private fun scan(
    scanner: BarcodeScanner,
    image: InputImage,
    onQrCode: (String) -> Unit,
    onError: (Throwable) -> Unit,
    onComplete: () -> Unit,
) {
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstNotNullOfOrNull { it.rawValue?.trim() }?.let(onQrCode)
        }
        .addOnFailureListener { error ->
            Log.w(TAG, "Unable to analyze camera frame", error)
            onError(error)
        }
        .addOnCompleteListener { onComplete() }
}

private const val TAG = "MatterQrScanner"
