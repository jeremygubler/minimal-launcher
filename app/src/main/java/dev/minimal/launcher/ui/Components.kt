package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.launcherApp
import dev.minimal.launcher.util.AppSearch

@Composable
fun AppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier) {
    val loader = LocalContext.current.launcherApp.icons
    val version by loader.version.collectAsState()
    val dark = LocalHomeColors.current.dark
    val bitmap by produceState<ImageBitmap?>(null, app.key, version, dark) { value = loader.load(app, dark) }
    Box(modifier.size(size)) {
        val gray = LocalGrayscale.current
        bitmap?.let {
            Image(
                it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter = if (gray) GrayscaleFilter else null,
            )
        }
    }
}

private val GrayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun homeTextStyle(size: TextUnit): TextStyle {
    val colors = LocalHomeColors.current
    val typeface = LocalHomeTypeface.current
    return TextStyle(
        color = colors.text,
        fontSize = size,
        fontFamily = typeface.family,
        fontWeight = typeface.weight,
        shadow = Shadow(colors.shadow, blurRadius = 8f),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    showIcon: Boolean,
    iconSize: Dp,
    fontSize: TextUnit,
    hasNotification: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    isNew: Boolean = false,
) {
    val dimmed = app.key in LocalBlockedApps.current
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.4f else 1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            AppIcon(app, iconSize)
            Spacer(Modifier.width(16.dp))
        }
        val style = homeTextStyle(fontSize)
        Text(
            app.label,
            style = if (textColor != null) style.copy(color = textColor) else style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isNew) {
            Spacer(Modifier.width(10.dp))
            Text(
                tr("Neu", "New"),
                style = homeTextStyle(12.sp).copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
            )
        }
        if (hasNotification) {
            Spacer(Modifier.width(10.dp))
            NotificationDot()
        }
    }
}

@Composable
fun NotificationDot() {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}

/** Dialog zum Auswählen einer App mit Suchfeld. */
@Composable
fun AppPickerDialog(
    title: String,
    apps: List<AppInfo>,
    onDismiss: () -> Unit,
    onPick: (AppInfo?) -> Unit,
    noneLabel: String? = null,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, apps) { if (query.isBlank()) apps else AppSearch.search(apps, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(tr("Suchen…", "Search…")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (noneLabel != null) {
                        item {
                            Text(
                                noneLabel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(null) }
                                    .padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    items(shown, key = { it.key }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } },
    )
}

/** Mehrere Apps auf einmal wählen (z. B. für eine leere Favoriten-Seite). */
@Composable
fun MultiAppPickerDialog(
    title: String,
    apps: List<AppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (List<AppInfo>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptyList<String>()) }
    val shown = remember(query, apps) { if (query.isBlank()) apps else AppSearch.search(apps, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(tr("Suchen…", "Search…")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.key }) { app ->
                        val checked = app.key in selected
                        val toggle = { selected = if (checked) selected - app.key else selected + app.key }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = toggle)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { toggle() })
                            AppIcon(app, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    // Reihenfolge der Auswahl bleibt erhalten.
                    val byKey = apps.associateBy { it.key }
                    onConfirm(selected.mapNotNull { byKey[it] })
                },
            ) { Text(tr("Hinzufügen", "Add") + if (selected.isNotEmpty()) " (${selected.size})" else "") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    hint: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = if (hint != null) { { Text(hint) } } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen", "Cancel")) } },
    )
}

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { (value, label) ->
                    Text(
                        (if (value == selected) "● " else "○ ") + label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen", "Close")) } },
    )
}

/** Kurze Denkpause, bevor eine ablenkende App im Fokus-Modus geöffnet wird. */
@Composable
fun FocusPauseDialog(
    app: AppInfo,
    seconds: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    message: String = tr("Der Fokus-Modus ist aktiv. Brauchst du ${app.label} gerade wirklich?", "Focus mode is on. Do you really need ${app.label} right now?"),
) {
    var remaining by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Kurz durchatmen", "Take a breath")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                }
                Text(message)
            }
        },
        confirmButton = {
            TextButton(enabled = remaining == 0, onClick = {
                onOpen()
                onDismiss()
            }) { Text(if (remaining > 0) tr("Trotzdem öffnen ($remaining)", "Open anyway ($remaining)") else tr("Trotzdem öffnen", "Open anyway")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Lieber nicht", "Not now")) } },
    )
}
