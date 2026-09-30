package dev.minimal.launcher.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.minimal.launcher.LauncherViewModel
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.IconPack
import dev.minimal.launcher.data.LauncherSettings
import kotlinx.coroutines.launch

/** Eigenes Icon für eine App wählen: aus einem Icon-Pack oder aus der Galerie. */
@Composable
fun IconPickerDialog(app: AppInfo, vm: LauncherViewModel, settings: LauncherSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs = remember { IconPack.installed(context) }
    var browsing by remember { mutableStateOf<Pair<String, String>?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val spec = vm.importIconImage(uri)
            if (spec != null) {
                vm.setCustomIcon(app.key, spec)
                onDismiss()
            } else {
                Toast.makeText(context, "Bild konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pack = browsing
    if (pack != null) {
        PackIconGrid(
            packName = pack.first,
            packLabel = pack.second,
            vm = vm,
            onBack = { browsing = null },
            onPick = { name ->
                vm.setCustomIcon(app.key, "pack:${pack.first}/$name")
                onDismiss()
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Icon für ${app.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                packs.forEach { (pkg, label) ->
                    SheetAction("Aus „$label“ wählen") { browsing = pkg to label }
                }
                if (packs.isEmpty()) {
                    Text(
                        "Kein Icon-Pack installiert. Icon-Packs gibt es im Play Store (Suche „icon pack“).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SheetAction("Aus Galerie wählen") {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (app.key in settings.customIcons) {
                    SheetAction("Auf Standard zurücksetzen") {
                        vm.setCustomIcon(app.key, null)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun PackIconGrid(
    packName: String,
    packLabel: String,
    vm: LauncherViewModel,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val names by produceState(emptyList<String>(), packName) { value = vm.packIconNames(packName) }
    val shown = remember(names, query) {
        val q = query.trim().lowercase().replace(" ", "_")
        if (q.isEmpty()) names else names.filter { it.contains(q) }
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text(packLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Icon suchen, z. B. whatsapp") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${shown.size} Icons", style = MaterialTheme.typography.bodySmall)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(56.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(shown, key = { it }) { name ->
                        val bitmap by produceState<ImageBitmap?>(null, packName, name) {
                            value = vm.packPreview(packName, name)
                        }
                        Box(
                            Modifier
                                .padding(4.dp)
                                .size(48.dp)
                                .clickable { onPick(name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            bitmap?.let { Image(it, contentDescription = name, modifier = Modifier.size(44.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onBack) { Text("Zurück") } },
    )
}
