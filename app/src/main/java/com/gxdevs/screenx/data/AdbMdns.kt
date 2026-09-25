package com.gxdevs.screenx.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.net.NetworkInterface

@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context,
    private val serviceType: String,
    private val onServiceDiscovered: (host: String, port: Int) -> Unit
) {

    private var registered = false
    private var running = false
    private var serviceName: String? = null
    private val listener = DiscoveryListener(this)
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)

    fun start() {
        if (running) return
        running = true
        if (!registered) {
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.w(TAG, "discoverServices failed", e)
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        unregister()
    }

    private fun unregister() {
        if (!registered) return
        registered = false
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Discovery listener was already unregistered", e)
        }
    }

    private fun onDiscoveryStart() {
        registered = true
        if (!running) unregister()
    }

    private fun onDiscoveryStop() {
        registered = false
    }

    private fun onServiceFound(info: NsdServiceInfo) {
        try {
            nsdManager.resolveService(info, ResolveListener(this))
        } catch (e: Exception) {
            Log.w(TAG, "resolveService failed", e)
        }
    }

    private fun onServiceLost(info: NsdServiceInfo) {
        Log.d(TAG, "onServiceLost: ${info.serviceName}")
        if (info.serviceName == serviceName) {
            onServiceDiscovered("", -1)
        }
    }

    private fun onServiceResolved(resolvedService: NsdServiceInfo) {
        val host = resolvedService.host ?: return
        val hostAddress = host.hostAddress ?: return
        val port = resolvedService.port
        if (port <= 0 || port > 65535) return

        // Verify that this service belongs to this device
        val isLocal = try {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.any { ni ->
                    ni.inetAddresses.asSequence().any { it.hostAddress == hostAddress }
                } ?: false
        } catch (_: Exception) {
            false
        }

        if (running && (isLocal || hostAddress == "127.0.0.1")) {
            serviceName = resolvedService.serviceName
            Log.i(TAG, "Local ADB service resolved: $hostAddress:$port ($serviceName)")
            onServiceDiscovered(hostAddress, port)
        }
    }

    internal class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            adbMdns.onDiscoveryStart()
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.v(TAG, "onStartDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            adbMdns.onDiscoveryStop()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.v(TAG, "onStopDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceLost(serviceInfo)
        }
    }

    internal class ResolveListener(private val adbMdns: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, i: Int) {
            Log.w(TAG, "Resolve failed for ${nsdServiceInfo.serviceName}: error $i")
        }

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(nsdServiceInfo)
        }
    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        private const val TAG = "AdbMdns"
    }
}
