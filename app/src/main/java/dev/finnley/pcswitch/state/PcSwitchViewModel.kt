package dev.finnley.pcswitch.state

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.finnley.pcswitch.BuildConfig
import dev.finnley.pcswitch.ha.HaClient
import dev.finnley.pcswitch.ha.HaClient.HaSession
import dev.finnley.pcswitch.notifications.NotificationHelper
import dev.finnley.pcswitch.network.AndroidNetworkEventSource
import dev.finnley.pcswitch.network.NetworkWatchdog
import dev.finnley.pcswitch.pcstatus.PcStatusChecker
import dev.finnley.pcswitch.pcstatus.PcStatusResolver
import dev.finnley.pcswitch.state.SwitchManager
import dev.finnley.pcswitch.state.SwitchConfig
import dev.finnley.pcswitch.update.UpdateManager
import dev.finnley.pcswitch.vpn.VpnController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Mode {
    data object HomeLan : Mode()
    data object HomeVpn : Mode()
    data object Remote : Mode()
    data object Offline : Mode()
    data object Connecting : Mode()
}

/** Immutable Snapshot der UI-relevanten Entity-States (Coalescing, R5). */
data class EntitySnapshot(
    val switchStates: Map<String, String>,
    val onState: String,
    val offState: String
)

data class UiState(
    val inSetup: Boolean = false,
    val inSettings: Boolean = false,
    val inSwitches: Boolean = false,
    val loading: Boolean = true,
    // Refresh-Indikator (Design S6): true während Probe/Session-Rebuild.
    // Default-Wert hält die Public-API stabil.
    val refreshing: Boolean = false,
    val authError: Boolean = false,
    val authErrorDismissed: Boolean = false,
    val mode: Mode = Mode.Offline,
    val vpnBusy: Boolean = false,
    val switches: List<SwitchConfig> = emptyList(),
    val currentSwitchIndex: Int = 0,
    val switchStates: Map<String, String> = emptyMap(),
    val lastAction: Map<String, String> = emptyMap(),
    val onState: String = "unknown",
    val offState: String = "unknown",
    val lastUpdate: Long = 0,
    val updateAvailable: UpdateManager.UpdateInfo? = null,
    val updateChecking: Boolean = false,
    val downloadProgress: Int? = null,
    val downloadFailed: Boolean = false,
    val statusNote: String = "",
    val pcStatus: String = "unknown",  // "on" | "off" | "unknown" (R7: PcStatusResolver)
    // PC toggle transition state: idle, booting, shutting_down
    val pcToggleState: String = "idle",
    val pcToggleError: String = "",
    // Entity search state
    val entitySearchLoading: Boolean = false,
    val entitySearchError: String? = null,
    val entitySearchResults: List<dev.finnley.pcswitch.ha.HaClient.EntityState> = emptyList(),
    val showEntitySearch: Boolean = false,
    val entitySearchTarget: String = ""  // "primary", "secondary", "state", "on", "off"
)

class PcSwitchViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app
    val settings = Settings(ctx)
    val switchManager = SwitchManager(ctx)
    private val updater = UpdateManager(ctx)

    private val _state = MutableStateFlow(
        UiState(inSetup = !settings.setupDone || settings.token.isBlank())
    )
    val state: StateFlow<UiState> = _state

    /** Primäres VPN-Signal (S9/R4): true, wenn das Default-Netzwerk TRANSPORT_VPN
     *  hat. Eventaktuell via NetworkWatchdog (750ms-Debounce) — die UI nutzt es
     *  als Transport-Indikator statt vpnBusy (vpnBusy bleibt Button-Gate für
     *  VPN-Switch-Aktionen connectToHome/disconnectHome). */
    val vpnTransportActive: StateFlow<Boolean>
        get() = networkWatchdog.vpnTransportActive

    private var lanSession: HaSession? = null
    private var relaySession: HaSession? = null
    private var probeJob: Job? = null
    private var pcStatusJob: Job? = null
    private var pcToggleJob: Job? = null
    private var vpnJob: Job? = null
    private var updateJob: Job? = null
    private var sessionJobs = listOf<Job>()

    // Coalescing (R5): Zeitpunkt des letzten Emits. Echte Änderungen werden
    // immer sofort emittiert, identische Snapshots maximal 1x/30s (Heartbeat).
    private var lastEmitAt: Long = 0

    // R12-lite: zuletzt genutztes Entity-Set. Identisches Set + lebende
    // Session -> kein Rebuild (kein Teardown, kein "unknown"-Flicker).
    private var lastEntitySet: Set<String>? = null

    // S9 (R4): WLAN/VPN eventgetrieben. Netzwerk-Events (WLAN-Wechsel, VPN
    // up/down) triggern debounced sofortige Probes; vpnTransportActive ist
    // das primäre VPN-Signal für die UI (ersetzt 90s-vpnBusy als Indikator).
    private val networkWatchdog = NetworkWatchdog(AndroidNetworkEventSource(ctx), viewModelScope)

    private val lanBase get() = settings.lanBase
    private val relayBase get() = settings.relayBase

    init {
        // S9 (R4): WLAN/VPN-Events -> sofortiger Probe (debounced 750ms).
        // Callback läuft auf Dispatchers.IO (kein Main-Thread-Block); der
        // Initial-Probe bei start() deckt Events vor dem ersten Callback ab.
        networkWatchdog.start {
            viewModelScope.launch(Dispatchers.IO) { probeOnce() }
        }
        // Don't start sessions yet — wait for switches to load first
        probeJob = viewModelScope.launch(Dispatchers.IO) {
            var firstProbe = true
            while (true) {
                try {
                    probeOnce()
                    if (firstProbe && !_state.value.inSetup) {
                        firstProbe = false
                        _state.update { it.copy(loading = false) }
                    }
                } catch (e: Exception) {
                    // probeOnce can throw if HA probe fails; don't crash the probe loop
                    if (firstProbe && !_state.value.inSetup) {
                        firstProbe = false
                        _state.update { it.copy(loading = false) }
                    }
                }
                // S9 (R4): 30s Safety-Net — Primärweg ist jetzt eventgetrieben
                // (NetworkWatchdog triggert sofortige Probes bei WLAN/VPN-Events).
                delay(30_000)
            }
        }
        // Adaptive PC-Status-Check (R8): ~3s während Transition (booting/
        // shutting_down), ~15s im Steady-State; kein Probe bei offline/setup
        // (kein Netzwerk -> Probe sinnlos, Status wird nicht aktualisiert).
        pcStatusJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val s = _state.value
                if (s.inSetup || s.mode == Mode.Offline) {
                    delay(15_000)
                    continue
                }
                val inTransition =
                    s.pcToggleState == "booting" || s.pcToggleState == "shutting_down"
                delay(if (inTransition) 3_000 else 15_000)
                checkPcStatus()
            }
        }
        // Load switches and observe changes — start sessions after switches load
        observeSwitches()
        // Check for updates on startup
        viewModelScope.launch {
            delay(2000) // Wait a bit after startup
            checkForUpdate()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // S9 (R4): sauber stoppen — Watchdog deregistriert den NetworkCallback,
        // Jobs + HA-Sessions laufen nicht weiter (kein Zombie-Polling).
        networkWatchdog.stop()
        probeJob?.cancel()
        pcStatusJob?.cancel()
        pcToggleJob?.cancel()
        vpnJob?.cancel()
        updateJob?.cancel()
        sessionJobs.forEach { it.cancel() }
        lanSession?.stop()
        relaySession?.stop()
    }

    // ---------- Connect to home / Heimnetz trennen ----------

    fun connectToHome() {
        if (vpnJob?.isActive == true) return
        _state.update {
            it.copy(vpnBusy = true, mode = Mode.Connecting, statusNote = "Starte OpenVPN…")
        }
        vpnJob = viewModelScope.launch(Dispatchers.IO) {
            val err = VpnController.connectToHome(ctx, settings.vpnProfileId)
            if (err != null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                }
                _state.update {
                    it.copy(
                        vpnBusy = false,
                        mode = Mode.Offline,
                        statusNote = err
                    )
                }
                return@launch
            }
            _state.update { it.copy(statusNote = "Verbinde Heimnetz…") }
            // LAN alle 3s proben, max. 90s — dann HomeVpn oder wieder Offline.
            for (i in 0 until 30) {
                delay(3000)
                if (HaClient.probe(lanBase, false)) {
                    _state.update {
                        it.copy(vpnBusy = false, mode = Mode.HomeVpn, statusNote = "")
                    }
                    return@launch
                }
            }
            _state.update {
                it.copy(
                    vpnBusy = false,
                    mode = Mode.Offline,
                    statusNote = "Heimnetz nicht erreichbar"
                )
            }
        }
    }

    fun disconnectHome() {
        if (vpnJob?.isActive == true) return
        _state.update { it.copy(vpnBusy = true, statusNote = "Trenne Heimnetz…") }
        vpnJob = viewModelScope.launch(Dispatchers.IO) {
            VpnController.disconnectHome(ctx)
            // Immediately re-probe to detect VPN disconnection
            _state.update { it.copy(vpnBusy = false, statusNote = "") }
            probeOnce()
        }
    }

    // ---------- PC an / aus ----------

    fun pressOn() = pressConfirmed(settings.onEntity)
    fun pressOff() = pressConfirmed(settings.offEntity)

    /** Press the primary (on) button of the current switch. For PC switches, checks actual status first (async). */
    fun pressCurrentSwitchOn() {
        val switches = _state.value.switches
        if (switches.isEmpty()) return
        val switch = switches[_state.value.currentSwitchIndex]
        val isPcSwitch = switch.name.lowercase().contains("pc")

        // If already transitioning, ignore
        if (_state.value.pcToggleState != "idle") return

        if (isPcSwitch) {
            // Check PC status asynchronously to avoid blocking the UI thread
            viewModelScope.launch {
                val reachable = withContext(Dispatchers.IO) {
                    PcStatusChecker.isReachable(settings.pcIp)
                }
                if (reachable) {
                    // PC already on — just toggle normally (no booting state)
                    pressConfirmed(switch.primaryEntity, switch.id, "on", switch.name)
                } else {
                    // PC is off — start boot flow
                    bootPc(switch)
                }
            }
        } else {
            pressConfirmed(switch.primaryEntity, switch.id, "on", switch.name)
        }
    }

    /** Press the secondary (off) button of the current switch. For PC switches, checks actual status first (async). */
    fun pressCurrentSwitchOff() {
        val switches = _state.value.switches
        if (switches.isEmpty()) return
        val switch = switches[_state.value.currentSwitchIndex]
        val isPcSwitch = switch.name.lowercase().contains("pc")

        // If already transitioning, ignore
        if (_state.value.pcToggleState != "idle") return

        if (isPcSwitch) {
            // Check PC status asynchronously to avoid blocking the UI thread
            viewModelScope.launch {
                val reachable = withContext(Dispatchers.IO) {
                    PcStatusChecker.isReachable(settings.pcIp)
                }
                if (reachable) {
                    // PC is on — start shutdown flow
                    shutdownPc(switch)
                } else {
                    // PC already off — just toggle normally (no shutting_down state)
                    pressConfirmed(switch.primaryEntity, switch.id, "off", switch.name)
                }
            }
        } else {
            val entity = if (switch.type == "dual") {
                if (switch.secondaryEntity.isNotBlank()) {
                    switch.secondaryEntity
                } else {
                    // B4: Ohne OFF-Entity früher: primary (ON!) wird erneut gedrückt —
                    // stiller Fehltrigger. Jetzt: Action überspringen + Hinweis.
                    Log.w(TAG, "OFF for \"${switch.name}\": no OFF entity configured — action skipped")
                    Toast.makeText(ctx, "Kein OFF-Entity hinterlegt", Toast.LENGTH_SHORT).show()
                    return
                }
            } else switch.primaryEntity
            pressConfirmed(entity, switch.id, "off", switch.name)
        }
    }

    /**
     * B4-Fix: Press mit Zustellbestätigung. HaSession.press() wartet auf die HA-
     * Result-Antwort (5s-Timeout; timeout → Zombie-Session wird erkannt + Reconnect).
     * Optimistischer UI-State wird NUR gesetzt, wenn HA die Zustellung bestätigt.
     * Bei Fehler: bis zu 3 Auto-Retries (3s Abstand), dann Toast — nie still verwerfen.
     */
    private fun pressConfirmed(
        entity: String,
        switchId: String? = null,
        direction: String? = null,
        switchName: String? = null,
    ) {
        if (entity.isBlank()) return
        viewModelScope.launch {
            var ok = sendPress(entity)
            var attempt = 0
            while (!ok && attempt < 3) {
                attempt++
                delay(3_000)
                ok = sendPress(entity)
            }
            if (ok) {
                _state.update {
                    it.copy(
                        lastAction = if (switchId != null && direction != null)
                            it.lastAction + (switchId to direction) else it.lastAction,
                        switchStates = if (switchId != null && direction != null)
                            it.switchStates + (switchId to direction) else it.switchStates
                    )
                }
                if (switchName != null && direction != null) {
                    broadcastTileState(direction == "on", switchName)
                }
            } else {
                Log.w(TAG, "press($entity) nach ${attempt + 1} Versuchen nicht zugestellt — keine Zustandsänderung")
                Toast.makeText(ctx, "HA nicht erreichbar – Action nicht gesendet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** WA: HA-Antwort abwarten. false = keine Session / send failed / Timeout (Zombie). */
    private suspend fun sendPress(entity: String): Boolean {
        val s = activeSession()
        if (s == null) {
            Log.w(TAG, "press($entity): no active HA session")
            return false
        }
        return withContext(Dispatchers.IO) { s.press(entity) }
    }

    /** Boot PC flow: send WoL, wait for reachable, or timeout at 60s. */
    private fun bootPc(switch: SwitchConfig) {
        _state.update {
            it.copy(
                pcToggleState = "booting",
                pcToggleError = ""
            )
        }

        pcToggleJob?.cancel()
        pcToggleJob = viewModelScope.launch(Dispatchers.IO) {
            // B4: WoL-Paket zählt nur als gesendet, wenn HA die Zustellung bestätigt
            // (5s-Warte auf Result). Sonst sofort Fehler — nicht 60s auf ein Paket
            // warten, das nie zugestellt wurde.
            val ok = sendPress(switch.primaryEntity)
            if (!ok) {
                _state.update {
                    it.copy(
                        pcToggleState = "idle",
                        pcToggleError = "HA nicht erreichbar – PC nicht gestartet"
                    )
                }
                Toast.makeText(ctx, "HA nicht erreichbar – PC nicht gestartet", Toast.LENGTH_LONG).show()
                return@launch
            }
            val timeoutMs = 60_000L
            val start = System.currentTimeMillis()
            val hasStateEntity = switch.stateEntity.isNotBlank()
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    delay(2000)
                    val haState = if (hasStateEntity)
                        activeSession()?.entityStates?.value?.get(switch.stateEntity)
                    else null
                    val tcp = PcStatusChecker.isReachable(settings.pcIp)
                    if (PcTransition.isDone(haState, tcp, PcTransition.Phase.BOOT, hasStateEntity)) {
                        // Boot fertig (HA-first, R12)
                        _state.update {
                            it.copy(
                                pcToggleState = "idle",
                                pcStatus = PcStatusResolver.ON
                            )
                        }
                        broadcastTileState(true, switch.name)
                        return@launch
                    }
                } catch (e: Exception) {
                    // Ignore individual check failures
                }
            }
            // Timeout — revert
            _state.update {
                it.copy(
                    pcToggleState = "idle",
                    pcToggleError = "PC bootete nicht innerhalb von 60 Sekunden",
                    pcStatus = PcStatusResolver.OFF
                )
            }
            Toast.makeText(ctx, "PC bootete nicht rechtzeitig", Toast.LENGTH_LONG).show()
        }
    }

    /** Shutdown PC flow: press off, wait for unreachable, or timeout at 30s. */
    private fun shutdownPc(switch: SwitchConfig) {
        _state.update {
            it.copy(
                pcToggleState = "shutting_down",
                pcToggleError = ""
            )
        }

        pcToggleJob?.cancel()
        pcToggleJob = viewModelScope.launch(Dispatchers.IO) {
            // B4: Shutdown-Command zählt nur als gesendet, wenn HA bestätigt.
            val ok = sendPress(switch.secondaryEntity)
            if (!ok) {
                _state.update {
                    it.copy(
                        pcToggleState = "idle",
                        pcToggleError = "HA nicht erreichbar – Shutdown nicht gesendet"
                    )
                }
                Toast.makeText(ctx, "HA nicht erreichbar – Shutdown nicht gesendet", Toast.LENGTH_LONG).show()
                return@launch
            }
            val timeoutMs = 30_000L
            val start = System.currentTimeMillis()
            val hasStateEntity = switch.stateEntity.isNotBlank()
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    delay(2000)
                    val haState = if (hasStateEntity)
                        activeSession()?.entityStates?.value?.get(switch.stateEntity)
                    else null
                    val tcp = PcStatusChecker.isReachable(settings.pcIp)
                    if (PcTransition.isDone(haState, tcp, PcTransition.Phase.SHUTDOWN, hasStateEntity)) {
                        // Shutdown fertig (HA-first, R12)
                        _state.update {
                            it.copy(
                                pcToggleState = "idle",
                                pcStatus = PcStatusResolver.OFF
                            )
                        }
                        broadcastTileState(false, switch.name)
                        return@launch
                    }
                } catch (e: Exception) {
                    // Ignore individual check failures
                }
            }
            // Timeout — revert
            _state.update {
                it.copy(
                    pcToggleState = "idle",
                    pcToggleError = "PC shut down nicht innerhalb von 30 Sekunden",
                    pcStatus = PcStatusResolver.ON
                )
            }
            Toast.makeText(ctx, "PC shut down nicht rechtzeitig", Toast.LENGTH_LONG).show()
        }
    }

    private fun broadcastTileState(isOn: Boolean, switchName: String) {
        val intent = android.content.Intent("dev.finnley.pcswitch.TILE_STATE_CHANGED")
            .putExtra("is_on", isOn)
            .putExtra("switch_name", switchName)
            .setPackage(ctx.packageName)
        ctx.sendBroadcast(intent)
    }

    // ---------- Switch Carousel ----------

    fun nextSwitch() {
        val st = _state.value
        if (st.switches.isEmpty()) return
        val next = (st.currentSwitchIndex + 1) % st.switches.size
        _state.update { it.copy(currentSwitchIndex = next) }
    }

    fun prevSwitch() {
        val st = _state.value
        if (st.switches.isEmpty()) return
        val prev = (st.currentSwitchIndex - 1 + st.switches.size) % st.switches.size
        _state.update { it.copy(currentSwitchIndex = prev) }
    }

    fun setSwitchIndex(index: Int) {
        _state.update { st ->
            if (index >= 0 && index < st.switches.size) {
                st.copy(currentSwitchIndex = index)
            } else {
                st
            }
        }
    }

    // ---------- Switch Management ----------

    fun addSwitch(name: String, type: String, primaryEntity: String, secondaryEntity: String, stateEntity: String = "") {
        viewModelScope.launch {
            switchManager.addSwitch(name, type, primaryEntity, secondaryEntity, stateEntity)
        }
    }

    fun updateSwitch(switchConfig: SwitchConfig) {
        viewModelScope.launch {
            switchManager.updateSwitch(switchConfig)
        }
    }

    /**
     * Beobachtet die Switch-Config aus dem DataStore.
     *
     * B7: Korrupte/lesbar-unmöglche `switches.preferences_pb` (z.B. durch
     * externe Manipulation oder abgebrochenen Write) warf bisher eine
     * Exception im Collector und crash-te die App. Jetzt: Datei zurücksetzen
     * und einmal neu beobachten.
     */
    private fun observeSwitches() {
        viewModelScope.launch {
            try {
                switchManager.switchesFlow().collect { switches ->
                    _state.update { st ->
                        st.copy(switches = switches)
                    }
                    // Start/restart sessions whenever switches change so entity subscriptions are correct
                    startSessions()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Switch-DataStore unreadable (${e::class.simpleName}: ${e.message}) — resetting and retrying", e)
                resetCorruptSwitchStore()
                observeSwitches()
            }
        }
    }

    /** Löscht die (potenziell korrupten) DataStore-Dateien; DataStore legt sie neu an. */
    private fun resetCorruptSwitchStore() {
        listOf("switches.preferences_pb", "switches.preferences_pb-wal").forEach { name ->
            try {
                File(ctx.filesDir, "datastore/$name").delete()
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete datastore file $name: ${e.message}")
            }
        }
    }

    fun removeSwitch(id: String) {
        viewModelScope.launch {
            val st = _state.value
            switchManager.removeSwitch(id)
            // Adjust current index if needed
            if (st.switches.size <= st.currentSwitchIndex) {
                _state.update { it.copy(currentSwitchIndex = 0) }
            }
        }
    }

    fun reorderSwitches(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            switchManager.reorderSwitches(fromIndex, toIndex)
        }
    }

    // ---------- Entity Search ----------

    fun openEntitySearch(target: String) {
        _state.update { it.copy(showEntitySearch = true, entitySearchTarget = target) }
        fetchEntities()
    }

    fun closeEntitySearch() {
        _state.update { it.copy(showEntitySearch = false) }
    }

    fun onEntitySelected(entityId: String) {
        // The caller will handle the entity selection based on target
        closeEntitySearch()
    }

    private fun fetchEntities() {
        viewModelScope.launch {
            _state.update { it.copy(entitySearchLoading = true, entitySearchError = null) }
            try {
                val session = activeSession()
                if (session == null || !session.alive.value) {
                    _state.update {
                        it.copy(
                            entitySearchLoading = false,
                            entitySearchError = "Keine Verbindung zu Home Assistant"
                        )
                    }
                    return@launch
                }
                val entities = session.getEntityStates()
                _state.update { it.copy(entitySearchLoading = false, entitySearchResults = entities) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        entitySearchLoading = false,
                        entitySearchError = "Fehler beim Laden der Entities: ${e.message}"
                    )
                }
            }
        }
    }

    // ---------- Setup ----------

    fun saveSetup(
        lan: String,
        relay: String,
        token: String,
        on: String,
        off: String,
        vpnId: String,
        pcIp: String = ""
    ) {
        settings.lanBase = lan
        settings.relayBase = relay
        settings.token = token
        settings.onEntity = on
        settings.offEntity = off
        settings.vpnProfileId = vpnId
        if (pcIp.isNotBlank()) {
            settings.pcIp = pcIp
        }
        settings.setupDone = true
        // Clear auth error when token is updated
        _state.update { it.copy(inSetup = token.isBlank(), authError = false, authErrorDismissed = false) }
        startSessions()
        viewModelScope.launch(Dispatchers.IO) { probeOnce() }
        viewModelScope.launch(Dispatchers.IO) { checkPcStatus() }
        checkForUpdate()
    }

    fun dismissAuthError() {
        _state.update { it.copy(authErrorDismissed = true) }
    }

    fun saveNetworkSettings(lan: String, relay: String) {
        settings.lanBase = lan
        settings.relayBase = relay
        startSessions()
        viewModelScope.launch(Dispatchers.IO) { probeOnce() }
    }

    fun savePcIp(ip: String) {
        settings.pcIp = ip
        viewModelScope.launch(Dispatchers.IO) { checkPcStatus() }
    }

    /**
     * Kombiniert PC-Status (R7/R8): HA-StateEntity (primary) + TCP-Fallback
     * -> genau "on" | "off" via PcStatusResolver. Ohne StateEntity rein TCP.
     * Entity-State kommt aus der aktiven Session (LAN vor Relay).
     */
    private fun checkPcStatus() {
        try {
            val st = _state.value
            val stateEntity = st.switches.firstOrNull { it.stateEntity.isNotBlank() }?.stateEntity
            val haState = stateEntity
                ?.let { activeSession()?.entityStates?.value?.get(it) }
            val reachable = PcStatusChecker.isReachable(settings.pcIp)
            val resolved = PcStatusResolver.resolve(haState, reachable)
            Log.d(TAG, "pcStatus: ha=${haState ?: "-"} tcp=${if (reachable) "reachable" else "unreachable"} -> $resolved")
            _state.update {
                it.copy(pcStatus = resolved)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkPcStatus fehlgeschlagen: ${e.message}", e)
            _state.update { it.copy(pcStatus = PcStatusResolver.UNKNOWN) }
        }
    }

    fun openSetup() {
        _state.update { it.copy(inSetup = true) }
    }

    fun closeSetup() {
        _state.update { it.copy(inSetup = false) }
    }

    fun openSettings() {
        _state.update { it.copy(inSettings = true) }
    }

    fun closeSettings() {
        _state.update { it.copy(inSettings = false) }
    }

    fun openSwitches() {
        _state.update { it.copy(inSwitches = true) }
    }

    fun closeSwitches() {
        _state.update { it.copy(inSwitches = false) }
    }

    // ---------- Deep Link Handlers ----------

    /**
     * Handle a setup deep link. Parses the config and applies it.
     */
    fun handleSetupDeepLink(
        haUrl: String?,
        haToken: String?,
        switches: List<dev.finnley.pcswitch.deeplink.DeepLinkHandler.SwitchConfig>?,
        autoConnect: Boolean
    ) {
        // Save HA connection settings
        if (!haUrl.isNullOrBlank()) {
            settings.lanBase = haUrl
        }
        if (!haToken.isNullOrBlank()) {
            settings.token = haToken
        }

        // Save switches if provided
        if (!switches.isNullOrEmpty()) {
            viewModelScope.launch {
                val switchConfigs = switches.map { sw ->
                    dev.finnley.pcswitch.state.SwitchConfig.newSwitch(
                        id = "switch_${System.currentTimeMillis()}",
                        name = sw.name,
                        type = sw.type,
                        primaryEntity = sw.primaryEntity,
                        secondaryEntity = sw.secondaryEntity
                    )
                }
                switchManager.saveSwitches(switchConfigs)
            }
        }

        settings.setupDone = true
        _state.update { it.copy(inSetup = false) }
        startSessions()

        if (autoConnect) {
            connectToHome()
        }
    }

    /**
     * Handle a trigger deep link. Finds the switch by name or index and triggers it.
     */
    fun handleTriggerDeepLink(press: String, switchId: String?) {
        val st = _state.value
        if (st.switches.isEmpty()) return

        // Find switch by name or index
        val targetSwitch = when {
            switchId == null || switchId.isBlank() -> st.switches.firstOrNull()
            else -> {
                // Try as index first
                switchId.toIntOrNull()?.let { idx ->
                    if (idx >= 0 && idx < st.switches.size) st.switches[idx] else null
                } ?: st.switches.firstOrNull { it.name.equals(switchId, ignoreCase = true) }
            }
        }

        if (targetSwitch == null) return

        // Find the switch index to set as current
        val idx = st.switches.indexOf(targetSwitch)
        if (idx != st.currentSwitchIndex) {
            _state.update { it.copy(currentSwitchIndex = idx) }
        }

        when (press.lowercase()) {
            "on" -> pressCurrentSwitchOn()
            "off" -> pressCurrentSwitchOff()
            "toggle" -> {
                if (targetSwitch.type == "dual") {
                    pressCurrentSwitchOff()
                } else {
                    pressCurrentSwitchOn()
                }
            }
        }
    }

    // ---------- Self-Update ----------

    fun checkForUpdate() {
        updateJob?.cancel()
        _state.update { it.copy(updateAvailable = null, updateChecking = true, downloadFailed = false) }
        updateJob = viewModelScope.launch {
            val info = updater.check(BuildConfig.VERSION_CODE)
            _state.update { it.copy(updateAvailable = info, updateChecking = false) }
            if (info == null) {
                Toast.makeText(ctx, "Du bist auf dem neuesten Stand", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun downloadUpdate() {
        val info = _state.value.updateAvailable ?: return
        if (_state.value.downloadProgress != null) return
        Toast.makeText(ctx, "Update wird heruntergeladen…", Toast.LENGTH_SHORT).show()
        _state.update { it.copy(downloadProgress = 0, downloadFailed = false) }
        viewModelScope.launch {
            // Save previous APK version for rollback
            updater.savePreviousVersion()
            val file = updater.download(info) { p ->
                _state.update { it.copy(downloadProgress = p) }
            }
            if (file != null) {
                _state.update { it.copy(downloadProgress = null) }
                Toast.makeText(ctx, "Update wird installiert…", Toast.LENGTH_SHORT).show()
                updater.install(file)
            } else {
                _state.update {
                    it.copy(downloadProgress = null, downloadFailed = true, statusNote = "Download fehlgeschlagen")
                }
                Toast.makeText(ctx, "Download fehlgeschlagen", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun retryDownload() {
        downloadUpdate()
    }

    fun rollback() {
        val file = updater.getPreviousVersion()
        if (file != null) {
            Toast.makeText(ctx, "Rollback wird installiert…", Toast.LENGTH_SHORT).show()
            updater.install(file)
        } else {
            Toast.makeText(ctx, "Keine vorherige Version gefunden", Toast.LENGTH_LONG).show()
        }
    }

    fun hasPreviousVersion(): Boolean {
        return updater.hasPreviousVersion()
    }

    // ---------- Probing + Sessions ----------

    private fun activeSession(): HaSession? =
        lanSession?.takeIf { it.alive.value } ?: relaySession?.takeIf { it.alive.value }

    private fun startSessions() {
        val token = settings.token
        val entitySet = buildEntitySet(_state.value.switches)
        // R12-lite: Set unverändert + Session lebt -> Rebuild komplett überspringen
        // (kein Teardown, kein "unknown"-Flicker bei no-op-Writes wie Name-Edits).
        val currentLan = lanSession
        if (entitySet == lastEntitySet && currentLan != null && currentLan.alive.value) return
        lastEntitySet = entitySet
        lanSession?.stop()
        relaySession?.stop()
        lanSession = null
        relaySession = null
        // State-Erhalt (R12-lite): alte switchStates/onState/offState bleiben
        // (kein "unknown"-Reset); refreshing bis zum ersten Snapshot der Session.
        _state.update { it.copy(refreshing = token.isNotBlank()) }
        if (token.isNotBlank()) {
            lanSession = HaSession(lanBase, false, token, entitySet).also { it.start() }
            relaySession = HaSession(relayBase, true, token, entitySet).also { it.start() }
        }
        sessionJobs.forEach { it.cancel() }
        sessionJobs = emptyList()
        lanSession?.let {
            s -> sessionJobs += viewModelScope.launch { s.entityStates.collect { emitEntityStates() } }
        }
        relaySession?.let {
            s -> sessionJobs += viewModelScope.launch { s.entityStates.collect { emitEntityStates() } }
        }
    }

    /** Entity-Set für die HA-Sessions: Settings-Entities + pro Switch state/primary-Entity. */
    private fun buildEntitySet(switches: List<SwitchConfig>): Set<String> {
        val entities = mutableSetOf<String>()
        entities.addAll(settings.entitySet())
        for (sw in switches) {
            if (sw.stateEntity.isNotBlank()) {
                entities.add(sw.stateEntity)
            } else {
                // No state entity configured — use primary entity for state tracking
                entities.add(sw.primaryEntity)
            }
        }
        return entities
    }

    /**
     * Coalescing (R5): Emit nur bei echter Änderung (Snapshot-Vergleich)
     * oder 1x/30s (Heartbeat, hält lastUpdate frisch). Notification und
     * Tile-Broadcast nur bei tatsächlicher Änderung.
     */
    private fun emitEntityStates() {
        val s = activeSession() ?: return
        // R12-lite: eine neue Session startet mit leerem Map; der all-"unknown"-
        // Snapshot wird nicht emittiert, bis die ersten Daten ankommen (State-Erhalt).
        if (s.entityStates.value.isEmpty()) return
        val st = _state.value
        val switches = st.switches
        val snap = buildSnapshot(s.entityStates.value, switches, settings.onEntity, settings.offEntity)
        val current = EntitySnapshot(st.switchStates, st.onState, st.offState)
        val now = System.currentTimeMillis()
        if (!shouldEmit(snap, current, lastEmitAt, now)) return
        lastEmitAt = now
        _state.update {
            it.copy(
                switchStates = snap.switchStates,
                onState = snap.onState,
                offState = snap.offState,
                refreshing = false, // erster Snapshot der Session angekommen
                lastUpdate = now
            )
        }
        if (snap == current) return // Heartbeat: nur lastUpdate aktualisieren

        // Notifications für Switches, deren State sich tatsächlich geändert hat
        for (sw in switches) {
            val newState = snap.switchStates[sw.id]
            if (newState != null && newState != "unknown" && newState != current.switchStates[sw.id]) {
                NotificationHelper.sendSwitchNotification(ctx, sw.name, newState, sw.id)
            }
        }

        // Broadcast current switch state to tile (nur bei echter Änderung)
        if (switches.isNotEmpty()) {
            val currentSwitch = switches[st.currentSwitchIndex]
            val currentSwitchState = snap.switchStates[currentSwitch.id]
            if (currentSwitchState != null) {
                broadcastTileState(currentSwitchState == "on", currentSwitch.name)
            }
        }
    }

    /**
     * Mode-Logik: LAN schlägt Relay. VPN-Transport entscheidet, ob
     * HomeLan oder HomeVpn. Relay nur, wenn LAN tot ist.
     */
    private fun probeOnce() {
        val st = _state.value
        if (st.inSetup) return
        val lanReachable = HaClient.probe(lanBase, false)
        val relayReachable = if (lanReachable) false else HaClient.probe(relayBase, true)
        // S9 (R4): VPN primär aus dem Event-Flag (NetworkWatchdog, 750ms-Debounce),
        // onVpn() als Snapshot-Fallback (z.B. vor dem ersten Capability-Event).
        val vpn = networkWatchdog.vpnTransportActive.value || onVpn()
        
        // Check for auth failure
        val lanAuthFailed = lanSession?.authFailed?.value == true
        val relayAuthFailed = relaySession?.authFailed?.value == true
        
        val note = when {
            !lanReachable && !relayReachable ->
                if (vpn) "OpenVPN aktiv – Heimnetz nicht erreichbar" else "Heimnetz nicht erreichbar"
            lanReachable && lanAuthFailed -> "Token prüfen (Einstellungen)"
            relayReachable && relayAuthFailed -> "Token prüfen (Einstellungen)"
            else -> ""
        }
        // VPN that routes LAN = OpenVPN → HomeVpn
        // VPN that doesn't route LAN = Tailscale → Remote
        // No VPN + LAN reachable = HomeLan
        val mode = when {
            vpn && lanReachable -> Mode.HomeVpn
            lanReachable -> Mode.HomeLan
            relayReachable -> Mode.Remote
            else -> Mode.Offline
        }
        // S9: Während VPN-Dialing (Connecting) kein Downgrade auf Offline —
        // der 3s-Loop in connectToHome entscheidet das Endergebnis; Upgrades
        // (HomeVpn/HomeLan/Remote) werden sofort übernommen, damit die
        // VPN-Aktivierung <3s sichtbar wird (kein Offline-Flicker).
        if (st.mode == Mode.Connecting && mode == Mode.Offline) return
        _state.update {
            it.copy(
                mode = mode,
                statusNote = note,
                authError = lanAuthFailed || relayAuthFailed,
                authErrorDismissed = if (lanAuthFailed || relayAuthFailed) false else it.authErrorDismissed
            )
        }
        emitEntityStates()
    }

    /** Aktives Netzwerk ist ein VPN? */
    private fun onVpn(): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "PcSwitchViewModel"

        /** Heartbeat-Intervall: identische Snapshots werden trotzdem 1x/30s
         *  emittiert, damit lastUpdate frisch bleibt (R5). */
        const val HEARTBEAT_MS = 30_000L

        /**
         * Mappt Entity-States auf einen UI-Snapshot.
         * Regeln wie bisher: stateEntity vor primaryEntity, "unknown" wenn
         * Entity nicht vorhanden. Pure Funktion (testbar ohne Android-Runtime).
         */
        fun buildSnapshot(
            entityStates: Map<String, String>,
            switches: List<SwitchConfig>,
            onEntity: String,
            offEntity: String
        ): EntitySnapshot {
            val switchStates = switches.associate { sw ->
                val state = when {
                    sw.stateEntity.isNotBlank() -> entityStates[sw.stateEntity] ?: "unknown"
                    else -> entityStates[sw.primaryEntity] ?: "unknown"
                }
                sw.id to state
            }
            return EntitySnapshot(
                switchStates = switchStates,
                onState = entityStates[onEntity] ?: "unknown",
                offState = entityStates[offEntity] ?: "unknown"
            )
        }

        /**
         * Soll [snap] emittiert werden?
         * - Echte Änderung (unterschiedlich zu [current]) -> sofort
         * - Identisch -> nur wenn Heartbeat-Frist (Default 30s) abgelaufen
         * Pure Funktion (testbar ohne Android-Runtime).
         */
        fun shouldEmit(
            snap: EntitySnapshot,
            current: EntitySnapshot,
            lastEmitAt: Long,
            now: Long,
            heartbeatMs: Long = HEARTBEAT_MS
        ): Boolean =
            snap != current || now - lastEmitAt >= heartbeatMs
    }
}
