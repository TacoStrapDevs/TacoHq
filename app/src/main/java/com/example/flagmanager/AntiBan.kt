package com.example.flagmanager

/**
 * AntiBan — header and payload filter for the MITM proxy.
 *
 * Strips Roblox telemetry headers and known detection vectors from
 * outbound requests before they reach the CDN. Reduces the signal
 * that a proxy is sitting in the path.
 *
 * MitmProxy.handleConnection() calls:
 *   isBlockedHeader() — on every forwarded request header
 *   isTelemetryPath() — before deciding to pass through or drop a request
 */
object AntiBan {

    /**
     * Returns true if this request header should be dropped from the
     * forwarded request to the upstream server.
     *
     * Blocked categories:
     *  - Roblox analytics / telemetry IDs
     *  - Proxy-detection fingerprints (Via, X-Forwarded-For)
     *  - Session tokens we don't want leaked through an unexpected path
     */
    fun isBlockedHeader(headerNameLower: String): Boolean =
        headerNameLower in BLOCKED_HEADERS ||
            BLOCKED_PREFIXES.any { headerNameLower.startsWith(it) }

    private val BLOCKED_HEADERS = setOf(
        "via",
        "x-forwarded-for",
        "x-forwarded-host",
        "x-forwarded-proto",
        "x-real-ip",
        "forwarded",
        "x-roblox-id",
        "x-roblox-session",
        "x-csrf-token",             // CSRF — not needed on GET CDN calls; stripping avoids token leak
        "roblox-session-id",
        "rbx-analytics",
        "rbx-browser-userid",
        "rbx-event-id",
        "rbx-machine-id",
        "rbx-device-id",
        "rbxgameid",
        "rbxplaceid",
        "rbxuserid",
        "rbxauthenticationsessionid"
    )

    private val BLOCKED_PREFIXES = listOf(
        "x-roblox-",
        "rbx-",
        "x-analytics-",
        "x-telemetry-"
    )

    /**
     * Returns true if the request should be silently dropped with a 204
     * rather than forwarded to the real server.
     *
     * Called by MitmProxy.handleConnection() before passThrough.
     * Matches known Roblox telemetry and diagnostics endpoint prefixes.
     */
    fun isTelemetryPath(path: String): Boolean =
        TELEMETRY_PATHS.any { path.startsWith(it) }

    private val TELEMETRY_PATHS = listOf(
        "/v2/diagnostics",
        "/v1/diagnostics",
        "/v2/analytics",
        "/diagnostics/",
        "/analytics/",
        "/v1/events",
        "/game/report-stats",
        "/timing/",
        "/e.png",           // pixel tracker
        "/t.png"
    )
}
