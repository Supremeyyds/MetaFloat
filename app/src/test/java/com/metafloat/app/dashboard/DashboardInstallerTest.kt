package com.metafloat.app.dashboard

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DashboardInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadLatest_installsValidArchiveAndCleansTemporaryFiles() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(Buffer().write(validArchive())))
        server.start()
        try {
            val cacheDir = temporaryFolder.newFolder("cache")
            val filesDir = temporaryFolder.newFolder("files")
            val installer = DashboardInstaller(cacheDir, filesDir, OkHttpClient())

            val result = installer.downloadLatest(
                DashboardDownloadMirror.CUSTOM,
                customMirrorBaseUrl(server),
            )

            assertTrue(result.isSuccess)
            assertTrue(installer.isInstalled())
            assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
            assertFalse(filesDir.listFiles().orEmpty().any { it.name.startsWith("zashboard-staging-") })
            assertFalse(filesDir.listFiles().orEmpty().any { it.name.startsWith("zashboard-backup-") })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun downloadLatest_httpFailureCleansTemporaryFile() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        try {
            val cacheDir = temporaryFolder.newFolder("cache")
            val installer = DashboardInstaller(
                cacheDir,
                temporaryFolder.newFolder("files"),
                OkHttpClient(),
            )

            val result = installer.downloadLatest(
                DashboardDownloadMirror.CUSTOM,
                customMirrorBaseUrl(server),
            )

            assertTrue(result.isFailure)
            assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun downloadLatest_rejectsDeclaredArchiveOver64MiB() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setBody("")
                .setHeader("Content-Length", DashboardInstaller.MAX_COMPRESSED_BYTES + 1),
        )
        server.start()
        try {
            val cacheDir = temporaryFolder.newFolder("cache")
            val installer = DashboardInstaller(
                cacheDir,
                temporaryFolder.newFolder("files"),
                OkHttpClient(),
            )

            val result = installer.downloadLatest(
                DashboardDownloadMirror.CUSTOM,
                customMirrorBaseUrl(server),
            )

            assertTrue(result.isFailure)
            assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun downloadLatest_cancellationCancelsCallAndCleansTemporaryFile() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(validArchive()))
                .setBodyDelay(5, TimeUnit.SECONDS),
        )
        server.start()
        try {
            val cacheDir = temporaryFolder.newFolder("cache")
            val installer = DashboardInstaller(
                cacheDir,
                temporaryFolder.newFolder("files"),
                OkHttpClient(),
            )
            val job = launch {
                installer.downloadLatest(
                    DashboardDownloadMirror.CUSTOM,
                    customMirrorBaseUrl(server),
                )
            }
            server.takeRequest(2, TimeUnit.SECONDS)

            job.cancelAndJoin()
            repeat(20) {
                if (cacheDir.listFiles().orEmpty().isEmpty()) {
                    return@repeat
                }
                delay(50)
            }

            assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
        } finally {
            server.shutdown()
        }
    }

    private fun customMirrorBaseUrl(server: MockWebServer): String {
        return server.url("/proxy").toString().trimEnd('/')
    }

    private fun validArchive(): ByteArray {
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("dist/index.html"))
                zip.write("dashboard".toByteArray())
                zip.closeEntry()
            }
            bytes.toByteArray()
        }
    }
}
