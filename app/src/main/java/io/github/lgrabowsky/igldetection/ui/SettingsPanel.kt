package io.github.lgrabowsky.igldetection.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lgrabowsky.igldetection.camera.CameraOptions
import io.github.lgrabowsky.igldetection.camera.CameraState
import io.github.lgrabowsky.igldetection.settings.AppSettings

@Composable
fun SettingsPanel(
    settings: AppSettings,
    camera: CameraState,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Einstellungen",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("Fertig") }
            }

            Section("Kamera") {
                RadioRow(
                    label = "Automatisch (externe USB-Kamera bevorzugt, sonst Rückkamera)",
                    selected = settings.cameraKey == CameraOptions.AUTO,
                    onClick = { onChange { it.copy(cameraKey = CameraOptions.AUTO) } },
                )
                for (option in camera.options) {
                    RadioRow(
                        label = option.label + if (option == camera.active) "  ✓ aktiv" else "",
                        selected = settings.cameraKey == option.key,
                        onClick = { onChange { it.copy(cameraKey = option.key) } },
                    )
                }
                Hint(
                    "USB-Webcams (z. B. Logitech StreamCam) erscheinen hier, wenn das Handy sie als Kamera " +
                        "unterstützt (UVC, auf vielen Geräten ab Android 14). Erscheint sie nach dem Anstecken " +
                        "nicht, die App einmal schließen und neu starten.",
                )
                if (camera.unavailableUsbCameras.isNotEmpty()) {
                    Text(
                        "Angesteckt: ${camera.unavailableUsbCameras.joinToString()} – Android stellt dieses " +
                            "Gerät aber nicht als Kamera bereit. Falls ein Neustart der App nicht hilft, " +
                            "unterstützt dieses Handy keine externen Kameras.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
                Text("Bild der externen Kamera drehen", fontWeight = FontWeight.Medium)
                ChoiceRow(
                    options = listOf(0, 90, 180, 270),
                    selected = settings.externalRotationDegrees,
                    label = { "$it°" },
                    onSelect = { value -> onChange { it.copy(externalRotationDegrees = value) } },
                )
                Hint(
                    "Für USB-Webcams, die sich nicht mit dem Handy mitdrehen: 0° bei normal montierter " +
                        "Webcam, 180° wenn sie kopfüber hängt. Hat die Kamera keinen eigenen Zoom, wird " +
                        "digital (in Software) gezoomt.",
                )
                SwitchRow(
                    label = "Hohe Auflösung (1080p)",
                    description = "Liest Schilder aus größerer Entfernung, braucht aber mehr Akku.",
                    checked = settings.highResolution,
                    onCheckedChange = { value -> onChange { it.copy(highResolution = value) } },
                )
            }

            Section("Erlaubtes Tempo für E-Fahrzeuge bei IG-L") {
                ChoiceRow(
                    options = listOf(100, 110, 120, 130),
                    selected = settings.evSpeedKmh,
                    label = { "$it km/h" },
                    onSelect = { value -> onChange { it.copy(evSpeedKmh = value) } },
                )
                Hint("Ohne IG-L gilt auf Autobahnen und Schnellstraßen in der Regel 130 km/h.")
            }

            Section("Erkennung") {
                SwitchRow(
                    label = "Tempo-Zahl nur mit rotem Ring werten",
                    description = "Empfohlen. Verhindert, dass Zahlen auf LKW, Kennzeichen o. Ä. als Tempolimit gelten.",
                    checked = settings.requireRedRing,
                    onCheckedChange = { value -> onChange { it.copy(requireRedRing = value) } },
                )
                Text("Suchbereich (oberer Bildanteil)", fontWeight = FontWeight.Medium)
                ChoiceRow(
                    options = listOf(0.5f, 0.65f, 0.8f, 1f),
                    selected = settings.searchAreaBottom,
                    label = { "${(it * 100).toInt()} %" },
                    onSelect = { value -> onChange { it.copy(searchAreaBottom = value) } },
                )
                Hint("Schilder unterhalb dieser Linie (z. B. Aufkleber auf LKW) werden ignoriert. Die Linie ist in der Debug-Anzeige gelb eingezeichnet.")
                Text("Befund gültig für", fontWeight = FontWeight.Medium)
                ChoiceRow(
                    options = listOf(5, 15, 30, 60),
                    selected = settings.validityMinutes,
                    label = { "$it min" },
                    onSelect = { value -> onChange { it.copy(validityMinutes = value) } },
                )
                Hint("Ohne erneute Bestätigung durch ein Schild wird der Status danach wieder „unbekannt“.")
            }

            Section("Anzeige & Ton") {
                SwitchRow(
                    label = "Sprachausgabe",
                    description = "Ansage, wenn IG-L aktiv bzw. aufgehoben wird.",
                    checked = settings.speechEnabled,
                    onCheckedChange = { value -> onChange { it.copy(speechEnabled = value) } },
                )
                SwitchRow(
                    label = "Bild-im-Bild beim Verlassen",
                    description = "Die Erkennung läuft als kleines Fenster weiter, z. B. neben der Navigation.",
                    checked = settings.autoPip,
                    onCheckedChange = { value -> onChange { it.copy(autoPip = value) } },
                )
                SwitchRow(
                    label = "Debug-Anzeige",
                    description = "Zeigt erkannte Texte, Schilder und den Suchbereich im Kamerabild.",
                    checked = settings.showDebugOverlay,
                    onCheckedChange = { value -> onChange { it.copy(showDebugOverlay = value) } },
                )
            }

            Section("Hinweis") {
                Hint(
                    "Diese App ist eine Hilfe und kann Schilder übersehen oder falsch lesen. Maßgeblich " +
                        "sind immer die tatsächliche Beschilderung und die geltende Rechtslage. Die Ausnahme " +
                        "vom IG-L-Tempolimit gilt nur für Fahrzeuge mit grünem Kennzeichen (reiner " +
                        "Elektroantrieb), nicht für Hybride. Bitte die App nicht während der Fahrt bedienen.",
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontWeight = FontWeight.Medium)
            Hint(description)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            val isSelected = option == selected
            val colors = MaterialTheme.colorScheme
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) colors.primaryContainer else colors.surface,
                contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                modifier = Modifier
                    .border(1.dp, if (isSelected) colors.primary else colors.outline, RoundedCornerShape(8.dp))
                    .clickable { onSelect(option) },
            ) {
                Text(label(option), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}
