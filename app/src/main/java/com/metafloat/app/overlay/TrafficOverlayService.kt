package com.metafloat.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.metafloat.app.MetaFloatApp
import com.metafloat.app.R
import com.metafloat.app.dashboard.DashboardSheetActivity
import com.metafloat.app.model.ConnectionState
import com.metafloat.app.model.ControllerConnectionConfig
import com.metafloat.app.model.TrafficFormatter
import com.metafloat.app.model.TrafficSample
import com.metafloat.app.model.UiText
import com.metafloat.app.settings.AppThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrafficOverlayService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var overlayController: OverlayWindowController? = null
    private var trafficJob: Job? = null
    private var latestSample: TrafficSample? = null
    private var connectionState: ConnectionState = ConnectionState.Idle
    private var currentConfig = ControllerConnectionConfig()
    private var currentThemeMode = AppThemeMode.SYSTEM
    private var lastNotificationSnapshot: NotificationSnapshot? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            val initialSnapshot = notificationSnapshot()
            lastNotificationSnapshot = initialSnapshot
            startForeground(NOTIFICATION_ID, buildNotification(initialSnapshot))
        } catch (throwable: RuntimeException) {
            stopSelf()
            return
        }
        isRunning = true
        observeConfig()
        observeThemeMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                backendVersion = intent.getStringExtra(EXTRA_BACKEND_VERSION) ?: backendVersion
            }
            ACTION_SHOW_OVERLAY -> showOverlay()
            ACTION_HIDE_OVERLAY -> hideOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        trafficJob?.cancel()
        hideOverlay()
        serviceJob.cancel()
        isRunning = false
        backendVersion = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayController != null && !Settings.canDrawOverlays(this)) {
            hideOverlay()
        } else {
            overlayController?.onConfigurationChanged()
        }
    }

    private fun observeConfig() {
        scope.launch {
            appContainer().settingsRepository.observeConnectionConfig().collect { config ->
                currentConfig = config
                updateNotification()
                startTraffic(config)
            }
        }
    }

    private fun observeThemeMode() {
        scope.launch {
            appContainer().settingsRepository.observeThemeMode().collect { themeMode ->
                currentThemeMode = themeMode
                updateNotification()
            }
        }
    }

    private fun startTraffic(config: ControllerConnectionConfig) {
        trafficJob?.cancel()
        connectionState = ConnectionState.Connecting
        render()
        trafficJob = scope.launch {
            val reconnectPolicy = TrafficReconnectPolicy()
            while (isActive) {
                collectTrafficAttempt(config, reconnectPolicy)
                if (!isActive) {
                    break
                }
                val retry = reconnectPolicy.onAttemptFailed()
                connectionState = when (retry.displayState) {
                    TrafficRetryDisplayState.RECONNECTING -> ConnectionState.Reconnecting
                    TrafficRetryDisplayState.DISCONNECTED -> ConnectionState.Disconnected(
                        UiText.Resource(R.string.overlay_status_disconnected),
                    )
                }
                render()
                delay(retry.delayMillis)
            }
        }
    }

    private suspend fun collectTrafficAttempt(
        config: ControllerConnectionConfig,
        reconnectPolicy: TrafficReconnectPolicy,
    ) = coroutineScope {
        var lastValidFrameAt = SystemClock.elapsedRealtime()
        val attemptEnded = CompletableDeferred<Unit>()
        val streamJob = launch {
            try {
                appContainer().mihomoApiClient.observeTraffic(config).collect { sample ->
                    lastValidFrameAt = SystemClock.elapsedRealtime()
                    reconnectPolicy.onValidFrame()
                    latestSample = sample
                    connectionState = ConnectionState.Connected()
                    render()
                }
                attemptEnded.complete(Unit)
            } catch (cancelled: CancellationException) {
                if (!attemptEnded.isCompleted) {
                    throw cancelled
                }
            } catch (_: Throwable) {
                attemptEnded.complete(Unit)
            }
        }
        val watchdogJob = launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastValidFrameAt
                val remaining = reconnectPolicy.frameTimeoutMillis - elapsed
                if (reconnectPolicy.hasFrameTimedOut(lastValidFrameAt, now)) {
                    if (attemptEnded.complete(Unit)) {
                        if (connectionState !is ConnectionState.Disconnected) {
                            connectionState = ConnectionState.Reconnecting
                            render()
                        }
                        streamJob.cancel()
                    }
                    break
                }
                delay(remaining)
            }
        }
        attemptEnded.await()
        streamJob.cancel()
        watchdogJob.cancel()
    }

    private fun render() {
        overlayController?.render(latestSample, connectionState)
        updateNotification()
    }

    private fun showOverlay() {
        if (overlayController != null || !Settings.canDrawOverlays(this)) {
            return
        }
        val controller = OverlayWindowController(
            context = this,
            onPositionChanged = { x, y ->
                scope.launch {
                    appContainer().settingsRepository.saveOverlayPosition(x, y)
                }
            },
            onWindowFailure = ::hideOverlay,
        )
        overlayController = controller
        scope.launch {
            try {
                val (x, y) = appContainer().settingsRepository.readOverlayPosition()
                if (overlayController !== controller) {
                    return@launch
                }
                controller.attach(x, y)
                controller.render(latestSample, connectionState)
                isOverlayVisible = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (overlayController === controller) {
                    hideOverlay()
                }
            }
        }
    }

    private fun hideOverlay() {
        overlayController?.detach()
        overlayController = null
        isOverlayVisible = false
    }

    private fun notificationSnapshot(): NotificationSnapshot {
        val sample = latestSample.takeIf { connectionState is ConnectionState.Connected }
        val content = if (sample != null) {
            getString(
                R.string.overlay_notification_speed,
                TrafficFormatter.formatBytesPerSecond(sample.downBytesPerSecond),
                TrafficFormatter.formatBytesPerSecond(sample.upBytesPerSecond),
            )
        } else {
            getString(notificationStatusResource())
        }
        val total = sample?.let {
            getString(
                R.string.overlay_notification_total,
                TrafficFormatter.formatBytes(it.downTotalBytes),
                TrafficFormatter.formatBytes(it.upTotalBytes),
            )
        }
        return NotificationSnapshot(
            content = content,
            total = total,
            dashboardUrl = currentConfig.dashboardUrl(),
            themeMode = currentThemeMode,
        )
    }

    private fun notificationStatusResource(): Int {
        return when (connectionState) {
            ConnectionState.Idle -> R.string.overlay_status_idle
            ConnectionState.Connecting -> R.string.overlay_status_connecting
            ConnectionState.Reconnecting -> R.string.overlay_status_reconnecting
            is ConnectionState.Connected -> R.string.overlay_status_connected
            is ConnectionState.Disconnected -> R.string.overlay_status_disconnected
            is ConnectionState.Failed -> R.string.overlay_status_failed
        }
    }

    private fun updateNotification() {
        val snapshot = notificationSnapshot()
        if (snapshot == lastNotificationSnapshot) {
            return
        }
        lastNotificationSnapshot = snapshot
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(snapshot),
        )
    }

    private fun buildNotification(snapshot: NotificationSnapshot): Notification {
        val expandedText = snapshot.total?.let { "${snapshot.content}\n$it" } ?: snapshot.content
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_metafloat)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(snapshot.content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText),
            )
            .setContentIntent(dashboardPendingIntent(snapshot))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .build()
    }

    private fun dashboardPendingIntent(snapshot: NotificationSnapshot): PendingIntent {
        val intent = DashboardSheetActivity.createIntent(
            context = this,
            dashboardUrl = snapshot.dashboardUrl,
            themeMode = snapshot.themeMode,
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            DASHBOARD_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun appContainer() = (application as MetaFloatApp).container

    companion object {
        private const val CHANNEL_ID = "traffic_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val DASHBOARD_REQUEST_CODE = 1002
        private const val ACTION_START_MONITORING =
            "com.metafloat.app.action.START_TRAFFIC_MONITORING"
        private const val ACTION_SHOW_OVERLAY =
            "com.metafloat.app.action.SHOW_TRAFFIC_OVERLAY"
        private const val ACTION_HIDE_OVERLAY =
            "com.metafloat.app.action.HIDE_TRAFFIC_OVERLAY"
        private const val EXTRA_BACKEND_VERSION = "backend_version"

        var isRunning: Boolean = false
            private set

        var isOverlayVisible: Boolean = false
            private set

        var backendVersion: String? = null
            private set

        fun monitoringIntent(context: Context, version: String?): Intent {
            return Intent(context, TrafficOverlayService::class.java)
                .setAction(ACTION_START_MONITORING)
                .putExtra(EXTRA_BACKEND_VERSION, version)
        }

        fun showOverlayIntent(context: Context): Intent {
            return Intent(context, TrafficOverlayService::class.java)
                .setAction(ACTION_SHOW_OVERLAY)
        }

        fun hideOverlayIntent(context: Context): Intent {
            return Intent(context, TrafficOverlayService::class.java)
                .setAction(ACTION_HIDE_OVERLAY)
        }
    }

}

private data class NotificationSnapshot(
    val content: String,
    val total: String?,
    val dashboardUrl: String,
    val themeMode: AppThemeMode,
)
