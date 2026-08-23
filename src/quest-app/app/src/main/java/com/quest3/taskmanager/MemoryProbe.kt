package com.quest3.taskmanager

import com.quest3.taskmanager.shell.ShellManager

object MemoryProbe {
    private val totalPssRegex = Regex("""TOTAL\s+(\d+)""")
    private val summaryPssRegex = Regex("""^\s*([\d,]+)\s*K:\s+([a-zA-Z][a-zA-Z0-9_.:]+)""")

    data class RamMap(
        val byPackage: Map<String, Long>,
        val activePackages: Set<String>,
        val cachedOnlyPackages: Set<String>
    )

    fun loadRamMap(
        psPackages: Set<String>,
        procRamKb: Map<String, Long> = emptyMap(),
        meminfoOutput: String? = null,
        psOutput: String? = null
    ): RamMap {
        val byPackage = mutableMapOf<String, Long>()
        if (!psOutput.isNullOrBlank()) {
            parsePsRssFromOutput(psOutput).forEach { (pkg, kb) ->
                byPackage[pkg] = (byPackage[pkg] ?: 0) + kb
            }
        } else {
            loadPsRss().forEach { (pkg, kb) -> byPackage[pkg] = (byPackage[pkg] ?: 0) + kb }
        }
        procRamKb.forEach { (pkg, kb) ->
            if (kb > 0) byPackage[pkg] = maxOf(byPackage[pkg] ?: 0, kb)
        }
        val meminfo = meminfoOutput ?: ShellManager.run("dumpsys meminfo", timeoutSec = 45).combined
        parseMeminfoDetails(meminfo).forEach { (pkg, kb) ->
            byPackage[pkg] = maxOf(byPackage[pkg] ?: 0, kb)
        }
        parseMeminfoSummary(meminfo).forEach { (pkg, kb) ->
            byPackage[pkg] = maxOf(byPackage[pkg] ?: 0, kb)
        }
        parseMeminfoSummaryAll(meminfo).forEach { (rawName, kb) ->
            val pkg = RunningAppsProbe.normalizePackageName(rawName)
            if (RunningAppsProbe.isLikelyPackageName(pkg)) {
                byPackage[pkg] = maxOf(byPackage[pkg] ?: 0, kb)
            }
        }

        val knownRunning = psPackages.toMutableSet()
        knownRunning.addAll(procRamKb.filter { it.value > 0 }.keys)

        val active = knownRunning.toMutableSet()
        active.addAll(byPackage.filter { (pkg, kb) -> kb > 0 && pkg in knownRunning }.keys)
        active.addAll(procRamKb.filter { it.value >= 32_768 }.keys)

        val cachedOnly = byPackage.filter { (pkg, kb) ->
            kb > 0 && pkg !in active
        }.keys

        return RamMap(byPackage, active, cachedOnly)
    }

    /** Быстрый RAM без dumpsys meminfo — только ps/uid/proc. */
    fun buildFastRamMap(psPackages: Set<String>, ramByPackage: Map<String, Long>): RamMap {
        val active = psPackages.toMutableSet()
        active.addAll(ramByPackage.filter { it.value > 0 }.keys)
        return RamMap(ramByPackage, active, emptySet())
    }

    fun extendedRamFromPs(psOutput: String): Map<String, Long> =
        parsePsRssFromOutput(psOutput, allNames = true)

    private fun parseMeminfoDetails(output: String): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        var currentPkg: String? = null
        var pss = 0L
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("** MEMINFO in pid")) {
                if (currentPkg != null && pss > 0) {
                    result[currentPkg] = (result[currentPkg] ?: 0) + pss
                }
                currentPkg = Regex("""\[(.+?)\]""").find(trimmed)?.groupValues?.get(1)
                    ?.let { RunningAppsProbe.normalizePackageName(it) }
                    ?.takeIf { RunningAppsProbe.isLikelyPackageName(it) }
                pss = 0
                continue
            }
            if (currentPkg != null && trimmed.startsWith("TOTAL")) {
                totalPssRegex.find(trimmed)?.groupValues?.get(1)?.toLongOrNull()?.let { pss = it }
            }
        }
        if (currentPkg != null && pss > 0) {
            result[currentPkg] = (result[currentPkg] ?: 0) + pss
        }
        return result
    }

    private fun parseMeminfoSummary(output: String): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        var inSummary = false
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Total PSS by process")) {
                inSummary = true
                continue
            }
            if (inSummary) {
                if (trimmed.isBlank() || trimmed.startsWith("Total PSS by OOM")) break
                val match = summaryPssRegex.find(trimmed) ?: continue
                val kb = match.groupValues[1].replace(",", "").toLongOrNull() ?: continue
                val pkg = RunningAppsProbe.normalizePackageName(match.groupValues[2])
                if (RunningAppsProbe.isLikelyPackageName(pkg)) {
                    result[pkg] = (result[pkg] ?: 0) + kb
                }
            }
        }
        return result
    }

    private fun loadPsRss(): Map<String, Long> =
        parsePsRssFromOutput(ShellManager.run("ps -A -o NAME,RSS").combined)

    private fun parsePsRssFromOutput(
        output: String,
        allNames: Boolean = false
    ): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("UID") || trimmed.startsWith("NAME")) continue
            val parts = trimmed.split(Regex("""\s+"""))
            val name: String
            val rss: Long?
            if (parts.size >= 3 && parts[0].toIntOrNull() != null) {
                name = RunningAppsProbe.normalizePackageName(
                    parts.subList(2, parts.size).joinToString(" ")
                )
                rss = parts[1].toLongOrNull()
            } else if (parts.size >= 2) {
                name = RunningAppsProbe.normalizePackageName(parts[0])
                rss = parts.last().toLongOrNull()
            } else {
                continue
            }
            if (name.isBlank()) continue
            if (!allNames && !RunningAppsProbe.isLikelyPackageName(name)) continue
            val rssKb = rss ?: continue
            result[name] = (result[name] ?: 0) + rssKb
        }
        return result
    }

    /** RAM по всем именам процессов (включая нативные демоны). */
    fun loadExtendedProcessRam(meminfoOutput: String? = null, psOutput: String? = null): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        if (!psOutput.isNullOrBlank()) {
            parsePsRssFromOutput(psOutput, allNames = true).forEach { (name, rss) ->
                result[name] = (result[name] ?: 0) + rss
            }
        } else {
            ShellManager.run("ps -A -o NAME,RSS").combined.lines().forEach { line ->
                val parts = line.trim().split(Regex("""\s+"""))
                if (parts.size < 2) return@forEach
                val name = RunningAppsProbe.normalizePackageName(parts[0])
                if (name.isBlank()) return@forEach
                val rss = parts.last().toLongOrNull() ?: return@forEach
                result[name] = (result[name] ?: 0) + rss
            }
        }
        val meminfo = meminfoOutput ?: ShellManager.run("dumpsys meminfo", timeoutSec = 45).combined
        parseMeminfoSummaryAll(meminfo).forEach { (name, kb) ->
            result[name] = maxOf(result[name] ?: 0, kb)
        }
        return result
    }

    private fun parseMeminfoSummaryAll(output: String): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        var inSummary = false
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Total PSS by process")) {
                inSummary = true
                continue
            }
            if (inSummary) {
                if (trimmed.isBlank() || trimmed.startsWith("Total PSS by OOM")) break
                val match = summaryPssRegex.find(trimmed) ?: continue
                val kb = match.groupValues[1].replace(",", "").toLongOrNull() ?: continue
                val name = RunningAppsProbe.normalizePackageName(match.groupValues[2])
                if (name.isNotBlank()) {
                    result[name] = (result[name] ?: 0) + kb
                }
            }
        }
        return result
    }
}
