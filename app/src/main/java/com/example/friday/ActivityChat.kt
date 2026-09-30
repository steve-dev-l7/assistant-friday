package com.example.friday

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ActivityChat : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView

    private lateinit var messageInput: EditText

    private lateinit var sendButton: ImageButton

    private lateinit var backButton: ImageButton

    private lateinit var settingsButton : ImageButton
    private lateinit var typingIndicator: LinearLayout

    private val messagesList = mutableListOf<Message>()

    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chatRoot)) { v, insets ->
            // Get both system bars (status/nav bars) AND the keyboard (ime)
            val insetsType = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            val barsAndKeyboard = insets.getInsets(insetsType)

            v.setPadding(barsAndKeyboard.left, barsAndKeyboard.top, barsAndKeyboard.right, barsAndKeyboard.bottom)

            // We also want to scroll to the bottom when the keyboard pops up
            if (insets.isVisible(WindowInsetsCompat.Type.ime()) && messagesList.isNotEmpty()) {
                chatRecyclerView.scrollToPosition(messagesList.size - 1)
            }

            insets
        }

        // Initialize Views
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        typingIndicator = findViewById(R.id.typingIndicator)
        backButton = findViewById(R.id.backButton)
        settingsButton = findViewById(R.id.chatSettingsButton)

        settingsButton.setOnClickListener {
            startActivity(Intent(this, Settings::class.java))
        }

        // Back to the home page
        backButton.setOnClickListener {
            startActivity(Intent(this, HomePage::class.java))
            finish()
        }

        // Setup recycler
        chatAdapter = ChatAdapter(messagesList)
        chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        chatRecyclerView.adapter = chatAdapter

        // Initial Boot-up message
        addMessage(Message("Hey, I'm awake! What can I help you with?", true, getCurrentTime()))

        sendButton.setOnClickListener {
            val    userText = messageInput.text.toString().trim()

            if(userText.isNotEmpty()){
                addMessage(Message(userText, false, getCurrentTime()))
                messageInput.text.clear()

                generateFridayResponse(userText)
            }

        }
    }

    private fun addMessage(message: Message) {
        messagesList.add(message)
        chatAdapter.notifyItemInserted(messagesList.size - 1)
        chatRecyclerView.scrollToPosition(messagesList.size - 1)
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun generateFridayResponse(query: String) {
        // 1. Check if Friday is ready
        if (!FridayBrain.isReady) {
            addMessage(Message("Give me a second sir, still loading...", true, getCurrentTime()))
            return
        }

        // 2. Show "Friday is thinking..." FIRST
        typingIndicator.visibility = View.VISIBLE
        chatRecyclerView.scrollToPosition(messagesList.size - 1)

        // Notice we are NOT creating the message bubble here anymore!

        // 3. Launch Coroutine to call FridayBrain
        lifecycleScope.launch(Dispatchers.IO) {
            var isFirstToken = true
            var messageIndex = -1
            var responseMessage: Message? = null

            FridayBrain.sendMessage(
                userInput = query,
                onToken = { token ->
                    // Switch to Main thread to update the UI safely
                    runOnUiThread {
                        if (isFirstToken) {
                            // FIRST TOKEN ARRIVED!
                            // Hide the typing indicator
                            typingIndicator.visibility = View.GONE

                            // Now create the bot bubble with the first token
                            responseMessage = Message(token, true, getCurrentTime())
                            messagesList.add(responseMessage)
                            messageIndex = messagesList.size - 1

                            chatAdapter.notifyItemInserted(messageIndex)
                            chatRecyclerView.scrollToPosition(messageIndex)

                            isFirstToken = false
                        } else {
                            // Subsequent tokens: just append to the existing bubble
                            responseMessage?.let {
                                it.text += token
                                chatAdapter.notifyItemChanged(messageIndex)
                                chatRecyclerView.scrollToPosition(messageIndex)
                            }
                        }
                    }
                },
                onDone = {
                    runOnUiThread {
                        if (isFirstToken) {
                            // Fallback: If AI completes without sending any text
                            typingIndicator.visibility = View.GONE
                            addMessage(Message("Sorry, something went wrong.", true, getCurrentTime()))
                        } else {
                            // Final scroll to ensure the whole message is visible
                            chatRecyclerView.scrollToPosition(messageIndex)
                        }
                    }
                }
            )
        }
    }
}