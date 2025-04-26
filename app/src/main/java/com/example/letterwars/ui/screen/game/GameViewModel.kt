package com.example.letterwars.ui.screen.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.Game
import com.example.letterwars.data.model.GameStatus
import com.example.letterwars.data.model.GameTile
import com.example.letterwars.data.model.Move
import com.example.letterwars.data.repository.GameRepository
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

    private val _validPositions = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val validPositions: StateFlow<List<Pair<Int, Int>>> = _validPositions

    fun loadGame(gameId: String) {
        viewModelScope.launch {
            val fetchedGame = repository.getGame(gameId)
            _game.value = fetchedGame
        }
    }

    fun listenGameChanges(gameId: String) {
        repository.listenGame(gameId) { updatedGame ->
            _game.value = updatedGame
        }
    }


    fun endGame(game: Game, loserId: String) {
        viewModelScope.launch {
            val winnerId = when {
                loserId == game.player1Id -> game.player2Id
                loserId == game.player2Id -> game.player1Id
                else -> null
            }

            repository.endGame(
                game.copy(
                    status = GameStatus.FINISHED,
                    winnerId = winnerId
                )
            )
        }
    }

    fun addPendingMove(row: Int, col: Int, letter: String) {
        val currentGame = _game.value ?: return
        val updatedMoves = currentGame.pendingMoves.toMutableMap()
        updatedMoves["$row-$col"] = letter

        val updatedGame = currentGame.copy(pendingMoves = updatedMoves)
        _game.value = updatedGame
    }

    fun clearPendingMoves() {
        val currentGame = _game.value ?: return
        val updatedGame = currentGame.copy(pendingMoves = emptyMap())
        _game.value = updatedGame
    }

    fun confirmMove(placedLetters: Map<Pair<Int, Int>, RackLetter>) {
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
            val newLetters = mutableListOf<String>()

            repeat(placedLetters.size) {
                val randomLetter = updatedRemainingLetters.keys.randomOrNull()
                if (randomLetter != null) {
                    newLetters.add(randomLetter)
                    updatedRemainingLetters[randomLetter] = updatedRemainingLetters[randomLetter]!! - 1
                    if (updatedRemainingLetters[randomLetter] == 0) {
                        updatedRemainingLetters.remove(randomLetter)
                    }
                }
            }
            updatedCurrentLetters.addAll(newLetters)

            val nextTurnPlayerId = if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
                currentGame.player2Id
            } else {
                currentGame.player1Id
            }

            val newMove = Move(
                playerId = currentGame.currentTurnPlayerId,
                word = placedLetters.values.joinToString("") { it.letter }, // Şimdilik basit birleştiriyoruz
                positions = placedLetters.keys.toList(),
                scoreEarned = 0, // Şu an skor hesaplamıyoruz, istersen burada hesaplarız
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
                moveHistory = updatedMoveHistory // ⭐
            )

            repository.updateGame(updatedGame)
            _game.value = updatedGame
        }
    }


    fun passTurn() {
        viewModelScope.launch {
            val currentGame = _game.value ?: return@launch

            val nextTurnPlayerId = if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
                currentGame.player2Id
            } else {
                currentGame.player1Id
            }

            val updatedGame = currentGame.copy(
                currentTurnPlayerId = nextTurnPlayerId
            )

            repository.updateGame(updatedGame)

            _game.value = updatedGame
        }
    }

    fun updateValidPositions() {
        val currentGame = _game.value ?: return

        val board = currentGame.board

        val newValidPositions = mutableListOf<Pair<Int, Int>>()

        for (i in 0..14) {
            for (j in 0..14) {
                val key = "$i-$j"
                if (board[key]?.letter.isNullOrEmpty()) {
                    // Komşu hücrede harf var mı kontrolü
                    val neighbors = listOf(
                        "${i - 1}-$j",
                        "${i + 1}-$j",
                        "$i-${j - 1}",
                        "$i-${j + 1}",
                        "${i - 1}-${j - 1}",
                        "${i + 1}-${j + 1}",
                        "${i - 1}-${j + 1}",
                        "${i + 1}-${j - 1}"
                    )

                    if (neighbors.any { neighborKey ->
                            board[neighborKey]?.letter?.isNotEmpty() == true
                        } || (i == 7 && j == 7)) { // Başlangıç için merkez
                        newValidPositions.add(Pair(i, j))
                    }
                }
            }
        }

        _validPositions.value = newValidPositions
    }

}
