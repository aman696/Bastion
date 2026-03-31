package com.aman.bastion.ui.hardcorelock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aman.bastion.ui.theme.BastionColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HardcoreLockScreen(
    packageName: String,
    onNavigateUp: () -> Unit,
    viewModel: HardcoreLockViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var remaining by remember { mutableLongStateOf(state.hardcoreUntilMs - System.currentTimeMillis()) }
    LaunchedEffect(state.hardcoreUntilMs) {
        while (remaining > 0) {
            delay(1000L)
            remaining = state.hardcoreUntilMs - System.currentTimeMillis()
        }
        if (!state.isLoading) onNavigateUp()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BastionColors.HardcoreBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "HARDCORE LOCK ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = BastionColors.AccentDanger
            )
            Spacer(Modifier.height(24.dp))

            state.icon?.let { bmp ->
                Image(
                    bitmap             = bmp,
                    contentDescription = null,
                    modifier           = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .redGlow(BastionColors.AccentDanger, 20.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text  = state.appName,
                style = MaterialTheme.typography.titleLarge,
                color = BastionColors.TextPrimary
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text  = formatCountdown(remaining),
                style = MaterialTheme.typography.displayLarge,
                color = BastionColors.AccentDanger
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text      = "You set this lock. It cannot be removed until the timer expires.\nCome back later.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = BastionColors.TextSecondary,
                modifier  = Modifier.padding(horizontal = 40.dp),
                textAlign = TextAlign.Center
            )

            if (state.createdAt > 0L) {
                Spacer(Modifier.height(8.dp))
                val dateStr = remember(state.createdAt) {
                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        .format(Date(state.createdAt))
                }
                Text(
                    text  = "Set on $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = BastionColors.TextMuted
                )
            }
        }

        // "Why did I set this?" at the bottom
        Column(
            modifier               = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment    = Alignment.CenterHorizontally
        ) {
            val note = state.blockNote
            if (note != null) {
                Text(
                    text     = "Why did I set this?",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = BastionColors.TextSecondary,
                    modifier = Modifier.clickable { viewModel.toggleShowNote() }
                )
                if (state.showNote) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = note,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = BastionColors.TextPrimary,
                        modifier  = Modifier.padding(horizontal = 40.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun Modifier.redGlow(color: Color, glowRadius: Dp): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().also { p ->
            p.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color  = android.graphics.Color.TRANSPARENT
                setShadowLayer(glowRadius.toPx(), 0f, 0f, color.copy(alpha = 0.6f).toArgb())
            }
        }
        canvas.drawRoundRect(
            left    = 0f, top = 0f,
            right   = size.width, bottom = size.height,
            radiusX = 12.dp.toPx(), radiusY = 12.dp.toPx(),
            paint   = paint
        )
    }
}

private fun formatCountdown(ms: Long): String {
    val total = maxOf(ms, 0L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
