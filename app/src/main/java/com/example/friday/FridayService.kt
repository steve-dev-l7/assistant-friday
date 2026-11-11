package com.example.friday
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class FridayService : Service(), WakeWordManager.WakeWordListener, TextToSpeech.OnInitListener , CommandListenerManager.CommandResultListener, UserCommandExecutor.ExecutorFeedbackListener{

    private var CHANNELID="HappyBackgroundService"

    private var wakeWordManager: WakeWordManager?=null

    private var TAG= "FridayService"


    private var tts: TextToSpeech?=null

    private lateinit var userCommandExecutor: UserCommandExecutor

    private var isCallListenerActive = false

    // For call listener control
    private lateinit var callListener: CallListener

    private val fullResponse = StringBuffer()


    private lateinit var commandManager: CommandListenerManager // lateinit (Late Initialization) we are promising to the kotlin compiler I will init this variable later

    // Streaming AI reply chunk tracking - ithu vachi thaan exact ah last chunk edhu nu therinjukalam
    private var aiChunkCounter = 0
    private var lastAiUtteranceId: String? = null

    // Service ku own coroutine scope venum - lifecycleScope Activity ku mattum than irukum
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private val binder = LocalBinder()



    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()


        //initialize wakeword manager with context and listener
        wakeWordManager= WakeWordManager(this,this)
        wakeWordManager?.initVOSK()

        //add tts with context and listener for service class
        tts= TextToSpeech(this,this)

        // init command listener
        commandManager= CommandListenerManager(this,this)

        // init user command executor
        userCommandExecutor= UserCommandExecutor(this,this)

        // creating obj for call listener
        callListener = CallListener()
        startCallListener()


    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    inner class LocalBinder  : Binder(){
        fun getService(): FridayService = this@FridayService
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()



        return START_STICKY
    }

    private fun createNotificationChannel(){

        val serviceChannel= NotificationChannel(
            CHANNELID,
            "Friday is listening",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun startForeground(){
        val notification= NotificationCompat.Builder(this,CHANNELID)
            .setContentTitle("Friday Service Running")
            .setContentText("Listening for Wake Word")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            startForeground(101, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(101, notification)
        }
    }

    //own service destroy panna intha call pannuvom background la irukka yelleme stop agidum
    override fun onDestroy() {
        stopCallListener()
        wakeWordManager?.stopRecognizer()
        commandManager.destroyCommandListenerManager() // here we are not used the ? null check because the variable is lateinit so the variable should have value else the app will crash
        serviceJob.cancel()
        FridayBrain.shutdown()
        stopSelf()
        super.onDestroy()
    }

    //rom WakeWordManager interface user friday or happy nu call panna detect agutha illaiyanu inga tha theriyum true na detected else not detected
    override fun onWakeWordDetected(detected: Boolean, text: String) {
        if(detected){
            speakOut("Yes?","HEARING")
        }
    }

    // from WakeWordManager interface anga model loade agalana antha error inga show agum log cat la show pandrom
    override fun onError(error: String) {
        Log.d(TAG,error)
    }

    // TTs init
    override fun onInit(status: Int) {
        if(status== TextToSpeech.SUCCESS){
            val result=tts!!.setLanguage(Locale.US)

            if(result== TextToSpeech.LANG_MISSING_DATA || result== TextToSpeech.LANG_NOT_SUPPORTED){
                Log.d("TTS", "Intha language support illa bro illa data missing!")
            }
            else{
                Log.d("TTS", "Loaded successfully")
            }

            // utterance id ah vechi wake word manager and command listener manager ah tts stop and start la control pandrom
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener(){
                override fun onDone(utteranceId: String?) {
                    if(utteranceId=="HEARING") {
                        wakeWordManager?.stopForHandsOff()
                        commandManager.startListeningForCommand()
                        return
                    }

                    if(utteranceId=="DEACTIVATE_SERVICE"){
                        onDestroy()
                        return
                    }

                    // AI streaming chunk-ah irundha, adhu truly LAST chunk ah nu mattum check pandrom
                    // intermediate chunk na wake word restart pannama, innum TTS speak pannikittu irukkum
                    val isIntermediateAiChunk=utteranceId?.startsWith("AI_REPLY_") == true &&
                            utteranceId!=lastAiUtteranceId

                    if(wakeWordManager?.speechService==null && !isIntermediateAiChunk){
                        wakeWordManager?.restartWakeWord()
                        Log.d(TAG, "VOSK restarted end of a tts")
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    wakeWordManager?.restartWakeWord()
                }

                override fun onStart(utteranceId: String?) {
                    // no condition to check here always tts start appo wakeword ah stop pandrom because own app tts ah listen panna koodathu
                    wakeWordManager?.stopForHandsOff()
                }

            })


        }else{
            Log.d("TTS","Initialization failed for tts")
        }
    }



    // speak out fun ah vechi string send panni text ah output la audio ah show pandrom
    fun speakOut(text:String ,utteranceId: String){
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH,null,utteranceId)
    }

    private fun speakAiChunk(text: String): String {
        if (text.isBlank()) return ""

        val id = "AI_REPLY_${aiChunkCounter++}"
        lastAiUtteranceId = id

        // QUEUE_ADD ensures chunks are played sequentially without cutting each other off
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)



        fullResponse.append(text)

        return id
    }

    // from CommandListenerManager interface user happy ku kudukkura command ah inga tha varum CommandListenerManager class la irunthu ing antha command ah process pandrom
    override fun onCommandReceived(command: String) {
        Log.d("Friday", "Command: $command")

        userCommandExecutor.executeCommand(command)
    }

    // from CommandListenerManager interface user kudutha command detect agalana illa internet illana error inga varum atha speakOut fun ku send pani user ku sollurom
    override fun onCommandError(error: String) {
        speakOut(error,"COMMAND_LISTENER_MANAGER_ERROR")
    }

    // from userCommandExecutor Interface command execute pannamudiayama illana yethavathu info sollanum na anga irunthu inga vathudum
    override fun commandExecutorOnSpeak(text: String) {
        speakOut(text, "ON_COMMAND_EXECUTION")
    }

    override fun commandExecutorOnUnknownQuery(rawCommand: String) {
        askFriday(rawCommand)
    }

    override fun commandExecutorOnDeactivate() {
        speakOut("Deactivating Friday","DEACTIVATE_SERVICE")
    }

    fun startCallListener(){

        if(!isCallListenerActive){
            val intentFilter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(callListener, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(callListener, intentFilter)
            }
            isCallListenerActive = true
            Log.d(TAG, "CallListener Started - Tracking Calls")
        }

    }

    fun stopCallListener(){
        if(isCallListenerActive){
            isCallListenerActive = false
            unregisterReceiver(callListener)
            Log.d(TAG, "CallListener Stopped - Ignored Calls")
        }
    }

    fun askFriday(query: String, onComplete: (()-> Unit)? = null) {
        if(!FridayBrain.isReady){
            speakOut("Give me a second sir, still loading", "AI_NOT_READY")
            return
        }
        tts?.stop()
        aiChunkCounter = 0
        val sentenceBuffer = StringBuilder()

        serviceScope.launch {
            FridayBrain.sendMessage(
                userInput = query,
                onToken = { token ->
                    sentenceBuffer.append(token)

                    if (token.contains(".") || token.contains("!") || token.contains("?")) {
                        val englishSentence = sentenceBuffer.toString().trim()
                        sentenceBuffer.clear() // Clear for the next sentence


                        speakAiChunk(englishSentence)
                    }
                },
                onDone = {
                    // Leftover text edhachum irundha, athaiyum translate panni pesurom
                    val leftover = sentenceBuffer.toString().trim()
                    if (leftover.isNotEmpty()) {

                            speakAiChunk(leftover)
                            Log.d("Friday", "Friday AI reply complete for query: '$query'")

                            onComplete?.invoke()

                    } else {
                        Log.d("Friday", "Friday AI reply complete for query: '$query'")
                        onComplete?.invoke()
                    }
                    Log.d("Friday Response",fullResponse.toString())
                    fullResponse.delete(0,fullResponse.length)
                }
            )
        }
    }
}