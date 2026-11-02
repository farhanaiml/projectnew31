package com.deepscan.app

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class ScanViewModel : ViewModel() {
    var report by androidx.compose.runtime.mutableStateOf<ScanReport?>(null)
        private set
    var scanning by androidx.compose.runtime.mutableStateOf(false)
        private set
    var progress by androidx.compose.runtime.mutableStateOf("Ready to investigate storage")
        private set
    var message by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun scan() {
        if (scanning) return
        job = viewModelScope.launch {
            scanning = true
            progress = "Preparing scan…"
            message = null
            try {
                report = StorageScanner.scan { state ->
                    // Updates are intentionally infrequent; Compose state supports background writes.
                    progress = "${state.stage} · ${state.filesScanned} files / ${state.foldersScanned} folders"
                }
                progress = if (report?.truncated == true) "Scan limit reached — results are partial" else "Scan complete"
            } catch (_: CancellationException) {
                progress = "Scan cancelled"
            } catch (_: ScanCancelledException) {
                progress = "Scan cancelled"
            } catch (e: Exception) {
                message = e.localizedMessage ?: "Scan failed"
                progress = "Unable to scan storage"
            } finally {
                scanning = false
            }
        }
    }

    fun stop() { job?.cancel() }

    fun deleteFile(item: ScanItem) {
        if (item.isDirectory) {
            message = "Folder deletion is not supported."
            return
        }
        val root = runCatching { Environment.getExternalStorageDirectory().canonicalPath.trimEnd('/') }.getOrNull()
        val resolved = runCatching { File(item.path).canonicalPath }.getOrNull()
        if (root == null || resolved == null || !resolved.startsWith("$root/") ||
            resolved.startsWith("$root/Android/") || !File(item.path).isFile) {
            message = "This path is protected or no longer exists."
            return
        }
        val success = runCatching { File(item.path).delete() }.getOrDefault(false)
        message = if (success) "Deleted ${item.name}. Run another scan for updated results." else
            "Deletion was blocked by Android or the file is no longer available."
        if (success) {
            // Do not rerun a potentially long duplicate hash scan automatically.
            report = null
            progress = "Storage changed — run a new scan"
        }
    }

    fun clearMessage() { message = null }
}
