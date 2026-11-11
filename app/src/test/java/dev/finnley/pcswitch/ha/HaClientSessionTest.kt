package dev.finnley.pcswitch.ha

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2-Tests: HaClient-robustheit (R10, R11, R3, R2).
 * S3-Tests: HaClient eventgetrieben (R1, R6, R-legacy).
 * Hand-rolled Fake-WebSocket-Factory + Fake-Clock (TestScheduler-Virtual Time).
 * Pure JVM, kein MockK/Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HaClientSessionTest {

    // ---------- Fakes ----------

    private class FakeWs(private val factory: FakeWsFactory) : WebSocket {
        val sent = mutableListOf<String>()
        var listener: WebSocketListener? = null
        var openedAt = 0L
        private val req = Request.Builder().url("ws://fake/api/websocket").build()

        override fun request(): Request = req

        fun open() {
            openedAt = factory.clock()
            val resp = Response.Builder()
                .request(Request.Builder().url("ws://fake/api/websocket").build())
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .build()
            listener?.onOpen(this, resp)
        }

        fun message(text: String) {
            listener?.onMessage(this, text)
        }

        fun closeFromServer(code: Int = 1006) {
            listener?.onClosed(this, code, "server closed")
        }

        fun fail(t: Throwable) {
            listener?.onFailure(this, t, null)
        }

        override fun cancel() {}

        override fun close(code: Int, reason: String?): Boolean {
            listener?.onClosed(this, code, reason ?: "")
            return true
        }

        override fun send(text: String): Boolean {
            sent.add(text)
            factory.onSend(this, text)
            return true
        }

        override fun send(message: ByteString): Boolean = true

        override fun queueSize(): Long = 0
    }

    private class FakeWsFactory : okhttp3.WebSocket.Factory {
        val connections = mutableListOf<FakeWs>()
        var clock: () -> Long = { 0L }
        var onSend: (FakeWs, String) -> Unit = { _, _ -> }
        var onConnect: (FakeWs) -> Unit = {}

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            val ws = FakeWs(this)
            ws.listener = listener
            connections.add(ws)
            onConnect(ws)
            return ws
        }
    }

    private fun session(
        factory: FakeWsFactory,
        scope: kotlinx.coroutines.CoroutineScope,
        entities: Set<String>,
        clock: () -> Long,
    ) = HaClient.HaSession(
        base = "192.168.1.10:8123",
        secureDefault = false,
        token = "tok",
        entities = entities,
        factory = factory,
        scope = scope,
        clock = clock,
        backoff = ReconnectBackoff { 0.5 },
    )

    private fun getStatesSent(ws: FakeWs): Int =
        ws.sent.count { JSONObject(it).optString("type") == "get_states" }

    // ---------- Tests ----------

    /** R3: auth_invalid → kein Reconnect (keine endlose 4001-Schleife). */
    @Test
    fun auth_invalid_stops_without_reconnect() = runTest {
        val factory = FakeWsFactory()
        val s = session(factory, this, setOf("switch.pc")) {
            testScheduler.currentTime
        }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        runCurrent()
        ws.message("""{"type":"auth_invalid"}""")
        runCurrent()
        assertTrue("authFailed muss gesetzt sein", s.authFailed.value)
        advanceTimeBy(35_000)
        assertEquals("Kein Reconnect nach auth_invalid", 1, factory.connections.size)
        s.stop()
    }

    /** R2: Reconnect-Delays folgen dem Backoff (1s→30s, Jitter), steigend. */
    @Test
    fun reconnect_delays_follow_backoff() = runTest {
        val factory = FakeWsFactory()
        factory.clock = { testScheduler.currentTime }
        // jede Verbindung: öffnen, sofort Server-Close → Reconnect
        factory.onConnect = { ws -> ws.open(); ws.closeFromServer() }
        val s = session(factory, this, emptySet()) { testScheduler.currentTime }
        s.start()
        advanceTimeBy(9_000)
        val t = factory.connections.map { it.openedAt }
        assertEquals("3 Closes → 4 Verbindungen", 4, t.size)
        val d = (1 until t.size).map { t[it] - t[it - 1] }
        assertTrue("d1=${d[0]} in [1000,1500]", d[0] in 1000..1500)
        assertTrue("d2=${d[1]} in [2000,3000]", d[1] in 2000..3000)
        assertTrue("d3=${d[2]} in [4000,6000]", d[2] in 4000..6000)
        assertTrue("Delays steigend", d[0] < d[1] && d[1] < d[2])
        s.stop()
    }

    /** R10: get_states ohne Antwort → 8s-Timeout, kein Crash, Callback NICHT aufgerufen. */
    @Test
    fun get_states_times_out_at_8s() = runTest {
        val factory = FakeWsFactory()
        // Fake-WS antwortet nie auf Requests; subscribe_entities wird abgelehnt
        // (ältere HA) → Legacy-Pfad mit get_states-Loop.
        factory.onSend = { ws, text ->
            val o = JSONObject(text)
            if (o.optString("type") == "subscribe_entities") {
                ws.message(
                    """{"id":${o.optInt("id")},"type":"result","success":false,"error":{"code":"not_supported","message":"unknown command"}}"""
                )
            }
        }
        val s = session(factory, this, setOf("switch.pc")) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        runCurrent()
        advanceTimeBy(12_000)
        runCurrent()
        assertTrue("Callback nie aufgerufen (States leer)", s.entityStates.value.isEmpty())
        assertTrue(
            "Legacy-Watchdog läuft weiter (≥3 get_states: init + 5s + 10s), gesendet=${getStatesSent(ws)}",
            getStatesSent(ws) >= 3
        )
        s.stop()
    }

    /** R1: Entity-Event-Message aktualisiert entityStates. */
    @Test
    fun state_event_updates_entity_states() = runTest {
        val factory = FakeWsFactory()
        val s = session(factory, this, setOf("switch.pc")) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        runCurrent()
        ws.message(
            """{"type":"event","event":{"entity_id":"switch.pc","new_state":{"state":"on"}}}"""
        )
        runCurrent()
        assertEquals(mapOf("switch.pc" to "on"), s.entityStates.value)
        s.stop()
    }

    // ---------- S3: eventgetrieben (R1, R6, R-legacy) ----------

    /**
     * R1: auth_ok → subscribe_entities mit entity_ids.
     * ABWEICHUNG vom Design: coalesce_messages wird NICHT gesendet — HA validiert
     * das Schema strikt (voluptuous PREVENT_EXTRA, websocket_api/connection.py)
     * und würde das Feld als "extra keys not allowed" ablehnen, was den
     * Legacy-Fallback erzwingen und den eventgetriebenen Pfad dauerhaft
     * deaktivieren würde.
     */
    @Test
    fun auth_ok_sends_subscribe_entities_with_ids_and_coalesce() = runTest {
        val factory = FakeWsFactory()
        val s = session(factory, this, setOf("light.x", "switch.y")) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        runCurrent()
        val sub = ws.sent
            .map { JSONObject(it) }
            .firstOrNull { it.optString("type") == "subscribe_entities" }
        assertNotNull("subscribe_entities muss gesendet werden", sub)
        val ids = sub!!.getJSONArray("entity_ids")
        assertEquals(
            listOf("light.x", "switch.y"),
            (0 until ids.length()).map { ids.optString(it) }
        )
        assertFalse(
            "coalesce_messages würde HA ablehnen (PREVENT_EXTRA) → nicht senden",
            sub.has("coalesce_messages")
        )
        s.stop()
    }

    /** R-legacy: keine Entities → direkter Legacy-Pfad (subscribe_events + get_states). */
    @Test
    fun auth_ok_legacy_path_without_entity_ids() = runTest {
        val factory = FakeWsFactory()
        val s = session(factory, this, emptySet()) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        runCurrent()
        assertEquals("Legacy: get_states sofort gesendet", 1, getStatesSent(ws))
        assertTrue(
            "Legacy: subscribe_events (state_changed) gesendet",
            ws.sent.any {
                val o = JSONObject(it)
                o.optString("type") == "subscribe_events" &&
                    o.optString("event_type") == "state_changed"
            }
        )
        assertFalse(
            "Kein subscribe_entities ohne Entities",
            ws.sent.any { JSONObject(it).optString("type") == "subscribe_entities" }
        )
        s.stop()
    }

    /** R-legacy: HA antwortet auf subscribe_entities mit Fehler → Legacy-Pfad + 5s-Loop. */
    @Test
    fun legacy_ha_error_falls_back_to_get_states_loop() = runTest {
        val factory = FakeWsFactory()
        factory.onSend = { ws, text ->
            val o = JSONObject(text)
            if (o.optString("type") == "subscribe_entities") {
                ws.message(
                    """{"id":${o.optInt("id")},"type":"result","success":false,"error":{"code":"not_supported","message":"unknown command"}}"""
                )
            }
        }
        val s = session(factory, this, setOf("switch.pc")) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        runCurrent()
        assertTrue(
            "Fallback: subscribe_events (state_changed) gesendet",
            ws.sent.any {
                val o = JSONObject(it)
                o.optString("type") == "subscribe_events" &&
                    o.optString("event_type") == "state_changed"
            }
        )
        assertTrue("Fallback: get_states gesendet", getStatesSent(ws) >= 1)
        advanceTimeBy(12_000)
        runCurrent()
        assertTrue(
            "Fallback: 5s-get_states-Loop läuft (t=0,5,10 → ≥3), gesendet=${getStatesSent(ws)}",
            getStatesSent(ws) >= 3
        )
        s.stop()
    }

    // ---------- S4: press mit Zustellbestätigung (B4) ----------

    /** B4: press bekommt HA-Result success=true → true. */
    @Test
    fun press_confirmed_by_ha_returns_true() = runTest {
        val factory = FakeWsFactory()
        factory.onSend = { ws, text ->
            val o = JSONObject(text)
            if (o.optString("type") == "call_service") {
                ws.message("""{"id":${o.optInt("id")},"type":"result","success":true}""")
            }
        }
        val s = session(factory, this, setOf("switch.pc")) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        runCurrent()
        assertTrue("press muss bestätigt werden", s.press("switch.pc"))
        s.stop()
    }

    /** B4: press bekommt HA-Fehler (success=false) → false, Session bleibt alive. */
    @Test
    fun press_ha_error_returns_false() = runTest {
        val factory = FakeWsFactory()
        factory.onSend = { ws, text ->
            val o = JSONObject(text)
            if (o.optString("type") == "call_service") {
                ws.message(
                    """{"id":${o.optInt("id")},"type":"result","success":false,"message":"entity not found"}"""
                )
            }
        }
        val s = session(factory, this, setOf("switch.pc")) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        runCurrent()
        assertFalse("HA-Fehler muss false liefern", s.press("switch.pc"))
        assertTrue("Session bleibt alive (keine Netzwerk-Störung)", s.alive.value)
        s.stop()
    }

    /** B4: keine Antwort in 5s → false + Session wird als tot markiert + Reconnect. */
    @Test
    fun press_timeout_marks_session_dead_and_reconnects() = runTest {
        val factory = FakeWsFactory()
        factory.clock = { testScheduler.currentTime }
        val s = session(factory, this, setOf("switch.pc")) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        runCurrent()
        // press blockiert bis zur 5s-Timeout (Virtual Time).
        assertFalse("Timeout muss false liefern", s.press("switch.pc"))
        assertFalse("Zombie-Session muss als tot markiert sein", s.alive.value)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("Reconnect muss eingeplant sein", 2, factory.connections.size)
        s.stop()
    }

    /** R11: Events halten die Drift unter 60s → kein volles get_states. */
    @Test
    fun drift_watchdog_no_fetch_within_60s_of_events() = runTest {
        val factory = FakeWsFactory()
        val s = session(factory, this, setOf("switch.pc")) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        // Init-Snapshot (subscribe_entities: a=Addition, s=State) ersetzt den Store.
        ws.message("""{"type":"event","event":{"a":{"switch.pc":{"s":"on"}}}}""")
        runCurrent()
        assertEquals(mapOf("switch.pc" to "on"), s.entityStates.value)
        // Events alle 20s → Drift maximal 20s, nie >60s.
        repeat(3) {
            advanceTimeBy(20_000)
            runCurrent()
            ws.message("""{"type":"event","event":{"c":{"switch.pc":{"+":{"s":"off"}}}}}""")
            runCurrent()
        }
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(
            "Events halten Drift <60s → kein get_states, gesendet=${getStatesSent(ws)}",
            0,
            getStatesSent(ws)
        )
        assertEquals(mapOf("switch.pc" to "off"), s.entityStates.value)
        s.stop()
    }

    /** R11: >60s Stille → volles get_states (Drift-Sync). */
    @Test
    fun drift_watchdog_fetches_after_60s_silence() = runTest {
        val factory = FakeWsFactory()
        val s = session(factory, this, setOf("switch.pc")) { testScheduler.currentTime }
        s.start()
        val ws = factory.connections.single()
        ws.open()
        ws.message("""{"type":"auth_ok"}""")
        ws.message("""{"type":"event","event":{"a":{"switch.pc":{"s":"on"}}}}""")
        runCurrent()
        // Danach Stille: kein Entity-Event mehr.
        advanceTimeBy(75_000)
        runCurrent()
        assertTrue(
            "61s+ Stille → get_states gesendet (Drift>60s), gesendet=${getStatesSent(ws)}",
            getStatesSent(ws) >= 1
        )
        s.stop()
    }
}
