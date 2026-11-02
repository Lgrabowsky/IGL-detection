package io.github.lgrabowsky.igldetection.camera

import androidx.annotation.OptIn
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing

/** Eine auswählbare Kamera, wie sie in den Einstellungen angezeigt wird. */
data class CameraOption(val key: String, val label: String, val isExternal: Boolean)

/**
 * Listet die verfügbaren Kameras auf – inklusive externer USB-Kameras (UVC), sofern Android sie
 * als Kamera bereitstellt (Camera2 „EXTERNAL“; auf vielen Geräten ab Android 14).
 */
object CameraOptions {

    const val AUTO = "auto"

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
     * z. B. abgesteckte Webcam): externe Kamera bevorzugt, sonst Rückkamera.
     */
    fun select(cameras: List<Pair<CameraOption, CameraInfo>>, key: String): Pair<CameraOption, CameraInfo>? =
        cameras.firstOrNull { it.first.key == key }
            ?: cameras.firstOrNull { it.first.isExternal }
            ?: cameras.firstOrNull { it.first.key.startsWith("back") }
            ?: cameras.firstOrNull()
}
