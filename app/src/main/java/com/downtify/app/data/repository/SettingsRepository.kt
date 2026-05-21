package com.downtify.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.downtify.app.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val AUDIO_FORMAT = stringPreferencesKey("audio_format")
        val BITRATE = stringPreferencesKey("bitrate")
        val ORGANIZE_BY_ARTIST = booleanPreferencesKey("organize_by_artist")
        val ORGANIZE_BY_ALBUM = booleanPreferencesKey("organize_by_album")
        val GENERATE_M3U = booleanPreferencesKey("generate_m3u")
        val DOWNLOAD_LYRICS = booleanPreferencesKey("download_lyrics")
        val MAX_PARALLEL_DOWNLOADS = intPreferencesKey("max_parallel_downloads")
        val LANGUAGE = stringPreferencesKey("language")
        val SOUNDCLOUD_OAUTH_TOKEN = stringPreferencesKey("soundcloud_oauth_token")
        val VIDEO_FORMAT = stringPreferencesKey("video_format")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val ORGANIZE_VIDEOS_BY_ARTIST = booleanPreferencesKey("organize_videos_by_artist")
        val ORGANIZE_VIDEOS_BY_ALBUM = booleanPreferencesKey("organize_videos_by_album")
        val WELCOME_SHOWN = booleanPreferencesKey("welcome_shown")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                audioFormat = AudioFormat.entries.find { it.name == preferences[PreferencesKeys.AUDIO_FORMAT] } ?: AudioFormat.MP3,
                bitrate = Bitrate.entries.find { it.value == preferences[PreferencesKeys.BITRATE] } ?: Bitrate.KBPS_320,
                organizeByArtist = preferences[PreferencesKeys.ORGANIZE_BY_ARTIST] ?: false,
                organizeByAlbum = preferences[PreferencesKeys.ORGANIZE_BY_ALBUM] ?: false,
                generateM3u = preferences[PreferencesKeys.GENERATE_M3U] ?: true,
                downloadLyrics = preferences[PreferencesKeys.DOWNLOAD_LYRICS] ?: true,
                maxParallelDownloads = preferences[PreferencesKeys.MAX_PARALLEL_DOWNLOADS] ?: 3,
                language = AppLanguage.entries.find { it.code == preferences[PreferencesKeys.LANGUAGE] } ?: AppLanguage.ENGLISH,
                soundCloudOAuthToken = preferences[PreferencesKeys.SOUNDCLOUD_OAUTH_TOKEN] ?: "",
                videoFormat = VideoFormat.entries.find { it.name == preferences[PreferencesKeys.VIDEO_FORMAT] } ?: VideoFormat.MP4,
                videoQuality = VideoQuality.entries.find { it.name == preferences[PreferencesKeys.VIDEO_QUALITY] } ?: VideoQuality.P_1080,
                organizeVideosByArtist = preferences[PreferencesKeys.ORGANIZE_VIDEOS_BY_ARTIST] ?: false,
                organizeVideosByAlbum = preferences[PreferencesKeys.ORGANIZE_VIDEOS_BY_ALBUM] ?: false,
                welcomeShown = preferences[PreferencesKeys.WELCOME_SHOWN] ?: false
            )
        }

    suspend fun updateAudioFormat(format: AudioFormat) {
        context.dataStore.edit { it[PreferencesKeys.AUDIO_FORMAT] = format.name }
    }

    suspend fun updateBitrate(bitrate: Bitrate) {
        context.dataStore.edit { it[PreferencesKeys.BITRATE] = bitrate.value }
    }

    suspend fun updateOrganizeByArtist(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ORGANIZE_BY_ARTIST] = enabled }
    }

    suspend fun updateOrganizeByAlbum(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ORGANIZE_BY_ALBUM] = enabled }
    }

    suspend fun updateGenerateM3U(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.GENERATE_M3U] = enabled }
    }

    suspend fun updateDownloadLyrics(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DOWNLOAD_LYRICS] = enabled }
    }

    suspend fun updateMaxParallelDownloads(count: Int) {
        context.dataStore.edit { it[PreferencesKeys.MAX_PARALLEL_DOWNLOADS] = count }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { it[PreferencesKeys.LANGUAGE] = language.code }
    }

    suspend fun updateSoundCloudOAuthToken(token: String) {
        context.dataStore.edit { it[PreferencesKeys.SOUNDCLOUD_OAUTH_TOKEN] = token }
    }

    suspend fun updateVideoFormat(format: VideoFormat) {
        context.dataStore.edit { it[PreferencesKeys.VIDEO_FORMAT] = format.name }
    }

    suspend fun updateVideoQuality(quality: VideoQuality) {
        context.dataStore.edit { it[PreferencesKeys.VIDEO_QUALITY] = quality.name }
    }

    suspend fun updateOrganizeVideosByArtist(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ORGANIZE_VIDEOS_BY_ARTIST] = enabled }
    }

    suspend fun updateOrganizeVideosByAlbum(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ORGANIZE_VIDEOS_BY_ALBUM] = enabled }
    }

    suspend fun markWelcomeShown() {
        context.dataStore.edit { it[PreferencesKeys.WELCOME_SHOWN] = true }
    }
}
