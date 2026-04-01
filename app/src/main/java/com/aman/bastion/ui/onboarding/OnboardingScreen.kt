package com.aman.bastion.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aman.bastion.ui.theme.BastionColors

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Auto-advance when permission is granted
    LaunchedEffect(state.notificationGranted) {
        if (state.currentStep == 1 && state.notificationGranted) viewModel.advance()
    }
    LaunchedEffect(state.usageGranted) {
        if (state.currentStep == 2 && state.usageGranted) viewModel.advance()
    }
    LaunchedEffect(state.overlayGranted) {
        if (state.currentStep == 3 && state.overlayGranted) viewModel.advance()
    }
    LaunchedEffect(state.accessibilityGranted) {
        if (state.currentStep == 4 && state.accessibilityGranted) viewModel.advance()
    }

    val notifLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    } else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BastionColors.Background),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState   = state.currentStep,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                (slideOutHorizontally { -it } + fadeOut())
            },
            label = "step"
        ) { step ->
            when (step) {
                0 -> StepWelcome(onNext = viewModel::advance)
                1 -> StepNotification(
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else viewModel.advance()
                    }
                )
                2 -> StepUsageAccess(
                    onGrant = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                )
                3 -> StepOverlay(
                    onGrant = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"))
                        )
                    }
                )
                4 -> StepAccessibility(
                    onGrant = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                5 -> StepBattery(
                    onGrant = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"))
                            )
                        }
                        viewModel.advance()
                    },
                    onSkip = viewModel::advance
                )
                6 -> StepAllSet(
                    onFinish = {
                        viewModel.markOnboardingDone()
                        onFinished()
                    }
                )
                else -> StepAllSet(onFinish = { viewModel.markOnboardingDone(); onFinished() })
            }
        }
    }
}

// ── Step screens ──────────────────────────────────────────────────────────────

@Composable
private fun StepWelcome(onNext: () -> Unit) {
    OnboardingStep(
        icon     = "🛡",
        title    = "BASTION",
        body     = "Built for brains that need real walls, not suggestions.",
        subtext  = "You'll need to grant 4 permissions. Each one matters.",
        ctaLabel = "Let's do it →",
        ctaAction = onNext
    )
}

@Composable
private fun StepNotification(onGrant: () -> Unit) {
    OnboardingStep(
        icon     = "🔔",
        title    = "Stay aware",
        body     = "We show a persistent notification so the blocking service stays alive. You'll barely notice it.",
        subtext  = "Without this, Android kills the service.",
        ctaLabel = "Grant notification access →",
        ctaAction = onGrant
    )
}

@Composable
private fun StepUsageAccess(onGrant: () -> Unit) {
    OnboardingStep(
        icon     = "👁",
        title    = "See which app is open",
        body     = "We use Android's Usage Access to know which app is in the foreground. We never read your content — just package names.",
        subtext  = "Tap 'Bastion' in the list, then toggle it on. Come back when done.",
        ctaLabel = "Open Usage Access settings →",
        ctaAction = onGrant
    )
}

@Composable
private fun StepOverlay(onGrant: () -> Unit) {
    OnboardingStep(
        icon     = "⬛",
        title    = "Draw the block screen",
        body     = "This lets us draw the block overlay on top of Instagram or TikTok when you hit a restricted area.",
        subtext  = "Come back when done.",
        ctaLabel = "Grant overlay permission →",
        ctaAction = onGrant
    )
}

@Composable
private fun StepAccessibility(onGrant: () -> Unit) {
    OnboardingStep(
        icon     = "⚙️",
        title    = "The core engine",
        body     = "Bastion uses Android's Accessibility API to detect which tab you're on inside Instagram — so it can block Reels without blocking your DMs.\n\nWe only read the UI structure of apps you've configured. We never read your messages, see your content, or send anything off your device.",
        subtext  = "Look for 'Bastion' under Installed Apps. Enable it. Android will show a warning — this is normal.",
        ctaLabel = "Open Accessibility settings →",
        ctaAction = onGrant
    )
}

@Composable
private fun StepBattery(onGrant: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("🔋", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(24.dp))
        Text("Stay alive in the background",
            style = MaterialTheme.typography.titleLarge, color = BastionColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Text("Some Android manufacturers aggressively kill background apps. Unrestricted battery access keeps Bastion running even when your screen is off.",
            style = MaterialTheme.typography.bodyLarge, color = BastionColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text("If skipped, blocking may stop working when your screen turns off on Samsung/Xiaomi devices.",
            style = MaterialTheme.typography.bodySmall, color = BastionColors.AccentDanger)
        Spacer(Modifier.height(32.dp))
        OnboardingCta(label = "Exempt from battery optimization →", onClick = onGrant)
        Spacer(Modifier.height(12.dp))
        Text(
            text     = "Skip (not recommended)",
            style    = MaterialTheme.typography.bodySmall,
            color    = BastionColors.TextSecondary,
            modifier = Modifier.clickable(onClick = onSkip)
        )
    }
}

@Composable
private fun StepAllSet(onFinish: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text  = "✓",
            style = MaterialTheme.typography.displayLarge,
            color = BastionColors.AccentSuccess
        )
        Spacer(Modifier.height(24.dp))
        Text("Bastion is active.",
            style = MaterialTheme.typography.titleLarge, color = BastionColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Now add your first restriction.",
            style = MaterialTheme.typography.bodyLarge, color = BastionColors.TextSecondary)
        Spacer(Modifier.height(40.dp))
        OnboardingCta(label = "Protect an app →", onClick = onFinish)
    }
}

// ── Shared layout ─────────────────────────────────────────────────────────────

@Composable
private fun OnboardingStep(
    icon: String,
    title: String,
    body: String,
    subtext: String,
    ctaLabel: String,
    ctaAction: () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(icon, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = BastionColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = BastionColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(subtext, style = MaterialTheme.typography.bodySmall, color = BastionColors.TextMuted)
        Spacer(Modifier.height(40.dp))
        OnboardingCta(label = ctaLabel, onClick = ctaAction)
    }
}

@Composable
private fun OnboardingCta(label: String, onClick: () -> Unit) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BastionColors.AccentAmber)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.Black)
    }
}
