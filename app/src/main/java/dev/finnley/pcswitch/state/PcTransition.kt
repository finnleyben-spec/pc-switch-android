package dev.finnley.pcswitch.state

/**
 * Reine isDone-Logik für die PC-Boot/Shutdown-Transition (Design §5.3).
 *
 * Semantik (Design §5.1/§5.3 + §7.1 Test-Spezifikation):
 * - stateEntity konfiguriert ([hasStateEntity]) und HA-State definitiv
 *   ("on"/"off") -> HA ist maßgebend: Zielzustand erreicht ("on" bei Boot,
 *   "off" bei Shutdown). Ein definitiver HA-State, der das Ziel widerspricht,
 *   blockiert das Done (z. B. HA "off" + TCP reachable bei Boot -> false,
 *   kein Flip-Flop — "HA gewinnt", wie beim Status-Resolver).
 * - Kein stateEntity oder HA-State noch nicht definitiv (null/leer/"unknown")
 *   -> TCP entscheidet (Boot: reachable, Shutdown: !reachable).
 *
 * Reine Funktion: keine Android-Abhängigkeiten, keine Coroutines.
 */
object PcTransition {

    enum class Phase { BOOT, SHUTDOWN }

    fun isDone(
        haState: String?,
        tcpReachable: Boolean,
        phase: Phase,
        hasStateEntity: Boolean = true
    ): Boolean {
        if (hasStateEntity) {
            val ha = haState?.trim()?.lowercase()
            if (ha == "on" || ha == "off") {
                return when (phase) {
                    Phase.BOOT -> ha == "on"
                    Phase.SHUTDOWN -> ha == "off"
                }
            }
        }
        // Kein stateEntity (oder HA noch nicht definitiv) -> reines TCP.
        return when (phase) {
            Phase.BOOT -> tcpReachable
            Phase.SHUTDOWN -> !tcpReachable
        }
    }
}
