
package com.example.flagmanager

/**
 * Represents a Roblox game session.
 *
 * endpointIp is a candidate network endpoint, not proof
 * of the physical location of the game server.
 */
data class RobloxSession(
    val placeId: String? = null,
    val jobId: String? = null,
    val endpointIp: String? = null,
    val serverId: String? = null,
    val joined: Boolean = false,
    val loading: Boolean = false,
    val connectionConfirmed: Boolean = false
)

/**
 * Events emitted when Roblox session information changes.
 */
data class RobloxLogEvent(
    val type: Type,
    val session: RobloxSession
) {
    enum class Type {
        JOINED,
        ENDPOINT_UPDATED,
        SERVER_CONFIRMED,
        DISCONNECTED
    }
}

class RobloxSessionParser {

    companion object {
        private const val PENDING_ENDPOINT_MAX_AGE = 15_000L
    }

    private var currentSession = RobloxSession()

    private var pendingEndpointIp: String? = null
    private var pendingEndpointTimestamp = 0L

    private val joinRegex = Regex(
        """Joining game ['"]([^'"]+)['"] place (\d+)(?: at ([0-9a-fA-F:.]+))?""",
        RegexOption.IGNORE_CASE
    )

    private val udmuxRegex = Regex(
        """UDMUX Address\s*=\s*([0-9a-fA-F:.]+)""",
        RegexOption.IGNORE_CASE
    )

    private val rccRegex = Regex(
        """RCC Server Address\s*=\s*([0-9a-fA-F:.]+)""",
        RegexOption.IGNORE_CASE
    )

    private val serverIdRegex = Regex(
        """serverId\s*:\s*['"]?([a-zA-Z0-9_-]+)""",
        RegexOption.IGNORE_CASE
    )

    private val disconnectPatterns = listOf(
        Regex("""Leaving game""", RegexOption.IGNORE_CASE),
        Regex("""Connection lost""", RegexOption.IGNORE_CASE),
        Regex("""Disconnected from server""", RegexOption.IGNORE_CASE),
        Regex("""Disconnecting from server""", RegexOption.IGNORE_CASE)
    )

    /**
     * Extracts an endpoint from supported Roblox log formats.
     */
    private fun extractEndpoint(line: String): String? {
        return udmuxRegex.find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: rccRegex.find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
    }

    /**
     * Saves an endpoint temporarily when it appears before
     * the corresponding join message.
     */
    private fun savePendingEndpoint(ip: String) {
        pendingEndpointIp = ip
        pendingEndpointTimestamp = System.currentTimeMillis()
    }

    /**
     * Returns a recent pending endpoint, if available.
     */
    private fun consumePendingEndpoint(): String? {
        val now = System.currentTimeMillis()

        val isRecent =
            pendingEndpointIp != null &&
            now - pendingEndpointTimestamp in
                0..PENDING_ENDPOINT_MAX_AGE

        val endpoint = if (isRecent) {
            pendingEndpointIp
        } else {
            null
        }

        clearPendingEndpoint()
        return endpoint
    }

    private fun clearPendingEndpoint() {
        pendingEndpointIp = null
        pendingEndpointTimestamp = 0L
    }

    /**
     * Processes one Roblox log line.
     */
    @Synchronized
    fun parse(line: String): RobloxLogEvent? {

        if (line.isBlank()) return null

        val detectedEndpoint = extractEndpoint(line)

        // Detect a new game session.
        val joinMatch = joinRegex.find(line)

        if (joinMatch != null) {

            val jobId = joinMatch.groupValues[1]
                .takeIf { it.isNotBlank() }

            val placeId = joinMatch.groupValues[2]
                .takeIf { it.isNotBlank() }

            val initialEndpoint = joinMatch.groupValues
                .getOrNull(3)
                ?.takeIf { it.isNotBlank() }

            // Prefer the endpoint from the join line.
            // Otherwise use a recently buffered endpoint.
            val endpoint = initialEndpoint
                ?: detectedEndpoint
                ?: consumePendingEndpoint()

            clearPendingEndpoint()

            currentSession = RobloxSession(
                placeId = placeId,
                jobId = jobId,
                endpointIp = endpoint,
                joined = true
            )

            return RobloxLogEvent(
                type = RobloxLogEvent.Type.JOINED,
                session = currentSession.copy()
            )
        }

        // Detect disconnection before processing endpoints.
        if (disconnectPatterns.any { it.containsMatchIn(line) }) {

            val endedSession = currentSession.copy()

            currentSession = RobloxSession()
            clearPendingEndpoint()

            return if (endedSession.joined) {
                RobloxLogEvent(
                    type = RobloxLogEvent.Type.DISCONNECTED,
                    session = endedSession
                )
            } else {
                null
            }
        }

        // Buffer endpoints that appear before a join.
        if (!currentSession.joined) {

            if (detectedEndpoint != null) {
                savePendingEndpoint(detectedEndpoint)
            }

            return null
        }

        // Update the active session's endpoint.
        if (
            detectedEndpoint != null &&
            detectedEndpoint != currentSession.endpointIp
        ) {

            currentSession = currentSession.copy(
                endpointIp = detectedEndpoint
            )

            return RobloxLogEvent(
                type = RobloxLogEvent.Type.ENDPOINT_UPDATED,
                session = currentSession.copy()
            )
        }

        // Detect loading stage.
        if (
            line.contains(
                "game_join_loadtime",
                ignoreCase = true
            ) &&
            !currentSession.loading
        ) {
            currentSession = currentSession.copy(
                loading = true
            )
        }

        // serverId is a session identifier, not an IP address.
        val serverId = serverIdRegex
            .find(line)
            ?.groupValues
            ?.getOrNull(1)

        if (
            serverId != null &&
            serverId != currentSession.serverId
        ) {

            currentSession = currentSession.copy(
                serverId = serverId,
                connectionConfirmed = true
            )

            return RobloxLogEvent(
                type = RobloxLogEvent.Type.SERVER_CONFIRMED,
                session = currentSession.copy()
            )
        }

        return null
    }

    @Synchronized
    fun getCurrentSession(): RobloxSession {
        return currentSession.copy()
    }

    @Synchronized
    fun reset() {
        currentSession = RobloxSession()
        clearPendingEndpoint()
    }
}
