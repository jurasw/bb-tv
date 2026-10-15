package pl.bbtv.phone

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import pl.bbtv.protocol.Protocol

class PhoneActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("phone", Context.MODE_PRIVATE) }
    private lateinit var client: RemoteClient
    private lateinit var discovery: PhoneDiscovery
    private var selectedHost = ""
    private var pendingShare: String? = null
    private var latest = JSONObject()
    private var updating = false
    private var speech: SpeechRecognizer? = null
    private var speechLanguage = "pl-PL"
    private var held: Runnable? = null
    private val found = linkedMapOf<String, String>()
    private fun status(text: String) { findViewById<TextView>(R.id.connection_status).text = text }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    private fun edit(id: Int) = findViewById<EditText>(id)
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_phone)
        val content = findViewById<View>(android.R.id.content)
        content.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) { val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom) }
            else { @Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom) }
            insets
        }
        content.requestApplyInsets()
        speechLanguage = prefs.getString("language", "pl-PL")!!
        edit(R.id.host).setText(prefs.getString("host", ""))
        client = RemoteClient(::status, ::renderState, { token, deviceId -> prefs.edit().putString("token:$selectedHost", token).putString("deviceId:$selectedHost", deviceId).apply(); edit(R.id.pin).text.clear() }, { pendingShare?.let { link -> if (client.send("open", JSONObject().put("url", link))) { pendingShare = null; toast("Link wysłany na TV") } } })
        discovery = PhoneDiscovery(this, { name, host -> main.post { found[host] = name; status("Znaleziono ${found.size} TV — dotknij Znajdź") } }, { message -> main.post { status(message) } })
        button(R.id.connect) { connect(false) }
        button(R.id.pair) { connect(true) }
        button(R.id.discover) { showFound() }
        button(R.id.send) { sendAddress(edit(R.id.address).text.toString()) }
        edit(R.id.address).setOnEditorActionListener { _, _, _ -> sendAddress(edit(R.id.address).text.toString()); true }
        button(R.id.voice) { startVoice() }
        findViewById<Button>(R.id.language).apply { text = speechLanguage; setOnClickListener { prompt("Język głosu (np. pl-PL, en-US)", speechLanguage) { value -> if (Regex("[a-z]{2,3}-[A-Z]{2}").matches(value)) { speechLanguage = value; text = value; prefs.edit().putString("language", value).apply() } else toast("Podaj kod, np. pl-PL") } } }
        mapOf(R.id.back to "back", R.id.forward to "forward", R.id.reload to "reload", R.id.home to "home", R.id.media to "media", R.id.fullscreen to "fullscreen", R.id.click to "click").forEach { (id, command) -> button(id) { send(command) } }
        findViewById<Button>(R.id.click).setOnLongClickListener { send("fullscreen"); true }
        button(R.id.volume_up) { send("volume", JSONObject().put("delta", 1)) }
        button(R.id.volume_down) { send("volume", JSONObject().put("delta", -1)) }
        button(R.id.scroll_up) { send("scroll", JSONObject().put("dx", 0).put("dy", -550)) }
        button(R.id.scroll_down) { send("scroll", JSONObject().put("dx", 0).put("dy", 550)) }
        button(R.id.type) { prompt("Tekst do aktywnego pola na TV", "") { value -> send("type", JSONObject().put("text", value)) } }
        button(R.id.bookmarks) { showBookmarks() }
        button(R.id.search_settings) { prompt("URL wyszukiwarki z {query}", latest.optString("search", "https://www.google.com/search?q={query}")) { value -> runCatching { Protocol.search("test", value); send("config", JSONObject().put("search", value)) }.onFailure { toast("Nieprawidłowy szablon") } } }
        findViewById<Switch>(R.id.link_mode).setOnCheckedChangeListener { _, checked -> if (!updating) send("link_mode", JSONObject().put("enabled", checked)) }
        findViewById<Switch>(R.id.adblock).setOnCheckedChangeListener { _, checked -> if (!updating) send("adblock", JSONObject().put("scope", "global").put("enabled", checked)) }
        findViewById<Switch>(R.id.site_adblock).setOnCheckedChangeListener { _, checked -> if (!updating) send("adblock", JSONObject().put("scope", "site").put("enabled", checked)) }
        dpad(R.id.up, 0, -1); dpad(R.id.down, 0, 1); dpad(R.id.left, -1, 0); dpad(R.id.right, 1, 0)
        setupTouchpad()
        if (saved == null) receiveShare(intent) else pendingShare = saved.getString("pendingShare")
    }
    private fun button(id: Int, action: () -> Unit) { findViewById<Button>(id).setOnClickListener { action() } }
    private fun prompt(title: String, value: String, action: (String) -> Unit) { val field = EditText(this).apply { setText(value); maxLines = 3 }; AlertDialog.Builder(this).setTitle(title).setView(field).setPositiveButton("Wyślij") { _, _ -> action(field.text.toString()) }.setNegativeButton("Anuluj", null).show() }
    private fun endpoint(value: String): String {
        require(Regex("[0-9.]+(:[0-9]{1,5})?").matches(value)) { "Podaj lokalny adres IPv4 TV" }
        val parts = value.split(':'); val octets = parts[0].split('.').map { it.toInt() }
        require(octets.size == 4 && octets.all { it in 0..255 }) { "Nieprawidłowe IP" }
        require(octets[0] == 10 || octets[0] == 192 && octets[1] == 168 || octets[0] == 172 && octets[1] in 16..31 || octets[0] == 169 && octets[1] == 254 || octets[0] == 127) { "Dozwolona jest tylko sieć lokalna" }
        val port = parts.getOrNull(1)?.toInt() ?: Protocol.PORT
        require(port in 1..65535)
        return "${octets.joinToString(".")}:$port"
    }
    private fun connect(pair: Boolean) {
        runCatching {
            val host = endpoint(edit(R.id.host).text.toString().trim())
            val pin = if (pair) edit(R.id.pin).text.toString() else ""
            if (pair) require(Regex("[0-9]{6}").matches(pin)) { "Wpisz sześciocyfrowy PIN z TV" }
            val token = prefs.getString("token:$host", "")!!
            if (!pair && token.isBlank()) { status("Wpisz PIN z TV i dotknij Sparuj"); return }
            selectedHost = host
            prefs.edit().putString("host", host).apply()
            client.start(host, if (pair) "" else token, pin, prefs.getString("deviceId:$host", "")!!)
        }.onFailure { status(it.message ?: "Błędny adres") }
    }
    private fun send(name: String, data: JSONObject = JSONObject(), quiet: Boolean = false) { if (!client.send(name, data) && !quiet) toast("Najpierw połącz z TV") }
    private fun sendAddress(value: String) {
        val input = value.trim(); if (input.isEmpty()) return
        val url = Protocol.sharedUrl(input) ?: runCatching { if (!input.contains(' ') && input.contains('.')) Protocol.url(input) else null }.getOrNull()
        if (url != null) send("open", JSONObject().put("url", url)) else send("search", JSONObject().put("text", input.take(2048)))
    }
    private fun renderState(obj: JSONObject) {
        latest = obj; updating = true
        findViewById<Switch>(R.id.link_mode).isChecked = obj.optBoolean("linkMode")
        findViewById<Switch>(R.id.adblock).isChecked = obj.optBoolean("adblock", true)
        findViewById<Switch>(R.id.site_adblock).isChecked = obj.optBoolean("siteAdblock", true)
        updating = false
        findViewById<TextView>(R.id.page_status).text = "${obj.optString("title")}\n${obj.optString("url")}\nZablokowano: sieć ${obj.optLong("networkBlocked")} · elementy ${obj.optInt("cosmeticBlocked")}\nWebView: ${obj.optString("webView")}"
    }
    private fun showFound() {
        discovery.start()
        if (found.isEmpty()) { status("Szukanie TV… Jeśli brak wyniku, wpisz IP i PIN z TV."); return }
        val entries = found.entries.toList()
        AlertDialog.Builder(this).setTitle("Telewizory w sieci").setItems(entries.map { "${it.value} · ${it.key}" }.toTypedArray()) { _, index -> edit(R.id.host).setText(entries[index].key) }.setNegativeButton("Zamknij", null).show()
    }
    private fun showBookmarks() {
        val array = latest.optJSONArray("bookmarks") ?: JSONArray()
        val titles = mutableListOf("＋ Dodaj bieżącą stronę TV")
        for (i in 0 until array.length()) titles.add(array.getJSONObject(i).optString("title"))
        AlertDialog.Builder(this).setTitle("Zakładki TV").setItems(titles.toTypedArray()) { _, index ->
            if (index == 0) send("bookmark_add") else {
                val item = index - 1
                AlertDialog.Builder(this).setTitle(titles[index]).setItems(arrayOf("Otwórz", "Usuń", "Przenieś wyżej", "Przenieś niżej")) { _, action -> when (action) {
                    0 -> send("open", JSONObject().put("url", array.getJSONObject(item).getString("url")))
                    1 -> send("bookmark_delete", JSONObject().put("index", item))
                    2 -> if (item > 0) send("bookmark_move", JSONObject().put("from", item).put("to", item - 1))
                    3 -> if (item < array.length() - 1) send("bookmark_move", JSONObject().put("from", item).put("to", item + 1))
                } }.show()
            }
        }.setNegativeButton("Zamknij", null).show()
    }
    private fun stopHeld() { held?.let { main.removeCallbacks(it) }; held = null }
    private fun dpad(id: Int, dx: Int, dy: Int) {
        val view = findViewById<Button>(id)
        view.setOnClickListener { send("move", JSONObject().put("dx", dx * 16).put("dy", dy * 16)) }
        view.setOnTouchListener { _, event -> when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { stopHeld(); var count = 0; val task = object : Runnable { override fun run() { val speed = (12 + count++ * 2).coerceAtMost(45); send("move", JSONObject().put("dx", dx * speed).put("dy", dy * speed), true); main.postDelayed(this, 80) } }; held = task; main.post(task); true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopHeld(); true }
            else -> true
        } }
    }
    private fun setupTouchpad() {
        val pad = findViewById<View>(R.id.touchpad)
        var x = 0f; var y = 0f; var startX = 0f; var startY = 0f; var dx = 0f; var dy = 0f; var moved = false; var longTap = false; var flushPending = false
        val flush = Runnable { flushPending = false; if (dx != 0f || dy != 0f) { send("move", JSONObject().put("dx", dx.coerceIn(-500f,500f)).put("dy", dy.coerceIn(-500f,500f)), true); dx = 0f; dy = 0f } }
        val hold = Runnable { if (!moved) { longTap = true; send("fullscreen") } }
        pad.setOnClickListener { send("click") }
        pad.setOnTouchListener { _, event -> when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x = event.x; y = event.y; startX = x; startY = y; flushPending = false; main.removeCallbacks(flush); moved = false; longTap = false; dx = 0f; dy = 0f; main.postDelayed(hold, 650); true }
            MotionEvent.ACTION_MOVE -> { if (kotlin.math.abs(event.x - startX) + kotlin.math.abs(event.y - startY) > 12) { moved = true; main.removeCallbacks(hold) }; dx += (event.x - x) * 2; dy += (event.y - y) * 2; x = event.x; y = event.y; if (!flushPending) { flushPending = true; main.postDelayed(flush, 33) }; true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { main.removeCallbacks(hold); main.removeCallbacks(flush); flushPending = false; if (event.actionMasked == MotionEvent.ACTION_UP) { if (!moved && !longTap) pad.performClick() else flush.run() }; true }
            else -> true
        } }
    }
    private fun startVoice() {
        if (Build.VERSION.SDK_INT < 31) { status("Lokalny głos wymaga Androida 12+. Na tym telefonie wpisz tekst."); return }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) { status("Brak lokalnego silnika mowy. Zainstaluj silnik/pakiet offline albo wpisz tekst."); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41); return }
        speech?.destroy()
        speech = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(this) }.getOrElse { status("Lokalny silnik mowy jest niedostępny. Wpisz tekst."); return }
        speech!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status("Mów ($speechLanguage)…") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status("Rozpoznawanie…") }
            override fun onError(error: Int) { status("Głos: błąd $error. Sprawdź pobrany pakiet języka offline.") }
            override fun onResults(results: Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { edit(R.id.address).setText(it); sendAddress(it) }; status("Rozpoznawanie zakończone") }
            override fun onPartialResults(results: Bundle?) {}
            override fun onEvent(type: Int, params: Bundle?) {}
        })
        runCatching { speech!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguage).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)) }.onFailure { status("Nie można uruchomić lokalnego rozpoznawania. Wpisz tekst.") }
    }
    override fun onRequestPermissionsResult(request: Int, permissions: Array<out String>, results: IntArray) { super.onRequestPermissionsResult(request, permissions, results); if (request == 41 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoice() }
    private fun receiveShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val link = Protocol.sharedUrl(text)
            if (link == null) { status("Udostępniony tekst nie zawiera URL HTTP/HTTPS"); return }
            edit(R.id.address).setText(link)
            if (client.send("open", JSONObject().put("url", link))) toast("Link wysłany na TV") else { pendingShare = link; status("Link gotowy — połącz lub sparuj TV") }
        }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("pendingShare", pendingShare); super.onSaveInstanceState(out) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveShare(intent) }
    override fun onStart() { super.onStart(); discovery.start(); if (edit(R.id.host).text.isNotBlank()) connect(false) }
    override fun onStop() { stopHeld(); main.removeCallbacksAndMessages(null); discovery.stop(); client.stop(); speech?.cancel(); super.onStop() }
    override fun onDestroy() { speech?.destroy(); speech = null; super.onDestroy() }
}
