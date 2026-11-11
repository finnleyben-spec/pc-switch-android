package dev.finnley.pcswitch.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Eventgetriebene Reaktivität: Netzwerk-Events (Available/Lost/VPN-Capability)
 * lösen debounced einen Status-Check aus (R2).
 *
 * - 750 ms Debounce (R3): Event-Bursts (z.B. WLAN<->VPN) -> genau 1 Probe
 * - [vpnTransportActive] als StateFlow (R6): UI kann "im VPN" anzeigen,
 *   ohne das Event zu verlieren
 * - Initialer Probe bei [start] (R7), damit ohne Event kein 30s-Safety-Intervall
 *   gewartet wird
 *
 * Die Logik ist Android-frei (injected [NetworkEventSource] + CoroutineScope)
 * und dadurch unit-testbar ohne Robolectric (Fake-Source, s. NetworkWatchdogTest).
 * Die Callbacks der Source kommen vom Connectivity-Thread; der Probe-Callback
 * läuft im übergebenen CoroutineScope (kein UI-Thread-Blocking).
 *
 * Hinweis: Das 30s-Safety-Intervall im ViewModel bleibt als Fallback (R10).
 */
class NetworkWatchdog(
    private val source: NetworkEventSource,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 750,
    // Injektionsspitze für Tests (Design); Debounce selbst läuft über delay()
    // und ist mit kotlinx-coroutines-test virtual time testbar.
    @Suppress("unused")
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    /** true, wenn das Default-Netzwerk aktuell den VPN-Transport hat. */
    val vpnTransportActive = MutableStateFlow(false)

    private var pending: Job? = null
    private var started = false
    private var onNetworkChange: (() -> Unit)? = null

    private val listener = object : NetworkEventSource.Listener {
        override fun onNetworkAvailable() {
            Log.d(TAG, "network available")
            debounced()
        }

        override fun onLost() {
            Log.d(TAG, "network lost")
            debounced()
        }

        override fun onVpnTransportChanged(active: Boolean) {
            Log.d(TAG, "vpn transport ${if (active) "active" else "inactive"}")
            vpnTransportActive.value = active
            debounced()
        }
    }

    /**
     * Startet den Watchdog: registriert die Source und feuert sofort
     * einen Initial-Probe ([onNetworkChange]), bevor das erste Event kommt.
     * [onNetworkChange] wird debounced (750 ms) bei Netzwerk-Events aufgerufen.
     */
    fun start(onNetworkChange: () -> Unit) {
        if (started) return
        started = true
        this.onNetworkChange = onNetworkChange
        try {
            source.register(listener)
        } catch (e: Exception) {
            // register kann selten werfen -> Watchdog inert,
            // das 30s-Safety-Intervall im ViewModel bleibt als Fallback
            Log.w(TAG, "source.register() fehlgeschlagen: ${e.message} — nur 30s-Safety-Intervall aktiv", e)
        }
        Log.d(TAG, "started — initial probe")
        onNetworkChange() // Initial-Probe
    }

    /** Stoppt den Watchdog: cancelt pendingen Debounce + deregistriert die Source. */
    fun stop() {
        pending?.cancel()
        pending = null
        if (started) {
            started = false
            try {
                source.unregister()
            } catch (_: Exception) {
                // Source kann bei Doppel-Stop werfen -> ignorieren
            }
        }
        onNetworkChange = null
    }

    /** Debounced Probe: alle Events innerhalb von [debounceMs] -> genau 1 Call. */
    private fun debounced() {
        val cb = onNetworkChange ?: return
        pending?.cancel()
        pending = scope.launch {
            delay(debounceMs)
            Log.d(TAG, "debounced probe fired")
            cb()
        }
    }

    companion object {
        private const val TAG = "NetworkWatchdog"
    }
}
