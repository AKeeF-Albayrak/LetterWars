package com.example.letterwars.data.repository

import com.example.letterwars.data.firebase.FirestoreGameDataSource
import com.example.letterwars.data.firebase.FireBaseGameDataSource
import com.example.letterwars.data.model.GameDuration
import com.google.firebase.firestore.ListenerRegistration

class QueueRepository(
    private val queueDataSource: FirestoreGameDataSource = FirestoreGameDataSource(),
    private val gameDataSource: FireBaseGameDataSource = FireBaseGameDataSource()
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

    fun listenQueueUserCount(
        duration: GameDuration,
        onUpdate: (Int) -> Unit
    ): ListenerRegistration {
        return queueDataSource.listenQueueUserCount(duration, onUpdate)
    }

    fun listenForGameForPlayer(
        playerId: String,
        onGameFound: (String?) -> Unit
    ): ListenerRegistration {
        return gameDataSource.listenForGameForPlayer(playerId, onGameFound)
    }

}
