package com.dailythread.app.sync

import com.dailythread.app.BuildConfig
import com.dailythread.app.data.repository.AuthRepository
import com.dailythread.app.data.repository.TokenStore
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RealtimeInvalidationClient(
    private val tokenStore: TokenStore,
    private val syncEngine: SyncEngine
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val syncScheduled = AtomicBoolean(false)
    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null
    @Volatile private var stopped = true

    suspend fun start() {
        stopped = false
        connect()
    }

    fun stop() {
        stopped = true
        heartbeatJob?.cancel()
        heartbeatJob = null
        socket?.close(1000, "screen_closed")
        socket = null
    }

    private suspend fun connect() {
        val token = AuthRepository(tokenStore).validAccessToken() ?: return
        socket?.cancel()
        val wsBase = BuildConfig.SUPABASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/')
        val url = "$wsBase/realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_PUBLISHABLE_KEY}&vsn=1.0.0"
        val request = Request.Builder().url(url).build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = mapOf(
                    "topic" to "realtime:public:*",
                    "event" to "phx_join",
                    "payload" to mapOf(
                        "config" to mapOf(
                            "broadcast" to mapOf("ack" to false, "self" to false),
                            "presence" to mapOf("key" to ""),
                            "postgres_changes" to listOf(mapOf("event" to "*", "schema" to "public"))
                        ),
                        "access_token" to token
                    ),
                    "ref" to "1"
                )
                webSocket.send(gson.toJson(payload))
                heartbeatJob?.cancel()
                heartbeatJob = scope.launch {
                    while (isActive && !stopped) {
                        delay(25_000)
                        webSocket.send(
                            gson.toJson(
                                mapOf(
                                    "topic" to "phoenix",
                                    "event" to "heartbeat",
                                    "payload" to emptyMap<String, String>(),
                                    "ref" to System.currentTimeMillis().toString()
                                )
                            )
                        )
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"event\":\"postgres_changes\"") || text.contains("postgres_changes")) {
                    scheduleSync()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                if (!stopped) scheduleReconnect()
            }
        })
    }

    private fun scheduleSync() {
        if (!syncScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(350)
            runCatching { syncEngine.runOnce() }
            syncScheduled.set(false)
        }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        scope.launch {
            delay(10_000)
            if (!stopped) runCatching { connect() }
        }
    }
}
