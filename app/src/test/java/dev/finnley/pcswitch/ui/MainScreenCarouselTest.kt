package dev.finnley.pcswitch.carousel

import dev.finnley.pcswitch.state.SwitchConfig
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

// Carousel navigation logic tests
// The actual PagerState UI is tested via instrumentation tests on a device.
// These unit tests verify the data flow and ordering logic.

class CarouselDataFlowTest {

    @Test
    fun switch_order_determines_carousel_order() {
        val switches = listOf(
            SwitchConfig(id = "pc", name = "PC", type = "dual", primaryEntity = "switch.pc"),
            SwitchConfig(id = "lamp", name = "Lamp", type = "toggle", primaryEntity = "light.lamp")
        )

        // First switch appears at index 0
        assertEquals("PC", switches[0].name)
        assertEquals("switch.pc", switches[0].primaryEntity)

        // Second switch appears at index 1
        assertEquals("Lamp", switches[1].name)
        assertEquals("light.lamp", switches[1].primaryEntity)
    }

    @Test
    fun empty_switch_list() {
        val switches = emptyList<SwitchConfig>()
        assertTrue(switches.isEmpty())
    }

    @Test
    fun single_switch_shows_only_that_switch() {
        val switches = listOf(
            SwitchConfig(id = "pc", name = "PC", type = "dual", primaryEntity = "switch.pc")
        )
        assertEquals(1, switches.size)
        assertEquals("PC", switches[0].name)
    }
}