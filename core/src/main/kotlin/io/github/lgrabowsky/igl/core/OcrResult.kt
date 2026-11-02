package io.github.lgrabowsky.igl.core

/** Ein einzelnes Wort, wie es die Texterkennung (OCR) liefert. */
data class OcrElement(val text: String, val box: BoundingBox)

/** Eine Textzeile aus einem oder mehreren Wörtern. */
data class OcrLine(val text: String, val box: BoundingBox, val elements: List<OcrElement>)

/**
 * OCR-Ergebnis eines Kamerabildes – unabhängig von der verwendeten OCR-Bibliothek
 * (in der App: ML Kit), damit die Auswertung ohne Android getestet werden kann.
 */
data class OcrResult(val lines: List<OcrLine>) {
    companion object {
        val EMPTY = OcrResult(emptyList())
    }
}
