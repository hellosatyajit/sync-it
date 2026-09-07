package app.syncit.android

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class RelayClient(private val context: Context) {
    private val prefs get() = context.getSharedPreferences("syncit", Context.MODE_PRIVATE)
    val relayUrl get() = prefs.getString("relay_url", "")!!.trimEnd('/')
    val phrase get() = SecretStore.load(context)
    private val deviceId: String get() {
        prefs.getString("device_id", null)?.let { return it }
        return ("android_" + UUID.randomUUID().toString().replace("-", "")).also { prefs.edit().putString("device_id", it).apply() }
    }
    val isConfigured get() = relayUrl.startsWith("https://") && phrase.length >= 8

    fun saveConfig(url: String, secret: String) {
        prefs.edit().putString("relay_url", url.trim().trimEnd('/')).apply()
        SecretStore.save(context, secret)
    }

    fun push(payload: ClipboardPayload) {
        val connection = open("/v1/messages", "POST")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.use { it.write(Json.envelope(SyncCrypto.encrypt(payload, phrase)).toString().toByteArray()) }
        check(connection.responseCode == 201) { "Relay returned ${connection.responseCode}" }
        connection.disconnect()
    }

    fun pull(cursor: String): List<RelayMessage> {
        val connection = open("/v1/messages?after=" + URLEncoder.encode(cursor, "UTF-8"), "GET")
        check(connection.responseCode == 200) { "Relay returned ${connection.responseCode}" }
        return connection.inputStream.bufferedReader().use { Json.messages(it.readText()) }.also { connection.disconnect() }
    }

    private fun open(path: String, method: String): HttpURLConnection = (URL(relayUrl + path).openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 10_000; readTimeout = 10_000; useCaches = false
        setRequestProperty("X-SyncIt-Channel", SyncCrypto.channelId(phrase)); setRequestProperty("X-SyncIt-Device", deviceId)
    }
}

