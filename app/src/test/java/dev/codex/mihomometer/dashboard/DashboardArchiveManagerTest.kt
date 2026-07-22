package dev.codex.mihomometer.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DashboardArchiveManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test(expected = IOException::class)
    fun extract_rejectsPathTraversal() {
        val zip = zipFile(mapOf("../outside.txt" to "bad".toByteArray()))
        DashboardArchiveManager().extract(zip, File(temporaryFolder.root, "unpack"))
    }

    @Test
    fun extract_acceptsDistDirectoryAndRequiresIndex() {
        val zip = zipFile(
            mapOf(
                "dist/index.html" to "dashboard".toByteArray(),
                "dist/assets/app.js" to "script".toByteArray(),
            ),
        )

        val source = DashboardArchiveManager().extract(zip, File(temporaryFolder.root, "unpack"))

        assertEquals("dist", source.name)
        assertTrue(File(source, "index.html").isFile)
    }

    @Test(expected = IOException::class)
    fun extract_rejectsTooManyEntries() {
        val zip = zipFile(
            mapOf(
                "one.txt" to byteArrayOf(1),
                "two.txt" to byteArrayOf(2),
                "index.html" to byteArrayOf(3),
            ),
        )
        DashboardArchiveManager(maxEntries = 2)
            .extract(zip, File(temporaryFolder.root, "unpack"))
    }

    @Test(expected = IOException::class)
    fun extract_rejectsExpandedContentOverLimit() {
        val zip = zipFile(mapOf("index.html" to ByteArray(5)))
        DashboardArchiveManager(maxExpandedBytes = 4)
            .extract(zip, File(temporaryFolder.root, "unpack"))
    }

    @Test
    fun stageAndReplace_activatesNewVersionAndRemovesBackup() {
        val source = temporaryFolder.newFolder("source")
        File(source, "index.html").writeText("new")
        val staging = File(temporaryFolder.root, "staging")
        val target = temporaryFolder.newFolder("zashboard")
        File(target, "index.html").writeText("old")
        val backup = File(temporaryFolder.root, "backup")
        val manager = DashboardArchiveManager()

        manager.stage(source, staging)
        manager.replace(target, staging, backup)

        assertEquals("new", File(target, "index.html").readText())
        assertFalse(staging.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun replace_restoresPreviousVersionWhenActivationFails() {
        val target = temporaryFolder.newFolder("zashboard")
        File(target, "index.html").writeText("old")
        val staging = temporaryFolder.newFolder("staging")
        File(staging, "index.html").writeText("new")
        val backup = File(temporaryFolder.root, "backup")
        var moveCount = 0
        val manager = DashboardArchiveManager(
            moveDirectory = { source, destination ->
                moveCount += 1
                when (moveCount) {
                    2 -> false
                    else -> source.renameTo(destination)
                }
            },
        )

        val result = runCatching { manager.replace(target, staging, backup) }

        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("old", File(target, "index.html").readText())
        assertTrue(File(staging, "index.html").isFile)
        assertFalse(backup.exists())
    }

    @Test(expected = IOException::class)
    fun copyWithLimit_rejectsCompressedContentOverLimit() {
        copyWithLimit(
            ByteArrayInputStream(ByteArray(5)),
            ByteArrayOutputStream(),
            maxBytes = 4,
        )
    }

    private fun zipFile(entries: Map<String, ByteArray>): File {
        val file = temporaryFolder.newFile("archive-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return file
    }
}
