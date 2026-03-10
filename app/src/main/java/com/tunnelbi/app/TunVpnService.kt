package com.tunnelbi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.File

class TunVpnService : VpnService() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val CHANNEL_ID = "tunnelbi_vpn"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var process: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTunnel()
            ACTION_STOP -> stopTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        startForeground(1, buildNotification())

        // 1. Crear interfaz TUN
        vpnInterface = Builder()
            .addAddress("172.19.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setMtu(1500)
            .setSession("TunnelBI")
            .establish()

        val fd = vpnInterface?.fd ?: return

        // 2. Copiar binario de assets a filesDir
        val binary = File(filesDir, "sing-box")
        if (!binary.exists()) {
            assets.open("sing-box-arm64").use { input ->
                binary.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            binary.setExecutable(true)
        }

        // 3. Escribir config VLESS
        val config = File(filesDir, "config.json")
        config.writeText(buildConfig())

        // 4. Lanzar sing-box
        process = ProcessBuilder(
            binary.absolutePath, "run", "-c", config.absolutePath
        ).apply {
            environment()["ANDROID_VPN_FD"] = fd.toString()
            redirectErrorStream(true)
        }.start()
    }

    private fun stopTunnel() {
        process?.destroy()
        process = null
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel()
    }

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
