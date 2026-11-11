# E2E-Smoke-Checkliste (S11) — PC Switch Android

App: `dev.finnley.pcswitch` v2.8.0 (build aus `app-debug.apk`), echtes Gerät,
HA-Backend erreichbar, PC erreichbar. Vorher: `adb logcat -c` leeren, App neu starten.

Vorbereitung (einmalig):
- `adb logcat -c` — Log leeren
- HA: Developer Tools → States (https://<ha>/developer-tool/states) öffnen
- HA: Developer Tools → Events → `state_changed` beobachten (optional)
- Android Studio → Profiler → Compose-Tab (für Punkt 1/6) öffnen
- Stoppuhr bereit

---

## 1. Idle 10 min — kein Reconnect-Storm, UI still

Schritte:
1. App öffnen, auf den Startbildschirm stellen, 10 min nicht antasten.
2. HA Developer Tools → States: WebSocket-Monitoring (Browser-DevTools → Network → WS
   oder HA-Log) — zählen, wie oft `get_states` kommt.
3. Parallel: `adb logcat | grep -iE "(get_states|subscribe|mode)"`
4. Compose-Profiler (Android Studio): Recompositions pro Minute ablesen.

Messung: HA-Log / WS-Traffic, Logcat-Filter, Compose-Profiler.

Erwartet:
- HA: ≤ 1 `get_states`/Min (vorher: 5-s-`get_states`-Sturm ≈ 12/min)
- `subscribe_entities` genau 1× pro Session (kein Re-Subscribe-Loop)
- Compose-Profiler: < 10 Recompositions/min (vorher: Tick-Flut)
- UI: nur "zuletzt aktualisiert" tickt, Rest still

[ ] Bestanden

## 2. VPN connect → HomeVpn in <3 s

Schritte:
1. VPN (z. B. WireGuard/Tailscale) am Gerät aufbauen.
2. Stoppuhr starten beim VPN-Up, stoppen wenn App `HomeVpn`/LAN-Modus zeigt.

Messung: Stoppuhr + Logcat-Filter `grep -i mode` (Mode-Wechsel-Logzeilen).

Erwartet:
- Mode `HomeVpn` in < 3 s (vorher: bis 90 s — 5-s-Probe + 3 GETs pro `onVpn()`)
- Kein Fehlschlag/Retry-Burst (Logcat zeigt eine saubere Transition)

[ ] Bestanden

## 3. Airplane-Mode → Offline <3 s, zurück <3 s

Schritte:
1. Flugmodus einschalten → Stoppuhr bis App `Offline` zeigt.
2. Flugmodus ausschalten → Stoppuhr bis App wieder `HomeVpn`/LAN zeigt.

Messung: Stoppuhr + Logcat-Filter `grep -iE "(mode|watchdog)"`.

Erwartet:
- `Offline` in < 3 s (vorher: erst nach nächster 5-s-Probe, teils > 10 s)
- Rückkehr < 3 s, ohne Crash/Reconnect-Loop (Logcat: 1 Probe, 1 Transition)
- HA-Session wird sauber geschlossen und neu aufgebaut (kein Zombie-WS)

[ ] Bestanden

## 4. PC boot/shutdown mit stateEntity — kein Flip

Schritte:
1. PC-Entity in HA setzen (z. B. via HA UI den `stateEntity` auf `on`/`off`
   oder echten Boot/Shutdown des PCs).
2. App-UI beobachten: Status auf der PC-Karte + Carousel-Zeile + Button-Label.
3. Bei Boot: Status bleibt `unknown` → `on` genau einmal (kein on→off→on-Flip).
4. Bei Shutdown: `off` stabil, Transition "fertig" erscheint.

Messung: Auge + `adb logcat | grep -iE "(pcStatus|transition)"`, HA-States
(Entität wechselt einmal, kein Ping-Pong).

Erwartet:
- Status wechselt ohne Flip (vorher: TCP+HA gemischt → Flackern)
- Karte, Carousel-Zeile und Button zeigen immer identischen Status
- Transition (Boot/Shutdown) läuft sichtbar durch, endet sauber

[ ] Bestanden

## 5. Switch-Name editieren — kein "unknown"-Flickern

Schritte:
1. Settings → Switch auswählen → Namen ändern → speichern.
2. Startbildschirm direkt danach beobachten (5–10 s).

Messung: Auge; Logcat-Filter `grep -i unknown` sollte leer bleiben;
`refreshing`-Dimming darf sichtbar sein (erwartet).

Erwartet:
- Status bleibt während des Neuladens erhalten (kein `unknown`-Flickern)
- Weiches Dimming (`refreshing`), danach neuer Name, alter Status
- (vorher: Entity-Set-Neuabonnieren → Status kurz `unknown` → flackert)

[ ] Bestanden

## 6. "Zuletzt aktualisiert" tickt — kein Layout-Shift, kein Hitch

Schritte:
1. Startbildschirm öffnen, 30 s in Android Studio Profiler (Compose) aufnehmen.
2. Zeitstempel (Statuszeile + PC-Karte) beobachten: tickt ~alle 15 s
   ("vor X min" / "gerade eben").
3. Prüfen, ob sich beim Ticken Layout verschiebt oder der UI "hakt".

Messung: Compose-Profiler 30 s (Recompositions, Frames), Auge (Shift/Hitch).

Erwartet:
- Zeitstempel tickt alle 15 s (relatives Format), Werte aktualisieren
- Kein Layout-Shift beim Ticken (vorher: Textbreiten-Wechsel → Ruckeln)
- Compose-Profiler: Recomposition nur vom Zeitstempel, < 10/min insgesamt,
  keine Dropped Frames

[ ] Bestanden

## 7. QuickSettings-Tile spiegelt nur bei echter Änderung

Schritte:
1. Tile `PC Switch` in QuickSettings ziehen.
2. `adb logcat | grep -iE "(tile|pcStatus)"` öffnen.
3. 2–3 min Idle beobachten → Tile darf sich NICHT verändern.
4. PC-Status ändern (Punkt 4) → Tile aktualisiert sich genau einmal.

Messung: Logcat (Tile-Update-Logs), Auge (Tile-Farbe/Status).

Erwartet:
- Idle: keine Tile-Updates (vorher: 5-s-Zyklus → Tile pulsiert ständig)
- Bei echter Statusänderung: genau 1 Tile-Update, Status korrekt gespiegelt

[ ] Bestanden

---

Abgeschlossen: ______ 7/7 → S11 grün, S12 (Version-Bump) freigegeben.
Auffälligkeiten / Fehlschläge:

- 
