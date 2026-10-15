package pl.bbtv.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address
import java.util.ArrayDeque
import pl.bbtv.protocol.Protocol

class PhoneDiscovery(context: Context, private val found: (String, String) -> Unit, private val error: (String) -> Unit) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    @Volatile private var active = false
    private val queue = ArrayDeque<NsdServiceInfo>()
    @Volatile private var resolving = false
    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String?) {}
        override fun onDiscoveryStopped(type: String?) {}
        override fun onStartDiscoveryFailed(type: String?, code: Int) { active = false; error("NSD niedostępne ($code) — wpisz IP ręcznie") }
        override fun onStopDiscoveryFailed(type: String?, code: Int) {}
        override fun onServiceLost(info: NsdServiceInfo?) {}
        override fun onServiceFound(info: NsdServiceInfo) { if (active && info.serviceType.contains("_tvbrowser._tcp")) { synchronized(queue) { queue.add(info) }; resolveNext() } }
    }
    @Synchronized private fun resolveNext() {
        if (resolving || !active) return
        val info = synchronized(queue) { queue.poll() } ?: return
        resolving = true
        @Suppress("DEPRECATION")
        try { nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo?, code: Int) { finish() }
            override fun onServiceResolved(service: NsdServiceInfo) { if (active && service.host is Inet4Address) found(service.serviceName, "${service.host.hostAddress}:${service.port}"); finish() }
            private fun finish() { resolving = false; resolveNext() }
        }) } catch (_: Exception) { resolving = false }
    }
    fun start() { if (active) return; active = true; runCatching { nsd.discoverServices(Protocol.SERVICE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { active = false; error("Brak NSD — wpisz IP ręcznie") } }
    fun stop() { if (active) runCatching { nsd.stopServiceDiscovery(listener) }; active = false; synchronized(queue) { queue.clear() } }
}
