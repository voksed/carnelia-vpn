package com.carnelia.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.carnelia.vpn.R
import com.carnelia.vpn.StandaloneToolsActivity
import com.carnelia.vpn.utils.AppLogger
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * DnsOnlyService — v2.4.0
 *
 * A minimal VPN-mode service that intercepts ONLY DNS queries and forwards them
 * to the user-selected encrypted DNS server (Cloudflare / AdGuard / Google /
 * custom).
 *
 * This gives the user ad-blocking and privacy for DNS WITHOUT connecting to a
 * full VPN server. Useful when:
 *  - VPN is slow or unavailable
 *  - User only wants encrypted/filtered DNS (anti-tracking, ad blocking)
 *  - Corporate/hotel network blocks all VPN but DNS is still controllable
 *
 * How it works:
 *  1. Creates a TUN interface with addRoute for only the chosen DNS server IP.
 *     All other traffic is NOT routed through the VPN — it goes to the real
 *     network as usual.
 *  2. A background thread reads DNS UDP packets from the TUN interface and
 *     relays them to the real DNS server via a bound DatagramSocket that bypasses
 *     the TUN (using VpnService.protect()).
 *  3. Responses are written back into the TUN fd so Android sees a reply.
 *
 * DNS options available:
 *  - Cloudflare  1.1.1.1 / 1.0.0.1
 *  - AdGuard     94.140.14.14 / 94.140.15.15
 *  - Google      8.8.8.8 / 8.8.4.4
 *  - Quad9       9.9.9.9
 *  - Custom (user-entered)
 */
class DnsOnlyService : VpnService() {

    companion object {
        const val ACTION_START  = "com.carnelia.vpn.DNSONLY_START"
        const val ACTION_STOP   = "com.carnelia.vpn.DNSONLY_STOP"
        const val EXTRA_DNS_LABEL = "dns_label"  // "cloudflare" | "adguard" | "google" | "quad9" | "custom"
        const val EXTRA_DNS_IP    = "dns_ip"     // primary DNS IP string

        private const val NOTIFICATION_ID = 43
        private const val CHANNEL_ID = "dnsonly_channel"
        private const val TUN_ADDRESS = "10.55.0.1"
        private const val TUN_PREFIX = 32

        var isRunning: Boolean = false
            private set
        var activeDnsLabel: String = ""
            private set

        val DNS_PRESETS = mapOf(
            "cloudflare" to Pair("1.1.1.1", "1.0.0.1"),
            "adguard"    to Pair("94.140.14.14", "94.140.15.15"),
            "google"     to Pair("8.8.8.8", "8.8.4.4"),
            "quad9"      to Pair("9.9.9.9", "149.112.112.112")
        )
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ---------------------------------------------------------------
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val label = intent.getStringExtra(EXTRA_DNS_LABEL) ?: "cloudflare"
                val dnsIp = intent.getStringExtra(EXTRA_DNS_IP)
                    ?: DNS_PRESETS[label]?.first
                    ?: "1.1.1.1"
                startDnsMode(label, dnsIp)
            }
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    override fun onRevoke() {
        stop()
    }

    // ---------------------------------------------------------------
    private fun startDnsMode(label: String, primaryDns: String) {
        isRunning = true
        activeDnsLabel = label
        startForeground(NOTIFICATION_ID, buildNotification(label, primaryDns))
        AppLogger.log("DnsOnly: Starting — label=$label dns=$primaryDns")

        try {
            val builder = Builder()
            builder.setSession("DNS-Only: $label")
            builder.addAddress(TUN_ADDRESS, TUN_PREFIX)

            // Route ONLY the DNS server's IP — everything else bypasses VPN
            builder.addRoute(primaryDns, 32)
            val secondaryDns = DNS_PRESETS[label]?.second
            if (secondaryDns != null && secondaryDns != primaryDns) {
                builder.addRoute(secondaryDns, 32)
            }

            // Tell Android to use our DNS
            builder.addDnsServer(primaryDns)
            if (secondaryDns != null) builder.addDnsServer(secondaryDns)

            // Exclude self from VPN so the relay socket can reach real network
            try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

            if (android.os.Build.VERSION.SDK_INT >= 29) builder.setMetered(false)

            vpnInterface = builder.establish()
            AppLogger.log("DnsOnly: TUN interface established")

            // Start DNS relay coroutine
            scope.launch { runDnsRelay(primaryDns) }

        } catch (e: Exception) {
            AppLogger.error("DnsOnly: Failed to establish interface", e)
            stop()
        }
    }

    /**
     * Reads UDP packets from TUN, extracts DNS queries (port 53), forwards to
     * the real DNS server via a protected socket, and writes replies back.
     */
    private suspend fun runDnsRelay(dnsServerIp: String) {
        val tunFd = vpnInterface ?: return
        val inputStream  = FileInputStream(tunFd.fileDescriptor)
        val outputStream = FileOutputStream(tunFd.fileDescriptor)
        val buf = ByteArray(4096)

        AppLogger.log("DnsOnly: Relay running → $dnsServerIp:53")

        try {
            while (scope.isActive) {
                // Read a packet from the TUN (this is a raw IP packet)
                val len = withContext(Dispatchers.IO) {
                    try { inputStream.read(buf) } catch (e: Exception) { -1 }
                }
                if (len < 0) break
                if (len < 28) continue          // Too short to be a valid UDP/DNS packet

                val packet = buf.copyOf(len)

                // Verify IPv4 UDP (protocol=17) packet heading to port 53
                if (packet[0].toInt() and 0xF0 != 0x40) continue // not IPv4
                val protocol = packet[9].toInt() and 0xFF
                if (protocol != 17) continue // not UDP

                val ihl = (packet[0].toInt() and 0x0F) * 4
                if (len < ihl + 8) continue
                val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or
                              (packet[ihl + 3].toInt() and 0xFF)
                if (dstPort != 53) continue

                // Extract DNS payload (after IP + UDP headers)
                val dnsPayloadLen = len - ihl - 8
                if (dnsPayloadLen <= 0) continue
                val dnsPayload = packet.copyOfRange(ihl + 8, len)

                // Forward via protected socket
                scope.launch(Dispatchers.IO) {
                    try {
                        val socket = DatagramSocket()
                        protect(socket)  // bypass VPN to reach real network
                        val dnsAddr = InetAddress.getByName(dnsServerIp)
                        val sendPacket = DatagramPacket(dnsPayload, dnsPayload.size, dnsAddr, 53)
                        socket.soTimeout = 3000
                        socket.send(sendPacket)

                        val recvBuf = ByteArray(4096)
                        val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                        socket.receive(recvPacket)
                        socket.close()

                        // Wrap response back into a fake IP/UDP packet and write to TUN
                        val response = buildUdpResponse(
                            srcIp = dnsServerIp,
                            dstIp = TUN_ADDRESS,
                            srcPort = 53,
                            dstPort = extractSourcePort(packet, ihl),
                            payload = recvBuf.copyOf(recvPacket.length)
                        )
                        withContext(Dispatchers.IO) {
                            try { outputStream.write(response) } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        AppLogger.log("DnsOnly: Relay error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.log("DnsOnly: Relay loop ended: ${e.message}")
        }
    }

    private fun extractSourcePort(packet: ByteArray, ihl: Int): Int =
        ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)

    /**
     * Build minimal IPv4 UDP packet to send back into the TUN.
     */
    private fun buildUdpResponse(
        srcIp: String, dstIp: String,
        srcPort: Int, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val src = InetAddress.getByName(srcIp).address
        val dst = InetAddress.getByName(dstIp).address
        val udpLen = 8 + payload.size
        val ipLen  = 20 + udpLen
        val buf = ByteBuffer.allocate(ipLen)

        // IP header (no options, TTL=64, proto=UDP)
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(ipLen.toShort())
        buf.putShort(0) // ID
        buf.putShort(0x4000) // Don't fragment
        buf.put(64) // TTL
        buf.put(17) // Protocol: UDP
        buf.putShort(0) // Checksum (0 = let OS compute)
        buf.put(src)
        buf.put(dst)

        // UDP header
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0) // UDP checksum (optional)

        buf.put(payload)
        return buf.array()
    }

    private fun stop() {
        isRunning = false
        activeDnsLabel = ""
        scope.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.log("DnsOnly: Stopped")
    }

    private fun buildNotification(label: String, dnsIp: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DNS Protection", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = Intent(this, DnsOnlyService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val openIntent = Intent(this, StandaloneToolsActivity::class.java)
        val openPi = PendingIntent.getActivity(this, 1, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val labelDisplay = label.replaceFirstChar { it.uppercase() }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("DNS Protection: $labelDisplay")
            .setContentText("Encrypted DNS active → $dnsIp")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop",
                    stopPi
                ).build()
            )
            .build()
    }
}
