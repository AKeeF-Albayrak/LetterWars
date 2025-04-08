package com.example.letterwars.data.model

data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val totalGames: Int = 0,
    val wonGames: Int = 0
) {
    fun getWinRate(): Int {
        return if (totalGames == 0) 0 else ((wonGames.toDouble() / totalGames) * 100).toInt()
    }
}

