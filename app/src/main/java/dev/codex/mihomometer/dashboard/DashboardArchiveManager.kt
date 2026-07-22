package dev.codex.mihomometer.dashboard

import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

internal class DashboardArchiveManager(
    private val maxExpandedBytes: Long = DEFAULT_MAX_EXPANDED_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val moveDirectory: (File, File) -> Boolean = { source, destination ->
        source.renameTo(destination)
    },
) {
    fun extract(zipFile: File, destination: File): File {
        if (!destination.mkdirs()) {
            throw IOException("Unable to create unpack directory")
        }
        val canonicalRoot = destination.canonicalFile
        var entryCount = 0
        var expandedBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                if (entryCount > maxEntries) {
                    throw IOException("Too many files in Zashboard archive")
                }

                val outputFile = File(destination, entry.name).canonicalFile
                if (!outputFile.path.startsWith(canonicalRoot.path + File.separator)) {
                    throw IOException("Invalid zip entry")
                }

                if (entry.isDirectory) {
                    if (!outputFile.exists() && !outputFile.mkdirs()) {
                        throw IOException("Unable to create archive directory")
                    }
                } else {
                    outputFile.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) {
                            throw IOException("Unable to create archive directory")
                        }
                    }
                    outputFile.outputStream().buffered().use { output ->
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) {
                                break
                            }
                            expandedBytes += read
                            if (expandedBytes > maxExpandedBytes) {
                                throw IOException("Zashboard archive is too large after extraction")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        return resolveDistDirectory(destination).also { sourceDir ->
            if (!File(sourceDir, INDEX_FILE).isFile) {
                throw IOException("Zashboard index.html not found")
            }
        }
    }

    fun stage(sourceDir: File, stagingDir: File) {
        if (!stagingDir.mkdirs()) {
            throw IOException("Unable to create staging directory")
        }
        sourceDir.walkTopDown().forEach { source ->
            val relative = source.relativeTo(sourceDir)
            val destination = File(stagingDir, relative.path)
            if (source.isDirectory) {
                if (!destination.exists() && !destination.mkdirs()) {
                    throw IOException("Unable to stage Zashboard directory")
                }
            } else {
                destination.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        if (!File(stagingDir, INDEX_FILE).isFile) {
            throw IOException("Staged Zashboard index.html not found")
        }
    }

    fun replace(targetDir: File, stagingDir: File, backupDir: File) {
        if (!stagingDir.isDirectory || !File(stagingDir, INDEX_FILE).isFile) {
            throw IOException("Invalid staged Zashboard directory")
        }
        var oldInstallationMoved = false
        if (targetDir.exists()) {
            if (!moveDirectory(targetDir, backupDir)) {
                throw IOException("Unable to back up existing Zashboard")
            }
            oldInstallationMoved = true
        }

        try {
            if (!moveDirectory(stagingDir, targetDir)) {
                throw IOException("Unable to activate downloaded Zashboard")
            }
            if (oldInstallationMoved) {
                backupDir.deleteRecursively()
            }
        } catch (throwable: Throwable) {
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            if (oldInstallationMoved && !moveDirectory(backupDir, targetDir)) {
                throw IOException(
                    "Zashboard activation failed and the previous version could not be restored",
                    throwable,
                )
            }
            throw throwable
        }
    }

    private fun resolveDistDirectory(unpackedDir: File): File {
        val directIndex = File(unpackedDir, INDEX_FILE)
        if (directIndex.isFile) {
            return unpackedDir
        }

        val distDir = File(unpackedDir, "dist")
        if (File(distDir, INDEX_FILE).isFile) {
            return distDir
        }

        return unpackedDir.walkTopDown()
            .firstOrNull { it.name == INDEX_FILE }
            ?.parentFile
            ?: unpackedDir
    }

    companion object {
        const val DEFAULT_MAX_EXPANDED_BYTES = 256L * 1024L * 1024L
        const val DEFAULT_MAX_ENTRIES = 20_000
        private const val INDEX_FILE = "index.html"
    }
}

internal fun copyWithLimit(
    source: java.io.InputStream,
    destination: java.io.OutputStream,
    maxBytes: Long,
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = source.read(buffer)
        if (read < 0) {
            return total
        }
        total += read
        if (total > maxBytes) {
            throw IOException("Downloaded Zashboard archive is too large")
        }
        destination.write(buffer, 0, read)
    }
}
