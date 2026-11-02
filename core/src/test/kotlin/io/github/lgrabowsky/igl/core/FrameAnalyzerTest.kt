package io.github.lgrabowsky.igl.core

import io.github.lgrabowsky.igl.core.TestImages.toRgbImage
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameAnalyzerTest {

    /**
     * Nachbau der Szene aus dem Beispielfoto (1200 × 800): Schilderbrücke mit „100“ | „IG-L“ | „100“,
     * darunter ein LKW mit Telefonnummer und einem „80“-Aufkleber mit rotem Rand.
     */
    private fun gantryScene(): Pair<OcrResult, RgbImage> {
        val image = TestImages.canvas(1200, 800, Color(70, 90, 60))
        TestImages.fillRect(image, box(0, 230, 1200, 45), TestImages.ORANGE_BEAM)
        TestImages.fillRect(image, box(335, 105, 500, 195), Color(225, 225, 228))
        TestImages.drawLedSign(image, cx = 433, cy = 198, radius = 41)
        TestImages.drawLedSign(image, cx = 735, cy = 202, radius = 41)
        TestImages.fillRect(image, box(522, 145, 122, 140), Color(25, 25, 28))
        // LKW-Aufkleber weit unten im Bild
        TestImages.drawPaintedSign(image, cx = 690, cy = 690, radius = 22)

        val ocr = OcrResult(
            listOf(
                ocrLine("100" to box(407, 182, 53, 33)),
                ocrLine("IG-L" to box(550, 251, 62, 20)),
                ocrLine("100" to box(710, 187, 50, 30)),
                ocrLine(
                    "tel.:" to box(680, 560, 30, 12),
                    "+420" to box(712, 560, 30, 12),
                    "383" to box(745, 560, 22, 12),
                    "393" to box(770, 560, 22, 12),
                    "290" to box(795, 560, 22, 12),
                ),
                ocrLine("80" to box(678, 680, 24, 20)),
            ),
        )
        return ocr to image.toRgbImage()
    }

    @Test
    fun `Schilderbruecke aus dem Beispielfoto`() {
        val (ocr, image) = gantryScene()
        val result = FrameAnalyzer().analyze(1000L, 1200, 800, ocr, image)

        assertEquals(listOf(100, 100), result.speedSigns.map { it.valueKmh })
        assertTrue(result.speedSigns.all { it.ring != null })
        val igl = result.iglMatches.single()
        assertEquals("IG-L", igl.text)
        assertTrue(igl.nearSpeedSign)
    }

    @Test
    fun `ohne Ringpruefung zaehlt auch der Aufkleber nicht, weil er unten im Bild ist`() {
        val (ocr, image) = gantryScene()
        val analyzer = FrameAnalyzer(DetectionConfig(requireRedRing = false))
        val result = analyzer.analyze(1000L, 1200, 800, ocr, image)

        assertEquals(listOf(100, 100), result.speedSigns.map { it.valueKmh })
    }

    @Test
    fun `Zahl ohne roten Ring wird verworfen`() {
        val image = TestImages.canvas(1200, 800, Color.WHITE)
        val ocr = OcrResult(listOf(ocrLine("100" to box(407, 182, 53, 33))))
        val result = FrameAnalyzer().analyze(0L, 1200, 800, ocr, image.toRgbImage())

        assertTrue(result.speedSigns.isEmpty())
    }

    @Test
    fun `ohne Pixeldaten und mit Ringpflicht gibt es keine Tempo-Schilder, IG-L aber schon`() {
        val (ocr, _) = gantryScene()
        val result = FrameAnalyzer().analyze(0L, 1200, 800, ocr, image = null)

        assertTrue(result.speedSigns.isEmpty())
        val igl = result.iglMatches.single()
        assertEquals(false, igl.nearSpeedSign)
    }

    @Test
    fun `IG-L im unteren Bildbereich wird ignoriert`() {
        val ocr = OcrResult(listOf(ocrLine("IG-L" to box(500, 700, 60, 20))))
        val result = FrameAnalyzer().analyze(0L, 1200, 800, ocr, null)

        assertNull(result.iglMatches.firstOrNull())
    }
}
