# Minimal Launcher

Ein minimalistischer Android-Launcher im Stil von Niagara – geschrieben in Kotlin mit Jetpack Compose.
Alle Funktionen sind enthalten, es gibt keine kostenpflichtige Version.

## Funktionen

**Startbildschirm**
- Vertikale Favoritenliste, einhändig bedienbar (unten ausgerichtet)
- Uhr, Datum, nächster Wecker und nächster Termin (antippen öffnet Uhr, Kalender bzw. den Termin)
- Akku-Hinweis beim Laden oder unter 20 %
- Mediensteuerung, wenn Musik oder Podcasts laufen (Titel, Zurück/Pause/Weiter)
- Widgets direkt auf dem Startbildschirm (hinzufügen, sortieren, entfernen)
- **Mehrere Favoriten-Seiten** (z. B. „Start“, „Arbeit“, „Privat“): links/rechts wischen oder Seitennamen antippen; verwalten unter *Einstellungen → Favoriten & Seiten*
- Ordner als Favoriten (aufklappbar, mit Mini-Icon-Vorschau)
- Ordner direkt aus dem App-Menü anlegen („Neuen Ordner mit dieser App“)
- Favoriten per **Drag & Drop** sortieren (lange drücken und ziehen; lange drücken ohne Ziehen öffnet das Menü)
- **Wisch-Aktionen:** Favorit nach rechts wischen öffnet eine zweite App (z. B. WhatsApp → Signal)

**Alle Apps**
- Buchstabenleiste am Rand mit „Wellen“-Effekt und haptischem Feedback – ziehen springt direkt zum Buchstaben
- Alphabetische Liste mit Abschnitts-Überschriften, Arbeitsprofil-Apps inklusive
- Linkshänder-Modus (Leiste links)
- **Vertrauliches Profil** (Android 15+, früher „Privater Bereich“): eigener Abschnitt am Ende der Liste (🔒 in der Buchstabenleiste), entsperren/sperren direkt im Launcher
- Pausiertes Arbeitsprofil: Antippen einer Arbeits-App bietet an, das Profil fortzusetzen

**Suche**
- Unscharfe Suche (Anfang, Wortanfang, Initialen, Teilstring, Buchstabenfolge; ignoriert Umlaute/Akzente)
- Integrierter Taschenrechner (`12*3,5+4`, Ergebnis antippen zum Kopieren)
- **Vorschläge**: meistgenutzte Apps beim Öffnen der Suche; Treffer werden nach Nutzung sortiert (nur lokal gespeichert)
- **App-Aktionen** in der Suche, z. B. „Neue Nachricht“ oder „Scannen“
- **Kontakte** in den Suchergebnissen (fragt beim ersten Mal nach der Berechtigung, abschaltbar)
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
- **Designsymbole** (Themed Icons): einfarbige Icons in den Systemfarben, wie beim Pixel Launcher (Android 13+)
- Icon-Packs (ADW/Nova-Format), Icons an/aus, Icon- und Schriftgröße
- Hintergrund abdunkeln und weichzeichnen (Android 12+)
- Gesten: Doppeltippen, nach oben/unten wischen – frei belegbar (Sperren, Benachrichtigungen, Schnelleinstellungen, Suche, Alle Apps, Gemini/Assistant)
- Ausgeblendete und umbenannte Apps verwalten
- Sicherung/Wiederherstellung aller Einstellungen als JSON-Datei

**Stabilität & Tempo**
- App-Namen und Icons werden zwischengespeichert – der Launcher ist nach einem Neustart sofort da
- Lokales Absturzprotokoll unter *Einstellungen → Fehlerprotokoll* (teilen oder löschen); es werden keine Daten automatisch gesendet

## Bedienung

| Geste | Standard |
|---|---|
| Buchstabenleiste ziehen | Zu Apps mit diesem Buchstaben springen |
| Nach unten wischen | Benachrichtigungen |
| Nach oben wischen | Suche |
| Doppeltippen | Bildschirm sperren |
| Leeren Bereich lange drücken | Menü (Widgets, Hintergrund, Einstellungen) |
| App lange drücken | App-Menü |
| Favorit lange drücken und ziehen | Favoriten umsortieren |
| Links/rechts wischen | Favoriten-Seite wechseln |

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

Jeder Push auf `main` führt die Unit-Tests aus und baut die APK über GitHub Actions
(Reiter **Actions → Build APK → Artifacts**). Die Datei ohne `-debug` ist die optimierte Release-Version.

## Releases & automatische Updates

Jeder erfolgreiche Build auf `main` erstellt automatisch ein GitHub-Release `v1.0.<Build-Nr>` mit der APK
(zusätzlich auch jeder Tag `v*`).
Mit [Obtainium](https://github.com/ImranR98/Obtainium) lässt sich das Repo als Quelle eintragen –
dann kommen Updates direkt aufs Handy.

Alle Builds sind mit demselben Schlüssel (`keystore/launcher.keystore`) signiert, daher lassen sich neue
Versionen einfach über alte installieren, ohne Einstellungen zu verlieren. Für eine Veröffentlichung im
Play Store einen eigenen, geheimen Schlüssel verwenden und über `SIGNING_KEYSTORE_PATH`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` und `SIGNING_KEY_PASSWORD` setzen.

Mindestversion: Android 8.0 (API 26).
