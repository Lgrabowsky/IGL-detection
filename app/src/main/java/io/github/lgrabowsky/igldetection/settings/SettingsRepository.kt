package io.github.lgrabowsky.igldetection.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** Speichert die [AppSettings] in den SharedPreferences. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("igl_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        write(state.updateAndGet(transform))
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            cameraKey = prefs.getString(KEY_CAMERA, d.cameraKey) ?: d.cameraKey,
            zoomRatio = prefs.getFloat(KEY_ZOOM, d.zoomRatio),
            externalRotationDegrees = prefs.getInt(KEY_EXTERNAL_ROTATION, d.externalRotationDegrees),
            highResolution = prefs.getBoolean(KEY_HIGH_RES, d.highResolution),
            evSpeedKmh = prefs.getInt(KEY_EV_SPEED, d.evSpeedKmh),
            speechEnabled = prefs.getBoolean(KEY_SPEECH, d.speechEnabled),
            requireRedRing = prefs.getBoolean(KEY_RED_RING, d.requireRedRing),
            searchAreaBottom = prefs.getFloat(KEY_SEARCH_AREA, d.searchAreaBottom),
            validityMinutes = prefs.getInt(KEY_VALIDITY, d.validityMinutes),
            showDebugOverlay = prefs.getBoolean(KEY_DEBUG, d.showDebugOverlay),
            autoPip = prefs.getBoolean(KEY_AUTO_PIP, d.autoPip),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit {
            putString(KEY_CAMERA, s.cameraKey)
            putFloat(KEY_ZOOM, s.zoomRatio)
            putInt(KEY_EXTERNAL_ROTATION, s.externalRotationDegrees)
            putBoolean(KEY_HIGH_RES, s.highResolution)
            putInt(KEY_EV_SPEED, s.evSpeedKmh)
            putBoolean(KEY_SPEECH, s.speechEnabled)
            putBoolean(KEY_RED_RING, s.requireRedRing)
            putFloat(KEY_SEARCH_AREA, s.searchAreaBottom)
            putInt(KEY_VALIDITY, s.validityMinutes)
            putBoolean(KEY_DEBUG, s.showDebugOverlay)
            putBoolean(KEY_AUTO_PIP, s.autoPip)
        }
    }

    private companion object {
        const val KEY_CAMERA = "camera"
        const val KEY_ZOOM = "zoom"
        const val KEY_EXTERNAL_ROTATION = "external_rotation"
        const val KEY_HIGH_RES = "high_resolution"
        const val KEY_EV_SPEED = "ev_speed"
        const val KEY_SPEECH = "speech"
        const val KEY_RED_RING = "red_ring"
        const val KEY_SEARCH_AREA = "search_area"
        const val KEY_VALIDITY = "validity_minutes"
        const val KEY_DEBUG = "debug_overlay"
        const val KEY_AUTO_PIP = "auto_pip"
    }
}
