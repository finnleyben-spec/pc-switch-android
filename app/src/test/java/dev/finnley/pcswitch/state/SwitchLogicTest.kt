package dev.finnley.pcswitch.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchLogicTest {

    @Test
    fun toggle_switch_uses_primary_entity() {
        val config = SwitchConfig(
            id = "lamp",
            name = "Lamp",
            type = "toggle",
            primaryEntity = "light.living_room",
            secondaryEntity = ""
        )

        // For toggle, the primary entity is always used
        assertEquals("light.living_room", config.primaryEntity)
        assertEquals("", config.secondaryEntity)
    }

    @Test
    fun dual_switch_has_both_entities() {
        val config = SwitchConfig(
            id = "pc",
            name = "PC",
            type = "dual",
            primaryEntity = "switch.pc_power",
            secondaryEntity = "switch.pc_off"
        )

        assertEquals("switch.pc_power", config.primaryEntity)
        assertEquals("switch.pc_off", config.secondaryEntity)
    }

    @Test
    fun switch_config_copy_creates_modified_copy() {
        val original = SwitchConfig(
            id = "pc",
            name = "PC",
            type = "dual",
            primaryEntity = "switch.pc_power",
            secondaryEntity = "switch.pc_off"
        )

        val renamed = original.copy(name = "Computer")
        assertEquals("Computer", renamed.name)
        assertEquals("PC", original.name) // Original unchanged
    }

    @Test
    fun new_switch_factory_method() {
        val config = SwitchConfig.newSwitch("test", "Test", "toggle", "light.test")
        assertEquals("test", config.id)
        assertEquals("Test", config.name)
        assertEquals("toggle", config.type)
        assertEquals("light.test", config.primaryEntity)
        assertEquals("", config.secondaryEntity)
    }

    @Test
    fun new_switch_with_secondary_entity() {
        val config = SwitchConfig.newSwitch("test", "Test", "dual", "switch.on", "switch.off")
        assertEquals("switch.on", config.primaryEntity)
        assertEquals("switch.off", config.secondaryEntity)
    }
}