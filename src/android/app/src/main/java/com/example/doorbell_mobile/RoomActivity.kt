package com.example.doorbell_mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.doorbell_mobile.network.MqttManager
import org.json.JSONObject
import timber.log.Timber
import android.webkit.ConsoleMessage

class RoomActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val PERMISSION_REQUEST_CODE = 1000

    private var messageListener: ((JSONObject) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        // Request necessary permissions
        requestPermissions()

        webView = findViewById(R.id.webViewRoom)

        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false  // Important for audio
            cacheMode = WebSettings.LOAD_NO_CACHE
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Enable additional settings for audio
            setGeolocationEnabled(true)
            databaseEnabled = true
        }

        // Set WebChromeClient to handle permission requests
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    Timber.tag("RoomActivity").d("Permission requested: ${request.resources.joinToString()}")
                    request.grant(request.resources)
                }
            }
        }

        // Add JavaScript interface for communication with HTML
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        // Set up MQTT message listener
        setupMqttMessageListener()

        // Load the HTML file
        webView.loadUrl("file:///android_asset/html/index.html")
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Timber.tag("RoomActivity").d("All permissions granted")
                // Reload the page to apply permissions
                webView.reload()
            } else {
                Timber.tag("RoomActivity").e("Audio permissions denied")
                Toast.makeText(this, "Các quyền cần thiết bị từ chối", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMqttMessageListener() {
        // Create listener function and store its reference
        messageListener = { json ->
            try {
                val status = json.optString("status", "")

                if (status == "end_call") {
                    runOnUiThread {
                        stopVideoFeedAndExit()
                    }
                }
            } catch (e: Exception) {
                Timber.tag("RoomActivity").e("Error processing MQTT message: ${e.message}")
            }
        }

        // Add the listener to MqttManager
        messageListener?.let { MqttManager.addMessageListener(it) }
    }

    private fun stopVideoFeedAndExit() {
        // Send end call message via MQTT
//        val json = JSONObject().apply {
//            put("status", "end")
//            put("time", System.currentTimeMillis())
//        }
//
//        MqttManager.sendMessage(json,
//            onError = { errorMsg ->
//                Timber.tag("RoomActivity").e("Error sending end call: $errorMsg")
//                // Continue with exit even if MQTT fails
//                finishAndGoToMain()
//            }
//        )

        finishAndGoToMain()
    }

    private fun finishAndGoToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun sendMqttMessage(message: String) {
            try {
                val json = JSONObject(message)
                MqttManager.sendMessage(json,
                    onError = { errorMsg ->
                        Timber.tag("RoomActivity").e("Error sending MQTT from JS: $errorMsg")
                    }
                )
            } catch (e: Exception) {
                Timber.tag("RoomActivity").e("Error processing JS message: ${e.message}")
            }
        }

        @JavascriptInterface
        fun exitRoom() {
            runOnUiThread {
                stopVideoFeedAndExit()
            }
        }

        @JavascriptInterface
        fun onWebEvent(jsonString: String) {
            try {
                val json = JSONObject(jsonString)
                val status = json.optString("status", "")
                val msg = json.optString("msg", "")

                Timber.tag("RoomActivity").d("Web event: $status - $msg")

                if (status == "error") {
                    runOnUiThread {
                        Toast.makeText(this@RoomActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                } else if (status == "end_call") {
                    runOnUiThread {
                        stopVideoFeedAndExit()
                    }
                }
            } catch (e: Exception) {
                Timber.tag("RoomActivity").e("Error processing web event: ${e.message}")
            }
        }

        @JavascriptInterface
        fun onOrientationChange(orientation: String) {
            // Handle orientation changes if needed
            Timber.tag("RoomActivity").d("Orientation changed to: $orientation")
        }

        @JavascriptInterface
        fun onAudioStatusChange(enabled: Boolean) {
            Timber.tag("RoomActivity").d("Microphone status changed: ${if (enabled) "enabled" else "disabled"}")
            runOnUiThread {
                if (!enabled) {
                    Toast.makeText(this@RoomActivity,
                        "Không thể truy cập micro. Kiểm tra quyền truy cập",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        // Send end call message if not already sent
        try {
            val json = JSONObject().apply {
                put("status", "end")
                put("time", System.currentTimeMillis())
            }
            MqttManager.sendMessage(json)
        } catch (e: Exception) {
            Timber.tag("RoomActivity").e("Error sending end call on destroy: ${e.message}")
        }

        // Clean up WebView
        webView.loadUrl("about:blank")
        webView.destroy()
        messageListener?.let { MqttManager.removeMessageListener(it) }
        super.onDestroy()
    }
}