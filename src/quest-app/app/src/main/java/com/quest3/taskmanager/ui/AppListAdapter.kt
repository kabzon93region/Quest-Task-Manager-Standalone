package com.quest3.taskmanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quest3.taskmanager.AppEntry
import com.quest3.taskmanager.MemoryFormat
import com.quest3.taskmanager.ProcessState
import com.quest3.taskmanager.R
import com.quest3.taskmanager.databinding.ItemAppRowBinding

enum class AppListMode { RUNNING, ALL_APPS }

class AppListAdapter(
    private val mode: AppListMode,
    private val onItemClick: (AppEntry) -> Unit,
    private val onSelectionChanged: (Set<String>) -> Unit,
    private val onRunBgChanged: (AppEntry, Boolean) -> Unit,
    private val onBgDataChanged: (AppEntry, Boolean) -> Unit
) : ListAdapter<AppEntry, AppListAdapter.ViewHolder>(Diff) {

    private val selected = mutableSetOf<String>()
    private var suppressSwitch = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getSelectedPackages(): Set<String> = selected.toSet()

    fun clearSelection() {
        selected.clear()
        onSelectionChanged(selected)
        notifyDataSetChanged()
    }

    fun updatePolicy(packageName: String, runAllowed: Boolean?, dataAllowed: Boolean?) {
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index < 0) return
        val old = currentList[index]
        val updated = old.copy(
            runInBackgroundAllowed = runAllowed ?: old.runInBackgroundAllowed,
            backgroundDataAllowed = dataAllowed ?: old.backgroundDataAllowed
        )
        val newList = currentList.toMutableList()
        newList[index] = updated
        submitList(newList)
    }

    inner class ViewHolder(private val binding: ItemAppRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: AppEntry) {
            val ctx = binding.root.context
            var title = entry.label
            if (entry.processState == ProcessState.CACHED) {
                title += ctx.getString(R.string.process_cached_suffix)
            }
            binding.label.text = title
            binding.packageName.text = entry.packageName
            binding.icon.setImageDrawable(entry.icon)

            val diskText = ctx.getString(R.string.memory_disk, MemoryFormat.formatKb(entry.diskSizeKb))
            val ramText = if (entry.processState != ProcessState.NONE) {
                ctx.getString(R.string.memory_ram, MemoryFormat.formatKb(entry.ramUsageKb))
            } else {
                ctx.getString(R.string.memory_ram, ctx.getString(R.string.memory_ram_none))
            }
            binding.memory.text = if (mode == AppListMode.RUNNING) {
                ramText
            } else {
                ctx.getString(R.string.memory_line, diskText, ramText)
            }

            binding.root.setOnClickListener(null)
            binding.root.isClickable = false
            binding.appInfoArea.setOnClickListener { onItemClick(entry) }

            when (mode) {
                AppListMode.RUNNING -> {
                    binding.checkSelect.visibility = View.VISIBLE
                    binding.policyControls.visibility = View.GONE
                    binding.checkSelect.setOnCheckedChangeListener(null)
                    binding.checkSelect.isChecked = entry.packageName in selected
                    binding.checkSelect.setOnCheckedChangeListener { _, checked ->
                        if (checked) selected.add(entry.packageName) else selected.remove(entry.packageName)
                        onSelectionChanged(selected.toSet())
                    }
                }
                AppListMode.ALL_APPS -> {
                    binding.checkSelect.visibility = View.GONE
                    val showPolicy = !entry.isDaemon ||
                        entry.runInBackgroundAllowed != null ||
                        entry.backgroundDataAllowed != null
                    binding.policyControls.visibility = if (showPolicy) View.VISIBLE else View.GONE
                    if (showPolicy) {
                        suppressSwitch = true
                        binding.switchRunBg.isChecked = entry.runInBackgroundAllowed == true
                        binding.switchBgData.isChecked = entry.backgroundDataAllowed == true
                        suppressSwitch = false
                        binding.switchRunBg.setOnCheckedChangeListener { _, checked ->
                            if (!suppressSwitch) onRunBgChanged(entry, checked)
                        }
                        binding.switchBgData.setOnCheckedChangeListener { _, checked ->
                            if (!suppressSwitch) onBgDataChanged(entry, checked)
                        }
                    } else {
                        binding.switchRunBg.setOnCheckedChangeListener(null)
                        binding.switchBgData.setOnCheckedChangeListener(null)
                    }
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AppEntry>() {
        override fun areItemsTheSame(a: AppEntry, b: AppEntry) = a.packageName == b.packageName
        override fun areContentsTheSame(a: AppEntry, b: AppEntry) = a == b
    }
}
