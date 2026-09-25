package com.gxdevs.screenx.data

import android.content.Context
import android.os.Build
import android.sun.security.x509.*
import android.util.Base64
import android.util.Log
import com.gxdevs.screenx.utils.NetworkUtils
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.*

/**
 * Manages wireless ADB pairing and TLS connection for ScreenX's silent screen recording.
 *
 * Uses MuntashirAkon/libadb-android which implements the full SPAKE2+ pairing protocol
 * and TLS-based authentication (same approach as anyapk).
 *
 * Supports both device local IP (wlan0) and loopback (127.0.0.1), which is crucial for
 * devices like Samsung Galaxy where adbd only binds to the Wi-Fi IP address.
 */
object AdbManager {

    private const val TAG = "AdbManager"
    private val PAIRING_CODE_PATTERN = Regex("\\d{6}")
    private const val PAIRING_TIMEOUT_MS = 20_000L

    private val pairingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Live connection manager — null when not connected */
    @Volatile
    var manager: AbsAdbConnectionManager? = null
        private set

    val isConnected: Boolean get() = manager != null

    // ── Key + Certificate management (stored in SharedPreferences) ─────────────

    fun getOrCreateConnectionManager(context: Context): AbsAdbConnectionManager {
        val prefs = context.getSharedPreferences("screenx_adb_keys", Context.MODE_PRIVATE)
        val privKeyStr = prefs.getString("private_key", null)
        val certStr = prefs.getString("certificate", null)

        val privateKey: PrivateKey
        val certificate: Certificate

        if (privKeyStr != null && certStr != null) {
            try {
                val keyFactory = java.security.KeyFactory.getInstance("RSA")
                privateKey = keyFactory.generatePrivate(
                    java.security.spec.PKCS8EncodedKeySpec(Base64.decode(privKeyStr, Base64.DEFAULT))
                )
                certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(Base64.decode(certStr, Base64.DEFAULT).inputStream())
            } catch (e: Exception) {
                Log.w(TAG, "Stored keys corrupt, regenerating", e)
                return generateAndSaveManager(context)
            }
        } else {
            return generateAndSaveManager(context)
        }

        return object : AbsAdbConnectionManager() {
            override fun getPrivateKey() = privateKey
            override fun getCertificate() = certificate
            override fun getDeviceName() = "ScreenX"
        }.also { it.setApi(Build.VERSION.SDK_INT) }
    }

    private fun generateAndSaveManager(context: Context): AbsAdbConnectionManager {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
        val keyPair = kpg.generateKeyPair()

        // Self-signed X.509 cert (required by libadb-android's TLS auth)
        val subject = "CN=ScreenX"
        val expiry = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000 * 10)
        val exts = CertificateExtensions()
        exts.set(
            "SubjectKeyIdentifier",
            SubjectKeyIdentifierExtension(KeyIdentifier(keyPair.public).identifier)
        )

        val x500 = X500Name(subject)
        val now = Date()
        val then = Date(expiry)
        exts.set("PrivateKeyUsage", PrivateKeyUsageExtension(now, then))

        val info = X509CertInfo()
        info.set("version", CertificateVersion(2))
        info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
        info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get("SHA512withRSA")))
        info.set("subject", CertificateSubjectName(x500))
        info.set("key", CertificateX509Key(keyPair.public))
        info.set("validity", CertificateValidity(now, then))
        info.set("issuer", CertificateIssuerName(x500))
        info.set("extensions", exts)

        val cert = X509CertImpl(info)
        cert.sign(keyPair.private, "SHA512withRSA")

        // Persist
        context.getSharedPreferences("screenx_adb_keys", Context.MODE_PRIVATE).edit()
            .putString("private_key", Base64.encodeToString(keyPair.private.encoded, Base64.DEFAULT))
            .putString("certificate", Base64.encodeToString(cert.encoded, Base64.DEFAULT))
            .apply()

        Log.i(TAG, "Generated new ADB key + certificate for ScreenX")

        return object : AbsAdbConnectionManager() {
            override fun getPrivateKey() = keyPair.private
            override fun getCertificate() = cert
            override fun getDeviceName() = "ScreenX"
        }.also { it.setApi(Build.VERSION.SDK_INT) }
    }

    // ── Pairing (SPAKE2+) ─────────────────────────────────────────────────────

    /**
     * One-time SPAKE2+ pairing handshake.
     * [pairingPort] — port shown in Wireless Debugging → "Pair device with pairing code"
     * [pairingCode] — the 6-digit code shown on the same dialog
     * [host] — optional IP address (defaults to device's Wi-Fi IP, falls back to 127.0.0.1)
     */
    suspend fun pair(
        context: Context,
        pairingPort: Int,
        pairingCode: String,
        host: String? = null
    ): Result<Boolean> {
        val code = pairingCode.trim()
        if (!PAIRING_CODE_PATTERN.matches(code)) {
            return Result.failure(Exception("Pairing code must be exactly 6 digits."))
        }
        if (pairingPort !in 1..65535) {
            return Result.failure(Exception("Invalid pairing port ($pairingPort)."))
        }

        // Build list of target IP addresses to attempt
        val targetIps = mutableListOf<String>()
        if (!host.isNullOrBlank()) targetIps.add(host)
        val localIp = NetworkUtils.getLocalIpAddress(context)
        if (!localIp.isNullOrBlank() && !targetIps.contains(localIp)) targetIps.add(localIp)
        if (!targetIps.contains("127.0.0.1")) targetIps.add("127.0.0.1")

        Log.i(TAG, "Pairing port=$pairingPort code=$code candidate IPs=$targetIps")

        val attempt = pairingScope.async {
            val mgr = getOrCreateConnectionManager(context)
            var lastEx: Exception? = null
            for (ip in targetIps) {
                try {
                    Log.i(TAG, "Trying SPAKE2+ pair at $ip:$pairingPort")
                    val ok = mgr.pair(ip, pairingPort, code)
                    if (ok) return@async true
                } catch (e: Exception) {
                    Log.w(TAG, "Pair failed on $ip:$pairingPort: ${e.message}")
                    lastEx = e
                }
            }
            if (lastEx != null) throw lastEx
            false
        }

        return try {
            val paired = withTimeoutOrNull(PAIRING_TIMEOUT_MS) { attempt.await() }
            when (paired) {
                true -> {
                    SettingsManager(context).setAdbPaired(true)
                    Log.i(TAG, "Pairing successful on port $pairingPort")
                    Result.success(true)
                }
                false -> {
                    Result.failure(Exception("Device rejected code. Tap 'Pair device with pairing code' for a fresh code."))
                }
                null -> {
                    attempt.cancel()
                    Result.failure(Exception("Pairing timed out. Keep the pairing dialog open and try again."))
                }
            }
        } catch (e: CancellationException) {
            attempt.cancel()
            throw e
        } catch (e: Exception) {
            attempt.cancel()
            Log.w(TAG, "Pairing error", e)
            Result.failure(Exception("Pairing failed: ${e.message}"))
        }
    }

    // ── Connection ────────────────────────────────────────────────────────────

    /**
     * Connects to Wireless ADB using TLS.
     * If [connectionPort] > 0, connects directly to candidate IPs (Wi-Fi IP, then loopback).
     * Otherwise, uses libadb's auto-discovery via mDNS.
     */
    suspend fun connect(
        context: Context,
        connectionPort: Int = 0,
        host: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        disconnect()
        val mgr = getOrCreateConnectionManager(context)

        val success = try {
            if (connectionPort > 0) {
                val targetIps = mutableListOf<String>()
                if (!host.isNullOrBlank()) targetIps.add(host)
                val localIp = NetworkUtils.getLocalIpAddress(context)
                if (!localIp.isNullOrBlank() && !targetIps.contains(localIp)) targetIps.add(localIp)
                if (!targetIps.contains("127.0.0.1")) targetIps.add("127.0.0.1")

                var connected = false
                for (ip in targetIps) {
                    try {
                        Log.i(TAG, "Connecting to $ip:$connectionPort")
                        connected = mgr.connect(ip, connectionPort)
                        if (connected) break
                    } catch (e: Exception) {
                        Log.w(TAG, "Connect to $ip:$connectionPort failed: ${e.message}")
                    }
                }
                connected
            } else {
                mgr.autoConnect(context, 10_000)
            }
        } catch (e: Exception) {
            Log.w(TAG, "connect threw exception", e)
            false
        }

        if (success) {
            manager = mgr
            if (connectionPort > 0) {
                SettingsManager(context).setAdbPort(connectionPort)
            }
            SettingsManager(context).setAdbPaired(true)
            Log.i(TAG, "Connected to Wireless ADB (port=$connectionPort)")
            Result.success(true)
        } else {
            Result.failure(Exception("Could not connect to Wireless ADB. Ensure Wireless Debugging is ON in Developer Options."))
        }
    }

    /** Tries to reconnect using the last-known port or auto-discovery. */
    suspend fun reconnectIfNeeded(context: Context): Boolean {
        if (isConnected) return true
        return try {
            val savedPort = SettingsManager(context).adbPortFlow.first()
            val result = if (savedPort > 0) {
                connect(context, savedPort)
            } else {
                connect(context, 0)
            }
            result.isSuccess
        } catch (e: Exception) {
            Log.w(TAG, "reconnectIfNeeded failed: ${e.message}")
            false
        }
    }

    fun disconnect() {
        try {
            manager?.close()
        } catch (_: Exception) {}
        manager = null
    }
}
