package com.txzing.media.scanner.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 媒体文件数据访问对象
 */
@Dao
interface MediaDao {

    // ==================== Media 操作 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mediaList: List<MediaEntity>)

    @Update
    suspend fun update(media: MediaEntity)

    @Transaction
    suspend fun insertOrUpdate(media: MediaEntity) {
        val existing = findByPath(media.path)
        if (existing != null) {
            update(media.copy(id = existing.id))
        } else {
            insert(media)
        }
    }

    @Delete
    suspend fun delete(media: MediaEntity)

    @Query("SELECT * FROM media WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): MediaEntity?

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun findById(id: Long): MediaEntity?

    @Query("SELECT * FROM media ORDER BY date_modified DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE media_type = :mediaType ORDER BY date_modified DESC")
    fun getMediaByType(mediaType: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE volume_id = :volumeId ORDER BY date_modified DESC")
    fun getMediaByVolume(volumeId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE name LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchMedia(query: String): Flow<List<MediaEntity>>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun getMediaCount(): Int

    @Query("SELECT COUNT(*) FROM media WHERE media_type = :mediaType")
    suspend fun getMediaCountByType(mediaType: String): Int

    @Query("SELECT COUNT(*) FROM media WHERE volume_id = :volumeId")
    suspend fun getMediaCountByVolume(volumeId: String): Int

    @Query("DELETE FROM media WHERE volume_id = :volumeId")
    suspend fun deleteByVolumeId(volumeId: String)

    @Query("DELETE FROM media")
    suspend fun deleteAll()

    // ==================== StorageVolume 操作 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVolume(storage: StorageEntity): Long

    @Update
    suspend fun updateVolume(storage: StorageEntity)

    @Delete
    suspend fun deleteVolume(storage: StorageEntity)

    @Query("SELECT * FROM storage_volumes")
    fun getAllVolumes(): Flow<List<StorageEntity>>

    @Query("SELECT * FROM storage_volumes WHERE volume_id = :volumeId LIMIT 1")
    suspend fun findVolumeById(volumeId: String): StorageEntity?

    @Query("DELETE FROM storage_volumes WHERE volume_id = :volumeId")
    suspend fun deleteVolumeById(volumeId: String)

    // ==================== ScanTask 操作 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanTask(task: ScanTaskEntity): Long

    @Update
    suspend fun updateScanTask(task: ScanTaskEntity)

    @Delete
    suspend fun deleteScanTask(task: ScanTaskEntity)

    @Query("SELECT * FROM scan_tasks ORDER BY created_at DESC")
    fun getAllScanTasks(): Flow<List<ScanTaskEntity>>

    @Query("SELECT * FROM scan_tasks WHERE task_id = :taskId LIMIT 1")
    suspend fun findScanTaskById(taskId: String): ScanTaskEntity?

    @Query("SELECT * FROM scan_tasks WHERE status = :status ORDER BY created_at DESC")
    fun getScanTasksByStatus(status: String): Flow<List<ScanTaskEntity>>

    @Query("DELETE FROM scan_tasks WHERE status = 'COMPLETED' AND completed_at < :before")
    suspend fun deleteOldCompletedTasks(before: Long)
}
