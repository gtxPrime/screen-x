package com.gxdevs.screenx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import com.gxdevs.screenx.MainActivity
import com.gxdevs.screenx.data.AdbManager
import com.gxdevs.screenx.data.AdbMdns
import com.gxdevs.screenx.data.SettingsManager
import kotlinx.coroutines.*

/**
 * Handles the "enter pairing code" notification:
 *  - Listens via mDNS (AdbMdns) for the wireless debugging pairing port & host
 *  - Prompts the user with an inline reply notification
 *  - Performs the SPAKE2+ pairing via AdbManager.pair()
 *  - Automatically connects via TLS once paired
 */
class PairingInputService : Service() {

    private enum class State { WAITING_FOR_PORT, READY, PAIRING, FAILED }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var adbMdns: AdbMdns? = null
    private var idleTimeoutJob: Job? = null
    private var pairingJob: Job? = null

    private var state = State.WAITING_FOR_PORT
    private var pairingHost = ""
    private var pairingPort = 0
    private var failureMessage: String? = null

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat(buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        startForegroundCompat(buildNotification())
        startDiscovery()

        if (intent?.action == ACTION_SUBMIT_CODE) {
            submitCode(
                code = intent.getStringExtra(EXTRA_CODE).orEmpty(),
                port = intent.getIntExtra(EXTRA_PORT, 0),
                host = intent.getStringExtra(EXTRA_HOST).orEmpty()
            )
        } else {
            startIdleTimeout()
        }

        return START_NOT_STICKY
    }

    private fun startDiscovery() {
        if (adbMdns != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            adbMdns = AdbMdns(this, AdbMdns.TLS_PAIRING) { host, port ->
                Log.i(TAG, "Pairing port discovered via mDNS: $host:$port")
                if (port <= 0 || state == State.PAIRING) return@AdbMdns

                serviceScope.launch {
                    pairingHost = host
                    pairingPort = port
                    state = State.READY
                    failureMessage = null
                    updateNotification()
                    startIdleTimeout()
                }
            }.apply { start() }
        }
    }

    private fun startIdleTimeout() {
        idleTimeoutJob?.cancel()
        idleTimeoutJob = serviceScope.launch {
            delay(IDLE_TIMEOUT_MS)
            Log.i(TAG, "Idle timeout reached; stopping PairingInputService")
            stopSelf()
        }
    }

    private fun submitCode(code: String, port: Int, host: String = "") {
        if (pairingJob?.isActive == true) {
            Log.d(TAG, "Pairing already in progress")
            return
        }

        idleTimeoutJob?.cancel()
        state = State.PAIRING
        failureMessage = null
        updateNotification()

        pairingJob = serviceScope.launch {
            val effectivePort = if (port > 0) port else pairingPort
            val effectiveHost = if (host.isNotBlank()) host else pairingHost

            if (effectivePort <= 0) {
                state = State.FAILED
                failureMessage = "Pairing port not detected yet. Open 'Pair device with pairing code' in Developer Options."
                updateNotification()
                startIdleTimeout()
                return@launch
            }

            val result = AdbManager.pair(
                context = this@PairingInputService,
                pairingPort = effectivePort,
                pairingCode = code,
                host = effectiveHost.ifEmpty { null }
            )

            result.onSuccess {
                SettingsManager(this@PairingInputService).setAdbPaired(true)
                showResultNotification(
                    title = "ScreenX ADB Paired Successfully!",
                    text = "Tap to open ScreenX. ADB screen recording is now ready."
                )
                Toast.makeText(this@PairingInputService, "ScreenX Paired Successfully!", Toast.LENGTH_LONG).show()

                // Try connecting in background
                AdbManager.connect(this@PairingInputService, 0, effectiveHost.ifEmpty { null })

                returnToApp()
                stopSelf()
            }.onFailure { error ->
                val message = error.message ?: "Pairing failed."
                Log.w(TAG, "Pairing failed: $message", error)
                state = State.FAILED
                failureMessage = message
                updateNotification()
                Toast.makeText(this@PairingInputService, message, Toast.LENGTH_LONG).show()
                startIdleTimeout()
            }
        }
    }

    private fun returnToApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not bring MainActivity to front", e)
        }
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val title = when (state) {
            State.WAITING_FOR_PORT -> "ScreenX: Waiting for Pairing Dialog"
            State.READY -> "ScreenX: Enter Pairing Code"
            State.PAIRING -> "ScreenX: Pairing with Device…"
            State.FAILED -> "ScreenX: Pairing Failed"
        }

        val summary = when (state) {
            State.WAITING_FOR_PORT -> "In Developer Options, tap \"Pair device with pairing code\""
            State.READY -> "Found port $pairingPort. Tap Reply and enter the 6-digit code shown on screen."
            State.PAIRING -> "Authenticating with wireless debugging daemon…"
            State.FAILED -> failureMessage ?: "Pairing failed. Tap Retry with a fresh code."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setContentIntent(openAppPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(state == State.PAIRING)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent()
            )

        if (state == State.READY || state == State.FAILED) {
            val remoteInput = RemoteInput.Builder(KEY_PAIRING_INPUT)
                .setLabel("6-digit code")
                .build()

            val replyIntent = Intent(this, PairingInputReceiver::class.java).apply {
                action = ACTION_PAIRING_INPUT
                putExtra(EXTRA_PORT, pairingPort)
                putExtra(EXTRA_HOST, pairingHost)
            }

            val replyPendingIntent = PendingIntent.getBroadcast(
                this,
                REQUEST_REPLY,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_input_add,
                    if (state == State.FAILED) "Retry" else "Reply",
                    replyPendingIntent
                )
                    .addRemoteInput(remoteInput)
                    .build()
            )
        }

        return builder.build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, 0)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancelPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        REQUEST_CANCEL,
        Intent(this, PairingInputReceiver::class.java).setAction(ACTION_PAIRING_CANCEL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun showResultNotification(title: String, text: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())

        notificationManager.notify(RESULT_NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wireless ADB Pairing",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification to enter the 6-digit wireless debugging code"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        adbMdns?.stop()
        adbMdns = null
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val TAG = "PairingInputService"

        private const val REQUEST_REPLY = 101
        private const val REQUEST_CANCEL = 102
        private const val REQUEST_OPEN_APP = 103

        private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L

        const val CHANNEL_ID = "screenx_adb_pairing"
        const val NOTIFICATION_ID = 4001
        const val RESULT_NOTIFICATION_ID = 4002
        const val KEY_PAIRING_INPUT = "pairing_input"
        const val ACTION_PAIRING_INPUT = "com.gxdevs.screenx.PAIRING_INPUT"
        const val ACTION_PAIRING_CANCEL = "com.gxdevs.screenx.PAIRING_CANCEL"
        const val ACTION_SUBMIT_CODE = "com.gxdevs.screenx.SUBMIT_PAIRING_CODE"
        const val EXTRA_CODE = "pairing_code"
        const val EXTRA_PORT = "PORT_EXTRA"
        const val EXTRA_HOST = "HOST_EXTRA"
    }
}
