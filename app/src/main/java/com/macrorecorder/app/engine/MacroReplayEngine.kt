package com.macrorecorder.app.engine

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.macrorecorder.app.data.MacroAction
import com.macrorecorder.app.data.MacroActionType
import com.macrorecorder.app.data.MacroWorkflow
import com.macrorecorder.app.service.MacroAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ReplayState {
    object Idle : ReplayState()
    data class Running(val step: Int, val total: Int, val currentAction: MacroAction) : ReplayState()
    object Completed : ReplayState()
    object Stopped : ReplayState()
    data class Error(val message: String) : ReplayState()
}

/**
 * 4. Engine that uses dispatchGesture() and ACTION_SET_TEXT within the AccessibilityService
 * to inject actions sequentially when replaying the JSON workflow.
 */
class MacroReplayEngine private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var replayJob: Job? = null

    private val _replayState = MutableStateFlow<ReplayState>(ReplayState.Idle)
    val replayState: StateFlow<ReplayState> = _replayState.asStateFlow()

    /**
     * Executes sequential playback of a recorded MacroWorkflow.
     */
    fun startReplay(workflow: MacroWorkflow, onFinished: (() -> Unit)? = null) {
        val service = MacroAccessibilityService.instance
        if (service == null) {
            _replayState.value = ReplayState.Error("AccessibilityService is not enabled.")
            return
        }

        if (workflow.actions.isEmpty()) {
            _replayState.value = ReplayState.Error("Macro has no actions to replay.")
            return
        }

        stopReplay()

        replayJob = scope.launch {
            try {
                Log.d(TAG, "Starting sequential replay of macro '${workflow.name}' (${workflow.actions.size} steps)")

                var currentForegroundPackage = ""

                for (action in workflow.actions) {
                    _replayState.value = ReplayState.Running(
                        step = action.stepIndex,
                        total = workflow.actions.size,
                        currentAction = action
                    )

                    // 1. Wait the recorded step delay (with reasonable safety bounds)
                    val waitTime = action.delayFromPreviousMs.coerceIn(250L, 5000L)
                    delay(waitTime)

                    // 2. Bring target package to foreground if it changed
                    if (action.packageName.isNotBlank() && action.packageName != currentForegroundPackage && action.packageName != context.packageName) {
                        bringAppToForeground(action.packageName)
                        currentForegroundPackage = action.packageName
                        delay(600L) // Allow screen render
                    }

                    // 3. Resolve target node structurally by resourceId or text label
                    val node = service.findStructuralNode(
                        packageName = action.packageName,
                        resourceId = action.resourceId,
                        textLabel = action.textLabel
                    )

                    // 4. Inject action via AccessibilityService APIs
                    executeAction(service, action, node)

                    try {
                        node?.recycle()
                    } catch (ignored: Exception) {}
                }

                _replayState.value = ReplayState.Completed
                Log.d(TAG, "Sequential replay of '${workflow.name}' completed successfully.")
                onFinished?.invoke()
            } catch (e: CancellationException) {
                _replayState.value = ReplayState.Stopped
                Log.i(TAG, "Sequential replay was cancelled.")
            } catch (e: Exception) {
                _replayState.value = ReplayState.Error(e.message ?: "Unknown replay error")
                Log.e(TAG, "Error during replay execution: ${e.message}", e)
            }
        }
    }

    private suspend fun executeAction(
        service: MacroAccessibilityService,
        action: MacroAction,
        node: AccessibilityNodeInfo?
    ) {
        when (action.actionType) {
            MacroActionType.CLICK -> {
                var clicked = false

                // 1. If structural node found, compute bounds and dispatch gesture
                if (node != null) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (!rect.isEmpty) {
                        val centerX = rect.centerX().toFloat()
                        val centerY = rect.centerY().toFloat()
                        Log.d(TAG, "Dispatching click gesture to structural node at ($centerX, $centerY)")
                        clicked = service.dispatchClickGesture(centerX, centerY)
                    }
                    // Also attempt direct node performAction as supplement
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }

                // 2. Fallback to recorded bounds if structural node wasn't directly found or gesture failed
                if (!clicked && action.fallbackBounds != null) {
                    val fx = action.fallbackBounds.centerX.toFloat()
                    val fy = action.fallbackBounds.centerY.toFloat()
                    Log.d(TAG, "Dispatching click gesture to fallback coordinates ($fx, $fy)")
                    service.dispatchClickGesture(fx, fy)
                }
            }

            MacroActionType.SET_TEXT -> {
                val textToSet = action.textPayload ?: action.textLabel ?: ""
                var textSet = false

                if (node != null) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    textSet = service.injectSetText(node, textToSet)
                }

                if (!textSet) {
                    // Try targeting active focused input
                    val focusedNode = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focusedNode != null) {
                        service.injectSetText(focusedNode, textToSet)
                        try { focusedNode.recycle() } catch (ignored: Exception) {}
                    }
                }
            }

            MacroActionType.SCROLL_FORWARD -> {
                if (node != null && node.isScrollable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                } else {
                    // Swipe up gesture fallback
                    val displayMetrics = context.resources.displayMetrics
                    val startX = displayMetrics.widthPixels / 2f
                    val startY = displayMetrics.heightPixels * 0.75f
                    val endY = displayMetrics.heightPixels * 0.25f
                    service.dispatchSwipeGesture(startX, startY, startX, endY)
                }
            }

            MacroActionType.SCROLL_BACKWARD -> {
                if (node != null && node.isScrollable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                } else {
                    // Swipe down gesture fallback
                    val displayMetrics = context.resources.displayMetrics
                    val startX = displayMetrics.widthPixels / 2f
                    val startY = displayMetrics.heightPixels * 0.25f
                    val endY = displayMetrics.heightPixels * 0.75f
                    service.dispatchSwipeGesture(startX, startY, startX, endY)
                }
            }

            MacroActionType.KEY_BACK -> {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }

            MacroActionType.KEY_HOME -> {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun bringAppToForeground(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to bring $packageName to foreground: ${e.message}")
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
        if (_replayState.value is ReplayState.Running) {
            _replayState.value = ReplayState.Stopped
        }
    }

    companion object {
        private const val TAG = "MacroReplayEngine"

        @Volatile
        private var instance: MacroReplayEngine? = null

        fun getInstance(context: Context): MacroReplayEngine {
            return instance ?: synchronized(this) {
                instance ?: MacroReplayEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
