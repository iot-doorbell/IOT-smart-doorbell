package com.example.doorbell_mobile

import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.doorbell_mobile.network.MqttManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var avatarIv: ImageView
    private lateinit var callerNameTv: TextView
    private lateinit var btnAccept: ImageButton
    private lateinit var btnReject: ImageButton
    private lateinit var player: MediaPlayer
    private var timerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        // View references
        avatarIv = findViewById(R.id.avatar)
        callerNameTv = findViewById(R.id.textCallerName)
        btnAccept = findViewById(R.id.btnAccept)
        btnReject = findViewById(R.id.btnReject)

        // Intent data
        val avatarUrl = intent.getStringExtra("AVATAR_URL")
        val caller = intent.getStringExtra("CALLER_NAME") ?: "Doorbell"
        val endTime = intent.getLongExtra("END_TIME", System.currentTimeMillis() + 30000)

        val callingStatus = intent.getBooleanExtra("CALLING", false)
        if (!callingStatus) {
            returnToHistory()
            return
        }

        callerNameTv.text = caller
        avatarUrl?.let { Glide.with(this).load(R.drawable.avatar).circleCrop().into(avatarIv) }

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = MediaPlayer.create(this, uri)
        player.isLooping = true
        player.start()

        startCountdown(endTime)

        // Button handlers
        btnReject.setOnClickListener {
            timerJob?.cancel()
            player.stop()

            // Send rejection via MQTT
            val json = JSONObject().apply {
                put("status", "end")
                put("time", System.currentTimeMillis())
            }

            MqttManager.sendMessage(json,
                onError = { errorMsg ->
                    Timber.tag("IncomingCallActivity").e("Error sending reject message: $errorMsg")
                }
            )

            returnToHistory()
        }

        btnAccept.setOnClickListener {
            timerJob?.cancel()
            player.stop()

            // Send acceptance via MQTT
            val json = JSONObject().apply {
                put("status", "accept")
                put("time", System.currentTimeMillis())
            }

            MqttManager.sendMessage(json,
                onError = { errorMsg ->
                    Timber.tag("IncomingCallActivity").e("Error sending accept message: $errorMsg")
                    Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                    returnToHistory()
                }
            )

            joinRoom()
        }
    }

    private fun startCountdown(endTime: Long) {
        Timber.tag("IncomingCallActivity").d("Duration: ${endTime - System.currentTimeMillis()}")
        timerJob = lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                val rem = endTime - System.currentTimeMillis()
                if (rem <= 0) {
                    Timber.tag("IncomingCallActivity").d("Missed call")
                    val json = JSONObject().apply {
                        put("status", "end")
                        put("time", System.currentTimeMillis())
                    }

                    MqttManager.sendMessage(json)
                    player.stop()
                    returnToHistory()
                    break
                }
                delay(1000)
            }
        }
    }

    private fun joinRoom() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                runOnUiThread { joinRoomActivity() }
            } catch (e: Exception) {
                e.printStackTrace()
                Timber.tag("IncomingCallActivity").d("Error joining call: ${e.message}")
                Toast.makeText(this@IncomingCallActivity, "Error joining call", Toast.LENGTH_SHORT)
                    .show()
                runOnUiThread { returnToHistory() }
            }
        }
    }

    private fun joinRoomActivity() {
        val intent = Intent(this, RoomActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun returnToHistory() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("selected_tab", "bell_history")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        if (::player.isInitialized && player.isPlaying) {
            player.stop()
            player.release()
        }
    }
}