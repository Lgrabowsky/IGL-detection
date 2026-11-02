package io.github.lgrabowsky.igldetection.settings

import io.github.lgrabowsky.igl.core.DetectionConfig
import io.github.lgrabowsky.igl.core.TrackerConfig
import io.github.lgrabowsky.igldetection.camera.CameraOptions

data class AppSettings(
    /** [CameraOptions.AUTO] oder der Schlüssel einer bestimmten Kamera. */
    val cameraKey: String = CameraOptions.AUTO,
    /** Optischer/digitaler Zoom – Schilder werden so früher lesbar. */
    val zoomRatio: Float = 2f,
    /** Drehung des Bildes externer Kameras (0/90/180/270°), z. B. bei kopfüber montierter Webcam. */
    val externalRotationDegrees: Int = 0,
    /** 1920 × 1080 statt 1280 × 720 für die Analyse. */
    val highResolution: Boolean = true,
    /** Für E-Fahrzeuge erlaubtes Tempo, wenn ein IG-L-Limit aktiv ist. */
    val evSpeedKmh: Int = 130,
    val speechEnabled: Boolean = true,
    val requireRedRing: Boolean = true,
    /** Oberer Bildanteil (0..1), in dem nach Schildern gesucht wird. */
    val searchAreaBottom: Float = 0.65f,
    val validityMinutes: Int = 15,
    val showDebugOverlay: Boolean = false,
    /** Beim Verlassen der App automatisch in den Bild-im-Bild-Modus wechseln. */
    val autoPip: Boolean = true,
) {
    fun toDetectionConfig() = DetectionConfig(
        requireRedRing = requireRedRing,
        searchAreaBottom = searchAreaBottom,
    )

    fun toTrackerConfig() = TrackerConfig(
        stateValidityMs = validityMinutes * 60_000L,
    )
}
