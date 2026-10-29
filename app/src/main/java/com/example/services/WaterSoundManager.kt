package com.example.services

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class WaterSoundManager {
    private var audioTrack: AudioTrack? = null
    private var soundJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private var currentEffect = "air-naik"
    private var isSoundEnabled = true
    private var isPlaying = false

    private val sampleRate = 22050
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    fun start() {
        if (isPlaying) return
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlaying = true
            startAudioLoop()
        } catch (_: Exception) {
            // AudioTrack failed to initialize, fail gracefully
            isPlaying = false
        }
    }

    fun stop() {
        isPlaying = false
        soundJob?.cancel()
        soundJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun updateConfig(enabled: Boolean, effect: String) {
        isSoundEnabled = enabled
        currentEffect = effect
        if (!enabled || effect == "tanpa-efek") {
            audioTrack?.setVolume(0f)
        } else {
            audioTrack?.setVolume(0.45f)
        }
    }

    private var splashFramesRemaining = 0

    fun triggerSplash() {
        splashFramesRemaining = (sampleRate * 0.18f).toInt() // ~180ms splash sound
    }

    private fun startAudioLoop() {
        soundJob?.cancel()
        soundJob = scope.launch {
            val chunk = ShortArray(1024)
            var phase = 0.0
            var lowPass = 0.0

            while (isActive && isPlaying) {
                if (!isSoundEnabled || currentEffect == "tanpa-efek") {
                    chunk.fill(0)
                    audioTrack?.write(chunk, 0, chunk.size)
                    continue
                }

                when (currentEffect) {
                    "air-naik" -> {
                        // Gentle rushing river / bubbling stream
                        for (i in chunk.indices) {
                            val white = (Random.nextFloat() * 2f - 1f)
                            lowPass += 0.08 * (white - lowPass)
                            phase += 2.0 * PI * 18.0 / sampleRate
                            val modulation = 0.6 + 0.4 * sin(phase)
                            var sample = (lowPass * modulation * 14000).toInt()

                            if (splashFramesRemaining > 0) {
                                splashFramesRemaining--
                                val splashTone = sin(phase * 25.0) * 8000
                                sample = (sample * 0.5 + splashTone * 0.5).toInt()
                            }
                            chunk[i] = sample.coerceIn(-32767, 32767).toShort()
                        }
                    }
                    "riak" -> {
                        // Water drop chime / gentle ripple resonance
                        for (i in chunk.indices) {
                            var sample = 0
                            if (splashFramesRemaining > 0) {
                                val progress = 1f - (splashFramesRemaining.toFloat() / (sampleRate * 0.18f))
                                val freq = 520.0 - (progress * 220.0) // 520Hz down to 300Hz
                                phase += 2.0 * PI * freq / sampleRate
                                val envelope = (1f - progress) * (1f - progress)
                                sample = (sin(phase) * envelope * 18000).toInt()
                                splashFramesRemaining--
                            } else {
                                // Very soft ambient water hum
                                phase += 2.0 * PI * 80.0 / sampleRate
                                sample = (sin(phase) * 1200).toInt()
                            }
                            chunk[i] = sample.coerceIn(-32767, 32767).toShort()
                        }
                    }
                    "hujan" -> {
                        // Soothing rain drops on glass
                        for (i in chunk.indices) {
                            val noise = (Random.nextFloat() * 2f - 1f)
                            lowPass += 0.12 * (noise - lowPass)
                            var sample = (lowPass * 9000).toInt()

                            // Random raindrop impact
                            if (Random.nextInt(120) == 0) {
                                sample += (Random.nextFloat() * 8000).toInt()
                            }
                            if (splashFramesRemaining > 0) {
                                splashFramesRemaining--
                                sample += (Random.nextFloat() * 10000 - 5000).toInt()
                            }
                            chunk[i] = sample.coerceIn(-32767, 32767).toShort()
                        }
                    }
                    else -> {
                        chunk.fill(0)
                    }
                }
                audioTrack?.write(chunk, 0, chunk.size)
            }
        }
    }
}
