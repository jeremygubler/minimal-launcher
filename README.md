# Kanso

*Kanso (簡素) – japanisch für Einfachheit durch Weglassen des Unnötigen.*

Ein minimalistischer Android-Launcher im Stil von Niagara – geschrieben in Kotlin mit Jetpack Compose.
Alle Funktionen sind enthalten, es gibt keine kostenpflichtige Version.

## Funktionen

**Startbildschirm**
- Vertikale Favoritenliste, einhändig bedienbar (unten ausgerichtet)
- Uhr, Datum, nächster Wecker und bis zu drei kommende Termine (antippen öffnet Uhr, Kalender bzw. den Termin)
- **Wetter** (optional, standardmäßig aus): Open-Meteo ohne Konto, fester Ort oder gerundeter Standort
- Akku-Hinweis beim Laden oder unter 20 %
- **Bildschirmzeit** heute unter der Uhr (antippen: Top-Apps), Nutzung pro App im App-Menü – benötigt „Nutzungszugriff“, bleibt lokal
- **Aufgabenliste** unter der Uhr: in der Suche `todo Milch kaufen` oder `todo morgen Zahnarzt` (auch im Home-Menü); antippen = erledigt, lange drücken = bearbeiten/löschen; erledigte verschwinden am nächsten Tag
- **Schnellnotiz** auf dem Startbildschirm (Home-Menü oder in der Suche `notiz …`)
- Mediensteuerung, wenn Musik oder Podcasts laufen (Titel, Zurück/Pause/Weiter)
- Widgets direkt auf dem Startbildschirm (hinzufügen, sortieren, entfernen)
- **Mehrere Favoriten-Seiten** (z. B. „Start“, „Arbeit“, „Privat“): links/rechts wischen oder Seitennamen antippen; verwalten unter *Einstellungen → Favoriten & Seiten*
- **Kontextbasierte Seiten**: Seite wechselt bei Kopfhörern, beim Laden, mit einem bestimmten Bluetooth-Gerät (z. B. Auto) oder WLAN; Kontext hat Vorrang vor Zeitplänen
- **Automatischer Seitenwechsel nach Zeitplan** (z. B. „Arbeit“ Mo–Fr 08:00–17:00, auch über Mitternacht); gewechselt wird nur zu Beginn/Ende eines Zeitfensters
- **Kontakte als Favoriten** (in der Suche einen Kontakt lange drücken)
- Ordner als Favoriten (aufklappbar, mit Mini-Icon-Vorschau)
- Ordner direkt aus dem App-Menü anlegen („Neuen Ordner mit dieser App“)
- Favoriten per **Drag & Drop** sortieren (lange drücken und ziehen; lange drücken ohne Ziehen öffnet das Menü)
- **Wisch-Aktionen:** Favorit nach rechts wischen öffnet eine zweite App (z. B. WhatsApp → Signal), nach links einen App-Shortcut (z. B. „Neue Nachricht“)

**Alle Apps**
- Buchstabenleiste am Rand mit „Wellen“-Effekt und haptischem Feedback – ziehen springt direkt zum Buchstaben
- Alphabetische Liste mit Abschnitts-Überschriften, Arbeitsprofil-Apps inklusive
- Linkshänder-Modus (Leiste links)
- Neu installierte Apps sind drei Tage lang mit „Neu“ markiert
- **Vertrauliches Profil** (Android 15+, früher „Privater Bereich“): eigener Abschnitt am Ende der Liste (🔒 in der Buchstabenleiste), entsperren/sperren direkt im Launcher
- Pausiertes Arbeitsprofil: Antippen einer Arbeits-App bietet an, das Profil fortzusetzen

**Suche**
- Unscharfe Suche (Anfang, Wortanfang, Initialen, Teilstring, Buchstabenfolge; ignoriert Umlaute/Akzente)
- **Schnellaktionen**: `timer 5 min`, `wecker 7:30`, Einheiten umrechnen (`10 km in mi`, `25 °c`, `2 lb`), Systemeinstellungen (`wlan`, `bluetooth`, `akku` …), Telefonnummern anrufen/SMS, Webadressen öffnen
- Integrierter Taschenrechner (`12*3,5+4`, Ergebnis antippen zum Kopieren)
- **Vorschläge**: meistgenutzte Apps beim Öffnen der Suche; Treffer werden nach Nutzung sortiert (nur lokal gespeichert)
- **App-Aktionen** in der Suche, z. B. „Neue Nachricht“ oder „Scannen“
- **Kontakte** in den Suchergebnissen (fragt beim ersten Mal nach der Berechtigung, abschaltbar)
- Websuche mit wählbarer Suchmaschine (Google, DuckDuckGo, Startpage, Ecosia, Bing) und Play-Store-Suche als Fallback; Enter öffnet den ersten Treffer

**Benachrichtigungen**
- Benachrichtigungspunkte an Apps und Ordnern
- Vorschau der neuesten Benachrichtigung direkt unter dem Favoriten (antippen = öffnen, lange drücken = verwerfen)

**App-Menü (lange drücken)**
- App-Shortcuts (z. B. „Neue Nachricht“), wenn der Launcher Standard ist
- Favorit hinzufügen/entfernen, Wisch-Aktion festlegen, zu Ordner hinzufügen
- Umbenennen, Ausblenden, App-Info, Deinstallieren
- **Aufräumen**: schlägt Apps vor, die seit 30/90/180 Tagen nicht geöffnet wurden – ausblenden, deinstallieren oder bewusst behalten

**Anpassung**
- Hell/Dunkel/System, Akzentfarbe inkl. Material You
- **Designsymbole** (Themed Icons): einfarbige Icons in den Systemfarben, wie beim Pixel Launcher (Android 13+)
- **Eigene Icons pro App**: aus jedem installierten Icon-Pack (mit Suche) oder aus der Galerie
- Schriftart (System, Serif, Monospace, Handschrift) und Schriftstärke für den Startbildschirm
- Icon-Packs (ADW/Nova-Format), Icons an/aus, Icon- und Schriftgröße
- Hintergrund abdunkeln und weichzeichnen (Android 12+)
- Gesten: Doppeltippen, nach oben/unten wischen – frei belegbar (Sperren, Benachrichtigungen, Schnelleinstellungen, Suche, Alle Apps, Gemini/Assistant)
- Ausgeblendete und umbenannte Apps verwalten
- Sicherung/Wiederherstellung aller Einstellungen als JSON-Datei, optional **automatisch täglich** in einen Ordner (7 Stände)

**Fokus-Modus**
- Apps als ablenkend markieren (App-Menü oder Einstellungen)
- Wenn aktiv: ausgegraut, keine Benachrichtigungen/Punkte, einstellbare Denkpause (0–30 s) vor dem Öffnen
- Manuell (lange auf den Startbildschirm drücken) oder per Zeitplan

**Digitales Wohlbefinden**
- Tageslimit pro App (z. B. 30 min) und pro Kategorie (z. B. Social zusammen 1 h): danach Denkpause mit „Trotzdem öffnen“
- **Wochenbericht**: Balkendiagramm der letzten 7 Tage, Durchschnitt, Top-Apps, Tagesziel mit Serie, Nutzung nach Kategorie
- Icons nach Zeitplan in Graustufen (z. B. abends)

**App-Sperre**
- Ausgewählte Apps öffnen sich aus dem Launcher nur nach Fingerabdruck/PIN (inkl. ihrer Shortcuts); keine Benachrichtigungsvorschau

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
| Home-Taste auf dem Startbildschirm | einstellbar (z. B. Suche öffnen) |

## Einrichtung

Beim ersten Start führt ein kurzer Assistent durch die folgenden Schritte (erneut aufrufbar unter *Einstellungen → Einrichtung*).

1. APK installieren und **Launcher-Einstellungen** öffnen (erscheint als eigene App).
2. Unter „Einrichtung“ den Launcher als Standard festlegen.
3. Optional: **Benachrichtigungszugriff** erlauben (Punkte & Vorschau) und die **Bedienungshilfe** aktivieren (Sperren per Doppeltipp). Die Bedienungshilfe liest keine Bildschirminhalte.

## Andere Hersteller

Der Launcher nutzt nur Standard-Schnittstellen und läuft ab Android 8 auf allen Geräten.
Xiaomi, Oppo, OnePlus, Vivo, Huawei, Samsung & Co. beenden Hintergrunddienste teils aggressiv –
dann verschwinden Benachrichtigungspunkte und Mediensteuerung. Auf diesen Geräten zeigt
*Einstellungen → Einrichtung* direkt die Knöpfe „Von Akku-Optimierung ausnehmen“ und
„Autostart erlauben“. Einige Funktionen hängen von der Android-Version ab: Material You (12+),
Weichzeichnen (12+, nicht auf allen Geräten), Designsymbole (13+), vertrauliches Profil (15+).

## Varianten & Google Play

- **sideload** (GitHub-Releases): alle Funktionen frei, fester Schlüssel – die bisherige Version.
- **play** (Google Play): Pro-Funktionen per Einmalkauf `pro_lifetime`, Upload-Schlüssel aus GitHub Secrets,
  Google-konforme Berechtigungen und Hinweise. Jeder Push erzeugt zusätzlich ein AAB als Artefakt.

Checkliste, Store-Texte, Datensicherheit und Berechtigungs-Begründungen: [`docs/play-store.md`](docs/play-store.md).
Datenschutzerklärung: [`docs/privacy-policy.md`](docs/privacy-policy.md).

## Bauen

Voraussetzung: JDK 17 und Android SDK (oder einfach Android Studio öffnen).

```bash
./gradlew assembleSideloadDebug
# APK: app/build/outputs/apk/sideload/debug/app-sideload-debug.apk
./gradlew bundlePlayRelease   # AAB für Google Play
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
