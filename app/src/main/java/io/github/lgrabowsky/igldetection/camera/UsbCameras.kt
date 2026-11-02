package io.github.lgrabowsky.igldetection.camera

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Findet angesteckte USB-Videogeräte (UVC, z. B. Logitech StreamCam) – unabhängig davon, ob
 * Android sie als Kamera bereitstellt. So kann die App erklären, warum eine angesteckte Webcam
 * nicht in der Kameraliste auftaucht.
 */
object UsbCameras {

    fun connectedNames(context: Context): List<String> = try {
        val usb = context.getSystemService(UsbManager::class.java)
        usb?.deviceList?.values.orEmpty()
            .filter { isVideoDevice(it) }
            .map { device -> device.productName?.trim()?.takeIf { it.isNotEmpty() } ?: "USB-Kamera" }
    } catch (e: Exception) {
        Log.w("UsbCameras", "USB-Geräte konnten nicht gelesen werden", e)
        emptyList()
    }

    fun isVideoDevice(device: UsbDevice): Boolean =
        device.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
            (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }
}
