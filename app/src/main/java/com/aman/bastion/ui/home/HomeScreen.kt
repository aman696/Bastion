package com.aman.bastion.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.selectedApp?.let { app ->
        AppOptionsSheet(
            app = app,
            onDismiss = viewModel::onDismissSheet,
            onBlock = viewModel::onBlock,
            onHardcoreBlock = viewModel::onHardcoreBlock,
            onUnblock = viewModel::onUnblock
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bastion") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search apps…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.apps.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No apps match", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    LazyColumn {
                        items(uiState.apps, key = { it.packageName }) { app ->
                            AppRow(app = app, onClick = { viewModel.onAppSelected(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppListItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(app.appName) },
        leadingContent = {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = {
            when {
                app.isHardcoreActive -> {
                    val remaining = app.hardcoreUntilMs - System.currentTimeMillis()
                    val minutes = remaining / 60_000
                    AssistChip(
                        onClick = {},
                        label = { Text("${minutes}m") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
                app.isBlocked -> {
                    AssistChip(
                        onClick = {},
                        label = { Text("Blocked") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
                else -> {}
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
