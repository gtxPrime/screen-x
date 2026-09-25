package com.gxdevs.screenx.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Gets the device's local IPv4 address (WiFi interface preferred).
     * Necessary for Wireless ADB on devices (e.g. Samsung One UI) where adbd
     * only binds to the Wi-Fi IP and refuses 127.0.0.1.
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            // Check active network capabilities
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

            // Try WifiManager connectionInfo
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null && wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val ipInt = wifiManager.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    return String.format(
                        java.util.Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        (ipInt shr 8) and 0xff,
                        (ipInt shr 16) and 0xff,
                        (ipInt shr 24) and 0xff
                    )
                }
            }

            // Fallback: iterate over non-loopback network interfaces (wlan0, etc.)
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress
                        if (!host.isNullOrEmpty()) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }

        return null
    }
}
