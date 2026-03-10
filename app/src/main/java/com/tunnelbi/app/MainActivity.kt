package com.tunnelbi.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    private var isConnected = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)

        btnConnect.setOnClickListener {
            if (!isConnected) {
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    startVpn()
                }
            } else {
                stopVpn()
            }
        }
    }

    private fun startVpn() {
        startForegroundService(Intent(this, TunVpnService::class.java).apply {
            action = TunVpnService.ACTION_START
        })
        isConnected = true
        tvStatus.text = "Conectado"
        tvStatus.setTextColor(0xFF4CAF50.toInt())
        btnConnect.text = "Desconectar"
        btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF5555.toInt())
    }

    private fun stopVpn() {
        startService(Intent(this, TunVpnService::class.java).apply {
            action = TunVpnService.ACTION_STOP
        })
        isConnected = false
        tvStatus.text = "Desconectado"
        tvStatus.setTextColor(0xFFFF5555.toInt())
        btnConnect.text = "Conectar"
        btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
    }
}
