package com.downtify.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedVideoDao {

    @Query("SELECT * FROM downloaded_videos ORDER BY downloadDate DESC")
    fun getAllVideos(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE id = :id")
    suspend fun getVideoById(id: Int): DownloadedVideoEntity?

    @Query("SELECT * FROM downloaded_videos WHERE songId = :songId")
    suspend fun getVideoBySongId(songId: String): DownloadedVideoEntity?

    @Query("SELECT COUNT(*) FROM downloaded_videos")
    suspend fun getVideoCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: DownloadedVideoEntity): Long

    @Update
    suspend fun updateVideo(video: DownloadedVideoEntity)

    @Delete
    suspend fun deleteVideo(video: DownloadedVideoEntity)

    @Query("DELETE FROM downloaded_videos WHERE id = :id")
    suspend fun deleteVideoById(id: Int)

    @Query("DELETE FROM downloaded_videos")
    suspend fun deleteAllVideos()

    @Query("SELECT * FROM downloaded_videos WHERE name LIKE '%' || :query || '%' OR artists LIKE '%' || :query || '%'")
    suspend fun searchVideos(query: String): List<DownloadedVideoEntity>
}
