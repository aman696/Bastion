package com.aman.bastion.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aman.bastion.ui.theme.BastionColors
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onAppTapped: (packageName: String, isHardcoreActive: Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = BastionColors.Background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                ProtectionStatusCard(
                    status = state.protectionStatus,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            if (state.ruledApps.isNotEmpty()) {
                item {
                    Text(
                        text = "YOUR APPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = BastionColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            items(state.ruledApps, key = { it.packageName }) { app ->
                AppRow(
                    app     = app,
                    onClick = { onAppTapped(app.packageName, app.isHardcoreActive) }
                )
            }

            item {
                AddProtectionRow(
                    expanded      = state.addPanelExpanded,
                    searchQuery   = state.addSearchQuery,
                    filteredApps  = state.filteredAllApps,
                    onToggle      = viewModel::onAddPanelToggle,
                    onQueryChange = viewModel::onAddSearchQuery,
                    onAppTapped   = { pkg -> onAppTapped(pkg, false) }
                )
            }
        }
    }
}

// ── Protection Status Card ────────────────────────────────────────────────────

@Composable
private fun ProtectionStatusCard(status: ProtectionStatus, modifier: Modifier = Modifier) {
    when (status) {
        is ProtectionStatus.None           -> NoneStatusCard(modifier)
        is ProtectionStatus.Active         -> ActiveStatusCard(status, modifier)
        is ProtectionStatus.HardcoreActive -> HardcoreStatusCard(status, modifier)
    }
}

@Composable
private fun NoneStatusCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(24.dp)
    ) {
        Column {
            Text(
                text  = "No active restrictions",
                style = MaterialTheme.typography.titleMedium,
                color = BastionColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "You're unprotected right now.",
                style = MaterialTheme.typography.bodySmall,
                color = BastionColors.TextMuted
            )
        }
    }
}

@Composable
private fun ActiveStatusCard(status: ProtectionStatus.Active, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = BastionColors.AccentSuccess, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text  = "Bastion is protecting you",
                style = MaterialTheme.typography.titleMedium,
                color = BastionColors.TextPrimary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "${status.blockCount} active restriction${if (status.blockCount != 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(status.chips) { (appName, label) ->
                ActiveBlockChip(appName = appName, label = label)
            }
        }
    }
}

@Composable
private fun ActiveBlockChip(appName: String, label: String) {
    Text(
        text  = "$appName — $label",
        style = MaterialTheme.typography.labelSmall,
        color = BastionColors.AccentAmber,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(BastionColors.BadgeBlockedBg)
            .border(1.dp, BastionColors.AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun HardcoreStatusCard(status: ProtectionStatus.HardcoreActive, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label         = "borderAlpha"
    )

    var remaining by remember { mutableLongStateOf(status.untilMs - System.currentTimeMillis()) }
    LaunchedEffect(status.untilMs) {
        while (remaining > 0) {
            delay(1000L)
            remaining = status.untilMs - System.currentTimeMillis()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BastionColors.HardcoreCardBg)
            .border(1.dp, BastionColors.AccentDanger.copy(alpha = borderAlpha), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column {
            Text(
                text  = "HARDCORE LOCK ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = BastionColors.AccentDanger
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap             = status.appIcon,
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text  = status.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = BastionColors.TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = formatCountdown(remaining),
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 28.sp),
                        color = BastionColors.AccentDanger
                    )
                }
            }
        }
    }
}

// ── App Row ───────────────────────────────────────────────────────────────────

@Composable
private fun AppRow(app: AppListItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap             = app.icon,
            contentDescription = null,
            modifier           = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = app.appName,
                style    = MaterialTheme.typography.bodyLarge,
                color    = BastionColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text     = app.packageName,
                style    = MaterialTheme.typography.bodySmall,
                color    = BastionColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        AppStatusBadge(app = app)
    }
}

@Composable
private fun AppStatusBadge(app: AppListItem) {
    when {
        app.isHardcoreActive -> {
            var remaining by remember { mutableLongStateOf(app.hardcoreUntilMs - System.currentTimeMillis()) }
            LaunchedEffect(app.hardcoreUntilMs) {
                while (remaining > 0) { delay(1000L); remaining = app.hardcoreUntilMs - System.currentTimeMillis() }
            }
            val h = remaining / 3_600_000
            val m = (remaining % 3_600_000) / 60_000
            val label = if (h > 0) "LOCKED ${h}h ${m}m" else "LOCKED ${m}m"
            StatusPill(text = label, bg = BastionColors.BadgeLockedBg, fg = BastionColors.AccentDanger)
        }
        app.activeInAppRuleCount > 1 -> {
            StatusPill(
                text = "${app.activeInAppRuleCount} RULES",
                bg   = BastionColors.BadgeBlockedBg,
                fg   = BastionColors.AccentAmber
            )
        }
        app.firstInAppRuleShortLabel != null -> {
            StatusPill(
                text = "${app.firstInAppRuleShortLabel} BLOCKED",
                bg   = BastionColors.BadgeBlockedBg,
                fg   = BastionColors.AccentAmber
            )
        }
        app.isBlocked -> {
            StatusPill(text = "BLOCKED", bg = BastionColors.BadgeBlockedBg, fg = BastionColors.AccentAmber)
        }
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ── Add Protection Row ────────────────────────────────────────────────────────

@Composable
private fun AddProtectionRow(
    expanded: Boolean,
    searchQuery: String,
    filteredApps: List<AppListItem>,
    onToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAppTapped: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = if (expanded) "− Hide apps" else "+ Add protection",
                style = MaterialTheme.typography.bodyMedium,
                color = BastionColors.AccentAmber
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            Column {
                BasicTextField(
                    value         = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine    = true,
                    textStyle     = MaterialTheme.typography.bodyMedium.copy(color = BastionColors.TextPrimary),
                    cursorBrush   = SolidColor(BastionColors.AccentAmber),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BastionColors.SurfaceElevated)
                                .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search apps…",
                                    color = BastionColors.TextMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                filteredApps.forEach { app ->
                    AppRow(app = app, onClick = { onAppTapped(app.packageName) })
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatCountdown(ms: Long): String {
    val total = maxOf(ms, 0L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
