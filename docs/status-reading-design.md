# Design: Status-Auslesung — PC Switch Android-App

**Datum:** 2026-11-11
**Basis:** `docs/status-reading-research.md` (IST-Analyse, W1–W16, R1–R15)
**Stand des Codes:** HEAD `1a8e333` (v2.8.0, 861 Zeilen `PcSwitchViewModel.kt`)
**Typ:** Reines Design — KEINE Codeänderungen durch dieses Dokument.
**Uncommitted (beibehalten, nicht anrühren):** `app/build.gradle.kts` versionCode 72→74; `release.sh` → `~/.android-sdk/build-tools/34.0.0/`.

Pfade relativ zu `app/src/main/java/dev/finnley/pcswitch/`.

---

## Sektion 0: Scope-Entscheidung

### 0.1 Die vier User-Ziele

| # | Ziel | Messbar |
|---|------|---------|
| Z1 | **Flüssig** | Keine Recomposition-Stürme, kein "unknown"-Flickern bei Switch-Edits, keine Reconnect-Storms; Idle-UI still |
| Z2 | **Robust** | Verbindung stellt sich zuverlässig neu auf (Backoff, Watchdog), kein auth_invalid-Loop, kein UI-Deadlock, kein 90s-Blindfenster |
| Z3 | **Präzise** | Ein einziger konsistenter PC-Status (kein TCP/HA-Flip-Flop), WLAN/VPN ohne Snapshot-Blindperioden |
| Z4 | **Transparent** | "zuletzt aktualisiert" + Ladezustände sichtbar (Daten sind da, werden nur nie gerendert), Gothic-Cyber-Design unverändert |

### 0.2 Scope-Entscheidung: R1–R15

| # | Prio | Empfehlung | In? | Begründung |
|---|------|-----------|-----|------------|
| R1 | P0 | `subscribe_entities` mit `entity_ids` | **JA** | Fundament: serverseitiger Filter + eventgetrieben; Voraussetzung für R5/R6; halbiert die Idle-Last (aktuell: `get_states` alle **5 s**, Watchdog `HaClient.kt:280-285`, Kommentar "20s" ist veraltet). |
| R2 | P0 | Reconnect mit exp. Backoff + Jitter (1s→30s) | **JA** | User-Vorgabe, Z2. Aktuell: fester `delay(2500)` (`HaClient.kt:165-173`). |
| R3 | P0 | `auth_invalid` → kein Retry | **JA** | User-Vorgabe, Z2. Aktuell: `w.close(4001)` (`HaClient.kt:199-202`) → `onClosed` → `scheduleReconnect()` → **endloser 2.5s-Auth-Loop** (W3). |
| R4 | P0 | `ConnectivityManager.NetworkCallback` | **JA** | User-Vorgabe, Z2/Z3. Ersetzt 5s-Poll + 90s-Blindperiod. Permission `ACCESS_NETWORK_STATE` **bereits vorhanden** (Manifest, Kommentar "VPN-Transport-Erkennung"). Keine neue Permission. |
| R5 | P0 | State-Coalescing im ViewModel | **JA** | User-Vorgabe, Z1. Aktuell: jeder Entity-Event → `_state.update` + Tile-Broadcast (W7). |
| R6 | P0 | `coalesce_messages: 1` | **JA** | Serverseitige Hälfte von R5, 1 JSON-Feld, Zero-Cost. |
| R7 | P1 | HA-Entity als primäre Status-Quelle | **JA** | User-Vorgabe ("ein einziger, konsistenter pcStatus"), Z3. Aktuell: `checkPcStatus()` (`PcSwitchViewModel.kt`) setzt rohe TCP-Werte `"reachable"/"unreachable"/"unknown"`; Carousel (`MainScreen.kt:408`) zeigt TCP, Statuskarte zeigt HA-`state` → Diskrepanz. |
| R8 | P1 | Adaptives Intervall | **JA** | User-Vorgabe, Z1/Z2. Aktuell: fester 10s-TCP-Poll (`init`, `delay(10_000)`). |
| R9 | P1 | HA-Event-Loop statt TCP-Loop (Boot/Shutdown) | **JA** | Direkt gekoppelt an R7/R8; aktueller Loop checkt nur TCP alle 2s (W11). Mit `stateEntity` wird HA-Event der Primary-Signal, TCP bleibt Fallback. |
| R10 | P1 | `withTimeout(8s)` auf HA-HTTP | **JA** | Einzeiler pro Funktion, behebt UI-Deadlock (W8, Z4). Bisher: `delay(6000)`-Trick in `getStates()` (`HaClient.kt:223-228`), kein Timeout in `getEntityStates/getEntityIds` → `loading=true` bleibt ewig. |
| R11 | P1 | `AtomicInteger` + `@Volatile` | **JA** | 2 Zeilen, Korrektheit-Prämissen für R1/R2 (ConcurrentHashMap-Kallbacks + WS-Thread), kein API-Wechsel. |
| R12 | P2 | `startSessions()` nur bei tatsächlicher Änderung | **TEILWEISE (R12-lite)** | "Entity-Set identisch → Rebuild komplett überspringen" + **State-Erhalt** (kein `unknown`-Reset) behebt das Edit-Flickern (W7, Z1). Vollversion (Resubscribe im Live-Session ohne Teardown) bleibt offen: Session-Lifecycle-Änderung, hoheres Risiko; Re-Auth kostet nur ~200–500 ms und der sichtbare Flicker ist durch State-Erhalt eliminiert. |
| R13 | P2 | `lastUpdate` in UI rendern | **JA** | User-Vorgabe ("Daten sind schon da, werden nur nie angezeigt"), Z4. `lastUpdate` wird bereits in `emitEntityStates` gesetzt — es fehlt nur das Rendering. |
| R14 | P2 | Mode-Hysterese (5s Stabilität) | **NEIN** | R4 macht Mode-Wechsel eventgetrieben; der 30s-Sicherheits-Probe begrenzt Flapping bereits. Nach R4-Feldbetrieb (v2.9) erneut bewerten. |
| R15 | P2 | Tailscale-IP als drittes Signal | **NEIN** | Optionales Signal, braucht User-Konfiguration (Peer-IP). `TRANSPORT_VPN`-Flag + Relay-Probe decken das aktuelle Setup vollständig ab. Später als eigene Feature-Entscheidung. |

**Zusammenfassung:** Alle 6 P0s + 5 P1s (R7–R11) + 2 P2s (R12-lite, R13) gehen rein. Offen: R12-Vollversion, R14, R15 — alle mit Begründung oben.

### 0.3 Unveränderliche Randbedingungen

- Natives Kotlin, **keine neuen Dependencies** (OkHttp, Coroutines, Compose, JUnit4, Robolectric vorhanden; neue Tests nutzen JUnit4 + hand-rolled Fakes — kein Mocking-Framework, da keines in `app/build.gradle.kts` deklariert ist).
- Bestehende Unit-Tests (`SwitchLogicTest`, `SwitchConfigSerializationTest`, `SwitchManagerPersistenceTest`, `MainScreenCarouselTest`/`CarouselDataFlowTest`, `SettingsScreenDragDropTest`) bleiben grün.
- **Kein UI-Redesign:** Gothic Cyber, Layout, Farben, Serif-Font bleiben. Nur: Zeitstempel-Text + 2 zustandsgetriebene Modifier + eine Mapping-Zeile.
- Public-API der `PcSwitchViewModel` (alle `fun` + `state: StateFlow<UiState>`) und `HaClient`-Klassen bleibt signaturstabil → Unit-Tests ohne Robolectric/Android-Runtime wo möglich.
- Uncommitted Changes (versionCode 74, release.sh) bleiben unangetastet.

---

## Sektion 1: Datenfluss vorher / nachher

### 1.1 Vorher (Stand HEAD)

```
 SwitchManager.switchesFlow (DataStore)
        │  jeder emit
        ▼
 PcSwitchViewModel.init ──────────────────────────────► startSessions()
        │                                                 │ stop beider Sessions (Teardown!)
        │                                                 ▼
        │                                     lanSession = HaSession(lanBase, …, entities)
        │                                     relaySession = HaSession(relayBase, …, entities)
        │                                                 │ auth → subscribe_events (ALLE Entities!)
        │                                                 │ + get_states
        │                                                 ▼
        │                                     watchdog: get_states ALLE 5 s   ◄── W2: 288 req/h + Events
        │                                                 │
        │                                     entityStates (StateFlow, JEDES Event)
        │                                                 ▼
        │                                     emitEntityStates()
        │                                     ├─► _state.update (IMMER, auch wenn nichts anders ist)
        │                                     ├─► NotificationHelper (nur bei Änderung)
        │                                     └─► broadcastTileState (IMMER)   ◄── W7: Recomposition-Sturm
        │
        ├── probeJob: ALLE 5 s ──► probeOnce()
        │        (HTTP LAN-Base + HTTP Relay-Base + onVpn(): 3 GETs)   ◄── W6: ~50 req/min Idle
        │        → mode (HomeLan/HomeVpn/Offline)
        │        ✗ wird bei vpnBusy=TRUE GESPRUNGEN                    ◄── W5: 90s Blindperiod
        │
        └── pcStatusJob: ALLE 10 s ──► checkPcStatus()
                 TCP:22 → pcStatus = "reachable"/"unreachable"/"unknown"
                 (roher TCP-Wert, unabhängig von HA-Entity)             ◄── W10: Flip-Flop + Diskrepanz
                                                                         (MainScreen.kt:408 zeigt TCP,
                                                                          Statuskarte zeigt HA-state)
 UI: MainScreen liest state, pcStatus, entityStates…
     lastUpdate wird gesetzt, ABER NIEMALS gerendert                    ◄── W9
```

### 1.2 Nachher (Zielzustand)

```
 SwitchManager.switchesFlow (DataStore)
        │  jeder emit
        ▼
 PcSwitchViewModel ──► startSessions()
        │                ├─ entity-SET gleich wie zuletzt? ──JA──► NICHTS (kein Teardown, kein Flicker)  [R12-lite]
        │                └─ geändert? ──► Rebuild, ABER alte States bleiben (kein "unknown"),
        │                                  refreshing=true bis erstes Snapshot                          [R12-lite]
        ▼
 HaSession (LAN + Relay)
   auth_ok ──► subscribe_entities { entity_ids: [...], coalesce_messages: 1 }     [R1+R6]
              (entity_ids = exakt das gemeldete Set; HA<2023.10 → Legacy-Pfad
               subscribe_events + get_states-Loop, unverändert)
   watchdog (Drift-Modus): get_states nur wenn >60 s kein Entity-Event            [R1]
   Reconnect: Backoff 1s→30s + Jitter, Reset bei auth_ok,                          [R2+R3]
              auth_invalid → stop() OHNE Retry
        │
        ▼
 entityStates (StateFlow, coalesced vom HA-Server)
        ▼
 emitEntityStates()
   buildSnapshot() → (switchStates, onState, offState)
   ┌─ Snapshot == aktuell? ──JA──► emit NUR wenn >30 s seit letztem emit (Heartbeat)   [R5]
   └─ anders? ──────────────────► _state.update { …, lastUpdate=now }
                                  NotificationHelper (nur bei Änderung)
                                  broadcastTileState (nur bei Änderung)
        │
        ▼
 UiState: switchStates, onState, offState, lastUpdate, refreshing, pcStatus (KOMBINIERT)

 NetworkWatchdog (NEU, ConnectivityManager)                                     [R4]
   registerDefaultNetworkCallback
   ├─ onAvailable / onLost      ──► debounced (750 ms) ──► probeOnce()
   └─ onCapabilitiesChanged     ──► vpnTransportActive = caps.hasTransport(TRANSPORT_VPN)
                                     + debounced probeOnce()
   Sicherheits-Timer (ViewModel probeJob): probeOnce() alle 30 s (Fallback,
   ersetzt 5s-Loop) — VPN-Status kommt aus vpnTransportActive, KEINE onVpn()-GETs mehr

 pcStatusJob (adaptiv):                                                            [R8]
   Transition (booting/shutting_down): TCP alle 3 s
   Steady-State:                         TCP alle 15 s
   Offline/Setup:                        kein Probe
   → PcStatusResolver.resolve(haStateEntity, tcp) → pcStatus = "on"/"off"/"unknown"
     (HA-Entity primary, TCP fallback — EIN Wert für alle Konsumenten)            [R7]
 UI: MainScreen
   ├─ Statuszeile: Status + "· aktualisiert vor Xs" (lastUpdate, 15s-Tick)        [R13]
   ├─ PC-Karte: loading/refreshing → dezent, ohne Layout-Shift
   └─ Carousel: displayState = pcStatus (KOMBINIERT) — Diskrepanz weg             [R7]
```

### 1.3 Wer pollt was wie oft (Zielzustand)

| Quelle | Art | Intervall / Trigger | Ersatz für |
|--------|-----|--------------------|------------|
| HA-Entity-States | **Event** (WS `subscribe_entities`, `coalesce_messages:1`) | sofort beim Event | `get_states` alle 5 s |
| HA-Drift-Sync | Poll | `get_states` nur wenn >60 s kein Event | sturer 5s-Loop |
| HA-Reconnect | Event (WS close) | Backoff 1→30 s, Reset bei `auth_ok` | fester 2.5 s |
| Netzwerk (WLAN/VPN) | **Event** (NetworkCallback) | `onAvailable`/`onLost`/`onCapabilitiesChanged`, 750 ms Debounce | 5s-Poll mit `onVpn()`-Snapshot |
| Mode-Check | Poll (Safety) | alle 30 s | 5s-Loop |
| PC-TCP | Poll (adaptiv) | 3 s Transition / 15 s steady / skip Offline | fester 10 s |
| PC-Status-UI | StateFlow | nur bei echter Änderung + 30 s Heartbeat | jedes Event |
| Tile-Broadcast | StateFlow | nur bei echter Änderung | jedes Event |
| "zuletzt aktualisiert" | UI-Tick | 15 s, nur wenn Text sich ändert | — (neu, R13) |

### 1.4 Eventgetrieben vs. Polling

- **Event:** HA-Entity-States (WS), Reconnect-Trigger (WS close), Netzwerk-Verfügbarkeit + VPN-Transport (NetworkCallback), Button-Presses (HA-Service → Event kommt zurück).
- **Poll (bewusst verbleibend):** Drift-Sync (60 s, verteidigt gegen Event-Verlust), Mode-Safety (30 s, verteidigt gegen OEM-Callback-Miss), TCP-Port-22 (3/15 s, kein Push von der PC-Seite).
- **Weg:** 5s-`get_states`-Loop, 5s-HTTP-Mode-Loop mit `onVpn()`-GETs, rohes 10s-TCP als einziger Status.

---

## Sektion 2: Änderungen pro Datei

### 2.1 `ha/HaClient.kt`

**Konkret was:**

1. **`auth_ok`-Handler (aktuell Zeile 183–197):** Wenn `entities` (bereits über den Konstruktor `HaSession(url, isRelay, token, entities)` übergeben, Zeile 746/747 im ViewModel) **nicht leer** ist:
   ```json
   {"id":N,"type":"subscribe_entities","entity_ids":["light.x","switch.y"],"coalesce_messages":1}
   ```
   statt `getStates()` + `subscribe_events`. Subscription-ID merken. **Legacy-Pfad** (entities leer, z. B. HA < 2023.10 ohne Support): exakt wie heute (`getStates` + `subscribe_events` + 5s-`get_states`-Watchdog).
2. **`scheduleReconnect()` (aktuell 165–173):** `delay(2500)` → `delay(backoff.nextDelayMs())` (siehe Sektion 3). `backoff.reset()` im `auth_ok`-Handler.
3. **`auth_invalid` (aktuell 199–202):** `authFailed.value = true` + `w.close(4001)` bleibt, ABER `onClosed`/`onFailure` prüfen dann `authFailed` → **kein** `scheduleReconnect()` (stop, Session endet). ViewModel zeigt `authError` (existiert bereits, Zeile 829/833).
4. **`getStates()` (aktuell 223–228):** `delay(6000)`-Trick → `withTimeout(8_000)` um den Request. Gleiche Behandlung für `getEntityStates`/`getEntityIds` (R10) — diese haben aktuell **kein** Timeout → `loading=true`-Deadlock.
5. **Watchdog (aktuell 280–285):** zwei Modi:
   - **Drift-Modus** (subscribed): Loop `delay(10_000)`; wenn `now - lastEntityEventAt > 60_000` → `getStates` (volles Resync). Sonst weiter schlafen.
   - **Legacy-Modus** (kein `subscribe_entities`): unverändert `delay(5_000)` + `getStates`.
6. **`reqId` (aktuell 102):** `var reqId = 0` → `AtomicInteger` (R11). **`lastEntityEventAt`:** neues `@Volatile var`, in `applyStates()` aktualisiert (R11).
7. **Testbarkeit:** `HaSession` erhält optional `factory: WebSocket.Factory` (Default: die existierende `OkHttpClient`-Instanz, Zeile 97 — `OkHttpClient` implementiert `WebSocket.Factory`). Unit-Tests injizieren eine Fake-Factory. Kein Default-Verhalten ändert sich.

**Public-API bleibt stabil:** `HaSession(url, isRelay, token, entities)` — kein Signature-Wechsel (der `factory`-Parameter ist optional mit Default). `start()/stop()/press(entity)/alive/entityStates/authFailed` unverändert. Neues: `lastEntityEventAt: Long` (public, für Heartbeat-Diagnose).

**Risiko/Regression:**
- HA < 2023.10: `subscribe_entities` wird mit Fehlerantwort beantwortet → Handler fällt auf Legacy-Pfad zurück (Test abdecken). Verhalten dann wie heute.
- Ohne `get_states`-Sturm ist eine fehlerhafte WS-Verbindung (offene Pipe, keine Events) erst nach 60 s sichtbar → Drift-Watchdog deckt das ab; zusätzlich verliert die App bei totalem WS-Ausfall auf Reconnect-Backoff zurück (bestehend).
- `withTimeout(8s)` vs. alt `delay(6000)`: Worst Case 2 s länger, dafür deterministisch — kein Deadlock mehr.

### 2.2 `ha/ReconnectBackoff.kt` (NEU)

```kotlin
package dev.finnley.pcswitch.ha

/** Exp. Backoff + Jitter: 1s → 30s Cap. Pure JVM, testbar (injected random). */
class ReconnectBackoff(
    private val baseMs: Long = 1_000,
    private val capMs: Long = 30_000,
    private val random: () -> Double = Math::random
) {
    private var attempt = 0

    /** delay = min(cap, scaled + U(0, scaled/2)); scaled = min(cap, base·2^attempt). */
    fun nextDelayMs(): Long {
        val scaled = (baseMs * (1L shl minOf(attempt, 16))).coerceAtMost(capMs)
        val delay = scaled + (random() * (scaled / 2)).toLong()
        attempt++
        return delay.coerceAtMost(capMs)
    }

    /** Nach erfolgreichem auth_ok. */
    fun reset() { attempt = 0 }
}
```

Sequenz bei `random=0.5`: **1.25 s, 2.5 s, 5 s, 10 s, 20 s, 30 s, 30 s, …** (Bereiche: [1–1.5], [2–3], [4–6], [8–12], [16–24], [30–30] s).

**API:** neu, keine Abhängigkeit. **Risiko:** keins (neue Datei, reines Kotlin, keine Android-Imports).

### 2.3 `state/PcSwitchViewModel.kt`

**Konkret was (alle Änderungen intern, Public-API signaturstabil):**

1. **`startSessions()` (aktuell ~718–757):** R12-lite:
   - `entitySet` berechnen (wie heute).
   - `if (entitySet == lastEntitySet && lanSession != null && lanSession.alive.value) return` — kein Rebuild bei unverändertem Set (heute: **jeder** DataStore-emit, inkl. `pcIp`-Änderung oder no-op-Writes, reißt beide Sessions ab).
   - Bei Rebuild: **States nicht auf "unknown" resetten** — alte `switchStates`/`onState`/`offState` bleiben, `refreshing = true`; Entities, die nicht mehr im Set sind, werden `"unknown"`. `refreshing = false` nach erstem Snapshot.
   - `lastEntitySet = entitySet` merken (neues `private var`).
2. **`emitEntityStates()` (aktuell ~759–806):** Coalescing (Sektion 3.3):
   - `snap = buildSnapshot(states, switches, onEntity, offEntity)` (reine Companion-Funktion).
   - `if (snap == current && now - lastEmitAt < 30_000) return` — kein `_state.update`, kein Broadcast, keine Notification.
   - Sonst: `_state.update { it.copy(…, lastUpdate = now) }` + Notification/Broadcast **nur wenn `snap != current`**.
   - Neues `private var lastEmitAt: Long`.
3. **`probeOnce()` (aktuell ~812–848):**
   - **`vpnBusy`-Skip entfernen** (Z2, 90s-Blindperiod). `inSetup`-Skip bleibt.
   - `val vpn = networkWatchdog.vpnTransportActive.value || onVpn()` — `onVpn()` bleibt als Fallback (z. B. vor dem ersten Callback, in Tests), ist aber nicht mehr der primäre Weg (spart 3 GETs pro Probe).
   - Rest (LAN/HTTP, Relay/HTTP, Mode-Entscheidung, statusNote) unverändert.
4. **`probeJob` (aktuell 98–117):** `delay(5_000)` → `delay(30_000)` (Safety-Net, Sektion 4.3). `firstProbe`-Logik (loading=false) bleibt.
5. **`pcStatusJob` (aktuell 118–124):** adaptiv (Sektion 5.2):
   ```
   val s = _state.value
   if (s.inSetup || s.mode == "offline") { delay(15_000); continue }
   val inTransition = s.pcToggleState in ("booting", "shutting_down")
   delay(if (inTransition) 3_000 else 15_000)
   val tcp = PcStatusChecker.isReachable(settings.pcIp)
   val haState = entityStates.value[pcSwitchStateEntity]?.state  // Lookup: Switch mit stateEntity ≠ ""
   _state.update { it.copy(pcStatus = PcStatusResolver.resolve(haState, tcp)) }
   ```
   `pcSwitchStateEntity`: `switches.firstOrNull { it.stateEntity.isNotBlank() }?.stateEntity` (PC-Switch trägt das State-Entity).
6. **`bootPc()` / `shutdownPc()` (aktuell ~297–384):** HA-first-Loop (Sektion 5.3): 2s-Ticks wie heute, aber Abbruchbedingung wird:
   - `stateEntity` gesetzt: HA-Entity zeigt Zielzustand ("on" bei Boot, "off" bei Shutdown) **ODER** TCP bestätigt (reachable / !reachable).
   - kein `stateEntity`: reines TCP wie heute.
   - Timeouts (Boot 60 s, Shutdown 30 s) bleiben.
7. **`init`:** `networkWatchdog = NetworkWatchdog(AndroidNetworkEventSource(ctx), viewModelScope).also { it.start { probeOnce() } }` + `onCleared()`-Override: Watchdog + Sessions + Jobs sauber stoppen (heute: `onCleared` fehlt/ist leer — Sessions laufen weiter, wenn der ViewModel-Steady-Flow aufhört).
8. **`UiState`:** neues Feld `refreshing: Boolean = false` (Default-Wert → bestehende Konstruktionen/Tests brechen nicht). `lastUpdate` existiert bereits. `pcStatus` bleibt `String`, Werte werden zu `"on"/"off"/"unknown"` (Resolver) statt `"reachable"/"unreachable"` — UI-Mapping aktualisiert sich in `MainScreen.kt` (eine Stelle, Sektion 2.6).
9. **`connectToHome()`/`disconnectHome()`:** Signatur + Intent-Logik unverändert. Blindperiod ist weg, weil (a) `probeOnce` nicht mehr bei `vpnBusy` springt und (b) `NetworkWatchdog.onAvailable` bei VPN-Aktivierung sofort (debounced) triggert. Der bestehende 3s×30 Probe-Loop bleibt als zusätzlicher Fallback.

**Public-API bleibt stabil:** alle `fun` (connectToHome, pressCurrentSwitchOn/Off, bootPc, shutdownPc, save*, searchEntities, checkPcStatus, onPcSwitchTrigger, …) + `state: StateFlow<UiState>` unverändert. `checkPcStatus()` (public, wird vom Setup-Flow genutzt) behält Signatur, intern nutzt sie den Resolver.

**Risiko/Regression:**
- `startSessions`-Skip: wenn die `alive`-Flag-Prüpfung falsch positiv ist (Session tot, `alive=true` hängt), wird der Rebuild übersprungen → Gegenmaßnahme: `alive` wird bei `onClosed/onFailure` synchron false gesetzt (bestehend); zusätzlich prüft der 30s-Safety-Loop den Mode und `startSessions` wird bei `mode == Offline` + `!alive` erzwungen (kleine Bedingung im `startSessions`-Skip).
- Coalescing-Heartbeat (30 s) erzeugt 1 Recomposition/Minute im Steady-State — vernachlässigbar, hält `lastUpdate` frisch.
- `pcStatus`-Wertewechsel ("reachable"→"on"): alle Konsumenten sind in `MainScreen.kt` + Tile (`EXTRA_IS_ON` nutzt `state`, nicht `pcStatus` → unaffected). Grep bei Implementierung: `pcStatus`-Vergleiche (`== "reachable"`).

### 2.4 `pcstatus/PcStatusResolver.kt` (NEU)

```kotlin
package dev.finnley.pcswitch.pcstatus

/** Kombiniert HA-Entity-State (primary) und TCP-Port-22 (Fallback) zu EINEM Status.
 *  Pure JVM, deterministisch → kein Flip-Flop (W10). */
object PcStatusResolver {
    const val ON = "on"
    const val OFF = "off"
    const val UNKNOWN = "unknown"

    fun resolve(haState: String?, tcpReachable: Boolean): String =
        if (haState == ON || haState == OFF) haState
        else if (tcpReachable) ON else OFF
}
```

- `haState` aus `stateEntity` (Werte aus HA: "on"/"off"/"unknown"/andere). HA-Entity gesetzt und definiert → **HA gewinnt** (User hat es explizit konfiguriert). `stateEntity` leer/unknown → **TCP entscheidet**.
- **API:** neu, kein Android-Import. **Risiko:** keins (neu, rein, deterministisch).

### 2.5 `network/NetworkEventSource.kt` + `network/NetworkWatchdog.kt` (NEU)

**`NetworkEventSource.kt`:**
```kotlin
package dev.finnley.pcswitch.network

interface NetworkEventSource {
    interface Listener {
        fun onNetworkAvailable()
        fun onNetworkLost()
        fun onVpnTransportChanged(active: Boolean)
    }
    fun register(listener: Listener)
    fun unregister()
}

/** Android-Adapter: ConnectivityManager.registerDefaultNetworkCallback.
 *  Permission: ACCESS_NETWORK_STATE (bereits im Manifest). */
class AndroidNetworkEventSource(context: Context) : NetworkEventSource {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var listener: Listener? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = listener?.onNetworkAvailable()
        override fun onLost(network: Network) = listener?.onNetworkLost()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            listener?.onVpnTransportChanged(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    }
    override fun register(listener: Listener) { this.listener = listener; cm.registerDefaultNetworkCallback(callback) }
    override fun unregister() { try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}; listener = null }
}
```

**`NetworkWatchdog.kt`:**
```kotlin
package dev.finnley.pcswitch.network

/** Debounced Event-Quelle für probeOnce + VPN-Transport-Flag.
 *  Logik ist Android-frei (injected Source + Clock) → Unit-testbar. */
class NetworkWatchdog(
    private val source: NetworkEventSource,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 750,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    val vpnTransportActive = MutableStateFlow(false)
    private var pending: Job? = null

    fun start(onNetworkChange: () -> Unit) {
        source.register(listener)
        onNetworkChange()          // Initial-Probe, bevor das erste Event kommt
    }

    fun stop() { pending?.cancel(); source.unregister() }

    private fun debounced(onChange: () -> Unit) {
        pending?.cancel()
        pending = scope.launch { delay(debounceMs); onChange() }
    }
    // listener: onNetworkAvailable/onLost → debounced; onVpnTransportChanged → Flag + debounced
}
```

**API:** neu. `NetworkEventSource` ist eine Nahtstelle — die Logik (`NetworkWatchdog`) ist ohne Robolectric testbar (Fake-Source), der Android-Adapter selbst ist ~20 Zeilen dünnes Wrapping.
**Risiko/Regression:**
- OEMs mit defekten NetworkCallback-Events → 30s-Safety-Probe fängt es (Sektion 4.3).
- `registerDefaultNetworkCallback`-Callbacks kommen auf dem Connectivity-Thread → alle Übergaben in die Coroutine-Scope (debounced launch) — kein Main-Thread-Work, kein ANR.
- `unregister`-Exceptions (z. B. Doppel-Stop) werden geschluckt (bestehendes `unregisterNetworkCallback`-Verhalten).

### 2.6 `ui/MainScreen.kt`

**Konkret was:**

1. **Zeitstempel (R13):** In der Statuszeile (oben, neben `statusNote`) und auf der PC-Karte: `"· aktualisiert vor Xs"`.
   - Helper `fun relativeTime(lastUpdate: Long, now: Long): String` (Companion oder local): <10 s → "gerade eben", <60 s → "vor Xs", <3600 s → "vor Xm", sonst "vor Xh". `lastUpdate == 0L` → kein Text (no Data).
   - Tick: `var now by remember { mutableLongStateOf(clock()) }` + `LaunchedEffect` mit 15s-`delay`, die `now` nur aktualisiert, wenn sich das formatierte Ergebnis ändert (Vergleich der String-Variante) → Recomposition nur bei Textwechsel.
   - Keine neue Zeile: Zeitstempel wird an den vorhandenen Status-Text angehängt → **kein Layout-Shift**.
2. **Ladezustände (R13/Z4):**
   - `loading || refreshing` → PC-Karte erhält einen dezenten, bestehenden Stil-Modifikator (die Gothic-Cyber-Breathing-Animation, die für den Connect-Button existiert, wird als `Modifier.composed`-Stil wiederverwendet — kein neuer Animations-Code), Text bleibt lesbar.
   - Carousel: `refreshing == true` → Karten werden ~60 % gedimmt (alpha-Modifier), Status bleibt sichtbar (kein "unknown"-Flickern mehr, weil R12-lite die States ohnehin erhält).
3. **Carousel-Diskrepanz (Zeile 408):** `val displayState = if (isPcSwitch && pcStatus != "unknown") pcStatus else state` → nach S8 ist `pcStatus` der **kombinierte** Resolver-Wert ("on"/"off"/"unknown") → Zeile bleibt, zeigt aber denselben Wert wie Statuskarte/Button. Mapping: "on" → bestehende "erreichbar"-Darstellung (wie heute "reachable"), "off" → bestehende "nicht erreichbar"-Darstellung (wie heute "unreachable"), "unknown" → grau (bestehend). Eine Mapping-Stelle im Composable (heute: Vergleich `pcStatus == "reachable"`).
4. **Kein Design-Wechsel:** Farben, Serif-Font, Layout-Hierarchie, Gothic-Cyber-Ästhetik, alle Composables bleiben. Hinzugekommen: 1 Text-Append, 2 zustandsgetriebene Modifier, 1 Mapping-Zeile.

**API:** Composables `MainScreen`/`SwitchCarousel` — Signatur unverändert (alle über `PcSwitchViewModel.state` konsumieren).
**Risiko/Regression:** 15s-Tick erzeugt maximal 1 leichte Recomposition pro 15 s (nur bei Textwechsel) — unter der Hitches-Schwelle. Kein ANR-Risiko (kein Blocking, kein IO im Composable).

### 2.7 `app/build.gradle.kts`

**Keine Änderung an Dependencies** (Randbedingung: keine schweren neuen Dependencies). Robolectric 4.11.1 + JUnit4 sind vorhanden; die neuen Tests brauchen **kein** Robolectric (hand-rolled Fakes: Fake-WebSocket-Factory, Fake-NetworkEventSource, Fake-Clock).
Die uncommitteden Änderungen (versionCode 74, release.sh) bleiben unangetastet.

### 2.8 Release-Repo `/home/finnley/projects/pc-switch` (separater Schritt, NICHT jetzt)

Nach erfolgreicher Implementierung + Testgrün (Sektion 8, S12): `versionCode` 74 → **75**, `versionName` → **2.9.0**, `/home/finley/projects/pc-switch/version.json` → `2.9.0`. Eigener, kleiner, 1:1 delegierbarer Schritt — nicht Teil dieses Designs und hier nicht ausgeführt.

---

## Sektion 3: Reconnect- + Coalescing-Design

### 3.1 Reconnect

**Parameter (fix, dokumentiert):**

| Parameter | Wert |
|-----------|------|
| `baseMs` | 1 000 |
| `capMs` | 30 000 |
| Formel | `delay = min(cap, scaled + U(0, scaled/2))`, `scaled = min(cap, base·2^attempt)` |
| Bereiche | [1–1.5] s, [2–3] s, [4–6] s, [8–12] s, [16–24] s, [30–30] s (ab attempt 5) |
| Reset | bei `auth_ok` (→ attempt 0) |
| Kein Retry | `auth_invalid` → `authFailed=true`, Session stoppt, **kein** `scheduleReconnect` |

**Sequenz-Beispiel** (random=0.5): 1.25 s → 2.5 s → 5 s → 10 s → 20 s → 30 s → 30 s …
Worst Case 6 Versuche bis 30 s ≈ 90 s — danach stabil 30 s-Abstände (kein Storm, kein Aufgeben, kein endloser 2.5s-Loop mehr).

**Wo implementiert:**
- `ha/ReconnectBackoff.kt` (neu, Sektion 2.2) — reine Logik, injizierbarer `random: () -> Double`.
- `ha/HaClient.kt`: `HaSession` hält eine `ReconnectBackoff`-Instanz (pro Session); `scheduleReconnect()` nutzt `nextDelayMs()`; `auth_ok`-Handler ruft `reset()`; `onClosed/onFailure` prüfen `authFailed` → Skip.

**Rationale:** "Equal Jitter" (obere Hälfte) garantiert ≥1 s (User-Vorgabe "1s→30s") und ≤30 s, verteilt Reconnects mehrerer Geräte/Services (LAN+Relay = 2 Sessions) statistisch → kein thundering herd gegen den HA-Server.

### 3.2 auth_invalid

- `auth_token` abgültig/revoked → `authFailed.value = true`, WS close(4001), **keine** Reconnect-Schedule.
- ViewModel reagiert wie heute (Zeile 829/833): `authError` wird gesetzt → UI zeigt "HA-Authentifizierung fehlgeschlagen" → User fixiert Token (Setup) → `startSessions` nach Token-Änderung baut Sessions neu (DataStore-emit).

### 3.3 Coalescing

**Serverseitig (HA):** `coalesce_messages: 1` im `subscribe_entities`-Payload → HA bündelt `state_changed`-Events pro Entity in einer Zeitfenster (HA-interne Aggregation; Feature seit HA 2023.10). Weniger Messages pro Batch.

**Clientseitig (ViewModel):**
```
emitEntityStates():
  snap  = buildSnapshot(entityStates, switches, onEntity, offEntity)
  cur   = currentSnapshot(_state.value)
  if (snap == cur && now - lastEmitAt < 30_000) return     // coalescing: nichts Neues, kein Heartbeat fällig
  lastEmitAt = now
  _state.update { it.copy(snap…, lastUpdate = now) }
  if (snap != cur) { notifyHelper(…); broadcastTileState() }  // Side-Effects NUR bei echter Änderung
```

- **`buildSnapshot`** (reine Companion-Funktion): Mappt `Map<String, EntityState>` → `(switchStates: Map<String,String>, onState: String, offState: String)`. Regeln wie heute: `stateEntity` bevorzugt vor `primaryEntity`, Fallback `"unknown"`.
- **Heartbeat:** Identische States → trotzdem 1 emit/30 s, damit `lastUpdate` frisch bleibt (UI zeigt "gerade eben", nicht "vor 5 min" bei stabiler Verbindung). Max. 1 Recomposition/Minute im Steady-State.
- **Tile-Broadcast + Notification:** nur im `snap != cur`-Zweig (heute: Broadcast bei jedem emit) → Tile-UI (QuickSettingsTileService) wird nicht mehr umsonst geweckt.

### 3.4 Wie getestet (siehe Sektion 7)

- **Backoff:** `ReconnectBackoffTest` — deterministisch über injizierten `random` (Sequenz, Cap, Reset, Overflow).
- **Coalescing:** `StateCoalescingTest` — reine Funktionen `buildSnapshot`/`shouldEmit` (identisch→skip, geändert→immediat, Heartbeat nach 30 s).
- **WS-Verhalten (subscribe, drift, auth_invalid, Reconnect-Zeiten):** `HaClientSessionTest` mit Fake-WebSocket-Factory + Fake-Clock (Sektion 7.1).

---

## Sektion 4: WLAN/VPN eventgetriebenes Design

### 4.1 API + Permissions

- **API:** `ConnectivityManager.registerDefaultNetworkCallback(NetworkCallback)` (API 21+, minSdk der App erfüllt).
  - Callbacks: `onAvailable`, `onLost`, `onCapabilitiesChanged` (liefert `NetworkCapabilities`).
  - **Permission:** `ACCESS_NETWORK_STATE` — **bereits im `AndroidManifest.xml`** (Kommentar "VPN-Transport-Erkennung (HomeLan vs. HomeVpn)"). **Keine neue Permission.**
  - Callbacks laufen auf dem Connectivity-Thread → `NetworkWatchdog` hoppt in den Coroutine-Scope (debounced `launch`) — kein Main-Thread-Work.

### 4.2 Klassen-Design

Siehe Sektion 2.5 (`NetworkEventSource`-Interface + `AndroidNetworkEventSource`-Adapter + `NetworkWatchdog` mit `vpnTransportActive: StateFlow<Boolean>` + Debounce 750 ms).

- `onAvailable` → debounced `probeOnce()`
- `onLost` → debounced `probeOnce()`
- `onCapabilitiesChanged` → `vpnTransportActive = caps.hasTransport(TRANSPORT_VPN)` + debounced `probeOnce()`

**Debounce-Rationale:** Network-Switches (z. B. WLAN↔VPN, Roaming) feuern `onLost`+`onAvailable` in schneller Folge → 750 ms Fenster bündelt zu 1 Probe statt N.

### 4.3 Fallback

| Szenario | Schutz |
|----------|--------|
| OEM verfehlt Callback-Events | `probeJob` alle **30 s** (Safety-Net, ersetzt 5s-Loop) — Mode korrigiert sich innerhalb 30 s |
| Callback kommt vor dem ersten `onAvailable` (App-Start) | `start()` feuert 1 Initial-`probeOnce()` |
| `registerDefaultNetworkCallback` wirft (selten) | try/catch → Watchdog inert, 30s-Loop bleibt → degradierter, aber funktionierender Modus (wie heute) |
| `unregister` bei Doppel-Stop | Exception geschluckt |

### 4.4 Ersetzung 5s-Snapshot / 90s-vpnBusy

| Heute | Nachher |
|-------|---------|
| `probeJob` alle 5 s, jeder Probe `onVpn()` = 3 GETs | 30 s Safety-Net + eventgetriebene Probes; VPN-Status aus `vpnTransportActive` (Event), **0 zusätzliche HTTP-Requests** |
| `connectToHome`: `vpnBusy=true` → `probeOnce` springt **90 s** (30×3 s) → Mode bleibt `Connecting` | `vpnBusy` nur noch Button-Gate; VPN-Aktivierung → `onAvailable`/`onCapabilitiesChanged` → Probe innerhalb ~1–2 s → Mode `HomeVpn` sofort |
| `disconnectHome`: analog | analog — `onLost` → Probe → `HomeLan`/`Offline` sofort |

**Idle-Traffic-Ziel:** von ~50 HTTP-Requests/min (Probe) + 288 `get_states`/h (WS) auf **~2 Probes/min + ≤1 `get_states`/min** (nur Drift) + Events.

### 4.5 HomeVpn vs. HomeLan

`probeOnce`: `val vpn = networkWatchdog.vpnTransportActive.value || onVpn()` — `TRANSPORT_VPN`-Flag ist primär (eventaktuell), `onVpn()` (aktiver-Netz-Snapshot) bleibt als Fallback für den Moment vor dem ersten Capability-Event. Mode-Entscheidung (LAN-HTTP vs. Relay-HTTP) wie heute.

---

## Sektion 5: PC-Status-Design

### 5.1 Kombinierte Quelle (kein Flip-Flop)

`PcStatusResolver.resolve(haState, tcpReachable)` (Sektion 2.4):

| HA-`stateEntity` | TCP:22 | Ergebnis | Begründung |
|------------------|--------|----------|------------|
| "on" | egal | **on** | HA primär (explizit konfiguriert) |
| "off" | egal | **off** | HA primär |
| "unknown" / leer / null | erreichbar | **on** | TCP-Fallback |
| "unknown" / leer / null | nicht erreichbar | **off** | TCP-Fallback |

- Deterministisch: gleiche Inputs → gleiches Ergebnis (×100 getestet) → **kein Flip-Flop** (W10).
- HA-State wird über die Entity-Subscription angeliefert (keine Extra-Requests); TCP-Port-22 bleibt der immer-verfügbare Fallback (HA down, kein `stateEntity`, Setup-Modus).
- Konflikt "HA on, TCP off" (stale HA): wird nicht als Flap gerendert (HA gewinnt stabil); Stale-Heilung kommt von WS-Reconnect + 60s-Drift-Sync.

### 5.2 Adaptives Intervall

| Zustand | Intervall | Begründung |
|---------|-----------|------------|
| `pcToggleState` = booting/shutting_down | **3 s** | Transition soll zügig sichtbar werden |
| Steady-State | **15 s** | Status ändert sich selten; Traffic sparen (Z1/Z2) |
| `mode == offline` oder `inSetup` (Setup ohne PC-Kontext) | **kein TCP-Probe** (15 s-Schlaf, kein Update) | Probe ist sinnlos ohne Netz; letzter Wert bleibt (statusNote erklärt Offline) |

Ersetzt den festen 10 s-Poll. Transition-Ende: `pcToggleState` zurück auf idle → 15 s-Modus.

### 5.3 Transition-Loops (Boot/Shutdown) — HA-first

`bootPc()`/`shutdownPc()` (2s-Ticks, Timeouts 60 s/30 s bleiben):

```
tick:
  stateEntity ≠ ""?
    HA-Entity zeigt Zielzustand?  (Boot: "on", Shutdown: "off")  → DONE
  TCP-Port-22 bestätigt?          (Boot: reachable, Shutdown: !reachable) → DONE
  else: weiter warten
```

- `stateEntity` gesetzt → **HA-Event ist Primary** (kommt über die Subscription, 2s-Tick ist nur der Abfrage-Rhythmus — keine Extra-Requests).
- kein `stateEntity` → reines TCP (wie heute, z. B. Setup-Modus).
- **API:** `bootPc/shutdownPc` signaturstabil; die Abbruch-Logik wird in eine reine Companion-Funktion `PcTransition.isDone(haState: String?, tcpReachable: Boolean, phase: Phase): Boolean` ausgelagert → unit-testbar (Sektion 7).

### 5.4 Carousel-vs-Button-Diskrepanz (MainScreen.kt:408) — Auflösung

**Root Cause (verifiziert, HEAD):**
- Zeile 408: `val displayState = if (isPcSwitch && pcStatus != "unknown") pcStatus else state` → Carousel zeigt **rohes TCP** (`"reachable"/"unreachable"`).
- Statuskarte/Button nutzen `state` (HA-Entity: `switchStates`/`onState`/`offState`).
- Bei TCP-Timeout + HA-"on": Carousel zeigt "unreachable", Karte zeigt "on" → sichtbare Diskrepanz (W10).

**Auflösung:** Nach S8 ist `pcStatus` der **einzige kombinierte** Wert (Resolver, Sektion 5.1) → Carousel (Zeile 408), Statuskarte und Button lesen denselben Wert. Die Zeile 408 bleibt (der `!= "unknown"`-Guard ist dann harmlos), nur das String-Mapping ändert sich ("reachable"→"on", "unreachable"→"off"). **Ein Wert, alle Konsumenten, keine Diskrepanz.**

---

## Sektion 6: UI-Feedback-Design

### 6.1 "zuletzt aktualisiert" (R13)

- **Datenquelle:** `UiState.lastUpdate` (existiert, wird in `emitEntityStates` bei jedem (coalesced) emit auf `now` gesetzt).
- **Rendering:** Statuszeile: `{statusNote} · aktualisiert {relativeTime(lastUpdate)}`; PC-Karte analog. `relativeTime`: <10 s "gerade eben", <60 s "vor Xs", <3600 s "vor Xm", sonst "vor Xh". `lastUpdate == 0L` → kein Suffix.
- **Tick ohne Recomposition-Sturm:** `remember`-State `now`, `LaunchedEffect` mit 15 s-`delay`; `now` wird nur neu gesetzt, wenn sich das **formatierte** `relativeTime`-Ergebnis ändert (String-Vergleich) → max. 1 Text-Recomposition pro 15 s, sonst 0.
- **Kein Layout-Shift:** Suffix wird an den bestehenden Text angehängt (gleiche Zeile), keine neue UI-Zeile.

### 6.2 Ladezustände (Z4)

| Zustand | UI |
|---------|----|
| `loading == true` (App-Start, bis erste Probe) | Wie heute (bestehender Lade-Hinweis) + PC-Karte gedimmt |
| `refreshing == true` (R12-Rebuild, kein "unknown"-Reset) | PC-Karte + Carousel-Karten ~60 % alpha, Breathing-Modifikator (bestehender Connect-Button-Stil, `Modifier.composed`), Status **bleibt sichtbar** |
| `pcToggleState != "idle"` | Wie heute (Spinner am Toggle-Button) |

### 6.3 Weiche Übergänge ohne Hitches/ANR

- **Kein Blocking im Composable:** keine IO, keine `runBlocking`; alle Daten über `StateFlow` (bestehend).
- **Recomposition-Budget:** Coalescing (≤1 emit/30 s im Steady-State) + 15 s-Text-Tick + Drift-Sync (≤1/min) → <10 Recompositions/min im Idle (vorher: pro Entity-Event + pro 5 s-`get_states`-Zyklus).
- **Animationen:** bestehende Breathing/Transition-Animationen bleiben; `refreshing`-Dimming nutzt `animateFloatAsState` (bestehendes Muster) — GPU-compositable, kein Layout-Recalc.
- **ANR-Vermeidung:** NetworkCallback-Hop in Coroutine-Scope (Sektion 2.5); TCP-Probe läuft auf `Dispatchers.IO` (bestehend); `withTimeout(8s)` verhindert hängende HA-HTTP (R10).

### 6.4 Was NICHT geändert wird

Gothic-Cyber-Layout, Farben, Serif-Font, Composable-Struktur, Toggle-Button-Design, Carousel-Geometrie, QuickSettings-Tile-UI, Notification-Content (nur Trigger-Frequenz ändert sich). **Kein Redesign.**

---

## Sektion 7: Testplan

### 7.1 Neue Unit-Tests (JUnit4, pure JVM, hand-rolled Fakes — kein Mocking-Framework)

**`app/src/test/java/dev/finnley/pcswitch/ha/ReconnectBackoffTest.kt`** (neu)
- `first_delay_between_1s_and_1_5s` (random=0.0 → 1000 ms; random=0.999 → ≤1500 ms)
- `sequence_with_fixed_random_0_5` (→ [1250, 2500, 5000, 10000, 20000, 30000, 30000])
- `never_exceeds_cap` (random=1.0, 30 Aufrufe)
- `reset_returns_to_base_range` (5 Aufrufe → reset → nächste ∈ [1000, 1500])
- `overflow_attempt_stays_capped` (40 Aufrufe, kein Overflow, `minOf(attempt, 16)`)

**`app/src/test/java/dev/finnley/pcswitch/ha/HaClientSessionTest.kt`** (neu; Fake-WebSocket-Factory + Fake-Clock)
- `auth_ok_sends_subscribe_entities_with_ids_and_coalesce` (gesendetes JSON: `type=subscribe_entities`, `entity_ids` korrekt, `coalesce_messages=1`)
- `auth_ok_legacy_path_without_entity_ids` (`subscribe_events` wie heute)
- `legacy_ha_error_falls_back_to_get_states_loop` (Server antwortet mit Fehler auf `subscribe_entities` → Legacy)
- `drift_watchdog_no_fetch_within_60s_of_events` (Fake-Clock +60 s mit Events → kein `get_states`)
- `drift_watchdog_fetches_after_60s_silence` (+61 s ohne Events → `get_states` gesendet)
- `auth_invalid_stops_without_reconnect` (`auth_invalid`-Message → nach 35 s keine neue Verbindung)
- `reconnect_delays_follow_backoff` (WS-Close ×3 → Delays ∈ Backoff-Bereichen, steigen)
- `get_states_times_out_at_8s` (Fake-WS antwortet nie → kein Crash, Callback nicht ongerufen, Watchdog weiter)
- `state_event_updates_entity_states` (Entity-Event-Message → `entityStates` aktualisiert)

**`app/src/test/java/dev/finnley/pcswitch/state/StateCoalescingTest.kt`** (neu; testet die reinen Companion-Funktionen `buildSnapshot`/`shouldEmit`)
- `identical_snapshot_skips_emit`
- `changed_snapshot_emits_immediately`
- `identical_snapshot_heartbeats_after_30s`
- `build_prefers_state_entity_over_primary`
- `build_falls_back_to_primary_entity`
- `build_missing_entity_is_unknown`

**`app/src/test/java/dev/finnley/pcswitch/network/NetworkWatchdogTest.kt`** (neu; Fake-Source + Fake-Clock)
- `vpn_capability_sets_transport_flag`
- `vpn_capability_removed_clears_flag`
- `available_triggers_debounced_probe` (Fake-Clock +800 ms → genau 1 `onNetworkChange`)
- `burst_of_events_debounced_to_one` (5 Events in 500 ms → 1 Call)
- `stop_unregisters_source_and_cancels_pending`
- `start_fires_initial_probe`

**`app/src/test/java/dev/finnley/pcswitch/pcstatus/PcStatusResolverTest.kt`** (neu)
- `ha_on_wins_over_tcp_off`
- `ha_off_wins_over_tcp_on`
- `tcp_fallback_when_ha_null_or_empty`
- `tcp_fallback_when_ha_unknown`
- `stable_no_flap_100_iterations` (gleiche Inputs → gleiches Output ×100)

**`app/src/test/java/dev/finnley/pcswitch/state/PcTransitionTest.kt`** (neu; testet `PcTransition.isDone`)
- `boot_done_when_ha_on` / `boot_done_when_tcp_reachable`
- `shutdown_done_when_ha_off` / `shutdown_done_when_tcp_unreachable`
- `no_state_entity_pure_tcp`
- `not_done_mixed` (HA "off" + TCP reachable bei Boot → false)

### 7.2 Bestehende Tests — bleiben grün (keine Änderungen nötig)

| Test-Datei | Warum grün |
|------------|-----------|
| `state/SwitchLogicTest.kt` | Reine `SwitchConfig`-Logik (primary/stateEntity-Auswahl) — `SwitchConfig` wird nicht angefasst |
| `state/SwitchConfigSerializationTest.kt` | `SwitchConfig`-Serialisierung — unverändert |
| `state/SwitchManagerPersistenceTest.kt` | `SwitchManager`/DataStore — unverändert |
| `ui/MainScreenCarouselTest.kt` (Klasse `CarouselDataFlowTest`) | Reine Order/Navigation-Logik über `SwitchConfig`-Listen — unverändert |
| `ui/SettingsScreenDragDropTest.kt` | Reine Drag-Drop-Index-Logik — unverändert |

Kein bestehender Test referenziert `HaSession`-Interna, `probeJob`-Intervalle, `pcStatus`-String-Werte oder `emitEntityStates` — die geänderten Stellen sind intern/private.

### 7.3 Verifizierung

- **Per Schritt:** `./gradlew test` (alle grün) + Schritt-spezifischer manueller Smoke (Sektion 8).
- **Gesamt (S11):** Device-Smoke-Checkliste:
  1. Idle 10 min → kein Reconnect-Storm (HA-Log: ≤1 `get_states`/min), UI still (Compose-Profiler: <10 Recompositions/min).
  2. VPN connect → Mode `HomeVpn` in <3 s (vorher bis 90 s).
  3. Airplane-Mode → Mode `Offline` in <3 s, zurück → <3 s.
  4. PC boot/shutdown mit `stateEntity` → Status wechselt ohne Flip, Transition sichtbar.
  5. Switch-Name editieren → kein "unknown"-Flickern (State-Erhalt).
  6. "zuletzt aktualisiert" tickt, kein Layout-Shift, kein Hitch (Studio-Profil 30 s).
  7. Tile (QuickSettings) spiegelt Status nur bei echter Änderung (Logcat).

---

## Sektion 8: Implementierungs-Steps

Geordnet, klein, jeweils unabhängig verifizierbar. Jeder Step = 1:1 delegierbarer Implementierungs-Auftrag. Alle Steps: `./gradlew test` grün als Abschlusskriterium.

| # | Step | Dateien | Verifizierung (unabhängig) |
|---|------|---------|---------------------------|
| S1 | `ReconnectBackoff` (neue Datei, Sektion 2.2) + `ReconnectBackoffTest` (5 Tests) | `ha/ReconnectBackoff.kt`, `ha/ReconnectBackoffTest.kt` | `./gradlew test --tests "*ReconnectBackoffTest"` |
| S2 | `HaClient`: R11 (`AtomicInteger`, `@Volatile`), R10 (`withTimeout(8s)` auf `getStates/getEntityStates/getEntityIds`), R3 (`auth_invalid` → kein Reconnect), R2 (Backoff in `scheduleReconnect`, Reset bei `auth_ok`), `factory`-Injektion; `HaClientSessionTest` (Reconnect/auth/Timeout-Tests) | `ha/HaClient.kt`, `ha/HaClientSessionTest.kt` | `./gradlew test --tests "*HaClientSessionTest"`; manuell: falsches Token → App stoppt Retry (Logcat), UI zeigt Auth-Fehler |
| S3 | `HaClient`: R1+R6 (`subscribe_entities` mit `entity_ids`+`coalesce_messages:1`, 60s-Drift-Watchdog, Legacy-Fallback auf HA-Fehler); Tests (subscribe/drift) | `ha/HaClient.kt`, `ha/HaClientSessionTest.kt` | `./gradlew test`; manuell: HA-Developer-Tools → `subscribe_entities` sichtbar, kein 5s-`get_states`-Sturm |
| S4 | `NetworkEventSource` + `AndroidNetworkEventSource` + `NetworkWatchdog` (neue Dateien, Sektion 2.5) + `NetworkWatchdogTest` (6 Tests) | `network/NetworkEventSource.kt`, `network/NetworkWatchdog.kt`, `network/NetworkWatchdogTest.kt` | `./gradlew test --tests "*NetworkWatchdogTest"` |
| S5 | `PcStatusResolver` (neue Datei, Sektion 2.4) + `PcStatusResolverTest` (5 Tests) | `pcstatus/PcStatusResolver.kt`, `pcstatus/PcStatusResolverTest.kt` | `./gradlew test --tests "*PcStatusResolverTest"` |
| S6 | ViewModel-Coalescing (R5): `buildSnapshot`/`shouldEmit` (Companion), `emitEntityStates` umgebaut, `lastEmitAt`, `UiState.refreshing`; `StateCoalescingTest` (6 Tests) | `state/PcSwitchViewModel.kt`, `state/StateCoalescingTest.kt` | `./gradlew test`; manuell: Idle 2 min → Compose-Profiler: keine Recompositions außer Heartbeat (1/min) |
| S7 | ViewModel R12-lite: `startSessions` Entity-Set-Diff + State-Erhalt + `lastEntitySet`; `PcTransition`-Companion + `PcTransitionTest` (6 Tests) | `state/PcSwitchViewModel.kt`, `state/PcTransitionTest.kt` | `./gradlew test`; manuell: Switch-Edit → kein "unknown"-Flickern, `refreshing`-Dimming sichtbar |
| S8 | ViewModel kombiniert `pcStatus` (R7+R8): Resolver-Einsatz, adaptiver `pcStatusJob` (3s/15s/skip), HA-first-Loops in `bootPc`/`shutdownPc` (via `PcTransition.isDone`), `checkPcStatus` nutzt Resolver | `state/PcSwitchViewModel.kt` | `./gradlew test`; manuell: PC boot → Status wechselt stabil (kein Flip), Carousel + Karte + Button zeigen identisch |
| S9 | ViewModel WLAN/VPN (R4): `NetworkWatchdog`-Wiring in `init`/`onCleared`, `probeOnce` ohne `vpnBusy`-Skip, `vpnTransportActive` primär, `probeJob` → 30 s | `state/PcSwitchViewModel.kt` | `./gradlew test`; manuell: VPN connect → `HomeVpn` <3 s; Airplane → `Offline` <3 s |
| S10 | UI (R13): `relativeTime` + Zeitstempel (Statuszeile + PC-Karte), 15s-Tick, `loading`/`refreshing`-Dimming, `pcStatus`-Mapping ("on"/"off"/"unknown"), Carousel-Zeile 408 bleibt (Value jetzt kombiniert) | `ui/MainScreen.kt` | `./gradlew test`; manuell: Zeitstempel tickt ohne Shift, Dimming weich, kein Hitch (Profil 30 s) |
| S11 | E2E-Smoke (Device): komplette Checkliste Sektion 7.3 | — | Checkliste 1–7 bestanden |
| S12 | **(Separat, nur nach S1–S11 grün)** Version-Bump: `versionCode` 74 → **75**, `versionName` → **2.9.0**, `version.json` (Release-Repo `/home/finley/projects/pc-switch`) → `2.9.0` | `app/build.gradle.kts`, `/home/finley/projects/pc-switch/version.json` | `./gradlew assembleRelease` baut; `version.json` = 2.9.0 |

**Abhängigkeiten:** S1→S2→S3 (HaClient-Reihe); S4, S5 unabhängig; S6→S7→S8→S9 (ViewModel-Reihe, S9 baut auf S8); S10 nach S8 (braucht kombiniertes `pcStatus`); S11 nach allen; S12 separat.
**Uncommitted-Changes** (versionCode 74, release.sh): in S12 einfließen (74→75), bis dahin unangetastet.

---

## Anhang: Verifizierte IST-Fakten (HEAD `1a8e333`)

- `PcSwitchViewModel(app: Application) : AndroidViewModel(app)` (Zeile 69); `probeJob` 5 s (`delay(5_000)`, Zeile 116), `pcStatusJob` 10 s (`delay(10_000)`, Zeile 123).
- `checkPcStatus()` setzt `"reachable"/"unreachable"/"unknown"` (reines TCP, `PcStatusChecker.isReachable(ip)`, Port 22).
- `HaSession(url, isRelay, token, entities)` — `entities` wird **bereits** übergeben (Zeile 746/747), aber für `subscribe_entities` nicht genutzt; `subscribe_events` (alle Entities) + `get_states` im Watchdog **alle 5 s** (`HaClient.kt:280-285`, Kommentar "20s" veraltet).
- Reconnect: fixer `delay(2500)` (`HaClient.kt:165-173`); `auth_invalid` → `close(4001)` → `onClosed` → `scheduleReconnect` (endlos, `HaClient.kt:199-202`); `authFailed: MutableStateFlow` (Zeile 93/185/201).
- `onVpn()` = 3 GETs pro Aufruf (LAN-Base + 2 Relay-Pfade), jeder 5. s.
- `MainScreen.kt:408`: `val displayState = if (isPcSwitch && pcStatus != "unknown") pcStatus else state` (Carousel zeigt rohes TCP; Statuskarte zeigt HA-`state`).
- Manifest: `ACCESS_NETWORK_STATE` vorhanden (Kommentar "VPN-Transport-Erkennung"); `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES` vorhanden.
- Tests: JUnit4 + Robolectric 4.11.1 deklariert; **kein** Mocking-Framework (MockK/Mockito) in `app/build.gradle.kts` → neue Tests nutzen Fakes.
- `UiState.lastUpdate: Long` existiert (wird in `emitEntityStates` gesetzt, nie gerendert).
