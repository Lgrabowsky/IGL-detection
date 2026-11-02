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
        limit: Int? = null,
        signHeight: Int = 40,
        signCenterY: Int = 150,
    ) = FrameObservation(
        timestampMs = t,
        imageWidth = 1280,
        imageHeight = 720,
        iglMatches = listOfNotNull(igl?.let { IglMatch("IG-L", box(600, 200, 60, 20), it, near) }),
        speedSigns = listOfNotNull(
            limit?.let {
                SpeedSign(it, box(500, signCenterY - signHeight / 2, signHeight * 3 / 2, signHeight), 1f, null)
            },
        ),
    )

    /** Simuliert ~6 Bilder/s über [fromMs]..[toMs]. */
    private fun drive(fromMs: Long, toMs: Long, make: (Long) -> FrameObservation): IglStatus {
        var t = fromMs
        var status = tracker.status
        while (t <= toMs) {
            status = tracker.update(make(t))
            t += 160
        }
        return status
    }

    @Test
    fun `ein einzelner Treffer loest nichts aus`() {
        tracker.update(frame(0, igl = 1f))
        assertIs<Unknown>(tracker.update(frame(160)))
    }

    @Test
    fun `zwei sichere Treffer kurz hintereinander aktivieren IG-L`() {
        tracker.update(frame(0, igl = 1f))
        val status = tracker.update(frame(160, igl = 1f))
        assertIs<IglActive>(status)
        assertEquals(null, status.limitKmh)
    }

    @Test
    fun `Treffer, die zu weit auseinander liegen, aktivieren nicht`() {
        tracker.update(frame(0, igl = 1f))
        assertIs<Unknown>(tracker.update(frame(5_000, igl = 1f)))
    }

    @Test
    fun `Schilderbruecke mit IG-L - Status bleibt nach dem Passieren aktiv`() {
        drive(0, 2_000) { frame(it, igl = 1f, near = true, limit = 100) }
        val status = tracker.tick(5_000)
        assertIs<IglActive>(status)
        assertEquals(100, status.limitKmh)
        assertEquals(2_000L - 2_000L % 160, status.lastConfirmedMs)
    }

    @Test
    fun `gut sichtbares Schild ohne IG-L hebt den Status auf`() {
        drive(0, 2_000) { frame(it, igl = 1f, near = true, limit = 100) }
        drive(60_000, 62_000) { frame(it, limit = 130) }

        val status = tracker.tick(65_000)
        assertIs<NoIgl>(status)
        assertEquals(130, status.limitKmh)
    }

    @Test
    fun `nur klein oder weit unten gesehenes Schild hebt IG-L nicht auf`() {
        drive(0, 2_000) { frame(it, igl = 1f, near = true, limit = 100) }
        drive(60_000, 62_000) { frame(it, limit = 80, signHeight = 12) } // zu klein
        drive(90_000, 92_000) { frame(it, limit = 80, signCenterY = 600) } // LKW-Aufkleber

        val status = tracker.tick(95_000)
        assertIs<IglActive>(status)
        assertEquals(100, status.limitKmh)
    }

    @Test
    fun `IG-L zuerst gelesen, Zahl erst beim Naeherkommen`() {
        drive(0, 1_000) { frame(it, igl = 1f) }
        drive(1_200, 3_000) { frame(it, limit = 100) }

        val status = tracker.tick(6_000)
        assertIs<IglActive>(status)
        assertEquals(100, status.limitKmh)
    }

    @Test
    fun `unsicherer IG-L-Hinweis verhindert eine Aufhebung`() {
        drive(0, 2_000) { frame(it, igl = 1f, near = true, limit = 100) }
        drive(60_000, 62_000) { frame(it, limit = 100, igl = if (it == 60_960L) 0.6f else null) }

        assertIs<IglActive>(tracker.tick(65_000))
    }

    @Test
    fun `Schild ohne IG-L ohne vorherigen Befund`() {
        drive(0, 2_000) { frame(it, limit = 100) }
        val status = tracker.tick(4_000)
        assertIs<NoIgl>(status)
        assertEquals(100, status.limitKmh)
    }

    @Test
    fun `Zahl in nur einem Bild zaehlt nicht`() {
        tracker.update(frame(0, limit = 100))
        assertEquals(Unknown(), tracker.tick(4_000))
    }

    @Test
    fun `undeutliches Schild ohne Befund merkt sich nur das Limit`() {
        drive(0, 2_000) { frame(it, limit = 80, signHeight = 12) }
        assertEquals(Unknown(lastLimitKmh = 80), tracker.tick(4_000))
    }

    @Test
    fun `Befund verfaellt nach der eingestellten Zeit`() {
        tracker.config = TrackerConfig(stateValidityMs = 60_000)
        drive(0, 2_000) { frame(it, igl = 1f, near = true, limit = 100) }
        assertIs<IglActive>(tracker.tick(50_000))
        assertEquals(Unknown(), tracker.tick(70_000))
    }

    @Test
    fun `erneute Bestaetigung verlaengert die Gueltigkeit`() {
        tracker.config = TrackerConfig(stateValidityMs = 60_000)
        drive(0, 2_000) { frame(it, igl = 1f, near = true, limit = 100) }
        drive(50_000, 52_000) { frame(it, igl = 1f, near = true, limit = 100) }
        assertIs<IglActive>(tracker.tick(100_000))
    }

    @Test
    fun `reset setzt alles zurueck`() {
        drive(0, 2_000) { frame(it, igl = 1f, near = true, limit = 100) }
        tracker.reset()
        assertEquals(Unknown(), tracker.status)
    }
}
