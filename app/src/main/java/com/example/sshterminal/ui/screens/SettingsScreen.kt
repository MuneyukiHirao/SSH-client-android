package com.example.sshterminal.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sshterminal.BuildConfig
import com.example.sshterminal.domain.model.TerminalColorScheme
import com.example.sshterminal.domain.model.TerminalColors
import com.example.sshterminal.domain.model.ThemeMode
import com.example.sshterminal.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection("Terminal") {
                // Font size
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Font Size: ${state.fontSize.toInt()}sp",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = state.fontSize,
                        onValueChange = { viewModel.setFontSize(it) },
                        valueRange = 10f..24f,
                        steps = 13
                    )
                }

                HorizontalDivider()

                // Keep screen on
                SwitchSettingItem(
                    title = "Keep Screen On",
                    description = "Prevent screen from turning off during sessions",
                    checked = state.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) }
                )

                HorizontalDivider()

                // Vibration feedback
                SwitchSettingItem(
                    title = "Vibration Feedback",
                    description = "Vibrate on bell character",
                    checked = state.vibrateFeedback,
                    onCheckedChange = { viewModel.setVibrateFeedback(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection("Appearance") {
                // Theme mode selection
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Terminal color scheme
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Terminal Color Scheme",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(TerminalColorScheme.entries) { scheme ->
                            ColorSchemePreview(
                                scheme = scheme,
                                isSelected = state.terminalColorScheme == scheme,
                                onClick = { viewModel.setTerminalColorScheme(scheme) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection("Connection") {
                // Connection timeout
                ClickableSettingItem(
                    title = "Connection Timeout",
                    description = "${state.connectionTimeout} seconds",
                    onClick = { /* Show dialog */ }
                )

                HorizontalDivider()

                // Keep alive interval
                ClickableSettingItem(
                    title = "Keep Alive Interval",
                    description = "${state.keepAliveInterval} seconds",
                    onClick = { /* Show dialog */ }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection("tmux") {
                // Auto tmux
                SwitchSettingItem(
                    title = "Auto tmux",
                    description = "Automatically attach or create tmux session on connect",
                    checked = state.autoTmux,
                    onCheckedChange = { viewModel.setAutoTmux(it) }
                )

                HorizontalDivider()

                // tmux session name
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Session Name",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Name for tmux session (attach existing or create new)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var sessionName by remember { mutableStateOf(state.tmuxSessionName) }

                    OutlinedTextField(
                        value = sessionName,
                        onValueChange = {
                            sessionName = it
                            viewModel.setTmuxSessionName(it)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.autoTmux
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection("About") {
                ClickableSettingItem(
                    title = "Version",
                    description = BuildConfig.VERSION_NAME,
                    onClick = { }
                )

                HorizontalDivider()

                ClickableSettingItem(
                    title = "Licenses",
                    description = "Open source licenses",
                    onClick = { /* Show licenses */ }
                )

                HorizontalDivider()

                ClickableSettingItem(
                    title = "Privacy Policy",
                    description = "",
                    onClick = { /* Open privacy policy */ }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ColorSchemePreview(
    scheme: TerminalColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = TerminalColors.fromScheme(scheme)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp, 50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(colors.background))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(6.dp)
        ) {
            Column {
                // Sample text preview
                Text(
                    text = "$ ls",
                    color = Color(colors.foreground),
                    style = MaterialTheme.typography.labelSmall
                )
                Row {
                    Text(
                        text = "src",
                        color = Color(colors.blue),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "main",
                        color = Color(colors.green),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = scheme.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ClickableSettingItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
