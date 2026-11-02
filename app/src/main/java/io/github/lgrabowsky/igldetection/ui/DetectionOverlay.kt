package io.github.lgrabowsky.igldetection.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import io.github.lgrabowsky.igl.core.BoundingBox
import io.github.lgrabowsky.igldetection.DebugFrame
import kotlin.math.min

/**
 * Zeichnet die Erkennungsergebnisse über die Kameravorschau (Vorschau mit FIT_CENTER skaliert):
 * grau = erkannter Text, rot = Tempolimit mit Ring, grün = „IG-L“, gelb gestrichelt = Suchbereich.
 */
@Composable
fun DetectionOverlay(frame: DebugFrame?, searchAreaBottom: Float, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        val current = frame ?: return@Canvas
        val observation = current.result.observation
        val imageW = observation.imageWidth.toFloat()
        val imageH = observation.imageHeight.toFloat()
        if (imageW <= 0f || imageH <= 0f) return@Canvas

        val scale = min(size.width / imageW, size.height / imageH)
        val offset = Offset((size.width - imageW * scale) / 2f, (size.height - imageH * scale) / 2f)
        fun map(box: BoundingBox) = Pair(
            Offset(offset.x + box.left * scale, offset.y + box.top * scale),
            Size(box.width * scale, box.height * scale),
        )

        val lineY = offset.y + imageH * searchAreaBottom * scale
        drawLine(
            color = IglColors.DebugSearchArea,
            start = Offset(offset.x, lineY),
            end = Offset(offset.x + imageW * scale, lineY),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
        )

        for (box in current.result.textBoxes) {
            val (topLeft, boxSize) = map(box)
            drawRect(Color.White.copy(alpha = 0.5f), topLeft, boxSize, style = Stroke(1.5f))
        }
        for (sign in observation.speedSigns) {
            val (topLeft, boxSize) = map(sign.box)
            drawRect(IglColors.DebugSpeed, topLeft, boxSize, style = Stroke(4f))
            sign.ring?.let { ring ->
                drawCircle(
                    color = IglColors.DebugSpeed,
                    radius = ring.radius * scale,
                    center = Offset(offset.x + ring.centerX * scale, offset.y + ring.centerY * scale),
                    style = Stroke(2f),
                )
            }
            label("${sign.valueKmh}", topLeft, IglColors.DebugSpeed, textMeasurer)
        }
        for (match in observation.iglMatches) {
            val (topLeft, boxSize) = map(match.box)
            drawRect(IglColors.DebugIgl, topLeft, boxSize, style = Stroke(4f))
            label("IG-L ${"%.2f".format(match.score)}", topLeft, IglColors.DebugIgl, textMeasurer)
        }

        label(
            "${current.result.processingMs} ms · Evidenz ${"%.1f".format(current.evidence)}",
            Offset(8f, 8f),
            IglColors.DebugText,
            textMeasurer,
            above = false,
        )
    }
}

private fun DrawScope.label(
    text: String,
    anchor: Offset,
    color: Color,
    textMeasurer: TextMeasurer,
    above: Boolean = true,
) {
    val layout = textMeasurer.measure(text, TextStyle(color = color, fontSize = 12.sp))
    val y = if (above) anchor.y - layout.size.height - 2f else anchor.y
    drawRect(Color.Black.copy(alpha = 0.6f), Offset(anchor.x, y), Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
    drawText(layout, topLeft = Offset(anchor.x, y))
}
