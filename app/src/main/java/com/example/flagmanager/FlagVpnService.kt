package com.example.flagmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class FlagVpnService : VpnService() {

    companion object {
        private const val TAG           = "FlagVpnService"
        private const val CHANNEL_ID    = "vpn_bypass"
        private const val NOTIF_ID      = 9001
        private const val EXTRA_FLAGS   = "extra_flags_json"
        private const val ACTION_UPDATE = "com.example.flagmanager.ACTION_UPDATE_FLAGS"
        private const val ACTION_STOP   = "com.example.flagmanager.ACTION_STOP"
        private const val TUN_ADDRESS   = "10.99.0.1"
        private const val TUN_MASK      = 32
        private const val TUN_MTU       = 1500
        private const val ROBLOX_PKG    = "com.roblox.client"

        private val CDN_HOSTS = listOf(
            "clientsettingscdn.roblox.com",
            "clientsettings.roblox.com"
        )

        /**
         * Returns the VpnService.prepare() intent, or null if the app already
         * holds VPN permission. The flagsJson parameter is kept for source
         * compatibility; the actual flags are delivered by buildDirectIntent()
         * after the permission dialog is granted.
         */
        fun buildStartIntent(context: Context, flagsJson: String): Intent? = prepare(context)

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

        /** Safe start for MainActivity; returns false on failure, never throws. */
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

    private val scope    = CoroutineScope(Dispatchers.IO)
    private var tunnelJob: Job? = null
    private var tunFd    : ParcelFileDescriptor? = null
    private var vpnOutput: FileOutputStream? = null
    @Volatile private var isRunning = true
    private val writeLock = Any()

    private val tcpConnections = ConcurrentHashMap<String, TcpState>()
    private val udpSessions    = ConcurrentHashMap<String, UdpState>()

    data class TcpState(var clientSeq: Long = 0, var serverSeq: Long = 1000L)
    data class UdpState(val socket: DatagramSocket)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { teardown(); stopSelf(); return START_NOT_STICKY }
            ACTION_UPDATE -> {
                val json = intent.getStringExtra(EXTRA_FLAGS) ?: "{}"
                MitmProxy.updateFlags(json)
                // If we were killed and restarted by the system, re-establish the tunnel
                if (tunFd == null && isRunning) {
                    postNotification()
                    tunnelJob?.cancel()
                    tunnelJob = scope.launch { startPipeline(json) }
                }
                return START_STICKY
            }
        }
        val flagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"
        postNotification()

        if (tunFd == null) {
            isRunning = true
            tunnelJob?.cancel()
            tunnelJob = scope.launch { startPipeline(flagsJson) }
        } else {
            // Already running — just refresh the injected flags
            MitmProxy.updateFlags(flagsJson)
        }
        return START_STICKY
    }

    private suspend fun startPipeline(flagsJson: String) = withContext(Dispatchers.IO) {
        MitmProxy.start(flagsJson, MitmProxy.PROXY_PORT_HTTP, applicationContext)
        MitmProxy.vpnService = this@FlagVpnService

        val cdnIps = resolveCdnHosts()
        if (cdnIps.isEmpty()) {
            Log.e(TAG, "DNS failed")
            stopSelf()
            return@withContext
        }

        tunFd = buildTun(cdnIps)
        if (tunFd == null) {
            Log.e(TAG, "TUN failed")
            stopSelf()
            return@withContext
        }

        startTunnelLoop()
        Log.d(TAG, "TUN + Proxy pipeline live (HTTPS + HTTP)")
    }

    private fun buildTun(cdnIps: List<String>): ParcelFileDescriptor? = runCatching {
        val builder = Builder()
            .setSession("TacoStrap VPN")
            .setMtu(TUN_MTU)
            .addAddress(TUN_ADDRESS, TUN_MASK)
            .addDnsServer("8.8.8.8")

        cdnIps.forEach { ip -> if (ip.isNotEmpty()) builder.addRoute(ip, 32) }
        runCatching { builder.addAllowedApplication(ROBLOX_PKG) }
        runCatching { builder.addAllowedApplication(packageName) }

        builder.establish()
    }.onFailure { Log.e(TAG, "buildTun error: ${it.message}") }.getOrNull()

    private fun resolveCdnHosts(): List<String> =
        CDN_HOSTS.flatMap { host ->
            runCatching {
                InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.filter { it.isNotEmpty() }
            }.getOrElse { emptyList() }
        }.distinct()

    private fun startTunnelLoop() {
        val fd = tunFd ?: return
        val input = FileInputStream(fd.fileDescriptor)
        vpnOutput = FileOutputStream(fd.fileDescriptor)

        thread(name = "TunLoop", isDaemon = true) {
            val buffer = ByteArray(65535)
            while (isRunning && tunFd != null) {
                try {
                    val length = input.read(buffer)
                    if (length > 0) processIpPacket(buffer, length)
                } catch (e: Exception) {
                    Log.w(TAG, "TUN read error: ${e.message}")
                    break
                }
            }
        }
    }

    private fun processIpPacket(buffer: ByteArray, length: Int) {
        if (length < 20) return
        val ihl = (buffer[0].toInt() and 0x0F) * 4
        val protocol = buffer[9].toInt() and 0xFF

        val srcIp  = "${buffer[12].toInt() and 0xFF}.${buffer[13].toInt() and 0xFF}.${buffer[14].toInt() and 0xFF}.${buffer[15].toInt() and 0xFF}"
        val destIp = "${buffer[16].toInt() and 0xFF}.${buffer[17].toInt() and 0xFF}.${buffer[18].toInt() and 0xFF}.${buffer[19].toInt() and 0xFF}"

        if (protocol == 6) { // TCP
            if (length < ihl + 20) return
            val srcPort  = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
            val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)

            val seq = ByteBuffer.wrap(buffer, ihl + 4, 4).int.toLong() and 0xFFFFFFFFL
            val ack = ByteBuffer.wrap(buffer, ihl + 8, 4).int.toLong() and 0xFFFFFFFFL
            val flags = buffer[ihl + 13].toInt() and 0xFF
            val tcpHeaderLen = ((buffer[ihl + 12].toInt() and 0xF0) shr 4) * 4

            if (length < ihl + tcpHeaderLen) return
            val payloadLen = length - ihl - tcpHeaderLen
            val connKey = "$srcPort-$destIp-$destPort"

            val state = tcpConnections.getOrPut(connKey) { TcpState() }

            // SYN
            if ((flags and 0x02) != 0 && (flags and 0x10) == 0) {
                state.clientSeq = seq + 1
                sendTcpPacket(state.serverSeq, state.clientSeq, 0x12, srcIp, srcPort, destIp, destPort)
                return
            }

            // Handle both port 80 (plain HTTP) and port 443 (TLS → MITM proxy)
            if (payloadLen > 0) {
                state.clientSeq = seq + payloadLen
                // ACK the data so the client doesn't retransmit
                sendTcpPacket(state.serverSeq, state.clientSeq, 0x10, srcIp, srcPort, destIp, destPort)

                val payload = ByteArray(payloadLen)
                System.arraycopy(buffer, ihl + tcpHeaderLen, payload, 0, payloadLen)

                when (destPort) {
                    80  -> handleHttpIntercept(payload, srcIp, srcPort, destIp, destPort, state, tls = false)
                    443 -> handleHttpIntercept(payload, srcIp, srcPort, destIp, destPort, state, tls = true)
                }
            } else if ((flags and 0x01) != 0) {
                // FIN — close our side
                tcpConnections.remove(connKey)
            }
        } else if (protocol == 17) { // UDP (game traffic)
            if (length < ihl + 8) return
            val srcPort  = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
            val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)

            val payloadLen = length - ihl - 8
            if (payloadLen > 0) {
                val payload = ByteArray(payloadLen)
                System.arraycopy(buffer, ihl + 8, payload, 0, payloadLen)
                forwardUdpTraffic(payload, srcIp, srcPort, destIp, destPort)
            }
        }
    }

    /**
     * Called for both plain HTTP (port 80) and TLS (port 443). For TLS, the
     * payload is the raw TLS ClientHello; we relay it to the local MITM proxy
     * which terminates TLS with our CA and injects flags.
     *
     * NOTE: this is a minimal 1-request/1-response bridge. It works for the
     * small settings JSON fetches Roblox makes at startup. It is not a full
     * TCP stack.
     */
    private fun handleHttpIntercept(
        requestPayload: ByteArray,
        srcIp: String, srcPort: Int,
        destIp: String, destPort: Int,
        state: TcpState,
        tls: Boolean
    ) {
        thread(isDaemon = true) {
            var proxySocket: Socket? = null
            try {
                proxySocket = Socket()
                protect(proxySocket)
                val proxyPort = if (tls) MitmProxy.PROXY_PORT_HTTPS else MitmProxy.PROXY_PORT_HTTP
                proxySocket.connect(
                    InetSocketAddress(InetAddress.getLoopbackAddress(), proxyPort), 5000
                )
                proxySocket.soTimeout = 8000

                // For TLS the proxy expects a TLS ClientHello and will respond
                // with a TLS ServerHello + cert signed by our CA.
                val out = proxySocket.getOutputStream()
                out.write(requestPayload)
                out.flush()

                val responseBytes = proxySocket.getInputStream().readBytes()
                if (responseBytes.isEmpty()) return@thread

                // Send the response back into the TUN. We send in chunks small
                // enough that the IP packet fits the MTU.
                val maxChunk = TUN_MTU - 20 - 20
                var offset = 0
                while (offset < responseBytes.size) {
                    val chunkLen = minOf(maxChunk, responseBytes.size - offset)
                    val chunk = ByteArray(chunkLen)
                    System.arraycopy(responseBytes, offset, chunk, 0, chunkLen)
                    sendTcpPacket(
                        state.serverSeq, state.clientSeq, 0x18,
                        destIp, destPort, srcIp, srcPort, chunk
                    )
                    state.serverSeq += chunkLen
                    offset += chunkLen
                }
            } catch (e: Exception) {
                Log.w(TAG, "Intercept (tls=$tls) failed: ${e.message}")
            } finally {
                runCatching { proxySocket?.close() }
            }
        }
    }

    private fun forwardUdpTraffic(payload: ByteArray, srcIp: String, srcPort: Int, destIp: String, destPort: Int) {
        val key = "$srcPort-$destIp-$destPort"

        val state = udpSessions.getOrPut(key) {
            val socket = DatagramSocket().also { protect(it) }
            val newState = UdpState(socket)

            thread(isDaemon = true) {
                val responseBuffer = ByteArray(65535)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                while (isRunning && !socket.isClosed) {
                    try {
                        socket.receive(responsePacket)
                        sendUdpPacket(responsePacket.data, responsePacket.length, destIp, destPort, srcIp, srcPort)
                    } catch (e: Exception) {
                        break
                    }
                }
            }
            newState
        }

        try {
            state.socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(destIp), destPort))
        } catch (e: Exception) {
            Log.e(TAG, "UDP send failed: ${e.message}")
        }
    }

    private fun sendUdpPacket(payload: ByteArray, length: Int, srcIp: String, srcPort: Int, destIp: String, destPort: Int) {
        val vpnOut = vpnOutput ?: return
        val totalLen = 20 + 8 + length
        val buffer = ByteBuffer.allocate(totalLen)

        buffer.put(0x45); buffer.put(0); buffer.putShort(totalLen.toShort())
        buffer.putShort(0); buffer.putShort(0x4000); buffer.put(64); buffer.put(17); buffer.putShort(0)
        buffer.put(InetAddress.getByName(srcIp).address)
        buffer.put(InetAddress.getByName(destIp).address)

        buffer.putShort(srcPort.toShort()); buffer.putShort(destPort.toShort())
        buffer.putShort((8 + length).toShort()); buffer.putShort(0)
        buffer.put(payload, 0, length)

        val packet = buffer.array()
        var ipSum = 0
        for (i in 0 until 20 step 2) ipSum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        while (ipSum shr 16 > 0) ipSum = (ipSum and 0xFFFF) + (ipSum shr 16)
        val ipChecksum = ipSum.inv() and 0xFFFF
        packet[10] = (ipChecksum shr 8).toByte(); packet[11] = ipChecksum.toByte()

        synchronized(writeLock) {
            vpnOut.write(packet); vpnOut.flush()
        }
    }

    private fun sendTcpPacket(
        seq: Long, ack: Long, flags: Int,
        srcIp: String, srcPort: Int, destIp: String, destPort: Int,
        payload: ByteArray = ByteArray(0)
    ) {
        val vpnOut = vpnOutput ?: return
        val tcpHeaderLen = 20
        val totalLen = 20 + tcpHeaderLen + payload.size
        val buffer = ByteBuffer.allocate(totalLen)

        buffer.put(0x45); buffer.put(0); buffer.putShort(totalLen.toShort())
        buffer.putShort(0); buffer.putShort(0x4000); buffer.put(64); buffer.put(6); buffer.putShort(0)
        buffer.put(InetAddress.getByName(srcIp).address)
        buffer.put(InetAddress.getByName(destIp).address)

        buffer.putShort(srcPort.toShort()); buffer.putShort(destPort.toShort())
        buffer.putInt(seq.toInt()); buffer.putInt(ack.toInt())
        buffer.put((tcpHeaderLen / 4 shl 4).toByte()); buffer.put(flags.toByte())
        buffer.putShort(65535.toShort()); buffer.putShort(0); buffer.putShort(0)
        buffer.put(payload)

        val packet = buffer.array()
        var ipSum = 0
        for (i in 0 until 20 step 2) ipSum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        while (ipSum shr 16 > 0) ipSum = (ipSum and 0xFFFF) + (ipSum shr 16)
        val ipChecksum = ipSum.inv() and 0xFFFF
        packet[10] = (ipChecksum shr 8).toByte(); packet[11] = ipChecksum.toByte()

        var tcpSum = 0
        tcpSum += ((InetAddress.getByName(srcIp).address[0].toInt() and 0xFF) shl 8) or (InetAddress.getByName(srcIp).address[1].toInt() and 0xFF)
        tcpSum += ((InetAddress.getByName(srcIp).address[2].toInt() and 0xFF) shl 8) or (InetAddress.getByName(srcIp).address[3].toInt() and 0xFF)
        tcpSum += ((InetAddress.getByName(destIp).address[0].toInt() and 0xFF) shl 8) or (InetAddress.getByName(destIp).address[1].toInt() and 0xFF)
        tcpSum += ((InetAddress.getByName(destIp).address[2].toInt() and 0xFF) shl 8) or (InetAddress.getByName(destIp).address[3].toInt() and 0xFF)
        tcpSum += 6; tcpSum += tcpHeaderLen + payload.size

        for (i in 20 until totalLen step 2) {
            val high = packet[i].toInt() and 0xFF
            val low = if (i + 1 < totalLen) packet[i + 1].toInt() and 0xFF else 0
            tcpSum += (high shl 8) or low
        }
        while (tcpSum shr 16 > 0) tcpSum = (tcpSum and 0xFFFF) + (tcpSum shr 16)
        val tcpChecksum = tcpSum.inv() and 0xFFFF
        packet[36] = (tcpChecksum shr 8).toByte(); packet[37] = tcpChecksum.toByte()

        synchronized(writeLock) {
            vpnOut.write(packet); vpnOut.flush()
        }
    }

    private fun teardown() {
        isRunning = false
        tunnelJob?.cancel()
        tunnelJob = null
        runCatching { tunFd?.close() }
        tunFd = null
        runCatching { vpnOutput?.close() }
        vpnOutput = null
        tcpConnections.clear()
        udpSessions.values.forEach { runCatching { it.socket.close() } }
        udpSessions.clear()
        MitmProxy.stop()
    }

    private fun postNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN Bypass", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, FlagVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TacoStrap VPN active")
            .setContentText("HTTPS + HTTP interception live")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .setOngoing(true).build())
    }

    override fun onDestroy() {
        teardown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

/**
 * Conservative fallback. Delete this object if you already have a full
 * AntiBan.kt in the project — this stub only exists so the build succeeds.
 */
object AntiBan {
    private val TELEMETRY_PATHS = listOf(
        "/telemetry", "/analytics", "/log", "/metrics", "/beacon", "/collect"
    )
    private val BLOCKED_HEADERS = setOf(
        "if-none-match", "if-modified-since"
    )

    fun isTelemetryPath(path: String): Boolean {
        val p = path.substringBefore('?').lowercase()
        return TELEMETRY_PATHS.any { p.startsWith(it) || p.contains("$it/") }
    }

    fun isBlockedHeader(name: String): Boolean = name.lowercase() in BLOCKED_HEADERS
}

/**
 * Conservative fallback. Delete this object if you already have a full
 * DesyncInjector.kt in the project.
 */
object DesyncInjector {
    private val DESYNC_FLAGS = mapOf(
        "DFIntPhysicsSenderMaxBandwidthBps"       to "100000",
        "DFIntPhysicsSenderRate"                  to "1",
        "DFIntDataSenderMaxBandwidthBps"          to "100000",
        "DFIntMinimalIntervalBetweenPhysicsSendMS" to "1"
    )

    fun ensureInMap(map: MutableMap<String, String>) {
        DESYNC_FLAGS.forEach { (k, v) -> map.putIfAbsent(k, v) }
    }

    fun removeFromMap(map: MutableMap<String, String>) {
        DESYNC_FLAGS.keys.forEach { map.remove(it) }
    }
}
