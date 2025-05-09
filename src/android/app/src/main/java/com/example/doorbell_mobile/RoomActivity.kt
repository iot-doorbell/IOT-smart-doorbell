package com.example.doorbell_mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.doorbell_mobile.adapters.ChatAdapter
import com.example.doorbell_mobile.models.ChatMessage
import com.example.doorbell_mobile.constants.ConstVal
import com.example.doorbell_mobile.network.WebSocketAudioManager
import com.example.doorbell_mobile.network.WebSocketSignalManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class RoomActivity : AppCompatActivity() {

    private lateinit var webViewVideo: WebView
    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnEnd: MaterialButton
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var errorText: TextView
    private val client = OkHttpClient()

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
        errorText =findViewById(R.id.tvErrorMessage)

        // --- Setup WebView ---
        val webSettings: WebSettings = webViewVideo.settings
        webSettings.javaScriptEnabled = true

        webViewVideo.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                showError("Lỗi khi tải stream: ${error?.description}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webViewVideo.visibility = View.VISIBLE
                errorText.visibility = View.GONE
            }
        }

        webViewVideo.loadUrl("${ConstVal.DOORBELL_URL}/${ConstVal.MJEG_ENDPOINT}?portrait=0")


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
            val json = JSONObject().apply {
                put("status", "end")
                put("time", System.currentTimeMillis())
            }
            WebSocketSignalManager.sendMessage(json)
            WebSocketAudioManager.closeSocketAudio()
            stopVideoFeedAndExit()
        }
    }

    private fun showError(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            webViewVideo.visibility = View.GONE
            errorText.visibility = View.VISIBLE
            errorText.text = message
        }
    }

    private fun finishActivity() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun stopVideoFeedAndExit() {
        val request = Request.Builder()
            .url("${ConstVal.DOORBELL_URL}/${ConstVal.STOP_STREAM_ENDPOINT}")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    // Gọi dù lỗi (vẫn cho người dùng rời khỏi màn)
                    finishActivity()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                runOnUiThread {
                    finishActivity()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        WebSocketAudioManager.shutdownTTS()
    }
}