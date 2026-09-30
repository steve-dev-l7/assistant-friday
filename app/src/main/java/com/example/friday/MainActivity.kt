package com.example.friday

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // View IDs-a edukkurom
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val progressText = findViewById<TextView>(R.id.progressText)

        lifecycleScope.launch {
            try {
                // Inga thaan antha callback varuthu!
                val modelPath = ModelAssetManager.getModelPath(applicationContext) { progress, message ->
                    progressBar.progress = progress
                    progressText.text = message
                }

                // Download mudinchathum FridayBrain-a start pandrom
                FridayBrain.initialize(applicationContext, modelPath)

                startActivity(Intent(this@MainActivity, HomePage::class.java))
                finish()

            } catch (e: Exception) {
                Log.e("Friday", "Model init failed", e)
                progressText.text = "ERROR: ${e.localizedMessage}"
                progressText.setTextColor(android.graphics.Color.RED)
                Toast.makeText(this@MainActivity, "Network Error. Try again.", Toast.LENGTH_LONG).show()
            }
        }
    }
}