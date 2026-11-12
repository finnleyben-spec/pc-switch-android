package dev.finnley.pcswitch.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryFilterTest {

    @Test
    fun haByName() {
        assertTrue(DiscoveryFilter.isHaCandidate("homeassistant", "homeassistant.local", 8123))
        assertTrue(DiscoveryFilter.isHaCandidate("home-assistant", "192.168.1.10", 8080))
        assertTrue(DiscoveryFilter.isHaCandidate("HomeAssistant", "ha.local", 8123))
    }

    @Test
    fun haByStandardPort() {
        assertTrue(DiscoveryFilter.isHaCandidate("http", "192.168.1.10", 8123))
        assertTrue(DiscoveryFilter.isHaCandidate("webserver", "10.0.0.5", 8123))
    }

    @Test
    fun haByShortNameLocal() {
        assertTrue(DiscoveryFilter.isHaCandidate("ha", "ha.local", 8080))
    }

    @Test
    fun notHa() {
        assertFalse(DiscoveryFilter.isHaCandidate("printer", "printer.local", 80))
        assertFalse(DiscoveryFilter.isHaCandidate("mys3", "storage.lan", 9000))
        assertFalse(DiscoveryFilter.isHaCandidate("http", "8.8.8.8", 80))
    }

    @Test
    fun likelihood() {
        assertEquals(1, DiscoveryFilter.haLikelihood("homeassistant", "x", 8080))
        assertEquals(1, DiscoveryFilter.haLikelihood("ha", "ha.local", 80))
        assertEquals(0, DiscoveryFilter.haLikelihood("http", "10.0.0.5", 8123))
    }
}
