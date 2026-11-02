package io.github.lgrabowsky.igldetection

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lgrabowsky.igl.core.IglStateTracker
import io.github.lgrabowsky.igl.core.IglStatus
import io.github.lgrabowsky.igldetection.camera.AnalysisResult
import io.github.lgrabowsky.igldetection.camera.CameraState
import io.github.lgrabowsky.igldetection.camera.SignAnalyzer
import io.github.lgrabowsky.igldetection.settings.AppSettings
import io.github.lgrabowsky.igldetection.settings.SettingsRepository
import io.github.lgrabowsky.igldetection.speech.Announcer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Debug-Informationen zum zuletzt analysierten Bild. */
data class DebugFrame(val result: AnalysisResult, val evidence: Float)

/**
 * Hält die Erkennungs-Pipeline (Analyzer → Tracker) und den daraus abgeleiteten Status.
 * Überlebt Konfigurationswechsel; die Kamera selbst wird von der Oberfläche gebunden.
 */
class DetectionViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    val settings: StateFlow<AppSettings> = settingsRepository.settings

    private val tracker = IglStateTracker(settings.value.toTrackerConfig())
    private val announcer = Announcer(application)
    private val lock = Any()

    private val _status = MutableStateFlow<IglStatus>(IglStatus.Unknown)
    val status: StateFlow<IglStatus> = _status.asStateFlow()

    private val _debug = MutableStateFlow<DebugFrame?>(null)
    val debug: StateFlow<DebugFrame?> = _debug.asStateFlow()

    /** Vorschau aus den analysierten Bildern (nur bei externen Kameras, siehe CameraController). */
    private val _previewFrame = MutableStateFlow<Bitmap?>(null)
    val previewFrame: StateFlow<Bitmap?> = _previewFrame.asStateFlow()

    private val _camera = MutableStateFlow(CameraState())
    val camera: StateFlow<CameraState> = _camera.asStateFlow()

    /** Wird an CameraX übergeben; ruft [onAnalysisResult] auf dem Analyse-Thread auf. */
    val analyzer = SignAnalyzer(
        detectionConfig = { settings.value.toDetectionConfig() },
        onResult = ::onAnalysisResult,
        onPreviewFrame = { _previewFrame.value = it },
    )

    init {
        viewModelScope.launch {
            settings.collect { tracker.config = it.toTrackerConfig() }
        }
        // Schild-Episoden beenden und Befunde verfallen lassen, auch wenn gerade keine Bilder kommen.
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                step { tracker.tick(SystemClock.elapsedRealtime()) }
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsRepository.update(transform)

    fun onCameraState(state: CameraState) {
        _camera.value = state
        if (!state.framePreview) _previewFrame.value = null
    }

    fun resetStatus() = step {
        tracker.reset()
        tracker.status
    }

    private fun onAnalysisResult(result: AnalysisResult) {
        step { tracker.update(result.observation) }
        _debug.value = if (settings.value.showDebugOverlay) DebugFrame(result, tracker.currentEvidence) else null
    }

    private fun step(block: () -> IglStatus) = synchronized(lock) {
        val old = _status.value
        val new = block()
        if (new == old) return@synchronized
        _status.value = new
        if (settings.value.speechEnabled) Announcer.announcementFor(old, new)?.let(announcer::say)
    }

    override fun onCleared() {
        analyzer.close()
        announcer.shutdown()
    }
}
