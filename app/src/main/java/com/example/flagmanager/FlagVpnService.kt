package com.example.flagmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * FlagVpnService — starts the MITM proxy and keeps a foreground notification alive.
 * No raw IP packet processing — the proxy runs as a plain TCP server on PROXY_PORT.
 * Roblox traffic reaches it because we set the VPN tun address and the proxy
 * binds to that address; no packet rewriting needed.
 */
class FlagVpnService : VpnService() {

    companion object {
        private const val TAG = "FlagVpnService"
        private const val CHANNEL_ID = "vpn_bypass"
        private const val NOTIF_ID = 9001
        private const val EXTRA_FLAGS = "extra_flags_json"
        private const val ACTION_UPDATE = "com.example.flagmanager.ACTION_UPDATE_FLAGS"
        private const val ACTION_STOP = "com.example.flagmanager.ACTION_STOP"

        const val PROXY_PORT = 8899

        fun buildStartIntent(context: Context, flagsJson: String): Intent? =
            prepare(context) // null = already permitted, caller starts directly

        fun buildDirectIntent(context: Context, flagsJson: String): Intent =
            Intent(context, FlagVpnService::class.java).putExtra(EXTRA_FLAGS, flagsJson)

        fun updateFlags(context: Context, flagsJson: String) {
            context.startService(
                Intent(context, FlagVpnService::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_FLAGS, flagsJson)
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var proxyJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_UPDATE -> {
                val json = intent.getStringExtra(EXTRA_FLAGS) ?: "{}"
                MitmProxy.updateFlags(json)
                Log.d(TAG, "flags updated")
                return START_STICKY
            }
        }

        val flagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"
        postNotification()

        // Start the proxy — plain TCP, no raw IP manipulation
        proxyJob = scope.launch {
            try {
                MitmProxy.start(flagsJson, PROXY_PORT)
            } catch (e: Exception) {
                Log.e(TAG, "proxy start error: ${e.message}")
            }
        }

        Log.d(TAG, "VPN service started, proxy on :$PROXY_PORT")
        return START_STICKY
    }

    private fun postNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN Bypass", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, FlagVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MangoStrap VPN bypass active")
            .setContentText("CDN interception live")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        proxyJob?.cancel()
        MitmProxy.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "VPN service destroyed")
        super.onDestroy()
    }
}
