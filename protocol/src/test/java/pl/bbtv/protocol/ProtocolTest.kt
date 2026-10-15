package pl.bbtv.protocol

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test fun commandRoundTrip() {
        val data = JSONObject().put("url", "https://example.org/")
        val parsed = Protocol.parse(Protocol.command("open", data).toString())
        assertEquals("open", parsed.getString("name"))
        assertEquals("https://example.org/", parsed.getJSONObject("data").getString("url"))
    }
    @Test fun rejectsUntrustedInputs() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "https://user:secret@example.org", "https://", "https://example.org/ bad", "data:text/html,test", "intent://test").forEach { value -> assertTrue(value, runCatching { Protocol.url(value) }.isFailure) }
        assertTrue(runCatching { Protocol.parse(Protocol.frame("command").put("name", "execute_js").put("data", JSONObject()).toString()) }.isFailure)
        assertTrue(runCatching { Protocol.parse("x".repeat(Protocol.MAX_MESSAGE + 1)) }.isFailure)
        assertTrue(runCatching { Protocol.parse(Protocol.frame("auth").put("v", 2).toString()) }.isFailure)
        assertTrue(runCatching { Protocol.number(JSONObject().put("dx", 1e100), "dx", 500.0) }.isFailure)
    }
    @Test fun encodedSearchAndShare() {
        assertEquals("https://example.org/?q=za%C5%BC%C3%B3%C5%82%C4%87+g%C4%99%C5%9Bl%C4%85", Protocol.search("zażółć gęślą", "https://example.org/?q={query}"))
        assertEquals("https://example.org/a?q=1", Protocol.sharedUrl("Czytaj: https://example.org/a?q=1"))
        assertEquals("https://example.org/Function_(mathematics)", Protocol.sharedUrl("https://example.org/Function_(mathematics)"))
        assertEquals("https://example.org/story", Protocol.sharedUrl("Zobacz (https://example.org/story)."))
        assertEquals("https://example.org/story", Protocol.sharedUrl("Tytuł\nhttps://example.org/story\nOpis"))
        assertEquals("HTTPS://example.org/", Protocol.url("HTTPS://example.org/"))
        assertEquals("https://example.org", Protocol.url("example.org"))
        assertNull(Protocol.sharedUrl("sam tekst"))
    }
    @Test fun resolvesAddressOrGoogleSearch() {
        val google = "https://www.google.com/search?q={query}"
        assertEquals("https://www.rivestream.app/", Protocol.resolve(" https://www.rivestream.app/ ", google))
        assertEquals("https://rivestream.app", Protocol.resolve("rivestream.app", google))
        assertEquals("https://claude.ai/new", Protocol.resolve("claude.ai/new", google))
        assertEquals("https://www.google.com/search?q=diuna+2", Protocol.resolve("diuna 2", google))
        assertEquals("https://www.google.com/search?q=pogoda", Protocol.resolve("pogoda", google))
        assertEquals("https://www.google.com/search?q=v1.2", Protocol.resolve("v1.2", google))
    }
}
