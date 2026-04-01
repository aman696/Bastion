package com.aman.bastion.ui.appdetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aman.bastion.domain.model.AppRule
import com.aman.bastion.ui.theme.BastionColors

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
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TopBar(onNavigateUp = onNavigateUp)
            AppHeroCard(state = state)
            StatusBanner(rule = state.currentRule, hasFeatureRestrictions = state.hasFeatureRestrictions)

            if (state.featureRows.isNotEmpty()) {
                SectionCard(
                    title = "1. Choose scope",
                    body = "Pick a full lock or keep the app open and block only the distracting surfaces."
                ) {
                    BoxWithConstraints {
                        val stacked = maxWidth < 380.dp
                        if (stacked) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ScopeCard(
                                    title = "Full App",
                                    body = "Launch the app and Bastion pushes you out entirely.",
                                    selected = state.selectedScope == ProtectionScopeSelection.FULL_APP,
                                    onSelect = { viewModel.onSelectScope(ProtectionScopeSelection.FULL_APP) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                ScopeCard(
                                    title = "Selected Features",
                                    body = "Leave the app available but lock the distracting surfaces.",
                                    selected = state.selectedScope == ProtectionScopeSelection.SELECTED_FEATURES,
                                    onSelect = { viewModel.onSelectScope(ProtectionScopeSelection.SELECTED_FEATURES) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ScopeCard(
                                    title = "Full App",
                                    body = "Launch the app and Bastion pushes you out entirely.",
                                    selected = state.selectedScope == ProtectionScopeSelection.FULL_APP,
                                    onSelect = { viewModel.onSelectScope(ProtectionScopeSelection.FULL_APP) },
                                    modifier = Modifier.weight(1f)
                                )
                                ScopeCard(
                                    title = "Selected Features",
                                    body = "Leave the app available but lock the distracting surfaces.",
                                    selected = state.selectedScope == ProtectionScopeSelection.SELECTED_FEATURES,
                                    onSelect = { viewModel.onSelectScope(ProtectionScopeSelection.SELECTED_FEATURES) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (state.selectedScope == ProtectionScopeSelection.SELECTED_FEATURES) {
                val featureSectionTitle = if (state.featureRows.size == 1) {
                    "2. Instagram guard"
                } else {
                    "2. Feature rules"
                }
                val featureSectionBody = if (state.featureRows.size == 1) {
                    "Use one focused Instagram rule instead of juggling separate toggles."
                } else {
                    "Turn on only the surfaces you want Bastion to intercept."
                }
                SectionCard(
                    title = featureSectionTitle,
                    body = featureSectionBody
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.featureRows.forEach { row ->
                            FeatureRuleRow(
                                row = row,
                                onToggle = { viewModel.onToggleFeatureRule(row) }
                            )
                        }
                    }
                }
            }

            SectionCard(
                title = if (
                    state.featureRows.isNotEmpty() &&
                    state.selectedScope == ProtectionScopeSelection.SELECTED_FEATURES
                ) {
                    "3. Set duration"
                } else {
                    "2. Set duration"
                },
                body = "Choose a concrete time anchor. Once it starts, there is no early undo."
            ) {
                HardcoreCard(
                    selected = true,
                    selectedDurationMs = state.selectedDurationMs,
                    onSelect = { viewModel.onSelectMode(BlockModeSelection.HARDCORE) },
                    onSelectDuration = viewModel::onSelectDuration,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionCard(
                title = if (
                    state.featureRows.isNotEmpty() &&
                    state.selectedScope == ProtectionScopeSelection.SELECTED_FEATURES
                ) {
                    "4. Intent note"
                } else {
                    "3. Intent note"
                },
                body = "Optional. One sentence can make the lock feel less abstract when impulse hits."
            ) {
                BlockNoteField(
                    value = state.blockNote,
                    onChange = viewModel::onBlockNoteChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ActivateButton(
                state = state,
                onActivate = viewModel::onActivateBlock,
                modifier = Modifier.fillMaxWidth()
            )

            val currentRule = state.currentRule
            if ((currentRule != null && !currentRule.isHardcoreActive) || state.hasFeatureRestrictions) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Remove restriction",
                        style = MaterialTheme.typography.bodySmall,
                        color = BastionColors.AccentDanger,
                        modifier = Modifier.clickable {
                            viewModel.onDeactivateBlock()
                            onNavigateUp()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onNavigateUp: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Back",
            style = MaterialTheme.typography.bodyMedium,
            color = BastionColors.AccentAmber,
            modifier = Modifier.clickable(onClick = onNavigateUp)
        )
        Text(
            text = "Lock setup",
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.TextMuted
        )
    }
}

@Composable
private fun AppHeroCard(state: AppDetailUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(28.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            state.icon?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.appName,
                    style = MaterialTheme.typography.displayMedium,
                    color = BastionColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = BastionColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (state.featureRows.isEmpty()) {
                "This app currently supports a full hardcore lock."
            } else {
                "Keep the setup simple: choose full lock or block only the surfaces that derail you."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = BastionColors.TextSecondary
        )
    }
}

@Composable
private fun StatusBanner(
    rule: AppRule?,
    hasFeatureRestrictions: Boolean
) {
    val (background, title, body, accent) = when {
        rule == null && !hasFeatureRestrictions -> Quadruple(
            BastionColors.Surface,
            "Unprotected",
            "No active restriction is set for this app yet.",
            BastionColors.TextSecondary
        )

        rule?.isHardcoreActive == true -> Quadruple(
            BastionColors.HardcoreCardBg,
            "Hardcore lock active",
            "This app is already under a timed lock.",
            BastionColors.AccentDanger
        )

        hasFeatureRestrictions && rule == null -> Quadruple(
            Color(0xFF152219),
            "Feature rules active",
            "Selected sections inside this app are currently restricted.",
            BastionColors.AccentSuccess
        )

        else -> Quadruple(
            Color(0xFF152219),
            "Restriction active",
            "Bastion is already protecting this app.",
            BastionColors.AccentSuccess
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = BastionColors.TextSecondary
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BastionColors.Surface)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            Column {
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
            content()
        }
    )
}

@Composable
private fun FeatureRuleRow(
    row: FeatureRowState,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BastionColors.SurfaceElevated)
            .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.feature.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = BastionColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = BastionColors.TextSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = row.isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF17130B),
                checkedTrackColor = BastionColors.AccentAmber,
                uncheckedThumbColor = BastionColors.TextSecondary,
                uncheckedTrackColor = BastionColors.BorderSubtle
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
    val background = if (selected) Color(0xFF241E12) else BastionColors.SurfaceElevated

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = BastionColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
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
    val presets = listOf(
        30L * 60_000L,
        60L * 60_000L,
        2L * 60L * 60_000L,
        4L * 60L * 60_000L,
        8L * 60L * 60_000L
    )
    var hoursText by remember(selectedDurationMs) {
        mutableStateOf((selectedDurationMs / 3_600_000L).toString())
    }
    var minutesText by remember(selectedDurationMs) {
        mutableStateOf(((selectedDurationMs % 3_600_000L) / 60_000L).toString())
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Quick picks",
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.TextMuted
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { duration ->
                DurationPresetChip(
                    label = formatDuration(duration),
                    selected = selectedDurationMs == duration,
                    onClick = {
                        onSelect()
                        onSelectDuration(duration)
                    }
                )
            }
        }
        Text(
            text = "Custom duration",
            style = MaterialTheme.typography.labelSmall,
            color = BastionColors.TextMuted
        )
        BoxWithConstraints {
            val stacked = maxWidth < 360.dp
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DurationInputField(
                        label = "Hours",
                        value = hoursText,
                        onValueChange = { next ->
                            onSelect()
                            hoursText = next.filter(Char::isDigit).take(3)
                            val hours = hoursText.toLongOrNull() ?: 0L
                            val minutes = (minutesText.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
                            onSelectDuration(((hours * 60L) + minutes) * 60_000L)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DurationInputField(
                        label = "Minutes",
                        value = minutesText,
                        onValueChange = { next ->
                            onSelect()
                            minutesText = next.filter(Char::isDigit).take(2)
                            val hours = hoursText.toLongOrNull() ?: 0L
                            val minutes = (minutesText.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
                            onSelectDuration(((hours * 60L) + minutes) * 60_000L)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DurationInputField(
                        label = "Hours",
                        value = hoursText,
                        onValueChange = { next ->
                            onSelect()
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
                            onSelect()
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
        Text(
            text = "Selected lock: ${formatDuration(selectedDurationMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) BastionColors.AccentDanger else BastionColors.TextSecondary
        )
    }
}

@Composable
private fun DurationPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) BastionColors.AccentDanger else BastionColors.BadgeLockedBg
    val foreground = if (selected) Color.White else BastionColors.TextPrimary
    val borderColor = if (selected) BastionColors.AccentDanger else BastionColors.BorderSubtle

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = foreground
        )
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
            color = BastionColors.TextMuted
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(BastionColors.BadgeLockedBg)
                        .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (value.isBlank()) {
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
private fun BlockNoteField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = BastionColors.TextPrimary),
        cursorBrush = SolidColor(BastionColors.AccentDanger),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BastionColors.SurfaceElevated)
                    .border(1.dp, BastionColors.BorderSubtle, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (value.isBlank()) {
                    Text(
                        text = "Example: no doomscrolling before sleep",
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
private fun ActivateButton(
    state: AppDetailUiState,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled =
        (state.selectedScope != ProtectionScopeSelection.SELECTED_FEATURES || state.hasFeatureRestrictions) &&
            state.selectedDurationMs > 0L

    val background = if (enabled) BastionColors.AccentDanger else BastionColors.SurfaceElevated
    val foreground = if (enabled) Color.White else BastionColors.TextMuted
    val label = if (enabled) {
        "Lock for ${formatDuration(state.selectedDurationMs)}"
    } else {
        "Set a duration first"
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .clickable(enabled = enabled, onClick = onActivate),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = foreground
            )
        }
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This cannot be undone before the timer ends.",
                style = MaterialTheme.typography.bodySmall,
                color = BastionColors.AccentDanger
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val hours = ms / 3_600_000L
    val minutes = (ms % 3_600_000L) / 60_000L
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
