package com.macrorecorder.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.macrorecorder.app.MainActivity
import com.macrorecorder.app.R
import com.macrorecorder.app.data.MacroRepository
import com.macrorecorder.app.engine.MacroRecordingManager
import com.macrorecorder.app.engine.MacroReplayEngine
import com.macrorecorder.app.engine.ReplayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 5. Floating trigger overlay button service that hovers over target apps,
 * allowing instant Record, Stop, and Replay actions.
 */
class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var observerJob: Job? = null

    private lateinit var recordingManager: MacroRecordingManager
    private lateinit var replayEngine: MacroReplayEngine
    private lateinit var repository: MacroRepository

    private var tvStatus: TextView? = null
    private var btnRecord: ImageView? = null
    private var btnReplay: ImageView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        recordingManager = MacroRecordingManager.getInstance(this)
        replayEngine = MacroReplayEngine.getInstance(this)
        repository = MacroRepository.getInstance(this)

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot start FloatingOverlayService without SYSTEM_ALERT_WINDOW permission")
            stopSelf()
            return
        }

        initOverlayView()
        observeStates()
        _isOverlayActive.value = true
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun initOverlayView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 200
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.floating_overlay_layout, null)

        tvStatus = overlayView?.findViewById(R.id.tv_overlay_status)
        btnRecord = overlayView?.findViewById(R.id.btn_overlay_record)
        btnReplay = overlayView?.findViewById(R.id.btn_overlay_replay)
        val btnClose = overlayView?.findViewById<ImageView>(R.id.btn_overlay_close)
        val btnDrag = overlayView?.findViewById<ImageView>(R.id.btn_drag_handle)

        // Dragging listener
        setupDragListener(btnDrag ?: overlayView!!)

        // Record Button click listener
        btnRecord?.setOnClickListener {
            handleRecordToggle()
        }

        // Replay Button click listener
        btnReplay?.setOnClickListener {
            handleReplayToggle()
        }

        // Close Button click listener
        btnClose?.setOnClickListener {
            stopSelf()
        }

        windowManager?.addView(overlayView, layoutParams)
    }

    private fun handleRecordToggle() {
        if (recordingManager.isRecording.value) {
            recordingManager.stopRecording {
                Log.d(TAG, "Recording saved from floating overlay")
            }
        } else {
            // Check if accessibility service is on
            if (MacroAccessibilityService.instance == null) {
                launchMainActivity("Please enable Macro Accessibility Service before recording.")
                return
            }
            recordingManager.startRecording("QuickMacro_${System.currentTimeMillis() % 1000}")
        }
    }

    private fun handleReplayToggle() {
        if (replayEngine.replayState.value is ReplayState.Running) {
            replayEngine.stopReplay()
        } else {
            val latest = repository.macrosFlow.value.firstOrNull()
            if (latest != null) {
                replayEngine.startReplay(latest)
            } else {
                launchMainActivity("No macros saved yet. Record a workflow first!")
            }
        }
    }

    private fun launchMainActivity(message: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            message?.let { putExtra("toast_message", it) }
        }
        startActivity(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun observeStates() {
        observerJob = serviceScope.launch {
            // Observe recording state
            launch {
                recordingManager.isRecording.collect { recording ->
                    if (recording) {
                        tvStatus?.text = "REC (${recordingManager.recordedActions.value.size})"
                        tvStatus?.setBackgroundResource(R.drawable.badge_recording)
                        btnRecord?.setImageResource(R.drawable.ic_stop)
                    } else if (replayEngine.replayState.value !is ReplayState.Running) {
                        tvStatus?.text = "IDLE"
                        tvStatus?.setBackgroundResource(R.drawable.badge_idle)
                        btnRecord?.setImageResource(R.drawable.ic_record)
                    }
                }
            }

            // Observe actions recorded count
            launch {
                recordingManager.recordedActions.collect { actions ->
                    if (recordingManager.isRecording.value) {
                        tvStatus?.text = "REC (${actions.size})"
                    }
                }
            }

            // Observe replay engine state
            launch {
                replayEngine.replayState.collect { state ->
                    when (state) {
                        is ReplayState.Running -> {
                            tvStatus?.text = "STEP ${state.step}/${state.total}"
                            tvStatus?.setBackgroundResource(R.drawable.badge_replaying)
                            btnReplay?.setImageResource(R.drawable.ic_stop)
                        }
                        ReplayState.Completed, ReplayState.Stopped, is ReplayState.Error -> {
                            if (!recordingManager.isRecording.value) {
                                tvStatus?.text = "IDLE"
                                tvStatus?.setBackgroundResource(R.drawable.badge_idle)
                            }
                            btnReplay?.setImageResource(R.drawable.ic_play)
                        }
                        ReplayState.Idle -> {
                            btnReplay?.setImageResource(R.drawable.ic_play)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (ignored: Exception) {}
        }
        overlayView = null
        _isOverlayActive.value = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingOverlayService"

        private val _isOverlayActive = MutableStateFlow(false)
        val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }
}
