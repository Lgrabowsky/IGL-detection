package io.github.lgrabowsky.igldetection.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.usb.UsbDevice
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import com.serenegiant.usb.Size as UvcSize

/**
 * App-eigener Treiber für USB-Webcams (UVC, z. B. Logitech StreamCam) – für Handys, die eine
 * angesteckte Webcam zwar erkennen, sie aber nicht als Kamera bereitstellen.
 *
 * Nutzt die Bibliothek UVCAndroid (libusb/libuvc/libjpeg-turbo). Ablauf:
 * USB-Gerät gefunden → Android fragt den Nutzer nach USB-Zugriff → Kamera öffnen → Bilder
 * (RGBA) über einen unsichtbaren [ImageReader] abholen → [onFrame] auf einem eigenen Thread.
 * Während eine Analyse läuft, werden neue Bilder verworfen (immer das aktuellste Bild zählt).
 */
class UvcCameraSource(
    context: Context,
    private val highResolution: Boolean,
    /** Wird auf einem Hintergrund-Thread aufgerufen und darf blockieren (Texterkennung). */
    private val onFrame: (Bitmap) -> Unit,
    /** Statusmeldung für die Oberfläche, null = alles in Ordnung. */
    private val onStatus: (String?) -> Unit,
) {
    private val appContext = context.applicationContext
    private val thread = HandlerThread("UvcCamera").apply { start() }
    private val handler = Handler(thread.looper)
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val analysisBusy = AtomicBoolean(false)

    // Nur auf [thread] verwendet:
    private var monitor: USBMonitor? = null
    private var camera: UVCCamera? = null
    private var reader: ImageReader? = null
    private var device: UsbDevice? = null
    private var rowBuffer: ByteBuffer? = null

    private val listener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice) {
            if (camera != null || !UsbCameras.isVideoDevice(device)) return
            onStatus("${device.displayName()}: Bitte den USB-Zugriff erlauben.")
            monitor?.requestPermission(device)
        }

        override fun onDetach(device: UsbDevice) {
            if (device == this@UvcCameraSource.device) {
                closeCamera()
                onStatus("${device.displayName()} wurde abgesteckt.")
            }
        }

        override fun onDeviceOpen(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock, createNew: Boolean) {
            openCamera(device, ctrlBlock)
        }

        override fun onDeviceClose(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
            if (device == this@UvcCameraSource.device) closeCamera()
        }

        override fun onCancel(device: UsbDevice) {
            if (UsbCameras.isVideoDevice(device)) {
                onStatus(
                    "Kein USB-Zugriff auf ${device.displayName()}. Webcam ab- und wieder anstecken " +
                        "und die Nachfrage mit „Zulassen“ bestätigen.",
                )
            }
        }
    }

    /** Beginnt, nach USB-Webcams zu suchen (fragt ggf. nach der USB-Berechtigung). */
    fun start() {
        handler.post {
            if (monitor != null) return@post
            try {
                monitor = USBMonitor(appContext, listener, handler).also { it.register() }
            } catch (e: Throwable) {
                Log.e(TAG, "USB-Überwachung konnte nicht gestartet werden", e)
                onStatus("USB-Treiber konnte nicht gestartet werden: ${e.message}")
            }
        }
    }

    /** Kamera schließen und USB-Überwachung beenden (z. B. wenn die App in den Hintergrund geht). */
    fun stop() {
        handler.post {
            closeCamera()
            monitor?.destroy()
            monitor = null
        }
    }

    fun release() {
        stop()
        handler.post { thread.quitSafely() }
        analysisExecutor.shutdown()
    }

    private fun openCamera(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        if (camera != null) return
        val name = device.displayName()
        try {
            val cam = UVCCamera(UVCParam())
            val result = cam.open(ctrlBlock)
            if (result != 0) {
                cam.destroy()
                onStatus("$name konnte nicht geöffnet werden (Fehler $result).")
                return
            }
            chooseSize(cam.supportedSizeList)?.let { size ->
                try {
                    cam.setPreviewSize(size)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Bildgröße $size nicht möglich, verwende Standard", e)
                }
            }
            val size = cam.previewSize
            val imageReader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 3)
            imageReader.setOnImageAvailableListener(::onImageAvailable, handler)
            // Die Bibliothek startet nur mit einer Ausgabefläche; der ImageReader dient als solche.
            cam.setPreviewDisplay(imageReader.surface)
            cam.startPreview()

            camera = cam
            reader = imageReader
            this.device = device
            Log.i(TAG, "$name geöffnet: ${size.width}×${size.height}")
            onStatus(null)
        } catch (e: Throwable) {
            Log.e(TAG, "USB-Kamera konnte nicht gestartet werden", e)
            closeCamera()
            onStatus("$name konnte nicht gestartet werden: ${e.message}")
        }
    }

    private fun closeCamera() {
        camera?.let {
            try {
                it.stopPreview()
                it.destroy()
            } catch (e: Throwable) {
                Log.w(TAG, "Fehler beim Schließen der USB-Kamera", e)
            }
        }
        camera = null
        reader?.close()
        reader = null
        device = null
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            Log.w(TAG, "Bild konnte nicht gelesen werden", e)
            null
        } ?: return
        image.use {
            // Analyse läuft noch → dieses Bild verwerfen (Puffer trotzdem freigeben).
            if (!analysisBusy.compareAndSet(false, true)) return
            val bitmap = try {
                it.toBitmap()
            } catch (e: Exception) {
                Log.w(TAG, "Bild konnte nicht umgewandelt werden", e)
                analysisBusy.set(false)
                return
            }
            analysisExecutor.execute {
                try {
                    onFrame(bitmap)
                } finally {
                    analysisBusy.set(false)
                }
            }
        }
    }

    /** RGBA-Bild (ggf. mit Zeilen-Padding) → Bitmap. */
    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val source = plane.buffer
        val rowBytes = width * 4
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (plane.rowStride == rowBytes) {
            source.rewind()
            bitmap.copyPixelsFromBuffer(source)
        } else {
            val target = rowBuffer?.takeIf { it.capacity() >= rowBytes * height }
                ?: ByteBuffer.allocateDirect(rowBytes * height).also { rowBuffer = it }
            target.clear()
            for (row in 0 until height) {
                source.limit(row * plane.rowStride + rowBytes)
                source.position(row * plane.rowStride)
                target.put(source)
            }
            target.flip()
            bitmap.copyPixelsFromBuffer(target)
        }
        bitmap.setHasAlpha(false)
        return bitmap
    }

    /** MJPEG bevorzugt (volle Bildrate auch bei 1080p), dann 16:9 und Größe nahe am Ziel. */
    private fun chooseSize(sizes: List<UvcSize>?): UvcSize? {
        if (sizes.isNullOrEmpty()) return null
        val targetArea = if (highResolution) 1920 * 1080 else 1280 * 720
        return sizes.minWithOrNull(
            compareBy<UvcSize>(
                { if (it.type == UVCCamera.UVC_VS_FRAME_MJPEG) 0 else 1 },
                { if (it.width * 9 == it.height * 16) 0 else 1 },
                { abs(it.width * it.height - targetArea) },
            ),
        )
    }

    private fun UsbDevice.displayName(): String =
        productName?.trim()?.takeIf { it.isNotEmpty() } ?: "USB-Kamera"

    private companion object {
        const val TAG = "UvcCameraSource"
    }
}
