package pl.bbtv.tv

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AdBlock private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs: SharedPreferences = app.getSharedPreferences("adblock", Context.MODE_PRIVATE)
    private val cache = File(app.filesDir, "ad-domains.txt")
    private val worker = Executors.newSingleThreadExecutor()
    private val updating = AtomicBoolean(false)
    private val offline = app.assets.open("ad-domains.txt").bufferedReader().use { DomainRules.parse(it.lineSequence()) }
    @Volatile private var domains: Set<String> = offline
    @Volatile var enabled = prefs.getBoolean("enabled", true)
        private set
    @Volatile private var disabledSites: Set<String> = HashSet(prefs.getStringSet("disabledSites", emptySet())!!)
    val blocked = AtomicLong()
    init { worker.execute { runCatching { if (cache.exists()) domains = HashSet(offline).apply { addAll(cache.bufferedReader().use { DomainRules.parse(it.lineSequence()) }) } } } }
    fun siteEnabled(host: String) = host.isNotEmpty() && !disabledSites.contains(host.lowercase(Locale.ROOT))
    fun active(host: String) = enabled && siteEnabled(host)
    fun setGlobal(value: Boolean) { enabled = value; prefs.edit().putBoolean("enabled", value).apply() }
    fun setSite(host: String, value: Boolean) { require(host.isNotBlank()); disabledSites = HashSet(disabledSites).apply { if (value) remove(host) else add(host) }; prefs.edit().putStringSet("disabledSites", disabledSites).apply() }
    fun blocks(url: String, pageHost: String): Boolean {
        if (!active(pageHost)) return false
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        if (DomainRules.contains(domains, host)) { blocked.incrementAndGet(); return true }
        return false
    }
    fun updateIfDue(done: () -> Unit = {}) {
        if (System.currentTimeMillis() - prefs.getLong("updated", 0) < 86400000 || !updating.compareAndSet(false, true)) { done(); return }
        worker.execute {
            try {
                val connection = URL("https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext").openConnection() as HttpURLConnection
                connection.connectTimeout = 10000; connection.readTimeout = 10000; connection.instanceFollowRedirects = false
                try {
                    require(connection.responseCode == 200)
                    val text = connection.inputStream.bufferedReader().use { it.readTextBounded(2 * 1024 * 1024) }
                    val parsed = DomainRules.parse(text.lineSequence())
                    require(parsed.size >= 100) { "Niepełna lista" }
                    val temp = File(app.filesDir, "ad-domains.tmp")
                    temp.writeText(parsed.joinToString("\n"))
                    require(temp.renameTo(cache))
                    domains = HashSet(offline).apply { addAll(parsed) }
                    prefs.edit().putLong("updated", System.currentTimeMillis()).apply()
                } finally { connection.disconnect() }
            } catch (_: Exception) { /* Keep the last valid cache and bundled offline rules. */ }
            finally { updating.set(false); done() }
        }
    }
    fun config(host: String): String = JSONObject().put("enabled", active(host)).put("host", host).toString()
    private fun java.io.Reader.readTextBounded(limit: Int): String { val out = StringBuilder(); val buf = CharArray(4096); while (true) { val n = read(buf); if (n < 0) break; require(out.length + n <= limit); out.append(buf, 0, n) }; return out.toString() }
    companion object {
        @Volatile private var instance: AdBlock? = null
        fun get(context: Context): AdBlock = instance ?: synchronized(this) { instance ?: AdBlock(context).also { instance = it } }
    }
}

object DomainRules {
    private val label = Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")
    private val whitespace = Regex("\\s+")
    fun parse(lines: Sequence<String>): HashSet<String> {
        val result = HashSet<String>()
        for (raw in lines) {
            val line = raw.trim().lowercase(Locale.ROOT)
            if (line.isBlank() || line.startsWith("!") || line.startsWith("#") || line.startsWith("[")) continue
            val candidates = if (line.startsWith("||") && line.endsWith("^") && !line.contains('$')) listOf(line.removePrefix("||").removeSuffix("^"))
                else if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ") || line.startsWith("0.0.0.0\t") || line.startsWith("127.0.0.1\t")) line.substringBefore('#').split(whitespace).drop(1)
                else listOf(line.substringBefore('#').trim())
            for (host in candidates) if (host.length <= 253 && host.contains('.') && host.split('.').all { it.length in 1..63 && label.matches(it) } && host != "localhost.localdomain" && !host.all { it.isDigit() || it == '.' }) result.add(host)
            if (result.size >= 50000) break
        }
        return result
    }
    fun contains(domains: Set<String>, input: String): Boolean {
        var host = input.lowercase(Locale.ROOT).trimEnd('.')
        while (host.contains('.')) { if (domains.contains(host)) return true; host = host.substringAfter('.') }
        return false
    }
}
