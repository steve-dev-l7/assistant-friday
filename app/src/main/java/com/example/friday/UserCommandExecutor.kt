package com.example.friday

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log

class UserCommandExecutor(private val context: Context, private val listener: ExecutorFeedbackListener) {

    private val TAG = "UserCommandExecutor"

    private val intentClassifier = IntentClassifier(context)
    private val reminderHandler = Handler(Looper.getMainLooper())

    private val contactsMap = HashMap<String, String>()

    interface ExecutorFeedbackListener {
        fun commandExecutorOnSpeak(text: String)
        fun commandExecutorOnUnknownQuery(rawCommand: String)
        fun commandExecutorOnDeactivate()
    }

    init {
        loadContactsToHashMap()
    }

    private fun loadContactsToHashMap(){
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)

        context.contentResolver.query(uri, projection, null, null, null)?.use {cursor ->
                val nameIndex =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    if (nameIndex != -1 && numberIndex != -1) {
                        val name =
                            cursor.getString(nameIndex)?.lowercase()?.replace("\\s+".toRegex(), "")
                                ?: ""
                        val number =
                            cursor.getString(numberIndex)?.replace("\\s+".toRegex(), "") ?: ""
                        if (name.isNotEmpty() && number.isNotEmpty()) {
                            contactsMap[name] = number
                        }
                    }
                }
        }
    }

    fun executeCommand(rawCommand: String) {

        // 1. Model direct-ah CommandResult return pannuthu (slots + confidence ellame ulla irukku)
        val result = intentClassifier.predict(rawCommand)

        Log.d(TAG, "processCommand: result=$result")

        // 2. Confidence check (Low confidence iruntha reject / unknown forward)
        if (result.confidence < 0.40f || result.intent.equals("UNKNOWN", ignoreCase = true)) {
            Log.d(TAG, "Low confidence (${result.confidence}) or UNKNOWN intent. Forwarding raw query: '$rawCommand'")
            listener.commandExecutorOnUnknownQuery(rawCommand)
            return
        }

        // Case-insensitive comparison kaga uppercase use pandrom
        when (result.intent.uppercase()) {

            "CALL" -> {
                val target = result.target
                if (target.isNullOrBlank()) {
                    listener.commandExecutorOnSpeak("Who do you want to call?")
                    return
                }
                val phoneNumber = getContactNumberByName(target)
                if (phoneNumber == null) {
                    listener.commandExecutorOnSpeak("Contact not found for $target")
                    return
                }
                makePhoneCall(target, phoneNumber)
                Log.d(TAG, "Detected command as call")
            }

            "MESSAGE" -> {
                val target = result.target
                val message = result.text

                if (target.isNullOrBlank()) {
                    listener.commandExecutorOnSpeak("Who should I message?")
                    return
                }
                if (message.isNullOrBlank()) {
                    listener.commandExecutorOnSpeak("What is the message?")
                    return
                }

                val phoneNumber = getContactNumberByName(target)
                if (phoneNumber == null) {
                    listener.commandExecutorOnSpeak("Contact not found for $target")
                    return
                }
                sendSms(target, phoneNumber, message)
                Log.d(TAG, "Detected command as message")
            }

            "LAUNCH" -> {
                val app = result.target
                if (app.isNullOrBlank()) {
                    listener.commandExecutorOnSpeak("Which app should I open?")
                    return
                }
                openApplication(app)
                Log.d(TAG, "Detected command as open application")
            }

            "REMINDER" -> {
                val task = result.task ?: "reminder"
                val time = result.time
                val timeType = result.timeType ?: "relative"

                if (time.isNullOrBlank()) {
                    listener.commandExecutorOnSpeak("When should I remind you?")
                    return
                }
                setDirectServiceReminder(task, time, timeType)
            }

            "DEACTIVATE" -> {
                Log.d(TAG, "Detected command as deactivate")
                listener.commandExecutorOnDeactivate()
            }

            else -> {
                Log.d(TAG, "Intent not handled locally - forwarding raw query to Friday AI: '$rawCommand'")
                listener.commandExecutorOnUnknownQuery(rawCommand)
            }
        }
    }

    fun getContactNumberByName(targetName: String): String? {
        val rawTarget = targetName.lowercase().replace("\\s+".toRegex(), "")
        val phoneticTarget = phoneticNormalize(targetName)

        var bestMatchNumber: String? = null
        var highestScore = 0.0

        for ((originalName, number) in contactsMap) {
            val rawContact = originalName.lowercase().replace("\\s+".toRegex(), "")

            // TIER 1: Direct Raw Match (Exact spelling check)
            val rawScore = calculateSimilarity(rawTarget, rawContact)

            if (rawScore >= 0.8 && rawScore > highestScore) {
                highestScore = rawScore
                bestMatchNumber = number
                continue // 80% mela kedaichiduchu, so next contact check poga move aagalam
            }

            // TIER 2: Phonetic Match (Spelling thappa iruntha mattum ithu vela seiyum)
            // Kadir vs Kathir mathiri cases-kku ithu kaappathum
            val phoneticContact = phoneticNormalize(originalName)
            val phoneticScore = calculateSimilarity(phoneticTarget, phoneticContact)

            // Phonetic pandrathala strict ah 85% mela iruntha mattum edukkalam
            if (phoneticScore >= 0.85 && phoneticScore > highestScore) {
                highestScore = phoneticScore
                bestMatchNumber = number
            }
        }

        return bestMatchNumber
    }

    // 1. THE PHONETIC NORMALIZER (Sound Flattening)
    private fun phoneticNormalize(name: String): String {
        var str = name.lowercase().replace("\\s+".toRegex(), "") // Space and caps remove pandrom

        // Tamil/Indian English spelling confusions ah replace pandrom
        str = str.replace("sh", "s")
        str = str.replace("th", "t")
        str = str.replace("d", "t")   // kadir -> katir, kathir -> katir
        str = str.replace("g", "k")   // ganesh -> kanesh
        str = str.replace("b", "p")   // balu -> palu
        str = str.replace("ph", "f")
        str = str.replace("ee", "i")
        str = str.replace("oo", "u")

        // Double letters ah single letter ah aakurom (ex: sasi vs sassi -> sasi)
        str = str.replace(Regex("(.)\\1+"), "$1")

        return str
    }

    private fun calculateSimilarity(s1: String, s2: String) : Double {

        if (s1 == s2) return 1.0
        val maxLength = Math.max(s1.length, s2.length)

        if (maxLength == 0) return 1.0

        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else {
                    if (j > 0) {
                        var newValue = costs[j - 1]
                        if (s1[i - 1] != s2[j - 1]) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1
                        }
                        costs[j - 1] = lastValue
                        lastValue = newValue
                    }
                }
            }
            if (i > 0) costs[s2.length] = lastValue

        }
        val distance = costs[s2.length]
        return (maxLength - distance).toDouble() / maxLength.toDouble()
    }

    private fun normalizeName(name: String): String {
        val emojiRegex = Regex(
            "[\\p{So}\\p{Sk}\\p{Cn}\uD83C-\uDBFF\uDC00-\uDFFF\u200D\uFE0F]"
        )
        return name
            .replace(emojiRegex, "")
            .trim()
            .replace("\\s+".toRegex(), " ")
            .lowercase()
    }

    private fun makePhoneCall(contactName: String, phoneNumber: String) {
        listener.commandExecutorOnSpeak("Calling $contactName")

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val uri = Uri.fromParts("tel", phoneNumber, null)
                telecomManager.placeCall(uri, Bundle())
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                listener.commandExecutorOnSpeak("Call permission denied")
            } catch (e: Exception) {
                Log.e(TAG, "Error placing call: ${e.message}")
                listener.commandExecutorOnSpeak("Something went wrong while trying to call")
            }
        }, 1500)
    }

    private fun openApplication(appName: String) {
        val packageName = when (appName.lowercase()) {
            // Social / Communication
            "whatsapp" -> "com.whatsapp"
            "instagram" -> "com.instagram.android"
            "facebook" -> "com.facebook.katana"
            "messenger" -> "com.facebook.orca"
            "twitter", "x" -> "com.twitter.android"
            "snapchat" -> "com.snapchat.android"
            "telegram" -> "org.telegram.messenger"
            "discord" -> "com.discord"
            "linkedin" -> "com.linkedin.android"
            "reddit" -> "com.reddit.frontpage"
            "pinterest" -> "com.pinterest"

            // Browsers / Google apps
            "chrome" -> "com.android.chrome"
            "youtube" -> "com.google.android.youtube"
            "gmail" -> "com.google.android.gm"
            "maps", "google maps" -> "com.google.android.apps.maps"
            "calendar" -> "com.google.android.calendar"
            "google docs", "docs" -> "com.google.android.apps.docs"

            // Music / Entertainment
            "spotify" -> "com.spotify.music"
            "netflix" -> "com.netflix.mediaclient"
            "prime video", "amazon prime" -> "com.amazon.avod.thirdpartyclient"
            "hotstar", "disney hotstar" -> "in.startv.hotstar"
            "youtube music" -> "com.google.android.apps.youtube.music"

            // Shopping / Food / Travel
            "amazon" -> "com.amazon.mShop.android.shopping"
            "flipkart" -> "com.flipkart.android"
            "zomato" -> "com.application.zomato"
            "swiggy" -> "in.swiggy.android"
            "uber" -> "com.ubercab"
            "ola" -> "com.olacabs.customer"
            "paytm" -> "net.one97.paytm"
            "phonepe" -> "com.phonepe.app"
            "google pay", "gpay" -> "com.google.android.apps.nbu.paisa.user"

            // Games
            "free fire" -> "com.dts.freefireth"
            "free fire max" -> "com.dts.freefiremax"
            "pubg", "pubg mobile" -> "com.tencent.ig"
            "bgmi" -> "com.pubg.imobile"
            "call of duty", "cod mobile" -> "com.activision.callofduty.shooter"
            "clash of clans" -> "com.supercell.clashofclans"
            "clash royale" -> "com.supercell.clashroyale"
            "minecraft" -> "com.mojang.minecraftpe"
            "roblox" -> "com.roblox.client"
            "genshin impact", "genshin" -> "com.miHoYo.GenshinImpact"

            // Utility
            "calculator" -> "com.android.calculator2"
            "gallery" -> "com.android.gallery3d"
            "camera" -> "com.android.camera2"

            else -> null
        }

        if (packageName == null) {
            listener.commandExecutorOnSpeak("Couldn't open $appName")
            return
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            listener.commandExecutorOnSpeak("Opening $appName")
            try {
                context.startActivity(launchIntent)
            } catch (exception: Exception) {
                Log.d(TAG, "Failed to open $appName ($exception)")
                listener.commandExecutorOnSpeak("Couldn't open $appName")
            }
        } else {
            listener.commandExecutorOnSpeak("$appName is not installed")
        }
    }

    private fun sendSms(targetName: String, phoneNumber: String, messageText: String) {
        try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
            val messageParts = smsManager.divideMessage(messageText)
            smsManager.sendMultipartTextMessage(phoneNumber, null, messageParts, null, null)

            listener.commandExecutorOnSpeak("Done!")
            Log.d(TAG, "Multipart SMS Sent Successfully to $targetName. Total parts: ${messageParts.size}")
        } catch (e: Exception) {
            Log.e(TAG, "SMS Send Error: ${e.message}")
            listener.commandExecutorOnSpeak("Can't send SMS right now")
        }
    }

    private fun setDirectServiceReminder(taskText: String, timeText: String, timeType: String) {
        val triggerTimeMillis = if (timeType == "relative") {
            calculateRelativeTime(timeText)
        } else {
            calculateAbsoluteTime(timeText)
        }

        if (triggerTimeMillis == null) {
            listener.commandExecutorOnSpeak("Sorry, I couldn't understand that time")
            return
        }

        scheduleAlarm(taskText, triggerTimeMillis)
        listener.commandExecutorOnSpeak("Okay, I'll remind you to $taskText at $timeText")
    }

    private fun scheduleAlarm(taskText: String, triggerTimeMillis: Long) {
        val delayMillis = triggerTimeMillis - System.currentTimeMillis()

        if (delayMillis <= 0) {
            listener.commandExecutorOnSpeak("That time already crossed, Sir!")
            return
        }

        val readableTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(triggerTimeMillis))

        reminderHandler.postDelayed({
            listener.commandExecutorOnSpeak("Hey sir, reminder: $taskText at $readableTime")
        }, delayMillis)

        Log.d(TAG, "Reminder set for '$taskText' after ${delayMillis}ms (at $readableTime)")
    }

    private fun calculateRelativeTime(timeText: String): Long? {
        val regex = Regex("(\\d+)\\s*(second|sec|minute|min|hour|hr)s?", RegexOption.IGNORE_CASE)
        val match = regex.find(timeText) ?: return null

        val value = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()

        val millis = when {
            unit.startsWith("sec") -> value * 1000L
            unit.startsWith("min") -> value * 60 * 1000L
            unit.startsWith("hour") || unit.startsWith("hr") -> value * 60 * 60 * 1000L
            else -> return null
        }

        return System.currentTimeMillis() + millis
    }

    private fun calculateAbsoluteTime(timeText: String): Long? {
        val cleanText = timeText.trim().lowercase()
        val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?")
        val match = timeRegex.find(cleanText) ?: return null

        val hourStr = match.groupValues[1]
        val minuteStr = match.groupValues[2]
        val meridiem = match.groupValues[3].replace(".", "")

        var hour = hourStr.toIntOrNull() ?: return null
        val minute = if (minuteStr.isNotEmpty()) minuteStr.toIntOrNull() ?: 0 else 0

        if (meridiem == "pm" && hour != 12) {
            hour += 12
        } else if (meridiem == "am" && hour == 12) {
            hour = 0
        }

        if (hour !in 0..23 || minute !in 0..59) return null

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        return calendar.timeInMillis
    }
}