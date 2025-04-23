package com.example.letterwars.data.model

enum class GameDuration(val minutes: Int) {
    QUICK_2(2),
    QUICK_5(5),
    EXTENDED_12H(720),
    EXTENDED_24H(1440);

    companion object {
        fun fromMinutes(min: Int): GameDuration =
            values().first { it.minutes == min }
    }
}

