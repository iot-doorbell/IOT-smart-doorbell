//package com.example.doorbell_mobile.ui.player
//
//import android.app.Application
//import android.net.Uri
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import androidx.media3.common.AudioAttributes
//import androidx.media3.common.C
//import androidx.media3.common.MediaItem
//import androidx.media3.exoplayer.ExoPlayer
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//
//class PlayerViewModel(app: Application) : AndroidViewModel(app) {
//
//    // ExoPlayer instance được khởi tạo một lần, sống qua config changes
//    private val _player: ExoPlayer = ExoPlayer.Builder(app).build().apply {
//        // Cấu hình AudioAttributes để tự động quản lý audio focus
//        val audioAttrs = AudioAttributes.Builder()
//            .setUsage(C.USAGE_MEDIA)
//            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
//            .build()
//        setAudioAttributes(audioAttrs, true)
//        setHandleAudioBecomingNoisy(true)
//    }
//    val player: ExoPlayer get() = _player
//
//    private var currentUri: Uri? = null
//
//    /**
//     * Chỉ set mediaItem và prepare một lần cho mỗi URI khác nhau.
//     */
//    fun initializePlayer(uri: Uri) {
//        if (currentUri != uri) {
//            currentUri = uri
//            viewModelScope.launch(Dispatchers.Main) {
//                _player.setMediaItem(MediaItem.fromUri(uri))
//                _player.prepare()
//            }
//        }
//    }
//
//    override fun onCleared() {
//        super.onCleared()
//        _player.release()
//    }
//}

package com.example.doorbell_mobile.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val renderersFactory = DefaultRenderersFactory(app).setEnableDecoderFallback(true)
    private val mediaSourceFactory = DefaultMediaSourceFactory(app)
    val player: ExoPlayer = ExoPlayer.Builder(app, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    private var isInitialized = false

    fun initializePlayer(videoUri: Uri) {
        if (!isInitialized) {
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            player.setAudioAttributes(audioAttrs, true)
            player.setHandleAudioBecomingNoisy(true)

            viewModelScope.launch(Dispatchers.Main) {
                val mediaItem = MediaItem.fromUri(videoUri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                isInitialized = true
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
