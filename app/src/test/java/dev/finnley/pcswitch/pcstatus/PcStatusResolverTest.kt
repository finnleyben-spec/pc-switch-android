package dev.finnley.pcswitch.pcstatus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests für [PcStatusResolver] (Design status-reading-design.md §7.1).
 * HA-Entity ist PRIMARY, TCP-Fallback nur bei null/leer/unknown.
 */
class PcStatusResolverTest {

    @Test
    fun ha_on_wins_over_tcp_off() {
        assertEquals(PcStatusResolver.ON, PcStatusResolver.resolve("on", false))
    }

    @Test
    fun ha_off_wins_over_tcp_on() {
        assertEquals(PcStatusResolver.OFF, PcStatusResolver.resolve("off", true))
    }

    @Test
    fun tcp_fallback_when_ha_null_or_empty() {
        assertEquals(PcStatusResolver.ON, PcStatusResolver.resolve(null, true))
        assertEquals(PcStatusResolver.OFF, PcStatusResolver.resolve(null, false))
        assertEquals(PcStatusResolver.ON, PcStatusResolver.resolve("", true))
        assertEquals(PcStatusResolver.OFF, PcStatusResolver.resolve("", false))
    }

    @Test
    fun tcp_fallback_when_ha_unknown() {
        assertEquals(PcStatusResolver.ON, PcStatusResolver.resolve("unknown", true))
        assertEquals(PcStatusResolver.OFF, PcStatusResolver.resolve("unknown", false))
    }

    @Test
    fun stable_no_flap_100_iterations() {
        // Alle relevanten Input-Kombinationen, je 100 Iterationen:
        // deterministisch, kein Flip-Flop zwischen HA- und TCP-Wert.
        val inputs: List<Pair<String?, Boolean>> = listOf(
            "on" to false,
            "on" to true,
            "off" to false,
            "off" to true,
            null to false,
            null to true,
            "" to false,
            "" to true,
            "unknown" to false,
            "unknown" to true,
            "unreachable" to false,
            "unreachable" to true
        )
        repeat(100) { i ->
            for ((ha, tcp) in inputs) {
                val expected = when {
                    ha == "on" || ha == "off" -> ha
                    tcp -> "on"
                    else -> "off"
                }
                assertEquals(
                    "iteration $i, haState=$ha, tcp=$tcp",
                    expected,
                    PcStatusResolver.resolve(ha, tcp)
                )
            }
        }
    }
}
