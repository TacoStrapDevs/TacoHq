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
import kotlinx.coroutines.withContext
import java.net.InetAddress

class FlagVpnService : VpnService() {

    companion object {
        private const val TAG           = "FlagVpnService"
        private const val CHANNEL_ID    = "vpn_bypass"
        private const val NOTIF_ID      = 9001
        private const val EXTRA_FLAGS   = "extra_flags_json"
        private const val ACTION_UPDATE = "com.example.flagmanager.ACTION_UPDATE_FLAGS"
        private const val ACTION_STOP   = "com.example.flagmanager.ACTION_STOP"
        private const val TUN_ADDRESS   = "10.99.0.1"
        private const val TUN_MASK      = 32
        private const val TUN_MTU       = 1500

        private val CDN_HOSTS = listOf(
            "clientsettingscdn.roblox.com",
            "clientsettings.roblox.com"
        )

        // change if your bootstrapper uses a different package
        private const val ROBLOX_PKG = "com.roblox.client"

        fun buildStartIntent(context: Context, flagsJson: String): Intent? = prepare(context)

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

    private val scope    = CoroutineScope(Dispatchers.IO)
    private var proxyJob : Job? = null
    private var tunFd    : ParcelFileDescriptor? = null

    // ── lifecycle ─────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { teardown(); stopSelf(); return START_NOT_STICKY }
            ACTION_UPDATE -> {
                MitmProxy.updateFlags(intent.getStringExtra(EXTRA_FLAGS) ?: "{}")
                return START_STICKY
            }
        }
        val flagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"
        postNotification()

        proxyJob = scope.launch { startPipeline(flagsJson) }
        return START_STICKY
    }

    override fun onDestroy() {
        teardown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ── pipeline ──────────────────────────────────────────────────────────────

    private suspend fun startPipeline(flagsJson: String) = withContext(Dispatchers.IO) {
        // STEP 1: resolve CDN IPs BEFORE establishing TUN
        // if we resolve after TUN is up, DNS goes through TUN → loops → fails
        val cdnIps = resolveCdnHosts()
        Log.d(TAG, "CDN IPs resolved: $cdnIps")

        // STEP 2: establish TUN
        tunFd = buildTun(cdnIps)
        if (tunFd == null) {
            Log.e(TAG, "TUN establish failed — stopping")
            stopSelf(); return@withContext
        }

        // STEP 3: wire vpnService ref so protect() is available in MitmProxy.passThrough
        MitmProxy.vpnService = this@FlagVpnService

        // STEP 4: start proxy — protect() now works for all upstream sockets
        try {
            MitmProxy.start(flagsJson, MitmProxy.PROXY_PORT_HTTP, applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "proxy start error: ${e.message}")
        }

        Log.d(TAG, "pipeline live — HTTP :${MitmProxy.PROXY_PORT_HTTP} HTTPS :${MitmProxy.PROXY_PORT_HTTPS}")
    }

    private fun buildTun(cdnIps: List<String>): ParcelFileDescriptor? = runCatching {
        val builder = Builder()
            .setSession("FlagManager VPN")
            .setMtu(TUN_MTU)
            .addAddress(TUN_ADDRESS, TUN_MASK)
            .addDnsServer("8.8.8.8")

        if (cdnIps.isNotEmpty()) {
            // surgical: only CDN IPs through TUN, everything else untouched
            cdnIps.forEach { ip -> if (ip.isNotEmpty()) builder.addRoute(ip, 32) }
        } else {
            // fallback if DNS resolve failed — full route, passThrough handles non-CDN
            builder.addRoute("0.0.0.0", 0)
            Log.w(TAG, "DNS resolve failed — full route fallback active")
        }

        // scope to Roblox only — no other apps go through TUN
        runCatching { builder.addAllowedApplication(ROBLOX_PKG) }
            .onFailure { Log.w(TAG, "addAllowedApplication failed: ${it.message}") }

        builder.establish()
    }.onFailure { Log.e(TAG, "buildTun: ${it.message}") }.getOrNull()

    private fun resolveCdnHosts(): List<String> =
        CDN_HOSTS.flatMap { host ->
            runCatching {
                InetAddress.getAllByName(host)
                    .mapNotNull { it.hostAddress }
                    .filter { it.isNotEmpty() }
            }.onFailure { Log.w(TAG, "resolve $host: ${it.message}") }
             .getOrElse { emptyList() }
        }.distinct()

    private fun teardown() {
        proxyJob?.cancel()
        MitmProxy.stop()
        runCatching { tunFd?.close() }
        tunFd = null
        Log.d(TAG, "pipeline torn down")
    }

    // ── notification ──────────────────────────────────────────────────────────

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
        startForeground(
            NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TacoStrap VPN active")
                .setContentText("CDN interception live — flags injected")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
                .setOngoing(true)
                .build()
        )
    }
}
