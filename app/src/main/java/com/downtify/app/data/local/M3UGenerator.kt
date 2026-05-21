package com.downtify.app.data.local

import com.downtify.app.domain.model.Song
import com.downtify.app.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3UGenerator @Inject constructor(
    private val storageUtils: StorageUtils
) {

    data class M3UEntry(
        val file: File,
        val title: String,
        val artist: String,
        val duration: Int
    )

    suspend fun generateM3U(
        playlistName: String,
        entries: List<M3UEntry>,
        subdir: String? = null
    ): File? = withContext(Dispatchers.IO) {
        val sanitized = storageUtils.sanitizeFileName(playlistName)
        val targetDir = subdir?.let { storageUtils.getPlaylistDirectory(it) }
            ?: storageUtils.getM3UDirectory()
        
        val m3uFile = File(targetDir, "$sanitized.m3u")
        val lines = mutableListOf("#EXTM3U")
        var kept = 0

        for (entry in entries) {
            if (!entry.file.exists()) continue
            
            val label = if (entry.title.isNotBlank() && entry.artist.isNotBlank()) {
                "${entry.artist} - ${entry.title}"
            } else {
                entry.file.nameWithoutExtension
            }
            
            lines.add("#EXTINF:${entry.duration},$label")
            lines.add(entry.file.name)
            kept++
        }

        if (kept == 0) return@withContext null

        m3uFile.writeText(lines.joinToString("\n") + "\n")
        m3uFile
    }
}
