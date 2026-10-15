package pl.bbtv.tv

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import pl.bbtv.protocol.Protocol

class Bookmarks(private val prefs: SharedPreferences) {
    private val items = mutableListOf<Pair<String, String>>()
    init {
        val stored = prefs.getString("bookmarks", null)
        if (stored == null) items.addAll(defaults)
        else runCatching { val a = JSONArray(stored); for (i in 0 until a.length().coerceAtMost(100)) { val o = a.getJSONObject(i); items.add(o.getString("title") to Protocol.url(o.getString("url"))) } }
        if (stored != null && prefs.getInt("bookmarksVersion", 1) < 2) {
            items.removeAll { it.second == "https://vod.tvp.pl/" || it.second == "https://pluto.tv/" }
            defaults.filter { d -> items.none { it.second == d.second } }.asReversed().forEach { items.add(0, it) }
            save()
        }
        prefs.edit().putInt("bookmarksVersion", 2).apply()
    }
    private val defaults get() = listOf("Rivestream" to "https://www.rivestream.app/", "YouTube" to "https://www.youtube.com/", "ChatGPT" to "https://chatgpt.com/", "Claude" to "https://claude.ai/", "Google" to "https://www.google.com/")
    fun json(): JSONArray = JSONArray().also { a -> items.forEach { a.put(JSONObject().put("title", it.first).put("url", it.second)) } }
    private fun save() { prefs.edit().putString("bookmarks", json().toString()).apply() }
    fun add(title: String, url: String) { val valid = Protocol.url(url); if (items.none { it.second == valid } && items.size < 100) { items.add(title.take(100) to valid); save() } }
    fun delete(index: Int) { require(index in items.indices); items.removeAt(index); save() }
    fun move(from: Int, to: Int) { require(from in items.indices && to in items.indices); items.add(to, items.removeAt(from)); save() }
}
