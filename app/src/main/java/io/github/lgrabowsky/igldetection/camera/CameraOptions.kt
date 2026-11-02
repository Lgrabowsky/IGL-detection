package io.github.lgrabowsky.igldetection.camera

import androidx.annotation.OptIn
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing

/** Eine auswählbare Kamera, wie sie in den Einstellungen angezeigt wird. */
data class CameraOption(
    val key: String,
    val label: String,
    val isExternal: Boolean,
    /** USB-Webcam, die über den App-eigenen Treiber ([UvcCameraSource]) angesprochen wird. */
    val usesAppDriver: Boolean = false,
)

/**
 * Listet die verfügbaren Kameras auf – inklusive externer USB-Kameras (UVC): entweder von Android
 * bereitgestellt (Camera2 „EXTERNAL“; auf vielen Geräten ab Android 14) oder über den
 * App-eigenen USB-Treiber.
 */
object CameraOptions {

    const val AUTO = "auto"
    const val APP_DRIVER = "usb-app-driver"

    /** Eintrag für eine USB-Webcam, die nur über den App-eigenen Treiber nutzbar ist. */
    fun appDriverOption(deviceName: String) = CameraOption(
        key = APP_DRIVER,
        label = "$deviceName (USB, App-Treiber)",
        isExternal = true,
        usesAppDriver = true,
    )

    @OptIn(ExperimentalLensFacing::class)
    fun describe(infos: List<CameraInfo>): List<Pair<CameraOption, CameraInfo>> {
        val perFacing = HashMap<Int, Int>()
        return infos.map { info ->
            val facing = info.lensFacing
            val number = perFacing.merge(facing, 1, Int::plus) ?: 1
            val (prefix, label) = when (facing) {
                CameraSelector.LENS_FACING_BACK -> "back" to "Rückkamera"
                CameraSelector.LENS_FACING_FRONT -> "front" to "Frontkamera"
                CameraSelector.LENS_FACING_EXTERNAL -> "external" to "Externe Kamera (USB)"
                else -> "other" to "Kamera"
            }
            val option = CameraOption(
                key = "$prefix-$number",
                label = if (number > 1) "$label $number" else label,
                isExternal = facing == CameraSelector.LENS_FACING_EXTERNAL,
            )
            option to info
        }
    }

    /**
     * Wählt die Kamera zu [key]. Bei [AUTO] (oder wenn die gespeicherte Kamera nicht mehr da ist,
     * z. B. abgesteckte Webcam): USB-Webcam bevorzugt (über Android, sonst App-Treiber), sonst
     * Rückkamera.
     */
    fun select(options: List<CameraOption>, key: String): CameraOption? =
        options.firstOrNull { it.key == key }
            ?: options.firstOrNull { it.isExternal && !it.usesAppDriver }
            ?: options.firstOrNull { it.usesAppDriver }
            ?: options.firstOrNull { it.key.startsWith("back") }
            ?: options.firstOrNull()
}
