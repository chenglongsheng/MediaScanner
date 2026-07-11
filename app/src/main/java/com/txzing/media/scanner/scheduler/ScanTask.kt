package com.txzing.media.scanner.scheduler

/**
 * 扫描任务状态
 */
enum class ScanTaskStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * 扫描任务数据类，封装单次扫描任务的所有信息
 */
data class ScanTask(
    val id: String,
    val rootPath: String,
    val volumeId: String,
    val priority: Int = 0,
    val status: ScanTaskStatus = ScanTaskStatus.PENDING,
    val totalFiles: Int = 0,
    val scannedFiles: Int = 0,
    val newFilesFound: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val fileTypes: Set<MediaFileType> = MediaFileType.ALL,
    val scanSubdirectories: Boolean = true
)

enum class MediaFileType(val extensions: Set<String>) {
    AUDIO(setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma")),
    VIDEO(setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp")),
    IMAGE(setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"));

    companion object {
        val ALL: Set<MediaFileType> = setOf(AUDIO, VIDEO, IMAGE)

        /**
         * 根据文件扩展名判断媒体类型
         */
        fun fromExtension(extension: String): MediaFileType? {
            val ext = extension.lowercase()
            return when {
                AUDIO.extensions.contains(ext) -> AUDIO
                VIDEO.extensions.contains(ext) -> VIDEO
                IMAGE.extensions.contains(ext) -> IMAGE
                else -> null
            }
        }

        /**
         * 获取所有支持的文件扩展名
         */
        fun allExtensions(): Set<String> {
            return ALL.flatMap { it.extensions }.toSet()
        }
    }
}
