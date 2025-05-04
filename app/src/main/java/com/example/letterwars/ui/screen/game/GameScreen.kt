    package com.example.letterwars.ui.screen.game

    import androidx.compose.animation.AnimatedVisibility
    import androidx.compose.animation.core.*
    import androidx.compose.animation.fadeIn
    import androidx.compose.animation.fadeOut
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
    import androidx.compose.material.icons.outlined.*
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
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.input.pointer.pointerInput
    import androidx.compose.ui.layout.onGloballyPositioned
    import androidx.compose.ui.layout.positionInRoot
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.unit.IntOffset
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import androidx.navigation.NavController
    import com.example.letterwars.data.model.*
    import com.example.letterwars.data.util.letterPoints
    import com.google.firebase.auth.FirebaseAuth
    import kotlinx.coroutines.delay
    import kotlin.math.PI
    import kotlin.math.cos
    import kotlin.math.roundToInt
    import kotlin.math.sin

    val LocalSelectedLetterExists = compositionLocalOf { false }

    @Composable
    fun GameScreen(gameId: String?, navController: NavController) {

        val viewModel: GameViewModel = viewModel()
        val gameState by viewModel.game.collectAsState()
        val triggeredEffects by viewModel.triggeredEffects.collectAsState()

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

        val currentDragTargetCell = remember { mutableStateOf<Position?>(null) }


        var showMineEffect by remember { mutableStateOf<MineType?>(null) }
        var showRewardEffect by remember { mutableStateOf<RewardType?>(null) }
        var currentEffectIndex by remember { mutableStateOf(0) }

        LaunchedEffect(triggeredEffects) {
            if (triggeredEffects.isNotEmpty()) {
                currentEffectIndex = 0
                val effect = triggeredEffects.firstOrNull()
                if (effect != null) {
                    showMineEffect = effect.mineType
                    showRewardEffect = effect.rewardType
                }
            } else {
                showMineEffect = null
                showRewardEffect = null
            }
        }

        val selectedLetter = remember { mutableStateOf<SelectedLetter?>(null) }

        val isPlayerTurn by remember(gameState?.currentTurnPlayerId) {
            derivedStateOf {
                gameState?.currentTurnPlayerId == viewModel.currentUserId
            }
        }

        val remainingTimeSeconds = remember(gameState?.expireTimeMillis) {
            mutableStateOf(
                ((gameState?.expireTimeMillis ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0) / 1000L
            )
        }

        LaunchedEffect(gameState?.startTimeMillis, gameState?.expireTimeMillis) {
            gameState?.let { game ->
                while (true) {
                    val now = System.currentTimeMillis()
                    val newRemaining = (game.expireTimeMillis - now).coerceAtLeast(0) / 1000L
                    remainingTimeSeconds.value = newRemaining

                    if (newRemaining <= 0L) {
                        val loserId = game.currentTurnPlayerId
                        loserPlayerId.value = loserId
                        viewModel.SurrenderGame(game, loserId)
                        showGameOver.value = true
                        break
                    }

                    delay(1000L)
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

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val currentLetters = remember(gameState) {
            when (gameState?.currentTurnPlayerId) {
                gameState?.player1Id -> gameState?.currentLetters1
                gameState?.player2Id -> gameState?.currentLetters2
                else -> emptyList()
            }
        }

        val rackLetters = remember { mutableStateListOf<RackLetter>() }

        LaunchedEffect(currentLetters) {
            rackLetters.clear()
            currentLetters?.forEach { letter ->
                val point = letterPoints[letter.uppercase().first()] ?: 0
                rackLetters.add(RackLetter(letter, point))
            }
        }

        val placedLetters = remember { mutableStateMapOf<Position, RackLetter>() }
        val cellPositions = remember { mutableStateMapOf<Position, Pair<Offset, Size>>() }

        val placeLetter = { letter: RackLetter, rackIndex: Int, row: Int, col: Int ->
            placedLetters[Position(row, col)] = letter

            if (rackIndex != -1) {
                rackLetters[rackIndex] = RackLetter(letter = "", points = 0)
            }

            viewModel.addPendingMove(row, col, letter.letter)

            selectedLetter.value = null
            draggedLetter = null

            viewModel.updateValidPositions()
        }

        val validPlacementPositions by viewModel.validPositions.collectAsState()

        val findCellAtPosition = { position: Offset ->
            var foundCell: Position? = null

            cellPositions.forEach { (cellCoord, cellBounds) ->
                val (cellPos, cellSize) = cellBounds
                if (position.x >= cellPos.x &&
                    position.x <= cellPos.x + cellSize.width &&
                    position.y >= cellPos.y &&
                    position.y <= cellPos.y + cellSize.height) {

                    if (!placedLetters.containsKey(cellCoord)) {
                        foundCell = cellCoord
                    }
                }
            }

            foundCell
        }

        val frozenIndices = remember(gameState) {
            val currentPlayerId = viewModel.currentUserId
            gameState?.frozenLettersEffects
                ?.firstOrNull { it.playerId == currentPlayerId }
                ?.letterIndices ?: emptyList()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2C003E))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
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
                        val isPlayer1 = gameState?.player1Id == viewModel.currentUserId

                        val playerScore = if (isPlayer1) gameState?.player1Score ?: 0 else gameState?.player2Score ?: 0
                        val opponentScore = if (isPlayer1) gameState?.player2Score ?: 0 else gameState?.player1Score ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerScoreCard(
                                name = viewModel.currentUsername.collectAsState().value,
                                score = playerScore,
                                isActive = isPlayerTurn,
                                showArrow = false,
                                arrowDirection = ""
                            )

                            PlayerScoreCard(
                                name = viewModel.opponentUsername.collectAsState().value,
                                score = opponentScore,
                                isActive = !isPlayerTurn,
                                showArrow = false,
                                arrowDirection = "",
                                onClick = {
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
                                    remainingSeconds = remainingTimeSeconds.value.toInt(),
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

                        CompositionLocalProvider(
                            LocalSelectedLetterExists provides (selectedLetter.value != null)
                        ) {
                            GameBoardWithEffects(
                                boardState = boardState,
                                placedLetters = placedLetters,
                                validPlacementPositions = validPlacementPositions,
                                currentDragTargetCell = currentDragTargetCell.value,
                                onCellClick = { row, col ->
                                    selectedLetter.value?.let { selected ->
                                        if (validPlacementPositions.contains(Position(row, col))) {
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
                                    cellPositions[Position(row, col)] = Pair(offset, size)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if(isPlayerTurn){
                            GameActionsCard(
                                onSurrender = {
                                    if (gameState != null && viewModel.currentUserId != null) {
                                        viewModel.SurrenderGame(gameState!!, viewModel.currentUserId!!)
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
                                    viewModel.clearPendingMoves(placedLetters)
                                    placedLetters.clear()
                                    viewModel.updateValidPositions()
                                }
                            )
                        }
                        else
                        {
                            RewardActionsCard(
                                areaBlockCount = 2,
                                letterFreezeCount = 3,
                                extraTurnCount = 1,
                                onAreaBlock = { /* ViewModel.activateAreaBlock("left" | "right") */ },
                                onLetterFreeze = {
                                    val opponentLetters = if (viewModel.currentUserId == gameState?.player1Id) {
                                        gameState?.currentLetters2 ?: emptyList()
                                    } else {
                                        gameState?.currentLetters1 ?: emptyList()
                                    }

                                    val indices = opponentLetters.indices.shuffled().take(2)
                                    viewModel.activateLetterFreeze()
                                },
                                onExtraTurn = { viewModel.activateExtraTurn() }
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        LetterRack(
                            letters = rackLetters,
                            selectedLetter = selectedLetter,
                            isPlayerTurn = isPlayerTurn,
                            frozenIndices = frozenIndices,
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
                                val currentPosition = dragStartPosition + dragOffset
                                val targetCell = findCellAtPosition(currentPosition)
                                currentDragTargetCell.value = targetCell
                            },
                            onLetterDragEnd = {
                                val currentPosition = dragStartPosition + dragOffset
                                val targetCell = findCellAtPosition(currentPosition)

                                if (
                                    targetCell != null &&
                                    !(targetCell.row == 7 && targetCell.col == 7) &&
                                    validPlacementPositions.contains(targetCell)
                                ) {
                                    draggedLetter?.let { letter ->
                                        placeLetter(
                                            RackLetter(letter.letter, letter.points),
                                            letter.rackIndex,
                                            targetCell.row,
                                            targetCell.col
                                        )
                                    }
                                }

                                draggedLetter = null
                                dragOffset = Offset.Zero
                                currentDragTargetCell.value = null
                            },
                            onShuffle = {
                                rackLetters.shuffle()
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }

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

                    SpecialEffectPopupManager(
                        showMineEffect = showMineEffect,
                        showRewardEffect = showRewardEffect,
                        onDismiss = {
                            showMineEffect = null
                            showRewardEffect = null

                            currentEffectIndex++
                            if (currentEffectIndex < triggeredEffects.size) {
                                val nextEffect = triggeredEffects[currentEffectIndex]
                                showMineEffect = nextEffect.mineType
                                showRewardEffect = nextEffect.rewardType
                            } else {
                                viewModel.clearTriggeredEffects()
                            }
                        }
                    )
                }
            }

            if (showGameOver.value || gameState?.status == GameStatus.FINISHED) {
                GameOverCard(
                    game = gameState!!,
                    currentUserId = viewModel.currentUserId!!,
                    navController = navController
                )
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
                .background(Color(0xFF2C003E).copy(alpha = 0.9f)),
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
                            navController.navigate("home") {
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
        onUndo: () -> Unit,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF450149)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    text = "Teslim Ol",
                    icon = Icons.Default.Close,
                    color = Color(0xFFEF9A9A),
                    onClick = onSurrender
                )

                Spacer(modifier = Modifier.width(6.dp))

                ActionButton(
                    text = "Pas",
                    icon = Icons.Default.Done,
                    color = Color(0xFF90CAF9),
                    onClick = onPass
                )

                Spacer(modifier = Modifier.width(6.dp))

                ActionButton(
                    text = "Onayla",
                    icon = Icons.Default.Done,
                    color = Color(0xFFA5D6A7),
                    onClick = onConfirm
                )

                Spacer(modifier = Modifier.width(6.dp))

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
        icon: ImageVector,
        color: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = modifier
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
            Color(0xFFBBDEFB)
        else
            Color(0xFFE0E0E0)

        val contentColor = if (isActive)
            Color(0xFF1565C0)
        else
            Color(0xFF424242)

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
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val hours = minutes / 60

        val displayTime = if (totalSeconds >= 3600) {
            String.format("%02d:%02d", hours, minutes % 60)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }

        val progress = remainingSeconds.toFloat() / totalSeconds.toFloat()

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.LightGray,
                    radius = size.minDimension / 2,
                    style = Stroke(width = 4f)
                )

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

                val minuteAngle = (1f - progress) * 360f * (PI / 180f)
                val handLength = size.minDimension / 2 - 15f

                val handX = (size.width / 2 + cos(minuteAngle - PI / 2) * handLength).toFloat()
                val handY = (size.height / 2 + sin(minuteAngle - PI / 2) * handLength).toFloat()

                drawLine(
                    color = Color.Black,
                    start = Offset(size.width / 2, size.height / 2),
                    end = Offset(handX, handY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = Color.Black,
                    radius = 4f,
                    center = Offset(size.width / 2, size.height / 2)
                )
            }

            Text(
                text = displayTime,
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
    fun MineTypeIcon(type: MineType, modifier: Modifier = Modifier) {
        val (icon, color, contentDescription) = when (type) {
            MineType.POINT_DIVISION -> Triple(
                Icons.Filled.KeyboardArrowDown,
                Color(0xFFE57373),
                "Puan Bölünmesi"
            )
            MineType.POINT_TRANSFER -> Triple(
                Icons.Filled.Person,
                Color(0xFFFFB74D),
                "Puan Transferi"
            )
            MineType.LETTER_RESET -> Triple(
                Icons.Filled.Refresh,
                Color(0xFFFFF176),
                "Harf Sıfırlama"
            )
            MineType.BONUS_CANCEL -> Triple(
                Icons.Filled.Clear,
                Color(0xFFBA68C8),
                "Bonus İptali"
            )
            MineType.WORD_CANCEL -> Triple(
                Icons.Filled.Close,
                Color(0xFFEF5350),
                "Kelime İptali"
            )
        }

        MineRewardIconBase(
            icon = icon,
            color = color,
            contentDescription = contentDescription,
            modifier = modifier
        )
    }


    @Composable
    fun RewardTypeIcon(type: RewardType, modifier: Modifier = Modifier) {
        val (icon, color, contentDescription) = when (type) {
            RewardType.AREA_BLOCK -> Triple(
                Icons.Filled.Star,
                Color(0xFF4FC3F7),
                "Alan Bloklama"
            )
            RewardType.LETTER_FREEZE -> Triple(
                Icons.Filled.Face,
                Color(0xFF81C784),
                "Harf Dondurma"
            )
            RewardType.EXTRA_TURN -> Triple(
                Icons.Filled.Refresh,
                Color(0xFFFFD54F),
                "Ekstra Tur"
            )
        }

        MineRewardIconBase(
            icon = icon,
            color = color,
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
    @Composable
    private fun MineRewardIconBase(
        icon: ImageVector,
        color: Color,
        contentDescription: String,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = color,
                modifier = Modifier.size(10.dp)
            )
        }
    }
    @Composable
    fun MineRewardPopup(
        title: String,
        description: String,
        icon: ImageVector,
        color: Color,
        onDismiss: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(0.8f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = description,
                        fontSize = 16.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = color
                        ),
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        Text("Tamam")
                    }
                }
            }
        }
    }

    fun getMineTypeInfo(type: MineType): Triple<ImageVector, Color, String> {
        return when (type) {
            MineType.POINT_DIVISION -> Triple(
                Icons.Filled.KeyboardArrowDown,
                Color(0xFFE57373),
                "Bu hamleden kazandığınız puanlar yarıya düşürüldü!"
            )
            MineType.POINT_TRANSFER -> Triple(
                Icons.Filled.Person,
                Color(0xFFFFB74D),
                "Bu hamleden kazandığınız puanlar rakibinize transfer edildi!"
            )
            MineType.LETTER_RESET -> Triple(
                Icons.Filled.Refresh,
                Color(0xFFFFF176),
                "Elinizdeki harfler yenileriyle değiştirildi!"
            )
            MineType.BONUS_CANCEL -> Triple(
                Icons.Filled.Clear,
                Color(0xFFBA68C8),
                "Bu hamledeki tüm bonus kareler etkisiz hale getirildi!"
            )
            MineType.WORD_CANCEL -> Triple(
                Icons.Filled.Close,
                Color(0xFFEF5350),
                "Bu hamledeki kelime iptal edildi ve sıra rakibinize geçti!"
            )
        }
    }

    fun getRewardTypeInfo(type: RewardType): Triple<ImageVector, Color, String> {
        return when (type) {
            RewardType.AREA_BLOCK -> Triple(
                Icons.Filled.Star,
                Color(0xFF4FC3F7),
                "Tahtada 3x3'lük bir alanı rakibinize kapatabilirsiniz!"
            )
            RewardType.LETTER_FREEZE -> Triple(
                Icons.Filled.Face,
                Color(0xFF81C784),
                "Rakibinizin elindeki bir harfi bir tur boyunca dondurabilirsiniz!"
            )
            RewardType.EXTRA_TURN -> Triple(
                Icons.Filled.Refresh,
                Color(0xFFFFD54F),
                "Ekstra bir tur kazandınız! Tekrar hamle yapabilirsiniz."
            )
        }
    }



    @Composable
    fun SpecialEffectPopupManager(
        showMineEffect: MineType?,
        showRewardEffect: RewardType?,
        onDismiss: () -> Unit
    ) {
        var showPopup by remember { mutableStateOf(false) }
        var popupTitle by remember { mutableStateOf("") }
        var popupDescription by remember { mutableStateOf("") }
        var popupIcon by remember { mutableStateOf<ImageVector?>(null) }
        var popupColor by remember { mutableStateOf(Color.Gray) }

        val mineInfo = showMineEffect?.let { getMineTypeInfo(it) }
        val rewardInfo = showRewardEffect?.let { getRewardTypeInfo(it) }

        LaunchedEffect(showMineEffect, showRewardEffect) {
            when {
                showMineEffect != null && mineInfo != null -> {
                    val (icon, color, description) = mineInfo
                    popupTitle = "Mayın Etkisi!"
                    popupDescription = description
                    popupIcon = icon
                    popupColor = color
                    showPopup = true
                }
                showRewardEffect != null && rewardInfo != null -> {
                    val (icon, color, description) = rewardInfo
                    popupTitle = "Ödül Kazandınız!"
                    popupDescription = description
                    popupIcon = icon
                    popupColor = color
                    showPopup = true
                }
                else -> {
                    showPopup = false
                }
            }
        }

        AnimatedVisibility(
            visible = showPopup,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            if (popupIcon != null) {
                MineRewardPopup(
                    title = popupTitle,
                    description = popupDescription,
                    icon = popupIcon!!,
                    color = popupColor,
                    onDismiss = onDismiss
                )
            }
        }
    }

    @Composable
    fun GameBoardWithEffects(
        boardState: List<List<GameTile>>,
        placedLetters: Map<Position, RackLetter>,
        validPlacementPositions: List<Position>,
        currentDragTargetCell: Position?,
        onCellClick: (Int, Int) -> Unit,
        onCellPositioned: (Int, Int, Offset, Size) -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE1BEE7)
            )
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
                            val placedLetter = placedLetters[Position(i, j)]
                            val isValidPlacement = validPlacementPositions.contains(Position(i, j))

                            BoardCellWithSpecialEffects(
                                tile = tile,
                                placedLetter = placedLetter,
                                isValidPlacement = isValidPlacement,
                                row = i,
                                col = j,
                                currentDragTargetCell = currentDragTargetCell,
                                validPlacementPositions = validPlacementPositions,
                                placedLetters = placedLetters,
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
    fun BoardCellWithSpecialEffects(
        tile: GameTile,
        placedLetter: RackLetter?,
        isValidPlacement: Boolean,
        row: Int,
        col: Int,
        currentDragTargetCell: Position?,
        validPlacementPositions: List<Position>,
        placedLetters: Map<Position, RackLetter>,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val backgroundColor = when (tile.cellType) {
            CellType.NORMAL        -> Color(0xFFF3E5F5)
            CellType.DOUBLE_LETTER -> Color(0xFFAED6F1)
            CellType.TRIPLE_LETTER -> Color(0xFF5DADE2)
            CellType.DOUBLE_WORD   -> Color(0xFFF5CBA7)
            CellType.TRIPLE_WORD   -> Color(0xFFE59866)
            CellType.CENTER        -> Color(0xFFF9E79F)
        }

        val hasLetter = placedLetter != null || !tile.letter.isNullOrEmpty()

        Box(
            modifier = modifier
                .padding(1.dp)
                .background(backgroundColor)
                .border(0.5.dp, Color.Black.copy(alpha = 0.3f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (!hasLetter && (tile.mineType != null || tile.rewardType != null)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        tile.mineType != null   ->
                            MineTypeIcon(tile.mineType, modifier = Modifier.size(16.dp))
                        tile.rewardType != null ->
                            RewardTypeIcon(tile.rewardType, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (hasLetter) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val letterChar = placedLetter?.letter ?: tile.letter!!
                    Text(
                        text = letterChar.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                when (tile.cellType) {
                    CellType.DOUBLE_LETTER -> Text("2L", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    CellType.TRIPLE_LETTER -> Text("3L", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    CellType.DOUBLE_WORD   -> Text("2W", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    CellType.TRIPLE_WORD   -> Text("3W", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    else                   -> {}
                }
            }

            val isLetterSelected = LocalSelectedLetterExists.current ||
                    currentDragTargetCell == Position(row, col)
            val isCenterCell = row == 7 && col == 7
            if ((isLetterSelected) &&
                !hasLetter && !isCenterCell
            ) {
                val isValidTarget = validPlacementPositions.contains(Position(row, col))
                val transition = rememberInfiniteTransition()
                val scale by transition.animateFloat(
                    initialValue = 0.8f,
                    targetValue  = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(800),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                val alpha by transition.animateFloat(
                    initialValue = 0.5f,
                    targetValue  = 0.9f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(800),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                Box(
                    Modifier
                        .size(if (isValidTarget) 10.dp * scale else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isValidTarget) Color.Green.copy(alpha = alpha)
                            else Color.Red.copy(alpha = 0.7f)
                        )
                )
            }
        }
    }


    @Composable
    fun LetterRack(
        letters: MutableList<RackLetter>,
        selectedLetter: MutableState<SelectedLetter?>,
        isPlayerTurn: Boolean,
        frozenIndices: List<Int>,
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
                    containerColor = Color(0xFF5E35B1)
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
                                    isPlayerTurn = isPlayerTurn && !frozenIndices.contains(i),
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
                                LetterTile(
                                    letter = letter.letter,
                                    points = letter.points,
                                    isSelected = selectedLetter.value?.rackIndex == i,
                                    isPlayerTurn = isPlayerTurn && !frozenIndices.contains(i),
                                    onClick = {
                                    },
                                    onDragStart = { offset, position ->
                                    },
                                    onDrag = { change, dragAmount ->
                                    },
                                    onDragEnd = {
                                    }
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

        val backgroundColor = if (isSelected) Color(0xFFFFD700) else Color(0xFFFFC107)
        val borderColor = if (isSelected) Color(0xFF4CAF50) else Color(0xFF673AB7)
        val borderWidth = if (isSelected) 2.dp else 1.dp


        val textColor = Color(0xFF212121)

        var position by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .size(50.dp)
                .scale(scale)
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
                .clickable(enabled = isPlayerTurn) { onClick() }
                .onGloballyPositioned { coordinates ->
                    position = coordinates.positionInRoot()
                }
                .pointerInput(isPlayerTurn) {
                    if (isPlayerTurn) {
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
            Text(
                text = letter.uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )

            Text(
                text = points.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }

    @Composable
    fun RewardActionsCard(
        areaBlockCount: Int,
        letterFreezeCount: Int,
        extraTurnCount: Int,
        onAreaBlock: () -> Unit,
        onLetterFreeze: () -> Unit,
        onExtraTurn: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF450149)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RewardButton("Harf Yasağı", Icons.Default.Lock, letterFreezeCount, onLetterFreeze)
                RewardButton("Bölge Yasağı", Icons.Default.Lock, areaBlockCount, onAreaBlock)
            }
        }
    }

    @Composable
    fun RewardButton(
        label: String,
        icon: ImageVector,
        count: Int,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF59D)),
            modifier = Modifier.height(50.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(18.dp))
                Text(text = label, fontSize = 10.sp, maxLines = 1)
                Text(text = "x$count", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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