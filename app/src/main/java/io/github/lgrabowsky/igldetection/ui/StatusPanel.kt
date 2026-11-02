package io.github.lgrabowsky.igldetection.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lgrabowsky.igl.core.IglStatus
import kotlinx.coroutines.delay

/**
 * Große, auf einen Blick lesbare Statusanzeige.
 *
 * @param compact verkleinerte Darstellung für den Bild-im-Bild-Modus (ohne Bedienelemente).
 */
@Composable
fun StatusPanel(
    status: IglStatus,
    evSpeedKmh: Int,
    compact: Boolean,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = when (status) {
            is IglStatus.IglActive -> IglColors.Active
            is IglStatus.NoIgl -> IglColors.NoIgl
            IglStatus.Unknown -> IglColors.Unknown
        },
        label = "statusBackground",
    )

    BoxWithConstraints(modifier.background(background)) {
        val signDiameter = minOf(maxWidth, maxHeight) * if (compact) 0.42f else 0.3f
        Column(Modifier.fillMaxSize().padding(if (compact) 6.dp else 16.dp)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 8.dp),
            ) {
                StatusContent(status, evSpeedKmh, compact, signDiameter)
            }
            if (!compact) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    OutlinedButton(onClick = onReset) { Text("Zurücksetzen", color = Color.White) }
                    OutlinedButton(onClick = onOpenSettings) { Text("Einstellungen", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun StatusContent(status: IglStatus, evSpeedKmh: Int, compact: Boolean, signDiameter: Dp) {
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(5_000)
            value = SystemClock.elapsedRealtime()
        }
    }

    when (status) {
        is IglStatus.IglActive -> {
            Headline("IG-L AKTIV", IglColors.ActiveAccent, compact)
            SpeedLimitSign(status.limitKmh, withIglPlate = true, diameter = signDiameter)
            BigText("E-Auto: bis $evSpeedKmh km/h", if (compact) 16 else 30)
            if (!compact) {
                Detail("Tempo ${status.limitKmh} mit Zusatz IG-L gilt nicht für Fahrzeuge mit grünem Kennzeichen.")
                Detail("Bestätigt ${formatAge(now - status.lastConfirmedMs)}")
            }
        }

        is IglStatus.NoIgl -> {
            Headline("KEIN IG-L", Color.White, compact)
            SpeedLimitSign(status.limitKmh, withIglPlate = false, diameter = signDiameter)
            BigText("Tempo ${status.limitKmh} gilt für alle", if (compact) 14 else 24)
            if (!compact) Detail("Tempolimit ohne IG-L-Zusatz erkannt ${formatAge(now - status.lastConfirmedMs)}")
        }

        IglStatus.Unknown -> {
            Headline("Suche IG-L …", Color(0xFFCFD8DC), compact)
            if (!compact) {
                Detail("Noch kein Tempolimit erkannt. Die Kamera sollte nach vorne auf Fahrbahn und Schilderbrücken zeigen.")
            }
        }
    }
}

@Composable
private fun Headline(text: String, color: Color, compact: Boolean) {
    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.ExtraBold,
        fontSize = if (compact) 18.sp else 34.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BigText(text: String, sizeSp: Int) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = sizeSp.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Detail(text: String) {
    Text(text = text, color = Color(0xFFE0E0E0), fontSize = 14.sp, textAlign = TextAlign.Center)
}

private fun formatAge(ageMs: Long): String {
    val minutes = ageMs / 60_000
    return when {
        minutes < 1 -> "gerade eben"
        minutes == 1L -> "vor 1 Minute"
        else -> "vor $minutes Minuten"
    }
}
