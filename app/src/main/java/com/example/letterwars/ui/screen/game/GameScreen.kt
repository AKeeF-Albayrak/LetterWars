package com.example.letterwars.ui.screen.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.letterwars.data.model.CellType
import com.example.letterwars.data.model.Game
import com.example.letterwars.data.model.GameTile
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GameScreen(gameId: String?, navController: NavController) {

    val viewModel: GameViewModel = viewModel()
    val gameState by viewModel.game.collectAsState()

    LaunchedEffect(gameId) {
        if (gameId != null) {
            viewModel.listenGameChanges(gameId)
        }
    }

    val showGameOver = remember { mutableStateOf(false) }
    val loserPlayerId = remember { mutableStateOf<String?>(null) }

    var draggedLetter by remember { mutableStateOf<DraggedLetter?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }

    val selectedLetter = remember { mutableStateOf<SelectedLetter?>(null) }

    val isPlayerTurn by remember(gameState?.currentTurnPlayerId) {
        derivedStateOf {
            gameState?.currentTurnPlayerId == viewModel.currentUserId
        }
    }

    val remainingTimeSeconds = remember { mutableStateOf(150) }

    LaunchedEffect(gameState?.gameId) {
        gameState?.let { game ->
            remainingTimeSeconds.value = game.duration.minutes.times(60)

            while (remainingTimeSeconds.value > 0) {
                delay(1000L)
                remainingTimeSeconds.value -= 1
            }

            if (remainingTimeSeconds.value <= 0) {
                val loserId = game.currentTurnPlayerId
                loserPlayerId.value = loserId
                viewModel.endGame(game, loserId)
                showGameOver.value = true
            }
        }
    }


    val boardState = remember(gameState?.board) {
        List(15) { row ->
            List(15) { col ->
                val key = "$row-$col"
                gameState?.board?.get(key) ?: GameTile()
            }
        }
    }

    val rackLetters = remember(gameState?.currentLetters) {
        mutableStateListOf<RackLetter>().apply {
            gameState?.currentLetters?.forEach { letter ->
                add(RackLetter(letter, 0))
            }
        }
    }

    val placedLetters = remember { mutableStateMapOf<Pair<Int, Int>, RackLetter>() }

    val cellPositions = remember { mutableStateMapOf<Pair<Int, Int>, Pair<Offset, Size>>() }

    val placeLetter = { letter: RackLetter, rackIndex: Int, row: Int, col: Int ->
        placedLetters[Pair(row, col)] = letter

        if (rackIndex != -1) {
            rackLetters[rackIndex] = RackLetter(letter = "", points = 0)
        }

        viewModel.addPendingMove(row, col, letter.letter)

        selectedLetter.value = null
        draggedLetter = null

        viewModel.updateValidPositions()
    }


    val validPlacementPositions by viewModel.validPositions.collectAsState()


    // Function to check if a position is over a valid board cell
    val findCellAtPosition = { position: Offset ->
        var foundCell: Pair<Int, Int>? = null

        cellPositions.forEach { (cellCoord, cellBounds) ->
            val (cellPos, cellSize) = cellBounds
            if (position.x >= cellPos.x &&
                position.x <= cellPos.x + cellSize.width &&
                position.y >= cellPos.y &&
                position.y <= cellPos.y + cellSize.height) {

                // Check if cell is empty
                if (!placedLetters.containsKey(cellCoord)) {
                    foundCell = cellCoord
                }
            }
        }

        foundCell
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main game card - çiziminize göre düzenlendi
        Card(
            modifier = Modifier
                .fillMaxSize()
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Player cards at the top
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Player card
                        PlayerScoreCard(
                            name = "Oyuncu",
                            score = 118,
                            isActive = isPlayerTurn,
                            showArrow = false,
                            arrowDirection = ""
                        )

                        PlayerScoreCard(
                            name = "Rakip",
                            score = 89,
                            isActive = !isPlayerTurn,
                            showArrow = false,
                            arrowDirection = "",
                            onClick = {
                                // Burada arkadaş ekleme işlevi olacak
                                println("Rakip kartına tıklandı, arkadaş ekleme işlevi burada olacak")
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPlayerTurn) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Oyuncu Sırası",
                                tint = Color.Green,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        val currentGame = gameState

                        if (currentGame != null) {
                            ClockTimer(
                                remainingSeconds = remainingTimeSeconds.value,
                                totalSeconds = currentGame.duration.minutes * 60
                            )
                        }

                        if (!isPlayerTurn) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Rakip Sırası",
                                tint = Color.Green,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                    }

                    // Game board
                    GameBoard(
                        boardState = boardState,
                        placedLetters = placedLetters,
                        validPlacementPositions = validPlacementPositions,
                        onCellClick = { row, col ->
                            selectedLetter.value?.let { selected ->
                                if (validPlacementPositions.contains(Pair(row, col)) && !(row == 7 && col == 7)) {
                                    placeLetter(
                                        RackLetter(selected.letter, selected.points),
                                        selected.rackIndex,
                                        row,
                                        col
                                    )
                                }
                            }
                        },
                        onCellPositioned = { row, col, offset, size ->
                            cellPositions[Pair(row, col)] = Pair(offset, size)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if(isPlayerTurn){
                        GameActionsCard(
                            onSurrender = {
                                if (gameState != null && viewModel.currentUserId != null) {
                                    viewModel.endGame(gameState!!, viewModel.currentUserId!!)
                                    showGameOver.value = true
                                }
                            },
                            onPass = {
                                viewModel.passTurn()
                                placedLetters.clear()
                            },
                            onConfirm = {
                                viewModel.confirmMove(placedLetters)
                                placedLetters.clear()
                            },
                            onUndo = {
                                placedLetters.forEach { (_, rackLetter) ->
                                    val emptyIndex = rackLetters.indexOfFirst { it.letter.isEmpty() }
                                    if (emptyIndex != -1) {
                                        rackLetters[emptyIndex] = rackLetter
                                    } else {
                                        rackLetters.add(rackLetter)
                                    }
                                }
                                placedLetters.clear()
                                viewModel.clearPendingMoves()
                                viewModel.updateValidPositions()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    LetterRack(
                        letters = rackLetters,
                        selectedLetter = selectedLetter,
                        isPlayerTurn = isPlayerTurn,
                        onLetterClick = { letter, points, index ->
                            if (selectedLetter.value?.rackIndex == index) {
                                selectedLetter.value = null
                            } else {
                                selectedLetter.value = SelectedLetter(letter, points, index)
                            }
                        },
                        onLetterDragStart = { letter, points, index, offset, position ->
                            selectedLetter.value = null
                            draggedLetter = DraggedLetter(letter, points, index)
                            dragOffset = offset
                            dragStartPosition = position
                        },
                        onLetterDrag = { change, dragAmount ->
                            dragOffset += dragAmount
                        },
                        onLetterDragEnd = {
                            val currentPosition = dragStartPosition + dragOffset
                            val targetCell = findCellAtPosition(currentPosition)

                            if (targetCell != null && !(targetCell.first == 7 && targetCell.second == 7)) {
                                draggedLetter?.let { letter ->
                                    placeLetter(
                                        RackLetter(letter.letter, letter.points),
                                        letter.rackIndex,
                                        targetCell.first,
                                        targetCell.second
                                    )
                                }
                            }

                            draggedLetter = null
                            dragOffset = Offset.Zero
                        },
                        onShuffle = {
                            rackLetters.shuffle()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Dragged letter overlay
                draggedLetter?.let { letter ->
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (dragStartPosition.x + dragOffset.x).roundToInt(),
                                    (dragStartPosition.y + dragOffset.y).roundToInt()
                                )
                            }
                            .size(50.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFD700))
                            .border(1.dp, Color.Black, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = letter.letter,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = letter.points.toString(),
                                fontSize = 12.sp,
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                        }
                    }
                }
            }
        }
        if (showGameOver.value) {
            viewModel.currentUserId?.let {
                GameOverCard(
                    game = gameState!!,
                    currentUserId = it,
                    navController = navController
                )
            }
        }
    }
}

@Composable
fun GameOverCard(game: Game, currentUserId: String, navController: NavController) {
    val message = when {
        game.winnerId == null -> "Berabere! 🤝"
        game.winnerId == currentUserId -> "Tebrikler, Kazandın! 🎉"
        else -> "Üzgünüm, Kaybettin. 😢"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Oyun Bitti",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    fontSize = 20.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        navController.navigate("home_screen") {
                            popUpTo(0)
                        }
                    }
                ) {
                    Text("Ana Sayfaya Dön")
                }
            }
        }
    }
}

@Composable
fun GameActionsCard(
    onSurrender: () -> Unit,
    onPass: () -> Unit,
    onConfirm: () -> Unit,
    onUndo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp), // Yüksekliği biraz artırdım
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp), // Dikey padding ekledim
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Teslim ol butonu
            ActionButton(
                text = "Teslim Ol",
                icon = Icons.Default.Close,
                color = Color(0xFFEF9A9A),
                onClick = onSurrender
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Pas butonu
            ActionButton(
                text = "Pas",
                icon = Icons.Default.Done,
                color = Color(0xFF90CAF9),
                onClick = onPass
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Onayla butonu
            ActionButton(
                text = "Onayla",
                icon = Icons.Default.Done,
                color = Color(0xFFA5D6A7),
                onClick = onConfirm
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Geri Al butonu
            ActionButton(
                text = "Geri Al",
                icon = Icons.Default.Close,
                color = Color(0xFFFFF59D),
                onClick = onUndo
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier
            .height(50.dp)
            .height(50.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PlayerScoreCard(
    name: String,
    score: Int,
    isActive: Boolean,
    showArrow: Boolean,
    arrowDirection: String,
    onClick: () -> Unit = {}
) {
    val backgroundColor = if (isActive)
        Color(0xFFBBDEFB) // Daha yumuşak mavi
    else
        Color(0xFFE0E0E0) // Daha yumuşak gri

    val contentColor = if (isActive)
        Color(0xFF1565C0) // Koyu mavi
    else
        Color(0xFF424242) // Koyu gri

    Card(
        modifier = Modifier
            .width(130.dp)
            .height(90.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 6.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )

                Text(
                    text = score.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun ClockTimer(remainingSeconds: Int, totalSeconds: Int) {
    val progress = remainingSeconds.toFloat() / totalSeconds.toFloat()
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(80.dp)
    ) {
        // Clock face
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw clock outline
            drawCircle(
                color = Color.LightGray,
                radius = size.minDimension / 2,
                style = Stroke(width = 4f)
            )
            // Draw hour ticks
            for (i in 0 until 12) {
                val angle = (i * 30) * (PI / 180f)
                val startRadius = size.minDimension / 2 - 10f
                val endRadius = size.minDimension / 2 - 5f

                val startX = (size.width / 2 + cos(angle) * startRadius).toFloat()
                val startY = (size.height / 2 + sin(angle) * startRadius).toFloat()
                val endX = (size.width / 2 + cos(angle) * endRadius).toFloat()
                val endY = (size.height / 2 + sin(angle) * endRadius).toFloat()

                drawLine(
                    color = Color.Gray,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2f
                )
            }

            // Draw minute hand
            val minuteAngle = (remainingSeconds / 60f) * 360f * (PI / 180f)
            val minuteHandLength = size.minDimension / 2 - 15f
            val minuteX = (size.width / 2 + cos(minuteAngle - PI / 2) * minuteHandLength).toFloat()
            val minuteY = (size.height / 2 + sin(minuteAngle - PI / 2) * minuteHandLength).toFloat()

            drawLine(
                color = Color.Black,
                start = Offset(size.width / 2, size.height / 2),
                end = Offset(minuteX, minuteY),
                strokeWidth = 3f
            )

            // Draw second hand
            val secondAngle = (remainingSeconds % 60) * 6f * (PI / 180f)
            val secondHandLength = size.minDimension / 2 - 10f
            val secondX = (size.width / 2 + cos(secondAngle - PI / 2) * secondHandLength).toFloat()
            val secondY = (size.height / 2 + sin(secondAngle - PI / 2) * secondHandLength).toFloat()

            drawLine(
                color = Color.Red,
                start = Offset(size.width / 2, size.height / 2),
                end = Offset(secondX, secondY),
                strokeWidth = 2f
            )

            // Draw center dot
            drawCircle(
                color = Color.Black,
                radius = 4f,
                center = Offset(size.width / 2, size.height / 2)
            )
        }

        // Digital time display
        Text(
            text = String.format("%02d:%02d", minutes, seconds),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
fun GameBoard(
    boardState: List<List<GameTile>>,
    placedLetters: Map<Pair<Int, Int>, RackLetter>,
    validPlacementPositions: List<Pair<Int, Int>>,
    onCellClick: (Int, Int) -> Unit,
    onCellPositioned: (Int, Int, Offset, Size) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            for (i in boardState.indices) {
                Row(
                    modifier = Modifier.weight(1f)
                ) {
                    for (j in boardState[i].indices) {
                        val tile = boardState[i][j]
                        val placedLetter = placedLetters[Pair(i, j)]
                        val isValidPlacement = validPlacementPositions.contains(Pair(i, j))

                        BoardCell(
                            tile = tile,
                            placedLetter = placedLetter,
                            isValidPlacement = isValidPlacement,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    val size = Size(
                                        coordinates.size.width.toFloat(),
                                        coordinates.size.height.toFloat()
                                    )
                                    onCellPositioned(i, j, position, size)
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
    tile: GameTile,
    placedLetter: RackLetter?,
    isValidPlacement: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = when (tile.cellType) {
        CellType.NORMAL -> MaterialTheme.colorScheme.surface
        CellType.DOUBLE_LETTER -> Color(0xFFAED6F1)
        CellType.TRIPLE_LETTER -> Color(0xFF5DADE2)
        CellType.DOUBLE_WORD -> Color(0xFFF5CBA7)
        CellType.TRIPLE_WORD -> Color(0xFFE59866)
        CellType.CENTER -> Color(0xFFF9E79F)
    }

    Box(
        modifier = modifier
            .padding(1.dp)
            .background(backgroundColor)
            .border(0.5.dp, Color.Black.copy(alpha = 0.3f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            placedLetter != null -> {
                Box(
                    modifier = Modifier
                        .padding(1.dp)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFFFD700)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = placedLetter.letter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            !tile.letter.isNullOrEmpty() -> {
                Text(
                    text = tile.letter,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                when (tile.cellType) {
                    CellType.DOUBLE_LETTER -> Text("2L", fontSize = 8.sp)
                    CellType.TRIPLE_LETTER -> Text("3L", fontSize = 8.sp)
                    CellType.DOUBLE_WORD -> Text("2W", fontSize = 8.sp)
                    CellType.TRIPLE_WORD -> Text("3W", fontSize = 8.sp)
                    else -> {}
                }
            }
        }

        if (isValidPlacement) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.Green.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
fun LetterRack(
    letters: MutableList<RackLetter>,
    selectedLetter: MutableState<SelectedLetter?>,
    isPlayerTurn: Boolean,  // 🔥 Sıra sende mi kontrolü için eklendi
    onLetterClick: (String, Int, Int) -> Unit,
    onLetterDragStart: (String, Int, Int, Offset, Offset) -> Unit,
    onLetterDrag: (change: Any, Offset) -> Unit,
    onLetterDragEnd: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFBCAAA4)
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                    ) {
                        if (letter.letter.isNotEmpty()) {
                            LetterTile(
                                letter = letter.letter,
                                points = letter.points,
                                isSelected = selectedLetter.value?.rackIndex == i,
                                isPlayerTurn = isPlayerTurn,
                                onClick = {
                                    if (isPlayerTurn) {
                                        onLetterClick(letter.letter, letter.points, i)
                                    }
                                },
                                onDragStart = { offset, position ->
                                    if (isPlayerTurn) {
                                        onLetterDragStart(letter.letter, letter.points, i, offset, position)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (isPlayerTurn) {
                                        onLetterDrag(change, dragAmount)
                                    }
                                },
                                onDragEnd = {
                                    if (isPlayerTurn) {
                                        onLetterDragEnd()
                                    }
                                }
                            )
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x33FFFFFF))
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if(isPlayerTurn){
                Button(
                    onClick = { onShuffle() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD54F)
                    )
                ) {
                    Text(text = "Karıştır 🔀", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LetterTile(
    letter: String,
    points: Int,
    isSelected: Boolean,
    isPlayerTurn: Boolean,
    onClick: () -> Unit,
    onDragStart: (Offset, Offset) -> Unit,
    onDrag: (Any, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.2f
            isSelected -> 1.15f
            else -> 1f
        }
    )

    val backgroundColor = if (isSelected) Color(0xFFFFF176) else Color(0xFFFFF59D)
    val borderColor = if (isSelected) Color.Green else Color.Black
    val borderWidth = if (isSelected) 2.dp else 1.dp

    var position by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(50.dp)
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .clickable(enabled = isPlayerTurn) {  // 🔥 sadece kendi sıranda tıklanabilir
                onClick()
            }
            .onGloballyPositioned { coordinates ->
                position = coordinates.positionInRoot()
            }
            .pointerInput(isPlayerTurn) {
                if (isPlayerTurn) { // 🔥 sadece kendi sıranda sürükleyebilirsin
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            onDragStart(offset, position)
                        },
                        onDragEnd = {
                            isDragging = false
                            onDragEnd()
                        },
                        onDragCancel = {
                            isDragging = false
                            onDragEnd()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(change, dragAmount)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = letter,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = points.toString(),
                fontSize = 12.sp,
                modifier = Modifier.offset(y = (-2).dp)
            )
        }
    }
}

data class Size(val width: Float, val height: Float)


data class RackLetter(
    val letter: String,
    val points: Int
)

data class DraggedLetter(
    val letter: String,
    val points: Int,
    val rackIndex: Int
)

data class SelectedLetter(
    val letter: String,
    val points: Int,
    val rackIndex: Int
)
