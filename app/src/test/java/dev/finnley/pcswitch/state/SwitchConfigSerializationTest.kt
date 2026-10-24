package dev.finnley.pcswitch.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class SwitchConfigSerializationTest {

    @Test
    fun toggle_switch_serializes_to_json() {
        val config = SwitchConfig(
            id = "lamp",
            name = "Lamp",
            type = "toggle",
            primaryEntity = "light.living_room",
            secondaryEntity = ""
        )

        val obj = JSONObject()
        obj.put("id", config.id)
        obj.put("name", config.name)
        obj.put("type", config.type)
        obj.put("primaryEntity", config.primaryEntity)
        obj.put("secondaryEntity", config.secondaryEntity)

        assertEquals("lamp", obj.getString("id"))
        assertEquals("Lamp", obj.getString("name"))
        assertEquals("toggle", obj.getString("type"))
        assertEquals("light.living_room", obj.getString("primaryEntity"))
        assertEquals("", obj.getString("secondaryEntity"))
    }

    @Test
    fun dual_switch_serializes_to_json() {
        val config = SwitchConfig(
            id = "pc",
            name = "PC",
            type = "dual",
            primaryEntity = "switch.pc_power",
            secondaryEntity = "switch.pc_off"
        )

        val obj = JSONObject()
        obj.put("id", config.id)
        obj.put("name", config.name)
        obj.put("type", config.type)
        obj.put("primaryEntity", config.primaryEntity)
        obj.put("secondaryEntity", config.secondaryEntity)

        assertEquals("pc", obj.getString("id"))
        assertEquals("PC", obj.getString("name"))
        assertEquals("dual", obj.getString("type"))
        assertEquals("switch.pc_power", obj.getString("primaryEntity"))
        assertEquals("switch.pc_off", obj.getString("secondaryEntity"))
    }

    @Test
    fun switch_config_round_trip() {
        val original = SwitchConfig(
            id = "pc",
            name = "PC",
            type = "dual",
            primaryEntity = "switch.pc_power",
            secondaryEntity = "switch.pc_off"
        )

        // Serialize
        val obj = JSONObject()
        obj.put("id", original.id)
        obj.put("name", original.name)
        obj.put("type", original.type)
        obj.put("primaryEntity", original.primaryEntity)
        obj.put("secondaryEntity", original.secondaryEntity)

        // Deserialize
        val restored = SwitchConfig(
            id = obj.getString("id"),
            name = obj.getString("name"),
            type = obj.getString("type"),
            primaryEntity = obj.getString("primaryEntity"),
            secondaryEntity = obj.getString("secondaryEntity")
        )

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.type, restored.type)
        assertEquals(original.primaryEntity, restored.primaryEntity)
        assertEquals(original.secondaryEntity, restored.secondaryEntity)
    }

    @Test
    fun list_of_switches_serializes_to_json_array() {
        val switches = listOf(
            SwitchConfig(
                id = "pc",
                name = "PC",
                type = "dual",
                primaryEntity = "switch.pc_power",
                secondaryEntity = "switch.pc_off"
            ),
            SwitchConfig(
                id = "lamp",
                name = "Lamp",
                type = "toggle",
                primaryEntity = "light.living_room",
                secondaryEntity = ""
            )
        )

        val arr = JSONArray()
        for (sw in switches) {
            val obj = JSONObject()
            obj.put("id", sw.id)
            obj.put("name", sw.name)
            obj.put("type", sw.type)
            obj.put("primaryEntity", sw.primaryEntity)
            obj.put("secondaryEntity", sw.secondaryEntity)
            arr.put(obj)
        }

        assertEquals(2, arr.length())
        assertEquals("pc", arr.getJSONObject(0).getString("id"))
        assertEquals("lamp", arr.getJSONObject(1).getString("id"))
    }

    @Test
    fun switch_config_equality() {
        val a = SwitchConfig(
            id = "pc",
            name = "PC",
            type = "dual",
            primaryEntity = "switch.pc_power",
            secondaryEntity = "switch.pc_off"
        )
        val b = SwitchConfig(
            id = "pc",
            name = "PC",
            type = "dual",
            primaryEntity = "switch.pc_power",
            secondaryEntity = "switch.pc_off"
        )
        assertEquals(a, b)
    }

    @Test
    fun switch_config_inequality() {
        val a = SwitchConfig(
            id = "pc",
            name = "PC",
            type = "dual",
            primaryEntity = "switch.pc_power",
            secondaryEntity = "switch.pc_off"
        )
        val b = SwitchConfig(
            id = "lamp",
            name = "Lamp",
            type = "toggle",
            primaryEntity = "light.living_room",
            secondaryEntity = ""
        )
        assertFalse(a == b)
    }
}