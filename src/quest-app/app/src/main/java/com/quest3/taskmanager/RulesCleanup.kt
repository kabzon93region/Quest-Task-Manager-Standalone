package com.quest3.taskmanager

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.runBlocking

class RulesCleanup(private val context: Context) {
    private val pm = context.packageManager

    fun run(): CleanResult {
        FileLogger.i("=== rules cleanup start ===")
        val repository = AppRepository(context)
        val snapshot = RunningAppsProbe.collectRunningSnapshot(context, fast = true)
        val installed = installedPackageNames()
        val candidates = collectRunningInstalled(snapshot, installed)

        val blocked = candidates.filter { BackgroundPolicy.isRunInBackgroundBlocked(it) }
        FileLogger.i(
            "cleanup: running=${candidates.size} blocked=${blocked.size}" +
                if (blocked.isEmpty()) "" else " targets=${blocked.take(15).joinToString()}"
        )

        val result = runBlocking {
            repository.killByRules(candidates)
        }

        FileLogger.i(
            "=== rules cleanup done killed=${result.killed} " +
                "failed=${result.failed} skipped=${result.skippedProtected} ==="
        )
        return CleanResult(
            scannedCount = candidates.size,
            targetCount = blocked.size,
            killedCount = result.killed,
            skippedAllowedCount = candidates.size - blocked.size
        )
    }

    private fun installedPackageNames(): Set<String> =
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
            .toSet()

    /** Все установленные процессы из ps/meminfo — как при ручном «По правилам» на вкладке «Запущенные». */
    private fun collectRunningInstalled(snapshot: RunningSnapshot, installed: Set<String>): Set<String> =
        (snapshot.displayPackages +
            snapshot.psActiveNames +
            snapshot.ramMap.byPackage.keys +
            snapshot.ramMap.cachedOnlyPackages +
            snapshot.ramMap.activePackages)
            .filter { pkg ->
                pkg in installed &&
                    !RunningAppsProbe.isNativeProcessName(pkg) &&
                    RunningAppsProbe.isLikelyPackageName(pkg)
            }
            .toSet()
}

data class CleanResult(
    val scannedCount: Int,
    val targetCount: Int,
    val killedCount: Int,
    val skippedAllowedCount: Int = 0
)
