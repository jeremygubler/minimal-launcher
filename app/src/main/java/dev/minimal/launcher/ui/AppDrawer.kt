package dev.minimal.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.LauncherSettings

private sealed interface DrawerItem {
    val key: String
    data class Header(val letter: String) : DrawerItem { override val key = "h_$letter" }
    data class App(val app: AppInfo) : DrawerItem { override val key = app.key }
}

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    settings: LauncherSettings,
    notifications: Set<String>,
    targetLetter: String?,
    scrollerDragging: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    val rows = remember(apps) {
        buildList {
            var last: String? = null
            apps.forEach { app ->
                if (app.letter != last) {
                    last = app.letter
                    add(DrawerItem.Header(app.letter))
                }
                add(DrawerItem.App(app))
            }
        }
    }
    val headerIndex = remember(rows) {
        rows.withIndex().filter { it.value is DrawerItem.Header }
            .associate { (it.value as DrawerItem.Header).letter to it.index }
    }
    val state = rememberLazyListState()

    LaunchedEffect(targetLetter, headerIndex) {
        val idx = targetLetter?.let { headerIndex[it] } ?: return@LaunchedEffect
        if (scrollerDragging) state.scrollToItem(idx) else state.animateScrollToItem(idx)
    }

    val fontSize = (20 * settings.textScale).sp
    val side = if (settings.alphabetLeft) PaddingValues(start = 72.dp, end = 28.dp) else PaddingValues(start = 28.dp, end = 72.dp)

    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
    ) {
        items(rows.size, key = { rows[it].key }) { i ->
            when (val item = rows[i]) {
                is DrawerItem.Header -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(side)
                        .padding(top = 20.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    val active = item.letter == targetLetter
                    Text(
                        item.letter,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) MaterialTheme.colorScheme.primary else LocalHomeColors.current.secondary,
                    )
                }
                is DrawerItem.App -> AppRow(
                    app = item.app,
                    showIcon = settings.showIcons,
                    iconSize = settings.iconSize.dp,
                    fontSize = fontSize,
                    hasNotification = settings.notificationDots && item.app.notificationKey in notifications,
                    onClick = { onLaunch(item.app) },
                    onLongClick = { onLongPress(item.app) },
                    modifier = Modifier.padding(side),
                )
            }
        }
    }
}
