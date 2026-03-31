package com.aman.bastion.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOptionsSheet(
    app: AppListItem,
    onDismiss: () -> Unit,
    onBlock: (String) -> Unit,
    onHardcoreBlock: (String, Long) -> Unit,
    onUnblock: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(app.appName, style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(24.dp))

            when {
                app.isHardcoreActive -> {
                    val remaining = app.hardcoreUntilMs - System.currentTimeMillis()
                    val hours = remaining / 3_600_000
                    val minutes = (remaining % 3_600_000) / 60_000
                    val label = if (hours > 0) "${hours}h ${minutes}m remaining"
                               else "${minutes}m remaining"
                    Text(
                        text = "Hardcore block active — $label",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                app.isBlocked -> {
                    OutlinedButton(
                        onClick = { onUnblock(app.packageName); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Unblock") }
                    Spacer(Modifier.height(16.dp))
                    HardcoreSectionLabel()
                    Spacer(Modifier.height(8.dp))
                    HardcoreDurationRow(app.packageName, onHardcoreBlock, onDismiss)
                }

                else -> {
                    Button(
                        onClick = { onBlock(app.packageName); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Block") }
                    Spacer(Modifier.height(16.dp))
                    HardcoreSectionLabel()
                    Spacer(Modifier.height(8.dp))
                    HardcoreDurationRow(app.packageName, onHardcoreBlock, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun HardcoreSectionLabel() {
    Text(
        text = "Hardcore Block (cannot be undone until timer expires)",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HardcoreDurationRow(
    packageName: String,
    onHardcoreBlock: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "30m" to 30 * 60_000L,
        "1h"  to 60 * 60_000L,
        "2h"  to 2 * 60 * 60_000L,
        "4h"  to 4 * 60 * 60_000L,
        "8h"  to 8 * 60 * 60_000L
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, ms) ->
            InputChip(
                selected = false,
                onClick = { onHardcoreBlock(packageName, ms); onDismiss() },
                label = { Text(label) }
            )
        }
    }
}
