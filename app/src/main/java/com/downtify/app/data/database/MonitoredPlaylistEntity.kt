package com.downtify.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_playlists")
data class MonitoredPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val spotifyId: String,
    val name: String,
    val url: String,
    val intervalMinutes: Int = 60,
    val enabled: Boolean = true,
    val lastChecked: Long? = null,
    val lastTrackCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
