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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
    val totalSteps = 8

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
    LaunchedEffect(state.deviceAdminGranted) {
        if (state.currentStep == 6 && state.deviceAdminGranted) viewModel.advance()
    }

    val notifLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    } else null
    val deviceAdminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF17231C), BastionColors.Background)
                )
            )
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingHeader(step = state.currentStep, totalSteps = totalSteps)
            Spacer(Modifier.height(14.dp))
            PrivacyDisclaimerStrip()
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                AnimatedContent(
                    targetState = state.currentStep,
                    transitionSpec = {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    },
                    label = "onboardingStep"
                ) { step ->
                    when (step) {
                        0 -> OnboardingCard(
                            eyebrow = "WELCOME",
                            title = "Set the walls before you need them.",
                            body = "Bastion works best when setup feels calm and deliberate. We'll turn on the permissions that keep your restrictions alive.",
                            footnote = "This takes a minute or two. After that, you only come back to refine your rules.",
                            ctaLabel = "Start Setup",
                            ctaAction = viewModel::advance
                        )

                        1 -> OnboardingCard(
                            eyebrow = "NOTIFICATIONS",
                            title = "Keep the service visible to Android.",
                            body = "Bastion uses a persistent notification so the blocker service keeps running in the background.",
                            footnote = "Android is much more likely to kill the protection layer without this.",
                            ctaLabel = "Grant Notification Access",
                            ctaAction = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.advance()
                                }
                            }
                        )

                        2 -> OnboardingCard(
                            eyebrow = "USAGE ACCESS",
                            title = "Let Bastion see which app is on screen.",
                            body = "This gives Bastion the foreground app package name. It does not reveal messages, posts, browser content, or anything you type.",
                            footnote = "Open the list, choose Bastion, turn it on, then come back.",
                            ctaLabel = "Open Usage Access",
                            ctaAction = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        )

                        3 -> OnboardingCard(
                            eyebrow = "OVERLAYS",
                            title = "Allow Bastion to draw over apps.",
                            body = "Bastion needs overlay permission for block screens and inline masks inside supported apps like Instagram and YouTube.",
                            footnote = "Grant the permission, then return here.",
                            ctaLabel = "Open Overlay Permission",
                            ctaAction = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        )

                        4 -> OnboardingCard(
                            eyebrow = "ACCESSIBILITY",
                            title = "Turn on the core blocker engine.",
                            body = "Accessibility lets Bastion understand app structure, like when Instagram is on Reels instead of DMs.",
                            footnote = "Android will show a warning. That's normal for any app using Accessibility.",
                            ctaLabel = "Open Accessibility Settings",
                            ctaAction = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        )

                        5 -> OnboardingSplitCard(
                            eyebrow = "BATTERY",
                            title = "Reduce the chance your phone kills Bastion.",
                            body = "Some phones aggressively shut down background apps. Battery exemption makes the blocker far more reliable.",
                            footnote = "Especially important on Samsung, Xiaomi, and similar Android skins.",
                            primaryLabel = "Exempt Battery Optimization",
                            primaryAction = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                                viewModel.advance()
                            },
                            secondaryLabel = "Skip For Now",
                            secondaryAction = viewModel::advance
                        )

                        6 -> OnboardingSplitCard(
                            eyebrow = "EXTRA PROTECTION",
                            title = "Add device-admin backup protection.",
                            body = "This helps Bastion resist easy shutdown paths when you are tempted to switch it off under pressure.",
                            footnote = "Recommended before relying on hardcore locks.",
                            primaryLabel = "Enable Extra Protection",
                            primaryAction = {
                                deviceAdminLauncher.launch(viewModel.buildDeviceAdminIntent())
                            },
                            secondaryLabel = "Skip For Now",
                            secondaryAction = viewModel::advance
                        )

                        else -> OnboardingCard(
                            eyebrow = "READY",
                            title = "Your blocker is armed.",
                            body = "The next step is simple: pick one app, decide whether to lock the full app or selected features, and set a duration.",
                            footnote = "Start small and make the first setup one you know you'll actually keep.",
                            ctaLabel = "Open Bastion",
                            ctaAction = {
                                viewModel.markOnboardingDone()
                                onFinished()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyDisclaimerStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF131A16))
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "LOCAL",
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.AccentAmber
        )
        Text(
            text = "Your app rules, usage state, and blocker decisions stay on this phone. Bastion does not send them to any server.",
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextSecondary
        )
    }
}

@Composable
private fun OnboardingHeader(step: Int, totalSteps: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SETUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = BastionColors.TextMuted
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Bastion onboarding",
                    style = MaterialTheme.typography.titleLarge,
                    color = BastionColors.TextPrimary
                )
            }
            Text(
                text = "${step + 1} / $totalSteps",
                style = MaterialTheme.typography.titleMedium,
                color = BastionColors.AccentAmber
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(
                            if (index <= step) BastionColors.AccentAmber
                            else BastionColors.BorderSubtle
                        )
                )
            }
        }
    }
}

@Composable
private fun OnboardingCard(
    eyebrow: String,
    title: String,
    body: String,
    footnote: String,
    ctaLabel: String,
    ctaAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(30.dp))
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.AccentAmber
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = BastionColors.TextPrimary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = BastionColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = footnote,
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextMuted
        )
        Spacer(Modifier.height(28.dp))
        OnboardingPrimaryButton(label = ctaLabel, onClick = ctaAction)
    }
}

@Composable
private fun OnboardingSplitCard(
    eyebrow: String,
    title: String,
    body: String,
    footnote: String,
    primaryLabel: String,
    primaryAction: () -> Unit,
    secondaryLabel: String,
    secondaryAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(30.dp))
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.AccentAmber
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = BastionColors.TextPrimary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = BastionColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = footnote,
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextMuted
        )
        Spacer(Modifier.height(28.dp))
        OnboardingPrimaryButton(label = primaryLabel, onClick = primaryAction)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BastionColors.SurfaceElevated)
                .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(16.dp))
                .clickable(onClick = secondaryAction),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.titleMedium,
                color = BastionColors.TextSecondary
            )
        }
    }
}

@Composable
private fun OnboardingPrimaryButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BastionColors.AccentAmber)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF18130A),
            textAlign = TextAlign.Center
        )
    }
}
