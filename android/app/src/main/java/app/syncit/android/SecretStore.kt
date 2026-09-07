package app.syncit.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

object SecretStore {
    private const val ALIAS = "syncit_pairing_key"
    private const val VALUE = "pairing_phrase"

    private fun key(): java.security.Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.getKey(ALIAS, null)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    fun save(context: Context, phrase: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val combined = cipher.iv + cipher.doFinal(phrase.toByteArray())
        context.getSharedPreferences("syncit", Context.MODE_PRIVATE).edit().putString(VALUE, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): String {
        return try {
            val combined = Base64.decode(context.getSharedPreferences("syncit", Context.MODE_PRIVATE).getString(VALUE, ""), Base64.DEFAULT)
            if (combined.size < 13) return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, combined.copyOfRange(0, 12)))
            String(cipher.doFinal(combined.copyOfRange(12, combined.size)))
        } catch (_: Exception) { "" }
    }
}

