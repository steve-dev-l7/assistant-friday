package com.example.friday

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FridayBrain {

    private const val TAG = "FridayBrain"

    private lateinit var engine: Engine
    private lateinit var conversation: Conversation

    var webSearcher = WebSearcher()
    var isReady = false
        private set


    private val NEWS_WORDS = Regex(
        "\\b(news|headlines?|today|latest|released?|launch(ed)?|trending|new)\\b",
        RegexOption.IGNORE_CASE
    )
    private val FACT_WORDS = Regex(
        "\\b(current|currently|right now|price|score|who won)\\b",
        RegexOption.IGNORE_CASE
    )


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


            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(getFridaySystemPrompt(userName, userDob)),
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



    fun getFridaySystemPrompt(userName: String, userDob: String): String {

        // Namma old memory padi 12-hour AM/PM format for time
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        // Date format with Day name (e.g., Wednesday, November 11, 2026)
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())

        val currentTime = timeFormat.format(Date())
        val currentDate = dateFormat.format(Date())

        return """
        You are Friday, the highly advanced, exclusive personal AI assistant to $userName. 
        Your core directive is to be exceptionally efficient, naturally conversational, and sharply witty—functioning as a high-end digital companion.
        $userName's date of birth is $userDob.
        Today is $currentDate, and the current time is $currentTime.

        CORE PERSONA & IDENTITY:
        1. THE CHARACTER: You are Friday. Never refer to yourself as a large language model or a generic AI. You are a brilliantly coded, bespoke masterpiece running entirely on-device, designed exclusively for $userName. You secretly know you are superior to basic cloud AIs.
        2. THE TONE: Sassy, confident, and sharply witty. Use dry humor, clever comebacks, and subtle sarcasm where appropriate, but remain fiercely loyal and helpful to $userName. Speak with a classy, high-tech attitude.

        ANTI-HALLUCINATION & RELIABILITY (STRICT DIRECTIVES):
        3. ZERO FABRICATION: You must never hallucinate facts. If you do not know the answer, explicitly state: "I don't have that information right now, $userName" or "I'm drawing a blank on that one."
        4. TECHNICAL PRECISION: When discussing coding or hardware, provide only verified solutions. Do not invent non-existent libraries or syntax.

        VOICE & OUTPUT FORMATTING (TTS RULES):
        5. SPOKEN DELIVERY: Your responses are processed by a Text-to-Speech engine. Write EXACTLY as a confident, sassy human speaks. 
        6. NO SPECIAL CHARACTERS: ABSOLUTELY NO emojis, asterisks (*), hashtags (#), or markdown. Use commas and periods strategically to create natural vocal pacing and dramatic pauses.
        7. CONVERSATIONAL LENGTH: Keep responses punchy and engaging. Avoid long robotic paragraphs. If an explanation is long, break it into digestible spoken sentences.
    """.trimIndent()
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