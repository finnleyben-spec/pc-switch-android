package dev.finnley.pcswitch.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für PcTransition.isDone (Design status-reading-design.md §7.1, §5.3).
 * Reine Funktion -> testbar auf der JVM ohne Android-Runtime.
 */
class PcTransitionTest {

    @Test
    fun boot_done_when_ha_on() {
        // HA-Entity (stateEntity) zeigt bereits "on" -> done, auch ohne TCP
        assertTrue(
            PcTransition.isDone(haState = "on", tcpReachable = false, phase = PcTransition.Phase.BOOT)
        )
    }

    @Test
    fun boot_done_when_tcp_reachable() {
        // Kein stateEntity / HA noch unbekannt -> TCP bestätigt das Boot
        assertTrue(
            PcTransition.isDone(haState = null, tcpReachable = true, phase = PcTransition.Phase.BOOT)
        )
        assertTrue(
            PcTransition.isDone(haState = "unknown", tcpReachable = true, phase = PcTransition.Phase.BOOT)
        )
    }

    @Test
    fun shutdown_done_when_ha_off() {
        // HA-Entity zeigt "off" -> done, auch während TCP noch steht
        assertTrue(
            PcTransition.isDone(haState = "off", tcpReachable = true, phase = PcTransition.Phase.SHUTDOWN)
        )
    }

    @Test
    fun shutdown_done_when_tcp_unreachable() {
        // Kein stateEntity -> TCP bestätigt den Shutdown
        assertTrue(
            PcTransition.isDone(haState = null, tcpReachable = false, phase = PcTransition.Phase.SHUTDOWN)
        )
    }

    @Test
    fun no_state_entity_pure_tcp() {
        // Ohne stateEntity entscheidet nur TCP:
        assertFalse(
            PcTransition.isDone(haState = null, tcpReachable = false, phase = PcTransition.Phase.BOOT, hasStateEntity = false)
        )
        assertTrue(
            PcTransition.isDone(haState = null, tcpReachable = true, phase = PcTransition.Phase.BOOT, hasStateEntity = false)
        )
        assertFalse(
            PcTransition.isDone(haState = null, tcpReachable = true, phase = PcTransition.Phase.SHUTDOWN, hasStateEntity = false)
        )
        assertTrue(
            PcTransition.isDone(haState = null, tcpReachable = false, phase = PcTransition.Phase.SHUTDOWN, hasStateEntity = false)
        )
    }

    @Test
    fun not_done_mixed() {
        // HA "off" + TCP reachable bei Boot -> NICHT done (HA maßgebend, kein Flip)
        assertFalse(
            PcTransition.isDone(haState = "off", tcpReachable = true, phase = PcTransition.Phase.BOOT)
        )
    }
}
