
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

    private var currentSession = RobloxSession()

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
     * Processes one Roblox log line.
     */
    @Synchronized
    fun parse(line: String): RobloxLogEvent? {

        if (line.isBlank()) return null

        // Detect a new Roblox game session.
        val joinMatch = joinRegex.find(line)

        if (joinMatch != null) {

            val jobId = joinMatch.groupValues[1]
                .takeIf { it.isNotBlank() }

            val placeId = joinMatch.groupValues[2]
                .takeIf { it.isNotBlank() }

            val initialEndpoint = joinMatch.groupValues
                .getOrNull(3)
                ?.takeIf { it.isNotBlank() }

            currentSession = RobloxSession(
                placeId = placeId,
                jobId = jobId,
                endpointIp = initialEndpoint,
                joined = true
            )

            return RobloxLogEvent(
                type = RobloxLogEvent.Type.JOINED,
                session = currentSession.copy()
            )
        }

        if (!currentSession.joined) return null

        // Detect disconnection before processing other updates.
        if (disconnectPatterns.any { it.containsMatchIn(line) }) {

            val endedSession = currentSession.copy()

            currentSession = RobloxSession()

            return RobloxLogEvent(
                type = RobloxLogEvent.Type.DISCONNECTED,
                session = endedSession
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

        // UDMUX is preferred over RCC for location lookup.
        val udmuxAddress = udmuxRegex
            .find(line)
            ?.groupValues
            ?.getOrNull(1)

        val rccAddress = rccRegex
            .find(line)
            ?.groupValues
            ?.getOrNull(1)

        val detectedEndpoint = udmuxAddress ?: rccAddress

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
    }
}
