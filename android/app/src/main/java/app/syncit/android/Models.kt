package app.syncit.android

data class ClipboardPayload(val id: String, val type: String, val value: String, val createdAt: Long)
data class EncryptedEnvelope(val version: Int, val nonce: String, val ciphertext: String)
data class RelayMessage(val id: String, val sender: String, val createdAt: Long, val version: Int, val nonce: String, val ciphertext: String)

