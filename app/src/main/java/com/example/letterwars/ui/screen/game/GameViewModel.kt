package com.example.letterwars.ui.screen.game

import android.app.Application
import android.util.Log
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

    // Tetiklenen özel efektleri takip etmek için StateFlow
    private val _triggeredEffects = MutableStateFlow<List<TriggeredEffect>>(emptyList())
    val triggeredEffects: StateFlow<List<TriggeredEffect>> = _triggeredEffects

    // Tetiklenen efektleri temsil eden veri sınıfı
    data class TriggeredEffect(
        val position: Position,
        val mineType: MineType? = null,
        val rewardType: RewardType? = null
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
                println("Winner Id Nasi NULL oluo")
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



    fun clearPendingMoves(placedLetters: Map<Position, RackLetter>) {
        val currentGame = _game.value ?: return
        val updatedBoard = currentGame.board.toMutableMap()
        val updatedPendingMoves = currentGame.pendingMoves.toMutableMap()

        // 1. Harfleri tahtadan sil
        for (key in currentGame.pendingMoves.keys) {
            val tile = updatedBoard[key]?.copy(letter = null)
            if (tile != null) {
                updatedBoard[key] = tile
            }
        }

        // 2. Harfleri kullanıcıya geri ver
        val updatedLetters = if (currentGame.currentTurnPlayerId == currentGame.player1Id)
            currentGame.currentLetters1.toMutableList()
        else
            currentGame.currentLetters2.toMutableList()

        placedLetters.values.forEach {
            updatedLetters.add(it.letter)
        }

        // 3. Güncellenmiş game nesnesini oluştur
        val updatedGame = currentGame.copy(
            board = updatedBoard,
            pendingMoves = emptyMap(),
            currentLetters1 = if (currentGame.currentTurnPlayerId == currentGame.player1Id) updatedLetters else currentGame.currentLetters1,
            currentLetters2 = if (currentGame.currentTurnPlayerId == currentGame.player2Id) updatedLetters else currentGame.currentLetters2,
        )

        _game.value = updatedGame

        viewModelScope.launch {
            repository.updateGame(updatedGame)
        }
    }



    fun confirmMove(placedLetters: Map<Position, RackLetter>) {
        viewModelScope.launch {
            val currentGame = _game.value ?: return@launch
            val context = getApplication<Application>().applicationContext

            // Tetiklenen efektleri topla
            val triggeredEffectsList = mutableListOf<TriggeredEffect>()
            println("sa")

            // 1. Tahtayı güncelle
            val updatedBoard = currentGame.board.toMutableMap()
            placedLetters.forEach { (pos, rackLetter) ->
                val key = "${pos.row}-${pos.col}"
                val currentTile = updatedBoard[key]

                // Eğer bu hücrede bir mine veya reward varsa, efektler listesine ekle
                if (currentTile?.mineType != null || currentTile?.rewardType != null) {
                    triggeredEffectsList.add(
                        TriggeredEffect(
                            position = pos,
                            mineType = currentTile.mineType,
                            rewardType = currentTile.rewardType
                        )
                    )
                }

                updatedBoard[key] = currentTile?.copy(letter = rackLetter.letter) ?: GameTile(letter = rackLetter.letter)

            }

            // Tetiklenen efektleri güncelle
            _triggeredEffects.value = triggeredEffectsList

            println("⏳ checkWords çağrılıyor")
            // 2. Tüm kelimeleri bul ve doğrula
            val wordList = checkWords(context, updatedBoard, placedLetters)

            // Eğer kelime geçersizse, harfleri geri ver ve işlemi sonlandır
            if (wordList == null) {
                // 1. Harfleri oyuncunun rack'ine geri koy
                val updatedCurrentLetters = if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
                    currentGame.currentLetters1.toMutableList()
                } else {
                    currentGame.currentLetters2.toMutableList()
                }
                placedLetters.values.forEach { letter -> updatedCurrentLetters += letter.letter }

                // 2. Tahtadan harfleri temizle
                val revertedBoard = currentGame.board.toMutableMap()
                placedLetters.keys.forEach { pos ->
                    val key = "${pos.row}-${pos.col}"
                    val originalTile = currentGame.board[key]
                    revertedBoard[key] = (originalTile ?: GameTile()).copy(letter = null)
                }

                val updatedGame = currentGame.copy(
                    board = revertedBoard,
                    pendingMoves = emptyMap(),
                    currentLetters1 = if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                        updatedCurrentLetters else currentGame.currentLetters1,
                    currentLetters2 = if (currentGame.currentTurnPlayerId == currentGame.player2Id)
                        updatedCurrentLetters else currentGame.currentLetters2
                )

                repository.updateGame(updatedGame)
                _game.value = updatedGame
                return@launch
            }

            println("bitmedi")
            wordList.forEach { word ->
                println("📝 Kelime: ${word.word}, Pozisyonlar: ${word.positions}")
            }

            // 3. Puanı hesapla
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


            // 4. Kullanılan harfleri çıkar
            val updatedCurrentLetters = if (currentGame.currentTurnPlayerId == currentGame.player1Id) {
                currentGame.currentLetters1.toMutableList()
            } else {
                currentGame.currentLetters2.toMutableList()
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
                currentLetters1 = if (currentGame.currentTurnPlayerId == currentGame.player1Id) updatedCurrentLetters else currentGame.currentLetters1,
                currentLetters2 = if (currentGame.currentTurnPlayerId == currentGame.player2Id) updatedCurrentLetters else currentGame.currentLetters2,
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

            val bothPlayersOutOfLetters =
                updatedGame.currentLetters1.isEmpty() && updatedGame.currentLetters2.isEmpty()

            val player1OutOfLetters = updatedGame.currentLetters1.isEmpty()
            val player2OutOfLetters = updatedGame.currentLetters2.isEmpty()

            val lastMove = updatedGame.moveHistory.lastOrNull()
            val isLastMoveEmptyWord = lastMove?.word.isNullOrEmpty()

            val shouldConclude = bothPlayersOutOfLetters ||
                    (player1OutOfLetters && updatedGame.currentTurnPlayerId == updatedGame.player2Id && isLastMoveEmptyWord) ||
                    (player2OutOfLetters && updatedGame.currentTurnPlayerId == updatedGame.player1Id && isLastMoveEmptyWord)

            if (shouldConclude) {
                concludeGame(updatedGame)
                return@launch
            }
        }
    }

    fun clearTriggeredEffects() {
        _triggeredEffects.value = emptyList()
    }

    fun passTurn() {
        viewModelScope.launch {
            val currentGame = _game.value ?: return@launch

            // 1 — Kopyalar
            val updatedBoard            = currentGame.board.toMutableMap()
            val updatedRemainingLetters = currentGame.remainingLetters.toMutableMap()
            val updatedCurrentLetters   =
                if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                    currentGame.currentLetters1.toMutableList()
                else
                    currentGame.currentLetters2.toMutableList()

            // 2 — PENDING harfleri geri al ➜ hem board'dan sil hem rack'e ekle
            if (currentGame.pendingMoves.isNotEmpty()) {
                currentGame.pendingMoves.forEach { (key, letter) ->
                    // Board'daki hücreyi harfsiz hâle getir
                    updatedBoard[key] = (updatedBoard[key] ?: GameTile()).copy(letter = null)
                    // Kullanıcının eline geri ekle
                    updatedCurrentLetters += letter
                }
            }

            // 3 — Gerekiyorsa harf çek
            val lettersNeeded = 7 - updatedCurrentLetters.size
            if (lettersNeeded > 0) {
                val newLetters = drawLetters(updatedRemainingLetters, lettersNeeded)
                updatedCurrentLetters.addAll(newLetters)
            }

            // 4 — Sıra değişimi ve zamanlar
            val nextTurnPlayerId =
                if (currentGame.currentTurnPlayerId == currentGame.player1Id)
                    currentGame.player2Id
                else
                    currentGame.player1Id

            val currentTime = System.currentTimeMillis()
            val expireTime  = currentTime + (currentGame.duration.minutes * 60 * 1000)

            // 5 — Pas hamlesini ekle
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

            val isAllEmptyMoves = updatedMoveHistory.size >= 4 &&
                    updatedMoveHistory.takeLast(4).all { it.word.isEmpty() }

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
        val newValidPositions = mutableSetOf<Position>()

        val placedPositions = pendingMoves.keys.mapNotNull { key ->
            val parts = key.split("-")
            if (parts.size == 2) {
                val row = parts[0].toIntOrNull()
                val col = parts[1].toIntOrNull()
                if (row != null && col != null) Position(row, col) else null
            } else null
        }

        // 🚩 1. Hiç hamle yok, merkez boş → ilk hamle
        if (pendingMoves.isEmpty()) {
            val centerTile = board["7-7"]
            if (centerTile?.letter.isNullOrEmpty()) {
                newValidPositions.add(Position(7, 7))
                _validPositions.value = newValidPositions.toList()
                return
            }

            // 🚩 2. Tahtada harfler var ama kullanıcı harf koymamış
            for (row in 0..14) {
                for (col in 0..14) {
                    val key = "$row-$col"
                    val tile = board[key]
                    if (!tile?.letter.isNullOrEmpty()) {
                        val directions = listOf(
                            -1 to 0, 1 to 0, 0 to -1, 0 to 1,
                            -1 to -1, -1 to 1, 1 to -1, 1 to 1
                        )
                        for ((dr, dc) in directions) {
                            val nr = row + dr
                            val nc = col + dc
                            if (nr in 0..14 && nc in 0..14) {
                                val neighborKey = "$nr-$nc"
                                val neighborTile = board[neighborKey]
                                if (neighborTile?.letter.isNullOrEmpty()) {
                                    newValidPositions.add(Position(nr, nc))
                                }
                            }
                        }
                    }
                }
            }

            _validPositions.value = newValidPositions.toList()
            return
        }

        // 🚩 3. Tek harf yerleştirildiyse → 8 yönde boş alanlara doğru ilerle
        if (placedPositions.size == 1) {
            val origin = placedPositions.first()
            val directions = listOf(
                -1 to 0, 1 to 0, 0 to -1, 0 to 1,
                -1 to -1, -1 to 1, 1 to -1, 1 to 1
            )

            for ((dr, dc) in directions) {
                var r = origin.row + dr
                var c = origin.col + dc
                while (r in 0..14 && c in 0..14) {
                    val key = "$r-$c"
                    val tile = board[key]
                    if (tile?.letter.isNullOrEmpty()) {
                        newValidPositions.add(Position(r, c))
                        r += dr
                        c += dc
                    } else break
                }
            }

            _validPositions.value = newValidPositions.toList()
            return
        }

        // 🚩 4. İki veya daha fazla harf konmuşsa → yön belirlenir
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
                val (dr2, dc2) = Pair(-dr, -dc)

                var start = sorted.first()
                var end = sorted.last()

                while (true) {
                    val next = Position(start.row + dr, start.col + dc)
                    val key = "${next.row}-${next.col}"
                    if (next.row in 0..14 && next.col in 0..14 && !board[key]?.letter.isNullOrEmpty()) {
                        start = next
                    } else break
                }

                while (true) {
                    val next = Position(end.row + dr2, end.col + dc2)
                    val key = "${next.row}-${next.col}"
                    if (next.row in 0..14 && next.col in 0..14 && !board[key]?.letter.isNullOrEmpty()) {
                        end = next
                    } else break
                }

                val before = Position(start.row + dr, start.col + dc)
                val after = Position(end.row + dr2, end.col + dc2)

                if (before.row in 0..14 && before.col in 0..14 && board["${before.row}-${before.col}"]?.letter.isNullOrEmpty() == true)
                    newValidPositions.add(before)
                if (after.row in 0..14 && after.col in 0..14 && board["${after.row}-${after.col}"]?.letter.isNullOrEmpty() == true)
                    newValidPositions.add(after)
            }
        }

        _validPositions.value = newValidPositions.toList()

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

    // Method to activate area block
    fun activateAreaBlock(side: String) {
        viewModelScope.launch {
            val currentGame = _game.value ?: return@launch
            val userId = currentUserId ?: return@launch

            repository.activateAreaBlock(currentGame.gameId, userId, side)
        }
    }

    // Method to activate letter freeze
    fun activateLetterFreeze(letterIndices: List<Int>) {
        viewModelScope.launch {
            val currentGame = _game.value ?: return@launch
            val targetPlayerId = if (currentGame.player1Id == currentUserId)
                currentGame.player2Id else currentGame.player1Id

            repository.freezeLetters(currentGame.gameId, targetPlayerId, letterIndices)
        }
    }

    // Method to activate extra turn
    fun activateExtraTurn() {
        viewModelScope.launch {
            val currentGame = _game.value ?: return@launch
            val userId = currentUserId ?: return@launch

            repository.setExtraTurn(currentGame.gameId, userId)
        }
    }
}