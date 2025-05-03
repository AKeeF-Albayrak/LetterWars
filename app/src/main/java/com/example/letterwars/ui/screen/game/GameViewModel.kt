package com.example.letterwars.ui.screen.game

import android.app.Application
import android.system.Os.remove
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
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



    fun confirmMove(
        placedLetters: Map<Position, RackLetter>
    ) = viewModelScope.launch {
        val currentGame = _game.value ?: return@launch
        val context = getApplication<Application>().applicationContext

        // 1. Tahtayı güncelle
        val triggeredEffectsList = mutableListOf<TriggeredEffect>()
        val updatedBoard = currentGame.board.toMutableMap()

        placedLetters.forEach { (pos, rackLetter) ->
            val key = "${pos.row}-${pos.col}"
            val currentTile = updatedBoard[key]

            if (currentTile?.mineType != null || currentTile?.rewardType != null) {
                triggeredEffectsList += TriggeredEffect(
                    position = pos,
                    mineType = currentTile.mineType,
                    rewardType = currentTile.rewardType
                )
            }

            updatedBoard[key] = GameTile(letter = rackLetter.letter)
        }

        _triggeredEffects.value = triggeredEffectsList

        // 2. Kelimeleri doğrula
        val wordList = checkWords(context, updatedBoard, placedLetters)
        if (wordList == null) {
            clearPendingMoves()
            return@launch
        }

        val mainWord = wordList.first()

        // 3. Puanı hesapla
        val score = calculateScore(updatedBoard, placedLetters, wordList)

        // Mayın etkileri kontrolü
        val isPointTransfer = mainWord.positions.any {
            updatedBoard["${it.row}-${it.col}"]?.mineType == MineType.POINT_TRANSFER
        }

        val isLetterReset = mainWord.positions.any {
            updatedBoard["${it.row}-${it.col}"]?.mineType == MineType.LETTER_RESET
        }

        // 4. Skorları ayarla
        val updatedPlayer1Score: Int
        val updatedPlayer2Score: Int

        if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
            if (isPointTransfer) {
                updatedPlayer1Score = currentGame.player1Score
                updatedPlayer2Score = currentGame.player2Score + score
            } else {
                updatedPlayer1Score = currentGame.player1Score + score
                updatedPlayer2Score = currentGame.player2Score
            }
        } else {
            if (isPointTransfer) {
                updatedPlayer1Score = currentGame.player1Score + score
                updatedPlayer2Score = currentGame.player2Score
            } else {
                updatedPlayer1Score = currentGame.player1Score
                updatedPlayer2Score = currentGame.player2Score + score
            }
        }

        // 5. Kullanılan harfleri çıkar
        val updatedCurrentLetters =
            if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                currentGame.currentLetters1.toMutableList()
            else
                currentGame.currentLetters2.toMutableList()

        placedLetters.values.forEach { rackLetter ->
            updatedCurrentLetters.remove(rackLetter.letter)
        }

        // 6. Kalan harfleri havuza ver ve yeni harf çek (eğer letter reset varsa)
        val updatedRemainingLetters = currentGame.remainingLetters.toMutableMap()
        if (isLetterReset) {
            updatedCurrentLetters.forEach { letter ->
                updatedRemainingLetters[letter] = updatedRemainingLetters.getOrDefault(letter, 0) + 1
            }
            updatedCurrentLetters.clear()
        }

        val lettersNeeded = maxOf(0, 7 - updatedCurrentLetters.size)
        val newLetters = drawLetters(updatedRemainingLetters, lettersNeeded)
        updatedCurrentLetters += newLetters

        // 7. Sıradaki oyuncu
        val nextTurnPlayerId =
            if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                currentGame.player2Id
            else
                currentGame.player1Id

        // 8. Zamanlar
        val currentTime = System.currentTimeMillis()
        val expireTime = currentTime + (currentGame.duration.minutes * 60 * 1000)

        // 9. Hamleyi oluştur
        val newMove = Move(
            playerId = currentGame.currentTurnPlayerId,
            word = mainWord.word,
            positions = mainWord.positions,
            scoreEarned = score,
            timeMillis = currentTime
        )

        // 10. Oyunu güncelle
        val updatedMoveHistory = currentGame.moveHistory.toMutableList().apply { add(newMove) }

        val updatedGame = currentGame.copy(
            board = updatedBoard,
            currentLetters1 = if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                updatedCurrentLetters else currentGame.currentLetters1,
            currentLetters2 = if (currentGame.currentTurnPlayerId == currentGame.player2Id)
                updatedCurrentLetters else currentGame.currentLetters2,
            remainingLetters = updatedRemainingLetters,
            currentTurnPlayerId = nextTurnPlayerId,
            moveHistory = updatedMoveHistory,
            player1Score = updatedPlayer1Score,
            player2Score = updatedPlayer2Score,
            pendingMoves = emptyMap(),
            startTimeMillis = currentTime,
            expireTimeMillis = expireTime
        )

        repository.updateGame(updatedGame)
        _game.value = updatedGame

        // 11. Oyun bitti mi?
        val bothPlayersOut = updatedGame.currentLetters1.isEmpty() &&
                updatedGame.currentLetters2.isEmpty()

        val player1Out = updatedGame.currentLetters1.isEmpty()
        val player2Out = updatedGame.currentLetters2.isEmpty()
        val lastMove = updatedGame.moveHistory.lastOrNull()
        val lastEmpty = lastMove?.word.isNullOrEmpty()

        val shouldConclude =
            bothPlayersOut ||
                    (player1Out && updatedGame.currentTurnPlayerId == updatedGame.player2Id && lastEmpty) ||
                    (player2Out && updatedGame.currentTurnPlayerId == updatedGame.player1Id && lastEmpty)

        if (shouldConclude) concludeGame(updatedGame)
    }



    fun clearTriggeredEffects() {
        _triggeredEffects.value = emptyList()
    }

    fun passTurn() {
        viewModelScope.launch {
            val currentGame = _game.value ?: return@launch

            // pendingMoves varsa temizle (tahtadan sil + harfleri geri ver)
            if (currentGame.pendingMoves.isNotEmpty()) {
                clearPendingMoves()
            }

            val updatedGameAfterClear = _game.value ?: return@launch

            val updatedRemainingLetters = updatedGameAfterClear.remainingLetters.toMutableMap()
            val updatedCurrentLetters =
                if (updatedGameAfterClear.currentTurnPlayerId == updatedGameAfterClear.player1Id)
                    updatedGameAfterClear.currentLetters1.toMutableList()
                else
                    updatedGameAfterClear.currentLetters2.toMutableList()

            val lettersNeeded = 7 - updatedCurrentLetters.size
            if (lettersNeeded > 0) {
                val newLetters = drawLetters(updatedRemainingLetters, lettersNeeded)
                updatedCurrentLetters.addAll(newLetters)
            }

            val nextTurnPlayerId =
                if (updatedGameAfterClear.currentTurnPlayerId == updatedGameAfterClear.player1Id)
                    updatedGameAfterClear.player2Id
                else
                    updatedGameAfterClear.player1Id

            val currentTime = System.currentTimeMillis()
            val expireTime = currentTime + (updatedGameAfterClear.duration.minutes * 60 * 1000)

            val updatedMoveHistory = updatedGameAfterClear.moveHistory.toMutableList().apply {
                add(
                    Move(
                        playerId = updatedGameAfterClear.currentTurnPlayerId,
                        word = "",
                        positions = emptyList(),
                        scoreEarned = 0,
                        timeMillis = currentTime
                    )
                )
            }

            val updatedGame = updatedGameAfterClear.copy(
                currentTurnPlayerId = nextTurnPlayerId,
                currentLetters1 = if (updatedGameAfterClear.currentTurnPlayerId == updatedGameAfterClear.player1Id)
                    updatedCurrentLetters else updatedGameAfterClear.currentLetters1,
                currentLetters2 = if (updatedGameAfterClear.currentTurnPlayerId == updatedGameAfterClear.player2Id)
                    updatedCurrentLetters else updatedGameAfterClear.currentLetters2,
                remainingLetters = updatedRemainingLetters,
                startTimeMillis = currentTime,
                expireTimeMillis = expireTime,
                moveHistory = updatedMoveHistory
            )

            val isAllEmptyMoves = updatedMoveHistory.takeLast(4)
                .all { it.word.isEmpty() }

            if (isAllEmptyMoves) {
                concludeGame(updatedGame)
                return@launch
            }

            repository.updateGame(updatedGame)
            _game.value = updatedGame
        }
    }

    private suspend fun concludeGame(game: Game) {
        val letters1 = game.currentLetters1
        val letters2 = game.currentLetters2

        val isPlayer1Out = letters1.isEmpty()
        val isPlayer2Out = letters2.isEmpty()

        val penalty1 = letters1.sumOf { letterScore(it) }
        val penalty2 = letters2.sumOf { letterScore(it) }

        var player1Score = game.player1Score
        var player2Score = game.player2Score

        when {
            isPlayer1Out && isPlayer2Out -> {
                // Her iki oyuncu bitirdiyse sadece ceza puanları düşülür
                player1Score -= penalty1
                player2Score -= penalty2
            }
            isPlayer1Out -> {
                // Player 1 bitti → rakibin harflerinin puanını kazanır
                player1Score += penalty2
                player2Score -= penalty2
            }
            isPlayer2Out -> {
                // Player 2 bitti → rakibin harflerinin puanını kazanır
                player2Score += penalty1
                player1Score -= penalty1
            }
            else -> {
                // Her iki oyuncuda harf varsa ama oyun sonlanıyorsa (örneğin 4 pas) → ikisi de ceza alır
                player1Score -= penalty1
                player2Score -= penalty2
            }
        }

        val winnerId = when {
            player1Score > player2Score -> game.player1Id
            player2Score > player1Score -> game.player2Id
            else -> null // Beraberlik
        }

        val finalGame = game.copy(
            player1Score = player1Score,
            player2Score = player2Score
        )

        repository.endGame(finalGame, winnerId)
    }


    fun letterScore(letter: String): Int {
        return when (letter.uppercase()) {
            "A", "E", "İ", "N", "L", "R" -> 1
            "K", "T", "M", "U", "Y", "S" -> 2
            "B", "D", "O", "Z" -> 3
            "C", "Ş", "H" -> 4
            "Ç", "P" -> 5
            "G" -> 6
            "F", "V" -> 7
            "J" -> 8
            "Ğ", "Ö", "Ü" -> 9
            "Q", "W", "X" -> 10
            else -> 0
        }
    }

    fun updateValidPositions() {
        val currentGame = _game.value ?: return
        val board = currentGame.board
        val pendingMoves = currentGame.pendingMoves
        val newValidPositions = mutableListOf<Position>()

        val centerTileEmpty = board["7-7"]?.letter.isNullOrEmpty()
        if (centerTileEmpty && pendingMoves.isEmpty()) {
            newValidPositions.add(Position(7, 7))
            _validPositions.value = newValidPositions
            return
        }

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
            val first = sorted.first()

            val (dr, dc) = when (direction) {
                "horizontal"      -> 0 to 1
                "vertical"        -> 1 to 0
                "diagonal-main"   -> 1 to 1
                "diagonal-anti"   -> 1 to -1
                else              -> return
            }

            // Negatif yönde en başa kadar git
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

            // Pozitif yönde en sona kadar git
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

            // Baş ve son arasındaki tüm boş hücreleri valid say
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