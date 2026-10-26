package dev.finnley.pcswitch.ha

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Home Assistant-Zugriff — gleiches Flat-Protokoll wie die PWA:
 *   -> {"type":"auth","access_token":"..."}   -> {"type":"auth_ok"}
 *   -> {"id":n,"type":"get_states"}           -> {"id":n,"type":"result","result":[...]}
 *   -> {"id":n,"type":"subscribe_events","event_type":"state_changed"}
 *   -> {"id":n,"type":"call_service","domain":"button","service":"press","entity_id":"..."}
 */
object HaClient {

    /** HTTP-Erreichbarkeits-Probe gegen <base>/api/ (2xx/401/403 = erreichbar). */
    fun probe(base: String, secureDefault: Boolean): Boolean {
        val url = httpBase(base, secureDefault) ?: return false
        val req = Request.Builder().url(url.trimEnd('/') + "/api/").build()
        return try {
            sharedClient.newCall(req).execute().use { r ->
                r.code in 200..299 || r.code == 401 || r.code == 403
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Base ("host[:port]" oder http(s)://) → normale http(s)-URL. */
    fun httpBase(base: String, secureDefault: Boolean): String? {
        val b = base.trim()
        if (b.isEmpty()) return null
        val s = when {
            b.startsWith("ws://") -> "http://" + b.removePrefix("ws://")
            b.startsWith("wss://") -> "https://" + b.removePrefix("wss://")
            b.startsWith("http://") || b.startsWith("https://") -> b
            else -> (if (secureDefault) "https://" else "http://") + b
        }
        return try {
            java.net.URI(s).let { s }
        } catch (e: Exception) {
            null
        }
    }

    /** Base → ws(s)://<host>[:port]/api/websocket. */
    fun wsUrl(base: String, secureDefault: Boolean): String? {
        val http = httpBase(base, secureDefault) ?: return null
        return try {
            val u = java.net.URI(http)
            val scheme = if (http.startsWith("https")) "wss" else "ws"
            val port = if (u.port == -1) "" else ":%d".format(u.port)
            "$scheme://${u.host}$port/api/websocket"
        } catch (e: Exception) {
            null
        }
    }

    private val sharedClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Eine lebende HA-Verbindung: Auth, State-Subscribe, Watchdog (get_states alle 20s),
     * Auto-Reconnect nach 2.5s. [entityStates] hält die States der [entities].
     */
    class HaSession(
        base: String,
        secureDefault: Boolean,
        private val token: String,
        private val entities: Set<String>,
    ) {
        val entityStates = MutableStateFlow<Map<String, String>>(emptyMap())
        val alive = MutableStateFlow(false)
        val lastUpdate = MutableStateFlow(0L)
        val authFailed = MutableStateFlow(false)

        private val url: String? = wsUrl(base, secureDefault)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        private var ws: WebSocket? = null
        private var reqId = 0
        private val pending = ConcurrentHashMap<Int, (JSONObject) -> Unit>()
        private var authed = false
        private var reconnectJob: Job? = null
        private var watchdogJob: Job? = null
        private var stopped = false

        fun start() {
            val u = url ?: return
            stopped = false
            ws = client.newWebSocket(
                Request.Builder().url(u).build(),
                object : WebSocketListener() {
                    override fun onOpen(w: WebSocket, response: Response) {
                        authed = false
                        w.send("""{"type":"auth","access_token":"$token"}""")
                    }

                    override fun onMessage(w: WebSocket, text: String) = handle(w, text)

                    override fun onFailure(w: WebSocket, t: Throwable, response: Response?) {
                        alive.value = false
                        scheduleReconnect()
                    }

                    override fun onClosed(w: WebSocket, code: Int, reason: String) {
                        alive.value = false
                        scheduleReconnect()
                    }
                }
            )
        }

        fun stop() {
            stopped = true
            watchdogJob?.cancel()
            reconnectJob?.cancel()
            ws?.cancel()
            ws = null
            scope.cancel()
        }

        /** call_service: button.press, light.turn_on/off, switch.turn_on/off (Domain aus Entity ableiten). */
        fun press(entity: String): Boolean {
            val w = ws ?: return false
            if (!authed) return false
            val domain = entity.substringBefore('.', "")
            val service = when (domain) {
                "button", "input_button" -> "press"
                "light", "switch", "fan", "cover", "lock", "vacuum" -> "toggle"
                else -> "press"
            }
            val o = JSONObject()
                .put("id", ++reqId)
                .put("type", "call_service")
                .put("domain", domain)
                .put("service", service)
                .put("service_data", JSONObject().put("entity_id", entity))
            return w.send(o.toString())
        }

        private fun scheduleReconnect() {
            if (stopped || reconnectJob?.isActive == true) return
            reconnectJob = scope.launch {
                delay(2500)
                reconnectJob = null
                if (stopped) return@launch
                ws?.cancel()
                start()
            }
        }

        private fun handle(w: WebSocket, text: String) {
            val o = try {
                JSONObject(text)
            } catch (e: Exception) {
                return
            }
            when (o.optString("type")) {
                "auth_ok" -> {
                    authed = true
                    authFailed.value = false
                    alive.value = true
                    getStates { applyStates(it.optJSONArray("result")) }
                    w.send(
                        JSONObject()
                            .put("id", ++reqId)
                            .put("type", "subscribe_events")
                            .put("event_type", "state_changed")
                            .toString()
                    )
                    watchdogJob?.cancel()
                    watchdogJob = scope.launch { watchdog() }
                }

                "auth_invalid" -> {
                    alive.value = false
                    authFailed.value = true
                    w.close(4001, "auth_invalid")
                }

                "result" -> {
                    val id = o.optInt("id", 0)
                    pending.remove(id)?.invoke(o)
                }

                "event" -> {
                    val ev = o.optJSONObject("event") ?: return
                    val entity = ev.optString("entity_id")
                    if (entity in entities) {
                        val ns = ev.optJSONObject("new_state")
                        entityStates.value =
                            entityStates.value + (entity to (ns?.optString("state") ?: "unknown"))
                        lastUpdate.value = System.currentTimeMillis()
                    }
                }
            }
        }

        private fun getStates(cb: (JSONObject) -> Unit) {
            val id = ++reqId
            pending[id] = cb
            ws?.send(JSONObject().put("id", id).put("type", "get_states").toString())
            scope.launch {
                delay(6000)
                pending.remove(id)
            }
        }

        /** Fetch all entity IDs from HA via get_entity_ids command. */
        suspend fun getEntityIds(): List<String> {
            return withContext(Dispatchers.IO) {
                val channel = Channel<List<String>>(1)
                val id = ++reqId
                pending[id] = { o ->
                    val arr = o.optJSONArray("result")
                    if (arr != null) {
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            list.add(arr.optString(i))
                        }
                        channel.trySend(list)
                    } else {
                        channel.trySend(emptyList())
                    }
                }
                ws?.send(JSONObject().put("id", id).put("type", "get_entity_ids").toString())
                channel.receive()
            }
        }

        /** Fetch all entity states (with friendly names from attributes). */
        suspend fun getEntityStates(): List<EntityState> {
            return withContext(Dispatchers.IO) {
                val channel = Channel<List<EntityState>>(1)
                val id = ++reqId
                pending[id] = { o ->
                    val arr = o.optJSONArray("result")
                    if (arr != null) {
                        val list = mutableListOf<EntityState>()
                        for (i in 0 until arr.length()) {
                            val s = arr.optJSONObject(i) ?: continue
                            val attrs = s.optJSONObject("attributes")
                            val friendlyName = attrs?.optString("friendly_name", "") ?: ""
                            list.add(EntityState(s.optString("entity_id"), s.optString("state"), friendlyName))
                        }
                        channel.trySend(list)
                    } else {
                        channel.trySend(emptyList())
                    }
                }
                ws?.send(JSONObject().put("id", id).put("type", "get_states").toString())
                channel.receive()
            }
        }

        private suspend fun watchdog() {
            while (true) {
                delay(5_000)
                if (!alive.value) return
                getStates { applyStates(it.optJSONArray("result")) }
            }
        }

        private fun applyStates(arr: JSONArray?) {
            if (arr == null) return
            val m = HashMap<String, String>()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val e = s.optString("entity_id")
                if (e in entities) m[e] = s.optString("state")
            }
            entityStates.value = m
            lastUpdate.value = System.currentTimeMillis()
        }
    }

    /** Entity state with entity_id, state value, and friendly name. */
    data class EntityState(
        val entityId: String,
        val state: String,
        val friendlyName: String
    )
}
