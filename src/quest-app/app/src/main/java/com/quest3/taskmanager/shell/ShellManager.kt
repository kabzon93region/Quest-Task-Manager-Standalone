package com.quest3.taskmanager.shell

import android.content.Context
import com.quest3.taskmanager.FileLogger
import com.quest3.taskmanager.R
import com.quest3.taskmanager.RunningAppsProbe

object ShellManager {
    private val backends: List<ShellBackend> = listOf(AdbShellBackend)
    private val shellLock = Any()

    fun activeBackend(): ShellBackend? = backends.firstOrNull { it.isReady() }

    fun isReady(): Boolean = activeBackend() != null

    fun activeBackendId(): String = activeBackend()?.id ?: "none"

    fun run(command: String, timeoutSec: Long = 25): ShellResult = synchronized(shellLock) {
        val backend = activeBackend()
            ?: return ShellResult(-1, "", "shell not ready")
        backend.run(command, timeoutSec)
    }

    /** Завершение приложения или нативного демона. Успех = процесс реально исчез из ps. */
    fun killTarget(processName: String, appUid: Int? = null): Boolean {
        val safe = sanitizePackage(processName)
        if (safe.isBlank()) return false

        val isAppPackage = safe.contains('.') &&
            !RunningAppsProbe.isNativeProcessName(safe) &&
            (RunningAppsProbe.isLikelyPackageName(safe) || appUid != null)

        if (isAppPackage) {
            run("am force-stop '$safe'", timeoutSec = 8)
            run("am kill '$safe'", timeoutSec = 5)
            if (appUid != null) killPidsForUid(appUid, safe)
        }
        killByProcessName(safe)
        if (appUid != null) killPidsForUid(appUid, safe)

        val alive = isPackageRunning(safe, appUid)
        FileLogger.i("kill $safe uid=$appUid alive=$alive backend=${activeBackendId()}")
        return !alive
    }

    fun isPackageRunning(packageName: String, appUid: Int? = null): Boolean {
        val out = run("ps -A -o UID,NAME", timeoutSec = 6).combined
        for (line in out.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("UID")) continue
            val parts = trimmed.split(Regex("""\s+"""))
            if (parts.size < 2) continue
            val uid = parts[0].toIntOrNull()
            val name = parts.subList(1, parts.size).joinToString(" ")
            if (name == packageName || name.contains(packageName)) return true
            if (RunningAppsProbe.normalizePackageName(name) == packageName) return true
            if (appUid != null && uid == appUid) return true
        }
        return false
    }

    private fun killPidsForUid(uid: Int, label: String): Boolean {
        val pids = findPidsForUid(uid)
        if (pids.isEmpty()) return false
        var any = false
        for (pid in pids) {
            val result = run("kill -9 $pid", timeoutSec = 5)
            if (result.exitCode == 0) any = true
        }
        FileLogger.i("kill uid=$uid $label pids=$pids ok=$any")
        return any
    }

    private fun findPidsForUid(uid: Int): List<Int> {
        val out = run("ps -A -o PID,UID", timeoutSec = 6).combined
        val pids = mutableListOf<Int>()
        for (line in out.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("PID")) continue
            val parts = trimmed.split(Regex("""\s+"""))
            if (parts.size < 2) continue
            val pid = parts[0].toIntOrNull() ?: continue
            val lineUid = parts[1].toIntOrNull() ?: continue
            if (lineUid == uid) pids.add(pid)
        }
        return pids.distinct()
    }

    private fun killByProcessName(name: String): Boolean {
        val pids = findPids(name)
        if (pids.isEmpty()) return false
        var any = false
        for (pid in pids) {
            val result = run("kill -9 $pid", timeoutSec = 5)
            if (result.exitCode == 0) any = true
        }
        FileLogger.i("kill -9 $name pids=$pids ok=$any")
        return any
    }

    private fun findPids(processName: String): List<Int> {
        val out = run("ps -A -o PID,NAME", timeoutSec = 6).combined
        val pids = mutableListOf<Int>()
        for (line in out.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("PID")) continue
            val parts = trimmed.split(Regex("""\s+"""))
            if (parts.size < 2) continue
            val pid = parts[0].toIntOrNull() ?: continue
            val rawName = parts.subList(1, parts.size).joinToString(" ")
            val normalized = RunningAppsProbe.normalizePackageName(rawName)
            if (normalized == processName || rawName == processName || rawName.contains(processName)) {
                pids.add(pid)
            }
        }
        return pids.distinct()
    }

    private fun sanitizePackage(packageName: String): String =
        packageName.replace("'", "").replace(";", "").trim()

    fun statusMessage(context: Context): String {
        if (ShellWatchdog.isReconnecting) {
            return context.getString(R.string.shell_status_adb_reconnecting)
        }
        if (AdbShellBackend.isReady()) {
            return context.getString(R.string.shell_status_adb_ok)
        }
        if (ShellWatchdog.lastReconnectFailed) {
            val err = ShellWatchdog.lastError().orEmpty()
            return if (isDebugOffError(err)) {
                context.getString(R.string.shell_status_adb_debug_off)
            } else {
                context.getString(R.string.shell_status_adb_lost)
            }
        }
        if (AdbShellBackend.isPaired(context)) {
            return context.getString(R.string.shell_status_adb_need_connect)
        }
        return context.getString(R.string.shell_status_need_setup)
    }

    private fun isDebugOffError(message: String): Boolean =
        message.contains("Connection refused", ignoreCase = true) ||
            message.contains("Wireless debugging", ignoreCase = true) ||
            message.contains("debug port", ignoreCase = true)
}
