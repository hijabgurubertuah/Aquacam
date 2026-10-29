package com.example.services

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class WaterVideoRecorder(
    private val context: Context,
    private val effectRenderer: WaterEffectRenderer
) {
    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    var isRecording = false
        private set
    var recordingDurationSec = 0
        private set
    private var currentOutputFile: File? = null
    private var startTimeMs = 0L

    var onTimerTick: ((Int) -> Unit)? = null
    var onMaxDurationReached: (() -> Unit)? = null

    fun startRecording(
        outputFile: File,
        isHighQuality: Boolean,
        hasAudioPermission: Boolean,
        getFrameBitmap: () -> Bitmap?,
        effect: String,
        waterColor: String,
        waterHeight: Int,
        waveStrength: Int,
        tiltAngleProvider: () -> Float
    ): Boolean {
        if (isRecording) return false
        currentOutputFile = outputFile
        recordingDurationSec = 0
        startTimeMs = System.currentTimeMillis()

        val width = if (isHighQuality) 1080 else 720
        val height = if (isHighQuality) 1920 else 1280
        val bitRate = if (isHighQuality) 7_000_000 else 3_500_000

        try {
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            val canRecordAudio = hasAudioPermission && ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (canRecordAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            if (canRecordAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
            }

            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(bitRate)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.setOrientationHint(90) // Portrait orientation

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isRecording = true

            val inputSurface = recorder.surface

            // Render loop onto surface
            recordingJob = scope.launch {
                val frameDelay = 33L // ~30 fps
                val destRect = Rect(0, 0, width, height)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)

                while (isActive && isRecording) {
                    val frameStart = System.currentTimeMillis()
                    val elapsedSec = ((frameStart - startTimeMs) / 1000).toInt()

                    if (elapsedSec != recordingDurationSec) {
                        recordingDurationSec = elapsedSec
                        Handler(Looper.getMainLooper()).post {
                            onTimerTick?.invoke(recordingDurationSec)
                        }
                    }

                    if (elapsedSec >= 60) {
                        Handler(Looper.getMainLooper()).post {
                            onMaxDurationReached?.invoke()
                        }
                        break
                    }

                    try {
                        val frameBmp = getFrameBitmap()
                        if (frameBmp != null && !frameBmp.isRecycled) {
                            val canvas: Canvas? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                inputSurface.lockHardwareCanvas()
                            } else {
                                inputSurface.lockCanvas(null)
                            }

                            if (canvas != null) {
                                canvas.drawBitmap(frameBmp, null, destRect, paint)

                                // Draw water overlay matching preview
                                val stamped = effectRenderer.stampEffectOnBitmap(
                                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
                                    effect = effect,
                                    waterColor = waterColor,
                                    waterHeight = waterHeight,
                                    waveStrength = waveStrength,
                                    tiltAngle = tiltAngleProvider()
                                )
                                canvas.drawBitmap(stamped, null, destRect, paint)
                                stamped.recycle()

                                inputSurface.unlockCanvasAndPost(canvas)
                            }
                        }
                    } catch (_: Exception) {
                        // ignore dropped frames
                    }

                    val took = System.currentTimeMillis() - frameStart
                    val sleepTime = (frameDelay - took).coerceAtLeast(5L)
                    delay(sleepTime)
                }
            }

            return true
        } catch (e: Exception) {
            isRecording = false
            try {
                mediaRecorder?.reset()
                mediaRecorder?.release()
            } catch (_: Exception) {}
            mediaRecorder = null
            return false
        }
    }

    fun stopRecording(): Pair<File?, Int> {
        if (!isRecording) return Pair(null, 0)
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        val finalDuration = recordingDurationSec
        val file = currentOutputFile

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        return Pair(file, finalDuration)
    }
}
