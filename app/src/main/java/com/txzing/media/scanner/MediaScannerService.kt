package com.txzing.media.scanner

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.txzing.media.scanner.scheduler.MediaFileType

/**
 * 媒体扫描服务，在后台运行并执行媒体文件扫描
 */
class MediaScannerService : Service() {

    private lateinit var scanScheduler: com.txzing.media.scanner.scheduler.ScanScheduler
    private val registeredCallbacks = mutableListOf<IMediaScanCallback>()

    private val binder = object : IMediaScanner.Stub() {

        override fun startScan(path: String) {
            scanScheduler.scheduleScan(path, "manual_scan")
        }

        override fun stopScan() {
            scanScheduler.cancelAllTasks()
        }

        override fun getScanStatus(): Int {
            val tasks = scanScheduler.getActiveTasks()
            if (tasks.isEmpty()) return IMediaScanner.STATUS_IDLE

            val hasRunning = tasks.any { it.status == com.txzing.media.scanner.scheduler.ScanTaskStatus.RUNNING }
            val hasPaused = tasks.any { it.status == com.txzing.media.scanner.scheduler.ScanTaskStatus.PAUSED }

            return when {
                hasRunning -> IMediaScanner.STATUS_SCANNING
                hasPaused -> IMediaScanner.STATUS_PAUSED
                else -> IMediaScanner.STATUS_IDLE
            }
        }

        override fun registerCallback(callback: IMediaScanCallback) {
            synchronized(registeredCallbacks) {
                registeredCallbacks.add(callback)
            }
        }

        override fun unregisterCallback(callback: IMediaScanCallback) {
            synchronized(registeredCallbacks) {
                registeredCallbacks.remove(callback)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        scanScheduler = com.txzing.media.scanner.scheduler.ScanScheduler(this)
        scanScheduler.initialize()

        // 设置扫描进度回调
        scanScheduler.setOnTaskProgressCallback { taskId, current, total ->
            notifyProgress(current, total)
        }

        scanScheduler.setOnTaskCompletedCallback { taskId ->
            notifyScanCompleted()
        }

        scanScheduler.setOnFileFoundCallback { taskId, path, mediaType ->
            notifyFileFound(path, mediaType)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        scanScheduler.release()
        super.onDestroy()
    }

    private fun notifyProgress(current: Int, total: Int) {
        synchronized(registeredCallbacks) {
            registeredCallbacks.forEach { callback ->
                try {
                    callback.onProgress(current, total)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun notifyFileFound(path: String, mediaType: MediaFileType) {
        val typeCode = when (mediaType) {
            MediaFileType.AUDIO -> 0
            MediaFileType.VIDEO -> 1
            MediaFileType.IMAGE -> 2
        }
        synchronized(registeredCallbacks) {
            registeredCallbacks.forEach { callback ->
                try {
                    callback.onFileFound(path, typeCode)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun notifyScanCompleted() {
        synchronized(registeredCallbacks) {
            registeredCallbacks.forEach { callback ->
                try {
                    callback.onScanCompleted()
                } catch (_: Exception) {
                }
            }
        }
    }
}
