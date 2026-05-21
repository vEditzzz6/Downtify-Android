package com.downtify.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val songId: String,
    val name: String,
    val artists: String,
    val albumName: String? = null,
    val coverUrl: String? = null,
    val duration: Int = 0,
    val filePath: String,
    val fileSize: Long = 0,
    val downloadDate: Long = System.currentTimeMillis(),
    val playlistId: Int? = null,
    val source: String = "spotify"
)

fun DownloadedTrackEntity.toSong(): com.downtify.app.domain.model.Song {
    return com.downtify.app.domain.model.Song(
        songId = songId,
        name = name,
        artists = artists.split(", "),
        albumName = albumName,
        coverUrl = coverUrl,
        duration = duration,
        url = "",
        source = source,
        localFilePath = filePath,
        downloadStatus = com.downtify.app.domain.model.DownloadStatus.DONE
    )
}
