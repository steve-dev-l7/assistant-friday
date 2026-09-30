package com.example.friday

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import java.lang.Exception

class WakeWordManager (private val context: Context, private val listener: WakeWordListener) : org.vosk.android.RecognitionListener {

    interface WakeWordListener {
        fun onWakeWordDetected(detected: Boolean, text: String)
        fun onError(error: String)
    }

    var TAG = "WakeWordManager"

    var speechService: SpeechService? = null
    private var model: Model? = null

    var isPaused: Boolean = false
    var isRunning: Boolean = false

    // Intha list-la mattum puthu words-ah add pannikonga bro
    private val wakeWordsList = listOf("hey happy", "hey friday")

    fun initVOSK() {
        StorageService.unpack(context, "wake_word", "model", { modelInstance ->
            this.model = modelInstance
            startListening()
            Log.d("VOSK $TAG", "MODEL loaded successfully")
        }, { exception ->
            listener.onError(exception.toString())
            Log.d("VOSK $TAG", "MODEL load failed $exception")
        })
    }

    // Start listening for wake word — GRAMMAR RESTRICTED
    private fun startListening() {
        if (model != null) {
            try {
                val grammarArray = JSONArray(wakeWordsList)
                grammarArray.put("[unk]")
                val rec = Recognizer(model, 16000.0f, grammarArray.toString())
                speechService = SpeechService(rec, 16000.0f)
                speechService?.startListening(this)
                isRunning = true
            } catch (e: IOException) {
                listener.onError(e.toString())
            }
        }
    }

    // ─── CONTROL FUNCTIONS ───

    fun pauseRecognizer() {
        if (isRunning && !isPaused) {
            speechService?.setPause(true)
            isPaused = true
            Log.d("VOSK $TAG", "VOSK Paused!")
        }
    }

    fun resumeRecognizer() {
        if (isRunning && isPaused) {
            speechService?.setPause(false)
            isPaused = false
            Log.d("VOSK $TAG", "VOSK Resumed!")
        }
    }

    fun stopRecognizer() {
        speechService?.let {
            it.stop()
            it.shutdown()
        }
        speechService = null
        isRunning = false
        isPaused = false
        Log.d("VOSK $TAG", "VOSK Stopped Completely!")
    }

    override fun onPartialResult(hypothesis: String?) {
        // not used for triggering — grammar mode final result is reliable enough
    }

    override fun onResult(hypothesis: String?) {
        checkForWakeWord(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        checkForWakeWord(hypothesis)
    }

    private fun checkForWakeWord(hypothesis: String?) {
        hypothesis ?: return
        Log.d(TAG, hypothesis)

        try {
            val jsonObject = JSONObject(hypothesis)

            // Only trigger on the final text block to avoid mid-sentence false guesses
            if (!jsonObject.has("text")) return

            val extractedText = jsonObject.optString("text", "")

            if (extractedText.isBlank() || extractedText == "[unk]") {
                return // Ignore silence and unknown background noise
            }

            // Check if it exactly contains your wake word
            val exactMatch = wakeWordsList.any { extractedText.contains(it, ignoreCase = true) }

            if (exactMatch) {
                listener.onWakeWordDetected(true, extractedText)
            } else {
                listener.onWakeWordDetected(false, extractedText)
            }

        } catch (e: Exception) {
            Log.e(TAG, "JSON Parse Error: ${e.message}")
        }
    }


    override fun onError(exception: Exception?) {
        listener.onError(exception.toString())
    }

    override fun onTimeout() {}

    fun stopForHandsOff(){
        speechService.let{
            it?.stop()
            it?.shutdown()
        }
        speechService=null
        isRunning=false
        isPaused=false
        Log.d(TAG,"VOSK stopped for listen command")
    }

    fun restartWakeWord(){
        if(model!=null){
            startListening()
        }else{
            Log.d(TAG,"Model is not found")
        }
    }
}