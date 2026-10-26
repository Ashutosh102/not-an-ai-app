package com.macrorecorder.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrorecorder.app.data.MacroWorkflow
import com.macrorecorder.app.ui.theme.DarkCard
import com.macrorecorder.app.ui.theme.PlayGreen
import com.macrorecorder.app.ui.theme.PrimaryBlue
import com.macrorecorder.app.ui.theme.RecordRed
import com.macrorecorder.app.ui.theme.TextPrimary
import com.macrorecorder.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacroCard(
    workflow: MacroWorkflow,
    onReplay: (MacroWorkflow) -> Unit,
    onInspectJson: (MacroWorkflow) -> Unit,
    onPreviewVideo: (MacroWorkflow) -> Unit,
    onDelete: (MacroWorkflow) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateString = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault())
        .format(Date(workflow.createdAt))
    val seconds = (workflow.durationMs / 1000.0)

    val uniquePackages = workflow.actions
        .map { it.packageName }
        .filter { it.isNotBlank() }
        .distinct()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title & Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = { onDelete(workflow) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Macro",
                        tint = RecordRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badges row: Steps count, Duration, Video indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeChip(
                    text = "${workflow.actions.size} Steps",
                    backgroundColor = Color(0x2038BDF8),
                    textColor = PrimaryBlue
                )
                BadgeChip(
                    text = String.format(Locale.US, "%.1fs", seconds),
                    backgroundColor = Color(0x202DD4BF),
                    textColor = Color(0xFF2DD4BF)
                )
                if (!workflow.videoFilePath.isNullOrBlank()) {
                    BadgeChip(
                        text = "Video MP4",
                        backgroundColor = Color(0x20EF4444),
                        textColor = RecordRed
                    )
                }
            }

            // Target packages flow
            if (uniquePackages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uniquePackages.forEach { pkg ->
                        Text(
                            text = pkg,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(Color(0xFF334155), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row: Replay, JSON, Video
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Replay Button
                Button(
                    onClick = { onReplay(workflow) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PlayGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Replay", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // View JSON button
                OutlinedButton(
                    onClick = { onInspectJson(workflow) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "JSON", fontSize = 12.sp)
                }

                // View Video button (if recorded)
                if (!workflow.videoFilePath.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onPreviewVideo(workflow) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RecordRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Video", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
