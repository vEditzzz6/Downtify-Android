package com.downtify.app.data.local

import android.util.Log
import com.downtify.app.domain.model.Song
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class SoundCloudTrack(
    val song: Song,
    val hlsStreamUrl: String
)

data class SoundCloudUrl(
    val user: String,
    val slug: String
)

@Singleton
class SoundCloudScraper @Inject constructor(
    private val client: OkHttpClient
) {

    companion object {
        private const val TAG = "SoundCloudScraper"
        private val SOUNDCLOUD_URL_REGEX = Regex(
            "(?:https?://)?(?:(?:www|m)\\.)?soundcloud\\.com/([^/?#]+)/([^/?#]+)"
        )
        private const val CLIENT_ID = "tUy37JutyVy6r6JSMLnScSmBwA5DoTXE"
        private const val API_V2_BASE = "https://api-v2.soundcloud.com"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    }

    fun parseUrl(url: String): SoundCloudUrl? {
        if (url.isBlank()) return null
        val match = SOUNDCLOUD_URL_REGEX.find(url) ?: return null
        return SoundCloudUrl(match.groupValues[1], match.groupValues[2])
    }

    private suspend fun resolveShortUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            return response.request.url.toString()
        }
    }

    suspend fun resolveTrack(url: String, oauthToken: String): SoundCloudTrack {
        if (oauthToken.isBlank()) {
            throw IllegalArgumentException("SoundCloud OAuth token not set. Add it in Settings.")
        }

        val resolvedUrl = if (url.contains("on.soundcloud.com") || url.contains("snd.sc")) {
            resolveShortUrl(url)
        } else {
            url
        }

        val parsed = parseUrl(resolvedUrl)
            ?: throw IllegalArgumentException("Invalid SoundCloud URL")
        val permalink = "${parsed.user}/${parsed.slug}"
        Log.d(TAG, "Resolving SoundCloud track: $permalink")

        val trackUrn = resolvePermalink(permalink, oauthToken)
        val trackId = trackUrn.split(":").last()
        Log.d(TAG, "Track URN: $trackUrn, ID: $trackId")

        Log.d(TAG, "Fetching track metadata for ID: $trackId")
        val trackJson = fetchTrackMetadata(trackId, oauthToken)
        Log.d(TAG, "Track metadata received, keys: ${trackJson.keySet()}")

        Log.d(TAG, "Extracting HLS transcoding URL")
        val hlsUrl = extractHlsTranscodingUrl(trackJson)
        Log.d(TAG, "HLS transcoding URL: $hlsUrl")

        Log.d(TAG, "Resolving HLS manifest URL")
        val m3u8Url = resolveHlsManifestUrl(hlsUrl, oauthToken)
        Log.d(TAG, "M3U8 URL: $m3u8Url")

        val song = buildSong(trackJson, trackId)
        Log.d(TAG, "Built song: ${song.name}")

        return SoundCloudTrack(song, m3u8Url)
    }

    private suspend fun resolvePermalink(permalink: String, token: String): String {
        val httpUrl = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api-v2.soundcloud.com")
            .addPathSegment("resolve")
            .addQueryParameter("url", "https://soundcloud.com/$permalink")
            .addQueryParameter("client_id", CLIENT_ID)
            .build()
        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", USER_AGENT)
            .header("Authorization", token)
            .header("Accept", "application/json; charset=utf-8")
            .build()

        val responseBody = executeRequest(request)
        val json = JsonParser.parseString(responseBody)
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            val trackId = obj.get("id")?.takeUnless { it.isJsonNull }?.asLong
                ?: throw Exception("Could not resolve SoundCloud URL")
            val urn = "soundcloud:tracks:$trackId"
            Log.d(TAG, "Resolved track URN: $urn, title: ${obj.get("title")}")
            return urn
        }
        throw Exception("Could not resolve SoundCloud URL: unexpected response")
    }

    private suspend fun fetchTrackMetadata(trackId: String, token: String): JsonObject {
        val httpUrl = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api-v2.soundcloud.com")
            .addPathSegments("tracks/$trackId")
            .addQueryParameter("client_id", CLIENT_ID)
            .build()
        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", USER_AGENT)
            .header("Authorization", token)
            .header("Accept", "application/json")
            .build()

        val responseBody = executeRequest(request)
        val json = JsonParser.parseString(responseBody)
        if (json.isJsonObject) return json.asJsonObject
        throw Exception("Unexpected track metadata response: $responseBody")
    }

    private fun extractHlsTranscodingUrl(trackJson: JsonObject): String {
        val media = trackJson.get("media")?.takeUnless { it.isJsonNull }?.asJsonObject
            ?: throw Exception("No media found for SoundCloud track")
        val transcodings = media.get("transcodings")?.takeUnless { it.isJsonNull }?.asJsonArray
            ?: throw Exception("No transcodings found for SoundCloud track")

        val hlsTranscoding = transcodings.firstOrNull { item ->
            if (item == null || !item.isJsonObject) return@firstOrNull false
            val fmt = item.asJsonObject.get("format")?.takeUnless { it.isJsonNull }?.asJsonObject
            fmt?.get("protocol")?.takeUnless { it.isJsonNull }?.asString == "hls"
        }?.asJsonObject
            ?: throw Exception("No HLS stream available for this SoundCloud track")

        return hlsTranscoding.get("url")?.takeUnless { it.isJsonNull }?.asString
            ?: throw Exception("HLS transcoding URL is empty")
    }

    private suspend fun resolveHlsManifestUrl(transcodingUrl: String, token: String): String {
        val url = transcodingUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("client_id", CLIENT_ID)
            ?.build()?.toString() ?: transcodingUrl

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Authorization", token)
            .header("Accept", "application/json")
            .build()

        val responseBody = executeRequest(request)
        val json = JsonParser.parseString(responseBody)
        if (!json.isJsonObject) throw Exception("Unexpected HLS manifest response")
        return json.asJsonObject.get("url")?.takeUnless { it.isJsonNull }?.asString
            ?: throw Exception("Failed to get HLS manifest URL")
    }

    private fun buildSong(trackJson: JsonObject, trackId: String): Song {
        val title = trackJson.get("title")?.takeUnless { it.isJsonNull }?.asString ?: ""
        val username = trackJson.get("user")?.takeUnless { it.isJsonNull }?.asJsonObject
            ?.get("username")?.takeUnless { it.isJsonNull }?.asString ?: "Unknown"
        val artworkUrl = trackJson.get("artwork_url")?.takeUnless { it.isJsonNull }?.asString ?: ""
        val durationMs = trackJson.get("duration")?.takeUnless { it.isJsonNull }?.asLong ?: 0
        val permalinkUrl = trackJson.get("permalink_url")?.takeUnless { it.isJsonNull }?.asString
            ?: "https://soundcloud.com/$username/$trackId"
        val releaseDate = trackJson.get("release_date")?.takeUnless { it.isJsonNull }?.asString ?: ""
        val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""

        return Song(
            songId = trackId,
            name = title,
            artists = listOf(username),
            albumName = null,
            coverUrl = if (artworkUrl.isNotEmpty()) {
                artworkUrl.replace("-large.jpg", "-t500x500.jpg")
                    .replace("-large.png", "-t500x500.png")
            } else "",
            duration = (durationMs / 1000).toInt(),
            url = permalinkUrl,
            explicit = false,
            releaseDate = releaseDate,
            year = year,
            source = "soundcloud"
        )
    }

    private suspend fun executeRequest(request: Request): String {
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) {
                Log.w(TAG, "API error ${response.code}: ${body.take(500)}")
                throw Exception("SoundCloud API error ${response.code}: ${body.take(200)}")
            }
            body
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: ${request.url}", e)
            throw e
        }
    }
}
