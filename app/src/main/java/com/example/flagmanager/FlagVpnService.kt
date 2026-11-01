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
import kotlinx.coroutines.withContext

/**
 * Foreground service hosting MitmProxy. NO TUN — nothing is routed,
 * nothing can blackhole. The bypass transport is the system HTTP proxy
 * + a user-installed MITM CA (both rootless).
 */
class FlagVpnService : VpnService() {

    companion object {
        private const val TAG = "FlagVpnService"
        private const val CHANNEL_ID = "vpn_bypass"
        private const val NOTIF_ID = 9001
        private const val EXTRA_FLAGS = "extra_flags_json"
        private const val ACTION_UPDATE = "com.example.flagmanager.ACTION_UPDATE_FLAGS"
        private const val ACTION_STOP = "com.example.flagmanager.ACTION_STOP"

        @Volatile var isActive = false
            private set

        fun buildDirectIntent(context: Context, flagsJson: String): Intent =
            Intent(context, FlagVpnService::class.java).putExtra(EXTRA_FLAGS, flagsJson)

        fun updateFlags(context: Context, flagsJson: String) {
            val i = Intent(context, FlagVpnService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_FLAGS, flagsJson)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            }.onFailure { Log.e(TAG, "updateFlags failed: ${it.message}") }
        }

        fun startSafely(context: Context, flagsJson: String): Boolean =
            runCatching {
                val i = buildDirectIntent(context, flagsJson)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
                true
            }.getOrElse {
                Log.e(TAG, "startSafely failed: ${it.message}")
                false
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var proxyJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                MitmProxy.updateFlags(intent.getStringExtra(EXTRA_FLAGS) ?: "{}")
                return START_STICKY
            }
        }
        val flagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"
        postNotification()

        if (proxyJob == null || !MitmProxy.isRunning()) {
            proxyJob = scope.launch { startPipeline(flagsJson) }
        } else {
            MitmProxy.updateFlags(flagsJson)
        }
        return START_STICKY
    }

    private suspend fun startPipeline(flagsJson: String) = withContext(Dispatchers.IO) {
        MitmProxy.start(flagsJson, applicationContext)
        MitmProxy.vpnService = this@FlagVpnService
        if (!MitmProxy.isRunning()) {
            Log.e(TAG, "proxy failed to bind")
            stopSelf()
            return@withContext
        }
        Log.d(TAG, "MitmProxy listening on 127.0.0.1:${MitmProxy.PROXY_PORT_HTTP}")

        val ok = trySetProxy("127.0.0.1", MitmProxy.PROXY_PORT_HTTP)
        if (!ok) {
            Log.w(TAG, "system proxy not set — grant WRITE_SECURE_SETTINGS or run Shizuku")
        } else {
            Log.d(TAG, "system http_proxy = 127.0.0.1:${MitmProxy.PROXY_PORT_HTTP}")
        }
        isActive = true
    }

    private fun trySetProxy(host: String, port: Int): Boolean {
        val shizukuOk = runCatching {
            val cmdClass = Class.forName("rikka.shizuku.Shizuku")
            val ping = cmdClass.getMethod("pingBinder").invoke(null) as Boolean
            if (!ping) return@runCatching false
            val perm = cmdClass.getMethod("checkSelfPermission").invoke(null) as Int
            perm == 0
        }.getOrDefault(false)

        if (shizukuOk) {
            val ok = runCatching {
                val newProcess = Class.forName("rikka.shizuku.Shizuku")
                    .getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    ).apply { isAccessible = true }
                val p = newProcess.invoke(
                    null,
                    arrayOf("sh", "-c", "settings put global http_proxy $host:$port"),
                    null,
                    null
                ) as Process
                p.waitFor() == 0
            }.getOrDefault(false)
            if (ok) return true
        }

        val direct = runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "settings put global http_proxy $host:$port"))
                .waitFor() == 0
        }.getOrDefault(false)
        if (direct) return true

        return runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "settings put global http_proxy $host:$port"))
                .waitFor() == 0
        }.getOrDefault(false)
    }

    private fun clearProxy() {
        runCatching {
            val newProcess = Class.forName("rikka.shizuku.Shizuku")
                .getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
            newProcess.invoke(
                null,
                arrayOf("sh", "-c", "settings put global http_proxy :0"),
                null,
                null
            )
        }
        runCatching {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "settings put global http_proxy :0")).waitFor()
        }
        runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put global http_proxy :0")).waitFor()
        }
    }

    private fun teardown() {
        isActive = false
        proxyJob?.cancel()
        proxyJob = null
        clearProxy()
        MitmProxy.stop()
        Log.d(TAG, "teardown complete")
    }

    private fun postNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Flag Bypass", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, FlagVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        startForeground(
            NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TacoStrap Bypass")
                .setContentText("MITM proxy live — flags injected")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
                .setOngoing(true)
                .build()
        )
    }

    override fun onDestroy() {
        teardown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
