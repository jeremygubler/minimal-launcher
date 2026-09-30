package dev.minimal.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.minimal.launcher.data.AppInfo

/** Pop-up-Widget eines Favoriten (nach rechts wischen). */
@Composable
fun WidgetPopup(
    host: AppWidgetHost,
    widgetId: Int,
    app: AppInfo,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val info = remember(widgetId) { AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) }
    val density = LocalDensity.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Tippen neben das Pop-up schliesst es.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clickable(interactionSource = null, indication = null) { /* Klicks nicht durchreichen */ },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app, 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(app.label, style = MaterialTheme.typography.titleSmall)
                        }
                        TextButton(onClick = onOpenApp) { Text("Öffnen") }
                    }
                    if (info == null) {
                        Text(
                            "Das Widget ist nicht mehr verfügbar. Lege es im App-Menü neu fest.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        // Android 12+ nennt die gewünschte Grösse in Zellen – grosszügiger als minHeight.
                        val cells = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellHeight else 0
                        val height = maxOf(with(density) { info.minHeight.toDp() }, (cells * 90).dp)
                            .coerceIn(120.dp, maxHeight)
                        AndroidView(
                            factory = { ctx -> host.createView(ctx, widgetId, info) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(height)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        )
                    }
                }
            }
        }
    }
}
