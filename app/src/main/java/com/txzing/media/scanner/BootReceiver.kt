package com.txzing.media.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机启动广播接收器，设备启动后自动开始媒体扫描
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as? MediaScannerApplication ?: return

            // 设备启动完成后，扫描所有已挂载的存储卷
            app.storageManager.refreshVolumes()
            app.scanScheduler.scanAllVolumes()
        }
    }
}
