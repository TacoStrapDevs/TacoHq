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
import kotlin.concurrent.thread

/**
 * FlagVpnService — foreground service that starts MitmProxy and intercepts traffic.
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
    private var vpnInterface: ParcelFileDescriptor? = null
    private var udpThread: Thread? = null

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

        proxyJob = scope.launch {
            try {
                MitmProxy.start(flagsJson, PROXY_PORT)
            } catch (e: Exception) {
                Log.e(TAG, "proxy start error: ${e.message}")
            }
        }

        setupVpnTunnel()

        Log.d(TAG, "service started, proxy on :$PROXY_PORT")
        return START_STICKY
    }

    private fun setupVpnTunnel() {
        try {
            val builder = Builder()
                .setSession("TacoStrap Ghost-Route")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                return
            }

            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)

            udpThread = thread(name = "UdpLoopback", isDaemon = true) {
                val buffer = ByteArray(32767)
                while (vpnInterface != null) {
                    try {
                        val length = vpnInput.read(buffer)
                        if (length > 0) {
                            val protocol = buffer[9].toInt() and 0xFF
                            if (protocol == 17) {
                                // UDP Packet for RakNet (Desyncs)
                            } else if (protocol == 6) {
                                // TCP Packet for HTTP (FFlags)
                            }
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "VPN setup error: ${e.message}")
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
            .setContentTitle("TacoStrap active")
            .setContentText("Ghost-Route intercepting traffic")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        proxyJob?.cancel()
        MitmProxy.stop()
        vpnInterface?.close()
        vpnInterface = null
        udpThread?.interrupt()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "service destroyed")
        super.onDestroy()
    }
}
