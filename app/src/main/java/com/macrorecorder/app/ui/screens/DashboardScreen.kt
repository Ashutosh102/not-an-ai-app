package com.macrorecorder.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrorecorder.app.data.MacroRepository
import com.macrorecorder.app.data.MacroWorkflow
import com.macrorecorder.app.engine.MacroRecordingManager
import com.macrorecorder.app.engine.MacroReplayEngine
import com.macrorecorder.app.engine.ReplayState
import com.macrorecorder.app.service.FloatingOverlayService
import com.macrorecorder.app.service.MacroAccessibilityService
import com.macrorecorder.app.ui.components.JsonViewerDialog
import com.macrorecorder.app.ui.components.MacroCard
import com.macrorecorder.app.ui.components.PermissionBanner
import com.macrorecorder.app.ui.components.VideoPreviewDialog
import com.macrorecorder.app.ui.theme.DarkBackground
import com.macrorecorder.app.ui.theme.DarkCard
import com.macrorecorder.app.ui.theme.PlayGreen
import com.macrorecorder.app.ui.theme.PrimaryBlue
import com.macrorecorder.app.ui.theme.RecordRed
import com.macrorecorder.app.ui.theme.TextPrimary
import com.macrorecorder.app.ui.theme.TextSecondary
import com.macrorecorder.app.ui.utils.SampleMacroGenerator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    isAccessibilityEnabled: Boolean,
    isOverlayEnabled: Boolean,
    isMediaProjectionReady: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onStartRecordingWithProjection: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MacroRepository.getInstance(context) }
    val recordingManager = remember { MacroRecordingManager.getInstance(context) }
    val replayEngine = remember { MacroReplayEngine.getInstance(context) }

    val macros by repository.macrosFlow.collectAsState()
    val isRecording by recordingManager.isRecording.collectAsState()
    val recordedActions by recordingManager.recordedActions.collectAsState()
    val replayState by replayEngine.replayState.collectAsState()
    val isOverlayActive by FloatingOverlayService.isOverlayActive.collectAsState()

    var workflowNameInput by remember { mutableStateOf("QuickWorkflow") }
    var inspectingWorkflow by remember { mutableStateOf<MacroWorkflow?>(null) }
    var previewingVideoWorkflow by remember { mutableStateOf<MacroWorkflow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(if (isRecording) RecordRed else PlayGreen, RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Macro Recorder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    // Floating Trigger Overlay Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Overlay Button",
                            tint = if (isOverlayActive) PrimaryBlue else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Overlay",
                            fontSize = 12.sp,
                            color = if (isOverlayActive) PrimaryBlue else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isOverlayActive,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (isOverlayEnabled) {
                                        FloatingOverlayService.start(context)
                                    } else {
                                        onEnableOverlay()
                                    }
                                } else {
                                    FloatingOverlayService.stop(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryBlue,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkCard
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Permission Readiness Banner
            item {
                PermissionBanner(
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isOverlayEnabled = isOverlayEnabled,
                    isMediaProjectionReady = isMediaProjectionReady,
                    onEnableAccessibility = onEnableAccessibility,
                    onEnableOverlay = onEnableOverlay,
                    onRequestMediaProjection = onRequestMediaProjection
                )
            }

            // 2. Replay Progress Bar (when replaying)
            if (replayState is ReplayState.Running) {
                val state = replayState as ReplayState.Running
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Replaying Step ${state.step} of ${state.total}",
                                    fontWeight = FontWeight.Bold,
                                    color = PlayGreen,
                                    fontSize = 13.sp
                                )
                                Button(
                                    onClick = { replayEngine.stopReplay() },
                                    colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(text = "Halt", fontSize = 11.sp, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${state.currentAction.actionType} → ${state.currentAction.packageName}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val progress = state.step.toFloat() / state.total.toFloat()
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = PlayGreen,
                                trackColor = Color(0x3322C55E)
                            )
                        }
                    }
                }
            }

            // 3. Workflow Capture Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isRecording) "Recording Workflow in Progress..." else "Capture New Macro",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRecording) {
                                "Tap around target apps. We are logging package names, resource node IDs, and text labels while capturing screen video."
                            } else {
                                "Records screen video via MediaProjection and intercepts structural node IDs without raw X/Y."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!isRecording) {
                            OutlinedTextField(
                                value = workflowNameInput,
                                onValueChange = { workflowNameInput = it },
                                label = { Text("Macro Name", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (MacroAccessibilityService.instance == null) {
                                        Toast.makeText(context, "Please enable Accessibility Service first!", Toast.LENGTH_SHORT).show()
                                        onEnableAccessibility()
                                        return@Button
                                    }
                                    onStartRecordingWithProjection(workflowNameInput)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FiberManualRecord,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Start Recording Macro",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Recording is active
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recorded Actions: ${recordedActions.size}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RecordRed
                                )

                                Button(
                                    onClick = {
                                        recordingManager.stopRecording { saved ->
                                            Toast.makeText(context, "Saved '${saved.name}' with ${saved.actions.size} steps!", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Stop & Save", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Saved Macros Header & Load Sample button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Macros (${macros.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val s1 = SampleMacroGenerator.createSettingsSearchSample()
                                val s2 = SampleMacroGenerator.createCalculatorSample()
                                repository.saveMacro(s1)
                                repository.saveMacro(s2)
                                Toast.makeText(context, "Loaded 2 sample macros!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Load Samples", fontSize = 11.sp, color = PrimaryBlue)
                    }
                }
            }

            // 5. Macro Items List
            if (macros.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No saved workflows yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Load Samples' above or enable the Floating Trigger Overlay to record workflows across any Android application.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(macros, key = { it.id }) { workflow ->
                    MacroCard(
                        workflow = workflow,
                        onReplay = { itemToReplay ->
                            if (MacroAccessibilityService.instance == null) {
                                Toast.makeText(context, "Accessibility service required for automated replay", Toast.LENGTH_LONG).show()
                                onEnableAccessibility()
                            } else {
                                Toast.makeText(context, "Replaying '${itemToReplay.name}'...", Toast.LENGTH_SHORT).show()
                                replayEngine.startReplay(itemToReplay) {
                                    Toast.makeText(context, "Replay completed!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onInspectJson = { itemToInspect ->
                            inspectingWorkflow = itemToInspect
                        },
                        onPreviewVideo = { itemWithVideo ->
                            previewingVideoWorkflow = itemWithVideo
                        },
                        onDelete = { itemToDelete ->
                            scope.launch {
                                repository.deleteMacro(itemToDelete.id)
                                Toast.makeText(context, "Deleted '${itemToDelete.name}'", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // JSON Spec Dialog
    inspectingWorkflow?.let { workflow ->
        JsonViewerDialog(
            workflow = workflow,
            onDismiss = { inspectingWorkflow = null }
        )
    }

    // Video Preview Dialog
    previewingVideoWorkflow?.let { workflow ->
        VideoPreviewDialog(
            workflow = workflow,
            onDismiss = { previewingVideoWorkflow = null }
        )
    }
}
