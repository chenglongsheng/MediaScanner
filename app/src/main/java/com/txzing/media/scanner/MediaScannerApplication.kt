package com.txzing.media.scanner

import android.app.Application
import com.txzing.media.scanner.database.MediaDatabase
import com.txzing.media.scanner.scheduler.ScanScheduler
import com.txzing.media.scanner.storage.StorageManager

/**
 * 媒体扫描器 Application，负责初始化核心组件
 */
class MediaScannerApplication : Application() {

    lateinit var database: MediaDatabase
        private set

    lateinit var storageManager: StorageManager
        private set

    lateinit var scanScheduler: ScanScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化数据库
        database = MediaDatabase.getInstance(this)

        // 初始化存储管理器
        storageManager = StorageManager(this)
        storageManager.initialize()

        // 初始化扫描调度器
        scanScheduler = ScanScheduler(this)
        scanScheduler.initialize()
    }

    override fun onTerminate() {
        storageManager.release()
        scanScheduler.release()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: MediaScannerApplication
            private set
    }
}
