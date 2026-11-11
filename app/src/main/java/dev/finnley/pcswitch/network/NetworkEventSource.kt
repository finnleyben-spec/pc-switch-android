package dev.finnley.pcswitch.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Nahtstelle: Netzwerk-Events (Verfügbarkeit + VPN-Transport) als Listener-Events.
 *
 * Reines JVM-Interface (kein Android-Code) — dadurch ist [NetworkWatchdog]
 * unit-testbar ohne Robolectric (Fake-Source, s. NetworkWatchdogTest).
 *
 * Android-Implementation: [AndroidNetworkEventSource] (ConnectivityManager).
 * Die Events sind nur TRIGGER — der eigentliche Status wird vom ViewModel
 * per probeOnce() nachgefragt (kein Status aus dem Event selbst).
 */
interface NetworkEventSource {
    interface Listener {
        /** Default-Netzwerk verfügbar (neues Network, z.B. nach VPN-Setup). */
        fun onNetworkAvailable()

        /** Default-Netzwerk verloren. */
        fun onLost()

        /**
         * VPN-Transport-Flag am Default-Netzwerk geändert.
         * [active] = true, wenn TRANSPORT_VPN aktiv ist (z.B. WireGuard-Interface
         * als default interface, während WLAN noch da ist).
         */
        fun onVpnTransportChanged(active: Boolean)
    }

    /** Listener registriert; wird genau einmal vor [unregister] aufgerufen. */
    fun register(listener: Listener)

    /** Listener deregistrieren (idempotent, doppeltes Stop darf nicht werfen). */
    fun unregister()
}

/**
 * Android-Adapter: ConnectivityManager.registerDefaultNetworkCallback.
 *
 * Permission: ACCESS_NETWORK_STATE (bereits im AndroidManifest).
 * Die Callbacks kommen vom Connectivity-Thread; [NetworkWatchdog] hopt
 * in seinen Coroutine-Scope (kein UI-Thread-Blocking).
 */
class AndroidNetworkEventSource(context: Context) : NetworkEventSource {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var listener: NetworkEventSource.Listener? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            listener?.onNetworkAvailable()
        }

        override fun onLost(network: Network) {
            listener?.onLost()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            listener?.onVpnTransportChanged(
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            )
        }
    }

    override fun register(listener: NetworkEventSource.Listener) {
        this.listener = listener
        cm.registerDefaultNetworkCallback(callback)
    }

    override fun unregister() {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // Doppel-Stop oder bereits deregistriert → ignorieren
        }
        listener = null
    }
}
