package pl.bbtv.phone

import android.Manifest
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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.json.JSONObject
import pl.bbtv.protocol.Protocol

class PhoneActivity : ComponentActivity(), Remote {
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("phone", Context.MODE_PRIVATE) }
    private lateinit var client: RemoteClient
    private lateinit var discovery: PhoneDiscovery
    private var selectedHost = ""
    private var pendingShare: String? = null
    private var speech: SpeechRecognizer? = null
    private val ui = RemoteState()
    override fun onCreate(saved: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(saved)
        ui.language = prefs.getString("language", "pl-PL")!!
        ui.host = prefs.getString("host", "")!!
        client = RemoteClient({ text -> ui.status = text; ui.connected = client.ready }, { ui.tv = it }, { token, deviceId -> prefs.edit().putString("token:$selectedHost", token).putString("deviceId:$selectedHost", deviceId).apply(); ui.pin = "" }, { pendingShare?.let { link -> if (client.send("open", JSONObject().put("url", link))) { pendingShare = null; notice("Link wysłany na TV") } } })
        discovery = PhoneDiscovery(this, { name, host -> main.post { ui.found[host] = name } }, { message -> main.post { ui.status = message } })
        setContent { RemoteScreen(ui, this) }
        if (saved == null) receiveShare(intent) else pendingShare = saved.getString("pendingShare")
    }
    private fun notice(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    private fun endpoint(value: String): String {
        require(Regex("[0-9.]+(:[0-9]{1,5})?").matches(value)) { "Podaj lokalny adres IPv4 TV" }
        val parts = value.split(':'); val octets = parts[0].split('.').map { it.toInt() }
        require(octets.size == 4 && octets.all { it in 0..255 }) { "Nieprawidłowe IP" }
        require(octets[0] == 10 || octets[0] == 192 && octets[1] == 168 || octets[0] == 172 && octets[1] in 16..31 || octets[0] == 169 && octets[1] == 254 || octets[0] == 127) { "Dozwolona jest tylko sieć lokalna" }
        val port = parts.getOrNull(1)?.toInt() ?: Protocol.PORT
        require(port in 1..65535)
        return "${octets.joinToString(".")}:$port"
    }
    override fun connect(pair: Boolean) {
        runCatching {
            val host = endpoint(ui.host.trim())
            val pin = if (pair) ui.pin else ""
            if (pair) require(Regex("[0-9]{6}").matches(pin)) { "Wpisz sześciocyfrowy PIN z TV" }
            val token = prefs.getString("token:$host", "")!!
            if (!pair && token.isBlank()) { ui.status = "Wpisz PIN z TV i dotknij Sparuj"; return }
            selectedHost = host
            prefs.edit().putString("host", host).apply()
            client.start(host, if (pair) "" else token, pin, prefs.getString("deviceId:$host", "")!!)
        }.onFailure { ui.status = it.message ?: "Błędny adres" }
    }
    override fun discover() { discovery.start(); if (ui.found.isEmpty()) ui.status = "Szukanie TV… Jeśli brak wyniku, wpisz IP i PIN z TV." }
    override fun send(name: String, data: JSONObject, quiet: Boolean) { if (!client.send(name, data) && !quiet) notice("Najpierw połącz z TV") }
    override fun go(text: String) {
        val input = text.trim(); if (input.isEmpty()) return
        val url = Protocol.sharedUrl(input) ?: runCatching { if (!input.contains(' ') && input.contains('.')) Protocol.url(input) else null }.getOrNull()
        if (url != null) send("open", JSONObject().put("url", url)) else send("search", JSONObject().put("text", input.take(2048)))
    }
    override fun search(template: String): Boolean = runCatching { Protocol.search("test", template); send("config", JSONObject().put("search", template)) }.onFailure { notice("Nieprawidłowy szablon") }.isSuccess
    override fun language(value: String): Boolean {
        if (!Regex("[a-z]{2,3}-[A-Z]{2}").matches(value)) { notice("Podaj kod, np. pl-PL"); return false }
        ui.language = value; prefs.edit().putString("language", value).apply(); return true
    }
    override fun voice() {
        if (Build.VERSION.SDK_INT < 31) { notice("Lokalny głos wymaga Androida 12+. Wpisz tekst."); return }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) { notice("Brak lokalnego silnika mowy. Zainstaluj pakiet offline albo wpisz tekst."); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41); return }
        speech?.destroy()
        speech = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(this) }.getOrElse { notice("Lokalny silnik mowy jest niedostępny. Wpisz tekst."); return }
        speech!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { ui.listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { ui.listening = false }
            override fun onError(error: Int) { ui.listening = false; notice("Głos: błąd $error. Sprawdź pakiet języka offline.") }
            override fun onResults(results: Bundle?) { ui.listening = false; results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { ui.address = it; go(it) } }
            override fun onPartialResults(results: Bundle?) {}
            override fun onEvent(type: Int, params: Bundle?) {}
        })
        runCatching { speech!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, ui.language).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)) }.onFailure { notice("Nie można uruchomić lokalnego rozpoznawania. Wpisz tekst.") }
    }
    override fun onRequestPermissionsResult(request: Int, permissions: Array<String>, results: IntArray) { super.onRequestPermissionsResult(request, permissions, results); if (request == 41 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) voice() }
    private fun receiveShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val link = Protocol.sharedUrl(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            if (link == null) { notice("Udostępniony tekst nie zawiera URL HTTP/HTTPS"); return }
            ui.address = link
            if (client.send("open", JSONObject().put("url", link))) notice("Link wysłany na TV") else { pendingShare = link; ui.status = "Link gotowy — połącz lub sparuj TV" }
        }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("pendingShare", pendingShare); super.onSaveInstanceState(out) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveShare(intent) }
    override fun onStart() { super.onStart(); discovery.start(); if (ui.host.isNotBlank()) connect(false) }
    override fun onStop() { main.removeCallbacksAndMessages(null); discovery.stop(); client.stop(); ui.connected = false; speech?.cancel(); ui.listening = false; super.onStop() }
    override fun onDestroy() { speech?.destroy(); speech = null; super.onDestroy() }
}
