package app.syncit.android

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SyncCrypto {
    fun channelId(phrase: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest("syncit-channel-v1:$phrase".toByteArray()),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private fun key(phrase: String): SecretKeySpec {
        val spec = PBEKeySpec(phrase.toCharArray(), "syncit-e2ee-v1".toByteArray(), 120_000, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    fun encrypt(payload: ClipboardPayload, phrase: String): EncryptedEnvelope {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(phrase))
        val encrypted = cipher.doFinal(Json.payload(payload).toString().toByteArray())
        val combined = ByteBuffer.allocate(cipher.iv.size + encrypted.size).put(cipher.iv).put(encrypted).array()
        return EncryptedEnvelope(1, Base64.encodeToString(cipher.iv, Base64.NO_WRAP), Base64.encodeToString(combined, Base64.NO_WRAP))
    }

    fun decrypt(message: RelayMessage, phrase: String): ClipboardPayload {
        require(message.version == 1)
        val combined = Base64.decode(message.ciphertext, Base64.DEFAULT)
        val nonce = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(phrase), GCMParameterSpec(128, nonce))
        return Json.payload(org.json.JSONObject(String(cipher.doFinal(encrypted))))
    }
}

