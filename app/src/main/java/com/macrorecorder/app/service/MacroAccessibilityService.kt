package com.macrorecorder.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.macrorecorder.app.data.MacroActionType
import com.macrorecorder.app.engine.MacroRecordingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 1. Background AccessibilityService that listens for screen interactions structurally
 * (logging active package name, specific Resource Node ID, and Text Label) without raw X/Y reliance.
 * 4. Injects actions sequentially using dispatchGesture() and ACTION_SET_TEXT when replaying.
 */
class MacroAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.i(TAG, "MacroAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val recordingManager = MacroRecordingManager.getInstance(this)
        if (!recordingManager.isRecording.value) return

        val eventPackage = event.packageName?.toString() ?: ""
        // Filter out actions from our own application and system UI overlays
        if (eventPackage.isBlank() || eventPackage == packageName) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClickedEvent(event, recordingManager)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleTextChangedEvent(event, recordingManager)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleViewScrolledEvent(event, recordingManager)
            }
        }
    }

    private fun handleViewClickedEvent(event: AccessibilityEvent, recordingManager: MacroRecordingManager) {
        val source = event.source
        val pkg = event.packageName?.toString() ?: source?.packageName?.toString() ?: ""
        val resourceId = source?.viewIdResourceName
        val textLabel = (source?.text ?: source?.contentDescription)?.toString()
        val contentDesc = source?.contentDescription?.toString()
        val className = (source?.className ?: event.className)?.toString()

        val bounds = Rect()
        source?.getBoundsInScreen(bounds)

        recordingManager.recordAction(
            actionType = MacroActionType.CLICK,
            packageName = pkg,
            resourceId = resourceId,
            textLabel = textLabel,
            contentDescription = contentDesc,
            className = className,
            textPayload = null,
            bounds = if (bounds.isEmpty) null else bounds
        )

        try {
            source?.recycle()
        } catch (ignored: Exception) {}
    }

    private fun handleTextChangedEvent(event: AccessibilityEvent, recordingManager: MacroRecordingManager) {
        val source = event.source
        val pkg = event.packageName?.toString() ?: source?.packageName?.toString() ?: ""
        val resourceId = source?.viewIdResourceName
        val textLabel = source?.text?.toString()
        val contentDesc = source?.contentDescription?.toString()
        val className = (source?.className ?: event.className)?.toString()

        val textPayload = event.text?.joinToString("") ?: source?.text?.toString() ?: ""

        val bounds = Rect()
        source?.getBoundsInScreen(bounds)

        recordingManager.recordAction(
            actionType = MacroActionType.SET_TEXT,
            packageName = pkg,
            resourceId = resourceId,
            textLabel = textLabel,
            contentDescription = contentDesc,
            className = className,
            textPayload = textPayload,
            bounds = if (bounds.isEmpty) null else bounds
        )

        try {
            source?.recycle()
        } catch (ignored: Exception) {}
    }

    private fun handleViewScrolledEvent(event: AccessibilityEvent, recordingManager: MacroRecordingManager) {
        val source = event.source
        val pkg = event.packageName?.toString() ?: source?.packageName?.toString() ?: ""
        val resourceId = source?.viewIdResourceName
        val textLabel = (source?.text ?: source?.contentDescription)?.toString()

        val bounds = Rect()
        source?.getBoundsInScreen(bounds)

        recordingManager.recordAction(
            actionType = MacroActionType.SCROLL_FORWARD,
            packageName = pkg,
            resourceId = resourceId,
            textLabel = textLabel,
            contentDescription = source?.contentDescription?.toString(),
            className = source?.className?.toString(),
            bounds = if (bounds.isEmpty) null else bounds
        )

        try {
            source?.recycle()
        } catch (ignored: Exception) {}
    }

    override fun onInterrupt() {
        Log.w(TAG, "MacroAccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        _isServiceActive.value = false
        Log.i(TAG, "MacroAccessibilityService destroyed")
        super.onDestroy()
    }

    // =========================================================================
    // Replay Injection Engine API: dispatchGesture() and ACTION_SET_TEXT
    // =========================================================================

    /**
     * Injects a precise click gesture at (x, y) using dispatchGesture().
     */
    suspend fun dispatchClickGesture(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "dispatchGesture completed successfully at ($x, $y)")
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "dispatchGesture was cancelled at ($x, $y)")
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)

            if (!dispatched) {
                Log.e(TAG, "dispatchGesture returned false immediately")
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    /**
     * Injects a swipe gesture from start to end coordinates.
     */
    suspend fun dispatchSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)

            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    /**
     * Injects text into an AccessibilityNodeInfo using ACTION_SET_TEXT.
     */
    fun injectSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.d(TAG, "ACTION_SET_TEXT performed on ${node.viewIdResourceName ?: "node"}: result=$result")
        return result
    }

    /**
     * Structurally locates the target AccessibilityNodeInfo on the current screen by:
     * 1. Exact Resource Node ID (viewIdResourceName)
     * 2. Visible text label / content description
     * 3. Depth-first traversal matching criteria
     */
    fun findStructuralNode(
        packageName: String?,
        resourceId: String?,
        textLabel: String?
    ): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        // 1. Try finding by Resource ID
        if (!resourceId.isNullOrBlank()) {
            val nodesById = root.findAccessibilityNodeInfosByViewId(resourceId)
            if (!nodesById.isNullOrEmpty()) {
                val candidate = nodesById.firstOrNull { it.isVisibleToUser } ?: nodesById.first()
                Log.d(TAG, "Located node structurally by resourceId: $resourceId")
                return candidate
            }
        }

        // 2. Try finding by Text Label
        if (!textLabel.isNullOrBlank()) {
            val nodesByText = root.findAccessibilityNodeInfosByText(textLabel)
            if (!nodesByText.isNullOrEmpty()) {
                val candidate = nodesByText.firstOrNull { it.isVisibleToUser } ?: nodesByText.first()
                Log.d(TAG, "Located node structurally by textLabel: $textLabel")
                return candidate
            }
        }

        // 3. Fallback: Recursive DFS traversal
        return findNodeDfs(root, resourceId, textLabel)
    }

    private fun findNodeDfs(
        node: AccessibilityNodeInfo?,
        resourceId: String?,
        textLabel: String?
    ): AccessibilityNodeInfo? {
        if (node == null) return null

        // Match resource ID
        if (!resourceId.isNullOrBlank() && node.viewIdResourceName.equals(resourceId, ignoreCase = true)) {
            return node
        }

        // Match text or content description
        if (!textLabel.isNullOrBlank()) {
            val nodeText = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()
            if (nodeText.equals(textLabel, ignoreCase = true) || contentDesc.equals(textLabel, ignoreCase = true)) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val match = findNodeDfs(child, resourceId, textLabel)
            if (match != null) {
                return match
            }
        }

        return null
    }

    companion object {
        private const val TAG = "MacroAccessibility"

        @Volatile
        var instance: MacroAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()
    }
}
