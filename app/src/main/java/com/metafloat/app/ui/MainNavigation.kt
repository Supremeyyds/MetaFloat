package com.metafloat.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import com.metafloat.app.dashboard.DashboardDownloadMirror
import com.metafloat.app.model.ControllerProtocol
import com.metafloat.app.settings.AppThemeMode

@Composable
internal fun MainScreen(
    state: MainUiState,
    mirrorLatencies: StateFlow<Map<DashboardDownloadMirror, MirrorLatencyState>>,
    onProtocolChange: (ControllerProtocol) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDownloadMirrorChange: (DashboardDownloadMirror) -> Unit,
    onCustomMirrorBaseUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleOverlay: () -> Unit,
    onDashboardAction: () -> Unit,
    onTestAllMirrors: () -> Unit,
    onTestSelectedMirror: () -> Unit,
    onCancelMirrorTests: () -> Unit,
    onReinstallDashboard: () -> Unit,
) {
    var activeScreen by rememberSaveable { mutableStateOf(MainScreenRoute.Home) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val horizontalPadding = if (maxWidth < 360.dp) 12.dp else 20.dp
            val verticalPadding = if (maxHeight < 720.dp) 12.dp else 18.dp
            val compactHeight = maxHeight < 720.dp
            val topPadding = if (compactHeight) 6.dp else 8.dp

            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = activeScreen,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = tween(220),
                            initialOffsetX = { width -> direction * width / 4 },
                        ) + fadeIn(tween(220))
                        ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(180),
                            targetOffsetX = { width -> -direction * width / 6 },
                        ) + fadeOut(tween(180)),
                    )
                },
                label = "screen_transition",
            ) { screen ->
                when (screen) {
                    MainScreenRoute.Home -> HomeContent(
                        state = state,
                        compactHeight = compactHeight,
                        horizontalPadding = horizontalPadding,
                        topPadding = topPadding,
                        verticalPadding = verticalPadding,
                        onProtocolChange = onProtocolChange,
                        onHostChange = onHostChange,
                        onPortChange = onPortChange,
                        onPathChange = onPathChange,
                        onSecretChange = onSecretChange,
                        onLabelChange = onLabelChange,
                        onOpenSettings = { activeScreen = MainScreenRoute.Settings },
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                        onToggleOverlay = onToggleOverlay,
                        onDashboardAction = onDashboardAction,
                    )

                    MainScreenRoute.Settings -> SettingsContent(
                        themeMode = state.themeMode,
                        downloadMirror = state.downloadMirror,
                        isDownloadingDashboard = state.isDownloadingDashboard,
                        compactHeight = compactHeight,
                        horizontalPadding = horizontalPadding,
                        topPadding = topPadding,
                        verticalPadding = verticalPadding,
                        onBack = { activeScreen = MainScreenRoute.Home },
                        onThemeModeChange = onThemeModeChange,
                        onMirrorClick = { activeScreen = MainScreenRoute.Mirror },
                        onReinstallDashboard = onReinstallDashboard,
                    )

                    MainScreenRoute.Mirror -> DownloadMirrorContent(
                        state = state,
                        mirrorLatencies = mirrorLatencies,
                        compactHeight = compactHeight,
                        horizontalPadding = horizontalPadding,
                        topPadding = topPadding,
                        verticalPadding = verticalPadding,
                        onSelect = onDownloadMirrorChange,
                        onCustomMirrorBaseUrlChange = onCustomMirrorBaseUrlChange,
                        onBack = {
                            onCancelMirrorTests()
                            activeScreen = MainScreenRoute.Settings
                        },
                        onTestAllMirrors = onTestAllMirrors,
                        onTestSelectedMirror = onTestSelectedMirror,
                    )
                }
            }
        }
    }
}

private enum class MainScreenRoute {
    Home,
    Settings,
    Mirror,
}
