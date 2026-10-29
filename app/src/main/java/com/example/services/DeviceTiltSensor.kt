package com.example.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2

class DeviceTiltSensor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _tiltAngle = MutableStateFlow(0f) // in radians, smoothed
    val tiltAngle: StateFlow<Float> = _tiltAngle.asStateFlow()

    private var currentFilteredAngle = 0f
    private val alpha = 0.15f // smoothing factor

    fun start() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val ax = event.values[0]
        val ay = event.values[1]

        // Roll angle relative to gravity: 0 when vertical portrait
        val rawAngle = atan2(-ax.toDouble(), ay.toDouble()).toFloat()
        // Clamp extreme flips to keep water surface natural (-45 deg to +45 deg)
        val clampedAngle = rawAngle.coerceIn(-0.85f, 0.85f)

        // Low-pass exponential smoothing filter
        currentFilteredAngle += alpha * (clampedAngle - currentFilteredAngle)
        _tiltAngle.value = currentFilteredAngle
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
