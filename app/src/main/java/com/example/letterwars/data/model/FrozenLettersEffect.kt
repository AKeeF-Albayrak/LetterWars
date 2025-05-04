package com.example.letterwars.data.model

data class FrozenLettersEffect(
    val playerId: String,
    val letterIndices: List<Int>,
    val clearTurn: Int
)