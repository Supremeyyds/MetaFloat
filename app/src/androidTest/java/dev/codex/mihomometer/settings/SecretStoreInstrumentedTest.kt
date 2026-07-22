package dev.codex.mihomometer.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretStoreInstrumentedTest {
    @Test
    fun stableSecurityCryptoVersion_readsAndWritesSecret() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val secretStore = SecretStore(context)
        val original = secretStore.readSecret()
        try {
            secretStore.writeSecret("instrumentation-secret")
            assertEquals("instrumentation-secret", secretStore.readSecret())
        } finally {
            secretStore.writeSecret(original)
        }
    }
}
