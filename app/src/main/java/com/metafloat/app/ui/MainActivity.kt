package com.metafloat.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
    private var trafficMonitoringRequested = false
    private var showOverlayWhenPermissionGranted = false
    private var connectedBackendVersion: String? = null

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
        if (trafficMonitoringRequested) {
            startTrafficMonitoringService()
        }
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
                                is MainUiEvent.StartTrafficMonitoring -> {
                                    ensureTrafficMonitoringStarted(event.version)
                                }
                                MainUiEvent.StopTrafficMonitoring -> stopTrafficMonitoringService()
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
                    onDisconnect = viewModel::disconnectBackend,
                    onToggleOverlay = {
                        if (state.overlayRunning) {
                            hideOverlay()
                        } else {
                            viewModel.saveAndStartOverlay(::ensureOverlayPermissionThenShow)
                        }
                    },
                    onDashboardAction = viewModel::handleDashboardAction,
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
        if (TrafficOverlayService.isRunning) {
            viewModel.restoreConnectedState(TrafficOverlayService.backendVersion)
        }
        viewModel.updateOverlayRunning(TrafficOverlayService.isOverlayVisible)
        if (showOverlayWhenPermissionGranted && Settings.canDrawOverlays(this)) {
            showOverlayWhenPermissionGranted = false
            showOverlay()
        }
    }

    private fun ensureTrafficMonitoringStarted(version: String?) {
        trafficMonitoringRequested = true
        connectedBackendVersion = version
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startTrafficMonitoringService()
    }

    private fun startTrafficMonitoringService() {
        if (!trafficMonitoringRequested) {
            return
        }
        try {
            ContextCompat.startForegroundService(
                this,
                TrafficOverlayService.monitoringIntent(this, connectedBackendVersion),
            )
        } catch (_: RuntimeException) {
            trafficMonitoringRequested = false
        }
    }

    private fun ensureOverlayPermissionThenShow() {
        if (!Settings.canDrawOverlays(this)) {
            showOverlayWhenPermissionGranted = true
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        showOverlay()
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            return
        }
        try {
            ContextCompat.startForegroundService(
                this,
                TrafficOverlayService.showOverlayIntent(this),
            )
            viewModel.updateOverlayRunning(true)
        } catch (_: RuntimeException) {
            viewModel.updateOverlayRunning(false)
            Toast.makeText(this, R.string.overlay_start_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideOverlay() {
        showOverlayWhenPermissionGranted = false
        if (TrafficOverlayService.isRunning) {
            startService(TrafficOverlayService.hideOverlayIntent(this))
        }
        viewModel.updateOverlayRunning(false)
    }

    private fun stopTrafficMonitoringService() {
        trafficMonitoringRequested = false
        showOverlayWhenPermissionGranted = false
        connectedBackendVersion = null
        stopService(Intent(this, TrafficOverlayService::class.java))
        viewModel.updateOverlayRunning(false)
    }

    private fun openDashboard(url: String, themeMode: AppThemeMode) {
        startActivity(DashboardActivity.createIntent(this, url, themeMode))
    }
}
