package dev.minimal.launcher.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.minimal.launcher.LauncherViewModel
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.ScreenTime
import dev.minimal.launcher.util.IntentionReminder
import kotlinx.coroutines.delay

private val QUICK_INTENTIONS = listOf("Nachricht beantworten", "Etwas nachschauen", "Etwas teilen", "Langeweile")
private const val BOREDOM = "Langeweile"
private val TIMERS = listOf(0, 5, 10, 20)

/**
 * Absichtsfrage vor einer ablenkenden App: „Wozu öffnest du …?“ – mit Zähler für heute,
 * Schnellauswahl und optionalem Timer, der danach an die Absicht erinnert.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntentionDialog(
    app: AppInfo,
    vm: LauncherViewModel,
    seconds: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var intention by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    val today by produceState<Pair<Int, Long>?>(null, app.key) { value = vm.appToday(app) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) minutes = 0
    }
    val chosen = custom.trim().ifEmpty { intention }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wozu öffnest du ${app.label}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        today?.let { (opens, ms) ->
                            when (opens) {
                                0 -> "Heute noch nicht geöffnet"
                                1 -> "Heute schon einmal geöffnet · ${ScreenTime.format(ms)}"
                                else -> "Heute schon $opens× geöffnet · ${ScreenTime.format(ms)}"
                            }
                        } ?: "Kurz innehalten, dann bewusst öffnen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_INTENTIONS.forEach { q ->
                        FilterChip(
                            selected = intention == q && custom.isBlank(),
                            onClick = {
                                intention = q
                                custom = ""
                            },
                            label = { Text(q) },
                        )
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    singleLine = true,
                    placeholder = { Text("Oder in eigenen Worten…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (chosen == BOREDOM) {
                    Text(
                        "Langeweile ist okay. Vielleicht erst ein paar ruhige Atemzüge – oder kurz aus dem Fenster schauen?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text("Erinnern nach", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIMERS.forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = {
                                minutes = m
                                if (m > 0 && !IntentionReminder.canNotify(context) &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            label = { Text(if (m == 0) "Nicht" else "$m min") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = remaining == 0 && chosen.isNotBlank(),
                onClick = {
                    IntentionReminder.schedule(context, app.label, chosen, minutes)
                    onOpen()
                    onDismiss()
                },
            ) { Text(if (remaining > 0) "Öffnen ($remaining)" else "Öffnen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Lieber nicht") } },
    )
}
