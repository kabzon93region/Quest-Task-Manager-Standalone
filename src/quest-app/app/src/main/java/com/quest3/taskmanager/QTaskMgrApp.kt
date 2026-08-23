package com.quest3.taskmanager

import android.app.Application
import com.quest3.taskmanager.shell.adb.AdbKeyStore

class QTaskMgrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdbKeyStore.init(this)
    }
}
