package com.txzing.media.scanner.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 存储管理器，负责监听存储设备插拔事件，管理扫描触发
 */
class StorageManager(private val context: Context) {

    private val volumeRepository = VolumeRepository(context)
    private val handler = Handler(Looper.getMainLooper())

    private val _availableVolumes = MutableStateFlow<List<StorageVolume>>(emptyList())
    val availableVolumes: StateFlow<List<StorageVolume>> = _availableVolumes.asStateFlow()

    private val _mountedVolumes = MutableStateFlow<List<StorageVolume>>(emptyList())
    val mountedVolumes: StateFlow<List<StorageVolume>> = _mountedVolumes.asStateFlow()

    private var onVolumeMounted: ((StorageVolume) -> Unit)? = null
    private var onVolumeUnmounted: ((StorageVolume) -> Unit)? = null

    private val storageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_MEDIA_MOUNTED -> {
                    val path = intent.data?.path
                    if (path != null) {
                        refreshVolumes()
                        volumeRepository.getVolumeForPath(path)?.let { volume ->
                            onVolumeMounted?.invoke(volume)
                        }
                    }
                }
                Intent.ACTION_MEDIA_UNMOUNTED,
                Intent.ACTION_MEDIA_BAD_REMOVAL,
                Intent.ACTION_MEDIA_EJECT -> {
                    val path = intent.data?.path
                    if (path != null) {
                        volumeRepository.getVolumeForPath(path)?.let { volume ->
                            onVolumeUnmounted?.invoke(volume)
                        }
                        refreshVolumes()
                    }
                }
            }
        }
    }

    /**
     * 初始化存储管理器，注册广播接收器
     */
    fun initialize() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }

        context.registerReceiver(storageReceiver, filter)
        refreshVolumes()
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            context.unregisterReceiver(storageReceiver)
        } catch (e: Exception) {
            // 接收器可能未注册
        }
    }

    /**
     * 设置存储卷挂载回调
     */
    fun setOnVolumeMountedCallback(callback: (StorageVolume) -> Unit) {
        onVolumeMounted = callback
    }

    /**
     * 设置存储卷卸载回调
     */
    fun setOnVolumeUnmountedCallback(callback: (StorageVolume) -> Unit) {
        onVolumeUnmounted = callback
    }

    /**
     * 刷新存储卷列表
     */
    fun refreshVolumes() {
        val volumes = volumeRepository.getMountedVolumes()
        _availableVolumes.value = volumes
        _mountedVolumes.value = volumes.filter { it.state == VolumeState.MOUNTED }
    }

    /**
     * 获取所有需要扫描的路径
     */
    fun getScanPaths(): List<String> {
        return mountedVolumes.value.map { it.path }
    }

    /**
     * 获取指定路径的存储卷
     */
    fun getVolumeForPath(path: String): StorageVolume? {
        return volumeRepository.getVolumeForPath(path)
    }
}
