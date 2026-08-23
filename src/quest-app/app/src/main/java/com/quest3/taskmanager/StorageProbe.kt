package com.quest3.taskmanager

import com.quest3.taskmanager.shell.ShellManager

object StorageProbe {
    private val outputLine = Regex("""^([a-zA-Z0-9_.]+)\s+(\d+)$""")

    fun loadDiskSizes(packages: Collection<String>): Map<String, Long> {
        if (packages.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Long>()
        for (chunk in packages.chunked(40)) {
            val script = chunk.joinToString("\n") { pkg ->
                val safe = pkg.replace("'", "").replace(";", "")
                """
                apks=${'$'}(pm path '$safe' 2>/dev/null | cut -d: -f2)
                total=0
                for p in ${'$'}apks; do
                  [ -z "${'$'}p" ] && continue
                  d=${'$'}(dirname "${'$'}p")
                  s=${'$'}(du -sk "${'$'}d" 2>/dev/null | awk '{print ${'$'}1}')
                  [ -n "${'$'}s" ] && total=${'$'}((total + s))
                done
                [ "${'$'}total" -gt 0 ] && echo "$safe ${'$'}total"
                """.trimIndent()
            }
            ShellManager.run(script, timeoutSec = 90).combined.lines().forEach { line ->
                val m = outputLine.find(line.trim()) ?: return@forEach
                m.groupValues[2].toLongOrNull()?.let { kb ->
                    result[m.groupValues[1]] = kb
                }
            }
        }
        FileLogger.d("disk sizes: ${result.size}/${packages.size}")
        return result
    }
}
