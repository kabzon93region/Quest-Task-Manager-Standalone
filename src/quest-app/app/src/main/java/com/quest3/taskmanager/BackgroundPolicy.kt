package com.quest3.taskmanager

import com.quest3.taskmanager.shell.ShellManager

/**
 * Чтение/запись прав как в [No More Background](https://github.com/adilhanney/no_more_background).
 */
object BackgroundPolicy {
    private val packageUidRegex = Regex("""package:([a-zA-Z0-9_.]+)\s+uid:(\d+)""")

    fun loadContext(): PolicyContext {
        val uidToPackages = mutableMapOf<Int, MutableSet<String>>()
        ShellManager.run("cmd package list packages -U").combined.let { text ->
            packageUidRegex.findAll(text).forEach { match ->
                val pkg = match.groupValues[1]
                val uid = match.groupValues[2].toIntOrNull() ?: return@forEach
                uidToPackages.getOrPut(uid) { mutableSetOf() }.add(pkg)
            }
        }
        val blacklisted = parseBlacklist(
            ShellManager.run("cmd netpolicy list restrict-background-blacklist").combined
        ).toMutableSet()
        if (blacklisted.isEmpty()) {
            val netpolicy = ShellManager.run("dumpsys netpolicy").combined
            Regex("""UID=(\d+)\s+policy=1\s+\(REJECT_METERED_BACKGROUND\)""")
                .findAll(netpolicy)
                .forEach { m -> m.groupValues[1].toIntOrNull()?.let { blacklisted.add(it) } }
        }
        return PolicyContext(uidToPackages, blacklisted)
    }

    fun isRunInBackgroundBlocked(packageName: String): Boolean {
        val text = ShellManager.run("cmd appops get $packageName RUN_ANY_IN_BACKGROUND").combined
        return text.contains("ignore", ignoreCase = true) || text.contains("deny", ignoreCase = true)
    }

    fun isBackgroundDataBlocked(packageName: String, ctx: PolicyContext): Boolean {
        val uid = getUid(packageName, ctx) ?: return false
        return uid in ctx.backgroundDataBlockedUids
    }

    fun setRunInBackgroundAllowed(packageName: String, allowed: Boolean): Boolean {
        val mode = if (allowed) "allow" else "ignore"
        val result = ShellManager.run("cmd appops set $packageName RUN_ANY_IN_BACKGROUND $mode")
        FileLogger.i("set run-in-background $packageName -> $mode exit=${result.exitCode}")
        return result.exitCode == 0
    }

    fun setBackgroundDataAllowed(packageName: String, allowed: Boolean, ctx: PolicyContext): Boolean {
        val uid = getUid(packageName, ctx) ?: return false
        val cmd = if (allowed) {
            "cmd netpolicy remove restrict-background-blacklist $uid"
        } else {
            "cmd netpolicy add restrict-background-blacklist $uid"
        }
        val result = ShellManager.run(cmd)
        FileLogger.i("set bg-data $packageName uid=$uid allowed=$allowed exit=${result.exitCode}")
        return result.exitCode == 0
    }

    private fun getUid(packageName: String, ctx: PolicyContext): Int? {
        ctx.uidToPackages.entries.firstOrNull { packageName in it.value }?.key?.let { return it }
        val single = ShellManager.run("dumpsys package $packageName | grep -m1 'uid='").combined
        return Regex("""uid=(\d+)""").find(single)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseBlacklist(text: String): Set<Int> {
        val marker = "Restrict background blacklisted UIDs:"
        val line = text.lines().firstOrNull { it.contains(marker, ignoreCase = true) } ?: return emptySet()
        return Regex("""\d+""").findAll(line.substringAfter(marker)).mapNotNull { it.value.toIntOrNull() }.toSet()
    }
}

data class PolicyContext(
    val uidToPackages: Map<Int, Set<String>>,
    val backgroundDataBlockedUids: Set<Int>
)
