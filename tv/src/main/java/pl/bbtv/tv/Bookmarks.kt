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
        val tiles = items.joinToString("") {
            val host = runCatching { java.net.URI(it.second).host.orEmpty().removePrefix("www.") }.getOrDefault("")
            val hue = ((host.hashCode() % 360) + 360) % 360
            "<a class='t' href=\"${escape(it.second)}\"><span class='c' style='background:linear-gradient(135deg,hsl($hue,55%,52%),hsl(${(hue + 40) % 360},60%,30%))'>${escape(host.take(1).uppercase())}</span><span class='n'>${escape(it.first)}</span><span class='h'>${escape(host)}</span></a>"
        }
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>BB TV</title><style>" +
            "html,body{margin:0;min-height:100%}body{background:radial-gradient(ellipse at 50% -20%,#2a2a33 0%,#0d0d10 55%,#000 100%);color:#f5f5f7;font-family:-apple-system,'Roboto','Helvetica Neue',sans-serif;padding:5vh 6vw;box-sizing:border-box}" +
            "h1{font-size:2.6vw;font-weight:700;letter-spacing:-.02em;margin:0 0 .4vh}p{color:#98989f;font-size:1.15vw;margin:0 0 4.5vh}" +
            ".g{display:flex;flex-wrap:wrap;margin:0 -1vw}.t{width:14vw;margin:0 1vw 3.2vh;text-decoration:none;color:inherit;outline:none;-webkit-transition:-webkit-transform .18s ease;transition:transform .18s ease}" +
            ".c{display:block;height:7.9vw;border-radius:1.1vw;box-shadow:0 .6vw 1.6vw rgba(0,0,0,.45);font-size:3.4vw;font-weight:700;line-height:7.9vw;text-align:center;color:rgba(255,255,255,.92)}" +
            ".n{display:block;margin-top:1.4vh;font-size:1.15vw;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.h{display:block;font-size:.9vw;color:#6e6e73;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}" +
            ".t:focus,.t:hover,.t:active{-webkit-transform:scale(1.08);transform:scale(1.08)}.t:focus .c,.t:hover .c{box-shadow:0 0 0 .25vw #f5f5f7,0 1.2vw 2.6vw rgba(0,0,0,.6)}" +
            ".e{color:#6e6e73;font-size:1.2vw}</style></head><body><h1>Ulubione</h1><p>Steruj pilotem lub telefonem · Menu otwiera ustawienia i parowanie</p><div class='g'>" +
            tiles.ifEmpty { "<div class='e'>Brak zakładek. Dodaj stronę z telefonu: Ulubione → +</div>" } + "</div></body></html>"
    }
}
