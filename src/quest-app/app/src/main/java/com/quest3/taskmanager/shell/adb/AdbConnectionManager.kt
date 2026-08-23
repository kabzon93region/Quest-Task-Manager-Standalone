package com.quest3.taskmanager.shell.adb

import android.content.Context
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.shell.AdbShellResponse
import com.quest3.taskmanager.AppSettings
import com.quest3.taskmanager.FileLogger
import com.quest3.taskmanager.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException

object AdbConnectionManager {
    private val mutex = Mutex()
    private var kadb: Kadb? = null

    fun isConnected(): Boolean = kadb != null

    suspend fun healthCheck(): Boolean {
        if (!isConnected()) return false
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                healthCheckLocked()
            }
        }
    }

    suspend fun pair(context: Context, host: String, pairPort: Int, code: String): Result<Unit> {
        AdbKeyStore.init(context)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    Kadb.pair(host, pairPort, code)
                    AppSettings.prefs(context).edit()
                        .putBoolean(AppSettings.KEY_ADB_PAIRED, true)
                        .remove(AppSettings.KEY_ADB_PAIR_CODE)
                        .apply()
                    FileLogger.i("adb pair ok $host:$pairPort")
                    Result.success(Unit)
                } catch (e: Exception) {
                    FileLogger.e("adb pair failed", e)
                    Result.failure(mapError(e))
                }
            }
        }
    }

    suspend fun connect(context: Context, host: String, debugPort: Int): Result<String> {
        AdbKeyStore.init(context)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                connectLocked(context, host, debugPort)
            }
        }
    }

    suspend fun tryAutoConnect(context: Context): Result<String> {
        val prefs = AppSettings.prefs(context)
        if (!prefs.getBoolean(AppSettings.KEY_ADB_PAIRED, false)) {
            return Result.failure(Exception("not paired"))
        }
        val port = prefs.getString(AppSettings.KEY_ADB_DEBUG_PORT, "").orEmpty().toIntOrNull()
            ?: return Result.failure(Exception("debug port missing"))

        if (isConnected() && healthCheck()) {
            return Result.success("already connected")
        }
        if (isConnected()) {
            disconnect()
        }
        return connect(context, DEFAULT_HOST, port)
    }

    suspend fun disconnect() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                disconnectInternal()
            }
        }
    }

    suspend fun exec(command: String, timeoutSec: Long = 25): ShellResult {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val client = kadb
                    ?: return@withContext ShellResult(-1, "", "adb not connected")
                try {
                    withTimeout(timeoutSec * 1000) {
                        val response = client.shell(command)
                        ShellResult(
                            exitCode = response.exitCode,
                            stdout = shellText(response),
                            stderr = response.errorOutput.orEmpty()
                        )
                    }
                } catch (_: TimeoutCancellationException) {
                    ShellResult(-1, "", "timeout")
                } catch (e: Exception) {
                    if (isTransportError(e)) {
                        FileLogger.w("adb exec transport error, disconnecting: ${e.message}")
                        disconnectInternal()
                    }
                    ShellResult(-1, "", e.message.orEmpty())
                }
            }
        }
    }

    private suspend fun connectLocked(context: Context, host: String, debugPort: Int): Result<String> {
        return try {
            disconnectInternal()
            val instance = Kadb.create(host, debugPort)
            val idResult = instance.shell("id")
            val output = shellText(idResult)
            if (idResult.exitCode != 0 || !output.contains("uid=2000")) {
                closeQuietly(instance)
                return Result.failure(
                    Exception("Connection refused — enable Wireless debugging and check debug port")
                )
            }
            kadb = instance
            AppSettings.prefs(context).edit()
                .putString(AppSettings.KEY_ADB_DEBUG_PORT, debugPort.toString())
                .apply()
            FileLogger.i("adb connect ok $host:$debugPort")
            Result.success(output.trim())
        } catch (e: Exception) {
            FileLogger.e("adb connect failed", e)
            Result.failure(mapError(e))
        }
    }

    private fun healthCheckLocked(): Boolean {
        val client = kadb ?: return false
        return try {
            val response = client.shell("id")
            response.exitCode == 0 && shellText(response).contains("uid=2000")
        } catch (e: Exception) {
            FileLogger.w("adb health check failed: ${e.message}")
            disconnectInternal()
            false
        }
    }

    private fun disconnectInternal() {
        kadb?.let { closeQuietly(it) }
        kadb = null
    }

    private fun closeQuietly(client: Kadb) {
        try {
            client.close()
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun shellText(response: AdbShellResponse): String = response.output.orEmpty()

    private fun isTransportError(e: Exception): Boolean {
        if (e is ConnectException || e is SocketException || e is SocketTimeoutException || e is IOException) {
            return true
        }
        val msg = e.message.orEmpty()
        return msg.contains("Connection", ignoreCase = true) ||
            msg.contains("broken pipe", ignoreCase = true) ||
            msg.contains("reset", ignoreCase = true) ||
            msg.contains("closed", ignoreCase = true)
    }

    private fun mapError(e: Exception): Exception {
        val msg = e.message.orEmpty()
        return when {
            e is ConnectException || msg.contains("Connection refused", ignoreCase = true) ->
                Exception("Connection refused — enable Wireless debugging and check debug port")
            e is SocketTimeoutException || msg.contains("timeout", ignoreCase = true) ->
                Exception("Timeout — pairing code may have expired")
            msg.contains("pair", ignoreCase = true) && msg.contains("fail", ignoreCase = true) ->
                Exception("Pairing failed — check code and pairing port")
            else -> e
        }
    }

    const val DEFAULT_HOST = "127.0.0.1"
}
