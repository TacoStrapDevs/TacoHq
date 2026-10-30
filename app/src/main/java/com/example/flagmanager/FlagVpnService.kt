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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector

/**
 * FlagVpnService — creates a local VPN tunnel that redirects Roblox's HTTP traffic
 * through MitmProxy. MitmProxy intercepts the CDN flag fetch and injects all flags
 * (including those blocked by Roblox's binary allowlist) into the response.
 *
 * Required in AndroidManifest.xml:
 *   <service
 *       android:name=".FlagVpnService"
 *       android:permission="android.permission.BIND_VPN_SERVICE"
 *       android:exported="false">
 *     <intent-filter>
 *       <action android:name="android.net.VpnService" />
 *     </intent-filter>
 *   </service>
 *
 * Also add to <application>:
 *   android:networkSecurityConfig="@xml/network_security_config"
 */
class FlagVpnService : VpnService() {

    companion object {
        private const val TAG = "FlagVpnService"
        private const val CHANNEL_ID = "vpn_bypass"
        private const val NOTIF_ID = 9001
        private const val EXTRA_FLAGS = "extra_flags_json"
        private const val ACTION_UPDATE = "com.example.flagmanager.ACTION_UPDATE_FLAGS"

        // Local proxy port — MitmProxy listens here
        const val PROXY_PORT = 8899

        /**
         * Returns a VpnService.prepare() intent if permission is needed, else null.
         * Null means we can start directly.
         */
        fun buildStartIntent(context: Context, flagsJson: String): Intent? {
            val prepareIntent = prepare(context) // null if already permitted
            return prepareIntent // caller must startActivityForResult if non-null
        }

        /** Direct start intent — only call after prepare() returned null. */
        fun buildDirectIntent(context: Context, flagsJson: String): Intent =
            Intent(context, FlagVpnService::class.java).putExtra(EXTRA_FLAGS, flagsJson)

        /** Update the injected flags while VPN is already running. */
        fun updateFlags(context: Context, flagsJson: String) {
            context.startService(
                Intent(context, FlagVpnService::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_FLAGS, flagsJson)
            )
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentFlagsJson: String = "{}"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE) {
            currentFlagsJson = intent.getStringExtra(EXTRA_FLAGS) ?: "{}"
            MitmProxy.updateFlags(currentFlagsJson)
            Log.d(TAG, "flags updated — ${currentFlagsJson.length} bytes")
            return START_STICKY
        }

        currentFlagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"
        startForeground()
        establish()
        return START_STICKY
    }

    private fun startForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN Bypass", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FlagVpnService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MangoStrap VPN bypass active")
            .setContentText("CDN interception live — flags injected")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun establish() {
        // Start the MITM proxy first so the port is ready before VPN routes traffic to it
        MitmProxy.start(currentFlagsJson, PROXY_PORT)

        vpnInterface = Builder()
            .setSession("MangoStrap Flag Bypass")
            .addAddress("10.99.0.1", 24)
            .addDnsServer("1.1.1.1")
            // Only route Roblox CDN traffic — addAllowedApplication keeps everything else on real network
            .addDisallowedApplication("com.android.providers.telephony") // keep SMS/calls unaffected
            .addRoute("0.0.0.0", 0) // catch-all; MitmProxy only intercepts Roblox host
            .setMtu(1500)
            .establish()

        if (vpnInterface == null) {
            Log.e(TAG, "VPN interface establish() returned null — permission not granted?")
            stopSelf()
            return
        }

        proxyJob = scope.launch {
            runTunnel()
        }
        Log.d(TAG, "VPN tunnel established, proxy on :$PROXY_PORT")
    }

    /**
     * Tunnel loop — reads IP packets from the VPN tun fd and forwards to the MITM proxy.
     * Outbound packets whose destination is a Roblox CDN IP get their TCP payload intercepted;
     * everything else is forwarded raw through a protected socket.
     */
    private fun runTunnel() {
        val tun = vpnInterface ?: return
        val input = FileInputStream(tun.fileDescriptor)
        val output = FileOutputStream(tun.fileDescriptor)
        val buffer = ByteBuffer.allocate(32_767)

        try {
            while (!Thread.currentThread().isInterrupted) {
                buffer.clear()
                val n = input.read(buffer.array())
                if (n <= 0) continue
                buffer.limit(n)

                val packet = buffer.array().copyOf(n)
                val forwarded = MitmProxy.processPacket(packet, n)
                if (forwarded != null) output.write(forwarded)
            }
        } catch (e: Exception) {
            Log.e(TAG, "tunnel error: ${e.message}")
        }
    }

    override fun onDestroy() {
        proxyJob?.cancel()
        MitmProxy.stop()
        runCatching { vpnInterface?.close() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "VPN tunnel torn down")
        super.onDestroy()
    }
}
