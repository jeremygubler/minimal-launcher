package dev.minimal.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.Favorite
import dev.minimal.launcher.data.GestureAction
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.NotificationPreview
import dev.minimal.launcher.data.NotificationStore
import dev.minimal.launcher.util.SystemActions
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import dev.minimal.launcher.data.NowPlaying
import dev.minimal.launcher.data.ScreenTime
import dev.minimal.launcher.data.WeatherInfo
import dev.minimal.launcher.util.CalendarEvent
import dev.minimal.launcher.util.CalendarEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun HomeContent(
    settings: LauncherSettings,
    appsByKey: Map<String, AppInfo>,
    notifications: Map<String, List<NotificationPreview>>,
    widgetHost: AppWidgetHost,
    editWidgets: Boolean,
    onEditWidgetsDone: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onFolderLongPress: (Favorite) -> Unit,
    onHomeLongPress: () -> Unit,
    onReorderFavorites: (List<String>) -> Unit,
    onPageChange: (String) -> Unit,
    screenTimeTotal: Long?,
    onScreenTimeClick: () -> Unit,
    weather: WeatherInfo?,
    onNoteClick: () -> Unit,
    onStartShortcut: (AppInfo, String) -> Unit,
    perform: (GestureAction) -> Unit,
) {
    val currentPageChange by rememberUpdatedState(onPageChange)
    val currentSettings by rememberUpdatedState(settings)
    val currentPerform by rememberUpdatedState(perform)
    val currentHomeLongPress by rememberUpdatedState(onHomeLongPress)
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val sidePadding = if (settings.alphabetLeft) {
        Modifier.padding(start = 72.dp, end = 28.dp)
    } else {
        Modifier.padding(start = 28.dp, end = 72.dp)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(settings.doubleTap) {
                detectTapGestures(
                    onDoubleTap = { currentPerform(settings.doubleTap) },
                    onLongPress = { currentHomeLongPress() },
                )
            }
            .pointerInput(Unit) {
                // Links/rechts wischen wechselt die Favoriten-Seite.
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val s = currentSettings
                        val idx = s.pages.indexOfFirst { it.id == s.activePage }
                        val target = when {
                            total < -swipeThreshold -> idx + 1
                            total > swipeThreshold -> idx - 1
                            else -> idx
                        }
                        if (target != idx && target in s.pages.indices) currentPageChange(s.pages[target].id)
                    },
                ) { _, dx -> total += dx }
            }
            .pointerInput(settings.swipeDown, settings.swipeUp) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total > swipeThreshold -> currentPerform(settings.swipeDown)
                            total < -swipeThreshold -> currentPerform(settings.swipeUp)
                        }
                    },
                ) { _, dy -> total += dy }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .then(sidePadding)
        ) {
            Spacer(Modifier.height(32.dp))
            ClockBlock(settings, screenTimeTotal, onScreenTimeClick, weather)
            if (settings.note.isNotBlank()) NoteLine(settings.note, onNoteClick)
            if (settings.showMedia) MediaBlock()
            if (settings.widgets.isNotEmpty()) {
                WidgetsArea(
                    ids = settings.widgets,
                    host = widgetHost,
                    edit = editWidgets,
                    onRemove = onRemoveWidget,
                    onMove = onMoveWidget,
                    onDone = onEditWidgetsDone,
                )
            }
            Spacer(Modifier.weight(1f))
            if (settings.pages.size > 1) {
                PageIndicator(settings, onPageChange)
                Spacer(Modifier.height(12.dp))
            }
            val pageIndex = settings.pages.indexOfFirst { it.id == settings.activePage }
            AnimatedContent(
                targetState = settings.activePage,
                transitionSpec = {
                    val fromIdx = settings.pages.indexOfFirst { it.id == initialState }
                    val dir = if (pageIndex >= fromIdx) 1 else -1
                    (slideInHorizontally { w -> dir * w / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> -dir * w / 3 } + fadeOut())
                },
                label = "page",
            ) { page ->
                FavoritesList(
                    settings = settings,
                    favorites = settings.pageFavorites(page),
                    appsByKey = appsByKey,
                    notifications = notifications,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                    onFolderLongPress = onFolderLongPress,
                    onReorder = onReorderFavorites,
                    onStartShortcut = onStartShortcut,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun NoteLine(note: String, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("📝", style = homeTextStyle(16.sp))
        Spacer(Modifier.width(8.dp))
        Text(note, style = homeTextStyle(16.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ClockBlock(
    settings: LauncherSettings,
    screenTimeTotal: Long?,
    onScreenTimeClick: () -> Unit,
    weather: WeatherInfo?,
) {
    val context = LocalContext.current
    val colors = LocalHomeColors.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = System.currentTimeMillis() }

    val timeFormat = remember(now) { DateFormat.getTimeFormat(context) }
    val alarm = remember(now, settings.showAlarm) {
        if (settings.showAlarm) SystemActions.nextAlarm(context)?.takeIf { it - now < DateUtils.DAY_IN_MILLIS } else null
    }
    val noRipple = remember { MutableInteractionSource() }

    Column {
        if (settings.showClock) {
            Text(
                timeFormat.format(Date(now)),
                style = homeTextStyle(60.sp).copy(fontWeight = FontWeight.Light),
                modifier = Modifier.clickable(noRipple, null) { SystemActions.openClock(context) },
            )
        }
        if (settings.showDate) {
            Text(
                DateUtils.formatDateTime(
                    context, now,
                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR,
                ),
                style = homeTextStyle(18.sp).copy(color = colors.secondary),
                modifier = Modifier.clickable(noRipple, null) { SystemActions.openCalendar(context) },
            )
        }
        if (weather != null) {
            Text(
                weather.summary(),
                style = homeTextStyle(15.sp).copy(color = colors.secondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(noRipple, null) {
                        SystemActions.webSearch(context, "Wetter " + (weather.place ?: ""))
                    },
            )
        }
        if (alarm != null) {
            Text(
                "Wecker · " + timeFormat.format(Date(alarm)),
                style = homeTextStyle(15.sp).copy(color = colors.secondary),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(noRipple, null) { SystemActions.openClock(context) },
            )
        }
        if (LocalFocusActive.current) {
            Text(
                "Fokus aktiv",
                style = homeTextStyle(15.sp).copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (screenTimeTotal != null) {
            Text(
                "Bildschirmzeit heute · " + ScreenTime.format(screenTimeTotal),
                style = homeTextStyle(15.sp).copy(color = colors.secondary),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(noRipple, null, onClick = onScreenTimeClick),
            )
        }
        if (settings.showBattery) BatteryLine()
        val event by produceState<CalendarEvent?>(null, now, settings.showEvents) {
            value = if (settings.showEvents) withContext(Dispatchers.IO) { CalendarEvents.next(context, now) } else null
        }
        event?.let { e ->
            val whenText = when {
                e.allDay -> "Heute"
                e.begin <= now -> "Jetzt"
                DateUtils.isToday(e.begin) -> timeFormat.format(Date(e.begin))
                else -> "Morgen " + timeFormat.format(Date(e.begin))
            }
            Text(
                "$whenText · ${e.title}",
                style = homeTextStyle(15.sp).copy(color = colors.secondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(noRipple, null) { CalendarEvents.open(context, e) },
            )
        }
    }
}

/** Akku nur zeigen, wenn es relevant ist: beim Laden oder bei höchstens 20 %. */
@Composable
private fun BatteryLine() {
    val context = LocalContext.current
    val colors = LocalHomeColors.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                level = if (raw >= 0) raw * 100 / scale else -1
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                full = status == BatteryManager.BATTERY_STATUS_FULL
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || full
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { receiver.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    if (level < 0 || (!charging && level > 20)) return
    val text = when {
        full -> "Akku voll"
        charging -> "Lädt · $level %"
        else -> "Akku schwach · $level %"
    }
    Text(
        text,
        style = homeTextStyle(15.sp).copy(color = if (!charging) Color(0xFFF28B82) else colors.secondary),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun MediaBlock() {
    val context = LocalContext.current
    val colors = LocalHomeColors.current
    val media by NowPlaying.state.collectAsState()
    val info = media ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable { NowPlaying.open(context) }
        ) {
            Text(
                info.title,
                style = homeTextStyle(16.sp).copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (info.artist.isNotBlank()) {
                Text(
                    info.artist,
                    style = homeTextStyle(14.sp).copy(color = colors.secondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MediaButton("⏮\uFE0E", "Zurück") { NowPlaying.previous() }
        MediaButton(if (info.playing) "⏸\uFE0E" else "▶\uFE0E", if (info.playing) "Pause" else "Abspielen") {
            NowPlaying.playPause()
        }
        MediaButton("⏭\uFE0E", "Weiter") { NowPlaying.next() }
    }
}

@Composable
private fun MediaButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = homeTextStyle(20.sp))
    }
}

@Composable
private fun WidgetsArea(
    ids: List<Int>,
    host: AppWidgetHost,
    edit: Boolean,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    val density = LocalDensity.current
    val colors = LocalHomeColors.current

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ids.forEach { id ->
            key(id) {
                val info = remember(id) { manager.getAppWidgetInfo(id) }
                if (info != null) {
                    val height = with(density) { info.minHeight.toDp() }.coerceAtLeast(64.dp)
                    Box(Modifier.fillMaxWidth()) {
                        AndroidView(
                            factory = { ctx -> host.createView(ctx, id, info) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(height),
                        )
                        if (edit) {
                            Row(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.scrim.copy(alpha = 0.75f))
                            ) {
                                TextButton(onClick = { onMove(id, -1) }) { Text("↑") }
                                TextButton(onClick = { onMove(id, 1) }) { Text("↓") }
                                TextButton(onClick = { onRemove(id) }) { Text("Entfernen") }
                            }
                        }
                    }
                }
            }
        }
        if (edit) {
            TextButton(onClick = onDone) { Text("Fertig") }
        }
    }
}

@Composable
private fun PageIndicator(settings: LauncherSettings, onPageChange: (String) -> Unit) {
    val colors = LocalHomeColors.current
    val accent = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        settings.pages.forEach { page ->
            val active = page.id == settings.activePage
            Text(
                page.name,
                style = homeTextStyle(15.sp).copy(
                    color = if (active) accent else colors.secondary,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPageChange(page.id) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FavoritesList(
    settings: LauncherSettings,
    favorites: List<Favorite>,
    appsByKey: Map<String, AppInfo>,
    notifications: Map<String, List<NotificationPreview>>,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onFolderLongPress: (Favorite) -> Unit,
    onReorder: (List<String>) -> Unit,
    onStartShortcut: (AppInfo, String) -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    val fontSize = (26 * settings.textScale).sp
    val haptics = LocalHapticFeedback.current
    val touchSlop = LocalViewConfiguration.current.touchSlop

    // Lange drücken und ziehen sortiert Favoriten um; lange drücken ohne Ziehen öffnet das Menü.
    var order by remember { mutableStateOf(favorites.map { it.id }) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(favorites) { if (draggingId == null) order = favorites.map { it.id } }

    val currentFavorites by rememberUpdatedState(favorites)
    val currentAppsByKey by rememberUpdatedState(appsByKey)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentFolderLongPress by rememberUpdatedState(onFolderLongPress)
    val currentReorder by rememberUpdatedState(onReorder)

    fun moveBy(dy: Float) {
        val id = draggingId ?: return
        dragOffset += dy
        dragDistance += abs(dy)
        val idx = order.indexOf(id)
        if (idx < 0) return
        if (dragOffset > 0 && idx < order.lastIndex) {
            val h = heights[order[idx + 1]] ?: return
            if (dragOffset > h / 2f) {
                order = order.toMutableList().apply { add(idx + 1, removeAt(idx)) }
                dragOffset -= h
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } else if (dragOffset < 0 && idx > 0) {
            val h = heights[order[idx - 1]] ?: return
            if (-dragOffset > h / 2f) {
                order = order.toMutableList().apply { add(idx - 1, removeAt(idx)) }
                dragOffset += h
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    fun finishDrag() {
        val id = draggingId ?: return
        draggingId = null
        dragOffset = 0f
        if (dragDistance < touchSlop) {
            val fav = currentFavorites.firstOrNull { it.id == id } ?: return
            if (fav.isFolder) {
                currentFolderLongPress(fav)
            } else {
                fav.apps.firstOrNull()?.let { currentAppsByKey[it] }?.let(currentLongPress)
            }
        } else {
            currentReorder(order)
        }
    }

    fun reorderModifier(id: String) = Modifier.pointerInput(id) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                draggingId = id
                dragOffset = 0f
                dragDistance = 0f
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDragEnd = { finishDrag() },
            onDragCancel = { finishDrag() },
        ) { change, amount ->
            change.consume()
            moveBy(amount.y)
        }
    }

    val byId = favorites.associateBy { it.id }
    val shown = order.mapNotNull { byId[it] } + favorites.filter { it.id !in order }

    Column {
        if (favorites.isEmpty()) {
            Text(
                "Halte eine App gedrückt, um sie zu den Favoriten hinzuzufügen. " +
                    "Ziehe an der Buchstabenleiste, um alle Apps zu sehen.",
                style = homeTextStyle(15.sp).copy(color = LocalHomeColors.current.secondary),
            )
        }
        shown.forEach { fav ->
            key(fav.id) {
                val favApps = fav.apps.mapNotNull { appsByKey[it] }
                val isDragged = draggingId == fav.id
                Box(
                    Modifier
                        .onSizeChanged { heights[fav.id] = it.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            if (isDragged) {
                                translationY = dragOffset
                                scaleX = 1.04f
                                scaleY = 1.04f
                                alpha = 0.9f
                            }
                        }
                ) {
                    if (fav.isFolder) {
                        FolderEntry(
                            folder = fav,
                            apps = favApps,
                            expanded = expanded == fav.id,
                            settings = settings,
                            fontSize = fontSize,
                            notifications = notifications,
                            reorder = reorderModifier(fav.id),
                            onToggle = { expanded = if (expanded == fav.id) null else fav.id },
                            onLaunch = onLaunch,
                            onLongPress = onLongPress,
                        )
                    } else {
                        favApps.firstOrNull()?.let { app ->
                            FavoriteEntry(
                                app = app,
                                swipeApp = fav.swipeApp?.let { appsByKey[it] },
                                leftShortcutLabel = fav.swipeLeftShortcut?.let { fav.swipeLeftLabel ?: "Aktion" },
                                onLeftSwipe = { fav.swipeLeftShortcut?.let { onStartShortcut(app, it) } },
                                notifications = notifications[app.notificationKey].orEmpty(),
                                settings = settings,
                                fontSize = fontSize,
                                reorder = reorderModifier(fav.id),
                                onLaunch = onLaunch,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteEntry(
    app: AppInfo,
    swipeApp: AppInfo?,
    leftShortcutLabel: String?,
    notifications: List<NotificationPreview>,
    settings: LauncherSettings,
    fontSize: TextUnit,
    reorder: Modifier,
    onLaunch: (AppInfo) -> Unit,
    onLeftSwipe: () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shownX by animateFloatAsState(
        dragX,
        animationSpec = if (dragging) snap() else spring(),
        label = "swipe",
    )
    val currentSwipe by rememberUpdatedState(swipeApp)
    val currentLaunch by rememberUpdatedState(onLaunch)
    val currentLeftSwipe by rememberUpdatedState(onLeftSwipe)
    val canRight = swipeApp != null
    val canLeft = leftShortcutLabel != null

    Column {
        Box(reorder) {
            if (swipeApp != null && shownX > 1f) {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .alpha(min(1f, shownX / threshold)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(swipeApp, 24.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(swipeApp.label, style = homeTextStyle(13.sp), maxLines = 1)
                }
            }
            if (leftShortcutLabel != null && shownX < -1f) {
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .alpha(min(1f, -shownX / threshold)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚡ $leftShortcutLabel", style = homeTextStyle(13.sp), maxLines = 1)
                }
            }
            AppRow(
                app = app,
                showIcon = settings.showIcons,
                iconSize = settings.iconSize.dp,
                fontSize = fontSize,
                hasNotification = settings.notificationDots && notifications.isNotEmpty(),
                onClick = { onLaunch(app) },
                onLongClick = null,
                modifier = Modifier
                    .offset { IntOffset(shownX.roundToInt(), 0) }
                    .pointerInput(canRight, canLeft) {
                        if (!canRight && !canLeft) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                if (dragX >= threshold) currentSwipe?.let(currentLaunch)
                                if (dragX <= -threshold) currentLeftSwipe()
                                dragging = false
                                dragX = 0f
                            },
                            onDragCancel = {
                                dragging = false
                                dragX = 0f
                            },
                        ) { change, dx ->
                            change.consume()
                            dragX = (dragX + dx).coerceIn(
                                if (canLeft) -threshold * 1.6f else 0f,
                                if (canRight) threshold * 1.6f else 0f,
                            )
                        }
                    },
            )
        }
        if (settings.notificationPreview && notifications.isNotEmpty()) {
            NotificationPreviewBlock(
                items = notifications,
                startPadding = if (settings.showIcons) settings.iconSize.dp + 16.dp else 0.dp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationPreviewBlock(items: List<NotificationPreview>, startPadding: Dp) {
    val context = LocalContext.current
    val colors = LocalHomeColors.current
    val first = items.first()
    Column(
        Modifier
            .padding(start = startPadding, bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { NotificationStore.open(context, first) },
                onLongClick = { NotificationStore.dismiss(first) },
            )
    ) {
        if (first.title.isNotBlank()) {
            Text(
                first.title,
                style = homeTextStyle(14.sp).copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (first.text.isNotBlank()) {
            Text(
                first.text,
                style = homeTextStyle(13.sp).copy(color = colors.secondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (items.size > 1) {
            Text(
                "+${items.size - 1} weitere",
                style = homeTextStyle(12.sp).copy(color = colors.secondary),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderEntry(
    folder: Favorite,
    apps: List<AppInfo>,
    expanded: Boolean,
    settings: LauncherSettings,
    fontSize: TextUnit,
    notifications: Map<String, List<NotificationPreview>>,
    reorder: Modifier,
    onToggle: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    val colors = LocalHomeColors.current
    val hasNotification = settings.notificationDots && apps.any { it.notificationKey in notifications }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(reorder)
                .combinedClickable(onClick = onToggle)
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (settings.showIcons) {
                FolderIcon(apps, settings.iconSize.dp)
                Spacer(Modifier.width(16.dp))
            }
            Text(
                folder.name ?: "Ordner",
                style = homeTextStyle(fontSize),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(if (expanded) "  ▾" else "  ▸", style = homeTextStyle(16.sp).copy(color = colors.secondary))
            if (hasNotification) {
                Spacer(Modifier.width(10.dp))
                NotificationDot()
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = if (settings.showIcons) settings.iconSize.dp / 2 else 16.dp)) {
                apps.forEach { app ->
                    AppRow(
                        app = app,
                        showIcon = settings.showIcons,
                        iconSize = settings.iconSize.dp * 0.8f,
                        fontSize = fontSize * 0.8f,
                        hasNotification = settings.notificationDots && app.notificationKey in notifications,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                    )
                }
                if (apps.isEmpty()) {
                    Text("Leer – lange drücken zum Bearbeiten", style = homeTextStyle(14.sp).copy(color = colors.secondary))
                }
            }
        }
    }
}

@Composable
private fun FolderIcon(apps: List<AppInfo>, size: Dp) {
    val colors = LocalHomeColors.current
    val cell = size / 2 - 2.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.text.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            apps.take(4).chunked(2).forEach { row ->
                Row {
                    row.forEach { AppIcon(it, cell * 0.8f, Modifier.padding(1.dp)) }
                }
            }
        }
    }
}
