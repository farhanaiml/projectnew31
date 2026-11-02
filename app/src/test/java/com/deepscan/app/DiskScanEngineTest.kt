package com.deepscan.app

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DiskScanEngineTest {
    @Test fun countsNestedFoldersAndFileSizes() {
        val root = Files.createTempDirectory("deepscan-test").toFile()
        try {
            val a = root.resolve("A").apply { mkdirs() }
            val b = a.resolve("B").apply { mkdirs() }
            a.resolve("one.txt").writeText("abc")
            b.resolve("two.txt").writeText("12345")
            val result = DiskScanEngine(root).scan()
            assertEquals(2, result.fileCount)
            assertEquals(3, result.directoryCount)
            assertEquals(8L, result.scannedBytes)
            assertEquals(8L, result.folders.first { it.path == a.absolutePath }.bytes)
            assertEquals(5L, result.folders.first { it.path == b.absolutePath }.bytes)
        } finally { root.deleteRecursively() }
    }

    @Test fun confirmsDuplicatesByContentNotJustSize() {
        val root = Files.createTempDirectory("deepscan-dupes").toFile()
        try {
            root.resolve("first.txt").writeText("HELLO")
            root.resolve("second.txt").writeText("HELLO")
            root.resolve("third.txt").writeText("WORLD")
            val result = DiskScanEngine(root).scan()
            assertEquals(1, result.duplicateGroups.size)
            assertEquals(2, result.duplicateGroups.single().copies.size)
            assertEquals(5L, result.reclaimableBytes)
            assertEquals(3, result.fileCount)
        } finally { root.deleteRecursively() }
    }

    @Test fun skipsProtectedAppFolders() {
        val root = Files.createTempDirectory("deepscan-android").toFile()
        try {
            root.resolve("Android/data/private").apply { mkdirs(); resolve("secret.bin").writeBytes(ByteArray(111)) }
            root.resolve("Android/obb/game").apply { mkdirs(); resolve("file.obb").writeBytes(ByteArray(333)) }
            root.resolve("Android/media/example").apply { mkdirs(); resolve("public.mp4").writeBytes(ByteArray(40)) }
            val report = DiskScanEngine(root).scan()
            assertEquals(1, report.fileCount)
            assertEquals(40L, report.scannedBytes)
            assertTrue(report.skippedPaths >= 2)
        } finally { root.deleteRecursively() }
    }

    @Test fun scanIsReadOnly() {
        val root = Files.createTempDirectory("deepscan-readonly").toFile()
        try {
            val file = root.resolve("dont-delete.txt")
            file.writeText("critical")
            DiskScanEngine(root).scan()
            assertEquals("critical", file.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationAndFileSafetyLimit() {
        val root = Files.createTempDirectory("deepscan-limit").toFile()
        try {
            repeat(6) { root.resolve("file$it").writeText("$it") }
            val report = DiskScanEngine(root, maxFiles = 3).scan()
            assertTrue(report.truncated)
            assertEquals(3, report.fileCount)
            try {
                DiskScanEngine(root).scan(cancelled = { true })
                fail("Should cancel")
            } catch (_: ScanCancelledException) { }
        } finally { root.deleteRecursively() }
    }

    @Test fun humanReadableSizes() {
        assertEquals("0 B", readableSize(0))
        assertEquals("1.0 KB", readableSize(1024))
        assertEquals("1.0 MB", readableSize(1024 * 1024))
    }
}
