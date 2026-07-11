package com.txzing.media.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.txzing.media.scanner.storage.StorageManager

/**
 * 存储设备插拔广播接收器，监听 USB/SD 卡等外部存储的挂载和卸载事件
 */
class StorageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MediaScannerApplication ?: return
        val storageManager = app.storageManager
        val scanScheduler = app.scanScheduler

        when (intent.action) {
            Intent.ACTION_MEDIA_MOUNTED -> {
                val path = intent.data?.path
                if (path != null) {
                    storageManager.refreshVolumes()
                    // 新存储挂载时自动触发扫描
                    scanScheduler.scheduleScan(
                        path = path,
                        volumeId = "external_${System.currentTimeMillis()}"
                    )
                }
            }

            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_BAD_REMOVAL,
            Intent.ACTION_MEDIA_EJECT -> {
                val path = intent.data?.path
                if (path != null) {
                    val volume = storageManager.getVolumeForPath(path)
                    if (volume != null) {
                        scanScheduler.cancelVolumeTasks(volume.id)
                    }
                    storageManager.refreshVolumes()
                }
            }
        }
    }
}
