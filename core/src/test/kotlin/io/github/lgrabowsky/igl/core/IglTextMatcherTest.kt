package io.github.lgrabowsky.igl.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IglTextMatcherTest {

    @Test
    fun `exakte Schreibweisen werden voll gewertet`() {
        assertEquals(1f, IglTextMatcher.score("IG-L"))
        assertEquals(1f, IglTextMatcher.score("IG–L"))
        assertEquals(1f, IglTextMatcher.score(" IG - L "))
        assertEquals(1f, IglTextMatcher.score("(IG-L)"))
        assertEquals(0.95f, IglTextMatcher.score("IGL"))
    }

    @Test
    fun `typische OCR-Verwechslungen werden toleriert`() {
        for (text in listOf("1G-L", "lG-L", "|G-L", "I6-L", "IG.L", "IG_L", "1GL")) {
            assertTrue(IglTextMatcher.score(text) >= 0.5f, "\"$text\" sollte erkannt werden")
        }
    }

    @Test
    fun `andere Woerter werden nicht als IG-L erkannt`() {
        val rejected = listOf(
            "", "IG", "GL", "IGLU", "IG-LKW", "Spiegl", "IGEL", "IG-X", "AG-L", "IGXL", "100", "igl",
        )
        for (text in rejected) {
            assertTrue(IglTextMatcher.score(text) < 0.5f, "\"$text\" darf nicht erkannt werden")
        }
    }

    @Test
    fun `auf mehrere OCR-Woerter verteilter Schriftzug wird zusammengesetzt`() {
        // LED-Anzeigen haben große Buchstabenabstände: „I G - L“.
        val line = ocrLine(
            "I" to box(550, 251, 6, 20),
            "G" to box(562, 251, 14, 20),
            "-" to box(582, 259, 8, 4),
            "L" to box(598, 251, 14, 20),
        )
        val matches = IglTextMatcher.findMatches(OcrResult(listOf(line)))
        assertEquals(1, matches.size)
        assertEquals(BoundingBox(550, 251, 612, 271), matches.single().box)
        assertEquals(1f, matches.single().score)
    }

    @Test
    fun `IG-L neben einer Zahl in derselben Zeile bleibt ein Treffer`() {
        val line = ocrLine("100" to box(0, 0, 50, 30), "IG-L" to box(80, 0, 60, 20))
        val match = IglTextMatcher.findMatches(OcrResult(listOf(line))).single()
        assertEquals("IG-L", match.text)
        assertEquals(1f, match.score)
    }

    @Test
    fun `IGL in normalem Text wird abgewertet`() {
        val line = ocrLine("IGL" to box(0, 0, 40, 20), "Transporte" to box(50, 0, 120, 20))
        assertTrue(IglTextMatcher.findMatches(OcrResult(listOf(line))).isEmpty())
    }

    @Test
    fun `doppelte Treffer fuer denselben Schriftzug werden zusammengefasst`() {
        val lines = listOf(
            ocrLine("IG-L" to box(100, 100, 60, 20)),
            ocrLine("IGL" to box(102, 101, 58, 19)),
        )
        val matches = IglTextMatcher.findMatches(OcrResult(lines))
        assertEquals(1, matches.size)
        assertEquals("IG-L", matches.single().text)
    }
}
