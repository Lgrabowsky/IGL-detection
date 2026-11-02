package io.github.lgrabowsky.igldetection.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ergebnis von [CameraController.bind] für die Oberfläche. */
data class CameraState(
    val options: List<CameraOption> = emptyList(),
    val active: CameraOption? = null,
    val zoomRange: ClosedFloatingPointRange<Float>? = null,
    val error: String? = null,
)

/** Verbindet Kamera-Vorschau und Bildanalyse (CameraX) mit dem Lebenszyklus der Oberfläche. */
class CameraController(private val context: Context) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraKey: String,
        highResolution: Boolean,
        analyzer: ImageAnalysis.Analyzer,
    ): CameraState {
        val provider = awaitProvider().also { this.provider = it }
        val cameras = CameraOptions.describe(provider.availableCameraInfos)
        val options = cameras.map { it.first }
        val (selected, info) = CameraOptions.select(cameras, cameraKey)
            ?: return CameraState(options, error = "Keine Kamera gefunden.")

        val aspect16to9 = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect16to9).build())
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val targetSize = if (highResolution) Size(1920, 1080) else Size(1280, 720)
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspect16to9)
                    .setResolutionStrategy(
                        ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(analysisExecutor, analyzer)

        provider.unbindAll()
        return try {
            val camera = provider.bindToLifecycle(lifecycleOwner, info.cameraSelector, preview, analysis)
            this.camera = camera
            val zoom = camera.cameraInfo.zoomState.value
            CameraState(
                options = options,
                active = selected,
                zoomRange = zoom?.let { it.minZoomRatio..it.maxZoomRatio },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Kamera konnte nicht gestartet werden", e)
            camera = null
            CameraState(options, selected, error = "Kamera konnte nicht gestartet werden: ${e.message}")
        }
    }

    fun setZoom(ratio: Float) {
        val camera = camera ?: return
        val state = camera.cameraInfo.zoomState.value
        val clamped = if (state != null) ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio) else ratio
        camera.cameraControl.setZoomRatio(clamped)
    }

    fun release() {
        provider?.unbindAll()
        camera = null
        analysisExecutor.shutdown()
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private companion object {
        const val TAG = "CameraController"
    }
}
