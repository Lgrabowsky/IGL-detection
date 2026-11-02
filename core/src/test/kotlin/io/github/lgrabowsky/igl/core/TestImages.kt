package io.github.lgrabowsky.igl.core

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Erzeugt synthetische Testbilder von Verkehrszeichen (ohne echte Fotos im Repository). */
object TestImages {

    val ORANGE_BEAM = Color(235, 160, 40)
    val SIGN_RED = Color(200, 30, 35)
    val LED_RED = Color(255, 60, 50)

    fun canvas(width: Int, height: Int, background: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = background
            fillRect(0, 0, width, height)
            dispose()
        }
        return image
    }

    /** Klassisches Blechschild: weiße Scheibe mit rotem Rand. */
    fun drawPaintedSign(image: BufferedImage, cx: Int, cy: Int, radius: Int, ringWidth: Int = radius / 5) {
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = SIGN_RED
            fillOval(cx - radius, cy - radius, 2 * radius, 2 * radius)
            color = Color.WHITE
            val inner = radius - ringWidth
            fillOval(cx - inner, cy - inner, 2 * inner, 2 * inner)
            dispose()
        }
    }

    /** LED-Anzeige einer Verkehrsbeeinflussungsanlage: schwarzer Grund, Ring aus roten Punkten. */
    fun drawLedSign(image: BufferedImage, cx: Int, cy: Int, radius: Int, dotRadius: Int = 2) {
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color(25, 25, 28)
            fillRect(cx - radius - 12, cy - radius - 12, 2 * radius + 24, 2 * radius + 24)
            color = LED_RED
            for (row in 0..1) {
                val r = radius - row * (2 * dotRadius + 1)
                val dots = (2 * PI * r / (2 * dotRadius + 2)).roundToInt()
                val phase = if (row == 0) 0.0 else PI / dots
                for (i in 0 until dots) {
                    val a = 2 * PI * i / dots + phase
                    val x = (cx + r * cos(a)).roundToInt()
                    val y = (cy + r * sin(a)).roundToInt()
                    fillOval(x - dotRadius, y - dotRadius, 2 * dotRadius, 2 * dotRadius)
                }
            }
            dispose()
        }
    }

    fun fillRect(image: BufferedImage, box: BoundingBox, color: Color) {
        image.createGraphics().apply {
            this.color = color
            fillRect(box.left, box.top, box.width, box.height)
            dispose()
        }
    }

    /** Zeichnet ein paar helle „Ziffern“-Striche in die Box (die Ringprüfung soll sie ignorieren). */
    fun drawFakeDigits(image: BufferedImage, box: BoundingBox, color: Color) {
        image.createGraphics().apply {
            this.color = color
            stroke = BasicStroke(maxOf(2f, box.height / 8f))
            val n = 3
            val w = box.width / n
            for (i in 0 until n) {
                drawOval(box.left + i * w + 2, box.top + 2, w - 4, box.height - 4)
            }
            dispose()
        }
    }

    fun BufferedImage.toRgbImage(): RgbImage {
        val pixels = getRGB(0, 0, width, height, null, 0, width)
        return IntArrayRgbImage(width, height, pixels)
    }
}

/** Kurzschreibweise für OCR-Testdaten: eine Zeile, deren Wörter nebeneinander liegen. */
fun ocrLine(vararg words: Pair<String, BoundingBox>): OcrLine {
    val elements = words.map { (text, box) -> OcrElement(text, box) }
    return OcrLine(
        text = elements.joinToString(" ") { it.text },
        box = elements.map { it.box }.reduce(BoundingBox::union),
        elements = elements,
    )
}

fun box(left: Int, top: Int, width: Int, height: Int) = BoundingBox(left, top, left + width, top + height)
