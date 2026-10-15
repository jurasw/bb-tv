package pl.bbtv.tv

import android.content.Context
import android.webkit.WebView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TvApi28Test {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        context.getSharedPreferences("test", Context.MODE_PRIVATE).edit().clear().commit()
        // Activity tests use bundled rules and never fetch third-party lists.
        context.getSharedPreferences("adblock", Context.MODE_PRIVATE).edit().putLong("updated", System.currentTimeMillis()).commit()
    }
    @Test fun pinSingleUseAndTokenRevocation() {
        val prefs = context.getSharedPreferences("test", Context.MODE_PRIVATE)
        val pairing = Pairing(prefs)
        val pin = pairing.renew()
        assertEquals(6, pin.length)
        val token = pairing.pair(pin)!!
        assertTrue(pairing.authorized(token))
        assertFalse(prefs.getStringSet("tokens", emptySet())!!.contains(token))
        assertNull(pairing.pair(pin))
        pairing.revoke()
        assertFalse(pairing.authorized(token))
    }
    @Test fun pinExpiresAndRateLimits() {
        val pairing = Pairing(context.getSharedPreferences("test", Context.MODE_PRIVATE))
        val pin = pairing.renew()
        repeat(5) { pairing.pair("wrong") }
        assertNull(pairing.pair(pin))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
        assertNotNull(pairing.pair(pin))
        val expired = pairing.renew()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(121))
        assertNull(pairing.pair(expired))
    }
    @Test fun bookmarksPersistOrderAndEscapeHtml() {
        val prefs = context.getSharedPreferences("test", Context.MODE_PRIVATE)
        val b = Bookmarks(prefs)
        assertEquals(3, b.json().length())
        b.add("<script>bad</script>", "https://example.org/")
        b.move(3, 0)
        assertTrue(b.html().contains("&lt;script&gt;"))
        assertFalse(b.html().contains("<script>bad"))
        assertEquals("https://example.org/", Bookmarks(prefs).json().getJSONObject(0).getString("url"))
        b.delete(0)
        assertEquals(3, Bookmarks(prefs).json().length())
    }
    @Test fun domainBoundaryAndMixedListFormats() {
        val rules = DomainRules.parse(sequenceOf("# comment", "0.0.0.0 ads.example.org", "127.0.0.1 tracker.example.net # note", "||banner.example.com^", "@@||allowed.example.org^", "||bad.example.org/path^", "localhost"))
        assertEquals(3, rules.size)
        assertTrue(DomainRules.contains(rules, "sub.ads.example.org"))
        assertTrue(DomainRules.contains(rules, "ADS.EXAMPLE.ORG."))
        assertFalse(DomainRules.contains(rules, "notads.example.org"))
        assertFalse(DomainRules.contains(rules, "ads.example.org.evil.org"))
    }
    @Test fun activityCreatesSingleDesktopWebViewOnApi28() {
        val controller = Robolectric.buildActivity(TvActivity::class.java).create()
        try {
            val activity = controller.get()
            val web = activity.findViewById<WebView>(R.id.web)
            assertTrue(web.settings.javaScriptEnabled)
            assertTrue(web.settings.domStorageEnabled)
            assertFalse(web.settings.mediaPlaybackRequiresUserGesture)
            assertFalse(web.settings.allowFileAccess)
            assertFalse(web.settings.userAgentString.contains(" Mobile"))
        } finally { controller.destroy() }
    }
    @Test fun nativeFullscreenKeepsWebViewAndBackRestoresIt() {
        val controller = Robolectric.buildActivity(TvActivity::class.java).create()
        try {
            val activity = controller.get()
            val web = activity.findViewById<WebView>(R.id.web)
            var hidden = 0
            val chrome = web.webChromeClient!!
            chrome.onShowCustomView(android.widget.FrameLayout(activity), android.webkit.WebChromeClient.CustomViewCallback { hidden++ })
            assertEquals(android.view.View.INVISIBLE, web.visibility)
            assertSame(web, activity.findViewById<WebView>(R.id.web))
            activity.onBackPressed()
            assertEquals(android.view.View.VISIBLE, web.visibility)
            assertEquals(1, hidden)
            chrome.onHideCustomView()
            assertEquals(1, hidden)
        } finally { controller.destroy() }
    }

}
