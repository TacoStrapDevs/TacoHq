package com.example.flagmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * FlagVpnService — establishes a tun interface that routes all IPv4 traffic
 * through the device, then starts MitmProxy on PROXY_PORT to intercept Roblox
 * CDN flag fetches and inject the user's flag JSON.
 *
 * Traffic routing: tun address 10.13.37.1/32 + default route 0.0.0.0/0.
 * MitmProxy binds on all interfaces at PROXY_PORT; the OS redirects outbound
 * TCP through the tun fd, and iptables/VPN framework delivers it to the proxy.
 *
 * // run in a VM or test device — this hooks ALL outbound IPv4 traffic globally
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

        /**
         * Returns a non-null Intent if VPN permission must first be granted via
         * startActivityForResult. Null means permission is already held; caller
         * can start the service directly with buildDirectIntent.
         */
        fun buildStartIntent(context: Context, flagsJson: String): Intent? =
            prepare(context)

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
    private var tunFd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val json = intent.getStringExtra(EXTRA_FLAGS) ?: "{}"
                MitmProxy.updateFlags(json)
                Log.d(TAG, "flags updated live")
                return START_STICKY
            }
        }

        val flagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"

        postNotification()

        if (!establishTun()) {
            Log.e(TAG, "tun establish failed — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        proxyJob = scope.launch {
            try {
                MitmProxy.start(flagsJson, PROXY_PORT)
            } catch (e: Exception) {
                Log.e(TAG, "proxy start error: ${e.message}")
            }
        }

        Log.d(TAG, "VPN service started, tun up, proxy on :$PROXY_PORT")
        return START_STICKY
    }

    /**
     * Builds and establishes the tun interface.
     * Routes all IPv4 traffic (0.0.0.0/0) through the tun fd so MitmProxy
     * receives every outbound connection including Roblox CDN HTTPS.
     * Returns false if the VPN builder throws (e.g. permission revoked mid-session).
     */
    private fun establishTun(): Boolean {
        tunFd?.runCatching { close() }
        tunFd = null
        return try {
            tunFd = Builder()
                .setSession("TacoStrap")
                .addAddress("10.13.37.1", 32)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)        // all IPv4 through tun
                .setMtu(1500)
                .establish()
            tunFd != null
        } catch (e: Exception) {
            Log.e(TAG, "tun establish error: ${e.message}")
            false
        }
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
            .setContentTitle("TacoStrap VPN bypass active")
            .setContentText("CDN interception live")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        // Ordered teardown: stop proxy producer first, then release tun fd.
        proxyJob?.cancel()
        MitmProxy.stop()
        runCatching { tunFd?.close() }
        tunFd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "VPN service destroyed")
        super.onDestroy()
    }
}
