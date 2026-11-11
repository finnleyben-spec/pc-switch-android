package dev.finnley.pcswitch.ha

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Home Assistant-Zugriff — gleiches Flat-Protokoll wie die PWA:
 *   -> {"type":"auth","access_token":"..."}   -> {"type":"auth_ok"}
 *   -> {"id":n,"type":"get_states"}           -> {"id":n,"type":"result","result":[...]}
 *   -> {"id":n,"type":"subscribe_events","event_type":"state_changed"}
 *   -> {"id":n,"type":"call_service","domain":"button","service":"press","entity_id":"..."}
 */
object HaClient {

    private const val TAG = "HaClient"

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
     * Eine lebende HA-Verbindung: Auth, State-Subscribe, Watchdog (get_states),
     * Auto-Reconnect mit exponentiellem Backoff (1s→30s, [ReconnectBackoff]).
     * [entityStates] hält die States der [entities].
     *
     * Testbarkeit: [factory] (WebSocket-Factory), [scope], [clock] und [backoff]
     * sind injizierbar (Defaults = Produktverhalten); Public-API bleibt signaturstabil.
     */
    class HaSession(
        base: String,
        secureDefault: Boolean,
        private val token: String,
        private val entities: Set<String>,
        factory: WebSocket.Factory = defaultWebSocketClient(),
        scope: CoroutineScope? = null,
        private val clock: () -> Long = { System.currentTimeMillis() },
        backoff: ReconnectBackoff = ReconnectBackoff(),
    ) {
        val entityStates = MutableStateFlow<Map<String, String>>(emptyMap())
        val alive = MutableStateFlow(false)
        val lastUpdate = MutableStateFlow(0L)
        val authFailed = MutableStateFlow(false)
        /** Zeitpunkt des letzten Entity-Events/State-Sync (Drift-Watchdog, R11). */
        @Volatile
        var lastEntityEventAt: Long = clock()
            private set

        private val url: String? = wsUrl(base, secureDefault)
        private val factory: WebSocket.Factory = factory
        private val ownsScope = scope == null
        private val scope: CoroutineScope =
            scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val backoff: ReconnectBackoff = backoff

        @Volatile
        private var ws: WebSocket? = null
        private val reqId = AtomicInteger(0)
        private val pending = ConcurrentHashMap<Int, (JSONObject) -> Unit>()
        @Volatile
        private var authed = false
        private var reconnectJob: Job? = null
        private var watchdogJob: Job? = null
        @Volatile
        private var stopped = false
        /** true: HA < 2023.10 → subscribe_events + get_states-Loop (Legacy-Pfad). */
        @Volatile
        private var legacyMode = false
        /** true: erster subscribe_entities-Snapshot hat entityStates ersetzt (kein Merge). */
        @Volatile
        private var snapshotReplaced = false
        /** id der subscribe_entities-Request (für Error-Fallback, R-legacy); 0 = keine. */
        @Volatile
        private var subscribeEntitiesId = 0

        fun start() {
            val u = url ?: run {
                Log.e(TAG, "start(): keine gültige WS-URL aus Base — Connection übersprungen")
                return
            }
            Log.d(TAG, "connect: $u")
            stopped = false
            ws = factory.newWebSocket(
                Request.Builder().url(u).build(),
                object : WebSocketListener() {
                    override fun onOpen(w: WebSocket, response: Response) {
                        Log.d(TAG, "WS open — authenticating")
                        authed = false
                        w.send("""{"type":"auth","access_token":"$token"}""")
                    }

                    override fun onMessage(w: WebSocket, text: String) = handle(w, text)

                    override fun onFailure(w: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "WS failure: ${t.message} (http=${response?.code})")
                        alive.value = false
                        if (!authFailed.value) scheduleReconnect()
                    }

                    override fun onClosed(w: WebSocket, code: Int, reason: String) {
                        Log.w(TAG, "WS closed code=$code reason=\"$reason\"")
                        alive.value = false
                        if (!authFailed.value) scheduleReconnect()
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
            if (ownsScope) scope.cancel()
        }

        /**
         * call_service mit Antwort-Warte (B4-Fix): HA antwortet auf jedes id-gebeutete
         * Request mit {type:"result",success:...}. 5s ohne Antwort → Socket ist ein
         * Zombie (TCP tot, aber onFailure noch nicht gefeuert) → Session wird als
         * tot markiert und reconnectet. Damit wird kein Press mehr stillschweigend
         * in ein totes Socket geschickt (früher: ~15s-Blindfenster bis 15s-Ping).
         */
        suspend fun press(entity: String): Boolean {
            val w = ws ?: run {
                Log.w(TAG, "press($entity): kein WebSocket offen")
                return false
            }
            if (!authed) {
                Log.w(TAG, "press($entity): nicht authentifiziert")
                return false
            }
            val domain = entity.substringBefore('.', "")
            val service = when (domain) {
                "button", "input_button" -> "press"
                "light", "switch", "fan", "cover", "lock", "vacuum" -> "toggle"
                else -> "press"
            }
            val id = reqId.incrementAndGet()
            val ch = Channel<Boolean>(1)
            pending[id] = { o ->
                val ok = o.optBoolean("success", false)
                if (!ok) {
                    Log.w(TAG, "press($entity): HA-Fehler: " +
                        (o.optJSONObject("error")?.optString("message") ?: o.optString("message", "?")))
                }
                ch.trySend(ok)
            }
            val sent = w.send(
                JSONObject()
                    .put("id", id)
                    .put("type", "call_service")
                    .put("domain", domain)
                    .put("service", service)
                    .put("service_data", JSONObject().put("entity_id", entity))
                    .toString()
            )
            if (!sent) {
                pending.remove(id)
                Log.w(TAG, "press($entity): send fehlgeschlagen (Socket geschlossen)")
                return false
            }
            return try {
                val ok = withTimeout(5_000) { ch.receive() }
                Log.d(TAG, "press($entity) service=$service -> ${if (ok) "bestätigt" else "abgelehnt"}")
                ok
            } catch (e: CancellationException) {
                pending.remove(id)
                Log.w(TAG, "press($entity): keine Antwort in 5s → Zombie-Session, Reconnect")
                alive.value = false
                scheduleReconnect()
                false
            }
        }

        private fun scheduleReconnect() {
            if (stopped || authFailed.value || reconnectJob?.isActive == true) return
            val d = backoff.nextDelayMs()
            Log.d(TAG, "reconnect in ${d}ms")
            reconnectJob = scope.launch {
                delay(d)
                reconnectJob = null
                if (stopped || authFailed.value) return@launch
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
                    Log.d(TAG, "authenticated (${if (entities.isEmpty()) "legacy" else "subscribe_entities"}, ${entities.size} entities)")
                    authed = true
                    authFailed.value = false
                    alive.value = true
                    backoff.reset()
                    snapshotReplaced = false
                    if (entities.isEmpty()) {
                        // Keine Entities: nichts abzubonnieren → Legacy subscribe_events.
                        enterLegacyPath(w)
                    } else {
                        // HA >= 2023.10: subscribe_entities (R1+R6) — Server sendet
                        // Init-Snapshot + komprimierte a/c/r-Diffs. Antwortet der Server
                        // mit Fehler (ältere HA), wechselt der "result"-Handler in den
                        // Legacy-Pfad (subscribe_events + get_states-Loop).
                        legacyMode = false
                        subscribeEntitiesId = reqId.incrementAndGet()
                        val ids = JSONArray()
                        for (e in entities) ids.put(e)
                        w.send(
                            JSONObject()
                                .put("id", subscribeEntitiesId)
                                .put("type", "subscribe_entities")
                                .put("entity_ids", ids)
                                .toString()
                        )
                    }
                    watchdogJob?.cancel()
                    watchdogJob = scope.launch { watchdog() }
                }

                "auth_invalid" -> {
                    Log.e(TAG, "auth_invalid — Token von HA abgelehnt, kein Reconnect")
                    alive.value = false
                    authFailed.value = true
                    w.close(4001, "auth_invalid")
                }

                "result" -> {
                    val id = o.optInt("id", 0)
                    if (id == subscribeEntitiesId) {
                        subscribeEntitiesId = 0
                        if (!o.optBoolean("success", false)) {
                            // HA < 2023.10 kennt subscribe_entities nicht → Legacy-Pfad.
                            enterLegacyPath(w)
                        }
                        return
                    }
                    pending.remove(id)?.invoke(o)
                }

                "event" -> {
                    val ev = o.optJSONObject("event") ?: return
                    val a = ev.optJSONObject("a")
                    val c = ev.optJSONObject("c")
                    val r = ev.optJSONArray("r")
                    if (a == null && c == null && r == null) {
                        // Legacy subscribe_events: state_changed-Event.
                        val entity = ev.optString("entity_id")
                        if (entity in entities) {
                            val ns = ev.optJSONObject("new_state")
                            entityStates.value =
                                entityStates.value +
                                    (entity to (ns?.optString("state") ?: "unknown"))
                            lastEntityEventAt = clock()
                            lastUpdate.value = clock()
                        }
                        return
                    }
                    // subscribe_entities: komprimierte Diff-Nachrichten (HA >= 2023.10):
                    //   a: {entity_id: {s: state, ...}}          (Addition/Init-Snapshot)
                    //   c: {entity_id: {"+": {s: new_state, ...}}} (Change)
                    //   r: [entity_id, ...]                      (Removal)
                    val cur = HashMap(entityStates.value)
                    if (a != null) {
                        if (!snapshotReplaced) {
                            cur.clear()  // erstes Payload ERSETZT den Store (kein Merge)
                            snapshotReplaced = true
                        }
                        val keys = a.keys()
                        while (keys.hasNext()) {
                            val eid = keys.next()
                            if (eid in entities) {
                                cur[eid] = a.optJSONObject(eid)?.optString("s") ?: "unknown"
                            }
                        }
                    }
                    if (c != null) {
                        val keys = c.keys()
                        while (keys.hasNext()) {
                            val eid = keys.next()
                            if (eid in entities) {
                                c.optJSONObject(eid)?.optJSONObject("+")
                                    ?.optString("s")
                                    ?.let { cur[eid] = it }
                            }
                        }
                    }
                    if (r != null) {
                        for (i in 0 until r.length()) cur.remove(r.optString(i))
                    }
                    entityStates.value = cur
                    lastEntityEventAt = clock()
                    lastUpdate.value = clock()
                }
            }
        }

        private fun getStates(cb: (JSONObject) -> Unit) {
            sendRequest("get_states", cb)
        }

        /**
         * Request-Antwort über Channel mit hartem 8s-Timeout (R10):
         * antwortet der Server nicht, wird der Callback NICHT aufgerufen
         * (kein hängender receive(), kein loading=true-Deadlock).
         */
        private fun sendRequest(type: String, cb: (JSONObject) -> Unit) {
            val id = reqId.incrementAndGet()
            val ch = Channel<JSONObject>(1)
            pending[id] = { o -> ch.trySend(o) }
            ws?.send(JSONObject().put("id", id).put("type", type).toString())
            scope.launch {
                val o = try {
                    withTimeout(8_000) { ch.receive() }
                } catch (e: CancellationException) {
                    pending.remove(id)  // Timeout oder Scope-Stop: kein Callback
                    Log.w(TAG, "request '$type' (id=$id) ohne Antwort (8s-Timeout oder Stop)")
                    return@launch
                }
                pending.remove(id)
                cb(o)
            }
        }

        /** Fetch all entity IDs from HA via get_entity_ids command (8s-Timeout, R10). */
        suspend fun getEntityIds(): List<String> {
            return withContext(Dispatchers.IO) {
                val channel = Channel<List<String>>(1)
                val id = reqId.incrementAndGet()
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
                try {
                    withTimeout(8_000) { channel.receive() }
                } catch (e: CancellationException) {
                    pending.remove(id)
                    emptyList()
                }
            }
        }

        /** Fetch all entity states (with friendly names from attributes, 8s-Timeout, R10). */
        suspend fun getEntityStates(): List<EntityState> {
            return withContext(Dispatchers.IO) {
                val channel = Channel<List<EntityState>>(1)
                val id = reqId.incrementAndGet()
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
                try {
                    withTimeout(8_000) { channel.receive() }
                } catch (e: CancellationException) {
                    pending.remove(id)
                    emptyList()
                }
            }
        }

        /**
         * Drift-Watchdog (R11): Legacy-Modus → 5s-Vollpolling wie bisher.
         * subscribe_entities-Modus → volles get_states nur, wenn >60s lang kein
         * Entity-Event angekommen ist (drift); sonst läuft alles eventgetrieben.
         */
        private suspend fun watchdog() {
            while (true) {
                delay(if (legacyMode) 5_000L else 10_000L)
                if (!alive.value) return
                if (legacyMode || clock() - lastEntityEventAt > 60_000L) {
                    getStates { applyStates(it.optJSONArray("result")) }
                }
            }
        }

        /** Legacy-Pfad (HA < 2023.10): subscribe_events + 5s-get_states-Loop wie heute. */
        private fun enterLegacyPath(w: WebSocket) {
            legacyMode = true
            Log.d(TAG, "legacy mode (HA < 2023.10) — subscribe_events + 5s-Vollpolling")
            getStates { applyStates(it.optJSONArray("result")) }
            w.send(
                JSONObject()
                    .put("id", reqId.incrementAndGet())
                    .put("type", "subscribe_events")
                    .put("event_type", "state_changed")
                    .toString()
            )
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
            lastEntityEventAt = clock()
            lastUpdate.value = clock()
        }
    }

    /** Entity state with entity_id, state value, and friendly name. */
    data class EntityState(
        val entityId: String,
        val state: String,
        val friendlyName: String
    )
}

/** Default-WebSocket-Factory für [HaClient.HaSession] (OkHttpClient implementiert [okhttp3.WebSocket.Factory]). */
private fun defaultWebSocketClient(): WebSocket.Factory = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)
    .pingInterval(15, TimeUnit.SECONDS)
    .build()
