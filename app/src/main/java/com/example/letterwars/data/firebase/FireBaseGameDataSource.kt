package com.example.letterwars.data.firebase

import android.util.Log
import com.example.letterwars.data.model.*
import com.example.letterwars.data.util.drawLetters
import com.example.letterwars.data.util.generateEmptyBoard
import com.example.letterwars.data.util.generateLetterPool
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FireBaseGameDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

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

    suspend fun checkTurnExpirationForUser(userId: String, currentTimeMillis: Long): Boolean {
        return try {
            val querySnapshot = firestore.collection("games")
                .whereArrayContains("players", userId)
                .whereEqualTo("status", GameStatus.IN_PROGRESS.name)
                .get()
                .await()

            var anyGameEnded = false

            for (document in querySnapshot.documents) {
                val game = document.toObject(Game::class.java) ?: continue

                val isOpponentTurnExpired = game.currentTurnPlayerId != userId &&
                        currentTimeMillis >= game.expireTimeMillis

                if (isOpponentTurnExpired) {
                    val loserId = game.currentTurnPlayerId
                    val winnerId = when (loserId) {
                        game.player1Id -> game.player2Id
                        game.player2Id -> game.player1Id
                        else -> null
                    }

                    if (winnerId != null) {
                        val updatedGame = game.copy(
                            status = GameStatus.FINISHED,
                            winnerId = winnerId
                        )

                        firestore.collection("games")
                            .document(game.gameId)
                            .update(
                                mapOf(
                                    "status" to GameStatus.FINISHED.name,
                                    "winnerId" to winnerId
                                )
                            )
                            .await()

                        anyGameEnded = true
                    }
                }
            }

            anyGameEnded
        } catch (e: Exception) {
            false
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

    suspend fun endGame(game: Game, winnerId: String?){
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
        try {
            val updates = mapOf(
                "currentTurnPlayerId" to game.currentTurnPlayerId,
                "expireTimeMillis" to game.expireTimeMillis,
                "startTimeMillis" to game.startTimeMillis,
                "status" to game.status.name,
                "board" to game.board,
                "remainingLetters" to game.remainingLetters,
                "currentLetters1" to game.currentLetters1,
                "currentLetters2" to game.currentLetters2,
                "moveHistory" to game.moveHistory,
                "pendingMoves" to game.pendingMoves,
                "player1Score" to game.player1Score,
                "player2Score" to game.player2Score,
                "winnerId" to game.winnerId
            )

            firestore.collection("games")
                .document(game.gameId)
                .update(updates)
                .await()
        } catch (e: Exception) {
            Log.e("updateGame", "❌ Firestore güncelleme hatası: ${e.message}")
        }
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
        Log.d("FireBaseGameDataSource", "🔵 listenForGameForPlayer başlatıldı: $playerId")

        // Tüm oyunları dinliyoruz ve client-side filtreleme yapıyoruz
        return firestore.collection("games")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FireBaseGameDataSource", "❌ listenForGameForPlayer hatası: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    Log.d("FireBaseGameDataSource", "🔵 Dinleyici: ${snapshot.size()} adet oyun bulundu")

                    // Şu anki zaman
                    val currentTime = System.currentTimeMillis()

                    for (doc in snapshot.documents) {
                        val game = doc.toObject(Game::class.java)
                        if (game != null) {
                            // Bu oyun bu oyuncuyu içeriyor mu ve yeni mi başladı?
                            val isPlayer1 = game.player1Id == playerId
                            val isPlayer2 = game.player2Id == playerId
                            val isInProgress = game.status == GameStatus.IN_PROGRESS

                            // Son 30 saniye içinde başlamış oyunlar
                            val isRecentlyStarted = (currentTime - game.startTimeMillis) < 30000L

                            Log.d("FireBaseGameDataSource", "🔵 Oyun: ${game.gameId}, " +
                                    "P1: $isPlayer1, P2: $isPlayer2, durum: ${game.status}, " +
                                    "başlama: ${game.startTimeMillis}, şu an: $currentTime")

                            if ((isPlayer1 || isPlayer2) && isInProgress && isRecentlyStarted) {
                                Log.d("FireBaseGameDataSource", "🟢 Eşleşme bulundu: ${game.gameId}")
                                onGameFound(game.gameId)
                                return@addSnapshotListener
                            }
                        }
                    }
                } else {
                    Log.d("FireBaseGameDataSource", "🔵 Dinleyici: Oyun bulunamadı")
                }
            }
    }
    /**
     * Basitleştirilmiş sorgu - orderBy kısmı kaldırıldı
     * Bu şekilde kompozit indeks gerekmeden çalışır
     */
    suspend fun findWaitingGame(duration: GameDuration): Game? {
        return try {
            Log.d("FireBaseGameDataSource", "🔵 findWaitingGame başlatıldı: $duration")

            val snapshot = firestore.collection("games")
                .whereEqualTo("status", GameStatus.WAITING_FOR_PLAYER.name)
                .whereEqualTo("duration", duration.name)
                // orderBy kaldırıldı - indeks gerektirmez
                .limit(1)
                .get()
                .await()

            val result = snapshot.documents.firstOrNull()?.toObject(Game::class.java)
            Log.d("FireBaseGameDataSource", "🔵 findWaitingGame sonucu: ${result?.gameId ?: "null"}")

            result
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
            val drawnLetters1 = drawLetters(letterPool, 7)
            val drawnLetters2 = drawLetters(letterPool, 7)
            val currentTime = System.currentTimeMillis()

            val game = Game(
                gameId = gameId,
                player1Id = playerId,
                currentTurnPlayerId = playerId,
                status = GameStatus.WAITING_FOR_PLAYER,
                duration = duration,
                startTimeMillis = 0L,
                expireTimeMillis = 0L,
                board = generateEmptyBoard().toMutableMap(),
                remainingLetters = letterPool.mapKeys { it.key.toString() }.toMutableMap(),
                currentLetters1 = drawnLetters1.map { it.toString() }.toMutableList(),
                currentLetters2 = drawnLetters2.map { it.toString() }.toMutableList(),
                moveHistory = mutableListOf(),
                createdAt = currentTime,
                // İki oyuncuyu listelemek için players alanı eklendi
                players = listOf(playerId)
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

    // Düzeltilmiş - İki oyuncuya da bildirim gönderecek şekilde güncelleme yapılıyor
    /**
     * Bekleyen bir oyuna katılmayı dener.
     * Bu fonksiyon atomik olarak çalışır ve race condition'ları önler.
     */
    suspend fun tryJoinWaitingGame(gameId: String, player2Id: String): Boolean {
        Log.d("FireBaseGameDataSource", "🔵 tryJoinWaitingGame başlatıldı: gameId=$gameId, player2Id=$player2Id")

        return try {
            val gameRef = firestore.collection("games").document(gameId)

            val gameSnapshot = gameRef.get().await()
            val game = gameSnapshot.toObject(Game::class.java)

            if (game == null) {
                Log.d("FireBaseGameDataSource", "❌ tryJoinWaitingGame: Oyun bulunamadı")
                return false
            }

            if (game.status != GameStatus.WAITING_FOR_PLAYER) {
                Log.d("FireBaseGameDataSource", "❌ tryJoinWaitingGame: Oyun bekleme durumunda değil, mevcut durum: ${game.status}")
                return false
            }

            if (game.player2Id.isNotEmpty()) {
                Log.d("FireBaseGameDataSource", "❌ tryJoinWaitingGame: Oyuna zaten başka bir oyuncu katılmış: ${game.player2Id}")
                return false
            }

            val currentTime = System.currentTimeMillis()
            val expireTime = currentTime + (game.duration.minutes * 60 * 1000)

            val result = firestore.runTransaction { transaction ->
                val freshSnapshot = transaction.get(gameRef)
                val freshGame = freshSnapshot.toObject(Game::class.java)

                if (freshGame == null ||
                    freshGame.status != GameStatus.WAITING_FOR_PLAYER ||
                    freshGame.player2Id.isNotEmpty()) {
                    return@runTransaction false
                }

                // Mevcut oyunculara 2. oyuncuyu da ekle
                val updatedPlayers = freshGame.players.toMutableList().apply {
                    if (!contains(player2Id)) add(player2Id)
                }

                val updates = mapOf(
                    "player2Id" to player2Id,
                    "status" to GameStatus.IN_PROGRESS.name,
                    "startTimeMillis" to currentTime,
                    "expireTimeMillis" to expireTime,
                    "players" to updatedPlayers
                )

                transaction.update(gameRef, updates)
                true
            }.await()

            Log.d("FireBaseGameDataSource", "🔵 tryJoinWaitingGame sonuç: $result")
            result
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ tryJoinWaitingGame hatası: ${e.message}", e)
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

    /**
     * Basitleştirilmiş bekleyen oyun sayısı sorgusu
     * Bu şekilde kompozit indeks gerekmeden çalışır
     */
    suspend fun getWaitingGamesCount(duration: GameDuration): Int {
        return try {
            Log.d("FireBaseGameDataSource", "🔵 getWaitingGamesCount başlatıldı: $duration")

            val snapshot = firestore.collection("games")
                .whereEqualTo("status", GameStatus.WAITING_FOR_PLAYER.name)
                .whereEqualTo("duration", duration.name)
                .get()
                .await()

            val count = snapshot.size()
            Log.d("FireBaseGameDataSource", "🔵 getWaitingGamesCount sonucu: $count")

            count
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ getWaitingGamesCount hatası: ${e.message}")
            0 // Hata durumunda 0 dön
        }
    }

    suspend fun getGamesByUser(userId: String): List<Game> {
        return try {
            val snapshot = firestore.collection("games")
                .whereArrayContains("players", userId)
                .get()
                .await()

            snapshot.documents.mapNotNull { it.toObject(Game::class.java) }
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ getGamesByUser hatası: ${e.message}")
            emptyList()
        }
    }
}