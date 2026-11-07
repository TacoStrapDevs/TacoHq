
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
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Monitors Roblox foreground activity and game-session logs.
 *
 * RobloxLogHelper detects session events.
 * RobloxServerLocation estimates the country of a public endpoint.
 *
 * IP geolocation is an estimate, not proof of physical
 * game-server location.
 */
class RobloxWatchService : Service() {

    companion object {
        private const val TAG = "TacoStrapWatch"

        private const val CHANNEL_TRACKING = "tacostrap_tracking"
        private const val CHANNEL_JOIN = "tacostrap_join"
        private const val CHANNEL_JOIN_POPUP = "tacostrap_join_popup"

        private const val TRACKING_ID = 7810
        private const val JOIN_ID = 7811

        private const val WATCH_PREFS = "tacostrap_notifications"
        private const val SETTINGS_PREFS = "fm"

        private const val ROBLOX_PACKAGE = "com.roblox.client"

        private const val POLL_INTERVAL = 1500L
        private const val LOG_RETRY_INTERVAL = 15000L

        fun hasUsageAccess(context: Context): Boolean {
            val appOps =
                context.getSystemService(Context.APP_OPS_SERVICE)
                    as AppOpsManager

            @Suppress("DEPRECATION")
            return appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }

        fun start(context: Context) {
            val intent = Intent(
                context,
                RobloxWatchService::class.java
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, RobloxWatchService::class.java)
            )
        }

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(
                WATCH_PREFS,
                Context.MODE_PRIVATE
            ).getBoolean("watch_enabled", false)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private var lastRobloxForegroundAt = 0L
    private var lastEventTimestamp = 0L
    private var lastLogStartAttempt = 0L

    private var currentServerCountry = "Unknown"
    private var currentServerCode: String? = null
    private var activeEndpointIp: String? = null

    private var sessionGeneration = 0

    private var sessionDetected = false
    private var serviceDestroyed = false

    private var logMonitoringStatus =
        "Waiting for Roblox session logs"

    private var logHelper: RobloxLogHelper? = null
    private var locationResolver: RobloxServerLocation? = null

    private val poll = object : Runnable {
        override fun run() {

            if (
                !isEnabled(this@RobloxWatchService) ||
                !hasUsageAccess(this@RobloxWatchService)
            ) {
                stopSelf()
                return
            }

            detectRobloxForeground()
            tryStartLogMonitoring()

            handler.postDelayed(
                this,
                POLL_INTERVAL
            )
        }
    }

    override fun onCreate() {
        super.onCreate()

        createChannels()

        locationResolver = RobloxServerLocation()

        logHelper = RobloxLogHelper(
            onEvent = { event ->
                handleSessionEvent(event)
            },
            onStatus = { status ->
                if (!serviceDestroyed) {
                    logMonitoringStatus = status
                    updateTrackingNotification()
                }
            }
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (!isEnabled(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!hasUsageAccess(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(
            TRACKING_ID,
            trackingNotification()
        )

        handler.removeCallbacks(poll)
        handler.post(poll)

        tryStartLogMonitoring()

        return START_STICKY
    }

    /**
     * Starts Shizuku log monitoring.
     * Retries periodically if Shizuku is unavailable.
     */
    private fun tryStartLogMonitoring() {

        if (serviceDestroyed) return

        val helper = logHelper ?: return

        if (helper.isRunning()) return

        val now = System.currentTimeMillis()

        if (
            now - lastLogStartAttempt <
            LOG_RETRY_INTERVAL
        ) {
            return
        }

        lastLogStartAttempt = now

        try {
            helper.start()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Could not start Roblox log monitoring",
                e
            )
        }
    }

    /**
     * Handles parsed Roblox session events.
     * These callbacks are delivered on the main thread
     * by RobloxLogHelper.
     */
    private fun handleSessionEvent(
        event: RobloxLogEvent
    ) {

        if (serviceDestroyed) return

        when (event.type) {

            RobloxLogEvent.Type.JOINED -> {

                sessionGeneration++

                sessionDetected = true
                activeEndpointIp = null

                currentServerCountry = "Unknown"
                currentServerCode = null

                updateTrackingNotification()
                postRobloxActivityNotification()

                val endpoint = event.session.endpointIp

                if (!endpoint.isNullOrBlank()) {
                    lookupServerCountry(endpoint)
                }
            }

            RobloxLogEvent.Type.ENDPOINT_UPDATED -> {

                val endpoint = event.session.endpointIp

                if (!endpoint.isNullOrBlank()) {
                    lookupServerCountry(endpoint)
                }

                // Refresh the activity notification even
                // while the country lookup is pending.
                postRobloxActivityNotification()
            }

            RobloxLogEvent.Type.SERVER_CONFIRMED -> {

                // serverId is not an IP address.
                // Never use it to guess a country.
                Log.d(
                    TAG,
                    "Roblox server identifier detected"
                )
            }

            RobloxLogEvent.Type.DISCONNECTED -> {

                sessionGeneration++

                sessionDetected = false
                activeEndpointIp = null

                currentServerCountry = "Unknown"
                currentServerCode = null

                updateTrackingNotification()
            }
        }
    }

    /**
     * Looks up the estimated country of a detected endpoint.
     */
    private fun lookupServerCountry(ip: String) {

        if (serviceDestroyed) return

        if (ip == activeEndpointIp) return

        activeEndpointIp = ip

        currentServerCountry = "Unknown"
        currentServerCode = null

        val generation = sessionGeneration

        updateTrackingNotification()
        postRobloxActivityNotification()

        val resolver = locationResolver ?: return

        resolver.lookupAsync(ip) { result ->

            handler.post {

                if (serviceDestroyed) return@post

                // Ignore results belonging to an older session.
                if (generation != sessionGeneration) {
                    return@post
                }

                // Ignore results for an outdated endpoint.
                if (activeEndpointIp != ip) {
                    return@post
                }

                if (result.isKnown) {

                    currentServerCountry = result.country
                    currentServerCode = result.countryCode

                    Log.d(
                        TAG,
                        "Server country estimate: " +
                            "${result.country} (${result.countryCode})"
                    )

                } else {

                    currentServerCountry = "Unknown"
                    currentServerCode = null

                    Log.d(
                        TAG,
                        "Server location unavailable: " +
                            "${result.message}"
                    )
                }

                updateTrackingNotification()

                if (sessionDetected) {
                    postRobloxActivityNotification()
                }
            }
        }
    }

    private fun createChannels() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager =
            getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                "TacoStrap tracking",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_JOIN,
                "Roblox activity",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_JOIN_POPUP,
                "Roblox activity pop-ups",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun trackingNotification(): Notification {

        val prefs = getSharedPreferences(
            SETTINGS_PREFS,
            Context.MODE_PRIVATE
        )

        val showGame = prefs.getBoolean(
            "show_game_tracking",
            true
        )

        val content = if (showGame) {
            "Watching Roblox · Server: $currentServerCountry"
        } else {
            "Watching for Roblox activity"
        }

        return NotificationCompat.Builder(
            this,
            CHANNEL_TRACKING
        )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("TacoStrap activity tracking")
            .setColor(Color.rgb(255, 201, 40))
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$content\n$logMonitoringStatus"
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateTrackingNotification() {

        if (serviceDestroyed) return

        val manager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        manager.notify(
            TRACKING_ID,
            trackingNotification()
        )
    }

    private fun detectRobloxForeground() {

        val usage = getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager

        val now = System.currentTimeMillis()

        val events = usage.queryEvents(
            (now - 5000L).coerceAtLeast(0L),
            now
        )

        val event = UsageEvents.Event()

        var detected = false

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            if (event.timeStamp <= lastEventTimestamp) {
                continue
            }

            lastEventTimestamp = event.timeStamp

            val foregroundEvent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType ==
                        UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType ==
                        UsageEvents.Event.MOVE_TO_FOREGROUND
                }

            if (
                foregroundEvent &&
                event.packageName == ROBLOX_PACKAGE
            ) {
                detected = true
            }
        }

        if (
            detected &&
            now - lastRobloxForegroundAt > 15000L
        ) {
            lastRobloxForegroundAt = now
            postRobloxActivityNotification()
        }
    }

    /**
     * Posts or updates the Roblox activity notification.
     */
    private fun postRobloxActivityNotification() {

        val prefs = getSharedPreferences(
            SETTINGS_PREFS,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean("join_notifications", true)) {
            return
        }

        val popup = prefs.getBoolean(
            "popup_notifications",
            false
        )

        val channel = if (popup) {
            CHANNEL_JOIN_POPUP
        } else {
            CHANNEL_JOIN
        }

        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)

        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                JOIN_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
        }

        val showLocation = prefs.getBoolean(
            "show_server_location",
            true
        )

        val locationLine = if (showLocation) {
            "Server location: $currentServerCountry"
        } else {
            "Server location hidden"
        }

        val sessionLine = if (sessionDetected) {
            "Game session detected"
        } else {
            "Game session not verified"
        }

        val builder = NotificationCompat.Builder(
            this,
            channel
        )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("TacoStrap · Roblox activity")
            .setColor(Color.rgb(255, 201, 40))
            .setContentText(
                "$locationLine · $sessionLine"
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$locationLine\n" +
                        "$sessionLine\n" +
                        "Country is estimated from the detected " +
                        "network endpoint and may be unavailable."
                )
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        if (pending != null) {
            builder.setContentIntent(pending)
        }

        val timeout = prefs.getInt(
            "dismiss_seconds",
            0
        )

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            timeout > 0
        ) {
            builder.setTimeoutAfter(
                timeout * 1000L
            )
        }

        if (
            Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val manager = getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.notify(
                JOIN_ID,
                builder.build()
            )
        }
    }

    override fun onDestroy() {

        serviceDestroyed = true

        handler.removeCallbacks(poll)

        try {
            logHelper?.stop()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Could not stop Roblox log helper",
                e
            )
        }

        logHelper = null

        try {
            locationResolver?.close()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Could not close location resolver",
                e
            )
        }

        locationResolver = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
