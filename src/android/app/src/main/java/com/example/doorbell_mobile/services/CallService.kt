package com.example.doorbell_mobile.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.doorbell_mobile.DoorbellApp
import com.example.doorbell_mobile.IncomingCallActivity
import com.example.doorbell_mobile.R
import com.example.doorbell_mobile.models.CallMessage
import com.example.doorbell_mobile.network.MqttManager
import org.json.JSONObject
import timber.log.Timber

class CallService : Service() {
    companion object {
        private const val CHANNEL_ID = "call_channel"
        private const val DETECTION_CHANNEL_ID = "detection_channel"
        private const val NOTIF_ID = 1
        private const val DETECTION_NOTIF_ID = 2
    }

    private var messageListener: ((JSONObject) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        Timber.tag("CallService").d("CallService created")

        // Initialize MQTT connection if not already connected
        if (!MqttManager.isConnected()) {
            MqttManager.initMqtt(this,
                onConnected = {
                    Timber.tag("CallService").d("MQTT connected in service")
                    setupMqttListener()
                },
                onError = { error ->
                    Timber.tag("CallService").e("MQTT connection error: $error")
                }
            )
        } else {
            setupMqttListener()
        }
    }

    private fun setupMqttListener() {
        // Create the listener function and store its reference
        messageListener = { json ->
            Timber.tag("CallService").d("Received MQTT message in CallService: $json")
            processIncomingMessage(json)
        }

        // Add the listener to MqttManager
        messageListener?.let { MqttManager.addMessageListener(it) }
    }

    private fun processIncomingMessage(json: JSONObject) {
        try {
            val status = json.optString("status", "")

            if (status == "calling") {
                // Create CallMessage object from JSON
                val msg = CallMessage(
                    callerName = json.optString("callerName", "Visitor"),
                    status = status,
                    endTime = json.optLong("endTime", System.currentTimeMillis() + 30000),
                    avatarUrl = json.optString("avatarUrl", ""),
                )
                showIncoming(msg)
            } else if (status == "person_detected") {
                // Handle person detected notification
                showPersonDetectedNotification(json)
            }
        } catch (e: Exception) {
            Timber.tag("CallService").e("Error processing message: ${e.message}")
        }
    }

    private fun showPersonDetectedNotification(json: JSONObject) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Get details from JSON
        val personName = json.optString("name", "Someone")
        val detectionTime = json.optLong("timestamp", System.currentTimeMillis())

        // Create intent for notification
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("PERSON_DETECTED", true)
            putExtra("DETECTION_TIMESTAMP", detectionTime)
            putExtra("PERSON_NAME", personName)
        }

        val requestCode = System.currentTimeMillis().toInt()
        val pi = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use ringtone sound instead of notification sound for more attention
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        // Create the notification
        val notif = NotificationCompat.Builder(this, DETECTION_CHANNEL_ID)
            .setContentTitle("Person Detected!")
            .setContentText("$personName is at your door")
            .setSmallIcon(R.drawable.ic_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setSound(soundUri)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        // Show notification
        mgr.notify(DETECTION_NOTIF_ID, notif)

        // If app is in foreground, directly show the activity
        if (DoorbellApp.isInForeground) {
            startActivity(intent)
        }

        // Play sound directly in addition to notification
        try {
            val ringtone = RingtoneManager.getRingtone(applicationContext, soundUri)
            ringtone.play()
            Toast.makeText(this, "Person detected: $personName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.tag("CallService").e("Error playing ringtone: ${e.message}")
        }

        Timber.tag("CallService").d("Person detection notification shown with sound")
    }

    private fun createNotificationChannels() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Call notifications channel
        val callChannel = NotificationChannel(
            CHANNEL_ID,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        )
        callChannel.description = "Channel for incoming call notifications"
        mgr.createNotificationChannel(callChannel)

        // Person detection channel with high importance
        val detectionChannel = NotificationChannel(
            DETECTION_CHANNEL_ID,
            "Person Detection Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for when a person is detected at your door"
            enableLights(true)
            lightColor = 0xFF0000FF.toInt()
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            importance = NotificationManager.IMPORTANCE_HIGH
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        mgr.createNotificationChannel(detectionChannel)
    }

    private fun showIncoming(msg: CallMessage) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.cancel(NOTIF_ID)

        val requestCode = System.currentTimeMillis().toInt()

        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALLER_NAME", msg.callerName)
            putExtra("END_TIME", msg.endTime)
            putExtra("AVATAR_URL", msg.avatarUrl)
            putExtra("CALLING", true)
            putExtra("TIMESTAMP", System.currentTimeMillis())
        }

        // If app is in foreground, just open activity without notification
        if (DoorbellApp.isInForeground) {
            startActivity(intent)
            return
        }

        Timber.tag("CallService").d("Creating notification for incoming call")

        val pi = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play ringtone
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtone = RingtoneManager.getRingtone(this, uri)
        ringtone.play()

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Incoming Call")
            .setContentText(msg.callerName)
            .setSmallIcon(R.drawable.ic_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        mgr.notify(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        // Clean up by removing the message listener
        messageListener?.let { MqttManager.removeMessageListener(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}