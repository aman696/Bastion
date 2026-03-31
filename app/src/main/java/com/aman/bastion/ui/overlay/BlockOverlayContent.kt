package com.aman.bastion.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aman.bastion.ui.theme.BastionColors
import kotlinx.coroutines.delay

/**
 * Rendered inside a ComposeView attached to WindowManager TYPE_APPLICATION_OVERLAY.
 * No ViewModel — reads directly from BlockerServiceState.
 */
@Composable
fun BlockOverlayContent(
    overlayStateFlow: kotlinx.coroutines.flow.StateFlow<OverlayState> = BlockerServiceState.overlay,
    onDismiss: () -> Unit
) {
    val state by overlayStateFlow.collectAsState()
    if (state == OverlayState.Hidden) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.93f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Text(
                text  = "BASTION",
                style = MaterialTheme.typography.labelSmall,
                color = BastionColors.TextMuted
            )
            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                is OverlayState.Soft -> SoftOverlay(s, onDismiss)
                is OverlayState.Hardcore -> HardcoreOverlay(s)
                OverlayState.Hidden -> {}
            }
        }

        // Redirect button (bottom)
        if (state is OverlayState.Soft) {
            val soft = state as OverlayState.Soft
            if (soft.redirectLabel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BastionColors.SurfaceElevated)
                        .clickable(onClick = onDismiss)   // service handles actual deep link
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text  = soft.redirectLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BastionColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftOverlay(state: OverlayState.Soft, onMarkComplete: () -> Unit) {
    Text(
        text  = state.featureName,
        style = MaterialTheme.typography.displayMedium,
        color = BastionColors.AccentAmber
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text  = "You blocked this.",
        style = MaterialTheme.typography.bodyLarge,
        color = BastionColors.TextSecondary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text  = "Complete your task to unlock",
        style = MaterialTheme.typography.bodyMedium,
        color = BastionColors.TextSecondary
    )
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BastionColors.AccentAmber)
            .clickable(onClick = onMarkComplete),
        contentAlignment = Alignment.Center
    ) {
        Text("Mark Complete", style = MaterialTheme.typography.titleMedium, color = Color.Black)
    }
}

@Composable
private fun HardcoreOverlay(state: OverlayState.Hardcore) {
    var remaining by remember { mutableLongStateOf(state.untilMs - System.currentTimeMillis()) }
    LaunchedEffect(state.untilMs) {
        while (remaining > 0) { delay(1000L); remaining = state.untilMs - System.currentTimeMillis() }
    }

    Text(
        text  = state.featureName,
        style = MaterialTheme.typography.displayMedium,
        color = BastionColors.AccentDanger
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text  = "You set this lock.",
        style = MaterialTheme.typography.bodyLarge,
        color = BastionColors.TextSecondary
    )
    Spacer(Modifier.height(24.dp))
    val total = maxOf(remaining, 0L) / 1000L
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    Text(
        text  = "%02d:%02d:%02d".format(h, m, s),
        style = MaterialTheme.typography.displayLarge,
        color = BastionColors.AccentDanger
    )
}
