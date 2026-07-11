package com.txzing.media.scanner.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.txzing.media.scanner.database.MediaDatabase
import com.txzing.media.scanner.database.MediaEntity
import com.txzing.media.scanner.parser.AudioParser
import com.txzing.media.scanner.parser.ImageParser
import com.txzing.media.scanner.parser.VideoParser
import com.txzing.media.scanner.scanner_core.ScannerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager Worker，在后台执行实际的扫描任务
 */
class ScanWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val database = MediaDatabase.getInstance(context)
    private val mediaDao = database.mediaDao()

    private val audioParser = AudioParser()
    private val videoParser = VideoParser()
    private val imageParser = ImageParser()

    private val scannerEngine = ScannerEngine(
        audioParser = audioParser,
        videoParser = videoParser,
        imageParser = imageParser
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        val rootPath = inputData.getString(KEY_ROOT_PATH) ?: return@withContext Result.failure()
        val volumeId = inputData.getString(KEY_VOLUME_ID) ?: ""
        val extensions = inputData.getStringArray(KEY_FILE_EXTENSIONS)?.toSet() ?: MediaFileType.allExtensions()

        try {
            var processedCount = 0

            scannerEngine.scan(
                rootPath = rootPath,
                extensions = extensions,
                onProgress = { current, total ->
                    processedCount = current
                    val progressData = Data.Builder()
                        .putInt(KEY_PROGRESS_CURRENT, current)
                        .putInt(KEY_PROGRESS_TOTAL, total)
                        .build()
                    setProgress(progressData)
                },
                onFileFound = { file ->
                    val mediaEntity = MediaEntity(
                        path = file.path,
                        name = file.name,
                        size = file.size,
                        dateModified = file.lastModified,
                        mediaType = file.mediaType,
                        mimeType = file.mimeType,
                        duration = file.duration,
                        width = file.width,
                        height = file.height,
                        title = file.title,
                        artist = file.artist,
                        album = file.album,
                        volumeId = volumeId
                    )
                    mediaDao.insertOrUpdate(mediaEntity)
                },
                onError = { file, error ->
                    // 记录错误但继续扫描
                    android.util.Log.e(TAG, "Error processing file: $file, error: $error")
                }
            )

            // 清理已不存在的媒体记录
            mediaDao.deleteByVolumeId(volumeId)

            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_RESULT_FILES, processedCount)
                    .build()
            )

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Scan failed for path: $rootPath", e)
            return@withContext Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR, e.message ?: "Unknown error")
                    .build()
            )
        }
    }

    companion object {
        private const val TAG = "ScanWorker"

        const val KEY_TASK_ID = "task_id"
        const val KEY_ROOT_PATH = "root_path"
        const val KEY_VOLUME_ID = "volume_id"
        const val KEY_FILE_EXTENSIONS = "file_extensions"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_RESULT_FILES = "result_files"
        const val KEY_ERROR = "error"
    }
}
