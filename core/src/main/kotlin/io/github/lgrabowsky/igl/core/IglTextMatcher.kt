package io.github.lgrabowsky.igl.core

/** Ein im Bild gefundener „IG-L“-Schriftzug. */
data class IglMatch(
    /** Erkannter Rohtext, z. B. "IG-L", "IGL" oder "1G-L". */
    val text: String,
    val box: BoundingBox,
    /** 0..1 – wie sicher der Text „IG-L“ bedeutet (inkl. Kontext). */
    val score: Float,
    /** true, wenn im selben Bild ein bestätigtes Tempolimit-Schild in der Nähe ist. */
    val nearSpeedSign: Boolean = false,
)

/**
 * Erkennt den Zusatz „IG-L“ (Immissionsschutzgesetz-Luft) in OCR-Text.
 *
 * Die Texterkennung liest den Schriftzug – vor allem auf LED-Anzeigen von
 * Verkehrsbeeinflussungsanlagen – nicht immer exakt. Typische Verwechslungen
 * (I↔1/l/|, G↔6/C, Bindestrich fehlt oder ist ein anderes Zeichen) werden
 * daher toleriert, aber mit einem niedrigeren Score bewertet.
 */
object IglTextMatcher {

    private val I_LIKE = mapOf(
        'I' to 1.0f, 'l' to 0.9f, '1' to 0.8f, '|' to 0.8f, 'i' to 0.7f, '!' to 0.6f, 'Í' to 0.6f, 'Ì' to 0.6f,
    )
    private val G_LIKE = mapOf(
        'G' to 1.0f, '6' to 0.75f, 'g' to 0.7f, 'C' to 0.6f,
    )
    private val L_LIKE = mapOf(
        'L' to 1.0f, 'Ł' to 0.8f, 'l' to 0.6f, '1' to 0.5f,
    )
    private val DASHES = setOf('-', '‐', '‑', '‒', '–', '—', '−')
    private val OTHER_SEPARATORS = setOf('_', '.', '·', '•', ':', '~', '=')
    private const val NO_SEPARATOR_FACTOR = 0.95f
    private const val OTHER_SEPARATOR_FACTOR = 0.9f

    /** Zeichen, die am Anfang/Ende eines Wortes ignoriert werden (Satzzeichen, Rahmen …). */
    private const val TRIM_CHARS = ".,;:'\"`´()[]{}<>*"

    /** Faktor, wenn in derselben Zeile weitere Wörter stehen (z. B. Firmenname „IGL Transporte“). */
    private const val TEXT_CONTEXT_FACTOR = 0.4f

    /** Maximal so viele aufeinanderfolgende OCR-Wörter werden zusammengesetzt ("I", "G", "-", "L"). */
    private const val MAX_JOINED_ELEMENTS = 4

    /** Bewertet, wie sehr [text] wie „IG-L“ aussieht. 0 = keine Übereinstimmung. */
    fun score(text: String): Float {
        val compact = text.filterNot { it.isWhitespace() }.trim { it in TRIM_CHARS }
        if (compact.length !in 3..4) return 0f
        val i = I_LIKE[compact[0]] ?: return 0f
        val g = G_LIKE[compact[1]] ?: return 0f
        val l = L_LIKE[compact[compact.length - 1]] ?: return 0f
        val separator = if (compact.length == 3) {
            NO_SEPARATOR_FACTOR
        } else {
            when (compact[2]) {
                in DASHES -> 1f
                in OTHER_SEPARATORS -> OTHER_SEPARATOR_FACTOR
                else -> return 0f
            }
        }
        return i * g * l * separator
    }

    /** Sucht alle „IG-L“-Schriftzüge mit einem Score von mindestens [minScore]. */
    fun findMatches(ocr: OcrResult, minScore: Float = 0.5f): List<IglMatch> {
        val candidates = mutableListOf<IglMatch>()
        for (line in ocr.lines) {
            val elements = line.elements.ifEmpty { listOf(OcrElement(line.text, line.box)) }
            for (start in elements.indices) {
                val joined = StringBuilder()
                var box = elements[start].box
                val lastIndex = minOf(elements.size, start + MAX_JOINED_ELEMENTS) - 1
                for (end in start..lastIndex) {
                    joined.append(elements[end].text)
                    if (end > start) box = box.union(elements[end].box)
                    if (joined.length > 8) break
                    val textScore = score(joined.toString())
                    if (textScore <= 0f) continue
                    val score = textScore * contextFactor(elements, start, end)
                    if (score >= minScore) candidates += IglMatch(joined.toString(), box, score)
                }
            }
        }
        return suppressOverlaps(candidates, { it.box }, { it.score })
    }

    /**
     * Auf Schildern steht „IG-L“ für sich allein (höchstens neben einer Zahl).
     * Stehen weitere Wörter in derselben Zeile, handelt es sich eher um normalen Text.
     */
    private fun contextFactor(elements: List<OcrElement>, start: Int, end: Int): Float {
        val hasOtherWords = elements.withIndex().any { (index, element) ->
            index !in start..end && element.text.count { it.isLetter() } >= 2
        }
        return if (hasOtherWords) TEXT_CONTEXT_FACTOR else 1f
    }
}

/**
 * Non-Maximum-Suppression: behält von sich überlappenden Kandidaten nur den mit
 * dem höchsten Score.
 */
internal fun <T> suppressOverlaps(
    candidates: List<T>,
    box: (T) -> BoundingBox,
    score: (T) -> Float,
    maxIou: Float = 0.3f,
): List<T> {
    val kept = mutableListOf<T>()
    for (candidate in candidates.sortedByDescending(score)) {
        val b = box(candidate)
        val overlaps = kept.any { other ->
            val o = box(other)
            b.intersectionOverUnion(o) > maxIou || o.containsCenterOf(b) || b.containsCenterOf(o)
        }
        if (!overlaps) kept += candidate
    }
    return kept
}

private fun BoundingBox.containsCenterOf(other: BoundingBox): Boolean =
    other.centerX >= left && other.centerX < right && other.centerY >= top && other.centerY < bottom
