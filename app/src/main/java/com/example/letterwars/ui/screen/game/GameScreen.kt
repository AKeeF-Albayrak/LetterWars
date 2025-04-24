package com.example.letterwars.ui.screen.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.letterwars.ui.screen.common.FloatingLettersBackground
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.roundToInt

@Composable
fun GameScreen(gameId: String?, navController: NavController) {
    // State for the dragged letter
    val draggedLetter = remember { mutableStateOf<DraggedLetter?>(null) }

    LaunchedEffect(gameId) {
        println("Game ID: $gameId")
    }

    // State for the board
    val boardState = remember {
        List(15) { row ->
            List(15) { col ->
                when {
                    // Center cell
                    row == 7 && col == 7 -> BoardCell(type = CellType.CENTER)

                    // Triple Word Score cells
                    (row == 0 || row == 14) && (col == 0 || col == 7 || col == 14) ||
                            (row == 7 && (col == 0 || col == 14)) -> BoardCell(type = CellType.TRIPLE_WORD)

                    // Double Word Score cells
                    row == col || row + col == 14 -> {
                        if ((row >= 1 && row <= 4) || (row >= 10 && row <= 13)) {
                            BoardCell(type = CellType.DOUBLE_WORD)
                        } else {
                            BoardCell()
                        }
                    }

                    // Triple Letter Score cells
                    (row == 1 || row == 13) && (col == 5 || col == 9) ||
                            (row == 5 || row == 9) && (col == 1 || col == 5 || col == 9 || col == 13) ->
                        BoardCell(type = CellType.TRIPLE_LETTER)

                    // Double Letter Score cells
                    (row == 0 || row == 14) && (col == 3 || col == 11) ||
                            (row == 2 || row == 12) && (col == 6 || col == 8) ||
                            (row == 3 || row == 11) && (col == 0 || col == 7 || col == 14) ||
                            (row == 6 || row == 8) && (col == 2 || col == 6 || col == 8 || col == 12) ||
                            (row == 7 && (col == 3 || col == 11)) -> BoardCell(type = CellType.DOUBLE_LETTER)

                    // Normal cells
                    else -> BoardCell()
                }
            }.toMutableStateList()
        }
    }

    // State for the rack letters
    val rackLetters = remember {
        mutableStateListOf(
            RackLetter("E", 1),
            RackLetter("N", 1),
            RackLetter("O", 2),
            RackLetter("T", 1),
            RackLetter("N", 1),
            RackLetter("R", 1),
            RackLetter("A", 1)
        )
    }

    // State for tracking which cell each letter is placed on
    val placedLetters = remember { mutableStateMapOf<Pair<Int, Int>, RackLetter>() }

    // State for timer
    val remainingTime = remember { mutableStateOf("02:30") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with scores
            GameTopBar(
                playerName = "Oyuncu",
                playerScore = 86,
                remainingLetters = 79,
                opponentName = "Rakip",
                opponentScore = 0
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timer
            TimerDisplay(remainingTime = remainingTime.value)

            Spacer(modifier = Modifier.height(8.dp))

            // Game board
            GameBoard(
                boardState = boardState,
                placedLetters = placedLetters,
                onCellClick = { row, col ->
                    draggedLetter.value?.let { letter ->
                        // Place the letter on the board
                        placedLetters[Pair(row, col)] = RackLetter(letter.letter, letter.points)

                        // Remove the letter from the rack if it came from there
                        if (letter.rackIndex != -1) {
                            rackLetters[letter.rackIndex] = RackLetter("", 0)
                        }

                        // Clear the dragged letter
                        draggedLetter.value = null
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Letter rack
            LetterRack(
                letters = rackLetters,
                onLetterDrag = { letter, points, index, offset ->
                    draggedLetter.value = DraggedLetter(letter, points, index, offset)
                }
            )
        }

        // Dragged letter overlay
        draggedLetter.value?.let { letter ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(letter.offset.x.roundToInt(), letter.offset.y.roundToInt()) }
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFD700))
                    .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = letter.letter,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = letter.points.toString(),
                        fontSize = 10.sp,
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GameTopBar(
    playerName: String,
    playerScore: Int,
    remainingLetters: Int,
    opponentName: String,
    opponentScore: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Player info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = playerName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = playerScore.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Remaining letters
        Card(
            modifier = Modifier.padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = remainingLetters.toString(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Opponent info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = opponentName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = opponentScore.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun TimerDisplay(remainingTime: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = "Kalan Süre: $remainingTime",
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun GameBoard(
    boardState: List<List<BoardCell>>,
    placedLetters: Map<Pair<Int, Int>, RackLetter>,
    onCellClick: (Int, Int) -> Unit
) {
    val cellPositions = remember { mutableStateMapOf<Pair<Int, Int>, Offset>() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            for (i in boardState.indices) {
                Row(
                    modifier = Modifier.weight(1f)
                ) {
                    for (j in boardState[i].indices) {
                        val cell = boardState[i][j]
                        val placedLetter = placedLetters[Pair(i, j)]

                        BoardCell(
                            cell = cell,
                            placedLetter = placedLetter,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .onGloballyPositioned { coordinates ->
                                    cellPositions[Pair(i, j)] = coordinates.positionInRoot()
                                },
                            onClick = { onCellClick(i, j) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoardCell(
    cell: BoardCell,
    placedLetter: RackLetter?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = when (cell.type) {
        CellType.NORMAL -> MaterialTheme.colorScheme.surface
        CellType.DOUBLE_LETTER -> Color(0xFF90CAF9) // Light Blue
        CellType.TRIPLE_LETTER -> Color(0xFF1E88E5) // Blue
        CellType.DOUBLE_WORD -> Color(0xFFFFCCBC) // Light Orange
        CellType.TRIPLE_WORD -> Color(0xFFFF8A65) // Orange
        CellType.CENTER -> Color(0xFFFFD54F) // Yellow
    }

    Box(
        modifier = modifier
            .padding(1.dp)
            .background(backgroundColor)
            .border(0.5.dp, Color.Black.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onClick() },
                    onDrag = { _, _ -> },
                    onDragEnd = {},
                    onDragCancel = {}
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (placedLetter != null) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFFD700)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = placedLetter.letter,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (placedLetter.points > 0) {
                        Text(
                            text = placedLetter.points.toString(),
                            fontSize = 8.sp,
                            modifier = Modifier.offset(y = (-1).dp)
                        )
                    }
                }
            }
        } else {
            when (cell.type) {
                CellType.DOUBLE_LETTER -> Text("2L", fontSize = 8.sp)
                CellType.TRIPLE_LETTER -> Text("3L", fontSize = 8.sp)
                CellType.DOUBLE_WORD -> Text("2W", fontSize = 8.sp)
                CellType.TRIPLE_WORD -> Text("3W", fontSize = 8.sp)
                else -> {}
            }
        }
    }
}

@Composable
fun LetterRack(
    letters: List<RackLetter>,
    onLetterDrag: (String, Int, Int, Offset) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF8D6E63) // Brown color for wooden rack feel
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in letters.indices) {
                val letter = letters[i]
                if (letter.letter.isNotEmpty()) {
                    LetterTile(
                        letter = letter.letter,
                        points = letter.points,
                        onDragStart = { offset ->
                            onLetterDrag(letter.letter, letter.points, i, offset)
                        }
                    )
                } else {
                    // Empty slot
                    Spacer(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FFFFFF))
                    )
                }
            }
        }
    }
}

@Composable
fun LetterTile(
    letter: String,
    points: Int,
    onDragStart: (Offset) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isDragging) 1.2f else 1f)

    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFFD700)) // Gold color for letter tiles
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onDragStart(offset)
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = letter,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = points.toString(),
                fontSize = 10.sp,
                modifier = Modifier.offset(y = (-2).dp)
            )
        }
    }
}

// Data classes and enums
enum class CellType {
    NORMAL,
    DOUBLE_LETTER,
    TRIPLE_LETTER,
    DOUBLE_WORD,
    TRIPLE_WORD,
    CENTER
}

data class BoardCell(
    val type: CellType = CellType.NORMAL
)

data class RackLetter(
    val letter: String,
    val points: Int
)

data class DraggedLetter(
    val letter: String,
    val points: Int,
    val rackIndex: Int,
    val offset: Offset
)
