package dev.finnley.pcswitch.ui

import dev.finnley.pcswitch.ha.HaClient.EntityState
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class EntityFilterTest {

    private val testEntities = listOf(
        EntityState("button.pc_wake", "on", "PC Wake"),
        EntityState("button.lights", "off", "Lights Toggle"),
        EntityState("input_button.pc_off", "", "PC Off"),
        EntityState("input_button.lamp", "", "Lamp Input"),
        EntityState("switch.pc_power", "on", "PC Power"),
        EntityState("switch.lamp", "off", "Lamp Switch"),
        EntityState("light.ceiling", "on", "Ceiling Light"),
        EntityState("light.desk", "off", "Desk Light"),
        EntityState("cover.blinds", "closed", "Blinds"),
        EntityState("lock.front_door", "locked", "Front Door"),
        EntityState("fan.ceiling", "on", "Ceiling Fan"),
        EntityState("sensor.temperature", "23.5", "Temperature"),
        EntityState("input_boolean.away", "off", "Away Mode"),
        EntityState("person.finnley", "home", "Finnley"),
        EntityState("automation.test", "idle", "Test Automation"),
        EntityState("scene.movie", "", "Movie Scene")
    )

    // --- Domain filter tests ---

    @Test
    fun all_domain_returns_everything() {
        val filtered = filterEntities(testEntities, "", "all")
        assertEquals(testEntities.size, filtered.size)
    }

    @Test
    fun button_filter_matches_button_and_input_button() {
        val filtered = filterEntities(testEntities, "", "button")
        assertTrue(filtered.any { it.entityId == "button.pc_wake" })
        assertTrue(filtered.any { it.entityId == "button.lights" })
        assertTrue(filtered.any { it.entityId == "input_button.pc_off" })
        assertTrue(filtered.any { it.entityId == "input_button.lamp" })
        assertEquals(4, filtered.size)
    }

    @Test
    fun input_button_filter_matches_only_input_button() {
        val filtered = filterEntities(testEntities, "", "input_button")
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.entityId == "input_button.pc_off" })
        assertTrue(filtered.any { it.entityId == "input_button.lamp" })
    }

    @Test
    fun switch_filter_matches_only_switch() {
        val filtered = filterEntities(testEntities, "", "switch")
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.entityId == "switch.pc_power" })
        assertTrue(filtered.any { it.entityId == "switch.lamp" })
    }

    @Test
    fun light_filter_matches_only_light() {
        val filtered = filterEntities(testEntities, "", "light")
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.entityId == "light.ceiling" })
        assertTrue(filtered.any { it.entityId == "light.desk" })
    }

    @Test
    fun cover_filter_matches_only_cover() {
        val filtered = filterEntities(testEntities, "", "cover")
        assertEquals(1, filtered.size)
        assertEquals("cover.blinds", filtered[0].entityId)
    }

    @Test
    fun lock_filter_matches_only_lock() {
        val filtered = filterEntities(testEntities, "", "lock")
        assertEquals(1, filtered.size)
        assertEquals("lock.front_door", filtered[0].entityId)
    }

    @Test
    fun fan_filter_matches_only_fan() {
        val filtered = filterEntities(testEntities, "", "fan")
        assertEquals(1, filtered.size)
        assertEquals("fan.ceiling", filtered[0].entityId)
    }

    @Test
    fun sensor_filter_matches_only_sensor() {
        val filtered = filterEntities(testEntities, "", "sensor")
        assertEquals(1, filtered.size)
        assertEquals("sensor.temperature", filtered[0].entityId)
    }

    @Test
    fun input_boolean_filter_matches_only_input_boolean() {
        val filtered = filterEntities(testEntities, "", "input_boolean")
        assertEquals(1, filtered.size)
        assertEquals("input_boolean.away", filtered[0].entityId)
    }

    @Test
    fun person_filter_matches_only_person() {
        val filtered = filterEntities(testEntities, "", "person")
        assertEquals(1, filtered.size)
        assertEquals("person.finnley", filtered[0].entityId)
    }

    @Test
    fun unknown_domain_matches_nothing() {
        val filtered = filterEntities(testEntities, "", "nonexistent")
        assertEquals(0, filtered.size)
    }

    @Test
    fun domain_does_not_match_substrings() {
        // "button" should not match "input_button" when filtering for "input" (doesn't exist)
        val filtered = filterEntities(testEntities, "", "input")
        // No entities with domain "input."
        assertEquals(0, filtered.size)
    }

    // --- Query filter tests ---

    @Test
    fun empty_query_returns_all() {
        val filtered = filterEntities(testEntities, "", "all")
        assertEquals(testEntities.size, filtered.size)
    }

    @Test
    fun query_matches_entity_id() {
        val filtered = filterEntities(testEntities, "pc_wake", "all")
        assertEquals(1, filtered.size)
        assertEquals("button.pc_wake", filtered[0].entityId)
    }

    @Test
    fun query_matches_friendly_name() {
        val filtered = filterEntities(testEntities, "PC Wake", "all")
        assertEquals(1, filtered.size)
        assertEquals("button.pc_wake", filtered[0].entityId)
    }

    @Test
    fun query_is_case_insensitive() {
        val filtered = filterEntities(testEntities, "PC", "all")
        assertTrue(filtered.any { it.entityId == "button.pc_wake" })
        assertTrue(filtered.any { it.entityId == "input_button.pc_off" })
        assertTrue(filtered.any { it.entityId == "switch.pc_power" })
        assertEquals(3, filtered.size)
    }

    @Test
    fun query_no_match() {
        val filtered = filterEntities(testEntities, "nonexistent", "all")
        assertEquals(0, filtered.size)
    }

    // --- Combined filter tests ---

    @Test
    fun domain_and_query_combined() {
        val filtered = filterEntities(testEntities, "pc", "switch")
        assertEquals(1, filtered.size)
        assertEquals("switch.pc_power", filtered[0].entityId)
    }

    @Test
    fun domain_filter_with_query_no_match() {
        val filtered = filterEntities(testEntities, "fan", "switch")
        assertEquals(0, filtered.size)
    }

    // --- normalizeStateForDisplay tests ---

    @Test
    fun normalize_on_states() {
        assertEquals("on", normalizeStateForDisplay("on"))
        assertEquals("on", normalizeStateForDisplay("true"))
        assertEquals("on", normalizeStateForDisplay("1"))
        assertEquals("on", normalizeStateForDisplay("active"))
        assertEquals("on", normalizeStateForDisplay("connected"))
        assertEquals("on", normalizeStateForDisplay("ON"))
        assertEquals("on", normalizeStateForDisplay(" On "))
    }

    @Test
    fun normalize_off_states() {
        assertEquals("off", normalizeStateForDisplay("off"))
        assertEquals("off", normalizeStateForDisplay("false"))
        assertEquals("off", normalizeStateForDisplay("0"))
        assertEquals("off", normalizeStateForDisplay("inactive"))
        assertEquals("off", normalizeStateForDisplay("disconnected"))
        assertEquals("off", normalizeStateForDisplay("closed"))
        assertEquals("off", normalizeStateForDisplay("locked"))
        assertEquals("off", normalizeStateForDisplay("stopped"))
        assertEquals("off", normalizeStateForDisplay("paused"))
        assertEquals("off", normalizeStateForDisplay("OFF"))
        assertEquals("off", normalizeStateForDisplay(" Off "))
    }

    @Test
    fun normalize_unknown_states() {
        assertEquals("unknown", normalizeStateForDisplay(null))
        assertEquals("unknown", normalizeStateForDisplay(""))
        assertEquals("unknown", normalizeStateForDisplay("idle"))
        assertEquals("unknown", normalizeStateForDisplay("home"))
        assertEquals("unknown", normalizeStateForDisplay("23.5"))
        assertEquals("unknown", normalizeStateForDisplay("unavailable"))
    }
}
