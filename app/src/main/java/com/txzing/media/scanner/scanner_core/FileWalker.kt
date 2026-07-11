package com.txzing.media.scanner.scanner_core

import java.io.File

/**
 * 文件遍历器，递归遍历目录获取所有文件
 */
class FileWalker {

    /**
     * 遍历指定目录下的所有文件
     * @param rootPath 根路径
     * @param excludeDirs 需要排除的目录名
     * @param maxDepth 最大递归深度，-1 表示无限制
     * @return 所有文件的列表
     */
    fun walk(
        rootPath: String,
        excludeDirs: Set<String> = DEFAULT_EXCLUDE_DIRS,
        maxDepth: Int = -1
    ): List<File> {
        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return emptyList()
        }

        val files = mutableListOf<File>()
        walkInternal(rootDir, files, excludeDirs, 0, maxDepth)
        return files
    }

    private fun walkInternal(
        dir: File,
        result: MutableList<File>,
        excludeDirs: Set<String>,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (maxDepth >= 0 && currentDepth > maxDepth) return
        if (excludeDirs.contains(dir.name)) return

        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            // 无法读取的目录跳过
            return
        }

        if (children == null) return

        for (child in children) {
            if (child.isDirectory) {
                // 跳过隐藏目录和排除目录
                if (!child.name.startsWith(".") && !excludeDirs.contains(child.name)) {
                    walkInternal(child, result, excludeDirs, currentDepth + 1, maxDepth)
                }
            } else if (child.isFile) {
                result.add(child)
            }
        }
    }

    companion object {
        private val DEFAULT_EXCLUDE_DIRS = setOf(
            "Android",
            "AndroidStudioProjects",
            ".thumbnails",
            "cache",
            "temp"
        )
    }
}
