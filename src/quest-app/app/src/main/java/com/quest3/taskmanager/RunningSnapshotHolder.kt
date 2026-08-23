package com.quest3.taskmanager

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Один снимок процессов на refresh — без параллельных тяжёлых проб. */
object RunningSnapshotHolder {
    private val mutex = Mutex()
    private var cached: RunningSnapshot? = null
    private var cachedAt = 0L

    private const val TTL_MS = 8_000L
    private const val COLLECT_TIMEOUT_MS = 30_000L

    suspend fun getOrCollect(
        context: Context,
        forceRefresh: Boolean = false,
        fast: Boolean = true
    ): RunningSnapshot {
        if (!forceRefresh) {
            peekFresh()?.let { return it }
        }
        return mutex.withLock {
            if (!forceRefresh) {
                peekFresh()?.let { return it }
            }
            FileLogger.probe("snapshot collect start fast=$fast")
            val snapshot = withTimeout(COLLECT_TIMEOUT_MS) {
                RunningAppsProbe.collectRunningSnapshot(context, fast = fast)
            }
            cached = snapshot
            cachedAt = System.currentTimeMillis()
            FileLogger.probe("snapshot collect done display=${snapshot.displayPackages.size}")
            snapshot
        }
    }

    fun invalidate() {
        cached = null
        cachedAt = 0L
    }

    private fun peekFresh(): RunningSnapshot? {
        val snap = cached ?: return null
        return if (System.currentTimeMillis() - cachedAt < TTL_MS) snap else null
    }
}
