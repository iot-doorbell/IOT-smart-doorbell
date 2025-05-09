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
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.doorbell_mobile.constants.ConstVal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.IOException

class MonitorActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var errorText: TextView
    private lateinit var endBtn: Button
    private val client = OkHttpClient()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)

        webView = findViewById(R.id.doorbell_webview)
        errorText = findViewById(R.id.error_message)
        endBtn = findViewById(R.id.btn_end_call)

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true

        val intent = intent
        if (intent != null && intent.getBooleanExtra("accept_call", false)) {
            onClose()
        }

        webView.webViewClient = object : WebViewClient() {
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
                webView.visibility = View.VISIBLE
                errorText.visibility = View.GONE
            }
        }

        webView.loadUrl("${ConstVal.DOORBELL_URL}/${ConstVal.MJEG_ENDPOINT}?portrait=1")


        endBtn.setOnClickListener {
            stopVideoFeedAndExit()
        }
    }

    private fun showError(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            webView.visibility = View.GONE
            errorText.visibility = View.VISIBLE
            errorText.text = message
        }
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
                    goBackToMain()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                runOnUiThread {
                    goBackToMain()
                }
            }
        })
    }

    private fun goBackToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun onClose() {
        webView.stopLoading();
        finish()
    }
}

