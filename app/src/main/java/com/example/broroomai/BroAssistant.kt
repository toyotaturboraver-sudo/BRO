package com.example.broroomai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class BroAssistant(
    private val context: Context,
    private val usbSerialManager: UsbSerialManager,
    private val onUIUpdate: (command: String, response: String) -> Unit
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false

    init {
        tts = TextToSpeech(context, this)
        initSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.0f)
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // Automatically restart listening on error/timeout to stay active
                    if (isListening) {
                        startListening()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0].lowercase(Locale.ROOT)
                        processSpokenPhrase(spokenText)
                    }
                    if (isListening) {
                        startListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startContinuousListening() {
        isListening = true
        startListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processSpokenPhrase(text: String) {
        // Only trigger if hotword "bro" is present
        if (text.contains("bro")) {
            val commandPart = text.substringAfter("bro").trim()
            parseAndExecuteCommand(commandPart)
        }
    }

    fun processDirectCommand(cmd: String) {
        executeCommand(cmd)
    }

    private fun parseAndExecuteCommand(text: String) {
        when {
            text.contains("turn on light") || text.contains("light on") || text.contains("lights on") -> {
                executeCommand("LIGHT_ON")
            }
            text.contains("turn off light") || text.contains("light off") || text.contains("lights off") -> {
                executeCommand("LIGHT_OFF")
            }
            text.contains("status") -> {
                executeCommand("STATUS")
            }
            text.contains("ping") -> {
                executeCommand("PING")
            }
            text.contains("stop") -> {
                executeCommand("STOP")
            }
            text.contains("hello") || text.contains("hi") -> {
                speak("Yo! BRO room assistant at your service.")
                onUIUpdate("GREETING", "Yo! BRO room assistant at your service.")
            }
            else -> {
                speak("Command not recognized, bro.")
                onUIUpdate("UNKNOWN", "Command not recognized, bro.")
            }
        }
    }

    private fun executeCommand(cmd: String) {
        val sent = usbSerialManager.sendCommand(cmd)
        val responseText = if (sent) {
            when (cmd) {
                "LIGHT_ON" -> "Turning light on."
                "LIGHT_OFF" -> "Turning light off."
                "STATUS" -> "Requesting status."
                "PING" -> "Pinging Arduino."
                "STOP" -> "Emergency stop engaged."
                else -> "Executing command $cmd."
            }
        } else {
            "Failed. Arduino USB is not connected."
        }

        speak(responseText)
        onUIUpdate(cmd, responseText)
    }

    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "BRO_TTS_ID")
    }

    fun shutdown() {
        isListening = false
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}