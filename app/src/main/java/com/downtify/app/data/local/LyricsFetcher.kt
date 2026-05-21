package com.downtify.app.data.local

import com.downtify.app.domain.model.Song
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsFetcher @Inject constructor(
    private val client: OkHttpClient
) {

    companion object {
        private const val BASE_URL = "https://lrclib.net/api"
    }

    data class Lyrics(val plain: String?, val synced: String?) {
        fun hasAny() = !plain.isNullOrBlank() || !synced.isNullOrBlank()
    }

    suspend fun fetchLyrics(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        if (song.artists.isEmpty() || song.name.isBlank()) return@withContext null

        val params = mutableListOf(
            "track_name" to song.name,
            "artist_name" to song.artists.first()
        )
        if (!song.albumName.isNullOrBlank()) {
            params += "album_name" to song.albumName
        }
        if (song.duration > 0) {
            params += "duration" to song.duration.toString()
        }

        val queryString = params.joinToString("&") { "${it.first}=${it.second.replace(" ", "%20")}" }
        val url = "$BASE_URL/get?$queryString"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Downtify Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) return@withContext null

            val json = JsonParser.parseString(response.body?.string()).asJsonObject
            val plain = json.get("plainLyrics")?.takeUnless { it.isJsonNull }?.asString?.trim()
            val synced = json.get("syncedLyrics")?.takeUnless { it.isJsonNull }?.asString?.trim()

            if (plain.isNullOrBlank() && synced.isNullOrBlank()) return@withContext null
            Lyrics(plain, synced)
        }
    }

    suspend fun saveLyricsSidecar(audioFile: File, lyrics: Lyrics) = withContext(Dispatchers.IO) {
        if (lyrics.synced.isNullOrBlank()) return@withContext
        val lrcFile = File(audioFile.parent, audioFile.nameWithoutExtension + ".lrc")
        lrcFile.writeText(lyrics.synced)
    }
}
