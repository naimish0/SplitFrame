package com.rameshta.splitframe.presentation

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ui.components.SplitFrameTopAppBar

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val selectedTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val selectedLanguage = SupportedAppLanguage.entries.firstOrNull {
        selectedTags.equals(it.languageTag, ignoreCase = true)
    }
    Scaffold(
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.settings),
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = SettingsContentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SettingsActionCard(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.language),
                        supportingText = selectedLanguage?.let { stringResource(it.labelRes) }
                            ?: stringResource(R.string.language_system_default),
                        onClick = onOpenLanguage,
                        modifier = Modifier.testTag(SettingsLanguageCardTestTag),
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.privacy_and_data),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        SettingsActionCard(
                            icon = Icons.Default.PrivacyTip,
                            title = stringResource(R.string.privacy_policy),
                            supportingText = stringResource(R.string.privacy_settings_summary),
                            onClick = onOpenPrivacyPolicy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LanguageScreen(
    onBack: () -> Unit,
    onLanguageSelected: (String) -> Unit = ::applyApplicationLanguage,
) {
    var selectedTags by rememberSaveable {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
    Scaffold(
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.language),
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = SettingsContentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .testTag(LanguageListTestTag)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.language_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                item {
                    LanguageChoiceRow(
                        label = stringResource(R.string.language_system_default),
                        selected = selectedTags.isBlank(),
                        onClick = {
                            selectedTags = ""
                            onLanguageSelected("")
                        },
                    )
                }
                SupportedAppLanguage.entries.forEach { language ->
                    item(key = language.languageTag) {
                        LanguageChoiceRow(
                            label = stringResource(language.labelRes),
                            selected = selectedTags.equals(language.languageTag, ignoreCase = true),
                            onClick = {
                                selectedTags = language.languageTag
                                onLanguageSelected(language.languageTag)
                            },
                        )
                    }
                }
            }
        }
    }
}

const val SettingsLanguageCardTestTag = "settings-language-card"
const val LanguageListTestTag = "language-list"
private val SettingsContentMaxWidth = 720.dp

private fun applyApplicationLanguage(languageTag: String) {
    AppCompatDelegate.setApplicationLocales(
        if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        },
    )
}

@Composable
private fun LanguageChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        tonalElevation = if (selected) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
            )
        }
    }
}

enum class SupportedAppLanguage(
    val languageTag: String,
    @param:StringRes val labelRes: Int,
) {
    English("en", R.string.language_english),
    German("de", R.string.language_german),
    French("fr", R.string.language_french),
    Japanese("ja", R.string.language_japanese),
    Hindi("hi", R.string.language_hindi),
    Russian("ru", R.string.language_russian),
    Spanish("es", R.string.language_spanish),
    PortuguesePortugal("pt-PT", R.string.language_portuguese_portugal),
    PortugueseBrazil("pt-BR", R.string.language_portuguese_brazil),
    Italian("it", R.string.language_italian),
    Indonesian("id", R.string.language_indonesian),
    Arabic("ar", R.string.language_arabic),
    Korean("ko", R.string.language_korean),
    Urdu("ur", R.string.language_urdu),
}
