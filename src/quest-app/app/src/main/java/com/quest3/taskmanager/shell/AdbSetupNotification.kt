package com.quest3.taskmanager.shell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.quest3.taskmanager.FileLogger
import com.quest3.taskmanager.R

object AdbSetupNotification {
    // RemoteInput actions require a mutable PendingIntent (Android 12+).
    private const val REMOTE_INPUT_PENDING_FLAGS =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    const val CHANNEL_ID = "qtaskmgr_adb_setup"
    const val NOTIFICATION_ID = 2002

    const val ACTION_PAIR = "com.quest3.taskmanager.standalone.ACTION_ADB_PAIR"
    const val ACTION_CONNECT = "com.quest3.taskmanager.standalone.ACTION_ADB_CONNECT"
    const val EXTRA_REMOTE_INPUT_RESULT = "adb_setup_input"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.adb_notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.adb_notif_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun showPairingNeeded(context: Context) {
        postNotification(context) {
            val remoteInput = RemoteInput.Builder(EXTRA_REMOTE_INPUT_RESULT)
                .setLabel(context.getString(R.string.adb_notif_pair_hint))
                .build()
            val intent = Intent(ACTION_PAIR).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, 0, intent, REMOTE_INPUT_PENDING_FLAGS)
            val action = NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.adb_notif_pair_action),
                pending
            ).addRemoteInput(remoteInput).build()
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.adb_notif_pair_title))
                .setContentText(context.getString(R.string.adb_notif_pair_text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(action)
                .build()
        }
    }

    fun showConnectNeeded(context: Context) {
        postNotification(context) {
            val remoteInput = RemoteInput.Builder(EXTRA_REMOTE_INPUT_RESULT)
                .setLabel(context.getString(R.string.adb_notif_connect_hint))
                .build()
            val intent = Intent(ACTION_CONNECT).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, 1, intent, REMOTE_INPUT_PENDING_FLAGS)
            val action = NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.adb_notif_connect_action),
                pending
            ).addRemoteInput(remoteInput).build()
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.adb_notif_connect_title))
                .setContentText(context.getString(R.string.adb_notif_connect_text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(action)
                .build()
        }
    }

    private inline fun postNotification(context: Context, build: () -> android.app.Notification) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.notify(NOTIFICATION_ID, build())
        } catch (e: Exception) {
            FileLogger.e("adb setup notification failed", e)
        }
    }

    fun dismiss(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID)
    }
}
