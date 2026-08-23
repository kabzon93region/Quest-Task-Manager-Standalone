package com.quest3.taskmanager.shell.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.quest3.taskmanager.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Auto-discover ADB debug port via mDNS (_adb._tcp).
 * Falls back to shell-based discovery if mDNS fails.
 */
object PortDiscovery {

    private const val SERVICE_TYPE = "_adb._tcp."
    private const val MDNS_TIMEOUT_MS = 5000L
    private const val SHELL_TIMEOUT_MS = 3000L

    /**
     * Try to discover the ADB debug port.
     * Returns the port number or null if discovery fails.
     */
    suspend fun discover(context: Context): Int? {
        // Try mDNS first
        val mdnsPort = discoverViaMdns(context)
        if (mdnsPort != null) {
            FileLogger.i("port discovered via mDNS: $mdnsPort")
            return mdnsPort
        }

        // Fallback: try shell-based discovery (if already connected)
        val shellPort = discoverViaShell()
        if (shellPort != null) {
            FileLogger.i("port discovered via shell: $shellPort")
            return shellPort
        }

        FileLogger.w("port discovery failed")
        return null
    }

    /**
     * Discover ADB port via Android NsdManager (mDNS).
     * On Quest, this may not work reliably due to firmware restrictions.
     */
    private suspend fun discoverViaMdns(context: Context): Int? {
        return withTimeoutOrNull(MDNS_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                var resolved = false

                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        FileLogger.w("mDNS resolve failed: $errorCode")
                        if (!resolved) {
                            resolved = true
                            continuation.resume(null)
                        }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val port = serviceInfo.port
                        FileLogger.i("mDNS resolved: port=$port")
                        if (!resolved) {
                            resolved = true
                            continuation.resume(port)
                        }
                    }
                }

                val discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        FileLogger.i("mDNS discovery started")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        FileLogger.i("mDNS service found: ${serviceInfo.serviceName}")
                        if (!resolved) {
                            try {
                                nsdManager.resolveService(serviceInfo, resolveListener)
                            } catch (e: Exception) {
                                FileLogger.w("mDNS resolve exception: ${e.message}")
                                if (!resolved) {
                                    resolved = true
                                    continuation.resume(null)
                                }
                            }
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        FileLogger.w("mDNS service lost: ${serviceInfo.serviceName}")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        FileLogger.i("mDNS discovery stopped")
                        if (!resolved) {
                            resolved = true
                            continuation.resume(null)
                        }
                    }

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        FileLogger.w("mDNS start discovery failed: $errorCode")
                        if (!resolved) {
                            resolved = true
                            continuation.resume(null)
                        }
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        FileLogger.w("mDNS stop discovery failed: $errorCode")
                    }
                }

                continuation.invokeOnCancellation {
                    try {
                        nsdManager.stopServiceDiscovery(discoveryListener)
                    } catch (_: Exception) {
                        // ignore
                    }
                }

                try {
                    nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                } catch (e: Exception) {
                    FileLogger.w("mDNS discovery exception: ${e.message}")
                    if (!resolved) {
                        resolved = true
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    /**
     * Fallback: discover ADB port by parsing /proc/net/tcp via shell.
     * Only works if we already have an active shell connection.
     */
    private suspend fun discoverViaShell(): Int? {
        return withContext(Dispatchers.IO) {
            try {
                // Try to find ADB daemon port from /proc/net/tcp
                // ADB daemon listens on a random port in range 30000-49999
                val result = AdbConnectionManager.exec("cat /proc/net/tcp", 3)
                if (result.exitCode != 0) return@withContext null

                val ports = mutableSetOf<Int>()
                for (line in result.stdout.lines()) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val localAddress = parts[1]
                        val state = parts[3]
                        // State 0A = LISTEN
                        if (state == "0A") {
                            val portHex = localAddress.substringAfter(":")
                            val port = portHex.toIntOrNull(16) ?: continue
                            // ADB daemon typically listens on ports 30000-49999
                            if (port in 30000..49999) {
                                ports.add(port)
                            }
                        }
                    }
                }

                // Return the most likely port (usually there's only one)
                ports.firstOrNull()
            } catch (e: Exception) {
                FileLogger.w("shell port discovery failed: ${e.message}")
                null
            }
        }
    }
}
