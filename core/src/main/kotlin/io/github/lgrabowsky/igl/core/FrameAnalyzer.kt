package io.github.lgrabowsky.igl.core

/** Einstellungen für die Auswertung eines einzelnen Bildes. */
data class DetectionConfig(
    /** Tempo-Zahlen nur werten, wenn ein roter Ring gefunden wurde. */
    val requireRedRing: Boolean = true,
    /**
     * Nur Treffer oberhalb dieser Bildhöhe (Anteil 0..1 von oben) werten. Schilder hängen über
     * bzw. neben der Fahrbahn; weiter unten stehen eher Aufkleber auf LKW („80“) o. Ä.
     */
    val searchAreaBottom: Float = 0.65f,
    val minIglScore: Float = 0.5f,
    val minSpeedScore: Float = 0.5f,
    /**
     * „IG-L“ gilt als „neben einem 100er-Schild“, wenn der Abstand ≤ Faktor × Zahlenhöhe ist
     * (Zusatztafel direkt unter dem Schild ≈ 2, mittlere Anzeige einer Schilderbrücke ≈ 5).
     */
    val nearSignDistanceFactor: Float = 6f,
)

/** Ein bestätigtes Tempolimit-Schild in einem Bild. */
data class SpeedSign(
    val valueKmh: Int,
    /** Position der Zahl (nicht des ganzen Schildes). */
    val box: BoundingBox,
    val score: Float,
    /** Gefundener roter Ring oder null, wenn die Ringprüfung deaktiviert ist. */
    val ring: RingResult?,
)

/** Alles, was in einem einzelnen Kamerabild gefunden wurde. */
data class FrameObservation(
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val iglMatches: List<IglMatch>,
    val speedSigns: List<SpeedSign>,
)

/** Wertet das OCR-Ergebnis (und optional die Pixel) eines einzelnen Bildes aus. */
class FrameAnalyzer(
    val config: DetectionConfig = DetectionConfig(),
    private val ringVerifier: RedRingVerifier = RedRingVerifier(),
) {

    /**
     * @param ocr Texterkennung des (aufrecht gedrehten) Bildes.
     * @param image Pixel desselben Bildes für die Ringprüfung; darf null sein (dann keine Ringprüfung
     *   möglich – mit [DetectionConfig.requireRedRing] werden dann keine Tempo-Schilder gemeldet).
     */
    fun analyze(
        timestampMs: Long,
        imageWidth: Int,
        imageHeight: Int,
        ocr: OcrResult,
        image: RgbImage?,
    ): FrameObservation {
        val searchBottom = imageHeight * config.searchAreaBottom

        val speedSigns = SpeedLimitMatcher.findCandidates(ocr, config.minSpeedScore)
            .filter { it.box.centerY <= searchBottom }
            .mapNotNull { candidate ->
                val ring = image?.let { ringVerifier.findRing(it, candidate.box) }
                if (config.requireRedRing && ring == null) {
                    null
                } else {
                    SpeedSign(candidate.valueKmh, candidate.box, candidate.score, ring)
                }
            }

        val iglMatches = IglTextMatcher.findMatches(ocr, config.minIglScore)
            .filter { it.box.centerY <= searchBottom }
            .filterNot { match -> speedSigns.any { it.box.intersectionOverUnion(match.box) > 0f } }
            .map { match ->
                // IG-L gibt es nur mit 100 km/h → nur ein 100er-Schild in der Nähe stützt den Treffer.
                val near = speedSigns.any { sign ->
                    sign.valueKmh == IGL_LIMIT_KMH &&
                        match.box.centerDistanceTo(sign.box) <= config.nearSignDistanceFactor * sign.box.height
                }
                match.copy(nearSpeedSign = near)
            }

        return FrameObservation(timestampMs, imageWidth, imageHeight, iglMatches, speedSigns)
    }
}
