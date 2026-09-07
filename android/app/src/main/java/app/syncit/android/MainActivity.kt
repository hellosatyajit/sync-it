package app.syncit.android

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var relay: RelayClient
    private lateinit var status: TextView
    private lateinit var url: EditText
    private lateinit var phrase: EditText
    private var lastClip = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        relay = RelayClient(this)
        title = "Sync It"
        val pad = (24 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(pad, pad, pad, pad); setBackgroundColor(Color.rgb(16, 17, 20)) }
        fun text(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.WHITE); setPadding(0, 10, 0, 10) }
        container.addView(text("Sync It", 32f))
        container.addView(text("Your clipboard, on both devices.", 16f).apply { setTextColor(Color.LTGRAY) })
        url = EditText(this).apply { hint = "https://your-relay.example.com"; setText(relay.relayUrl); inputType = InputType.TYPE_TEXT_VARIATION_URI; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        phrase = EditText(this).apply { hint = "Private pairing phrase"; setText(relay.phrase); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        container.addView(url, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(phrase, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val start = Button(this).apply { text = "Save and start sync"; setOnClickListener { saveAndStart() } }
        val send = Button(this).apply { text = "Send clipboard now"; setOnClickListener { sendClipboard() } }
        container.addView(start); container.addView(send)
        status = text(if (relay.isConfigured) "Configured" else "Enter your settings", 15f)
        container.addView(status)
        container.addView(text("Android allows automatic clipboard reading only while this screen is visible. In other apps, use “Send clipboard” from the Sync It notification. Items received from your Mac are placed on the clipboard automatically.", 14f).apply { setTextColor(Color.LTGRAY) })
        setContentView(ScrollView(this).apply { addView(container) })
    }

    private fun saveAndStart() {
        if (!url.text.startsWith("https://") || phrase.text.length < 8) { status.text = "Use an HTTPS URL and a phrase of at least 8 characters"; return }
        relay.saveConfig(url.text.toString(), phrase.text.toString())
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
        SyncService.start(this); status.text = "Connected · background sync enabled"
    }

    private fun sendClipboard() {
        val payload = runCatching { ClipboardBridge.capture(this) }.getOrNull()
        if (payload == null) { status.text = "Nothing supported on the clipboard"; return }
        scope.launch { status.text = "Sending…"; status.text = withContext(Dispatchers.IO) { runCatching { relay.push(payload); "Clipboard sent" }.getOrElse { "Send failed: ${it.message}" } } }
    }

    override fun onResume() {
        super.onResume()
        if (relay.isConfigured) {
            scope.launch {
                while (isActive) {
                    val payload = runCatching { ClipboardBridge.capture(this@MainActivity) }.getOrNull()
                    val marker = payload?.let { it.type + it.value.hashCode() }
                    if (marker != null && marker != lastClip) {
                        lastClip = marker
                        withContext(Dispatchers.IO) { runCatching { relay.push(payload) } }
                    }
                    delay(1_000)
                }
            }
        }
    }

    override fun onPause() { super.onPause(); scope.coroutineContext.cancelChildren() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
