package io.github.lgrabowsky.igl.core

import kotlin.math.max

/** IG-L-Tempolimits gibt es auf Autobahnen nur als 100 km/h. */
const val IGL_LIMIT_KMH = 100

/** Der aus mehreren Bildern abgeleitete Zustand. */
sealed interface IglStatus {

    /** Kein gültiger Befund (Start, oder letzter Befund abgelaufen). */
    data object Unknown : IglStatus

    /**
     * Ein 100er-Schild mit Zusatz „IG-L“ wurde erkannt. Das Limit gilt nicht für Elektrofahrzeuge.
     */
    data class IglActive(
        val sinceMs: Long,
        val lastConfirmedMs: Long,
    ) : IglStatus {
        val limitKmh: Int get() = IGL_LIMIT_KMH
    }

    /** Ein Tempolimit-Schild ohne IG-L wurde erkannt (100 ohne Zusatz oder ein anderes Limit). */
    data class NoIgl(
        val limitKmh: Int,
        val sinceMs: Long,
        val lastConfirmedMs: Long,
    ) : IglStatus
}

data class TrackerConfig(
    /** Zeitfenster, in dem „IG-L“-Treffer aufsummiert werden. */
    val evidenceWindowMs: Long = 3_000,
    /** Summe der Treffer-Scores im Zeitfenster, ab der „IG-L“ als gelesen gilt (≈ 2 sichere Bilder). */
    val activationEvidence: Float = 1.8f,
    /** Gewichtung eines „IG-L“-Treffers direkt neben einem 100er-Schild. */
    val nearSignBoost: Float = 1.5f,
    /** Ein Schild gilt als passiert, wenn es so lange nicht mehr gesehen wurde. */
    val signGapMs: Long = 1_500,
    /** In so vielen Bildern muss ein Tempowert gelesen werden, damit das Schild als erkannt gilt. */
    val minSignFrames: Int = 2,
    /** So weit dürfen „IG-L“ und das zugehörige 100er-Schild zeitlich auseinander liegen. */
    val iglLeadMs: Long = 5_000,
    /** Nach dieser Zeit ohne erneute Bestätigung verfällt ein Befund. */
    val stateValidityMs: Long = 15 * 60_000L,
)

/**
 * Fasst die Einzelbild-Auswertungen ([FrameObservation]) über die Zeit zu einem [IglStatus]
 * zusammen. Regeln:
 *
 * - **IG-L aktiv** nur, wenn „IG-L“ in mehreren Bildern kurz hintereinander gelesen wurde
 *   (Score-Summe ≥ [TrackerConfig.activationEvidence]) *und* in derselben Zeit ein 100er-Schild
 *   zu sehen war – IG-L gibt es nur mit 100 km/h.
 * - **Jedes andere Tempolimit** (80, 60, 130 …) beendet IG-L sofort, sobald es erkannt ist
 *   (in ≥ [TrackerConfig.minSignFrames] Bildern gelesen).
 * - **100 ohne IG-L:** Wurde ein 100er-Schild passiert, ohne dass dazu „IG-L“ bestätigt wurde,
 *   endet IG-L ebenfalls. (Ob ein Zusatz dabei ist, steht erst fest, wenn das Schild vorbei ist –
 *   der kleine Zusatz wird oft erst beim Näherkommen lesbar.)
 * - **Verfall:** ohne erneute Bestätigung wird der Status nach [TrackerConfig.stateValidityMs]
 *   wieder „unbekannt“.
 *
 * Im Zweifel wird also eher *kein* IG-L angezeigt – eine fälschliche IG-L-Anzeige wäre der
 * gefährlichere Fehler.
 *
 * Alle Zeitangaben in Millisekunden einer monotonen Uhr (z. B. `SystemClock.elapsedRealtime()`).
 */
class IglStateTracker(config: TrackerConfig = TrackerConfig()) {

    @Volatile
    var config: TrackerConfig = config

    var status: IglStatus = IglStatus.Unknown
        private set

    private class Evidence(val timeMs: Long, val value: Float)

    /** Ein Tempowert, der gerade (mit Unterbrechungen < signGapMs) zu sehen ist. */
    private class SignTrack(val valueKmh: Int, val firstSeenMs: Long) {
        var lastSeenMs = firstSeenMs
        var frames = 0
    }

    private val evidence = ArrayDeque<Evidence>()
    private val tracks = HashMap<Int, SignTrack>()
    private var lastHundredSeenMs = Long.MIN_VALUE / 2
    private var lastIglConfirmedMs = Long.MIN_VALUE / 2

    /** Aktuelle Summe der IG-L-Hinweise im Zeitfenster (für Debug-Anzeigen). */
    @get:Synchronized
    val currentEvidence: Float
        get() = evidence.sumOf { it.value.toDouble() }.toFloat()

    @Synchronized
    fun update(observation: FrameObservation): IglStatus {
        val now = observation.timestampMs
        val cfg = config
        finishPassedSigns(now, cfg)
        pruneEvidence(now, cfg)

        for (value in observation.speedSigns.map { it.valueKmh }.distinct()) {
            val track = tracks.getOrPut(value) { SignTrack(value, now) }
            track.lastSeenMs = now
            track.frames++
            if (value == IGL_LIMIT_KMH) lastHundredSeenMs = now
        }

        val frameEvidence = observation.iglMatches.maxOfOrNull {
            it.score * if (it.nearSpeedSign) cfg.nearSignBoost else 1f
        } ?: 0f
        if (frameEvidence > 0f) evidence.addLast(Evidence(now, frameEvidence))

        val otherLimit = recognizedOtherLimit(cfg)
        if (otherLimit != null) {
            // Ein anderes Tempolimit ist zu sehen → kein IG-L, egal was sonst gelesen wurde.
            setNoIgl(otherLimit.valueKmh, now)
        } else if (currentEvidence >= cfg.activationEvidence && now - lastHundredSeenMs <= cfg.iglLeadMs) {
            lastIglConfirmedMs = now
            status = when (val s = status) {
                is IglStatus.IglActive -> s.copy(lastConfirmedMs = now)
                else -> IglStatus.IglActive(sinceMs = now, lastConfirmedMs = now)
            }
        }

        expire(now, cfg)
        return status
    }

    /** Ohne neues Bild aufrufen (z. B. periodisch), damit Schilder als passiert gelten und Befunde verfallen. */
    @Synchronized
    fun tick(nowMs: Long): IglStatus {
        val cfg = config
        finishPassedSigns(nowMs, cfg)
        pruneEvidence(nowMs, cfg)
        expire(nowMs, cfg)
        return status
    }

    @Synchronized
    fun reset() {
        evidence.clear()
        tracks.clear()
        lastHundredSeenMs = Long.MIN_VALUE / 2
        lastIglConfirmedMs = Long.MIN_VALUE / 2
        status = IglStatus.Unknown
    }

    private fun recognizedOtherLimit(cfg: TrackerConfig): SignTrack? =
        tracks.values
            .filter { it.valueKmh != IGL_LIMIT_KMH && it.frames >= cfg.minSignFrames }
            .maxByOrNull { it.lastSeenMs }

    private fun pruneEvidence(now: Long, cfg: TrackerConfig) {
        while (evidence.isNotEmpty() && now - evidence.first().timeMs > cfg.evidenceWindowMs) {
            evidence.removeFirst()
        }
    }

    private fun finishPassedSigns(now: Long, cfg: TrackerConfig) {
        // Bei gleichzeitig passierten Schildern zuletzt die „anderen“ Limits auswerten – sie gewinnen.
        val passed = tracks.values
            .filter { now - it.lastSeenMs > cfg.signGapMs }
            .sortedWith(compareBy({ it.lastSeenMs }, { it.valueKmh != IGL_LIMIT_KMH }))
        for (track in passed) {
            tracks.remove(track.valueKmh)
            if (track.frames < cfg.minSignFrames) continue // nur kurz gelesen → ignorieren

            if (track.valueKmh != IGL_LIMIT_KMH) {
                setNoIgl(track.valueKmh, track.lastSeenMs)
                continue
            }
            val iglBelongsToSign = lastIglConfirmedMs >= track.firstSeenMs - cfg.iglLeadMs
            val current = status
            when {
                iglBelongsToSign && current is IglStatus.IglActive ->
                    status = current.copy(lastConfirmedMs = max(current.lastConfirmedMs, track.lastSeenMs))

                // IG-L wurde zwar gelesen, danach aber ein anderes Limit erkannt → so lassen.
                iglBelongsToSign -> Unit

                // 100er-Schild ohne IG-L passiert.
                recognizedOtherLimit(cfg) == null -> setNoIgl(IGL_LIMIT_KMH, track.lastSeenMs)
            }
        }
    }

    private fun setNoIgl(limitKmh: Int, timeMs: Long) {
        val current = status
        status = if (current is IglStatus.NoIgl && current.limitKmh == limitKmh) {
            current.copy(lastConfirmedMs = max(current.lastConfirmedMs, timeMs))
        } else {
            IglStatus.NoIgl(limitKmh, sinceMs = timeMs, lastConfirmedMs = timeMs)
        }
    }

    private fun expire(now: Long, cfg: TrackerConfig) {
        val lastConfirmed = when (val s = status) {
            is IglStatus.IglActive -> s.lastConfirmedMs
            is IglStatus.NoIgl -> s.lastConfirmedMs
            IglStatus.Unknown -> return
        }
        if (now - lastConfirmed > cfg.stateValidityMs) status = IglStatus.Unknown
    }
}
