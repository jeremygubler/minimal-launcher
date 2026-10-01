package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
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
        title = { Text(tr("Pro freischalten", "Unlock Pro")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (feature != null) Text(tr("„$feature“ gehört zu Pro.", "“$feature” is part of Pro."), style = MaterialTheme.typography.bodyLarge)
                Text(
                    tr("Einmal kaufen, für immer nutzen – kein Abo:\n", "Buy once, use forever – no subscription:\n") +
                        tr("• Fokus-Sitzungen mit echter App-Sperre\n", "• Focus sessions with real app blocking\n") +
                        tr("• Benachrichtigungs-Zusammenfassung zu festen Zeiten\n", "• Notification digest at set times\n") +
                        tr("• Kontextbasierte Seiten (Auto, Kopfhörer, WLAN, Laden)\n", "• Context-based pages (car, headphones, Wi-Fi, charging)\n") +
                        tr("• Pop-up-Widgets auf Favoriten\n", "• Pop-up widgets on favorites\n") +
                        tr("• Absichtsfrage mit Timer-Erinnerung\n", "• Intention prompt with timer reminder\n") +
                        tr("• Wochenbericht, Tagesziel, Kategorie-Limits & Abendrückblick\n", "• Weekly report, daily goal, category limits & evening recap\n") +
                        tr("• Kanso-Stile & Schriften\n", "• Kanso styles & fonts\n") +
                        tr("• Eigene Icons pro App\n", "• Custom icons per app\n") +
                        tr("• Aufgabenliste auf dem Startbildschirm\n", "• Task list on the home screen\n") +
                        tr("• Automatische tägliche Sicherung", "• Automatic daily backup"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { context.findActivity()?.let(Pro::purchase) }) {
                Text(if (price != null) tr("Freischalten · $price", "Unlock · $price") else tr("Freischalten", "Unlock"))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Pro.restore() }) { Text(tr("Wiederherstellen", "Restore")) }
                TextButton(onClick = onDismiss) { Text(tr("Später", "Later")) }
            }
        },
    )
}

/** Hinweise vor Berechtigungsanfragen (von Google Play für Standort & Bedienungshilfe verlangt). */
enum class Disclosure(val title: String, val text: String, val accept: String) {
    ACCESSIBILITY(
        tr("Bedienungshilfe", "Accessibility service"),
        tr("Der Launcher nutzt die Bedienungshilfen-Schnittstelle von Android ausschließlich, um auf deinen Wunsch ", "The launcher uses Android's accessibility API solely to, at your request, ") +
            tr("den Bildschirm per Doppeltipp zu sperren und die Benachrichtigungs- oder Schnelleinstellungsleiste per ", "lock the screen with a double tap and open the notification or quick settings shade with a ") +
            tr("Wischgeste zu öffnen. Er liest keine Bildschirminhalte, beobachtet keine Eingaben und sammelt oder ", "swipe gesture. It does not read screen content, observe input, or collect or ") +
            tr("überträgt keine Daten.\n\nIm nächsten Schritt den Eintrag „… – Gesten“ aktivieren.", "transmit any data.\n\nIn the next step, enable the entry “… – Gestures”."),
        tr("Weiter", "Continue"),
    ),
    WEATHER_LOCATION(
        tr("Standort fürs Wetter", "Location for weather"),
        tr("Für das Wetter am aktuellen Ort liest der Launcher deinen ungefähren Standort, rundet ihn auf ca. 1 km ", "For weather at your current location, the launcher reads your approximate location, rounds it to about 1 km ") +
            tr("und sendet nur diese gerundeten Koordinaten an Open-Meteo, um die Vorhersage abzurufen. Der Standort ", "and sends only these rounded coordinates to Open-Meteo to fetch the forecast. The location ") +
            tr("wird nicht gespeichert und nur abgefragt, solange das Wetter eingeschaltet ist. Alternativ kannst du ", "is not stored and only requested while weather is turned on. Alternatively you can ") +
            tr("einen festen Ort eintragen – dann ist keine Standortberechtigung nötig.", "enter a fixed place – then no location permission is needed."),
        tr("Standort erlauben", "Allow location"),
    ),
    WIFI_LOCATION(
        tr("Standort für WLAN-Regeln", "Location for Wi-Fi rules"),
        tr("Android gibt den Namen des verbundenen WLANs nur mit Standortberechtigung heraus. Der Launcher nutzt sie ", "Android only reveals the name of the connected Wi-Fi with location permission. The launcher uses it ") +
            tr("ausschließlich, um den WLAN-Namen mit deiner Seitenregel zu vergleichen. Es werden keine Standortdaten ", "solely to compare the Wi-Fi name with your page rule. No location data ") +
            tr("gespeichert oder übertragen.", "is stored or transmitted."),
        tr("Standort erlauben", "Allow location"),
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Nicht jetzt", "Not now")) } },
    )
}
