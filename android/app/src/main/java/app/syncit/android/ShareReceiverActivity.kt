package app.syncit.android

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareReceiverActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        val relay = RelayClient(this)
        if (!relay.isConfigured) {
            Toast.makeText(this, "Open Sync It and configure the relay first", Toast.LENGTH_LONG).show()
            finishWithoutAnimation()
            return
        }

        scope.launch {
            val payload = runCatching { ClipboardBridge.captureShare(this@ShareReceiverActivity, intent) }.getOrNull()
            val sent = payload != null && withContext(Dispatchers.IO) { runCatching { relay.push(payload) }.isSuccess }
            Toast.makeText(
                this@ShareReceiverActivity,
                if (sent) "Sent to Mac" else "Could not send this item",
                Toast.LENGTH_SHORT
            ).show()
            finishWithoutAnimation()
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
