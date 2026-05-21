package com.downtify.app.domain.model

data class Song(
    val songId: String,
    val name: String,
    val artists: List<String>,
    val albumName: String? = null,
    val coverUrl: String? = null,
    val duration: Int = 0,
    val url: String,
    val explicit: Boolean = false,
    val releaseDate: String? = null,
    val year: String? = null,
    val source: String = "spotify",
    val trackNumber: Int? = null,
    val albumTrackTotal: Int? = null,
    val youtubeId: String? = null,
    val streamUrl: String? = null,
    val localFilePath: String? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE
)

enum class DownloadStatus {
    NONE, QUEUED, DOWNLOADING, CONVERTING, DONE, ERROR
}
