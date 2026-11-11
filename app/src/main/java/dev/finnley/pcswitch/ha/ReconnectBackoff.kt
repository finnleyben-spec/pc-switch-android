package dev.finnley.pcswitch.ha

/**
 * Exponential Backoff mit Jitter für WebSocket-Reconnects.
 * Delay = min(30s, 1s · 2^n + U(0, 1s·2^n/2))
 *
 * n steigt pro nextDelay()-Aufruf; reset() geht zurück auf den Basisbereich (1.0–1.5s).
 * Zufallsquelle injizierbar für Tests (deterministischer Jitter).
 */
class ReconnectBackoff(
    private val baseMs: Long = 1_000L,
    private val capMs: Long = 30_000L,
    private val random: () -> Double = { Math.random() },
) {
    private var attempt = 0

    /** Nächste Verzögerung in ms; incrementiert den internen Zähler. */
    fun nextDelayMs(): Long {
        val scaled = minOf(capMs, baseMs * (1L shl minOf(attempt, 16)))  // Overflow-Schutz
        val jitter = (scaled / 2.0) * random()  // U(0, scaled/2)
        attempt += 1
        return minOf(capMs, (scaled + jitter).toLong())
    }

    /** Zähler zurücksetzen (nach erfolgreichem auth). */
    fun reset() {
        attempt = 0
    }
}
