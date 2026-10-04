package dev.glowcow.stackd.ui.scan

import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.glowcow.stackd.barcode.MlKitFormats
import dev.glowcow.stackd.barcode.ScannedCode
import kotlinx.coroutines.awaitCancellation

/** Back camera with live barcode detection; [capture] is exposed for still shots. */
@Composable
fun CameraPreview(
    torch: Boolean,
    detect: Boolean,
    capture: ImageCapture,
    onCode: (ScannedCode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val detectState by rememberUpdatedState(detect)
    val onCodeState by rememberUpdatedState(onCode)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val scanner = remember { MlKitFormats.newScanner() }
    DisposableEffect(scanner) { onDispose { scanner.close() } }

    val preview = remember { Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } } }
    val analysis = remember {
        val executor = ContextCompat.getMainExecutor(context)
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build(),
            )
            .build()
            .apply {
                setAnalyzer(executor, MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
                    if (!detectState) return@MlKitAnalyzer
                    result.getValue(scanner)?.firstNotNullOfOrNull(MlKitFormats::toScanned)?.let { onCodeState(it) }
                })
            }
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, capture)
            awaitCancellation()
        } finally {
            provider.unbindAll()
            camera = null
        }
    }

    LaunchedEffect(camera, torch) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch) }
    }

    // Embedded in the view tree, so the preview is clipped and moves with the screen transitions.
    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED) }
}
