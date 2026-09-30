package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import dev.minimal.launcher.util.ContactResult
import dev.minimal.launcher.util.ContactSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.content.ClipData
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.util.AppSearch
import dev.minimal.launcher.util.Calculator
import dev.minimal.launcher.util.QuickAction
import dev.minimal.launcher.util.QuickActions
import dev.minimal.launcher.data.Tasks
import dev.minimal.launcher.util.SystemActions

@Composable
fun SearchScreen(
    apps: List<AppInfo>,
    settings: LauncherSettings,
    notifications: Set<String>,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onContactsDenied: () -> Unit,
    onSetNote: (String) -> Unit,
    onAddTask: (String, java.time.LocalDate?) -> Unit,
    onPinContact: (String, String) -> Unit,
    usage: Map<String, Double>,
    loadShortcuts: suspend () -> List<ShortcutInfo>,
    shortcutIcon: (ShortcutInfo) -> Drawable?,
    onShortcut: (ShortcutInfo) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = LocalHomeColors.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, apps, usage) { AppSearch.search(apps, query, usage) }
    val suggestions = remember(apps, usage, settings.showSuggestions) {
        if (!settings.showSuggestions) emptyList()
        else apps.filter { (usage[it.key] ?: 0.0) > 0.05 }.sortedByDescending { usage[it.key] }.take(5)
    }
    val shortcuts by produceState(emptyList<ShortcutInfo>(), settings.searchShortcuts) {
        value = if (settings.searchShortcuts) loadShortcuts() else emptyList()
    }
    val labelsByPackage = remember(apps) { apps.associate { it.packageName to it.label } }
    val shortcutResults = remember(query, shortcuts) {
        if (query.trim().length < 2) emptyList()
        else shortcuts.filter { sc ->
            AppSearch.matches(sc.shortLabel?.toString().orEmpty(), query) ||
                AppSearch.matches(sc.longLabel?.toString().orEmpty(), query)
        }.take(5)
    }
    val calc = remember(query) { Calculator.evaluate(query) }
    val quickActions = remember(query) { QuickActions.parse(query) }

    var contactsAllowed by remember { mutableStateOf(ContactSearch.hasPermission(context)) }
    val requestContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsAllowed = granted
        if (!granted) onContactsDenied()
    }
    val contacts by produceState(emptyList<ContactResult>(), query, contactsAllowed, settings.searchContacts) {
        value = if (!settings.searchContacts || !contactsAllowed || query.trim().length < 2) {
            emptyList()
        } else {
            delay(150) // Entprellen beim Tippen
            withContext(Dispatchers.IO) { ContactSearch.search(context, query) }
        }
    }

    LaunchedEffect(Unit) {
        if (settings.autoKeyboard) {
            focus.requestFocus()
            keyboard?.show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.fillMaxWidth()) {
            if (query.isEmpty()) {
                Text(tr("Suchen…", "Search…"), style = homeTextStyle(30.sp).copy(color = colors.secondary))
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = homeTextStyle(30.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = {
                    val first = results.firstOrNull()
                    val firstAction = quickActions.firstOrNull { it !is QuickAction.Conversion }
                    when {
                        first != null -> onLaunch(first)
                        firstAction != null -> QuickActions.perform(context, firstAction)
                        query.isNotBlank() -> SystemActions.webSearch(context, query, settings.searchEngine)
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            if (calc != null) {
                item(key = "calc") {
                    Text(
                        "= $calc",
                        style = homeTextStyle(26.sp).copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText(tr("Ergebnis", "Result"), calc))
                                Toast.makeText(context, tr("Ergebnis kopiert", "Result copied"), Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
            val noteText = Regex("^notiz\\s+(.+)$", RegexOption.IGNORE_CASE).find(query.trim())?.groupValues?.get(1)
            if (noteText != null) {
                item(key = "note") {
                    ActionLine(tr("📝  Als Notiz auf den Startbildschirm: „$noteText“", "📝  Pin as note to home screen: “$noteText”")) {
                        onSetNote(noteText)
                        Toast.makeText(context, tr("Notiz gespeichert", "Note saved"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val taskCommand = Tasks.parseCommand(query, java.time.LocalDate.now())
            if (taskCommand != null) {
                item(key = "task") {
                    val (title, due) = taskCommand
                    val whenText = when (due) {
                        null -> ""
                        java.time.LocalDate.now() -> tr(" (heute)", " (today)")
                        java.time.LocalDate.now().plusDays(1) -> tr(" (morgen)", " (tomorrow)")
                        else -> tr(" (übermorgen)", " (day after tomorrow)")
                    }
                    ActionLine(tr("☐  Aufgabe hinzufügen: „$title“$whenText", "☐  Add task: “$title”$whenText")) {
                        onAddTask(title, due)
                        Toast.makeText(context, tr("Aufgabe gespeichert", "Task saved"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            items(quickActions, key = { "qa_" + it.title }) { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (action is QuickAction.Conversion) {
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText(tr("Ergebnis", "Result"), action.value))
                                Toast.makeText(context, tr("Ergebnis kopiert", "Result copied"), Toast.LENGTH_SHORT).show()
                            } else {
                                QuickActions.perform(context, action)
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Text(action.icon, style = homeTextStyle(20.sp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        action.title,
                        style = homeTextStyle((20 * settings.textScale).sp).copy(
                            color = if (action is QuickAction.Conversion) MaterialTheme.colorScheme.primary else colors.text,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (query.isBlank() && suggestions.isNotEmpty()) {
                item(key = "suggestions_header") { SectionLabel(tr("Vorschläge", "Suggestions")) }
                items(suggestions, key = { "s_" + it.key }) { app ->
                    AppRow(
                        app = app,
                        showIcon = settings.showIcons,
                        iconSize = settings.iconSize.dp,
                        fontSize = (22 * settings.textScale).sp,
                        hasNotification = settings.notificationDots && app.notificationKey in notifications,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                    )
                }
            }
            items(results.take(30), key = { it.key }) { app ->
                AppRow(
                    app = app,
                    showIcon = settings.showIcons,
                    iconSize = settings.iconSize.dp,
                    fontSize = (22 * settings.textScale).sp,
                    hasNotification = settings.notificationDots && app.notificationKey in notifications,
                    onClick = { onLaunch(app) },
                    onLongClick = { onLongPress(app) },
                )
            }
            if (shortcutResults.isNotEmpty()) {
                item(key = "shortcuts_header") { SectionLabel(tr("Aktionen", "Actions")) }
                items(shortcutResults, key = { "sc_${it.`package`}_${it.id}_${it.userHandle.hashCode()}" }) { sc ->
                    ShortcutResultRow(
                        shortcut = sc,
                        appLabel = labelsByPackage[sc.`package`],
                        fontSize = (20 * settings.textScale).sp,
                        loadIcon = shortcutIcon,
                        onClick = { onShortcut(sc) },
                    )
                }
            }
            if (contacts.isNotEmpty()) {
                item(key = "contacts_header") { SectionLabel(tr("Kontakte", "Contacts")) }
                items(contacts, key = { "contact_${it.id}" }) { contact ->
                    ContactRow(
                        contact,
                        (22 * settings.textScale).sp,
                        onClick = { ContactSearch.open(context, contact) },
                        onLongClick = {
                            onPinContact(contact.uri.toString(), contact.name)
                            Toast.makeText(context, tr("${contact.name} zu Favoriten hinzugefügt", "${contact.name} added to favorites"), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            if (settings.searchContacts && !contactsAllowed && query.trim().length >= 2) {
                item(key = "contacts_permission") {
                    ActionLine(tr("Auch Kontakte durchsuchen – Zugriff erlauben", "Also search contacts – allow access")) {
                        requestContacts.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            }
            if (query.isNotBlank()) {
                item(key = "web") {
                    ActionLine(tr("Im Web suchen: „$query“", "Search the web: “$query”")) { SystemActions.webSearch(context, query, settings.searchEngine) }
                }
                item(key = "store") {
                    ActionLine(tr("Im Play Store suchen", "Search the Play Store")) { SystemActions.storeSearch(context, query) }
                }
            }
        }
    }
}

@Composable
private fun ActionLine(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = homeTextStyle(17.sp).copy(color = LocalHomeColors.current.secondary),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ContactRow(contact: ContactResult, fontSize: TextUnit, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                contact.name.take(1).uppercase(),
                style = homeTextStyle(15.sp).copy(fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(contact.name, style = homeTextStyle(fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = homeTextStyle(13.sp).copy(color = LocalHomeColors.current.secondary, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ShortcutResultRow(
    shortcut: ShortcutInfo,
    appLabel: String?,
    fontSize: TextUnit,
    loadIcon: (ShortcutInfo) -> Drawable?,
    onClick: () -> Unit,
) {
    val icon by produceState<ImageBitmap?>(null, shortcut.id, shortcut.`package`) {
        value = withContext(Dispatchers.IO) { loadIcon(shortcut)?.toBitmap(96, 96)?.asImageBitmap() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp)) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString(),
                style = homeTextStyle(fontSize),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (appLabel != null) {
                Text(
                    appLabel,
                    style = homeTextStyle(13.sp).copy(color = LocalHomeColors.current.secondary),
                    maxLines = 1,
                )
            }
        }
    }
}
