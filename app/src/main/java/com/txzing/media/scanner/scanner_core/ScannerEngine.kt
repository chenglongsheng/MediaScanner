package com.txzing.media.scanner.scanner_core

import com.txzing.media.scanner.parser.AudioParser
import com.txzing.media.scanner.parser.ImageParser
import com.txzing.media.scanner.parser.VideoParser
import java.io.File

/**
 * 扫描的文件信息
 */
data class ScannedFile(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mediaType: String,
    val mimeType: String,
    val duration: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val title: String = "",
    val artist: String = "",
    val album: String = ""
)

/**
 * 扫描引擎，协调 FileWalker、ScanFilter 和各个 Parser 完成扫描
 */
class ScannerEngine(
    private val audioParser: AudioParser,
    private val videoParser: VideoParser,
    private val imageParser: ImageParser
) {
    private val fileWalker = FileWalker()
    private val scanFilter = ScanFilter()

    /**
     * 执行扫描
     */
    fun scan(
        rootPath: String,
        extensions: Set<String>,
        onProgress: (current: Int, total: Int) -> Unit,
        onFileFound: (ScannedFile) -> Unit,
        onError: (file: String, error: String) -> Unit
    ) {
        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            onError(rootPath, "Path does not exist or is not a directory")
            return
        }

        // 遍历文件
        val files = fileWalker.walk(rootPath)
        val filteredFiles = scanFilter.filterByExtensions(files, extensions)
        val totalFiles = filteredFiles.size
        var processedCount = 0

        filteredFiles.forEach { file ->
            try {
                val scannedFile = processFile(file)
                if (scannedFile != null) {
                    onFileFound(scannedFile)
                }
            } catch (e: Exception) {
                onError(file.absolutePath, e.message ?: "Unknown error")
            }

            processedCount++
            onProgress(processedCount, totalFiles)
        }
    }

    /**
     * 处理单个文件，返回解析后的信息
     */
    private fun processFile(file: File): ScannedFile? {
        val extension = file.extension.lowercase()
        val mimeType = scanFilter.getMimeType(extension)

        return when {
            scanFilter.isAudioFile(extension) -> {
                val metadata = audioParser.parse(file)
                ScannedFile(
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    mediaType = "audio",
                    mimeType = mimeType,
                    duration = metadata.duration,
                    title = metadata.title,
                    artist = metadata.artist,
                    album = metadata.album
                )
            }
            scanFilter.isVideoFile(extension) -> {
                val metadata = videoParser.parse(file)
                ScannedFile(
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    mediaType = "video",
                    mimeType = mimeType,
                    duration = metadata.duration,
                    width = metadata.width,
                    height = metadata.height,
                    title = metadata.title
                )
            }
            scanFilter.isImageFile(extension) -> {
                val metadata = imageParser.parse(file)
                ScannedFile(
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    mediaType = "image",
                    mimeType = mimeType,
                    width = metadata.width,
                    height = metadata.height
                )
            }
            else -> null
        }
    }
}
