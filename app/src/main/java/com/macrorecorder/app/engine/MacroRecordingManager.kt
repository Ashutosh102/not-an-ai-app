package com.macrorecorder.app.engine

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.macrorecorder.app.data.MacroAction
import com.macrorecorder.app.data.MacroActionType
import com.macrorecorder.app.data.MacroBounds
import com.macrorecorder.app.data.MacroRepository
import com.macrorecorder.app.data.MacroWorkflow
import com.macrorecorder.app.service.ScreenRecordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MacroRecordingManager private constructor(private val context: Context) {

    private val repository = MacroRepository.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedActions = MutableStateFlow<List<MacroAction>>(emptyList())
    val recordedActions: StateFlow<List<MacroAction>> = _recordedActions.asStateFlow()

    private var recordingMacroId: String = ""
    private var recordingMacroName: String = ""
    private var recordingStartTime: Long = 0L
    private var lastActionTimestamp: Long = 0L
    private var videoFilePath: String? = null

    /**
     * Starts recording session:
     * - Generates unique macro ID
     * - Configures video recording file path
     * - Sets timestamps
     */
    fun startRecording(macroName: String, videoPath: String? = null) {
        val id = UUID.randomUUID().toString()
        recordingMacroId = id
        recordingMacroName = macroName.ifBlank { "Macro_${System.currentTimeMillis() % 10000}" }
        recordingStartTime = System.currentTimeMillis()
        lastActionTimestamp = recordingStartTime
        videoFilePath = videoPath ?: repository.createNewVideoFile(id).absolutePath

        _recordedActions.value = emptyList()
        _isRecording.value = true

        vibrateFeedback()
        Log.d(TAG, "Recording started: id=$id, name=$recordingMacroName, video=$videoFilePath")
    }

    /**
     * Intercepts and records a UI interaction from the AccessibilityService.
     * Uses structural identifiers: package name, resource ID, or text label.
     */
    fun recordAction(
        actionType: MacroActionType,
        packageName: String,
        resourceId: String?,
        textLabel: String?,
        contentDescription: String?,
        className: String?,
        textPayload: String? = null,
        bounds: Rect? = null
    ) {
        if (!_isRecording.value) return

        // Ignore events originating from our own app
        if (packageName == context.packageName) {
            return
        }

        // Avoid capturing empty actions with neither structural identity nor coordinates
        if (resourceId.isNullOrBlank() && textLabel.isNullOrBlank() && bounds == null && textPayload.isNullOrBlank()) {
            return
        }

        val now = System.currentTimeMillis()
        val delay = (now - lastActionTimestamp).coerceAtLeast(100L)

        // De-duplicate rapid identical clicks (debouncing within 150ms)
        val currentList = _recordedActions.value
        if (currentList.isNotEmpty()) {
            val lastAction = currentList.last()
            if (lastAction.actionType == actionType &&
                lastAction.packageName == packageName &&
                lastAction.resourceId == resourceId &&
                lastAction.textLabel == textLabel &&
                (now - lastAction.timestamp) < 150L
            ) {
                return
            }
        }

        val macroBounds = bounds?.let {
            MacroBounds(it.left, it.top, it.right, it.bottom)
        }

        val action = MacroAction(
            stepIndex = currentList.size + 1,
            actionType = actionType,
            packageName = packageName,
            resourceId = resourceId,
            textLabel = textLabel,
            contentDescription = contentDescription,
            className = className,
            textPayload = textPayload,
            delayFromPreviousMs = delay,
            timestamp = now,
            fallbackBounds = macroBounds
        )

        lastActionTimestamp = now
        _recordedActions.value = currentList + action

        Log.d(TAG, "Captured structural action [Step ${action.stepIndex}]: ${action.actionType} on pkg=${action.packageName}, id=${action.resourceId}, text=${action.textLabel}")
    }

    /**
     * Stops the active recording session, halts screen recording, and serializes the workflow into JSON.
     */
    fun stopRecording(onSaved: ((MacroWorkflow) -> Unit)? = null) {
        if (!_isRecording.value) return

        _isRecording.value = false
        val totalDuration = System.currentTimeMillis() - recordingStartTime

        // Stop media projection service
        ScreenRecordService.stopRecording(context)

        val workflow = MacroWorkflow(
            id = recordingMacroId,
            name = recordingMacroName,
            description = "Captured ${recordedActions.value.size} steps across target apps",
            createdAt = recordingStartTime,
            durationMs = totalDuration,
            videoFilePath = videoFilePath,
            actions = _recordedActions.value
        )

        scope.launch {
            repository.saveMacro(workflow)
            onSaved?.invoke(workflow)
        }

        vibrateFeedback()
        Log.d(TAG, "Recording stopped. Saved ${workflow.actions.size} steps to JSON.")
    }

    fun cancelRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        ScreenRecordService.stopRecording(context)
        _recordedActions.value = emptyList()
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (e: Exception) {
            // Ignore if vibration fails
        }
    }

    companion object {
        private const val TAG = "MacroRecordingManager"

        @Volatile
        private var instance: MacroRecordingManager? = null

        fun getInstance(context: Context): MacroRecordingManager {
            return instance ?: synchronized(this) {
                instance ?: MacroRecordingManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
