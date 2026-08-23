package com.quest3.taskmanager

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.quest3.taskmanager.shell.ShellManager

/**
 * Maps running PIDs to packages via Linux UID (same signal Android Settings uses for "Stop").
 */
object UidRunningProbe {
    data class Snapshot(
        val packages: Set<String>,
        val ramByPackageKb: Map<String, Long>
    )

    /** Один awk по всем status-файлам в /proc — быстрее цикла for pid с двумя awk на каждый PID. */
    internal val procUidScript = """
        awk '/^Name:/{if(u>=10000&&r>0)print u,r;u=r=0}/^Uid:/{u=${'$'}2+0}/^VmRSS:/{r=${'$'}2+0} END{if(u>=10000&&r>0)print u,r}' /proc/[0-9]*/status 2>/dev/null
    """.trimIndent()

    private var uidToPkgCache: Map<Int, String>? = null
    private var uidToPkgCacheAt = 0L
    private const val UID_MAP_TTL_MS = 60_000L

    fun collect(
        context: Context,
        psOut: String? = null,
        scanProc: Boolean = false,
        procTimeoutSec: Long = 20,
        procUidOut: String? = null
    ): Snapshot {
        val uidToPkg = loadUidToPackage(context)
        if (uidToPkg.isEmpty()) return Snapshot(emptySet(), emptyMap())

        val ramByUid = mutableMapOf<Int, Long>()
        val activeUids = mutableSetOf<Int>()
        if (!psOut.isNullOrBlank()) {
            collectFromPsOutput(psOut, uidToPkg.keys, ramByUid, activeUids)
        } else {
            collectFromPs(uidToPkg.keys, ramByUid, activeUids)
        }
        when {
            !procUidOut.isNullOrBlank() ->
                parseProcUidOutput(procUidOut, uidToPkg.keys, ramByUid, activeUids)
            scanProc ->
                collectFromProcStatus(uidToPkg.keys, ramByUid, activeUids, procTimeoutSec)
        }

        val packages = linkedSetOf<String>()
        val ramByPackage = mutableMapOf<String, Long>()
        for (uid in activeUids) {
            val pkg = uidToPkg[uid] ?: continue
            packages.add(pkg)
            val rssKb = ramByUid[uid] ?: 0L
            if (rssKb > 0) ramByPackage[pkg] = (ramByPackage[pkg] ?: 0L) + rssKb
        }
        FileLogger.probe("uid-probe: uidMap=${uidToPkg.size} packages=${packages.size} uids=${ramByUid.size} proc=${scanProc || procUidOut != null}")
        return Snapshot(packages, ramByPackage)
    }

    private fun loadUidToPackage(context: Context): Map<Int, String> {
        val now = System.currentTimeMillis()
        val cached = uidToPkgCache
        if (cached != null && now - uidToPkgCacheAt < UID_MAP_TTL_MS) return cached

        val map = mutableMapOf<Int, String>()
        for (app in context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (app.uid < Process.FIRST_APPLICATION_UID) continue
            map.putIfAbsent(app.uid, app.packageName)
        }
        uidToPkgCache = map
        uidToPkgCacheAt = now
        return map
    }

    private fun collectFromPsOutput(
        psOut: String,
        appUids: Set<Int>,
        ramByUid: MutableMap<Int, Long>,
        activeUids: MutableSet<Int>
    ) {
        for (line in psOut.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("UID")) continue
            val parts = trimmed.split(Regex("""\s+"""))
            if (parts.size < 3) continue
            val uid = parts[0].toIntOrNull() ?: continue
            if (uid !in appUids) continue
            activeUids.add(uid)
            val rssKb = parts[1].toLongOrNull() ?: 0L
            if (rssKb > 0) ramByUid[uid] = (ramByUid[uid] ?: 0L) + rssKb
        }
    }

    private fun collectFromPs(
        appUids: Set<Int>,
        ramByUid: MutableMap<Int, Long>,
        activeUids: MutableSet<Int>
    ) {
        val out = ShellManager.run("ps -A -o UID,RSS,NAME", timeoutSec = 8).combined
        collectFromPsOutput(out, appUids, ramByUid, activeUids)
    }

    private fun collectFromProcStatus(
        appUids: Set<Int>,
        ramByUid: MutableMap<Int, Long>,
        activeUids: MutableSet<Int>,
        procTimeoutSec: Long
    ) {
        val out = ShellManager.run(procUidScript, timeoutSec = procTimeoutSec).combined
        parseProcUidOutput(out, appUids, ramByUid, activeUids)
    }

    private fun parseProcUidOutput(
        out: String,
        appUids: Set<Int>,
        ramByUid: MutableMap<Int, Long>,
        activeUids: MutableSet<Int>
    ) {
        for (line in out.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val parts = trimmed.split(Regex("""\s+"""))
            if (parts.size < 2) continue
            val uid = parts[0].toIntOrNull() ?: continue
            if (uid !in appUids) continue
            val rssKb = parts[1].toLongOrNull() ?: 0L
            if (rssKb <= 0) continue
            activeUids.add(uid)
            ramByUid[uid] = (ramByUid[uid] ?: 0L) + rssKb
        }
    }
}
