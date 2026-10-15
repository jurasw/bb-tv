package pl.bbtv.tv

import android.content.SharedPreferences
import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class Pairing(private val prefs: SharedPreferences) {
    private val random = SecureRandom()
    private var expires = 0L
    private var attempts = 0
    private var lockedUntil = 0L
    private var pin = ""
    val deviceId: String = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
    @Synchronized fun renew(): String {
        pin = "%06d".format(java.util.Locale.US, random.nextInt(1000000))
        expires = SystemClock.elapsedRealtime() + 120000
        return pin
    }
    @Synchronized fun label(): String = if (SystemClock.elapsedRealtime() < expires) pin else "wygasł — odnowienie w ustawieniach"
    @Synchronized fun active(): String? = if (SystemClock.elapsedRealtime() < expires) pin else null
    @Synchronized fun pair(value: String): String? {
        val now = SystemClock.elapsedRealtime()
        if (now < lockedUntil || now >= expires) return null
        if (!MessageDigest.isEqual(value.toByteArray(), pin.toByteArray())) {
            if (++attempts >= 5) { lockedUntil = now + 60000; attempts = 0 }
            return null
        }
        attempts = 0
        expires = 0
        val token = ByteArray(32).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val hashes = prefs.getStringSet("tokens", emptySet())!!.toMutableSet()
        if (hashes.size >= 8) hashes.clear()
        hashes.add(hash(token))
        prefs.edit().putStringSet("tokens", hashes).apply()
        return token
    }
    @Synchronized fun authorized(token: String): Boolean = token.length == 64 && prefs.getStringSet("tokens", emptySet())!!.contains(hash(token))
    @Synchronized fun revoke() { prefs.edit().remove("tokens").apply(); expires = 0 }
    private fun hash(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
