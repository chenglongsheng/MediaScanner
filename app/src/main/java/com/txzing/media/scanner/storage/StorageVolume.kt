package com.txzing.media.scanner.storage

/**
 * 存储卷数据类，表示一个挂载的存储设备
 */
data class StorageVolume(
    val id: String,
    val path: String,
    val label: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val state: VolumeState,
    val totalBytes: Long,
    val availableBytes: Long
)

enum class VolumeState {
    MOUNTED,
    UNMOUNTED,
    REMOVED,
    BAD_REMOVAL,
    EJECTING
}
