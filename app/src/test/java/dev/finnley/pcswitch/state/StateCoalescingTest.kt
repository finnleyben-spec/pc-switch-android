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

    // B3: Rohe HA-States (z. B. ISO-Timestamps von input_button ohne StateEntity)
    // dürfen nie als Status-Label durchkommen — buildSnapshot normalisiert sie.

    @Test
    fun build_normalizes_iso_timestamps_to_unknown() {
        val sw = switch("s1", primary = "input_button.pc_switch_test_on") // kein stateEntity
        val snap = PcSwitchViewModel.buildSnapshot(
            entityStates = mapOf("input_button.pc_switch_test_on" to "2026-11-11T22:34:00.123456+00:00"),
            switches = listOf(sw),
            onEntity = "button.on",
            offEntity = "button.off"
        )
        assertEquals("unknown", snap.switchStates["s1"])
    }

    @Test
    fun build_normalizes_recognized_states() {
        val snap = PcSwitchViewModel.buildSnapshot(
            entityStates = mapOf(
                "bool.true" to "true",
                "num.one" to "1",
                "bool.false" to "false",
                "num.zero" to "0",
                "mixed.casewhite" to "  On  "
            ),
            switches = listOf(
                switch("s1", primary = "bool.true"),
                switch("s2", primary = "num.one"),
                switch("s3", primary = "bool.false"),
                switch("s4", primary = "num.zero"),
                switch("s5", primary = "mixed.casewhite")
            ),
            onEntity = "button.on",
            offEntity = "button.off"
        )
        assertEquals("on", snap.switchStates["s1"])
        assertEquals("on", snap.switchStates["s2"])
        assertEquals("off", snap.switchStates["s3"])
        assertEquals("off", snap.switchStates["s4"])
        assertEquals("on", snap.switchStates["s5"])
    }

    @Test
    fun build_normalizes_unavailable_and_raw_payloads() {
        val snap = PcSwitchViewModel.buildSnapshot(
            entityStates = mapOf(
                "a.unavail" to "unavailable",
                "b.sensor" to "42.5",
                "c.unknown" to "unknown"
            ),
            switches = listOf(
                switch("s1", primary = "a.unavail"),
                switch("s2", primary = "b.sensor"),
                switch("s3", primary = "c.unknown")
            ),
            onEntity = "button.on",
            offEntity = "button.off"
        )
        assertEquals("unknown", snap.switchStates["s1"])
        assertEquals("unknown", snap.switchStates["s2"])
        assertEquals("unknown", snap.switchStates["s3"])
    }

    @Test
    fun normalize_state_pure_function() {
        assertEquals("on", PcSwitchViewModel.normalizeState("on"))
        assertEquals("on", PcSwitchViewModel.normalizeState("TRUE"))
        assertEquals("off", PcSwitchViewModel.normalizeState("off"))
        assertEquals("off", PcSwitchViewModel.normalizeState("False"))
        assertEquals("unknown", PcSwitchViewModel.normalizeState(null))
        assertEquals("unknown", PcSwitchViewModel.normalizeState(""))
        assertEquals("unknown", PcSwitchViewModel.normalizeState("2026-11-11T22:34:00.123456Z"))
        assertEquals("unknown", PcSwitchViewModel.normalizeState("unavailable"))
    }
}
