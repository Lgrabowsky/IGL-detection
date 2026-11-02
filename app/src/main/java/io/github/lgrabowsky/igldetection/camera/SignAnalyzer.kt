package io.github.lgrabowsky.igldetection.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.lgrabowsky.igl.core.BoundingBox
import io.github.lgrabowsky.igl.core.DetectionConfig
import io.github.lgrabowsky.igl.core.FrameAnalyzer
import io.github.lgrabowsky.igl.core.FrameObservation
import io.github.lgrabowsky.igl.core.IntArrayRgbImage
import io.github.lgrabowsky.igl.core.OcrElement
import io.github.lgrabowsky.igl.core.OcrLine
import io.github.lgrabowsky.igl.core.OcrResult
import io.github.lgrabowsky.igl.core.RgbImage
import kotlin.math.roundToInt

/** Ergebnis eines analysierten Kamerabildes. */
data class AnalysisResult(
    val observation: FrameObservation,
    /** Alle erkannten Textzeilen (für die Debug-Anzeige). */
    val textBoxes: List<BoundingBox>,
    val processingMs: Long,
)

/**
 * CameraX-Analyzer: Kamerabild → aufrecht drehen → ggf. Software-Zoom → oberen Suchbereich ausschneiden →
 * ML-Kit-Texterkennung → [FrameAnalyzer] (IG-L, Tempo-Zahlen, roter Ring).
 *
 * Läuft auf einem eigenen Thread. Dank `STRATEGY_KEEP_ONLY_LATEST` wird immer das neueste Bild
 * verarbeitet, sobald das vorige fertig ist.
 */
class SignAnalyzer(
    private val detectionConfig: () -> DetectionConfig,
    private val onResult: (AnalysisResult) -> Unit,
    /** Erhält das aufbereitete Bild, wenn [publishPreviewFrames] gesetzt ist. */
    private val onPreviewFrame: (Bitmap) -> Unit,
) : ImageAnalysis.Analyzer {

    /**
     * Feste Bilddrehung statt der von CameraX berechneten. Für externe Kameras (USB-Webcam), die
     * sich nicht mit dem Handy mitdrehen; null = CameraX-Drehung verwenden (eingebaute Kameras).
     */
    @Volatile
    var fixedRotationDegrees: Int? = null

    /** Digitaler Zoom in Software (Bildmitte vergrößern) für Kameras ohne eigenen Zoom. */
    @Volatile
    var softwareZoom: Float = 1f

    /** Aufbereitete Bilder zusätzlich als Vorschau ausgeben (wenn keine CameraX-Vorschau läuft). */
    @Volatile
    var publishPreviewFrames: Boolean = false

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var frameAnalyzer = FrameAnalyzer()
    private var pixelBuffer = IntArray(0)
    private var lastFrameMs = 0L

    @Volatile
    private var closed = false

    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (closed || now - lastFrameMs < MIN_FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastFrameMs = now

        val rotation = fixedRotationDegrees ?: imageProxy.imageInfo.rotationDegrees
        val frame = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "Bild konnte nicht gelesen werden", e)
            null
        } finally {
            imageProxy.close()
        }
        if (frame == null) return

        val upright = frame.rotatedBy(rotation).zoomedBy(softwareZoom)
        if (publishPreviewFrames) onPreviewFrame(upright)
        val config = detectionConfig()
        if (config != frameAnalyzer.config) frameAnalyzer = FrameAnalyzer(config)

        // Nur der obere Bildteil wird gelesen: Schilder hängen oben, und die OCR wird schneller.
        val searchHeight = (upright.height * config.searchAreaBottom).roundToInt().coerceIn(1, upright.height)
        val searchArea = if (searchHeight < upright.height) {
            Bitmap.createBitmap(upright, 0, 0, upright.width, searchHeight)
        } else {
            upright
        }

        val text = try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(searchArea, 0)))
        } catch (e: Exception) {
            if (!closed) Log.w(TAG, "Texterkennung fehlgeschlagen", e)
            return
        }

        val ocr = text.toOcrResult()
        val observation = frameAnalyzer.analyze(
            timestampMs = now,
            imageWidth = upright.width,
            imageHeight = upright.height,
            ocr = ocr,
            image = searchArea.toRgbImage(),
        )
        onResult(
            AnalysisResult(
                observation = observation,
                textBoxes = ocr.lines.map { it.box },
                processingMs = SystemClock.elapsedRealtime() - now,
            ),
        )
    }

    fun close() {
        closed = true
        recognizer.close()
    }

    private fun Bitmap.toRgbImage(): RgbImage {
        val size = width * height
        if (pixelBuffer.size < size) pixelBuffer = IntArray(size)
        getPixels(pixelBuffer, 0, width, 0, 0, width, height)
        return IntArrayRgbImage(width, height, pixelBuffer)
    }

    private companion object {
        const val TAG = "SignAnalyzer"

        /** Begrenzung auf ca. 8 Bilder/s – schont Akku und verhindert Überhitzen in der Halterung. */
        const val MIN_FRAME_INTERVAL_MS = 120L

        fun Bitmap.rotatedBy(degrees: Int): Bitmap {
            if (degrees % 360 == 0) return this
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }

        /**
         * Schneidet die Bildmitte aus und vergrößert sie wieder auf die ursprüngliche Größe.
         * Bringt keine zusätzlichen Details, aber kleine Schrift wird für die Texterkennung größer.
         */
        fun Bitmap.zoomedBy(zoom: Float): Bitmap {
            if (zoom <= 1.01f) return this
            val cropWidth = (width / zoom).roundToInt().coerceIn(1, width)
            val cropHeight = (height / zoom).roundToInt().coerceIn(1, height)
            val matrix = Matrix().apply { setScale(width.toFloat() / cropWidth, height.toFloat() / cropHeight) }
            return Bitmap.createBitmap(
                this,
                (width - cropWidth) / 2,
                (height - cropHeight) / 2,
                cropWidth,
                cropHeight,
                matrix,
                true,
            )
        }

        fun Rect.toBoundingBox() = BoundingBox(left, top, right, bottom)

        fun Text.toOcrResult(): OcrResult = OcrResult(
            textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val lineBox = line.boundingBox ?: return@mapNotNull null
                    OcrLine(
                        text = line.text,
                        box = lineBox.toBoundingBox(),
                        elements = line.elements.mapNotNull { element ->
                            element.boundingBox?.let { OcrElement(element.text, it.toBoundingBox()) }
                        },
                    )
                }
            },
        )
    }
}
