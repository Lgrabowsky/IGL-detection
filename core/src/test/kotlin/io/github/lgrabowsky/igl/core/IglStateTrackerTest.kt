package io.github.lgrabowsky.igl.core

import io.github.lgrabowsky.igl.core.IglStatus.IglActive
import io.github.lgrabowsky.igl.core.IglStatus.NoIgl
import io.github.lgrabowsky.igl.core.IglStatus.Unknown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IglStateTrackerTest {

    private val tracker = IglStateTracker()

    private fun frame(
        t: Long,
        igl: Float? = null,
        near: Boolean = false,
        vararg limits: Int,
    ) = FrameObservation(
        timestampMs = t,
        imageWidth = 1280,
        imageHeight = 720,
        iglMatches = listOfNotNull(igl?.let { IglMatch("IG-L", box(600, 200, 60, 20), it, near) }),
        speedSigns = limits.mapIndexed { i, limit -> SpeedSign(limit, box(400 + i * 300, 130, 60, 40), 1f, null) },
    )

    /** Simuliert ~6 Bilder/s über [fromMs]..[toMs]. */
    private fun drive(fromMs: Long, toMs: Long, make: (Long) -> FrameObservation): IglStatus {
        var t = fromMs
        while (t <= toMs) {
            tracker.update(make(t))
            t += 160
        }
        return tracker.status
    }

    /** Schilderbrücke „100 | IG-L | 100“, ca. 2 s im Bild. */
    private fun passIglGantry(fromMs: Long) = drive(fromMs, fromMs + 2_000) { frame(it, 1f, true, 100, 100) }

    // --- Aktivierung -------------------------------------------------------------------------

    @Test
    fun `IG-L mit 100er-Schild wird aktiv und bleibt nach dem Passieren aktiv`() {
        assertIs<IglActive>(passIglGantry(0))
        val status = tracker.tick(5_000)
        assertIs<IglActive>(status)
        assertEquals(100, status.limitKmh)
    }

    @Test
    fun `ein einzelner IG-L-Treffer loest nichts aus`() {
        tracker.update(frame(0, 1f, true, 100))
        assertIs<Unknown>(tracker.update(frame(160, null, false, 100)))
    }

    @Test
    fun `IG-L ohne 100er-Schild wird nicht angezeigt`() {
        assertEquals(Unknown, drive(0, 2_000) { frame(it, 1f) })
    }

    @Test
    fun `IG-L zuerst gelesen, 100 kurz danach`() {
        drive(0, 1_000) { frame(it, 1f) }
        assertEquals(Unknown, tracker.status)
        drive(1_200, 3_000) { frame(it, null, false, 100) }

        assertIs<IglActive>(tracker.tick(6_000))
    }

    @Test
    fun `IG-L zusammen mit 80 wird nicht angezeigt`() {
        val status = drive(0, 2_000) { frame(it, 1f, false, 80) }
        assertIs<NoIgl>(status)
        assertEquals(80, status.limitKmh)
    }

    @Test
    fun `IG-L neben 100 und gleichzeitig ein anderes Limit - das andere Limit gewinnt`() {
        val status = drive(0, 2_000) { frame(it, 1f, true, 100, 80) }
        assertIs<NoIgl>(status)
        assertEquals(80, status.limitKmh)
        val afterPassing = tracker.tick(5_000)
        assertIs<NoIgl>(afterPassing)
        assertEquals(80, afterPassing.limitKmh)
    }

    // --- Aufhebung durch andere Schilder ----------------------------------------------------

    @Test
    fun `ein anderes Limit beendet IG-L sofort, noch bevor das Schild passiert ist`() {
        passIglGantry(0)
        tracker.update(frame(60_000, null, false, 80))
        assertIs<IglActive>(tracker.status) // erst ein Bild – könnte ein Lesefehler sein
        val status = tracker.update(frame(60_160, null, false, 80))
        assertIs<NoIgl>(status)
        assertEquals(80, status.limitKmh)
    }

    @Test
    fun `jedes andere Limit beendet IG-L`() {
        for (limit in listOf(30, 40, 50, 60, 70, 80, 90, 110, 120, 130)) {
            tracker.reset()
            passIglGantry(0)
            drive(60_000, 61_000) { frame(it, null, false, limit) }
            val status = tracker.status
            assertIs<NoIgl>(status, "Limit $limit")
            assertEquals(limit, status.limitKmh)
        }
    }

    @Test
    fun `100 ohne IG-L beendet IG-L, sobald das Schild passiert ist`() {
        passIglGantry(0)
        drive(60_000, 62_000) { frame(it, null, false, 100) }
        assertIs<IglActive>(tracker.status) // Zusatz könnte noch lesbar werden

        val status = tracker.tick(65_000)
        assertIs<NoIgl>(status)
        assertEquals(100, status.limitKmh)
    }

    @Test
    fun `100 mit nur unsicherem IG-L-Hinweis zaehlt als 100 ohne IG-L`() {
        passIglGantry(0)
        drive(60_000, 62_000) { frame(it, if (it == 60_960L) 0.6f else null, false, 100) }
        assertIs<NoIgl>(tracker.tick(65_000))
    }

    @Test
    fun `nach einem anderen Limit wird IG-L beim naechsten IG-L-Schild wieder aktiv`() {
        passIglGantry(0)
        drive(60_000, 61_000) { frame(it, null, false, 80) }
        assertIs<NoIgl>(tracker.tick(64_000))

        passIglGantry(120_000)
        assertIs<IglActive>(tracker.tick(125_000))
    }

    @Test
    fun `einmal falsch gelesenes Limit wird ignoriert`() {
        passIglGantry(0)
        tracker.update(frame(60_000, null, false, 80))
        assertIs<IglActive>(tracker.tick(65_000))
    }

    @Test
    fun `100er-Schild ohne IG-L ohne vorherigen Befund`() {
        drive(0, 2_000) { frame(it, null, false, 100) }
        val status = tracker.tick(4_000)
        assertIs<NoIgl>(status)
        assertEquals(100, status.limitKmh)
    }

    @Test
    fun `Zahl in nur einem Bild zaehlt nicht`() {
        tracker.update(frame(0, null, false, 100))
        assertEquals(Unknown, tracker.tick(4_000))
    }

    // --- Gültigkeit ----------------------------------------------------------------------------

    @Test
    fun `Befund verfaellt nach der eingestellten Zeit`() {
        tracker.config = TrackerConfig(stateValidityMs = 60_000)
        passIglGantry(0)
        assertIs<IglActive>(tracker.tick(50_000))
        assertEquals(Unknown, tracker.tick(70_000))
    }

    @Test
    fun `erneute Bestaetigung verlaengert die Gueltigkeit`() {
        tracker.config = TrackerConfig(stateValidityMs = 60_000)
        passIglGantry(0)
        passIglGantry(50_000)
        assertIs<IglActive>(tracker.tick(100_000))
    }

    @Test
    fun `reset setzt alles zurueck`() {
        passIglGantry(0)
        tracker.reset()
        assertEquals(Unknown, tracker.status)
    }
}
