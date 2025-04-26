package com.example.letterwars.data.util

import com.example.letterwars.data.model.CellType
import com.example.letterwars.data.model.Game
import com.example.letterwars.data.model.GameTile

fun generateEmptyBoard(): Map<String, GameTile> {
    val board = mutableMapOf<String, GameTile>()

    for (i in 0..14) {
        for (j in 0..14) {
            val cellType = when {
                i == 7 && j == 7 -> CellType.CENTER

                (i == 0 || i == 14) && (j == 0 || j == 7 || j == 14) ||
                        (i == 7 && (j == 0 || j == 14)) -> CellType.TRIPLE_WORD

                i == j || i + j == 14 -> {
                    if ((i in 1..4) || (i in 10..13)) CellType.DOUBLE_WORD
                    else CellType.NORMAL
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

            board["$i-$j"] = GameTile(cellType = cellType)
        }
    }

    return board
}


fun generateLetterPool(): MutableMap<Char, Int> {
    return mutableMapOf(
        'A' to 12, 'B' to 2, 'C' to 2, 'Ç' to 2, 'D' to 2,
        'E' to 8, 'F' to 1, 'G' to 1, 'Ğ' to 1, 'H' to 1,
        'I' to 4, 'İ' to 7, 'J' to 1, 'K' to 7, 'L' to 7,
        'M' to 4, 'N' to 5, 'O' to 3, 'Ö' to 1, 'P' to 1,
        'R' to 6, 'S' to 3, 'Ş' to 2, 'T' to 5, 'U' to 3,
        'Ü' to 2, 'V' to 1, 'Y' to 2, 'Z' to 2, '*' to 2 // JOKER
    )
}

fun drawLetters(pool: MutableMap<Char, Int>, count: Int): List<Char> {
    val available = pool.flatMap { entry -> List(entry.value) { entry.key } }.toMutableList()
    available.shuffle()
    val drawn = available.take(count)

    drawn.forEach {
        pool[it] = pool[it]?.minus(1) ?: 0
        if (pool[it] == 0) pool.remove(it)
    }

    return drawn
}