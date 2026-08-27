package com.example.broroomai

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException

class UsbSerialManager(
    private val context: Context,
    private val onDataReceived: (String) -> Unit,
    private val onError: (String) -> Unit
) : SerialInputOutputManager.Listener {

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.broroomai.USB_PERMISSION"
        private const val WRITE_WAIT_MILLIS = 2000
        private const val READ_WAIT_MILLIS = 2000
        private const val BAUD_RATE = 9600
    }

    fun connect(): Boolean {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            onError("No USB devices found")
            return false
        }

        val driver: UsbSerialDriver = availableDrivers[0]
        val device: UsbDevice = driver.device

        if (!usbManager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                Intent(ACTION_USB_PERMISSION), 
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
            onError("Requesting USB permission...")
            return false
        }

        val connection: UsbDeviceConnection = usbManager.openDevice(device) ?: run {
            onError("Opening USB device connection failed")
            return false
        }

        usbSerialPort = driver.ports[0]
        try {
            usbSerialPort?.open(connection)
            usbSerialPort?.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            ioManager = SerialInputOutputManager(usbSerialPort, this)
            ioManager?.start()
            return true
        } catch (e: IOException) {
            onError("Error opening port: ${e.message}")
            disconnect()
            return false
        }
    }

    fun sendData(data: String) {
        if (usbSerialPort == null) {
            onError("Cannot send: USB not connected")
            return
        }
        try {
            val bytes = data.toByteArray(Charsets.UTF_8)
            usbSerialPort?.write(bytes, WRITE_WAIT_MILLIS)
        } catch (e: IOException) {
            onError("Error sending data: ${e.message}")
        }
    }

    fun disconnect() {
        ioManager?.listener = null
        ioManager?.stop()
        ioManager = null

        try {
            usbSerialPort?.close()
        } catch (ignored: IOException) {
        }
        usbSerialPort = null
    }

    override fun onNewData(data: ByteArray) {
        val message = String(data, Charsets.UTF_8)
        onDataReceived(message)
    }

    override fun onRunError(e: Exception) {
        onError("USB Serial Error: ${e.message}")
        disconnect()
    }
}