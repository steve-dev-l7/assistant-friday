package com.example.friday

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log

class CallListener : BroadcastReceiver() {

    companion object {
        // State track panna oru variable
        var isRinging = false
    }

    var TAG ="CallListener"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    isRinging = true
                    Log.d(TAG, "Phone is ringing...")

                    // Inga Friday-oda mic/STT-a pause pannidunga
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    isRinging = false
                    Log.d(TAG, "Call answered.")
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    if (isRinging) {
                        // Ring aagi direct ah IDLE vantha, it's a MISSED CALL!
                        isRinging = false
                        Log.d(TAG, "Missed call detected from: $incomingNumber")

                        incomingNumber?.let {
                            sendFridaySms(it)
                        }
                    }
                }
            }
        }
    }

    private fun sendFridaySms(phoneNumber: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val messageParts = smsManager.divideMessage(
                "Hello, this is Friday, ${UserInfo.userName}'s AI Assistant. Steve is currently unavailable. Please leave a message, and he will get back to you shortly."
            )
            smsManager.sendMultipartTextMessage(phoneNumber, null, messageParts, null, null)
            Log.d(TAG, "Auto-SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
        }
    }
}