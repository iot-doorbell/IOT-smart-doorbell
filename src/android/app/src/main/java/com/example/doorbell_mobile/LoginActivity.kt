package com.example.doorbell_mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.doorbell_mobile.constants.ConstVal
import com.example.doorbell_mobile.network.WebSocketSignalManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sharedPreferences.contains("username")) {
            navigateToMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        if (WebSocketSignalManager.isConnectedSocketSignal()) {
            WebSocketSignalManager.disconnectSignal()
        }

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvError = findViewById<TextView>(R.id.tvError)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                tvError.setText(R.string.error_empty_credentials)
                tvError.visibility = View.VISIBLE
            } else {
                tvError.visibility = View.GONE
                loginToServer(username, password, tvError)
            }
        }
    }

    private fun loginToServer(username: String, password: String, tvError: TextView) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }

        val requestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("${ConstVal.DOORBELL_URL}/${ConstVal.LOGIN_ENDPOINT}")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvError.setText(R.string.error_connection_failed)
                    tvError.visibility = View.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        handleSuccessfulLogin(response.body?.string())
                    } else {
                        tvError.text = when (response.code) {
                            401 -> getString(R.string.error_invalid_credentials)
                            else -> getString(R.string.error_unexpected, response.message)
                        }
                        tvError.visibility = View.VISIBLE
                        Timber.tag("LoginActivity")
                            .d("Login failed: ${response.code} - ${response.message}")
                    }
                }
            }
        })
    }

    private fun handleSuccessfulLogin(responseBody: String?) {
        val json = JSONObject(responseBody ?: "")
        sharedPreferences.edit {
            putString("id", json.getString("userId"))
            putString("username", json.getString("userName"))
            putString("email", json.getString("userEmail"))
            putString("avatar", json.optString("userAvatar", ""))
        }
        Toast.makeText(this, R.string.login_successful, Toast.LENGTH_SHORT).show()
        navigateToMainActivity()
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

// username: dev405051
// password: 123456789