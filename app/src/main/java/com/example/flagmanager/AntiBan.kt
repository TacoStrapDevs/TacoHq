package com.example.flagmanager

object AntiBan {

    fun isBlockedHeader(headerNameLower: String): Boolean =
        headerNameLower in BLOCKED_HEADERS || BLOCKED_PREFIXES.any { headerNameLower.startsWith(it) }

    private val BLOCKED_HEADERS = setOf(
        "via", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto",
        "x-real-ip", "forwarded", "x-roblox-id", "x-roblox-session",
        "x-csrf-token", "roblox-session-id", "rbx-analytics", "rbx-browser-userid",
        "rbx-event-id", "rbx-machine-id", "rbx-device-id", "rbxgameid",
        "rbxplaceid", "rbxuserid", "rbxauthenticationsessionid"
    )

    private val BLOCKED_PREFIXES = listOf(
        "x-roblox-", "rbx-", "x-analytics-", "x-telemetry-"
    )

    fun isTelemetryPath(path: String): Boolean =
        TELEMETRY_PATHS.any { path.startsWith(it) }

    private val TELEMETRY_PATHS = listOf(
        "/v2/diagnostics", "/v1/diagnostics", "/v2/analytics",
        "/diagnostics/", "/analytics/", "/v1/events",
        "/game/report-stats", "/timing/", "/e.png", "/t.png"
    )
}
