package com.example.broroomai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var usbSerialManager: UsbSerialManager
    private lateinit var broAssistant: BroAssistant

    // Declared class properties so findViewById bindings resolve correctly
    private var statusTextView: TextView? = null
    private var connectButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        connectButton = findViewById(R.id.connectButton)

        usbSerialManager = UsbSerialManager(this) { data ->
            runOnUiThread {
                onHardwareDataReceived(data)
            }
        }

        broAssistant = BroAssistant(this, usbSerialManager)

        checkPermissions()

        connectButton?.setOnClickListener {
            if (!usbSerialManager.isConnected) {
                val connected = usbSerialManager.connect()
                if (connected) {
                    statusTextView?.text = "Status: Connected to Arduino"
                    broAssistant.speak("Connected to hardware system.")
                } else {
                    statusTextView?.text = "Status: Connection Failed"
                    Toast.makeText(this, "Failed to connect to USB device", Toast.LENGTH_SHORT).show()
                }
            } else {
                usbSerialManager.disconnect()
                statusTextView?.text = "Status: Disconnected"
            }
        }
    }

    private fun onHardwareDataReceived(data: String) {
        broAssistant.updateDoorStateFromArduino(data)
        Toast.makeText(this, "Arduino: $data", Toast.LENGTH_SHORT).show()
    }

    fun checkStatus() {
        if (usbSerialManager.isConnected) {
            broAssistant.sendCommand("STATUS_CHECK")
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        broAssistant.shutdown()
        usbSerialManager.disconnect()
    }
}