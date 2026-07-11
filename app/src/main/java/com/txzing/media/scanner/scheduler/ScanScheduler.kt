package com.txzing.media.scanner.scheduler

import android.content.Context
import androidx.work.*
import com.txzing.media.scanner.storage.StorageManager
import com.txzing.media.scanner.storage.StorageVolume
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 扫描调度器，负责管理扫描任务的创建、调度和执行
 */
class ScanScheduler(private val context: Context) {

    private val storageManager = StorageManager(context)
    private val activeTasks = mutableMapOf<String, ScanTask>()
    private val taskResults = mutableMapOf<String, MutableList<String>>()

    private var onTaskProgress: ((String, Int, Int) -> Unit)? = null
    private var onTaskCompleted: ((String) -> Unit)? = null
    private var onFileFound: ((String, String, MediaFileType) -> Unit)? = null

    /**
     * 初始化调度器
     */
    fun initialize() {
        storageManager.initialize()

        storageManager.setOnVolumeMountedCallback { volume ->
            scheduleVolumeScan(volume)
        }

        storageManager.setOnVolumeUnmountedCallback { volume ->
            cancelVolumeTasks(volume.id)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        storageManager.release()
        cancelAllTasks()
    }

    /**
     * 调度对指定路径的扫描
     */
    fun scheduleScan(
        path: String,
        volumeId: String,
        fileTypes: Set<MediaFileType> = MediaFileType.ALL,
        priority: Int = 0
    ): String {
        val taskId = UUID.randomUUID().toString()
        val task = ScanTask(
            id = taskId,
            rootPath = path,
            volumeId = volumeId,
            priority = priority,
            fileTypes = fileTypes
        )

        activeTasks[taskId] = task
        taskResults[taskId] = mutableListOf()

        enqueueScanWork(task)
        return taskId
    }

    /**
     * 调度对整个存储卷的扫描
     */
    fun scheduleVolumeScan(volume: StorageVolume): String {
        return scheduleScan(
            path = volume.path,
            volumeId = volume.id,
            priority = if (volume.isPrimary) 1 else 0
        )
    }

    /**
     * 扫描所有已挂载的存储卷
     */
    fun scanAllVolumes(): List<String> {
        val taskIds = mutableListOf<String>()
        storageManager.getScanPaths().forEach { path ->
            val volume = storageManager.getVolumeForPath(path)
            if (volume != null) {
                taskIds.add(scheduleVolumeScan(volume))
            }
        }
        return taskIds
    }

    /**
     * 将扫描任务加入 WorkManager 队列
     */
    private fun enqueueScanWork(task: ScanTask) {
        val inputData = Data.Builder()
            .putString(ScanWorker.KEY_TASK_ID, task.id)
            .putString(ScanWorker.KEY_ROOT_PATH, task.rootPath)
            .putString(ScanWorker.KEY_VOLUME_ID, task.volumeId)
            .putStringArray(
                ScanWorker.KEY_FILE_EXTENSIONS,
                task.fileTypes.flatMap { it.extensions }.toTypedArray()
            )
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag(task.id)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                task.id,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
    }

    /**
     * 暂停指定任务
     */
    fun pauseTask(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(taskId)
        activeTasks[taskId] = activeTasks[taskId]?.copy(status = ScanTaskStatus.PAUSED)
    }

    /**
     * 取消指定任务
     */
    fun cancelTask(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(taskId)
        activeTasks[taskId] = activeTasks[taskId]?.copy(status = ScanTaskStatus.CANCELLED)
    }

    /**
     * 取消指定存储卷的所有任务
     */
    fun cancelVolumeTasks(volumeId: String) {
        activeTasks.filter { it.value.volumeId == volumeId }.keys.forEach { taskId ->
            cancelTask(taskId)
        }
    }

    /**
     * 取消所有任务
     */
    fun cancelAllTasks() {
        activeTasks.keys.forEach { taskId ->
            cancelTask(taskId)
        }
        activeTasks.clear()
    }

    /**
     * 获取活跃任务列表
     */
    fun getActiveTasks(): List<ScanTask> {
        return activeTasks.values.toList()
    }

    /**
     * 获取指定任务
     */
    fun getTask(taskId: String): ScanTask? {
        return activeTasks[taskId]
    }

    /**
     * 设置任务进度回调
     */
    fun setOnTaskProgressCallback(callback: (String, Int, Int) -> Unit) {
        onTaskProgress = callback
    }

    /**
     * 设置任务完成回调
     */
    fun setOnTaskCompletedCallback(callback: (String) -> Unit) {
        onTaskCompleted = callback
    }

    /**
     * 设置文件发现回调
     */
    fun setOnFileFoundCallback(callback: (String, String, MediaFileType) -> Unit) {
        onFileFound = callback
    }
}
