package com.example.letterwars.data.firebase

import android.util.Log
import com.example.letterwars.data.model.*
import com.example.letterwars.data.util.drawLetters
import com.example.letterwars.data.util.generateEmptyBoard
import com.example.letterwars.data.util.generateLetterPool
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FireBaseGameDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun createGame(
        player1Id: String,
        player2Id: String,
        duration: GameDuration
    ): Pair<Boolean, String?> {
        return try {
            val gameId = UUID.randomUUID().toString()

            val firstTurnPlayerId = if (Math.random() < 0.5) player1Id else player2Id

            val letterPool = generateLetterPool()
            val drawnLetters = drawLetters(letterPool, 7)

            val game = Game(
                gameId = gameId,
                player1Id = player1Id,
                player2Id = player2Id,
                currentTurnPlayerId = firstTurnPlayerId,
                duration = duration,
                board = generateEmptyBoard().toMutableMap(),
                remainingLetters = letterPool.mapKeys { it.key.toString() }.toMutableMap(),
                currentLetters = drawnLetters.map { it.toString() }.toMutableList(),
                startTimeMillis = System.currentTimeMillis()
            )


            firestore.collection("games").document(gameId).set(game).await()
            Pair(true, gameId)
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ createGame hatası: ${e.message}")
            Pair(false, null)
        }
    }

    suspend fun getGame(gameId: String): Game? {
        return try {
            val snapshot = firestore.collection("games")
                .document(gameId)
                .get()
                .await()

            snapshot.toObject(Game::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
