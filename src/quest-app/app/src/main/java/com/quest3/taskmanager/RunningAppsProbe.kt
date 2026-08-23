package com.quest3.taskmanager

import android.content.Context
import com.quest3.taskmanager.shell.ShellManager

data class RunningSnapshot(
    val psPackages: Set<String>,
    val psActiveNames: Set<String>,
    val activityPackages: Set<String>,
    val procPackages: Set<String>,
    val uidPackages: Set<String>,
    val meminfoPackages: Set<String>,
    val ramMap: MemoryProbe.RamMap,
    val extendedRam: Map<String, Long>
) {
    private val runningPackages: Set<String>
        get() = psPackages + activityPackages + procPackages + uidPackages +
            meminfoPackages + ramMap.activePackages

    val displayPackages: Set<String>
        get() = runningPackages
            .filter { !RunningAppsProbe.isNativeProcessName(it) }
            .toSet()

    fun processState(pkg: String): ProcessState = when {
        pkg in runningPackages -> ProcessState.ACTIVE
        pkg in ramMap.cachedOnlyPackages -> ProcessState.CACHED
        (ramMap.byPackage[pkg] ?: 0) > 0 -> ProcessState.CACHED
        else -> ProcessState.NONE
    }

    fun daemonProcessState(name: String): ProcessState = when {
        name in psActiveNames -> ProcessState.ACTIVE
        (extendedRam[name] ?: 0) > 0 -> ProcessState.CACHED
        else -> ProcessState.NONE
    }
}

object RunningAppsProbe {
    private const val MEMINFO_GAP_LIMIT = 36

    private val processRecordPkg = Regex("""ProcessRecord\{[^}]*\s+\d+:\s*([a-zA-Z][a-zA-Z0-9_.]+)""")
    private val pkgFromPsArgs = Regex("""\b([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]+)+)\b""")
    private val packageAttrRegex = Regex("""(?:packageName|package|process)=([a-zA-Z][a-zA-Z0-9_.]+)""")
    private val activityComponentRegex = Regex("""\b([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]+)+)/[.\w]+""")
    private val looseComponentRegex = Regex(
        """(?:mResumedActivity|mLastPausedActivity|topResumedActivity|realActivity|baseActivity|origActivity)[^=]*=\s*[^\s]*\s+([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]+)+)"""
    )

    private val PS_SKIP = setOf("sh", "shizuku", "shizuku_server", "[sh]", "logd", "lmkd", "servicemanager")

    private const val MARKER_PS = "---QTM_PS---"
    private const val MARKER_UID = "---QTM_UID---"
    private const val MARKER_ACT = "---QTM_ACT---"

    private data class FastBatch(val psOut: String, val uidOut: String, val actOut: String)

    fun collectRunningSnapshot(context: Context, fast: Boolean = true): RunningSnapshot {
        val t0 = System.currentTimeMillis()
        val installed = PackageListProbe.installed(context)
        val userPackages = PackageListProbe.userInstalled(context)
        FileLogger.probe("running probe: installed=${installed.size} user=${userPackages.size} fast=$fast")

        val psOut: String
        val psPackages: Set<String>
        val psActiveNames: Set<String>
        val dumpsysPackages: Set<String>
        val activityPackages: Set<String>
        val proc: ProcFsProbe.Snapshot
        val uid: UidRunningProbe.Snapshot
        var tPs = 0L
        var tDumpsys = 0L
        var tActivity = 0L
        var tProc = 0L
        var tUid = 0L
        var tBatch = 0L

        if (fast) {
            var t = System.currentTimeMillis()
            val batch = collectFastBatch()
            tBatch = System.currentTimeMillis() - t
            psOut = batch.psOut
            psPackages = parsePsPackages(psOut, installed)
            psActiveNames = parsePsAllNames(psOut)
            activityPackages = extractPackages(batch.actOut, installed)
            proc = ProcFsProbe.Snapshot(emptySet(), emptyMap())
            uid = UidRunningProbe.collect(context, psOut = psOut, procUidOut = batch.uidOut)
            dumpsysPackages = emptySet()
        } else {
            var t = System.currentTimeMillis()
            psOut = ShellManager.run("ps -A -o UID,RSS,NAME", timeoutSec = 8).combined
            psPackages = parsePsPackages(psOut, installed)
            psActiveNames = parsePsAllNames(psOut)
            tPs = System.currentTimeMillis() - t

            t = System.currentTimeMillis()
            dumpsysPackages = collectDumpsysProcessPackages(installed)
            tDumpsys = System.currentTimeMillis() - t

            t = System.currentTimeMillis()
            activityPackages = collectActivityPackages(installed)
            tActivity = System.currentTimeMillis() - t

            t = System.currentTimeMillis()
            proc = ProcFsProbe.collect(installed, timeoutSec = 30)
            tProc = System.currentTimeMillis() - t

            t = System.currentTimeMillis()
            uid = UidRunningProbe.collect(context, psOut = psOut, scanProc = true, procTimeoutSec = 30)
            tUid = System.currentTimeMillis() - t
        }

        var t = System.currentTimeMillis()
        val fastFound = psPackages + dumpsysPackages + activityPackages + proc.packages + uid.packages
        val meminfo = if (fast) {
            MeminfoPackageProbe.Snapshot(emptySet(), emptyMap())
        } else {
            val meminfoTargets = (userPackages - fastFound).take(MEMINFO_GAP_LIMIT)
            MeminfoPackageProbe.collect(meminfoTargets)
        }
        val tMeminfo = System.currentTimeMillis() - t

        val mergedRam = mutableMapOf<String, Long>()
        mergeRam(mergedRam, proc.ramByPackageKb)
        mergeRam(mergedRam, uid.ramByPackageKb)
        mergeRam(mergedRam, meminfo.ramByPackageKb)
        mergeRam(mergedRam, parsePsRam(psOut, installed))

        val mergedPs = fastFound + meminfo.packages
        t = System.currentTimeMillis()
        val ramMap = if (fast) {
            MemoryProbe.buildFastRamMap(mergedPs, mergedRam)
        } else {
            val meminfoDump = ShellManager.run("dumpsys meminfo", timeoutSec = 45).combined
            MemoryProbe.loadRamMap(mergedPs, mergedRam, meminfoDump, psOut)
        }
        val tRam = System.currentTimeMillis() - t
        val extendedRam = if (fast) {
            MemoryProbe.extendedRamFromPs(psOut)
        } else {
            val meminfoDump = ShellManager.run("dumpsys meminfo", timeoutSec = 45).combined
            MemoryProbe.loadExtendedProcessRam(meminfoDump, psOut)
        }

        val snapshot = RunningSnapshot(
            mergedPs, psActiveNames, activityPackages, proc.packages,
            uid.packages, meminfo.packages, ramMap, extendedRam
        )
        val elapsed = System.currentTimeMillis() - t0
        val display = snapshot.displayPackages
        val overlayCandidates = (proc.packages + uid.packages + meminfo.packages) - psPackages
        FileLogger.i(
            "running: ps=${psPackages.size} dumpsys=${dumpsysPackages.size} activity=${activityPackages.size} " +
                "proc=${proc.packages.size} uid=${uid.packages.size} meminfo=${meminfo.packages.size} " +
                "display=${display.size} ram=${ramMap.byPackage.size} overlay=${overlayCandidates.size} ${elapsed}ms"
        )
        FileLogger.probe(
            "running timing: batch=${tBatch}ms ps=${tPs}ms dumpsys=${tDumpsys}ms activity=${tActivity}ms " +
                "proc=${tProc}ms uid=${tUid}ms meminfo=${tMeminfo}ms ram=${tRam}ms"
        )
        logPackageSample("running ps", psPackages)
        logPackageSample("running uid", uid.packages)
        logPackageSample("running display", display)
        if (overlayCandidates.isNotEmpty()) {
            logPackageSample("running overlay", overlayCandidates)
        }
        logUserPackageDiagnostics(
            userPackages, installed, display,
            psPackages, proc.packages, uid.packages, meminfo.packages,
            activityPackages, dumpsysPackages
        )
        return snapshot
    }

    private fun parsePsPackages(text: String, installed: Set<String>): Set<String> {
        if (text.isBlank()) return emptySet()
        return parsePs(text, appPackagesOnly = true, installed = installed)
    }

    private fun parsePsAllNames(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        return parsePs(text, appPackagesOnly = false, installed = emptySet())
    }

    private fun parsePsRam(text: String, installed: Set<String>): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("UID")) continue
            val parts = trimmed.split(Regex("""\s+"""))
            if (parts.size < 3) continue
            val pkg = normalizePackageName(parts.subList(2, parts.size).joinToString(" "))
            if (!isRecognizedPackage(pkg, installed)) continue
            val rss = parts[1].toLongOrNull() ?: continue
            if (rss > 0) result[pkg] = (result[pkg] ?: 0L) + rss
        }
        return result
    }

    private fun collectFastBatch(): FastBatch {
        val script = """
            echo '$MARKER_PS'
            ps -A -o UID,RSS,NAME
            echo '$MARKER_UID'
            ${UidRunningProbe.procUidScript}
            echo '$MARKER_ACT'
            dumpsys activity activities
        """.trimIndent()
        val combined = ShellManager.run(script, timeoutSec = 12).combined
        return FastBatch(
            psOut = sliceBetween(combined, MARKER_PS, MARKER_UID),
            uidOut = sliceBetween(combined, MARKER_UID, MARKER_ACT),
            actOut = combined.substringAfter(MARKER_ACT).trimStart('\n', '\r')
        )
    }

    private fun sliceBetween(text: String, start: String, end: String): String {
        val from = text.indexOf(start)
        if (from < 0) return ""
        val bodyStart = from + start.length
        val to = text.indexOf(end, bodyStart)
        if (to < 0) return text.substring(bodyStart).trim()
        return text.substring(bodyStart, to).trim('\n', '\r')
    }

    private fun collectActivityPackagesFast(installed: Set<String>): Set<String> {
        val activities = ShellManager.run("dumpsys activity activities", timeoutSec = 12).combined
        return extractPackages(activities, installed)
    }

    private fun logUserPackageDiagnostics(
        userPackages: Set<String>,
        installed: Set<String>,
        display: Set<String>,
        ps: Set<String>,
        proc: Set<String>,
        uid: Set<String>,
        meminfo: Set<String>,
        activity: Set<String>,
        dumpsys: Set<String>
    ) {
        val allDetected = ps + proc + uid + meminfo + activity + dumpsys
        val userRunning = userPackages.intersect(allDetected)
        FileLogger.probe("running user-apps: detected=${userRunning.size}/${userPackages.size}")
        if (userRunning.isEmpty()) return

        val lines = userRunning.sorted().take(25).map { pkg ->
            val sources = buildList {
                if (pkg in ps) add("ps")
                if (pkg in proc) add("proc")
                if (pkg in uid) add("uid")
                if (pkg in meminfo) add("meminfo")
                if (pkg in activity) add("act")
                if (pkg in dumpsys) add("dsys")
                if (pkg !in display) add("!display")
            }
            "$pkg[${sources.joinToString("+")}]"
        }
        FileLogger.probe("running user detail: ${lines.joinToString("; ")}")

        val hidden = userRunning.filter { it !in display }
        if (hidden.isNotEmpty()) {
            FileLogger.w("running user hidden from display: ${hidden.sorted().joinToString()}")
        }
    }

    private fun logPackageSample(label: String, packages: Collection<String>) {
        if (packages.isEmpty()) return
        FileLogger.probe("$label (${packages.size}): ${packages.sorted().take(20).joinToString()}")
    }

    private fun mergeRam(target: MutableMap<String, Long>, source: Map<String, Long>) {
        source.forEach { (pkg, kb) ->
            if (kb > 0) target[pkg] = maxOf(target[pkg] ?: 0, kb)
        }
    }

    fun collectDaemonNames(snapshot: RunningSnapshot, installed: Set<String>): Set<String> {
        val candidates = snapshot.psActiveNames +
            snapshot.extendedRam.filter { (_, kb) -> kb > 0 }.keys
        return candidates
            .filter { isDaemonCandidate(it, installed) }
            .filter { snapshot.daemonProcessState(it) != ProcessState.NONE }
            .toSet()
    }

    fun isDaemonCandidate(name: String, installed: Set<String>): Boolean {
        if (name.isBlank() || name in PS_SKIP) return false
        if (name in installed && isRecognizedPackage(name, installed) && !isNativeProcessName(name)) return false
        return true
    }

    fun isRecognizedPackage(pkg: String, installed: Set<String>): Boolean =
        pkg in installed || isLikelyPackageName(pkg)

    private fun collectDumpsysProcessPackages(installed: Set<String>): Set<String> {
        val out = ShellManager.run("dumpsys activity processes", timeoutSec = 15).combined
        if (out.isBlank()) return emptySet()
        return extractPackages(out, installed)
    }

    private fun collectActivityPackages(installed: Set<String>): Set<String> {
        val activities = ShellManager.run("dumpsys activity activities", timeoutSec = 15).combined
        val recents = ShellManager.run("dumpsys activity recents", timeoutSec = 12).combined
        val oom = ShellManager.run("dumpsys activity oom", timeoutSec = 12).combined
        return extractPackages(activities, installed) +
            extractPackages(recents, installed) +
            extractPackages(oom, installed)
    }

    private fun extractPackages(text: String, installed: Set<String>): Set<String> {
        val result = linkedSetOf<String>()
        processRecordPkg.findAll(text).forEach { match ->
            addPackageCandidate(result, match.groupValues[1], installed)
        }
        packageAttrRegex.findAll(text).forEach { match ->
            addPackageCandidate(result, match.groupValues[1], installed)
        }
        activityComponentRegex.findAll(text).forEach { match ->
            addPackageCandidate(result, match.groupValues[1], installed)
        }
        looseComponentRegex.findAll(text).forEach { match ->
            addPackageCandidate(result, match.groupValues[1], installed)
        }
        return result
    }

    private fun addPackageCandidate(target: MutableSet<String>, raw: String, installed: Set<String>) {
        val pkg = normalizePackageName(raw)
        if (pkg.isBlank() || pkg in PS_SKIP) return
        if (isRecognizedPackage(pkg, installed)) target.add(pkg)
    }

    internal fun normalizePackageName(name: String): String {
        var current = name.trim().substringBefore("/").substringBefore(":")
        while (true) {
            val dot = current.lastIndexOf('.')
            if (dot <= 0) break
            val segment = current.substring(dot + 1)
            if (segment.isEmpty() || !isJavaClassSegment(segment)) break
            current = current.substring(0, dot)
        }
        return current
    }

    private fun isJavaClassSegment(segment: String): Boolean {
        if (segment.isEmpty() || !segment[0].isUpperCase()) return false
        val classSuffixes = listOf(
            "Activity", "Service", "Application", "Receiver", "Provider",
            "Fragment", "Impl", "Wrapper", "Delegate"
        )
        return classSuffixes.any { segment.endsWith(it) }
    }

    private val NATIVE_PROCESS_PREFIXES = listOf(
        "android.", "media.", "hidl.", "vendor.", "system.", "webview",
    )

    internal fun isNativeProcessName(name: String): Boolean {
        val base = name.substringBefore(":").substringBefore("/")
        if (!base.contains('.')) return true
        if (name.contains('@')) return true
        return NATIVE_PROCESS_PREFIXES.any { base.startsWith(it, ignoreCase = true) }
    }

    internal fun isLikelyPackageName(name: String): Boolean {
        val base = normalizePackageName(name)
        if (!base.contains('.')) return false
        if (isNativeProcessName(base)) return false
        val segments = base.split('.')
        if (segments.size < 2) return false
        return segments.all { s ->
            s.isNotEmpty() && s[0].isLetter() && s.all { c -> c.isLetterOrDigit() || c == '_' }
        }
    }

    private fun parsePs(text: String, appPackagesOnly: Boolean, installed: Set<String>): Set<String> {
        val result = linkedSetOf<String>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("UID") || trimmed.startsWith("NAME")) continue
            val parts = trimmed.split(Regex("""\s+"""))
            if (parts.size < 3) continue
            val namePart = parts.subList(2, parts.size).joinToString(" ")
            val candidate = normalizePackageName(namePart.split(Regex("""\s+""")).firstOrNull() ?: namePart)
            if (candidate.isBlank() || candidate in PS_SKIP) continue
            if (appPackagesOnly) {
                if (isRecognizedPackage(candidate, installed)) {
                    result.add(candidate)
                }
                pkgFromPsArgs.findAll(trimmed).forEach { match ->
                    val pkg = normalizePackageName(match.groupValues[1])
                    if (isRecognizedPackage(pkg, installed) && pkg !in PS_SKIP) result.add(pkg)
                }
            } else {
                result.add(candidate)
                pkgFromPsArgs.findAll(trimmed).forEach { match ->
                    val pkg = normalizePackageName(match.groupValues[1])
                    if (pkg.isNotBlank() && pkg !in PS_SKIP) result.add(pkg)
                }
            }
        }
        return result
    }
}
