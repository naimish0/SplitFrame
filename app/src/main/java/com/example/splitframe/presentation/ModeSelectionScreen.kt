package com.example.splitframe.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.splitframe.R
import com.example.splitframe.ads.NativeAdvancedAd
import com.example.splitframe.ui.components.SplitFrameTopAppBar

@Composable
fun ModeSelectionScreen(
    onOpenPhotoCollage: () -> Unit,
    onOpenVideoSplit: () -> Unit,
) {
    Scaffold(
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.app_tagline),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ModeCard(
                    title = stringResource(R.string.mode_photo_collage),
                    description = stringResource(R.string.mode_photo_collage_desc),
                    icon = Icons.Default.Collections,
                    onClick = onOpenPhotoCollage,
                )
            }
            item {
                HomeNativeAd()
            }
            item {
                ModeCard(
                    title = stringResource(R.string.mode_video_split),
                    description = stringResource(R.string.mode_video_split_desc),
                    icon = Icons.Default.VideoLibrary,
                    onClick = onOpenVideoSplit,
                )
            }
            item {
                HomeNativeAd()
            }
        }
    }
}

@Composable
private fun HomeNativeAd() {
    NativeAdvancedAd(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
        outlineColor = MaterialTheme.colorScheme.outlineVariant,
        primaryColor = MaterialTheme.colorScheme.primary,
        onPrimaryColor = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
