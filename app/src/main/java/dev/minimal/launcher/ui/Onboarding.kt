package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
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
import androidx.compose.runtime.mutableStateOf
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
    var showAccessibilityInfo by remember { mutableStateOf(false) }
    if (showAccessibilityInfo) {
        DisclosureDialog(
            disclosure = Disclosure.ACCESSIBILITY,
            onAccept = { SystemActions.openAccessibility(context) },
            onDismiss = { showAccessibilityInfo = false },
        )
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("Willkommen 👋", "Welcome 👋")) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(tr("Ein paar Schritte, damit alles funktioniert. Alles ist optional und später in den Einstellungen änderbar.", "A few steps so everything works. All optional and changeable later in settings."))

                Step(tr("Als Standard-Launcher festlegen", "Set as default launcher"), isDefault, tr("Festlegen", "Set")) {
                    SystemActions.openHomeSettings(context)
                }
                Step(tr("Benachrichtigungszugriff (Punkte, Vorschau, Mediensteuerung)", "Notification access (dots, preview, media controls)"), notifications, tr("Erlauben", "Allow")) {
                    SystemActions.openNotificationAccess(context)
                }
                Step(tr("Bedienungshilfe (Sperren per Doppeltipp)", "Accessibility service (double tap to lock)"), accessibility, tr("Aktivieren", "Enable")) {
                    showAccessibilityInfo = true
                }
                if (!notifications || !accessibility) {
                    Text(
                        tr("Meldet Android „Eingeschränkte Einstellung“? Dann in der App-Info oben rechts ⋮ → ", "Android says “Restricted setting”? Then in app info tap ⋮ at the top right → ") +
                            tr("„Eingeschränkte Einstellungen zulassen“ und danach erneut erlauben.", "“Allow restricted settings” and allow again."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { SystemActions.openAppDetails(context) }) { Text(tr("App-Info öffnen", "Open app info")) }
                }
                DeviceCompat.aggressiveVendor?.let { vendor ->
                    Step(tr("Von Akku-Optimierung ausnehmen ($vendor)", "Exclude from battery optimization ($vendor)"), battery, tr("Ausnehmen", "Exclude")) {
                        DeviceCompat.requestIgnoreBatteryOptimizations(context)
                    }
                    TextButton(onClick = { DeviceCompat.openAutostart(context) }) { Text(tr("Autostart erlauben", "Allow autostart")) }
                }

                Text(tr("Kurz erklärt", "Quick guide"), style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("• Buchstabenleiste am Rand ziehen → alle Apps\n", "• Drag the letter bar at the edge → all apps\n") +
                        tr("• Nach oben wischen → Suche (auch Rechner, Timer, Einstellungen …)\n", "• Swipe up → search (also calculator, timer, settings …)\n") +
                        tr("• App lange drücken → Favoriten, Wisch-Aktionen, Sperre, Limits\n", "• Long-press an app → favorites, swipe actions, lock, limits\n") +
                        tr("• Leeren Bereich lange drücken → Widgets, Notiz, Fokus-Modus, Einstellungen", "• Long-press an empty area → widgets, note, focus mode, settings"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(tr("Los geht's", "Let's go")) } },
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
