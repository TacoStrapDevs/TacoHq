
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

class FlagVpnService : VpnService() {

    companion object {
        private const val TAG = "FlagVpnService"
        private const val CHANNEL_ID = "tacostrap_vpn"
        private const val NOTIFICATION_ID = 9001

        private const val PREFS_NAME = "tacostrap_vpn_state"
        private const val KEY_STATE = "vpn_state"

        private const val EXTRA_FLAGS = "extra_flags_json"
        private const val ACTION_STOP =
            "com.example.flagmanager.ACTION_STOP"
        private const val ACTION_UPDATE =
            "com.example.flagmanager.ACTION_UPDATE_FLAGS"

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
                .putExtra(EXTRA_FLAGS, flagsJson)
        }

        fun start(context: Context) {
            val intent = Intent(context, FlagVpnService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FlagVpnService::class.java)
                .setAction(ACTION_STOP)

            context.startService(intent)
        }

        fun updateFlags(context: Context, flagsJson: String) {
            val intent = Intent(context, FlagVpnService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_FLAGS, flagsJson)

            context.startService(intent)
        }

        fun getState(context: Context): String {
            return context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_STATE, "off") ?: "off"
        }

        private fun setState(context: Context, state: String) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, state)
                .apply()
        }
    }

    private var serviceStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE -> {
                val json = intent.getStringExtra(EXTRA_FLAGS) ?: "{}"
                MitmProxy.updateFlags(json)
                Log.d(TAG, "Diagnostic flags updated")
                return START_STICKY
            }
        }

        val flagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"

        startForeground(
            NOTIFICATION_ID,
            buildNotification("Starting local diagnostic listener")
        )

        if (!serviceStarted) {
            serviceStarted = true
            setState(this, "starting")

            try {
                MitmProxy.start(flagsJson, PROXY_PORT)

                setState(this, "active")

                updateNotification(
                    "Local diagnostic service running"
                )

                Log.i(TAG, "Service started on port $PROXY_PORT")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start diagnostic listener", e)

                setState(this, "error")
                serviceStarted = false

                updateNotification("Service failed to start")
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            val channel = NotificationChannel(
                CHANNEL_ID,
                "TacoStrap Local Service",
                NotificationManager.IMPORTANCE_LOW
            )

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TacoStrap")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, FlagVpnService::class.java)
                        .setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(message: String) {
        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(message)
        )
    }

    override fun onDestroy() {
        serviceStarted = false

        MitmProxy.stop()

        setState(this, "off")

        stopForeground(STOP_FOREGROUND_REMOVE)

        Log.i(TAG, "Service stopped")

        super.onDestroy()
    }
}
