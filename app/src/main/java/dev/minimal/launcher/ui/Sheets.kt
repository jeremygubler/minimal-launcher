package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.widget.Toast
import android.os.UserManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.minimal.launcher.LauncherViewModel
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.Favorite
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.ScreenTime
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.minimal.launcher.util.SystemActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    app: AppInfo,
    vm: LauncherViewModel,
    settings: LauncherSettings,
    pickerApps: List<AppInfo>,
    appsByKey: Map<String, AppInfo>,
    onDismiss: () -> Unit,
    onPickPopupWidget: (AppWidgetProviderInfo) -> Unit,
) {
    val context = LocalContext.current
    val shortcuts by produceState(emptyList<ShortcutInfo>(), app.key) { value = vm.shortcuts(app) }
    var pickingWidget by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var pickingSwipe by remember { mutableStateOf(false) }
    var pickingFolder by remember { mutableStateOf(false) }
    var creatingFolder by remember { mutableStateOf(false) }
    var pickingLeft by remember { mutableStateOf(false) }
    var pickingLimit by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }
    val pro = isPro()
    var paywall by remember { mutableStateOf<String?>(null) }
    val isFavorite = settings.isFavorite(app.key)
    val favorite = settings.pageFavorites().firstOrNull { !it.isFolder && it.apps.firstOrNull() == app.key }
    val folders = settings.pageFavorites().filter { it.isFolder }
    var movingToPage by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 44.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    val usageToday by produceState<Long?>(null, app.packageName, settings.showScreenTime) {
                        value = if (settings.showScreenTime) vm.screenTimeToday()[app.packageName] ?: 0L else null
                    }
                    val sub = listOfNotNull(
                        app.originalLabel.takeIf { it != app.label },
                        tr("Arbeitsprofil", "Work profile").takeIf { app.isWork },
                        usageToday?.let { tr("Heute: ", "Today: ") + ScreenTime.format(it) },
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (shortcuts.isNotEmpty() && app.key !in settings.lockedApps) {
                shortcuts.forEach { shortcut ->
                    ShortcutRow(shortcut, vm) {
                        vm.startShortcut(shortcut)
                        onDismiss()
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            SheetAction(if (isFavorite) tr("Aus Favoriten entfernen", "Remove from favorites") else tr("Zu Favoriten hinzufügen", "Add to favorites")) {
                vm.toggleFavorite(app)
                onDismiss()
            }
            if (favorite != null) {
                val swipeLabel = when {
                    favorite.widgetId != null -> tr("Pop-up-Widget", "Pop-up widget")
                    else -> favorite.swipeApp?.let { appsByKey[it]?.label } ?: tr("keine", "none")
                }
                SheetAction(tr("Wisch-Aktion (nach rechts): $swipeLabel", "Swipe action (right): $swipeLabel")) { pickingSwipe = true }
                SheetAction(if (favorite.widgetId != null) tr("Pop-up-Widget ändern", "Change pop-up widget") else tr("Pop-up-Widget festlegen", "Set pop-up widget")) {
                    if (pro) pickingWidget = true else paywall = tr("Pop-up-Widgets", "Pop-up widgets")
                }
                if (favorite.widgetId != null) {
                    SheetAction(tr("Pop-up-Widget entfernen", "Remove pop-up widget")) {
                        vm.setFavoriteWidget(app.key, null)
                        onDismiss()
                    }
                }
                if (shortcuts.isNotEmpty()) {
                    SheetAction(tr("Wisch-Aktion (nach links): ${favorite.swipeLeftLabel ?: "keine"}", "Swipe action (left): ${favorite.swipeLeftLabel ?: "none"}")) { pickingLeft = true }
                }
            }
            if (favorite != null && settings.pages.size > 1) {
                SheetAction(tr("Auf andere Seite verschieben", "Move to another page")) { movingToPage = true }
            }
            if (folders.isNotEmpty()) {
                SheetAction(tr("Zu Ordner hinzufügen", "Add to folder")) { pickingFolder = true }
            }
            SheetAction(tr("Neuen Ordner mit dieser App", "New folder with this app")) { creatingFolder = true }
            SheetAction(
                if (app.key in settings.focusApps) tr("Nicht mehr als ablenkend markieren", "Unmark as distracting") else tr("Als ablenkend markieren (Fokus-Modus)", "Mark as distracting (focus mode)")
            ) {
                vm.toggleFocusApp(app.key)
                onDismiss()
            }
            SheetAction(tr("Tageslimit: ", "Daily limit: ") + (settings.appLimits[app.key]?.let { "$it min" } ?: "keins")) {
                pickingLimit = true
            }
            SheetAction(
                if (app.key in settings.lockedApps) tr("App-Sperre aufheben", "Remove app lock") else tr("Mit Fingerabdruck/PIN sperren", "Lock with fingerprint/PIN")
            ) {
                vm.toggleLockedApp(app.key)
                onDismiss()
            }
            SheetAction(tr("Icon ändern", "Change icon")) { if (pro) pickingIcon = true else paywall = tr("Eigene Icons", "Custom icons") }
            SheetAction(tr("Umbenennen", "Rename")) { renaming = true }
            SheetAction(tr("Ausblenden", "Hide")) {
                vm.hide(app)
                onDismiss()
            }
            SheetAction(tr("App-Info", "App info")) {
                vm.openAppInfo(app)
                onDismiss()
            }
            SheetAction(tr("Deinstallieren", "Uninstall")) {
                SystemActions.uninstall(context, app.packageName)
                onDismiss()
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = tr("Umbenennen", "Rename"),
            initial = app.label,
            hint = app.originalLabel,
            onDismiss = { renaming = false },
            onConfirm = {
                vm.rename(app, it)
                renaming = false
                onDismiss()
            },
        )
    }
    if (pickingSwipe) {
        AppPickerDialog(
            title = tr("Beim Wischen nach rechts öffnen", "Open on swipe right"),
            apps = pickerApps.filter { it.key != app.key },
            noneLabel = tr("Keine Wisch-Aktion", "No swipe action"),
            onDismiss = { pickingSwipe = false },
            onPick = {
                vm.setSwipeApp(app.key, it?.key)
                pickingSwipe = false
                onDismiss()
            },
        )
    }
    paywall?.let { PaywallDialog(feature = it, onDismiss = { paywall = null }) }
    if (pickingWidget) {
        WidgetPickerDialog(
            title = tr("Pop-up-Widget", "Pop-up widget"),
            packageName = app.packageName,
            onDismiss = { pickingWidget = false },
            onPick = { info ->
                pickingWidget = false
                onPickPopupWidget(info)
                onDismiss()
            },
        )
    }
    if (pickingIcon) {
        IconPickerDialog(app = app, vm = vm, settings = settings, onDismiss = {
            pickingIcon = false
            onDismiss()
        })
    }
    if (pickingLimit) {
        ChoiceDialog(
            title = tr("Tageslimit für ${app.label}", "Daily limit for ${app.label}"),
            options = listOf(0 to tr("Kein Limit", "No limit")) + listOf(15, 30, 45, 60, 90, 120, 180).map { it to tr("$it Minuten", "$it minutes") },
            selected = settings.appLimits[app.key] ?: 0,
            onDismiss = { pickingLimit = false },
        ) { minutes ->
            vm.setAppLimit(app.key, minutes)
            pickingLimit = false
            if (minutes > 0 && !ScreenTime.hasAccess(context)) {
                Toast.makeText(context, tr("Für Tageslimits bitte „Nutzungszugriff“ erlauben", "Please allow “Usage access” for daily limits"), Toast.LENGTH_LONG).show()
                SystemActions.openUsageAccess(context)
            }
            onDismiss()
        }
    }
    if (pickingLeft) {
        ChoiceDialog(
            title = tr("Beim Wischen nach links", "On swipe left"),
            options = listOf("" to tr("Keine Aktion", "No action")) + shortcuts.map { it.id to (it.shortLabel ?: it.longLabel ?: it.id).toString() },
            selected = favorite?.swipeLeftShortcut ?: "",
            onDismiss = { pickingLeft = false },
        ) { id ->
            val label = shortcuts.firstOrNull { it.id == id }?.let { (it.shortLabel ?: it.longLabel)?.toString() }
            vm.setSwipeLeftShortcut(app.key, id.ifEmpty { null }, label)
            pickingLeft = false
            onDismiss()
        }
    }
    if (creatingFolder) {
        TextInputDialog(
            title = tr("Neuer Ordner", "New folder"),
            initial = "",
            hint = tr("z. B. Social, Arbeit, Tools", "e.g. Social, Work, Tools"),
            onDismiss = { creatingFolder = false },
            onConfirm = { name ->
                vm.createFolder(name.ifBlank { tr("Ordner", "Folder") }, listOf(app.key))
                creatingFolder = false
                onDismiss()
            },
        )
    }
    if (movingToPage && favorite != null) {
        ChoiceDialog(
            title = tr("Auf Seite verschieben", "Move to page"),
            options = settings.pages.filter { it.id != favorite.page }.map { it.id to it.name },
            selected = null,
            onDismiss = { movingToPage = false },
        ) { pageId ->
            vm.moveFavoriteToPage(favorite.id, pageId)
            movingToPage = false
            onDismiss()
        }
    }
    if (pickingFolder) {
        ChoiceDialog(
            title = tr("Ordner wählen", "Choose folder"),
            options = folders.map { it.id to (it.name ?: tr("Ordner", "Folder")) },
            selected = null,
            onDismiss = { pickingFolder = false },
            onPick = {
                vm.addToFolder(it, app.key)
                pickingFolder = false
                onDismiss()
            },
        )
    }
}

@Composable
private fun ShortcutRow(shortcut: ShortcutInfo, vm: LauncherViewModel, onClick: () -> Unit) {
    val icon by produceState<ImageBitmap?>(null, shortcut.id) {
        value = withContext(Dispatchers.IO) {
            vm.shortcutIcon(shortcut)?.toBitmap(96, 96)?.asImageBitmap()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp)) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SheetAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMenuSheet(
    hasWidgets: Boolean,
    hasNote: Boolean,
    onEditNote: () -> Unit,
    onAddTask: () -> Unit,
    focusOn: Boolean,
    onToggleFocus: () -> Unit,
    onDismiss: () -> Unit,
    onAddWidget: () -> Unit,
    onEditWidgets: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            SheetAction(if (focusOn) tr("Fokus-Modus ausschalten", "Turn off focus mode") else tr("Fokus-Modus einschalten", "Turn on focus mode"), onToggleFocus)
            SheetAction(tr("Aufgabe hinzufügen", "Add task"), onAddTask)
            SheetAction(if (hasNote) tr("Notiz bearbeiten", "Edit note") else tr("Notiz hinzufügen", "Add note"), onEditNote)
            SheetAction(tr("Widget hinzufügen", "Add widget"), onAddWidget)
            if (hasWidgets) SheetAction(tr("Widgets bearbeiten", "Edit widgets"), onEditWidgets)
            SheetAction(tr("Hintergrundbild ändern", "Change wallpaper")) {
                onDismiss()
                SystemActions.chooseWallpaper(context)
            }
            SheetAction(tr("Launcher-Einstellungen", "Launcher settings"), onSettings)
            if (!SystemActions.isDefaultLauncher(context)) {
                SheetAction(tr("Als Standard-Launcher festlegen", "Set as default launcher")) {
                    onDismiss()
                    SystemActions.openHomeSettings(context)
                }
            }
        }
    }
}

@Composable
fun FolderEditDialog(
    folder: Favorite,
    appsByKey: Map<String, AppInfo>,
    pickerApps: List<AppInfo>,
    vm: LauncherViewModel,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val apps = folder.apps.mapNotNull { key -> appsByKey[key]?.let { key to it } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folder.name ?: tr("Ordner", "Folder")) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(apps, key = { it.first }) { (key, app) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { vm.removeFromFolder(folder.id, key) }) { Text("✕") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { adding = true }) { Text(tr("App hinzufügen", "Add app")) }
                    TextButton(onClick = { renaming = true }) { Text(tr("Umbenennen", "Rename")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Fertig", "Done")) } },
        dismissButton = {
            TextButton(onClick = {
                vm.removeFavorite(folder.id)
                onDismiss()
            }) { Text(tr("Ordner löschen", "Delete folder")) }
        },
    )

    if (adding) {
        AppPickerDialog(
            title = tr("App hinzufügen", "Add app"),
            apps = pickerApps.filter { it.key !in folder.apps },
            onDismiss = { adding = false },
            onPick = { app ->
                app?.let { vm.addToFolder(folder.id, it.key) }
                adding = false
            },
        )
    }
    if (renaming) {
        TextInputDialog(
            title = tr("Ordner umbenennen", "Rename folder"),
            initial = folder.name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                vm.renameFolder(folder.id, it.ifBlank { tr("Ordner", "Folder") })
                renaming = false
            },
        )
    }
}

private data class WidgetEntry(val info: AppWidgetProviderInfo, val appLabel: String, val label: String)

@Composable
fun WidgetPickerDialog(
    onDismiss: () -> Unit,
    title: String = tr("Widget hinzufügen", "Add widget"),
    /** Nur Widgets dieser App anzeigen (für Pop-up-Widgets). */
    packageName: String? = null,
    onPick: (AppWidgetProviderInfo) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val entries = remember(packageName) {
        val manager = AppWidgetManager.getInstance(context)
        val pm = context.packageManager
        val users = context.getSystemService(UserManager::class.java).userProfiles
        users.flatMap { manager.getInstalledProvidersForProfile(it) }
            .filter { packageName == null || it.provider.packageName == packageName }
            .map { info ->
            WidgetEntry(info, appLabel(pm, info.provider.packageName), info.loadLabel(pm) ?: "")
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }
    val shown = remember(query, entries) {
        if (query.isBlank()) entries
        else entries.filter { it.appLabel.contains(query, true) || it.label.contains(query, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entries.isEmpty()) {
                    Text(tr("Diese App bietet keine Widgets an.", "This app doesn't offer any widgets."))
                    return@Column
                }
                if (packageName == null) OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(tr("Suchen…", "Search…")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(shown) { entry ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(entry.info) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                            Text(entry.appLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } },
    )
}

private fun appLabel(pm: PackageManager, packageName: String): String = try {
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (e: Exception) {
    packageName
}


/** Aufgabe bearbeiten: Titel, Fälligkeit (ohne/heute/morgen) oder löschen. */
@Composable
fun TaskEditDialog(
    task: dev.minimal.launcher.data.TaskItem,
    onDismiss: () -> Unit,
    onSave: (String, java.time.LocalDate?) -> Unit,
    onDelete: () -> Unit,
) {
    val today = java.time.LocalDate.now()
    var title by remember { mutableStateOf(task.title) }
    var due by remember { mutableStateOf(task.due) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Aufgabe", "Task")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf<Pair<String, java.time.LocalDate?>>(tr("Ohne Datum", "No date") to null, tr("Heute", "Today") to today, tr("Morgen", "Tomorrow") to today.plusDays(1))
                        .forEach { (label, date) ->
                            TextButton(onClick = { due = date }) {
                                Text(
                                    label,
                                    color = if (due == date) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, due) }) { Text(tr("Speichern", "Save")) } },
        dismissButton = { TextButton(onClick = onDelete) { Text(tr("Löschen", "Delete")) } },
    )
}
