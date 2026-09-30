package dev.minimal.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.minimal.launcher.pro.Pro

/** Ist Pro freigeschaltet? (In der GitHub-Version immer ja.) */
@Composable
fun isPro(): Boolean = Pro.isPro.collectAsState().value

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Hinweis zur Pro-Version mit Kauf und Wiederherstellen; schließt sich nach dem Kauf selbst. */
@Composable
fun PaywallDialog(feature: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pro by Pro.isPro.collectAsState()
    val price by Pro.price.collectAsState()
    LaunchedEffect(pro) { if (pro) onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pro freischalten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (feature != null) Text("„$feature“ gehört zu Pro.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Einmal kaufen, für immer nutzen – kein Abo:\n" +
                        "• Kontextbasierte Seiten (Auto, Kopfhörer, WLAN, Laden)\n" +
                        "• Eigene Icons pro App\n" +
                        "• Automatische tägliche Sicherung\n" +
                        "• Wochenbericht, Tagesziel & Kategorie-Limits\n" +
                        "• Aufgabenliste auf dem Startbildschirm",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { context.findActivity()?.let(Pro::purchase) }) {
                Text(if (price != null) "Freischalten · $price" else "Freischalten")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Pro.restore() }) { Text("Wiederherstellen") }
                TextButton(onClick = onDismiss) { Text("Später") }
            }
        },
    )
}

/** Hinweise vor Berechtigungsanfragen (von Google Play für Standort & Bedienungshilfe verlangt). */
enum class Disclosure(val title: String, val text: String, val accept: String) {
    ACCESSIBILITY(
        "Bedienungshilfe",
        "Der Launcher nutzt die Bedienungshilfen-Schnittstelle von Android ausschließlich, um auf deinen Wunsch " +
            "den Bildschirm per Doppeltipp zu sperren und die Benachrichtigungs- oder Schnelleinstellungsleiste per " +
            "Wischgeste zu öffnen. Er liest keine Bildschirminhalte, beobachtet keine Eingaben und sammelt oder " +
            "überträgt keine Daten.\n\nIm nächsten Schritt den Eintrag „… – Gesten“ aktivieren.",
        "Weiter",
    ),
    WEATHER_LOCATION(
        "Standort fürs Wetter",
        "Für das Wetter am aktuellen Ort liest der Launcher deinen ungefähren Standort, rundet ihn auf ca. 1 km " +
            "und sendet nur diese gerundeten Koordinaten an Open-Meteo, um die Vorhersage abzurufen. Der Standort " +
            "wird nicht gespeichert und nur abgefragt, solange das Wetter eingeschaltet ist. Alternativ kannst du " +
            "einen festen Ort eintragen – dann ist keine Standortberechtigung nötig.",
        "Standort erlauben",
    ),
    WIFI_LOCATION(
        "Standort für WLAN-Regeln",
        "Android gibt den Namen des verbundenen WLANs nur mit Standortberechtigung heraus. Der Launcher nutzt sie " +
            "ausschließlich, um den WLAN-Namen mit deiner Seitenregel zu vergleichen. Es werden keine Standortdaten " +
            "gespeichert oder übertragen.",
        "Standort erlauben",
    ),
}

@Composable
fun DisclosureDialog(disclosure: Disclosure, onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(disclosure.title) },
        text = { Text(disclosure.text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onAccept()
            }) { Text(disclosure.accept) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Nicht jetzt") } },
    )
}
