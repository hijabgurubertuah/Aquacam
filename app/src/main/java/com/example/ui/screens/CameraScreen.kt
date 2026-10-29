package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.AppSettings
import com.example.data.RecordDao
import com.example.data.RecordItem
import com.example.data.SettingsRepository
import com.example.services.DeviceTiltSensor
import com.example.services.MediaStorageManager
import com.example.services.WaterEffectRenderer
import com.example.services.WaterSoundManager
import com.example.services.WaterVideoRecorder
import com.example.ui.components.triggerHapticFeedback
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.OceanBlue
import com.example.ui.theme.RecordRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CameraScreen(
    settingsRepo: SettingsRepository,
    recordDao: RecordDao,
    storageManager: MediaStorageManager,
    soundManager: WaterSoundManager,
    tiltSensor: DeviceTiltSensor,
    onNavigateToGallery: () -> Unit,
    onRecordingStateChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings by settingsRepo.settings.collectAsState()
    val tiltAngle by tiltSensor.tiltAngle.collectAsState()
    val latestRecord by recordDao.getLatestRecord().collectAsState(initial = null)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Video direkam tanpa suara", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // Effect Renderer instance
    val effectRenderer = remember { WaterEffectRenderer() }
    val videoRecorder = remember { WaterVideoRecorder(context, effectRenderer) }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var currentCameraLens by remember {
        mutableStateOf(
            if (settings.lastCamera == "depan") CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }

    var captureMode by remember { mutableStateOf("foto") } // "foto" or "video"
    var isRecordingVideo by remember { mutableStateOf(false) }
    var videoSeconds by remember { mutableIntStateOf(0) }
    var showAdjustPanel by remember { mutableStateOf(false) }

    // Flash effect on photo capture
    val flashAlpha = remember { Animatable(0f) }

    // Sound manager lifecycle sync
    LaunchedEffect(settings.waterSound, settings.lastEffect) {
        soundManager.updateConfig(settings.waterSound, settings.lastEffect)
    }

    // FPS watchdog and render loop
    var frameTick by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(settings.lastEffect, settings.waterHeight, settings.waveStrength) {
        var lastTime = System.currentTimeMillis()
        while (true) {
            withFrameMillis { frameTime ->
                val now = System.currentTimeMillis()
                val dt = ((now - lastTime) / 1000f).coerceIn(0.001f, 0.1f)
                lastTime = now

                val targetFrac = settings.waterHeight / 100f
                val active = settings.lastEffect != "tanpa-efek"

                effectRenderer.update(
                    dtSec = dt,
                    targetHeightFraction = targetFrac,
                    effectActive = active,
                    effect = settings.lastEffect,
                    waveStrength = settings.waveStrength
                )
                frameTick = (frameTick + 1f) % 1000f
            }
        }
    }

    // Recording timer callbacks
    DisposableEffect(Unit) {
        videoRecorder.onTimerTick = { sec ->
            videoSeconds = sec
        }
        videoRecorder.onMaxDurationReached = {
            scope.launch {
                stopAndSaveVideo(
                    videoRecorder = videoRecorder,
                    storageManager = storageManager,
                    recordDao = recordDao,
                    settings = settings,
                    context = context,
                    onFinish = {
                        isRecordingVideo = false
                        onRecordingStateChange(false)
                        Toast.makeText(context, "Batas 60 detik tercapai, video tersimpan di Galeri", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
        onDispose {
            if (videoRecorder.isRecording) {
                videoRecorder.stopRecording()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (!hasCameraPermission) {
            // Permission Denied View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Izin kamera diperlukan untuk memakai AquaCam",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OceanBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("open_settings_button")
                ) {
                    Text("Buka Pengaturan")
                }
            }
        } else {
            // Live Camera View
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewViewRef = this
                    }
                },
                update = { view ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(view.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(currentCameraLens)
                                .build()

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(context))
                },
                modifier = Modifier.fillMaxSize()
            )

            // Canvas Water Overlay with Touch Ripples
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings.lastEffect, settings.waveStrength) {
                        detectTapGestures { offset ->
                            if (settings.lastEffect == "hujan") {
                                effectRenderer.addRainSplash(offset.x, offset.y, settings.waveStrength)
                            } else {
                                effectRenderer.addRipple(offset.x, offset.y, settings.waveStrength)
                            }
                            if (settings.waterSound) {
                                soundManager.triggerSplash()
                            }
                        }
                    }
            ) {
                // Re-draw driven by frameTick
                if (frameTick >= 0f) {
                    effectRenderer.drawComposeWater(
                        scope = this,
                        effect = settings.lastEffect,
                        waterColor = settings.waterColor,
                        tiltAngle = tiltAngle,
                        waveStrength = settings.waveStrength
                    )
                }
            }

            // Screen Flash on Photo Capture
            if (flashAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = flashAlpha.value))
                )
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera Flip Button (disabled during recording)
                IconButton(
                    onClick = {
                        if (!isRecordingVideo) {
                            val nextLens = if (currentCameraLens == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                            currentCameraLens = nextLens
                            settingsRepo.updateLastCamera(if (nextLens == CameraSelector.LENS_FACING_FRONT) "depan" else "belakang")
                        }
                    },
                    enabled = !isRecordingVideo,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .testTag("switch_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Ganti Kamera",
                        tint = if (isRecordingVideo) Color.Gray else Color.White
                    )
                }

                // Video Timer Pill
                if (isRecordingVideo) {
                    Surface(
                        color = RecordRed,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val m = videoSeconds / 60
                            val s = videoSeconds % 60
                            Text(
                                text = String.format("%02d:%02d", m, s),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Effect Settings Toggle
                IconButton(
                    onClick = { showAdjustPanel = !showAdjustPanel },
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (showAdjustPanel) OceanBlue else Color.Black.copy(alpha = 0.45f), CircleShape)
                        .testTag("effect_adjust_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Pengaturan Efek",
                        tint = Color.White
                    )
                }
            }

            // Bottom Controls Area
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Effect Adjustment Sheet
                AnimatedVisibility(
                    visible = showAdjustPanel,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Only show water height slider if "air-naik"
                            if (settings.lastEffect == "air-naik") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tinggi Air", color = Color.White, fontSize = 13.sp)
                                    Text("${settings.waterHeight}%", color = CyanAccent, fontSize = 13.sp)
                                }
                                Slider(
                                    value = settings.waterHeight.toFloat(),
                                    onValueChange = { settingsRepo.updateWaterHeight(it.toInt()) },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = CyanAccent,
                                        activeTrackColor = OceanBlue
                                    ),
                                    modifier = Modifier.testTag("water_height_slider")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Kekuatan Gelombang", color = Color.White, fontSize = 13.sp)
                                Text("${settings.waveStrength}%", color = CyanAccent, fontSize = 13.sp)
                            }
                            Slider(
                                value = settings.waveStrength.toFloat(),
                                onValueChange = { settingsRepo.updateWaveStrength(it.toInt()) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CyanAccent,
                                    activeTrackColor = OceanBlue
                                ),
                                modifier = Modifier.testTag("wave_strength_slider")
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Warna Air", color = Color.White, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val colors = listOf(
                                    "jernih" to "Jernih",
                                    "biru-laut" to "Biru Laut",
                                    "hijau-kolam" to "Hijau Kolam"
                                )
                                colors.forEach { (key, label) ->
                                    val isSel = settings.waterColor == key
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSel) OceanBlue else Color.White.copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { settingsRepo.updateWaterColor(key) }
                                            .testTag("color_choice_$key")
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Effect Selector Cards Row (Air Naik, Riak, Hujan di Layar)
                if (!isRecordingVideo) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val effectsList = listOf(
                            Triple("air-naik", "Air Naik", Icons.Default.Water),
                            Triple("riak", "Riak", Icons.Default.Waves),
                            Triple("hujan", "Hujan di Layar", Icons.Default.Grain)
                        )
                        effectsList.forEach { (effKey, effLabel, effIcon) ->
                            val isActive = settings.lastEffect == effKey
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isActive) DarkSurface else Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        width = if (isActive) 2.dp else 1.dp,
                                        color = if (isActive) CyanAccent else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        // Tap active card again to turn off
                                        if (isActive) {
                                            settingsRepo.updateEffect("tanpa-efek")
                                        } else {
                                            settingsRepo.updateEffect(effKey)
                                        }
                                    }
                                    .testTag("effect_card_$effKey")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = effIcon,
                                        contentDescription = effLabel,
                                        tint = if (isActive) CyanAccent else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = effLabel,
                                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Mode Switcher Pills (Foto / Video)
                    Row(
                        modifier = Modifier
                            .padding(bottom = 14.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (captureMode == "foto") OceanBlue else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { captureMode = "foto" }
                                .testTag("mode_photo_button")
                        ) {
                            Text(
                                text = "Foto",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (captureMode == "foto") FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (captureMode == "video") OceanBlue else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { captureMode = "video" }
                                .testTag("mode_video_button")
                        ) {
                            Text(
                                text = "Video",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (captureMode == "video") FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Shutter Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery Thumbnail Shortcut
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable {
                                if (!isRecordingVideo) onNavigateToGallery()
                            }
                            .testTag("gallery_thumbnail_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (latestRecord != null && File(latestRecord!!.filePath).exists()) {
                            AsyncImage(
                                model = File(latestRecord!!.filePath),
                                contentDescription = "Galeri Terakhir",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = "Galeri",
                                tint = CyanAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Main Action Shutter Button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(4.dp, if (captureMode == "video") RecordRed else CyanAccent, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(if (captureMode == "video") RecordRed else Color.White)
                            .clickable {
                                if (captureMode == "foto") {
                                    // Take Photo
                                    triggerHapticFeedback(context, settings.vibration)
                                    scope.launch {
                                        flashAlpha.snapTo(0.85f)
                                        flashAlpha.animateTo(0f, tween(300))

                                        takeAndSavePhoto(
                                            previewView = previewViewRef,
                                            effectRenderer = effectRenderer,
                                            storageManager = storageManager,
                                            recordDao = recordDao,
                                            settings = settings,
                                            tiltAngle = tiltAngle,
                                            context = context
                                        )
                                    }
                                } else {
                                    // Video Toggle
                                    triggerHapticFeedback(context, settings.vibration)
                                    if (!isRecordingVideo) {
                                        val started = startVideoRecording(
                                            videoRecorder = videoRecorder,
                                            storageManager = storageManager,
                                            settings = settings,
                                            previewView = previewViewRef,
                                            tiltAngleProvider = { tiltAngle }
                                        )
                                        if (started) {
                                            isRecordingVideo = true
                                            onRecordingStateChange(true)
                                        } else {
                                            Toast.makeText(context, "Gagal memulai rekaman", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // Stop video recording
                                        scope.launch {
                                            stopAndSaveVideo(
                                                videoRecorder = videoRecorder,
                                                storageManager = storageManager,
                                                recordDao = recordDao,
                                                settings = settings,
                                                context = context,
                                                onFinish = {
                                                    isRecordingVideo = false
                                                    onRecordingStateChange(false)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            .testTag("camera_shutter_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (captureMode == "video" && isRecordingVideo) {
                            // Rounded square icon when recording
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                            )
                        }
                    }

                    // Empty placeholder to balance row spacing
                    Box(modifier = Modifier.size(54.dp))
                }
            }
        }
    }
}

private suspend fun takeAndSavePhoto(
    previewView: PreviewView?,
    effectRenderer: WaterEffectRenderer,
    storageManager: MediaStorageManager,
    recordDao: RecordDao,
    settings: AppSettings,
    tiltAngle: Float,
    context: Context
) {
    if (!storageManager.hasEnoughStorageSpace()) {
        Toast.makeText(context, "Gagal menyimpan, ruang penyimpanan penuh", Toast.LENGTH_SHORT).show()
        return
    }

    val previewBmp = previewView?.bitmap
    val baseBmp = if (previewBmp != null && !previewBmp.isRecycled) {
        previewBmp
    } else {
        Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(20, 30, 45))
        }
    }

    val compositeBmp = withContext(Dispatchers.Default) {
        effectRenderer.stampEffectOnBitmap(
            bitmap = baseBmp,
            effect = settings.lastEffect,
            waterColor = settings.waterColor,
            waterHeight = settings.waterHeight,
            waveStrength = settings.waveStrength,
            tiltAngle = tiltAngle
        )
    }

    val (record, savedToGallery) = withContext(Dispatchers.IO) {
        val res = storageManager.savePhoto(
            bitmap = compositeBmp,
            effect = settings.lastEffect,
            waterColor = settings.waterColor,
            height = settings.waterHeight,
            wave = settings.waveStrength
        )
        recordDao.insertRecord(res.first)
        res
    }

    if (savedToGallery) {
        Toast.makeText(context, "Foto tersimpan di Galeri", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Tersimpan di aplikasi, izin galeri belum diberikan", Toast.LENGTH_SHORT).show()
    }
}

private fun startVideoRecording(
    videoRecorder: WaterVideoRecorder,
    storageManager: MediaStorageManager,
    settings: AppSettings,
    previewView: PreviewView?,
    tiltAngleProvider: () -> Float
): Boolean {
    val file = storageManager.createVideoOutputFile()
    val isHigh = settings.videoQuality == "tinggi"
    return videoRecorder.startRecording(
        outputFile = file,
        isHighQuality = isHigh,
        hasAudioPermission = true,
        getFrameBitmap = { previewView?.bitmap },
        effect = settings.lastEffect,
        waterColor = settings.waterColor,
        waterHeight = settings.waterHeight,
        waveStrength = settings.waveStrength,
        tiltAngleProvider = tiltAngleProvider
    )
}

private suspend fun stopAndSaveVideo(
    videoRecorder: WaterVideoRecorder,
    storageManager: MediaStorageManager,
    recordDao: RecordDao,
    settings: AppSettings,
    context: Context,
    onFinish: () -> Unit
) {
    val (file, duration) = withContext(Dispatchers.IO) {
        videoRecorder.stopRecording()
    }
    onFinish()

    if (file == null || !file.exists()) {
        return
    }

    if (duration < 1) {
        storageManager.deleteFile(file.absolutePath)
        Toast.makeText(context, "Rekaman terlalu singkat", Toast.LENGTH_SHORT).show()
        return
    }

    val (record, savedToGallery) = withContext(Dispatchers.IO) {
        val res = storageManager.registerVideo(
            videoFile = file,
            durationSec = duration,
            effect = settings.lastEffect,
            waterColor = settings.waterColor,
            height = settings.waterHeight,
            wave = settings.waveStrength
        )
        recordDao.insertRecord(res.first)
        res
    }

    if (savedToGallery) {
        Toast.makeText(context, "Video tersimpan di Galeri", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Tersimpan di aplikasi, izin galeri belum diberikan", Toast.LENGTH_SHORT).show()
    }
}
