package com.quest3.taskmanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.quest3.taskmanager.databinding.ActivityMainBinding
import com.quest3.taskmanager.shell.ShellWatchdog
import com.quest3.taskmanager.ui.AllAppsFragment
import com.quest3.taskmanager.ui.LogFragment
import com.quest3.taskmanager.ui.RunningTasksFragment
import com.quest3.taskmanager.ui.SettingsFragment
import com.quest3.taskmanager.ui.TerminalFragment
import com.quest3.taskmanager.shell.AdbSetupNotification
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FileLogger.onAppLaunch(this)
        AdbSetupNotification.createChannels(this)

        setSupportActionBar(binding.toolbar)

        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 5
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> RunningTasksFragment()
                1 -> AllAppsFragment()
                2 -> TerminalFragment()
                3 -> SettingsFragment()
                4 -> LogFragment()
                else -> LogFragment()
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_running)
                1 -> getString(R.string.tab_apps)
                2 -> getString(R.string.tab_terminal)
                3 -> getString(R.string.tab_settings)
                4 -> getString(R.string.tab_log)
                else -> getString(R.string.tab_log)
            }
        }.attach()

        binding.viewPager.post { bootstrapListTabs() }

        lifecycleScope.launch { ShellWatchdog.ensureShell(this@MainActivity) }

        ensureNotificationPermissionIfNeeded()
        AppSettings.syncNotificationService(this)
    }

    override fun onResume() {
        super.onResume()
        AppSettings.syncNotificationService(this)
        lifecycleScope.launch { ShellWatchdog.ensureShell(this@MainActivity) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            AppSettings.syncNotificationService(this)
        }
    }

    private fun ensureNotificationPermissionIfNeeded() {
        if (!AppSettings.isNotificationEnabled(this)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    private fun bootstrapListTabs() {
        lifecycleScope.launch {
            ListTabsBootstrap.bootstrap(
                context = this@MainActivity,
                running = supportFragmentManager.findFragmentByTag("f0") as? RunningTasksFragment,
                allApps = supportFragmentManager.findFragmentByTag("f1") as? AllAppsFragment
            )
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 100
    }
}
