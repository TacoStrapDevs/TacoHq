package com.example.flagmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * FlagVpnService
 *
 * Manages the foreground service lifecycle and diagnostic listener.
 *
 * IMPORTANT:
 * This starts a local TCP diagnostic listener. It does not establish
 * an Android VPN tunnel or intercept Roblox traffic.
 */
class FlagVpnService : VpnService() {

    companion object {
        private const val TAG = "FlagVpnService"
        private const val CHANNEL_ID = "tacostrap_diagnostic"
        private const val NOTIFICATION_ID = 9001

        private const val ACTION_STOP =
            "com.example.flagmanager.ACTION_STOP"

        const val PROXY_PORT = 8899

        fun buildStartIntent(
            context: Context,
            flagsJson: String
        ): Intent? {
            return prepare(context)
        }

        fun buildDirectIntent(
            context: Context,
            flagsJson: String
        ): Intent {
            return Intent(context, FlagVpnService::class.java)
        }

        fun updateFlags(
            context: Context,
            flagsJson: String
        ) {
            // This diagnostic listener does not process FastFlags.
            Log.d(TAG, "Flag update requested; diagnostic listener ignores flags")
        }

        fun stop(context: Context) {
            val intent = Intent(context, FlagVpnService::class.java)
                .setAction(ACTION_STOP)

            context.startService(intent)
        }
    }

    @Volatile
    private var serviceStarted = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        postNotification()

        if (!serviceStarted) {
            serviceStarted = true

            try {
                MitmProxy.start(PROXY_PORT)

                Log.d(
                    TAG,
                    "Diagnostic listener requested on port $PROXY_PORT"
                )

            } catch (e: Exception) {
                serviceStarted = false
                Log.e(TAG, "Listener startup failed", e)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    private fun postNotification() {
        val manager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TacoStrap Diagnostic Service",
                NotificationManager.IMPORTANCE_LOW
            )

            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(
            this,
            FlagVpnService::class.java
        ).setAction(ACTION_STOP)

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TacoStrap service active")
                .setContentText("Local diagnostic listener")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_delete,
                    "Stop",
                    stopPendingIntent
                )
                .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        serviceStarted = false

        MitmProxy.stop()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        Log.d(TAG, "Service destroyed")

        super.onDestroy()
    }
}
