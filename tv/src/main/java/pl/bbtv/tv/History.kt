package pl.bbtv.tv

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class History(private val prefs: SharedPreferences) {
    private val items = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
    fun add(title: String, url: String) {
        if (!url.startsWith("http") || url.contains("home.bbtv.invalid")) return
        for (i in items.length() - 1 downTo 0) if (items.getJSONObject(i).optString("url") == url) items.remove(i)
        val copy = JSONArray().put(JSONObject().put("title", title.take(200)).put("url", url.take(4096)).put("time", System.currentTimeMillis()))
        for (i in 0 until items.length().coerceAtMost(99)) copy.put(items.get(i))
        while (items.length() > 0) items.remove(0)
        for (i in 0 until copy.length()) items.put(copy.get(i))
        save()
    }
    fun clear() { while (items.length() > 0) items.remove(0); save() }
    fun json(): JSONArray = JSONArray(items.toString())
    private fun save() { prefs.edit().putString("history", items.toString()).apply() }
}
