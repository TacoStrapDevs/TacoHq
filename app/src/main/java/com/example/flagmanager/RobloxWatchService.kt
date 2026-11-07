
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
 * Country information is estimated from a detected public
 * network endpoint. It is not proof of physical server location.
 */
class RobloxWatchService : Service() {

    companion object {
        private const val TAG = "TacoStrapWatch"

        private const val CHANNEL_TRACKING = "tacostrap_tracking"
        private const val CHANNEL_JOIN = "tacostrap_join"
        private const val CHANNEL_JOIN_POPUP = "tacostrap_join_popup"

        private const val TRACKING_ID = 7810
        private const val OPEN_ID = 7811
        private const val GAME_ID = 7812

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
            val intent = Intent(context, RobloxWatchService::class.java)

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

    private var lastEventTimestamp = 0L
    private var lastLogStartAttempt = 0L

    private var robloxInForeground = false

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

            handler.postDelayed(this, POLL_INTERVAL)
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

        if (!isEnabled(this) || !hasUsageAccess(this)) {
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

    private fun tryStartLogMonitoring() {

        if (serviceDestroyed) return

        val helper = logHelper ?: return

        if (helper.isRunning()) return

        val now = System.currentTimeMillis()

        if (now - lastLogStartAttempt < LOG_RETRY_INTERVAL) {
            return
        }

        lastLogStartAttempt = now

        try {
            helper.start()
        } catch (e: Exception) {
            Log.e(TAG, "Could not start Roblox log monitoring", e)
        }
    }

    /**
     * Updates foreground state and activity notifications.
     */
    private fun setRobloxForeground(isForeground: Boolean) {

        if (robloxInForeground == isForeground) return

        robloxInForeground = isForeground

        if (isForeground) {

            if (sessionDetected) {
                postGameSessionNotification()
            } else {
                postRobloxOpenedNotification()
            }

        } else {

            cancelActivityNotifications()
        }
    }

    /**
     * Detects which application is currently in the foreground
     * using Android UsageStats events.
     */
    private fun detectRobloxForeground() {

        val usage = getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager

        val now = System.currentTimeMillis()

        val events = usage.queryEvents(
            (now - 10000L).coerceAtLeast(0L),
            now
        )

        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            if (event.timeStamp <= lastEventTimestamp) {
                continue
            }

            lastEventTimestamp = event.timeStamp

            val isResumed =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType ==
                        UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType ==
                        UsageEvents.Event.MOVE_TO_FOREGROUND
                }

            val isPaused =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType ==
                        UsageEvents.Event.ACTIVITY_PAUSED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType ==
                        UsageEvents.Event.MOVE_TO_BACKGROUND
                }

            if (isResumed) {

                setRobloxForeground(
                    event.packageName == ROBLOX_PACKAGE
                )

            } else if (
                isPaused &&
                event.packageName == ROBLOX_PACKAGE
            ) {

                setRobloxForeground(false)
            }
        }
    }

    /**
     * Handles parsed Roblox session events.
     */
    private fun handleSessionEvent(event: RobloxLogEvent) {

        if (serviceDestroyed) return

        when (event.type) {

            RobloxLogEvent.Type.JOINED -> {

                sessionGeneration++

                sessionDetected = true
                activeEndpointIp = null

                currentServerCountry = "Unknown"
                currentServerCode = null

                updateTrackingNotification()

                if (robloxInForeground) {
                    postGameSessionNotification()
                }

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

                if (robloxInForeground && sessionDetected) {
                    postGameSessionNotification()
                }
            }

            RobloxLogEvent.Type.SERVER_CONFIRMED -> {

                // serverId is not an IP address.
                // Never use it to guess a country.
                Log.d(TAG, "Roblox server identifier detected")
            }

            RobloxLogEvent.Type.DISCONNECTED -> {

                sessionGeneration++

                sessionDetected = false
                activeEndpointIp = null

                currentServerCountry = "Unknown"
                currentServerCode = null

                cancelGameNotification()
                updateTrackingNotification()

                if (robloxInForeground) {
                    postRobloxOpenedNotification()
                }
            }
        }
    }

    /**
     * Looks up the estimated country of a detected endpoint.
     */
    private fun lookupServerCountry(ip: String) {

        if (serviceDestroyed || ip == activeEndpointIp) return

        activeEndpointIp = ip

        currentServerCountry = "Unknown"
        currentServerCode = null

        val generation = sessionGeneration

        updateTrackingNotification()

        if (robloxInForeground && sessionDetected) {
            postGameSessionNotification()
        }

        val resolver = locationResolver ?: return

        resolver.lookupAsync(ip) { result ->

            handler.post {

                if (serviceDestroyed) return@post

                if (generation != sessionGeneration) {
                    return@post
                }

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
                        "Server location unavailable: ${result.message}"
                    )
                }

                updateTrackingNotification()

                if (robloxInForeground && sessionDetected) {
                    postGameSessionNotification()
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
                "TacoStrap background monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Required while Roblox background monitoring is enabled"
                setShowBadge(false)
            }
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

    /**
     * Minimal persistent notification required by Android
     * while this foreground service is running.
     */
    private fun trackingNotification(): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_TRACKING
        )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("TacoStrap monitoring")
            .setContentText("Roblox monitoring is active")
            .setColor(Color.rgb(255, 201, 40))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
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

    private fun selectedActivityChannel(): String {

        val prefs = getSharedPreferences(
            SETTINGS_PREFS,
            Context.MODE_PRIVATE
        )

        return if (
            prefs.getBoolean("popup_notifications", false)
        ) {
            CHANNEL_JOIN_POPUP
        } else {
            CHANNEL_JOIN
        }
    }

    private fun canPostNotifications(): Boolean {

        return Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Builds a Roblox activity notification.
     */
    private fun buildActivityNotification(
        notificationId: Int,
        title: String,
        content: String,
        expandedText: String
    ): Notification {

        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)

        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(
            this,
            selectedActivityChannel()
        )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setColor(Color.rgb(255, 201, 40))
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(expandedText)
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        if (pending != null) {
            builder.setContentIntent(pending)
        }

        val timeout = getSharedPreferences(
            SETTINGS_PREFS,
            Context.MODE_PRIVATE
        ).getInt("dismiss_seconds", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && timeout > 0) {
            builder.setTimeoutAfter(timeout * 1000L)
        }

        return builder.build()
    }

    /**
     * Notification shown when Roblox opens without an active game session.
     */
    private fun postRobloxOpenedNotification() {

        if (!robloxInForeground || sessionDetected) return

        val prefs = getSharedPreferences(
            SETTINGS_PREFS,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean("join_notifications", true)) return
        if (!canPostNotifications()) return

        val notification = buildActivityNotification(
            notificationId = OPEN_ID,
            title = "TacoStrap · Roblox detected",
            content = "Roblox is now open",
            expandedText = "Roblox is now in the foreground."
        )

        val manager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        manager.cancel(GAME_ID)
        manager.notify(OPEN_ID, notification)
    }

    /**
     * Notification shown while Roblox is in the foreground
     * and a game session has been detected.
     */
    private fun postGameSessionNotification() {

        if (!robloxInForeground || !sessionDetected) return

        val prefs = getSharedPreferences(
            SETTINGS_PREFS,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean("join_notifications", true)) return
        if (!canPostNotifications()) return

        val showLocation = prefs.getBoolean(
            "show_server_location",
            true
        )

        val locationLine = if (showLocation) {
            "Server location: $currentServerCountry"
        } else {
            "Server location hidden"
        }

        val notification = buildActivityNotification(
            notificationId = GAME_ID,
            title = "TacoStrap · Game joined",
            content = locationLine,
            expandedText =
                "Game session detected\n" +
                    "$locationLine\n" +
                    "Country is estimated from the detected " +
                    "network endpoint and may be unavailable."
        )

        val manager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        manager.cancel(OPEN_ID)
        manager.notify(GAME_ID, notification)
    }

    private fun cancelGameNotification() {

        val manager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        manager.cancel(GAME_ID)
    }

    private fun cancelActivityNotifications() {

        val manager = getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        manager.cancel(OPEN_ID)
        manager.cancel(GAME_ID)
    }

    override fun onDestroy() {

        serviceDestroyed = true

        handler.removeCallbacks(poll)

        cancelActivityNotifications()

        try {
            logHelper?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Could not stop Roblox log helper", e)
        }

        logHelper = null

        try {
            locationResolver?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Could not close location resolver", e)
        }

        locationResolver = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
