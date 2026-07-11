package com.txzing.media.scanner.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 存储卷实体
 */
@Entity(
    tableName = "storage_volumes",
    indices = [
        androidx.room.Index(value = ["volume_id"], unique = true)
    ]
)
data class StorageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "volume_id")
    val volumeId: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "is_primary")
    val isPrimary: Boolean = false,

    @ColumnInfo(name = "is_removable")
    val isRemovable: Boolean = false,

    @ColumnInfo(name = "state")
    val state: String,

    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long = 0,

    @ColumnInfo(name = "available_bytes")
    val availableBytes: Long = 0,

    @ColumnInfo(name = "last_scan_time")
    val lastScanTime: Long = 0,

    @ColumnInfo(name = "media_count")
    val mediaCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
