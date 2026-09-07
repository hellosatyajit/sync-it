package app.syncit.android

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class SendClipboardActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            delay(180) // wait until this activity has focus, satisfying Android clipboard privacy
            val payload = runCatching { ClipboardBridge.capture(this@SendClipboardActivity) }.getOrNull()
            val result = withContext(Dispatchers.IO) { runCatching { payload?.let { RelayClient(this@SendClipboardActivity).push(it) } } }
            Toast.makeText(this@SendClipboardActivity, if (payload == null) "Nothing to send" else if (result.isSuccess) "Clipboard sent" else "Could not send clipboard", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

