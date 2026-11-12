# E2E-Testbericht PC-Switch v2.9.0 (versionCode 75) — 2026-11-11

**Umgebung:** Android-Emulator (AVD `pcswitch`, 1080×2400, 480dpi), App
`dev.finnley.pcswitch` v2.9.0, HA `192.168.0.205:8123` (LAN, Token aus
`astra-ha-token`), Test-Entities:

- `input_button.pc_switch_test_on`
- `input_button.pc_switch_test_off`
- `input_boolean.test_wallbox_switch`

**Keine Codeänderungen in dieser Runde** — nur Testing + Doku.
Echtes PC-Power-Entity (`input_button.pc_ausschalten`) wurde NICHT
angestellt (Security-Audit: nur via manueller Deep-Link erreichbar).

---

## S11-Checkliste — Ergebnis

| # | Test | Status | Anmerkung |
|---|------|--------|-----------|
| 1 | Idle 10 min | ✅ bestanden | Kein Reconnect-Storm (PCAP: App sendet KEINE SYN-Pakete, persistenter WS + 15-s-Ping). Kein Crash/FATAL in logcat. Hinweis: ~1×SYN/2.5 s + 15-s-Bursts auf HA:8123 kommen vom **Host**, nicht von der App |
| 2 | VPN < 3 s | ⬜ nicht getestet | Kein VPN im Emulator-Setup |
| 3 | Airplane-Mode | ⬜ nicht getestet | nicht durchgeführt |
| 4 | PC boot/shutdown | ⚠️ teilweise | ECHTER PC-Test nicht möglich (Agent läuft auf dem PC → Shutdown = Session-Ende). Wallbox-Äquivalent getestet: Slider ON→Primary-Button, OFF→Secondary-Button, StateEntity-Sync via WS (s. Bug B4 für das Sync-Verhalten) |
| 5 | Switch editieren | ✅ bestanden | Edit-Dialog: alle Felder (inkl. stateEntity, secondaryEntity) werden korrekt persistiert. Kein Unknown-Flickern beobachtet |
| 6 | "Zuletzt aktualisiert" tickt | ✅ bestanden | Relative Timestamps ("gerade eben", "vor 15 s") ticken; kein Layout-Shift sichtbar |
| 7 | QS-Tile | ✅ sichtbar | Tile "PC-Switch" in QuickSettings vorhanden. Idle-Update-Verhalten nicht logcat-verifizierbar (fehlende Logs, s. B6) |

Zusätzlich getestet (außerhalb S11):

| Test | Status | Anmerkung |
|------|--------|-----------|
| Cold Start (force-stop → start) | ✅ | Stabil, kein Crash; Status korrekt ("Aus · gerade eben" nach Restart) |
| Switch anlegen (Create-Dialog) | ⚠️ | Name/Primary/Secondary korrekt, **stateEntity geht verloren** → Bug B2 |
| Switch löschen | ✅ | Funktioniert, aber **ohne Bestätigungsdialog** → B5 |
| Slider Toggle ON/OFF | ✅ | Drag-only (B4); ON/OFF triggern korrekte Buttons, HA-State verifiziert |
| DataStore-Korruption | ⚠️ | Manipulierte `.preferences_pb` → App-Crash beim Start → B6 |

---

## Bugs

### B1 — TopAppBar ohne Status-Bar-Inset (hohe Priorität)

**Symptom:** Back- und Add-Button in der TopAppBar sind praktisch nicht bedienbar.
Die Buttons liegen bei y 64–190 px; die System-Statusleiste belegt
`[0,0]–[1080,136]` (+ Geste-Zone bis y 168). Die Button-Glyphen (y 105–148)
sind also **unter der Statusleiste** — Taps davor werden vom System
geschluckt. Nur der schmale Streifen y 168–190 reagiert (verifiziert:
Back bei y=180 navigiert, "+" bei y=180 öffnet den Dialog; y ≤ 168 tot).

**Repro:** Switches-Seite öffnen, Back-Pfeil tippen (jeder Punkt im sichtbaren
Icon) → nichts. Hardware-Back funktioniert (kein Touch).

**Ursache (Code):** `SwitchesScreen.kt` / `SettingsScreen`-Header zeichnen die
TopAppBar ohne `systemBarsPadding()` / Edge-to-Edge-Insets; der Inhalt startet
ab y≈64 px statt unterhalb der Statusleiste.

**Fix-Idee:** `.systemBarsPadding()` auf die Content-Column (oder
`WindowInsets`-Padding auf die TopAppBar). Betrifft alle Screens mit TopAppBar.

### B2 — Create-Dialog: stateEntity wird nicht gespeichert (hohe Priorität)

**Symptom:** Im "Switch hinzufügen"-Dialog wird das State-Entity-Feld
angenommen, aber beim Speichern verworfen. DataStore nach Save:

```json
{"name":"B1Repro","primaryEntity":"input_button.pc_switch_test_on",
 "secondaryEntity":"input_button.pc_switch_test_off","stateEntity":""}
```

`secondaryEntity` wird dagegen korrekt gespeichert (frühere Annahme
"Secondary geht verloren" war ein Proto-Parsing-Artifakt — s. B7).

**Ursache (Code):** `SwitchesScreen.kt` → `onAddSwitch` übergeht
`stateEntity` beim Aufruf der Save-Logik (nur Name, Type, Primary, Secondary).
Der Edit-Flow übergibt alle Felder (deshalb funktioniert Edit).

**Fix-Idee:** `stateEntity` im Create-Flow wie im Edit-Flow übergeben.

### B3 — (früher "ISO-Timestamp" — NICHT reproduzierbar)

Vorherige Session meldete ISO-Timestamps neben dem Switch-Namen in der Liste.
Aktuelle Prüfung (a11y-Dump + Screenshot, Vision-Verifikation): **kein
Timestamp sichtbar**, Liste zeigt nur Name / Entity / Badge. Vermutlich
Zustandsabhängig oder bereits gefixt (S10: relativeTime). → Als offen stehen
lassen, falls es auf einem echten Gerät noch auftritt.

**Status 2026-11-12: GEFIXT.** Root Cause: `input_button`-Entities ohne
`stateEntity` liefern als State eine rohe ISO-Timestamp → `switchStateLabel`
zeigte sie im Status-Label (Repro: TestB4, uiautomator-Dump „Unbekannt"
vorher unsichtbar, Label zeigte Timestamp). Fix Commit `6561156`:
`buildSnapshot` normalisiert jeden State ≠ on/off zu `unknown`
(„Unbekannt"). +2 Unit-Tests. Device-Re-Check mit neuem APK ausstehend
(siehe `e2e-open-items-2026-11-12.md`, Punkt 2).

### B4 — State-Sync-Lücke ~15 s: OFF-Action kurz nach ON schlägt fehl (mittel)

**Symptom:** Slider nach rechts (ON) triggert den Primary-Button, HA-State
wechseln auf `on`. Unmittelbar danach Slider nach links (OFF): **nichts
passiert**, obwohl die Logik OFF bei State `on` den Secondary-Button triggern
sollte. Erst nach ~15 s (WS-State-Arrival / State-Refresh) klappt OFF.

**Ursache (Verdacht):** Die App handelt auf dem **lokalen** State (noch `off`),
nicht auf dem HA-Fakt. Der lokale State hinkt dem WS-Push um die
Refresh-Zyklen hinterher. Für den echten PC (EIN/`AUS`) ist das relevant:
schnelles An-Aus könnte nicht triggern.

**Fix-Idee:** State-Optimistic-Update oder direct WS-State-Priorität bei
Actions.

**Status 2026-11-12: ROOT CAUSE KORRIGIERT + CODE-FIX FERTIG.** Der Verdacht
(lokaler State-Hinterherschlurf) war falsch — Logcat-Nachweis: Beim State-Flip
(optimistic/WS) schrieb `LaunchedEffect(isOn)` per Spring in `animProgress` und
übersteuerte den laufenden OFF-Drag (~400 ms); das Crossing wurde vom
manipulierten animierten Wert entschieden und fiel hinter den Drag-Ende.
Zusätzlich restartete der `pointerInput`-Key `isOn` den Detektor mid-Drag.
Fix Commit `29011a7`: fingerbasierte Crossing-Entscheidung, `isOn` als
State-Holder (kein Key), `isDragging` stoppt die Sync-Animation.
Device-Verifikation (ON→OFF bei 0.2 s Pause) ausstehend —
`e2e-open-items-2026-11-12.md`, Punkt 1.

### B5 — Löschen ohne Bestätigung (niedrig, UX)

Das Löschen-Icon in der Switch-Liste entfernt sofort und ohne
Bestätigungsdialog. Tippfehler-/Fehltipp-Risiko. → Confirm-Dialog ergänzen.

### B6 — Fehlende Logs in den neuen Komponenten (mittel, Testbarkeit)

`NetworkWatchdog`, `HaClient` (WS-Connect/Reconnect), `PcStatusResolver`,
`SwitchManager` loggen (kaum) nichts über `Log.*`. Logcat ist für Debugging
nichts tauglich (alle "FATAL"-Treffer waren uiautomator-Noise). → Log-Level
`D`/`W` an den kritischen Punkten (Connect, Reconnect, Mode-Wechsel,
Resolve-Entscheidung, Save/Load Switches).

### B7 — DataStore-Crash bei korrupter Datei (niedrig, Robustness)

Manuell manipuliertes `files/datastore/switches.preferences_pb`
(inkorrektes Proto-Format) → **App-Crash beim Start** (Stacktrace in
`crash_t3.txt`, Scratch). Kein Fallback, kein Log. → Datei validieren /
bei Fehler zurücksetzen + Toast. (Normalbetrieb: DataStore 1.1.1 stabil.)

**Hinweis zur DataStore-Struktur (für künftige POCs):**
`Preferences → MapEntry(key=field 1, value=field 2)`, und das Value-Feld
enthält selbst ein Proto, in dem der JSON-String in **Field 5** liegt
(nicht Field 1/2). Frühere manuelle Injections sind an genau dieser Stelle
kaputtgegangen.

---

## Nicht getestet / nicht möglich in dieser Runde

- **PcStatusResolver TCP-Fallback** (HA unreachable → Port 22): würde HA-
  Ausfall erfordern — nicht durchgeführt (Risk für andere LAN-Tests).
- **Echter PC-ON/OFF-Zyklus**: Agent läuft auf dem PC → kein Shutdown möglich.
  Nur über Wallbox-Entities nachvollzogen (B4 zeigt das Sync-Verhalten).
- **Exakter 15-s-Latenzwert**: qualitativ beobachtet, nicht gemessen
  (keine Logs, s. B6).
- **Recompositions/Frame-Profiling** (Checkliste 1/6): kein Android Studio
  Profiler im Emulator-Setup; qualitativ: UI still, kein sichtbarer Hitch.

## Verifiziert & i.O.

- Security: echte PC-Entities nur via manueller Deep-Link; UI nutzt
  `onEntity`/`offEntity` aus der Konfig (Test-Entities).
- Token-Handling: kein Token-Banner, HA-Auth OK.
- Persistenter HA-WebSocket mit 15-s-Ping (PCAP + Code bestätigt).
- QS-Tile vorhanden.
- Cold Start stabil.
- Edit-Dialog persistiert alle Felder korrekt.
- Delete persistiert korrekt (DataStore nach Löschen verifiziert).
