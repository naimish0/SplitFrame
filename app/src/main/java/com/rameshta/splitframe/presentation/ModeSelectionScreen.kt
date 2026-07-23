package com.rameshta.splitframe.presentation

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rameshta.splitframe.R
import com.rameshta.splitframe.data.RecentVideoProject
import com.rameshta.splitframe.data.RecentVideoProjectStatus
import com.rameshta.splitframe.domain.LayoutTemplate
import com.rameshta.splitframe.presentation.home.HomeUiState
import com.rameshta.splitframe.presentation.home.RecentPhotoExport
import com.rameshta.splitframe.ui.components.SplitFrameTopAppBar
import com.rameshta.splitframe.ui.theme.splitFrameDimens

@Composable
fun ModeSelectionScreen(
    state: HomeUiState,
    onOpenPhotoCollage: () -> Unit,
    onOpenResizeImage: () -> Unit,
    onOpenVideoProjects: () -> Unit,
    onOpenVideoProject: (String) -> Unit,
    onOpenLayout: (String) -> Unit,
    onOpenRecentPhotoExport: (String) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val dimens = splitFrameDimens()
    Scaffold(
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = onOpenPrivacyPolicy) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = stringResource(R.string.privacy_policy),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .testTag(HomeDashboardListTestTag),
            contentPadding = PaddingValues(
                horizontal = dimens.space16,
                vertical = dimens.space16,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.space16),
        ) {
            item(key = "home-intro") {
                Text(
                    text = stringResource(R.string.home_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "home-primary-actions") {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.space12)) {
                    PrimaryActionCard(
                        title = stringResource(R.string.home_create_collage),
                        description = stringResource(R.string.home_create_collage_desc),
                        icon = Icons.Default.Collections,
                        emphasized = true,
                        onClick = onOpenPhotoCollage,
                    )
                    PrimaryActionCard(
                        title = stringResource(R.string.home_resize_image),
                        description = stringResource(R.string.home_resize_image_desc),
                        icon = Icons.Default.PhotoSizeSelectLarge,
                        onClick = onOpenResizeImage,
                    )
                    PrimaryActionCard(
                        title = stringResource(R.string.home_merge_videos),
                        description = stringResource(R.string.home_merge_videos_desc),
                        icon = Icons.Default.VideoLibrary,
                        onClick = onOpenVideoProjects,
                    )
                }
            }

            when (state) {
                HomeUiState.Loading -> item(key = "home-loading") {
                    LoadingCard()
                }
                HomeUiState.Error -> item(key = "home-error") {
                    DashboardMessageCard(
                        title = stringResource(R.string.home_error_title),
                        message = stringResource(R.string.home_error_body),
                        icon = Icons.Default.History,
                    )
                }
                is HomeUiState.Ready -> {
                    if (!state.hasPersonalizedContent) {
                        item(key = "home-empty") {
                            DashboardMessageCard(
                                title = stringResource(R.string.home_empty_title),
                                message = stringResource(R.string.home_empty_body),
                                icon = Icons.Default.History,
                            )
                        }
                    }
                    state.continueProject?.let { project ->
                        item(key = "home-continue-editing") {
                            HomeSection(
                                title = stringResource(R.string.home_continue_editing),
                            ) {
                                ContinueProjectCard(
                                    project = project,
                                    onClick = { onOpenVideoProject(project.id) },
                                )
                            }
                        }
                    }
                    if (state.recentProjects.isNotEmpty()) {
                        item(key = "home-recent-projects") {
                            RecentProjectsSection(
                                projects = state.recentProjects,
                                onSeeAll = onOpenVideoProjects,
                                onOpenProject = onOpenVideoProject,
                                onManageProject = onOpenVideoProjects,
                            )
                        }
                    }
                    if (state.recentPhotoExports.isNotEmpty()) {
                        item(key = "home-recent-photo-exports") {
                            RecentPhotoExportsSection(
                                exports = state.recentPhotoExports,
                                onOpenExport = onOpenRecentPhotoExport,
                            )
                        }
                    }
                    if (state.favoriteLayouts.isNotEmpty()) {
                        item(key = "home-favorite-layouts") {
                            LayoutSection(
                                title = stringResource(R.string.home_favorite_layouts),
                                supportingText = stringResource(R.string.home_favorite_layouts_desc),
                                icon = Icons.Default.Favorite,
                                keyPrefix = "favorite",
                                layouts = state.favoriteLayouts,
                                onOpenLayout = onOpenLayout,
                            )
                        }
                    }
                    if (state.recentlyUsedLayouts.isNotEmpty()) {
                        item(key = "home-recent-layouts") {
                            LayoutSection(
                                title = stringResource(R.string.home_recent_layouts),
                                supportingText = stringResource(R.string.home_recent_layouts_desc),
                                icon = Icons.Default.Schedule,
                                keyPrefix = "recent-layout",
                                layouts = state.recentlyUsedLayouts,
                                onOpenLayout = onOpenLayout,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal const val HomeDashboardListTestTag = "home-dashboard-list"

@Composable
private fun PrimaryActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    val dimens = splitFrameDimens()
    val accessibilityLabel = stringResource(R.string.home_action_accessibility, title, description)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                role = Role.Button
            },
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (emphasized) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(dimens.space16),
            horizontalArrangement = Arrangement.spacedBy(dimens.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconLarge),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.space4),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    val dimens = splitFrameDimens()
    val label = stringResource(R.string.home_loading)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(dimens.space16),
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(dimens.icon))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DashboardMessageCard(
    title: String,
    message: String,
    icon: ImageVector,
) {
    val dimens = splitFrameDimens()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(dimens.space16),
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.space4),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentPhotoExportsSection(
    exports: List<RecentPhotoExport>,
    onOpenExport: (String) -> Unit,
) {
    val dimens = splitFrameDimens()
    HomeSection(
        title = stringResource(R.string.home_recent_photo_exports),
        supportingText = stringResource(R.string.home_recent_photo_exports_desc),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            contentPadding = PaddingValues(end = dimens.space4),
        ) {
            items(
                items = exports,
                key = { export -> "photo-export:${export.id}" },
            ) { export ->
                RecentPhotoExportCard(
                    export = export,
                    onClick = { onOpenExport(export.savedUri) },
                )
            }
        }
    }
}

@Composable
private fun RecentPhotoExportCard(
    export: RecentPhotoExport,
    onClick: () -> Unit,
) {
    val dimens = splitFrameDimens()
    val title = export.template?.titleText() ?: stringResource(R.string.home_photo_export)
    val accessibilityLabel = stringResource(
        R.string.home_open_photo_export_accessibility,
        title,
        export.resolution.label,
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .heightIn(min = dimens.touchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                role = Role.Button
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(dimens.space12),
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Image, contentDescription = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.space4),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = export.resolution.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(export.createdAtMillis).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    supportingText: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dimens = splitFrameDimens()
    Column(verticalArrangement = Arrangement.spacedBy(dimens.space8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.space2),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailingContent?.invoke()
        }
        content()
    }
}

@Composable
private fun ContinueProjectCard(
    project: RecentVideoProject,
    onClick: () -> Unit,
) {
    val dimens = splitFrameDimens()
    val accessibilityLabel = stringResource(R.string.home_continue_project_accessibility, project.name)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                role = Role.Button
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(dimens.space16),
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null)
            ProjectSummary(project = project, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecentProjectsSection(
    projects: List<RecentVideoProject>,
    onSeeAll: () -> Unit,
    onOpenProject: (String) -> Unit,
    onManageProject: () -> Unit,
) {
    val dimens = splitFrameDimens()
    HomeSection(
        title = stringResource(R.string.home_recent_projects),
        trailingContent = {
            TextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.home_see_all))
            }
        },
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            contentPadding = PaddingValues(end = dimens.space4),
        ) {
            items(
                items = projects,
                key = { project -> "video:${project.id}" },
            ) { project ->
                ProjectRailCard(
                    project = project,
                    onClick = if (project.status == RecentVideoProjectStatus.Corrupt) {
                        onManageProject
                    } else {
                        { onOpenProject(project.id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProjectRailCard(
    project: RecentVideoProject,
    onClick: () -> Unit,
) {
    val dimens = splitFrameDimens()
    val accessibilityLabel = if (project.status == RecentVideoProjectStatus.Corrupt) {
        stringResource(R.string.home_manage_project_accessibility, project.name)
    } else {
        stringResource(R.string.home_open_project_accessibility, project.name)
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(240.dp)
            .heightIn(min = dimens.touchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                role = Role.Button
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(dimens.space12),
            verticalArrangement = Arrangement.spacedBy(dimens.space4),
        ) {
            ProjectSummary(project = project)
            Text(
                text = DateUtils.getRelativeTimeSpanString(project.updatedAtMillis).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectSummary(
    project: RecentVideoProject,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(splitFrameDimens().space4),
    ) {
        Text(
            text = project.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = project.statusText(),
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
    }
}

@Composable
private fun RecentVideoProject.statusText(): String =
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

@Composable
private fun LayoutSection(
    title: String,
    supportingText: String,
    icon: ImageVector,
    keyPrefix: String,
    layouts: List<LayoutTemplate>,
    onOpenLayout: (String) -> Unit,
) {
    val dimens = splitFrameDimens()
    HomeSection(title = title, supportingText = supportingText) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            contentPadding = PaddingValues(end = dimens.space4),
        ) {
            items(
                items = layouts,
                key = { layout -> "$keyPrefix:${layout.id}" },
            ) { layout ->
                LayoutRailCard(
                    layout = layout,
                    icon = icon,
                    onClick = { onOpenLayout(layout.id) },
                )
            }
        }
    }
}

@Composable
private fun LayoutRailCard(
    layout: LayoutTemplate,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val dimens = splitFrameDimens()
    val title = layout.titleText()
    val accessibilityLabel = stringResource(R.string.home_open_layout_accessibility, title)
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(180.dp)
            .heightIn(min = dimens.touchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                role = Role.Button
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(dimens.space12),
            verticalArrangement = Arrangement.spacedBy(dimens.space8),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.template_cell_count, layout.slotCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
