package com.quest3.taskmanager.shell

import android.content.Context
import com.quest3.taskmanager.AppSettings
import com.quest3.taskmanager.R
import com.quest3.taskmanager.shell.adb.AdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object AdbShellBackend : ShellBackend {
    override val id = "adb"

    fun isPaired(context: Context): Boolean =
        AppSettings.prefs(context).getBoolean(AppSettings.KEY_ADB_PAIRED, false)

    fun savePairing(context: Context, pairingPort: String, pairingCode: String) {
        AppSettings.prefs(context).edit()
            .putString(AppSettings.KEY_ADB_PAIR_PORT, pairingPort.trim())
            .putString(AppSettings.KEY_ADB_PAIR_CODE, pairingCode.trim())
            .apply()
    }

    fun saveDebugPort(context: Context, port: String) {
        AppSettings.prefs(context).edit()
            .putString(AppSettings.KEY_ADB_DEBUG_PORT, port.trim())
            .apply()
    }

    fun getDebugPort(context: Context): String =
        AppSettings.prefs(context).getString(AppSettings.KEY_ADB_DEBUG_PORT, "").orEmpty()

    fun getPairPort(context: Context): String =
        AppSettings.prefs(context).getString(AppSettings.KEY_ADB_PAIR_PORT, "").orEmpty()

    suspend fun pair(context: Context): Pair<Boolean, String> {
        val prefs = AppSettings.prefs(context)
        val port = prefs.getString(AppSettings.KEY_ADB_PAIR_PORT, "").orEmpty().toIntOrNull()
        val code = prefs.getString(AppSettings.KEY_ADB_PAIR_CODE, "").orEmpty()
        if (port == null || port <= 0) return false to context.getString(R.string.adb_error_pair_port)
        if (code.isBlank()) return false to context.getString(R.string.adb_error_pair_code)
        return AdbConnectionManager.pair(context, AdbConnectionManager.DEFAULT_HOST, port, code)
            .fold(
                onSuccess = { true to context.getString(R.string.adb_pair_ok) },
                onFailure = { false to localizeError(context, it) }
            )
    }

    suspend fun connect(context: Context): Pair<Boolean, String> {
        val port = getDebugPort(context).toIntOrNull()
            ?: return false to context.getString(R.string.adb_error_debug_port)
        return AdbConnectionManager.connect(context, AdbConnectionManager.DEFAULT_HOST, port)
            .fold(
                onSuccess = { true to context.getString(R.string.adb_connect_ok, it) },
                onFailure = { false to localizeError(context, it) }
            )
    }

    suspend fun tryAutoConnect(context: Context): Pair<Boolean, String> {
        return AdbConnectionManager.tryAutoConnect(context).fold(
            onSuccess = { true to it },
            onFailure = { false to localizeError(context, it) }
        )
    }

    suspend fun disconnect() {
        AdbConnectionManager.disconnect()
    }

    private fun localizeError(context: Context, error: Throwable): String {
        val msg = error.message.orEmpty()
        return when {
            msg.equals("not paired", ignoreCase = true) ->
                context.getString(R.string.shell_status_need_setup)
            msg.equals("debug port missing", ignoreCase = true) ->
                context.getString(R.string.adb_error_debug_port)
            msg.contains("Connection refused", ignoreCase = true) ||
                (msg.contains("debug port", ignoreCase = true) && msg.contains("enable", ignoreCase = true)) ->
                context.getString(R.string.adb_error_connection_refused)
            msg.contains("timeout", ignoreCase = true) ||
                msg.contains("expired", ignoreCase = true) ->
                context.getString(R.string.adb_error_timeout)
            msg.contains("Pairing failed", ignoreCase = true) ->
                context.getString(R.string.adb_error_pair_failed)
            msg.isNotBlank() -> msg
            else -> context.getString(R.string.adb_error_connection_refused)
        }
    }

    override fun isReady(): Boolean = AdbConnectionManager.isConnected()

    override fun run(command: String, timeoutSec: Long): ShellResult =
        runBlocking(Dispatchers.IO) {
            AdbConnectionManager.exec(command, timeoutSec)
        }
}
