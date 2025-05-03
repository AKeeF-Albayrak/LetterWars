package com.example.letterwars.ui.screen.game

import MineType
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.*
import com.example.letterwars.data.repository.GameRepository
import com.example.letterwars.data.repository.UserRepository
import com.example.letterwars.data.util.calculateScore
import com.example.letterwars.data.util.checkWords
import com.example.letterwars.data.util.detectDirection
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = GameRepository()

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

    // Tetiklenen özel efektleri takip etmek için yeni bir StateFlow
    private val _triggeredEffects = MutableStateFlow<List<TriggeredEffect>>(emptyList())
    val triggeredEffects: StateFlow<List<TriggeredEffect>> = _triggeredEffects

    // Tetiklenen mayın türünü takip etmek için bir StateFlow ekliyoruz
    private val _triggeredMine = MutableStateFlow<MineType?>(null)
    val triggeredMine: StateFlow<MineType?> = _triggeredMine

    // Tetiklenen efektleri temsil eden veri sınıfı
    data class TriggeredEffect(
        val position: Position,
        val mineType: MineType?,
        val rewardType: RewardType?
    )

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
            if (winnerId == null) {
                println("Winner Id Nası NULL oldu")
            } else {
                repository.endGame(game, winnerId)
            }
        }
    }

    fun addPendingMove(row: Int, col: Int, letter: String) {
        val currentGame = _game.value ?: return

        // 1. Oyuncunun mevcut harf listesini al
        val updatedCurrentLetters = if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
            currentGame.currentLetters1.toMutableList()
        } else {
            currentGame.currentLetters2.toMutableList()
        }

        // 2. Harfin ilk eşleşmesini sil
        updatedCurrentLetters.remove(letter)

        // 3. pendingMoves ve board güncelle
        val updatedMoves = currentGame.pendingMoves.toMutableMap()
        updatedMoves["$row-$col"] = letter

        val updatedBoard = currentGame.board.toMutableMap()
        val key = "$row-$col"
        val tile = updatedBoard[key]?.copy(letter = letter) ?: GameTile(letter = letter)
        updatedBoard[key] = tile

        // 4. Oyunu kaydet
        val updatedGame = currentGame.copy(
            pendingMoves = updatedMoves,
            board = updatedBoard,
            currentLetters1 = if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                updatedCurrentLetters else currentGame.currentLetters1,
            currentLetters2 = if (currentGame.currentTurnPlayerId == currentGame.player2Id)
                updatedCurrentLetters else currentGame.currentLetters2
        )
        _game.value = updatedGame
        viewModelScope.launch {
            repository.updateGame(updatedGame)
        }
    }

    fun clearPendingMoves() {
        val currentGame = _game.value ?: return

        val updatedBoard = currentGame.board.toMutableMap()
        val returnedLetters = currentGame.pendingMoves.values.toMutableList()

        // Board'dan pendingMoves harflerini sil
        for (key in currentGame.pendingMoves.keys) {
            val tile = updatedBoard[key]?.copy(letter = null)
            if (tile != null) {
                updatedBoard[key] = tile
            }
        }

        // Sadece sıra kimdeyse onun currentLetters'ını güncelle
        val updatedGame = if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
            currentGame.copy(
                board = updatedBoard,
                pendingMoves = emptyMap(),
                currentLetters1 = (currentGame.currentLetters1 + returnedLetters).toMutableList()
            )
        } else {
            currentGame.copy(
                board = updatedBoard,
                pendingMoves = emptyMap(),
                currentLetters2 = (currentGame.currentLetters2 + returnedLetters).toMutableList()
            )
        }

        _game.value = updatedGame

        viewModelScope.launch {
            repository.updateGame(updatedGame)
        }
    }

    fun updateValidPositions() {
        val currentGame = _game.value ?: return
        val board = currentGame.board
        val newValidPositions = mutableListOf<Position>()

        val centerTileEmpty = board["7-7"]?.letter.isNullOrEmpty()
        if (centerTileEmpty && currentGame.pendingMoves.isEmpty()) {
            newValidPositions.add(Position(7, 7))
            _validPositions.value = newValidPositions
            return
        }

        // Mevcut geçici taşlar üzerinden geç
        val placedPositions = currentGame.pendingMoves.keys.mapNotNull { key ->
            val parts = key.split("-")
            if (parts.size == 2) {
                val row = parts[0].toIntOrNull()
                val col = parts[1].toIntOrNull()
                if (row != null && col != null) Position(row, col) else null
            } else null
        }

        if (placedPositions.size < 2) {
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
        } else {
            // İki taş yerleştirildiği zaman yönü tespit et
            val direction = detectDirection(placedPositions.toSet()) ?: return

            val sorted = placedPositions.sortedWith(compareBy({ it.row }, { it.col }))
            val first = sorted.first()

            val (dr, dc) = when (direction) {
                "horizontal" -> 0 to 1
                "vertical" -> 1 to 0
                "diagonal-main" -> 1 to 1
                "diagonal-anti" -> 1 to -1
                else -> return
            }

            // Yönün negatif kısmına kadar git
            var r = first.row
            var c = first.col
            while (true) {
                val nr = r - dr
                val nc = c - dc
                val key = "$nr-$nc"
                if (nr in 0..14 && nc in 0..14 && !board[key]?.letter.isNullOrEmpty()) {
                    r = nr
                    c = nc
                } else break
            }

            val start = Position(r, c)

            // Yönün pozitif kısmına kadar git
            r = first.row
            c = first.col
            while (true) {
                val nr = r + dr
                val nc = c + dc
                val key = "$nr-$nc"
                if (nr in 0..14 && nc in 0..14 && !board[key]?.letter.isNullOrEmpty()) {
                    r = nr
                    c = nc
                } else break
            }

            val end = Position(r, c)

            // Başlangıç ve bitiş arasındaki tüm boş hücreleri valid say
            var cr = start.row
            var cc = start.col
            while (true) {
                val key = "$cr-$cc"
                if (board[key]?.letter.isNullOrEmpty()) {
                    newValidPositions.add(Position(cr, cc))
                }

                if (cr == end.row && cc == end.col) break
                cr += dr
                cc += dc
            }
        }

        _validPositions.value = newValidPositions
    }

    // Mayına yakalandığında tetiklenecek fonksiyon
    fun onMineTriggered(mineType: MineType) {
        _triggeredMine.value = mineType
    }

    fun clearTriggeredMine() {
        _triggeredMine.value = null
    }

    // Yeni mayınları eklemek için yardımcı işlev
    fun addNewMines() {
        val currentGame = _game.value ?: return
        val updatedBoard = currentGame.board.toMutableMap()

        // Yeni mayınları ekleyelim
        placeNewMines(updatedBoard)

        val updatedGame = currentGame.copy(board = updatedBoard)
        _game.value = updatedGame

        viewModelScope.launch {
            repository.updateGame(updatedGame)
        }
    }

    private fun placeNewMines(board: MutableMap<String, GameTile>) {
        // Yeni mayınları yerleştir
        placeAreaBlockMine(board)
        placeLetterFreezeMine(board)
        placeExtraTurnMine(board)
    }

    private fun placeAreaBlockMine(board: MutableMap<String, GameTile>) {
        // Sağ taraf için 7-15 arası bölgede yerleştirilecek mayınlar
        for (row in 7..14) {
            for (col in 7..14) {
                val key = "$row-$col"
                if (board[key]?.mineType == null) {
                    board[key] = GameTile(mineType = MineType.AREA_BLOCK)
                    return
                }
            }
        }
    }

    private fun placeLetterFreezeMine(board: MutableMap<String, GameTile>) {
        for (row in 0..14) {
            for (col in 0..14) {
                val key = "$row-$col"
                if (board[key]?.mineType == null) {
                    board[key] = GameTile(mineType = MineType.LETTER_FREEZE)
                    return
                }
            }
        }
    }

    private fun placeExtraTurnMine(board: MutableMap<String, GameTile>) {
        for (row in 0..14) {
            for (col in 0..14) {
                val key = "$row-$col"
                if (board[key]?.mineType == null) {
                    board[key] = GameTile(mineType = MineType.EXTRA_TURN)
                    return
                }
            }
        }
    }
}
