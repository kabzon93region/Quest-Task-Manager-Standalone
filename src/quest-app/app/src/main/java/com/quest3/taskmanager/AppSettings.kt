package com.quest3.taskmanager

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    const val PREFS = "qtaskmgr_standalone_settings"
    const val KEY_LOGGING = "logging_enabled"
    const val KEY_LOG_PATH = "log_path"
    const val KEY_NOTIFICATION = "notification_enabled"
    const val KEY_ADB_PAIR_PORT = "adb_pair_port"
    const val KEY_ADB_PAIR_CODE = "adb_pair_code"
    const val KEY_ADB_DEBUG_PORT = "adb_debug_port"
    const val KEY_ADB_PAIRED = "adb_paired"
    const val DEFAULT_LOG_PATH = "/sdcard/Download/QTaskManager-Standalone.log"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isNotificationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATION, true)

    /** Синхронизирует foreground-сервис с переключателем в настройках. */
    fun syncNotificationService(context: Context) {
        val enabled = isNotificationEnabled(context)
        val running = CleanupForegroundService.isRunning(context)
        when {
            enabled && !running -> {
                CleanupForegroundService.start(context)
                FileLogger.i("notification sync: started (pref=on)")
            }
            !enabled && running -> {
                CleanupForegroundService.stop(context)
                FileLogger.i("notification sync: stopped (pref=off)")
            }
            else -> FileLogger.d("notification sync: ok enabled=$enabled running=$running")
        }
    }
}
