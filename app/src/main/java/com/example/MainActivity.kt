package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AppDatabase
import com.example.data.SeedData
import com.example.data.SettingsRepository
import com.example.services.DeviceTiltSensor
import com.example.services.MediaStorageManager
import com.example.services.WaterSoundManager
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AquaCamTheme
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.OceanBlue

class MainActivity : ComponentActivity() {

    private lateinit var soundManager: WaterSoundManager
    private lateinit var tiltSensor: DeviceTiltSensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on during camera usage
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val database = AppDatabase.getDatabase(applicationContext)
        val recordDao = database.recordDao()
        val settingsRepo = SettingsRepository(applicationContext)
        val storageManager = MediaStorageManager(applicationContext)

        soundManager = WaterSoundManager()
        tiltSensor = DeviceTiltSensor(applicationContext)

        setContent {
            var currentScreen by remember { mutableStateOf("kamera") } // "kamera", "galeri", "pengaturan"
            var isRecordingActive by remember { mutableStateOf(false) }

            // Populate sample data if empty
            LaunchedEffect(Unit) {
                SeedData.populateIfEmpty(applicationContext, recordDao)
            }

            // Sensor and Sound Lifecycle
            DisposableEffect(Unit) {
                tiltSensor.start()
                soundManager.start()
                onDispose {
                    tiltSensor.stop()
                    soundManager.stop()
                }
            }

            // System dark mode vs Camera full dark mode
            val isDark = isSystemInDarkTheme() || (currentScreen == "kamera")

            AquaCamTheme(darkTheme = isDark) {
                // Back button handling: from sub-screens, return to Kamera
                if (currentScreen != "kamera") {
                    BackHandler {
                        currentScreen = "kamera"
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (currentScreen == "kamera") DarkBackground else MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 430.dp)
                    ) {
                        Scaffold(
                            bottomBar = {
                                // Bottom bar is hidden while recording
                                if (!isRecordingActive) {
                                    AquaBottomNavigationBar(
                                        currentScreen = currentScreen,
                                        onSelectScreen = { currentScreen = it }
                                    )
                                }
                            },
                            containerColor = Color.Transparent
                        ) { paddingValues ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        bottom = if (isRecordingActive) 0.dp else paddingValues.calculateBottomPadding()
                                    )
                            ) {
                                Crossfade(
                                    targetState = currentScreen,
                                    label = "ScreenTransition"
                                ) { screen ->
                                    when (screen) {
                                        "kamera" -> {
                                            CameraScreen(
                                                settingsRepo = settingsRepo,
                                                recordDao = recordDao,
                                                storageManager = storageManager,
                                                soundManager = soundManager,
                                                tiltSensor = tiltSensor,
                                                onNavigateToGallery = { currentScreen = "galeri" },
                                                onRecordingStateChange = { isRecordingActive = it }
                                            )
                                        }
                                        "galeri" -> {
                                            GalleryScreen(
                                                recordDao = recordDao,
                                                storageManager = storageManager,
                                                onNavigateToCamera = { currentScreen = "kamera" }
                                            )
                                        }
                                        "pengaturan" -> {
                                            SettingsScreen(
                                                settingsRepo = settingsRepo,
                                                recordDao = recordDao
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::tiltSensor.isInitialized) tiltSensor.start()
        if (::soundManager.isInitialized) soundManager.start()
    }

    override fun onPause() {
        super.onPause()
        if (::tiltSensor.isInitialized) tiltSensor.stop()
        if (::soundManager.isInitialized) soundManager.stop()
    }
}

@Composable
fun AquaBottomNavigationBar(
    currentScreen: String,
    onSelectScreen: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme() || (currentScreen == "kamera")
    val navBarColor = if (isDark) DarkSurface else MaterialTheme.colorScheme.surface

    NavigationBar(
        containerColor = navBarColor,
        tonalElevation = 8.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        val items = listOf(
            Triple("kamera", "Kamera", Pair(Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt)),
            Triple("galeri", "Galeri", Pair(Icons.Filled.Collections, Icons.Outlined.Collections)),
            Triple("pengaturan", "Pengaturan", Pair(Icons.Filled.Settings, Icons.Outlined.Settings))
        )

        items.forEach { (route, label, icons) ->
            val isSelected = currentScreen == route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectScreen(route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) icons.first else icons.second,
                        contentDescription = label
                    )
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = OceanBlue,
                    indicatorColor = OceanBlue,
                    unselectedIconColor = if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_tab_$route")
            )
        }
    }
}
