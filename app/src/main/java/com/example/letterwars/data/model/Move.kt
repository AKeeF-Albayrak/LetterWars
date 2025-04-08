package com.example.letterwars.data.model

data class Move(
    val playerId: String,
    val word: String,
    val positions: List<Pair<Int, Int>>,
    val scoreEarned: Int,
    val timeMillis: Long
)
