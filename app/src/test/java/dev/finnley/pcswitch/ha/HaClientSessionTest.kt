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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2-Tests: HaClient-robustheit (R10, R11, R3, R2).
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
        // Fake-WS antwortet nie auf Requests
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
            "Watchdog läuft weiter (≥3 get_states: init + 5s + 10s), gesendet=${getStatesSent(ws)}",
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
}
