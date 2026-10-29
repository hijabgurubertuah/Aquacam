package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aquacam_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            lastEffect = prefs.getString("lastEffect", "air-naik") ?: "air-naik",
            waterHeight = prefs.getInt("waterHeight", 50),
            waveStrength = prefs.getInt("waveStrength", 40),
            waterColor = prefs.getString("waterColor", "biru-laut") ?: "biru-laut",
            waterSound = prefs.getBoolean("waterSound", true),
            vibration = prefs.getBoolean("vibration", true),
            videoQuality = prefs.getString("videoQuality", "tinggi") ?: "tinggi",
            lastCamera = prefs.getString("lastCamera", "belakang") ?: "belakang"
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit()
            .putString("lastEffect", newSettings.lastEffect)
            .putInt("waterHeight", newSettings.waterHeight)
            .putInt("waveStrength", newSettings.waveStrength)
            .putString("waterColor", newSettings.waterColor)
            .putBoolean("waterSound", newSettings.waterSound)
            .putBoolean("vibration", newSettings.vibration)
            .putString("videoQuality", newSettings.videoQuality)
            .putString("lastCamera", newSettings.lastCamera)
            .apply()
        _settings.value = newSettings
    }

    fun updateEffect(effect: String) {
        updateSettings(_settings.value.copy(lastEffect = effect))
    }

    fun updateWaterHeight(height: Int) {
        updateSettings(_settings.value.copy(waterHeight = height))
    }

    fun updateWaveStrength(strength: Int) {
        updateSettings(_settings.value.copy(waveStrength = strength))
    }

    fun updateWaterColor(color: String) {
        updateSettings(_settings.value.copy(waterColor = color))
    }

    fun updateWaterSound(enabled: Boolean) {
        updateSettings(_settings.value.copy(waterSound = enabled))
    }

    fun updateVibration(enabled: Boolean) {
        updateSettings(_settings.value.copy(vibration = enabled))
    }

    fun updateVideoQuality(quality: String) {
        updateSettings(_settings.value.copy(videoQuality = quality))
    }

    fun updateLastCamera(camera: String) {
        updateSettings(_settings.value.copy(lastCamera = camera))
    }

    fun exportBackupJson(records: List<RecordItem>): String {
        val root = JSONObject()
        val s = _settings.value
        val settingsObj = JSONObject().apply {
            put("lastEffect", s.lastEffect)
            put("waterHeight", s.waterHeight)
            put("waveStrength", s.waveStrength)
            put("waterColor", s.waterColor)
            put("waterSound", s.waterSound)
            put("vibration", s.vibration)
            put("videoQuality", s.videoQuality)
            put("lastCamera", s.lastCamera)
        }
        root.put("settings", settingsObj)

        val array = JSONArray()
        for (r in records) {
            val item = JSONObject().apply {
                put("id", r.id)
                put("type", r.type)
                put("effect", r.effect)
                put("waterColor", r.waterColor)
                put("waterHeight", r.waterHeight)
                put("waveStrength", r.waveStrength)
                put("createdAt", r.createdAt)
                put("durationSec", r.durationSec)
                put("sizeKB", r.sizeKB)
                put("filePath", r.filePath)
                put("isSample", r.isSample)
            }
            array.put(item)
        }
        root.put("records", array)
        return root.toString(2)
    }

    fun parseBackupJson(jsonString: String): Pair<AppSettings, List<RecordItem>> {
        val root = JSONObject(jsonString)
        val sObj = root.optJSONObject("settings") ?: JSONObject()
        val restoredSettings = AppSettings(
            lastEffect = sObj.optString("lastEffect", "air-naik"),
            waterHeight = sObj.optInt("waterHeight", 50),
            waveStrength = sObj.optInt("waveStrength", 40),
            waterColor = sObj.optString("waterColor", "biru-laut"),
            waterSound = sObj.optBoolean("waterSound", true),
            vibration = sObj.optBoolean("vibration", true),
            videoQuality = sObj.optString("videoQuality", "tinggi"),
            lastCamera = sObj.optString("lastCamera", "belakang")
        )

        val recordsList = mutableListOf<RecordItem>()
        val array = root.optJSONArray("records")
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                recordsList.add(
                    RecordItem(
                        id = item.optString("id", java.util.UUID.randomUUID().toString()),
                        type = item.optString("type", "foto"),
                        effect = item.optString("effect", "air-naik"),
                        waterColor = item.optString("waterColor", "biru-laut"),
                        waterHeight = item.optInt("waterHeight", 50),
                        waveStrength = item.optInt("waveStrength", 40),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        durationSec = item.optInt("durationSec", 0),
                        sizeKB = item.optLong("sizeKB", 0),
                        filePath = item.optString("filePath", ""),
                        isSample = item.optBoolean("isSample", false)
                    )
                )
            }
        }
        return Pair(restoredSettings, recordsList)
    }
}
