package com.txzing.media.scanner.scanner_core

import java.io.File

/**
 * 扫描过滤器，负责根据扩展名、MIME 类型等条件过滤文件
 */
class ScanFilter {

    /**
     * 根据扩展名过滤文件列表
     */
    fun filterByExtensions(files: List<File>, extensions: Set<String>): List<File> {
        if (extensions.isEmpty()) return files
        return files.filter { file ->
            extensions.contains(file.extension.lowercase())
        }
    }

    /**
     * 根据最小文件大小过滤
     */
    fun filterByMinSize(files: List<File>, minSizeBytes: Long): List<File> {
        return files.filter { it.length() >= minSizeBytes }
    }

    /**
     * 判断是否是音频文件
     */
    fun isAudioFile(extension: String): Boolean {
        return AUDIO_EXTENSIONS.contains(extension.lowercase())
    }

    /**
     * 判断是否是视频文件
     */
    fun isVideoFile(extension: String): Boolean {
        return VIDEO_EXTENSIONS.contains(extension.lowercase())
    }

    /**
     * 判断是否是图片文件
     */
    fun isImageFile(extension: String): Boolean {
        return IMAGE_EXTENSIONS.contains(extension.lowercase())
    }

    /**
     * 判断是否是支持的媒体文件
     */
    fun isMediaFile(extension: String): Boolean {
        return isAudioFile(extension) || isVideoFile(extension) || isImageFile(extension)
    }

    /**
     * 根据扩展名获取 MIME 类型
     */
    fun getMimeType(extension: String): String {
        return MIME_TYPE_MAP[extension.lowercase()] ?: "application/octet-stream"
    }

    companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma")
        val VIDEO_EXTENSIONS = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")

        val MIME_TYPE_MAP = mapOf(
            // Audio
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "aac" to "audio/aac",
            "flac" to "audio/flac",
            "ogg" to "audio/ogg",
            "m4a" to "audio/mp4",
            "wma" to "audio/x-ms-wma",
            // Video
            "mp4" to "video/mp4",
            "avi" to "video/x-msvideo",
            "mkv" to "video/x-matroska",
            "mov" to "video/quicktime",
            "wmv" to "video/x-ms-wmv",
            "flv" to "video/x-flv",
            "webm" to "video/webm",
            "3gp" to "video/3gpp",
            // Image
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "gif" to "image/gif",
            "bmp" to "image/bmp",
            "webp" to "image/webp",
            "heic" to "image/heic",
            "heif" to "image/heif"
        )
    }
}
