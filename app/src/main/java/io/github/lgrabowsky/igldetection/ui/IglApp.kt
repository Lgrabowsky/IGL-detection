package io.github.lgrabowsky.igldetection.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lgrabowsky.igldetection.DetectionViewModel
import io.github.lgrabowsky.igldetection.camera.CameraController

@Composable
fun IglApp(viewModel: DetectionViewModel, inPictureInPicture: Boolean) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // Erneut prüfen, wenn die Berechtigung in den System-Einstellungen erteilt wurde.
    LifecycleResumeEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }

    if (hasCameraPermission) {
        MainScreen(viewModel, inPictureInPicture)
    } else {
        PermissionScreen(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
    }
}

@Composable
private fun MainScreen(viewModel: DetectionViewModel, inPictureInPicture: Boolean) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    val debug by viewModel.debug.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                // Die Vorschau bleibt auch im Bild-im-Bild-Modus bestehen, sonst stoppt die Kamera.
                CameraPreview(viewModel, Modifier.fillMaxSize())
                if (settings.showDebugOverlay && !inPictureInPicture) {
                    DetectionOverlay(debug, settings.searchAreaBottom, Modifier.fillMaxSize())
                }
                val cameraInfo = camera.info
                if (!inPictureInPicture && cameraInfo != null) {
                    Text(
                        text = cameraInfo,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(8.dp)
                            .background(Color(0xCC263238), MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                camera.error?.let { error ->
                    Text(
                        text = error,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp),
                    )
                }
                val zoomRange = camera.zoomRange
                if (!inPictureInPicture && zoomRange != null && zoomRange.endInclusive > zoomRange.start) {
                    ZoomBar(
                        zoom = settings.zoomRatio.coerceIn(zoomRange.start, zoomRange.endInclusive),
                        range = zoomRange,
                        digital = camera.softwareZoom,
                        onZoomChange = { value -> viewModel.updateSettings { it.copy(zoomRatio = value) } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (!inPictureInPicture) {
                StatusPanel(
                    status = status,
                    evSpeedKmh = settings.evSpeedKmh,
                    compact = false,
                    onReset = viewModel::resetStatus,
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.weight(0.42f).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }

        if (inPictureInPicture) {
            // Im kleinen Fenster nur der Status – die Vorschau liegt verdeckt darunter weiter aktiv.
            StatusPanel(
                status = status,
                evSpeedKmh = settings.evSpeedKmh,
                compact = true,
                onReset = {},
                onOpenSettings = {},
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showSettings) {
            SettingsPanel(
                settings = settings,
                camera = camera,
                onChange = viewModel::updateSettings,
                onClose = { showSettings = false },
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.6f)
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }
}

@Composable
private fun CameraPreview(viewModel: DetectionViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    val previewFrame by viewModel.previewFrame.collectAsStateWithLifecycle()

    // USB-Kamera an-/abgesteckt → Kamera neu verbinden.
    var usbEvents by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                usbEvents++
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val controller = remember { CameraController(context.applicationContext) }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    LaunchedEffect(settings.cameraKey, settings.highResolution, usbEvents) {
        val state = controller.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            cameraKey = settings.cameraKey,
            highResolution = settings.highResolution,
            externalRotationDegrees = settings.externalRotationDegrees,
            analyzer = viewModel.analyzer,
            onCameraInfo = viewModel::onCameraInfo,
        )
        viewModel.onCameraState(state)
        controller.setZoom(settings.zoomRatio)
    }
    LaunchedEffect(settings.zoomRatio, camera.active) {
        controller.setZoom(settings.zoomRatio)
    }
    LaunchedEffect(settings.externalRotationDegrees) {
        controller.setExternalRotation(settings.externalRotationDegrees)
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (camera.framePreview) {
            // Externe Kamera: die analysierten (richtig gedrehten) Bilder als Vorschau zeigen.
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                previewFrame?.let { frame ->
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "Kamerabild",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomBar(
    zoom: Float,
    range: ClosedFloatingPointRange<Float>,
    digital: Boolean,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Zoom ${"%.1f".format(zoom)}×" + if (digital) " (digital)" else "", color = Color.White)
        Slider(
            value = zoom,
            onValueChange = onZoomChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Für die Schildererkennung wird die Kamera benötigt. Die Bilder werden nur auf dem " +
                "Gerät ausgewertet und weder gespeichert noch übertragen.",
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) { Text("Kamera erlauben") }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ) { Text("App-Einstellungen öffnen") }
    }
}
