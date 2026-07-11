package com.txzing.media.scanner.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 媒体文件实体
 */
@Entity(
    tableName = "media",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["media_type"]),
        Index(value = ["volume_id"]),
        Index(value = ["date_modified"])
    ]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "size")
    val size: Long = 0,

    @ColumnInfo(name = "date_modified")
    val dateModified: Long = 0,

    @ColumnInfo(name = "media_type")
    val mediaType: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "duration")
    val duration: Long = 0,

    @ColumnInfo(name = "width")
    val width: Int = 0,

    @ColumnInfo(name = "height")
    val height: Int = 0,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "artist")
    val artist: String = "",

    @ColumnInfo(name = "album")
    val album: String = "",

    @ColumnInfo(name = "volume_id")
    val volumeId: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
