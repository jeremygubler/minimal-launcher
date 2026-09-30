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
> **Pro (Einmalkauf, kein Abo):** kontextbasierte Seiten (Auto, Kopfhörer, WLAN), eigene Icons, automatische
> Sicherung, Wochenbericht mit Tagesziel und Kategorie-Limits, Aufgabenliste.

**Kategorie:** Personalisierung · **Einstufung:** ohne Altersbeschränkung

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
| **Benachrichtigungszugriff** | Benachrichtigungspunkte, Vorschau, Mediensteuerung auf dem Startbildschirm. |
| **Pakete sehen** | Über `<queries>` (kein QUERY_ALL_PACKAGES) – Launcher-Kernfunktion. |
| **REQUEST_DELETE_PACKAGES** | „Deinstallieren“ im App-Menü des Launchers. |
| Akku-Optimierung | In der Play-Version entfernt (nur Link zur Einstellungsliste). |

## 5. Vor dem ersten Upload prüfen

- [x] Name/Paketname gesetzt, Icon passt zum Namen (Ensō, `docs/store/icon-512.png` für den Store), kein Bezug zu „Niagara“
- [ ] Kontakt-E-Mail in Datenschutzerklärung und Store-Eintrag
- [ ] Screenshots (mind. 2) und Feature-Grafik 1024×500
- [ ] Kauf in der internen Testspur getestet, „Wiederherstellen“ getestet
- [ ] Bedienungshilfe-Deklaration inkl. Video eingereicht
