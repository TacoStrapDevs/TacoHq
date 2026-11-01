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
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
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

        @Volatile var activeFlagsMap: Map<String, String> = emptyMap()

        fun buildStartIntent(context: Context, flagsJson: String): Intent? = prepare(context)

        fun buildDirectIntent(context: Context, flagsJson: String): Intent =
            Intent(context, FlagVpnService::class.java).putExtra(EXTRA_FLAGS, flagsJson)

        fun updateFlags(context: Context, flagsJson: String) {
            context.startService(
                Intent(context, FlagVpnService::class.java)
                    .setAction(ACTION_UPDATE)
                    .putExtra(EXTRA_FLAGS, flagsJson)
            )
        }
    }

    private val scope    = CoroutineScope(Dispatchers.IO)
    private var tunnelJob : Job? = null
    private var tunFd    : ParcelFileDescriptor? = null
    private var vpnOutput : FileOutputStream? = null

    private val tcpConnections = ConcurrentHashMap<String, TcpState>()

    data class TcpState(
        var clientSeq: Long = 0,
        var serverSeq: Long = 0,
        var socket: Socket? = null
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { teardown(); stopSelf(); return START_NOT_STICKY }
            ACTION_UPDATE -> {
                val json = intent.getStringExtra(EXTRA_FLAGS) ?: "{}"
                updateFlagsMap(json)
                return START_STICKY
            }
        }
        val flagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"
        updateFlagsMap(flagsJson)
        postNotification()

        if (tunFd == null) {
            tunnelJob = scope.launch { startPipeline() }
        }
        return START_STICKY
    }

    private fun updateFlagsMap(json: String) {
        activeFlagsMap = try {
            val root = JSONObject(json)
            val map = mutableMapOf<String, String>()
            root.keys().forEach { key -> map[key] = root.get(key).toString() }
            map
        } catch (_: Throwable) { emptyMap() }
    }

    private suspend fun startPipeline() = withContext(Dispatchers.IO) {
        val cdnIps = resolveCdnHosts()
        if (cdnIps.isEmpty()) { 
            Log.e(TAG, "DNS resolution failed. Stopping.")
            stopSelf()
            return@withContext 
        }

        tunFd = buildTun(cdnIps)
        if (tunFd == null) { 
            Log.e(TAG, "TUN build failed. Stopping.")
            stopSelf()
            return@withContext 
        }

        startTunnelLoop()
        Log.d(TAG, "TUN pipeline live")
    }

    private fun buildTun(cdnIps: List<String>): ParcelFileDescriptor? = runCatching {
        val builder = Builder()
            .setSession("FlagManager VPN")
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
            while (tunFd != null) {
                try {
                    val length = input.read(buffer)
                    if (length > 0) {
                        processIpPacket(buffer, length)
                    }
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
        if (protocol != 6) return // TCP only

        val srcIp = "${buffer[12].toInt() and 0xFF}.${buffer[13].toInt() and 0xFF}.${buffer[14].toInt() and 0xFF}.${buffer[15].toInt() and 0xFF}"
        val destIp = "${buffer[16].toInt() and 0xFF}.${buffer[17].toInt() and 0xFF}.${buffer[18].toInt() and 0xFF}.${buffer[19].toInt() and 0xFF}"
        val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
        val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)

        val seq = ByteBuffer.wrap(buffer, ihl + 4, 4).int.toLong() and 0xFFFFFFFFL
        val flags = buffer[ihl + 13].toInt() and 0xFF
        val tcpHeaderLen = ((buffer[ihl + 12].toInt() and 0xF0) shr 4) * 4
        val payloadLen = length - ihl - tcpHeaderLen

        val connKey = "$srcPort-$destIp-$destPort"

        if (destPort == 80) {
            val state = tcpConnections.getOrPut(connKey) { TcpState() }
            state.clientSeq = seq

            // Handle SYN
            if ((flags and 0x02) != 0 && payloadLen == 0) {
                sendTcpResponse(seq + 1, 0, 0x12, srcIp, srcPort, destIp, destPort) // SYN-ACK
                state.serverSeq = 1
            }
            // Handle ACK (Handshake complete)
            else if ((flags and 0x10) != 0 && payloadLen == 0) {
                // Ready for HTTP GET
            }
            // Handle PSH, ACK (HTTP GET)
            else if (payloadLen > 0) {
                val payload = String(buffer, ihl + tcpHeaderLen, payloadLen, Charsets.UTF_8)
                if (payload.startsWith("GET ")) {
                    val newClientSeq = seq + payloadLen
                    handleHttpIntercept(payload, srcIp, srcPort, destIp, destPort, newClientSeq)
                }
            }
        }
    }

    private fun handleHttpIntercept(requestText: String, srcIp: String, srcPort: Int, destIp: String, destPort: Int, clientSeq: Long) {
        thread {
            try {
                val lines = requestText.split("\r\n")
                val path = lines[0].split(" ")[1]

                val socket = Socket()
                protect(socket) // Protect proxy socket
                socket.connect(InetSocketAddress(InetAddress.getByName(destIp), destPort), 5000)

                val out = socket.getOutputStream()
                val reqBuilder = StringBuilder("GET $path HTTP/1.1\r\nHost: $destIp\r\n")
                lines.drop(1).takeWhile { it.isNotEmpty() }.forEach { reqBuilder.append("$it\r\n") }
                reqBuilder.append("Connection: close\r\n\r\n")
                out.write(reqBuilder.toString().toByteArray())
                out.flush()

                val responseText = socket.getInputStream().bufferedReader().readText()
                socket.close()

                var finalBody = responseText
                if (path.contains("settings", true)) {
                    val headerEnd = responseText.indexOf("\r\n\r\n")
                    if (headerEnd != -1) {
                        val headers = responseText.substring(0, headerEnd)
                        val bodyStr = responseText.substring(headerEnd + 4)
                        val root = JSONObject(bodyStr)
                        val settings = root.optJSONObject("applicationSettings") ?: JSONObject()
                        activeFlagsMap.forEach { (k, v) -> settings.put(k, v) }
                        root.put("applicationSettings", settings)
                        finalBody = "$headers\r\n\r\n${root.toString()}"
                    }
                }

                sendTcpData(finalBody.toByteArray(), srcIp, srcPort, destIp, destPort, clientSeq)

            } catch (e: Exception) {
                Log.e(TAG, "Intercept failed: ${e.message}")
            }
        }
    }

    private fun sendTcpData(payload: ByteArray, destIp: String, destPort: Int, srcIp: String, srcPort: Int, clientSeq: Long) {
        val state = tcpConnections["$srcPort-$destIp-$destPort"] ?: return
        val serverSeq = state.serverSeq
        
        sendTcpResponse(serverSeq, clientSeq, 0x18, destIp, destPort, srcIp, srcPort, payload)
        
        state.serverSeq += payload.size
    }

    private fun sendTcpResponse(seq: Long, ack: Long, flags: Int, srcIp: String, srcPort: Int, destIp: String, destPort: Int, payload: ByteArray = ByteArray(0)) {
        val vpnOut = vpnOutput ?: return
        val tcpHeaderLen = 20
        val totalLen = 20 + tcpHeaderLen + payload.size
        val buffer = ByteBuffer.allocate(totalLen)

        buffer.put(0x45)
        buffer.put(0)
        buffer.putShort(totalLen.toShort())
        buffer.putShort(0)
        buffer.putShort(0x4000)
        buffer.put(64)
        buffer.put(6)
        buffer.putShort(0)

        buffer.put(InetAddress.getByName(srcIp).address)
        buffer.put(InetAddress.getByName(destIp).address)

        buffer.putShort(srcPort.toShort())
        buffer.putShort(destPort.toShort())
        buffer.putInt(seq.toInt())
        buffer.putInt(ack.toInt())
        buffer.put((tcpHeaderLen / 4 shl 4).toByte())
        buffer.put(flags.toByte())
        buffer.putShort(65535.toShort())
        buffer.putShort(0)
        buffer.putShort(0)

        buffer.put(payload)

        vpnOut.write(buffer.array())
        vpnOut.flush()
    }

    private fun teardown() {
        tunnelJob?.cancel()
        runCatching { tunFd?.close() }
        tunFd = null
        tcpConnections.clear()
    }

    private fun postNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "VPN Bypass", NotificationManager.IMPORTANCE_LOW))
        }
        val stopPi = PendingIntent.getService(this, 0, Intent(this, FlagVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TacoStrap VPN active")
            .setContentText("TUN TCP bridge live")
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
