package com.macrorecorder.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.macrorecorder.app.data.MacroRepository
import com.macrorecorder.app.engine.MacroRecordingManager
import com.macrorecorder.app.service.MacroAccessibilityService
import com.macrorecorder.app.service.ScreenRecordService
import com.macrorecorder.app.ui.screens.DashboardScreen
import com.macrorecorder.app.ui.theme.MacroRecorderTheme
import com.macrorecorder.app.utils.AccessibilityUtils
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var repository: MacroRepository
    private lateinit var recordingManager: MacroRecordingManager

    private var isAccessibilityEnabled by mutableStateOf(false)
    private var isOverlayEnabled by mutableStateOf(false)
    private var isMediaProjectionReady by mutableStateOf(false)

    private var savedProjectionResultCode: Int = 0
    private var savedProjectionData: Intent? = null
    private var pendingMacroName: String? = null

    // MediaProjection screen capture launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            savedProjectionResultCode = result.resultCode
            savedProjectionData = result.data
            isMediaProjectionReady = true
            Toast.makeText(this, "Screen video recording permission granted", Toast.LENGTH_SHORT).show()

            // If a macro recording was waiting for this permission, launch it immediately
            pendingMacroName?.let { name ->
                startRecordingWithSavedProjection(name)
                pendingMacroName = null
            }
        } else {
            Toast.makeText(this, "Screen recording permission was denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestMediaProjectionInternal()
        } else {
            Toast.makeText(this, "Notification permission is needed for screen recording foreground service", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        repository = MacroRepository.getInstance(this)
        recordingManager = MacroRecordingManager.getInstance(this)

        intent?.getStringExtra("toast_message")?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }

        setContent {
            MacroRecorderTheme {
                DashboardScreen(
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isOverlayEnabled = isOverlayEnabled,
                    isMediaProjectionReady = isMediaProjectionReady,
                    onEnableAccessibility = {
                        AccessibilityUtils.openAccessibilitySettings(this)
                    },
                    onEnableOverlay = {
                        AccessibilityUtils.openOverlaySettings(this)
                    },
                    onRequestMediaProjection = {
                        requestMediaProjection()
                    },
                    onStartRecordingWithProjection = { name ->
                        if (savedProjectionResultCode != 0 && savedProjectionData != null) {
                            startRecordingWithSavedProjection(name)
                        } else {
                            pendingMacroName = name
                            requestMediaProjection()
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun updatePermissionStates() {
        isAccessibilityEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(
            this,
            MacroAccessibilityService::class.java
        )
        isOverlayEnabled = AccessibilityUtils.canDrawOverlays(this)
    }

    private fun requestMediaProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestMediaProjectionInternal()
    }

    private fun requestMediaProjectionInternal() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(captureIntent)
    }

    private fun startRecordingWithSavedProjection(macroName: String) {
        val macroId = UUID.randomUUID().toString()
        val videoFile = repository.createNewVideoFile(macroId)
        val outputPath = videoFile.absolutePath

        // 1. Launch screen recorder foreground service via MediaProjection
        savedProjectionData?.let { data ->
            ScreenRecordService.startRecording(
                context = this,
                resultCode = savedProjectionResultCode,
                resultData = data,
                outputPath = outputPath
            )
        }

        // 2. Start structural accessibility recording
        recordingManager.startRecording(macroName = macroName, videoPath = outputPath)
        Toast.makeText(this, "Recording started! Switch to target apps now.", Toast.LENGTH_SHORT).show()
    }
}
