package dev.finnley.pcswitch.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-Logik-Tests für [HaClient] (keine echten Server-Abhängigkeiten).
 */
class HaClientTest {

    // ── URL-Ableitung ────────────────────────────────────────────────

    @Test
    fun httpBaseLanInsecure() {
        assertEquals(
            "http://192.168.1.10:8123",
            HaClient.httpBase("192.168.1.10:8123", false)
        )
    }

    @Test
    fun httpBaseSecureDefault() {
        assertEquals(
            "https://192.168.1.10:8123",
            HaClient.httpBase("192.168.1.10:8123", true)
        )
    }

    @Test
    fun httpBaseExplicitSchemeKept() {
        assertEquals("https://ha.local", HaClient.httpBase("https://ha.local", false))
        assertEquals("http://ha.local:8123", HaClient.httpBase("http://ha.local:8123", true))
    }

    @Test
    fun httpBaseFromWs() {
        assertEquals("http://ha:8123", HaClient.httpBase("ws://ha:8123", true))
        assertEquals("https://ha:8123", HaClient.httpBase("wss://ha:8123", false))
    }

    @Test
    fun httpBaseEmptyOrInvalid() {
        assertNull(HaClient.httpBase("", true))
        assertNull(HaClient.httpBase("   ", false))
        assertNull(HaClient.httpBase("http://:8123", false))
    }

    @Test
    fun wsUrl() {
        assertEquals(
            "ws://ha:8123/api/websocket",
            HaClient.wsUrl("http://ha:8123", true)
        )
        assertEquals(
            "wss://ha:8123/api/websocket",
            HaClient.wsUrl("https://ha:8123", false)
        )
        assertNull(HaClient.wsUrl("", true))
    }

    @Test
    fun wsUrlDefaultPorts() {
        assertEquals("ws://ha.local/api/websocket", HaClient.wsUrl("ha.local", false))
        assertEquals("wss://ha.local/api/websocket", HaClient.wsUrl("ha.local", true))
    }

    // ── Token-REST-Check-Klassifikation ──────────────────────────────

    @Test
    fun classifyTokenCode() {
        assertEquals(HaClient.TokenCheck.VALID, HaClient.classifyTokenCode(200))
        assertEquals(HaClient.TokenCheck.VALID, HaClient.classifyTokenCode(204))
        assertEquals(HaClient.TokenCheck.INVALID_TOKEN, HaClient.classifyTokenCode(401))
        assertEquals(HaClient.TokenCheck.INVALID_TOKEN, HaClient.classifyTokenCode(403))
        assertEquals(HaClient.TokenCheck.UNREACHABLE, HaClient.classifyTokenCode(500))
        assertEquals(HaClient.TokenCheck.UNREACHABLE, HaClient.classifyTokenCode(302))
    }

    @Test
    fun checkTokenBlankTokenOrBase() {
        assertEquals(
            HaClient.TokenCheck.UNREACHABLE,
            HaClient.checkToken("192.168.1.10:8123", "", false)
        )
        assertEquals(
            HaClient.TokenCheck.UNREACHABLE,
            HaClient.checkToken("", "x.y.z", true)
        )
        assertEquals(
            HaClient.TokenCheck.UNREACHABLE,
            HaClient.checkToken("http://:8123", "x.y.z", false)
        )
    }

    @Test
    fun checkTokenConnectionRefused() {
        // 127.0.0.1:1 hat (praktisch) keinen Listener → deterministisch unerreichbar.
        assertEquals(
            HaClient.TokenCheck.UNREACHABLE,
            HaClient.checkToken("127.0.0.1:1", "x.y.z", false)
        )
    }
}
