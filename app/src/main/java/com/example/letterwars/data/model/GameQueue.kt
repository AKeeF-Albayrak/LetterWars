package com.example.letterwars.data.model

data class GameQueue(
    val playerId: String = "",

    val durationMinutes: Int = 0,

    val joinedAt: Long = System.currentTimeMillis()
)
