package io.github.lgrabowsky.igl.core

/** Der aus mehreren Bildern abgeleitete Zustand. */
sealed interface IglStatus {

    /** Kein gültiger Befund (Start, oder letzter Befund abgelaufen). */
    data class Unknown(
        /** Zuletzt gesehenes Tempolimit, bei dem der IG-L-Status unklar blieb. */
        val lastLimitKmh: Int? = null,
    ) : IglStatus

    /**
     * Ein Tempolimit mit Zusatz „IG-L“ wurde erkannt. Es gilt nicht für Elektrofahrzeuge.
     */
    data class IglActive(
        /** Angezeigtes IG-L-Tempolimit, null wenn nur „IG-L“ gelesen wurde. */
        val limitKmh: Int?,
        val sinceMs: Long,
        val lastConfirmedMs: Long,
    ) : IglStatus

    /** Ein Tempolimit-Schild wurde gut sichtbar passiert – ohne Zusatz „IG-L“. */
    data class NoIgl(
        val limitKmh: Int,
        val sinceMs: Long,
        val lastConfirmedMs: Long,
    ) : IglStatus
}

data class TrackerConfig(
    /** Zeitfenster, in dem „IG-L“-Treffer aufsummiert werden. */
    val evidenceWindowMs: Long = 3_000,
    /** Summe der Treffer-Scores im Zeitfenster, ab der „IG-L aktiv“ gilt (≈ 2 sichere Bilder). */
    val activationEvidence: Float = 1.8f,
    /** Gewichtung eines „IG-L“-Treffers direkt neben einem Tempolimit-Schild. */
    val nearSignBoost: Float = 1.5f,
    /** Ein Schild gilt als passiert, wenn es so lange nicht mehr gesehen wurde. */
    val signGapMs: Long = 1_500,
    /** In so vielen Bildern muss ein Tempowert gelesen werden, damit er zählt. */
    val minSignFrames: Int = 2,
    /** „IG-L“, das bis zu so lange vor einem Schild bestätigt wurde, gehört zu diesem Schild. */
    val iglLeadMs: Long = 5_000,
    /** Ein Schild zählt nur dann als „ohne IG-L“, wenn währenddessen fast kein IG-L-Hinweis kam. */
    val maxIglEvidenceForNegative: Float = 0.5f,
    /**
     * „Ohne IG-L“ wird nur entschieden, wenn das Schild groß genug und hoch genug im Bild war –
     * sonst war der kleinere IG-L-Zusatz evtl. nur nicht lesbar (bzw. es war ein LKW-Aufkleber).
     */
    val conclusiveMinTextHeightPx: Int = 20,
    val conclusiveMaxCenterY: Float = 0.5f,
    /** Nach dieser Zeit ohne erneute Bestätigung verfällt ein Befund. */
    val stateValidityMs: Long = 15 * 60_000L,
)

/**
 * Fasst die Einzelbild-Auswertungen ([FrameObservation]) über die Zeit zu einem stabilen
 * [IglStatus] zusammen:
 *
 * - **Aktivierung:** „IG-L“ muss in mehreren Bildern kurz hintereinander gelesen werden
 *   (Summe der Scores im Zeitfenster ≥ [TrackerConfig.activationEvidence]). Ein einzelner
 *   OCR-Fehler löst also nichts aus.
 * - **Deaktivierung:** erst, wenn ein Tempolimit-Schild vollständig passiert wurde (eine
 *   „Schild-Episode“ ist vorbei), das gut lesbar war und bei dem kein „IG-L“ zu sehen war.
 * - **Verfall:** ohne erneute Bestätigung wird der Status nach
 *   [TrackerConfig.stateValidityMs] wieder „unbekannt“.
 *
 * Alle Zeitangaben in Millisekunden einer monotonen Uhr (z. B. `SystemClock.elapsedRealtime()`).
 */
class IglStateTracker(config: TrackerConfig = TrackerConfig()) {

    @Volatile
    var config: TrackerConfig = config

    var status: IglStatus = IglStatus.Unknown()
        private set

    private class Evidence(val timeMs: Long, val value: Float)

    /** Zeitraum, in dem ein (oder mehrere gleichzeitig sichtbare) Tempolimit-Schilder zu sehen sind. */
    private class SignEpisode(val startMs: Long, var iglEvidence: Float) {
        var lastSeenMs = startMs
        val framesPerLimit = HashMap<Int, Int>()
        var conclusive = false

        fun dominantLimit(minFrames: Int): Int? =
            framesPerLimit.filterValues { it >= minFrames }.maxByOrNull { it.value }?.key
    }

    private val evidence = ArrayDeque<Evidence>()
    private var episode: SignEpisode? = null
    private var lastLimitKmh: Int? = null
    private var lastLimitMs: Long = Long.MIN_VALUE

    /** Aktuelle Summe der IG-L-Hinweise im Zeitfenster (für Debug-Anzeigen). */
    @get:Synchronized
    val currentEvidence: Float
        get() = evidence.sumOf { it.value.toDouble() }.toFloat()

    @Synchronized
    fun update(observation: FrameObservation): IglStatus {
        val now = observation.timestampMs
        val cfg = config
        finishEpisodeIfPassed(now, cfg)
        pruneEvidence(now, cfg)

        val frameEvidence = observation.iglMatches.maxOfOrNull {
            it.score * if (it.nearSpeedSign) cfg.nearSignBoost else 1f
        } ?: 0f

        if (observation.speedSigns.isNotEmpty()) {
            val ep = episode ?: SignEpisode(now, iglEvidence = currentEvidence).also { episode = it }
            ep.lastSeenMs = now
            observation.speedSigns.map { it.valueKmh }.distinct().forEach { ep.framesPerLimit.merge(it, 1, Int::plus) }
            if (observation.speedSigns.any { isConclusive(it, observation, cfg) }) ep.conclusive = true
        }

        if (frameEvidence > 0f) {
            evidence.addLast(Evidence(now, frameEvidence))
            episode?.let { it.iglEvidence += frameEvidence }
        }

        if (currentEvidence >= cfg.activationEvidence) {
            val limit = episode?.dominantLimit(1)
                ?: lastLimitKmh.takeIf { now - lastLimitMs <= cfg.evidenceWindowMs * 2 }
            status = when (val s = status) {
                is IglStatus.IglActive -> s.copy(limitKmh = limit ?: s.limitKmh, lastConfirmedMs = now)
                else -> IglStatus.IglActive(limit, sinceMs = now, lastConfirmedMs = now)
            }
        }

        expire(now, cfg)
        return status
    }

    /** Ohne neues Bild aufrufen (z. B. periodisch), damit Schild-Episoden enden und Befunde verfallen. */
    @Synchronized
    fun tick(nowMs: Long): IglStatus {
        val cfg = config
        finishEpisodeIfPassed(nowMs, cfg)
        pruneEvidence(nowMs, cfg)
        expire(nowMs, cfg)
        return status
    }

    @Synchronized
    fun reset() {
        evidence.clear()
        episode = null
        lastLimitKmh = null
        lastLimitMs = Long.MIN_VALUE
        status = IglStatus.Unknown()
    }

    private fun pruneEvidence(now: Long, cfg: TrackerConfig) {
        while (evidence.isNotEmpty() && now - evidence.first().timeMs > cfg.evidenceWindowMs) {
            evidence.removeFirst()
        }
    }

    private fun isConclusive(sign: SpeedSign, observation: FrameObservation, cfg: TrackerConfig): Boolean =
        sign.box.height >= cfg.conclusiveMinTextHeightPx &&
            sign.box.centerY <= observation.imageHeight * cfg.conclusiveMaxCenterY

    private fun finishEpisodeIfPassed(now: Long, cfg: TrackerConfig) {
        val ep = episode ?: return
        if (now - ep.lastSeenMs <= cfg.signGapMs) return
        episode = null

        val limit = ep.dominantLimit(cfg.minSignFrames) ?: return
        lastLimitKmh = limit
        lastLimitMs = ep.lastSeenMs

        val current = status
        val iglBelongsToSign = current is IglStatus.IglActive &&
            current.lastConfirmedMs >= ep.startMs - cfg.iglLeadMs
        status = when {
            iglBelongsToSign -> (current as IglStatus.IglActive).copy(limitKmh = limit)

            ep.conclusive && ep.iglEvidence <= cfg.maxIglEvidenceForNegative ->
                if (current is IglStatus.NoIgl && current.limitKmh == limit) {
                    current.copy(lastConfirmedMs = ep.lastSeenMs)
                } else {
                    IglStatus.NoIgl(limit, sinceMs = ep.lastSeenMs, lastConfirmedMs = ep.lastSeenMs)
                }

            // Nicht eindeutig: Zustand beibehalten, nur das zuletzt gesehene Limit merken.
            current is IglStatus.Unknown -> IglStatus.Unknown(limit)
            else -> current
        }
    }

    private fun expire(now: Long, cfg: TrackerConfig) {
        val s = status
        val lastConfirmed = when (s) {
            is IglStatus.IglActive -> s.lastConfirmedMs
            is IglStatus.NoIgl -> s.lastConfirmedMs
            is IglStatus.Unknown -> return
        }
        if (now - lastConfirmed > cfg.stateValidityMs) status = IglStatus.Unknown()
    }
}
