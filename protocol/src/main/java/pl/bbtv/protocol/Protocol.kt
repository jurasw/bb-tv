package pl.bbtv.protocol

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

object Protocol {
    const val VERSION = 1
    const val PORT = 8765
    const val SERVICE = "_tvbrowser._tcp."
    const val SITE = "https://bbtv.juraswis.workers.dev"
    const val MAX_MESSAGE = 16384
    val commands = setOf("open", "search", "back", "forward", "reload", "home", "scroll", "move", "click", "type", "volume", "media", "fullscreen", "link_mode", "bookmark_add", "bookmark_delete", "bookmark_move", "adblock", "config", "state", "elements", "activate", "fill", "history", "history_clear")
    fun frame(type: String): JSONObject = JSONObject().put("v", VERSION).put("type", type)
    fun command(name: String, data: JSONObject = JSONObject()): JSONObject {
        require(name in commands)
        return frame("command").put("name", name).put("data", data)
    }
    fun parse(raw: String): JSONObject {
        require(raw.length <= MAX_MESSAGE) { "Wiadomość zbyt długa" }
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE) { "Wiadomość zbyt długa" }
        val obj = JSONObject(raw)
        require(obj.getInt("v") == VERSION) { "Niezgodna wersja protokołu" }
        require(obj.getString("type") in setOf("pair", "auth", "command", "ping")) { "Nieznany typ" }
        if (obj.getString("type") == "command") {
            require(obj.getString("name") in commands) { "Nieznana komenda" }
            require(obj.optJSONObject("data") != null) { "Brak danych" }
        }
        return obj
    }
    fun number(data: JSONObject, key: String, limit: Double): Float {
        val n = data.getDouble(key)
        require(!n.isNaN() && !n.isInfinite() && n in -limit..limit) { "Nieprawidłowa liczba" }
        return n.toFloat()
    }
    fun text(data: JSONObject, key: String, max: Int = 4096): String = data.getString(key).also { require(it.length <= max) }
    fun url(input: String): String {
        val text = input.trim()
        require(text.isNotEmpty() && text.length <= 4096 && text.none { it.isWhitespace() || it.code < 32 }) { "Nieprawidłowy URL" }
        val result = if (text.contains("://")) text else "https://$text"
        val uri = URI(result)
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Dozwolone są tylko adresy HTTP/HTTPS bez hasła" }
        return result
    }
    fun search(query: String, template: String): String {
        require(query.isNotBlank() && query.length <= 2048)
        require(template.contains("{query}"))
        return url(template.replace("{query}", URLEncoder.encode(query, "UTF-8")))
    }
    fun resolve(input: String, template: String): String {
        val text = input.trim()
        sharedUrl(text)?.let { return it }
        if (Regex("[^\\s./:]+(\\.[^\\s./:]+)*\\.[a-zA-Z]{2,}(:[0-9]{1,5})?([/?#]\\S*)?").matches(text)) runCatching { return url(text) }
        return search(text, template)
    }
    fun sharedUrl(text: String): String? {
        var candidate = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text)?.value ?: return null
        if (text.trim() != candidate) {
            candidate = candidate.trimEnd('.', ',')
            while (candidate.endsWith(')') && candidate.count { it == ')' } > candidate.count { it == '(' }) candidate = candidate.dropLast(1)
            while (candidate.endsWith(']') && candidate.count { it == ']' } > candidate.count { it == '[' }) candidate = candidate.dropLast(1)
        }
        return runCatching { url(candidate) }.getOrNull()
    }
}
