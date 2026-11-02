package io.github.lgrabowsky.igl.core

import io.github.lgrabowsky.igl.core.TestImages.toRgbImage
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedRingVerifierTest {

    private val verifier = RedRingVerifier()

    @Test
    fun `Blechschild mit dreistelliger Zahl`() {
        val image = TestImages.canvas(400, 300, Color(90, 120, 90))
        TestImages.drawPaintedSign(image, cx = 200, cy = 150, radius = 60)
        val digits = box(167, 129, 66, 42) // „130“: breit, ca. 0,35 × Durchmesser hoch
        TestImages.drawFakeDigits(image, digits, Color.BLACK)

        val ring = assertNotNull(verifier.findRing(image.toRgbImage(), digits))
        assertTrue(ring.radius in 45f..62f, "Radius ${ring.radius}")
    }

    @Test
    fun `Blechschild mit zweistelliger Zahl`() {
        val image = TestImages.canvas(400, 300, Color(150, 170, 200))
        TestImages.drawPaintedSign(image, cx = 200, cy = 150, radius = 50)
        val digits = box(178, 128, 44, 44) // „80“
        TestImages.drawFakeDigits(image, digits, Color.BLACK)

        assertTrue(verifier.hasRedRing(image.toRgbImage(), digits))
    }

    @Test
    fun `LED-Anzeige mit gepunktetem Ring wie im Beispielfoto`() {
        // Maße aus dem Beispielfoto: Ring-Ø ≈ 82 px, „100“ ≈ 52 × 32 px.
        val image = TestImages.canvas(300, 200, Color(220, 222, 225))
        TestImages.drawLedSign(image, cx = 150, cy = 100, radius = 41)
        val digits = box(124, 84, 52, 32)
        TestImages.drawFakeDigits(image, digits, Color(235, 235, 235))

        assertTrue(verifier.hasRedRing(image.toRgbImage(), digits))
    }

    @Test
    fun `leicht versetzte OCR-Box wird toleriert`() {
        val image = TestImages.canvas(400, 300, Color.DARK_GRAY)
        TestImages.drawPaintedSign(image, cx = 200, cy = 150, radius = 60)
        val digits = box(167 + 6, 129 + 5, 66, 42)

        assertTrue(verifier.hasRedRing(image.toRgbImage(), digits))
    }

    @Test
    fun `Zahl ohne Ring`() {
        val image = TestImages.canvas(400, 300, Color.WHITE)
        val digits = box(167, 129, 66, 42)
        TestImages.drawFakeDigits(image, digits, Color.BLACK)

        assertNull(verifier.findRing(image.toRgbImage(), digits))
    }

    @Test
    fun `Zahl auf roter Flaeche ist kein Ring`() {
        // z. B. weiße Schrift auf roter LKW-Plane
        val image = TestImages.canvas(400, 300, TestImages.SIGN_RED)
        val digits = box(167, 129, 66, 42)
        TestImages.drawFakeDigits(image, digits, Color.WHITE)

        assertFalse(verifier.hasRedRing(image.toRgbImage(), digits))
    }

    @Test
    fun `Zahl am orangen Brueckentraeger ist kein Ring`() {
        val image = TestImages.canvas(400, 300, Color.WHITE)
        TestImages.fillRect(image, box(0, 190, 400, 40), TestImages.ORANGE_BEAM)
        TestImages.fillRect(image, box(0, 70, 400, 40), TestImages.ORANGE_BEAM)
        val digits = box(167, 129, 66, 42)
        TestImages.drawFakeDigits(image, digits, Color.BLACK)

        assertFalse(verifier.hasRedRing(image.toRgbImage(), digits))
    }

    @Test
    fun `Halbkreis reicht nicht`() {
        val image = TestImages.canvas(400, 300, Color.WHITE)
        TestImages.drawPaintedSign(image, cx = 200, cy = 150, radius = 60)
        TestImages.fillRect(image, box(0, 150, 400, 150), Color.WHITE) // untere Hälfte abgedeckt
        val digits = box(167, 129, 66, 42)

        assertFalse(verifier.hasRedRing(image.toRgbImage(), digits))
    }

    @Test
    fun `Farbklassifikation`() {
        assertTrue(RedRingVerifier.isSignRed(0xFFC81E23.toInt())) // Schilderrot
        assertTrue(RedRingVerifier.isSignRed(0xFFFF3C32.toInt())) // LED-Rot
        assertFalse(RedRingVerifier.isSignRed(0xFFEBA028.toInt())) // Orange (Brücke)
        assertFalse(RedRingVerifier.isSignRed(0xFFFFFFFF.toInt())) // Weiß
        assertFalse(RedRingVerifier.isSignRed(0xFF3A0A0A.toInt())) // fast Schwarz
        assertFalse(RedRingVerifier.isSignRed(0xFFC8A0A0.toInt())) // blasses Rosa
    }
}
