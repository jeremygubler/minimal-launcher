package dev.minimal.launcher.ui

import android.Manifest
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import dev.minimal.launcher.data.FavoritePage
import dev.minimal.launcher.data.PageSchedule
import dev.minimal.launcher.data.ScreenTime
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

// Wiederverwendbare Bausteine der Einstellungen.

@Composable
internal fun GestureDialog(title: String, current: GestureAction, onDismiss: () -> Unit, onPick: (GestureAction) -> Unit) {
    ChoiceDialog(title, GestureAction.entries.map { it to it.label }, current, onDismiss) {
        onPick(it)
        onDismiss()
    }
}

@Composable
internal fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
internal fun ClickRow(title: String, subtitle: String?, onClick: () -> Unit) {
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
internal fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun SliderRow(
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
internal fun StatusRow(title: String, ok: Boolean, action: String, onClick: () -> Unit) {
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
internal fun ScheduleDialog(
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
