package com.quest3.taskmanager.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.quest3.taskmanager.AndroidSettingsLauncher
import com.quest3.taskmanager.AppSettings
import com.quest3.taskmanager.BuildConfig
import com.quest3.taskmanager.CleanupForegroundService
import com.quest3.taskmanager.FileLogger
import com.quest3.taskmanager.R
import com.quest3.taskmanager.databinding.FragmentSettingsBinding
import com.quest3.taskmanager.shell.AdbShellBackend
import com.quest3.taskmanager.shell.ShellManager
import com.quest3.taskmanager.shell.ShellWatchdog
import com.quest3.taskmanager.shell.adb.PortDiscovery

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = AppSettings.prefs(requireContext())
        val ctx = requireContext()

        binding.switchNotification.isChecked = AppSettings.isNotificationEnabled(ctx)
        binding.switchLogging.isChecked = prefs.getBoolean(AppSettings.KEY_LOGGING, true)
        binding.editLogPath.setText(
            prefs.getString(AppSettings.KEY_LOG_PATH, AppSettings.DEFAULT_LOG_PATH)
        )

        binding.editPairPort.setText(AdbShellBackend.getPairPort(ctx))
        binding.editDebugPort.setText(AdbShellBackend.getDebugPort(ctx))

        binding.switchNotification.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_NOTIFICATIONS
                    )
                }
                prefs.edit().putBoolean(AppSettings.KEY_NOTIFICATION, true).apply()
            } else {
                prefs.edit().putBoolean(AppSettings.KEY_NOTIFICATION, false).apply()
            }
            AppSettings.syncNotificationService(ctx)
        }

        binding.switchLogging.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(AppSettings.KEY_LOGGING, checked).apply()
            FileLogger.setEnabled(checked)
        }

        binding.editLogPath.doAfterTextChanged { text ->
            val path = text?.toString()?.trim().orEmpty()
            if (path.isNotEmpty()) {
                prefs.edit().putString(AppSettings.KEY_LOG_PATH, path).apply()
                FileLogger.setLogPath(path)
            }
        }

        binding.editPairPort.doAfterTextChanged {
            val port = it?.toString().orEmpty()
            AdbShellBackend.savePairing(ctx, port, binding.editPairCode.text?.toString().orEmpty())
            if (port.isBlank()) {
                AppSettings.prefs(ctx).edit().putBoolean(AppSettings.KEY_ADB_PAIRED, false).apply()
            }
        }
        binding.editPairCode.doAfterTextChanged {
            AdbShellBackend.savePairing(ctx, binding.editPairPort.text?.toString().orEmpty(), it?.toString().orEmpty())
        }
        binding.editDebugPort.doAfterTextChanged {
            AdbShellBackend.saveDebugPort(ctx, it?.toString().orEmpty())
        }

        binding.btnOpenWirelessDebugging.setOnClickListener { openWirelessDebugging() }
        binding.btnAdbPair.setOnClickListener {
            runAdbAction {
                val (ok, msg) = AdbShellBackend.pair(ctx)
                if (ok) {
                    val prefs = AppSettings.prefs(ctx)
                    val probedPort = prefs.getString(AppSettings.KEY_ADB_DEBUG_PORT, "").orEmpty()
                    if (probedPort.isNotEmpty()) {
                        binding.editDebugPort.setText(probedPort)
                        toast(getString(R.string.settings_adb_discover_ok, probedPort.toInt()))
                        val (_, connectMsg) = AdbShellBackend.connect(ctx)
                        toast(connectMsg)
                    } else {
                        // Probe failed — user must enter debug port manually
                        toast(getString(R.string.adb_pair_ok))
                        binding.editDebugPort.text?.clear()
                        binding.editDebugPort.requestFocus()
                    }
                } else {
                    toast(msg)
                }
                updateShellStatus()
            }
        }
        binding.btnAdbConnect.setOnClickListener {
            runAdbAction {
                val (_, msg) = AdbShellBackend.connect(ctx)
                toast(msg)
                updateShellStatus()
            }
        }
        binding.btnAdbDisconnect.setOnClickListener {
            runAdbAction {
                AdbShellBackend.disconnect()
                toast(getString(R.string.settings_adb_disconnected))
                updateShellStatus()
            }
        }
        binding.btnAdbAutoPort.setOnClickListener {
            runAdbAction {
                binding.shellStatus.text = getString(R.string.settings_adb_discovering)
                val port = PortDiscovery.discover(ctx)
                if (port != null) {
                    binding.editDebugPort.setText(port.toString())
                    toast(getString(R.string.settings_adb_discover_ok, port))
                } else {
                    toast(getString(R.string.settings_adb_discover_fail))
                }
                updateShellStatus()
            }
        }

        binding.btnOpenAndroidSettings.setOnClickListener { openAndroidSettings() }
        binding.appVersion.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        binding.btnGithub.setOnClickListener { openUrl(R.string.settings_github_url) }
        binding.btnDonate.setOnClickListener { openUrl(R.string.settings_donationalerts_url) }

        updateShellStatus()
    }

    override fun onResume() {
        super.onResume()
        binding.switchNotification.isChecked = AppSettings.isNotificationEnabled(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            ShellWatchdog.ensureShell(requireContext())
            updateShellStatus()
        }
    }

    private fun updateShellStatus() {
        binding.shellStatus.text = ShellManager.statusMessage(requireContext())
    }

    private fun runAdbAction(block: suspend () -> Unit) {
        setAdbBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } finally {
                setAdbBusy(false)
            }
        }
    }

    private fun setAdbBusy(busy: Boolean) {
        binding.btnAdbPair.isEnabled = !busy
        binding.btnAdbConnect.isEnabled = !busy
        binding.btnAdbDisconnect.isEnabled = !busy
        binding.btnAdbAutoPort.isEnabled = !busy
        if (busy) {
            binding.shellStatus.text = getString(R.string.settings_adb_busy)
        } else {
            updateShellStatus()
        }
    }

    private fun openWirelessDebugging() {
        val intents = listOf(
            Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
                return
            }
        }
        AndroidSettingsLauncher.openMainWithUi(requireContext())
    }

    private fun openAndroidSettings() {
        AndroidSettingsLauncher.openMainWithUi(requireContext())
    }

    private fun openUrl(urlResId: Int) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(urlResId))))
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 200
    }
}
