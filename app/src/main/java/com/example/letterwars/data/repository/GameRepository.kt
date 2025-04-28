package com.example.letterwars.data.repository

import com.example.letterwars.data.firebase.FireBaseGameDataSource
import com.example.letterwars.data.model.Game
import com.example.letterwars.data.model.GameTile

class GameRepository(
    private val gameDataSource: FireBaseGameDataSource = FireBaseGameDataSource()
) {
    suspend fun getGame(gameId: String): Game? {
        return gameDataSource.getGame(gameId)
    }

    suspend fun endGame(game: Game) {
        gameDataSource.endGame(game)
    }

    suspend fun endGame(game: Game, winnerId: String){
        gameDataSource.endGame(game, winnerId)
    }

    suspend fun updateGame(game: Game) {
        gameDataSource.updateGame(game)
    }

    fun listenGame(gameId: String, onGameChanged: (Game) -> Unit) {
        gameDataSource.listenGame(gameId, onGameChanged)
    }

    suspend fun updateBoardAndPendingMoves(gameId: String, board: Map<String, GameTile>, pendingMoves: Map<String, String>) {
        gameDataSource.updateBoardAndPendingMoves(gameId, board, pendingMoves)
    }


}
