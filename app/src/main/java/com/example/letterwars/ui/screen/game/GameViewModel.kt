package com.example.letterwars.ui.screen.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.Game
import com.example.letterwars.data.model.GameTile
import com.example.letterwars.data.model.Move
import com.example.letterwars.data.model.Position
import com.example.letterwars.data.repository.GameRepository
import com.example.letterwars.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(
    private val repository: GameRepository = GameRepository()
) : ViewModel() {

    private val _game = MutableStateFlow<Game?>(null)
    val game: StateFlow<Game?> = _game

    val currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    private val _currentUsername = MutableStateFlow<String>("")
    val currentUsername: StateFlow<String> = _currentUsername

    private val _opponentUsername = MutableStateFlow<String>("")
    val opponentUsername: StateFlow<String> = _opponentUsername

    private val userRepository = UserRepository()


    private val _validPositions = MutableStateFlow<List<Position>>(emptyList())
    val validPositions: StateFlow<List<Position>> = _validPositions

    fun listenGameChanges(gameId: String) {
        repository.listenGame(gameId) { updatedGame ->
            _game.value = updatedGame
            updateValidPositions()

            viewModelScope.launch {
                loadUsernames(updatedGame)
            }
        }
    }

    private suspend fun loadUsernames(game: Game) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val currentUser = userRepository.getUser(currentUserId)
        _currentUsername.value = currentUser?.username ?: currentUser?.email ?: "Oyuncu"

        val opponentId = if (game.player1Id == currentUserId) game.player2Id else game.player1Id

        if (opponentId.isNotEmpty()) {
            val opponentUser = userRepository.getUser(opponentId)
            _opponentUsername.value = opponentUser?.username ?: opponentUser?.email ?: "Rakip"
        }
    }


    fun SurrenderGame(game: Game, loserId: String) {
        viewModelScope.launch {
            val winnerId = when {
                loserId == game.player1Id -> game.player2Id
                loserId == game.player2Id -> game.player1Id
                else -> null
            }
            if(winnerId == null){
                println("Winner Id Nasi NULL oluo")
            }else
            {
                repository.endGame(game, winnerId)
            }
        }
    }

    fun addPendingMove(row: Int, col: Int, letter: String) {
        val currentGame = _game.value ?: return
        val updatedMoves = currentGame.pendingMoves.toMutableMap()
        updatedMoves["$row-$col"] = letter

        val updatedBoard = currentGame.board.toMutableMap()
        val key = "$row-$col"
        val tile = updatedBoard[key]?.copy(letter = letter)
        if (tile != null) {
            updatedBoard[key] = tile
        }

        val updatedGame = currentGame.copy(
            pendingMoves = updatedMoves,
            board = updatedBoard
        )
        _game.value = updatedGame

        viewModelScope.launch {
            repository.updateBoardAndPendingMoves(
                updatedGame.gameId,
                updatedBoard,
                updatedMoves
            )
        }
    }

    fun clearPendingMoves() {
        val currentGame = _game.value ?: return

        val updatedBoard = currentGame.board.toMutableMap()

        // Sadece pending moves içindekileri temizle
        for (key in currentGame.pendingMoves.keys) {
            val tile = updatedBoard[key]?.copy(letter = null)
            if (tile != null) {
                updatedBoard[key] = tile
            }
        }

        val updatedGame = currentGame.copy(
            pendingMoves = emptyMap(),
            board = updatedBoard
        )
        _game.value = updatedGame

        viewModelScope.launch {
            repository.updateBoardAndPendingMoves(
                updatedGame.gameId,
                updatedBoard,
                emptyMap()
            )
        }
    }



    fun confirmMove(placedLetters: Map<Position, RackLetter>) {
        viewModelScope.launch {
            val currentGame = _game.value ?: return@launch

            val updatedBoard = currentGame.board.toMutableMap()
            placedLetters.forEach { (pos, rackLetter) ->
                val (row, col) = pos
                val key = "$row-$col"
                updatedBoard[key] = GameTile(letter = rackLetter.letter)
            }

            val updatedCurrentLetters = currentGame.currentLetters.toMutableList()
            placedLetters.values.forEach { rackLetter ->
                updatedCurrentLetters.remove(rackLetter.letter)
            }

            val updatedRemainingLetters = currentGame.remainingLetters.toMutableMap()
            val lettersNeeded = 7 - updatedCurrentLetters.size
            val newLetters = drawLetters(updatedRemainingLetters, lettersNeeded)
            updatedCurrentLetters.addAll(newLetters)

            val nextTurnPlayerId = if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
                currentGame.player2Id
            } else {
                currentGame.player1Id
            }

            val currentTime = System.currentTimeMillis()
            val expireTime = currentTime + (currentGame.duration.minutes * 60 * 1000)

            val newMove = Move(
                playerId = currentGame.currentTurnPlayerId,
                word = placedLetters.values.joinToString("") { it.letter },
                positions = placedLetters.keys.toList(),
                scoreEarned = 0,
                timeMillis = System.currentTimeMillis()
            )

            val updatedMoveHistory = currentGame.moveHistory.toMutableList().apply {
                add(newMove)
            }

            val updatedGame = currentGame.copy(
                board = updatedBoard,
                currentLetters = updatedCurrentLetters,
                remainingLetters = updatedRemainingLetters,
                currentTurnPlayerId = nextTurnPlayerId,
                moveHistory = updatedMoveHistory,
                pendingMoves = emptyMap(), // pendingMoves sıfırlıyoruz
                startTimeMillis = currentTime, // yeni turn start time
                expireTimeMillis = expireTime  // yeni turn expire time
            )

            repository.updateGame(updatedGame)
            _game.value = updatedGame
        }
    }



    fun passTurn() {
        viewModelScope.launch {
            val currentGame = _game.value ?: return@launch

            val updatedRemainingLetters = currentGame.remainingLetters.toMutableMap()
            val updatedCurrentLetters = currentGame.currentLetters.toMutableList()

            val lettersNeeded = 7 - updatedCurrentLetters.size
            val newLetters = drawLetters(updatedRemainingLetters, lettersNeeded)
            updatedCurrentLetters.addAll(newLetters)

            val nextTurnPlayerId = if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
                currentGame.player2Id
            } else {
                currentGame.player1Id
            }

            val currentTime = System.currentTimeMillis()
            val expireTime = currentTime + (currentGame.duration.minutes * 60 * 1000)

            val updatedGame = currentGame.copy(
                currentTurnPlayerId = nextTurnPlayerId,
                currentLetters = updatedCurrentLetters,
                remainingLetters = updatedRemainingLetters,
                pendingMoves = emptyMap(), // pass turn'da da pendingMoves temizle
                startTimeMillis = currentTime, // yeni turn start time
                expireTimeMillis = expireTime  // yeni turn expire time
            )

            repository.updateGame(updatedGame)
            _game.value = updatedGame
        }
    }


    fun updateValidPositions() {
        val currentGame = _game.value ?: return
        val board = currentGame.board
        val newValidPositions = mutableListOf<Position>()

        for (i in 0..14) {
            for (j in 0..14) {
                val key = "$i-$j"
                val tile = board[key]

                if (!tile?.letter.isNullOrEmpty()) continue

                if (i == 7 && j == 7) {
                    newValidPositions.add(Position(i, j))
                    continue
                }

                val neighborOffsets = listOf(
                    -1 to 0, 1 to 0,
                    0 to -1, 0 to 1,
                    -1 to -1, 1 to 1,
                    -1 to 1, 1 to -1
                )

                if (neighborOffsets.any { (dx, dy) ->
                        val neighborKey = "${i + dx}-${j + dy}"
                        board[neighborKey]?.letter?.isNotEmpty() == true
                    }
                ) {
                    newValidPositions.add(Position(i, j))
                }
            }
        }

        _validPositions.value = newValidPositions

        println("Güncel Valid Positions (${newValidPositions.size} adet):")
        newValidPositions.forEach { pos ->
            println("Row: ${pos.row}, Col: ${pos.col}")
        }
    }



    private fun drawLetters(pool: MutableMap<String, Int>, count: Int): List<String> {
        val available = pool.flatMap { entry -> List(entry.value) { entry.key } }.toMutableList()
        available.shuffle()
        val drawn = available.take(count)

        drawn.forEach {
            pool[it] = pool[it]?.minus(1) ?: 0
            if (pool[it] == 0) pool.remove(it)
        }

        return drawn
    }
}
