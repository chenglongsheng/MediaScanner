package com.txzing.media.scanner.storage

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager as SystemStorageManager
import java.io.File

/**
 * 存储卷仓库，负责获取和管理所有挂载的存储卷
 */
class VolumeRepository(private val context: Context) {

    private val systemStorageManager: SystemStorageManager? =
        context.getSystemService(Context.STORAGE_SERVICE) as? SystemStorageManager

    /**
     * 获取所有已挂载的存储卷
     */
    fun getMountedVolumes(): List<StorageVolume> {
        val volumes = mutableListOf<StorageVolume>()

        // 内置存储（主存储）
        val primaryVolume = getPrimaryVolume()
        if (primaryVolume != null) {
            volumes.add(primaryVolume)
        }

        // 外置存储卷
        volumes.addAll(getExternalVolumes())

        return volumes
    }

    /**
     * 获取主存储卷
     */
    private fun getPrimaryVolume(): StorageVolume? {
        val storageDir = Environment.getExternalStorageDirectory()
        val statFs = try {
            android.os.StatFs(storageDir.absolutePath)
        } catch (e: Exception) {
            return null
        }

        val totalBytes = statFs.totalBytes
        val availableBytes = statFs.availableBytes

        return StorageVolume(
            id = "primary",
            path = storageDir.absolutePath,
            label = "内部存储",
            isPrimary = true,
            isRemovable = false,
            state = VolumeState.MOUNTED,
            totalBytes = totalBytes,
            availableBytes = availableBytes
        )
    }

    /**
     * 获取外置存储卷
     */
    private fun getExternalVolumes(): List<StorageVolume> {
        val volumes = mutableListOf<StorageVolume>()

        val externalFilesDirs = context.getExternalFilesDirs(null)
        for (dir in externalFilesDirs) {
            if (dir == null) continue

            val path = dir.absolutePath
            // 跳过主存储
            if (path.contains("/Android/data/")) {
                val rootPath = path.substringBefore("/Android/data/")
                val primaryPath = Environment.getExternalStorageDirectory().absolutePath
                if (rootPath == primaryPath) continue
            }

            val rootPath = extractRootPath(path)
            if (rootPath != null && File(rootPath).exists()) {
                val statFs = try {
                    android.os.StatFs(rootPath)
                } catch (e: Exception) {
                    continue
                }

                volumes.add(
                    StorageVolume(
                        id = "external_${volumes.size}",
                        path = rootPath,
                        label = "外部存储 ${volumes.size + 1}",
                        isPrimary = false,
                        isRemovable = true,
                        state = VolumeState.MOUNTED,
                        totalBytes = statFs.totalBytes,
                        availableBytes = statFs.availableBytes
                    )
                )
            }
        }

        return volumes
    }

    /**
     * 从文件路径中提取存储根路径
     */
    private fun extractRootPath(path: String): String? {
        // 常见的外置存储路径模式: /storage/XXXX-XXXX/...
        val storageDir = File("/storage")
        if (storageDir.exists()) {
            storageDir.listFiles()?.forEach { file ->
                if (file.isDirectory && path.startsWith(file.absolutePath)) {
                    return file.absolutePath
                }
            }
        }
        return null
    }

    /**
     * 获取指定路径所属的存储卷
     */
    fun getVolumeForPath(path: String): StorageVolume? {
        return getMountedVolumes().find { volume ->
            path.startsWith(volume.path)
        }
    }

    /**
     * 检查存储卷是否可用
     */
    fun isVolumeAvailable(volumeId: String): Boolean {
        return getMountedVolumes().any { it.id == volumeId }
    }
}
