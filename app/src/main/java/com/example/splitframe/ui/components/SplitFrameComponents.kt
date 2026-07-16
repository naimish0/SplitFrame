package com.example.splitframe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.splitframe.R
import com.example.splitframe.ui.theme.SplitFrameTheme
import com.example.splitframe.ui.theme.splitFrameColors
import com.example.splitframe.ui.theme.splitFrameDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitFrameTopAppBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dimens = splitFrameDimens()
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.space2)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun SplitFrameSection(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    content: @Composable () -> Unit,
) {
    val dimens = splitFrameDimens()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(dimens.space16),
            verticalArrangement = Arrangement.spacedBy(dimens.space12),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.space4)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

enum class StatusTone {
    Info,
    Success,
    Warning,
    Error,
}

@Composable
fun StatusMessage(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Info,
) {
    val colors = splitFrameColors()
    val (container, content) = when (tone) {
        StatusTone.Info -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Success -> colors.successContainer to colors.onSuccessContainer
        StatusTone.Warning -> colors.warningContainer to colors.onWarningContainer
        StatusTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AdContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = splitFrameColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.adContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.sponsored),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
        }
        Text(text = text, modifier = if (icon != null) Modifier.padding(start = 8.dp) else Modifier)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
        }
        Text(text = text, modifier = if (icon != null) Modifier.padding(start = 8.dp) else Modifier)
    }
}

@Preview(showBackground = true)
@Composable
private fun SplitFrameSectionPreview() {
    SplitFrameTheme {
        SplitFrameSection(
            title = stringResource(R.string.layout_controls),
            supportingText = stringResource(R.string.enhance_explanation),
        ) {
            StatusMessage(text = stringResource(R.string.export_success), tone = StatusTone.Success)
            PrimaryActionButton(text = stringResource(R.string.save), onClick = {}, icon = Icons.Default.Save)
        }
    }
}
