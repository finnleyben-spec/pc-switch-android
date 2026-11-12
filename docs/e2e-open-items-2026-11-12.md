# E2E-Offene Punkte — pc-switch-android (Handover 2026-11-12)

Branch: `coder/e2e-bugfixes` — 8 Commits vor main (Stand: `29011a7`).
Status: **B1, B2, B5, B6, B7 = fixiert + verifiziert. B3 = fixiert (device-
Re-Check ausstehend). B4 = Code-Fix fertig, device-Verifikation ausstehend.**

## 1. B4: ON→OFF < 1 s device-verifizieren (Hauptpunkt)

**Root Cause (per Logcat-Nachweis, nicht Vermutung):**
- `LaunchedEffect(isOn)` schrieb beim State-Flip (optimistic-Update/WS-Push)
  per Spring in `animProgress` und übersteuerte damit den **laufenden Drag**
  für ~400 ms.
- Das Crossing (Schwelle 0.5) wurde vom manipulierten animierten Wert
  entschieden → verschob sich so weit, dass der OFF-Drag (400 ms adb-swipe)
  die 0.5 erst am Ende/über dem Drag-Ende erreichte → kein Press.
- Zusätzlich: `isOn` war `pointerInput`-Key → State-Flip mid-Drag
  **restartete den Detektor** und verwarf den laufenden Drag.

**Fix (Commit `29011a7`):** Crossing aus Fingerposition
(`dragStart` + delta/travel, Basis im `onDragStart` eingefroren), `isOn` via
State-Holder (kein Key mehr), `isDragging` stoppt die State-Sync-Animation,
Knob folgt Finger 1:1, bei Release Snap auf logischen State.

**Testaufbau (alles verifiziert, funktioniert):**
- AVD: `$HOME/.android-sdk/emulator/emulator -avd pcswitch -no-boot-anim -no-window`
- adb: `$HOME/.android-sdk/platform-tools/adb`
- APK: `./gradlew assembleDebug` → `adb install -r app/build/outputs/apk/debug/*.apk`
- Carousel-Swipe TestB4→TestWallbox: `adb shell input swipe 950 1200 150 1200 500`
- TestWallbox-Toggle: **y=1819** (Box 1740–1900 px, rootPos aus `PcSwitchDebug`-Log)
  - ON: `adb shell input swipe 195 1819 885 1819 400`
  - OFF: `adb shell input swipe 885 1819 300 1819 400`

**Akzeptanzkriterium:**
```
adb shell input swipe 195 1819 885 1819 400
sleep 0.2
adb shell input swipe 885 1819 300 1819 400
```
→ logcat MUSS zeigen: `press(input_button.pc_switch_test_on) -> bestätigt`
UND `press(input_button.pc_switch_test_off) -> bestätigt`,
→ HA `input_boolean.test_wallbox_switch` = off→on→off.
Vorher: ON ok, OFF fehlte (0.2 s), mit 1.1 s Pause ok.

**Debug:** Tag `PcSwitchDebug` — `onDragStart` (dragBase, startX, travel) +
`onDrag` (fingerP). Erwartet: beim OFF-Drag sinkt fingerP von ~1.0 unter 0.5
**während** des Drags (früher: erst nach dem Drag-Ende).

## 2. B3: Label-Check (2 min, mit neuem APK mitlaufen)
- TestB4 (dual, **ohne** stateEntity) → Status-Label muss „Unbekannt" zeigen,
  keine rohe ISO-Timestamp. Static verifiziert (uiautomator) vor dem B4-Fix.
- Fix: Commit `6561156` — `buildSnapshot` normalisiert on/off≠ → unknown.
  2 Unit-Tests in `StateCoalescingTest.kt`.

## 3. PcSwitchDebug-Logs entfernen (vor Final-Commit)
- `EntityToggle.kt`: BOUNDS-Log in `onGloballyPositioned`, `onDragStart`-Log,
  `onDrag`-Log. Bewusst in `29011a7` enthalten (Test-Hilfe).

## 4. Regressions-Checks (nach Gesture-Fix, jeweils 1–2 min)
- Einzel-ON- und Einzel-OFF-Drag auf TestWallbox + TestB4.
- Drag OHNE Crossing 0.5 → Knob snappt zurück, kein Press.
- `transitioning=true` (PC-Switch) → Toggle nicht bedienbar.
- Carousel-Swipe bleibt unverändert möglich (Toggle-Geste stört nicht).
- `./gradlew testDebugUnitTest` — 75 Tests, 0 Failures (Stand Handover).

## 5. Merge main + Push
- `coder/e2e-bugfixes` → main, danach Branch aufräumen.
- Release-Auslieferung (GitHub Pages/`release.sh`) erst NACH Punkt 1–4.

## HA-Test-Fixtures (dauerhaft, von Finnley freigegeben 2026-11-12)
- `automation.e2e_pc_switch_test_on_toggelt_wallbox_helper`
- `automation.e2e_pc_switch_test_off_toggelt_wallbox_helper`
- Entities (nur diese testen!): `input_boolean.test_wallbox_switch`,
  `input_button.pc_switch_test_on`, `input_button.pc_switch_test_off`
- Safety verifiziert: 35 Automationen/Scripts gescannt, 0 Referenzen auf die
  Test-Entities (`ha_safety.py`).
- HA 2026.10.4: `input_button` feuert KEINE Events (`async_press` leer) →
  Automations nutzen State-Trigger (Timestamp-Wechsel).
- Token: JWT in `shared_prefs/pcswitch.xml` (per `adb exec-out run-as
  dev.finnley.pcswitch cat ...` holen). HA 192.168.0.205:8123.
- Helfer-Scripts: `~/.hermes/profiles/coder/cache/scratch/ha_safety.py`,
  `ha_create2.py` (UUID-basiertes Create/Update).

## Entity-Setup in der App (DataStore)
- **TestB4:** dual, primary=`input_button.pc_switch_test_on`, secondary leer,
  **kein stateEntity** (deshalb B3-Repro).
- **TestWallbox:** dual, primary=`input_button.pc_switch_test_on`,
  secondary=`input_button.pc_switch_test_off`,
  stateEntity=`input_boolean.test_wallbox_switch`.

## 6. E2E-Verifizierung abgeschlossen (2026-11-12, AVD `pcswitch`, HA 192.168.0.205:8123)

**B4 (0.2-s-Schnelltoggle): PASS** — exakt nach §2 (swipe ON 400ms → sleep 0.2 →
swipe OFF 400ms, TestWallbox y=1819). Live-State-Sampling (100ms):
off (0.0s) → on (0.8s) → off (1.4s). Logcat: beide Presses bestätigt
(`press(input_button.pc_switch_test_on)` 19:23:51.333,
`press(input_button.pc_switch_test_off)` 19:23:51.997, Δ=664ms).
Hinweis: das erste „FAIL“ um 19:19 war ein Test-Setup-Fehler, kein App-Bug — der
Carousel stand auf der TestB4-Karte (keine secondary-Entity), der OFF-Drag hatte
nichts zu drücken. Auf der richtigen Karte läuft die Doku-Sequenz deterministisch.

**B3 (State-Entity unbekannt): PASS** — TestB4 (ohne stateEntity) zeigt
„Unbekannt“ in der UI (UI-Dump 2026-11-12 19:2x).

**Regressions: 9/9 PASS** (Skript `regressions.py`, 2026-11-12 ~19:3x):
- Einzel-ON TestWallbox: press(test_on) + Helper → on.
- Einzel-OFF TestWallbox: press(test_off) + Helper → off.
- Drag OHNE Crossing 0.5: kein Press, Knob snappt zurück, State unverändert.
- TestB4 Einzel-ON: press(test_on) bestätigt; OFF-Drag (kein secondary) →
  kein press(test_off), kein Crash (PID unverändert).
- Carousel-Swipe beide Richtungen (TestWallbox ↔ TestB4) weiterhin möglich.
- `transitioning=true`: nur statisch verifiziert (`pointerInput`-Key `transitioning` +
  `if (enabled && !transitioning)`-Guard in EntityToggle.kt) — mit den Test-Fixtures
  nicht auslösbar (isPcSwitch=false für TestWallbox/TestB4).

**Unit-Tests:** `./gradlew testDebugUnitTest` → 100 Tests, 0 Failures, 0 Errors,
0 Skipped (2026-11-12; Handover hatte 75 — Suite ist seither gewachsen).

**Debug-Logs:** `PcSwitchDebug` nicht mehr auf main (grep: 0 Treffer) —
Punkt 3 erledigt (Removal war Teil des merged v2.10.0).

**Auslieferung:** v2.10.0 merged nach main + GitHub-Release veröffentlicht
(2026-11-12, siehe T553).
