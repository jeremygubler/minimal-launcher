# Minimal Launcher

Ein minimalistischer Android-Launcher im Stil von Niagara – geschrieben in Kotlin mit Jetpack Compose.
Alle Funktionen sind enthalten, es gibt keine kostenpflichtige Version.

## Funktionen

**Startbildschirm**
- Vertikale Favoritenliste, einhändig bedienbar (unten ausgerichtet)
- Uhr, Datum und nächster Wecker (antippen öffnet Uhr bzw. Kalender)
- Widgets direkt auf dem Startbildschirm (hinzufügen, sortieren, entfernen)
- Ordner als Favoriten (aufklappbar, mit Mini-Icon-Vorschau)
- **Wisch-Aktionen:** Favorit nach rechts wischen öffnet eine zweite App (z. B. WhatsApp → Signal)

**Alle Apps**
- Buchstabenleiste am Rand mit „Wellen“-Effekt und haptischem Feedback – ziehen springt direkt zum Buchstaben
- Alphabetische Liste mit Abschnitts-Überschriften, Arbeitsprofil-Apps inklusive
- Linkshänder-Modus (Leiste links)

**Suche**
- Unscharfe Suche (Anfang, Wortanfang, Initialen, Teilstring, Buchstabenfolge; ignoriert Umlaute/Akzente)
- Integrierter Taschenrechner (`12*3,5+4`, Ergebnis antippen zum Kopieren)
- Websuche und Play-Store-Suche als Fallback; Enter öffnet den ersten Treffer

**Benachrichtigungen**
- Benachrichtigungspunkte an Apps und Ordnern
- Vorschau der neuesten Benachrichtigung direkt unter dem Favoriten (antippen = öffnen, lange drücken = verwerfen)

**App-Menü (lange drücken)**
- App-Shortcuts (z. B. „Neue Nachricht“), wenn der Launcher Standard ist
- Favorit hinzufügen/entfernen, Wisch-Aktion festlegen, zu Ordner hinzufügen
- Umbenennen, Ausblenden, App-Info, Deinstallieren

**Anpassung**
- Hell/Dunkel/System, Akzentfarbe inkl. Material You
- Icon-Packs (ADW/Nova-Format), Icons an/aus, Icon- und Schriftgröße
- Hintergrund abdunkeln und weichzeichnen (Android 12+)
- Gesten: Doppeltippen, nach oben/unten wischen – frei belegbar (Sperren, Benachrichtigungen, Schnelleinstellungen, Suche, Alle Apps)
- Ausgeblendete und umbenannte Apps verwalten
- Sicherung/Wiederherstellung aller Einstellungen als JSON-Datei

## Bedienung

| Geste | Standard |
|---|---|
| Buchstabenleiste ziehen | Zu Apps mit diesem Buchstaben springen |
| Nach unten wischen | Benachrichtigungen |
| Nach oben wischen | Suche |
| Doppeltippen | Bildschirm sperren |
| Leeren Bereich lange drücken | Menü (Widgets, Hintergrund, Einstellungen) |
| App lange drücken | App-Menü |

## Einrichtung

1. APK installieren und **Launcher-Einstellungen** öffnen (erscheint als eigene App).
2. Unter „Einrichtung“ den Launcher als Standard festlegen.
3. Optional: **Benachrichtigungszugriff** erlauben (Punkte & Vorschau) und die **Bedienungshilfe** aktivieren (Sperren per Doppeltipp). Die Bedienungshilfe liest keine Bildschirminhalte.

## Bauen

Voraussetzung: JDK 17 und Android SDK (oder einfach Android Studio öffnen).

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Jeder Push baut die APK automatisch über GitHub Actions (Reiter **Actions → Build APK → Artifacts**).

Mindestversion: Android 8.0 (API 26).
