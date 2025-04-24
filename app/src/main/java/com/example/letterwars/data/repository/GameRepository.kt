package com.example.letterwars.data.repository

import com.example.letterwars.data.firebase.FirestoreGameDataSource
import com.example.letterwars.data.firebase.FireBaseGameDataSource
import com.example.letterwars.data.model.GameDuration

class GameRepository(
    private val queueDataSource: FirestoreGameDataSource = FirestoreGameDataSource(), // join/leave
    private val gameDataSource: FireBaseGameDataSource = FireBaseGameDataSource()     // createGame
) {

    suspend fun joinMatchQueue(
        playerId: String,
        duration: GameDuration,
        onGameReady: (Boolean, String?) -> Unit
    ) {
        val (matched, opponentId, _) = queueDataSource.joinQueue(playerId, duration)

        if (matched && opponentId != null) {
            val (success, gameId) = gameDataSource.createGame(
                player1Id = opponentId,
                player2Id = playerId,
                duration = duration
            )
            onGameReady(success, gameId)
        } else {
            onGameReady(false, null)
        }
    }

    suspend fun leaveMatchQueue(
        playerId: String,
        duration: GameDuration,
        onLeft: (Boolean) -> Unit
    ) {
        val success = queueDataSource.leaveQueue(playerId, duration)
        onLeft(success)
    }
}
