package com.aman.bastion.ui.appdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppDetailScreen(
    packageName: String,
    onNavigateUp: () -> Unit,
    onHardcoreActivated: (String) -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("App Detail — $packageName")
    }
}
