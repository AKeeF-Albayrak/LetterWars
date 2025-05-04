package com.example.letterwars.data.model
data class ActiveGameInfo(
    val startedAt: Long,
    val isYourTurn: Boolean,
    val remainingTimeLabel: String,
    val gameId: String
)

