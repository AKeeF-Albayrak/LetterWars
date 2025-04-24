package com.example.letterwars.data.model

import com.example.letterwars.data.util.generateEmptyBoard

data class Game(
    val gameId: String = "",
    val player1Id: String = "",
    val player2Id: String = "",
    val currentTurnPlayerId: String = "",
    val player1Score: Int = 0,
    val player2Score: Int = 0,
    val status: GameStatus = GameStatus.WAITING_FOR_PLAYER,
    val duration: GameDuration = GameDuration.QUICK_2,
    val startTimeMillis: Long = 0L,
    val board: Map<String, GameTile> = generateEmptyBoard(), // 🔄 Nested list yerine düz map, tip GameTile
    val remainingLetters: Map<String, Int> = emptyMap(), // 🔄 Char → String
    val player1Letters: List<String> = emptyList(), // 🔄 Char → String
    val player2Letters: List<String> = emptyList(), // 🔄 Char → String
    val moveHistory: List<Move> = emptyList()
)


