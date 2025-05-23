package com.example.doorbell_mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.doorbell_mobile.databinding.ActivityMainBinding
import com.example.doorbell_mobile.network.MqttManager
import com.example.doorbell_mobile.services.CallService
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize MQTT connection
        MqttManager.initMqtt(this,
            onConnected = {
                Timber.tag("MainActivity").d("MQTT connected successfully")
            },
            onError = { errorMsg ->
                Timber.tag("MainActivity").e("MQTT connection error: $errorMsg")
                Toast.makeText(this, "Connection error: $errorMsg", Toast.LENGTH_SHORT).show()
            }
        )

        // Start service to maintain MQTT connection in background
        startService(Intent(this, CallService::class.java))


        val navView: BottomNavigationView = binding.navView

        val btn_view_doorbell: Button = binding.btnViewDoorbell

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
//                R.id.navigation_ring,
                R.id.navigation_profile
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        btn_view_doorbell.setOnClickListener {
            // Send watch status via MQTT
            val json = JSONObject().apply {
                put("status", "accept")
                put("time", System.currentTimeMillis())
            }

            MqttManager.sendMessage(json,
                onError = { errorMsg ->
                    Timber.tag("MainActivity").e("Error sending watch status: $errorMsg")
                    Toast.makeText(this, "Connection error: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            )

            joinRoom()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                    .edit { clear() }

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)

                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)            // cập nhật Intent của Activity
        handleSelectedTab()          // và xử lý lại
    }

    private fun handleSelectedTab() {
        val selected = intent.getStringExtra("selected_tab") ?: return
        val navView: BottomNavigationView = binding.navView
        when (selected) {
//            "bell_history" -> navView.selectedItemId = R.id.navigation_ring
            "profile" -> navView.selectedItemId = R.id.navigation_profile
        }
    }

    private fun joinRoom() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                runOnUiThread { joinRoomActivity() }
            } catch (e: Exception) {
                e.printStackTrace()
                Timber.tag("MainActivity").d("Error joining call: ${e.message}")
                Toast.makeText(this@MainActivity, "Error joining call", Toast.LENGTH_SHORT)
                    .show()
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
}