package com.downtify.app.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.downtify.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.FieldKey
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioTagger @Inject constructor(
    private val client: OkHttpClient
) {

    suspend fun tag(file: File, song: Song) = withContext(Dispatchers.IO) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tag ?: throw Exception("Failed to read tag")

        tag.setField(FieldKey.TITLE, song.name)
        tag.setField(FieldKey.ARTIST, song.artists.joinToString(", "))
        tag.setField(FieldKey.ALBUM, song.albumName ?: "")
        tag.setField(FieldKey.YEAR, song.year ?: "")
        tag.setField(FieldKey.TRACK, song.trackNumber?.toString() ?: "1")
        tag.setField(FieldKey.ALBUM_ARTIST, song.artists.firstOrNull() ?: "")

        // Download and embed cover art
        if (!song.coverUrl.isNullOrBlank()) {
            val coverBytes = downloadCoverArt(song.coverUrl)
            if (coverBytes != null) {
                try {
                    val artwork = ArtworkFactory.getNew()
                    artwork.binaryData = coverBytes
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                } catch (e: UnsupportedOperationException) {
                    android.util.Log.w("AudioTagger", "Cover art not supported for this format, skipping")
                }
            }
        }

        audioFile.commit()
    }

    private fun downloadCoverArt(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        } catch (e: Exception) {
            null
        }
    }
}
