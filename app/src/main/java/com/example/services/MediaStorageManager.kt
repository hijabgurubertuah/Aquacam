package com.example.services

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.RecordItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class MediaStorageManager(private val context: Context) {

    private val mediaDir: File
        get() {
            val dir = File(context.filesDir, "aquacam_media")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun hasEnoughStorageSpace(): Boolean {
        return try {
            val stat = StatFs(context.filesDir.path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            bytesAvailable > 15L * 1024 * 1024 // at least 15MB
        } catch (_: Exception) {
            true
        }
    }

    suspend fun savePhoto(
        bitmap: Bitmap,
        effect: String,
        waterColor: String,
        height: Int,
        wave: Int
    ): Pair<RecordItem, Boolean> {
        val fileName = "IMG_AquaCam_${System.currentTimeMillis()}.jpg"
        val localFile = File(mediaDir, fileName)

        FileOutputStream(localFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        var savedToGallery = false
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AquaCam")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outStream ->
                    FileInputStream(localFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                savedToGallery = true
            }
        } catch (_: Exception) {
            savedToGallery = false
        }

        val record = RecordItem(
            id = UUID.randomUUID().toString(),
            type = "foto",
            effect = effect,
            waterColor = waterColor,
            waterHeight = height,
            waveStrength = wave,
            createdAt = System.currentTimeMillis(),
            durationSec = 0,
            sizeKB = (localFile.length() / 1024).coerceAtLeast(1),
            filePath = localFile.absolutePath,
            isSample = false
        )

        return Pair(record, savedToGallery)
    }

    fun createVideoOutputFile(): File {
        val fileName = "VID_AquaCam_${System.currentTimeMillis()}.mp4"
        return File(mediaDir, fileName)
    }

    suspend fun registerVideo(
        videoFile: File,
        durationSec: Int,
        effect: String,
        waterColor: String,
        height: Int,
        wave: Int
    ): Pair<RecordItem, Boolean> {
        var savedToGallery = false
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AquaCam")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outStream ->
                    FileInputStream(videoFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                savedToGallery = true
            }
        } catch (_: Exception) {
            savedToGallery = false
        }

        val record = RecordItem(
            id = UUID.randomUUID().toString(),
            type = "video",
            effect = effect,
            waterColor = waterColor,
            waterHeight = height,
            waveStrength = wave,
            createdAt = System.currentTimeMillis(),
            durationSec = durationSec,
            sizeKB = (videoFile.length() / 1024).coerceAtLeast(1),
            filePath = videoFile.absolutePath,
            isSample = false
        )

        return Pair(record, savedToGallery)
    }

    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else true
        } catch (_: Exception) {
            false
        }
    }

    fun shareMedia(record: RecordItem) = shareRecord(record)

    fun shareRecord(record: RecordItem) {
        val file = File(record.filePath)
        if (!file.exists()) return

        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }

        val mimeType = if (record.type == "video") "video/mp4" else "image/jpeg"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Bagikan lewat").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
