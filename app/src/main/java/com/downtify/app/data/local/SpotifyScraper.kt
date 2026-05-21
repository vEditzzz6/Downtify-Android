package com.downtify.app.data.local

import android.util.Log
import com.downtify.app.domain.model.Song
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyScraper @Inject constructor(
    private val client: OkHttpClient
) {

    private val gson = Gson()

    companion object {
        private const val TAG = "SpotifyScraper"
        private const val EMBED_BASE = "https://open.spotify.com/embed"
        private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val SPOTIFY_URL_REGEX = Regex(
            "(?:https?://)?(?:open\\.)?spotify\\.com/" +
            "(?:intl-[a-z]{2}/)?" +
            "(track|album|playlist|artist|episode|show)/" +
            "([A-Za-z0-9]+)"
        )
    }

    data class SpotifyUrl(val type: Type, val id: String)
    enum class Type { TRACK, ALBUM, PLAYLIST, ARTIST, EPISODE, SHOW }

    fun parseUrl(url: String): SpotifyUrl? {
        if (url.startsWith("spotify:")) {
            val parts = url.split(":")
            if (parts.size >= 3) {
                return try {
                    SpotifyUrl(Type.valueOf(parts[1].uppercase()), parts[2])
                } catch (e: IllegalArgumentException) { null }
            }
        }
        val match = SPOTIFY_URL_REGEX.find(url) ?: return null
        return SpotifyUrl(
            Type.valueOf(match.groupValues[1].uppercase()),
            match.groupValues[2]
        )
    }

    suspend fun resolveTrack(trackId: String): Song {
        val entity = fetchEmbed("track", trackId)
        return parseTrack(entity, trackId)
    }

    suspend fun resolveAlbum(albumId: String): List<Song> {
        val entity = fetchEmbed("album", albumId)
        val albumName = entity.get("name")?.asString ?: ""
        val coverUrl = extractCoverUrl(entity)
        val trackList = entity.getAsJsonObject("tracks")?.getAsJsonArray("items")
            ?: entity.getAsJsonArray("trackList")
            ?: return emptyList()

        return trackList.mapIndexedNotNull { index, item ->
            val track = extractTrackFromRow(item.asJsonObject)
            track?.copy(
                albumName = albumName,
                coverUrl = coverUrl,
                trackNumber = index + 1,
                albumTrackTotal = trackList.size()
            )
        }
    }

    suspend fun resolvePlaylist(playlistId: String): List<Song> {
        return try {
            val allTracks = mutableListOf<Song>()
            var offset = 0
            var coverUrl = ""
            var maxPages = 200 // safety limit (200 pages x 100 tracks = 20k max)
            
            while (maxPages-- > 0) {
                val entity = fetchEmbed("playlist", playlistId, if (offset == 0) null else offset)
                if (offset == 0) {
                    coverUrl = extractCoverUrl(entity)
                    Log.d(TAG, "Cover URL: $coverUrl")
                }
                
                val trackListElement = entity.get("trackList")
                if (trackListElement == null || trackListElement.isJsonNull || !trackListElement.isJsonArray) break
                
                val trackList = trackListElement.asJsonArray
                if (trackList.size() == 0) break
                
                Log.d(TAG, "Found ${trackList.size()} tracks at offset $offset (total so far: ${allTracks.size})")
                
                val parsed = trackList.mapIndexedNotNull { index, item ->
                    try {
                        if (item.isJsonObject) {
                            val track = extractTrackFromRow(item.asJsonObject)
                            track?.copy(coverUrl = coverUrl)
                        } else null
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing track $index at offset $offset", e)
                        null
                    }
                }
                
                allTracks.addAll(parsed)
                
                // If we got fewer than 100 tracks, we're done paginating
                if (trackList.size() < 100) break
                offset += 100
            }
            
            Log.d(TAG, "Total tracks resolved: ${allTracks.size}")
            allTracks
        } catch (e: Exception) {
            Log.e(TAG, "resolvePlaylist failed", e)
            throw e
        }
    }

    private suspend fun fetchEmbed(type: String, id: String, offset: Int? = null): JsonObject {
        val baseUrl = "$EMBED_BASE/$type/$id"
        val url = if (offset != null) "$baseUrl?offset=$offset" else baseUrl
        Log.d(TAG, "Fetching embed: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        try {
            val response = client.newCall(request).execute()
            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response headers: ${response.headers}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Error body: ${errorBody?.take(500)}")
                throw Exception("Spotify embed fetch failed: ${response.code}")
            }
            
            val html = response.body?.string() ?: throw Exception("Empty response")
            Log.d(TAG, "HTML length: ${html.length}")
            Log.d(TAG, "HTML first 500 chars: ${html.take(500)}")
            
            // Find __NEXT_DATA__ script tag
            val startTag = """<script id="__NEXT_DATA__" type="application/json">"""
            val endTag = "</script>"
            val startIndex = html.indexOf(startTag)
            if (startIndex == -1) {
                // Fallback: try alternative tag format
                val altStart = html.indexOf("""<script id="__NEXT_DATA__">""")
                if (altStart == -1) {
                    Log.e(TAG, "__NEXT_DATA__ not found in HTML")
                    throw Exception("Spotify embed payload not found")
                }
                val jsonStart = altStart + """<script id="__NEXT_DATA__">""".length
                val endIndex = html.indexOf(endTag, jsonStart)
                if (endIndex == -1) throw Exception("Script end tag not found")
                val jsonStr = html.substring(jsonStart, endIndex).trim()
                Log.d(TAG, "Extracted JSON length: ${jsonStr.length}")
                return extractEntity(JsonParser.parseString(jsonStr).asJsonObject)
            }
            
            val jsonStart = startIndex + startTag.length
            val endIndex = html.indexOf(endTag, jsonStart)
            if (endIndex == -1) throw Exception("Script end tag not found")
            val jsonStr = html.substring(jsonStart, endIndex).trim()
            Log.d(TAG, "Extracted JSON length: ${jsonStr.length}")
            return extractEntity(JsonParser.parseString(jsonStr).asJsonObject)
        } catch (e: Exception) {
            Log.e(TAG, "Fetch embed failed", e)
            throw e
        }
    }

    private fun extractEntity(payload: JsonObject): JsonObject {
        val pageProps = payload.getAsJsonObject("props")?.getAsJsonObject("pageProps")
        if (pageProps == null) {
            Log.e(TAG, "No pageProps found. Payload keys: ${payload.keySet()}")
            throw Exception("No pageProps in Spotify response")
        }
        
        val candidates = listOf(
            pageProps.getAsJsonObject("state")?.getAsJsonObject("data")?.getAsJsonObject("entity"),
            pageProps.getAsJsonObject("entity"),
            pageProps.getAsJsonObject("data")?.getAsJsonObject("entity")
        )
        
        val entity = candidates.firstOrNull { it != null }
        if (entity == null) {
            Log.e(TAG, "Entity not found. pageProps keys: ${pageProps.keySet()}")
            throw Exception("Entity not found in Spotify response")
        }
        Log.d(TAG, "Successfully extracted entity")
        return entity
    }

    private fun extractTrackFromRow(row: JsonObject): Song? {
        Log.d(TAG, "Row keys: ${row.keySet()}")
        
        // Extract track ID from URI (format: spotify:track:xxxxx)
        val uri = safeGetString(row, "uri") ?: return null
        val trackId = uri.split(":").lastOrNull() ?: return null
        
        val title = safeGetString(row, "title") ?: ""
        val subtitle = safeGetString(row, "subtitle") ?: ""
        val durationMs = safeGetInt(row, "duration") ?: 0
        val explicit = safeGetBoolean(row, "isExplicit") ?: false
        
        // Parse artists from subtitle (usually "Artist1, Artist2" or similar)
        val artists = if (subtitle.isNotBlank()) {
            subtitle.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        
        return Song(
            songId = trackId,
            name = title,
            artists = artists,
            albumName = "",
            coverUrl = "",
            duration = durationMs / 1000,
            url = "https://open.spotify.com/track/$trackId",
            explicit = explicit,
            releaseDate = "",
            year = "",
            source = "spotify"
        )
    }

    private fun parseTrack(entity: JsonObject, trackId: String): Song {
        val names = extractArtistNames(entity)
        val album = entity.getAsJsonObject("album")
        val albumName = safeGetString(album, "name") ?: ""
        val coverUrl = extractCoverUrl(entity)
        val durationMs = safeGetInt(entity, "duration") ?: safeGetInt(entity, "duration_ms") ?: 0
        val releaseDate = extractReleaseDate(entity)
        val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""

        return Song(
            songId = trackId,
            name = safeGetString(entity, "name") ?: safeGetString(entity, "title") ?: "",
            artists = names,
            albumName = albumName,
            coverUrl = coverUrl,
            duration = durationMs / 1000,
            url = "https://open.spotify.com/track/$trackId",
            explicit = safeGetBoolean(entity, "isExplicit") ?: safeGetBoolean(entity, "explicit") ?: false,
            releaseDate = releaseDate,
            year = year,
            source = "spotify"
        )
    }

    private fun extractArtistNames(entity: JsonObject): List<String> {
        val artists = entity.getAsJsonArray("artists") ?: return emptyList()
        return artists.mapNotNull { 
            if (it.isJsonObject) safeGetString(it.asJsonObject, "name")
            else if (!it.isJsonNull) it.asString 
            else null
        }.filter { it.isNotBlank() }
    }

    private fun extractCoverUrl(entity: JsonObject): String {
        val coverArt = entity.getAsJsonObject("coverArt")?.getAsJsonArray("sources")
        val visual = entity.getAsJsonObject("visualIdentity")?.getAsJsonArray("image")
        val album = entity.getAsJsonObject("album")?.getAsJsonObject("coverArt")?.getAsJsonArray("sources")
        
        val sources = listOfNotNull(coverArt, visual, album).flatten()
        if (sources.isEmpty()) return ""
        
        return sources.maxByOrNull { safeGetInt(it.asJsonObject, "width") ?: 0 }
            ?.asJsonObject?.let { safeGetString(it, "url") } ?: ""
    }

    private fun extractReleaseDate(entity: JsonObject): String {
        val releaseDate = entity.getAsJsonObject("releaseDate") ?: return ""
        val iso = safeGetString(releaseDate, "isoString") ?: ""
        if (iso.isNotEmpty()) return iso.substring(0, 10)
        val year = safeGetInt(releaseDate, "year") ?: 0
        return if (year > 0) "$year" else ""
    }

    private fun safeGetString(obj: JsonObject?, key: String): String? {
        if (obj == null) return null
        val element = obj.get(key) ?: return null
        if (element.isJsonNull) return null
        return element.asString
    }

    private fun safeGetInt(obj: JsonObject?, key: String): Int? {
        if (obj == null) return null
        val element = obj.get(key) ?: return null
        if (element.isJsonNull) return null
        return try { element.asInt } catch (e: Exception) { null }
    }

    private fun safeGetBoolean(obj: JsonObject?, key: String): Boolean? {
        if (obj == null) return null
        val element = obj.get(key) ?: return null
        if (element.isJsonNull) return null
        return try { element.asBoolean } catch (e: Exception) { null }
    }
}
