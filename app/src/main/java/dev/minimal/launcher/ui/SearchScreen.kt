package dev.minimal.launcher.ui

import android.content.ClipData
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
import dev.minimal.launcher.util.SystemActions

@Composable
fun SearchScreen(
    apps: List<AppInfo>,
    settings: LauncherSettings,
    notifications: Set<String>,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = LocalHomeColors.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, apps) { AppSearch.search(apps, query) }
    val calc = remember(query) { Calculator.evaluate(query) }

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
                Text("Suchen…", style = homeTextStyle(30.sp).copy(color = colors.secondary))
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
                    when {
                        first != null -> onLaunch(first)
                        query.isNotBlank() -> SystemActions.webSearch(context, query)
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
                                cm?.setPrimaryClip(ClipData.newPlainText("Ergebnis", calc))
                                Toast.makeText(context, "Ergebnis kopiert", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp),
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
            if (query.isNotBlank()) {
                item(key = "web") {
                    ActionLine("Im Web suchen: „$query“") { SystemActions.webSearch(context, query) }
                }
                item(key = "store") {
                    ActionLine("Im Play Store suchen") { SystemActions.storeSearch(context, query) }
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
