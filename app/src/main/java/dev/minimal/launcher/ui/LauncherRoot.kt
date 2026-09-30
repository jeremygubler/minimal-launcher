package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.minimal.launcher.LauncherViewModel
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.Favorite
import dev.minimal.launcher.data.Focus
import dev.minimal.launcher.data.FocusSessions
import dev.minimal.launcher.data.TaskItem
import dev.minimal.launcher.data.Tasks
import java.time.LocalDate
import dev.minimal.launcher.data.ScreenTime
import dev.minimal.launcher.data.AppCategories
import dev.minimal.launcher.data.WeatherInfo
import dev.minimal.launcher.data.GestureAction
import dev.minimal.launcher.util.SystemActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.max

interface HomeCallbacks {
    /** Widget binden; mit [favoriteAppKey] als Pop-up-Widget dieses Favoriten statt auf dem Startbildschirm. */
    fun addWidget(info: AppWidgetProviderInfo, favoriteAppKey: String? = null)
    fun removeWidget(id: Int)
    fun setBlur(enabled: Boolean)
    fun openSettings()
    /** Fingerabdruck/PIN abfragen und danach [onSuccess] ausführen. */
    fun authenticate(title: String, onSuccess: () -> Unit)
}

enum class Overlay { NONE, DRAWER, SEARCH }

@Composable
fun LauncherRoot(vm: LauncherViewModel, widgetHost: AppWidgetHost, callbacks: HomeCallbacks) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.visibleApps.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    val rawNotifications by vm.notifications.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    // Uhrzeit für Zeitpläne (jede Minute und beim Zurückkehren aktualisiert).
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = LocalDateTime.now()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = LocalDateTime.now() }
    val screenTime by produceState(emptyMap<String, Long>(), now, settings.showScreenTime) {
        value = if (settings.showScreenTime) vm.screenTimeToday() else emptyMap()
    }
    val weather by produceState<WeatherInfo?>(null, now, settings.showWeather, settings.weatherCity) {
        value = if (settings.showWeather) vm.weather() else null
    }
    var showScreenTimeDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var addingTask by remember { mutableStateOf(false) }
    var taskPaywall by remember { mutableStateOf(false) }
    val pro = isPro()
    val focusActive = Focus.isActive(settings, now)
    val blockedKeys = if (focusActive) settings.focusApps else emptySet()

    // Im Fokus-Modus keine Benachrichtigungen ablenkender Apps anzeigen.
    val notifications = remember(rawNotifications, blockedKeys, allApps) {
        if (blockedKeys.isEmpty()) {
            rawNotifications
        } else {
            val blockedNotificationKeys = allApps.filter { it.key in blockedKeys }.map { it.notificationKey }.toSet()
            rawNotifications.filterKeys { it !in blockedNotificationKeys }
        }
    }
    val notificationKeys = remember(notifications) { notifications.keys }
    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    val privateSpace by vm.privateSpace.collectAsStateWithLifecycle()
    val privateApps by vm.privateApps.collectAsStateWithLifecycle()
    val searchApps = remember(apps, privateApps) { apps + privateApps }
    val letters = remember(apps, privateSpace != null) {
        apps.map { it.letter }.distinct() + listOfNotNull(PRIVATE_LETTER.takeIf { privateSpace != null })
    }

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var targetLetter by remember { mutableStateOf<String?>(null) }
    var scrollerDragging by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<AppInfo?>(null) }
    var editFolder by remember { mutableStateOf<Favorite?>(null) }
    var showHomeMenu by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var editWidgets by remember { mutableStateOf(false) }
    var homeGesture by remember { mutableStateOf<GestureAction?>(null) }

    fun closeAll() {
        overlay = Overlay.NONE
        targetLetter = null
        actionsFor = null
        editFolder = null
        showHomeMenu = false
        editWidgets = false
    }

    val currentSettings by rememberUpdatedState(settings)
    LaunchedEffect(Unit) {
        vm.showReport.collect { show ->
            if (show) {
                closeAll()
                showScreenTimeDialog = true
                vm.reportShown()
            }
        }
    }
    LaunchedEffect(Unit) {
        vm.homePressed.collect { alreadyOnHome ->
            val nothingOpen = overlay == Overlay.NONE && actionsFor == null && editFolder == null &&
                !showHomeMenu && !showWidgetPicker && !editWidgets
            if (alreadyOnHome && nothingOpen && currentSettings.homePress != GestureAction.NONE) {
                homeGesture = currentSettings.homePress
            } else {
                closeAll()
            }
        }
    }
    // Nach dem Start einer App zurück zum Startbildschirm, wie bei Niagara.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { closeAll() }
    BackHandler { closeAll() }

    LaunchedEffect(overlay, settings.blur) { callbacks.setBlur(settings.blur && overlay != Overlay.NONE) }

    val dim by animateFloatAsState(
        if (overlay != Overlay.NONE) max(settings.wallpaperDim, 0.55f) else settings.wallpaperDim,
        label = "dim",
    )
    val homeColors = LocalHomeColors.current

    var focusPauseFor by remember { mutableStateOf<AppInfo?>(null) }
    var intentionFor by remember { mutableStateOf<AppInfo?>(null) }
    var addingFavorites by remember { mutableStateOf(false) }
    var startingSession by remember { mutableStateOf(false) }
    var sessionPaywall by remember { mutableStateOf(false) }
    var sessionBlockedFor by remember { mutableStateOf<AppInfo?>(null) }
    // Restzeit der Fokus-Sitzung (aktualisiert sich mit der Minuten-Uhr und beim Beenden).
    val sessionMinutes = remember(now, settings.focusSessionEnd) {
        FocusSessions.remainingMinutes(settings, System.currentTimeMillis())
    }
    // Absichtsfrage (Pro): im Fokus-Modus oder – falls gewünscht – immer bei ablenkenden Apps.
    val intentionKeys = if (settings.intentionPrompt && pro && (focusActive || settings.intentionAlways)) {
        settings.focusApps
    } else {
        emptySet()
    }
    // App-Sperre: gesperrte Apps (und ihre Shortcuts) nur nach Fingerabdruck/PIN öffnen.
    val lockedPackages = remember(allApps, settings.lockedApps) {
        allApps.filter { it.key in settings.lockedApps }.map { it.packageName }.toSet()
    }
    val openApp: (AppInfo) -> Unit = { app ->
        if (app.key in settings.lockedApps) callbacks.authenticate(app.label) { vm.launch(app) } else vm.launch(app)
    }
    // Tageslimit prüfen (braucht die heutige Bildschirmzeit, daher kurz im Hintergrund).
    val scope = rememberCoroutineScope()
    var limitReached by remember { mutableStateOf<Pair<AppInfo, String>?>(null) }
    val openChecked: (AppInfo) -> Unit = { app ->
        val limit = settings.appLimits[app.key]
        val categoryLimit = settings.categoryLimits[app.category.toString()]
        if (limit == null && categoryLimit == null) {
            openApp(app)
        } else {
            scope.launch {
                val usage = vm.screenTimeToday()
                val used = usage[app.packageName] ?: 0L
                val categoryPackages = allApps.filter { it.category == app.category }.map { it.packageName }.toSet()
                val usedCategory = usage.filterKeys { it in categoryPackages }.values.sum()
                limitReached = when {
                    limit != null && used >= limit * 60_000L -> app to
                        tr("Tageslimit erreicht: heute schon ${ScreenTime.format(used)} von $limit min. Trotzdem öffnen?", "Daily limit reached: ${ScreenTime.format(used)} of $limit min used today. Open anyway?")
                    categoryLimit != null && usedCategory >= categoryLimit * 60_000L -> app to
                        tr("Limit für „${AppCategories.label(app.category)}“ erreicht: heute zusammen ", "Limit for “${AppCategories.label(app.category)}” reached: together ") +
                        tr("${ScreenTime.format(usedCategory)} von $categoryLimit min. Trotzdem öffnen?", "${ScreenTime.format(usedCategory)} of $categoryLimit min today. Open anyway?")
                    else -> null
                }
                if (limitReached == null) openApp(app)
            }
        }
    }
    val launch: (AppInfo) -> Unit = { app ->
        when (app.key) {
            // Während einer Fokus-Sitzung sind ablenkende Apps wirklich gesperrt.
            in (if (sessionMinutes > 0) settings.focusApps else emptySet()) -> sessionBlockedFor = app
            in intentionKeys -> intentionFor = app
            in blockedKeys -> focusPauseFor = app
            else -> openChecked(app)
        }
    }
    val longPress: (AppInfo) -> Unit = { actionsFor = it }
    val perform: (GestureAction) -> Unit = { action ->
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(context)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(context)
            GestureAction.SEARCH -> overlay = Overlay.SEARCH
            GestureAction.DRAWER -> overlay = Overlay.DRAWER
            GestureAction.LOCK -> SystemActions.lockScreen(context)
            GestureAction.ASSISTANT -> SystemActions.openAssistant(context)
        }
    }

    val grayscale = settings.grayscaleSchedule?.matches(now) == true
    CompositionLocalProvider(
        LocalBlockedApps provides blockedKeys,
        LocalFocusActive provides focusActive,
        LocalGrayscale provides grayscale,
    ) {
    homeGesture?.let { gesture ->
        LaunchedEffect(gesture) {
            perform(gesture)
            homeGesture = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(homeColors.scrim.copy(alpha = dim))
    ) {
        AnimatedVisibility(overlay == Overlay.NONE, enter = fadeIn(), exit = fadeOut()) {
            HomeContent(
                settings = settings,
                appsByKey = appsByKey,
                notifications = notifications,
                widgetHost = widgetHost,
                editWidgets = editWidgets,
                onEditWidgetsDone = { editWidgets = false },
                onRemoveWidget = callbacks::removeWidget,
                onMoveWidget = vm::moveWidget,
                onLaunch = launch,
                onLongPress = longPress,
                onFolderLongPress = { editFolder = it },
                onHomeLongPress = { showHomeMenu = true },
                onReorderFavorites = vm::setFavoriteOrder,
                onPageChange = vm::setCurrentPage,
                screenTimeTotal = if (settings.showScreenTime && screenTime.isNotEmpty()) screenTime.values.sum() else null,
                onScreenTimeClick = { showScreenTimeDialog = true },
                weather = weather,
                onNoteClick = { editingNote = true },
                onToggleTask = vm::toggleTask,
                onTaskLongPress = { editingTask = it },
                onStartShortcut = { app, id ->
                    if (app.key in settings.lockedApps) {
                        callbacks.authenticate(app.label) { vm.startShortcutById(app, id) }
                    } else {
                        vm.startShortcutById(app, id)
                    }
                },
                onRemoveFavorite = vm::removeFavorite,
                onAddFavorites = { addingFavorites = true },
                perform = perform,
            )
        }
        AnimatedVisibility(
            overlay == Overlay.DRAWER,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut(),
        ) {
            AppDrawer(
                apps = apps,
                settings = settings,
                notifications = notificationKeys,
                targetLetter = targetLetter,
                scrollerDragging = scrollerDragging,
                privateSpace = privateSpace,
                privateApps = privateApps,
                onTogglePrivateSpace = { privateSpace?.let { vm.setPrivateSpaceLocked(!it.locked) } },
                onPrivateSpaceSettings = vm::openPrivateSpaceSettings,
                onLaunch = launch,
                onLongPress = longPress,
            )
        }
        AnimatedVisibility(overlay == Overlay.SEARCH, enter = fadeIn(), exit = fadeOut()) {
            SearchScreen(
                apps = searchApps,
                settings = settings,
                notifications = notificationKeys,
                onLaunch = launch,
                onLongPress = longPress,
                onContactsDenied = { vm.update { it.copy(searchContacts = false) } },
                onSetNote = { vm.setNote(it) },
                onAddTask = { title, due -> if (pro) vm.addTask(title, due) else taskPaywall = true },
                onPinContact = { uri, name -> vm.addContactFavorite(uri, name) },
                usage = usage,
                loadShortcuts = vm::allShortcuts,
                shortcutIcon = vm::shortcutIcon,
                onShortcut = { sc ->
                    if (sc.`package` in lockedPackages) {
                        callbacks.authenticate(sc.shortLabel?.toString() ?: "App") { vm.startShortcut(sc) }
                    } else {
                        vm.startShortcut(sc)
                    }
                },
            )
        }
        if (overlay != Overlay.SEARCH) {
            AlphabetScroller(
                letters = letters,
                onLeft = settings.alphabetLeft,
                onLetter = {
                    targetLetter = it
                    overlay = Overlay.DRAWER
                },
                onDragStateChanged = { scrollerDragging = it },
                modifier = Modifier
                    .align(if (settings.alphabetLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .systemBarsPadding()
                    .padding(vertical = 56.dp),
            )
        }
    }

    }

    focusPauseFor?.let { app ->
        FocusPauseDialog(
            app = app,
            seconds = settings.focusPauseSeconds,
            onOpen = { openChecked(app) },
            onDismiss = { focusPauseFor = null },
        )
    }

    if (startingSession) {
        FocusSessionDialog(
            focusAppCount = settings.focusApps.size,
            onStart = { minutes ->
                vm.startFocusSession(minutes)
                startingSession = false
            },
            onDismiss = { startingSession = false },
        )
    }
    if (sessionPaywall) PaywallDialog(feature = tr("Fokus-Sitzung", "Focus session"), onDismiss = { sessionPaywall = false })
    sessionBlockedFor?.let { app ->
        FocusSessionBlockedDialog(
            app = app,
            remainingMinutes = sessionMinutes,
            onStop = { vm.stopFocusSession() },
            onDismiss = { sessionBlockedFor = null },
        )
    }

    if (addingFavorites) {
        MultiAppPickerDialog(
            title = tr("Apps für „${settings.pages.firstOrNull { it.id == settings.activePage }?.name.orEmpty()}“", "Apps for “${settings.pages.firstOrNull { it.id == settings.activePage }?.name.orEmpty()}”"),
            apps = apps,
            onDismiss = { addingFavorites = false },
            onConfirm = {
                vm.addFavorites(it)
                addingFavorites = false
            },
        )
    }

    intentionFor?.let { app ->
        IntentionDialog(
            app = app,
            vm = vm,
            seconds = settings.focusPauseSeconds,
            onOpen = { openChecked(app) },
            onDismiss = { intentionFor = null },
        )
    }

    limitReached?.let { (app, message) ->
        FocusPauseDialog(
            app = app,
            seconds = settings.focusPauseSeconds,
            onOpen = { openApp(app) },
            onDismiss = { limitReached = null },
            message = message,
        )
    }

    if (!settings.onboardingDone) {
        OnboardingDialog(onDone = { vm.update { it.copy(onboardingDone = true) } })
    }

    if (showScreenTimeDialog) {
        ScreenTimeReport(
            vm = vm,
            settings = settings,
            appsByPackage = remember(allApps) { allApps.filter { !it.isWork }.associateBy { it.packageName } },
            onDismiss = { showScreenTimeDialog = false },
        )
    }

    if (taskPaywall) PaywallDialog(feature = tr("Aufgabenliste", "Task list"), onDismiss = { taskPaywall = false })
    if (addingTask) {
        TextInputDialog(
            title = tr("Neue Aufgabe", "New task"),
            initial = "",
            hint = tr("z. B. morgen Zahnarzt anrufen", "e.g. call dentist tomorrow"),
            onDismiss = { addingTask = false },
            onConfirm = { text ->
                Tasks.parseText(text, LocalDate.now())?.let { (title, due) -> vm.addTask(title, due) }
                addingTask = false
            },
        )
    }

    editingTask?.let { task ->
        TaskEditDialog(
            task = task,
            onDismiss = { editingTask = null },
            onSave = { title, due ->
                vm.updateTask(task.id, title, due)
                editingTask = null
            },
            onDelete = {
                vm.deleteTask(task.id)
                editingTask = null
            },
        )
    }

    if (editingNote) {
        TextInputDialog(
            title = tr("Notiz", "Note"),
            initial = settings.note,
            hint = tr("z. B. Milch kaufen", "e.g. buy milk"),
            onDismiss = { editingNote = false },
            onConfirm = {
                vm.setNote(it)
                editingNote = false
            },
        )
    }

    actionsFor?.let { app ->
        AppActionsSheet(
            app = app,
            vm = vm,
            settings = settings,
            pickerApps = apps,
            appsByKey = appsByKey,
            onDismiss = { actionsFor = null },
            onPickPopupWidget = { info -> callbacks.addWidget(info, app.key) },
        )
    }

    editFolder?.let { folder ->
        settings.favorites.firstOrNull { it.id == folder.id }?.let { current ->
            FolderEditDialog(
                folder = current,
                appsByKey = appsByKey,
                pickerApps = apps,
                vm = vm,
                onDismiss = { editFolder = null },
            )
        }
    }

    if (showHomeMenu) {
        HomeMenuSheet(
            hasWidgets = settings.widgets.isNotEmpty(),
            hasNote = settings.note.isNotBlank(),
            onAddTask = {
                showHomeMenu = false
                if (pro) addingTask = true else taskPaywall = true
            },
            onEditNote = {
                showHomeMenu = false
                editingNote = true
            },
            focusOn = settings.focusManual,
            sessionMinutes = sessionMinutes.takeIf { it > 0 },
            onFocusSession = {
                showHomeMenu = false
                when {
                    sessionMinutes > 0 -> vm.stopFocusSession()
                    pro -> startingSession = true
                    else -> sessionPaywall = true
                }
            },
            onToggleFocus = {
                showHomeMenu = false
                vm.setFocusManual(!settings.focusManual)
            },
            onDismiss = { showHomeMenu = false },
            onAddWidget = { showHomeMenu = false; showWidgetPicker = true },
            onEditWidgets = { showHomeMenu = false; editWidgets = true },
            onSettings = { showHomeMenu = false; callbacks.openSettings() },
        )
    }

    if (showWidgetPicker) {
        WidgetPickerDialog(
            onDismiss = { showWidgetPicker = false },
            onPick = {
                showWidgetPicker = false
                callbacks.addWidget(it)
            },
        )
    }
}
