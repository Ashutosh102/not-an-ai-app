package com.macrorecorder.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrorecorder.app.ui.theme.DarkCard
import com.macrorecorder.app.ui.theme.PlayGreen
import com.macrorecorder.app.ui.theme.RecordRed
import com.macrorecorder.app.ui.theme.TextPrimary
import com.macrorecorder.app.ui.theme.TextSecondary

@Composable
fun PermissionBanner(
    isAccessibilityEnabled: Boolean,
    isOverlayEnabled: Boolean,
    isMediaProjectionReady: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "System Permissions & Services",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Macro recording and automated replay require the following capabilities:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Accessibility Service Item
            PermissionItem(
                icon = Icons.Default.AccessibilityNew,
                title = "1. Accessibility Service",
                description = "Intercepts structural node IDs/labels and injects dispatchGesture() actions",
                isGranted = isAccessibilityEnabled,
                actionLabel = "Enable Service",
                onAction = onEnableAccessibility
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Overlay Permission Item
            PermissionItem(
                icon = Icons.Default.Layers,
                title = "2. Draw Over Apps (Overlay)",
                description = "Enables floating controller widget above target third-party apps",
                isGranted = isOverlayEnabled,
                actionLabel = "Grant Overlay",
                onAction = onEnableOverlay
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. MediaProjection Screen Recording
            PermissionItem(
                icon = Icons.Default.Videocam,
                title = "3. Screen Video Recording",
                description = "Simultaneous screen video capture during workflow recording",
                isGranted = isMediaProjectionReady,
                actionLabel = "Grant Capture",
                onAction = onRequestMediaProjection
            )
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isGranted) Color(0x1A22C55E) else Color(0x1AEF4444),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) PlayGreen else RecordRed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = if (isGranted) "Active" else "Action needed",
                        tint = if (isGranted) PlayGreen else RecordRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        if (!isGranted) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                contentPadding = ButtonDefaults.ContentPadding,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = actionLabel, fontSize = 11.sp, color = Color.White)
            }
        }
    }
}
