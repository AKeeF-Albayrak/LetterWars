package com.example.letterwars.data.model

import com.example.letterwars.data.util.generateEmptyBoard
import com.example.letterwars.data.util.generateLetterPool

data class Game(
    val gameId: String = "",
    val player1Id: String = "",
    val player2Id: String = "",
    val currentTurnPlayerId: String = "",
    val player1Score: Int = 0,
    val player2Score: Int = 0,
    val status: GameStatus = GameStatus.IN_PROGRESS,
    val duration: GameDuration = GameDuration.QUICK_2,
    val startTimeMillis: Long = 0L,
    val expireTimeMillis: Long = 0L,
    val board: MutableMap<String, GameTile> = generateEmptyBoard().toMutableMap(),
    val remainingLetters: MutableMap<String, Int> = generateLetterPool().mapKeys { it.key.toString() }.toMutableMap(),
    val currentLetters1: MutableList<String> = mutableListOf(),
    val currentLetters2: MutableList<String> = mutableListOf(),
    val moveHistory: MutableList<Move> = mutableListOf(),
    val winnerId: String? = null,
    val pendingMoves: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val players: List<String> = emptyList(),

    val areaBlockActivatedBy: String? = null,
    val areaBlockSide: String? = null, // "left" or "right"
    val areaBlockExpiresAt: Long? = null,
    val frozenLettersEffects: List<FrozenLettersEffect> = emptyList(),
    val extraTurnForPlayerId: String? = null
)