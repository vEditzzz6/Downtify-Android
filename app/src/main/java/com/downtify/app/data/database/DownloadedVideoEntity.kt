package com.downtify.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
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
    val source: String = "youtube",
    val videoFormat: String = "mp4",
    val videoQuality: String = "1080p"
)
