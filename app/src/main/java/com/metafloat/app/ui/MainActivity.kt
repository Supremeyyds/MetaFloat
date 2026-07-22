package com.metafloat.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.metafloat.app.MetaFloatApp
import com.metafloat.app.R
import com.metafloat.app.dashboard.DashboardActivity
import com.metafloat.app.overlay.TrafficOverlayService
import com.metafloat.app.settings.AppThemeMode

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val container = (application as MetaFloatApp).container
        MainViewModel.Factory(
            container.settingsRepository,
            container.mihomoApiClient,
            container.dashboardInstaller,
        )
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        startOverlayServiceIfAllowed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            MetaFloatTheme(themeMode = state.themeMode) {
                SystemBarsColorEffect()
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.events.collect { event ->
                            when (event) {
                                is MainUiEvent.Toast -> Toast
                                    .makeText(
                                        this@MainActivity,
                                        event.message.resolve(this@MainActivity),
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()

                                is MainUiEvent.OpenDashboard -> openDashboard(event.url, event.themeMode)
                            }
                        }
                    }
                }
                MainScreen(
                    state = state,
                    mirrorLatencies = viewModel.mirrorLatencies,
                    onProtocolChange = viewModel::updateProtocol,
                    onHostChange = viewModel::updateHost,
                    onPortChange = viewModel::updatePort,
                    onPathChange = viewModel::updateSecondaryPath,
                    onSecretChange = viewModel::updateSecret,
                    onLabelChange = viewModel::updateLabel,
                    onThemeModeChange = viewModel::updateThemeMode,
                    onDownloadMirrorChange = viewModel::updateDownloadMirror,
                    onCustomMirrorBaseUrlChange = viewModel::updateCustomMirrorBaseUrl,
                    onConnect = viewModel::connectBackend,
                    onDisconnect = {
                        stopOverlayService()
                        viewModel.disconnectBackend()
                    },
                    onToggleOverlay = {
                        if (state.overlayRunning) {
                            stopOverlayService()
                        } else {
                            viewModel.saveAndStartOverlay(::ensureOverlayPermissionsThenStart)
                        }
                    },
                    onOpenDashboard = viewModel::openDashboard,
                    onTestAllMirrors = viewModel::testAllMirrors,
                    onTestSelectedMirror = viewModel::testSelectedMirror,
                    onCancelMirrorTests = viewModel::cancelMirrorTests,
                    onReinstallDashboard = viewModel::reinstallDashboard,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.updateOverlayRunning(TrafficOverlayService.isRunning)
    }

    private fun ensureOverlayPermissionsThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startOverlayServiceIfAllowed()
        }
    }

    private fun startOverlayServiceIfAllowed() {
        if (!Settings.canDrawOverlays(this)) {
            return
        }
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, TrafficOverlayService::class.java),
            )
            viewModel.updateOverlayRunning(true)
        } catch (_: RuntimeException) {
            viewModel.updateOverlayRunning(false)
            Toast.makeText(this, R.string.overlay_start_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopOverlayService() {
        stopService(Intent(this, TrafficOverlayService::class.java))
        viewModel.updateOverlayRunning(false)
    }

    private fun openDashboard(url: String, themeMode: AppThemeMode) {
        startActivity(
            Intent(this, DashboardActivity::class.java)
                .putExtra(DashboardActivity.EXTRA_DASHBOARD_URL, url)
                .putExtra(DashboardActivity.EXTRA_THEME_MODE, themeMode.name),
        )
    }
}
