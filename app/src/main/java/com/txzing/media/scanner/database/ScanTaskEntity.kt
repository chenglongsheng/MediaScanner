package com.txzing.media.scanner.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 扫描任务实体
 */
@Entity(
    tableName = "scan_tasks",
    indices = [
        androidx.room.Index(value = ["task_id"], unique = true),
        androidx.room.Index(value = ["status"]),
        androidx.room.Index(value = ["created_at"])
    ]
)
data class ScanTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "task_id")
    val taskId: String,

    @ColumnInfo(name = "root_path")
    val rootPath: String,

    @ColumnInfo(name = "volume_id")
    val volumeId: String,

    @ColumnInfo(name = "priority")
    val priority: Int = 0,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "total_files")
    val totalFiles: Int = 0,

    @ColumnInfo(name = "scanned_files")
    val scannedFiles: Int = 0,

    @ColumnInfo(name = "new_files_found")
    val newFilesFound: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
