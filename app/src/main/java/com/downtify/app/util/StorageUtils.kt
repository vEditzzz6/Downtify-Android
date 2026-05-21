package com.downtify.app.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageUtils @Inject constructor(
    private val context: Context
) {
    companion object {
        const val DOWNTIFY_FOLDER_NAME = "Downtify"
        const val DOWNTIFY_VIDEOS_FOLDER_NAME = "Downtify Videos"
    }

    fun getDownloadsDirectory(): File {
        val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.getExternalStorageDirectory()
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        }
        val downtifyDir = File(baseDir, DOWNTIFY_FOLDER_NAME)
        if (!downtifyDir.exists()) {
            downtifyDir.mkdirs()
        }
        return downtifyDir
    }
    
    fun getArtistDirectory(artistName: String): File {
        val baseDir = getDownloadsDirectory()
        val artistDir = File(baseDir, sanitizeFileName(artistName))
        if (!artistDir.exists()) {
            artistDir.mkdirs()
        }
        return artistDir
    }

    fun getAlbumDirectory(artistName: String, albumName: String): File {
        val artistDir = getArtistDirectory(artistName)
        val albumDir = File(artistDir, sanitizeFileName(albumName))
        if (!albumDir.exists()) {
            albumDir.mkdirs()
        }
        return albumDir
    }

    fun getAlbumOnlyDirectory(albumName: String): File {
        val baseDir = getDownloadsDirectory()
        val albumDir = File(baseDir, sanitizeFileName(albumName))
        if (!albumDir.exists()) {
            albumDir.mkdirs()
        }
        return albumDir
    }
    
    fun getPlaylistDirectory(playlistName: String): File {
        val baseDir = getDownloadsDirectory()
        val playlistDir = File(baseDir, sanitizeFileName(playlistName))
        if (!playlistDir.exists()) {
            playlistDir.mkdirs()
        }
        return playlistDir
    }
    
    fun getM3UDirectory(): File {
        val baseDir = getDownloadsDirectory()
        val m3uDir = File(baseDir, "Playlists")
        if (!m3uDir.exists()) {
            m3uDir.mkdirs()
        }
        return m3uDir
    }

    fun getVideoDownloadsDirectory(): File {
        val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.getExternalStorageDirectory()
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        val downtifyDir = File(baseDir, DOWNTIFY_VIDEOS_FOLDER_NAME)
        if (!downtifyDir.exists()) {
            downtifyDir.mkdirs()
        }
        return downtifyDir
    }

    fun getVideoArtistDirectory(artistName: String): File {
        val baseDir = getVideoDownloadsDirectory()
        val artistDir = File(baseDir, sanitizeFileName(artistName))
        if (!artistDir.exists()) {
            artistDir.mkdirs()
        }
        return artistDir
    }

    fun getVideoAlbumDirectory(artistName: String, albumName: String): File {
        val artistDir = getVideoArtistDirectory(artistName)
        val albumDir = File(artistDir, sanitizeFileName(albumName))
        if (!albumDir.exists()) {
            albumDir.mkdirs()
        }
        return albumDir
    }

    fun getVideoAlbumOnlyDirectory(albumName: String): File {
        val baseDir = getVideoDownloadsDirectory()
        val albumDir = File(baseDir, sanitizeFileName(albumName))
        if (!albumDir.exists()) {
            albumDir.mkdirs()
        }
        return albumDir
    }

    fun listVideoFiles(): List<File> {
        val downloadsDir = getVideoDownloadsDirectory()
        val videoExtensions = setOf(".mp4", ".webm", ".mkv")
        return listFilesRecursive(downloadsDir).filter {
            it.extension.lowercase() in videoExtensions
        }
    }

    fun getVideoAvailableSpace(): Long {
        val stat = android.os.StatFs(getVideoDownloadsDirectory().path)
        return stat.availableBytes
    }
    
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
    }
    
    fun getAvailableSpace(): Long {
        val stat = android.os.StatFs(getDownloadsDirectory().path)
        return stat.availableBytes
    }
    
    fun getFileSize(file: File): Long {
        return if (file.exists()) file.length() else 0L
    }
    
    fun deleteFile(file: File): Boolean {
        return if (file.exists()) file.delete() else false
    }
    
    fun listAudioFiles(): List<File> {
        val downloadsDir = getDownloadsDirectory()
        val audioExtensions = setOf(".mp3", ".m4a", ".flac", ".ogg", ".wav", ".aac", ".opus")
        return listFilesRecursive(downloadsDir).filter { 
            it.extension.lowercase() in audioExtensions 
        }
    }
    
    private fun listFilesRecursive(dir: File): List<File> {
        val result = mutableListOf<File>()
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                result.addAll(listFilesRecursive(file))
            } else {
                result.add(file)
            }
        }
        return result
    }

    fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
