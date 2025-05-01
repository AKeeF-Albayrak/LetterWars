package com.example.letterwars.ui.screen.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.Game
import com.example.letterwars.data.model.GameTile
import com.example.letterwars.data.model.Move
import com.example.letterwars.data.model.Position
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
    application: Application,
    private val repository: GameRepository = GameRepository()
) : AndroidViewModel(application) {

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
            val context = getApplication<Application>().applicationContext

            // 1. Tahtayı güncelle
            val updatedBoard = currentGame.board.toMutableMap()
            placedLetters.forEach { (pos, rackLetter) ->
                val key = "${pos.row}-${pos.col}"
                updatedBoard[key] = GameTile(letter = rackLetter.letter)
            }

            // 2. Tüm kelimeleri bul ve doğrula
            val wordList = checkWords(context, updatedBoard, placedLetters) ?: return@launch

            // 3. Puanı hesapla
            val score = calculateScore(updatedBoard, placedLetters, wordList)

            // 4. Kullanılan harfleri çıkar
            val updatedCurrentLetters = currentGame.currentLetters.toMutableList().apply {
                placedLetters.values.forEach { remove(it.letter) }
            }

            // 5. Yeni harf çek
            val updatedRemainingLetters = currentGame.remainingLetters.toMutableMap()
            val lettersNeeded = 7 - updatedCurrentLetters.size
            val newLetters = drawLetters(updatedRemainingLetters, lettersNeeded)
            updatedCurrentLetters.addAll(newLetters)

            // 6. Sıradaki oyuncuyu belirle
            val nextTurnPlayerId = if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
                currentGame.player2Id
            } else {
                currentGame.player1Id
            }

            // 7. Zamanlar
            val currentTime = System.currentTimeMillis()
            val expireTime = currentTime + (currentGame.duration.minutes * 60 * 1000)

            // 8. Hamleyi oluştur
            val mainWord = wordList.first() // ilk kelime: ana kelime
            val newMove = Move(
                playerId = currentGame.currentTurnPlayerId,
                word = mainWord.word,
                positions = mainWord.positions,
                scoreEarned = score,
                timeMillis = currentTime
            )

            // 9. Güncelle
            val updatedMoveHistory = currentGame.moveHistory.toMutableList().apply {
                add(newMove)
            }

            val updatedGame = currentGame.copy(
                board = updatedBoard,
                currentLetters = updatedCurrentLetters,
                remainingLetters = updatedRemainingLetters,
                currentTurnPlayerId = nextTurnPlayerId,
                moveHistory = updatedMoveHistory,
                pendingMoves = emptyMap(),
                startTimeMillis = currentTime,
                expireTimeMillis = expireTime
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
        val pendingMoves = currentGame.pendingMoves
        val newValidPositions = mutableListOf<Position>()

        // pendingMoves üzerinden geçici taşlar
        val placedPositions = pendingMoves.keys.mapNotNull { key ->
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
            val direction = detectDirection(placedPositions.toSet()) ?: return

            val sorted = placedPositions.sortedWith(compareBy({ it.row }, { it.col }))

            when (direction) {
                "horizontal" -> {
                    val row = sorted.first().row
                    val cols = sorted.map { it.col }
                    var startCol = cols.minOrNull() ?: 0
                    var endCol = cols.maxOrNull() ?: 14

                    while (startCol > 0 && !board["$row-${startCol - 1}"]?.letter.isNullOrEmpty()) startCol--
                    while (endCol < 14 && !board["$row-${endCol + 1}"]?.letter.isNullOrEmpty()) endCol++

                    if (startCol > 0 && board["$row-${startCol - 1}"]?.letter.isNullOrEmpty() == true)
                        newValidPositions.add(Position(row, startCol - 1))
                    if (endCol < 14 && board["$row-${endCol + 1}"]?.letter.isNullOrEmpty() == true)
                        newValidPositions.add(Position(row, endCol + 1))
                }

                "vertical" -> {
                    val col = sorted.first().col
                    val rows = sorted.map { it.row }
                    var startRow = rows.minOrNull() ?: 0
                    var endRow = rows.maxOrNull() ?: 14

                    while (startRow > 0 && !board["${startRow - 1}-$col"]?.letter.isNullOrEmpty()) startRow--
                    while (endRow < 14 && !board["${endRow + 1}-$col"]?.letter.isNullOrEmpty()) endRow++

                    if (startRow > 0 && board["${startRow - 1}-$col"]?.letter.isNullOrEmpty() == true)
                        newValidPositions.add(Position(startRow - 1, col))
                    if (endRow < 14 && board["${endRow + 1}-$col"]?.letter.isNullOrEmpty() == true)
                        newValidPositions.add(Position(endRow + 1, col))
                }

                "diagonal-main", "diagonal-anti" -> {
                    val (dr, dc) = when (direction) {
                        "diagonal-main" -> Pair(-1, -1)
                        "diagonal-anti" -> Pair(-1, 1)
                        else -> return
                    }
                    val (dr2, dc2) = Pair(-dr, -dc) // ters yön

                    var start = sorted.first()
                    var end = sorted.last()

                    // Geriye doğru uzatma
                    while (true) {
                        val next = Position(start.row + dr, start.col + dc)
                        val key = "${next.row}-${next.col}"
                        if (next.row in 0..14 && next.col in 0..14 && !board[key]?.letter.isNullOrEmpty()) {
                            start = next
                        } else break
                    }

                    // İleriye doğru uzatma
                    while (true) {
                        val next = Position(end.row + dr2, end.col + dc2)
                        val key = "${next.row}-${next.col}"
                        if (next.row in 0..14 && next.col in 0..14 && !board[key]?.letter.isNullOrEmpty()) {
                            end = next
                        } else break
                    }

                    val before = Position(start.row + dr, start.col + dc)
                    val after = Position(end.row + dr2, end.col + dc2)

                    val beforeKey = "${before.row}-${before.col}"
                    val afterKey = "${after.row}-${after.col}"

                    if (before.row in 0..14 && before.col in 0..14 && board[beforeKey]?.letter.isNullOrEmpty() == true)
                        newValidPositions.add(before)
                    if (after.row in 0..14 && after.col in 0..14 && board[afterKey]?.letter.isNullOrEmpty() == true)
                        newValidPositions.add(after)
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
