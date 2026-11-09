package com.watchwave.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchwave.app.AppContainer
import com.watchwave.app.ui.components.EmptyState
import com.watchwave.app.ui.components.InfoBanner
import com.watchwave.app.ui.components.ScreenScaffold
import com.watchwave.app.ui.components.StatusPill
import com.watchwave.app.ui.components.WaveCard
import com.watchwave.app.ui.components.formatBytes
import com.watchwave.app.ui.components.formatDuration
import com.watchwave.app.ui.components.formatRelative
import com.watchwave.app.ui.theme.Muted
import com.watchwave.app.ui.theme.SurfaceRaised
import com.watchwave.app.ui.theme.WaveCyan
import com.watchwave.app.ui.theme.WaveRed
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val videos by remember(container) { container.mediaRepository.observeLibrary() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    fun report(imported: Int, skipped: Int) {
        val message = buildString {
            if (imported > 0) append("Added $imported video${if (imported == 1) "" else "s"}.") else append("No videos were added.")
            if (skipped > 0) append(" $skipped file${if (skipped == 1) "" else "s"} skipped: unreadable or missing duration.")
        }
        scope.launch { snackbar.showSnackbar(message) }
    }

    val pickVideos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val report = container.mediaRepository.importVideos(uris)
            importing = false
            report(report.imported.size, report.skipped.size)
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val report = container.mediaRepository.importTree(uri)
            importing = false
            report(report.imported.size, report.skipped.size)
        }
    }

    ScreenScaffold(
        title = "Library",
        subtitle = "${videos.size} video${if (videos.size == 1) "" else "s"} on this device",
        onBack = onBack,
        actions = {
            IconButton(onClick = { pickFolder.launch(null) }, enabled = !importing) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "Add folder")
            }
            IconButton(onClick = { pickVideos.launch(arrayOf("video/*")) }, enabled = !importing) {
                Icon(Icons.Filled.Add, contentDescription = "Add videos")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                InfoBanner(
                    message = "WatchWave stores only file metadata. Videos stay in their original folder and are streamed with HTTP Range when a room is live.",
                    color = WaveCyan,
                )
            }

            if (importing) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(SurfaceRaised)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Reading metadata and persisting access…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (videos.isEmpty()) {
                item {
                    EmptyState(
                        title = "Your library is empty",
                        body = "Add individual videos or a whole folder. Supported progressive formats include MP4, MKV, WebM and MOV.",
                        actionLabel = "Add videos",
                        onAction = { pickVideos.launch(arrayOf("video/*")) },
                    )
                }
            } else {
                items(videos, key = { it.uri }) { video ->
                    WaveCard(
                        modifier = Modifier.clickable { onSelect(video.uri) },
                        contentPadding = PaddingValues(14.dp),
                        accent = WaveCyan.copy(alpha = 0.22f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(WaveCyan.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = WaveCyan)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    video.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${formatDuration(video.durationMs)} · ${formatBytes(video.sizeBytes)} · ${formatRelative(video.addedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted,
                                )
                            }
                            IconButton(onClick = { pendingDelete = video.uri }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove from library",
                                    tint = WaveRed.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(onClick = { pickVideos.launch(arrayOf("video/*")) }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add videos")
                        }
                        OutlinedButton(onClick = { pickFolder.launch(null) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add folder")
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove from library?") },
            text = { Text("The original file is not deleted. WatchWave only forgets its metadata and persisted access permission.") },
            confirmButton = {
                Button(onClick = {
                    pendingDelete = null
                    scope.launch { container.mediaRepository.remove(uri) }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}
