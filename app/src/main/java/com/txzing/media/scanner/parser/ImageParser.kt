package com.txzing.media.scanner.parser

import android.graphics.BitmapFactory
import java.io.File

/**
 * 图片元数据
 */
data class ImageMetadata(
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val orientation: Int = 0
)

/**
 * 图片文件解析器，提取图片文件的元数据信息
 */
class ImageParser {

    /**
     * 解析图片文件元数据
     */
    fun parse(file: File): ImageMetadata {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            ImageMetadata(
                width = options.outWidth,
                height = options.outHeight,
                mimeType = options.outMimeType ?: "",
                orientation = 0 // 需要 ExifInterface 获取更详细的旋转信息
            )
        } catch (e: Exception) {
            ImageMetadata()
        }
    }

    /**
     * 仅获取图片尺寸（轻量操作）
     */
    fun getDimensions(file: File): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    /**
     * 获取图片 EXIF 方向信息
     */
    fun getExifOrientation(file: File): Int {
        return try {
            val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
            exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        }
    }
}
