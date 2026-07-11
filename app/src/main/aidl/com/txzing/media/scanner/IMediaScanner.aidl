// IMediaScanner.aidl
package com.txzing.media.scanner;

import com.txzing.media.scanner.IMediaScanCallback;

/**
 * MediaScanner 主接口，用于跨进程通信
 */
interface IMediaScanner {

    /**
     * 开始扫描指定路径
     */
    void startScan(String path);

    /**
     * 停止当前扫描
     */
    void stopScan();

    /**
     * 获取扫描状态
     */
    int getScanStatus();

    /**
     * 注册扫描回调
     */
    void registerCallback(IMediaScanCallback callback);

    /**
     * 取消注册扫描回调
     */
    void unregisterCallback(IMediaScanCallback callback);

    // 扫描状态常量
    const int STATUS_IDLE = 0;
    const int STATUS_SCANNING = 1;
    const int STATUS_PAUSED = 2;
    const int STATUS_COMPLETED = 3;
    const int STATUS_ERROR = 4;
}
