package com.downtify.app.domain.model

data class AppSettings(
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val bitrate: Bitrate = Bitrate.KBPS_320,
    val organizeByArtist: Boolean = false,
    val organizeByAlbum: Boolean = false,
    val generateM3u: Boolean = true,
    val downloadLyrics: Boolean = true,
    val maxParallelDownloads: Int = 3,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val soundCloudOAuthToken: String = "",
    val videoFormat: VideoFormat = VideoFormat.MP4,
    val videoQuality: VideoQuality = VideoQuality.P_1080,
    val organizeVideosByArtist: Boolean = false,
    val organizeVideosByAlbum: Boolean = false,
    val welcomeShown: Boolean = false
)

enum class AudioFormat(val extension: String) {
    MP3("mp3"),
    FLAC("flac"),
    M4A("m4a"),
    OGG("ogg"),
    OPUS("opus")
}

enum class Bitrate(val value: String) {
    KBPS_128("128"),
    KBPS_192("192"),
    KBPS_256("256"),
    KBPS_320("320")
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    PORTUGUESE_BR("pt-BR", "Português (BR)")
}

enum class VideoFormat(val extension: String) {
    MP4("mp4"),
    WEBM("webm"),
    MKV("mkv")
}

enum class VideoQuality(val label: String) {
    P_360("360p"),
    P_480("480p"),
    P_720("720p"),
    P_1080("1080p"),
    P_2160("4K")
}
