package io.github.lgrabowsky.igldetection.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Vorschriftszeichen „Geschwindigkeitsbeschränkung“, optional mit Zusatztafel „IG-L“. */
@Composable
fun SpeedLimitSign(limitKmh: Int?, withIglPlate: Boolean, diameter: Dp, modifier: Modifier = Modifier) {
    // Schriftgröße an die Grafik gekoppelt (dp statt sp), damit sie nicht aus dem Schild ragt.
    val numberFactor = if ((limitKmh ?: 0) >= 100) 0.34f else 0.42f
    val numberSize = with(LocalDensity.current) { (diameter * numberFactor).toSp() }
    val plateSize = with(LocalDensity.current) { (diameter * 0.2f).toSp() }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(diameter * 0.06f),
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .background(Color.White, CircleShape)
                .border(diameter * 0.11f, IglColors.SignRed, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = limitKmh?.toString() ?: "?",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = numberSize,
            )
        }
        if (withIglPlate) {
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(diameter * 0.04f))
                    .border(2.dp, Color.Black, RoundedCornerShape(diameter * 0.04f))
                    .padding(horizontal = diameter * 0.1f, vertical = diameter * 0.02f),
            ) {
                Text(
                    text = "IG-L",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = plateSize,
                )
            }
        }
    }
}
