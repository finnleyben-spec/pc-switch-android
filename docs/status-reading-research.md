# PC Switch Android — Status-Auslesung: Analyse & Research

- **Projekt:** `/home/finnley/projects/pc-switch-android` (nativ Kotlin, ~5600 LOC, v2.8.0)
- **Stand:** 2026-11-11, HEAD `1a8e333`
- **Scope:** Status-Auslesung (HA-Entitäten, PC-Erreichbarkeit, WLAN/VPN, UI-Feedback)
- **Reine Analyse — keine Codeänderungen.** Alle Zeilenreferenzen (`Datei:Zeile`) verifiziert gegen den HEAD-Stand.
- **Randbedingungen:** natives Kotlin, keine schweren neuen Dependencies, bestehende Unit-Tests bleiben grün, KEIN UI-Redesign (Gothic Cyber bleibt).

Pfade relativ zu `app/src/main/java/dev/finnley/pcswitch/`.

---

## Sektion 1: IST-Zustand

### Architektur-Überblick (Datenfluss)

```
HA-Server (LAN oder Relay)
    |  OkHttp WebSocket (HaClient.HaSession)
    |  - auth (token)
    |  - get_states (VOLLSTÄNDIG, bei jedem auth_ok)
    |  - subscribe_events "state_changed" (OHNE Entity-Filter)
    |  - Watchdog: get_states alle 5s
    v
HaSession.entityStates (MutableStateFlow<Map<String,String>>)
    |  .collect in viewModelScope (Main)
    v
PcSwitchViewModel.emitEntityStates()
    |  _state.update(UiState) + broadcastTileState(Intent)
    v
MainScreen (Compose, collectAsState)

Parallel, unabhängig:
- probeJob:    HTTP-GET <base>/api/ alle 5s (LAN + Relay)  -> mode (HomeLan/Remote/HomeVpn/Offline)
               + onVpn()-SNAPSHOT (TRANSPORT_VPN)
- pcStatusJob: TCP-Check (Port 22|80, 1.5s Timeout) alle 10s -> pcStatus (reachable/unreachable)
- connectToHome:  OpenVPN-Intent + Tailscale-Broadcast, dann HTTP-Probe alle 3s x 30
- bootPc/shutdownPc: TCP-Check-Loops 2s x 30 / 2s x 15
```

### 1.1 `ha/HaClient.kt` (307 Zeilen)

**Status-Mechanismen:**

| Mechanismus | Wie | Datei:Zeile |
|---|---|---|
| Erreichbarkeits-`probe()` | synchrone HTTP-GET `<base>/api/` (2xx/401/403 = erreichbar), sharedClient: connect 4s + read 8s | `HaClient.kt:33-43`, Client `75-78` |
| WebSocket | OkHttp `newWebSocket`, connectTimeout 4s, readTimeout 0, **pingInterval 15s** (OkHttp-Ping hält Connection alive) | `97-101`, `111-116` |
| Auth | nach `onOpen`: `{"type":"auth","access_token":...}` | `117-120` |
| State-Snapshot | bei jedem `auth_ok`: **`get_states` für ALLE Entitäten** (kein Filter) | `183-187`, `223-231` |
| Event-Abo | `subscribe_events` `state_changed` **ohne Entity-Filter**; Client filtert gegen `entities`-Set | `188-194`, `210-219` |
| Watchdog | **alle 5s** `get_states` + `applyStates` (KDoc behauptet "alle 20s" — veraltet!) | `280-286` (delay `282`), Kommentar `81` |
| applyStates | **kompletter Map-Replace** + `lastUpdate = now` bei JEDEM Abruf, auch ohne Änderung | `288-298` |
| Reconnect | `onFailure`/`onClosed` → `scheduleReconnect`: **fester `delay(2500)`**, Single-Flight via `reconnectJob`, kein Backoff/Jitter | `124-132`, `165-174` (delay `168`) |
| `auth_invalid` | `close(4001)` → `onClosed` → `scheduleReconnect` → **endlose 2.5s-Loop** | `199-203` |
| `press()` | WS-Command `turn_on`/`turn_off`, `pending[reqId]`-Callback, 10s-Timeout | `148-171` |
| `getEntityIds` / `getEntityStates` | `Channel(1)` + **`channel.receive()` OHNE Timeout** | `234-253`, `256-278` (receive `251`, `276`) |
| `isAlive` | `alive`-Flow, `true` bei `onOpen`, `false` bei `onFailure`/`onClosed` | `91`, `119`, `125`, `129` |

**Updates in die UI:** `entityStates` (Flow) wird im ViewModel gesammelt (`PcSwitchViewModel.kt:751-756`) → `emitEntityStates()`. Zusätzlich `lastUpdate`/`alive`/`authFailed`-Flows pro Session (aktive Session = Relay wenn `mode == Remote`, sonst LAN — `PcSwitchViewModel.kt:734-745`).

### 1.2 `pcstatus/PcStatusChecker.kt` (42 Zeilen)

- `object` mit **synchronem** TCP-Check: `tryTcp(ip, 22) || tryTcp(ip, 80)` (`PcStatusChecker.kt:20-23`), `SOCKET_TIMEOUT_MS = 1500` (`17`) → Worst Case 3s pro Check.
- Seit v2.7.4 (ANR-Fix) immer von `Dispatchers.IO` aufgerufen — im ViewModel an 6 Stellen: `PcSwitchViewModel.kt:118-123` (Haupt-Loop), `214`, `257`, `314`, `359`, `543`.
- Ergebnis ist ein bloßer Boolean → `pcStatus` = `"reachable"`/`"unreachable"` (auf Exception `"unknown"`), `PcSwitchViewModel.kt:540-550`.

### 1.3 `vpn/VpnController.kt` (63 Zeilen)

- `connectToHome()` (`22-37`): Tailscale-`DISCONNECT_VPN`-Broadcast (`23`) + OpenVPN-`CONNECT`-Intent mit `AUTOSTART_PROFILE_ID` (`26-31`). **Fire-and-forget — kein Status-Feedback.**
- `disconnectHome()` (`40-52`): OpenVPN-`DISCONNECT`-Intent (`42-47`) + Tailscale-`CONNECT_VPN`-Broadcast (`51`).
- **Keine Status-Auslesungsfähigkeit in der Datei** — ob OpenVPN/Tailscale wirklich verbunden sind, erfährt die App nur indirekt über HTTP-Proben (`PcSwitchViewModel.kt:165-173`).

### 1.4 `state/PcSwitchViewModel.kt` (861 Zeilen)

**UiState-Felder (Status-relevant):** `loading` (`40`), `mode` (`43`), `vpnBusy` (`44`), `switchStates` (`47`), `onState`/`offState` (`49-50`), `lastUpdate` (`51`), `statusNote` (`56`), `pcStatus` (`57`), `pcToggleState` (`59`).

**Orchestrierung (alle festen Intervalle):**

| Loop/Job | Intervall | Datei:Zeile |
|---|---|---|
| `probeJob` (Modus-Erkennung) | **alle 5s**: `probeOnce()` = HTTP-Probe LAN + Relay + `onVpn()` + `emitEntityStates()` | `98-116`, `probeOnce` `812-848` |
| `pcStatusJob` | **alle 10s**: `checkPcStatus()` (TCP) | `118-123`, `540-550` |
| `switchesFlow.collect` | bei JEDEM Emit: `startSessions()` (volles Session-Teardown/Rebuild) | `125-133`, `startSessions` `720-757` |
| `connectToHome` | HTTP-Probe LAN **alle 3s x 30 = 90s** (währenddessen `vpnBusy` → `probeOnce` wird übersprungen, `814`) | `143-182` (Loop `165-173`) |
| `bootPc` | TCP-Check **alle 2s x 30 = 60s** (`isReachable`, `314`) | `307-314` |
| `shutdownPc` | TCP-Check **alle 2s x 15 = 30s** (`!isReachable`, `359`) | `352-359` |
| `saveSetup`/`saveNetworkSettings` | `startSessions()` + `probeOnce()` + `checkPcStatus()` einmalig | `517-536` |

**`probeOnce()` (`812-848`) im Detail:**
1. Skip bei `inSetup || vpnBusy || mode == Connecting` (`814`)
2. `probe(lanBase)` + `probe(relayBase)` (je bis ~12s Latenz möglich, IO-Thread) (`815-816`)
3. `onVpn()` — **Snapshot** via `cm.activeNetwork` + `getNetworkCapabilities(...).hasTransport(TRANSPORT_VPN)` (`817`, `851-860`)
4. `statusNote` bei `authFailed` ("Token prüfen") (`820-821`, `826-827`)
5. Modus-Entscheidung: LAN ok → `HomeLan`; VPN+Relay ok → `Remote`; VPN+LAN ok → `HomeVpn`; sonst `Offline` (`833-838`)
6. `_state.update` + `emitEntityStates()` (`839-847`)

**`emitEntityStates()` (`759-806`):** liest `activeSession`, baut `switchStates` (HA-`stateEntity` hat Vorrang, `768-771`), Change-Detection gegen `previousSwitchStates` (`775-779`), `_state.update` mit `lastUpdate` (`782-789`), Notification bei Switch-Änderung (`792-794`), **`broadcastTileState` bei JEDEM Aufruf** (`796-805`).

**`startSessions()` (`720-757`):** stoppt beide Sessions, setzt `onState`/`offState`/`lastUpdate`/`switchStates` auf `"unknown"`/`0`/empty (`725-732`), erzeugt neue `HaSession`s (LAN + Relay), startet sie, sammelt `entityStates` → `emitEntityStates`.

### 1.5 `ui/MainScreen.kt` (688 Zeilen)

| Element | Mechanik | Datei:Zeile |
|---|---|---|
| Loading-Screen | `state.loading` (2s nach Start, bis erstes Entity-State) | `92`, Set `PcSwitchViewModel.kt:137` |
| Mode-Pill | Farbe/Label nach `mode`: HomeLan/HomeVpn→Ok, Remote/Connecting→Accent, Offline→Err | `146-159` |
| Carousel-Anzeige | `displayState` = PC-Switch && `pcStatus != "unknown"` → **`pcStatus` (TCP)**, sonst `switchStates` (HA) | `168`, `408` |
| Puls-Indikator | `animateFloatAsState` (breathing) | `413-417` |
| `statusNote` (z. B. "Token prüfen") | Text unter dem Carousel | `181-183` |
| VPN-Button | Offline/Remote → `connectToHome`; HomeLan/HomeVpn → `disconnectHome`; `busy = vpnBusy` | `192-202` |
| Boot/Shutdown | `pcToggleState` → "Hochfahren..." / "Herunterfahren..." | `209-210`, `409`, `433` |
| Toggle-Button | enabled nur `mode != Offline && !vpnBusy` | `243` |
| Karten-Animation | scale `animateFloatAsState` | `540`, `576` |

**Wichtig:** `lastUpdate` wird im ViewModel gesetzt (`PcSwitchViewModel.kt:51, 787`), aber **an keiner Stelle in der UI gerendert** (grep-verifiziert: kein Treffer in `ui/`).

### 1.6 Uncommitted Changes (git status — für den Design-Step relevant)

`git diff` (HEAD `1a8e333`, Branch-Status uncommitted):

1. **`app/build.gradle.kts`:** `versionCode` **72 → 74** (`versionName` bleibt `2.8.0`). → Nächstes Release muss **versionCode ≥ 75** verwenden.
2. **`release.sh`:** Build-Tools-Pfad `/home/finnley/android-sdk/build-tools/35.0.0/` → `/home/finnley/.android-sdk/build-tools/34.0.0/` (APKSIGNER, AAPT, ZIPALIGN). Keystore-Pfad/Alias/Passwort unverändert in der Datei (hier nicht wiedergegeben). → Design-/Release-Step muss die **neue SDK-Pfad-Konfiguration beibehalten** (build-tools 34.0.0 unter `~/.android-sdk`).

Beides **nicht wegwerfen**.

### 1.7 Bestehende Unit-Tests

`app/src/test/java/dev/finnley/pcswitch/`: `state/SwitchLogicTest.kt`, `state/SwitchConfigSerializationTest.kt`, `state/SwitchManagerPersistenceTest.kt`, `ui/MainScreenCarouselTest.kt`, `ui/SettingsScreenDragDropTest.kt` — testen reine Dataclasses/Persistence/Carousel-Layout, **kein** HaClient-/ViewModel-Interna. Änderungen an HaClient/ViewModel bleiben grün, solange die Public-API (UiState-Felder, HaSession-Flows) stabil bleibt.

---

## Sektion 2: Schwächen

Format: **was** | **wo** | **warum unflüssig** | **Auswirkung**

### HA-Layer

**W1 — Fester 2.5s-Reconnect ohne Backoff/Jitter**
`HaClient.kt:165-174` (delay `168`)
Fester `delay(2500)` bei jedem Reconnect. Beide Sessions (LAN+Relay) reconnecten synchron im Lockstep; kein exponentieller Backoff, kein Jitter.
Bei längerer HA-Downtime: endlose 2.5s-Retry-Sturme; wenn HA >2.5s zum Hochfahren braucht, prallt jeder Reconnect ab und es geht von vorn los. Kein Reset-Verhalten, kein Cap.
→ Batterie/CPU, Auth-Sturm am HA-Server, verzögerte Rückkehr nach Downtime (User-Ziel 1).

**W2 — Endlose Reconnect-Loop bei `auth_invalid`**
`HaClient.kt:199-203` + `165-174`
`auth_invalid` → `close(4001)` → `onClosed` → `scheduleReconnect` → neu verbinden → `auth_invalid` → … alle 2.5s **für immer**.
Falsches Token = endloser Auth-Storm (LAN + Relay), `probeOnce` zeigt nur die Note "Token prüfen" (`PcSwitchViewModel.kt:826-827`), die Sessions retryen trotzdem.
→ Server-Load, Verwirrung, kein Exit (User-Ziel 1).

**W3 — Watchdog: KDoc veraltet, Vollschnappschuss alle 5s, `lastUpdate` tickt immer**
`HaClient.kt:280-286` (delay `282`), `288-298`, Kommentar `81`
KDoc sagt "alle 20s", Code macht `delay(5_000)`. Jeder 5s-Abruf: `get_states` **aller** Entitäten, kompletter Map-Replace, und `lastUpdate = now` (`297`) — auch wenn sich nichts geändert hat.
(a) Bandbreite + JSON-Parcing (auf dem WS-Listener-Thread) alle 5s; (b) `lastUpdate`-Veränderung → `emitEntityStates` → `_state.update` → **Compose-Recomposition alle 5s im Idle**; (c) Out-of-order-Flicker: ein verzögerter Watchdog-Antwort kann frischere Event-Zustände überschreiben.
→ Unnötiges UI-Churn, möglicher State-Regression-Flicker (User-Ziel 1, 4).

**W4 — `subscribe_events` ohne Entity-Filter**
`HaClient.kt:188-194`, Filter `210-219`
Abo auf **alle** `state_changed`-Events der gesamten HA-Instanz; Client filtert gegen `entities`-Set (`213`).
Bei HA mit Tausenden Entitäten: meiste Bandbreite/CPU für irrelevante Events. Moderne HA bietet `subscribe_entities` mit `entity_ids` (Initial-Snapshot + nur Änderungen, kompakt, 35–90% Datenreduktion) — das HA-Android-Companion nutzt genau das.
→ Bandbreite/CPU, mehr Events als nötig (User-Ziel 1).

**W5 — Kein Coalescing (server- oder clientseitig)**
`HaClient.kt:210-219`; `supported_features` wird nie gesendet; `PcSwitchViewModel.kt:751-756, 782-789, 796-805`
Jedes `state_changed`-Event einer abonnierten Entität → sofort `entityStates.value`-Update → `emitEntityStates` → `_state.update` (neues UiState-Objekt) → Recomposition **plus** `broadcastTileState` (Intent) pro Event.
Burst (Reconnect, HA-Restart, flapping Sensor) = N Recompositions + N Tile-Broadcasts innerhalb von Millisekunden. HA kann serverseitig coalescen (Feature `coalesce_messages: 1` im Feature-Enablement-Phase), die App deklariert es nie.
→ Stottern bei Bursts, redundanter System-IPC (User-Ziel 1, 4).

**W6 — Race Conditions in `HaSession`**
`HaClient.kt:104, 106, 157, 188, 224`
`reqId` ist ein plain `Int`, wird inkrementiert vom Main-Thread (`press`, `157`), WS-Listener-Thread (`188`) und IO-Scope (`224`) — **nicht atomar**. `authed` ist plain `var`, geschrieben auf WS-Thread (`184`), gelesen auf Main (`149`) — kein Visibility-Garantie.
Konkurrente `++reqId` können doppelte IDs liefern → `pending`-Callback des falschen Requests wird aufgerufen → Entity-State-Korruption. Stale `authed`-Read → sporadische `press()`-Fehlschläge nach Auth.
→ Selten, aber reale Datenkorruption / fehlgeschlagene Presses (User-Ziel 1).

**W7 — `startSessions()` bei jedem `switchesFlow`-Emit**
`PcSwitchViewModel.kt:125-133, 720-757`
Volles Teardown (Sessions stoppen, `onState`/`offState`/`lastUpdate`/`switchStates` auf unknown/0/empty zurücksetzen, `725-732`) + Rebuild + Re-Auth + Vollschnappschuss bei jedem Emit — inkl. App-Start und jeder Switch-Edit.
UI zeigt für ~1–3s "unknown" (Pill + Carousel), State wird neu geholt, obwohl sich nichts geändert haben kann.
→ Sichtbarer Flicker bei Start und nach jeder Edit (User-Ziel 1, 4).

**W8 — `getEntityIds`/`getEntityStates`: unbegrenztes blockierendes Receive**
`HaClient.kt:251, 276`
`channel.receive()` ohne Timeout. Verliert der WS die Verbindung nach dem Send (oder die Antwort), hängt der aufrufende Coroutine **für immer**.
`entitySearchLoading` (`PcSwitchViewModel.kt:62`) bleibt true, UI-Search-Dialog bleibt in Loading hängen — kein Recovery ohne App-Neustart.
→ UI-Deadlock (User-Ziel 4).

### PC-Status

**W9 — Zwei Status-Quellen mit unterschiedlicher Latenz (HA-Entity vs. TCP)**
`PcSwitchViewModel.kt:118-123, 768-771`; `MainScreen.kt:168, 408`
`switchStates` nutzt die HA-`stateEntity` (eventgetrieben, aktuell), aber die Carousel-Anzeige des PC-Switches zeigt `pcStatus` (TCP, alle 10s) wenn `!= "unknown"` (`MainScreen.kt:408`).
Die beiden Quellen können gleichzeitig widersprüchlich sein (HA sagt "on", TCP noch "unreachable" nach Boot, oder umgekehrt) → sichtbares Flip-Flop zwischen den Anzeigefeldern.
→ Wahrgenommenes Stottern des PC-Status (User-Ziel 2).

**W10 — TCP-Check: fester 10s, netzwerkunabhängig**
`PcSwitchViewModel.kt:118-121, 540-550`
`isReachable` (TCP 22/80) läuft **alle 10s** — auch wenn das Netzwerk selbst offline ist. Kein adaptives Intervall, kein Trigger auf Netzwerkänderung.
"PC aus" vs. "Netzwerk aus" ist nicht unterscheidbar; Status-Veränderung maximal 10s + 3s (Worst Case TCP) verspätet.
→ Unpräziser, trögender PC-Status (User-Ziel 2).

**W11 — Feste 2s/3s-Intervalle in Transition-Loops**
`PcSwitchViewModel.kt:165-166 (3s×30), 313-314 (2s×30), 358-359 (2s×15)`
Boot/Shutdown/Home-Connect-Loops mit starren Intervallen, keine Adaptivität, kein Event-Trigger.
Reaktion auf den eigentlichen State-Wechsel maximal 2–3s verspätet; bei langsamen Boot (PC braucht >60s) gibt es gar kein Feedback mehr (Loop endet).
→ Grobe Granularität (User-Ziel 2).

### WLAN/VPN

**W12 — `onVpn()`: Snapshot statt Event**
`PcSwitchViewModel.kt:851-860`, nur aufgerufen aus `probeOnce` (`817`), 5s-Takt, bei `vpnBusy`/`Connecting`/`inSetup` **übersprungen** (`814`)
`cm.activeNetwork` + `getNetworkCapabilities` + `hasTransport(TRANSPORT_VPN)` — exakt das Muster, das die Android-Doku als ungeeignet für Production-Code markiert (Capabilities veralten sofort).
VPN-Verbindung/Trennung (OpenVPN up, Tailscale down) wird erst beim nächsten 5s-Probe-Tick erkannt; während `vpnBusy` (90s-Fenster von `connectToHome`) wird `probeOnce` gar nicht ausgeführt → Modus bleibt stundenlang "Connecting".
→ WLAN/VPN-Status bis zu 5s (oder 90s) stale (User-Ziel 3).

**W13 — Kein `ConnectivityManager.NetworkCallback` im ganzen App**
(ganze App — keine einzige Stelle)
WLAN-/VPN-Wechsel werden ausschließlich über die HTTP-Probe-Loop (5s) + TCP-Loop (10s) "entdeckt". Kein `registerDefaultNetworkCallback`, kein `onLosing` (präemptives Signal bevor das Netzwerk weg ist).
Mode-/VPN-/PC-Status-Updats sind rein pollgetrieben und maximal 5–10s stale; bei schnellem Wechsel (WiFi→VPN) fehlt jede unmittelbare Reaktion.
→ Kernproblem für User-Ziel 3.

**W14 — `connectToHome`: 90s blindes Probing, kein VPN-Feedback**
`PcSwitchViewModel.kt:164-173`; `VpnController.kt:22-37`
Nach dem OpenVPN-Intent wird nur per HTTP-Probe (3s×30) erraten, ob das Home-VPN steht. OpenVPN Connect bietet **keine** Status-API für Third-Party-Apps; Tailscale-LocalAPI ist auf Android nur in-App erreichbar.
Modus flippt erst, wenn die HTTP-Probe durchkommt; bei langsamem VPN-Aufbau (DNS, Route) bleibt "Connecting" unnötig lange.
→ Grober, unzuverlässiger VPN-Status (User-Ziel 3).

### UI

**W15 — `lastUpdate` wird berechnet, aber nie angezeigt**
`PcSwitchViewModel.kt:51, 787`; `MainScreen.kt`: kein Treffer (grep-verifiziert)
`UiState.lastUpdate` tickt (aufgrund W3 sogar alle 5s), aber kein UI-Element rendert "zuletzt aktualisiert".
User-Ziel 4 ("Ladezustände + zuletzt-aktualisiert-Feedback verbessern") ist heute nicht erfüllt — die Daten existieren schon, die Anzeige fehlt.
→ Kein Freshness-Feedback.

**W16 — `broadcastTileState` bei jedem `emitEntityStates`-Aufruf**
`PcSwitchViewModel.kt:796-805` (aufgerufen `847` pro Probe + `752/755` pro Event)
Intent-Broadcast geht bei jedem Probe-Tick (5s) und jedem HA-Event raus — nicht nur bei tatsächlicher Änderung.
Redundanter System-IPC, Batterielast, Latenz-Rauschen.
→ Unnötiger Overhead (User-Ziel 4).

---

## Sektion 3: Best Practices (mit Quellen)

### 3.1 Home-Assistant-WebSocket-API

**a) Offizielle WS-API:** Auth-Phase, `get_states`, `subscribe_events`, **Feature-Enablement-Phase** (Client kann `supported_features` deklariern, u. a. `coalesce_messages: 1` → HA batcht Events serverseitig), ping/pong.
https://developers.home-assistant.io/docs/api/websocket/

**b) `subscribe_entities` (modern, effizienter):** „To follow state, `subscribe_entities` is more efficient. It sends all the states the user has permission to read first, then sends only the changes in a compact format. Pass `entity_ids` to limit the entities." — liefert Initial-Snapshot **und** nur noch Änderungen, optional mit `entity_ids`-Filter; ersetzt damit `get_states` + `subscribe_events` + Watchdog.
https://contributors.home-assistant.io/core/internals/websocket-api/

**c) Einführung & Datenreduktion:** HA-Core-PR #67891 führt `subscribe_entities` ein (35–90% Datenreduktion je nach Entitäten-Mix); Version: seit HA 2023.10. Das HA-Android-Companion nutzt es bereits.
https://github.com/home-assistant/core/pull/67891
https://community.home-assistant.io/t/terrible-performance-on-seemingly-most-android-tablets/760318

**d) Quellsicht (2026.10.1):** `handle_subscribe_entities` — `entity_ids` optional, Initial-Snapshot + State-Diffs, `INCLUDE_EXCLUDE`-Filter.
https://github.com/home-assistant/core/blob/2026.10.1/homeassistant/components/websocket_api/commands.py

**e) Resubscribe-Semantik (wichtig für Reconnects!):** Das erste Payload jedes Subscribe-Zyklus ist **authoritative** → Store **ersetzen**, nicht mergen (js-websocket PR #773, Sept 2026 — fixte genau dieses Merge-bei-Resubscribe-Problem).
https://github.com/home-assistant/home-assistant-js-websocket/pull/773

### 3.2 WebSocket-Reconnection (exponentieller Backoff + Jitter)

**a) Exponentieller Backoff + Jitter:** Basis ~0.5–1s, Faktor ×2, Cap ~30s, Jitter (z. B. full jitter) gegen Thundering-Herd.
https://websocket.org/guides/reconnection/

**b) Backoff-Reset erst nach stabiler Verbindung; Close-Codes respektieren:** `auth_invalid`/4001 = **kein** Retry (Konfigurationsfehler, kein Transient), 1006/1011 = Retry mit Backoff. Reset des Backoffs nach erfolgreichem Auth + stabilen Session.
https://nownodes.io/blog/websocket-best-practices-for-production-reconnects-heartbeats-and-missed-events/
https://github.com/martinholovsky/SOTA-skills/blob/main/skills/sota-api-design/rules/05-realtime-websockets-sse.md

### 3.3 Android ConnectivityManager (eventgetriebener Netzwerkstatus)

**a) `registerDefaultNetworkCallback` / `registerNetworkCallback`:** `onAvailable`, `onLost`, `onLosing` (präemptiv!), `onCapabilitiesChanged` — das empfohlene Muster für Production. `NetworkRequest` mit `NET_CAPABILITY_NOT_VPN` für VPN-only-Erkennung.
https://developer.android.com/develop/connectivity/network-ops/reading-network-state

**b) `NetworkCallback`-Referenz:** `registerNetworkCallback(NetworkRequest)`, `onLosing(network, millisUntilLost)`, `NET_CAPABILITY_NOT_VPN`.
https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback

**c) VPN & Transports:** Android-VPNs (TUN, L3) können **mehrere Transports** haben (z. B. VPN-over-WiFi + VPN-over-Mobile) → `TRANSPORT_VPN`-Check auf dem `activeNetwork` ist nötig, aber unvollständig; `NetworkCapabilities`-Snapshot via `getNetworkCapabilities` ist laut Doku **nicht** für Production (veraltet sofort) → Callbacks nutzen.
https://developer.android.com/reference/android/net/NetworkCapabilities
https://developer.android.com/develop/connectivity/vpn

### 3.4 Tailscale / OpenVPN Status-Erkennung auf Android

**a) Tailscale: keine Third-Party-Status-API.** Kein CLI auf Android/iOS; LocalAPI-Socket (`tailscaled.sock`) ist nur in der Tailscale-App erreichbar.
https://tailscale.com/docs/reference/tailscale-cli
https://forum.tailscale.com/t/how-to-discover-other-tailscale-devices-on-android-and-ios/1139

**b) Praktische Dritt-App-Erkennung für Tailscale:** `TRANSPORT_VPN` via ConnectivityManager (primär) + **TCP-Probe an ein bekanntes CGNAT-Peer** (Tailscale-Range `100.64.0.0/10`) als sekundäres "Tailscale steht"-Signal.

**c) OpenVPN Connect: keine öffentliche Status-API** für Third-Party-Apps. Android-VPN = TUN via `VpnService` → Erkennung ausschließlich über `TRANSPORT_VPN` (ConnectivityManager-Callbacks). Die CONNECT/DISCONNECT-Intents (die die App schon nutzt, `VpnController.kt:22-52`) sind der unterstützte Steuer-Kanal.

---

## Sektion 4: Empfehlungen (priorisiert, auf die 4 User-Ziele gemappt)

| # | Empfehlung | Priority | User-Ziel | Beseitigt |
|---|---|---|---|---|
| R1 | `subscribe_entities` mit `entity_ids` (Initial-Snapshot ersetzt `get_states` + `subscribe_events`) | **P0** | 1 | W3, W4 |
| R2 | Exponentieller Backoff + Jitter für Reconnect (1s → 30s Cap, full jitter), Reset bei `auth_ok` | **P0** | 1 | W1 |
| R3 | `auth_invalid` = **kein** Retry (Backoff-Stop), `authFailed`-UI zeigen | **P0** | 1 | W2 |
| R4 | `ConnectivityManager.NetworkCallback` (`registerDefaultNetworkCallback`) → sofortiges `probeOnce` + `onVpn`-Recompute bei `onAvailable`/`onLost`/`onCapabilitiesChanged` | **P0** | 3 | W12, W13 |
| R5 | State-Coalescing im ViewModel: `_state.update` nur bei tatsächlicher Map-Änderung; `broadcastTileState` nur bei Change | **P0** | 1, 4 | W5, W16 |
| R6 | HA-Feature `coalesce_messages: 1` im WS-Feature-Enablement deklaren | **P0** | 1 | W5 |
| R7 | PC-Status: HA-`stateEntity` als **primäre** Anzeige (TCP nur sekundär/Validierung) | **P1** | 2 | W9 |
| R8 | Adaptives TCP-Intervall: 3s während Transition/Boot, 15–30s im Steady-State; Skip bei `mode == Offline` | **P1** | 2 | W10, W11 |
| R9 | Transition-Loops (boot/shutdown/home) auf HA-Entity-Events umstellen (wenn `stateEntity` gesetzt), TCP nur als Fallback | **P1** | 2, 3 | W11, W14 |
| R10 | `getEntityIds`/`getEntityStates`: `withTimeout(8_000)` statt unbegrenztem `receive()` | **P1** | 4 | W8 |
| R11 | `reqId` → `AtomicInteger`, `authed` → `@Volatile` | **P1** | 1 | W6 |
| R12 | `startSessions()` nur bei **tatsächlicher** Entity-Änderung (Diff alt vs. neu), nicht bei jedem `switchesFlow`-Emit | **P2** | 1, 4 | W7 |
| R13 | `lastUpdate` in MainScreen rendern ("zuletzt aktualisiert: Xs") — Daten fließen schon | **P2** | 4 | W15 |
| R14 | Modus-Hysterese: `mode`-Wechsel erst nach 2 übereinstimmenden Probes (kein Flicker bei Flapping) | **P2** | 3 | W12 |
| R15 | Optionales Tailscale-Signal: TCP-Probe an CGNAT-Peer (`100.64.0.0/10`) als sekundären "Tailscale steht"-Check | **P2** | 3 | W14 |

### Detail-Notizen zu den P0-Empfehlungen

**R1 — `subscribe_entities`:** `HaSession` ersetzt `get_states`+`subscribe_events` durch ein einzelnes `subscribe_entities` mit `entity_ids` (die `entities`-Liste, die ohnehin da ist). Das **erste** Payload jedes Zyklus **ersetzt** den lokalen Store (nicht mergen — s. js-websocket PR #773). Watchdog: von 5s Vollschnappschuss auf **60s Drift-Check** reduzieren (nur wenn `lastEvent` > 60s alt ist). Fallback für HA < 2023.10: bestehenden `subscribe_events` + `get_states`-Pfad behalten (Version via `get_states`-Antwort oder Config-Check). **Keine neue Dependency.**

**R2 — Backoff+Jitter:** `delay = min(cap, base * 2^attempt)` + `random(0..delay)` (full jitter); `base = 1_000`, `cap = 30_000`; Reset bei `auth_ok`. Beides rein im `HaSession.scheduleReconnect` — keine API-Änderung.

**R4 — NetworkCallback:** Im ViewModel (oder `VpnController`) ein `registerDefaultNetworkCallback` mit `onAvailable`/`onLost`/`onCapabilitiesChanged` → sofort `probeOnce()` + `onVpn()`-Recompute (außer bei `vpnBusy`). `onLosing` kann präemptiv die UI auf "Verbindung wird getrennt" setzen. Ersetzt das 5s-Polling für den **Modus** (HTTP-Probe bleibt als Reachability-Check, wird aber eventgetrieben angestoßen).

**R5+R6 — Coalescing:** In `emitEntityStates`: `if (newStates == oldStates) return` vor `_state.update`. `broadcastTileState` nur wenn `currentSwitchState` sich geändert hat. Zusätzlich `coalesce_messages: 1` im WS-Handshake (Feature-Enablement) — HA batcht dann Events serverseitig.

### Test-Impact

Bestehende Tests (`SwitchLogicTest`, `SwitchConfigSerializationTest`, `SwitchManagerPersistenceTest`, `MainScreenCarouselTest`, `SettingsScreenDragDropTest`) testen reine Dataclasses/Persistence/Layout — **keiner** berührt HaClient-/ViewModel-Interna. Alle P0-Empfehlungen halten die Public-API (`UiState`-Felder, `HaSession`-Flows) stabil → Tests bleiben grün. Neue Backoff/Coalescing-Logik ist mit injizierbarem Clock/Dispatcher unit-testbar (stdlib + bestehende Dependencies, keine neuen).

### Uncommitted-Changes für den Design-Step

- `app/build.gradle.kts`: `versionCode` = 74 (nächste Release ≥ 75)
- `release.sh`: Build-Tools-Pfad jetzt `~/.android-sdk/build-tools/34.0.0/` — beibehalten, nicht zurücksetzen.
