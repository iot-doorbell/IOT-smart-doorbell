package com.example.doorbell_mobile

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.doorbell_mobile.ui.player.PlayerViewModel

@UnstableApi
class VideoPlayerActivity : AppCompatActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private lateinit var playerView: PlayerView
    private lateinit var btnBack: ImageButton
    private lateinit var seekBarVolume: SeekBar
    private lateinit var audioManager: AudioManager
    private lateinit var videoUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        // 1. Tham chiếu các view
        playerView = findViewById(R.id.playerView)
        btnBack = playerView.findViewById(R.id.exo_back)
        seekBarVolume = playerView.findViewById(R.id.exo_volume)

        // 2. Cấu hình AudioManager & SeekBar âm lượng
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        seekBarVolume.max = maxVol
        seekBarVolume.progress = currVol
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        progress,
                        0
                    )
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })

        // 3. Nút Back để finish Activity
        btnBack.setOnClickListener { finish() }

        // 4. Lấy URL video và lưu lại
        intent.getStringExtra("video_url")?.let { url ->
            videoUri = url.toUri()
        } ?: run {
            finish()
            return
        }
    }

    override fun onStart() {
        super.onStart()
        // 5. Gán player và bắt đầu playback
        playerView.player = viewModel.player
        viewModel.initializePlayer(videoUri)
        viewModel.player.playWhenReady = true
    }

    override fun onStop() {
        // 6. Pause và ngắt liên kết player khỏi view
        viewModel.player.playWhenReady = false
        playerView.player = null
        super.onStop()
    }
}