package dev.minimal.launcher.ui

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
import dev.minimal.launcher.data.GestureAction
import dev.minimal.launcher.data.IconPack
import dev.minimal.launcher.data.NotificationStore
import dev.minimal.launcher.data.ThemeMode
import dev.minimal.launcher.launcherApp
import dev.minimal.launcher.service.LauncherAccessibilityService
import dev.minimal.launcher.util.SystemActions

private enum class SettingsDialog { NONE, THEME, ACCENT, ICON_PACK, DOUBLE_TAP, SWIPE_DOWN, SWIPE_UP, NEW_FOLDER }

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
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    val iconPacks = remember(resumeTick) { IconPack.installed(context) }
    val isDefault = remember(resumeTick) { SystemActions.isDefaultLauncher(context) }
    val hasNotificationAccess = remember(resumeTick) { NotificationStore.hasAccess(context) }
    val accessibilityOn = remember(resumeTick) { LauncherAccessibilityService.isRunning }

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

            item { Section("Favoriten") }
            s.favorites.forEachIndexed { index, fav ->
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
                            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { vm.moveFavorite(fav.id, -1) }, enabled = index > 0) { Text("↑") }
                        TextButton(onClick = { vm.moveFavorite(fav.id, 1) }, enabled = index < s.favorites.lastIndex) { Text("↓") }
                        TextButton(onClick = { vm.removeFavorite(fav.id) }) { Text("✕") }
                    }
                }
            }
            item { ClickRow("Ordner erstellen", "Mehrere Apps unter einem Favoriten") { dialog = SettingsDialog.NEW_FOLDER } }

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
        SettingsDialog.NEW_FOLDER -> TextInputDialog(
            title = "Neuer Ordner",
            initial = "",
            hint = "z. B. Social, Arbeit, Tools",
            onDismiss = { dialog = SettingsDialog.NONE },
        ) { name ->
            vm.createFolder(name.ifBlank { "Ordner" }, emptyList())
            dialog = SettingsDialog.NONE
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
