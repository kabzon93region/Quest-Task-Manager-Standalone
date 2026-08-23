package com.quest3.taskmanager.shell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.quest3.taskmanager.R

object AdbSetupNotification {
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
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val remoteInput = RemoteInput.Builder(EXTRA_REMOTE_INPUT_RESULT)
            .setLabel(context.getString(R.string.adb_notif_pair_hint))
            .build()
        val intent = Intent(ACTION_PAIR).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.adb_notif_pair_action),
            pending
        ).addRemoteInput(remoteInput).build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.adb_notif_pair_title))
            .setContentText(context.getString(R.string.adb_notif_pair_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun showConnectNeeded(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val remoteInput = RemoteInput.Builder(EXTRA_REMOTE_INPUT_RESULT)
            .setLabel(context.getString(R.string.adb_notif_connect_hint))
            .build()
        val intent = Intent(ACTION_CONNECT).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.adb_notif_connect_action),
            pending
        ).addRemoteInput(remoteInput).build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.adb_notif_connect_title))
            .setContentText(context.getString(R.string.adb_notif_connect_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID)
    }
}
