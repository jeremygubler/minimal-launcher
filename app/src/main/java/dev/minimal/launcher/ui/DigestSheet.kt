package dev.minimal.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.DateUtils
import dev.minimal.launcher.LauncherViewModel
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.NotificationDigest
import dev.minimal.launcher.util.tr

/** Zurückgehaltene Benachrichtigungen, nach App gruppiert. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigestSheet(vm: LauncherViewModel, appsByPackage: Map<String, AppInfo>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val held by vm.heldNotifications.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val groups = remember(held) {
        held.groupBy { it.pkg }.entries.sortedByDescending { e -> e.value.maxOf { it.time } }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(tr("Zusammenfassung", "Digest"), style = MaterialTheme.typography.titleLarge)
            Text(
                if (settings.digestEnabled) {
                    tr("Zustellung um ", "Delivered at ") + NotificationDigest.describe(settings.digestTimes)
                } else {
                    tr("Zusammenfassung ist ausgeschaltet.", "Digest is turned off.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (held.isEmpty()) {
                Text(
                    tr("Nichts verpasst – alles ruhig.", "Nothing missed – all quiet."),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp).padding(top = 8.dp)) {
                    groups.forEach { (pkg, list) ->
                        val app = appsByPackage[pkg]
                        item(key = "h_$pkg") {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (app != null) {
                                    AppIcon(app, 24.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    (app?.label ?: pkg) + " · ${list.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { vm.dismissHeld(list.map { it.key }) }) { Text(tr("Erledigt", "Clear")) }
                            }
                        }
                        items(list.sortedByDescending { it.time }, key = { it.key }) { n ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.openHeld(n) }
                                    .padding(start = 32.dp, top = 4.dp, bottom = 8.dp)
                            ) {
                                if (n.title.isNotBlank()) {
                                    Text(n.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (n.text.isNotBlank()) {
                                    Text(
                                        n.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    DateUtils.getRelativeTimeSpanString(n.time).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item(key = "d_$pkg") { HorizontalDivider() }
                    }
                }
                TextButton(onClick = { vm.clearHeld() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(tr("Alle als gelesen markieren", "Mark all as read"))
                }
            }
        }
    }
}
