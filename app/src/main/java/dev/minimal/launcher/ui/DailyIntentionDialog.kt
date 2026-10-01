package dev.minimal.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.minimal.launcher.util.tr

/** Tagesabsicht setzen, ändern oder abhaken. */
@Composable
fun DailyIntentionDialog(
    current: String?,
    done: Boolean,
    onSave: (String) -> Unit,
    onToggleDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Was ist dir heute wichtig?", "What matters to you today?")) },
        text = {
            Column {
                Text(
                    tr("Eine Sache genügt. Sie steht heute unter der Uhr.", "One thing is enough. It stays below the clock today."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(tr("z. B. Präsentation fertig machen", "e.g. finish the presentation")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row {
                if (current != null && text.trim() == current) {
                    TextButton(onClick = {
                        onToggleDone()
                        onDismiss()
                    }) { Text(if (done) tr("Wieder offen", "Reopen") else tr("Erledigt ✓", "Done ✓")) }
                }
                TextButton(
                    enabled = text.isNotBlank() && text.trim() != current,
                    onClick = {
                        onSave(text)
                        onDismiss()
                    },
                ) { Text(tr("Speichern", "Save")) }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (current != null && text.isBlank()) onSave("")
                onDismiss()
            }) { Text(if (current != null && text.isBlank()) tr("Entfernen", "Remove") else tr("Abbrechen", "Cancel")) }
        },
    )
}
