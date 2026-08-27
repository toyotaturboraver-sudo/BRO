package com.example.broroomai

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class BroAssistant(
    private val context: Context,
    private val usbSerialManager: UsbSerialManager
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    
    // Tracks current state of the door from Arduino feedback
    private var isDoorOpen: Boolean = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    // Called when Arduino sends data (e.g., "DOOR_OPEN" or "DOOR_CLOSED")
    fun updateDoorStateFromArduino(data: String) {
        val cleanData = data.trim().uppercase(Locale.ROOT)
        when {
            cleanData.contains("DOOR_OPEN") -> {
                isDoorOpen = true
            }
            cleanData.contains("DOOR_CLOSED") -> {
                isDoorOpen = false
            }
        }
    }

    fun processVoiceCommand(command: String) {
        val cleanCommand = command.lowercase(Locale.ROOT).trim()

        when {
            // Light Commands
            cleanCommand.contains("turn on the light") || cleanCommand.contains("light on") -> {
                executeCommand("LIGHT_ON", "Turning on the light, bro.")
            }
            cleanCommand.contains("turn off the light") || cleanCommand.contains("light off") -> {
                executeCommand("LIGHT_OFF", "Turning off the light.")
            }
            
            // Door Status Narrations
            cleanCommand.contains("door status") || 
            cleanCommand.contains("is the door open") || 
            cleanCommand.contains("is the door closed") ||
            cleanCommand.contains("check door") -> {
                narrateDoorStatus()
            }

            else -> {
                speak("Sorry bro, I didn't recognize that command.")
            }
        }
    }

    private fun narrateDoorStatus() {
        if (isDoorOpen) {
            speak("The door is currently open, bro.")
        } else {
            speak("The door is currently closed, bro.")
        }
    }

    fun sendCommand(rawCommand: String) {
        if (usbSerialManager.isConnected) {
            usbSerialManager.sendCommand(rawCommand)
        } else {
            speak("Hardware is not connected over USB.")
        }
    }

    private fun executeCommand(hardwareCmd: String, responseSpeech: String) {
        if (usbSerialManager.isConnected) {
            val success = usbSerialManager.sendCommand(hardwareCmd)
            if (success) {
                speak(responseSpeech)
            } else {
                speak("Failed to send command to the board.")
            }
        } else {
            speak("USB hardware isn't connected right now.")
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BRO_TTS_ID")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}