package com.downtify.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedTrackDao {

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadDate DESC")
    fun getAllTracks(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE id = :id")
    suspend fun getTrackById(id: Int): DownloadedTrackEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE songId = :songId")
    suspend fun getTrackBySongId(songId: String): DownloadedTrackEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE playlistId = :playlistId ORDER BY id ASC")
    suspend fun getTracksByPlaylistId(playlistId: Int): List<DownloadedTrackEntity>

    @Query("SELECT COUNT(*) FROM downloaded_tracks")
    suspend fun getTrackCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: DownloadedTrackEntity): Long

    @Update
    suspend fun updateTrack(track: DownloadedTrackEntity)

    @Delete
    suspend fun deleteTrack(track: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Int)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun deleteAllTracks()

    @Query("SELECT * FROM downloaded_tracks WHERE name LIKE '%' || :query || '%' OR artists LIKE '%' || :query || '%'")
    suspend fun searchTracks(query: String): List<DownloadedTrackEntity>
}
