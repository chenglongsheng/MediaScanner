package com.txzing.media.scanner.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.txzing.media.scanner.database.MediaDatabase

/**
 * 媒体内容提供者，对外提供媒体文件数据的查询和访问
 */
class MediaProvider : ContentProvider() {

    private lateinit var database: MediaDatabase

    companion object {
        const val AUTHORITY = "com.txzing.media.scanner.provider"

        private const val MEDIA = 1
        private const val MEDIA_ID = 2
        private const val MEDIA_TYPE = 3
        private const val STORAGE = 4
        private const val SCAN_TASKS = 5

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "media", MEDIA)
            addURI(AUTHORITY, "media/#", MEDIA_ID)
            addURI(AUTHORITY, "media/type/*", MEDIA_TYPE)
            addURI(AUTHORITY, "storage", STORAGE)
            addURI(AUTHORITY, "scan_tasks", SCAN_TASKS)
        }
    }

    override fun onCreate(): Boolean {
        context?.let {
            database = MediaDatabase.getInstance(it)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // 这里返回 null，实际查询通过 Room DAO 的 Flow 进行
        // ContentProvider 主要用于跨进程数据共享的场景
        return null
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            MEDIA -> "vnd.android.cursor.dir/vnd.txzing.media"
            MEDIA_ID -> "vnd.android.cursor.item/vnd.txzing.media"
            MEDIA_TYPE -> "vnd.android.cursor.dir/vnd.txzing.media"
            STORAGE -> "vnd.android.cursor.dir/vnd.txzing.storage"
            SCAN_TASKS -> "vnd.android.cursor.dir/vnd.txzing.scan_task"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // 媒体文件插入由扫描服务通过 Room DAO 直接处理
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }
}
