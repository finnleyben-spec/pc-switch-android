# PC Switch (Android)

Native Android-App zur Fernsteuerung deines PCs über **Home Assistant**:
einen Power-On-Button in HA drücken → PC startet; Power-Off → PC fährt herunter.
Die App zeigt den Live-Status (an/aus, via HA oder direkt per TCP-Check) und
kann per Notification informieren.

## Funktionen

- **Zwei Betriebsmodi**
  - *LAN*: direkt in deinem Heimnetz (http(s)://HA:8123)
  - *Remote*: über einen Relay-Server, z. B. Tailscale-Adresse (wird automatisch bevorzugt, falls konfiguriert)
- **Automatische HA-Erkennung** per mDNS (Suche auf `_homeassistant._tcp`) plus manuelle Adress-Eingabe mit Validierung
- **Verbindungstest** vor dem Speichern (Token wird per REST gegen `/api/` geprüft)
- **PC-Status** live in der App; optional direkt per TCP (Port 22/80) unabhängig von HA
- **Benachrichtigungen** (alle Ereignisse / nur Boot / nur Shutdown)
- **Self-Update**: APK wird direkt heruntergeladen und installiert (gleiche Signatur)
- **Backup**: Einstellungen exportieren/importieren

## Sicherheit

- Token wird **verschlüsselt** gespeichert (EncryptedSharedPreferences / Keystore-Hardware, falls verfügbar)
- **LAN**: `http://` erlaubt (übliche HA-Heimnetz-Configs) — nur für nicht-öffentliche Adressen (IPv4-Private/Loopback/`.local`)
- **Öffentliche/Remote-Hosts**: ausschließlich `https://` (System-CA oder selbst importiertes CA-Zertifikat)
- Kein Tracking, keine Telemetrie, keine Cloud-Abhängigkeit (Self-Update除外: eigener Server)

## Einrichtung

1. Home Assistant: **Einstellungen → Benutzer → API-Tokens** → Long-Lived Access Token erstellen
2. In HA einen **Power-On-Button** (z. B. `button.pc_wake` mit WOL-Command) und optional einen **Power-Off-Button** anlegen
3. App öffnen → Setup: Adresse (oder Auto-Suche), Token, Entity-IDs → *Verbindung testen* → speichern
4. Optional: Relay-Adresse (Remote), PC-IP (direkter Status-Check), Benachrichtigungen

## Build

- JDK 17, Android SDK 36
- Debug: `./gradlew assembleDebug`
- Release: `./gradlew assembleRelease`
  - Signierung: lokal `keystore.properties` (Template: `keystore.properties.example`) + Keystore im Repo-Root.
    Ohne lokale Signatur-Daten wird mit Debug-Signing gebaut (geeignet für Play-Upload mit Play App Signing).
  - **Kein** Signing-Material gehört ins Repository.
- Tests: `./gradlew testDebugUnitTest` (128 Unit-Tests)

## Lizenz

[GNU GPL v3](LICENSE)
