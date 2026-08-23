package com.quest3.taskmanager.shell

import android.content.Context
import com.quest3.taskmanager.FileLogger
import com.quest3.taskmanager.shell.adb.AdbConnectionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps ADB shell available: health check, auto-connect on resume and before critical operations.
 */
object ShellWatchdog {
    private val gate = Mutex()

    @Volatile
    var isReconnecting = false
        private set

    @Volatile
    var lastReconnectFailed = false
        private set

    @Volatile
    private var lastErrorMessage: String? = null

    fun lastError(): String? = lastErrorMessage

    suspend fun ensureShell(context: Context): Boolean {
        return gate.withLock {
            try {
                if (AdbShellBackend.isReady()) {
                    if (AdbConnectionManager.healthCheck()) {
                        clearFailure()
                        return@withLock true
                    }
                    FileLogger.w("shell watchdog: stale adb session, reconnecting")
                    AdbConnectionManager.disconnect()
                }
                reconnectLocked(context)
            } finally {
                isReconnecting = false
            }
        }
    }

    private suspend fun reconnectLocked(context: Context): Boolean {
        if (!AdbShellBackend.isPaired(context)) {
            markFailure("not paired")
            AdbSetupNotification.showPairingNeeded(context)
            return false
        }
        isReconnecting = true
        val (ok, msg) = AdbShellBackend.tryAutoConnect(context)
        if (ok) {
            clearFailure()
            AdbSetupNotification.dismiss(context)
            return true
        }
        FileLogger.w("shell watchdog: auto-connect failed: $msg")
        markFailure(msg)
        AdbSetupNotification.showConnectNeeded(context)
        return false
    }

    private fun clearFailure() {
        lastReconnectFailed = false
        lastErrorMessage = null
    }

    private fun markFailure(message: String) {
        lastReconnectFailed = !ShellManager.isReady()
        lastErrorMessage = message
    }
}
