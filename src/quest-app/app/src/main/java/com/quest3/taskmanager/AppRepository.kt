package com.quest3.taskmanager

import android.content.Context
import com.quest3.taskmanager.shell.ShellManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val policy = ProtectedAppsPolicy(context, context.packageName)
    private val ownPackage = context.packageName

    suspend fun loadRunningEntries(
        snapshot: RunningSnapshot? = null,
        loadDisk: Boolean = false
    ): List<AppEntry> = withContext(Dispatchers.IO) {
        requireShell()
        val snap = snapshot ?: RunningSnapshotHolder.getOrCollect(context, forceRefresh = true)
        val installed = installedPackageNames()
        val display = snap.displayPackages
        val packages = display
            .filter { it in installed && !RunningAppsProbe.isNativeProcessName(it) }
            .toSet()
        val daemonNames = RunningAppsProbe.collectDaemonNames(snap, installed)
        val runningWithRam = packages.count { (snap.ramMap.byPackage[it] ?: 0) > 0 }
        val filteredOut = display - packages
        FileLogger.d(
            "running filter: display=${display.size} apps=${packages.size} " +
                "filtered=${filteredOut.size} daemons=${daemonNames.size} withRam=$runningWithRam"
        )
        if (filteredOut.isNotEmpty()) {
            FileLogger.d("running filter dropped: ${filteredOut.sorted().take(15).joinToString()}")
        }
        if (packages.isNotEmpty()) {
            FileLogger.d("running apps: ${packages.sorted().take(25).joinToString()}")
        }
        val disk = if (loadDisk) StorageProbe.loadDiskSizes(packages) else emptyMap()
        val appEntries = buildEntries(
            packageNames = packages,
            snapshot = snap,
            disk = disk,
            includePolicies = false,
            policyCtx = null
        )
        val daemonEntries = buildDaemonEntries(
            names = daemonNames,
            snapshot = snap,
            includePolicies = false,
            policyCtx = null
        )
        mergeSorted(appEntries, daemonEntries)
    }

    suspend fun loadAllEntries(
        snapshot: RunningSnapshot? = null,
        loadDisk: Boolean = false,
        cachedDisk: Map<String, Long?> = emptyMap()
    ): List<AppEntry> = withContext(Dispatchers.IO) {
        requireShell()
        val snap = snapshot ?: RunningSnapshotHolder.getOrCollect(context, forceRefresh = true)
        val installed = installedPackageNames()
        val runningInSnapshot = snap.displayPackages.intersect(installed)
        FileLogger.d(
            "all apps: installed=${installed.size} runningInSnapshot=${runningInSnapshot.size}"
        )
        if (runningInSnapshot.isNotEmpty()) {
            FileLogger.d("all apps running: ${runningInSnapshot.sorted().take(25).joinToString()}")
        }
        val policyCtx = BackgroundPolicy.loadContext()
        val allPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
            .toSet()
        val daemonNames = RunningAppsProbe.collectDaemonNames(snap, installed)
        val disk = if (loadDisk) {
            StorageProbe.loadDiskSizes(allPackages)
        } else {
            cachedDisk.mapNotNull { (pkg, kb) -> kb?.let { pkg to it } }.toMap()
        }
        val appEntries = buildEntries(
            packageNames = allPackages,
            snapshot = snap,
            disk = disk,
            includePolicies = true,
            policyCtx = policyCtx
        )
        val daemonEntries = buildDaemonEntries(
            names = daemonNames,
            snapshot = snap,
            includePolicies = true,
            policyCtx = policyCtx
        )
        mergeSorted(appEntries, daemonEntries)
    }

    suspend fun killPackages(packages: Collection<String>): KillResult = withContext(Dispatchers.IO) {
        var killed = 0
        var skippedProtected = 0
        var failed = 0
        val killedPackages = mutableListOf<String>()
        for (pkg in orderedForKill(packages)) {
            if (pkg == ownPackage) {
                skippedProtected++
                FileLogger.w("kill skipped (self): $pkg")
                continue
            }
            if (policy.isKillProtected(pkg)) {
                skippedProtected++
                FileLogger.w("kill skipped (protected): $pkg")
                continue
            }
            val uid = packageUid(pkg)
            if (ShellManager.killTarget(pkg, uid)) {
                killed++
                killedPackages.add(pkg)
            } else {
                failed++
            }
        }
        FileLogger.i("kill done: killed=$killed failed=$failed skipped=$skippedProtected")
        KillResult(killed, skippedProtected, failed, killedPackages)
    }

    suspend fun killByRules(candidates: Collection<String>): KillResult = withContext(Dispatchers.IO) {
        val targets = candidates.filter {
            it != ownPackage &&
                !policy.isKillProtected(it) &&
                BackgroundPolicy.isRunInBackgroundBlocked(it)
        }
        FileLogger.i("kill by rules: candidates=${candidates.size} targets=${targets.size}")
        if (targets.isNotEmpty()) {
            FileLogger.d("kill by rules targets: ${targets.sorted().take(20).joinToString()}")
        }
        killPackages(targets)
    }

    fun orderedForKill(packages: Collection<String>): List<String> {
        val (self, others) = packages.distinct().partition { it == ownPackage }
        return others + self
    }

    fun isKillProtected(packageName: String): Boolean = policy.isKillProtected(packageName)

    private fun buildEntries(
        packageNames: Set<String>,
        snapshot: RunningSnapshot,
        disk: Map<String, Long>,
        includePolicies: Boolean,
        policyCtx: PolicyContext?
    ): List<AppEntry> {
        return packageNames
            .mapNotNull { pkg ->
                toEntry(pkg, snapshot, disk, includePolicies, policyCtx)
            }
    }

    private fun buildDaemonEntries(
        names: Set<String>,
        snapshot: RunningSnapshot,
        includePolicies: Boolean,
        policyCtx: PolicyContext?
    ): List<AppEntry> {
        return names.mapNotNull { name ->
            toDaemonEntry(name, snapshot, includePolicies, policyCtx)
        }
    }

    private fun mergeSorted(apps: List<AppEntry>, daemons: List<AppEntry>): List<AppEntry> =
        (apps + daemons).sortedWith(
            compareByDescending<AppEntry> { it.processState != ProcessState.NONE }
                .thenByDescending { it.ramUsageKb ?: 0 }
                .thenBy { it.label.lowercase() }
        )

    private fun toEntry(
        packageName: String,
        snapshot: RunningSnapshot,
        disk: Map<String, Long>,
        includePolicies: Boolean,
        policyCtx: PolicyContext?
    ): AppEntry? {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            val processState = snapshot.processState(packageName)
            val ramKb = snapshot.ramMap.byPackage[packageName]
            val isSystem = classifyIsSystem(packageName, appInfo)

            val runAllowed = if (includePolicies && policyCtx != null) {
                !BackgroundPolicy.isRunInBackgroundBlocked(packageName)
            } else null

            val dataAllowed = if (includePolicies && policyCtx != null) {
                !BackgroundPolicy.isBackgroundDataBlocked(packageName, policyCtx)
            } else null

            AppEntry(
                packageName = packageName,
                label = label,
                isSystem = isSystem,
                isDaemon = false,
                processState = effectiveProcessState(processState, ramKb),
                diskSizeKb = disk[packageName],
                ramUsageKb = effectiveRamKb(processState, ramKb),
                runInBackgroundAllowed = runAllowed,
                backgroundDataAllowed = dataAllowed,
                icon = icon
            )
        } catch (_: PackageManager.NameNotFoundException) {
            buildUnknownEntry(packageName, snapshot, disk)
        }
    }

    private fun toDaemonEntry(
        name: String,
        snapshot: RunningSnapshot,
        includePolicies: Boolean,
        policyCtx: PolicyContext?
    ): AppEntry? {
        val processState = snapshot.daemonProcessState(name)
        if (processState == ProcessState.NONE) return null
        val ramKb = snapshot.extendedRam[name] ?: 0L
        val appInfo = try {
            pm.getApplicationInfo(name, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: name
        val icon = appInfo?.let { pm.getApplicationIcon(it) } ?: pm.getDefaultActivityIcon()
        val policies = resolvePolicies(name, appInfo != null, includePolicies, policyCtx)

        return AppEntry(
            packageName = name,
            label = label,
            isSystem = true,
            isDaemon = true,
            processState = processState,
            diskSizeKb = null,
            ramUsageKb = ramKb,
            runInBackgroundAllowed = policies.first,
            backgroundDataAllowed = policies.second,
            icon = icon
        )
    }

    private fun resolvePolicies(
        name: String,
        hasAppInfo: Boolean,
        includePolicies: Boolean,
        policyCtx: PolicyContext?
    ): Pair<Boolean?, Boolean?> {
        if (!includePolicies || policyCtx == null) return null to null
        if (!hasAppInfo && RunningAppsProbe.isNativeProcessName(name)) return null to null
        return try {
            !BackgroundPolicy.isRunInBackgroundBlocked(name) to
                !BackgroundPolicy.isBackgroundDataBlocked(name, policyCtx)
        } catch (_: Exception) {
            null to null
        }
    }

    private fun buildUnknownEntry(
        packageName: String,
        snapshot: RunningSnapshot,
        disk: Map<String, Long>
    ): AppEntry? {
        if (!RunningAppsProbe.isLikelyPackageName(packageName)) return null
        val processState = snapshot.processState(packageName)
        val ramKb = snapshot.ramMap.byPackage[packageName]
        return AppEntry(
            packageName = packageName,
            label = packageName,
            isSystem = classifyIsSystem(packageName, null),
            isDaemon = false,
            processState = effectiveProcessState(processState, ramKb),
            diskSizeKb = disk[packageName],
            ramUsageKb = effectiveRamKb(processState, ramKb),
            runInBackgroundAllowed = null,
            backgroundDataAllowed = null,
            icon = pm.getDefaultActivityIcon()
        )
    }

    private fun classifyIsSystem(packageName: String, appInfo: ApplicationInfo?): Boolean {
        val systemFlag = when {
            appInfo != null -> isSystemApp(appInfo) || appInfo.uid < Process.FIRST_APPLICATION_UID
            else -> try {
                pm.getPackageUid(packageName, 0) < Process.FIRST_APPLICATION_UID
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        return policy.isSystemForFilter(packageName, systemFlag)
    }

    private fun installedPackageNames(): Set<String> =
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
            .toSet()

    private fun packageUid(packageName: String): Int? =
        try {
            pm.getPackageUid(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean =
        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun effectiveProcessState(state: ProcessState, ramKb: Long?): ProcessState {
        if (state != ProcessState.NONE) return state
        return if ((ramKb ?: 0) > 0) ProcessState.CACHED else ProcessState.NONE
    }

    private fun effectiveRamKb(state: ProcessState, ramKb: Long?): Long? {
        val kb = ramKb ?: 0L
        return if (state != ProcessState.NONE || kb > 0) kb else null
    }

    private fun requireShell() {
        check(ShellManager.isReady()) {
            "Shell not ready (configure Wireless ADB)"
        }
    }
}