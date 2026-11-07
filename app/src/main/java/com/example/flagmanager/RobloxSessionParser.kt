
package com.example.flagmanager

data class RobloxSession(
    var placeId: String? = null,
    var jobId: String? = null,
    var endpointIp: String? = null,
    var joined: Boolean = false,
    var loading: Boolean = false
)

data class RobloxLogEvent(
    val type: Type,
    val session: RobloxSession
) {
    enum class Type {
        JOINED,
        ENDPOINT_UPDATED,
        DISCONNECTED
    }
}

class RobloxSessionParser {

    private var current = RobloxSession()

    private val joinRegex = Regex(
        """Joining game ['"]([^'"]+)['"] place (\d+)(?: at ([0-9a-fA-F:.]+))?"""
    )

    private val udmuxRegex = Regex(
        """UDMUX Address\s*=\s*([0-9a-fA-F:.]+)"""
    )

    private val rccRegex = Regex(
        """RCC Server Address\s*=\s*([0-9a-fA-F:.]+)"""
    )

    fun parse(line: String): RobloxLogEvent? {

        val join = joinRegex.find(line)

        if (join != null) {
            current = RobloxSession(
                jobId = join.groupValues[1],
                placeId = join.groupValues[2],
                endpointIp = join.groupValues.getOrNull(3)
                    ?.takeIf { it.isNotBlank() },
                joined = true
            )

            return RobloxLogEvent(
                RobloxLogEvent.Type.JOINED,
                current.copy()
            )
        }

        if (!current.joined) return null

        if (line.contains("game_join_loadtime", true)) {
            current.loading = true
        }

        val rcc = rccRegex.find(line)?.groupValues?.get(1)
        val udmux = udmuxRegex.find(line)?.groupValues?.get(1)

        val endpoint = rcc ?: udmux

        if (endpoint != null && endpoint != current.endpointIp) {
            current.endpointIp = endpoint

            return RobloxLogEvent(
                RobloxLogEvent.Type.ENDPOINT_UPDATED,
                current.copy()
            )
        }

        val disconnected =
            line.contains("Disconnect", true) ||
            line.contains("Leaving game", true) ||
            line.contains("Connection lost", true)

        if (disconnected) {
            val ended = current.copy()
            current = RobloxSession()

            return RobloxLogEvent(
                RobloxLogEvent.Type.DISCONNECTED,
                ended
            )
        }

        return null
    }

    fun reset() {
        current = RobloxSession()
    }
}
