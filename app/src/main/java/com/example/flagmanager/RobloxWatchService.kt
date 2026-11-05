package com.example.flagmanager

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationCompat

/**
 * User-started Roblox foreground-app watcher.
 * It detects Roblox becoming foreground; it cannot inspect Roblox's private
 * session data or determine the physical game-server country.
 */
class RobloxWatchService : Service() {
    companion object {
        private const val CHANNEL_TRACKING = "tacostrap_tracking"
        private const val CHANNEL_JOIN = "tacostrap_join"
        private const val CHANNEL_JOIN_POPUP = "tacostrap_join_popup"
        private const val TRACKING_ID = 7810
        private const val JOIN_ID = 7811
        private const val WATCH_PREFS = "tacostrap_notifications"
        private const val SETTINGS_PREFS = "fm"
        private const val ROBLOX_PACKAGE = "com.roblox.client"

        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            return appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }

        fun start(context: Context) {
            val intent = Intent(context, RobloxWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RobloxWatchService::class.java))
        }

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(WATCH_PREFS, Context.MODE_PRIVATE).getBoolean("watch_enabled", false)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastRobloxForegroundAt = 0L
    private var lastEventTimestamp = 0L

    private val poll = object : Runnable {
        override fun run() {
            if (!isEnabled(this@RobloxWatchService) || !hasUsageAccess(this@RobloxWatchService)) {
                stopSelf()
                return
            }
            detectRobloxForeground()
            handler.postDelayed(this, 1500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Do not start (or let START_STICKY revive) tracking after the user has stopped it.
        if (!isEnabled(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!hasUsageAccess(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(TRACKING_ID, trackingNotification())
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_STICKY
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRACKING, "TacoStrap tracking", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_JOIN, "Roblox activity", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_JOIN_POPUP, "Roblox activity pop-ups", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun trackingNotification(): Notification {
        val prefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val showGame = prefs.getBoolean("show_game_tracking", true)
        return NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🌮 TacoStrap activity tracking")
            .setColor(Color.rgb(255, 201, 40))
            .setContentText(if (showGame) "Watching for Roblox to open · Server: Unknown" else "Watching for Roblox activity")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun detectRobloxForeground() {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usage.queryEvents((now - 5000L).coerceAtLeast(0L), now)
        val event = UsageEvents.Event()
        var detected = false
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp <= lastEventTimestamp) continue
            lastEventTimestamp = event.timeStamp
            val foregroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (foregroundEvent && event.packageName == ROBLOX_PACKAGE) detected = true
        }
        if (detected && now - lastRobloxForegroundAt > 15_000L) {
            lastRobloxForegroundAt = now
            postRobloxActivityNotification()
        }
    }

    private fun postRobloxActivityNotification() {
        val prefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("join_notifications", true)) return
        val popup = prefs.getBoolean("popup_notifications", false)
        val channel = if (popup) CHANNEL_JOIN_POPUP else CHANNEL_JOIN
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                this, 7811, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val locationLine = if (prefs.getBoolean("show_server_location", true)) "Server location: Unknown" else "Server location hidden"
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🌮 TacoStrap · Roblox opened")
            .setColor(Color.rgb(255, 201, 40))
            .setContentText("$locationLine · Game session not verified")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Roblox became the foreground app. $locationLine. TacoStrap cannot confirm that a game has joined or identify its server country from available Android data."))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .apply { if (pending != null) setContentIntent(pending) }
            .build()
        val timeout = prefs.getInt("dismiss_seconds", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && timeout > 0) notification.timeoutAfter = timeout * 1000L
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(JOIN_ID, notification)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
