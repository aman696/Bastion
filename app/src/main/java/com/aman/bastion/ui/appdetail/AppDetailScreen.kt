package com.aman.bastion.ui.appdetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aman.bastion.ui.theme.BastionColors
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AppDetailScreen(
    packageName: String,
    onNavigateUp: () -> Unit,
    onHardcoreActivated: (String) -> Unit,
    viewModel: AppDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onAdminPromptHandled() }

    if (
        state.currentRule?.isHardcoreActive == true &&
        state.selectedScope == ProtectionScopeSelection.FULL_APP &&
        state.pendingPostActivationMode == null &&
        !state.requiresAdminActivation
    ) {
        onHardcoreActivated(packageName)
        return
    }

    LaunchedEffect(state.requiresAdminActivation) {
        if (state.requiresAdminActivation) {
            adminLauncher.launch(viewModel.buildAdminActivationIntent())
        }
    }

    LaunchedEffect(state.pendingPostActivationMode, state.requiresAdminActivation) {
        if (state.pendingPostActivationMode == null || state.requiresAdminActivation) return@LaunchedEffect

        when (state.pendingPostActivationMode) {
            null -> Unit
            BlockModeSelection.HARDCORE -> {
                if (state.selectedScope == ProtectionScopeSelection.FULL_APP) {
                    onHardcoreActivated(packageName)
                } else {
                    onNavigateUp()
                }
            }
        }
        viewModel.consumePostActivation()
    }

    Scaffold(containerColor = BastionColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                Text(
                    text = "<-",
                    style = MaterialTheme.typography.titleLarge,
                    color = BastionColors.TextPrimary,
                    modifier = Modifier
                        .clickable(onClick = onNavigateUp)
                        .padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.icon?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = state.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = BastionColors.TextPrimary
                    )
                    Text(
                        text = state.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = BastionColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            StatusBanner(
                rule = state.currentRule,
                hasFeatureRestrictions = state.hasFeatureRestrictions,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(24.dp))

            if (state.featureRows.isNotEmpty()) {
                SectionLabel("WHAT TO BLOCK", Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ScopeCard(
                        title = "Full App",
                        body = "Kick you out of the whole app.",
                        selected = state.selectedScope == ProtectionScopeSelection.FULL_APP,
                        onSelect = { viewModel.onSelectScope(ProtectionScopeSelection.FULL_APP) },
                        modifier = Modifier.weight(1f)
                    )
                    ScopeCard(
                        title = "Selected Features",
                        body = "Only block chosen sections.",
                        selected = state.selectedScope == ProtectionScopeSelection.SELECTED_FEATURES,
                        onSelect = { viewModel.onSelectScope(ProtectionScopeSelection.SELECTED_FEATURES) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            if (state.selectedScope == ProtectionScopeSelection.SELECTED_FEATURES) {
                SectionLabel("FEATURE RULES", Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                state.featureRows.forEach { row ->
                    FeatureRuleRow(
                        row = row,
                        onToggle = { viewModel.onToggleFeatureRule(row) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Choose the sections here, then set the hardcore lock duration below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BastionColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            SectionLabel("BLOCK MODE", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HardcoreCard(
                    selected = true,
                    selectedDurationMs = state.selectedDurationMs,
                    onSelect = { viewModel.onSelectMode(BlockModeSelection.HARDCORE) },
                    onSelectDuration = viewModel::onSelectDuration,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))
            BlockNoteField(
                value = state.blockNote,
                onChange = viewModel::onBlockNoteChange,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            ActivateButton(
                state = state,
                onActivate = viewModel::onActivateBlock,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )

            val currentRule = state.currentRule
            if ((currentRule != null && !currentRule.isHardcoreActive) || state.hasFeatureRestrictions) {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Remove restriction",
                        style = MaterialTheme.typography.bodySmall,
                        color = BastionColors.TextSecondary,
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

@Composable
private fun StatusBanner(
    rule: AppRule?,
    hasFeatureRestrictions: Boolean,
    modifier: Modifier = Modifier
) {
    val (bg, text, fg) = when {
        rule == null && !hasFeatureRestrictions -> Triple(
            BastionColors.SurfaceElevated,
            "This app is unprotected",
            BastionColors.TextSecondary
        )

        rule?.isHardcoreActive == true -> Triple(
            BastionColors.HardcoreCardBg,
            "Hardcore lock is active",
            BastionColors.AccentDanger
        )

        hasFeatureRestrictions && rule == null -> Triple(
            Color(0xFF0A200A),
            "Selected features are restricted",
            BastionColors.AccentSuccess
        )

        else -> Triple(
            Color(0xFF0A200A),
            "Restriction is active",
            BastionColors.AccentSuccess
        )
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
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = BastionColors.TextSecondary,
        modifier = modifier
    )
}

@Composable
private fun FeatureRuleRow(row: FeatureRowState, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
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
            checked = row.isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = BastionColors.Background,
                checkedTrackColor = BastionColors.AccentAmber,
                uncheckedThumbColor = BastionColors.TextSecondary,
                uncheckedTrackColor = BastionColors.SurfaceElevated
            )
        )
    }
}

@Composable
private fun ScopeCard(
    title: String,
    body: String,
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = BastionColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
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
    var hoursText by remember(selectedDurationMs) {
        mutableStateOf((selectedDurationMs / 3_600_000L).toString())
    }
    var minutesText by remember(selectedDurationMs) {
        mutableStateOf(((selectedDurationMs % 3_600_000L) / 60_000L).toString())
    }

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
            "Locked for the time you set. Cannot be removed early.",
            style = MaterialTheme.typography.bodySmall,
            color = BastionColors.TextSecondary
        )
        if (selected) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DurationInputField(
                    label = "Hours",
                    value = hoursText,
                    onValueChange = { next ->
                        hoursText = next.filter(Char::isDigit).take(3)
                        val hours = hoursText.toLongOrNull() ?: 0L
                        val minutes = (minutesText.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
                        onSelectDuration(((hours * 60L) + minutes) * 60_000L)
                    },
                    modifier = Modifier.weight(1f)
                )
                DurationInputField(
                    label = "Minutes",
                    value = minutesText,
                    onValueChange = { next ->
                        minutesText = next.filter(Char::isDigit).take(2)
                        val hours = hoursText.toLongOrNull() ?: 0L
                        val minutes = (minutesText.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
                        onSelectDuration(((hours * 60L) + minutes) * 60_000L)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DurationInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = BastionColors.TextPrimary),
            cursorBrush = SolidColor(BastionColors.AccentDanger),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BastionColors.BadgeLockedBg)
                        .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BastionColors.TextMuted
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun BlockNoteField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = BastionColors.TextPrimary),
        cursorBrush = SolidColor(BastionColors.AccentDanger),
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
    val enabled =
        (state.selectedScope != ProtectionScopeSelection.SELECTED_FEATURES || state.hasFeatureRestrictions) &&
            state.selectedDurationMs > 0L

    val (bg, fg, label) = when {
        !enabled -> Triple(
            BastionColors.SurfaceElevated,
            BastionColors.TextMuted,
            "Set a lock duration"
        )

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
        if (enabled) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "This cannot be undone until the timer expires.",
                style = MaterialTheme.typography.bodySmall,
                color = BastionColors.AccentDanger,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
