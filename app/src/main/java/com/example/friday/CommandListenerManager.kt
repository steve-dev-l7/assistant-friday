package com.example.friday

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class CommandListenerManager(private val context: Context, private val listener: CommandResultListener) {

    private var speechRecognizer : SpeechRecognizer?=null
    private val mainHandler=Handler(Looper.getMainLooper())
    private val TAG = "CommandListenerManager"

    interface CommandResultListener{
        fun onCommandReceived(command: String)
        fun onCommandError(error: String)
    }

    init{
        // Main Thread-la recognizer-ah create pandrom
        mainHandler.post{
            speechRecognizer= SpeechRecognizer.createSpeechRecognizer(context)
            setupListener()
        }
    }

    private fun setupListener(){
        speechRecognizer?.setRecognitionListener(object : RecognitionListener{
            override fun onBeginningOfSpeech() {

            }

            override fun onBufferReceived(buffer: ByteArray?) {

            }

            override fun onEndOfSpeech() {

            }

            override fun onError(error: Int) {
                listener.onCommandError(getErrorText(error))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {

            }

            override fun onPartialResults(partialResults: Bundle?) {
                Log.d(TAG,partialResults.toString())
            }

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Mic ready bro, start speaking...")
            }

            override fun onResults(results: Bundle?) {
                val matches=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if(!matches.isNullOrEmpty()){
                    val command=matches[0]
                    Log.d(TAG, "Command Received: $command")
                    listener.onCommandReceived(command)
                }else{
                    listener.onCommandError("No match found")
                }
            }

            override fun onRmsChanged(rmsdB: Float) {

            }

        })
    }

    fun startListeningForCommand(){
        mainHandler.post{
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
        }
    }

    fun destroyCommandListenerManager(){
        mainHandler.post{
            speechRecognizer?.destroy()
        }
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Didn't understand, please try again."
        }
    }
}