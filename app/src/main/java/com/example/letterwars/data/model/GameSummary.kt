package com.example.letterwars.data.model

data class GameSummary(
    val gameId: String = "",
    val opponentUsername: String = "",
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val turnOwnerId: String = "",
    val result: GameResult? = null
)

