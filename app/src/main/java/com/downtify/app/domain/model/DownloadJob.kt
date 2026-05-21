package com.downtify.app.domain.model

data class DownloadJob(
    val songId: String,
    val song: Song,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val message: String = "",
    val filename: String? = null
)
