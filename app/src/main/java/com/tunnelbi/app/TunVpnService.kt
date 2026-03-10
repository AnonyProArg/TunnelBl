package com.tunnelbi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import go.Seq
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification as LibboxNotification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState

class TunVpnService : VpnService(), PlatformInterface {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val CHANNEL_ID = "tunnelbi_vpn"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var boxService: BoxService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTunnel()
            ACTION_STOP -> stopTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        startForeground(1, buildNotification())
        Seq.touch()
        try {
            boxService = Libbox.newService(buildConfig(), this)
            boxService?.start()
        } catch (e: Exception) {
            android.util.Log.e("TunnelBI", "start error: ${e.message}")
            stopSelf()
        }
    }

    private fun stopTunnel() {
        try {
            boxService?.close()
            boxService = null
        } catch (e: Exception) {
            android.util.Log.e("TunnelBI", "stop error: ${e.message}")
        }
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel()
    }

    override fun openTun(options: TunOptions): Int {
        val builder = Builder()
            .setSession("TunnelBI")
            .setMtu(options.getMTU())

        val inet4Address: RoutePrefixIterator = options.getInet4Address()
        while (inet4Address.hasNext()) {
            val prefix = inet4Address.next()
            builder.addAddress(prefix.address(), prefix.prefix())
        }

        val inet4Routes: RoutePrefixIterator = options.getInet4RouteAddress()
        while (inet4Routes.hasNext()) {
            val route = inet4Routes.next()
            builder.addRoute(route.address(), route.prefix())
        }

        val dns = options.getDNSServerAddress()
        builder.addDnsServer(dns.getValue() ?: "8.8.8.8")

        vpnInterface = builder.establish()
        return vpnInterface?.detachFd() ?: -1
    }

    override fun autoDetectInterfaceControl(fd: Int) { protect(fd) }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String, sourcePort: Int,
        destinationAddress: String, destinationPort: Int
    ): Int = 0

    override fun packageNameByUid(uid: Int): String = ""

    override fun uidByPackageName(packageName: String): Int = 0

    override fun getInterfaces(): NetworkInterfaceIterator? = null

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState = Libbox.newWIFIState("", "")

    override fun sendNotification(notification: LibboxNotification) {}

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun systemCertificates(): StringIterator? = null

    override fun writeLog(message: String) {
        android.util.Log.d("TunnelBI", message)
    }

    private fun buildConfig() = """
{
  "log": { "level": "warn" },
  "dns": {
    "servers": [{
      "tag": "remote",
      "address": "8.8.8.8"
    }]
  },
  "inbounds": [{
    "type": "tun",
    "interface_name": "tun0",
    "inet4_address": "172.19.0.1/30",
    "auto_route": true,
    "strict_route": false,
    "stack": "system",
    "mtu": 9000
  }],
  "outbounds": [
    {
      "type": "vless",
      "tag": "proxy",
      "server": "$LOCAL_HOST",
      "server_port": $LOCAL_PORT,
      "uuid": "11111111-1111-1111-1111-111111111111",
      "packet_encoding": "xudp",
      "tls": {
        "enabled": false
      },
      "multiplex": {
        "enabled": true,
        "protocol": "smux",
        "max_streams": 32,
        "padding": false
      }
    },
    {
      "type": "direct",
      "tag": "direct"
    }
  ],
  "route": {
    "final": "proxy"
  }
}
""".trimIndent() 

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, "TunnelBI VPN",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TunnelBI")
            .setContentText("VPN activa")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}
