package com.metafloat.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metafloat.app.R
import com.metafloat.app.dashboard.DashboardDownloadMirror
import com.metafloat.app.settings.AppThemeMode

@Composable
internal fun SettingsContent(
    themeMode: AppThemeMode,
    downloadMirror: DashboardDownloadMirror,
    isDownloadingDashboard: Boolean,
    compactHeight: Boolean,
    horizontalPadding: Dp,
    topPadding: Dp,
    verticalPadding: Dp,
    onBack: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onMirrorClick: () -> Unit,
    onReinstallDashboard: () -> Unit,
) {
    val contentSpacing = if (compactHeight) 8.dp else 12.dp

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PageHeader(
            modifier = Modifier.padding(
                start = horizontalPadding,
                top = topPadding,
                end = horizontalPadding,
                bottom = contentSpacing,
            ),
            title = stringResource(R.string.screen_settings),
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            SectionTitle(stringResource(R.string.section_appearance))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.section_mode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ThemeModeButtons(
                        selected = themeMode,
                        onSelect = onThemeModeChange,
                    )
                }
            }

            SectionTitle(stringResource(R.string.section_connection))
            SettingsRow(
                title = stringResource(R.string.screen_download_mirror),
                subtitle = stringResource(downloadMirror.labelResource),
                onClick = onMirrorClick,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onReinstallDashboard,
                enabled = !isDownloadingDashboard,
            ) {
                Text(
                    if (isDownloadingDashboard) {
                        stringResource(R.string.button_downloading)
                    } else {
                        stringResource(R.string.button_redownload_dashboard)
                    },
                )
            }
            Spacer(modifier = Modifier.height(verticalPadding))
        }
    }
}

@Composable
private fun ThemeModeButtons(
    selected: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 430.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    ThemeModeButton(
                        modifier = Modifier.fillMaxWidth(),
                        mode = mode,
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppThemeMode.entries.forEach { mode ->
                    ThemeModeButton(
                        modifier = Modifier.weight(1f),
                        mode = mode,
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeButton(
    modifier: Modifier,
    mode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            modifier = modifier,
            onClick = onClick,
        ) {
            Text(
                text = stringResource(mode.labelResource),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        OutlinedButton(
            modifier = modifier,
            onClick = onClick,
        ) {
            Text(
                text = stringResource(mode.labelResource),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = false,
                onClick = onClick,
                role = Role.Button,
            )
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
