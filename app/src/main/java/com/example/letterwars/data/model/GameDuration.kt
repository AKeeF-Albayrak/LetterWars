package com.example.letterwars.data.model

enum class GameDuration(val displayName: String, val durationMillis: Long) {
    QUICK_2_MIN("2 Dakika", 2 * 60 * 1000),
    QUICK_5_MIN("5 Dakika", 5 * 60 * 1000),
    EXTENDED_12_HOUR("12 Saat", 12 * 60 * 60 * 1000),
    EXTENDED_24_HOUR("24 Saat", 24 * 60 * 60 * 1000)
}
