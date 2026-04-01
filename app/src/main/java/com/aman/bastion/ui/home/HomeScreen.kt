package com.aman.bastion.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
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
    val visibleApps = remember(state.filteredAllApps, state.addSearchQuery) {
        if (state.addSearchQuery.isBlank()) state.filteredAllApps.take(4) else state.filteredAllApps.take(10)
    }

    Scaffold(containerColor = BastionColors.Background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { HomeHeader() }
            item { ProtectionStatusCard(status = state.protectionStatus) }
            item {
                FocusWorkspaceCard(
                    searchQuery = state.addSearchQuery,
                    urlDraft = state.urlDraft,
                    filteredApps = visibleApps,
                    blockedUrls = state.blockedUrls,
                    adultSitesEnabled = state.adultSitesEnabled,
                    onQueryChange = viewModel::onAddSearchQuery,
                    onUrlDraftChange = viewModel::onUrlDraftChange,
                    onAddUrl = viewModel::onAddUrlRule,
                    onToggleAdultSites = viewModel::onToggleAdultSites,
                    onRemoveUrl = viewModel::onRemoveUrlRule,
                    onAppTapped = { pkg -> onAppTapped(pkg, false) }
                )
            }

            if (state.ruledApps.isNotEmpty()) {
                item { SectionHeading("Active rules", "${state.ruledApps.size} protected right now") }
                items(state.ruledApps, key = { it.packageName }) { app ->
                    ProtectedAppCard(
                        app = app,
                        onClick = { onAppTapped(app.packageName, app.isHardcoreActive) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        Text(
            text = "Bastion",
            style = MaterialTheme.typography.displayMedium,
            color = BastionColors.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "See what is protecting you first. Add the next rule second.",
            style = MaterialTheme.typography.bodyLarge,
            color = BastionColors.TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "LOCAL ONLY  Everything stays on this phone.",
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.TextMuted
        )
    }
}

@Composable
private fun ProtectionStatusCard(
    status: ProtectionStatus
) {
    when (status) {
        is ProtectionStatus.None -> EmptyProtectionCard()
        is ProtectionStatus.Active -> ActiveProtectionCard(status)
        is ProtectionStatus.HardcoreActive -> HardcoreProtectionCard(status)
    }
}

@Composable
private fun EmptyProtectionCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "PROTECTION STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.TextMuted
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "No active restrictions",
            style = MaterialTheme.typography.titleMedium,
            color = BastionColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Protect one app or one site first. Small setups are easier to trust and keep.",
            style = MaterialTheme.typography.bodyMedium,
            color = BastionColors.TextSecondary
        )
    }
}

@Composable
private fun ActiveProtectionCard(status: ProtectionStatus.Active) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "PROTECTION STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.TextMuted
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(BastionColors.AccentSuccess)
            )
            Text(
                text = "${status.blockCount} active restriction${if (status.blockCount == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                color = BastionColors.TextPrimary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Bastion is currently covering these apps and surfaces.",
            style = MaterialTheme.typography.bodyMedium,
            color = BastionColors.TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(status.chips) { (appName, label) ->
                StatusChip(text = "$appName  $label")
            }
        }
    }
}

@Composable
private fun HardcoreProtectionCard(status: ProtectionStatus.HardcoreActive) {
    val pulse = rememberInfiniteTransition(label = "hardcorePulse")
    val borderAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    var remaining by remember { mutableLongStateOf(status.untilMs - System.currentTimeMillis()) }
    LaunchedEffect(status.untilMs) {
        while (remaining > 0) {
            delay(1_000)
            remaining = status.untilMs - System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BastionColors.HardcoreCardBg)
            .border(
                1.dp,
                BastionColors.AccentDanger.copy(alpha = borderAlpha),
                RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Text(
            text = "PROTECTION STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.TextMuted
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = status.appIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = status.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = BastionColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatCountdown(remaining),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 30.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = BastionColors.AccentDanger
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Hardcore lock is running. This is your active time anchor.",
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextSecondary
        )
    }
}

@Composable
private fun FocusWorkspaceCard(
    searchQuery: String,
    urlDraft: String,
    filteredApps: List<AppListItem>,
    blockedUrls: List<String>,
    adultSitesEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onUrlDraftChange: (String) -> Unit,
    onAddUrl: () -> Unit,
    onToggleAdultSites: () -> Unit,
    onRemoveUrl: (String) -> Unit,
    onAppTapped: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Protect Next",
            style = MaterialTheme.typography.titleMedium,
            color = BastionColors.TextPrimary
        )
        Text(
            text = "Choose one app or one site. Narrow lists are easier to act on when attention is already stretched.",
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextSecondary
        )
        SearchField(
            value = searchQuery,
            placeholder = "Search apps",
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth()
        )
        if (filteredApps.isEmpty()) {
            Text(
                text = "No apps match that search yet.",
                style = MaterialTheme.typography.bodySmall,
                color = BastionColors.TextMuted
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredApps.forEach { app ->
                    SelectableAppRow(app = app, onClick = { onAppTapped(app.packageName) })
                }
            }
        }
        if (searchQuery.isBlank()) {
            Text(
                text = "Showing a short list by default so the first step stays simple.",
                style = MaterialTheme.typography.bodySmall,
                color = BastionColors.TextMuted
            )
        }

        Text(
            text = "Block URL",
            style = MaterialTheme.typography.titleMedium,
            color = BastionColors.TextPrimary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(BastionColors.SurfaceElevated)
                .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Adult Sites",
                    style = MaterialTheme.typography.titleSmall,
                    color = BastionColors.TextPrimary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Built-in blocklist for mainstream adult, tube, cam, hentai, JAV, and clip-host domains across installed browsers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BastionColors.TextSecondary
                )
            }
            Switch(
                checked = adultSitesEnabled,
                onCheckedChange = { onToggleAdultSites() }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SearchField(
                value = urlDraft,
                placeholder = "example.com or youtube.com/shorts",
                onValueChange = onUrlDraftChange,
                modifier = Modifier.weight(1f)
            )
            PrimaryActionButton(
                label = "Add",
                background = BastionColors.AccentAmber,
                foreground = Color(0xFF18130A),
                onClick = onAddUrl,
                modifier = Modifier.width(84.dp)
            )
        }
        if (blockedUrls.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                blockedUrls.forEach { pattern ->
                    UrlRuleInlineRow(pattern = pattern, onRemove = { onRemoveUrl(pattern) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = BastionColors.TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextMuted
        )
    }
}

@Composable
private fun ProtectedAppCard(
    app: AppListItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleMedium,
                color = BastionColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = BastionColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        AppStatusBadge(app)
    }
}

@Composable
private fun AppStatusBadge(app: AppListItem) {
    when {
        app.isHardcoreActive -> {
            var remaining by remember { mutableLongStateOf(app.hardcoreUntilMs - System.currentTimeMillis()) }
            LaunchedEffect(app.hardcoreUntilMs) {
                while (remaining > 0) {
                    delay(1_000)
                    remaining = app.hardcoreUntilMs - System.currentTimeMillis()
                }
            }
            val hours = remaining / 3_600_000L
            val minutes = (remaining % 3_600_000L) / 60_000L
            val label = if (hours > 0) "LOCKED ${hours}h ${minutes}m" else "LOCKED ${minutes}m"
            StatusChip(
                text = label,
                background = BastionColors.BadgeLockedBg,
                foreground = BastionColors.AccentDanger
            )
        }

        app.activeInAppRuleCount > 1 -> {
            StatusChip(
                text = "${app.activeInAppRuleCount} RULES",
                background = BastionColors.BadgeBlockedBg,
                foreground = BastionColors.AccentAmber
            )
        }

        app.firstInAppRuleShortLabel != null -> {
            StatusChip(
                text = "${app.firstInAppRuleShortLabel} BLOCKED",
                background = BastionColors.BadgeBlockedBg,
                foreground = BastionColors.AccentAmber
            )
        }

        app.isBlocked -> {
            StatusChip(
                text = "BLOCKED",
                background = BastionColors.BadgeBlockedBg,
                foreground = BastionColors.AccentAmber
            )
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    background: Color = Color(0xFF263125),
    foreground: Color = BastionColors.TextPrimary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(background)
            .border(1.dp, foreground.copy(alpha = 0.2f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun SelectableAppRow(
    app: AppListItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BastionColors.SurfaceElevated)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = BastionColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = BastionColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Set up",
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.AccentAmber
        )
    }
}

@Composable
private fun UrlRuleInlineRow(
    pattern: String,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pattern,
            style = MaterialTheme.typography.bodyMedium,
            color = BastionColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Remove",
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.AccentDanger,
            modifier = Modifier.clickable(onClick = onRemove)
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = BastionColors.TextPrimary),
        cursorBrush = SolidColor(BastionColors.AccentAmber),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BastionColors.SurfaceElevated)
                    .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BastionColors.TextMuted
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun PrimaryActionButton(
    label: String,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = foreground
        )
    }
}

private fun formatCountdown(ms: Long): String {
    val total = maxOf(ms, 0L) / 1_000L
    val hours = total / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
