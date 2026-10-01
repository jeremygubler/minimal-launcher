package dev.minimal.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.minimal.launcher.util.tr

/**
 * „Was ist neu“ – nur bei spürbaren Neuerungen. [VERSION] von Hand erhöhen, wenn es etwas
 * Erwähnenswertes gibt (nicht bei jedem Build).
 */
object WhatsNew {
    const val VERSION = 1

    /** Titel → Beschreibung; „(Pro)“ wird angehängt, wo nötig. */
    val items: List<Triple<String, String, Boolean>>
        get() = listOf(
            Triple(tr("Kanso-Stile", "Kanso styles"), tr("Sumi, Washi, Matcha, Yoru, Sakura – mit den Schriften Cormorant und Inter.", "Sumi, Washi, Matcha, Yoru, Sakura – with the fonts Cormorant and Inter."), true),
            Triple(tr("Fokus-Sitzung", "Focus session"), tr("25, 50 oder 90 Minuten, ablenkende Apps wirklich gesperrt. Leeren Bereich lange drücken.", "25, 50 or 90 minutes with distracting apps truly locked. Long-press an empty area."), true),
            Triple(tr("Benachrichtigungs-Zusammenfassung", "Notification digest"), tr("Ablenkende Apps melden sich gesammelt, z. B. um 12 und 18 Uhr.", "Distracting apps notify you in bundles, e.g. at noon and 6 pm."), true),
            Triple(tr("Tagesabsicht & Abendrückblick", "Daily intention & evening recap"), tr("Morgens eine Sache, die zählt – abends ein ruhiger Rückblick.", "One thing that matters in the morning – a calm recap in the evening."), true),
            Triple(tr("Aufräumen", "Declutter"), tr("Findet Apps, die du seit Monaten nicht geöffnet hast.", "Finds apps you haven't opened in months."), false),
            Triple(tr("Neu gegliederte Einstellungen", "Reorganized settings"), tr("Alles in übersichtlichen Kategorien – jetzt auch auf Englisch.", "Everything in clear categories – now also in English."), false),
        )
}

@Composable
fun WhatsNewDialog(pro: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Neu in Kanso", "New in Kanso")) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WhatsNew.items.forEach { (title, text, isPro) ->
                    Column {
                        Text(
                            title + if (isPro && !pro) " (Pro)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schön", "Nice")) } },
    )
}
