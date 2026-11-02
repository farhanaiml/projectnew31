package com.deepscan.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores only aggregate metadata locally. No file contents, hashes or external uploads.
 * Maximum of twelve snapshots; Android backup is disabled in the manifest.
 */
data class FolderSnapshot(val path: String, val bytes: Long)
data class StorageSnapshot(
    val capturedAt: Long,
    val mappedBytes: Long,
    val fileCount: Int,
    val folders: List<FolderSnapshot>
)

object StorageHistoryStore {
    private const val NAME = "storage-history-v2.json"
    private const val LIMIT = 12

    fun read(context: Context): List<StorageSnapshot> = runCatching {
        val file = File(context.filesDir, NAME)
        if (!file.exists()) return emptyList()
        val source = JSONArray(file.readText())
        (0 until source.length()).map { i ->
            val json = source.getJSONObject(i)
            val paths = json.optJSONArray("folders") ?: JSONArray()
            StorageSnapshot(
                json.getLong("time"),
                json.getLong("bytes"),
                json.getInt("count"),
                (0 until paths.length()).map { j ->
                    val o = paths.getJSONObject(j)
                    FolderSnapshot(o.getString("path"), o.getLong("bytes"))
                }
            )
        }
    }.getOrDefault(emptyList())

    fun record(context: Context, report: ScanReport): List<StorageSnapshot> {
        // Ignore incomplete scans so history never presents partial metrics as comparable.
        if (report.truncated) return read(context)
        val snapshot = StorageSnapshot(
            System.currentTimeMillis(),
            report.scannedBytes,
            report.fileCount,
            report.folders.filter { it.parent == report.rootPath }
                .sortedByDescending { it.bytes }
                .take(120)
                .map { FolderSnapshot(it.path, it.bytes) }
        )
        val updated = (read(context) + snapshot).takeLast(LIMIT)
        val array = JSONArray()
        updated.forEach { item ->
            val children = JSONArray()
            item.folders.forEach { folder ->
                children.put(JSONObject().put("path", folder.path).put("bytes", folder.bytes))
            }
            array.put(JSONObject()
                .put("time", item.capturedAt)
                .put("bytes", item.mappedBytes)
                .put("count", item.fileCount)
                .put("folders", children))
        }
        // Atomic replacement prevents losing all history on interrupted writes.
        val target = File(context.filesDir, NAME)
        val temp = File(context.filesDir, "$NAME.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(target)) {
            target.writeText(array.toString())
            temp.delete()
        }
        return updated
    }
}
