package io.github.lgrabowsky.igl.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ein gefundener roter Ring um eine Zahl. */
data class RingResult(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    /** Anteil der Strahlen (0..1), die den Ring im erwarteten Abstand getroffen haben. */
    val coverage: Float,
)

/**
 * Prüft, ob eine erkannte Zahl von einem roten Kreisring umgeben ist – so wie bei
 * Vorschriftszeichen „Geschwindigkeitsbeschränkung“ und den LED-Anzeigen der
 * Verkehrsbeeinflussungsanlagen.
 *
 * Verfahren: Vom Zahlenmittelpunkt aus werden [angleSamples] Strahlen nach außen verfolgt und
 * jeweils der Abstand des ersten roten Pixels gemessen. Liegt dieser Abstand für einen großen
 * Teil der Strahlen ungefähr gleich weit entfernt, ist es ein Ring. Eine rote Fläche (z. B.
 * LKW-Plane) fällt durch, weil dort schon direkt neben der Zahl alles rot ist.
 */
class RedRingVerifier(
    private val angleSamples: Int = 64,
    private val minCoverage: Float = 0.6f,
) {

    fun findRing(image: RgbImage, textBox: BoundingBox): RingResult? {
        val w = textBox.width.toFloat()
        val h = textBox.height.toFloat()
        if (w < 4f || h < 4f) return null

        // Erwarteter Ringradius relativ zur Textgröße: „100“ ist breit (R ≈ 0,8·Breite),
        // zweistellige Zahlen sind eher quadratisch (R ≈ 1,1·Höhe).
        val rMin = max(w, h) * 0.5f + 1f
        val rMax = max(w * 0.9f, h * 1.8f) * 1.25f
        val step = max(1f, (rMax - rMin) / 80f)

        // Die OCR-Box sitzt nicht immer exakt in der Ringmitte → kleine Verschiebungen probieren.
        val offsets = floatArrayOf(0f, -0.15f * h, 0.15f * h)
        var best: RingResult? = null
        for (dy in offsets) {
            for (dx in offsets) {
                val result = probe(image, textBox.centerX + dx, textBox.centerY + dy, rMin, rMax, step)
                if (result != null && (best == null || result.coverage > best.coverage)) best = result
            }
        }
        return best
    }

    fun hasRedRing(image: RgbImage, textBox: BoundingBox): Boolean = findRing(image, textBox) != null

    private fun probe(image: RgbImage, cx: Float, cy: Float, rMin: Float, rMax: Float, step: Float): RingResult? {
        val firstRed = FloatArray(angleSamples)
        var found = 0
        for (k in 0 until angleSamples) {
            val angle = 2.0 * PI * k / angleSamples
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            firstRed[k] = -1f
            var r = rMin
            while (r <= rMax) {
                val x = (cx + r * dx).roundToInt()
                val y = (cy + r * dy).roundToInt()
                if (x < 0 || y < 0 || x >= image.width || y >= image.height) break
                if (isSignRed(image.argb(x, y))) {
                    firstRed[k] = r
                    found++
                    break
                }
                r += step
            }
        }
        if (found < angleSamples * minCoverage) return null

        val radii = firstRed.filter { it >= 0f }.sorted()
        val median = radii[radii.size / 2]
        // Rot direkt an der Zahl → Fläche statt Ring.
        if (median < rMin + max(1.5f, 0.05f * median)) return null
        val tolerance = max(2f, 0.2f * median)
        val consistent = radii.count { abs(it - median) <= tolerance }
        val coverage = consistent.toFloat() / angleSamples
        return if (coverage >= minCoverage) RingResult(cx, cy, median, coverage) else null
    }

    companion object {
        /**
         * Rot im Sinne eines Verkehrszeichen-Rands. Orange/Gelb (z. B. die Schilderbrücke im
         * Beispielbild) und blasse oder sehr dunkle Farben zählen nicht.
         */
        fun isSignRed(argb: Int): Boolean {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            if (r < 80 || r < g || r < b) return false
            val min = minOf(g, b)
            val chroma = r - min
            if (chroma.toFloat() / r < 0.35f) return false
            val hue = 60f * (g - b) / chroma // −60° … +60°, 0° = reines Rot
            return hue in -30f..22f
        }
    }
}
