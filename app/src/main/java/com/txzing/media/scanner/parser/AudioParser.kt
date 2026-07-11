package com.txzing.media.scanner.parser

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 音频元数据
 */
data class AudioMetadata(
    val duration: Long = 0,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0
)

/**
 * 音频文件解析器，提取音频文件的元数据信息
 */
class AudioParser {

    private val retriever = MediaMetadataRetriever()

    /**
     * 解析音频文件元数据
     */
    fun parse(file: File): AudioMetadata {
        return try {
            retriever.setDataSource(file.absolutePath)

            AudioMetadata(
                duration = extractLong(MediaMetadataRetriever.METADATA_KEY_DURATION),
                title = extractString(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: file.nameWithoutExtension,
                artist = extractString(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "",
                album = extractString(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "",
                genre = extractString(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "",
                bitrate = extractInt(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                sampleRate = extractInt(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE),
                channels = 0 // 从 MediaFormat 获取更准确
            )
        } catch (e: Exception) {
            // 解析失败返回默认值
            AudioMetadata()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 仅获取音频时长（轻量操作）
     */
    fun getDuration(file: File): Long {
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(file.absolutePath)
            val duration = extractLong(MediaMetadataRetriever.METADATA_KEY_DURATION, r)
            r.release()
            duration
        } catch (e: Exception) {
            0L
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
}
