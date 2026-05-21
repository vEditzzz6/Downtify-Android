package com.downtify.app.di

import android.content.Context
import com.downtify.app.data.database.DowntifyDatabase
import com.downtify.app.data.database.DownloadedVideoDao
import com.downtify.app.data.database.DownloadedTrackDao
import com.downtify.app.data.database.MonitoredPlaylistDao
import com.downtify.app.data.local.AudioConverter
import com.downtify.app.data.local.AudioTagger
import com.downtify.app.data.local.DownloadPipeline
import com.downtify.app.data.local.LyricsFetcher
import com.downtify.app.data.local.M3UGenerator
import com.downtify.app.data.local.NativeDownloader
import com.downtify.app.data.local.SoundCloudScraper
import com.downtify.app.data.local.SpotifyScraper
import com.downtify.app.data.local.YouTubeExtractor
import com.downtify.app.data.repository.DowntifyRepository
import com.downtify.app.data.repository.SettingsRepository
import com.downtify.app.util.NetworkUtils
import com.downtify.app.util.StorageUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideSpotifyScraper(client: OkHttpClient): SpotifyScraper = SpotifyScraper(client)

    @Provides
    @Singleton
    fun provideSoundCloudScraper(client: OkHttpClient): SoundCloudScraper = SoundCloudScraper(client)

    @Provides
    @Singleton
    fun provideYouTubeExtractor(): YouTubeExtractor = YouTubeExtractor()

    @Provides
    @Singleton
    fun provideNativeDownloader(
        @ApplicationContext context: Context,
        storageUtils: StorageUtils,
        client: OkHttpClient
    ): NativeDownloader = 
        NativeDownloader(context, storageUtils, client)

    @Provides
    @Singleton
    fun provideAudioConverter(): AudioConverter = AudioConverter()

    @Provides
    @Singleton
    fun provideAudioTagger(client: OkHttpClient): AudioTagger = AudioTagger(client)

    @Provides
    @Singleton
    fun provideLyricsFetcher(client: OkHttpClient): LyricsFetcher = LyricsFetcher(client)

    @Provides
    @Singleton
    fun provideM3UGenerator(storageUtils: StorageUtils): M3UGenerator = 
        M3UGenerator(storageUtils)

    @Provides
    @Singleton
    fun provideDownloadPipeline(
        youTubeExtractor: YouTubeExtractor,
        nativeDownloader: NativeDownloader,
        audioConverter: AudioConverter,
        audioTagger: AudioTagger,
        lyricsFetcher: LyricsFetcher,
        storageUtils: StorageUtils,
        @ApplicationContext context: Context
    ): DownloadPipeline = DownloadPipeline(
        youTubeExtractor,
        nativeDownloader,
        audioConverter,
        audioTagger,
        lyricsFetcher,
        storageUtils,
        context
    )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DowntifyDatabase =
        DowntifyDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideDownloadedTrackDao(database: DowntifyDatabase): DownloadedTrackDao =
        database.downloadedTrackDao()

    @Provides
    @Singleton
    fun provideMonitoredPlaylistDao(database: DowntifyDatabase): MonitoredPlaylistDao =
        database.monitoredPlaylistDao()

    @Provides
    @Singleton
    fun provideDownloadedVideoDao(database: DowntifyDatabase): DownloadedVideoDao =
        database.downloadedVideoDao()

    @Provides
    @Singleton
    fun provideRepository(
        spotifyScraper: SpotifyScraper,
        soundCloudScraper: SoundCloudScraper,
        youTubeExtractor: YouTubeExtractor,
        downloadPipeline: DownloadPipeline,
        m3uGenerator: M3UGenerator,
        trackDao: DownloadedTrackDao,
        playlistDao: MonitoredPlaylistDao,
        videoDao: DownloadedVideoDao,
        settingsRepository: SettingsRepository
    ): DowntifyRepository = DowntifyRepository(
        spotifyScraper,
        soundCloudScraper,
        youTubeExtractor,
        downloadPipeline,
        m3uGenerator,
        trackDao,
        playlistDao,
        videoDao,
        settingsRepository
    )

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideNetworkUtils(@ApplicationContext context: Context): NetworkUtils =
        NetworkUtils(context)

    @Provides
    @Singleton
    fun provideStorageUtils(@ApplicationContext context: Context): StorageUtils =
        StorageUtils(context)
}
