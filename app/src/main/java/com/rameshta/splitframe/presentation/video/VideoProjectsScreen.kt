package com.rameshta.splitframe.presentation.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.core.net.toUri
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ads.EmbeddedAdPolicy
import com.rameshta.splitframe.ads.NativeAdvancedAd
import com.rameshta.splitframe.data.DeleteVideoProjectResult
import com.rameshta.splitframe.data.DeletedVideoProject
import com.rameshta.splitframe.data.RecentVideoProject
import com.rameshta.splitframe.data.RecentVideoProjectStatus
import com.rameshta.splitframe.ui.components.SplitFrameTopAppBar
import com.rameshta.splitframe.ui.theme.splitFrameColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VideoProjectsScreen(
    projects: List<RecentVideoProject>,
    onBack: () -> Unit,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onRenameProject: suspend (String, String) -> Boolean,
    onDuplicateProject: suspend (String) -> String?,
    onDeleteProject: suspend (String) -> DeleteVideoProjectResult,
    onUndoDelete: suspend (DeletedVideoProject) -> Boolean,
    onFinalizeDelete: suspend (DeletedVideoProject) -> Boolean,
    showNativeAds: Boolean = false,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val duplicatedMessage = stringResource(R.string.video_project_duplicated)
    val actionFailedMessage = stringResource(R.string.video_project_action_failed)
    val deletedMessage = stringResource(R.string.video_project_deleted)
    val undoLabel = stringResource(R.string.undo)
    val activeExportMessage = stringResource(R.string.video_project_delete_export_active)
    var renameTarget by remember { mutableStateOf<RecentVideoProject?>(null) }
    var deleteTarget by remember { mutableStateOf<RecentVideoProject?>(null) }
    val nativeAdPositions = remember(projects.size, showNativeAds) {
        if (showNativeAds) {
            EmbeddedAdPolicy.nativeInsertionPositions(
                organicItemCount = projects.size,
                afterEvery = 6,
                maximumAds = 1,
            ).toSet()
        } else {
            emptySet()
        }
    }

    Scaffold(
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.video_projects_title),
                subtitle = stringResource(R.string.video_projects_subtitle),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewProject) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.video_project_new))
            }
        },
    ) { padding ->
        if (projects.isEmpty()) {
            EmptyProjects(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onNewProject = onNewProject,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                projects.forEachIndexed { index, project ->
                    item(key = project.id) {
                        VideoProjectCard(
                            project = project,
                            onOpen = { onOpenProject(project.id) },
                            onRename = { renameTarget = project },
                            onDuplicate = {
                                scope.launch {
                                    val duplicateId = onDuplicateProject(project.id)
                                    snackbarHostState.showSnackbar(
                                        message = if (duplicateId != null) {
                                            duplicatedMessage
                                        } else {
                                            actionFailedMessage
                                        },
                                    )
                                }
                            },
                            onDelete = { deleteTarget = project },
                        )
                    }
                    if (index + 1 in nativeAdPositions) {
                        item(key = "native-ad:video-projects:${index + 1}") {
                            VideoProjectsNativeAd()
                        }
                    }
                }
                item(key = "video-projects-bottom-space") { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    renameTarget?.let { project ->
        RenameProjectDialog(
            initialName = project.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                scope.launch {
                    if (!onRenameProject(project.id, name)) {
                        snackbarHostState.showSnackbar(actionFailedMessage)
                    }
                }
            },
        )
    }

    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.video_project_delete_title)) },
            text = { Text(stringResource(R.string.video_project_delete_body, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            when (val result = onDeleteProject(project.id)) {
                                is DeleteVideoProjectResult.Deleted -> {
                                    val snackbarResult = snackbarHostState.showSnackbar(
                                        message = deletedMessage,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                        onUndoDelete(result.deletion)
                                    } else {
                                        onFinalizeDelete(result.deletion)
                                    }
                                }
                                DeleteVideoProjectResult.ExportActive -> snackbarHostState.showSnackbar(
                                    activeExportMessage,
                                )
                                DeleteVideoProjectResult.NotFound -> snackbarHostState.showSnackbar(
                                    actionFailedMessage,
                                )
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun VideoProjectsNativeAd() {
    val colors = splitFrameColors()
    NativeAdvancedAd(
        containerColor = colors.adContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
        outlineColor = MaterialTheme.colorScheme.outlineVariant,
        primaryColor = MaterialTheme.colorScheme.primary,
        onPrimaryColor = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun VideoProjectCard(
    project: RecentVideoProject,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val canOpen = project.status != RecentVideoProjectStatus.Corrupt
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canOpen, onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectThumbnail(project)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = project.statusLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (
                        project.status == RecentVideoProjectStatus.MissingMedia ||
                        project.status == RecentVideoProjectStatus.Corrupt
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(project.updatedAtMillis).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename))
                    }
                    IconButton(onClick = onDuplicate, enabled = project.status != RecentVideoProjectStatus.Corrupt) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.duplicate))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectThumbnail(project: RecentVideoProject) {
    val context = LocalContext.current
    val thumbnailUri = project.thumbnailUri.takeIf {
        project.status == RecentVideoProjectStatus.Ready
    }
    val thumbnail by produceState<Pair<Boolean, Bitmap?>>(
        initialValue = (thumbnailUri == null) to null,
        key1 = thumbnailUri,
    ) {
        value = true to thumbnailUri?.let { loadVideoThumbnail(context, it) }
    }
    Box(
        modifier = Modifier
            .size(width = 112.dp, height = 72.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        if (!thumbnail.first) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        val bitmap = thumbnail.second
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.video_project_thumbnail, project.name),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (thumbnail.first) {
            Icon(Icons.Default.VideoFile, contentDescription = null)
        }
    }
}

@Composable
private fun EmptyProjects(modifier: Modifier, onNewProject: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.video_projects_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.video_projects_empty_body),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onNewProject) {
            Text(stringResource(R.string.video_project_new))
        }
    }
}

@Composable
private fun RenameProjectDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_project_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                label = { Text(stringResource(R.string.video_project_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RecentVideoProject.statusLabel(): String =
    when (status) {
        RecentVideoProjectStatus.Ready -> pluralStringResource(
            R.plurals.video_project_ready,
            mediaCount,
            mediaCount,
        )
        RecentVideoProjectStatus.Empty -> stringResource(R.string.video_project_empty)
        RecentVideoProjectStatus.MissingMedia -> pluralStringResource(
            R.plurals.video_project_missing_media,
            missingMediaCount,
            missingMediaCount,
        )
        RecentVideoProjectStatus.Corrupt -> stringResource(R.string.video_project_corrupt)
    }

private suspend fun loadVideoThumbnail(context: Context, uri: String): Bitmap? =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri.toUri())
            retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ThumbnailWidthPx,
                ThumbnailHeightPx,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

private const val ThumbnailWidthPx = 320
private const val ThumbnailHeightPx = 180
