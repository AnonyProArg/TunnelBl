package com.tunnelbi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import go.Seq
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunOptions

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
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun stopTunnel() {
        try {
            boxService?.close()
            boxService = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel()
    }

    // PlatformInterface — libbox nos pide el FD del TUN
    override fun openTun(options: TunOptions): Int {
        val builder = Builder()
            .setSession("TunnelBI")
            .setMtu(options.mtu.toInt())

        options.inet4Addresses?.let {
            for (i in 0 until it.len()) {
                val addr = it.get(i)
                builder.addAddress(addr.address, addr.prefix.toInt())
            }
        }

        options.inet4Routes?.let {
            for (i in 0 until it.len()) {
                val route = it.get(i)
                builder.addRoute(route.address, route.prefix.toInt())
            }
        }

        builder.addDnsServer("8.8.8.8")

        vpnInterface = builder.establish()
        return vpnInterface?.detachFd() ?: -1
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    override fun writeLog(message: String) {
        android.util.Log.d("TunnelBI", message)
    }

    override fun urlTest() {}

    override fun useManualTun(): Boolean = false

    private fun buildConfig() = """
    {
      "log": { "level": "info" },
      "inbounds": [{
        "type": "tun",
        "interface_name": "tun0",
        "inet4_address": "172.19.0.1/30",
        "auto_route": true,
        "strict_route": false,
        "stack": "system"
      }],
      "outbounds": [{
        "type": "vless",
        "tag": "proxy",
        "server": "TU_SERVIDOR",
        "server_port": 443,
        "uuid": "TU_UUID",
        "tls": {
          "enabled": true,
          "server_name": "TU_DOMINIO"
        }
      }]
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
