package com.example.data

data class AppSettings(
    val lastEffect: String = "air-naik",
    val waterHeight: Int = 50,
    val waveStrength: Int = 40,
    val waterColor: String = "biru-laut",
    val waterSound: Boolean = true,
    val vibration: Boolean = true,
    val videoQuality: String = "tinggi", // "standar" (720p) or "tinggi" (1080p)
    val lastCamera: String = "belakang" // "belakang" or "depan"
)
