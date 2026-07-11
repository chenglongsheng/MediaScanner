// IMediaScanCallback.aidl
package com.txzing.media.scanner;

/**
 * 媒体扫描进度回调接口
 */
oneway interface IMediaScanCallback {

    /**
     * 扫描进度更新
     * @param current 当前已扫描文件数
     * @param total 总文件数
     */
    void onProgress(int current, int total);

    /**
     * 发现新文件
     * @param path 文件路径
     * @param mediaType 媒体类型 (0=audio, 1=video, 2=image)
     */
    void onFileFound(String path, int mediaType);

    /**
     * 扫描完成
     */
    void onScanCompleted();

    /**
     * 扫描出错
     * @param errorMessage 错误信息
     */
    void onError(String errorMessage);
}
