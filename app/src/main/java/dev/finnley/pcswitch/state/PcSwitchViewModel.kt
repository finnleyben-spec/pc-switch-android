package dev.finnley.pcswitch.state

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.finnley.pcswitch.BuildConfig
import dev.finnley.pcswitch.ha.HaClient
import dev.finnley.pcswitch.ha.HaClient.HaSession
import dev.finnley.pcswitch.pcstatus.PcStatusChecker
import dev.finnley.pcswitch.state.SwitchManager
import dev.finnley.pcswitch.state.SwitchConfig
import dev.finnley.pcswitch.update.UpdateManager
import dev.finnley.pcswitch.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Mode {
    data object HomeLan : Mode()
    data object HomeVpn : Mode()
    data object Remote : Mode()
    data object Offline : Mode()
    data object Connecting : Mode()
}

data class UiState(
    val inSetup: Boolean = false,
    val inSettings: Boolean = false,
    val inSwitches: Boolean = false,
    val loading: Boolean = true,
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
    val pcStatus: String = "unknown",  // "reachable" | "unreachable" | "unknown"
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

    private var lanSession: HaSession? = null
    private var relaySession: HaSession? = null
    private var probeJob: Job? = null
    private var pcStatusJob: Job? = null
    private var vpnJob: Job? = null
    private var updateJob: Job? = null
    private var sessionJobs = listOf<Job>()

    private val lanBase get() = settings.lanBase
    private val relayBase get() = settings.relayBase

    init {
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
                delay(5_000)
            }
        }
        // Periodic PC status check (direct TCP, independent of HA)
        pcStatusJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                checkPcStatus()
                delay(10_000)
            }
        }
        // Load switches and observe changes — start sessions after switches load
        viewModelScope.launch {
            switchManager.switchesFlow().collect { switches ->
                _state.update { st ->
                    st.copy(switches = switches)
                }
                // Start/restart sessions whenever switches change so entity subscriptions are correct
                startSessions()
            }
        }
        // Check for updates on startup
        viewModelScope.launch {
            delay(2000) // Wait a bit after startup
            checkForUpdate()
        }
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

    fun pressOn() = press(settings.onEntity)
    fun pressOff() = press(settings.offEntity)

    /** Press the primary (on) button of the current switch. */
    fun pressCurrentSwitchOn() {
        val switches = _state.value.switches
        if (switches.isEmpty()) return
        val switch = switches[_state.value.currentSwitchIndex]
        press(switch.primaryEntity)
        _state.update {
            it.copy(
                lastAction = it.lastAction + (switch.id to "on"),
                switchStates = it.switchStates + (switch.id to "on")
            )
        }
    }

    /** Press the secondary (off) button of the current switch. */
    fun pressCurrentSwitchOff() {
        val switches = _state.value.switches
        if (switches.isEmpty()) return
        val switch = switches[_state.value.currentSwitchIndex]
        if (switch.type == "dual" && switch.secondaryEntity.isNotBlank()) {
            press(switch.secondaryEntity)
        } else {
            press(switch.primaryEntity)  // toggle behavior
        }
        _state.update {
            it.copy(
                lastAction = it.lastAction + (switch.id to "off"),
                switchStates = it.switchStates + (switch.id to "off")
            )
        }
    }

    private fun press(entity: String) {
        val s = activeSession() ?: return
        if (!s.alive.value) return
        s.press(entity)
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

    fun addSwitch(name: String, type: String, primaryEntity: String, secondaryEntity: String) {
        viewModelScope.launch {
            switchManager.addSwitch(name, type, primaryEntity, secondaryEntity)
        }
    }

    fun updateSwitch(switchConfig: SwitchConfig) {
        viewModelScope.launch {
            switchManager.updateSwitch(switchConfig)
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

    /** Direct TCP check for PC reachability (port 22 or 80). Independent of HA. */
    private fun checkPcStatus() {
        try {
            val ip = settings.pcIp
            val reachable = PcStatusChecker.isReachable(ip)
            _state.update {
                it.copy(pcStatus = if (reachable) "reachable" else "unreachable")
            }
        } catch (e: Exception) {
            _state.update { it.copy(pcStatus = "unknown") }
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
        lanSession?.stop()
        relaySession?.stop()
        lanSession = null
        relaySession = null
        _state.update {
            it.copy(
                onState = "unknown",
                offState = "unknown",
                lastUpdate = 0,
                switchStates = emptyMap()
            )
        }
        val token = settings.token
        if (token.isNotBlank()) {
            val switches = _state.value.switches
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
            lanSession = HaSession(lanBase, false, token, entities).also { it.start() }
            relaySession = HaSession(relayBase, true, token, entities).also { it.start() }
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

    private fun emitEntityStates() {
        val s = activeSession() ?: return
        val m = s.entityStates.value
        val switches = _state.value.switches
        val switchStates = mutableMapOf<String, String>()
        for (sw in switches) {
            val state = when {
                sw.stateEntity.isNotBlank() -> m[sw.stateEntity] ?: "unknown"
                else -> m[sw.primaryEntity] ?: "unknown"
            }
            switchStates[sw.id] = state
        }
        _state.update {
            it.copy(
                switchStates = switchStates,
                onState = m[settings.onEntity] ?: "unknown",
                offState = m[settings.offEntity] ?: "unknown",
                lastUpdate = s.lastUpdate.value
            )
        }
    }

    /**
     * Mode-Logik: LAN schlägt Relay. VPN-Transport entscheidet, ob
     * HomeLan oder HomeVpn. Relay nur, wenn LAN tot ist.
     */
    private fun probeOnce() {
        val st = _state.value
        if (st.inSetup || st.vpnBusy || st.mode == Mode.Connecting) return
        val lanReachable = HaClient.probe(lanBase, false)
        val relayReachable = if (lanReachable) false else HaClient.probe(relayBase, true)
        val vpn = onVpn()
        
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
}
