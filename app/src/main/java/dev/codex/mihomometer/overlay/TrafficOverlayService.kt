package dev.codex.mihomometer.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import dev.codex.mihomometer.MihomoMeterApp
import dev.codex.mihomometer.R
import dev.codex.mihomometer.model.ConnectionState
import dev.codex.mihomometer.model.ControllerConnectionConfig
import dev.codex.mihomometer.model.TrafficSample
import dev.codex.mihomometer.model.UiText
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
    private lateinit var overlayController: OverlayWindowController
    private var trafficJob: Job? = null
    private var latestSample: TrafficSample? = null
    private var connectionState: ConnectionState = ConnectionState.Idle

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (throwable: RuntimeException) {
            stopSelf()
            return
        }
        isRunning = true
        overlayController = OverlayWindowController(
            context = this,
            onPositionChanged = { x, y ->
                scope.launch {
                    appContainer().settingsRepository.saveOverlayPosition(x, y)
                }
            },
            onWindowFailure = ::stopSelf,
        )
        scope.launch {
            try {
                val (x, y) = appContainer().settingsRepository.readOverlayPosition()
                overlayController.attach(x, y)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                connectionState = ConnectionState.Failed(
                    throwable.message?.let(UiText::Dynamic)
                        ?: UiText.Resource(R.string.overlay_start_failed),
                )
                stopSelf()
            }
        }
        observeConfig()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        trafficJob?.cancel()
        if (::overlayController.isInitialized) {
            overlayController.detach()
        }
        serviceJob.cancel()
        isRunning = false
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (::overlayController.isInitialized) {
            overlayController.onConfigurationChanged()
        }
    }

    private fun observeConfig() {
        scope.launch {
            appContainer().settingsRepository.observeConnectionConfig().collect { config ->
                startTraffic(config)
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
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        overlayController.render(latestSample, connectionState)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
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

    private fun appContainer() = (application as MihomoMeterApp).container

    companion object {
        private const val CHANNEL_ID = "traffic_overlay"
        private const val NOTIFICATION_ID = 1001
        var isRunning: Boolean = false
            private set
    }

}
