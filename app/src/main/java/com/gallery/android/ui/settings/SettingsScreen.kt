package com.gallery.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader("Appearance")
            SettingsToggleRow(
                icon = Icons.Default.DarkMode,
                title = "Dark Theme",
                subtitle = "Use dark color scheme",
                checked = uiState.isDarkTheme,
                onCheckedChange = viewModel::setDarkTheme,
            )
            SettingsToggleRow(
                icon = Icons.Default.Palette,
                title = "Dynamic Colors",
                subtitle = "Use wallpaper-based colors (Android 12+)",
                checked = uiState.useDynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader("Grid")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Grid columns: ${uiState.gridColumns}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4).forEach { cols ->
                        FilterChip(
                            selected = uiState.gridColumns == cols,
                            onClick = { viewModel.setGridColumns(cols) },
                            label = { Text("$cols") },
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader("About")
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.outline) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.padding(horizontal = 0.dp),
    )
}
