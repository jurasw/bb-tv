package pl.bbtv.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import android.graphics.Bitmap
import android.app.job.JobScheduler
import android.app.job.JobInfo
import android.content.ComponentName
import java.io.ByteArrayInputStream
import java.util.Locale
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import pl.bbtv.protocol.Protocol

class TvActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var cursor: CursorView
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("tv", Context.MODE_PRIVATE) }
    private lateinit var pairing: Pairing
    private lateinit var bookmarks: Bookmarks
    private lateinit var discovery: TvDiscovery
    private var server: RemoteServer? = null
    private lateinit var full: MediaFullscreen
    private var longOk = false
    private var linkMode = false
    private var foreground = false
    private var serverError = ""
    private var webVersion = ""
    private var chromeMajor = 0
    private val adBlock by lazy { AdBlock.get(this) }
    private val browserScript by lazy { assets.open("browser.js").bufferedReader().use { it.readText() } }
    @Volatile private var pageHost = ""
    private var cosmeticBlocked = 0
    private var mediaActive = false
    private fun inject() { web.evaluateJavascript("window.__bbtvConfig=" + adBlock.config(pageHost) + ";" + browserScript, null) }
    private fun metrics() {
        web.evaluateJavascript("(function(){try{return JSON.stringify(window.__bbtv?window.__bbtv.metrics():{count:0,playing:false});}catch(e){return '{}';}})();") { raw ->
            runCatching { val parsed = JSONObject(org.json.JSONArray("[$raw]").getString(0)); cosmeticBlocked = parsed.optInt("count"); mediaActive = parsed.optBoolean("playing"); full.keepAwake(mediaActive && foreground) }
        }
    }
    private fun scheduleAdUpdates() {
        adBlock.updateIfDue()
        val jobs = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        runCatching { jobs.schedule(JobInfo.Builder(41, ComponentName(this, AdUpdateJob::class.java)).setPeriodic(86400000).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build()) }
    }
    private var pageTitle = "BB TV"
    private var searchTemplate = "https://www.google.com/search?q={query}"
    private val pulse = object : Runnable {
        override fun run() { if (foreground) { metrics(); updateHeader(); server?.publish(state()); main.postDelayed(this, 2000) } }
    }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_tv)
        pairing = Pairing(prefs).apply { renew() }
        bookmarks = Bookmarks(prefs)
        discovery = TvDiscovery(this)
        searchTemplate = prefs.getString("search", searchTemplate)!!
        web = findViewById(R.id.web)
        cursor = findViewById(R.id.cursor)
        full = MediaFullscreen(this, web, cursor, findViewById(R.id.toolbar))
        val originalUa = web.settings.userAgentString
        chromeMajor = Regex("Chrome/(\\d+)").find(originalUa)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        webVersion = if (Build.VERSION.SDK_INT >= 26) runCatching { WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } }.getOrNull() ?: originalUa else originalUa
        val chrome = Regex("Chrome/[0-9.]+").find(originalUa)?.value ?: "Chrome/50.0.0.0"
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            builtInZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: android.view.View, callback: WebChromeClient.CustomViewCallback) { full.show(view, callback) }
            override fun onHideCustomView() { full.exit() }
            override fun onCreateWindow(view: WebView?, dialog: Boolean, gesture: Boolean, message: android.os.Message?): Boolean = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, icon: Bitmap?) { full.exit(); pageHost = android.net.Uri.parse(url).host?.lowercase(Locale.ROOT).orEmpty(); cosmeticBlocked = 0; mediaActive = false }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = if (adBlock.blocks(request.url.toString(), pageHost)) WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0))) else null
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return runCatching { Protocol.url(request.url.toString()); !request.hasGesture() }.getOrDefault(true)
            }
            @Deprecated("API 21–23 fallback")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return runCatching { Protocol.url(url); SystemClock.elapsedRealtime() - lastClick > 1200 }.getOrDefault(true)
            }
            override fun onPageFinished(view: WebView, url: String) { pageTitle = view.title ?: url; inject(); server?.publish(state()) }
        }
        findViewById<Button>(R.id.home).setOnClickListener { home() }
        findViewById<Button>(R.id.settings).setOnClickListener { settings() }
        scheduleAdUpdates()
        home()
        web.requestFocus()
        if (chromeMajor < 90) Toast.makeText(this, "Stary WebView (Chrome $chromeMajor). Zaktualizuj Android System WebView w Sklepie Play.", Toast.LENGTH_LONG).show()
    }
    private var lastClick = 0L
    private fun updateHeader() { findViewById<TextView>(R.id.status).text = "${TvDiscovery.addresses()}:${Protocol.PORT} • PIN: ${pairing.label()}\n${if (serverError.isBlank()) if (linkMode) "Tryb linków" else "Tryb kursora" else serverError}" }
    private fun state(): JSONObject = Protocol.frame("state").put("url", web.url ?: "").put("title", pageTitle).put("bookmarks", bookmarks.json()).put("search", searchTemplate).put("linkMode", linkMode).put("webView", webVersion).put("chromeMajor", chromeMajor).put("adblock", adBlock.enabled).put("siteAdblock", adBlock.siteEnabled(pageHost)).put("networkBlocked", adBlock.blocked.get()).put("cosmeticBlocked", cosmeticBlocked).put("mediaActive", mediaActive).put("fullscreen", full.fullscreen)
    private fun settings() {
        val actions = arrayOf("Nowy PIN (2 minuty)", "Unieważnij wszystkie telefony", "Tryb linków: ${if (linkMode) "włączony" else "wyłączony"}", "Wyszukiwarka", "Adblock globalnie: ${adBlock.enabled}", "Adblock tej domeny: ${adBlock.siteEnabled(pageHost)}")
        AlertDialog.Builder(this).setTitle("BB TV — ustawienia").setMessage("${TvDiscovery.addresses()}:${Protocol.PORT}\nPIN: ${pairing.label()}\nWebView: $webVersion\n${if (chromeMajor < 90) "UWAGA: Chrome < 90. Zaktualizuj Android System WebView ze Sklepu Play." else ""}")
            .setPositiveButton("Opcje") { _, _ -> AlertDialog.Builder(this).setItems(actions) { _, i -> when (i) {
                0 -> { pairing.renew(); updateHeader(); settings() }
                1 -> { pairing.revoke(); server?.revoke(); updateHeader() }
                2 -> { linkMode = !linkMode }
                4 -> { adBlock.setGlobal(!adBlock.enabled); inject() }
                5 -> { if (pageHost.isNotBlank()) { adBlock.setSite(pageHost, !adBlock.siteEnabled(pageHost)); inject() } }
                3 -> { val edit = EditText(this).apply { setText(searchTemplate) }; AlertDialog.Builder(this).setTitle("URL wyszukiwarki z {query}").setView(edit).setPositiveButton("Zapisz") { _, _ -> runCatching { configureSearch(edit.text.toString()) }.onFailure { toast(it.message) } }.setNegativeButton("Anuluj", null).show() }
            } }.show() }.setNegativeButton("Zamknij", null).show()
    }
    private fun configureSearch(value: String) { Protocol.search("test", value); searchTemplate = value; prefs.edit().putString("search", value).apply() }
    private fun toast(message: String?) { Toast.makeText(this, message ?: "Błąd", Toast.LENGTH_SHORT).show() }
    private fun home() { full.exit(); web.loadDataWithBaseURL("https://home.bbtv.invalid/", bookmarks.html(), "text/html", "UTF-8", null); pageTitle = "BB TV"; web.requestFocus() }
    private fun open(url: String) { full.exit(); web.loadUrl(Protocol.url(url)); web.requestFocus() }
    private fun script(js: String) { web.evaluateJavascript("(function(){try{$js}catch(e){}})();", null) }
    private fun click() {
        lastClick = SystemClock.elapsedRealtime()
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now + if (action == MotionEvent.ACTION_UP) 60 else 0, action, cursor.cx, cursor.cy, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN; full.dispatchTouch(event); event.recycle()
        }
    }
    private fun move(dx: Float, dy: Float) {
        if (linkMode) {
            val delta = if (dy < 0 || dx < 0) -1 else 1
            script("var a=document.querySelectorAll('a[href],button,input,select,textarea,[tabindex]');var v=[];for(var i=0;i<a.length&&v.length<300;i++){if(a[i].offsetWidth&&a[i].offsetHeight)v.push(a[i]);}var n=-1;for(var j=0;j<v.length;j++){if(v[j]===document.activeElement)n=j;}if(v.length){if(n<0&&$delta<0)n=0;var old=window.__bbtvFocus;if(old&&old[0])old[0].style.outline=old[1];var el=v[(n+$delta+v.length)%v.length];window.__bbtvFocus=[el,el.style.outline];el.focus();el.scrollIntoView(false);el.style.outline='3px solid #50e3c2';}")
        } else {
            if (cursor.height > 0) {
                if (cursor.cy + dy < 0 || cursor.cy + dy >= cursor.height) web.scrollBy(0, (dy * 4).toInt())
                if (cursor.cx + dx < 0 || cursor.cx + dx >= cursor.width) web.scrollBy((dx * 4).toInt(), 0)
            }
            cursor.move(dx, dy)
        }
    }
    private fun activate() {
        if (!linkMode) { click(); return }
        web.evaluateJavascript("(function(){try{var e=document.activeElement;if(e&&e.tagName==='A')return e.href;if(e)e.click();}catch(x){}return '';})();") { result -> runCatching { val href = org.json.JSONArray("[$result]").optString(0); if (href.isNotBlank()) open(href) } }
    }
    private fun media() { script("var m=document.querySelector('video,audio');if(m){if(m.paused){var p=m.play();if(p&&p['catch'])p['catch'](function(){});}else m.pause();}") }
    private fun fullscreen() { full.toggle() }
    private fun command(obj: JSONObject, reply: (JSONObject) -> Unit) {
        main.post {
            if (!foreground) { reply(Protocol.frame("error").put("message", "TV w tle")); return@post }
            try {
                val d = obj.getJSONObject("data")
                when (obj.getString("name")) {
                    "open" -> open(Protocol.text(d, "url"))
                    "search" -> open(Protocol.search(Protocol.text(d, "text", 2048), searchTemplate))
                    "back" -> onBackPressed()
                    "forward" -> if (web.canGoForward()) web.goForward()
                    "reload" -> { full.exit(); web.reload() }
                    "home" -> home()
                    "scroll" -> web.scrollBy(Protocol.number(d, "dx", 4000.0).toInt(), Protocol.number(d, "dy", 4000.0).toInt())
                    "move" -> move(Protocol.number(d, "dx", 500.0), Protocol.number(d, "dy", 500.0))
                    "click" -> activate()
                    "type" -> script("var e=document.activeElement;if(e){var t=${JSONObject.quote(Protocol.text(d, "text")).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")};if(e.isContentEditable)e.textContent=t;else if('value' in e){var p=Object.getPrototypeOf(e);var s=Object.getOwnPropertyDescriptor(p,'value');if(s&&s.set)s.set.call(e,t);else e.value=t;}else return;var a=document.createEvent('HTMLEvents');a.initEvent('input',true,false);e.dispatchEvent(a);var b=document.createEvent('HTMLEvents');b.initEvent('change',true,false);e.dispatchEvent(b);}")
                    "volume" -> (getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, if (Protocol.number(d, "delta", 1.0) > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    "media" -> media()
                    "fullscreen" -> fullscreen()
                    "link_mode" -> { linkMode = d.getBoolean("enabled") }
                    "bookmark_add" -> { require(pageHost != "home.bbtv.invalid") { "Najpierw otwórz stronę" }; bookmarks.add(pageTitle, web.url ?: "") }
                    "bookmark_delete" -> { bookmarks.delete(d.getInt("index")); if (pageHost == "home.bbtv.invalid") home() }
                    "bookmark_move" -> { bookmarks.move(d.getInt("from"), d.getInt("to")); if (pageHost == "home.bbtv.invalid") home() }
                    "config" -> configureSearch(Protocol.text(d, "search"))
                    "adblock" -> { when (d.getString("scope")) { "global" -> adBlock.setGlobal(d.getBoolean("enabled")); "site" -> adBlock.setSite(pageHost, d.getBoolean("enabled")); else -> throw IllegalArgumentException("Nieznany zakres") }; inject() }
                    "state" -> Unit
                    else -> throw IllegalArgumentException("Funkcja niedostępna")
                }
                reply(state())
            } catch (e: Exception) { reply(Protocol.frame("error").put("message", e.message ?: "Błąd")) }
        }
    }
    override fun onStart() {
        super.onStart()
        try { server = RemoteServer(pairing, ::command).also { it.start(30000, false) }; discovery.start(); serverError = "" } catch (e: Exception) { server?.shutdown(); server = null; serverError = "Serwer: ${e.message}" }
    }
    override fun onStop() { foreground = false; main.removeCallbacks(pulse); discovery.stop(); server?.shutdown(); server = null; super.onStop() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU) { if (event.action == KeyEvent.ACTION_UP) settings(); return true }
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) { if (event.action == KeyEvent.ACTION_UP) media(); return true }
        if (!web.hasFocus() && !full.fullscreen) return super.dispatchKeyEvent(event)
        if (event.keyCode in intArrayOf(19,20,21,22)) {
            if (event.action == KeyEvent.ACTION_DOWN) { val distance = (8 + event.repeatCount * 2).coerceAtMost(50).toFloat(); move(if (event.keyCode == 21) -distance else if (event.keyCode == 22) distance else 0f, if (event.keyCode == 19) -distance else if (event.keyCode == 20) distance else 0f) }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) longOk = false
            if (event.action == KeyEvent.ACTION_DOWN && (event.isLongPress || event.repeatCount > 5) && !longOk) { longOk = true; fullscreen() }
            if (event.action == KeyEvent.ACTION_UP && !longOk) activate()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    @Deprecated("Old TV back dispatcher")
    override fun onBackPressed() { if (full.exit()) return; if (web.canGoBack()) web.goBack() else if (web.url?.contains("home.bbtv.invalid") == true) super.onBackPressed() else home() }
    override fun onPause() { foreground = false; main.removeCallbacks(pulse); full.cancelPending(); full.keepAwake(false); script("var m=document.querySelectorAll('video,audio');for(var i=0;i<m.length;i++)m[i].pause();"); web.onPause(); web.pauseTimers(); super.onPause() }
    override fun onResume() { super.onResume(); foreground = true; if (::web.isInitialized) { web.resumeTimers(); web.onResume() }; main.removeCallbacks(pulse); main.post(pulse) }
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_BACKGROUND && !foreground) web.clearCache(false) }
    override fun onDestroy() { full.exit(); main.removeCallbacksAndMessages(null); web.stopLoading(); (web.parent as? android.view.ViewGroup)?.removeView(web); web.destroy(); super.onDestroy() }
}
