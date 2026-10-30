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
import androidx.core.content.ContextCompat

/**
 * Foreground service lifecycle manager for TacoStrap's local diagnostic listener.
 * Note: this does NOT establish a VPN tunnel or route Roblox traffic.
 */
class FlagVpnService : VpnService() {
    companion object {
        private const val TAG = "FlagVpnService"
        private const val CHANNEL_ID = "tacostrap_service"
        private const val NOTIF_ID = 9001
        private const val PREFS = "tacostrap_vpn_runtime"
        private const val KEY_STATE = "state"
        private const val ACTION_STOP = "com.example.flagmanager.ACTION_STOP"
        const val PROXY_PORT = 8899

        fun buildStartIntent(context: Context, flagsJson: String): Intent? = prepare(context)

        fun buildDirectIntent(context: Context, flagsJson: String): Intent =
            Intent(context, FlagVpnService::class.java)

        fun start(context: Context) {
            val intent = Intent(context, FlagVpnService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FlagVpnService::class.java).setAction(ACTION_STOP))
        }

        // Compatibility hook: this diagnostic service does not inject or rewrite flag payloads.
        fun updateFlags(context: Context, flagsJson: String) {
            Log.d(TAG, "Flag update ignored: local diagnostic listener does not modify Roblox responses")
        }

        fun getState(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_STATE, "off") ?: "off"

        private fun setState(context: Context, state: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_STATE, state).apply()
        }
    }

    @Volatile private var started = false
    @Volatile private var startupFailed = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Android requires foreground promotion promptly after startForegroundService().
        startForeground(NOTIF_ID, buildNotification("Starting local listener…"))
        setState(this, "starting")

        if (!started) {
            started = true
            Thread {
                try {
                    // This is a local diagnostic HTTP listener only. It does not intercept Roblox flags.
                    MitmProxy.start(PROXY_PORT)
                    setState(this, "active")
                    updateNotification("Local listener running — no VPN tunnel")
                    Log.i(TAG, "Local listener started on :$PROXY_PORT")
                } catch (e: Exception) {
                    Log.e(TAG, "Listener startup failed", e)
                    startupFailed = true
                    setState(this, "error")
                    updateNotification("Listener failed to start")
                    stopSelf()
                }
            }.start()
        } else {
            setState(this, "active")
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "TacoStrap service", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(message: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TacoStrap local service")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop", PendingIntent.getService(
                this, 0, Intent(this, FlagVpnService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            ))
            .build()

    private fun updateNotification(message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(message))
    }

    override fun onDestroy() {
        started = false
        MitmProxy.stop()
        if (!startupFailed) setState(this, "off")
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "Service stopped")
        super.onDestroy()
    }
}
