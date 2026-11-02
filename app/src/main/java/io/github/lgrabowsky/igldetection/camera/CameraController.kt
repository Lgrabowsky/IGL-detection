package io.github.lgrabowsky.igldetection.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    /** Zoom wird in Software umgesetzt (Kamera hat keinen eigenen Zoom, z. B. USB-Webcam). */
    val softwareZoom: Boolean = false,
    /** Vorschau kommt aus den analysierten Bildern statt aus CameraX (bei externen Kameras). */
    val framePreview: Boolean = false,
    /** Angesteckte USB-Kameras, die Android nicht als Kamera bereitstellt. */
    val unavailableUsbCameras: List<String> = emptyList(),
    /** Hinweis zur Kamera, z. B. „USB-Zugriff erlauben“ beim App-eigenen USB-Treiber. */
    val info: String? = null,
    val error: String? = null,
)

/**
 * Verbindet Kamera und Bildanalyse mit dem Lebenszyklus der Oberfläche:
 * - eingebaute Kameras und von Android bereitgestellte USB-Kameras über CameraX,
 * - übrige USB-Webcams über den App-eigenen Treiber ([UvcCameraSource]).
 */
class CameraController(private val context: Context) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analyzer: SignAnalyzer? = null
    private var softwareZoom = false
    private var external = false

    private var uvcSource: UvcCameraSource? = null
    private var uvcLifecycle: Lifecycle? = null
    private val uvcLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> uvcSource?.start()
            Lifecycle.Event.ON_STOP -> uvcSource?.stop()
            else -> Unit
        }
    }

    /**
     * @param externalRotationDegrees Drehung für externe Kameras (Webcam-Montage), siehe [setExternalRotation].
     * @param onCameraInfo Statusmeldungen, die später eintreffen (USB-Berechtigung, Fehler …).
     */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraKey: String,
        highResolution: Boolean,
        externalRotationDegrees: Int,
        analyzer: SignAnalyzer,
        onCameraInfo: (String?) -> Unit,
    ): CameraState {
        this.analyzer = analyzer
        stopAppDriver()
        val provider = awaitProvider().also { this.provider = it }
        val usbNames = UsbCameras.connectedNames(context)
        val cameraXCameras = CameraOptions.describe(provider.availableCameraInfos).let { list ->
            // Genau eine USB-Kamera angesteckt → deren Namen anzeigen (z. B. „Logitech StreamCam (USB)“).
            if (usbNames.size == 1 && list.count { it.first.isExternal } == 1) {
                list.map { (option, info) ->
                    (if (option.isExternal) option.copy(label = "${usbNames.single()} (USB)") else option) to info
                }
            } else {
                list
            }
        }
        val androidProvidesUsbCamera = cameraXCameras.any { it.first.isExternal }
        val unavailableUsb = if (androidProvidesUsbCamera) emptyList() else usbNames
        // USB-Webcam angesteckt, aber nicht von Android freigegeben → App-eigener Treiber.
        val appDriverOption = unavailableUsb.firstOrNull()?.let(CameraOptions::appDriverOption)
        val options = cameraXCameras.map { it.first } + listOfNotNull(appDriverOption)
        val selected = CameraOptions.select(options, cameraKey)
            ?: return CameraState(options, unavailableUsbCameras = unavailableUsb, error = "Keine Kamera gefunden.")

        // Eine USB-Webcam dreht sich nicht mit dem Handy mit. CameraX rechnet bei externen Kameras
        // trotzdem die Display-Drehung ein – im Querformat käme das Bild dann um 90° gedreht an.
        // Deshalb: Bild so verwenden, wie die Webcam es liefert (plus einstellbare Drehung), und
        // die Vorschau direkt aus den analysierten Bildern anzeigen statt über CameraX.
        external = selected.isExternal
        analyzer.fixedRotationDegrees = if (external) externalRotationDegrees else null
        analyzer.publishPreviewFrames = external

        if (selected.usesAppDriver) {
            provider.unbindAll()
            camera = null
            softwareZoom = true
            startAppDriver(lifecycleOwner, highResolution, analyzer, onCameraInfo)
            return CameraState(
                options = options,
                active = selected,
                zoomRange = SOFTWARE_ZOOM_RANGE,
                softwareZoom = true,
                framePreview = true,
                unavailableUsbCameras = unavailableUsb,
                info = "${unavailableUsb.first()}: wird über den App-eigenen USB-Treiber verbunden …",
            )
        }

        val info = cameraXCameras.first { it.first == selected }.second
        val aspect16to9 = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        val useCases = mutableListOf<UseCase>()
        if (!external) {
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect16to9).build())
                .build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            useCases += preview
        }

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
        useCases += analysis

        provider.unbindAll()
        return try {
            val camera = provider.bindToLifecycle(lifecycleOwner, info.cameraSelector, *useCases.toTypedArray())
            this.camera = camera
            val hardwareZoomRange = camera.cameraInfo.zoomState.value
                ?.takeIf { it.maxZoomRatio >= MIN_USEFUL_HARDWARE_ZOOM }
                ?.let { it.minZoomRatio..it.maxZoomRatio }
            softwareZoom = hardwareZoomRange == null
            CameraState(
                options = options,
                active = selected,
                zoomRange = hardwareZoomRange ?: SOFTWARE_ZOOM_RANGE,
                softwareZoom = softwareZoom,
                framePreview = external,
                unavailableUsbCameras = unavailableUsb,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Kamera konnte nicht gestartet werden", e)
            camera = null
            CameraState(
                options,
                selected,
                unavailableUsbCameras = unavailableUsb,
                error = "Kamera konnte nicht gestartet werden: ${e.message}",
            )
        }
    }

    fun setZoom(ratio: Float) {
        val analyzer = analyzer ?: return
        if (softwareZoom) {
            analyzer.softwareZoom = ratio.coerceIn(SOFTWARE_ZOOM_RANGE.start, SOFTWARE_ZOOM_RANGE.endInclusive)
            return
        }
        analyzer.softwareZoom = 1f
        val camera = camera ?: return
        val state = camera.cameraInfo.zoomState.value
        val clamped = if (state != null) ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio) else ratio
        camera.cameraControl.setZoomRatio(clamped)
    }

    /** Drehung des Webcam-Bildes (0/90/180/270°), z. B. wenn die Kamera kopfüber montiert ist. */
    fun setExternalRotation(degrees: Int) {
        if (external) analyzer?.fixedRotationDegrees = degrees
    }

    fun release() {
        stopAppDriver()
        provider?.unbindAll()
        camera = null
        analysisExecutor.shutdown()
    }

    /** Startet den USB-Treiber, solange die App sichtbar ist (wie CameraX: ON_START … ON_STOP). */
    private fun startAppDriver(
        lifecycleOwner: LifecycleOwner,
        highResolution: Boolean,
        analyzer: SignAnalyzer,
        onCameraInfo: (String?) -> Unit,
    ) {
        val source = UvcCameraSource(
            context = context,
            highResolution = highResolution,
            onFrame = { frame -> analyzer.processFrame(frame, analyzer.fixedRotationDegrees ?: 0) },
            onStatus = onCameraInfo,
        )
        uvcSource = source
        val lifecycle = lifecycleOwner.lifecycle
        uvcLifecycle = lifecycle
        // addObserver liefert die bisherigen Ereignisse nach → startet sofort, wenn die App sichtbar ist.
        lifecycle.addObserver(uvcLifecycleObserver)
    }

    private fun stopAppDriver() {
        uvcLifecycle?.removeObserver(uvcLifecycleObserver)
        uvcLifecycle = null
        uvcSource?.release()
        uvcSource = null
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
        const val MIN_USEFUL_HARDWARE_ZOOM = 1.5f
        val SOFTWARE_ZOOM_RANGE = 1f..3f
    }
}
