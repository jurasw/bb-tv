package pl.bbtv.phone

import android.os.Handler
import android.os.Looper
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import pl.bbtv.protocol.Protocol
import java.net.URI

class RemoteClient(private val onStatus: (String) -> Unit, private val onFrame: (JSONObject) -> Unit, private val onToken: (String, String) -> Unit, private val onReady: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocketClient? = null
    private var enabled = false
    private var address = ""
    private var token = ""
    private var pin = ""
    private var deviceId = ""
    private var attempts = 0
    private var ready = false
    private val retry = Runnable { if (enabled) connect() }
    fun start(host: String, savedToken: String, pairPin: String = "", expectedDeviceId: String = "") {
        stop(); address = host; token = savedToken; pin = pairPin; deviceId = expectedDeviceId; enabled = true; attempts = 0; connect()
    }
    private fun connect() {
        if (!enabled) return
        onStatus("Łączenie z $address…")
        val client = object : WebSocketClient(URI("ws://$address/ws"), org.java_websocket.drafts.Draft_6455(), null, 5000) {
            override fun onOpen(handshake: ServerHandshake?) { main.post { if (this@RemoteClient.socket === this) { onStatus("Sprawdzanie TV…") } } }
            override fun onMessage(message: String) {
                if (message.length > 524288) return
                main.post {
                    if (this@RemoteClient.socket !== this) return@post
                    runCatching {
                        val obj = JSONObject(message)
                        when (obj.optString("type")) {
                            "hello" -> {
                                if (pin.isEmpty() && deviceId.isNotEmpty() && deviceId != obj.optString("deviceId")) {
                                    enabled = false; close(); onStatus("Inny TV pod tym IP — wybierz TV i sparuj ponownie")
                                } else {
                                    val auth = if (pin.isNotEmpty()) Protocol.frame("pair").put("pin", pin) else Protocol.frame("auth").put("token", token)
                                    send(auth.toString())
                                }
                            }
                            "paired", "authorized" -> {
                                if (obj.has("token")) { token = obj.getString("token"); deviceId = obj.getString("deviceId"); onToken(token, deviceId) }
                                pin = ""; attempts = 0; ready = true; onStatus("Połączono z TV"); send(Protocol.command("state").toString()); onReady()
                            }
                            "state" -> onFrame(obj)
                            "error" -> {
                                val msg = obj.optString("message", "Błąd TV")
                                if (!ready) { enabled = false; close() }
                                onStatus(msg)
                            }
                        }
                    }.onFailure { onStatus("Nieprawidłowa odpowiedź TV") }
                }
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) { main.post { if (this@RemoteClient.socket === this) { ready = false; if (enabled) { onStatus("Rozłączono — ponawianie…"); scheduleRetry() } } } }
            override fun onError(error: Exception?) { main.post { if (this@RemoteClient.socket === this && enabled) onStatus("TV niedostępny — sprawdź IP i sieć") } }
        }
        socket = client
        client.connectionLostTimeout = 10
        client.connect()
    }
    private fun scheduleRetry() { main.removeCallbacks(retry); main.postDelayed(retry, (1000L shl attempts++.coerceAtMost(4)).coerceAtMost(15000L)) }
    fun send(name: String, data: JSONObject = JSONObject()): Boolean {
        val s = socket ?: return false
        if (!ready || !s.isOpen) return false
        return runCatching { s.send(Protocol.command(name, data).toString()); true }.getOrDefault(false)
    }
    fun stop() { enabled = false; ready = false; main.removeCallbacksAndMessages(null); val old = socket; socket = null; old?.close() }
}
