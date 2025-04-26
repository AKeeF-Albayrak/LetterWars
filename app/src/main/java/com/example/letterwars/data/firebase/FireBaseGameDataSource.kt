package com.example.letterwars.data.firebase

import android.util.Log
import com.example.letterwars.data.model.*
import com.example.letterwars.data.util.drawLetters
import com.example.letterwars.data.util.generateEmptyBoard
import com.example.letterwars.data.util.generateLetterPool
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
            val startTimeMillis = System.currentTimeMillis()

            val game = Game(
                gameId = gameId,
                player1Id = player1Id,
                player2Id = player2Id,
                currentTurnPlayerId = firstTurnPlayerId,
                duration = duration,
                startTimeMillis = startTimeMillis,
                expireTimeMillis = startTimeMillis + (duration.minutes * 60 * 1000),
                board = generateEmptyBoard().toMutableMap(),
                remainingLetters = letterPool.mapKeys { it.key.toString() }.toMutableMap(),
                currentLetters = drawnLetters.map { it.toString() }.toMutableList()
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

    suspend fun endGame(game: Game) {
        try {
            val winnerId = when {
                game.player1Score > game.player2Score -> game.player1Id
                game.player2Score > game.player1Score -> game.player2Id
                else -> "DRAW"
            }

            firestore.collection("games")
                .document(game.gameId)
                .update(
                    mapOf(
                        "status" to GameStatus.FINISHED,
                        "winnerId" to winnerId
                    )
                )
                .await()

        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ endGame hatası: ${e.message}")
        }
    }

    suspend fun updateGame(game: Game) {
        firestore.collection("games").document(game.gameId).set(game).await()
    }

    suspend fun updatePendingMoves(gameId: String, pendingMoves: Map<String, String>) {
        firestore.collection("games").document(gameId)
            .update("pendingMoves", pendingMoves)
    }

    suspend fun confirmMove(gameId: String, board: Map<String, GameTile>) {
        firestore.collection("games").document(gameId)
            .update(
                mapOf(
                    "board" to board,
                    "pendingMoves" to emptyMap<String, String>()
                )
            )
    }

    fun listenGame(gameId: String, onGameChanged: (Game) -> Unit) {
        firestore.collection("games")
            .document(gameId)
            .addSnapshotListener { snapshot, e ->
                if (snapshot != null && snapshot.exists()) {
                    val game = snapshot.toObject(Game::class.java)
                    if (game != null) {
                        onGameChanged(game)
                    }
                }
            }
    }

    fun listenForGameForPlayer(
        playerId: String,
        onGameFound: (String?) -> Unit
    ): ListenerRegistration {
        return firestore.collection("games")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onGameFound(null)
                    return@addSnapshotListener
                }

                val gameDoc = snapshot?.documents?.firstOrNull { document ->
                    val player1Id = document.getString("player1Id")
                    val player2Id = document.getString("player2Id")
                    (player1Id == playerId || player2Id == playerId)
                }

                if (gameDoc != null) {
                    onGameFound(gameDoc.id)
                }
            }
    }
}
