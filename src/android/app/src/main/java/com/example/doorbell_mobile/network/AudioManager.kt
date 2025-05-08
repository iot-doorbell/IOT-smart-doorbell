package com.example.doorbell_mobile.network

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission

object AudioManager {

    private val sampleRate = 48000
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channelOutConfig = AudioFormat.CHANNEL_OUT_MONO
    private val channelInConfig = AudioFormat.CHANNEL_IN_MONO

    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null

    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private val bufferSizeOut =
        AudioTrack.getMinBufferSize(sampleRate, channelOutConfig, audioFormat)
    private val bufferSizeIn =
        AudioRecord.getMinBufferSize(sampleRate, channelInConfig, audioFormat)

    // Init Audio Output
    fun initSpeaker() {
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelOutConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeOut)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    // Init Microphone with echo/noise cancellation
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initMicrophone() {
        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION) // auto enables AEC & NS
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelInConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeIn)
            .build()

        // Apply echo canceller
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
            echoCanceler?.enabled = true
        }

        // Apply noise suppressor
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioRecord!!.audioSessionId)
            noiseSuppressor?.enabled = true
        }
    }

    // Start speaker playback
    fun startSpeaker() {
        audioTrack?.play()
    }

    // Play PCM data
    fun playAudio(data: ByteArray) {
        audioTrack?.write(data, 0, data.size)
    }

    // Start recording mic
    fun startMic() {
        audioRecord?.startRecording()
    }

    // Read mic data
    fun readMic(): ByteArray {
        val buffer = ByteArray(bufferSizeIn)
        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
        return if (read > 0) buffer.copyOf(read) else ByteArray(0)
    }

    // Stop and release everything
    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        echoCanceler?.release()
        noiseSuppressor?.release()
    }
}
