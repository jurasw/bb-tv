package pl.bbtv.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import pl.bbtv.protocol.Protocol
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class TvDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    @Volatile private var registered = false
    @Volatile private var wanted = false
    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo?) { registered = true; if (!wanted) stop() }
        override fun onRegistrationFailed(info: NsdServiceInfo?, error: Int) { registered = false }
        override fun onServiceUnregistered(info: NsdServiceInfo?) { registered = false }
        override fun onUnregistrationFailed(info: NsdServiceInfo?, error: Int) {}
    }
    fun start() { wanted = true; runCatching { nsd.registerService(NsdServiceInfo().apply { serviceName = "BB-TV"; serviceType = Protocol.SERVICE; port = Protocol.PORT }, NsdManager.PROTOCOL_DNS_SD, listener) } }
    fun stop() { wanted = false; if (registered) runCatching { nsd.unregisterService(listener) } }
    companion object {
        fun addresses(): String = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }.flatMap { Collections.list(it.inetAddresses) }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress || it.isLinkLocalAddress }.joinToString(" / ") { it.hostAddress ?: "" }
        }.getOrDefault("").ifBlank { "brak IPv4 — sprawdź Wi-Fi/Ethernet" }
    }
}
