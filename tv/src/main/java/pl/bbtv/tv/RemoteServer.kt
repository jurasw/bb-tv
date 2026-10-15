package pl.bbtv.tv

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import org.json.JSONObject
import pl.bbtv.protocol.Protocol
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit

class RemoteServer(private val pairing: Pairing, private val dispatch: (JSONObject, (JSONObject) -> Unit) -> Unit) : NanoWSD(Protocol.PORT) {
    private val sockets = CopyOnWriteArraySet<RemoteSocket>()
    private val outgoing = java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, java.util.concurrent.ArrayBlockingQueue<Runnable>(64), java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy())
    @Volatile private var active = true
    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        // Browsers send Origin; the native phone client does not. Never allow websites to control the TV.
        if (session.headers.containsKey("origin") || session.uri !in setOf("/", "/ws", "/health")) return newFixedLengthResponse(NanoHTTPD.Response.Status.FORBIDDEN, "text/plain", "Forbidden")
        val upgrade = session.headers["upgrade"]?.equals("websocket", true) == true
        if (upgrade && session.uri != "/ws") return newFixedLengthResponse(NanoHTTPD.Response.Status.FORBIDDEN, "text/plain", "Forbidden")
        if (upgrade && sockets.size >= 4) return newFixedLengthResponse(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Busy")
        return super.serve(session)
    }
    override fun serveHttp(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response = newFixedLengthResponse("BB TV: local remote service v1")
    override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): WebSocket = RemoteSocket(handshake)
    fun publish(state: JSONObject) { sockets.forEach { if (it.isAuthorized()) it.deliver(state) } }
    fun shutdown() { active = false; sockets.forEach { runCatching { it.close(WebSocketFrame.CloseCode.GoingAway, "TV stopped", false) } }; sockets.clear(); stop(); outgoing.shutdownNow() }
    fun revoke() { sockets.forEach { runCatching { it.close(WebSocketFrame.CloseCode.PolicyViolation, "Tokens revoked", false) } }; sockets.clear() }
    inner class RemoteSocket(handshake: NanoHTTPD.IHTTPSession) : WebSocket(handshake) {
        private var token = ""
        private var windowStart = 0L
        private var received = 0
        fun isAuthorized() = token.isNotEmpty() && pairing.authorized(token)
        fun deliver(obj: JSONObject) {
            if (active) runCatching { outgoing.execute { runCatching { send(obj.toString()) }.onFailure { sockets.remove(this) } } }
        }
        override fun onOpen() { sockets.add(this); deliver(Protocol.frame("hello").put("deviceId", pairing.deviceId)) }
        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) { sockets.remove(this) }
        override fun onException(exception: IOException?) { sockets.remove(this) }
        override fun onPong(pong: WebSocketFrame?) {}
        override fun onMessage(message: WebSocketFrame) {
            try {
                val now = System.nanoTime()
                if (now - windowStart > TimeUnit.SECONDS.toNanos(1)) { windowStart = now; received = 0 }
                require(++received <= 80) { "Za dużo komend" }
                val obj = Protocol.parse(message.textPayload)
                when (obj.getString("type")) {
                    "pair" -> {
                        val newToken = pairing.pair(Protocol.text(obj, "pin", 6)) ?: throw IllegalArgumentException("PIN błędny, wygasł lub blokada prób")
                        token = newToken
                        deliver(Protocol.frame("paired").put("token", newToken).put("deviceId", pairing.deviceId))
                    }
                    "auth" -> {
                        val candidate = Protocol.text(obj, "token", 64)
                        require(pairing.authorized(candidate)) { "Token odrzucony — sparuj ponownie" }
                        token = candidate
                        deliver(Protocol.frame("authorized").put("deviceId", pairing.deviceId))
                    }
                    "command" -> {
                        check(isAuthorized()) { "Najpierw sparuj telefon" }
                        dispatch(obj) { deliver(it) }
                    }
                    "ping" -> { check(isAuthorized()); deliver(Protocol.frame("pong")) }
                }
            } catch (e: Exception) { deliver(Protocol.frame("error").put("message", e.message ?: "Błędna komenda")) }
        }
    }
}
