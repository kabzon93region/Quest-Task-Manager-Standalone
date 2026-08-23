package com.quest3.taskmanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.quest3.taskmanager.AndroidSettingsLauncher
import com.quest3.taskmanager.AppEntry
import com.quest3.taskmanager.AppFilter
import com.quest3.taskmanager.AppListCache
import com.quest3.taskmanager.AppRepository
import com.quest3.taskmanager.BackgroundPolicy
import com.quest3.taskmanager.PolicyContext
import com.quest3.taskmanager.R
import com.quest3.taskmanager.shell.ShellManager
import com.quest3.taskmanager.shell.ShellWatchdog
import com.quest3.taskmanager.databinding.FragmentAllAppsBinding
import com.quest3.taskmanager.FileLogger
import com.quest3.taskmanager.RunningSnapshotHolder
import com.quest3.taskmanager.filtered
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AllAppsFragment : Fragment() {
    private var _binding: FragmentAllAppsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository
    private lateinit var adapter: AppListAdapter
    private var allItems = listOf<AppEntry>()
    private var filter = AppFilter.USER
    private var searchQuery = ""
    private var policyCtx: PolicyContext? = null
    private var lastAutoRefreshAt = 0L
    private lateinit var loading: LoadTracker

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAllAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loading = LoadTracker { active ->
            if (_binding == null) return@LoadTracker
            binding.progress.visibility = if (active) View.VISIBLE else View.GONE
        }
        repository = AppRepository(requireContext())
        adapter = AppListAdapter(
            mode = AppListMode.ALL_APPS,
            onItemClick = { entry -> openAppDetails(entry) },
            onSelectionChanged = {},
            onRunBgChanged = { entry, allowed -> setRunBg(entry, allowed) },
            onBgDataChanged = { entry, allowed -> setBgData(entry, allowed) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.chipAll.setOnClickListener { applyFilter(AppFilter.ALL) }
        binding.chipUser.setOnClickListener { applyFilter(AppFilter.USER) }
        binding.chipSystem.setOnClickListener { applyFilter(AppFilter.SYSTEM) }
        binding.chipDaemon.setOnClickListener { applyFilter(AppFilter.DAEMON) }
        binding.editSearch.doAfterTextChanged { text ->
            searchQuery = text?.toString().orEmpty()
            submitFilteredList()
        }
        binding.btnRefresh.setOnClickListener { refresh() }

        filter = AppFilter.USER
        updateFilterChips(filter)
    }

    override fun onResume() {
        super.onResume()
        autoRefreshIfStale()
    }

    private fun autoRefreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshAt < 2000) return
        lastAutoRefreshAt = now
        lifecycleScope.launch {
            try {
                ShellWatchdog.ensureShell(requireContext())
                if (!ShellManager.isReady()) return@launch
                refreshInternal()
            } catch (e: Exception) {
                FileLogger.w("all apps tab auto-refresh: ${e.message}")
            }
        }
    }

    fun setLoading(visible: Boolean) {
        if (visible) loading.begin() else loading.end()
    }

    fun displayEntries(items: List<AppEntry>) {
        allItems = items
        submitFilteredList()
    }

    private fun updateFilterChips(f: AppFilter) {
        binding.chipAll.isChecked = f == AppFilter.ALL
        binding.chipUser.isChecked = f == AppFilter.USER
        binding.chipSystem.isChecked = f == AppFilter.SYSTEM
        binding.chipDaemon.isChecked = f == AppFilter.DAEMON
    }

    private fun openAppDetails(entry: AppEntry) {
        AndroidSettingsLauncher.openAppDetailsWithUi(requireContext(), entry.packageName)
    }

    private fun applyFilter(f: AppFilter) {
        filter = f
        updateFilterChips(f)
        submitFilteredList()
    }

    private fun submitFilteredList() {
        adapter.submitList(allItems.filtered(filter, searchQuery))
    }

    fun refresh() {
        lastAutoRefreshAt = System.currentTimeMillis()
        loading.begin()
        lifecycleScope.launch {
            try {
                ShellWatchdog.ensureShell(requireContext())
                if (!ShellManager.isReady()) {
                    Toast.makeText(requireContext(), R.string.error_shell, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                refreshInternal()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Error", Toast.LENGTH_SHORT).show()
                FileLogger.w("all apps tab refresh: ${e.message}")
            } finally {
                loading.end()
            }
        }
    }

    private suspend fun refreshInternal() {
        val snapshot = withContext(Dispatchers.IO) {
            RunningSnapshotHolder.getOrCollect(requireContext(), forceRefresh = true)
        }
        val items = withContext(Dispatchers.IO) {
            policyCtx = BackgroundPolicy.loadContext()
            repository.loadAllEntries(
                snapshot,
                loadDisk = false,
                cachedDisk = diskFallback()
            )
        }
        allItems = items
        submitFilteredList()
        withContext(Dispatchers.IO) {
            AppListCache.saveAllApps(requireContext(), items)
        }
    }

    private fun diskFallback(): Map<String, Long?> {
        if (allItems.isNotEmpty()) {
            return allItems.associate { it.packageName to it.diskSizeKb }
        }
        return AppListCache.loadAllApps(requireContext()).orEmpty()
            .associate { it.packageName to it.diskSizeKb }
    }

    private fun setRunBg(entry: AppEntry, allowed: Boolean) {
        val previous = entry.runInBackgroundAllowed
        updateLocal(entry.packageName, runAllowed = allowed, dataAllowed = null)
        lifecycleScope.launch {
            ShellWatchdog.ensureShell(requireContext())
            val ok = BackgroundPolicy.setRunInBackgroundAllowed(entry.packageName, allowed)
            if (!ok) {
                updateLocal(entry.packageName, runAllowed = previous, dataAllowed = null)
                Toast.makeText(requireContext(), R.string.error_shell, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setBgData(entry: AppEntry, allowed: Boolean) {
        val previous = entry.backgroundDataAllowed
        updateLocal(entry.packageName, runAllowed = null, dataAllowed = allowed)
        lifecycleScope.launch {
            ShellWatchdog.ensureShell(requireContext())
            val ctx = policyCtx ?: BackgroundPolicy.loadContext().also { policyCtx = it }
            val ok = BackgroundPolicy.setBackgroundDataAllowed(entry.packageName, allowed, ctx)
            if (!ok) {
                updateLocal(entry.packageName, runAllowed = null, dataAllowed = previous)
                Toast.makeText(requireContext(), R.string.error_shell, Toast.LENGTH_SHORT).show()
            } else {
                policyCtx = BackgroundPolicy.loadContext()
            }
        }
    }

    private fun updateLocal(packageName: String, runAllowed: Boolean?, dataAllowed: Boolean?) {
        allItems = allItems.map { item ->
            if (item.packageName != packageName) item
            else item.copy(
                runInBackgroundAllowed = runAllowed ?: item.runInBackgroundAllowed,
                backgroundDataAllowed = dataAllowed ?: item.backgroundDataAllowed
            )
        }
        adapter.updatePolicy(packageName, runAllowed, dataAllowed)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
