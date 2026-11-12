package dev.finnley.pcswitch.state

/**
 * Pure-Eingabevalidatoren für Setup/Settings (v3.0 Public Release).
 * Alle Funktionen sind JVM-testbar (keine Android-Abhängigkeiten).
 */
object Validators {

    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val HOSTNAME = Regex("""^[a-zA-Z0-9](?:[a-zA-Z0-9._-]*[a-zA-Z0-9])?$""")
    private val ENTITY_PART = Regex("""^[a-z0-9_]+$""")

    /** Strenge IPv4-Prüfung (jede Oktette 0..255). */
    fun isValidIpv4(s: String): Boolean {
        val m = IPV4.matchEntire(s.trim()) ?: return false
        return (1..4).all { m.groupValues[it].toInt() in 0..255 }
    }

    /** Host = IPv4 oder RFC-ähnlicher Hostname (IPv6 wird nicht unterstützt). */
    fun isHost(s: String): Boolean {
        val h = s.trim()
        if (h.isEmpty() || h.length > 253) return false
        return isValidIpv4(h) || HOSTNAME.matches(h)
    }

    /**
     * "host" oder "host:port" (port 1..65535, optional).
     * Ohne explizites Schema — Schemas gehören in [explicitScheme].
     */
    fun isValidHostPort(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        val idx = t.lastIndexOf(':')
        val host = if (idx == -1) t else t.substring(0, idx)
        val port = if (idx == -1) "" else t.substring(idx + 1)
        if (!isHost(host)) return false
        if (port.isEmpty()) return true
        val p = port.toIntOrNull() ?: return false
        return p in 1..65535
    }

    /** Explizites Schema eines Bases: "http", "https", "" (kein Schema). */
    fun explicitScheme(base: String): String =
        when {
            base.trim().startsWith("http://") -> "http"
            base.trim().startsWith("https://") -> "https"
            else -> ""
        }

    /** Host ohne Schema/Port ("host" oder "host:port" → "host"). */
    fun bareHost(base: String): String {
        val t = base.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        return t.substringAfterLast('/').substringBeforeLast(':')
    }

    /**
     * Private/lokale Hosts, zu denen cleartext-HTTP vertretbar ist:
     * private IPv4-Bereiche, Loopback, CGNAT/Tailscale (100.64.0.0/10),
     * mDNS-Suffixe (.local) und übliche Heimnetz-Suffixe (.lan/.home/.home.arpa).
     */
    fun isPrivateNetworkHost(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h == "localhost" || h.startsWith("localhost.")) return true
        if (h.endsWith(".local") || h.endsWith(".lan") ||
            h.endsWith(".home") || h.endsWith(".home.arpa")
        ) return true
        if (!isValidIpv4(h)) return false
        val parts = h.split('.').map { it.toInt() }
        return when {
            parts[0] == 10 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 127 -> true
            parts[0] == 100 && parts[1] in 64..127 -> true  // CGNAT / Tailscale
            else -> false
        }
    }

    /**
     * HTTPS-Pflicht: ein explizit cleartext-Base ("http://") ist nur für
     * private/lokale Hosts erlaubt — öffentlicher Host über HTTP blockiert den
     * Token-Leak. Ohne explizites Schema entscheidet [secureDefault] (keine Pflicht).
     */
    fun requiresHttps(base: String): Boolean =
        explicitScheme(base) == "http" && !isPrivateNetworkHost(bareHost(base))

    /** HA-Entity-ID: "domain.object", beide Teile [a-z0-9_], max. 64 Zeichen. */
    fun isValidEntityId(s: String): Boolean {
        val parts = s.trim().lowercase().split('.')
        if (parts.size != 2) return false
        return parts.all { it.isNotEmpty() && it.length <= 64 && ENTITY_PART.matches(it) }
    }
}
