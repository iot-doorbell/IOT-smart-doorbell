package com.example.doorbell_mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.doorbell_mobile.adapters.ChatAdapter
import com.example.doorbell_mobile.models.ChatMessage
import com.example.doorbell_mobile.constants.ConstVal
import com.example.doorbell_mobile.network.WebSocketAudioManager
import com.google.android.material.button.MaterialButton

class RoomActivity : AppCompatActivity() {

    private lateinit var webViewVideo: WebView
    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnEnd: MaterialButton
    private lateinit var chatAdapter: ChatAdapter

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        // --- Khởi tạo UI ---
        webViewVideo = findViewById(R.id.webViewVideo)
        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnEnd = findViewById(R.id.btnEnd)

        // --- Setup WebView ---
        webViewVideo.settings.javaScriptEnabled = true
        webViewVideo.webViewClient = WebViewClient()
        webViewVideo.loadUrl("${ConstVal.DOORBELL_URL}/${ConstVal.MJEG_ENDPOINT}")

        // --- Khởi tạo chat adapter ---
        chatAdapter = ChatAdapter(mutableListOf())
        rvChat.apply {
            layoutManager = LinearLayoutManager(this@RoomActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }

        // --- Init WebSocket + TTS ---
        WebSocketAudioManager.initTextToSpeech(applicationContext)
        WebSocketAudioManager.initSocketAudio(this)

        // --- Nhận tin nhắn từ WebSocket ---
        WebSocketAudioManager.setOnTextMessage { msg ->
            runOnUiThread {
                chatAdapter.addMessage(ChatMessage(msg.text ?: "", false))
                rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                if (!msg.text.isNullOrEmpty()) WebSocketAudioManager.speak(msg.text)
            }
        }

        // --- Gửi tin nhắn ---
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                WebSocketAudioManager.sendTextData(text)
                chatAdapter.addMessage(ChatMessage(text, true))
                rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                etMessage.setText("")
            }
        }

        // --- End Call ---
        btnEnd.setOnClickListener {
            WebSocketAudioManager.closeSocketAudio()
            finishActivity()
        }
    }

    private fun finishActivity() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        WebSocketAudioManager.shutdownTTS()
    }
}