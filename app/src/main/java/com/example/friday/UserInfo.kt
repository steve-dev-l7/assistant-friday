package com.example.friday

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class UserInfo : AppCompatActivity() {



    var userMobile=""

    var userDOB=""

    companion object{
        var userName = ""
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user_info)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val inputName=findViewById<EditText>(R.id.inputName)

        val inputMobile=findViewById<EditText>(R.id.inputNumber)

        val inputDOB = findViewById<EditText>(R.id.inputDOB)

        val saveBtn=findViewById<Button>(R.id.saveBtn)

        saveBtn.setOnClickListener{

            userName=inputName.text.toString()

            userMobile=inputMobile.text.toString()

            userDOB = inputDOB.text.toString()

            if(userMobile.length != 10) {
                Toast.makeText(this,"mobile number is wrong",Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val sharedPreferences = getSharedPreferences("User",MODE_PRIVATE)

            sharedPreferences.edit {
                putString("name",userName)
                putString("mobile",userMobile)
                putString("dob",userDOB)
            }

            Toast.makeText(this,"User data saved successfully",Toast.LENGTH_LONG).show()

            val intent = Intent(this@UserInfo, HomePage::class.java)
            startActivity(intent)
        }
    }

}