package com.quest3.taskmanager

import android.content.Context
import android.widget.Toast
import com.quest3.taskmanager.shell.ShellManager
import com.quest3.taskmanager.shell.ShellWatchdog
import com.quest3.taskmanager.ui.AllAppsFragment
import com.quest3.taskmanager.ui.RunningTasksFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ListTabsBootstrap {
    suspend fun bootstrap(
        context: Context,
        running: RunningTasksFragment?,
        allApps: AllAppsFragment?
    ) {
        if (running == null || allApps == null) return

        if (AppListCache.hasCache(context)) {
            coroutineScope {
                launch { ShellWatchdog.ensureShell(context) }
                launch { loadFromCache(context, running, allApps) }
                launch { refreshRunningInBackground(context, running) }
            }
            return
        }

        ShellWatchdog.ensureShell(context)
        if (!ShellManager.isReady()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.error_shell, Toast.LENGTH_SHORT).show()
            }
            return
        }

        loadFreshParallel(context, running, allApps)
    }

    private suspend fun loadFromCache(
        context: Context,
        running: RunningTasksFragment,
        allApps: AllAppsFragment
    ) = coroutineScope {
        running.setLoading(true)
        allApps.setLoading(true)

        val runningDeferred = async(Dispatchers.IO) { AppListCache.loadRunning(context) }
        val allDeferred = async(Dispatchers.IO) { AppListCache.loadAllApps(context) }

        launch {
            try {
                val items = runningDeferred.await().orEmpty()
                withContext(Dispatchers.Main) {
                    running.displayEntries(items)
                }
                FileLogger.d("lists: cache running=${items.size}")
            } finally {
                withContext(Dispatchers.Main) {
                    running.setLoading(false)
                }
            }
        }

        launch {
            try {
                val items = allDeferred.await().orEmpty()
                withContext(Dispatchers.Main) {
                    allApps.displayEntries(items)
                }
                FileLogger.d("lists: cache all=${items.size}")
            } finally {
                withContext(Dispatchers.Main) {
                    allApps.setLoading(false)
                }
            }
        }
    }

    private suspend fun loadFreshParallel(
        context: Context,
        running: RunningTasksFragment,
        allApps: AllAppsFragment
    ) = coroutineScope {
        running.setLoading(true)
        allApps.setLoading(true)

        val repo = AppRepository(context)
        val snapshot = withContext(Dispatchers.IO) {
            RunningSnapshotHolder.getOrCollect(context, forceRefresh = true)
        }
        val runningDeferred = async(Dispatchers.IO) { repo.loadRunningEntries(snapshot) }
        val allDeferred = async(Dispatchers.IO) {
            repo.loadAllEntries(snapshot, loadDisk = !AppListCache.hasCache(context))
        }

        launch {
            try {
                val items = runningDeferred.await()
                withContext(Dispatchers.IO) {
                    AppListCache.saveRunning(context, items)
                }
                withContext(Dispatchers.Main) {
                    running.displayEntries(items)
                }
                FileLogger.i("lists: fresh running=${items.size}")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message ?: "Error", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    running.setLoading(false)
                }
            }
        }

        launch {
            try {
                val items = allDeferred.await()
                withContext(Dispatchers.IO) {
                    AppListCache.saveAllApps(context, items)
                }
                withContext(Dispatchers.Main) {
                    allApps.displayEntries(items)
                }
                FileLogger.i("lists: fresh all=${items.size}")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, e.message ?: "Error", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    allApps.setLoading(false)
                }
            }
        }
    }

    private suspend fun refreshRunningInBackground(
        context: Context,
        running: RunningTasksFragment
    ) {
        if (!ShellWatchdog.ensureShell(context) || !ShellManager.isReady()) return
        try {
            val snapshot = withContext(Dispatchers.IO) {
                RunningSnapshotHolder.getOrCollect(context, forceRefresh = true)
            }
            val items = withContext(Dispatchers.IO) {
                AppRepository(context).loadRunningEntries(snapshot)
            }
            withContext(Dispatchers.IO) {
                AppListCache.saveRunning(context, items)
            }
            withContext(Dispatchers.Main) {
                running.displayEntries(items)
            }
            FileLogger.i("lists: background running refresh=${items.size}")
        } catch (e: Exception) {
            FileLogger.w("lists: background running refresh failed: ${e.message}")
        }
    }
}
