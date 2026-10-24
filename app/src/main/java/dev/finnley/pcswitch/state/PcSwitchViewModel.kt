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
import dev.finnley.pcswitch.update.UpdateManager
import dev.finnley.pcswitch.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val mode: Mode = Mode.Offline,
    val vpnBusy: Boolean = false,
    val onState: String = "unknown",
    val offState: String = "unknown",
    val lastUpdate: Long = 0,
    val updateAvailable: UpdateManager.UpdateInfo? = null,
    val updateChecking: Boolean = false,
    val downloadProgress: Int? = null,
    val downloadFailed: Boolean = false,
    val statusNote: String = "",
)

class PcSwitchViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app
    val settings = Settings(ctx)
    private val updater = UpdateManager(ctx)

    private val _state = MutableStateFlow(
        UiState(inSetup = !settings.setupDone || settings.token.isBlank())
    )
    val state: StateFlow<UiState> = _state

    private var lanSession: HaSession? = null
    private var relaySession: HaSession? = null
    private var probeJob: Job? = null
    private var vpnJob: Job? = null
    private var updateJob: Job? = null
    private var sessionJobs = listOf<Job>()

    private val lanBase get() = settings.lanBase
    private val relayBase get() = settings.relayBase

    init {
        startSessions()
        probeJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                probeOnce()
                delay(20_000)
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
                Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
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
            delay(3000)
            _state.update { it.copy(vpnBusy = false, statusNote = "") }
            probeOnce() // Mode sofort neu bestimmen
        }
    }

    // ---------- PC an / aus ----------

    fun pressOn() = press(settings.onEntity)
    fun pressOff() = press(settings.offEntity)

    private fun press(entity: String) {
        val s = activeSession() ?: return
        if (!s.alive.value) return
        s.press(entity)
    }

    // ---------- Setup ----------

    fun saveSetup(
        lan: String,
        relay: String,
        token: String,
        on: String,
        off: String,
        vpnId: String
    ) {
        settings.lanBase = lan
        settings.relayBase = relay
        settings.token = token
        settings.onEntity = on
        settings.offEntity = off
        settings.vpnProfileId = vpnId
        settings.setupDone = true
        _state.update { it.copy(inSetup = token.isBlank()) }
        startSessions()
        viewModelScope.launch(Dispatchers.IO) { probeOnce() }
        checkForUpdate()
    }

    fun saveNetworkSettings(lan: String, relay: String) {
        settings.lanBase = lan
        settings.relayBase = relay
        startSessions()
        viewModelScope.launch(Dispatchers.IO) { probeOnce() }
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
            it.copy(onState = "unknown", offState = "unknown", lastUpdate = 0)
        }
        val token = settings.token
        if (token.isNotBlank()) {
            val entities = settings.entitySet()
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
        _state.update {
            it.copy(
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
        val note = when {
            !lanReachable && !relayReachable ->
                if (vpn) "OpenVPN aktiv – Heimnetz nicht erreichbar" else "Heimnetz nicht erreichbar"
            lanReachable && lanSession?.authFailed?.value == true -> "Token prüfen (Einstellungen)"
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
            it.copy(mode = mode, statusNote = note)
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
