package com.rameshta.splitframe.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rameshta.splitframe.R
import com.rameshta.splitframe.ads.ExternalUiReason
import com.rameshta.splitframe.ads.LocalExternalUiLauncher
import com.rameshta.splitframe.ui.components.SplitFrameTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    showAdPrivacyOptions: Boolean,
    onManageAdPrivacy: () -> Unit,
) {
    val context = LocalContext.current
    val externalUiLauncher = LocalExternalUiLauncher.current
    Scaffold(
        topBar = {
            SplitFrameTopAppBar(
                title = stringResource(R.string.privacy_policy),
                subtitle = stringResource(R.string.privacy_effective_date),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.privacy_intro),
                style = MaterialTheme.typography.bodyLarge,
            )
            PrivacySection(R.string.privacy_media_title, R.string.privacy_media_body)
            PrivacySection(R.string.privacy_storage_title, R.string.privacy_storage_body)
            PrivacySection(R.string.privacy_ads_title, R.string.privacy_ads_body)
            if (showAdPrivacyOptions) {
                OutlinedButton(
                    onClick = onManageAdPrivacy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null)
                    Text(
                        text = stringResource(R.string.manage_ad_privacy),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    externalUiLauncher.launch(ExternalUiReason.ExternalViewer) {
                        context.openWebPage(GOOGLE_PRIVACY_URL)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.google_privacy_policy))
            }
            PrivacySection(R.string.privacy_permissions_title, R.string.privacy_permissions_body)
            PrivacySection(R.string.privacy_choices_title, R.string.privacy_choices_body)
            PrivacySection(R.string.privacy_security_title, R.string.privacy_security_body)
            PrivacySection(R.string.privacy_children_title, R.string.privacy_children_body)
            PrivacySection(R.string.privacy_contact_title, R.string.privacy_contact_body)
        }
    }
}

@Composable
private fun PrivacySection(titleRes: Int, bodyRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.openWebPage(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private const val GOOGLE_PRIVACY_URL = "https://policies.google.com/privacy"
