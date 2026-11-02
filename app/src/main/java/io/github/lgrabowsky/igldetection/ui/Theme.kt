package io.github.lgrabowsky.igldetection.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object IglColors {
    val Active = Color(0xFF1B5E20)
    val ActiveAccent = Color(0xFF69F0AE)
    val NoIgl = Color(0xFF37474F)
    val Unknown = Color(0xFF212121)
    val SignRed = Color(0xFFD32F2F)
    val DebugText = Color(0xFFB0BEC5)
    val DebugIgl = Color(0xFF00E676)
    val DebugSpeed = Color(0xFFFF5252)
    val DebugSearchArea = Color(0xFFFFD740)
}

/** Dunkles Farbschema – blendet nachts im Auto nicht. */
@Composable
fun IglTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF81C784),
            secondary = Color(0xFF90A4AE),
        ),
        content = content,
    )
}
