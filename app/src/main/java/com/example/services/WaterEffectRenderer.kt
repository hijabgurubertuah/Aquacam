package com.example.services

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

data class WaterRipple(
    val x: Float,
    val y: Float,
    var radius: Float = 4f,
    val maxRadius: Float = 320f,
    var alpha: Float = 1.0f,
    val speed: Float = 7f
)

data class Raindrop(
    var x: Float,
    var y: Float,
    var radius: Float,
    var speedY: Float,
    var trailAlpha: Float = 0.4f,
    var isSplash: Boolean = false,
    var life: Float = 1.0f
)

class WaterEffectRenderer {
    val ripples = mutableListOf<WaterRipple>()
    val raindrops = mutableListOf<Raindrop>()

    var wavePhase = 0f
    var animatedHeightFraction = 0.5f // 0 to 1
    private var lastTimeMs = System.currentTimeMillis()

    fun addRipple(x: Float, y: Float, waveStrength: Int) {
        val speed = 5f + (waveStrength / 100f) * 6f
        val maxR = 200f + (waveStrength / 100f) * 220f
        ripples.add(WaterRipple(x = x, y = y, radius = 6f, maxRadius = maxR, alpha = 1.0f, speed = speed))
        if (ripples.size > 25) ripples.removeAt(0)
    }

    fun addRainSplash(x: Float, y: Float, waveStrength: Int) {
        val count = 4 + (waveStrength / 25)
        for (i in 0 until count) {
            val offsetAngle = Random.nextFloat() * 2f * PI.toFloat()
            val dist = Random.nextFloat() * 30f
            val sx = x + cos(offsetAngle) * dist
            val sy = y + sin(offsetAngle) * dist
            val rad = 4f + Random.nextFloat() * 8f
            raindrops.add(Raindrop(x = sx, y = sy, radius = rad, speedY = 0.5f, isSplash = true, life = 1.0f))
        }
    }

    fun update(
        dtSec: Float,
        targetHeightFraction: Float,
        effectActive: Boolean,
        effect: String,
        waveStrength: Int
    ) {
        // Smooth height transition (~2s up, ~1s down)
        val target = if (effectActive && effect == "air-naik") targetHeightFraction else 0f
        val speedRate = if (target > animatedHeightFraction) dtSec / 2.0f else dtSec / 1.0f
        if (animatedHeightFraction < target) {
            animatedHeightFraction = (animatedHeightFraction + speedRate).coerceAtMost(target)
        } else if (animatedHeightFraction > target) {
            animatedHeightFraction = (animatedHeightFraction - speedRate).coerceAtLeast(target)
        }

        // Wave phase
        val waveSpeed = 1.8f + (waveStrength / 100f) * 2.5f
        wavePhase += waveSpeed * dtSec

        // Update ripples
        val rippleIt = ripples.iterator()
        while (rippleIt.hasNext()) {
            val r = rippleIt.next()
            r.radius += r.speed * (dtSec * 60f)
            r.alpha = (1f - (r.radius / r.maxRadius)).coerceIn(0f, 1f)
            if (r.radius >= r.maxRadius || r.alpha <= 0.02f) {
                rippleIt.remove()
            }
        }

        // Ambient ripples for "riak"
        if (effect == "riak" && effectActive) {
            if (Random.nextFloat() < 0.05f + (waveStrength / 100f) * 0.06f && ripples.size < 8) {
                val rx = Random.nextFloat() * 1000f
                val ry = Random.nextFloat() * 1800f
                addRipple(rx, ry, waveStrength)
            }
        }

        // Update raindrops for "hujan"
        if (effect == "hujan" && effectActive) {
            val targetDropCount = 18 + (waveStrength / 100f * 35).toInt()
            while (raindrops.count { !it.isSplash } < targetDropCount) {
                val rx = Random.nextFloat() * 1080f
                val ry = Random.nextFloat() * 1920f
                val rad = 8f + Random.nextFloat() * 18f
                val sy = 1.2f + Random.nextFloat() * 3.5f
                raindrops.add(Raindrop(x = rx, y = ry, radius = rad, speedY = sy))
            }
        }

        val dropIt = raindrops.iterator()
        while (dropIt.hasNext()) {
            val d = dropIt.next()
            if (d.isSplash) {
                d.life -= dtSec * 2.2f
                if (d.life <= 0f) {
                    dropIt.remove()
                }
            } else {
                d.y += d.speedY * (dtSec * 60f)
                // Randomly wobble or glide
                if (Random.nextInt(12) == 0) {
                    d.x += (Random.nextFloat() * 2f - 1f)
                }
                if (d.y > 2200f) {
                    dropIt.remove()
                }
            }
        }

        // Merge nearby raindrops
        if (raindrops.size > 1) {
            for (i in 0 until raindrops.size - 1) {
                val a = raindrops[i]
                if (a.isSplash) continue
                for (j in i + 1 until raindrops.size) {
                    val b = raindrops[j]
                    if (b.isSplash) continue
                    val dx = a.x - b.x
                    val dy = a.y - b.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist < a.radius + b.radius) {
                        a.radius = kotlin.math.sqrt(a.radius * a.radius + b.radius * b.radius).coerceAtMost(36f)
                        a.speedY = (a.speedY + 0.8f).coerceAtMost(8f)
                        b.y = 3000f // mark for removal
                    }
                }
            }
        }
    }

    fun drawComposeWater(
        scope: DrawScope,
        effect: String,
        waterColor: String,
        tiltAngle: Float,
        waveStrength: Int
    ) {
        val width = scope.size.width
        val height = scope.size.height

        when (effect) {
            "air-naik" -> {
                if (animatedHeightFraction <= 0.005f) return
                drawRisingWater(scope, width, height, tiltAngle, waterColor, waveStrength)
                drawRipples(scope)
            }
            "riak" -> {
                drawRipples(scope)
            }
            "hujan" -> {
                drawRaindrops(scope, waterColor)
            }
        }
    }

    private fun drawRisingWater(
        scope: DrawScope,
        width: Float,
        height: Float,
        tiltAngle: Float,
        waterColor: String,
        waveStrength: Int
    ) {
        val waterY = height * (1f - animatedHeightFraction)
        val waveAmp = 12f + (waveStrength / 100f) * 22f
        val tanTilt = tan(tiltAngle.toDouble()).toFloat()

        val path = Path()
        val numPoints = 28
        val step = width / numPoints

        // Calculate surface points
        val surfacePoints = mutableListOf<Offset>()
        val centerX = width / 2f
        for (i in 0..numPoints) {
            val px = i * step
            // Tilt relative to center
            val tiltOffset = (px - centerX) * tanTilt
            val wave1 = sin(wavePhase + (px * 0.015f)) * waveAmp
            val wave2 = cos(wavePhase * 1.3f + (px * 0.03f)) * (waveAmp * 0.4f)
            val py = (waterY + tiltOffset + wave1 + wave2).coerceIn(0f, height + 100f)
            surfacePoints.add(Offset(px, py))
        }

        path.moveTo(surfacePoints[0].x, surfacePoints[0].y)
        for (i in 1 until surfacePoints.size) {
            val prev = surfacePoints[i - 1]
            val curr = surfacePoints[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadraticTo(prev.x, prev.y, midX, midY)
        }
        val last = surfacePoints.last()
        path.lineTo(last.x, last.y)
        path.lineTo(width, height)
        path.lineTo(0f, height)
        path.close()

        val (topColor, bottomColor) = when (waterColor) {
            "jernih" -> Pair(Color(0x55A5F3FC), Color(0x9938BDF8))
            "hijau-kolam" -> Pair(Color(0x665EEAD4), Color(0xCC065F46))
            else -> Pair(Color(0x7738BDF8), Color(0xD90C4A6E)) // biru-laut
        }

        // Draw body of water
        scope.drawPath(
            path = path,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(topColor, bottomColor),
                startY = waterY.coerceAtLeast(0f),
                endY = height
            )
        )

        // Draw surface light crest line
        val crestPath = Path()
        crestPath.moveTo(surfacePoints[0].x, surfacePoints[0].y)
        for (i in 1 until surfacePoints.size) {
            val prev = surfacePoints[i - 1]
            val curr = surfacePoints[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            crestPath.quadraticTo(prev.x, prev.y, midX, midY)
        }
        crestPath.lineTo(last.x, last.y)

        // Bright caustics crest
        scope.drawPath(
            path = crestPath,
            color = Color(0xCCFFFFFF),
            style = Stroke(width = 5f)
        )
        // Soft blue foam highlight
        scope.drawPath(
            path = crestPath,
            color = Color(0x88E0F2FE),
            style = Stroke(width = 11f)
        )
    }

    private fun drawRipples(scope: DrawScope) {
        for (r in ripples) {
            if (r.alpha <= 0.01f) continue
            // Outer ripple ring
            scope.drawCircle(
                color = Color(0xFFE0F2FE).copy(alpha = r.alpha * 0.7f),
                radius = r.radius,
                center = Offset(r.x, r.y),
                style = Stroke(width = 4.5f)
            )
            // Mid refractive ring
            if (r.radius > 15f) {
                scope.drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = r.alpha * 0.5f),
                    radius = r.radius * 0.85f,
                    center = Offset(r.x, r.y),
                    style = Stroke(width = 3f)
                )
            }
            // Inner splash point
            if (r.radius < 50f) {
                scope.drawCircle(
                    color = Color.White.copy(alpha = r.alpha * 0.8f),
                    radius = (50f - r.radius) * 0.2f,
                    center = Offset(r.x, r.y)
                )
            }
        }
    }

    private fun drawRaindrops(scope: DrawScope, waterColor: String) {
        val tintColor = when (waterColor) {
            "jernih" -> Color(0x2238BDF8)
            "hijau-kolam" -> Color(0x330D9488)
            else -> Color(0x330284C7)
        }
        // Very subtle lens condensation tint
        scope.drawRect(color = tintColor)

        for (d in raindrops) {
            if (d.isSplash) {
                val alpha = (d.life * 0.8f).coerceIn(0f, 1f)
                scope.drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = d.radius * (1f + (1f - d.life) * 0.5f),
                    center = Offset(d.x, d.y)
                )
            } else {
                // Droplet trail
                if (d.speedY > 2f) {
                    val trailY = d.y - (d.radius * 2.2f)
                    scope.drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(d.x, trailY),
                        end = Offset(d.x, d.y),
                        strokeWidth = d.radius * 0.5f
                    )
                }

                // Drop body (refractive glass-like circle)
                scope.drawCircle(
                    color = Color(0x44FFFFFF),
                    radius = d.radius,
                    center = Offset(d.x, d.y)
                )
                scope.drawCircle(
                    color = Color(0x8838BDF8),
                    radius = d.radius,
                    center = Offset(d.x, d.y),
                    style = Stroke(width = 2.5f)
                )

                // Specular reflection highlight on top-left of droplet
                val hx = d.x - d.radius * 0.35f
                val hy = d.y - d.radius * 0.35f
                scope.drawCircle(
                    color = Color(0xEEFFFFFF),
                    radius = d.radius * 0.28f,
                    center = Offset(hx, hy)
                )
            }
        }
    }

    /**
     * Stamping method for captured Photos and Video frames
     */
    fun stampEffectOnBitmap(
        bitmap: Bitmap,
        effect: String,
        waterColor: String,
        waterHeight: Int,
        waveStrength: Int,
        tiltAngle: Float
    ): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val w = output.width.toFloat()
        val h = output.height.toFloat()

        when (effect) {
            "air-naik" -> {
                val frac = (waterHeight / 100f).coerceIn(0f, 1f)
                val waterY = h * (1f - frac)
                val tanTilt = tan(tiltAngle.toDouble()).toFloat()
                val waveAmp = 18f + (waveStrength / 100f) * 26f

                val (topC, botC) = when (waterColor) {
                    "jernih" -> Pair(AndroidColor.argb(85, 165, 243, 252), AndroidColor.argb(160, 56, 189, 248))
                    "hijau-kolam" -> Pair(AndroidColor.argb(100, 94, 234, 212), AndroidColor.argb(210, 6, 95, 70))
                    else -> Pair(AndroidColor.argb(120, 56, 189, 248), AndroidColor.argb(220, 12, 74, 110))
                }

                val waterPaint = Paint().apply {
                    shader = LinearGradient(0f, waterY, 0f, h, topC, botC, Shader.TileMode.CLAMP)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                val path = AndroidPath()
                val numPoints = 32
                val step = w / numPoints
                val centerX = w / 2f
                val points = mutableListOf<Pair<Float, Float>>()

                for (i in 0..numPoints) {
                    val px = i * step
                    val tiltOffset = (px - centerX) * tanTilt
                    val wave = sin(wavePhase + (px * 0.015f)) * waveAmp
                    val py = (waterY + tiltOffset + wave).coerceIn(0f, h)
                    points.add(Pair(px, py))
                }

                path.moveTo(points[0].first, points[0].second)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val mx = (prev.first + curr.first) / 2f
                    val my = (prev.second + curr.second) / 2f
                    path.quadTo(prev.first, prev.second, mx, my)
                }
                path.lineTo(w, h)
                path.lineTo(0f, h)
                path.close()
                canvas.drawPath(path, waterPaint)

                // Crest highlights
                val crestPaint = Paint().apply {
                    color = AndroidColor.argb(210, 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 7f
                    isAntiAlias = true
                }
                val crestPath = AndroidPath()
                crestPath.moveTo(points[0].first, points[0].second)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    crestPath.quadTo(prev.first, prev.second, (prev.first + curr.first) / 2f, (prev.second + curr.second) / 2f)
                }
                canvas.drawPath(crestPath, crestPaint)
            }
            "riak" -> {
                val cx = w / 2f
                val cy = h / 2f
                val paint = Paint().apply {
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                for (r in listOf(80f, 160f, 260f, 380f, 520f)) {
                    paint.strokeWidth = 16f
                    paint.color = AndroidColor.argb(90, 255, 255, 255)
                    canvas.drawCircle(cx, cy, r, paint)
                    paint.strokeWidth = 6f
                    paint.color = AndroidColor.argb(160, 56, 189, 248)
                    canvas.drawCircle(cx, cy, r + 4, paint)
                }
            }
            "hujan" -> {
                val tint = when (waterColor) {
                    "jernih" -> AndroidColor.argb(35, 56, 189, 248)
                    "hijau-kolam" -> AndroidColor.argb(45, 13, 148, 136)
                    else -> AndroidColor.argb(45, 2, 132, 199)
                }
                canvas.drawColor(tint)

                val dropPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }
                val highlightPaint = Paint().apply {
                    isAntiAlias = true
                    color = AndroidColor.argb(230, 255, 255, 255)
                    style = Paint.Style.FILL
                }

                val drops = listOf(
                    Triple(w * 0.2f, h * 0.25f, 26f),
                    Triple(w * 0.5f, h * 0.4f, 38f),
                    Triple(w * 0.75f, h * 0.22f, 22f),
                    Triple(w * 0.35f, h * 0.65f, 32f),
                    Triple(w * 0.68f, h * 0.7f, 36f),
                    Triple(w * 0.45f, h * 0.85f, 24f),
                    Triple(w * 0.82f, h * 0.5f, 28f),
                    Triple(w * 0.15f, h * 0.55f, 20f)
                )

                for ((dx, dy, rad) in drops) {
                    dropPaint.shader = RadialGradient(
                        dx - rad * 0.3f, dy - rad * 0.3f, rad,
                        AndroidColor.argb(190, 255, 255, 255),
                        AndroidColor.argb(75, 56, 189, 248),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(dx, dy, rad, dropPaint)
                    canvas.drawCircle(dx - rad * 0.35f, dy - rad * 0.35f, rad * 0.28f, highlightPaint)
                }
            }
        }
        return output
    }
}
