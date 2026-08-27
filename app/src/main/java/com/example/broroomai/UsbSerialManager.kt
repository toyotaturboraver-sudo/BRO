package com.example.broroomai

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.concurrent.Executors

class UsbSerialManager(
    private val context: Context,
    private val onDataReceived: (String) -> Unit
) : SerialInputOutputManager.Listener {

    private val ACTION_USB_PERMISSION = "com.example.broroomai.USB_PERMISSION"
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbConnection: UsbDeviceConnection? = null
    private var serialPort: com.hoho.android.usbserial.driver.UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var connected = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { connectToDevice(it) }
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> connect()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> disconnect()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(usbReceiver, filter)
    }

    fun isConnected(): Boolean = connected

    fun connect() {
        if (connected) return

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) return

        val driver: UsbSerialDriver = availableDrivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            val flags = PendingIntent.FLAG_MUTABLE
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            usbManager.requestPermission(device, permissionIntent)
        } else {
            connectToDevice(device)
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = availableDrivers.find { it.device == device } ?: return

        usbConnection = usbManager.openDevice(driver.device) ?: return
        serialPort = driver.ports[0]

        try {
            serialPort?.open(usbConnection)
            serialPort?.setParameters(9600, 8, com.hoho.android.usbserial.driver.UsbSerialPort.STOPBITS_1, com.hoho.android.usbserial.driver.UsbSerialPort.PARITY_NONE)

            ioManager = SerialInputOutputManager(serialPort, this)
            Executors.newSingleThreadExecutor().submit(ioManager)
            connected = true
            
            // Sync time automatically on connect
            syncTimeWithArduino()
        } catch (e: IOException) {
            disconnect()
        }
    }

    fun sendCommand(cmd: String): Boolean {
        if (!connected || serialPort == null) return false
        
        // Strict Whitelist Validation
        val validCmds = listOf("LIGHT_ON", "LIGHT_OFF", "STATUS", "PING", "STOP")
        val isValid = validCmds.contains(cmd) || cmd.startsWith("TIME:")
        if (!isValid) return false

        return try {
            val data = "$cmd\n".toByteArray(Charsets.UTF_8)
            serialPort?.write(data, 2000)
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun syncTimeWithArduino() {
        val calendar = java.util.Calendar.getInstance()
        val hours = String.format("%02d", calendar.get(java.util.Calendar.HOUR_OF_DAY))
        val minutes = String.format("%02d", calendar.get(java.util.Calendar.MINUTE))
        sendCommand("TIME:$hours:$minutes")
    }

    override fun onNewData(data: ByteArray?) {
        data?.let {
            val str = String(it, Charsets.UTF_8)
            onDataReceived(str)
        }
    }

    override fun onRunError(e: Exception?) {
        disconnect()
    }

    fun disconnect() {
        ioManager?.stop()
        ioManager = null
        try {
            serialPort?.close()
        } catch (e: IOException) {}
        serialPort = null
        usbConnection?.close()
        usbConnection = null
        connected = false
    }
}