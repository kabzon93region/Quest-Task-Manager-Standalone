package com.quest3.taskmanager

import com.quest3.taskmanager.shell.ShellManager

/**
 * Reads live process list via /proc — more reliable than ps/dumpsys for VR games
 * paused under the Quest universal menu overlay.
 */
object ProcFsProbe {
    data class Snapshot(
        val packages: Set<String>,
        val ramByPackageKb: Map<String, Long>
    )

    private val PROC_SCAN_SCRIPT = """
        for pid in /proc/[0-9]*; do
          [ -r "${'$'}pid/cmdline" ] || continue
          pkg=${'$'}(tr '\0' '\n' < "${'$'}pid/cmdline" | head -n 1)
          [ -n "${'$'}pkg" ] || continue
          rss=${'$'}(awk '/VmRSS/ {print ${'$'}2}' "${'$'}pid/status" 2>/dev/null)
          echo "${'$'}pkg ${'$'}{rss:-0}"
        done
    """.trimIndent()

    fun collect(installed: Set<String> = emptySet(), timeoutSec: Long = 20): Snapshot {
        val out = ShellManager.run(PROC_SCAN_SCRIPT, timeoutSec = timeoutSec).combined
        if (out.isBlank()) return Snapshot(emptySet(), emptyMap())

        val packages = linkedSetOf<String>()
        val ram = mutableMapOf<String, Long>()
        val unrecognized = linkedSetOf<String>()
        for (line in out.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val space = trimmed.lastIndexOf(' ')
            if (space <= 0) continue
            val rawPkg = trimmed.substring(0, space).trim()
            val rssKb = trimmed.substring(space + 1).trim().toLongOrNull() ?: 0L
            val pkg = RunningAppsProbe.normalizePackageName(rawPkg)
            if (pkg.isBlank()) continue
            if (!RunningAppsProbe.isRecognizedPackage(pkg, installed)) {
                if (rssKb > 0 && RunningAppsProbe.isLikelyPackageName(pkg)) {
                    unrecognized.add("$rawPkg->$pkg")
                }
                continue
            }
            packages.add(pkg)
            if (rssKb > 0) {
                ram[pkg] = (ram[pkg] ?: 0L) + rssKb
            }
        }
        FileLogger.probe("procfs: packages=${packages.size} withRam=${ram.size} lines=${out.lineSequence().count()}")
        if (unrecognized.isNotEmpty()) {
            FileLogger.probe("procfs unrecognized (${unrecognized.size}): ${unrecognized.sorted().take(12).joinToString()}")
        }
        return Snapshot(packages, ram)
    }
}
