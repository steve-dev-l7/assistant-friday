package com.example.friday

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import android.provider.Settings
import android.widget.LinearLayout
import androidx.core.net.toUri
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.widget.TextView


class HomePage : AppCompatActivity(), TextToSpeech.OnInitListener {

    var name : String?=null

    var mobile : String?= null

    var userDOB: String?=null

    private var fridayService : FridayService?=null

    private var isBound = false

    private var glowAnimator : ObjectAnimator ?=null

    private lateinit var fridayChat : LinearLayout
    private val requiredPermissions = mutableListOf(
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Manifest.permission.READ_PHONE_STATE,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            Manifest.permission.SCHEDULE_EXACT_ALARM
        }
    }.toTypedArray()

    private var tts: TextToSpeech? = null

    private lateinit var dialog : ProgressDialog

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User settings page-la irundhu back vanthathum intha block run aagum
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permission Granted !", Toast.LENGTH_SHORT).show()
            // Inga unga Service-ah start pannalam
        } else {
            Toast.makeText(this, "Permission Denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestOverlayPermission() {
        // Permission already irukka nu check pandrom
        if (!Settings.canDrawOverlays(this)) {

            // Illana Settings page-ku poga oru Intent create pandrom
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri() // Ithu direct-a unga app-oda setting-ku kootitu pogum
            )

            // Settings page-ah launch pandrom
            overlayPermissionLauncher.launch(intent)

        }
    }

    private val serviceConnection = object : ServiceConnection{
        @SuppressLint("SetTextI18n")
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FridayService.LocalBinder
            fridayService=binder.getService()
            isBound = true

            fridayService?.askFriday("Hello Friday!")

            runOnUiThread {

                val statusDot = findViewById<View>(R.id.statusDot)

                statusDot.setBackgroundResource(R.drawable.dot_online)
                startDotGlow(statusDot)

                findViewById<TextView>(R.id.sleepingText).text = "Friday is active."

                findViewById<TextView>(R.id.underSleepingText).text="Say deactivate to stop Friday."

                Handler(Looper.getMainLooper()).postDelayed({
                    dialog.dismiss()
                },3000)
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onServiceDisconnected(name: ComponentName?) {
            fridayService = null
            isBound = false
            stopDotGlow()
            findViewById<View>(R.id.statusDot).setBackgroundResource(R.drawable.dot_offline)
            findViewById<TextView>(R.id.sleepingText).text = "Friday is sleeping..."

            findViewById<TextView>(R.id.underSleepingText).text="Tap below to wake up."
        }
    }

    private fun startDotGlow(dot:View){
        glowAnimator?.cancel()
        glowAnimator = ObjectAnimator.ofFloat(dot,"alpha",1f,0.3f).apply {
            duration = 800
            repeatMode= ValueAnimator.REVERSE
            repeatCount= ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopDotGlow(){
        glowAnimator?.cancel()
        glowAnimator=null
    }

    private val requestMultiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){permissionMap ->
        var allGranted=true
        permissionMap.forEach { (permission, isGranted) ->
            if(!isGranted){
                allGranted = false
                Toast.makeText(this, "$permission Denied!", Toast.LENGTH_SHORT).show()
            }
        }
        if(allGranted){
            Toast.makeText(this, "All Permissions Granted! Welcome!", Toast.LENGTH_SHORT).show()
        }
        else{
            Toast.makeText(this, "All permission are mandatory", Toast.LENGTH_LONG).show()
        }
    }


    private fun checkAndRequestPermission(){
        val permissionsToRequest = mutableListOf<String>()

        for(permission in requiredPermissions){
            if(ContextCompat.checkSelfPermission(this,permission)!= PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(permission)
            }
        }
        if(permissionsToRequest.isNotEmpty()){
            requestMultiplePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }else{
            Log.d("ForPermissionDebug","All permission already granted")
        }
    }


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_page)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //to ask permessions
        checkAndRequestPermission()

        checkAndRequestOverlayPermission()


        tts= TextToSpeech(this@HomePage,this)

        // get user details
        val sharedPreferences = getSharedPreferences("User", MODE_PRIVATE)
        name = sharedPreferences.getString("name", "")
        mobile = sharedPreferences.getString("mobile", "")
        userDOB =sharedPreferences.getString("dob","")

        // user details irukka illaiyanu check pandrom illana user kitta again profile ah update panna sollurom
        if(mobile == "" || name == "" || userDOB==""){
            Toast.makeText(this,"Failed to load user data", Toast.LENGTH_LONG).show()

            AlertDialog.Builder(this)
                .setTitle("Update Profile")
                .setMessage("Please Update your name and mobile number")
                .setPositiveButton("update"){dialog,which ->
                    val intent=Intent(this,UserInfo::class.java)
                    startActivity(intent)
                }.show()
        }

        val wakeUpButton = findViewById<Button>(R.id.wakeUpButton)

        val settings = findViewById<LinearLayout>(R.id.settings)



        fridayChat = findViewById(R.id.fridayChat)

        fridayChat.setOnClickListener {
            startActivity(Intent(this, ActivityChat::class.java))
        }

        settings.setOnClickListener {
            if(isServiceRunning(this, FridayService::class.java)){
                Toast.makeText(this,"Deactivate Friday to change settings", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val settingsIntent = Intent(this, com.example.friday.Settings::class.java)
            startActivity(settingsIntent)
        }

        wakeUpButton.setOnClickListener {
            if(isServiceRunning(this, FridayService::class.java)){
                Toast.makeText(this,"Happy service is already running", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            dialog = ProgressDialog(this)
            dialog.setTitle("Please wait ")
            dialog.setMessage("Warming up Friday! ")
            dialog.setCancelable(false)
            dialog.show()

            AlertDialog.Builder(this)
                .setTitle("Information")
                .setMessage("Activating 'Friday' may be drain your battery slightly faster. \n\nDo you want to activate Friday ?")
                .setPositiveButton("ok"){dialog,which ->
                    val intent= Intent(this, FridayService::class.java)
                    startForegroundService(intent)
                    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                }
                .setNegativeButton("cancel"){dialog,which->
                    dialog.dismiss()
                    Toast.makeText(this,"Activation Canceled by user", Toast.LENGTH_LONG).show()
                }.show()

        }

    }

    override fun onInit(status: Int) {
        if(status== TextToSpeech.SUCCESS){
            val result=tts!!.setLanguage(Locale.US)

            if(result== TextToSpeech.LANG_MISSING_DATA || result== TextToSpeech.LANG_NOT_SUPPORTED){
                Log.d("TTS", "Intha language support illa bro illa data missing!")
            }
            else{
                Log.d("TTS", "Loaded successfully")
            }
        }else{
            Log.d("TTS","Initialization failed for tts")
        }
    }


    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // Loop through running services
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true // Service run aaguthu!
            }
        }
        return false // Service run aagala
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}