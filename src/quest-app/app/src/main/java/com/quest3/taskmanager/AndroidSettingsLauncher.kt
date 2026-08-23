package com.quest3.taskmanager

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.quest3.taskmanager.shell.ShellManager

/**
 * [PACKAGE]. Главная: [DeepLinkHomepageActivity] (exported).
 * [SettingsHomepageActivity] не exported — с неё запуск невозможен.
 */
object AndroidSettingsLauncher {
    const val PACKAGE = "com.android.settings"

    private const val DEEP_LINK_HOME = ".homepage.DeepLinkHomepageActivity"
    private const val EMBED_ACTION = "android.settings.SETTINGS_EMBED_DEEP_LINK_ACTIVITY"
    private const val EXTRA_EMBED_INTENT = "android.provider.extra.SETTINGS_EMBEDDED_DEEP_LINK_INTENT"

    fun isInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun showMissingDialog(context: Context) {
        FileLogger.w("android settings missing: $PACKAGE")
        AlertDialog.Builder(context)
            .setTitle(R.string.settings_android_missing_title)
            .setMessage(R.string.settings_android_missing_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun openMainWithUi(context: Context): Boolean {
        if (!isInstalled(context)) {
            showMissingDialog(context)
            return false
        }
        if (open(context)) return true
        AlertDialog.Builder(context)
            .setTitle(R.string.settings_android_launch_failed)
            .setMessage(R.string.settings_android_launch_failed_hint)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        return false
    }

    fun openAppDetailsWithUi(context: Context, packageName: String): Boolean {
        if (!isInstalled(context)) {
            showMissingDialog(context)
            return false
        }
        if (openAppDetails(context, packageName)) return true
        Toast.makeText(context, R.string.settings_android_launch_failed, Toast.LENGTH_LONG).show()
        return false
    }

    fun open(context: Context): Boolean {
        if (!isInstalled(context)) {
            FileLogger.w("android settings missing: $PACKAGE")
            return false
        }

        if (launchComponentIntent(context, DEEP_LINK_HOME, "deeplink")) return true

        val embed = Intent(EMBED_ACTION).apply {
            setPackage(PACKAGE)
            addCategory(Intent.CATEGORY_DEFAULT)
            component = ComponentName(PACKAGE, "com.android.settings$DEEP_LINK_HOME")
        }
        if (launchIntent(context, embed, "embed-action")) return true

        val homepageTarget = component("$PACKAGE.homepage.SettingsHomepageActivity")
        val embedHome = Intent(EMBED_ACTION).apply {
            setPackage(PACKAGE)
            putExtra(EXTRA_EMBED_INTENT, homepageTarget)
        }
        if (launchIntent(context, embedHome, "embed-home")) return true

        if (launchComponentShell(DEEP_LINK_HOME, label = "deeplink")) return true

        FileLogger.e("android settings: main launch failed")
        return false
    }

    fun openAppDetails(context: Context, packageName: String): Boolean {
        if (!isInstalled(context)) {
            FileLogger.w("android settings missing for app details: $packageName")
            return false
        }

        val uri = "package:$packageName"
        val shellComponents = listOf(
            ".applications.InstalledAppDetailsTop",
            ".applications.InstalledAppDetails",
            ".Settings\$AppDetailsSettingsActivity",
        )
        for (suffix in shellComponents) {
            if (launchComponentShell(suffix, dataUri = uri, label = "app-details:$packageName")) return true
        }

        val intentComponents = listOf(
            "$PACKAGE.applications.InstalledAppDetailsTop",
            "$PACKAGE.applications.InstalledAppDetails",
            "$PACKAGE.Settings\$AppDetailsSettingsActivity",
        )
        for (className in intentComponents) {
            val intent = component(className).apply {
                data = Uri.fromParts("package", packageName, null)
                putExtra("pkg", packageName)
                putExtra(":settings:fragment_args_key", packageName)
                putExtra("package", packageName)
            }
            if (launchIntent(context, intent, "app-details:$packageName")) return true
        }

        FileLogger.e("app details launch failed: $packageName")
        return false
    }

    private fun launchComponentIntent(context: Context, classSuffix: String, label: String): Boolean {
        val className = if (classSuffix.startsWith(".")) PACKAGE + classSuffix else classSuffix
        return launchIntent(context, component(className), label)
    }

    private fun launchComponentShell(
        classSuffix: String,
        dataUri: String? = null,
        label: String
    ): Boolean {
        val normalized = if (classSuffix.startsWith(".")) classSuffix else ".$classSuffix"
        val component = "$PACKAGE/$normalized"
        val dataPart = dataUri?.let { " -d '${it.replace("'", "")}'" }.orEmpty()
        val cmd = "am start -n '$component'$dataPart"
        if (!launchViaShell(cmd)) return false
        FileLogger.i("opened android settings [shell/$label]: $component")
        return true
    }

    private fun component(className: String): Intent =
        Intent().apply {
            val relative = when {
                className.startsWith(".") -> className
                className.startsWith("$PACKAGE.") -> "." + className.removePrefix("$PACKAGE.")
                else -> className
            }
            component = ComponentName(PACKAGE, relative)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    private fun launchIntent(context: Context, intent: Intent, label: String): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            context.startActivity(intent)
            FileLogger.i("opened android settings [intent/$label]: ${intent.component?.className ?: intent.action}")
            true
        } catch (e: ActivityNotFoundException) {
            FileLogger.d("settings miss [$label]: ${intent.component?.className} ${e.message}")
            false
        } catch (e: SecurityException) {
            FileLogger.w("settings denied [$label]: ${intent.component?.className} ${e.message}")
            false
        } catch (e: Exception) {
            FileLogger.w("settings error [$label]: ${intent.component?.className} ${e.message}")
            false
        }
    }

    private fun launchViaShell(command: String): Boolean {
        if (!ShellManager.isReady()) return false
        val result = ShellManager.run(command, timeoutSec = 10)
        if (result.exitCode == 0) return true
        val err = result.stderr.trim().lineSequence().firstOrNull().orEmpty()
        FileLogger.d("settings shell fail exit=${result.exitCode}: $command ${err.take(120)}")
        return false
    }
}

