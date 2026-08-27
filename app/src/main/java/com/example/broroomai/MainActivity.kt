package com.example.broroomai

import android.Manifest
import android.content.contentValuesOf
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvUsbStatus: TextView
    private lateinit var tvArduinoStatus: TextView
    private lateinit var tvLightStatus: TextView
    private lateinit var tvLastCmd: TextView
    private lateinit var tvLastResp: TextView
    private lateinit var tvVoiceState: TextView

    private lateinit var usbSerialManager: UsbSerialManager
    private lateinit var broAssistant: BroAssistant

    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        tvUsbStatus = findViewById(R.id.tvUsbStatus)
        tvArduinoStatus = findViewById(R.id.tvArduinoStatus)
        tvLightStatus = findViewById(R.id.tvLightStatus)
        tvLastCmd = findViewById(R.id.tvLastCmd)
        tvLastResp = findViewById(R.id.tvLastResp)
        tvVoiceState = findViewById(R.id.tvVoiceState)

        val btnLightOn: Button = findViewById(R.id.btnLightOn)
        val btnLightOff: Button = findViewById(R.id.btnLightOff)
        val btnStatus: Button = findViewById(R.id.btnStatus)
        val btnPing: Button = findViewById(R.id.btnPing)
        val btnStop: Button = findViewById(R.id.btnStop)

        // Initialize Hardware Serial & Assistant
        usbSerialManager = UsbSerialManager(this) { data ->
            runOnUiThread {
                handleArduinoResponse(data)
            }
        }

        broAssistant = BroAssistant(this, usbSerialManager) { cmd, response ->
            runOnUiThread {
                tvLastCmd.text = "LAST COMMAND: $cmd"
                tvLastResp.text = "LAST RESPONSE: $response"
            }
        }

        // Setup Manual Override Buttons
        btnLightOn.setOnClickListener { broAssistant.processDirectCommand("LIGHT_ON") }
        btnLightOff.setOnClickListener { broAssistant.processDirectCommand("LIGHT_OFF") }
        btnStatus.setOnClickListener { broAssistant.processDirectCommand("STATUS") }
        btnPing.setOnClickListener { broAssistant.processDirectCommand("PING") }
        btnStop.setOnClickListener { broAssistant.processDirectCommand("STOP") }

        // Check Permissions and Start Listening
        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            startHotwordListener()
        }
    }

    private fun startHotwordListener() {
        tvVoiceState.text = "🎙️ ALWAYS LISTENING: \"BRO\""
        broAssistant.startContinuousListening()
    }

    private fun handleArduinoResponse(data: String) {
        val trimmed = data.trim()
        if (trimmed.contains("LIGHT_ON_ACK") || trimmed.contains("LIGHT: ON")) {
            tvLightStatus.text = "LIGHT: ON"
            tvLightStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
        } else if (trimmed.contains("LIGHT_OFF_ACK") || trimmed.contains("LIGHT: OFF")) {
            tvLightStatus.text = "LIGHT: OFF"
            tvLightStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
        } else if (trimmed.contains("PONG") || trimmed.contains("READY")) {
            tvArduinoStatus.text = "ARDUINO: ONLINE"
            tvArduinoStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startHotwordListener()
        } else {
            Toast.makeText(this, "Microphone permission is required for hotword activation!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        usbSerialManager.connect()
        if (usbSerialManager.isConnected()) {
            tvUsbStatus.text = "USB: CONNECTED"
            tvUsbStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
        } else {
            tvUsbStatus.text = "USB: DISCONNECTED"
            tvUsbStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_red))
        }
    }

    override fun onPause() {
        super.onPause()
        usbSerialManager.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        broAssistant.shutdown()
    }
}