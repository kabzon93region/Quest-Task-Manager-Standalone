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
import com.quest3.taskmanager.KillResult
import com.quest3.taskmanager.R
import com.quest3.taskmanager.RamInfo
import com.quest3.taskmanager.shell.ShellManager
import com.quest3.taskmanager.shell.ShellWatchdog
import com.quest3.taskmanager.databinding.FragmentRunningTasksBinding
import com.quest3.taskmanager.filtered
import com.quest3.taskmanager.FileLogger
import com.quest3.taskmanager.RunningSnapshotHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RunningTasksFragment : Fragment() {
    private var _binding: FragmentRunningTasksBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: AppRepository
    private lateinit var adapter: AppListAdapter
    private var allItems = listOf<AppEntry>()
    private var filter = AppFilter.USER
    private var searchQuery = ""
    private lateinit var loading: LoadTracker
    private var lastAutoRefreshAt = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRunningTasksBinding.inflate(inflater, container, false)
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
            mode = AppListMode.RUNNING,
            onItemClick = { entry -> openAppDetails(entry) },
            onSelectionChanged = { selected ->
                binding.statusText.text = getString(R.string.selected_count, selected.size)
            },
            onRunBgChanged = { _, _ -> },
            onBgDataChanged = { _, _ -> }
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
        binding.btnKillAll.setOnClickListener { killAll() }
        binding.btnKillSelected.setOnClickListener { killSelected() }
        binding.btnKillRules.setOnClickListener { killByRules() }

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
                FileLogger.w("running tab auto-refresh: ${e.message}")
            }
        }
    }

    fun setLoading(visible: Boolean) {
        if (visible) loading.begin() else loading.end()
    }

    fun displayEntries(items: List<AppEntry>) {
        allItems = items
        submitFilteredList()
        updateRamSummary()
    }

    private fun updateRamSummary() {
        if (_binding == null) return
        binding.ramSummary.text = getString(R.string.running_ram_summary, RamInfo.formatCompactMb(requireContext()))
        binding.ramSummary.visibility = View.VISIBLE
    }

    private fun updateFilterChips(f: AppFilter) {
        binding.chipAll.isChecked = f == AppFilter.ALL
        binding.chipUser.isChecked = f == AppFilter.USER
        binding.chipSystem.isChecked = f == AppFilter.SYSTEM
        binding.chipDaemon.isChecked = f == AppFilter.DAEMON
    }

    /** Все приложения списка (без демонов) — для kill, независимо от фильтра отображения. */
    private fun allAppItems(): List<AppEntry> = allItems.filter { !it.isDaemon }

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
            repository.loadRunningEntries(snapshot)
        }
        displayEntries(items)
        withContext(Dispatchers.IO) {
            AppListCache.saveRunning(requireContext(), items)
        }
    }

    private fun openAppDetails(entry: AppEntry) {
        AndroidSettingsLauncher.openAppDetailsWithUi(requireContext(), entry.packageName)
    }

    private fun killPackages(pkgs: Collection<String>) {
        if (pkgs.isEmpty()) return
        loading.begin()
        lifecycleScope.launch {
            try {
                ShellWatchdog.ensureShell(requireContext())
                if (!ShellManager.isReady()) {
                    Toast.makeText(requireContext(), R.string.error_shell, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val result = repository.killPackages(pkgs)
                showKillResult(result)
                adapter.clearSelection()
                if (result.killedPackages.isNotEmpty()) {
                    allItems = allItems.filter { it.packageName !in result.killedPackages }
                    submitFilteredList()
                    RunningSnapshotHolder.invalidate()
                }
                refreshInternal()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Error", Toast.LENGTH_SHORT).show()
            } finally {
                loading.end()
            }
        }
    }

    private fun showKillResult(result: KillResult) {
        val msg = when {
            result.skippedProtected > 0 && result.failed > 0 ->
                getString(R.string.killed_mixed, result.killed, result.skippedProtected, result.failed)
            result.skippedProtected > 0 ->
                getString(R.string.killed_with_self_skipped, result.killed)
            result.failed > 0 ->
                getString(R.string.killed_with_failed, result.killed, result.failed)
            else ->
                getString(R.string.killed_count, result.killed)
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    private fun killAll() = killPackages(allAppItems().map { it.packageName })
    private fun killSelected() = killPackages(adapter.getSelectedPackages())

    private fun killByRules() {
        loading.begin()
        lifecycleScope.launch {
            try {
                ShellWatchdog.ensureShell(requireContext())
                if (!ShellManager.isReady()) {
                    Toast.makeText(requireContext(), R.string.error_shell, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val pkgs = allAppItems().map { it.packageName }
                showKillResult(repository.killByRules(pkgs))
                refreshInternal()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Error", Toast.LENGTH_SHORT).show()
            } finally {
                loading.end()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
