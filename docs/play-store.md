# Google Play – Unterlagen & Checkliste

## 1. Was du selbst erledigen musst

1. **Google-Play-Entwicklerkonto** anlegen (einmalig 25 USD): https://play.google.com/console
2. **Namen & Paketnamen festlegen** – in `gradle.properties`:
   - `launcherName=…` (Anzeigename, gesetzt: `Kanso`)
   - `playApplicationId=…` (gesetzt: `ch.hazzar.kanso` – nach dem ersten Upload nicht mehr änderbar)
3. **Upload-Schlüssel erzeugen** (auf deinem Rechner, gut aufbewahren):
   ```bash
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 upload.jks > upload.b64   # macOS: base64 -i upload.jks -o upload.b64
   ```
   In GitHub unter *Settings → Secrets and variables → Actions* anlegen:
   `UPLOAD_KEYSTORE_BASE64` (Inhalt von upload.b64), `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS` (= upload),
   `UPLOAD_KEY_PASSWORD`. Danach baut jeder Push ein signiertes **AAB** (Artefakt „kanso-play-aab“).
   In der Play Console **Play App Signing** aktivieren (Standard) – der Upload-Schlüssel ist dann ersetzbar.
4. **Datenschutzerklärung veröffentlichen** (öffentliche URL nötig): Inhalt aus `docs/privacy-policy.md`,
   z. B. als GitHub Pages (Repo müsste dafür öffentlich sein) oder auf einer eigenen Webseite.
5. **In-App-Produkt anlegen**: *Monetarisieren → Produkte → In-App-Produkte*
   - Produkt-ID: **`pro_lifetime`** (genau so, sie steht im Code)
   - Typ: einmalig, Preis z. B. CHF 6.–
6. **Test**: interne Testspur anlegen, dich als Lizenztester eintragen, AAB hochladen, Kauf testen.

## 2. Store-Eintrag

**App-Name (max. 30 Zeichen)**
> Kanso – Minimal Launcher

*Kanso (簡素) ist ein japanisches Gestaltungsprinzip: Einfachheit durch Weglassen des Unnötigen.*

**Kurzbeschreibung (max. 80 Zeichen)**
> Minimalistischer Launcher: Favoriten, Buchstabenleiste, Fokus-Modus, Bildschirmzeit.

**Vollständige Beschreibung**
> Ein ruhiger, schneller Startbildschirm für Android – ohne Werbung, ohne Tracking, alles bleibt auf deinem Gerät.
>
> • Favoritenliste mit Ordnern, Wisch-Aktionen und mehreren Seiten
> • Buchstabenleiste mit Wellen-Effekt – jede App in einer Bewegung
> • Suche mit Rechner, Einheiten, Timer, Wecker, Kontakten und App-Aktionen
> • Benachrichtigungspunkte, Vorschau und Mediensteuerung
> • Uhr mit Terminen, Wecker, Akku und optionalem Wetter
> • Fokus-Modus mit Denkpause, Tageslimits, Graustufen am Abend
> • App-Sperre per Fingerabdruck, vertrauliches Profil, Arbeitsprofil
> • Icon-Packs, Designsymbole, Schriftarten, Material You
>
> **Pro (Einmalkauf, kein Abo):** kontextbasierte Seiten (Auto, Kopfhörer, WLAN), Fokus-Sitzungen, Benachrichtigungs-Zusammenfassung, Kanso-Stile & Schriften, Pop-up-Widgets, Absichtsfrage, Tagesabsicht, Abendrückblick, eigene Icons, automatische
> Sicherung, Wochenbericht mit Tagesziel und Kategorie-Limits, Aufgabenliste.

**Kategorie:** Personalisierung · **Einstufung:** ohne Altersbeschränkung

### English listing (en-US)

**App name**
> Kanso – Minimal Launcher

**Short description**
> A calm, minimal launcher: favorites, letter bar, focus mode, screen time.

**Full description**
> A calm, fast home screen for Android – no ads, no tracking, everything stays on your device.
> Kanso (簡素) is a Japanese design principle: simplicity by leaving out the unnecessary.
>
> • Favorites with folders, swipe actions and multiple pages
> • Letter bar with wave effect – any app in one motion
> • Search with calculator, unit conversion, timer, alarm, contacts and app actions
> • Notification dots, previews and media controls
> • Clock with events, alarm, battery and optional weather
> • Focus mode with a mindful pause, daily limits, grayscale in the evening
> • Declutter: find apps you haven't opened in months
> • App lock with fingerprint, private space, work profile
> • Icon packs, themed icons, fonts, Material You
>
> **Pro (one-time purchase, no subscription):** focus sessions, notification digest, Kanso styles & fonts, context-based pages (car, headphones, Wi-Fi), pop-up widgets,
> intention prompt, daily intention, evening recap, custom icons, automatic backup, weekly report with daily goal and category
> limits, task list.

## 3. Datensicherheit (Formular „Data safety“)

- Erhebt die App Nutzerdaten? → **Nein** (Daten verlassen das Gerät nicht; Ausnahme Wetter, siehe unten)
- Wetter (optional): *Ungefährer Standort* wird an Open-Meteo **übertragen**, nicht gespeichert,
  **nicht mit Dritten geteilt** im Sinne von Werbung/Analyse, Zweck „App-Funktionen“, optional.
- Verschlüsselung bei Übertragung: **Ja** (HTTPS)
- Löschung: Daten liegen lokal; Deinstallieren löscht alles.

## 4. Berechtigungen – Begründungen für die Play Console

| Berechtigung | Begründung (für das Deklarationsformular) |
|---|---|
| **Bedienungshilfe (AccessibilityService)** | Kernfunktion eines Launchers: Bildschirm per Doppeltipp sperren (`GLOBAL_ACTION_LOCK_SCREEN`) und Benachrichtigungsleiste per Wischgeste öffnen. Keine Inhalte werden gelesen. In-App-Hinweis vor der Aktivierung vorhanden. Video der Funktion für die Prüfung aufnehmen. |
| **Nutzungszugriff (PACKAGE_USAGE_STATS)** | Bildschirmzeit, Tageslimits, Wochenbericht (Digital Wellbeing). Daten bleiben lokal. |
| **Standort (grob/fein)** | Grob: Wetter am aktuellen Ort (gerundet). Fein: Android verlangt ihn, um den WLAN-Namen für Seitenregeln zu lesen. Beides optional, mit In-App-Hinweis vor der Anfrage; kein Hintergrundstandort. |
| **Benachrichtigungszugriff** | Benachrichtigungspunkte, Vorschau, Mediensteuerung auf dem Startbildschirm; optional die Zusammenfassung (Benachrichtigungen ablenkender Apps werden zurückgehalten und gesammelt zugestellt – nur lokal). |
| **Pakete sehen** | Über `<queries>` (kein QUERY_ALL_PACKAGES) – Launcher-Kernfunktion. |
| **REQUEST_DELETE_PACKAGES** | „Deinstallieren“ im App-Menü des Launchers. |
| **POST_NOTIFICATIONS** | Nur für die optionale Timer-Erinnerung der Absichtsfrage („10 min sind um“) und den optionalen Abendrückblick. Wird erst angefragt, wenn man eine dieser Funktionen einschaltet. |
| Akku-Optimierung | In der Play-Version entfernt (nur Link zur Einstellungsliste). |

## 5. Vor dem ersten Upload prüfen

- [x] Name/Paketname gesetzt, Icon passt zum Namen (Ensō, `docs/store/icon-512.png` für den Store), kein Bezug zu „Niagara“
- [ ] Kontakt-E-Mail in Datenschutzerklärung und Store-Eintrag
- [ ] Screenshots (mind. 2) und Feature-Grafik 1024×500
- [ ] Kauf in der internen Testspur getestet, „Wiederherstellen“ getestet
- [ ] Bedienungshilfe-Deklaration inkl. Video eingereicht

## Grafiken

| Datei | Verwendung |
|---|---|
| `docs/store/icon-512.png` | App-Symbol (512×512) |
| `docs/store/feature-graphic.png` | Feature-Grafik (1024×500) |
| `docs/store/screenshots/out/de/*.png` | Smartphone-Screenshots (1080×1920) |

### Screenshots

Echte Aufnahmen vom Gerät (Ein/Aus + Leiser) in `docs/store/screenshots/raw/` ablegen – diese Namen:

1. `1-home.png` – Startbildschirm mit Uhr, Wetter und Favoriten
2. `2-drawer.png` – App-Liste, Finger auf der Buchstabenleiste
3. `3-search.png` – Suche mit Rechnung, z. B. „12*7+3“
4. `4-focus.png` – Fokus-Modus aktiv (blockierte App / Pause-Dialog)
5. `5-report.png` – Bildschirmzeit-Wochenbericht
6. `6-pages.png` – Seiten-Einstellungen mit Kontextregel (📍)

Tipps: neutrales Hintergrundbild, keine privaten Benachrichtigungen, Kontakte oder Termine sichtbar.
Die Rohbilder sind per `.gitignore` vom Repo ausgeschlossen.

Rahmen erzeugen (Play erlaubt höchstens 2:1, das Pixel liefert ~20:9):
```bash
cd docs/store/screenshots && npm i playwright && node frame.mjs de   # oder: en
```
Bildunterschriften stehen in `captions.json`.
