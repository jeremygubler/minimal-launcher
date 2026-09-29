package dev.minimal.launcher.ui

import android.Manifest
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import dev.minimal.launcher.data.FavoritePage
import dev.minimal.launcher.data.PageSchedule
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.minimal.launcher.LauncherViewModel
import dev.minimal.launcher.data.CrashLog
import dev.minimal.launcher.data.GestureAction
import dev.minimal.launcher.data.IconPack
import dev.minimal.launcher.data.NotificationStore
import dev.minimal.launcher.data.ThemeMode
import dev.minimal.launcher.launcherApp
import dev.minimal.launcher.service.LauncherAccessibilityService
import dev.minimal.launcher.util.CalendarEvents
import dev.minimal.launcher.util.SystemActions

private enum class SettingsDialog { NONE, THEME, ACCENT, ICON_PACK, DOUBLE_TAP, SWIPE_DOWN, SWIPE_UP, NEW_FOLDER, NEW_PAGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    val visibleApps by vm.visibleApps.collectAsStateWithLifecycle()
    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    var dialog by remember { mutableStateOf(SettingsDialog.NONE) }
    var editFolderId by remember { mutableStateOf<String?>(null) }
    var renamePageId by remember { mutableStateOf<String?>(null) }
    var schedulePageId by remember { mutableStateOf<String?>(null) }
    var editFocusSchedule by remember { mutableStateOf(false) }
    var pickingFocusApp by remember { mutableStateOf(false) }
    var deletePageId by remember { mutableStateOf<String?>(null) }
    var movingFavoriteId by remember { mutableStateOf<String?>(null) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    val iconPacks = remember(resumeTick) { IconPack.installed(context) }
    val isDefault = remember(resumeTick) { SystemActions.isDefaultLauncher(context) }
    val hasNotificationAccess = remember(resumeTick) { NotificationStore.hasAccess(context) }
    val accessibilityOn = remember(resumeTick) { LauncherAccessibilityService.isRunning }
    var crashLog by remember(resumeTick) { mutableStateOf(CrashLog.read(context)) }
    var calendarAllowed by remember(resumeTick) { mutableStateOf(CalendarEvents.hasPermission(context)) }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarAllowed = granted
        vm.update { it.copy(showEvents = granted) }
        if (!granted) Toast.makeText(context, "Ohne Kalenderzugriff können keine Termine angezeigt werden", Toast.LENGTH_SHORT).show()
    }

    val store = context.launcherApp.settings
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val ok = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) } != null
        } catch (e: Exception) {
            false
        }
        Toast.makeText(context, if (ok) "Sicherung gespeichert" else "Sicherung fehlgeschlagen", Toast.LENGTH_SHORT).show()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val json = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            null
        }
        val ok = json != null && store.importJson(json)
        Toast.makeText(context, if (ok) "Einstellungen wiederhergestellt" else "Datei ungültig", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Launcher-Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item { Section("Einrichtung") }
            item {
                StatusRow("Standard-Launcher", isDefault, "Festlegen") { SystemActions.openHomeSettings(context) }
            }
            item {
                StatusRow("Benachrichtigungszugriff (Punkte & Vorschau)", hasNotificationAccess, "Erlauben") {
                    SystemActions.openNotificationAccess(context)
                }
            }
            item {
                StatusRow("Bedienungshilfe (Sperren per Doppeltipp)", accessibilityOn, "Aktivieren") {
                    SystemActions.openAccessibility(context)
                }
            }
            if (!hasNotificationAccess || !accessibilityOn) {
                item {
                    Hint(
                        "Meldet Android „App wurde Zugriff verweigert“ / „Eingeschränkte Einstellung“? " +
                            "Dann zuerst in der App-Info oben rechts auf ⋮ tippen und " +
                            "„Eingeschränkte Einstellungen zulassen“ wählen. Danach klappt das Erlauben."
                    )
                }
                item { ClickRow("App-Info öffnen", "Um eingeschränkte Einstellungen zuzulassen") { SystemActions.openAppDetails(context) } }
            }

            item { Section("Darstellung") }
            item {
                ClickRow("Design", when (s.themeMode) {
                    ThemeMode.SYSTEM -> "Wie System"
                    ThemeMode.LIGHT -> "Hell"
                    ThemeMode.DARK -> "Dunkel"
                }) { dialog = SettingsDialog.THEME }
            }
            item {
                ClickRow("Akzentfarbe", ACCENT_COLORS.firstOrNull { it.first == s.accent }?.second ?: "Eigene") {
                    dialog = SettingsDialog.ACCENT
                }
            }
            item {
                ClickRow("Icon-Pack", iconPacks.firstOrNull { it.first == s.iconPack }?.second ?: "Standard") {
                    dialog = SettingsDialog.ICON_PACK
                }
            }
            item { SwitchRow("App-Icons anzeigen", s.showIcons) { v -> vm.update { it.copy(showIcons = v) } } }
            if (Build.VERSION.SDK_INT >= 33) {
                item {
                    SwitchRow("Designsymbole (einfarbig in Systemfarbe)", s.themedIcons) { v ->
                        vm.update { it.copy(themedIcons = v) }
                    }
                }
            }
            item {
                SliderRow("Icon-Größe", s.iconSize.toFloat(), 24f..56f, "${s.iconSize} dp") { v ->
                    vm.update { it.copy(iconSize = v.toInt()) }
                }
            }
            item {
                SliderRow("Schriftgröße", s.textScale, 0.7f..1.5f, "${(s.textScale * 100).toInt()} %") { v ->
                    vm.update { it.copy(textScale = v) }
                }
            }
            item {
                SliderRow("Hintergrund abdunkeln", s.wallpaperDim, 0f..0.8f, "${(s.wallpaperDim * 100).toInt()} %") { v ->
                    vm.update { it.copy(wallpaperDim = v) }
                }
            }
            item { SwitchRow("Hintergrund weichzeichnen (Android 12+)", s.blur) { v -> vm.update { it.copy(blur = v) } } }
            item { SwitchRow("Uhr anzeigen", s.showClock) { v -> vm.update { it.copy(showClock = v) } } }
            item { SwitchRow("Datum anzeigen", s.showDate) { v -> vm.update { it.copy(showDate = v) } } }
            item { SwitchRow("Nächsten Wecker anzeigen", s.showAlarm) { v -> vm.update { it.copy(showAlarm = v) } } }
            item {
                SwitchRow("Nächsten Termin anzeigen", s.showEvents && calendarAllowed) { v ->
                    if (v && !CalendarEvents.hasPermission(context)) {
                        requestCalendar.launch(Manifest.permission.READ_CALENDAR)
                    } else {
                        vm.update { it.copy(showEvents = v) }
                    }
                }
            }
            item {
                SwitchRow("Mediensteuerung (Musik, Podcasts)", s.showMedia) { v -> vm.update { it.copy(showMedia = v) } }
            }
            item {
                SwitchRow("Akku beim Laden und unter 20 % anzeigen", s.showBattery) { v -> vm.update { it.copy(showBattery = v) } }
            }
            item { SwitchRow("Buchstabenleiste links (Linkshänder)", s.alphabetLeft) { v -> vm.update { it.copy(alphabetLeft = v) } } }

            item { Section("Benachrichtigungen") }
            item { SwitchRow("Benachrichtigungspunkte", s.notificationDots) { v -> vm.update { it.copy(notificationDots = v) } } }
            item {
                SwitchRow("Vorschau unter Favoriten", s.notificationPreview) { v ->
                    vm.update { it.copy(notificationPreview = v) }
                }
            }

            item { Section("Gesten") }
            item { ClickRow("Doppeltippen", s.doubleTap.label) { dialog = SettingsDialog.DOUBLE_TAP } }
            item { ClickRow("Nach unten wischen", s.swipeDown.label) { dialog = SettingsDialog.SWIPE_DOWN } }
            item { ClickRow("Nach oben wischen", s.swipeUp.label) { dialog = SettingsDialog.SWIPE_UP } }
            item { SwitchRow("Tastatur bei Suche automatisch öffnen", s.autoKeyboard) { v -> vm.update { it.copy(autoKeyboard = v) } } }
            item { SwitchRow("Kontakte in der Suche", s.searchContacts) { v -> vm.update { it.copy(searchContacts = v) } } }
            item { SwitchRow("App-Aktionen in der Suche (z. B. „Neue Nachricht“)", s.searchShortcuts) { v -> vm.update { it.copy(searchShortcuts = v) } } }
            item { SwitchRow("Vorschläge (meistgenutzte Apps)", s.showSuggestions) { v -> vm.update { it.copy(showSuggestions = v) } } }
            item {
                ClickRow("Nutzungsverlauf löschen", "Setzt Vorschläge und Sortierung zurück") {
                    vm.clearUsage()
                    Toast.makeText(context, "Nutzungsverlauf gelöscht", Toast.LENGTH_SHORT).show()
                }
            }

            item { Section("Favoriten & Seiten") }
            item { Hint("Auf dem Startbildschirm nach links/rechts wischen oder den Seitennamen antippen, um die Seite zu wechseln.") }
            if (s.pages.size > 1) {
                item {
                    SwitchRow("Seite automatisch nach Zeitplan wechseln", s.autoPages) { v -> vm.setAutoPages(v) }
                }
                if (s.autoPages) {
                    item {
                        Hint(
                            "Mit ⏰ einer Seite Tage und Uhrzeit zuweisen. Außerhalb aller Zeitpläne gilt die erste " +
                                "Seite ohne Zeitplan. Gewechselt wird nur zu Beginn und Ende eines Zeitfensters – " +
                                "dazwischen kannst du frei wechseln."
                        )
                    }
                }
            }
            s.pages.forEachIndexed { pageIndex, page ->
                item(key = "page_${page.id}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 8.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            page.name + if (page.id == s.activePage) "  (aktuell)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { vm.movePage(page.id, -1) }, enabled = pageIndex > 0) { Text("↑") }
                        TextButton(onClick = { vm.movePage(page.id, 1) }, enabled = pageIndex < s.pages.lastIndex) { Text("↓") }
                        if (s.autoPages && s.pages.size > 1) TextButton(onClick = { schedulePageId = page.id }) { Text("⏰") }
                        TextButton(onClick = { renamePageId = page.id }) { Text("✎") }
                        if (s.pages.size > 1) TextButton(onClick = { deletePageId = page.id }) { Text("✕") }
                    }
                }
                if (s.autoPages && page.schedule != null) {
                    item(key = "page_schedule_${page.id}") { Hint("⏰ " + page.schedule.describe()) }
                }
                val pageFavs = s.pageFavorites(page.id)
                if (pageFavs.isEmpty()) {
                    item(key = "page_empty_${page.id}") { Hint("Keine Favoriten auf dieser Seite.") }
                }
                pageFavs.forEachIndexed { index, fav ->
                    item(key = "fav_${fav.id}") {
                        val label = if (fav.isFolder) {
                            "📁 " + (fav.name ?: "Ordner") + " (${fav.apps.size})"
                        } else {
                            fav.apps.firstOrNull()?.let { appsByKey[it]?.label } ?: "Nicht installiert"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = fav.isFolder) { editFolderId = fav.id }
                                .padding(start = 36.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { vm.moveFavorite(fav.id, -1) }, enabled = index > 0) { Text("↑") }
                            TextButton(onClick = { vm.moveFavorite(fav.id, 1) }, enabled = index < pageFavs.lastIndex) { Text("↓") }
                            if (s.pages.size > 1) TextButton(onClick = { movingFavoriteId = fav.id }) { Text("⇄") }
                            TextButton(onClick = { vm.removeFavorite(fav.id) }) { Text("✕") }
                        }
                    }
                }
            }
            item { ClickRow("Seite hinzufügen", "z. B. „Arbeit“ oder „Privat“") { dialog = SettingsDialog.NEW_PAGE } }
            item { ClickRow("Ordner erstellen", "Mehrere Apps unter einem Favoriten") { dialog = SettingsDialog.NEW_FOLDER } }

            item { Section("Fokus-Modus") }
            item {
                Hint(
                    "Ablenkende Apps werden im Fokus-Modus ausgegraut, ihre Benachrichtigungen ausgeblendet, " +
                        "und vor dem Öffnen gibt es eine kurze Denkpause. Schnell umschalten: leeren Bereich " +
                        "auf dem Startbildschirm lange drücken."
                )
            }
            item { SwitchRow("Fokus-Modus jetzt aktiv", s.focusManual) { v -> vm.setFocusManual(v) } }
            item {
                ClickRow("Zeitplan", s.focusSchedule?.describe() ?: "Kein Zeitplan – nur manuell") { editFocusSchedule = true }
            }
            s.focusApps.forEach { key ->
                item(key = "focus_$key") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(appsByKey[key]?.label ?: key, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { vm.toggleFocusApp(key) }) { Text("✕") }
                    }
                }
            }
            item { ClickRow("Ablenkende App hinzufügen", if (s.focusApps.isEmpty()) "Noch keine ausgewählt" else "${s.focusApps.size} ausgewählt") { pickingFocusApp = true } }

            item { Section("Ausgeblendete Apps") }
            if (s.hidden.isEmpty()) {
                item { Hint("Keine. Halte eine App gedrückt und wähle „Ausblenden“.") }
            }
            s.hidden.forEach { key ->
                item(key = "hidden_$key") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(appsByKey[key]?.label ?: key, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { vm.unhide(key) }) { Text("Einblenden") }
                    }
                }
            }

            if (s.renamed.isNotEmpty()) {
                item { Section("Umbenannte Apps") }
                s.renamed.forEach { (key, name) ->
                    item(key = "renamed_$key") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                name + (appsByKey[key]?.originalLabel?.let { " ($it)" } ?: ""),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { appsByKey[key]?.let { vm.rename(it, null) } }) { Text("Zurücksetzen") }
                        }
                    }
                }
            }

            item { Section("Fehlerprotokoll") }
            val log = crashLog
            if (log == null) {
                item { Hint("Keine Abstürze aufgezeichnet.") }
            } else {
                item { Hint(log.lineSequence().take(2).joinToString("\n")) }
                item {
                    ClickRow("Protokoll teilen", "Zum Beispiel per E-Mail oder Chat senden") {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Minimal Launcher – Fehlerprotokoll")
                            .putExtra(Intent.EXTRA_TEXT, log)
                        SystemActions.start(context, Intent.createChooser(send, "Protokoll teilen"))
                    }
                }
                item {
                    ClickRow("Protokoll löschen", null) {
                        CrashLog.clear(context)
                        crashLog = null
                    }
                }
            }

            item { Section("Diagnose") }
            item {
                ClickRow("Diagnose teilen", "Technische Infos für die Fehlersuche (Profile, Berechtigungen)") {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Minimal Launcher – Diagnose")
                        .putExtra(Intent.EXTRA_TEXT, vm.diagnostics())
                    SystemActions.start(context, Intent.createChooser(send, "Diagnose teilen"))
                }
            }

            item { Section("Sicherung") }
            item { ClickRow("Einstellungen exportieren", "Als JSON-Datei speichern") { exportLauncher.launch("minimal-launcher-backup.json") } }
            item { ClickRow("Einstellungen importieren", "Aus JSON-Datei wiederherstellen") { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) } }
        }
    }

    when (dialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.THEME -> ChoiceDialog(
            "Design",
            listOf(ThemeMode.SYSTEM to "Wie System", ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel"),
            s.themeMode,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(themeMode = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.ACCENT -> ChoiceDialog(
            "Akzentfarbe", ACCENT_COLORS, s.accent,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(accent = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.ICON_PACK -> ChoiceDialog(
            "Icon-Pack",
            listOf<Pair<String?, String>>(null to "Standard") + iconPacks.map { it.first to it.second },
            s.iconPack,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(iconPack = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.DOUBLE_TAP -> GestureDialog("Doppeltippen", s.doubleTap, { dialog = SettingsDialog.NONE }) { v ->
            vm.update { it.copy(doubleTap = v) }
        }
        SettingsDialog.SWIPE_DOWN -> GestureDialog("Nach unten wischen", s.swipeDown, { dialog = SettingsDialog.NONE }) { v ->
            vm.update { it.copy(swipeDown = v) }
        }
        SettingsDialog.SWIPE_UP -> GestureDialog("Nach oben wischen", s.swipeUp, { dialog = SettingsDialog.NONE }) { v ->
            vm.update { it.copy(swipeUp = v) }
        }
        SettingsDialog.NEW_PAGE -> TextInputDialog(
            title = "Neue Seite",
            initial = "",
            hint = "z. B. Arbeit, Privat, Reisen",
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { name ->
                vm.addPage(name)
                dialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.NEW_FOLDER -> TextInputDialog(
            title = "Neuer Ordner",
            initial = "",
            hint = "z. B. Social, Arbeit, Tools",
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { name ->
                vm.createFolder(name.ifBlank { "Ordner" }, emptyList())
                dialog = SettingsDialog.NONE
            },
        )
    }

    if (editFocusSchedule) {
        ScheduleDialog(
            title = "Zeitplan: Fokus-Modus",
            existing = s.focusSchedule,
            onDismiss = { editFocusSchedule = false },
            onSave = { schedule ->
                vm.setFocusSchedule(schedule)
                editFocusSchedule = false
            },
        )
    }
    if (pickingFocusApp) {
        AppPickerDialog(
            title = "Ablenkende App wählen",
            apps = visibleApps.filter { it.key !in s.focusApps },
            onDismiss = { pickingFocusApp = false },
            onPick = { app ->
                app?.let { vm.toggleFocusApp(it.key) }
                pickingFocusApp = false
            },
        )
    }
    schedulePageId?.let { id ->
        s.pages.firstOrNull { it.id == id }?.let { page ->
            ScheduleDialog(
                title = "Zeitplan: ${page.name}",
                existing = page.schedule,
                onDismiss = { schedulePageId = null },
                onSave = { schedule ->
                    vm.setPageSchedule(id, schedule)
                    schedulePageId = null
                },
            )
        }
    }
    renamePageId?.let { id ->
        s.pages.firstOrNull { it.id == id }?.let { page ->
            TextInputDialog(
                title = "Seite umbenennen",
                initial = page.name,
                onDismiss = { renamePageId = null },
                onConfirm = { name ->
                    vm.renamePage(id, name)
                    renamePageId = null
                },
            )
        }
    }
    deletePageId?.let { id ->
        s.pages.firstOrNull { it.id == id }?.let { page ->
            val target = s.pages.first { it.id != id }.name
            AlertDialog(
                onDismissRequest = { deletePageId = null },
                title = { Text("Seite „${page.name}“ löschen?") },
                text = { Text("Ihre Favoriten werden auf die Seite „$target“ verschoben.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.removePage(id)
                        deletePageId = null
                    }) { Text("Löschen") }
                },
                dismissButton = { TextButton(onClick = { deletePageId = null }) { Text("Abbrechen") } },
            )
        }
    }
    movingFavoriteId?.let { favId ->
        val fav = s.favorites.firstOrNull { it.id == favId }
        if (fav != null) {
            ChoiceDialog(
                title = "Auf Seite verschieben",
                options = s.pages.map { it.id to it.name },
                selected = fav.page,
                onDismiss = { movingFavoriteId = null },
            ) { pageId ->
                vm.moveFavoriteToPage(favId, pageId)
                movingFavoriteId = null
            }
        }
    }
    editFolderId?.let { id ->
        s.favorites.firstOrNull { it.id == id }?.let { folder ->
            FolderEditDialog(
                folder = folder,
                appsByKey = appsByKey,
                pickerApps = visibleApps,
                vm = vm,
                onDismiss = { editFolderId = null },
            )
        }
    }
}

@Composable
private fun GestureDialog(title: String, current: GestureAction, onDismiss: () -> Unit, onPick: (GestureAction) -> Unit) {
    ChoiceDialog(title, GestureAction.entries.map { it to it.label }, current, onDismiss) {
        onPick(it)
        onDismiss()
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun ClickRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !ok, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (ok) Color(0xFF4CAF50) else Color(0xFFFFA000))
        )
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!ok) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun ScheduleDialog(
    title: String,
    existing: PageSchedule?,
    onDismiss: () -> Unit,
    onSave: (PageSchedule?) -> Unit,
) {
    val context = LocalContext.current
    val initial = existing ?: PageSchedule(setOf(1, 2, 3, 4, 5), 8 * 60, 17 * 60)
    var days by remember { mutableStateOf(initial.days) }
    var start by remember { mutableIntStateOf(initial.start) }
    var end by remember { mutableIntStateOf(initial.end) }
    val dayNames = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    fun pickTime(current: Int, set: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> set(hour * 60 + minute) },
            current / 60,
            current % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Tage", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNames.forEachIndexed { i, name ->
                        val day = i + 1
                        val on = day in days
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { days = if (on) days - day else days + day },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Row {
                    TextButton(onClick = { pickTime(start) { start = it } }) { Text("Von ${PageSchedule.format(start)}") }
                    TextButton(onClick = { pickTime(end) { end = it } }) { Text("Bis ${PageSchedule.format(end)}") }
                }
                if (end < start) {
                    Text("Endet am nächsten Tag.", style = MaterialTheme.typography.bodySmall)
                }
                if (start == end) {
                    Text(
                        "Beginn und Ende müssen verschieden sein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty() && start != end,
                onClick = { onSave(PageSchedule(days, start, end)) },
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { onSave(null) }) { Text("Entfernen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
