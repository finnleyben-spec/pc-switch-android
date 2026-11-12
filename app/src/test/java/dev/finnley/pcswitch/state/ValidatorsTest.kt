package dev.finnley.pcswitch.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    // ── IPv4 ─────────────────────────────────────────────────────────

    @Test
    fun ipv4Valid() {
        assertTrue(Validators.isValidIpv4("192.168.1.10"))
        assertTrue(Validators.isValidIpv4("10.0.0.5"))
        assertTrue(Validators.isValidIpv4("0.0.0.0"))
        assertTrue(Validators.isValidIpv4("255.255.255.255"))
    }

    @Test
    fun ipv4Invalid() {
        assertFalse(Validators.isValidIpv4("256.1.1.1"))
        assertFalse(Validators.isValidIpv4("1.2.3"))
        assertFalse(Validators.isValidIpv4("a.b.c.d"))
        assertFalse(Validators.isValidIpv4(""))
        assertFalse(Validators.isValidIpv4("192.168.1"))
    }

    // ── Host ─────────────────────────────────────────────────────────

    @Test
    fun hostValid() {
        assertTrue(Validators.isHost("homeassistant.local"))
        assertTrue(Validators.isHost("meine-pc.ts.net"))
        assertTrue(Validators.isHost("192.168.1.10"))
        assertTrue(Validators.isHost("  ha.local  ")) // wird getrimmt
        assertTrue(Validators.isHost("a"))
    }

    @Test
    fun hostInvalid() {
        assertFalse(Validators.isHost(""))
        assertFalse(Validators.isHost("   "))
        assertFalse(Validators.isHost("my pc"))
        assertFalse(Validators.isHost("-"))
    }

    // ── Host:Port ────────────────────────────────────────────────────

    @Test
    fun hostPortValid() {
        assertTrue(Validators.isValidHostPort("homeassistant.local"))
        assertTrue(Validators.isValidHostPort("homeassistant.local:8123"))
        assertTrue(Validators.isValidHostPort("192.168.1.10:1"))
        assertTrue(Validators.isValidHostPort("100.106.82.86:443"))
    }

    @Test
    fun hostPortInvalid() {
        assertFalse(Validators.isValidHostPort(""))
        assertFalse(Validators.isValidHostPort(":8123"))
        assertFalse(Validators.isValidHostPort("host:0"))
        assertFalse(Validators.isValidHostPort("host:70000"))
        assertFalse(Validators.isValidHostPort("host:abc"))
    }

    // ── Schema / bare host ───────────────────────────────────────────

    @Test
    fun explicitScheme() {
        assertEquals("http", Validators.explicitScheme("http://192.168.1.10:8123"))
        assertEquals("https", Validators.explicitScheme("https://ha.local"))
        assertEquals("", Validators.explicitScheme("192.168.1.10:8123"))
        assertEquals("", Validators.explicitScheme("ha.local"))
    }

    @Test
    fun bareHost() {
        assertEquals("192.168.1.10", Validators.bareHost("http://192.168.1.10:8123"))
        assertEquals("ha.local", Validators.bareHost("https://ha.local:8123/"))
        assertEquals("ha.local", Validators.bareHost("ha.local:8123"))
    }

    // ── Private-Host-Erkennung / HTTPS-Pflicht ───────────────────────

    @Test
    fun privateNetworkHost() {
        assertTrue(Validators.isPrivateNetworkHost("192.168.1.10"))
        assertTrue(Validators.isPrivateNetworkHost("10.0.0.5"))
        assertTrue(Validators.isPrivateNetworkHost("172.16.0.1"))
        assertTrue(Validators.isPrivateNetworkHost("127.0.0.1"))
        assertTrue(Validators.isPrivateNetworkHost("homeassistant.local"))
        assertTrue(Validators.isPrivateNetworkHost("ha.lan"))
        // CGNAT/Tailscale
        assertTrue(Validators.isPrivateNetworkHost("100.106.82.86"))
    }

    @Test
    fun publicHost() {
        assertFalse(Validators.isPrivateNetworkHost("8.8.8.8"))
        assertFalse(Validators.isPrivateNetworkHost("172.32.0.1"))
        assertFalse(Validators.isPrivateNetworkHost("101.106.82.86"))
        assertFalse(Validators.isPrivateNetworkHost("meine-pc.ts.net"))
    }

    @Test
    fun requiresHttps() {
        // Öffentlicher Host über explizites http:// → Pflicht.
        assertTrue(Validators.requiresHttps("http://8.8.8.8:8123"))
        // Private/lokale Ziele über http:// sind OK.
        assertFalse(Validators.requiresHttps("http://192.168.1.10:8123"))
        assertFalse(Validators.requiresHttps("http://homeassistant.local:8123"))
        assertFalse(Validators.requiresHttps("http://100.106.82.86:8123"))
        // Ohne explizites Schema entscheidet secureDefault — hier keine Pflicht.
        assertFalse(Validators.requiresHttps("192.168.1.10:8123"))
        assertFalse(Validators.requiresHttps("8.8.8.8:8123"))
        // https:// ist immer OK.
        assertFalse(Validators.requiresHttps("https://8.8.8.8:8123"))
    }

    // ── Entity-ID ────────────────────────────────────────────────────

    @Test
    fun entityIdValid() {
        assertTrue(Validators.isValidEntityId("button.turn_on_my_pc"))
        assertTrue(Validators.isValidEntityId("switch.my_pc"))
        assertTrue(Validators.isValidEntityId("input_button.turn_off_pc"))
        assertTrue(Validators.isValidEntityId("Button.Turn_On")) // wird gekleingedreht
        assertTrue(Validators.isValidEntityId("b.p"))
    }

    @Test
    fun entityIdInvalid() {
        assertFalse(Validators.isValidEntityId(""))
        assertFalse(Validators.isValidEntityId("no_dot"))
        assertFalse(Validators.isValidEntityId("a.b.c"))
        assertFalse(Validators.isValidEntityId("button."))
        assertFalse(Validators.isValidEntityId(".turn_on"))
        assertFalse(Validators.isValidEntityId("butto n.turn"))
        assertFalse(Validators.isValidEntityId("button.turn_on/pc"))
        assertFalse(
            Validators.isValidEntityId("button." + "a".repeat(65))
        )
    }
}
