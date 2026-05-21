package com.downtify.app.domain.model

data class MonitoredPlaylist(
    val id: Int = 0,
    val spotifyId: String,
    val name: String,
    val url: String,
    val intervalMinutes: Int = 60,
    val enabled: Boolean = true,
    val lastChecked: String? = null,
    val lastTrackCount: Int = 0,
    val createdAt: String
)

enum class CheckInterval(val minutes: Int, val labelRes: Int) {
    MIN_15(15, com.downtify.app.R.string.interval_15min),
    MIN_30(30, com.downtify.app.R.string.interval_30min),
    HOUR_1(60, com.downtify.app.R.string.interval_1hour),
    HOUR_6(360, com.downtify.app.R.string.interval_6hours),
    DAY_1(1440, com.downtify.app.R.string.interval_1day)
}
