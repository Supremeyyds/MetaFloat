package dev.codex.mihomometer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.codex.mihomometer.dashboard.DashboardDownloadMirror
import dev.codex.mihomometer.model.ControllerConnectionConfig
import dev.codex.mihomometer.model.ControllerProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class SettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val secretStore: SecretStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    constructor(context: Context) : this(
        dataStore = context.applicationContext.settingsDataStore,
        secretStore = SecretStore(context.applicationContext),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val storageMutex = Mutex()
    private val cachedConfig = MutableStateFlow<ControllerConnectionConfig?>(null)
    private val cachedThemeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    private val cachedDownloadMirror = MutableStateFlow(DashboardDownloadMirror.DIRECT_GITHUB)
    private val cachedCustomMirrorBaseUrl = MutableStateFlow("")

    init {
        scope.launch {
            dataStore.data
                .catch { throwable ->
                    if (throwable is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw throwable
                    }
                }
                .map { preferences ->
                    storageMutex.withLock {
                        PreferencesSnapshot(
                            config = preferences.toConfig(secretStore.readSecret()),
                            themeMode = AppThemeMode.fromStorageValue(preferences[KEY_THEME_MODE]),
                            downloadMirror = DashboardDownloadMirror.fromStorageValue(
                                preferences[KEY_DOWNLOAD_MIRROR],
                            ),
                            customMirrorBaseUrl = preferences[KEY_CUSTOM_MIRROR_BASE_URL] ?: "",
                        )
                    }
                }
                .collect { snapshot ->
                    cachedConfig.value = snapshot.config
                    cachedThemeMode.value = snapshot.themeMode
                    cachedDownloadMirror.value = snapshot.downloadMirror
                    cachedCustomMirrorBaseUrl.value = snapshot.customMirrorBaseUrl
                }
        }
    }

    fun observeConnectionConfig(): Flow<ControllerConnectionConfig> = cachedConfig.filterNotNull()
    fun observeThemeMode(): Flow<AppThemeMode> = cachedThemeMode
    fun observeDownloadMirror(): Flow<DashboardDownloadMirror> = cachedDownloadMirror
    fun observeCustomMirrorBaseUrl(): Flow<String> = cachedCustomMirrorBaseUrl

    suspend fun readConnectionConfig(): ControllerConnectionConfig {
        return cachedConfig.filterNotNull().first()
    }

    suspend fun saveConnectionConfig(config: ControllerConnectionConfig) {
        storageMutex.withLock {
            val previousSecret = secretStore.readSecret()
            secretStore.writeSecret(config.secret)
            try {
                dataStore.edit { preferences ->
                    preferences[KEY_PROTOCOL] = config.protocol.scheme
                    preferences[KEY_HOST] = config.host
                    preferences[KEY_PORT] = config.port
                    preferences[KEY_SECONDARY_PATH] = config.secondaryPath
                    preferences[KEY_LABEL] = config.label
                }
            } catch (throwable: Throwable) {
                secretStore.writeSecret(previousSecret)
                throw throwable
            }
            cachedConfig.value = config
        }
    }

    suspend fun saveThemeMode(themeMode: AppThemeMode) {
        storageMutex.withLock {
            dataStore.edit { preferences ->
                preferences[KEY_THEME_MODE] = themeMode.name
            }
            cachedThemeMode.value = themeMode
        }
    }

    suspend fun saveDownloadMirror(mirror: DashboardDownloadMirror) {
        storageMutex.withLock {
            dataStore.edit { preferences ->
                preferences[KEY_DOWNLOAD_MIRROR] = mirror.name
            }
            cachedDownloadMirror.value = mirror
        }
    }

    suspend fun saveCustomMirrorBaseUrl(baseUrl: String) {
        storageMutex.withLock {
            dataStore.edit { preferences ->
                preferences[KEY_CUSTOM_MIRROR_BASE_URL] = baseUrl
            }
            cachedCustomMirrorBaseUrl.value = baseUrl
        }
    }

    suspend fun saveOverlayPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_OVERLAY_X] = x
            preferences[KEY_OVERLAY_Y] = y
        }
    }

    suspend fun readOverlayPosition(): Pair<Int, Int> {
        val preferences = dataStore.data.first()
        val x = preferences[KEY_OVERLAY_X] ?: DEFAULT_OVERLAY_X
        val y = preferences[KEY_OVERLAY_Y] ?: DEFAULT_OVERLAY_Y
        return x to y
    }

    private fun Preferences.toConfig(secret: String): ControllerConnectionConfig {
        return ControllerConnectionConfig(
            protocol = ControllerProtocol.fromScheme(this[KEY_PROTOCOL] ?: ControllerProtocol.HTTP.scheme),
            host = this[KEY_HOST] ?: "127.0.0.1",
            port = this[KEY_PORT] ?: 9090,
            secondaryPath = this[KEY_SECONDARY_PATH] ?: "",
            secret = secret,
            label = this[KEY_LABEL] ?: "",
        )
    }

    companion object {
        private val KEY_PROTOCOL = stringPreferencesKey("protocol")
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_SECONDARY_PATH = stringPreferencesKey("secondary_path")
        private val KEY_LABEL = stringPreferencesKey("label")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DOWNLOAD_MIRROR = stringPreferencesKey("download_mirror")
        private val KEY_CUSTOM_MIRROR_BASE_URL = stringPreferencesKey("custom_mirror_base_url")
        private val KEY_PORT = intPreferencesKey("port")
        private val KEY_OVERLAY_X = intPreferencesKey("overlay_x")
        private val KEY_OVERLAY_Y = intPreferencesKey("overlay_y")
        private const val DEFAULT_OVERLAY_X = 24
        private const val DEFAULT_OVERLAY_Y = 1740
    }

    private data class PreferencesSnapshot(
        val config: ControllerConnectionConfig,
        val themeMode: AppThemeMode,
        val downloadMirror: DashboardDownloadMirror,
        val customMirrorBaseUrl: String,
    )
}
