package dev.minimal.launcher.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.minimal.launcher.data.GestureAction
import dev.minimal.launcher.util.SystemActions
import kotlin.math.max

interface HomeCallbacks {
    fun addWidget(info: AppWidgetProviderInfo)
    fun removeWidget(id: Int)
    fun setBlur(enabled: Boolean)
    fun openSettings()
}

enum class Overlay { NONE, DRAWER, SEARCH }

@Composable
fun LauncherRoot(vm: LauncherViewModel, widgetHost: AppWidgetHost, callbacks: HomeCallbacks) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.visibleApps.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val notificationKeys = remember(notifications) { notifications.keys }
    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    val privateSpace by vm.privateSpace.collectAsStateWithLifecycle()
    val privateApps by vm.privateApps.collectAsStateWithLifecycle()
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

    fun closeAll() {
        overlay = Overlay.NONE
        targetLetter = null
        actionsFor = null
        editFolder = null
        showHomeMenu = false
        editWidgets = false
    }

    LaunchedEffect(Unit) { vm.homePressed.collect { closeAll() } }
    // Nach dem Start einer App zurück zum Startbildschirm, wie bei Niagara.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { closeAll() }
    BackHandler { closeAll() }

    LaunchedEffect(overlay, settings.blur) { callbacks.setBlur(settings.blur && overlay != Overlay.NONE) }

    val dim by animateFloatAsState(
        if (overlay != Overlay.NONE) max(settings.wallpaperDim, 0.55f) else settings.wallpaperDim,
        label = "dim",
    )
    val homeColors = LocalHomeColors.current

    val launch: (AppInfo) -> Unit = { vm.launch(it) }
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
                apps = apps + privateApps,
                settings = settings,
                notifications = notificationKeys,
                onLaunch = launch,
                onLongPress = longPress,
                onContactsDenied = { vm.update { it.copy(searchContacts = false) } },
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

    actionsFor?.let { app ->
        AppActionsSheet(
            app = app,
            vm = vm,
            settings = settings,
            pickerApps = apps,
            appsByKey = appsByKey,
            onDismiss = { actionsFor = null },
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
