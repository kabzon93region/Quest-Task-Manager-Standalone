package com.quest3.taskmanager.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.quest3.taskmanager.R
import com.quest3.taskmanager.databinding.FragmentTerminalBinding
import com.quest3.taskmanager.shell.ShellManager
import com.quest3.taskmanager.shell.ShellWatchdog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalFragment : Fragment() {
    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appendLine("$ ${getString(R.string.terminal_welcome)}")
        updateStatus()

        binding.btnTerminalSend.setOnClickListener { runCommand() }
        binding.terminalInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                runCommand()
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            ShellWatchdog.ensureShell(requireContext())
            updateStatus()
        }
    }

    private fun updateStatus() {
        binding.terminalStatus.text = ShellManager.statusMessage(requireContext())
        val ready = ShellManager.isReady()
        binding.terminalInput.isEnabled = ready
        binding.btnTerminalSend.isEnabled = ready
    }

    private fun runCommand() {
        val cmd = binding.terminalInput.text?.toString()?.trim().orEmpty()
        if (cmd.isEmpty()) return
        binding.terminalInput.text?.clear()
        appendLine("$ $cmd")
        binding.btnTerminalSend.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            ShellWatchdog.ensureShell(requireContext())
            if (!ShellManager.isReady()) {
                appendLine(getString(R.string.error_shell))
                binding.btnTerminalSend.isEnabled = true
                updateStatus()
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                ShellManager.run(cmd, timeoutSec = 30)
            }
            val out = result.combined.trimEnd()
            if (out.isNotEmpty()) appendLine(out)
            appendLine("[exit ${result.exitCode}]")
            binding.btnTerminalSend.isEnabled = true
            updateStatus()
        }
    }

    private fun appendLine(text: String) {
        binding.terminalOutput.append("$text\n")
        binding.terminalScroll.post {
            binding.terminalScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
