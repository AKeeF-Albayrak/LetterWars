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
            repository.updateBoardAndPendingMoves(updatedGame.gameId, updatedBoard, updatedMoves)
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

    fun confirmMove(
        placedLetters: Map<Position, RackLetter>
    ) = viewModelScope.launch {
        val currentGame = _game.value ?: return@launch
        val context = getApplication<Application>().applicationContext

        /* ───────────────────────── 1. Tahtayı güncelle ───────────────────────── */

        val triggeredEffectsList = mutableListOf<TriggeredEffect>()
        val updatedBoard = currentGame.board.toMutableMap()

        placedLetters.forEach { (pos, rackLetter) ->
            val key = "${pos.row}-${pos.col}"
            val currentTile = updatedBoard[key]

            // Mayın / ödül tetikleniyorsa kaydet
            if (currentTile?.mineType != null || currentTile?.rewardType != null) {
                triggeredEffectsList += TriggeredEffect(
                    position   = pos,
                    mineType   = currentTile.mineType,
                    rewardType = currentTile.rewardType
                )
            }

            updatedBoard[key] = GameTile(letter = rackLetter.letter)
        }

        _triggeredEffects.value = triggeredEffectsList

        /* ───────────────────────── 2. Kelimeleri doğrula ─────────────────────── */

        val wordList = checkWords(context, updatedBoard, placedLetters)

        if (wordList == null) {
            /* ❗ Hatalı kelime durumunda kullanıcının harflerini **EKLEMEYİN**.
               Harfler zaten oyuncunun elindeydi, ekstra kopya yaratmayın. */

            val revertedBoard = currentGame.board.toMutableMap()
            placedLetters.keys.forEach { pos ->
                val key = "${pos.row}-${pos.col}"
                val originalTile = currentGame.board[key]
                revertedBoard[key] = (originalTile ?: GameTile()).copy(letter = null)
            }

            val updatedGame = currentGame.copy(
                board          = revertedBoard,
                pendingMoves   = emptyMap(),
                // Harf listeleri değişmedi
            )

            repository.updateGame(updatedGame)
            _game.value = updatedGame
            return@launch
        }

        /* ───────────────────────── 3. Puanı hesapla ─────────────────────────── */

        val score = calculateScore(updatedBoard, placedLetters, wordList)

        val updatedPlayer1Score: Int
        val updatedPlayer2Score: Int
        if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
            updatedPlayer1Score = currentGame.player1Score + score
            updatedPlayer2Score = currentGame.player2Score
        } else {
            updatedPlayer1Score = currentGame.player1Score
            updatedPlayer2Score = currentGame.player2Score + score
        }

        /* ───────────────────────── 4. Kullanılan harfleri çıkar ──────────────── */

        val updatedCurrentLetters =
            if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                currentGame.currentLetters1.toMutableList()
            else
                currentGame.currentLetters2.toMutableList()

        placedLetters.values.forEach { rackLetter ->
            updatedCurrentLetters.remove(rackLetter.letter)
        }

        /* ───────────────────────── 5. Yeni harf çek ─────────────────────────── */

        val updatedRemainingLetters = currentGame.remainingLetters.toMutableMap()
        val lettersNeeded = maxOf(0, 7 - updatedCurrentLetters.size) // güvenlik
        val newLetters = drawLetters(updatedRemainingLetters, lettersNeeded)
        updatedCurrentLetters += newLetters

        /* ───────────────────────── 6. Sıradaki oyuncu ───────────────────────── */

        val nextTurnPlayerId =
            if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                currentGame.player2Id
            else
                currentGame.player1Id

        /* ───────────────────────── 7. Zamanlar ──────────────────────────────── */

        val currentTime = System.currentTimeMillis()
        val expireTime  = currentTime + (currentGame.duration.minutes * 60 * 1000)

        /* ───────────────────────── 8. Hamleyi oluştur ───────────────────────── */

        val mainWord = wordList.first()
        val newMove = Move(
            playerId    = currentGame.currentTurnPlayerId,
            word        = mainWord.word,
            positions   = mainWord.positions,
            scoreEarned = score,
            timeMillis  = currentTime
        )

        /* ───────────────────────── 9. Oyunu güncelle ────────────────────────── */

        val updatedMoveHistory = currentGame.moveHistory.toMutableList().apply { add(newMove) }

        val updatedGame = currentGame.copy(
            board             = updatedBoard,
            currentLetters1   = if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                updatedCurrentLetters else currentGame.currentLetters1,
            currentLetters2   = if (currentGame.currentTurnPlayerId == currentGame.player2Id)
                updatedCurrentLetters else currentGame.currentLetters2,
            remainingLetters  = updatedRemainingLetters,
            currentTurnPlayerId = nextTurnPlayerId,
            moveHistory       = updatedMoveHistory,
            player1Score      = updatedPlayer1Score,
            player2Score      = updatedPlayer2Score,
            pendingMoves      = emptyMap(),
            startTimeMillis   = currentTime,
            expireTimeMillis  = expireTime
        )

        repository.updateGame(updatedGame)
        _game.value = updatedGame

        /* ───────────────────────── 10. Oyun bitti mi? ───────────────────────── */

        val bothPlayersOut = updatedGame.currentLetters1.isEmpty() &&
                updatedGame.currentLetters2.isEmpty()

        val player1Out = updatedGame.currentLetters1.isEmpty()
        val player2Out = updatedGame.currentLetters2.isEmpty()
        val lastMove   = updatedGame.moveHistory.lastOrNull()
        val lastEmpty  = lastMove?.word.isNullOrEmpty()

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

            // 1 — Kopyalar
            val updatedBoard            = currentGame.board.toMutableMap()
            val updatedRemainingLetters = currentGame.remainingLetters.toMutableMap()
            val updatedCurrentLetters   =
                if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                    currentGame.currentLetters1.toMutableList()
                else
                    currentGame.currentLetters2.toMutableList()

            // 2 — PENDING harfleri geri al ➜ hem board’dan sil hem rack’e ekle
            if (currentGame.pendingMoves.isNotEmpty()) {
                currentGame.pendingMoves.forEach { (key, letter) ->
                    // Board’daki hücreyi harfsiz hâle getir
                    updatedBoard[key] = (updatedBoard[key] ?: GameTile()).copy(letter = null)
                    // Kullanıcının eline geri ekle
                    updatedCurrentLetters += letter
                }
            }

            // 3 — Gerekiyorsa harf çek
            val lettersNeeded = 7 - updatedCurrentLetters.size
            if (lettersNeeded > 0) {
                val newLetters = drawLetters(updatedRemainingLetters, lettersNeeded)
                updatedCurrentLetters.addAll(newLetters)
            }

            // 4 — Sıra değişimi ve zamanlar
            val nextTurnPlayerId =
                if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                    currentGame.player2Id
                else
                    currentGame.player1Id

            val currentTime = System.currentTimeMillis()
            val expireTime  = currentTime + (currentGame.duration.minutes * 60 * 1000)

            // 5 — Pas hamlesini ekle
            val updatedMoveHistory = currentGame.moveHistory.toMutableList().apply {
                add(
                    Move(
                        playerId = currentGame.currentTurnPlayerId,
                        word = "",                       // pas
                        positions = emptyList(),
                        scoreEarned = 0,
                        timeMillis = currentTime
                    )
                )
            }

            val updatedGame = currentGame.copy(
                board               = updatedBoard,
                currentTurnPlayerId = nextTurnPlayerId,
                currentLetters1     = if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                    updatedCurrentLetters else currentGame.currentLetters1,
                currentLetters2     = if (currentGame.currentTurnPlayerId == currentGame.player2Id)
                    updatedCurrentLetters else currentGame.currentLetters2,
                remainingLetters    = updatedRemainingLetters,
                pendingMoves        = emptyMap(),                // temizlendi
                startTimeMillis     = currentTime,
                expireTimeMillis    = expireTime,
                moveHistory         = updatedMoveHistory
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


    private fun letterScore(letter: String): Int {
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