package com.example.friday

import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID

class FridayJarvisBridge(private val brokerUrl : String , private val context:android.content.Context, private val listener: BridgeListener) {

    companion object{
        const val TOPIC_FRIDAY_STATE = "bridge/friday/state"               // Friday -> Jarvis
        const val TOPIC_JARVIS_STATE = "bridge/jarvis/state"               // Jarvis -> Friday
        const val TOPIC_FRIDAY_COMMAND = "bridge/friday/command"           // Jarvis -> Friday (control Friday)
        const val TOPIC_JARVIS_COMMAND = "bridgejarvis/command"            // friday -> Jarvis (control Jarvis)

        private const val FAST_QOS = 0  // fire-and-forget, lowest latency

        private const val SAFE_QOS = 1  // use for commands that must not be dropped
    }

    interface BridgeListener{
        fun onJarvisStateReceived(state: JSONObject)        // Jarvis's live state, ms-level updates

        fun onCommandReceived(command: JSONObject)          // Jarvis is telling FRIDAY to do something.

        fun onConnectionChanged(connected: Boolean)

        fun onError(error: Throwable)
    }

    private val clientId = "friday-android-" + UUID.randomUUID().toString().take(8)
    private lateinit var client: MqttAndroidClient

    fun connect(){
        client = MqttAndroidClient(context, brokerUrl, clientId, MemoryPersistence())

        client.setCallback(object : MqttCallbackExtended{
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                TODO("Not yet implemented")
            }

            override fun connectionLost(cause: Throwable?) {
                TODO("Not yet implemented")
            }

            override fun messageArrived(
                topic: String?,
                message: MqttMessage?
            ) {
                TODO("Not yet implemented")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                TODO("Not yet implemented")
            }

        })
    }

    private fun subscribeToJarvisTopics(){
        try{

            client.subscribe(TOPIC_JARVIS_STATE,FAST_QOS)
            client.subscribe(TOPIC_FRIDAY_COMMAND,SAFE_QOS)

        }catch (ex: MqttException){
            listener.onError(ex)
        }
    }

    /** Broadcast Friday's current state. Retained so JARVIS gets it instantly on (re)connect. */
    fun publishState(stateJson: JSONObject){
        publish(TOPIC_FRIDAY_STATE, stateJson, FAST_QOS, retained = true)
    }

    /** Send a command to CONTROL JARVIS (e.g. {"action":"speak","text":"..."}). */
    fun publishCommand(commandJson: JSONObject, qos: Int = SAFE_QOS) {
        publish(TOPIC_JARVIS_COMMAND, commandJson, qos, retained = false)
    }

    private fun publish(topic: String, json: JSONObject, qos: Int, retained: Boolean) {

        if(!::client.isInitialized || !client.isConnected){
            listener.onError(IllegalStateException("MQTT client not connected"))
            return
        }

        try{

            val message = MqttMessage(json.toString().toByteArray()).apply {
                this.qos = qos
                isRetained = retained
            }
            client.publish(topic,message)


        }catch (e: MqttException){
            listener.onError(e)
        }
    }

    fun disconnect(){
        if(::client.isInitialized && client.isConnected){
            try{
                publish(TOPIC_FRIDAY_STATE, JSONObject().apply { put("status", "offline") }, FAST_QOS, retained = true)
                client.disconnect()
            }catch (e: MqttException) {
                listener.onError(e)
            }
        }
    }
}