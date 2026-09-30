package com.example.friday

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Calendar

class Settings : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("User", MODE_PRIVATE)

        val etName = findViewById<EditText>(R.id.etName)
        val etMobile = findViewById<EditText>(R.id.etMobile)
        val etDob = findViewById<EditText>(R.id.etDob)

        // ===== load existing user data =====
        etName.setText(prefs.getString("name", ""))
        etMobile.setText(prefs.getString("mobile", ""))
        etDob.setText(prefs.getString("dob", ""))

        etDob.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                etDob.setText("%02d/%02d/%d".format(d, m + 1, y))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // ===== sampler config sliders =====
        val seekTemp = findViewById<SeekBar>(R.id.seekTemperature)
        val tvTemp = findViewById<TextView>(R.id.tvTempLabel)
        seekTemp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvTemp.text = "Temperature: %.2f".format(progress / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val seekTopP = findViewById<SeekBar>(R.id.seekTopP)
        val tvTopP = findViewById<TextView>(R.id.tvTopPLabel)
        seekTopP.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvTopP.text = "Top-P: %.2f".format(progress / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val seekTopK = findViewById<SeekBar>(R.id.seekTopK)
        val tvTopK = findViewById<TextView>(R.id.tvTopKLabel)
        seekTopK.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvTopK.text = "Top-K: $progress"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // load saved (or default) model params — same defaults FridayBrain uses
        seekTemp.progress = (prefs.getFloat("temperature", 0.7f) * 100).toInt()
        seekTopP.progress = (prefs.getFloat("topP", 0.95f) * 100).toInt()
        seekTopK.progress = prefs.getInt("topK", 10)

        // set label text on first load too (listener only fires on change)
        tvTemp.text = "Temperature: %.2f".format(seekTemp.progress / 100f)
        tvTopP.text = "Top-P: %.2f".format(seekTopP.progress / 100f)
        tvTopK.text = "Top-K: ${seekTopK.progress}"

        // ===== save =====
        findViewById<TextView>(R.id.btnSave).setOnClickListener {

            val dialog = ProgressDialog(this)
            dialog.setTitle("Please wait")
            dialog.setMessage("Making Changes")
            dialog.setCancelable(false)
            dialog.show()

            val name = etName.text.toString().trim()
            val mobile = etMobile.text.toString().trim()
            val dob = etDob.text.toString().trim()

            if (mobile.isNotEmpty() && mobile.length != 10) {
                Toast.makeText(this, "mobile number is wrong", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            prefs.edit {
                putString("name", name)
                putString("mobile", mobile)
                putString("dob", dob)
                putFloat("temperature", seekTemp.progress / 100f)
                putFloat("topP", seekTopP.progress / 100f)
                putInt("topK", seekTopK.progress)
            }


            lifecycleScope.launch {
                try {
                    // PUTHU UPDATE: Callback add panniyachu
                    val modelPath = ModelAssetManager.getModelPath(applicationContext) { progress, message ->
                        // ProgressDialog-oda text-a dynamic ah update pandrom!
                        dialog.setMessage(message)
                    }

                    // The Architect's Fix: Settings maathuna Friday-a restart pannanum
                    // Illana pazhaya memory-la thaan irukkum
                    if (FridayBrain.isReady) {
                        FridayBrain.shutdown()
                    }

                    // FridayBrain-a puthu settings-oda initialize pandrom
                    FridayBrain.initialize(applicationContext, modelPath)

                    dialog.dismiss()

                    val intent = Intent(this@Settings, HomePage::class.java)
                    startActivity(intent)

                    // FIX: finish()-a coroutine kulla kondu vanthachu
                    finish()

                } catch (e: Exception) {
                    dialog.dismiss()
                    Toast.makeText(this@Settings, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            finish()
        }
    }
}