package dev.finnley.pcswitch.pcstatus

/**
 * Kombiniert HA-Entity-State (primary) und TCP-Port-22-Check (Fallback)
 * zu EINEM konsistenten PC-Status (R11).
 *
 * Regeln (deterministisch: gleiche Inputs -> gleiches Output):
 * - HA-Entity liefert definierten Wert ("on"/"off") -> HA gewinnt.
 * - HA null/leer/unknown/sonst -> TCP-Fallback: erreichbar = "on", sonst "off".
 *
 * Ersetzt das frühere Flip-Flop zwischen TCP-only- und HA-only-Auslesen
 * durch einen einzigen konsistenten Wert. Pure JVM -> unit-testbar.
 */
object PcStatusResolver {
    const val ON = "on"
    const val OFF = "off"
    const val UNKNOWN = "unknown"

    /**
     * @param haState aktueller HA-Entity-State (null/leer = nicht geladen)
     * @param tcpReachable Ergebnis des TCP-Port-22-Checks
     * @return genau einer von [ON], [OFF]
     */
    fun resolve(haState: String?, tcpReachable: Boolean): String =
        if (haState == ON || haState == OFF) haState
        else if (tcpReachable) ON else OFF
}
