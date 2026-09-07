package app.syncit.android

import org.json.JSONArray
import org.json.JSONObject

object Json {
    fun payload(value: ClipboardPayload) = JSONObject()
        .put("id", value.id).put("type", value.type).put("value", value.value).put("createdAt", value.createdAt)

    fun payload(json: JSONObject) = ClipboardPayload(json.getString("id"), json.getString("type"), json.getString("value"), json.getLong("createdAt"))

    fun envelope(value: EncryptedEnvelope) = JSONObject()
        .put("version", value.version).put("nonce", value.nonce).put("ciphertext", value.ciphertext)

    fun messages(text: String): List<RelayMessage> {
        val array: JSONArray = JSONObject(text).getJSONArray("messages")
        return (0 until array.length()).map { i ->
            val it = array.getJSONObject(i)
            RelayMessage(it.getString("id"), it.getString("sender"), it.getLong("createdAt"), it.getInt("version"), it.getString("nonce"), it.getString("ciphertext"))
        }
    }
}

