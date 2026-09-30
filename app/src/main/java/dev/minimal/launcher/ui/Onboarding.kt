package dev.minimal.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.minimal.launcher.data.NotificationStore
import dev.minimal.launcher.service.LauncherAccessibilityService
import dev.minimal.launcher.util.DeviceCompat
import dev.minimal.launcher.util.SystemActions

/** Einrichtungsassistent beim ersten Start – alle Schritte sind optional. */
@Composable
fun OnboardingDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val isDefault = remember(tick) { SystemActions.isDefaultLauncher(context) }
    val notifications = remember(tick) { NotificationStore.hasAccess(context) }
    val accessibility = remember(tick) { LauncherAccessibilityService.isRunning }
    val battery = remember(tick) { DeviceCompat.isIgnoringBatteryOptimizations(context) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Willkommen 👋") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Ein paar Schritte, damit alles funktioniert. Alles ist optional und später in den Einstellungen änderbar.")

                Step("Als Standard-Launcher festlegen", isDefault, "Festlegen") {
                    SystemActions.openHomeSettings(context)
                }
                Step("Benachrichtigungszugriff (Punkte, Vorschau, Mediensteuerung)", notifications, "Erlauben") {
                    SystemActions.openNotificationAccess(context)
                }
                Step("Bedienungshilfe (Sperren per Doppeltipp)", accessibility, "Aktivieren") {
                    SystemActions.openAccessibility(context)
                }
                if (!notifications || !accessibility) {
                    Text(
                        "Meldet Android „Eingeschränkte Einstellung“? Dann in der App-Info oben rechts ⋮ → " +
                            "„Eingeschränkte Einstellungen zulassen“ und danach erneut erlauben.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { SystemActions.openAppDetails(context) }) { Text("App-Info öffnen") }
                }
                DeviceCompat.aggressiveVendor?.let { vendor ->
                    Step("Von Akku-Optimierung ausnehmen ($vendor)", battery, "Ausnehmen") {
                        DeviceCompat.requestIgnoreBatteryOptimizations(context)
                    }
                    TextButton(onClick = { DeviceCompat.openAutostart(context) }) { Text("Autostart erlauben") }
                }

                Text("Kurz erklärt", style = MaterialTheme.typography.titleSmall)
                Text(
                    "• Buchstabenleiste am Rand ziehen → alle Apps\n" +
                        "• Nach oben wischen → Suche (auch Rechner, Timer, Einstellungen …)\n" +
                        "• App lange drücken → Favoriten, Wisch-Aktionen, Sperre, Limits\n" +
                        "• Leeren Bereich lange drücken → Widgets, Notiz, Fokus-Modus, Einstellungen",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Los geht's") } },
    )
}

@Composable
private fun Step(title: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (done) "✓" else "○",
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (!done) TextButton(onClick = onClick) { Text(action) }
    }
}
