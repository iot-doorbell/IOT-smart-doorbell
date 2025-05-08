package com.example.doorbell_mobile.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.doorbell_mobile.DoorbellApp
import com.example.doorbell_mobile.IncomingCallActivity
import com.example.doorbell_mobile.R
import com.example.doorbell_mobile.models.CallMessage
import com.example.doorbell_mobile.network.WebSocketSignalManager
import timber.log.Timber

class CallService : Service() {
    companion object {
        private const val CHANNEL_ID = "call_channel"
        private const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        Timber.tag("CallService").d("CallService created")

        WebSocketSignalManager.setOnCallMessage { msg ->
            Timber.tag("CallService").d("Received call message: $msg")
            if (msg.status == "calling" && msg.type == "server_to_app") {
                showIncoming(msg)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            )
            chan.description = "Channel for incoming call notifications"
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(chan)
        }
    }

    private fun showIncoming(msg: CallMessage) {

        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALLER_NAME", msg.callerName)
            putExtra("END_TIME", msg.endTime)
            putExtra("AVATAR_URL", msg.avatarUrl)
            putExtra("CALLING", true)
        }

        // Nếu app đang ở foreground, không cần hiển thị thông báo mà chỉ cần mở activity
        if (DoorbellApp.isInForeground) {
            startActivity(intent)
            return
        }

        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Phát chuông
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtone = RingtoneManager.getRingtone(this, uri)
        ringtone.play()

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Incoming Call")
            .setContentText(msg.callerName)
            .setSmallIcon(R.drawable.ic_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, notif)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}