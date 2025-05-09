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
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.doorbell_mobile.databinding.ActivityMainBinding
import com.example.doorbell_mobile.network.WebSocketAudioManager
import com.example.doorbell_mobile.network.WebSocketSignalManager
import com.example.doorbell_mobile.services.CallService
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Chạy websocket sau khi đăng nhập thành công
        WebSocketSignalManager.initSocketSignal(this)

        if (WebSocketAudioManager.isConnectedSocketAudio())
            WebSocketAudioManager.closeSocketAudio()
        // Khởi động service để duy trì WebSocket khi app vào background
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
            val intent = Intent(this, MonitorActivity::class.java)
            startActivity(intent)
            finish()
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
}