package com.quest3.taskmanager

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Списки пакетов через PackageManager — без shell. */
object PackageListProbe {
    fun installed(context: Context): Set<String> =
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
            .toSet()

    fun userInstalled(context: Context): Set<String> =
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { it.packageName }
            .toSet()
}
