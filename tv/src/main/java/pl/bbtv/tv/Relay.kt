package pl.bbtv.tv

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import pl.bbtv.protocol.Protocol
import java.net.URI
import java.security.SecureRandom

class Relay(private val prefs: SharedPreferences, private val dispatch: (JSONObject, (JSONObject) -> Unit) -> Unit, private val changed: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocketClient? = null
    private var enabled = false
    private var attempts = 0
    @Volatile var online = false
        private set
    @Volatile var phones = 0
        private set
    var code = ""
        private set
    private var codeExpires = 0L
    val room: String get() = prefs.getString("room", null) ?: renewRoom()
    val endpoint: String get() = prefs.getString("relay", null) ?: Protocol.SITE
    val site: String get() = prefs.getString("relaySite", null) ?: endpoint
    val link: String get() = "$site/#r=$room"
    private val retry = Runnable { connect() }
    private val refreshCode = Runnable { requestCode() }
    fun configure(endpoint: String?, site: String?): Boolean {
        if (endpoint == null && site == null) return false
        val nextEndpoint = if (endpoint == "prod") Protocol.SITE else origin(endpoint ?: this.endpoint) ?: return false
        val nextSite = origin(site ?: nextEndpoint) ?: return false
        if (nextEndpoint == this.endpoint && nextSite == this.site) return false
        val restart = enabled
        stop()
        prefs.edit().apply {
            if (nextEndpoint == Protocol.SITE) remove("relay") else putString("relay", nextEndpoint)
            if (nextSite == nextEndpoint) remove("relaySite") else putString("relaySite", nextSite)
        }.apply()
        if (restart) start()
        return true
    }
    private fun origin(value: String): String? = runCatching {
        val uri = URI(value)
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
        require(uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.orEmpty() in setOf("", "/"))
        require(uri.port == -1 || uri.port in 1..65535)
        value.trimEnd('/')
    }.getOrNull()
    fun renewRoom(): String {
        val value = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("room", value).apply()
        if (enabled) { stop(); start() }
        return value
    }
    fun start() { enabled = true; attempts = 0; connect() }
    fun stop() { enabled = false; main.removeCallbacksAndMessages(null); online = false; phones = 0; code = ""; socket?.close(); socket = null }
    fun publish(frame: JSONObject) { if (phones > 0) send(frame) }
    private fun send(frame: JSONObject) { runCatching { socket?.takeIf { it.isOpen }?.send(frame.toString()) } }
    private fun requestCode() {
        main.removeCallbacks(refreshCode)
        runCatching { socket?.takeIf { it.isOpen }?.send("{\"type\":\"code\"}") }
        main.postDelayed(refreshCode, 110000)
    }
    private fun connect() {
        if (!enabled) return
        main.removeCallbacks(retry)
        val client = object : WebSocketClient(URI("${endpoint.replaceFirst("http", "ws")}/ws/tv?room=$room")) {
            override fun onOpen(handshake: ServerHandshake?) { main.post { if (this@Relay.socket === this) { attempts = 0; online = true; requestCode(); changed() } } }
            override fun onMessage(message: String) {
                if (message.length > Protocol.MAX_MESSAGE) return
                main.post {
                    if (this@Relay.socket !== this) return@post
                    runCatching {
                        val obj = JSONObject(message)
                        when (obj.optString("type")) {
                            "presence" -> { phones = obj.optInt("phones"); changed() }
                            "code" -> { code = obj.getString("code"); codeExpires = System.currentTimeMillis() + obj.optLong("ttl", 120000); changed() }
                            else -> dispatch(Protocol.parse(message)) { send(it) }
                        }
                    }.onFailure { send(Protocol.frame("error").put("message", it.message ?: "Błędna komenda")) }
                }
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                main.post {
                    if (this@Relay.socket !== this) return@post
                    online = false; phones = 0; this@Relay.code = ""; changed()
                    if (enabled) main.postDelayed(retry, (1000L shl attempts++.coerceAtMost(5)).coerceAtMost(30000L))
                }
            }
            override fun onError(error: Exception?) {}
        }
        socket?.close()
        socket = client
        client.connectionLostTimeout = 30
        client.connect()
    }
}
