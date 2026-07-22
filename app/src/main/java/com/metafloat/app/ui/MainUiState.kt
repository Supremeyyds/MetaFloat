package com.metafloat.app.ui

import com.metafloat.app.dashboard.DashboardDownloadMirror
import com.metafloat.app.model.ConnectionConfigError
import com.metafloat.app.model.ConnectionState
import com.metafloat.app.model.ControllerConnectionConfig
import com.metafloat.app.settings.AppThemeMode

data class MainUiState(
    val config: ControllerConnectionConfig = ControllerConnectionConfig(),
    val portText: String = config.port.toString(),
    val configError: ConnectionConfigError? = null,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val isTesting: Boolean = false,
    val isDownloadingDashboard: Boolean = false,
    val dashboardInstalled: Boolean = false,
    val overlayRunning: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val downloadMirror: DashboardDownloadMirror = DashboardDownloadMirror.DIRECT_GITHUB,
    val customMirrorBaseUrl: String = "",
)

sealed interface MirrorLatencyState {
    data object Testing : MirrorLatencyState
    data class Success(val milliseconds: Long) : MirrorLatencyState
    data class Failed(val reason: String) : MirrorLatencyState
}
