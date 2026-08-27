package com.example.broroomai

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.concurrent.Executors

class UsbSerialManager(
    private val context: Context,
    private val onDataReceived: (String) -> Unit
) : SerialInputOutputManager.Listener {

    private var usbPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    val isConnected: Boolean
        get() = usbPort != null && usbPort!!.isOpen

    fun connect(): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) return false

        val driver: UsbSerialDriver = availableDrivers[0]
        val connection: UsbDeviceConnection = manager.openDevice(driver.device) ?: return false

        val port = driver.ports[0]
        return try {
            port.open(connection)
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbPort = port

            ioManager = SerialInputOutputManager(usbPort, this)
            Executors.newSingleThreadExecutor().submit(ioManager)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun sendCommand(command: String): Boolean {
        return if (isConnected) {
            try {
                val bytes = (command + "\n").toByteArray(Charsets.UTF_8)
                usbPort?.write(bytes, 2000)
                true
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        } else {
            false
        }
    }

    fun disconnect() {
        try {
            ioManager?.stop()
            ioManager = null
            usbPort?.close()
            usbPort = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onNewData(data: ByteArray) {
        val receivedText = String(data, Charsets.UTF_8)
        onDataReceived(receivedText)
    }

    override fun onRunError(e: Exception) {
        disconnect()
    }
}