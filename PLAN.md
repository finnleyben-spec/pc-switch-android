# PC Switch v2 — Native Android App (Compose)

Stand: 2026-10-23. Branch: `coder/v2-native`. Dieses Doku ist die Arbeits-Spezifikation.

## Ziel
Komplett native Android-App (Jetpack Compose, kein WebView). Gleicher visueller Look wie die PWA.
Kern-Feature: Button **"Connect to home"** — schaltet Tailscale aus + startet OpenVPN Connect
(per Intent). Damit ist HA auch unterwegs im Heimnetz erreichbar (OpenVPN routet das komplette
Heimnetz, HA bleibt 192.168.0.205).

## Endpunkte (HA)
| Modus | URL | Wann |
|---|---|---|
| HOME (LAN) | `ws://192.168.0.205:8123/api/websocket` | zu Hause WLAN |
| HOME (VPN) | dieselbe URL | unterwegs + OpenVPN aktiv (routet ganzes Heimnetz) |
| REMOTE (Tailscale-Relay) | `wss://100.106.82.86:8123/api/websocket` | Tailscale an, PC läuft (Relay) |
| OFFLINE | — | nichts erreichbar → "Connect to home" sichtbar |

WLAN-vs-VPN-Unterscheidung: `ConnectivityManager` → aktive Network →
`networkCapabilities.getTransportInfo(NetworkCapabilities.TRANSPORT_VPN)` != null → "per VPN".

## Architektur
- Single Activity, Kotlin, Jetpack Compose + Material3 (eigene Farb-Tokens, kein M3-Default).
- Dependencies (build.gradle.kts): `com.android.application` + Kotlin-Plugin + Compose-Compiler-Plugin.
  Compose BOM `2025.01.00`, `activity-compose`, `material3`, `androidx.core:core-ktx:1.15.0`,
  `lifecycle-viewmodel-compose`, `com.squareup.okhttp3:okhttp:4.12.0`,
  `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` + `org.jetbrains.kotlin.plugin.serialization`.
- minSdk 26, targetSdk/compileSdk 35, **applicationId bleibt `dev.finnley.pcswitch`**
  (Update-Kompatibilität zu v1.x, gleiche Signing-Key!).
- **Alles WebView-Code aus MainActivity.kt wird ersetzt.**

## Dateistruktur (alle neu)
```
app/src/main/java/dev/finnley/pcswitch/
  MainActivity.kt        # setContent { PcSwitchApp() }
  PcSwitchApp.kt         # Root: Prefs laden → SetupScreen oder MainScreen
  ui/Design.kt           # Farben, Typo, gemeinsame Components (StatusDot, EntityCard, SwitchButton)
  ui/SetupScreen.kt      # Setup-Formular (HA + OpenVPN-Profile-ID)
  ui/MainScreen.kt       # Hauptscreen (Status, VPN-Button, An/Aus, Entity-Cards, Footer)
  state/Settings.kt      # SharedPreferences: lanBase, relayBase, token, onEntity, offEntity,
                         #   vpnProfileId (Default leer), setupDone
  ha/HaClient.kt         # OkHttp-WebSocket-Client (s.u.)
  vpn/VpnController.kt   # OpenVPN-Intents + Tailscale-Broadcasts (s.u.)
  update/UpdateManager.kt# version.json + APK-Download + Install
  state/PcSwitchViewModel.kt # State-Holding + Probing + Orchestrierung
app/src/main/res/xml/file_paths.xml  # FileProvider: filesDir/apk
app/src/main/res/values/strings.xml
app/src/main/AndroidManifest.xml
```

## Design-Tokens (1:1 aus der PWA)
```
paper   #F2EFE7   (Hintergrund)
ink     #1A1815   (Text)
inkSoft #5C564C   (sekundärer Text)
line    #D8D2C4   (Border)
accent  #B4552D   (An-Schalter, CTA)
ok      #4A7C59
warn    #B08A2E
err     #A33A2A
```
- Titel "PC Switch": Serif (FontFamily.Serif), ~34sp, Letter-Spacing -0.02em.
- Uppercase-Labels: 11sp, letter-spacing 0.14em, inkSoft, weight 600.
- Schalter: 2 Spalten, min ~150dp, Radius 12, Border 1.5dp line; IN: Border+Glyph accent,
  OFF: Border line. Glyph: Kreis 64dp, An = volle Scheibe accent mit weißem ⏻ (U+23FB),
  Aus = volle Scheibe ink mit weißem ✕. Label 22sp serif darunter. Press: leicht nach unten.
- Entity-Card: Radius 12, Border line, Paper-Hintergrund, Uppercase-Label + Entity-ID + letzter Wert.
- Footer: 11sp inkSoft, "Einstellungen" als Text-Button.
- Update-Button (Header rechts): Pill, Border line, "Update · x.y.z" — sichtbar wenn version.json
  neuere version_code hat.

## HA-WebSocket-Client (HaClient) — Verhalten 1:1 aus der PWA
- Connect: `ws(s)://<base>/api/websocket`, Timeout 4s.
- Auth: `{"type":"auth","access_token":"<token>"}` → auf `auth_ok` warten.
- Requests: `{"id":n,"type":"get_service_calls"}` NICHT — nur: `get_states` (beide Entities),
  `subscribe_events` mit `event_type=state_changed`. Antworten per `id` matchen (pending-Map).
- `callService`: `{"id":n,"type":"call_service","service":"button.press"|"input_button.press",
  "entity_id":...}` (PWA-Logik: Domain aus Entity-Namen ableiten).
- Watchdog: alle 20s `get_states`-Probe; WebSocket-Close → nach 2.5s Reconnect (nur wenn Base ok).
- Callbacks (Flow/StateFlow): `entityStates: Map<String,String>`, `lastUpdate: Long`, `authOk`.
- Probing: `probe(url): Boolean` — HTTP-GET auf `<base>/api/` mit kurzem Timeout (3s)
  (200/401/403 = erreichbar, 401 ist OK = Token prüfen separat).

## VpnController — exakte Intents (VERIFIZIERT gegen Offizielle Doku)
### Connect to home (Reihenfolge strikt):
1. **Tailscale DISCONNECT** (falls installiert):
   ```kotlin
   val i = Intent("com.tailscale.ipn.DISCONNECT_VPN")
       .setComponent(ComponentName("com.tailscale.ipn", "com.tailscale.ipn.IPNReceiver"))
   context.sendBroadcast(i)  // try/catch, PackageNotFoundException ignorieren
   ```
2. **OpenVPN CONNECT**:
   ```kotlin
   val i = Intent("net.openvpn.openvpn.CONNECT")
       .setComponent(ComponentName("net.openvpn.openvpn", "net.openvpn.unified.MainActivity"))
       .putExtra("net.openvpn.openvpn.AUTOSTART_PROFILE_ID", profileId)
       .putExtra("net.openvpn.openvpn.AUTOCONNECT", true)
       .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   context.startActivity(i)  // try/catch
   ```
   `profileId` aus Settings. **Leer → Toast "OpenVPN Profile ID fehlt (Einstellungen)" + kein Start.**
3. Warten: Status "Verbinde Heimnetz…" — ViewModel probt LAN-Endpoint alle 3s bis 90s.

### Disconnect (Reihenfolge strikt):
1. **OpenVPN DISCONNECT**:
   ```kotlin
   Intent("net.openvpn.openvpn.DISCONNECT")
       .setComponent(ComponentName("net.openvpn.openvpn", "net.openvpn.unified.MainActivity"))
       .putExtra("net.openvpn.openvpn.STOP", true)
       .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   ```
2. **Tailscale CONNECT**: Broadcast `com.tailscale.ipn.CONNECT_VPN` an denselben Receiver.

## UpdateManager
- URL: `https://finnleyben-spec.github.io/pc-switch/version.json`
- Schema: `{"apk":{"version_name":"x.y.z","version_code":N,"url":"https://...apk","notes":"..."}}`
- Bei Start (und nach VPN-Aktion) abfragen: `version_code > BuildConfig.VERSION_CODE` →
  `updateAvailable` im ViewModel.
- Install-Flow: OkHttp-Streaming-Download nach `filesDir/apk/pc-switch-v<name>.apk`
  (Fortschritt im ViewModel), dann:
  ```kotlin
  val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
  val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
      .addFlags(FLAG_GRANT_READ_URI_PERMISSION or FLAG_ACTIVITY_NEW_TASK)
  ```
  Android 11+: vorher `ctx.resolveActivity(i)` null-Check → falls null:
  `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` mit PackageInfo.URI zum Request.
- Manifest: `<provider android:name="androidx.core.content.FileProvider"
  android:authorities="${applicationId}.fileprovider" android:grantUriPermissions="true"
  android:exported="false"><meta-data ... resource=@xml/file_paths></provider>`
  + `file_paths.xml`: `<files-path name="apk" path="apk/"/>`
  + Permission `REQUEST_INSTALL_PACKAGES`.

## AndroidManifest-Permissions
- `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`.
- **KEINE** weiteren Permissions (keine Benachrichtigungs-Zugriffe etc.).

## ViewModel-State
```kotlin
sealed class Mode { HomeLan; HomeVpn; Remote; Offline; Connecting }
data class UiState(
  val mode: Mode = Offline,
  val vpnBusy: Boolean = false,          // während Connect/Disconnect-Sequenz
  val onState: String = "unknown",       // Entity-States
  val offState: String = "unknown",
  val lastUpdate: Long = 0,
  val updateAvailable: UpdateInfo? = null,
  val downloadProgress: Int? = null,     // 0..100 während APK-Download
  val statusNote: String = ""            // z.B. "WLAN aus", "Token prüfen", "VPN wird gestartet"
)
```
- Probe-Loop (lifecycleScope, alle 20s): LAN → (falls fail) Relay. Ergebnis + VPN-Transport
  → Mode. `Connecting`: nur nach VPN-Button-Tap, endet auf LAN-Success (→ HomeVpn) oder Timeout.
- An/Aus-Buttons: `enabled = mode != Offline && !vpnBusy`; OFF-Button zusätzlich nur wenn
  Relay/LAN up ist (PWA-Logik: Aus braucht HA-Verbindung).

## UI: MainScreen
- Header: "PC Switch" + Version-Badge (`BuildConfig.VERSION_NAME`) + Update-Pill.
- Status-Row: Dot (ok/warn/err) + Text + note.
  - HomeLan: "Verbunden · Heimnetz (WLAN)" ok
  - HomeVpn: "Verbunden · Heimnetz per VPN" ok
  - Remote: "Remote · Tailscale-Relay" warn
  - Offline: "Offline" err + note "Heimnetz nicht erreichbar"
  - Connecting: "Verbinde Heimnetz…" warn
- **VPN-CTA** (prominent, über den Schaltern):
  - Offline (und nicht WLAN-zuhause): Button "Connect to home" (accent, groß)
  - HomeVpn: Button "Heimnetz trennen" (ink, Outline) → Disconnect-Sequenz
  - HomeLan / Remote: CTA verborgen
- Schalter-Grid (An/Aus) + Entity-Cards + Footer wie PWA.
- SetupScreen: Felder LAN-Base (Default 192.168.0.205:8123), Relay-Base (Default 100.106.82.86),
  Token, Wake-Entity, Shutdown-Entity, **OpenVPN Profile ID** (Hint: "OpenVPN Connect → Profil
  bearbeiten → Profile ID kopieren"). Button "Speichern & Verbinden" → LAN probe (4s) →
  MainScreen (Setup bleibt erreichbar via Einstellungen, setupDone wird gesetzt).

## Build & Commits (Pflicht)
- `./gradlew assembleDebug` MUSS grün sein (kein `--no-daemon`).
- Commits auf Branch `coder/v2-native` (vor jedem Commit `git branch --show-current` prüfen).
- **NICHT pushen, NICHT mergen.**
- Commit 1: "v2: natives Compose-Gerüst (Design, Prefs, Screens, Manifest, FileProvider)"
- Commit 2: "v2: HaClient (WS-Auth, Subscribe, Watchdog) + ViewModel + Probing"
- Commit 3: "v2: VpnController (OpenVPN-Intent, Tailscale-Broadcast) + UpdateManager"

## Abnahme (macht Hermes, nicht OpenCode)
- `assembleDebug` grün, Install auf Emulator, `adb logcat` ohne FATAL EXCEPTION,
  UI rendert (Setup → Main), VPN-Button zeigt bei Offline.
