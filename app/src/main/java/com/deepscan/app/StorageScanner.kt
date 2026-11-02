package com.deepscan.app

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

object StorageScanner {
    suspend fun scan(onProgress: (ScanProgress) -> Unit = {}): ScanReport = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        DiskScanEngine(Environment.getExternalStorageDirectory()).scan(
            cancelled = { !context.isActive }, onProgress = onProgress
        )
    }
}
