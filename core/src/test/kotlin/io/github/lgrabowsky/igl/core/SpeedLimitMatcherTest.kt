package io.github.lgrabowsky.igl.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeedLimitMatcherTest {

    @Test
    fun `gueltige Tempolimits werden erkannt`() {
        for (value in listOf(30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130)) {
            assertEquals(value to 1f, SpeedLimitMatcher.parse(value.toString()))
        }
    }

    @Test
    fun `OCR-Verwechslungen in Ziffern werden toleriert`() {
        assertEquals(100, SpeedLimitMatcher.parse("1OO")?.first)
        assertEquals(100, SpeedLimitMatcher.parse("l00")?.first)
        assertEquals(80, SpeedLimitMatcher.parse("B0")?.first)
        assertTrue(SpeedLimitMatcher.parse("1OO")!!.second < 1f)
    }

    @Test
    fun `andere Zahlen und Woerter werden verworfen`() {
        for (text in listOf("290", "383", "1000", "030", "100km", "OO", "IG-L", "5", "125", "")) {
            assertNull(SpeedLimitMatcher.parse(text), "\"$text\" ist kein Tempolimit")
        }
    }

    @Test
    fun `getrennt gelesene Ziffern werden zusammengesetzt`() {
        val line = ocrLine("1" to box(0, 0, 8, 30), "00" to box(14, 0, 36, 30))
        val candidate = SpeedLimitMatcher.findCandidates(OcrResult(listOf(line))).single()
        assertEquals(100, candidate.valueKmh)
        assertEquals(BoundingBox(0, 0, 50, 30), candidate.box)
    }

    @Test
    fun `Telefonnummer auf LKW liefert kein Tempolimit`() {
        val line = ocrLine(
            "tel.:" to box(0, 0, 40, 12),
            "+420" to box(45, 0, 40, 12),
            "383" to box(90, 0, 30, 12),
            "393" to box(125, 0, 30, 12),
            "290" to box(160, 0, 30, 12),
        )
        assertTrue(SpeedLimitMatcher.findCandidates(OcrResult(listOf(line))).isEmpty())
    }
}
