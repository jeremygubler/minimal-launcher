package dev.minimal.launcher.ui

import android.Manifest
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import dev.minimal.launcher.data.FavoritePage
import dev.minimal.launcher.data.PageSchedule
import dev.minimal.launcher.data.ScreenTime
import dev.minimal.launcher.BuildConfig
import dev.minimal.launcher.pro.Pro
import dev.minimal.launcher.data.AppCategories
import dev.minimal.launcher.data.AutoBackup
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.minimal.launcher.data.ContextMonitor
import dev.minimal.launcher.data.SearchEngine
import dev.minimal.launcher.data.Weather
import kotlin.math.roundToInt
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
import dev.minimal.launcher.data.HomeFont
import dev.minimal.launcher.data.HomeWeight
import dev.minimal.launcher.data.IconPack
import dev.minimal.launcher.data.NotificationStore
import dev.minimal.launcher.data.ThemeMode
import dev.minimal.launcher.launcherApp
import dev.minimal.launcher.service.LauncherAccessibilityService
import dev.minimal.launcher.util.CalendarEvents
import dev.minimal.launcher.util.DeviceCompat
import dev.minimal.launcher.util.SystemActions

private enum class SettingsDialog { NONE, THEME, ACCENT, ICON_PACK, DOUBLE_TAP, SWIPE_DOWN, SWIPE_UP, NEW_FOLDER, NEW_PAGE, FONT, WEIGHT, HOME_PRESS, ENGINE }

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
    var contextPageId by remember { mutableStateOf<String?>(null) }
    val contextState by ContextMonitor.state.collectAsStateWithLifecycle()
    var permissionTick by remember { mutableIntStateOf(0) }
    val requestBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ContextMonitor.refresh(context)
        permissionTick++
    }
    val requestFineLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ContextMonitor.refresh(context)
        permissionTick++
    }
    var editFocusSchedule by remember { mutableStateOf(false) }
    var editGrayscale by remember { mutableStateOf(false) }
    var categoryLimitFor by remember { mutableStateOf<Int?>(null) }
    var pickingFocusApp by remember { mutableStateOf(false) }
    var deletePageId by remember { mutableStateOf<String?>(null) }
    var movingFavoriteId by remember { mutableStateOf<String?>(null) }
    var resumeTick by remember { mutableIntStateOf(0) }
    var showDeclutter by remember { mutableStateOf(false) }
    val pro = isPro()
    var paywallFor by remember { mutableStateOf<String?>(null) }
    var showPaywall by remember { mutableStateOf(false) }
    var disclosure by remember { mutableStateOf<Disclosure?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val scope = rememberCoroutineScope()
    var backupTick by remember { mutableIntStateOf(0) }
    val lastBackupText = remember(backupTick, resumeTick) {
        val last = AutoBackup.lastRun(context)
        if (last == 0L) "noch keine" else DateUtils.getRelativeTimeSpanString(last).toString()
    }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        vm.update { it.copy(backupFolder = uri.toString()) }
        scope.launch {
            val ok = vm.backupNow()
            backupTick++
            Toast.makeText(context, if (ok) "Erste Sicherung gespeichert" else "Ordner nicht beschreibbar", Toast.LENGTH_SHORT).show()
        }
    }

    val iconPacks = remember(resumeTick) { IconPack.installed(context) }
    val isDefault = remember(resumeTick) { SystemActions.isDefaultLauncher(context) }
    val hasNotificationAccess = remember(resumeTick) { NotificationStore.hasAccess(context) }
    val accessibilityOn = remember(resumeTick) { LauncherAccessibilityService.isRunning }
    val usageAccess = remember(resumeTick) { ScreenTime.hasAccess(context) }
    val batteryUnrestricted = remember(resumeTick) { DeviceCompat.isIgnoringBatteryOptimizations(context) }
    val listenerDisconnected = remember(resumeTick) { DeviceCompat.isListenerDisconnected(context) }
    var crashLog by remember(resumeTick) { mutableStateOf(CrashLog.read(context)) }
    var editingCity by remember { mutableStateOf(false) }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Ohne Standort bitte einen festen Ort eintragen", Toast.LENGTH_LONG).show()
            editingCity = true
        }
    }
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
            if (BuildConfig.STORE_BUILD) {
                item { Section("Pro") }
                item {
                    if (pro) {
                        StatusRow("Pro ist freigeschaltet – danke!", true, "") {}
                    } else {
                        ClickRow("Pro freischalten", "Einmalkauf – kontextbasierte Seiten, eigene Icons, Sicherung, Wochenbericht, Aufgaben") {
                            paywallFor = null
                            showPaywall = true
                        }
                    }
                }
                if (!pro) item { ClickRow("Käufe wiederherstellen", null) { Pro.restore() } }
            }
            item { Section("Einrichtung") }
            item { ClickRow("Einrichtungsassistent erneut zeigen", null) { vm.update { it.copy(onboardingDone = false) } } }
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
                    disclosure = Disclosure.ACCESSIBILITY
                }
            }
            if (listenerDisconnected) {
                item {
                    Hint(
                        "Der Benachrichtigungsdienst wurde vom System beendet – Punkte und Mediensteuerung " +
                            "fehlen deshalb. Der Launcher versucht, ihn neu zu verbinden. Hilft das nicht, " +
                            "den Launcher unten von der Akku-Optimierung ausnehmen."
                    )
                }
            }
            DeviceCompat.aggressiveVendor?.let { vendor ->
                item {
                    StatusRow("Von Akku-Optimierung ausgenommen", batteryUnrestricted, "Ausnehmen") {
                        DeviceCompat.requestIgnoreBatteryOptimizations(context)
                    }
                }
                item {
                    ClickRow("Autostart / Hintergrundaktivität erlauben ($vendor)", "Öffnet die Seite des Herstellers") {
                        DeviceCompat.openAutostart(context)
                    }
                }
                item {
                    Hint(
                        "$vendor beendet Hintergrunddienste oft aggressiv. Damit Benachrichtigungspunkte und " +
                            "Mediensteuerung zuverlässig bleiben: Akku-Optimierung ausnehmen und Autostart erlauben."
                    )
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
            item { ClickRow("Schriftart", s.font.label) { dialog = SettingsDialog.FONT } }
            item { ClickRow("Schriftstärke", s.fontWeight.label) { dialog = SettingsDialog.WEIGHT } }
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
            if (s.showEvents) {
                item {
                    SliderRow("Anzahl Termine", s.eventCount.toFloat(), 1f..3f, "${s.eventCount}") { v ->
                        vm.update { it.copy(eventCount = v.roundToInt().coerceIn(1, 3)) }
                    }
                }
            }
            item { SwitchRow("Aufgaben unter der Uhr anzeigen", s.showTasks) { v -> vm.update { it.copy(showTasks = v) } } }
            if (s.tasks.any { it.isDone }) {
                item { ClickRow("Erledigte Aufgaben jetzt entfernen", null) { vm.clearDoneTasks() } }
            }
            item { SwitchRow("Neue Apps in der Liste markieren", s.markNewApps) { v -> vm.update { it.copy(markNewApps = v) } } }
            item {
                SwitchRow("Mediensteuerung (Musik, Podcasts)", s.showMedia) { v -> vm.update { it.copy(showMedia = v) } }
            }
            item {
                SwitchRow("Akku beim Laden und unter 20 % anzeigen", s.showBattery) { v -> vm.update { it.copy(showBattery = v) } }
            }
            item {
                SwitchRow("Wetter unter der Uhr (Internet, Open-Meteo)", s.showWeather) { v ->
                    vm.update { it.copy(showWeather = v) }
                    if (v && s.weatherCity.isBlank() && !Weather.hasLocationPermission(context)) {
                        disclosure = Disclosure.WEATHER_LOCATION
                    }
                }
            }
            if (s.showWeather) {
                item {
                    ClickRow(
                        "Ort fürs Wetter",
                        s.weatherCity.ifBlank { "Automatisch (ungefährer Standort)" },
                    ) { editingCity = true }
                }
                item {
                    Hint(
                        "Wetterdaten von Open-Meteo, ohne Konto und Tracking. Mit festem Ort ist keine " +
                            "Standortberechtigung nötig; sonst wird der Standort auf ca. 1 km gerundet. " +
                            "Aktualisierung alle 30 Minuten."
                    )
                }
            }
            item {
                SwitchRow("Bildschirmzeit unter der Uhr", s.showScreenTime) { v ->
                    vm.update { it.copy(showScreenTime = v) }
                    if (v && !ScreenTime.hasAccess(context)) SystemActions.openUsageAccess(context)
                }
            }
            if (s.showScreenTime && !usageAccess) {
                item {
                    Hint("Dafür „Nutzungszugriff“ für Kanso erlauben. Die Daten bleiben auf dem Gerät.")
                }
                item { ClickRow("Nutzungszugriff erlauben", null) { SystemActions.openUsageAccess(context) } }
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
            item { ClickRow("Home-Taste auf dem Startbildschirm", s.homePress.label) { dialog = SettingsDialog.HOME_PRESS } }
            item { ClickRow("Suchmaschine", s.searchEngine.label) { dialog = SettingsDialog.ENGINE } }
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
                    SwitchRow("Seite automatisch wechseln (Zeitplan & Kontext)", s.autoPages) { v -> vm.setAutoPages(v) }
                }
                if (s.autoPages) {
                    item {
                        Hint(
                            "⏰ Zeitplan (Tage + Uhrzeit) und 📍 Kontext (Kopfhörer, Laden, Bluetooth-Gerät, WLAN) " +
                                "pro Seite. Kontext hat Vorrang vor Zeitplänen; sonst gilt die erste Seite ohne Regeln. " +
                                "Gewechselt wird nur, wenn sich etwas ändert – dazwischen kannst du frei wechseln."
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
                        if (s.autoPages && s.pages.size > 1) TextButton(onClick = {
                            if (pro) contextPageId = page.id else paywallFor = "Kontextbasierte Seiten"
                        }) { Text("📍") }
                        TextButton(onClick = { renamePageId = page.id }) { Text("✎") }
                        if (s.pages.size > 1) TextButton(onClick = { deletePageId = page.id }) { Text("✕") }
                    }
                }
                if (s.autoPages && page.schedule != null) {
                    item(key = "page_schedule_${page.id}") { Hint("⏰ " + page.schedule.describe()) }
                }
                if (s.autoPages && page.context != null) {
                    item(key = "page_context_${page.id}") { Hint("📍 " + page.context.describe()) }
                }
                val pageFavs = s.pageFavorites(page.id)
                if (pageFavs.isEmpty()) {
                    item(key = "page_empty_${page.id}") { Hint("Keine Favoriten auf dieser Seite.") }
                }
                pageFavs.forEachIndexed { index, fav ->
                    item(key = "fav_${fav.id}") {
                        val label = if (fav.isContact) {
                            "👤 " + (fav.name ?: "Kontakt")
                        } else if (fav.isFolder) {
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
            item {
                SliderRow(
                    "Denkpause vor dem Öffnen",
                    s.focusPauseSeconds.toFloat(),
                    0f..30f,
                    if (s.focusPauseSeconds == 0) "keine" else "${s.focusPauseSeconds} s",
                ) { v -> vm.update { it.copy(focusPauseSeconds = v.roundToInt()) } }
            }
            item {
                SwitchRow("Absichtsfrage" + if (pro) "" else " (Pro)", s.intentionPrompt && pro) { v ->
                    if (!pro) paywallFor = "Absichtsfrage" else vm.update { it.copy(intentionPrompt = v) }
                }
            }
            item {
                Hint(
                    "Statt nur zu warten fragt Kanso: „Wozu öffnest du …?“ – mit Zähler, wie oft du die App heute " +
                        "schon geöffnet hast, und optionalem Timer, der dich danach an deine Absicht erinnert."
                )
            }
            if (s.intentionPrompt && pro) {
                item {
                    SwitchRow("Auch ausserhalb des Fokus-Modus fragen", s.intentionAlways) { v ->
                        vm.update { it.copy(intentionAlways = v) }
                    }
                }
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

            item {
                ClickRow(
                    "Icons in Graustufen",
                    s.grayscaleSchedule?.describe() ?: "Aus – z. B. abends, damit bunte Apps weniger locken",
                ) { editGrayscale = true }
            }
            item { Section("Tageslimits") }
            if (!pro) {
                item { ClickRow("Tagesziel & Kategorie-Limits (Pro)", "Wochenbericht, Ziel mit Serie, Limits pro Kategorie") { paywallFor = "Tagesziel & Kategorie-Limits" } }
            }
            if (pro) item {
                SliderRow(
                    "Tagesziel Bildschirmzeit",
                    s.dailyGoalMinutes.toFloat(),
                    0f..480f,
                    if (s.dailyGoalMinutes == 0) "aus" else ScreenTime.format(s.dailyGoalMinutes * 60_000L),
                ) { v -> vm.update { it.copy(dailyGoalMinutes = ((v / 15).roundToInt() * 15)) } }
            }
            item { Hint("Das Ziel erscheint im Wochenbericht (Bildschirmzeit unter der Uhr antippen) samt Serie.") }
            if (pro) AppCategories.all.forEach { (category, label) ->
                item(key = "cat_$category") {
                    ClickRow(
                        "Limit $label",
                        s.categoryLimits[category.toString()]?.let { "$it min pro Tag (alle $label-Apps zusammen)" } ?: "Kein Limit",
                    ) { categoryLimitFor = category }
                }
            }
            if (s.appLimits.isEmpty()) {
                item { Hint("Keine. App lange drücken → „Tageslimit“. Benötigt „Nutzungszugriff“.") }
            }
            s.appLimits.forEach { (key, minutes) ->
                item(key = "limit_$key") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${appsByKey[key]?.label ?: key} · $minutes min", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { vm.setAppLimit(key, null) }) { Text("✕") }
                    }
                }
            }

            item { Section("App-Sperre") }
            item {
                Hint(
                    "Gesperrte Apps öffnen sich aus dem Launcher nur nach Fingerabdruck oder PIN; ihre " +
                        "Benachrichtigungsvorschau wird ausgeblendet. Hinzufügen: App lange drücken → „Mit " +
                        "Fingerabdruck/PIN sperren“. Hinweis: Über „Zuletzt verwendet“ oder Benachrichtigungen " +
                        "bleibt die App erreichbar – das kann nur Android selbst verhindern."
                )
            }
            s.lockedApps.forEach { key ->
                item(key = "locked_$key") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🔒 " + (appsByKey[key]?.label ?: key), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { vm.toggleLockedApp(key) }) { Text("✕") }
                    }
                }
            }

            item { Section("Ausgeblendete Apps") }
            item { ClickRow("Aufräumen", "Apps finden, die du lange nicht geöffnet hast") { showDeclutter = true } }
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
                            .putExtra(Intent.EXTRA_SUBJECT, "Kanso – Fehlerprotokoll")
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
                        .putExtra(Intent.EXTRA_SUBJECT, "Kanso – Diagnose")
                        .putExtra(Intent.EXTRA_TEXT, vm.diagnostics())
                    SystemActions.start(context, Intent.createChooser(send, "Diagnose teilen"))
                }
            }

            item { Section("Sicherung") }
            item {
                ClickRow(
                    "Automatische Sicherung",
                    s.backupFolder?.let { "Täglich nach „${AutoBackup.folderLabel(it)}“ · letzte: $lastBackupText" }
                        ?: "Aus – Ordner wählen, dann täglich (7 Stände werden behalten)",
                ) { if (pro) pickBackupFolder.launch(null) else paywallFor = "Automatische Sicherung" }
            }
            if (s.backupFolder != null) {
                item {
                    ClickRow("Jetzt sichern", null) {
                        scope.launch {
                            val ok = vm.backupNow()
                            backupTick++
                            Toast.makeText(context, if (ok) "Sicherung gespeichert" else "Sicherung fehlgeschlagen", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                item {
                    ClickRow("Automatische Sicherung ausschalten", null) {
                        s.backupFolder?.let { folder ->
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(
                                    Uri.parse(folder),
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                )
                            }
                        }
                        vm.update { it.copy(backupFolder = null) }
                    }
                }
            }
            item { ClickRow("Einstellungen exportieren", "Als JSON-Datei speichern") { exportLauncher.launch("kanso-backup.json") } }
            item { ClickRow("Einstellungen importieren", "Aus JSON-Datei wiederherstellen") { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) } }
        }
    }

    if (showDeclutter) DeclutterDialog(vm = vm, onDismiss = { showDeclutter = false })

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
        SettingsDialog.HOME_PRESS -> GestureDialog("Home-Taste auf dem Startbildschirm", s.homePress, { dialog = SettingsDialog.NONE }) { v ->
            vm.update { it.copy(homePress = v) }
        }
        SettingsDialog.ENGINE -> ChoiceDialog(
            "Suchmaschine", SearchEngine.entries.map { it to it.label }, s.searchEngine,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(searchEngine = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.FONT -> ChoiceDialog(
            "Schriftart", HomeFont.entries.map { it to it.label }, s.font,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(font = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.WEIGHT -> ChoiceDialog(
            "Schriftstärke", HomeWeight.entries.map { it to it.label }, s.fontWeight,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(fontWeight = v) }; dialog = SettingsDialog.NONE }
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

    categoryLimitFor?.let { category ->
        ChoiceDialog(
            title = "Limit ${AppCategories.label(category)}",
            options = listOf(0 to "Kein Limit") + listOf(15, 30, 45, 60, 90, 120, 180).map { it to "$it Minuten" },
            selected = s.categoryLimits[category.toString()] ?: 0,
            onDismiss = { categoryLimitFor = null },
        ) { minutes ->
            vm.setCategoryLimit(category, minutes)
            categoryLimitFor = null
            if (minutes > 0 && !ScreenTime.hasAccess(context)) SystemActions.openUsageAccess(context)
        }
    }
    if (editGrayscale) {
        ScheduleDialog(
            title = "Icons in Graustufen",
            existing = s.grayscaleSchedule ?: PageSchedule(setOf(1, 2, 3, 4, 5, 6, 7), 21 * 60, 7 * 60),
            onDismiss = { editGrayscale = false },
            onSave = { schedule ->
                vm.update { it.copy(grayscaleSchedule = schedule) }
                editGrayscale = false
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
    if (editingCity) {
        TextInputDialog(
            title = "Ort fürs Wetter",
            initial = s.weatherCity,
            hint = "z. B. Zürich – leer = Standort",
            onDismiss = { editingCity = false },
            onConfirm = { city ->
                vm.update { it.copy(weatherCity = city.trim()) }
                editingCity = false
                if (city.isBlank() && !Weather.hasLocationPermission(context)) {
                    disclosure = Disclosure.WEATHER_LOCATION
                }
            },
        )
    }
    if (showPaywall || paywallFor != null) {
        PaywallDialog(feature = paywallFor, onDismiss = {
            showPaywall = false
            paywallFor = null
        })
    }
    disclosure?.let { d ->
        DisclosureDialog(
            disclosure = d,
            onAccept = {
                when (d) {
                    Disclosure.ACCESSIBILITY -> SystemActions.openAccessibility(context)
                    Disclosure.WEATHER_LOCATION -> requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    Disclosure.WIFI_LOCATION -> requestFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            onDismiss = { disclosure = null },
        )
    }
    contextPageId?.let { id ->
        s.pages.firstOrNull { it.id == id }?.let { page ->
            val hasBluetooth = remember(permissionTick, resumeTick) { ContextMonitor.hasBluetoothPermission(context) }
            val hasWifi = remember(permissionTick, resumeTick) { ContextMonitor.hasWifiPermission(context) }
            val bonded = remember(permissionTick, resumeTick) { ContextMonitor.bondedDevices(context) }
            ContextDialog(
                title = "Kontext: ${page.name}",
                existing = page.context,
                current = contextState,
                bondedDevices = bonded,
                hasBluetooth = hasBluetooth,
                hasWifi = hasWifi,
                onRequestBluetooth = {
                    if (Build.VERSION.SDK_INT >= 31) requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                },
                onRequestWifi = { disclosure = Disclosure.WIFI_LOCATION },
                onDismiss = { contextPageId = null },
                onSave = { ctx ->
                    vm.setPageContext(id, ctx)
                    contextPageId = null
                },
            )
        }
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
