package io.github.lgrabowsky.igldetection

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.lgrabowsky.igldetection.ui.IglApp
import io.github.lgrabowsky.igldetection.ui.IglTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: DetectionViewModel by viewModels()
    private var inPictureInPicture by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Display bleibt an, solange die App im Vordergrund ist (Halterung im Auto).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        inPictureInPicture = isInPictureInPictureMode

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.settings.collect { updatePictureInPictureParams(it.autoPip) }
            }
        }

        setContent {
            IglTheme {
                IglApp(viewModel, inPictureInPicture)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kamera-Berechtigung kann sich inzwischen geändert haben.
        updatePictureInPictureParams(viewModel.settings.value.autoPip)
    }

    /** Ab Android 12 wechselt das System selbst in Bild-im-Bild, wenn die App verlassen wird. */
    private fun updatePictureInPictureParams(autoEnter: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(pictureInPictureParams(autoEnter && hasCameraPermission()))
        }
    }

    /** Android 8–11: Bild-im-Bild beim Drücken von „Home“ manuell starten. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            viewModel.settings.value.autoPip &&
            hasCameraPermission()
        ) {
            enterPictureInPictureMode(pictureInPictureParams(autoEnter = false))
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    private fun pictureInPictureParams(autoEnter: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(autoEnter)
            }
            .build()

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
