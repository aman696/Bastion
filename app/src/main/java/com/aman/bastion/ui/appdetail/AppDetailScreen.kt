package com.aman.bastion.ui.appdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aman.bastion.domain.model.AppRule
import com.aman.bastion.domain.model.UnlockCondition
import com.aman.bastion.ui.theme.BastionColors

@Composable
fun AppDetailScreen(
    packageName: String,
    onNavigateUp: () -> Unit,
    onHardcoreActivated: (String) -> Unit,
    viewModel: AppDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // If a hardcore lock is active, navigate away to the lock screen
    if (state.currentRule?.isHardcoreActive == true) {
        onHardcoreActivated(packageName)
        return
    }

    Scaffold(containerColor = BastionColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Back nav
            Box(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                Text(
                    text     = "←",
                    style    = MaterialTheme.typography.titleLarge,
                    color    = BastionColors.TextPrimary,
                    modifier = Modifier
                        .clickable(onClick = onNavigateUp)
                        .padding(12.dp)
                )
            }

            // App header
            Row(
                modifier          = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.icon?.let { bmp ->
                    Image(
                        bitmap             = bmp,
                        contentDescription = null,
                        modifier           = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text  = state.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = BastionColors.TextPrimary
                    )
                    Text(
                        text     = state.packageName,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = BastionColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Status banner
            StatusBanner(
                rule     = state.currentRule,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Feature rules section (only for known apps)
            if (state.featureRows.isNotEmpty()) {
                SectionLabel("FEATURE RULES", Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                state.featureRows.forEach { row ->
                    FeatureRuleRow(
                        row      = row,
                        onToggle = { viewModel.onToggleFeatureRule(row) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            // Block mode cards
            SectionLabel("BLOCK MODE", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier              = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SoftBlockCard(
                    selected = state.selectedMode == BlockModeSelection.SOFT,
                    onSelect = { viewModel.onSelectMode(BlockModeSelection.SOFT) },
                    modifier = Modifier.weight(1f)
                )
                HardcoreCard(
                    selected           = state.selectedMode == BlockModeSelection.HARDCORE,
                    selectedDurationMs = state.selectedDurationMs,
                    onSelect           = { viewModel.onSelectMode(BlockModeSelection.HARDCORE) },
                    onSelectDuration   = viewModel::onSelectDuration,
                    modifier           = Modifier.weight(1f)
                )
            }

            // Unlock condition chips — horizontal scroll, shown only when Soft is selected
            if (state.selectedMode == BlockModeSelection.SOFT) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnlockCondition.entries.forEach { cond ->
                        val active = state.selectedUnlock == cond
                        Text(
                            text     = cond.displayLabel(),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = if (active) BastionColors.Background else BastionColors.AccentAmber,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) BastionColors.AccentAmber else BastionColors.BadgeBlockedBg)
                                .border(1.dp, BastionColors.AccentAmber.copy(alpha = if (active) 1f else 0.4f), RoundedCornerShape(100.dp))
                                .clickable { viewModel.onSelectUnlock(cond) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Block note (hardcore only)
            if (state.selectedMode == BlockModeSelection.HARDCORE) {
                Spacer(Modifier.height(12.dp))
                BlockNoteField(
                    value    = state.blockNote,
                    onChange = viewModel::onBlockNoteChange,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))

            // Activate button
            ActivateButton(
                state      = state,
                onActivate = {
                    viewModel.onActivateBlock()
                    if (state.selectedMode == BlockModeSelection.HARDCORE) {
                        onHardcoreActivated(packageName)
                    } else {
                        onNavigateUp()
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )

            // Deactivate link (only when a non-hardcore rule is active)
            val currentRule = state.currentRule
            if (currentRule != null && !currentRule.isHardcoreActive) {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text     = "Remove restriction",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = BastionColors.TextSecondary,
                        modifier = Modifier.clickable {
                            viewModel.onDeactivateBlock()
                            onNavigateUp()
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(rule: AppRule?, modifier: Modifier = Modifier) {
    val (bg, text, fg) = when {
        rule == null          -> Triple(BastionColors.SurfaceElevated, "This app is unprotected", BastionColors.TextSecondary)
        rule.isHardcoreActive -> Triple(BastionColors.HardcoreCardBg, "Hardcore lock is active", BastionColors.AccentDanger)
        else                  -> Triple(Color(0xFF0A200A), "Restriction is active", BastionColors.AccentSuccess)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

@Composable
private fun SectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text     = label,
        style    = MaterialTheme.typography.labelSmall,
        color    = BastionColors.TextSecondary,
        modifier = modifier
    )
}

@Composable
private fun FeatureRuleRow(row: FeatureRowState, onToggle: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.feature.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = BastionColors.TextPrimary
            )
            Text(
                row.feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = BastionColors.TextSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked         = row.isEnabled,
            onCheckedChange = { onToggle() },
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = BastionColors.Background,
                checkedTrackColor   = BastionColors.AccentAmber,
                uncheckedThumbColor = BastionColors.TextSecondary,
                uncheckedTrackColor = BastionColors.SurfaceElevated
            )
        )
    }
}

@Composable
private fun SoftBlockCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) BastionColors.AccentAmber else BastionColors.BorderSubtle
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BastionColors.SurfaceElevated)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .background(BastionColors.AccentAmber, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text("Soft Block", style = MaterialTheme.typography.titleMedium, color = BastionColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Blocked until your unlock condition is met.",
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextSecondary
        )
    }
}

@Composable
private fun HardcoreCard(
    selected: Boolean,
    selectedDurationMs: Long,
    onSelect: () -> Unit,
    onSelectDuration: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) BastionColors.AccentDanger else BastionColors.BorderSubtle
    val durations   = listOf(
        "30m" to 30 * 60_000L,
        "1h"  to 3_600_000L,
        "2h"  to 7_200_000L,
        "4h"  to 14_400_000L,
        "8h"  to 28_800_000L
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BastionColors.HardcoreCardBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .background(BastionColors.AccentDanger, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text("Hardcore", style = MaterialTheme.typography.titleMedium, color = BastionColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Locked for a set time. Cannot be removed early.",
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextSecondary
        )
        if (selected) {
            Spacer(Modifier.height(12.dp))
            durations.forEach { (label, ms) ->
                val active = selectedDurationMs == ms
                Text(
                    text     = label,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = if (active) Color.White else BastionColors.AccentDanger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (active) BastionColors.AccentDanger else BastionColors.BadgeLockedBg)
                        .clickable { onSelectDuration(ms) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun BlockNoteField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value         = value,
        onValueChange = onChange,
        textStyle     = MaterialTheme.typography.bodyMedium.copy(color = BastionColors.TextPrimary),
        cursorBrush   = SolidColor(BastionColors.AccentDanger),
        decorationBox = { inner ->
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BastionColors.SurfaceElevated)
                    .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        "Why are you blocking this? (optional)",
                        color = BastionColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun ActivateButton(
    state: AppDetailUiState,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = state.selectedMode != BlockModeSelection.NONE &&
        (state.selectedMode != BlockModeSelection.SOFT || state.selectedUnlock != null)

    val (bg, fg, label) = when {
        !enabled                              -> Triple(BastionColors.SurfaceElevated, BastionColors.TextMuted, "Choose a block mode")
        state.selectedMode == BlockModeSelection.SOFT ->
            Triple(BastionColors.AccentAmber, Color.Black, "Activate Soft Block")
        else -> Triple(
            BastionColors.AccentDanger,
            Color.White,
            "Lock for ${formatDuration(state.selectedDurationMs)}"
        )
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .clickable(enabled = enabled, onClick = onActivate),
            contentAlignment = Alignment.Center
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
        }
        if (state.selectedMode == BlockModeSelection.HARDCORE && enabled) {
            Spacer(Modifier.height(6.dp))
            Text(
                text     = "This cannot be undone until the timer expires.",
                style    = MaterialTheme.typography.bodySmall,
                color    = BastionColors.AccentDanger,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return if (h > 0) "${h}h" else "${m}m"
}
