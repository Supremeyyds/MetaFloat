package com.metafloat.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metafloat.app.R
import com.metafloat.app.model.ConnectionConfigError
import com.metafloat.app.model.ConnectionState
import com.metafloat.app.model.ControllerProtocol

@Composable
internal fun HomeContent(
    state: MainUiState,
    compactHeight: Boolean,
    horizontalPadding: Dp,
    topPadding: Dp,
    verticalPadding: Dp,
    onProtocolChange: (ControllerProtocol) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    val contentSpacing = if (compactHeight) 8.dp else 12.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(
                start = horizontalPadding,
                top = topPadding,
                end = horizontalPadding,
                bottom = verticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        HeaderRow(
            title = stringResource(R.string.app_name),
            onMenuClick = onOpenSettings,
        )
        ConnectionStatusCard(connectionState = state.connectionState, compact = compactHeight)

        ConnectionFields(
            protocol = state.config.protocol,
            host = state.config.host,
            port = state.portText,
            configError = state.configError,
            compact = compactHeight,
            onProtocolChange = onProtocolChange,
            onHostChange = onHostChange,
            onPortChange = onPortChange,
        )

        OptionalConnectionFields(
            secondaryPath = state.config.secondaryPath,
            label = state.config.label,
            configError = state.configError,
            compact = compactHeight,
            onPathChange = onPathChange,
            onLabelChange = onLabelChange,
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.config.secret,
            onValueChange = onSecretChange,
            label = { Text(stringResource(R.string.field_password)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )

        state.configError?.let { error ->
            Text(
                text = error.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        ConnectionControlButton(
            connectionState = state.connectionState,
            isConnecting = state.isTesting,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )

        HomeActionButtons(
            overlayRunning = state.overlayRunning,
            isDownloadingDashboard = state.isDownloadingDashboard,
            compact = compactHeight,
            onToggleOverlay = onToggleOverlay,
            onOpenDashboard = onOpenDashboard,
        )
    }
}

@Composable
private fun ConnectionStatusCard(connectionState: ConnectionState, compact: Boolean) {
    val status = connectionState.statusText()
    val detail = connectionState.detailText()
    val color = connectionState.statusColor()
    val horizontalPadding = if (compact) 12.dp else 16.dp
    val verticalPadding = if (compact) 10.dp else 14.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = color)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        drawCircle(color = color)
    }
}

@Composable
private fun ConnectionState.statusText(): String {
    return when (this) {
        ConnectionState.Idle -> stringResource(R.string.connection_status_idle)
        ConnectionState.Connecting -> stringResource(R.string.connection_status_connecting)
        ConnectionState.Reconnecting -> stringResource(R.string.connection_status_connecting)
        is ConnectionState.Connected -> stringResource(R.string.connection_status_connected)
        is ConnectionState.Disconnected -> stringResource(R.string.connection_status_disconnected)
        is ConnectionState.Failed -> stringResource(R.string.connection_status_failed)
    }
}

@Composable
private fun ConnectionState.detailText(): String {
    return when (this) {
        ConnectionState.Idle -> stringResource(R.string.connection_detail_idle)
        ConnectionState.Connecting -> stringResource(R.string.connection_detail_connecting)
        ConnectionState.Reconnecting -> stringResource(R.string.connection_detail_connecting)
        is ConnectionState.Connected -> version?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.connection_detail_available)
        is ConnectionState.Disconnected -> reason.resolve()
        is ConnectionState.Failed -> reason.resolve()
    }
}

@Composable
private fun ConnectionState.statusColor(): Color {
    return when (this) {
        ConnectionState.Idle -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
        ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
        ConnectionState.Reconnecting -> MaterialTheme.colorScheme.secondary
        is ConnectionState.Connected -> Color(0xFF20A464)
        is ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        is ConnectionState.Failed -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun ConnectionControlButton(
    connectionState: ConnectionState,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = connectionState is ConnectionState.Connected
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !isConnecting,
        onClick = if (connected) onDisconnect else onConnect,
    ) {
        ButtonLabel(
            when {
                isConnecting -> stringResource(R.string.button_connecting)
                connected -> stringResource(R.string.button_disconnect)
                else -> stringResource(R.string.button_connect)
            },
        )
    }
}

@Composable
private fun ConnectionFields(
    protocol: ControllerProtocol,
    host: String,
    port: String,
    configError: ConnectionConfigError?,
    compact: Boolean,
    onProtocolChange: (ControllerProtocol) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = if (compact) 8.dp else 12.dp
        if (maxWidth < 300.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                ProtocolField(
                    modifier = Modifier.fillMaxWidth(),
                    value = protocol,
                    onChange = onProtocolChange,
                )
                HostField(
                    modifier = Modifier.fillMaxWidth(),
                    value = host,
                    isError = configError == ConnectionConfigError.HOST_REQUIRED ||
                        configError == ConnectionConfigError.HOST_INVALID,
                    onChange = onHostChange,
                )
                PortField(
                    modifier = Modifier.fillMaxWidth(),
                    value = port,
                    isError = configError == ConnectionConfigError.PORT_INVALID,
                    onChange = onPortChange,
                )
            }
        } else if (maxWidth < 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    ProtocolField(
                        modifier = Modifier.weight(1f),
                        value = protocol,
                        onChange = onProtocolChange,
                    )
                    PortField(
                        modifier = Modifier.weight(1f),
                        value = port,
                        isError = configError == ConnectionConfigError.PORT_INVALID,
                        onChange = onPortChange,
                    )
                }
                HostField(
                    modifier = Modifier.fillMaxWidth(),
                    value = host,
                    isError = configError == ConnectionConfigError.HOST_REQUIRED ||
                        configError == ConnectionConfigError.HOST_INVALID,
                    onChange = onHostChange,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                ProtocolField(
                    modifier = Modifier.width(128.dp),
                    value = protocol,
                    onChange = onProtocolChange,
                )
                HostField(
                    modifier = Modifier.weight(1f),
                    value = host,
                    isError = configError == ConnectionConfigError.HOST_REQUIRED ||
                        configError == ConnectionConfigError.HOST_INVALID,
                    onChange = onHostChange,
                )
                PortField(
                    modifier = Modifier.width(116.dp),
                    value = port,
                    isError = configError == ConnectionConfigError.PORT_INVALID,
                    onChange = onPortChange,
                )
            }
        }
    }
}

@Composable
private fun OptionalConnectionFields(
    secondaryPath: String,
    label: String,
    configError: ConnectionConfigError?,
    compact: Boolean,
    onPathChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = if (compact) 8.dp else 12.dp
        if (maxWidth < 300.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                SecondaryPathField(
                    modifier = Modifier.fillMaxWidth(),
                    value = secondaryPath,
                    isError = configError == ConnectionConfigError.SECONDARY_PATH_INVALID,
                    onChange = onPathChange,
                )
                LabelField(
                    modifier = Modifier.fillMaxWidth(),
                    value = label,
                    onChange = onLabelChange,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                SecondaryPathField(
                    modifier = Modifier.weight(1f),
                    value = secondaryPath,
                    isError = configError == ConnectionConfigError.SECONDARY_PATH_INVALID,
                    onChange = onPathChange,
                )
                LabelField(
                    modifier = Modifier.weight(1f),
                    value = label,
                    onChange = onLabelChange,
                )
            }
        }
    }
}

@Composable
private fun SecondaryPathField(
    modifier: Modifier,
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.field_secondary_path)) },
        isError = isError,
        singleLine = true,
    )
}

@Composable
private fun LabelField(
    modifier: Modifier,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.field_label)) },
        singleLine = true,
    )
}

@Composable
private fun HostField(
    modifier: Modifier,
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.field_host)) },
        isError = isError,
        singleLine = true,
    )
}

@Composable
private fun PortField(
    modifier: Modifier,
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.field_port)) },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun HomeActionButtons(
    overlayRunning: Boolean,
    isDownloadingDashboard: Boolean,
    compact: Boolean,
    onToggleOverlay: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = if (compact) 8.dp else 12.dp
        if (maxWidth < 300.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                OverlayButton(
                    modifier = Modifier.fillMaxWidth(),
                    overlayRunning = overlayRunning,
                    onClick = onToggleOverlay,
                )
                DashboardButton(
                    modifier = Modifier.fillMaxWidth(),
                    isDownloadingDashboard = isDownloadingDashboard,
                    onClick = onOpenDashboard,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                OverlayButton(
                    modifier = Modifier.weight(1f),
                    overlayRunning = overlayRunning,
                    onClick = onToggleOverlay,
                )
                DashboardButton(
                    modifier = Modifier.weight(1f),
                    isDownloadingDashboard = isDownloadingDashboard,
                    onClick = onOpenDashboard,
                )
            }
        }
    }
}

@Composable
private fun OverlayButton(
    modifier: Modifier,
    overlayRunning: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
    ) {
        ButtonLabel(
            stringResource(
                if (overlayRunning) R.string.button_overlay_stop else R.string.button_overlay_start,
            ),
        )
    }
}

@Composable
private fun DashboardButton(
    modifier: Modifier,
    isDownloadingDashboard: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        enabled = !isDownloadingDashboard,
    ) {
        ButtonLabel(
            stringResource(
                if (isDownloadingDashboard) R.string.button_downloading else R.string.button_dashboard_open,
            ),
        )
    }
}

@Composable
private fun ButtonLabel(text: String) {
    Text(
        text = text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ProtocolField(
    modifier: Modifier = Modifier,
    value: ControllerProtocol,
    onChange: (ControllerProtocol) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value.scheme.uppercase(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_protocol_label)) },
            singleLine = true,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    role = Role.Button,
                    onClick = { expanded = true },
                )
                .semantics(mergeDescendants = true) {},
        ) {
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ControllerProtocol.entries.forEach { protocol ->
                DropdownMenuItem(
                    text = { Text(protocol.scheme.uppercase()) },
                    onClick = {
                        onChange(protocol)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionConfigError.message(): String {
    return when (this) {
        ConnectionConfigError.HOST_REQUIRED -> stringResource(R.string.validation_host_required)
        ConnectionConfigError.HOST_INVALID -> stringResource(R.string.validation_host_invalid)
        ConnectionConfigError.PORT_INVALID -> stringResource(R.string.validation_port_invalid)
        ConnectionConfigError.SECONDARY_PATH_INVALID -> stringResource(R.string.validation_path_invalid)
    }
}
