package com.quest3.taskmanager

import com.quest3.taskmanager.shell.ShellManager

/**
 * Authoritative per-package memory probe — matches Android Settings "running" detection.
 * Batched dumpsys meminfo for user-installed packages.
 */
object MeminfoPackageProbe {
    private val meminfoPidHeader = Regex("""\*\* MEMINFO in pid \d+ \[([^\]]+)]""")
    private val totalPss = Regex("""TOTAL\s+(\d+)""")

    fun collect(targetPackages: Collection<String>): Snapshot {
        if (targetPackages.isEmpty()) {
            FileLogger.d("meminfo-probe: skipped (no targets)")
            return Snapshot(emptySet(), emptyMap())
        }

        val packages = linkedSetOf<String>()
        val ram = mutableMapOf<String, Long>()
        val chunks = targetPackages.distinct().chunked(12)
        var emptyChunks = 0
        for (chunk in chunks) {
            val cmd = "dumpsys meminfo ${chunk.joinToString(" ")}"
            val t0 = System.currentTimeMillis()
            val out = ShellManager.run(cmd, timeoutSec = 30).combined
            val elapsed = System.currentTimeMillis() - t0
            if (out.isBlank()) {
                emptyChunks++
                FileLogger.w("meminfo-probe: empty output for chunk (${chunk.size} pkgs) ${elapsed}ms")
                continue
            }
            val foundInChunk = mutableListOf<String>()
            for (pkg in chunk) {
                val pss = parsePackagePss(out, pkg) ?: continue
                if (pss > 0) {
                    packages.add(pkg)
                    ram[pkg] = maxOf(ram[pkg] ?: 0, pss)
                    foundInChunk.add("$pkg=${pss}K")
                }
            }
            FileLogger.d(
                "meminfo chunk: checked=${chunk.size} found=${foundInChunk.size} ${elapsed}ms" +
                    if (foundInChunk.isEmpty()) "" else " ${foundInChunk.take(8).joinToString()}"
            )
        }
        FileLogger.d(
            "meminfo-probe: packages=${packages.size} checked=${targetPackages.size} " +
                "chunks=${chunks.size} empty=$emptyChunks"
        )
        return Snapshot(packages, ram)
    }

    private fun parsePackagePss(output: String, pkg: String): Long? {
        val marker = "** MEMINFO in pid"
        var searchFrom = 0
        while (true) {
            val idx = output.indexOf(marker, searchFrom)
            if (idx < 0) return null
            val sectionEnd = output.indexOf("** MEMINFO in pid", idx + marker.length)
                .let { if (it < 0) output.length else it }
            val section = output.substring(idx, sectionEnd)
            val header = meminfoPidHeader.find(section) ?: run {
                searchFrom = idx + marker.length
                continue
            }
            if (RunningAppsProbe.normalizePackageName(header.groupValues[1]) != pkg) {
                searchFrom = idx + marker.length
                continue
            }
            var pss = 0L
            for (line in section.lineSequence()) {
                if (line.trim().startsWith("TOTAL")) {
                    totalPss.find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { pss = it }
                    break
                }
            }
            return pss
        }
    }

    data class Snapshot(
        val packages: Set<String>,
        val ramByPackageKb: Map<String, Long>
    )
}
