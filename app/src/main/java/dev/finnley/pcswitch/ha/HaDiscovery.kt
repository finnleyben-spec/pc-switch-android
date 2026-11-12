package dev.finnley.pcswitch.ha

import android.util.Log
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Mdns-Discovery von Home-Assistant-Instanzen (v3.0 Public Release).
 *
 * Nutzt JmDNS (javax.jmdns, reine Java-Lib) über den Multicast-DNS-Standard:
 *   browse("_http._tcp.local") → ServiceInfo → HA-Filter.
 *
 * Hinweis: Das lokale SDK-Build dieser Maschine enthält keinen NsdManager
 * (gestrippter android.jar), daher Library statt Framework-API.
 * Auf Wi-Fi wird android.permission.CHANGE_WIFI_MULTICAST_STATE benötigt.
 *
 * Testbarkeit: [DiscoveryFilter] ist reine Logik (JVM-testbar);
 * die JmDNS-Kopplung wird im E2E-Test geprüft.
 */
class HaDiscovery {

    data class FoundHost(
        val host: String,
        val port: Int,
        val serviceName: String
    ) {
        /** Fertige Base für die Lan-Base-Eingabe ("host:port"). */
        val base: String get() = "$host:$port"
    }

    /**
     * Scannt das lokale Netzwerk nach HA-Instanzen.
     * @param timeoutMs maximale Scan-Dauer (Default 5s).
     * @return Kandidaten, HA-ähnliche zuerst, dann nach Host/Port sortiert.
     */
    suspend fun discover(timeoutMs: Long = 5000): List<FoundHost> =
        withContext(Dispatchers.IO) {
            val found = ConcurrentHashMap<String, FoundHost>()
            val jmdns: JmDNS? = try {
                val j = JmDNS.create()
                j.addServiceListener(BROWSE_TYPE, object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        ingest(event.info, found)
                    }
                    override fun serviceRemoved(event: ServiceEvent) = Unit
                    override fun serviceResolved(event: ServiceEvent) {
                        // SRV-Record vollständig aufgelöst (A-Record da)
                        ingest(event.info, found)
                    }
                })
                j
            } catch (e: Exception) {
                Log.w(TAG, "JmDNS-Setup fehlgeschlagen (kein Multicast/Wi-Fi?): ${e.message}")
                null
            }

            if (jmdns != null) {
                // Scan läuft die volle Dauer (mDNS-Responder antworten in 1–2s).
                withTimeoutOrNull(timeoutMs) { Thread.sleep(timeoutMs) }
                try {
                    jmdns.close()
                } catch (e: Exception) {
                    Log.w(TAG, "JmDNS-Close fehlgeschlagen: ${e.message}")
                }
            }

            found.values.toList()
                .sortedWith(
                    compareByDescending<FoundHost> {
                        DiscoveryFilter.haLikelihood(it.serviceName, it.host, it.port)
                    }.thenBy { it.host }.thenBy { it.port }
                )
        }

    private fun ingest(info: ServiceInfo?, found: ConcurrentHashMap<String, FoundHost>) {
        if (info == null) return
        val host = info.hostAddress ?: return
        val port = info.port
        if (port <= 0) return
        val name = info.name
            .removeSuffix(".$BROWSE_TYPE")
            .substringBeforeLast('.')
        if (!DiscoveryFilter.isHaCandidate(name, host, port)) return
        found["$host:$port"] = FoundHost(host, port, name)
    }

    companion object {
        private const val TAG = "HaDiscovery"
        private const val BROWSE_TYPE = "_http._tcp.local"
    }
}

/**
 * Reine Heuristik: wirkt der mDNS-Kandidat wie Home Assistant?
 * HA meldet sich typischerweise als Dienst "homeassistant" auf Port 8123.
 */
object DiscoveryFilter {

    private fun matchesHa(name: String, host: String): Boolean {
        val n = name.lowercase()
        val h = host.lowercase()
        return n.contains("homeassistant") ||
            n.contains("home-assistant") ||
            n == "ha" ||
            h.contains("homeassistant") ||
            h.contains("home-assistant") ||
            h.endsWith(".local") && (n.startsWith("home") || n == "ha")
    }

    /** Ja, wenn der Kandidat plausibel HA ist (Name, Host oder Standardport). */
    fun isHaCandidate(serviceName: String, host: String, port: Int): Boolean =
        matchesHa(serviceName, host) || port == 8123

    /** 1 = klar HA, 0 = nur Port-Treffer (für Sortierung). */
    fun haLikelihood(serviceName: String, host: String, port: Int): Int =
        if (matchesHa(serviceName, host)) 1 else 0
}
