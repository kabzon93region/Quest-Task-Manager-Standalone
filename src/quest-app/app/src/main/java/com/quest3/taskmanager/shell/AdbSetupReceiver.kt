package com.quest3.taskmanager.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.quest3.taskmanager.FileLogger
import com.quest3.taskmanager.R
import com.quest3.taskmanager.shell.adb.AdbConnectionManager
import kotlinx.coroutines.runBlocking

class AdbSetupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                handleIntent(context, intent)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun handleIntent(context: Context, intent: Intent) {
        when (intent.action) {
            AdbSetupNotification.ACTION_PAIR -> handlePair(context, intent)
            AdbSetupNotification.ACTION_CONNECT -> handleConnect(context, intent)
        }
    }

    private fun handlePair(context: Context, intent: Intent) {
        val input = getRemoteInputText(intent) ?: return
        val parts = input.trim().split(Regex("""\s+"""))
        if (parts.size < 2) {
            showToast(context, context.getString(R.string.adb_notif_pair_error, "формат: порт код"))
            return
        }
        val port = parts[0].toIntOrNull()
        val code = parts[1]
        if (port == null || port <= 0 || code.isBlank()) {
            showToast(context, context.getString(R.string.adb_notif_pair_error, "неверный формат"))
            return
        }
        FileLogger.i("adb notif pair: port=$port")
        AdbShellBackend.savePairing(context, port.toString(), code)
        val result = runBlocking { AdbConnectionManager.pair(context, AdbConnectionManager.DEFAULT_HOST, port, code) }
        result.fold(
            onSuccess = {
                FileLogger.i("adb notif pair ok")
                showToast(context, context.getString(R.string.adb_notif_pair_success))
                AdbSetupNotification.showConnectNeeded(context)
            },
            onFailure = { e ->
                val msg = e.message.orEmpty()
                FileLogger.w("adb notif pair failed: $msg")
                showToast(context, context.getString(R.string.adb_notif_pair_error, msg))
            }
        )
    }

    private fun handleConnect(context: Context, intent: Intent) {
        val input = getRemoteInputText(intent) ?: return
        val port = input.trim().toIntOrNull()
        if (port == null || port <= 0) {
            showToast(context, context.getString(R.string.adb_notif_connect_error, "неверный порт"))
            return
        }
        FileLogger.i("adb notif connect: port=$port")
        AdbShellBackend.saveDebugPort(context, port.toString())
        val result = runBlocking { AdbConnectionManager.connect(context, AdbConnectionManager.DEFAULT_HOST, port) }
        result.fold(
            onSuccess = { uid ->
                FileLogger.i("adb notif connect ok: $uid")
                showToast(context, context.getString(R.string.adb_notif_connect_success, uid))
                AdbSetupNotification.dismiss(context)
            },
            onFailure = { e ->
                val msg = e.message.orEmpty()
                FileLogger.w("adb notif connect failed: $msg")
                showToast(context, context.getString(R.string.adb_notif_connect_error, msg))
            }
        )
    }

    private fun getRemoteInputText(intent: Intent): String? {
        val bundle = RemoteInput.getResultsFromIntent(intent) ?: return null
        return bundle.getCharSequence(AdbSetupNotification.EXTRA_REMOTE_INPUT_RESULT)?.toString()
    }

    private fun showToast(context: Context, message: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
