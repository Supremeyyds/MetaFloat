package dev.codex.mihomometer.dashboard

import android.content.Context
import dev.codex.mihomometer.network.awaitResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DashboardInstaller private constructor(
    private val cacheDir: File,
    private val filesDir: File,
    private val client: OkHttpClient,
    private val archiveManager: DashboardArchiveManager,
) {
    constructor(context: Context, client: OkHttpClient) : this(
        cacheDir = context.applicationContext.cacheDir,
        filesDir = context.applicationContext.filesDir,
        client = client,
        archiveManager = DashboardArchiveManager(),
    )

    internal constructor(
        cacheDir: File,
        filesDir: File,
        client: OkHttpClient,
    ) : this(cacheDir, filesDir, client, DashboardArchiveManager())

    fun isInstalled(): Boolean {
        return File(dashboardDirectory(filesDir), INDEX_FILE).isFile
    }

    suspend fun downloadLatest(
        mirror: DashboardDownloadMirror,
        customMirrorBaseUrl: String,
    ): Result<Unit> {
        return suspendResult {
            withContext(Dispatchers.IO) {
                val zipFile = File.createTempFile(DOWNLOAD_PREFIX, DOWNLOAD_SUFFIX, cacheDir)
                try {
                    downloadZip(zipFile, mirror, customMirrorBaseUrl)
                    installZip(zipFile)
                } finally {
                    zipFile.delete()
                }
            }
        }
    }

    suspend fun measureLatency(
        mirror: DashboardDownloadMirror,
        customMirrorBaseUrl: String,
    ): Result<Long> {
        return suspendResult {
            withContext(Dispatchers.IO) {
                val startedAt = System.nanoTime()
                val request = Request.Builder()
                    .url(mirror.downloadUrl(customMirrorBaseUrl))
                    .head()
                    .build()
                client.newCall(request).awaitResponse().use { response ->
                    if (response.code == METHOD_NOT_ALLOWED) {
                        return@withContext measureGetLatency(mirror, customMirrorBaseUrl, startedAt)
                    }
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                }
                elapsedMillis(startedAt)
            }
        }
    }

    private suspend fun measureGetLatency(
        mirror: DashboardDownloadMirror,
        customMirrorBaseUrl: String,
        startedAt: Long,
    ): Long {
        val request = Request.Builder()
            .url(mirror.downloadUrl(customMirrorBaseUrl))
            .header("Range", "bytes=0-0")
            .build()
        client.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
        }
        return elapsedMillis(startedAt)
    }

    private suspend fun downloadZip(
        target: File,
        mirror: DashboardDownloadMirror,
        customMirrorBaseUrl: String,
    ) {
        val request = Request.Builder()
            .url(mirror.downloadUrl(customMirrorBaseUrl))
            .build()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, exception: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(exception)
                        } else {
                            target.delete()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use { bodyResponse ->
                                if (!bodyResponse.isSuccessful) {
                                    throw IOException("HTTP ${bodyResponse.code}")
                                }
                                val body = bodyResponse.body ?: throw IOException("Empty response body")
                                val declaredLength = body.contentLength()
                                if (declaredLength > MAX_COMPRESSED_BYTES) {
                                    throw IOException("Downloaded Zashboard archive is too large")
                                }
                                body.byteStream().use { input ->
                                    target.outputStream().buffered().use { output ->
                                        copyWithLimit(input, output, MAX_COMPRESSED_BYTES)
                                    }
                                }
                            }
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            } else {
                                target.delete()
                            }
                        } catch (throwable: Throwable) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(throwable)
                            } else {
                                target.delete()
                            }
                        }
                    }
                }
            )
        }
    }

    private fun installZip(zipFile: File) {
        val suffix = UUID.randomUUID().toString()
        val unpackDir = File(cacheDir, "zashboard-unpack-$suffix")
        val stagingDir = File(filesDir, "zashboard-staging-$suffix")
        val backupDir = File(filesDir, "zashboard-backup-$suffix")
        val targetDir = dashboardDirectory(filesDir)

        try {
            val sourceDir = archiveManager.extract(zipFile, unpackDir)
            archiveManager.stage(sourceDir, stagingDir)
            archiveManager.replace(targetDir, stagingDir, backupDir)
        } finally {
            unpackDir.deleteRecursively()
            stagingDir.deleteRecursively()
            if (targetDir.exists()) {
                backupDir.deleteRecursively()
            }
        }
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
    }

    companion object {
        internal const val MAX_COMPRESSED_BYTES = 64L * 1024L * 1024L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val METHOD_NOT_ALLOWED = 405
        private const val DOWNLOAD_PREFIX = "zashboard-"
        private const val DOWNLOAD_SUFFIX = ".zip"
        private const val INDEX_FILE = "index.html"

        fun dashboardDirectory(context: Context): File {
            return dashboardDirectory(context.applicationContext.filesDir)
        }

        private fun dashboardDirectory(filesDir: File): File = File(filesDir, "zashboard")
    }
}

private suspend inline fun <T> suspendResult(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
}
