package pl.bbtv.tv

import android.content.Context
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pl.bbtv.protocol.Protocol
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemoteServerTest {
    @Test fun websocketRejectsCommandsBeforeAuthAndAfterRevocation() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("server-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val pairing = Pairing(prefs)
        val pin = pairing.renew()
        val calls = AtomicInteger()
        val server = RemoteServer(pairing) { _, reply -> calls.incrementAndGet(); reply(Protocol.frame("state")) }
        val messages = LinkedBlockingQueue<JSONObject>()
        val client = object : WebSocketClient(URI("ws://127.0.0.1:${Protocol.PORT}/ws")) {
            override fun onOpen(handshake: ServerHandshake?) {}
            override fun onMessage(message: String) { messages.offer(JSONObject(message)) }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(error: Exception?) {}
        }
        fun next(): JSONObject = messages.poll(5, TimeUnit.SECONDS) ?: error("Missing websocket response")
        server.start(30000, false)
        try {
            assertTrue(client.connectBlocking(5, TimeUnit.SECONDS))
            assertEquals("hello", next().getString("type"))
            client.send(Protocol.command("home").toString())
            assertEquals("error", next().getString("type"))
            assertEquals(0, calls.get())
            client.send(Protocol.frame("pair").put("pin", pin).toString())
            val token = next().getString("token")
            assertTrue(pairing.authorized(token))
            client.send(Protocol.command("home").toString())
            assertEquals("state", next().getString("type"))
            assertEquals(1, calls.get())
            pairing.revoke()
            client.send(Protocol.command("home").toString())
            assertEquals("error", next().getString("type"))
            assertEquals(1, calls.get())
        } finally { client.closeBlocking(); server.shutdown() }
    }
    @Test fun webOriginsCannotOpenControlSocket() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("origin-test", Context.MODE_PRIVATE)
        val server = RemoteServer(Pairing(prefs)) { _, _ -> error("Must not dispatch") }
        server.start(30000, false)
        try {
            java.net.Socket("127.0.0.1", Protocol.PORT).use { socket ->
                socket.soTimeout = 5000
                socket.getOutputStream().write(("GET /ws HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nOrigin: https://example.org\r\n\r\n").toByteArray())
                val status = socket.getInputStream().bufferedReader().readLine()
                assertTrue(status, status.contains("403"))
            }
        } finally { server.shutdown() }
    }

}
