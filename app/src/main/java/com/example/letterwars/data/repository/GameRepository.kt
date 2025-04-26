package com.example.letterwars.data.repository

import com.example.letterwars.data.firebase.FireBaseGameDataSource
import com.example.letterwars.data.model.Game

class GameRepository(
    private val gameDataSource: FireBaseGameDataSource = FireBaseGameDataSource()
) {
    suspend fun getGame(gameId: String): Game? {
        return gameDataSource.getGame(gameId)
    }

    suspend fun endGame(game: Game) {
        gameDataSource.endGame(game)
    }

    suspend fun updateGame(game: Game) {
        gameDataSource.updateGame(game)
    }

    fun listenGame(gameId: String, onGameChanged: (Game) -> Unit) {
        gameDataSource.listenGame(gameId, onGameChanged)
    }


}
