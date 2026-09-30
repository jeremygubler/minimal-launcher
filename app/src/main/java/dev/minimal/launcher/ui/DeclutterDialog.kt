package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.minimal.launcher.LauncherViewModel
import dev.minimal.launcher.data.Declutter
import dev.minimal.launcher.data.ScreenTime
import dev.minimal.launcher.data.UnusedApp
import dev.minimal.launcher.util.SystemActions

private val PERIODS = listOf(30, 90, 180)

/** „Aufräumen“: lange nicht geöffnete Apps ausblenden, deinstallieren oder bewusst behalten. */
@Composable
fun DeclutterDialog(vm: LauncherViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(PERIODS.first()) }
    var resumeTick by remember { mutableIntStateOf(0) }
    // Nach dem Deinstallieren oder dem Erteilen des Nutzungszugriffs neu berechnen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val hasAccess = remember(resumeTick) { ScreenTime.hasAccess(context) }
    val unused by produceState<List<UnusedApp>?>(null, days, allApps, settings.hidden, settings.declutterKeep, resumeTick) {
        value = vm.unusedApps(days)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Aufräumen", "Declutter")) },
        text = {
            Column {
                Text(
                    tr("Apps, die du länger nicht geöffnet hast. Was du nicht brauchst, lässt du los.", "Apps you haven't opened in a while. Let go of what you don't need."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PERIODS.forEach { d ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(tr("$d Tage", "$d days")) })
                    }
                }
                if (!hasAccess) {
                    Text(
                        tr("Ohne Nutzungszugriff zählen nur Starts über den Launcher – Apps, die du z. B. über ", "Without usage access only launches from the launcher count – apps you open e.g. via ") +
                            tr("Benachrichtigungen öffnest, erscheinen dann fälschlich hier.", "notifications will wrongly show up here."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { SystemActions.openUsageAccess(context) }) { Text(tr("Nutzungszugriff erlauben", "Allow usage access")) }
                }
                Spacer(Modifier.size(8.dp))
                val list = unused
                when {
                    list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    list.isEmpty() -> Text(
                        tr("Alles aufgeräumt – keine ungenutzten Apps in diesem Zeitraum.", "All tidy – no unused apps in this period."),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    else -> {
                        Text(
                            if (list.size == 1) "1 App" else "${list.size} Apps",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            items(list, key = { it.app.key }) { item ->
                                UnusedRow(
                                    item = item,
                                    hasAccess = hasAccess,
                                    onKeep = { vm.keepApp(item.app.key) },
                                    onHide = { vm.hide(item.app) },
                                    onUninstall = { SystemActions.uninstall(context, item.app.packageName) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
                if (settings.declutterKeep.isNotEmpty()) {
                    TextButton(onClick = { vm.resetKeptApps() }) {
                        Text(tr("Behaltene wieder vorschlagen (${settings.declutterKeep.size})", "Suggest kept apps again (${settings.declutterKeep.size})"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Fertig", "Done")) } },
    )
}

@Composable
private fun UnusedRow(
    item: UnusedApp,
    hasAccess: Boolean,
    onKeep: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(item.app, 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    lastUsedText(item.lastUsed, hasAccess),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onKeep) { Text(tr("Behalten", "Keep")) }
            TextButton(onClick = onHide) { Text(tr("Ausblenden", "Hide")) }
            if (item.removable) TextButton(onClick = onUninstall) { Text(tr("Deinstallieren", "Uninstall")) }
        }
    }
}

private fun lastUsedText(lastUsed: Long?, hasAccess: Boolean): String {
    if (lastUsed == null) return if (hasAccess) tr("Im letzten Jahr nicht geöffnet", "Not opened in the last year") else tr("Noch nie über den Launcher geöffnet", "Never opened from the launcher")
    val days = ((System.currentTimeMillis() - lastUsed) / Declutter.DAY_MS).toInt()
    return when {
        days >= 365 -> tr("Zuletzt vor über einem Jahr", "Last used over a year ago")
        days >= 60 -> tr("Zuletzt vor ${days / 30} Monaten", "Last used ${days / 30} months ago")
        else -> tr("Zuletzt vor $days Tagen", "Last used $days days ago")
    }
}
