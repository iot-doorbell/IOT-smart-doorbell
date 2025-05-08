package com.example.doorbell_mobile.network

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.doorbell_mobile.constants.ConstVal
import com.example.doorbell_mobile.models.CallMessage
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

object WebSocketAudioManager : WebSocketListener() {
    private lateinit var client: OkHttpClient
    private var socketAudio: WebSocket? = null
    private lateinit var appContext: Context
    private var listener: ((CallMessage) -> Unit)? = null
    private var shouldReconnect = true

    private var tts: TextToSpeech? = null
    private var isTTSReady = false

    // Khởi tạo TextToSpeech để phát âm thanh
    fun initTextToSpeech(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                isTTSReady = status == TextToSpeech.SUCCESS
            }
        }
    }

    fun speak(text: String) {
        if (isTTSReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun shutdownTTS() {
        tts?.shutdown()
        tts = null
        isTTSReady = false
    }

    fun initSocketAudio(context: Context) {
        appContext = context.applicationContext
        client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
        connectSocketAudio()
    }

    private fun connectSocketAudio() {
        val req = Request.Builder().url(ConstVal.WS_URL_AUDIO).build()
        client.newWebSocket(req, this)
    }

    fun setOnTextMessage(listener: (CallMessage) -> Unit) {
        WebSocketAudioManager.listener = listener
    }

    fun isConnectedSocketAudio(): Boolean = socketAudio != null

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Timber.tag("WebSocket").d("Audio socket opened")
        socketAudio = webSocket
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Timber.tag("WebSocket").d("Received text message: $text")
        val msg = Gson().fromJson(text, CallMessage::class.java)
        listener?.invoke(msg)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Timber.tag("WebSocket").d("Audio socket failure: ${t.message}")
        socketAudio = null
        webSocket.cancel()
        if (shouldReconnect) {
            Thread.sleep(5000)
            connectSocketAudio()
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Timber.tag("WebSocket").d("Audio socket closing: $code, reason: $reason")
        if (code != 1000) {
            Timber.tag("WebSocket").d("Not closing WebSocket, waiting for client.")
            return
        }
        webSocket.close(1000, null)
        Timber.tag("WebSocket").d("WebSocket closed by client request.")
    }

    fun closeSocketAudio() {
        shouldReconnect = false
        socketAudio?.close(1000, "Client requested to disconnect")
        socketAudio = null
        Timber.tag("WebSocket").d("Disconnected by client request.")
    }

    // Gửi văn bản (text) qua WebSocket
    fun sendTextData(text: String) {
        Timber.tag("WebSocket").d("Sending text data: $text")

        val json = JSONObject().apply {
            put("type", "app_to_server")
            put("text", text)
        }

        socketAudio?.send(json.toString())
    }

}