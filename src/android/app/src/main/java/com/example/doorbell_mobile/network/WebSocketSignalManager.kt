package com.example.doorbell_mobile.network

import android.content.Context
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

object WebSocketSignalManager : WebSocketListener() {
    private lateinit var client: OkHttpClient
    private var socketSignal: WebSocket? = null
    private lateinit var appContext: Context
    private var listener: ((CallMessage) -> Unit)? = null

    fun initSocketSignal(context: Context) {
        appContext = context.applicationContext
        client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
        connectSocketSignal()
    }

    private fun sendRegisterMessage() {
        val json = Gson().toJson(mapOf("type" to "register", "role" to "app"))
        socketSignal?.send(json)
    }

    fun setOnCallMessage(listener: (CallMessage) -> Unit) {
        WebSocketSignalManager.listener = listener
    }

    fun isConnectedSocketSignal(): Boolean = socketSignal != null

    private fun connectSocketSignal() {
        val req = Request.Builder().url(ConstVal.WS_URL_SIGNAL).build()
        client.newWebSocket(req, this)
    }

    fun sendMessage(data: JSONObject) {
        var json = Gson().toJson(data)
        // check if json has {"nameValuePairs":{data}}, will send data instead
        if (json.contains("nameValuePairs")) {
            json = json.substringAfter("{\"nameValuePairs\":").substringBeforeLast("}")
        }
        Timber.tag("WebSocket").d("Sending: $json")
        // add type: app_to_server
        val jsonObject = JSONObject(json)
        jsonObject.put("type", "app_to_server")
        json = jsonObject.toString()
        socketSignal?.send(json)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        socketSignal = webSocket
        Timber.tag("WebSocket").d("Connected with socket: ${webSocket.request().url}")

        // send message register to server
        sendRegisterMessage()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (text.isEmpty()) Timber.tag("WebSocket").d("Received empty message")
        Timber.tag("WebSocket").d("Received: $text")
        val msg = Gson().fromJson(text, CallMessage::class.java)
        listener?.invoke(msg)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Timber.tag("WebSocket").e("Error: ${t.message}")
        webSocket.cancel()
        Thread.sleep(5000)
        connectSocketSignal()
        sendRegisterMessage()
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Timber.tag("WebSocket").w("Received closing: $code, reason: $reason")

        // Kiểm tra nếu code không phải do phía server yêu cầu đóng (code 1000 là yêu cầu đóng hợp lệ từ client)
        if (code != 1000) {
            Timber.tag("WebSocket").d("Not closing WebSocket, waiting for client.")
            return  // Không đóng WebSocket nếu không phải do phía client yêu cầu
        }

        // Nếu là đóng hợp lệ từ phía client (code 1000), cho phép đóng kết nối
        webSocket.close(1000, null)
        Timber.tag("WebSocket").d("WebSocket closed by client request.")
    }

    fun disconnectSignal() {
        // Chỉ đóng kết nối khi có yêu cầu từ phía client (hoặc điều kiện khác bạn mong muốn)
        if (socketSignal != null) {
            socketSignal?.close(
                1000,
                "Client requested to disconnect"
            )  // Thêm lý do cho việc đóng kết nối
            socketSignal = null
            Timber.tag("WebSocket").d("Disconnected by client request.")
        }
    }
}
