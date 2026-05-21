package com.downtify.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredPlaylistDao {

    @Query("SELECT * FROM monitored_playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<MonitoredPlaylistEntity>>

    @Query("SELECT * FROM monitored_playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Int): MonitoredPlaylistEntity?

    @Query("SELECT * FROM monitored_playlists WHERE spotifyId = :spotifyId")
    suspend fun getPlaylistBySpotifyId(spotifyId: String): MonitoredPlaylistEntity?

    @Query("SELECT * FROM monitored_playlists WHERE enabled = 1")
    suspend fun getEnabledPlaylists(): List<MonitoredPlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: MonitoredPlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: MonitoredPlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: MonitoredPlaylistEntity)

    @Query("DELETE FROM monitored_playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Int)

    @Query("UPDATE monitored_playlists SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE monitored_playlists SET intervalMinutes = :interval WHERE id = :id")
    suspend fun updateInterval(id: Int, interval: Int)

    @Query("UPDATE monitored_playlists SET lastChecked = :timestamp, lastTrackCount = :count WHERE id = :id")
    suspend fun updateLastCheck(id: Int, timestamp: Long, count: Int)
}
