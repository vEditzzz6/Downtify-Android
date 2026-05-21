# Downtify

Self-hosted music downloader for Android. Download tracks, albums, and playlists from **Spotify**, **SoundCloud**, and **YouTube** — then organize, tag, and play them all from one app.

## Features

### Multi-Source Downloading
- **Spotify** — Resolve links (tracks, albums, playlists) via embed page scraping
- **SoundCloud** — Resolve tracks/playlists via API, download HLS transcoded streams
- **YouTube** — Search videos inline, extract audio/video streams via NewPipe Extractor

### Audio Output
- **Formats:** MP3, FLAC, M4A, OGG, OPUS
- **Bitrates:** 128, 192, 256, 320 kbps (and lossless FLAC)
- **Conversion:** FFmpeg-powered audio conversion and video muxing
- **Metadata tagging:** Automatic embedding of title, artist, album, year, track number, and cover art via JAudioTagger
- **Lyrics:** LRC synced lyrics download from LRCLib

### Video Output
- **Formats:** MP4, WEBM, MKV
- **Qualities:** 360p, 480p, 720p, 1080p, 2160p (4K)
- Audio/video stream selection and muxing

### Library Management
- Automatic organization by **artist** and **album** directory structure
- M3U playlist generation
- Downloaded tracks and videos searchable by name, artist, or album
- Album, artist, and playlist grouping views

### Playlist Monitoring
- Monitor Spotify playlists for new tracks
- Configurable check intervals (15 min, 30 min, 1 hr, 6 hr, 1 day)
- Automatic download of new tracks on detection

### Audio Playback
- In-app player with play/pause, skip, seek, shuffle, repeat modes
- Queue management
- Sleep timer
- Playback speed control (0.5x–3.0x)
- LRC synced lyrics display
- Background playback via Media3/ExoPlayer MediaSession
- Mini player (persistent bottom bar)

### Download Management
- Parallel downloads (configurable: 1–100)
- Chunked multi-threaded downloads for large files
- Download progress with speed indicators (download/upload)
- Foreground service with notifications
- Video quality picker before download

### Settings
- Output format and bitrate selection
- Artist/album organization toggles
- M3U generation toggle
- Lyrics download toggle
- Max parallel downloads
- Language selection (English, Spanish, Portuguese)
- Video format and quality preferences
- SoundCloud OAuth token configuration

## Architecture

**Pattern:** MVVM with layered architecture

```
ui/           → Compose screens, navigation, theme, MainActivity
viewmodel/    → 6 ViewModels with StateFlow UI states
domain/       → Domain models (Song, Settings, Playlist, DownloadJob)
data/
  api/        → Retrofit service + WebSocket client
  database/   → Room (3 entities: tracks, videos, monitored playlists)
  local/      → Scrapers, extractors, download pipeline, converters
  repository/ → Central repository + DataStore settings
di/           → Hilt dependency injection module
service/      → Foreground download service + Media3 playback service
util/         → Network and storage utilities
```

## Tech Stack

| Category | Libraries |
|----------|-----------|
| **Language** | Kotlin 2.1.0 |
| **UI** | Jetpack Compose (Material 3), Compose BOM 2024.02 |
| **DI** | Hilt 2.54 |
| **Database** | Room 2.6.1 (3 entities, Flow-based DAOs) |
| **Preferences** | DataStore Preferences 1.0.0 |
| **Networking** | OkHttp 4.12.0, Retrofit 2.11.0, Gson 2.10.1 |
| **WebSocket** | Java-WebSocket 1.6.0 |
| **Playback** | Media3 / ExoPlayer 1.2.1 |
| **Image Loading** | Coil 2.5.0 |
| **YouTube** | NewPipe Extractor v0.26.1 |
| **FFmpeg** | ffmpeg-kit-full 6.1.4 |
| **Audio Tagging** | JAudioTagger 2.2.3 |
| **Background Work** | WorkManager 2.9.0 |
| **Crash Reporting** | Firebase Crashlytics |
| **Navigation** | Navigation Compose 2.7.7 |

## Project Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   └── java/com/downtify/app/
│       ├── DowntifyApplication.kt              # @HiltAndroidApp entry
│       ├── data/
│       │   ├── api/
│       │   │   ├── DowntifyApiService.kt       # Retrofit API interface
│       │   │   ├── DowntifyWebSocketClient.kt  # WebSocket for real-time server
│       │   │   └── RetrofitClient.kt           # Retrofit singleton
│       │   ├── database/
│       │   │   ├── DowntifyDatabase.kt         # Room DB (v3)
│       │   │   ├── DownloadedTrackDao.kt
│       │   │   ├── DownloadedTrackEntity.kt
│       │   │   ├── DownloadedVideoDao.kt
│       │   │   ├── DownloadedVideoEntity.kt
│       │   │   ├── MonitoredPlaylistDao.kt
│       │   │   └── MonitoredPlaylistEntity.kt
│       │   ├── local/
│       │   │   ├── AndroidDownloader.kt        # NewPipe Downloader adapter
│       │   │   ├── AudioConverter.kt           # FFmpeg conversion
│       │   │   ├── AudioTagger.kt              # Metadata tagging
│       │   │   ├── DownloadPipeline.kt         # Core download orchestrator
│       │   │   ├── LyricsFetcher.kt            # LRCLib lyrics
│       │   │   ├── M3UGenerator.kt             # Playlist file generation
│       │   │   ├── NativeDownloader.kt         # Chunked HTTP downloader
│       │   │   ├── SoundCloudScraper.kt        # SoundCloud API scraping
│       │   │   ├── SpotifyScraper.kt           # Spotify embed scraping
│       │   │   └── YouTubeExtractor.kt         # NewPipe YouTube extraction
│       │   └── repository/
│       │       ├── DowntifyRepository.kt       # Central data repository
│       │       └── SettingsRepository.kt       # DataStore settings
│       ├── di/
│       │   └── AppModule.kt                   # Hilt module
│       ├── domain/model/
│       │   ├── DownloadJob.kt
│       │   ├── Playlist.kt
│       │   ├── Settings.kt
│       │   └── Song.kt
│       ├── service/
│       │   ├── DownloadService.kt              # Foreground download service
│       │   ├── MusicControllerManager.kt       # Media controller singleton
│       │   └── PlaybackService.kt              # Media3 playback service
│       ├── ui/
│       │   ├── MainActivity.kt                 # Entry activity
│       │   ├── navigation/
│       │   │   └── DowntifyNavHost.kt          # NavHost + bottom nav
│       │   ├── screens/
│       │   │   ├── home/HomeScreen.kt          # URL resolver + download list
│       │   │   ├── search/SearchScreen.kt      # YouTube search + results
│       │   │   ├── library/LibraryScreen.kt    # Downloaded content browser
│       │   │   ├── player/PlayerScreen.kt      # Audio player
│       │   │   ├── player/MiniPlayer.kt        # Persistent mini player bar
│       │   │   ├── monitor/MonitorScreen.kt    # Playlist monitoring
│       │   │   └── settings/SettingsScreen.kt  # App settings
│       │   └── theme/
│       │       ├── Theme.kt                    # Material 3 theme
│       │       └── Type.kt                     # Typography
│       ├── util/
│       │   ├── NetworkUtils.kt                 # Connectivity checks
│       │   └── StorageUtils.kt                 # File system helpers
│       └── viewmodel/
│           ├── HomeViewModel.kt                # URL resolution + downloads
│           ├── LibraryViewModel.kt             # Library browsing
│           ├── MonitorViewModel.kt             # Playlist monitoring
│           ├── PlayerViewModel.kt              # Playback state
│           ├── SearchViewModel.kt              # YouTube search
│           └── SettingsViewModel.kt            # Settings state
```

## Screens

| Screen | Description |
|--------|-------------|
| **Home** | Paste Spotify/SoundCloud links, resolve tracks, download individually or all at once |
| **Search** | Search YouTube for audio/video content, preview and download results |
| **Library** | Browse downloaded tracks, albums, artists, playlists, and videos with search |
| **Player** | Full audio player with queue, shuffle, sleep timer, speed control, synced lyrics |
| **Monitor** | Add and manage Spotify playlist monitoring with configurable intervals |
| **Settings** | Configure formats, bitrates, organization, language, video preferences |

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Network requests to streaming services |
| `ACCESS_NETWORK_STATE` | Network connectivity checks |
| `MANAGE_EXTERNAL_STORAGE` | Save downloads to Music folder (Android 11+) |
| `READ_EXTERNAL_STORAGE` | Legacy storage read (Android 10 and below) |
| `WRITE_EXTERNAL_STORAGE` | Legacy storage write (Android 9 and below) |
| `READ_MEDIA_AUDIO` | Access audio files (Android 13+) |
| `FOREGROUND_SERVICE` | Background download and playback |
| `POST_NOTIFICATIONS` | Download and playback notifications (Android 13+) |
| `WAKE_LOCK` | Keep device awake during downloads |

## Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 36

### Setup
```bash
git clone <repo-url>
cd Downtify
```

Open in Android Studio, sync Gradle, and build.

### Configuration
- **Spotify/SoundCloud/YouTube** — No API keys required; uses web scraping and NewPipe Extractor
- **SoundCloud** — Optional: set an OAuth token in Settings for authenticated access
- **Firebase** — Add your own `google-services.json` for Crashlytics (optional; remove the plugin and dependencies if not needed)

## Translations

- English (default)
- Spanish (`values-es/`)
- Portuguese (Brazil) (`values-pt-rBR/`)

## License

This project is self-hosted and intended for personal use. Respect the terms of service of the respective streaming platforms.

---

**Version:** 2.7.0-native  |  **Package:** `com.downtify.app`  |  **Min SDK:** 24  |  **Target SDK:** 36
