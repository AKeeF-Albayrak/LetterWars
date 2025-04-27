package com.example.letterwars.data.model

data class Move(
    val playerId: String = "",
    val word: String = "",
    val positions: List<Position> = emptyList(),
    val scoreEarned: Int = 0,
    val timeMillis: Long = 0L
)

