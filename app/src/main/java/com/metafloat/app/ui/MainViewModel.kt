package com.metafloat.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.metafloat.app.R
import com.metafloat.app.dashboard.DashboardDownloadMirror
import com.metafloat.app.dashboard.DashboardInstaller
import com.metafloat.app.mihomo.MihomoApiClient
import com.metafloat.app.model.ConnectionConfigError
import com.metafloat.app.model.ConnectionState
import com.metafloat.app.model.ControllerConnectionConfig
import com.metafloat.app.model.ControllerProtocol
import com.metafloat.app.model.UiText
import com.metafloat.app.model.validate
import com.metafloat.app.settings.AppThemeMode
import com.metafloat.app.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val apiClient: MihomoApiClient,
    private val dashboardInstaller: DashboardInstaller,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        MainUiState(dashboardInstalled = dashboardInstaller.isInstalled()),
    )
    private val mutableMirrorLatencies = MutableStateFlow<Map<DashboardDownloadMirror, MirrorLatencyState>>(
        emptyMap(),
    )
    private val mutableEvents = Channel<MainUiEvent>(Channel.BUFFERED)
    private var connectJob: Job? = null
    private var configSaveJob: Job? = null
    private var mirrorTestJob: Job? = null
    private var initialConfigLoaded = false

    val state: StateFlow<MainUiState> = mutableState.asStateFlow()
    val mirrorLatencies: StateFlow<Map<DashboardDownloadMirror, MirrorLatencyState>> =
        mutableMirrorLatencies.asStateFlow()
    val events = mutableEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeConnectionConfig().collect { config ->
                if (!initialConfigLoaded) {
                    initialConfigLoaded = true
                    mutableState.update {
                        it.copy(
                            config = config,
                            portText = config.port.toString(),
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeThemeMode().collect { themeMode ->
                mutableState.update { it.copy(themeMode = themeMode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeDownloadMirror().collect { mirror ->
                mutableState.update { it.copy(downloadMirror = mirror) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeCustomMirrorBaseUrl().collect { baseUrl ->
                mutableState.update { it.copy(customMirrorBaseUrl = baseUrl) }
            }
        }
    }

    fun updateProtocol(protocol: ControllerProtocol) {
        updateConfig { it.copy(protocol = protocol) }
    }

    fun updateHost(host: String) {
        updateConfig { it.copy(host = host) }
    }

    fun updatePort(portText: String) {
        cancelActiveConnectionForConfigChange()
        val parsedPort = portText.toIntOrNull()?.takeIf { it in 1..65_535 }
        val nextConfig = parsedPort?.let { state.value.config.copy(port = it) }
        mutableState.update { current ->
            current.copy(
                config = nextConfig ?: current.config,
                portText = portText,
                configError = null,
                connectionState = current.connectionState.afterConfigChange(),
                isTesting = false,
            )
        }
        if (nextConfig != null) {
            scheduleConfigSave(nextConfig)
        }
    }

    fun updateSecondaryPath(path: String) {
        updateConfig { it.copy(secondaryPath = path) }
    }

    fun updateSecret(secret: String) {
        updateConfig { it.copy(secret = secret) }
    }

    fun updateLabel(label: String) {
        updateConfig { it.copy(label = label) }
    }

    fun updateThemeMode(themeMode: AppThemeMode) {
        viewModelScope.launch {
            settingsRepository.saveThemeMode(themeMode)
        }
    }

    fun updateDownloadMirror(mirror: DashboardDownloadMirror) {
        cancelMirrorTests()
        viewModelScope.launch {
            settingsRepository.saveDownloadMirror(mirror)
        }
    }

    fun updateCustomMirrorBaseUrl(baseUrl: String) {
        cancelMirrorTests()
        mutableMirrorLatencies.update { it - DashboardDownloadMirror.CUSTOM }
        mutableState.update { it.copy(customMirrorBaseUrl = baseUrl) }
        viewModelScope.launch {
            settingsRepository.saveCustomMirrorBaseUrl(baseUrl)
        }
    }

    fun updateOverlayRunning(running: Boolean) {
        mutableState.update { it.copy(overlayRunning = running) }
    }

    fun restoreConnectedState(version: String?) {
        mutableState.update { current ->
            if (current.connectionState is ConnectionState.Idle) {
                current.copy(
                    connectionState = ConnectionState.Connected(version),
                    isTesting = false,
                )
            } else {
                current
            }
        }
    }

    fun connectBackend() {
        val current = state.value
        val validationError = current.config.validate(current.portText)
        if (validationError != null) {
            mutableState.update { it.copy(configError = validationError) }
            viewModelScope.launch {
                mutableEvents.send(MainUiEvent.Toast(validationError.message()))
            }
            return
        }

        configSaveJob?.cancel()
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            mutableEvents.send(MainUiEvent.StopTrafficMonitoring)
            val config = state.value.config.copy(port = state.value.portText.toInt())
            settingsRepository.saveConnectionConfig(config)
            connectBackendInternal(config)
        }
    }

    fun disconnectBackend() {
        connectJob?.cancel()
        connectJob = null
        viewModelScope.launch {
            mutableEvents.send(MainUiEvent.StopTrafficMonitoring)
        }
        mutableState.update {
            it.copy(
                connectionState = ConnectionState.Disconnected(
                    UiText.Resource(R.string.connection_disconnected_manual),
                ),
                isTesting = false,
            )
        }
    }

    fun saveAndStartOverlay(onStart: () -> Unit) {
        viewModelScope.launch {
            if (state.value.connectionState !is ConnectionState.Connected) {
                mutableEvents.send(MainUiEvent.Toast(UiText.Resource(R.string.toast_backend_required)))
                return@launch
            }
            settingsRepository.saveConnectionConfig(state.value.config)
            onStart()
        }
    }

    fun testSelectedMirror() {
        val mirror = state.value.downloadMirror
        val customBaseUrl = state.value.customMirrorBaseUrl
        cancelMirrorTests()
        mirrorTestJob = viewModelScope.launch {
            val result = measureMirror(mirror, customBaseUrl)
            result.fold(
                onSuccess = { latency ->
                    mutableEvents.send(
                        MainUiEvent.Toast(
                            UiText.Resource(R.string.toast_mirror_latency, listOf(latency)),
                        ),
                    )
                },
                onFailure = {
                    mutableEvents.send(MainUiEvent.Toast(UiText.Resource(R.string.toast_mirror_unavailable)))
                },
            )
        }
    }

    fun testAllMirrors() {
        cancelMirrorTests()
        val customBaseUrl = state.value.customMirrorBaseUrl
        val mirrors = DashboardDownloadMirror.entries.filter { mirror ->
            mirror != DashboardDownloadMirror.CUSTOM || customBaseUrl.isNotBlank()
        }
        mutableMirrorLatencies.value = emptyMap()
        mirrorTestJob = viewModelScope.launch {
            for (mirror in mirrors) {
                measureMirror(mirror, customBaseUrl)
            }
        }
    }

    fun cancelMirrorTests() {
        mirrorTestJob?.cancel()
        mirrorTestJob = null
        mutableMirrorLatencies.update { states ->
            states.filterValues { it != MirrorLatencyState.Testing }
        }
    }

    fun reinstallDashboard() {
        viewModelScope.launch {
            downloadDashboard()
        }
    }

    fun handleDashboardAction() {
        viewModelScope.launch {
            val dashboardInstalled = dashboardInstaller.isInstalled()
            mutableState.update { it.copy(dashboardInstalled = dashboardInstalled) }
            if (!dashboardInstalled) {
                downloadDashboard()
                return@launch
            }
            if (state.value.connectionState !is ConnectionState.Connected) {
                mutableEvents.send(MainUiEvent.Toast(UiText.Resource(R.string.toast_backend_required)))
                return@launch
            }
            settingsRepository.saveConnectionConfig(state.value.config)
            mutableEvents.send(
                MainUiEvent.OpenDashboard(
                    url = state.value.config.dashboardUrl(),
                    themeMode = state.value.themeMode,
                ),
            )
        }
    }

    private suspend fun downloadDashboard() {
        if (state.value.isDownloadingDashboard) {
            return
        }
        mutableState.update { it.copy(isDownloadingDashboard = true) }
        val mirror = state.value.downloadMirror
        val customMirrorBaseUrl = state.value.customMirrorBaseUrl
        val result = dashboardInstaller.downloadLatest(mirror, customMirrorBaseUrl)
        val installed = dashboardInstaller.isInstalled()
        mutableState.update {
            it.copy(
                isDownloadingDashboard = false,
                dashboardInstalled = installed,
            )
        }
        result.fold(
            onSuccess = {
                mutableEvents.send(
                    MainUiEvent.Toast(UiText.Resource(R.string.toast_dashboard_download_complete)),
                )
            },
            onFailure = { throwable ->
                val failureMessage = throwable.message?.let { message ->
                    UiText.Resource(
                        R.string.toast_dashboard_download_failed,
                        listOf(message),
                    )
                } ?: UiText.Resource(R.string.toast_dashboard_download_failed_unknown)
                mutableEvents.send(
                    MainUiEvent.Toast(failureMessage),
                )
            },
        )
    }

    private suspend fun measureMirror(
        mirror: DashboardDownloadMirror,
        customBaseUrl: String,
    ): Result<Long> {
        mutableMirrorLatencies.update {
            it + (mirror to MirrorLatencyState.Testing)
        }
        val result = dashboardInstaller.measureLatency(mirror, customBaseUrl)
        mutableMirrorLatencies.update { current ->
            current + (
                mirror to result.fold(
                    onSuccess = { latency -> MirrorLatencyState.Success(latency) },
                    onFailure = { throwable ->
                        MirrorLatencyState.Failed(throwable.message.orEmpty())
                    },
                )
            )
        }
        return result
    }

    private suspend fun connectBackendInternal(config: ControllerConnectionConfig) {
        mutableState.update {
            it.copy(
                isTesting = true,
                connectionState = ConnectionState.Connecting,
            )
        }
        val result = apiClient.fetchVersion(config)
        if (state.value.config != config) {
            return
        }
        mutableState.update { current ->
            result.fold(
                onSuccess = { version ->
                    current.copy(
                        isTesting = false,
                        connectionState = ConnectionState.Connected(version),
                    )
                },
                onFailure = { throwable ->
                    current.copy(
                        isTesting = false,
                        connectionState = ConnectionState.Failed(
                            throwable.message
                                ?.let(UiText::Dynamic)
                                ?: UiText.Resource(R.string.connection_failed_default),
                        ),
                    )
                },
            )
        }
        result.fold(
            onSuccess = { version ->
                mutableEvents.send(MainUiEvent.StartTrafficMonitoring(version))
                mutableEvents.send(MainUiEvent.Toast(UiText.Resource(R.string.toast_connection_success)))
            },
            onFailure = {
                mutableEvents.send(MainUiEvent.StopTrafficMonitoring)
                mutableEvents.send(MainUiEvent.Toast(UiText.Resource(R.string.toast_connection_failed)))
            },
        )
    }

    override fun onCleared() {
        connectJob?.cancel()
        configSaveJob?.cancel()
        cancelMirrorTests()
        super.onCleared()
    }

    private fun updateConfig(transform: (ControllerConnectionConfig) -> ControllerConnectionConfig) {
        cancelActiveConnectionForConfigChange()
        val nextConfig = transform(state.value.config)
        mutableState.update { current ->
            current.copy(
                config = nextConfig,
                configError = null,
                connectionState = current.connectionState.afterConfigChange(),
                isTesting = false,
            )
        }
        scheduleConfigSave(nextConfig)
    }

    private fun scheduleConfigSave(config: ControllerConnectionConfig) {
        configSaveJob?.cancel()
        configSaveJob = viewModelScope.launch {
            delay(CONFIG_SAVE_DEBOUNCE_MILLIS)
            settingsRepository.saveConnectionConfig(config)
        }
    }

    private fun cancelActiveConnectionForConfigChange() {
        val wasConnected = state.value.connectionState is ConnectionState.Connected
        connectJob?.cancel()
        connectJob = null
        if (wasConnected) {
            viewModelScope.launch {
                mutableEvents.send(MainUiEvent.StopTrafficMonitoring)
            }
        }
    }

    private fun ConnectionState.afterConfigChange(): ConnectionState {
        return if (this is ConnectionState.Idle) {
            this
        } else {
            ConnectionState.Disconnected(UiText.Resource(R.string.connection_config_changed))
        }
    }

    private fun ConnectionConfigError.message(): UiText {
        return when (this) {
            ConnectionConfigError.HOST_REQUIRED -> UiText.Resource(R.string.validation_host_required)
            ConnectionConfigError.HOST_INVALID -> UiText.Resource(R.string.validation_host_invalid)
            ConnectionConfigError.PORT_INVALID -> UiText.Resource(R.string.validation_port_invalid)
            ConnectionConfigError.SECONDARY_PATH_INVALID -> UiText.Resource(R.string.validation_path_invalid)
        }
    }

    private companion object {
        const val CONFIG_SAVE_DEBOUNCE_MILLIS = 350L
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val apiClient: MihomoApiClient,
        private val dashboardInstaller: DashboardInstaller,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(settingsRepository, apiClient, dashboardInstaller) as T
        }
    }
}

sealed interface MainUiEvent {
    data class Toast(val message: UiText) : MainUiEvent
    data class OpenDashboard(val url: String, val themeMode: AppThemeMode) : MainUiEvent
    data class StartTrafficMonitoring(val version: String?) : MainUiEvent
    data object StopTrafficMonitoring : MainUiEvent
}
