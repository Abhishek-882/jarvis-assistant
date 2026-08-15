package com.jarvis.assistant.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import com.jarvis.assistant.ai.JarvisAction

/**
 * Executes phone control commands that JARVIS can perform.
 *
 * Handles all physical phone actions via Android Intents and system APIs:
 * - Open apps
 * - Set alarms/timers
 * - Make phone calls
 * - Send SMS messages
 * - Toggle flashlight
 * - Control volume
 * - Web search
 */
class CommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "JarvisCommands"

        // Common app package names for fuzzy matching
        private val APP_PACKAGES = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "spotify" to "com.spotify.music",
            "chrome" to "com.android.chrome",
            "google" to "com.google.android.googlequicksearchbox",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "camera" to "com.android.camera",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "settings" to "com.android.settings",
            "photos" to "com.google.android.apps.photos",
            "google photos" to "com.google.android.apps.photos",
            "telegram" to "org.telegram.messenger",
            "netflix" to "com.netflix.mediaclient",
            "amazon" to "com.amazon.mShop.android.shopping",
            "snapchat" to "com.snapchat.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "reddit" to "com.reddit.frontpage",
            "discord" to "com.discord",
            "zoom" to "us.zoom.videomeetings",
            "teams" to "com.microsoft.teams",
            "outlook" to "com.microsoft.office.outlook",
            "uber" to "com.ubercab",
            "lyft" to "me.lyft.android",
            "messages" to "com.google.android.apps.messaging",
            "phone" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "files" to "com.google.android.documentsui",
            "play store" to "com.android.vending",
            "music" to "com.google.android.music",
        )
    }

    /**
     * Execute a JARVIS action.
     */
    fun execute(action: JarvisAction) {
        Log.i(TAG, "Executing action: $action")

        try {
            when (action) {
                is JarvisAction.OpenApp -> openApp(action.appName)
                is JarvisAction.SetAlarm -> setAlarm(action.hour, action.minute, action.message)
                is JarvisAction.MakeCall -> makeCall(action.contact)
                is JarvisAction.SendSms -> sendSms(action.contact, action.message)
                is JarvisAction.ToggleFlashlight -> toggleFlashlight(action.on)
                is JarvisAction.SetVolume -> setVolume(action.level)
                is JarvisAction.WebSearch -> webSearch(action.query)
                is JarvisAction.SetTimer -> setTimer(action.minutes, action.message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute action: $action", e)
        }
    }

    /**
     * Open an app by name, using fuzzy matching against known package names.
     */
    private fun openApp(appName: String) {
        val normalizedName = appName.lowercase().trim()
        val packageName = APP_PACKAGES[normalizedName]
            ?: findAppByName(normalizedName)
            ?: normalizedName

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "Opened app: $packageName")
        } else {
            // Try opening Play Store for the app
            Log.w(TAG, "App not found: $packageName — opening Play Store search")
            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://search?q=$appName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(playIntent)
            } catch (e: Exception) {
                // Play Store not available, try browser
                val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/search?q=$appName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }
        }
    }

    /**
     * Find an app by searching installed packages by label name.
     */
    private fun findAppByName(name: String): String? {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase()
            label.contains(name) || name.contains(label)
        }?.packageName
    }

    /**
     * Set an alarm using the system alarm clock.
     */
    private fun setAlarm(hour: Int, minute: Int, message: String?) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Log.i(TAG, "Alarm set for $hour:${minute.toString().padStart(2, '0')}")
    }

    /**
     * Set a timer.
     */
    private fun setTimer(minutes: Int, message: String?) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Log.i(TAG, "Timer set for $minutes minutes")
    }

    /**
     * Make a phone call. Tries to find the contact by name first.
     */
    private fun makeCall(contact: String) {
        val phoneNumber = lookupContactNumber(contact) ?: contact

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            Log.i(TAG, "Calling: $phoneNumber")
        } catch (e: SecurityException) {
            // Fallback to dialer if CALL_PHONE not granted
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            Log.w(TAG, "CALL_PHONE not granted, opening dialer instead")
        }
    }

    /**
     * Send an SMS message.
     */
    private fun sendSms(contact: String, message: String) {
        val phoneNumber = lookupContactNumber(contact) ?: contact

        try {
            // Try sending directly
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager?.sendTextMessage(phoneNumber, null, message, null, null)
            Log.i(TAG, "SMS sent to $phoneNumber: $message")
        } catch (e: SecurityException) {
            // Fallback to SMS app
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.w(TAG, "SEND_SMS not granted, opening SMS app instead")
        }
    }

    /**
     * Toggle the flashlight on or off.
     */
    private fun toggleFlashlight(on: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            cameraId?.let {
                cameraManager.setTorchMode(it, on)
                Log.i(TAG, "Flashlight ${if (on) "ON" else "OFF"}")
            } ?: Log.w(TAG, "No flashlight available")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight", e)
        }
    }

    /**
     * Set the media volume level (0-100).
     */
    private fun setVolume(level: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (level / 100f * maxVolume).toInt().coerceIn(0, maxVolume)

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            targetVolume,
            AudioManager.FLAG_SHOW_UI
        )
        Log.i(TAG, "Volume set to $targetVolume/$maxVolume ($level%)")
    }

    /**
     * Perform a web search.
     */
    private fun webSearch(query: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Log.i(TAG, "Web search: $query")
    }

    /**
     * Look up a contact's phone number by name.
     */
    private fun lookupContactNumber(name: String): String? {
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val number = it.getString(
                        it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    )
                    Log.i(TAG, "Found contact '$name' → $number")
                    return number
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
        } catch (e: Exception) {
            Log.e(TAG, "Contact lookup error for '$name'", e)
        }

        return null
    }
}
