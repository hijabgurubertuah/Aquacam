package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class RecordItem(
    @PrimaryKey
    val id: String,
    val type: String, // "foto" or "video"
    val effect: String, // "air-naik", "riak", "hujan", "tanpa-efek"
    val waterColor: String, // "jernih", "biru-laut", "hijau-kolam"
    val waterHeight: Int, // 0-100
    val waveStrength: Int, // 0-100
    val createdAt: Long,
    val durationSec: Int = 0,
    val sizeKB: Long = 0,
    val filePath: String,
    val isSample: Boolean = false
)
