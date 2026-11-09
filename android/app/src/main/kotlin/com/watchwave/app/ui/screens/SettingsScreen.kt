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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.BuildConfig
import com.watchwave.app.security.SecureStore
import com.watchwave.app.ui.components.InfoBanner
import com.watchwave.app.ui.components.LabeledValue
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.SectionLabel
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.WaveAmber
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.data.settings.TunnelMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenTunnel: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val settings by container.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var displayName by remember(settings.displayName) { mutableStateOf(settings.displayName) }
    var portText by remember(settings.serverPort) { mutableStateOf(settings.serverPort.toString()) }
    var maxParticipantsText by remember(settings.maxParticipants) { mutableStateOf(settings.maxParticipants.toString()) }
    var ttlText by remember(settings.roomTtlMinutes) { mutableStateOf(settings.roomTtlMinutes.toString()) }
    var tunnelMode by remember(settings.tunnelMode) { mutableStateOf(settings.tunnelMode) }
    var companionUrl by remember(settings.companionPublicUrl) { mutableStateOf(settings.companionPublicUrl) }
    var cloudflaredPath by remember(settings.cloudflaredPath) { mutableStateOf(settings.cloudflaredPath) }
    var namedHostname by remember(settings.namedTunnelHostname) { mutableStateOf(settings.namedTunnelHostname) }
    var tunnelToken by remember { mutableStateOf(container.secureStore.get(SecureStore.CLOUDFLARED_TOKEN).orEmpty()) }
    var tokenVisible by remember { mutableStateOf(false) }
    var driftSmall by remember(settings.driftSmallMs) { mutableStateOf(settings.driftSmallMs.toString()) }
    var driftMedium by remember(settings.driftMediumMs) { mutableStateOf(settings.driftMediumMs.toString()) }
    var driftLarge by remember(settings.driftLargeMs) { mutableStateOf(settings.driftLargeMs.toString()) }
    var autoReconnect by remember(settings.autoReconnect) { mutableStateOf(settings.autoReconnect) }
    var saving by remember { mutableStateOf(false) }

    val portValid = portText.toIntOrNull()?.let { it in 1024..65535 } == true
    val maxValid = maxParticipantsText.toIntOrNull()?.let { it in 2..50 } == true
    val ttlValid = ttlText.toIntOrNull()?.let { it in 5..1440 } == true
    val driftValid = listOf(driftSmall, driftMedium, driftLarge).all { it.toLongOrNull()?.let { value -> value > 0 } == true }

    ScreenScaffold(
        title = "Settings",
        subtitle = "Host behaviour and connector configuration",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenDiagnostics) {
                Icon(Icons.Filled.Sensors, contentDescription = "Diagnostics")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InfoBanner("These defaults apply to the next room you create. A live room keeps the settings it started with.", color = WaveCyan)

            SectionLabel("Identity & server")
            WaveCard {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 32) displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Listen port") },
                    supportingText = { Text(if (portValid) "1024–65535 · accessible on the LAN" else "Enter a port from 1024 to 65535") },
                    isError = !portValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = maxParticipantsText,
                    onValueChange = { maxParticipantsText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Default participant limit") },
                    supportingText = { Text(if (maxValid) "Host included · 2 to 50" else "Enter 2 to 50") },
                    isError = !maxValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ttlText,
                    onValueChange = { ttlText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Default room lifetime (minutes)") },
                    supportingText = { Text(if (ttlValid) "5 to 1440 minutes" else "Enter 5 to 1440") },
                    isError = !ttlValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic WebSocket reconnect", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Exponential backoff with session-token reuse. Recommended.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                    Switch(checked = autoReconnect, onCheckedChange = { autoReconnect = it })
                }
            }

            SectionLabel("Tunnel")
            WaveCard {
                Text("Reachability mode", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tunnelMode == TunnelMode.ON_DEVICE,
                            onClick = { tunnelMode = TunnelMode.ON_DEVICE },
                            label = { Text("Instant public") },
                        )
                        FilterChip(
                            selected = tunnelMode == TunnelMode.NONE,
                            onClick = { tunnelMode = TunnelMode.NONE },
                            label = { Text("LAN only") },
                        )
                        FilterChip(
                            selected = tunnelMode == TunnelMode.COMPANION_URL,
                            onClick = { tunnelMode = TunnelMode.COMPANION_URL },
                            label = { Text("Companion") },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tunnelMode == TunnelMode.CLOUDFLARED_PROCESS,
                            onClick = { tunnelMode = TunnelMode.CLOUDFLARED_PROCESS },
                            label = { Text("Custom cloudflared") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                when (tunnelMode) {
                    TunnelMode.NONE -> Text(
                        "No public link is produced. Viewers on the same network use the LAN address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )

                    TunnelMode.ON_DEVICE -> {
                        Text(
                            "No setup and no account. When you start a party, WatchWave launches the " +
                                "bundled Cloudflare Quick Tunnel on this phone and shares the verified HTTPS link.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                        )
                        Spacer(Modifier.height(10.dp))
                        InfoBanner(
                            "Bundled for arm64 phones. On other devices use LAN, a companion URL, " +
                                "or a custom cloudflared binary.",
                            color = WaveAmber,
                        )
                    }

                    TunnelMode.COMPANION_URL -> {
                        OutlinedTextField(
                            value = companionUrl,
                            onValueChange = { companionUrl = it.trim() },
                            label = { Text("Public HTTPS URL") },
                            placeholder = { Text("https://wave.example.com") },
                            supportingText = { Text("Forwarded to this phone's listen port by cloudflared, Termux or a router.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        InfoBanner(
                            "The app verifies /api/v1/health at that URL before showing it. HTTP URLs are rejected.",
                            color = WaveAmber,
                        )
                    }

                    TunnelMode.CLOUDFLARED_PROCESS -> {
                        OutlinedTextField(
                            value = cloudflaredPath,
                            onValueChange = { cloudflaredPath = it.trim() },
                            label = { Text("cloudflared executable path") },
                            placeholder = { Text("/data/data/com.watchwave.app/files/cloudflared") },
                            supportingText = { Text("An Android-compatible static binary that is executable by this app.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tunnelToken,
                            onValueChange = { tunnelToken = it },
                            label = { Text("Named tunnel token (optional)") },
                            supportingText = { Text("Empty starts a quick tunnel. Tokens are encrypted with the Android Keystore.") },
                            singleLine = true,
                            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        if (tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (tokenVisible) "Hide token" else "Show token",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = namedHostname,
                            onValueChange = { namedHostname = it.trim() },
                            label = { Text("Named hostname (optional)") },
                            placeholder = { Text("wave.example.com") },
                            supportingText = { Text("Used for display and health verification when a named tunnel is active.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onOpenTunnel) {
                                Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tunnel status")
                            }
                            OutlinedButton(
                                onClick = {
                                    tunnelToken = ""
                                    container.secureStore.remove(SecureStore.CLOUDFLARED_TOKEN)
                                    scope.launch { snackbar.showSnackbar("Stored tunnel token removed") }
                                },
                                enabled = tunnelToken.isNotBlank(),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear token")
                            }
                        }
                    }
                }
            }

            SectionLabel("Synchronisation")
            WaveCard {
                Text(
                    "Drift thresholds decide when a viewer nudges speed or performs a seek. Lower values are stricter but may cause more visible corrections on unstable networks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = driftSmall,
                        onValueChange = { driftSmall = it.filter(Char::isDigit).take(5) },
                        label = { Text("Speed nudge (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = driftMedium,
                        onValueChange = { driftMedium = it.filter(Char::isDigit).take(5) },
                        label = { Text("Strong nudge (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = driftLarge,
                        onValueChange = { driftLarge = it.filter(Char::isDigit).take(5) },
                        label = { Text("Seek (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!driftValid) {
                    Spacer(Modifier.height(6.dp))
                    Text("All thresholds must be positive numbers.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
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
                    "Tunnel tokens are encrypted with a hardware-backed Keystore key where available. Room, host and session tokens are never persisted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }

            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        val repo = container.settingsRepository
                        val port = portText.toIntOrNull() ?: settings.serverPort
                        val max = maxParticipantsText.toIntOrNull() ?: settings.maxParticipants
                        val ttl = ttlText.toIntOrNull() ?: settings.roomTtlMinutes
                        val small = driftSmall.toLongOrNull() ?: settings.driftSmallMs
                        val medium = driftMedium.toLongOrNull() ?: settings.driftMediumMs
                        val large = driftLarge.toLongOrNull() ?: settings.driftLargeMs
                        repo.setDisplayName(displayName)
                        repo.setServerPort(port)
                        repo.setMaxParticipants(max)
                        repo.setRoomTtlMinutes(ttl)
                        repo.setTunnelMode(tunnelMode)
                        repo.setCompanionPublicUrl(companionUrl)
                        repo.setCloudflaredPath(cloudflaredPath)
                        repo.setNamedTunnelHostname(namedHostname)
                        repo.setDriftThresholds(small, medium, large)
                        repo.setAutoReconnect(autoReconnect)
                        if (tunnelToken.isBlank()) {
                            container.secureStore.remove(SecureStore.CLOUDFLARED_TOKEN)
                        } else {
                            container.secureStore.put(SecureStore.CLOUDFLARED_TOKEN, tunnelToken)
                        }
                        saving = false
                        snackbar.showSnackbar("Settings saved")
                    }
                },
                enabled = !saving && portValid && maxValid && ttlValid && driftValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…")
                } else {
                    Text("Save settings")
                }
            }
        }
    }
}
