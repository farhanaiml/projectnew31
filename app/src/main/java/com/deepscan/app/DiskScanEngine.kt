package com.deepscan.app

import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale

/** Pure JVM logic, independent of Android, so the file-indexing algorithm can be unit-tested. */
data class ScanItem(
    val path: String,
    val name: String,
    val bytes: Long,
    val isDirectory: Boolean,
    val parent: String,
    val modified: Long = 0L
)

data class DuplicateGroup(val bytesPerFile: Long, val copies: List<ScanItem>) {
    val reclaimableBytes: Long get() = bytesPerFile * (copies.size - 1)
}

data class ScanProgress(val stage: String, val filesScanned: Int, val foldersScanned: Int)

data class ScanReport(
    val rootPath: String,
    val items: List<ScanItem>,
    val fileCount: Int,
    val directoryCount: Int,
    val scannedBytes: Long,
    val skippedPaths: Int,
    val duplicateGroups: List<DuplicateGroup>,
    val truncated: Boolean
) {
    val folders: List<ScanItem> get() = items.filter { it.isDirectory }
    val files: List<ScanItem> get() = items.filterNot { it.isDirectory }
    val reclaimableBytes: Long get() = duplicateGroups.sumOf { it.reclaimableBytes }
}

/** Performs READ ONLY traversal and hashing. No scan operation ever deletes a file. */
class DiskScanEngine(
    private val root: File,
    private val maxFiles: Int = 200_000
) {
    fun scan(
        cancelled: () -> Boolean = { false },
        onProgress: (ScanProgress) -> Unit = {}
    ): ScanReport {
        require(root.isDirectory && root.canRead()) { "Shared storage is unavailable or unreadable" }
        val rootPath = root.canonicalPath.trimEnd('/')
        val queue = ArrayDeque<File>()
        queue.add(root)
        val visited = HashSet<String>()
        val folders = ArrayList<ScanItem>()
        val files = ArrayList<ScanItem>()
        val folderSizes = HashMap<String, Long>()
        var skipped = 0
        var truncated = false

        while (queue.isNotEmpty()) {
            if (cancelled()) throw ScanCancelledException()
            val dir = queue.removeFirst()
            val canonical = try { dir.canonicalPath } catch (_: Exception) { null }
            if (canonical == null || !withinRoot(canonical, rootPath) || isProtected(canonical, rootPath) ||
                !visited.add(canonical) || !dir.canRead()) {
                skipped++
                continue
            }
            val children = try { dir.listFiles() } catch (_: SecurityException) { null }
            if (children == null) {
                skipped++
                continue
            }
            folders.add(ScanItem(dir.absolutePath, if (canonical == rootPath) "Shared storage" else dir.name,
                0, true, dir.parent ?: "", dir.lastModified()))
            for (file in children) {
                if (cancelled()) throw ScanCancelledException()
                val fileCanonical = try { file.canonicalPath } catch (_: Exception) { null }
                if (fileCanonical == null || !withinRoot(fileCanonical, rootPath) || isProtected(fileCanonical, rootPath)) {
                    skipped++
                    continue
                }
                if (file.isDirectory) {
                    queue.add(file)
                } else if (file.isFile && file.canRead()) {
                    val size = file.length().coerceAtLeast(0L)
                    files.add(ScanItem(file.absolutePath, file.name, size, false, dir.absolutePath, file.lastModified()))
                    var ancestor: File? = dir
                    while (ancestor != null) {
                        val key = ancestor.absolutePath
                        folderSizes[key] = (folderSizes[key] ?: 0L) + size
                        if (key == root.absolutePath) break
                        ancestor = ancestor.parentFile
                    }
                    if (files.size >= maxFiles) {
                        truncated = true
                        break
                    }
                } else {
                    skipped++
                }
            }
            if (folders.size % 25 == 0) onProgress(ScanProgress("Reading folders", files.size, folders.size))
            if (truncated) break
        }

        onProgress(ScanProgress("Finding exact duplicates", files.size, folders.size))
        // Avoid reading every file twice: hash only where multiple files have the same byte size.
        val duplicateGroups = ArrayList<DuplicateGroup>()
        val bySize = files.filter { it.bytes > 0 }.groupBy { it.bytes }
        var candidates = 0
        for ((size, sameSize) in bySize) {
            if (sameSize.size < 2) continue
            val byHash = LinkedHashMap<String, MutableList<ScanItem>>()
            for (item in sameSize) {
                if (cancelled()) throw ScanCancelledException()
                val hash = sha256(File(item.path), cancelled) ?: continue
                byHash.getOrPut(hash) { ArrayList() }.add(item)
                candidates++
                if (candidates % 20 == 0) onProgress(ScanProgress("Checking matching files", files.size, folders.size))
            }
            for (copies in byHash.values) {
                if (copies.size > 1) duplicateGroups.add(DuplicateGroup(size, copies.toList()))
            }
        }
        onProgress(ScanProgress("Preparing results", files.size, folders.size))
        val foldersWithSizes = folders.map { it.copy(bytes = folderSizes[it.path] ?: 0L) }
        return ScanReport(root.absolutePath, (files + foldersWithSizes).sortedByDescending { it.bytes },
            files.size, foldersWithSizes.size, folderSizes[root.absolutePath] ?: 0L, skipped,
            duplicateGroups.sortedByDescending { it.reclaimableBytes }, truncated)
    }

    private fun withinRoot(path: String, rootPath: String): Boolean =
        path == rootPath || path.startsWith("$rootPath/")

    // App-specific storage owned by OTHER applications remains restricted even under All Files Access.
    private fun isProtected(path: String, rootPath: String): Boolean =
        listOf("$rootPath/Android/data", "$rootPath/Android/obb").any { path == it || path.startsWith("$it/") }

    private fun sha256(file: File, cancelled: () -> Boolean): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (cancelled()) throw ScanCancelledException()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: ScanCancelledException) {
        throw e
    } catch (_: Exception) {
        null // Files can disappear or become inaccessible while the scan runs.
    }
}

class ScanCancelledException : RuntimeException("Scan cancelled")

fun readableSize(bytes: Long): String {
    val abs = bytes.coerceAtLeast(0)
    if (abs < 1024) return "$abs B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var value = abs.toDouble()
    var index = -1
    do { value /= 1024.0; index++ } while (value >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.US, "%.1f %s", value, units[index])
}
