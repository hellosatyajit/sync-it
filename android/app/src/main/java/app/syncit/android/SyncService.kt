package app.syncit.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class SyncService : Service() {
    companion object {
        private const val CHANNEL = "syncit_status"
        private const val NOTIFICATION = 71
        fun start(context: Context) = context.startForegroundService(Intent(context, SyncService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, SyncService::class.java))
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cursor = ""

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Clipboard sync", NotificationManager.IMPORTANCE_LOW))
        val sendIntent = PendingIntent.getActivity(this, 0, Intent(this, SendClipboardActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val openIntent = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Sync It is connected")
            .setContentText("Tap Send clipboard when Sync It is in the background")
            .setContentIntent(openIntent).addAction(0, "Send clipboard", sendIntent).setOngoing(true).build()
        startForeground(NOTIFICATION, notification)
        scope.launch {
            val relay = RelayClient(this@SyncService)
            while (isActive) {
                try {
                    relay.pull(cursor).forEach { message ->
                        cursor = maxOf(cursor, message.id)
                        runCatching { SyncCrypto.decrypt(message, relay.phrase) }.getOrNull()?.let { ClipboardBridge.apply(this@SyncService, it) }
                    }
                } catch (_: Exception) { }
                delay(1_500)
            }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

