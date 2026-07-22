package com.metafloat.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metafloat.app.R
import com.metafloat.app.dashboard.DashboardDownloadMirror
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun DownloadMirrorContent(
    state: MainUiState,
    mirrorLatencies: StateFlow<Map<DashboardDownloadMirror, MirrorLatencyState>>,
    compactHeight: Boolean,
    horizontalPadding: Dp,
    topPadding: Dp,
    verticalPadding: Dp,
    onSelect: (DashboardDownloadMirror) -> Unit,
    onCustomMirrorBaseUrlChange: (String) -> Unit,
    onBack: () -> Unit,
    onTestAllMirrors: () -> Unit,
    onTestSelectedMirror: () -> Unit,
) {
    val latencyStates by mirrorLatencies.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        delay(MIRROR_TEST_START_DELAY_MILLIS)
        onTestAllMirrors()
    }

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
            title = stringResource(R.string.screen_download_mirror),
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
            MirrorGroupTitle(stringResource(R.string.mirror_group_direct))
            MirrorRow(
                mirror = DashboardDownloadMirror.DIRECT_GITHUB,
                selected = state.downloadMirror == DashboardDownloadMirror.DIRECT_GITHUB,
                latencyState = latencyStates[DashboardDownloadMirror.DIRECT_GITHUB],
                onSelect = onSelect,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            MirrorGroupTitle(stringResource(R.string.mirror_group_sites))
            DashboardDownloadMirror.entries
                .filterNot {
                    it == DashboardDownloadMirror.DIRECT_GITHUB || it == DashboardDownloadMirror.CUSTOM
                }
                .forEach { mirror ->
                    MirrorRow(
                        mirror = mirror,
                        selected = state.downloadMirror == mirror,
                        latencyState = latencyStates[mirror],
                        onSelect = onSelect,
                    )
                }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            MirrorGroupTitle(stringResource(R.string.mirror_group_custom))
            MirrorRow(
                mirror = DashboardDownloadMirror.CUSTOM,
                selected = state.downloadMirror == DashboardDownloadMirror.CUSTOM,
                latencyState = latencyStates[DashboardDownloadMirror.CUSTOM],
                onSelect = onSelect,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.customMirrorBaseUrl,
                onValueChange = onCustomMirrorBaseUrlChange,
                label = { Text(stringResource(R.string.field_custom_mirror)) },
                placeholder = { Text(stringResource(R.string.hint_custom_mirror)) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onTestSelectedMirror,
                enabled = !state.isDownloadingDashboard,
            ) {
                Text(stringResource(R.string.button_test_selected))
            }
            Spacer(modifier = Modifier.height(verticalPadding))
        }
    }
}

private const val MIRROR_TEST_START_DELAY_MILLIS = 280L

@Composable
private fun MirrorGroupTitle(text: String) {
    Text(
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MirrorRow(
    mirror: DashboardDownloadMirror,
    selected: Boolean,
    latencyState: MirrorLatencyState?,
    onSelect: (DashboardDownloadMirror) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = { onSelect(mirror) },
                role = Role.RadioButton,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = { onSelect(mirror) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(mirror.labelResource),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = latencyState.label(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun MirrorLatencyState?.label(): String {
    return when (this) {
        null -> stringResource(R.string.mirror_not_tested)
        MirrorLatencyState.Testing -> stringResource(R.string.mirror_testing)
        is MirrorLatencyState.Success -> stringResource(R.string.mirror_latency, milliseconds)
        is MirrorLatencyState.Failed -> stringResource(R.string.mirror_unavailable)
    }
}
