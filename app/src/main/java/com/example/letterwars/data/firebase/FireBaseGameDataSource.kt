package com.example.letterwars.data.firebase

import android.util.Log
import com.example.letterwars.data.model.*
import com.example.letterwars.data.util.drawLetters
import com.example.letterwars.data.util.generateEmptyBoard
import com.example.letterwars.data.util.generateLetterPool
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random
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
                        "status" to GameStatus.FINISHED.name,
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
                        "status" to GameStatus.FINISHED.name,
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
                "winnerId" to game.winnerId,
                // Mayın ve Ödül sistemi için yeni alanlar
                "areaBlockActivatedBy" to game.areaBlockActivatedBy,
                "areaBlockSide" to game.areaBlockSide,
                "areaBlockExpiresAt" to game.areaBlockExpiresAt,
                "frozenLetterIndices" to game.frozenLetterIndices,
                "frozenLettersPlayerId" to game.frozenLettersPlayerId,
                "frozenLettersExpiresAt" to game.frozenLettersExpiresAt,
                "extraTurnForPlayerId" to game.extraTurnForPlayerId
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
                        // Süresi dolmuş efektleri kontrol et
                        val currentTime = System.currentTimeMillis()
                        var needsUpdate = false
                        var updatedGame = game

                        // Bölge bloklamasının süresini kontrol et
                        if (game.areaBlockExpiresAt != null && game.areaBlockExpiresAt < currentTime) {
                            updatedGame = updatedGame.copy(
                                areaBlockActivatedBy = null,
                                areaBlockSide = null,
                                areaBlockExpiresAt = null
                            )
                            needsUpdate = true
                        }

                        // Dondurulmuş harflerin süresini kontrol et
                        if (game.frozenLettersExpiresAt != null && game.frozenLettersExpiresAt < currentTime) {
                            updatedGame = updatedGame.copy(
                                frozenLetterIndices = emptyList(),
                                frozenLettersPlayerId = null,
                                frozenLettersExpiresAt = null
                            )
                            needsUpdate = true
                        }

                        if (needsUpdate) {
                            // updateGame suspend fonksiyonunu bir coroutine içinde çağır
                            kotlinx.coroutines.GlobalScope.launch {
                                updateGame(updatedGame)
                            }
                            onGameChanged(updatedGame)
                        } else {
                            onGameChanged(game)
                        }
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

    suspend fun findWaitingGame(duration: GameDuration): Game? {
        return try {
            Log.d("FireBaseGameDataSource", "🔵 findWaitingGame başlatıldı: $duration")

            val snapshot = firestore.collection("games")
                .whereEqualTo("status", GameStatus.WAITING_FOR_PLAYER.name)
                .whereEqualTo("duration", duration.name)
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

            // Mayın ve ödül içeren tahta oluştur
            val boardWithEffects = generateBoardWithEffects()

            val game = Game(
                gameId = gameId,
                player1Id = playerId,
                currentTurnPlayerId = playerId,
                status = GameStatus.WAITING_FOR_PLAYER,
                duration = duration,
                startTimeMillis = 0L,
                expireTimeMillis = 0L,
                board = boardWithEffects.toMutableMap(),
                remainingLetters = letterPool.mapKeys { it.key.toString() }.toMutableMap(),
                currentLetters1 = drawnLetters1.map { it.toString() }.toMutableList(),
                currentLetters2 = drawnLetters2.map { it.toString() }.toMutableList(),
                moveHistory = mutableListOf(),
                createdAt = currentTime,
                players = listOf(playerId),
                // Mayın ve Ödül sistemi için varsayılan değerler
                areaBlockActivatedBy = null,
                areaBlockSide = null,
                areaBlockExpiresAt = null,
                frozenLetterIndices = emptyList(),
                frozenLettersPlayerId = null,
                frozenLettersExpiresAt = null,
                extraTurnForPlayerId = null
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

    // Mayın ve Ödül sistemi için yeni eklenen fonksiyonlar

    // Bölge blok durumunu günceller
    suspend fun updateAreaBlock(
        gameId: String,
        activatedBy: String?,
        side: String?,
        expiresAt: Long?
    ) {
        try {
            val updateMap = mapOf(
                "areaBlockActivatedBy" to activatedBy,
                "areaBlockSide" to side,
                "areaBlockExpiresAt" to expiresAt
            )

            firestore.collection("games")
                .document(gameId)
                .update(updateMap)
                .await()
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ updateAreaBlock hatası: ${e.message}")
        }
    }

    // Dondurulmuş harf durumunu günceller
    suspend fun updateFrozenLetters(
        gameId: String,
        frozenIndices: List<Int>,
        playerId: String?,
        expiresAt: Long?
    ) {
        try {
            val updateMap = mapOf(
                "frozenLetterIndices" to frozenIndices,
                "frozenLettersPlayerId" to playerId,
                "frozenLettersExpiresAt" to expiresAt
            )

            firestore.collection("games")
                .document(gameId)
                .update(updateMap)
                .await()
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ updateFrozenLetters hatası: ${e.message}")
        }
    }

    // Ekstra tur durumunu günceller
    suspend fun updateExtraTurn(gameId: String, playerId: String?) {
        try {
            firestore.collection("games")
                .document(gameId)
                .update("extraTurnForPlayerId", playerId)
                .await()
        } catch (e: Exception) {
            Log.e("FireBaseGameDataSource", "❌ updateExtraTurn hatası: ${e.message}")
        }
    }

    // Mayın ve ödülleri içeren bir tahta oluşturur
    private fun generateBoardWithEffects(): Map<String, GameTile> {
        val board = generateEmptyBoard().toMutableMap()

        // Mayınları ekle - her türden 3 tane
        val mineTypes = listOf(
            MineType.POINT_DIVISION,
            MineType.POINT_TRANSFER,
            MineType.LETTER_RESET,
            MineType.BONUS_CANCEL,
            MineType.WORD_CANCEL
        )

        val minePositions = getRandomBoardPositions(15) // Her türden 3 tane = 15 toplam

        minePositions.forEachIndexed { index, position ->
            val row = position.first
            val col = position.second
            val key = "$row-$col"

            // Zaten özel hücre ise veya merkez hücre ise, mayın eklemeyi atla
            val currentTile = board[key]
            if (currentTile?.cellType == CellType.NORMAL && !(row == 7 && col == 7)) {
                val mineType = mineTypes[index % mineTypes.size]
                board[key] = GameTile(
                    letter = null,
                    cellType = CellType.NORMAL,
                    mineType = mineType
                )
            }
        }

        // Ödülleri ekle - 2 AREA_BLOCK, 3 LETTER_FREEZE, 2 EXTRA_TURN
        val rewardTypes = listOf(
            RewardType.AREA_BLOCK, RewardType.AREA_BLOCK,
            RewardType.LETTER_FREEZE, RewardType.LETTER_FREEZE, RewardType.LETTER_FREEZE,
            RewardType.EXTRA_TURN, RewardType.EXTRA_TURN
        )

        // Mayınların olduğu konumları hariç tut
        val usedPositions = minePositions.toMutableList()

        rewardTypes.forEachIndexed { index, rewardType ->
            val position = getRandomBoardPosition(usedPositions)
            usedPositions.add(position)

            val row = position.first
            val col = position.second
            val key = "$row-$col"

            // Zaten özel hücre ise veya merkez hücre ise, ödül eklemeyi atla
            val currentTile = board[key]
            if (currentTile?.cellType == CellType.NORMAL && !(row == 7 && col == 7)) {
                board[key] = GameTile(
                    letter = null,
                    cellType = CellType.NORMAL,
                    rewardType = rewardType
                )
            }
        }

        return board
    }

    // Rastgele tahta konumları üretir (belirli konumları hariç tutarak)
    private fun getRandomBoardPositions(count: Int, exclude: List<Pair<Int, Int>> = emptyList()): List<Pair<Int, Int>> {
        val positions = mutableListOf<Pair<Int, Int>>()
        val availablePositions = mutableListOf<Pair<Int, Int>>()

        // Tüm olası konumları oluştur
        for (i in 0..14) {
            for (j in 0..14) {
                val pos = Pair(i, j)

                // Merkezi ve özel hücreleri hariç tut
                val isCenterCell = i == 7 && j == 7
                val isSpecialCell = isSpecialCell(i, j)

                if (!isCenterCell && !isSpecialCell && !exclude.contains(pos)) {
                    availablePositions.add(pos)
                }
            }
        }

        // Konumları karıştır
        availablePositions.shuffle()

        // İlk 'count' adet konumu al
        positions.addAll(availablePositions.take(count))

        return positions
    }

    // Tek bir rastgele konum üretir (belirli konumları hariç tutarak)
    private fun getRandomBoardPosition(exclude: List<Pair<Int, Int>> = emptyList()): Pair<Int, Int> {
        val positions = getRandomBoardPositions(1, exclude)
        return positions.firstOrNull() ?: Pair(
            Random.nextInt(0, 15),
            Random.nextInt(0, 15)
        )
    }

    // Hücrenin özel olup olmadığını kontrol eder (2L, 3L, 2W, 3W)
    private fun isSpecialCell(row: Int, col: Int): Boolean {
        // Triple Word Score hücreleri
        if ((row == 0 && col == 0) || (row == 0 && col == 7) || (row == 0 && col == 14) ||
            (row == 7 && col == 0) || (row == 7 && col == 14) ||
            (row == 14 && col == 0) || (row == 14 && col == 7) || (row == 14 && col == 14)) {
            return true
        }

        // Double Word Score hücreleri
        if ((row == 1 && col == 1) || (row == 2 && col == 2) || (row == 3 && col == 3) || (row == 4 && col == 4) ||
            (row == 10 && col == 10) || (row == 11 && col == 11) || (row == 12 && col == 12) || (row == 13 && col == 13) ||
            (row == 1 && col == 13) || (row == 2 && col == 12) || (row == 3 && col == 11) || (row == 4 && col == 10) ||
            (row == 10 && col == 4) || (row == 11 && col == 3) || (row == 12 && col == 2) || (row == 13 && col == 1)) {
            return true
        }

        // Triple Letter Score hücreleri
        if ((row == 1 && col == 5) || (row == 1 && col == 9) ||
            (row == 5 && col == 1) || (row == 5 && col == 5) || (row == 5 && col == 9) || (row == 5 && col == 13) ||
            (row == 9 && col == 1) || (row == 9 && col == 5) || (row == 9 && col == 9) || (row == 9 && col == 13) ||
            (row == 13 && col == 5) || (row == 13 && col == 9)) {
            return true
        }

        // Double Letter Score hücreleri
        if ((row == 0 && col == 3) || (row == 0 && col == 11) ||
            (row == 2 && col == 6) || (row == 2 && col == 8) ||
            (row == 3 && col == 0) || (row == 3 && col == 7) || (row == 3 && col == 14) ||
            (row == 6 && col == 2) || (row == 6 && col == 6) || (row == 6 && col == 8) || (row == 6 && col == 12) ||
            (row == 7 && col == 3) || (row == 7 && col == 11) ||
            (row == 8 && col == 2) || (row == 8 && col == 6) || (row == 8 && col == 8) || (row == 8 && col == 12) ||
            (row == 11 && col == 0) || (row == 11 && col == 7) || (row == 11 && col == 14) ||
            (row == 12 && col == 6) || (row == 12 && col == 8) ||
            (row == 14 && col == 3) || (row == 14 && col == 11)) {
            return true
        }

        return false
    }
}