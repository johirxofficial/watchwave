package com.watchwave.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.BuildConfig
import com.watchwave.app.ui.components.InfoBanner
import com.watchwave.app.ui.components.LabeledValue
import com.watchwave.app.ui.components.ResponsiveColumn
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.WaveAmber
import com.watchwave.app.ui.theme.WaveCyan
import kotlinx.coroutines.launch

/**
 * Power-user controls live here so the everyday settings screen stays short.
 */
@Composable
fun AdvancedSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val settings by container.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var driftSmall by remember(settings.driftSmallMs) { mutableStateOf(settings.driftSmallMs.toString()) }
    var driftMedium by remember(settings.driftMediumMs) { mutableStateOf(settings.driftMediumMs.toString()) }
    var driftLarge by remember(settings.driftLargeMs) { mutableStateOf(settings.driftLargeMs.toString()) }
    var autoReconnect by remember(settings.autoReconnect) { mutableStateOf(settings.autoReconnect) }
    var saving by remember { mutableStateOf(false) }

    val driftValid = listOf(driftSmall, driftMedium, driftLarge)
        .all { it.toLongOrNull()?.let { value -> value > 0 } == true }

    ScreenScaffold(
        title = "Advanced settings",
        subtitle = "Synchronisation, reconnects and build details",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenDiagnostics) {
                Icon(Icons.Filled.Sensors, contentDescription = "Diagnostics")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ResponsiveColumn(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InfoBanner(
                    "These controls are for unstable networks and power users. The defaults work well for most rooms.",
                    color = WaveAmber,
                )

                SectionLabel("Synchronisation")
                WaveCard {
                    Text(
                        "Drift thresholds decide when a viewer nudges speed or performs a seek. Lower values are stricter but may cause more visible corrections on unstable networks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = driftSmall,
                            onValueChange = { driftSmall = it.filter(Char::isDigit).take(5) },
                            label = { Text("Speed nudge") },
                            suffix = { Text("ms") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = driftMedium,
                            onValueChange = { driftMedium = it.filter(Char::isDigit).take(5) },
                            label = { Text("Strong nudge") },
                            suffix = { Text("ms") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = driftLarge,
                        onValueChange = { driftLarge = it.filter(Char::isDigit).take(5) },
                        label = { Text("Seek threshold") },
                        suffix = { Text("ms") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!driftValid) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "All thresholds must be positive numbers.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                SectionLabel("Connection")
                WaveCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatic WebSocket reconnect", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Exponential backoff with session-token reuse. Recommended even on stable Wi-Fi.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted,
                            )
                        }
                        Switch(checked = autoReconnect, onCheckedChange = { autoReconnect = it })
                    }
                }

                SectionLabel("Build")
                WaveCard {
                    LabeledValue("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    Spacer(Modifier.height(8.dp))
                    LabeledValue("Application ID", BuildConfig.APPLICATION_ID)
                    Spacer(Modifier.height(8.dp))
                    LabeledValue("Default base domain", BuildConfig.DEFAULT_BASE_DOMAIN.ifBlank { "not configured" })
                    Spacer(Modifier.height(8.dp))
                    LabeledValue("API protocol", "v1 · WebSocket sync")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tunnel tokens are encrypted with a hardware-backed Keystore key where available. " +
                            "Room, host and session tokens are never persisted.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }

                SectionLabel("Tools")
                WaveCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Inspect network interfaces, host status and recent log lines.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted,
                            )
                        }
                        OutlinedButton(onClick = onOpenDiagnostics) {
                            Icon(Icons.Filled.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open")
                        }
                    }
                }

                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            val repo = container.settingsRepository
                            val small = driftSmall.toLongOrNull() ?: settings.driftSmallMs
                            val medium = driftMedium.toLongOrNull() ?: settings.driftMediumMs
                            val large = driftLarge.toLongOrNull() ?: settings.driftLargeMs
                            repo.setDriftThresholds(small, medium, large)
                            repo.setAutoReconnect(autoReconnect)
                            saving = false
                            snackbar.showSnackbar("Advanced settings saved")
                        }
                    },
                    enabled = !saving && driftValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving…")
                    } else {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save advanced settings")
                    }
                }
            }
        }
    }
}
