package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object SeedData {
    suspend fun populateIfEmpty(context: Context, dao: RecordDao) {
        if (dao.getCount() > 0) return

        val sample1 = createSampleFile(
            context = context,
            name = "sample_air_naik.jpg",
            effect = "air-naik",
            waterColor = "biru-laut",
            height = 55
        )

        val sample2 = createSampleFile(
            context = context,
            name = "sample_riak.jpg",
            effect = "riak",
            waterColor = "jernih",
            height = 0
        )

        val sample3 = createSampleFile(
            context = context,
            name = "sample_hujan.jpg",
            effect = "hujan",
            waterColor = "hijau-kolam",
            height = 0
        )

        val now = System.currentTimeMillis()
        val items = listOf(
            RecordItem(
                id = UUID.randomUUID().toString(),
                type = "foto",
                effect = "air-naik",
                waterColor = "biru-laut",
                waterHeight = 55,
                waveStrength = 40,
                createdAt = now - 1000 * 60 * 30, // 30 mins ago
                sizeKB = (sample1.length() / 1024).coerceAtLeast(120),
                filePath = sample1.absolutePath,
                isSample = true
            ),
            RecordItem(
                id = UUID.randomUUID().toString(),
                type = "foto",
                effect = "riak",
                waterColor = "jernih",
                waterHeight = 50,
                waveStrength = 50,
                createdAt = now - 1000 * 60 * 90, // 1.5 hours ago
                sizeKB = (sample2.length() / 1024).coerceAtLeast(110),
                filePath = sample2.absolutePath,
                isSample = true
            ),
            RecordItem(
                id = UUID.randomUUID().toString(),
                type = "foto",
                effect = "hujan",
                waterColor = "hijau-kolam",
                waterHeight = 50,
                waveStrength = 45,
                createdAt = now - 1000 * 60 * 180, // 3 hours ago
                sizeKB = (sample3.length() / 1024).coerceAtLeast(130),
                filePath = sample3.absolutePath,
                isSample = true
            )
        )
        dao.insertRecords(items)
    }

    private fun createSampleFile(
        context: Context,
        name: String,
        effect: String,
        waterColor: String,
        height: Int
    ): File {
        val dir = File(context.filesDir, "samples")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        if (file.exists()) return file

        val width = 720
        val heightPx = 1280
        val bitmap = Bitmap.createBitmap(width, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background indoor camera-like setting
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, heightPx.toFloat(),
                Color.rgb(35, 45, 60),
                Color.rgb(15, 20, 30),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), heightPx.toFloat(), bgPaint)

        // Draw soft background shapes to look like a room scene
        val roomPaint = Paint().apply {
            color = Color.rgb(55, 65, 80)
            style = Paint.Style.FILL
        }
        canvas.drawRect(80f, 200f, 640f, 600f, roomPaint)

        val windowPaint = Paint().apply {
            color = Color.rgb(100, 130, 160)
            style = Paint.Style.FILL
        }
        canvas.drawRect(120f, 240f, 320f, 500f, windowPaint)
        canvas.drawRect(400f, 240f, 600f, 500f, windowPaint)

        when (effect) {
            "air-naik" -> {
                val waterY = heightPx * (1f - (height / 100f))
                val waterShader = LinearGradient(
                    0f, waterY, 0f, heightPx.toFloat(),
                    Color.argb(120, 2, 132, 199),
                    Color.argb(220, 3, 50, 120),
                    Shader.TileMode.CLAMP
                )
                val waterPaint = Paint().apply {
                    shader = waterShader
                    style = Paint.Style.FILL
                }

                val wavePath = Path()
                wavePath.moveTo(0f, waterY)
                val waveCount = 4
                val waveLen = width.toFloat() / waveCount
                for (i in 0 until waveCount) {
                    val x1 = i * waveLen + waveLen * 0.25f
                    val y1 = waterY - 24f
                    val x2 = i * waveLen + waveLen * 0.75f
                    val y2 = waterY + 24f
                    val x3 = (i + 1) * waveLen
                    val y3 = waterY
                    wavePath.cubicTo(x1, y1, x2, y2, x3, y3)
                }
                wavePath.lineTo(width.toFloat(), heightPx.toFloat())
                wavePath.lineTo(0f, heightPx.toFloat())
                wavePath.close()
                canvas.drawPath(wavePath, waterPaint)

                // Crest specular line
                val crestPaint = Paint().apply {
                    color = Color.argb(200, 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
                canvas.drawPath(wavePath, crestPaint)
            }
            "riak" -> {
                val cx = width / 2f
                val cy = heightPx / 2f
                val ripplePaint = Paint().apply {
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                for (r in listOf(60f, 130f, 210f, 300f, 400f)) {
                    ripplePaint.strokeWidth = 14f
                    ripplePaint.color = Color.argb(110, 255, 255, 255)
                    canvas.drawCircle(cx, cy, r, ripplePaint)
                    ripplePaint.strokeWidth = 6f
                    ripplePaint.color = Color.argb(170, 34, 211, 238)
                    canvas.drawCircle(cx, cy, r + 4, ripplePaint)
                }
            }
            "hujan" -> {
                // Pond green tint
                canvas.drawColor(Color.argb(60, 13, 148, 136))
                val dropPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }
                val highlightPaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.argb(220, 255, 255, 255)
                    style = Paint.Style.FILL
                }

                val drops = listOf(
                    Triple(150f, 300f, 22f),
                    Triple(360f, 450f, 35f),
                    Triple(520f, 250f, 18f),
                    Triple(220f, 750f, 28f),
                    Triple(480f, 800f, 32f),
                    Triple(300f, 950f, 20f),
                    Triple(600f, 600f, 25f),
                    Triple(180f, 520f, 16f)
                )

                for ((dx, dy, radius) in drops) {
                    dropPaint.shader = RadialGradient(
                        dx - radius * 0.3f, dy - radius * 0.3f, radius,
                        Color.argb(180, 255, 255, 255),
                        Color.argb(70, 13, 148, 136),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(dx, dy, radius, dropPaint)
                    // Highlight reflection
                    canvas.drawCircle(dx - radius * 0.35f, dy - radius * 0.35f, radius * 0.28f, highlightPaint)
                }
            }
        }

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file
    }
}
