package com.example.flagmanager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Native Android notifications for TacoStrap's Roblox session tracker.
 *
 * Visual direction: toasted-gold/yellow accent (#FFC928), dark-friendly copy,
 * and TacoStrap branding through the app launcher icon. Android controls the
 * notification card's actual background and layout; this class never attempts
 * to override the system notification shade.
 *
 * IMPORTANT: Call showServerJoined only after the session watcher has confirmed
 * a real join event. A location is shown only when the caller supplies
 * verified=true from a trusted Roblox datacenter/IP match. Otherwise it is
 * deliberately rendered as Unknown.
 */
object TacoStrapNotifications {
    private const val CHANNEL_SESSIONS = "tacostrap_roblox_sessions"
    private const val CHANNEL_TRACKING = "tacostrap_tracking_status"
    private const val NOTIFICATION_SESSION = 6410
    private const val NOTIFICATION_TRACKING = 6411
    private const val REQUEST_OPEN_APP = 6412

    private val tacoGold: Int = Color.rgb(255, 201, 40)

    data class ServerLocation(
        val country: String? = null,
        val city: String? = null,
        val source: String? = null,
        val verified: Boolean = false
    )

    data class RobloxSession(
        val gameName: String,
        val placeId: String? = null,
        val jobId: String? = null,
        val location: ServerLocation = ServerLocation()
    )

    /** Create notification channels. Safe to call repeatedly. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSIONS,
                "Roblox session alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "TacoStrap alerts when a Roblox server session is confirmed"
                enableVibration(true)
                setShowBadge(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                "TacoStrap tracking status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status of Roblox session tracking"
                setShowBadge(false)
            }
        )
    }

    /** True only when Android currently allows TacoStrap to post notifications. */
    fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Posts a TacoStrap-styled join notification. Returns false when permission
     * is missing or Android rejects the notification; it never crashes the app.
     */
    fun showServerJoined(context: Context, session: RobloxSession): Boolean {
        createChannels(context)
        if (!canPostNotifications(context)) return false

        val game = session.gameName.trim().ifBlank { "Roblox experience" }
        val location = formatLocation(session.location)
        val details = buildList {
            add("Game: $game")
            session.placeId?.takeIf { it.isNotBlank() }?.let { add("Place ID: $it") }
            add("Server location: $location")
            session.jobId?.takeIf { it.isNotBlank() }?.let { add("Job ID: $it") }
            session.location.source?.takeIf { session.location.verified && it.isNotBlank() }
                ?.let { add("Location source: $it") }
        }.joinToString("\n")

        val contentIntent = appPendingIntent(context) ?: return false
        val notification = NotificationCompat.Builder(context, CHANNEL_SESSIONS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🌮 Roblox server joined!")
            .setContentText("$game · $location")
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setSubText("TacoStrap  •  Session detected")
            .setColor(tacoGold)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_SESSION, notification)
            true
        }.getOrDefault(false)
    }

    /** Low-priority persistent status notification, intended only while tracking runs. */
    fun showTrackingStatus(context: Context, active: Boolean): Boolean {
        createChannels(context)
        if (!canPostNotifications(context)) return false

        if (!active) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_TRACKING)
            return true
        }

        val contentIntent = appPendingIntent(context) ?: return false
        val notification = NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🌮 TacoStrap tracking is active")
            .setContentText("Watching for confirmed Roblox sessions")
            .setSubText("TacoStrap  •  Gold edition")
            .setColor(tacoGold)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TRACKING, notification)
            true
        }.getOrDefault(false)
    }

    fun clearSessionNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_SESSION)
    }

    private fun formatLocation(location: ServerLocation): String {
        if (!location.verified) return "Unknown"
        val country = location.country?.trim().orEmpty()
        val city = location.city?.trim().orEmpty()
        if (country.isBlank()) return "Unknown"
        return if (city.isBlank() || city.equals(country, ignoreCase = true)) {
            country
        } else {
            "$city, $country"
        }
    }

    private fun appPendingIntent(context: Context): PendingIntent? {
        val launchIntent: Intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
