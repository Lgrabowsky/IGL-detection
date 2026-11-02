package io.github.lgrabowsky.igl.core

/** Eine im OCR-Text gefundene Zahl, die ein Tempolimit sein könnte (noch ohne Bildprüfung). */
data class SpeedCandidate(
    val valueKmh: Int,
    val text: String,
    val box: BoundingBox,
    val score: Float,
)

/**
 * Findet Zahlen, die als Tempolimit in Frage kommen (30 … 130 km/h in Zehnerschritten).
 *
 * Die Zahl allein ist noch kein Beweis für ein Verkehrszeichen (Telefonnummern, Kennzeichen …),
 * deshalb prüft [FrameAnalyzer] zusätzlich den roten Ring mit [RedRingVerifier].
 */
object SpeedLimitMatcher {

    val VALID_LIMITS_KMH: Set<Int> = setOf(30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130)

    /** Zeichen, die die OCR häufig statt einer Ziffer liefert, mit Vertrauensfaktor. */
    private val DIGIT_LOOKALIKES: Map<Char, Pair<Char, Float>> = mapOf(
        'O' to ('0' to 0.85f), 'o' to ('0' to 0.85f), 'D' to ('0' to 0.7f), 'Q' to ('0' to 0.7f),
        'I' to ('1' to 0.85f), 'l' to ('1' to 0.85f), '|' to ('1' to 0.85f), 'i' to ('1' to 0.7f),
        'S' to ('5' to 0.8f), 's' to ('5' to 0.7f), 'B' to ('8' to 0.8f), 'G' to ('6' to 0.7f),
        'Z' to ('2' to 0.7f), 'z' to ('2' to 0.6f),
    )
    private const val TRIM_CHARS = ".,;:'\"`´()[]{}<>*"

    /** Faktor, wenn in derselben Zeile weitere Wörter/Zahlen stehen. */
    private const val CONTEXT_FACTOR = 0.6f

    /** Liefert Tempowert und Score für [text] oder null, wenn es kein gültiges Tempolimit ist. */
    fun parse(text: String): Pair<Int, Float>? {
        val compact = text.filterNot { it.isWhitespace() }.trim { it in TRIM_CHARS }
        if (compact.length !in 2..3) return null
        if (compact.none { it in '0'..'9' }) return null
        var score = 1f
        val digits = StringBuilder(compact.length)
        for (c in compact) {
            if (c in '0'..'9') {
                digits.append(c)
            } else {
                val (digit, factor) = DIGIT_LOOKALIKES[c] ?: return null
                digits.append(digit)
                score *= factor
            }
        }
        if (digits[0] == '0') return null
        val value = digits.toString().toInt()
        return if (value in VALID_LIMITS_KMH) value to score else null
    }

    fun findCandidates(ocr: OcrResult, minScore: Float = 0.5f): List<SpeedCandidate> {
        val candidates = mutableListOf<SpeedCandidate>()
        for (line in ocr.lines) {
            val elements = line.elements.ifEmpty { listOf(OcrElement(line.text, line.box)) }
            for (start in elements.indices) {
                // Einzelnes Wort oder zwei Wörter zusammen (LED-Ziffern werden manchmal getrennt: "1" "00").
                for (end in start..minOf(start + 1, elements.size - 1)) {
                    val text = elements.subList(start, end + 1).joinToString("") { it.text }
                    val (value, textScore) = parse(text) ?: continue
                    val box = elements.subList(start, end + 1).map { it.box }.reduce(BoundingBox::union)
                    val score = textScore * if (elements.size > end - start + 1) CONTEXT_FACTOR else 1f
                    if (score >= minScore) candidates += SpeedCandidate(value, text, box, score)
                }
            }
        }
        return suppressOverlaps(candidates, { it.box }, { it.score })
    }
}
