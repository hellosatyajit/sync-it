package app.syncit.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object ClipboardBridge {
    private const val MAX_BYTES = 6 * 1024 * 1024

    fun capture(context: Context): ClipboardPayload? {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip ?: return null
        val item = clip.getItemAt(0)
        item.uri?.let { uri ->
            val type = context.contentResolver.getType(uri) ?: ""
            if (type.startsWith("image/")) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        require(output.size() <= MAX_BYTES) { "Image is larger than 6 MiB" }
                    }
                    output.toByteArray()
                } ?: return null
                require(bytes.size <= MAX_BYTES) { "Image is larger than 6 MiB" }
                return ClipboardPayload(UUID.randomUUID().toString(), type, Base64.encodeToString(bytes, Base64.NO_WRAP), System.currentTimeMillis())
            }
        }
        val text = item.coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() } ?: return null
        return ClipboardPayload(UUID.randomUUID().toString(), "text", text, System.currentTimeMillis())
    }

    fun captureShare(context: Context, intent: Intent): ClipboardPayload? {
        if (intent.action != Intent.ACTION_SEND) return null
        val uri = sharedUri(intent)
        if (uri != null) {
            val type = intent.type?.takeIf { it.startsWith("image/") }
                ?: context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
            if (type != null) {
                val bytes = readLimited(context, uri)
                return ClipboardPayload(UUID.randomUUID().toString(), type, Base64.encodeToString(bytes, Base64.NO_WRAP), System.currentTimeMillis())
            }
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotEmpty() } ?: return null
        return ClipboardPayload(UUID.randomUUID().toString(), "text", text, System.currentTimeMillis())
    }

    fun apply(context: Context, payload: ClipboardPayload) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (payload.type == "text") {
            manager.setPrimaryClip(ClipData.newPlainText("Synced from Mac", payload.value))
        } else if (payload.type.startsWith("image/")) {
            val directory = File(context.cacheDir, "received").apply { mkdirs() }
            val file = File(directory, "syncit-${payload.id}.png").apply { writeBytes(Base64.decode(payload.value, Base64.DEFAULT)) }
            val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            manager.setPrimaryClip(ClipData.newUri(context.contentResolver, "Synced image from Mac", uri))
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUri(intent: Intent): Uri? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private fun readLimited(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                require(output.size() <= MAX_BYTES) { "Image is larger than 6 MiB" }
            }
            output.toByteArray()
        } ?: error("The shared image could not be opened")
    }
}
