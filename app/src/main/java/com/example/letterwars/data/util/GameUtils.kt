package com.example.letterwars.data.util

import android.content.Context
import com.example.letterwars.data.model.*
import com.example.letterwars.ui.screen.game.RackLetter
import java.util.Locale

fun generateEmptyBoard(): Map<String, GameTile> {
    val board = mutableMapOf<String, GameTile>()

    for (i in 0..14) {
        for (j in 0..14) {
            val cellType = when {
                (i == 7 && j == 7) -> CellType.CENTER
                (i == 0 || i == 14) && (j == 0 || j == 7 || j == 14) ||
                        (i == 7 && (j == 0 || j == 14)) -> CellType.TRIPLE_WORD
                i == j || i + j == 14 -> {
                    if ((i in 1..4) || (i in 10..13)) CellType.DOUBLE_WORD else CellType.NORMAL
                }
                (i == 1 || i == 13) && (j == 5 || j == 9) ||
                        (i == 5 || i == 9) && (j == 1 || j == 5 || j == 9 || j == 13) -> CellType.TRIPLE_LETTER
                (i == 0 || i == 14) && (j == 3 || j == 11) ||
                        (i == 2 || i == 12) && (j == 6 || j == 8) ||
                        (i == 3 || i == 11) && (j == 0 || j == 7 || j == 14) ||
                        (i == 6 || i == 8) && (j == 2 || j == 6 || j == 8 || j == 12) ||
                        (i == 7 && (j == 3 || j == 11)) -> CellType.DOUBLE_LETTER
                else -> CellType.NORMAL
            }

            board["$i-$j"] = GameTile(letter = null, cellType = cellType)
        }
    }

    return board
}

fun generateLetterPool(): MutableMap<Char, Int> = mutableMapOf(
    'A' to 12, 'B' to 2, 'C' to 2, 'Ç' to 2, 'D' to 2,
    'E' to 8, 'F' to 1, 'G' to 1, 'Ğ' to 1, 'H' to 1,
    'I' to 4, 'İ' to 7, 'J' to 1, 'K' to 7, 'L' to 7,
    'M' to 4, 'N' to 5, 'O' to 3, 'Ö' to 1, 'P' to 1,
    'R' to 6, 'S' to 3, 'Ş' to 2, 'T' to 5, 'U' to 3,
    'Ü' to 2, 'V' to 1, 'Y' to 2, 'Z' to 2, '*' to 2
)

fun drawLetters(pool: MutableMap<Char, Int>, count: Int): List<Char> {
    val available = pool.flatMap { (char, qty) -> List(qty) { char } }.toMutableList()
    available.shuffle()
    val drawn = available.take(count)
    drawn.forEach { ch ->
        pool[ch] = (pool[ch] ?: 1) - 1
        if (pool[ch] == 0) pool.remove(ch)
    }
    return drawn
}

private var cachedWordSet: Set<String>? = null

fun checkWord(context: Context, word: String): Boolean {
    val startTime = System.currentTimeMillis()
    if (cachedWordSet == null) {
        cachedWordSet = try {
            context.assets.open("TurkceKelimeler/turkce_kelime_listesi.txt")
                .bufferedReader()
                .useLines { lines ->
                    lines.map { it.trim().lowercase(Locale("tr", "TR")) }.toSet()
                }
        } catch (e: Exception) {
            println("❌ Kelime listesi yüklenemedi: ${e.message}")
            emptySet()
        }
        println("📦 Kelime listesi belleğe alındı (toplam ${cachedWordSet?.size} kelime)")
    }

    val lowerWord = word.lowercase(Locale("tr", "TR"))
    val jokerCount = lowerWord.count { it == '*' }

    val alphabet = "abcçdefgğhıijklmnoöprsştuüvyz"
    val result = when (jokerCount) {
        0 -> cachedWordSet!!.contains(lowerWord)
        1 -> alphabet.any { ch ->
            cachedWordSet!!.contains(lowerWord.replaceFirst("*", ch.toString()))
        }
        2 -> alphabet.any { ch1 ->
            alphabet.any { ch2 ->
                val replaced = lowerWord
                    .replaceFirst("*", ch1.toString())
                    .replaceFirst("*", ch2.toString())
                cachedWordSet!!.contains(replaced)
            }
        }
        else -> false
    }

    val duration = System.currentTimeMillis() - startTime
    println("🔎 Kelime: \"$word\" | Joker: $jokerCount | Geçerli mi: $result | Süre: ${duration}ms")
    return result
}

fun detectDirection(positions: Set<Position>): String? {
    if (positions.size < 2) return null
    val rows = positions.map { it.row }.toSet()
    val cols = positions.map { it.col }.toSet()
    return when {
        rows.size == 1 -> "horizontal"
        cols.size == 1 -> "vertical"
        positions.all { it.row - it.col == positions.first().row - positions.first().col } -> "diagonal-main"
        positions.all { it.row + it.col == positions.first().row + positions.first().col } -> "diagonal-anti"
        else -> null
    }
}

val letterPoints: Map<Char, Int> = mapOf(
    'A' to 1, 'B' to 3, 'C' to 4, 'Ç' to 4, 'D' to 3,
    'E' to 1, 'F' to 7, 'G' to 5, 'Ğ' to 8, 'H' to 3,
    'I' to 2, 'İ' to 1, 'J' to 10, 'K' to 1, 'L' to 1,
    'M' to 2, 'N' to 1, 'O' to 2, 'Ö' to 7, 'P' to 5,
    'R' to 1, 'S' to 2, 'Ş' to 4, 'T' to 1, 'U' to 2,
    'Ü' to 3, 'V' to 7, 'Y' to 3, 'Z' to 4, '*' to 0
)

fun resolveWord(board: Map<String, GameTile>, placed: Set<Position>, direction: String): WordInfo {
    val start = placed.minWith(compareBy({ it.row }, { it.col }))
    val (drList, dcList) = when (direction) {
        "horizontal" -> listOf(0 to -1, 0 to 1)
        "vertical" -> listOf(-1 to 0, 1 to 0)
        "diagonal-main" -> listOf(-1 to -1, 1 to 1)
        "diagonal-anti" -> listOf(-1 to 1, 1 to -1)
        else -> return WordInfo("", emptyList())
    }.unzip()

    val positions = mutableSetOf<Position>()
    for ((dr, dc) in drList.zip(dcList)) {
        var r = start.row
        var c = start.col
        while (true) {
            r += dr
            c += dc
            val tile = board["$r-$c"]
            if (tile?.letter != null) positions.add(Position(r, c)) else break
        }
    }

    positions.addAll(placed)
    val sorted = positions.toList().distinct().sortedWith(compareBy({ it.row }, { it.col }))
    val word = sorted.joinToString("") { board["${it.row}-${it.col}"]?.letter ?: "" }
    return WordInfo(word, sorted)
}

fun findCrossWords(
    board: Map<String, GameTile>,
    placedLetters: Map<Position, RackLetter>,
    mainDirection: String
): List<WordInfo> {
    val crossWords = mutableListOf<WordInfo>()
    val deltas = when (mainDirection) {
        "horizontal" -> listOf(-1 to 0, 1 to 0)
        "vertical" -> listOf(0 to -1, 0 to 1)
        "diagonal-main" -> listOf(-1 to 1, 1 to -1)
        "diagonal-anti" -> listOf(-1 to -1, 1 to 1)
        else -> return emptyList()
    }

    for ((pos, _) in placedLetters) {
        val wordPositions = mutableListOf(pos)
        for ((dr, dc) in deltas) {
            var r = pos.row
            var c = pos.col
            while (true) {
                r += dr
                c += dc
                val tile = board["$r-$c"]
                if (tile?.letter != null) wordPositions.add(Position(r, c)) else break
            }
        }
        val sorted = wordPositions.distinct().sortedWith(compareBy({ it.row }, { it.col }))
        if (sorted.size > 1) {
            val word = sorted.joinToString("") { board["${it.row}-${it.col}"]?.letter ?: "" }
            crossWords.add(WordInfo(word, sorted))
        }
    }

    return crossWords
}

fun checkWords(
    context: Context,
    board: Map<String, GameTile>,
    placedLetters: Map<Position, RackLetter>
): List<WordInfo>? {
    if (placedLetters.isEmpty()) return null
    val direction = detectDirection(placedLetters.keys) ?: return null

    val allWords = mutableListOf<WordInfo>()

    val mainWord = resolveWord(board, placedLetters.keys, direction)
    if (!checkWord(context, mainWord.word)) return null
    allWords.add(mainWord)

    val crossWords = findCrossWords(board, placedLetters, direction)
    for (w in crossWords) {
        if (!checkWord(context, w.word)) return null
        allWords.add(w)
    }

    return allWords
}

fun calculateScore(
    board: Map<String, GameTile>,
    placedLetters: Map<Position, RackLetter>,
    words: List<WordInfo>
): Int {
    var total = 0

    for (word in words) {
        var wordScore = 0
        var wordMultiplier = 1

        for (pos in word.positions) {
            val tile = board["${pos.row}-${pos.col}"]
            val letter = tile?.letter?.uppercase()?.getOrNull(0) ?: continue
            val baseScore = letterPoints[letter] ?: 0

            val letterScore = when (tile.cellType) {
                CellType.DOUBLE_LETTER -> baseScore * 2
                CellType.TRIPLE_LETTER -> baseScore * 3
                else -> baseScore
            }

            wordScore += letterScore

            if (placedLetters.containsKey(pos)) {
                wordMultiplier *= when (tile.cellType) {
                    CellType.DOUBLE_WORD -> 2
                    CellType.TRIPLE_WORD -> 3
                    else -> 1
                }
            }
        }

        total += wordScore * wordMultiplier
    }

    return total
}
