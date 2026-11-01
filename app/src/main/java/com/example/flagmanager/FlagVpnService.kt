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
    private var isRunning = true

    private val tcpConnections = ConcurrentHashMap<String, TcpState>()
    private val udpSockets = ConcurrentHashMap<String, DatagramSocket>()

    data class TcpState(
        var clientSeq: Long = 0,
        var serverSeq: Long = 0
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { teardown(); stopSelf(); return START_NOT_STICKY }
            ACTION_UPDATE -> {
                val json = intent.getStringExtra(EXTRA_FLAGS) ?: "{}"
                MitmProxy.updateFlags(json)
                return START_STICKY
            }
        }
        val flagsJson = intent?.getStringExtra(EXTRA_FLAGS) ?: "{}"
        postNotification()

        if (tunFd == null) {
            tunnelJob = scope.launch { startPipeline(flagsJson) }
        }
        return START_STICKY
    }

    private suspend fun startPipeline(flagsJson: String) = withContext(Dispatchers.IO) {
        // 1. Start the local proxy
        MitmProxy.start(flagsJson, MitmProxy.PROXY_PORT_HTTP, applicationContext)
        MitmProxy.vpnService = this@FlagVpnService

        // 2. Setup TUN
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
        Log.d(TAG, "TUN + Proxy pipeline live")
    }

    private fun buildTun(cdnIps: List<String>): ParcelFileDescriptor? = runCatching {
        val builder = Builder()
            .setSession("FlagManager VPN")
            .setMtu(TUN_MTU)
            .addAddress(TUN_ADDRESS, TUN_MASK)
            .addDnsServer("8.8.8.8")

        cdnIps.forEach { ip -> if (ip.isNotEmpty()) builder.addRoute(ip, 32) }

        // Allow Roblox and our app to prevent loop
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
        
        val srcIp = "${buffer[12].toInt() and 0xFF}.${buffer[13].toInt() and 0xFF}.${buffer[14].toInt() and 0xFF}.${buffer[15].toInt() and 0xFF}"
        val destIp = "${buffer[16].toInt() and 0xFF}.${buffer[17].toInt() and 0xFF}.${buffer[18].toInt() and 0xFF}.${buffer[19].toInt() and 0xFF}"

        if (protocol == 6) { // TCP (HTTP Request)
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

                if ((flags and 0x02) != 0 && payloadLen == 0) { // SYN
                    state.serverSeq = 1000L
                    sendTcpPacket(seq + 1, state.serverSeq, 0x12, srcIp, srcPort, destIp, destPort)
                } else if (payloadLen > 0) { // HTTP GET
                    val payload = String(buffer, ihl + tcpHeaderLen, payloadLen, Charsets.UTF_8)
                    if (payload.startsWith("GET ")) {
                        handleHttpIntercept(payload, srcIp, srcPort, destIp, destPort, seq + payloadLen, state)
                    }
                }
            }
        } else if (protocol == 17) { // UDP (RakNet Game Traffic)
            val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
            val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
            val udpLen = ((buffer[ihl + 4].toInt() and 0xFF) shl 8) or (buffer[ihl + 5].toInt() and 0xFF)
            val payloadLen = udpLen - 8
            if (payloadLen > 0) {
                val payload = ByteArray(payloadLen)
                System.arraycopy(buffer, ihl + 8, payload, 0, payloadLen)
                handleUdpForward(payload, srcIp, srcPort, destIp, destPort)
            }
        }
    }

    private fun handleUdpForward(payload: ByteArray, srcIp: String, srcPort: Int, destIp: String, destPort: Int) {
        thread {
            try {
                val key = "$srcPort-$destIp-$destPort"
                val socket = udpSockets.getOrPut(key) { 
                    DatagramSocket().also { protect(it) } 
                }
                val address = InetAddress.getByName(destIp)
                socket.send(DatagramPacket(payload, payload.size, address, destPort))

                val responseBuffer = ByteArray(65535)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.soTimeout = 3000
                socket.receive(responsePacket)

                sendUdpPacket(responsePacket.data, responsePacket.length, destIp, destPort, srcIp, srcPort)
            } catch (e: Exception) {
                // Silent catch for UDP timeouts
            }
        }
    }

    private fun handleHttpIntercept(requestText: String, srcIp: String, srcPort: Int, destIp: String, destPort: Int, clientSeq: Long, state: TcpState) {
        thread {
            try {
                // Send to MitmProxy
                val socket = Socket()
                protect(socket)
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), MitmProxy.PROXY_PORT_HTTP), 5000)

                val out = socket.getOutputStream()
                out.write(requestText.toByteArray())
                out.flush()

                val responseText = socket.getInputStream().bufferedReader().readText()
                socket.close()

                // Write back to TUN
                sendTcpPacket(state.serverSeq, clientSeq, 0x18, destIp, destPort, srcIp, srcPort, responseText.toByteArray())
                state.serverSeq += responseText.length

            } catch (e: Exception) {
                Log.e(TAG, "Intercept failed: ${e.message}")
            }
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

        vpnOut.write(packet)
        vpnOut.flush()
    }

    private fun sendTcpPacket(seq: Long, ack: Long, flags: Int, srcIp: String, srcPort: Int, destIp: String, destPort: Int, payload: ByteArray = ByteArray(0)) {
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

        vpnOut.write(packet)
        vpnOut.flush()
    }

    private fun teardown() {
        isRunning = false
        tunnelJob?.cancel()
        runCatching { tunFd?.close() }
        tunFd = null
        tcpConnections.clear()
        udpSockets.values.forEach { runCatching { it.close() } }
        udpSockets.clear()
        MitmProxy.stop()
    }

    private fun postNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "VPN Bypass", NotificationManager.IMPORTANCE_LOW))
        }
        val stopPi = PendingIntent.getService(this, 0, Intent(this, FlagVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TacoStrap VPN active")
            .setContentText("TUN bridge live")
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
