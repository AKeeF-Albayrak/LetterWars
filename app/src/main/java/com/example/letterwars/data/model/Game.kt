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
    val duration: GameDuration = GameDuration.QUICK_2_MIN,
    val startTimeMillis: Long = 0L,
    val board: List<List<Tile>> = generateEmptyBoard(),
    val remainingLetters: Map<Char, Int> = emptyMap(),
    val player1Letters: List<Char> = emptyList(),
    val player2Letters: List<Char> = emptyList(),
    val moveHistory: List<Move> = emptyList()
)


