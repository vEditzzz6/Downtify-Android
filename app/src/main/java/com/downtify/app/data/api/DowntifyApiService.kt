package com.downtify.app.data.api

import com.downtify.app.domain.model.AppSettings
import com.downtify.app.domain.model.MonitoredPlaylist
import com.downtify.app.domain.model.Song
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DowntifyApiService {

    @GET("api/version")
    suspend fun getVersion(): String

    @GET("api/songs/search")
    suspend fun searchSongs(@Query("query") query: String): List<Song>

    @GET("api/song/url")
    suspend fun resolveUrl(@Query("url") url: String): List<Song>

    @GET("api/url")
    suspend fun getUrl(@Query("url") url: String): List<Song>

    @POST("api/download/url")
    suspend fun downloadUrl(
        @Query("url") url: String,
        @Query("client_id") clientId: String,
        @Body clientHints: @JvmSuppressWildcards Map<String, Any> = emptyMap()
    ): String

    @POST("api/download/batch")
    suspend fun downloadBatch(@Body payload: @JvmSuppressWildcards Map<String, Any>): Map<String, Any>

    @GET("api/queue")
    suspend fun getQueue(): List<@JvmSuppressWildcards Map<String, Any>>

    @DELETE("api/queue")
    suspend fun clearQueue(): Map<String, Boolean>

    @DELETE("api/queue/item")
    suspend fun removeQueueItem(@Query("song_id") songId: String): Map<String, Boolean>

    @GET("api/settings")
    suspend fun getSettings(): AppSettings

    @POST("api/settings/update")
    suspend fun updateSettings(@Body settings: @JvmSuppressWildcards Map<String, Any>): AppSettings

    @GET("api/monitor/playlists")
    suspend fun getMonitoredPlaylists(): List<MonitoredPlaylist>

    @POST("api/monitor/playlists")
    suspend fun addMonitoredPlaylist(@Body payload: @JvmSuppressWildcards Map<String, Any>): MonitoredPlaylist

    @PATCH("api/monitor/playlists/{id}")
    suspend fun updateMonitoredPlaylist(
        @Path("id") id: Int,
        @Body payload: @JvmSuppressWildcards Map<String, Any>
    ): MonitoredPlaylist

    @DELETE("api/monitor/playlists/{id}")
    suspend fun deleteMonitoredPlaylist(@Path("id") id: Int): Map<String, Any>

    @POST("api/monitor/playlists/{id}/check")
    suspend fun manualCheck(@Path("id") id: Int): Map<String, String>

    @POST("api/playlist/m3u")
    suspend fun generateM3U(@Body payload: @JvmSuppressWildcards Map<String, Any>): Map<String, Any>

    @GET("list")
    suspend fun listDownloads(): List<String>

    @DELETE("delete")
    suspend fun deleteDownload(@Query("file") file: String): Map<String, Any>

    @GET("cover")
    suspend fun getCover(@Query("file") file: String): Response<ResponseBody>
}
