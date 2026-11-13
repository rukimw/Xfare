package lk.xfare.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Auto-recovery for aggressive MIUI / HyperOS recent-app swipe killers
        try {
            val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
            val pendingIntentFlags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getForegroundService(
                applicationContext,
                RESTART_REQUEST_CODE,
                restartIntent,
                pendingIntentFlags
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1500L,
                pendingIntent
            )
        } catch (_: Exception) {
            // Fallback silently if exact alarm permission is restricted on the device
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fgs_notification_text)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(R.string.fgs_notification_text))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "xfare_keepalive_channel"
        private const val NOTIFICATION_ID = 2001
        private const val RESTART_REQUEST_CODE = 2002

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun ensureRunning(context: Context) {
            if (!isRunning) {
                start(context)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
            isRunning = false
        }
    }
}
