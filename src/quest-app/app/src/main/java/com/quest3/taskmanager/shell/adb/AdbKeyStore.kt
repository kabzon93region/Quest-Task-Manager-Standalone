package com.quest3.taskmanager.shell.adb

import android.content.Context
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import okio.Path.Companion.toPath
import java.io.File

/**
 * Persists ADB host private key so pairing survives app restarts.
 * Uses Kadb private-key-first identity (AOSP-aligned).
 */
object AdbKeyStore {
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val adbDir = File(context.filesDir, "adb").apply { mkdirs() }
            val keyPath = File(adbDir, "private_key.pem").absolutePath.toPath()
            val store = OkioFilePrivateKeyStore(privateKeyPath = keyPath)
            KadbCert.configure(
                store = store,
                policy = KadbCertPolicy(),
                additionalPrivateKeysPem = emptyList()
            )
            KadbCert.ensureReady()
            initialized = true
        }
    }
}
