package dev.finnley.pcswitch.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für das ViewModel-Coalescing (R5, Design status-reading-design.md §7.1).
 * buildSnapshot/shouldEmit sind reine Companion-Funktionen -> testbar
 * ohne Android-Runtime, hand-rolled Daten (kein MockK).
 */
class StateCoalescingTest {

    private fun switch(id: String, primary: String, state: String = "") =
        SwitchConfig(
            id = id,
            name = id,
            type = "toggle",
            primaryEntity = primary,
            stateEntity = state
        )

    private fun snapshot(states: Map<String, String>, on: String = "on", off: String = "off") =
        EntitySnapshot(switchStates = states, onState = on, offState = off)

    @Test
    fun identical_snapshot_skips_emit() {
        val snap = snapshot(mapOf("s1" to "on"))
        // identisch + kürzlich emittiert (5s) -> kein Emit
        assertFalse(
            PcSwitchViewModel.shouldEmit(snap, snap, lastEmitAt = 1_000L, now = 1_000L + 5_000L)
        )
    }

    @Test
    fun changed_snapshot_emits_immediately() {
        val current = snapshot(mapOf("s1" to "on"))
        val changed = snapshot(mapOf("s1" to "off"))
        // Änderung wird sofort emittiert, auch 0ms nach dem letzten Emit
        assertTrue(PcSwitchViewModel.shouldEmit(changed, current, lastEmitAt = 1_000L, now = 1_000L))
        assertTrue(PcSwitchViewModel.shouldEmit(changed, current, lastEmitAt = 1_000L, now = 1_500L))
    }

    @Test
    fun identical_snapshot_heartbeats_after_30s() {
        val snap = snapshot(mapOf("s1" to "on"))
        // genau 30s seit letztem Emit -> Heartbeat
        assertTrue(PcSwitchViewModel.shouldEmit(snap, snap, lastEmitAt = 0L, now = 30_000L))
        // knapp unter 30s -> noch kein Heartbeat
        assertFalse(PcSwitchViewModel.shouldEmit(snap, snap, lastEmitAt = 1L, now = 30_000L))
        // deutlich über 30s -> Heartbeat
        assertTrue(PcSwitchViewModel.shouldEmit(snap, snap, lastEmitAt = 0L, now = 61_000L))
    }

    @Test
    fun build_prefers_state_entity_over_primary() {
        val sw = switch("s1", primary = "switch.primary", state = "binary_sensor.state")
        val snap = PcSwitchViewModel.buildSnapshot(
            entityStates = mapOf(
                "switch.primary" to "on",
                "binary_sensor.state" to "off",
                "button.on" to "idle",
                "button.off" to "idle"
            ),
            switches = listOf(sw),
            onEntity = "button.on",
            offEntity = "button.off"
        )
        assertEquals("off", snap.switchStates["s1"]) // stateEntity gewinnt
        assertEquals("idle", snap.onState)
        assertEquals("idle", snap.offState)
    }

    @Test
    fun build_falls_back_to_primary_entity() {
        val sw = switch("s1", primary = "switch.primary") // kein stateEntity
        val snap = PcSwitchViewModel.buildSnapshot(
            entityStates = mapOf("switch.primary" to "on"),
            switches = listOf(sw),
            onEntity = "button.on",
            offEntity = "button.off"
        )
        assertEquals("on", snap.switchStates["s1"])
    }

    @Test
    fun build_missing_entity_is_unknown() {
        val sw = switch("s1", primary = "switch.primary")
        val snap = PcSwitchViewModel.buildSnapshot(
            entityStates = emptyMap(),
            switches = listOf(sw),
            onEntity = "button.on",
            offEntity = "button.off"
        )
        assertEquals("unknown", snap.switchStates["s1"])
        assertEquals("unknown", snap.onState)
        assertEquals("unknown", snap.offState)
    }
}
