package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
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
import dev.minimal.launcher.data.IntentionStats
import dev.minimal.launcher.data.ScreenTime
import dev.minimal.launcher.util.IntentionReminder
import kotlinx.coroutines.delay

private val QUICK_INTENTIONS: List<String> get() = listOf(tr("Nachricht beantworten", "Reply to a message"), tr("Etwas nachschauen", "Look something up"), tr("Etwas teilen", "Share something"), IntentionStats.BOREDOM)
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
        title = { Text(tr("Wozu öffnest du ${app.label}?", "Why are you opening ${app.label}?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        today?.let { (opens, ms) ->
                            when (opens) {
                                0 -> tr("Heute noch nicht geöffnet", "Not opened yet today")
                                1 -> tr("Heute schon einmal geöffnet · ${ScreenTime.format(ms)}", "Opened once today · ${ScreenTime.format(ms)}")
                                else -> tr("Heute schon $opens× geöffnet · ${ScreenTime.format(ms)}", "Opened $opens× today · ${ScreenTime.format(ms)}")
                            }
                        } ?: tr("Kurz innehalten, dann bewusst öffnen.", "Pause for a moment, then open mindfully."),
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
                    placeholder = { Text(tr("Oder in eigenen Worten…", "Or in your own words…")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (IntentionStats.isBoredom(chosen)) {
                    Text(
                        tr("Langeweile ist okay. Vielleicht erst ein paar ruhige Atemzüge – oder kurz aus dem Fenster schauen?", "Boredom is okay. Maybe take a few calm breaths first – or look out of the window?"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(tr("Erinnern nach", "Remind me after"), style = MaterialTheme.typography.labelLarge)
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
                            label = { Text(if (m == 0) tr("Nicht", "No") else "$m min") },
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
                    vm.logIntention(app, chosen)
                    onOpen()
                    onDismiss()
                },
            ) { Text(if (remaining > 0) tr("Öffnen ($remaining)", "Open ($remaining)") else tr("Öffnen", "Open")) }
        },
        dismissButton = {
            TextButton(onClick = {
                vm.logIntention(app, null)
                onDismiss()
            }) { Text(tr("Lieber nicht", "Not now")) }
        },
    )
}
