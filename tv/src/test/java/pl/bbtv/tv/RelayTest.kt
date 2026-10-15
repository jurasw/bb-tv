package pl.bbtv.tv

import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pl.bbtv.protocol.Protocol

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RelayTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("relay-test", Context.MODE_PRIVATE)
    private fun relay() = Relay(prefs, { _, _ -> }, {})

    @Before fun reset() { prefs.edit().clear().commit() }

    @Test fun localQrUsesPhoneAddressAndPreservesRoomAcrossModes() {
        val relay = relay()
        val room = relay.room
        assertTrue(relay.configure("http://10.0.2.2:8799", "http://192.168.0.24:8799/"))
        assertEquals("http://10.0.2.2:8799", relay.endpoint)
        assertEquals("http://192.168.0.24:8799/#r=$room", relay.link)
        assertTrue(PairQr.card(relay.link, "123456").contains("192.168.0.24:8799"))
        assertEquals(relay.link, relay().link)
        assertFalse(relay.configure("http://10.0.2.2:8799", "http://192.168.0.24:8799"))

        assertTrue(relay.configure("prod", null))
        assertEquals(Protocol.SITE, relay.endpoint)
        assertEquals("${Protocol.SITE}/#r=$room", relay.link)
        assertFalse(prefs.contains("relay"))
        assertFalse(prefs.contains("relaySite"))

        assertTrue(relay.configure("https://remote.example.org/", null))
        assertEquals("https://remote.example.org", relay.endpoint)
        assertEquals("https://remote.example.org/#r=$room", relay.link)
    }

    @Test fun invalidOriginsDoNotPartiallyChangeConfiguration() {
        val relay = relay()
        relay.configure("http://10.0.2.2:8799", "http://192.168.0.24:8799")
        val link = relay.link
        for (url in listOf("javascript:alert(1)", "https://user:password@example.org", "https://example.org/path", "https://example.org?room=x", "https://example.org/#r=x", "http://example.org:99999", "https://", "http://bad host")) {
            assertFalse(url, relay.configure(url, null))
            assertFalse(url, relay.configure("https://valid.example.org", url))
            assertEquals("http://10.0.2.2:8799", relay.endpoint)
            assertEquals(link, relay.link)
        }
    }

    @Test fun existingActivityAcceptsNewQrAddress() {
        val tvPrefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)
        tvPrefs.edit().clear().commit()
        context.getSharedPreferences("adblock", Context.MODE_PRIVATE).edit().putLong("updated", System.currentTimeMillis()).commit()
        val controller = Robolectric.buildActivity(TvActivity::class.java).create()
        try {
            controller.newIntent(Intent().putExtra("relay", "http://10.0.2.2:8799").putExtra("relay_site", "http://192.168.0.24:8799"))
            val configured = Relay(tvPrefs, { _, _ -> }, {})
            assertEquals("http://10.0.2.2:8799", configured.endpoint)
            assertTrue(configured.link.startsWith("http://192.168.0.24:8799/#r="))
            controller.newIntent(Intent().putExtra("relay", "prod"))
            assertEquals(Protocol.SITE, configured.site)
        } finally {
            controller.destroy()
            tvPrefs.edit().clear().commit()
        }
    }
}
