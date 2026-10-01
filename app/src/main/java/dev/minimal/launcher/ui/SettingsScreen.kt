package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
import android.Manifest
import dev.minimal.launcher.data.KansoStyle
import dev.minimal.launcher.data.NotificationDigest
import dev.minimal.launcher.util.DigestScheduler
import kotlinx.coroutines.Dispatchers
import dev.minimal.launcher.util.IntentionReminder
import dev.minimal.launcher.util.EveningRecapScheduler
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

private enum class SettingsDialog { NONE, STYLE, THEME, ACCENT, ICON_PACK, DOUBLE_TAP, SWIPE_DOWN, SWIPE_UP, NEW_FOLDER, NEW_PAGE, FONT, WEIGHT, HOME_PRESS, ENGINE }

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
    var pickingRecapTime by remember { mutableStateOf(false) }
    var pickingDigestTimes by remember { mutableStateOf(false) }
    val requestDigestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        DigestScheduler.sync(context)
    }
    val requestRecapNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            vm.update { it.copy(eveningRecap = false) }
            Toast.makeText(context, tr("Ohne Benachrichtigungen kein Abendrückblick", "No evening recap without notifications"), Toast.LENGTH_SHORT).show()
        }
        EveningRecapScheduler.sync(context)
    }
    val pro = isPro()
    var paywallFor by remember { mutableStateOf<String?>(null) }
    var showPaywall by remember { mutableStateOf(false) }
    var disclosure by remember { mutableStateOf<Disclosure?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val scope = rememberCoroutineScope()
    var backupTick by remember { mutableIntStateOf(0) }
    val lastBackupText = remember(backupTick, resumeTick) {
        val last = AutoBackup.lastRun(context)
        if (last == 0L) tr("noch keine", "none yet") else DateUtils.getRelativeTimeSpanString(last).toString()
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
            Toast.makeText(context, if (ok) tr("Erste Sicherung gespeichert", "First backup saved") else tr("Ordner nicht beschreibbar", "Folder not writable"), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, tr("Ohne Standort bitte einen festen Ort eintragen", "Without location, please enter a fixed place"), Toast.LENGTH_LONG).show()
            editingCity = true
        }
    }
    var calendarAllowed by remember(resumeTick) { mutableStateOf(CalendarEvents.hasPermission(context)) }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarAllowed = granted
        vm.update { it.copy(showEvents = granted) }
        if (!granted) Toast.makeText(context, tr("Ohne Kalenderzugriff können keine Termine angezeigt werden", "Without calendar access, events can't be shown"), Toast.LENGTH_SHORT).show()
    }

    val store = context.launcherApp.settings
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val ok = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) } != null
        } catch (e: Exception) {
            false
        }
        Toast.makeText(context, if (ok) tr("Sicherung gespeichert", "Backup saved") else tr("Sicherung fehlgeschlagen", "Backup failed"), Toast.LENGTH_SHORT).show()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val json = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            null
        }
        val ok = json != null && store.importJson(json)
        Toast.makeText(context, if (ok) tr("Einstellungen wiederhergestellt", "Settings restored") else tr("Datei ungültig", "Invalid file"), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Launcher-Einstellungen", "Launcher settings")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Zurück", "Back"))
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
                        StatusRow(tr("Pro ist freigeschaltet – danke!", "Pro is unlocked – thank you!"), true, "") {}
                    } else {
                        ClickRow(tr("Pro freischalten", "Unlock Pro"), tr("Einmalkauf – kontextbasierte Seiten, eigene Icons, Sicherung, Wochenbericht, Aufgaben", "One-time purchase – context pages, pop-up widgets, intentions, weekly report & more")) {
                            paywallFor = null
                            showPaywall = true
                        }
                    }
                }
                if (!pro) item { ClickRow(tr("Käufe wiederherstellen", "Restore purchases"), null) { Pro.restore() } }
            }
            item { Section(tr("Einrichtung", "Setup")) }
            item { ClickRow(tr("Einrichtungsassistent erneut zeigen", "Show setup assistant again"), null) { vm.update { it.copy(onboardingDone = false) } } }
            item {
                StatusRow(tr("Standard-Launcher", "Default launcher"), isDefault, tr("Festlegen", "Set")) { SystemActions.openHomeSettings(context) }
            }
            item {
                StatusRow(tr("Benachrichtigungszugriff (Punkte & Vorschau)", "Notification access (dots & preview)"), hasNotificationAccess, tr("Erlauben", "Allow")) {
                    SystemActions.openNotificationAccess(context)
                }
            }
            item {
                StatusRow(tr("Bedienungshilfe (Sperren per Doppeltipp)", "Accessibility service (double tap to lock)"), accessibilityOn, tr("Aktivieren", "Enable")) {
                    disclosure = Disclosure.ACCESSIBILITY
                }
            }
            if (listenerDisconnected) {
                item {
                    Hint(
                        tr("Der Benachrichtigungsdienst wurde vom System beendet – Punkte und Mediensteuerung ", "The notification service was stopped by the system – dots and media controls ") +
                            tr("fehlen deshalb. Der Launcher versucht, ihn neu zu verbinden. Hilft das nicht, ", "are missing. The launcher tries to reconnect it. If that doesn't help, ") +
                            tr("den Launcher unten von der Akku-Optimierung ausnehmen.", "exclude the launcher from battery optimization below.")
                    )
                }
            }
            DeviceCompat.aggressiveVendor?.let { vendor ->
                item {
                    StatusRow(tr("Von Akku-Optimierung ausgenommen", "Excluded from battery optimization"), batteryUnrestricted, tr("Ausnehmen", "Exclude")) {
                        DeviceCompat.requestIgnoreBatteryOptimizations(context)
                    }
                }
                item {
                    ClickRow(tr("Autostart / Hintergrundaktivität erlauben ($vendor)", "Allow autostart / background activity ($vendor)"), tr("Öffnet die Seite des Herstellers", "Opens the manufacturer's page")) {
                        DeviceCompat.openAutostart(context)
                    }
                }
                item {
                    Hint(
                        tr("$vendor beendet Hintergrunddienste oft aggressiv. Damit Benachrichtigungspunkte und ", "$vendor often kills background services aggressively. To keep notification dots and ") +
                            tr("Mediensteuerung zuverlässig bleiben: Akku-Optimierung ausnehmen und Autostart erlauben.", "media controls reliable: exclude from battery optimization and allow autostart.")
                    )
                }
            }
            if (!hasNotificationAccess || !accessibilityOn) {
                item {
                    Hint(
                        tr("Meldet Android „App wurde Zugriff verweigert“ / „Eingeschränkte Einstellung“? ", "Android says “App was denied access” / “Restricted setting”? ") +
                            tr("Dann zuerst in der App-Info oben rechts auf ⋮ tippen und ", "Then first tap ⋮ at the top right in app info and ") +
                            tr("„Eingeschränkte Einstellungen zulassen“ wählen. Danach klappt das Erlauben.", "choose “Allow restricted settings”. Allowing works after that.")
                    )
                }
                item { ClickRow(tr("App-Info öffnen", "Open app info"), tr("Um eingeschränkte Einstellungen zuzulassen", "To allow restricted settings")) { SystemActions.openAppDetails(context) } }
            }

            item { Section(tr("Darstellung", "Appearance")) }
            item {
                ClickRow(
                    tr("Kanso-Stil", "Kanso style") + if (pro) "" else " (Pro)",
                    (if (pro) s.kansoStyle else KansoStyle.NONE).label,
                ) { if (pro) dialog = SettingsDialog.STYLE else paywallFor = tr("Kanso-Stile", "Kanso styles") }
            }
            if (s.kansoStyle.active && pro) {
                item { SwitchRow(tr("Ensō im Hintergrund", "Ensō in the background"), s.kansoEnso) { v -> vm.update { it.copy(kansoEnso = v) } } }
                item {
                    Hint(
                        tr(
                            "Der Stil ersetzt Hintergrundbild, Design und Akzentfarbe durch abgestimmte, ruhige Farben.",
                            "The style replaces wallpaper, theme and accent color with calm, matching colors.",
                        )
                    )
                }
            }
            item {
                ClickRow(tr("Design", "Theme"), when (s.themeMode) {
                    ThemeMode.SYSTEM -> tr("Wie System", "System default")
                    ThemeMode.LIGHT -> tr("Hell", "Light")
                    ThemeMode.DARK -> tr("Dunkel", "Dark")
                }) { dialog = SettingsDialog.THEME }
            }
            item {
                ClickRow(tr("Akzentfarbe", "Accent color"), ACCENT_COLORS.firstOrNull { it.first == s.accent }?.second ?: tr("Eigene", "Custom")) {
                    dialog = SettingsDialog.ACCENT
                }
            }
            item {
                ClickRow(tr("Icon-Pack", "Icon pack"), iconPacks.firstOrNull { it.first == s.iconPack }?.second ?: tr("Standard", "Default")) {
                    dialog = SettingsDialog.ICON_PACK
                }
            }
            item { SwitchRow(tr("App-Icons anzeigen", "Show app icons"), s.showIcons) { v -> vm.update { it.copy(showIcons = v) } } }
            if (Build.VERSION.SDK_INT >= 33) {
                item {
                    SwitchRow(tr("Designsymbole (einfarbig in Systemfarbe)", "Themed icons (monochrome in system color)"), s.themedIcons) { v ->
                        vm.update { it.copy(themedIcons = v) }
                    }
                }
            }
            item {
                SliderRow(tr("Icon-Größe", "Icon size"), s.iconSize.toFloat(), 24f..56f, "${s.iconSize} dp") { v ->
                    vm.update { it.copy(iconSize = v.toInt()) }
                }
            }
            item { ClickRow(tr("Schriftart", "Font"), s.font.label) { dialog = SettingsDialog.FONT } }
            item { ClickRow(tr("Schriftstärke", "Font weight"), s.fontWeight.label) { dialog = SettingsDialog.WEIGHT } }
            item {
                SliderRow(tr("Schriftgröße", "Font size"), s.textScale, 0.7f..1.5f, "${(s.textScale * 100).toInt()} %") { v ->
                    vm.update { it.copy(textScale = v) }
                }
            }
            item {
                SliderRow(tr("Hintergrund abdunkeln", "Dim wallpaper"), s.wallpaperDim, 0f..0.8f, "${(s.wallpaperDim * 100).toInt()} %") { v ->
                    vm.update { it.copy(wallpaperDim = v) }
                }
            }
            item { SwitchRow(tr("Hintergrund weichzeichnen (Android 12+)", "Blur wallpaper (Android 12+)"), s.blur) { v -> vm.update { it.copy(blur = v) } } }
            item { SwitchRow(tr("Uhr anzeigen", "Show clock"), s.showClock) { v -> vm.update { it.copy(showClock = v) } } }
            item { SwitchRow(tr("Datum anzeigen", "Show date"), s.showDate) { v -> vm.update { it.copy(showDate = v) } } }
            item { SwitchRow(tr("Nächsten Wecker anzeigen", "Show next alarm"), s.showAlarm) { v -> vm.update { it.copy(showAlarm = v) } } }
            item {
                SwitchRow(tr("Nächsten Termin anzeigen", "Show next event"), s.showEvents && calendarAllowed) { v ->
                    if (v && !CalendarEvents.hasPermission(context)) {
                        requestCalendar.launch(Manifest.permission.READ_CALENDAR)
                    } else {
                        vm.update { it.copy(showEvents = v) }
                    }
                }
            }
            if (s.showEvents) {
                item {
                    SliderRow(tr("Anzahl Termine", "Number of events"), s.eventCount.toFloat(), 1f..3f, "${s.eventCount}") { v ->
                        vm.update { it.copy(eventCount = v.roundToInt().coerceIn(1, 3)) }
                    }
                }
            }
            item { SwitchRow(tr("Aufgaben unter der Uhr anzeigen", "Show tasks below the clock"), s.showTasks) { v -> vm.update { it.copy(showTasks = v) } } }
            if (s.tasks.any { it.isDone }) {
                item { ClickRow(tr("Erledigte Aufgaben jetzt entfernen", "Remove completed tasks now"), null) { vm.clearDoneTasks() } }
            }
            item { SwitchRow(tr("Neue Apps in der Liste markieren", "Mark new apps in the list"), s.markNewApps) { v -> vm.update { it.copy(markNewApps = v) } } }
            item {
                SwitchRow(tr("Mediensteuerung (Musik, Podcasts)", "Media controls (music, podcasts)"), s.showMedia) { v -> vm.update { it.copy(showMedia = v) } }
            }
            item {
                SwitchRow(tr("Akku beim Laden und unter 20 % anzeigen", "Show battery when charging and below 20 %"), s.showBattery) { v -> vm.update { it.copy(showBattery = v) } }
            }
            item {
                SwitchRow(tr("Wetter unter der Uhr (Internet, Open-Meteo)", "Weather below the clock (internet, Open-Meteo)"), s.showWeather) { v ->
                    vm.update { it.copy(showWeather = v) }
                    if (v && s.weatherCity.isBlank() && !Weather.hasLocationPermission(context)) {
                        disclosure = Disclosure.WEATHER_LOCATION
                    }
                }
            }
            if (s.showWeather) {
                item {
                    ClickRow(
                        tr("Ort fürs Wetter", "Place for weather"),
                        s.weatherCity.ifBlank { tr("Automatisch (ungefährer Standort)", "Automatic (approximate location)") },
                    ) { editingCity = true }
                }
                item {
                    Hint(
                        tr("Wetterdaten von Open-Meteo, ohne Konto und Tracking. Mit festem Ort ist keine ", "Weather data from Open-Meteo, no account or tracking. With a fixed place no ") +
                            tr("Standortberechtigung nötig; sonst wird der Standort auf ca. 1 km gerundet. ", "location permission is needed; otherwise the location is rounded to about 1 km. ") +
                            tr("Aktualisierung alle 30 Minuten.", "Updated every 30 minutes.")
                    )
                }
            }
            item {
                SwitchRow(tr("Bildschirmzeit unter der Uhr", "Screen time below the clock"), s.showScreenTime) { v ->
                    vm.update { it.copy(showScreenTime = v) }
                    if (v && !ScreenTime.hasAccess(context)) SystemActions.openUsageAccess(context)
                }
            }
            if (s.showScreenTime && !usageAccess) {
                item {
                    Hint(tr("Dafür „Nutzungszugriff“ für Kanso erlauben. Die Daten bleiben auf dem Gerät.", "Allow “Usage access” for Kanso. The data stays on the device."))
                }
                item { ClickRow(tr("Nutzungszugriff erlauben", "Allow usage access"), null) { SystemActions.openUsageAccess(context) } }
            }
            item { SwitchRow(tr("Buchstabenleiste links (Linkshänder)", "Letter bar on the left (left-handed)"), s.alphabetLeft) { v -> vm.update { it.copy(alphabetLeft = v) } } }

            item { Section(tr("Benachrichtigungen", "Notifications")) }
            item { SwitchRow(tr("Benachrichtigungspunkte", "Notification dots"), s.notificationDots) { v -> vm.update { it.copy(notificationDots = v) } } }
            item {
                SwitchRow(tr("Zusammenfassung ablenkender Apps", "Digest for distracting apps") + if (pro) "" else " (Pro)", s.digestEnabled && pro) { v ->
                    if (!pro) {
                        paywallFor = tr("Benachrichtigungs-Zusammenfassung", "Notification digest")
                    } else {
                        vm.update { it.copy(digestEnabled = v) }
                        if (v && !NotificationStore.hasAccess(context)) {
                            Toast.makeText(
                                context,
                                tr("Dafür bitte den Benachrichtigungszugriff erlauben", "Please allow notification access for this"),
                                Toast.LENGTH_LONG,
                            ).show()
                            SystemActions.openNotificationAccess(context)
                        }
                        if (v && !IntentionReminder.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestDigestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            DigestScheduler.sync(context)
                        }
                    }
                }
            }
            if (s.digestEnabled && pro) {
                item {
                    ClickRow(tr("Zustellung", "Delivery"), NotificationDigest.describe(s.digestTimes)) { pickingDigestTimes = true }
                }
                if (s.focusApps.isEmpty()) {
                    item {
                        Hint(
                            tr(
                                "Noch keine ablenkenden Apps markiert – App lange drücken → „Als ablenkend markieren“.",
                                "No distracting apps marked yet – long-press an app → “Mark as distracting”.",
                            )
                        )
                    }
                }
            }
            item {
                Hint(
                    tr(
                        "Benachrichtigungen deiner ablenkenden Apps verschwinden aus der Leiste und kommen gesammelt zu festen " +
                            "Zeiten. Anrufe, Wecker und Erinnerungen kommen immer sofort.",
                        "Notifications from your distracting apps leave the shade and arrive bundled at set times. " +
                            "Calls, alarms and reminders always come through immediately.",
                    )
                )
            }
            item {
                SwitchRow(tr("Vorschau unter Favoriten", "Preview below favorites"), s.notificationPreview) { v ->
                    vm.update { it.copy(notificationPreview = v) }
                }
            }

            item { Section(tr("Gesten", "Gestures")) }
            item { ClickRow(tr("Doppeltippen", "Double tap"), s.doubleTap.label) { dialog = SettingsDialog.DOUBLE_TAP } }
            item { ClickRow(tr("Nach unten wischen", "Swipe down"), s.swipeDown.label) { dialog = SettingsDialog.SWIPE_DOWN } }
            item { ClickRow(tr("Nach oben wischen", "Swipe up"), s.swipeUp.label) { dialog = SettingsDialog.SWIPE_UP } }
            item { ClickRow(tr("Home-Taste auf dem Startbildschirm", "Home button on the home screen"), s.homePress.label) { dialog = SettingsDialog.HOME_PRESS } }
            item { ClickRow(tr("Suchmaschine", "Search engine"), s.searchEngine.label) { dialog = SettingsDialog.ENGINE } }
            item { SwitchRow(tr("Tastatur bei Suche automatisch öffnen", "Open keyboard automatically in search"), s.autoKeyboard) { v -> vm.update { it.copy(autoKeyboard = v) } } }
            item { SwitchRow(tr("Kontakte in der Suche", "Contacts in search"), s.searchContacts) { v -> vm.update { it.copy(searchContacts = v) } } }
            item { SwitchRow(tr("App-Aktionen in der Suche (z. B. „Neue Nachricht“)", "App actions in search (e.g. “New message”)"), s.searchShortcuts) { v -> vm.update { it.copy(searchShortcuts = v) } } }
            item { SwitchRow(tr("Vorschläge (meistgenutzte Apps)", "Suggestions (most used apps)"), s.showSuggestions) { v -> vm.update { it.copy(showSuggestions = v) } } }
            item {
                ClickRow(tr("Nutzungsverlauf löschen", "Clear usage history"), tr("Setzt Vorschläge und Sortierung zurück", "Resets suggestions and sorting")) {
                    vm.clearUsage()
                    Toast.makeText(context, tr("Nutzungsverlauf gelöscht", "Usage history cleared"), Toast.LENGTH_SHORT).show()
                }
            }

            item { Section(tr("Favoriten & Seiten", "Favorites & pages")) }
            item { Hint(tr("Auf dem Startbildschirm nach links/rechts wischen oder den Seitennamen antippen, um die Seite zu wechseln.", "On the home screen, swipe left/right or tap the page name to switch pages.")) }
            if (s.pages.size > 1) {
                item {
                    SwitchRow(tr("Seite automatisch wechseln (Zeitplan & Kontext)", "Switch pages automatically (schedule & context)"), s.autoPages) { v -> vm.setAutoPages(v) }
                }
                if (s.autoPages) {
                    item {
                        Hint(
                            tr("⏰ Zeitplan (Tage + Uhrzeit) und 📍 Kontext (Kopfhörer, Laden, Bluetooth-Gerät, WLAN) ", "⏰ Schedule (days + time) and 📍 context (headphones, charging, Bluetooth device, Wi-Fi) ") +
                                tr("pro Seite. Kontext hat Vorrang vor Zeitplänen; sonst gilt die erste Seite ohne Regeln. ", "per page. Context takes priority over schedules; otherwise the first page without rules applies. ") +
                                tr("Gewechselt wird nur, wenn sich etwas ändert – dazwischen kannst du frei wechseln.", "Pages only switch when something changes – in between you can switch freely.")
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
                            if (pro) contextPageId = page.id else paywallFor = tr("Kontextbasierte Seiten", "Context-based pages")
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
                    item(key = "page_empty_${page.id}") { Hint(tr("Keine Favoriten auf dieser Seite.", "No favorites on this page.")) }
                }
                pageFavs.forEachIndexed { index, fav ->
                    item(key = "fav_${fav.id}") {
                        val label = if (fav.isContact) {
                            "👤 " + (fav.name ?: tr("Kontakt", "Contact"))
                        } else if (fav.isFolder) {
                            "📁 " + (fav.name ?: tr("Ordner", "Folder")) + " (${fav.apps.size})"
                        } else {
                            fav.apps.firstOrNull()?.let { appsByKey[it]?.label } ?: tr("Nicht installiert", "Not installed")
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
            item { ClickRow(tr("Seite hinzufügen", "Add page"), tr("z. B. „Arbeit“ oder „Privat“", "e.g. “Work” or “Personal”")) { dialog = SettingsDialog.NEW_PAGE } }
            item { ClickRow(tr("Ordner erstellen", "Create folder"), tr("Mehrere Apps unter einem Favoriten", "Several apps under one favorite")) { dialog = SettingsDialog.NEW_FOLDER } }

            item { Section(tr("Fokus-Modus", "Focus mode")) }
            item {
                Hint(
                    tr("Ablenkende Apps werden im Fokus-Modus ausgegraut, ihre Benachrichtigungen ausgeblendet, ", "In focus mode, distracting apps are grayed out, their notifications hidden, ") +
                        tr("und vor dem Öffnen gibt es eine kurze Denkpause. Schnell umschalten: leeren Bereich ", "and there is a short pause before opening them. Quick toggle: long-press an empty area ") +
                        tr("auf dem Startbildschirm lange drücken.", "on the home screen.")
                )
            }
            item { SwitchRow(tr("Fokus-Modus jetzt aktiv", "Focus mode on now"), s.focusManual) { v -> vm.setFocusManual(v) } }
            item {
                ClickRow(tr("Zeitplan", "Schedule"), s.focusSchedule?.describe() ?: tr("Kein Zeitplan – nur manuell", "No schedule – manual only")) { editFocusSchedule = true }
            }
            item {
                SliderRow(
                    tr("Denkpause vor dem Öffnen", "Pause before opening"),
                    s.focusPauseSeconds.toFloat(),
                    0f..30f,
                    if (s.focusPauseSeconds == 0) tr("keine", "none") else "${s.focusPauseSeconds} s",
                ) { v -> vm.update { it.copy(focusPauseSeconds = v.roundToInt()) } }
            }
            item {
                SwitchRow(tr("Absichtsfrage", "Intention prompt") + if (pro) "" else " (Pro)", s.intentionPrompt && pro) { v ->
                    if (!pro) paywallFor = tr("Absichtsfrage", "Intention prompt") else vm.update { it.copy(intentionPrompt = v) }
                }
            }
            item {
                Hint(
                    tr("Statt nur zu warten fragt Kanso: „Wozu öffnest du …?“ – mit Zähler, wie oft du die App heute ", "Instead of just waiting, Kanso asks: “Why are you opening …?” – with a count of how often you opened the app today ") +
                        tr("schon geöffnet hast, und optionalem Timer, der dich danach an deine Absicht erinnert.", "and an optional timer that reminds you of your intention afterwards.")
                )
            }
            if (s.intentionPrompt && pro) {
                item {
                    SwitchRow(tr("Auch ausserhalb des Fokus-Modus fragen", "Also ask outside focus mode"), s.intentionAlways) { v ->
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
            item { ClickRow(tr("Ablenkende App hinzufügen", "Add distracting app"), if (s.focusApps.isEmpty()) tr("Noch keine ausgewählt", "None selected yet") else tr("${s.focusApps.size} ausgewählt", "${s.focusApps.size} selected")) { pickingFocusApp = true } }

            item {
                ClickRow(
                    tr("Icons in Graustufen", "Grayscale icons"),
                    s.grayscaleSchedule?.describe() ?: tr("Aus – z. B. abends, damit bunte Apps weniger locken", "Off – e.g. in the evening, so colorful apps tempt less"),
                ) { editGrayscale = true }
            }
            item { Section(tr("Tageslimits", "Daily limits")) }
            if (!pro) {
                item { ClickRow(tr("Tagesziel & Kategorie-Limits (Pro)", "Daily goal & category limits (Pro)"), tr("Wochenbericht, Ziel mit Serie, Limits pro Kategorie", "Weekly report, goal with streak, limits per category")) { paywallFor = tr("Tagesziel & Kategorie-Limits", "Daily goal & category limits") } }
            }
            if (pro) item {
                SliderRow(
                    tr("Tagesziel Bildschirmzeit", "Daily screen time goal"),
                    s.dailyGoalMinutes.toFloat(),
                    0f..480f,
                    if (s.dailyGoalMinutes == 0) tr("aus", "off") else ScreenTime.format(s.dailyGoalMinutes * 60_000L),
                ) { v -> vm.update { it.copy(dailyGoalMinutes = ((v / 15).roundToInt() * 15)) } }
            }
            item { Hint(tr("Das Ziel erscheint im Wochenbericht (Bildschirmzeit unter der Uhr antippen) samt Serie.", "The goal appears in the weekly report (tap screen time below the clock) with your streak.")) }
            item {
                SwitchRow(tr("Tagesabsicht am Morgen", "Daily intention in the morning") + if (pro) "" else " (Pro)", s.dailyIntentionEnabled && pro) { v ->
                    if (!pro) paywallFor = tr("Tagesabsicht", "Daily intention") else vm.update { it.copy(dailyIntentionEnabled = v) }
                }
            }
            item {
                Hint(
                    tr(
                        "Unter der Uhr erscheint „Was ist dir heute wichtig?“ – eine Sache, die dich durch den Tag begleitet. Der Abendrückblick greift sie auf.",
                        "Below the clock you'll see “What matters to you today?” – one thing that guides your day. The evening recap picks it up.",
                    )
                )
            }
            item {
                SwitchRow(tr("Abendrückblick", "Evening recap") + if (pro) "" else " (Pro)", s.eveningRecap && pro) { v ->
                    if (!pro) {
                        paywallFor = tr("Abendrückblick", "Evening recap")
                    } else {
                        vm.update { it.copy(eveningRecap = v) }
                        if (v && !IntentionReminder.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestRecapNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            EveningRecapScheduler.sync(context)
                        }
                    }
                }
            }
            if (s.eveningRecap && pro) {
                item {
                    ClickRow(tr("Uhrzeit", "Time"), "%02d:%02d".format(s.eveningRecapMinute / 60, s.eveningRecapMinute % 60)) {
                        pickingRecapTime = true
                    }
                }
                item {
                    ClickRow(tr("Rückblick jetzt anzeigen", "Show recap now"), tr("Vorschau mit den Zahlen von heute", "Preview with today's numbers")) {
                        if (!IntentionReminder.canNotify(context)) {
                            Toast.makeText(
                                context,
                                tr("Bitte zuerst Benachrichtigungen für Kanso erlauben", "Please allow notifications for Kanso first"),
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            scope.launch(Dispatchers.IO) { EveningRecapScheduler.show(context) }
                        }
                    }
                }
            }
            item {
                Hint(
                    tr("Jeden Abend eine leise Benachrichtigung: Bildschirmzeit, Tagesziel, bewusste Öffnungen ", "A quiet notification every evening: screen time, daily goal, mindful opens ") +
                        tr("und erledigte Aufgaben. Antippen öffnet den Wochenbericht.", "and completed tasks. Tap to open the weekly report.")
                )
            }
            if (pro) AppCategories.all.forEach { (category, label) ->
                item(key = "cat_$category") {
                    ClickRow(
                        tr("Limit $label", "Limit $label"),
                        s.categoryLimits[category.toString()]?.let { tr("$it min pro Tag (alle $label-Apps zusammen)", "$it min per day (all $label apps together)") } ?: tr("Kein Limit", "No limit"),
                    ) { categoryLimitFor = category }
                }
            }
            if (s.appLimits.isEmpty()) {
                item { Hint(tr("Keine. App lange drücken → „Tageslimit“. Benötigt „Nutzungszugriff“.", "None. Long-press an app → “Daily limit”. Requires “Usage access”.")) }
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

            item { Section(tr("App-Sperre", "App lock")) }
            item {
                Hint(
                    tr("Gesperrte Apps öffnen sich aus dem Launcher nur nach Fingerabdruck oder PIN; ihre ", "Locked apps only open from the launcher after fingerprint or PIN; their ") +
                        tr("Benachrichtigungsvorschau wird ausgeblendet. Hinzufügen: App lange drücken → „Mit ", "notification preview is hidden. To add: long-press an app → “Lock with ") +
                        tr("Fingerabdruck/PIN sperren“. Hinweis: Über „Zuletzt verwendet“ oder Benachrichtigungen ", "fingerprint/PIN”. Note: via “Recents” or notifications ") +
                        tr("bleibt die App erreichbar – das kann nur Android selbst verhindern.", "the app stays reachable – only Android itself can prevent that.")
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

            item { Section(tr("Ausgeblendete Apps", "Hidden apps")) }
            item { ClickRow(tr("Aufräumen", "Declutter"), tr("Apps finden, die du lange nicht geöffnet hast", "Find apps you haven't opened in a long time")) { showDeclutter = true } }
            if (s.hidden.isEmpty()) {
                item { Hint(tr("Keine. Halte eine App gedrückt und wähle „Ausblenden“.", "None. Long-press an app and choose “Hide”.")) }
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
                        TextButton(onClick = { vm.unhide(key) }) { Text(tr("Einblenden", "Unhide")) }
                    }
                }
            }

            if (s.renamed.isNotEmpty()) {
                item { Section(tr("Umbenannte Apps", "Renamed apps")) }
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
                            TextButton(onClick = { appsByKey[key]?.let { vm.rename(it, null) } }) { Text(tr("Zurücksetzen", "Reset")) }
                        }
                    }
                }
            }

            item { Section(tr("Fehlerprotokoll", "Crash log")) }
            val log = crashLog
            if (log == null) {
                item { Hint(tr("Keine Abstürze aufgezeichnet.", "No crashes recorded.")) }
            } else {
                item { Hint(log.lineSequence().take(2).joinToString("\n")) }
                item {
                    ClickRow(tr("Protokoll teilen", "Share log"), tr("Zum Beispiel per E-Mail oder Chat senden", "Send e.g. by email or chat")) {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, tr("Kanso – Fehlerprotokoll", "Kanso – crash log"))
                            .putExtra(Intent.EXTRA_TEXT, log)
                        SystemActions.start(context, Intent.createChooser(send, tr("Protokoll teilen", "Share log")))
                    }
                }
                item {
                    ClickRow(tr("Protokoll löschen", "Clear log"), null) {
                        CrashLog.clear(context)
                        crashLog = null
                    }
                }
            }

            item { Section(tr("Diagnose", "Diagnostics")) }
            item {
                ClickRow(tr("Diagnose teilen", "Share diagnostics"), tr("Technische Infos für die Fehlersuche (Profile, Berechtigungen)", "Technical info for troubleshooting (profiles, permissions)")) {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, tr("Kanso – Diagnose", "Kanso – diagnostics"))
                        .putExtra(Intent.EXTRA_TEXT, vm.diagnostics())
                    SystemActions.start(context, Intent.createChooser(send, tr("Diagnose teilen", "Share diagnostics")))
                }
            }

            item { Section(tr("Sicherung", "Backup")) }
            item {
                ClickRow(
                    tr("Automatische Sicherung", "Automatic backup"),
                    s.backupFolder?.let { tr("Täglich nach „${AutoBackup.folderLabel(it)}“ · letzte: $lastBackupText", "Daily to “${AutoBackup.folderLabel(it)}” · last: $lastBackupText") }
                        ?: tr("Aus – Ordner wählen, dann täglich (7 Stände werden behalten)", "Off – choose a folder, then daily (7 versions are kept)"),
                ) { if (pro) pickBackupFolder.launch(null) else paywallFor = tr("Automatische Sicherung", "Automatic backup") }
            }
            if (s.backupFolder != null) {
                item {
                    ClickRow(tr("Jetzt sichern", "Back up now"), null) {
                        scope.launch {
                            val ok = vm.backupNow()
                            backupTick++
                            Toast.makeText(context, if (ok) tr("Sicherung gespeichert", "Backup saved") else tr("Sicherung fehlgeschlagen", "Backup failed"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                item {
                    ClickRow(tr("Automatische Sicherung ausschalten", "Turn off automatic backup"), null) {
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
            item { ClickRow(tr("Einstellungen exportieren", "Export settings"), tr("Als JSON-Datei speichern", "Save as JSON file")) { exportLauncher.launch("kanso-backup.json") } }
            item { ClickRow(tr("Einstellungen importieren", "Import settings"), tr("Aus JSON-Datei wiederherstellen", "Restore from JSON file")) { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) } }
        }
    }

    if (showDeclutter) DeclutterDialog(vm = vm, onDismiss = { showDeclutter = false })
    if (pickingDigestTimes) {
        ChoiceDialog(
            title = tr("Zusammenfassung um", "Digest at"),
            options = NotificationDigest.PRESETS.map { it to NotificationDigest.describe(it) },
            selected = s.digestTimes,
            onDismiss = { pickingDigestTimes = false },
        ) { times ->
            vm.update { it.copy(digestTimes = times) }
            DigestScheduler.sync(context)
            pickingDigestTimes = false
        }
    }
    if (pickingRecapTime) {
        ChoiceDialog(
            title = tr("Abendrückblick um", "Evening recap at"),
            options = (36..47).map { half -> half * 30 to "%02d:%02d".format(half / 2, half % 2 * 30) },
            selected = s.eveningRecapMinute,
            onDismiss = { pickingRecapTime = false },
        ) { minute ->
            vm.update { it.copy(eveningRecapMinute = minute) }
            EveningRecapScheduler.sync(context)
            pickingRecapTime = false
        }
    }

    when (dialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.THEME -> ChoiceDialog(
            tr("Design", "Theme"),
            listOf(ThemeMode.SYSTEM to tr("Wie System", "System default"), ThemeMode.LIGHT to tr("Hell", "Light"), ThemeMode.DARK to tr("Dunkel", "Dark")),
            s.themeMode,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(themeMode = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.ACCENT -> ChoiceDialog(
            tr("Akzentfarbe", "Accent color"), ACCENT_COLORS, s.accent,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(accent = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.ICON_PACK -> ChoiceDialog(
            tr("Icon-Pack", "Icon pack"),
            listOf<Pair<String?, String>>(null to tr("Standard", "Default")) + iconPacks.map { it.first to it.second },
            s.iconPack,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(iconPack = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.DOUBLE_TAP -> GestureDialog(tr("Doppeltippen", "Double tap"), s.doubleTap, { dialog = SettingsDialog.NONE }) { v ->
            vm.update { it.copy(doubleTap = v) }
        }
        SettingsDialog.SWIPE_DOWN -> GestureDialog(tr("Nach unten wischen", "Swipe down"), s.swipeDown, { dialog = SettingsDialog.NONE }) { v ->
            vm.update { it.copy(swipeDown = v) }
        }
        SettingsDialog.SWIPE_UP -> GestureDialog(tr("Nach oben wischen", "Swipe up"), s.swipeUp, { dialog = SettingsDialog.NONE }) { v ->
            vm.update { it.copy(swipeUp = v) }
        }
        SettingsDialog.HOME_PRESS -> GestureDialog(tr("Home-Taste auf dem Startbildschirm", "Home button on the home screen"), s.homePress, { dialog = SettingsDialog.NONE }) { v ->
            vm.update { it.copy(homePress = v) }
        }
        SettingsDialog.ENGINE -> ChoiceDialog(
            tr("Suchmaschine", "Search engine"), SearchEngine.entries.map { it to it.label }, s.searchEngine,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(searchEngine = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.FONT -> ChoiceDialog(
            tr("Schriftart", "Font"), HomeFont.entries.map { it to it.label + if (it.pro && !pro) " (Pro)" else "" }, s.font,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v ->
            if (v.pro && !pro) paywallFor = tr("Kanso-Schriften", "Kanso fonts") else vm.update { it.copy(font = v) }
            dialog = SettingsDialog.NONE
        }
        SettingsDialog.STYLE -> ChoiceDialog(
            tr("Kanso-Stil", "Kanso style"), KansoStyle.entries.map { it to it.label }, s.kansoStyle,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(kansoStyle = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.WEIGHT -> ChoiceDialog(
            tr("Schriftstärke", "Font weight"), HomeWeight.entries.map { it to it.label }, s.fontWeight,
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { v -> vm.update { it.copy(fontWeight = v) }; dialog = SettingsDialog.NONE }
        SettingsDialog.NEW_PAGE -> TextInputDialog(
            title = tr("Neue Seite", "New page"),
            initial = "",
            hint = tr("z. B. Arbeit, Privat, Reisen", "e.g. Work, Personal, Travel"),
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { name ->
                vm.addPage(name)
                dialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.NEW_FOLDER -> TextInputDialog(
            title = tr("Neuer Ordner", "New folder"),
            initial = "",
            hint = tr("z. B. Social, Arbeit, Tools", "e.g. Social, Work, Tools"),
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = { name ->
                vm.createFolder(name.ifBlank { tr("Ordner", "Folder") }, emptyList())
                dialog = SettingsDialog.NONE
            },
        )
    }

    categoryLimitFor?.let { category ->
        ChoiceDialog(
            title = tr("Limit ${AppCategories.label(category)}", "Limit ${AppCategories.label(category)}"),
            options = listOf(0 to tr("Kein Limit", "No limit")) + listOf(15, 30, 45, 60, 90, 120, 180).map { it to tr("$it Minuten", "$it minutes") },
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
            title = tr("Icons in Graustufen", "Grayscale icons"),
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
            title = tr("Zeitplan: Fokus-Modus", "Schedule: focus mode"),
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
            title = tr("Ablenkende App wählen", "Choose distracting app"),
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
            title = tr("Ort fürs Wetter", "Place for weather"),
            initial = s.weatherCity,
            hint = tr("z. B. Zürich – leer = Standort", "e.g. Zurich – empty = location"),
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
                title = tr("Kontext: ${page.name}", "Context: ${page.name}"),
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
                title = tr("Zeitplan: ${page.name}", "Schedule: ${page.name}"),
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
                title = tr("Seite umbenennen", "Rename page"),
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
                title = { Text(tr("Seite „${page.name}“ löschen?", "Delete page “${page.name}”?")) },
                text = { Text(tr("Ihre Favoriten werden auf die Seite „$target“ verschoben.", "Its favorites will be moved to page “$target”.")) },
                confirmButton = {
                    TextButton(onClick = {
                        vm.removePage(id)
                        deletePageId = null
                    }) { Text(tr("Löschen", "Delete")) }
                },
                dismissButton = { TextButton(onClick = { deletePageId = null }) { Text(tr("Abbrechen", "Cancel")) } },
            )
        }
    }
    movingFavoriteId?.let { favId ->
        val fav = s.favorites.firstOrNull { it.id == favId }
        if (fav != null) {
            ChoiceDialog(
                title = tr("Auf Seite verschieben", "Move to page"),
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
