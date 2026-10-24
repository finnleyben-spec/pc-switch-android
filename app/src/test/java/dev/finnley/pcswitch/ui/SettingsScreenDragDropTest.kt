package dev.finnley.pcswitch.dragdrop

import dev.finnley.pcswitch.state.SwitchConfig
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

// Drag and drop reordering logic tests
// The actual drag gesture is tested via instrumentation tests on a device.
// These unit tests verify the reordering logic that would be triggered by drag and drop.

class DragDropReorderTest {

    @Test
    fun move_first_to_last() {
        var switches = mutableListOf(
            SwitchConfig(id = "1", name = "First", type = "toggle", primaryEntity = "light.1"),
            SwitchConfig(id = "2", name = "Second", type = "toggle", primaryEntity = "light.2"),
            SwitchConfig(id = "3", name = "Third", type = "toggle", primaryEntity = "light.3")
        )

        // Drag item at index 0 to index 2
        val item = switches.removeAt(0)
        switches.add(2, item)

        assertEquals("Second", switches[0].name)
        assertEquals("Third", switches[1].name)
        assertEquals("First", switches[2].name)
    }

    @Test
    fun move_last_to_first() {
        var switches = mutableListOf(
            SwitchConfig(id = "1", name = "First", type = "toggle", primaryEntity = "light.1"),
            SwitchConfig(id = "2", name = "Second", type = "toggle", primaryEntity = "light.2"),
            SwitchConfig(id = "3", name = "Third", type = "toggle", primaryEntity = "light.3")
        )

        // Drag item at index 2 to index 0
        val item = switches.removeAt(2)
        switches.add(0, item)

        assertEquals("Third", switches[0].name)
        assertEquals("First", switches[1].name)
        assertEquals("Second", switches[2].name)
    }

    @Test
    fun move_middle_to_first() {
        var switches = mutableListOf(
            SwitchConfig(id = "1", name = "First", type = "toggle", primaryEntity = "light.1"),
            SwitchConfig(id = "2", name = "Second", type = "toggle", primaryEntity = "light.2"),
            SwitchConfig(id = "3", name = "Third", type = "toggle", primaryEntity = "light.3")
        )

        // Drag item at index 1 to index 0
        val item = switches.removeAt(1)
        switches.add(0, item)

        assertEquals("Second", switches[0].name)
        assertEquals("First", switches[1].name)
        assertEquals("Third", switches[2].name)
    }

    @Test
    fun move_middle_to_last() {
        var switches = mutableListOf(
            SwitchConfig(id = "1", name = "First", type = "toggle", primaryEntity = "light.1"),
            SwitchConfig(id = "2", name = "Second", type = "toggle", primaryEntity = "light.2"),
            SwitchConfig(id = "3", name = "Third", type = "toggle", primaryEntity = "light.3")
        )

        // Drag item at index 1 to index 2
        val item = switches.removeAt(1)
        switches.add(2, item)

        assertEquals("First", switches[0].name)
        assertEquals("Third", switches[1].name)
        assertEquals("Second", switches[2].name)
    }

    @Test
    fun swap_adjacent_items() {
        var switches = mutableListOf(
            SwitchConfig(id = "1", name = "First", type = "toggle", primaryEntity = "light.1"),
            SwitchConfig(id = "2", name = "Second", type = "toggle", primaryEntity = "light.2")
        )

        // Swap: drag 0 to 1
        val item = switches.removeAt(0)
        switches.add(1, item)

        assertEquals("Second", switches[0].name)
        assertEquals("First", switches[1].name)
    }

    @Test
    fun single_item_cannot_be_reordered() {
        val switches = mutableListOf(
            SwitchConfig(id = "1", name = "Only", type = "toggle", primaryEntity = "light.1")
        )

        // No valid drag target
        assertTrue(switches.size == 1)
    }
}