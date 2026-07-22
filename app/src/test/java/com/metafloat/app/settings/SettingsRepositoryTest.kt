package com.metafloat.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.metafloat.app.dashboard.DashboardDownloadMirror
import com.metafloat.app.model.ControllerConnectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SettingsRepositoryTest {
    @Test
    fun firstLoad_emitsDefaultsOnlyAfterStorageSnapshotIsRead() = runBlocking {
        val scope = testScope()
        try {
            val secretStorage = FakeSecretStorage("saved-secret")
            val repository = SettingsRepository(FakeDataStore(), secretStorage, scope)

            val config = withTimeout(1_000) { repository.readConnectionConfig() }

            assertEquals("127.0.0.1", config.host)
            assertEquals(9090, config.port)
            assertEquals("saved-secret", config.secret)
            assertEquals(
                DashboardDownloadMirror.DIRECT_GITHUB,
                repository.observeDownloadMirror().first(),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun dataStoreIOException_fallsBackToDefaultSnapshot() = runBlocking {
        val scope = testScope()
        try {
            val repository = SettingsRepository(
                FakeDataStore(readFailure = IOException("unavailable")),
                FakeSecretStorage(),
                scope,
            )

            val config = withTimeout(1_000) { repository.readConnectionConfig() }

            assertEquals(ControllerConnectionConfig(), config)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun savedMirrorAndSecret_areRestoredByANewRepositoryInstance() = runBlocking {
        val dataStore = FakeDataStore()
        val secretStorage = FakeSecretStorage()
        val firstScope = testScope()
        try {
            val firstRepository = SettingsRepository(dataStore, secretStorage, firstScope)
            firstRepository.saveDownloadMirror(DashboardDownloadMirror.GHFAST)
            firstRepository.saveConnectionConfig(
                ControllerConnectionConfig(
                    host = "192.0.2.10",
                    port = 9443,
                    secret = "persisted-secret",
                ),
            )
        } finally {
            firstScope.cancel()
        }

        val secondScope = testScope()
        try {
            val secondRepository = SettingsRepository(dataStore, secretStorage, secondScope)

            assertEquals(
                DashboardDownloadMirror.GHFAST,
                withTimeout(1_000) {
                    secondRepository.observeDownloadMirror().first {
                        it == DashboardDownloadMirror.GHFAST
                    }
                },
            )
            assertEquals(
                "persisted-secret",
                withTimeout(1_000) { secondRepository.readConnectionConfig() }.secret,
            )
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun failedConfigSave_restoresPreviousSecret() = runBlocking {
        val scope = testScope()
        try {
            val secretStorage = FakeSecretStorage("old-secret")
            val repository = SettingsRepository(
                FakeDataStore(updateFailure = IOException("write failed")),
                secretStorage,
                scope,
            )

            runCatching {
                repository.saveConnectionConfig(
                    ControllerConnectionConfig(secret = "new-secret"),
                )
            }

            assertEquals("old-secret", secretStorage.readSecret())
        } finally {
            scope.cancel()
        }
    }

    private fun testScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }
}

private class FakeSecretStorage(initialValue: String = "") : SecretStorage {
    private var value = initialValue

    override fun readSecret(): String = value

    override fun writeSecret(secret: String) {
        value = secret
    }
}

private class FakeDataStore(
    initialValue: Preferences = emptyPreferences(),
    private val readFailure: Throwable? = null,
    private val updateFailure: Throwable? = null,
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initialValue)

    override val data: Flow<Preferences> = if (readFailure == null) {
        state
    } else {
        flow { throw readFailure }
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        updateFailure?.let { throw it }
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
