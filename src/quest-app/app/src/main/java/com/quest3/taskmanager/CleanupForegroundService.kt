package com.quest3.taskmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.quest3.taskmanager.shell.ShellManager
import com.quest3.taskmanager.shell.ShellWatchdog
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CleanupForegroundService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isCleaning = AtomicBoolean(false)
    private var lastKilledCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isActive = true
        createNotificationChannel()
        FileLogger.configure(applicationContext)
        FileLogger.i("cleanup service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLEANUP -> runCleanup()
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> ensureForeground(showIdleNotification())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isActive = false
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun runCleanup() {
        if (!isCleaning.compareAndSet(false, true)) return

        executor.execute {
            runBlocking { ShellWatchdog.ensureShell(applicationContext) }
            if (!ShellManager.isReady()) {
                mainHandler.post {
                    isCleaning.set(false)
                    updateNotification(
                        getString(R.string.notification_title),
                        ramText(R.string.notification_shell_error),
                        true
                    )
                }
                return@execute
            }

            mainHandler.post {
                updateNotification(
                    getString(R.string.notification_title),
                    ramText(R.string.notification_cleaning),
                    true
                )
            }

            var killed = 0
            try {
                killed = RulesCleanup(applicationContext).run().killedCount
                lastKilledCount = killed
                FileLogger.i("notification cleanup killed=$killed")
            } catch (e: Exception) {
                FileLogger.e("notification cleanup failed", e)
            }
            mainHandler.post {
                val text = if (killed > 0) {
                    getString(
                        R.string.notification_result_closed,
                        ramLabel(),
                        getString(R.string.killed_count, killed)
                    )
                } else {
                    ramText(R.string.notification_result_none)
                }
                updateNotification(getString(R.string.notification_title), text, true)
                mainHandler.postDelayed({
                    updateNotification(getString(R.string.notification_title), idleText(), true)
                    isCleaning.set(false)
                }, RESULT_DISPLAY_MS)
            }
        }
    }

    private fun ensureForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun idleText(): String =
        if (lastKilledCount > 0) {
            getString(
                R.string.notification_result_closed,
                ramLabel(),
                getString(R.string.killed_count, lastKilledCount)
            )
        } else {
            ramText(R.string.notification_idle)
        }

    private fun showIdleNotification(): Notification =
        buildNotification(getString(R.string.notification_title), idleText(), true)

    private fun updateNotification(title: String, text: String, ongoing: Boolean) {
        val notification = buildNotification(title, text, ongoing)
        ensureForeground(notification)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String, ongoing: Boolean): Notification {
        val cleanupIntent = Intent(this, CleanupForegroundService::class.java).apply {
            action = ACTION_CLEANUP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getService(this, 0, cleanupIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun ramLabel(): String = RamInfo.formatCompactMb(applicationContext)
    private fun ramText(resId: Int): String = getString(resId, ramLabel())
    private fun ramText(resId: Int, arg: Int): String = getString(resId, ramLabel(), arg)

    companion object {
        const val CHANNEL_ID = "qtaskmgr_cleanup"
        const val NOTIFICATION_ID = 2001
        const val ACTION_CLEANUP = "com.quest3.taskmanager.standalone.ACTION_CLEANUP"
        const val ACTION_STOP = "com.quest3.taskmanager.standalone.ACTION_STOP"
        const val ACTION_START = "com.quest3.taskmanager.standalone.ACTION_START"
        private const val RESULT_DISPLAY_MS = 4000L

        @Volatile
        var isActive: Boolean = false
            private set

        fun isRunning(context: Context): Boolean {
            if (isActive) return true
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return manager.activeNotifications.any {
                it.id == NOTIFICATION_ID && it.packageName == context.packageName
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, CleanupForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CleanupForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
