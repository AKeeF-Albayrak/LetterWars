package com.example.letterwars.data.repository

import android.util.Log
import com.example.letterwars.data.firebase.FireBaseGameDataSource
import com.example.letterwars.data.model.*

data class EffectNotification(
    val id: String = "",
    val playerId: String = "",
    val effectType: String = "",
    val message: String = "",
    val timeMillis: Long = 0
)

class GameRepository(
    private val gameDataSource: FireBaseGameDataSource = FireBaseGameDataSource()
) {
    suspend fun getGame(gameId: String): Game? {
        return gameDataSource.getGame(gameId)
    }

    suspend fun endGame(game: Game) {
        gameDataSource.endGame(game)
    }

    suspend fun endGame(game: Game, winnerId: String?){
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

    suspend fun checkTurnExpirationForUser(userId: String, currentTimeMillis: Long) {
        gameDataSource.checkTurnExpirationForUser(userId, currentTimeMillis)
    }

    suspend fun getGamesByUser(userId: String): List<Game> {
        return gameDataSource.getGamesByUser(userId)
    }

    suspend fun activateAreaBlock(gameId: String, userId: String, side: String, duration: Long = 2 * 60 * 1000) {
        val game = gameDataSource.getGame(gameId) ?: return
        val updatedGame = game.copy(
            areaBlockActivatedBy = userId,
            areaBlockSide = side,
            areaBlockExpiresAt = System.currentTimeMillis() + duration
        )
        gameDataSource.updateGame(updatedGame)
    }

    suspend fun clearAreaBlock(gameId: String) {
        val game = gameDataSource.getGame(gameId) ?: return
        val updatedGame = game.copy(
            areaBlockActivatedBy = null,
            areaBlockSide = null,
            areaBlockExpiresAt = null
        )
        gameDataSource.updateGame(updatedGame)
    }

    suspend fun freezeLetters(gameId: String, targetPlayerId: String, letterIndices: List<Int>) {
        val game = gameDataSource.getGame(gameId) ?: return

        val clearTurn = game.moveHistory.count { it.playerId == targetPlayerId } + 1

        val updatedEffects = game.frozenLettersEffects.toMutableList().apply {
            add(FrozenLettersEffect(targetPlayerId, letterIndices, clearTurn))
        }

        val updatedGame = game.copy(frozenLettersEffects = updatedEffects)

        gameDataSource.updateGame(updatedGame)
    }


    suspend fun setExtraTurn(gameId: String, playerId: String) {
        val game = gameDataSource.getGame(gameId) ?: return
        val updatedGame = game.copy(
            extraTurnForPlayerId = playerId
        )
        gameDataSource.updateGame(updatedGame)
    }

    suspend fun clearExtraTurn(gameId: String) {
        val game = gameDataSource.getGame(gameId) ?: return
        val updatedGame = game.copy(
            extraTurnForPlayerId = null
        )
        gameDataSource.updateGame(updatedGame)
    }

    suspend fun generateBoardWithEffects(gameId: String) {
        val game = gameDataSource.getGame(gameId) ?: return
        val board = game.board.toMutableMap()

        addMinesToBoard(board)
        addRewardsToBoard(board)

        val updatedGame = game.copy(
            board = board
        )

        gameDataSource.updateGame(updatedGame)
    }

    private fun addMinesToBoard(board: MutableMap<String, GameTile>) {
        val mineTypes = listOf(
            MineType.POINT_DIVISION,
            MineType.POINT_TRANSFER,
            MineType.LETTER_RESET,
            MineType.BONUS_CANCEL,
            MineType.WORD_CANCEL
        )

        val minePositions = getRandomBoardPositions(15)

        minePositions.forEachIndexed { index, position ->
            val row = position.first
            val col = position.second
            val key = "$row-$col"

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
    }

    private fun addRewardsToBoard(board: MutableMap<String, GameTile>) {
        val rewardCounts = mapOf(
            RewardType.AREA_BLOCK to 2,
            RewardType.LETTER_FREEZE to 3,
            RewardType.EXTRA_TURN to 2
        )

        val usedPositions = board.filter { it.value.mineType != null }.keys
            .mapNotNull { key ->
                val parts = key.split("-")
                if (parts.size == 2) {
                    val row = parts[0].toIntOrNull()
                    val col = parts[1].toIntOrNull()
                    if (row != null && col != null) Pair(row, col) else null
                } else null
            }

        val rewardPositions = getRandomBoardPositions(7, usedPositions)

        var positionIndex = 0
        rewardCounts.forEach { (rewardType, count) ->
            repeat(count) {
                if (positionIndex < rewardPositions.size) {
                    val position = rewardPositions[positionIndex]
                    val row = position.first
                    val col = position.second
                    val key = "$row-$col"

                    val currentTile = board[key]
                    if (currentTile?.cellType == CellType.NORMAL &&
                        currentTile.mineType == null &&
                        !(row == 7 && col == 7)) {

                        board[key] = GameTile(
                            letter = null,
                            cellType = CellType.NORMAL,
                            rewardType = rewardType
                        )
                    }
                    positionIndex++
                }
            }
        }
    }

    private fun getRandomBoardPositions(count: Int, exclude: List<Pair<Int, Int>> = emptyList()): List<Pair<Int, Int>> {
        val positions = mutableListOf<Pair<Int, Int>>()
        val availablePositions = mutableListOf<Pair<Int, Int>>()

        for (row in 0..14) {
            for (col in 0..14) {
                val position = Pair(row, col)
                if ((row != 7 || col != 7) && !exclude.contains(position)) {
                    availablePositions.add(position)
                }
            }
        }

        availablePositions.shuffle()

        positions.addAll(availablePositions.take(count))

        return positions
    }

    suspend fun checkAndClearExpiredEffects(gameId: String) {
        val game = gameDataSource.getGame(gameId) ?: return
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

        if (needsUpdate) {
            gameDataSource.updateGame(updatedGame)
        }
    }

    suspend fun addEffectNotification(gameId: String, notification: EffectNotification) {
        Log.d("GameRepository", "Bildirim eklenecek, ancak şu anda uygulanmadı")
    }

    suspend fun removeEffectNotification(gameId: String, notificationId: String) {
        Log.d("GameRepository", "Bildirim silinecek, ancak şu anda uygulanmadı")
    }

    fun listenEffectNotifications(gameId: String, onNotificationsChanged: (List<EffectNotification>) -> Unit) {
        Log.d("GameRepository", "Bildirimler dinlenecek, ancak şu anda uygulanmadı")
    }

    suspend fun cleanupOldNotifications(gameId: String) {
        Log.d("GameRepository", "Eski bildirimler temizlenecek, ancak şu anda uygulanmadı")
    }
}