package dev.minimal.launcher.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.FocusSessions
import dev.minimal.launcher.util.IntentionReminder
import dev.minimal.launcher.util.tr

/** Fokus-Sitzung starten: Dauer wählen, ablenkende Apps sind bis zum Ende gesperrt. */
@Composable
fun FocusSessionDialog(focusAppCount: Int, onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var minutes by remember { mutableIntStateOf(FocusSessions.DURATIONS.first()) }
    // Für die „Geschafft“-Benachrichtigung am Ende; ohne Erlaubnis läuft die Sitzung trotzdem.
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onStart(minutes)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Fokus-Sitzung", "Focus session")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    tr(
                        "Ablenkende Apps bleiben gesperrt, bis die Zeit um ist. Danach gibt es eine kurze Pause.",
                        "Distracting apps stay locked until the time is up. Then take a short break.",
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusSessions.DURATIONS.forEach { m ->
                        FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
                    }
                }
                if (focusAppCount == 0) {
                    Text(
                        tr(
                            "Noch keine ablenkenden Apps markiert: App lange drücken → „Als ablenkend markieren“.",
                            "No distracting apps marked yet: long-press an app → “Mark as distracting”.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !IntentionReminder.canNotify(context)) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onStart(minutes)
                }
            }) { Text(tr("Starten", "Start")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } },
    )
}

/** Während einer Fokus-Sitzung: ablenkende App ist gesperrt. */
@Composable
fun FocusSessionBlockedDialog(app: AppInfo, remainingMinutes: Int, onStop: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Noch $remainingMinutes min Fokus", "$remainingMinutes min of focus left")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    tr(
                        "Du hast dir vorgenommen, dich zu konzentrieren. ${app.label} wartet bis nach der Sitzung.",
                        "You decided to focus. ${app.label} can wait until after the session.",
                    )
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Weiter fokussieren", "Keep focusing")) } },
        dismissButton = {
            TextButton(onClick = {
                onStop()
                onDismiss()
            }) { Text(tr("Sitzung beenden", "End session")) }
        },
    )
}
