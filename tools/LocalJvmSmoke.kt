import com.deepscan.app.DiskScanEngine
import com.deepscan.app.ScanCancelledException
import com.deepscan.app.readableSize
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("deepscan-smoke").toFile()
    try {
        root.resolve("DCIM").mkdirs()
        root.resolve("Download").mkdirs()
        root.resolve("Android/data/com.example").mkdirs()
        root.resolve("DCIM/photo1.jpg").writeBytes(ByteArray(1024) { 12 })
        root.resolve("Download/photo-copy.jpg").writeBytes(ByteArray(1024) { 12 })
        root.resolve("Download/other.jpg").writeBytes(ByteArray(1024) { 14 })
        root.resolve("Android/data/com.example/private").writeBytes(ByteArray(4096))
        val report = DiskScanEngine(root).scan()
        check(report.fileCount == 3) { "File count: ${report.fileCount}" }
        check(report.scannedBytes == 3072L) { "Wrong bytes: ${report.scannedBytes}" }
        check(report.duplicateGroups.size == 1) { "Wrong duplicates" }
        check(report.reclaimableBytes == 1024L) { "Wrong reclaimable bytes" }
        check(report.skippedPaths > 0) { "Protected folder was not skipped" }
        check(root.resolve("DCIM/photo1.jpg").isFile) { "Scan modified files!" }
        check(readableSize(1024) == "1.0 KB")
        var cancelled = false
        try { DiskScanEngine(root).scan(cancelled = { true }) }
        catch (_: ScanCancelledException) { cancelled = true }
        check(cancelled)
        println("PASS: files, folder bytes, SHA-256 duplicates, protected paths, read-only scans, cancellation, unit formatting")
    } finally {
        root.deleteRecursively()
    }
}
