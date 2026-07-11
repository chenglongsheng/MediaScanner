package com.txzing.media.scanner.parser

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 视频元数据
 */
data class VideoMetadata(
    val duration: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0,
    val frameRate: Float = 0f,
    val rotation: Int = 0,
    val title: String = "",
    val hasAudio: Boolean = true
)

/**
 * 视频文件解析器，提取视频文件的元数据信息
 */
class VideoParser {

    private val retriever = MediaMetadataRetriever()

    /**
     * 解析视频文件元数据
     */
    fun parse(file: File): VideoMetadata {
        return try {
            retriever.setDataSource(file.absolutePath)

            VideoMetadata(
                duration = extractLong(MediaMetadataRetriever.METADATA_KEY_DURATION),
                width = extractInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                height = extractInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                bitrate = extractInt(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                frameRate = extractFloat(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE),
                rotation = extractInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                title = extractString(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: file.nameWithoutExtension,
                hasAudio = extractString(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            )
        } catch (e: Exception) {
            // 解析失败返回默认值
            VideoMetadata()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 仅获取视频尺寸（轻量操作）
     */
    fun getDimensions(file: File): Pair<Int, Int> {
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(file.absolutePath)
            val width = extractInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, r)
            val height = extractInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, r)
            r.release()
            Pair(width, height)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    private fun extractString(key: Int, retriever: MediaMetadataRetriever = this.retriever): String? {
        return retriever.extractMetadata(key)
    }

    private fun extractLong(key: Int, retriever: MediaMetadataRetriever = this.retriever): Long {
        return retriever.extractMetadata(key)?.toLongOrNull() ?: 0L
    }

    private fun extractInt(key: Int, retriever: MediaMetadataRetriever = this.retriever): Int {
        return retriever.extractMetadata(key)?.toIntOrNull() ?: 0
    }

    private fun extractFloat(key: Int, retriever: MediaMetadataRetriever = this.retriever): Float {
        return retriever.extractMetadata(key)?.toFloatOrNull() ?: 0f
    }
}
