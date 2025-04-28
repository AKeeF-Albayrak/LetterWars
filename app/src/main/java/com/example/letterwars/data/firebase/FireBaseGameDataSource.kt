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

    suspend fun endGame(game: Game, winnerId: String){
        try {
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
            Log.e("FireBaseGameDataSource", "❌ endGame2 hatası: ${e.message}")
        }
    }

    suspend fun updateGame(game: Game) {
        firestore.collection("games").document(game.gameId).set(game).await()
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
        onGameFound: (String) -> Unit
    ): ListenerRegistration {
        val firestore = FirebaseFirestore.getInstance()
        return firestore.collection("games")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    val currentTime = System.currentTimeMillis()
                    for (doc in snapshot.documents) {
                        val game = doc.toObject(Game::class.java)
                        if (game != null) {
                            val isPlayerInGame = (game.player1Id == playerId || game.player2Id == playerId)
                            val isGameActive = (game.status == GameStatus.IN_PROGRESS)
                            val isRecentlyCreated = (currentTime - (game.createdAt ?: 0)) <= 5000L

                            if (isPlayerInGame && isGameActive && isRecentlyCreated) {
                                onGameFound(game.gameId)
                                break
                            }
                        }
                    }
                }
            }
    }

    suspend fun findWaitingGame(duration: GameDuration): Game? {
        return try {
            val snapshot = firestore.collection("games")
                .whereEqualTo("status", GameStatus.WAITING_FOR_PLAYER.name)
                .whereEqualTo("duration", duration)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.toObject(Game::class.java)
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ findWaitingGame hatası: ${e.message}")
            null
        }
    }

    suspend fun deleteGame(gameId: String) {
        try {
            firestore.collection("games")
                .document(gameId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ deleteGame hatası: ${e.message}")
        }
    }

    suspend fun createWaitingGame(playerId: String, duration: GameDuration): String? {
        return try {
            val gameId = UUID.randomUUID().toString()

            val letterPool = generateLetterPool()
            val drawnLetters = drawLetters(letterPool, 7)

            val game = Game(
                gameId = gameId,
                player1Id = playerId,
                currentTurnPlayerId = playerId,
                status = GameStatus.WAITING_FOR_PLAYER,
                duration = duration,
                startTimeMillis = System.currentTimeMillis(),
                expireTimeMillis = 0L,
                board = generateEmptyBoard().toMutableMap(),
                remainingLetters = letterPool.mapKeys { it.key.toString() }.toMutableMap(),
                currentLetters = drawnLetters.map { it.toString() }.toMutableList(),
                moveHistory = mutableListOf(),
                createdAt = System.currentTimeMillis()
            )

            firestore.collection("games").document(gameId).set(game).await()
            gameId
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ createWaitingGame hatası: ${e.message}")
            null
        }
    }


    suspend fun findWaitingGameForPlayer(playerId: String): Game? {
        return try {
            val snapshot = firestore.collection("games")
                .whereEqualTo("player1Id", playerId)
                .whereEqualTo("status", GameStatus.WAITING_FOR_PLAYER.name)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.toObject(Game::class.java)
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ findWaitingGameForPlayer hatası: ${e.message}")
            null
        }
    }

    suspend fun tryJoinWaitingGame(gameId: String, player2Id: String): Boolean {
        return try {
            val gameRef = firestore.collection("games").document(gameId)
            val snapshot = gameRef.get().await()
            val game = snapshot.toObject(Game::class.java)

            if (game != null && game.player2Id.isEmpty() && game.status == GameStatus.WAITING_FOR_PLAYER) {
                val updatedGame = game.copy(
                    player2Id = player2Id,
                    currentTurnPlayerId = game.player1Id,
                    status = GameStatus.IN_PROGRESS,
                    startTimeMillis = System.currentTimeMillis(),
                    expireTimeMillis = System.currentTimeMillis() + (game.duration.minutes * 60 * 1000)
                )
                gameRef.set(updatedGame).await()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }


    suspend fun updateBoardAndPendingMoves(gameId: String, board: Map<String, GameTile>, pendingMoves: Map<String, String>) {
        firestore.collection("games").document(gameId)
            .update(
                mapOf(
                    "board" to board,
                    "pendingMoves" to pendingMoves
                )
            )
            .await()
    }


}
