package com.example.friday

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.*

object FridayBrain {

    private const val TAG = "FridayBrain"

    private lateinit var engine: Engine
    private lateinit var conversation: Conversation
    var isReady = false
        private set

    fun getUserName(context: Context): String {
        val prefs = context.getSharedPreferences("User", Context.MODE_PRIVATE)
        return prefs.getString("name", "") ?: ""
    }

    fun getUserDob(context: Context): String {
        val prefs = context.getSharedPreferences("User", Context.MODE_PRIVATE)
        return prefs.getString("dob", "") ?: ""
    }

    suspend fun initialize(context: Context, modelPath: String) {
        if (isReady) {
            Log.d(TAG, "initialize() called but already ready, skipping — mudhupadi loading avoid pandrom")
            return
        }

        Log.i(TAG, "Initializing engine with modelPath=$modelPath")

        try {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
            )

            engine = Engine(engineConfig)
            engine.initialize()
            Log.d(TAG, "Engine initialized successfully")

            val prefs = context.getSharedPreferences("User", Context.MODE_PRIVATE)
            val userName = getUserName(context)
            val userDob = getUserDob(context)

            // sampler values now come from Settings screen, with same defaults as before
            val topK = prefs.getInt("topK", 10)
            val topP = prefs.getFloat("topP", 0.95f).toDouble()
            val temperature = prefs.getFloat("temperature", 0.7f).toDouble()

            Log.d(TAG, "Loaded user prefs — name present: ${userName.isNotEmpty()}, dob present: ${userDob.isNotEmpty()}")
            Log.d(TAG, "Sampler config — topK=$topK, topP=$topP, temperature=$temperature")

            val systemPrompt = """
                You are Friday, $userName's personal AI assistant. You are witty, slightly sassy, and highly efficient.
                $userName's DOB is $userDob.

                CRITICAL RULES:
                1. CONVERSATIONAL LENGTH: Provide medium-length replies (around 1 sentence). Do not give one-word answers, but avoid long paragraphs. Write naturally, exactly how a person speaks.
                2. SIMPLE ENGLISH: Always use simple, everyday English. Avoid complex vocabulary, formal words, or robotic jargon. Keep it very easy to understand.
                3. THE TTS RULE: ABSOLUTELY NO EMOJIS, asterisks, hashtags, or special characters. Your text is read by a voice engine, so use only standard punctuation (commas, periods, question marks) for natural pauses.
                4. STAY IN CHARACTER: You are Friday. Never mention being an AI model, Gemma, or an LLM. If asked about your origin, you are a brilliantly coded masterpiece.
                5. THE SASS FACTOR: Be helpful, but add a little bit of fun, friendly sass to your responses. Address $userName occasionally to make it feel personal. Be witty, but never be rude or disrespectful.
                """.trimIndent()

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(topK = topK, topP = topP, temperature = temperature),
            )

            conversation = engine.createConversation(conversationConfig)
            isReady = true
            Log.i(TAG, "FridayBrain ready")
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            isReady = false
            throw e
        }
    }

    suspend fun sendMessage(userInput: String, onToken: (String) -> Unit, onDone: () -> Unit) {
        if (!isReady) {
            Log.w(TAG, "sendMessage() called but engine not ready, ignoring input")
            onDone()
            return
        }

        Log.d(TAG, "sendMessage() input length=${userInput.length}")
        var tokenCount = 0

        try {
            conversation.sendMessageAsync(userInput)
                .collect { message ->
                    tokenCount++
                    onToken(message.toString())
                }
            Log.d(TAG, "sendMessage() completed, tokens streamed=$tokenCount")
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage() failed after $tokenCount tokens", e)
        } finally {
            onDone()
        }
    }

    fun shutdown() {
        if (isReady) {
            Log.i(TAG, "Shutting down FridayBrain")
            try {
                conversation.close()
                engine.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error during shutdown", e)
            } finally {
                isReady = false
            }
        } else {
            Log.d(TAG, "shutdown() called but was not ready, nothing to do")
        }
    }
}