package pl.bbtv.tv

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import pl.bbtv.protocol.Protocol

class Bookmarks(private val prefs: SharedPreferences) {
    private val items = mutableListOf<Pair<String, String>>()
    init {
        val stored = prefs.getString("bookmarks", null)
        if (stored == null) items.addAll(listOf("YouTube" to "https://www.youtube.com/", "TVP VOD" to "https://vod.tvp.pl/", "Pluto TV" to "https://pluto.tv/"))
        else runCatching { val a = JSONArray(stored); for (i in 0 until a.length().coerceAtMost(100)) { val o = a.getJSONObject(i); items.add(o.getString("title") to Protocol.url(o.getString("url"))) } }
    }
    fun json(): JSONArray = JSONArray().also { a -> items.forEach { a.put(JSONObject().put("title", it.first).put("url", it.second)) } }
    private fun save() { prefs.edit().putString("bookmarks", json().toString()).apply() }
    fun add(title: String, url: String) { val valid = Protocol.url(url); if (items.none { it.second == valid } && items.size < 100) { items.add(title.take(100) to valid); save() } }
    fun delete(index: Int) { require(index in items.indices); items.removeAt(index); save() }
    fun move(from: Int, to: Int) { require(from in items.indices && to in items.indices); items.add(to, items.removeAt(from)); save() }
    fun html(): String {
        fun escape(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
        return "<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='background:#101820;color:#eaf6f4;font:24px sans-serif;padding:32px'><h1>BB TV</h1><p>Steruj pilotem albo telefonem. Menu: ustawienia i parowanie.</p>" + items.joinToString("") { "<p><a style='color:#50e3c2;padding:10px;display:inline-block' href=\"${escape(it.second)}\">${escape(it.first)}</a></p>" } + "</body></html>"
    }
}
