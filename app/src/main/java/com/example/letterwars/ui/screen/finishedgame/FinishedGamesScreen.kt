package com.example.letterwars.ui.screen.finishedgames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.letterwars.data.model.GameResult
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedGamesScreen(
    navController: NavController,
    viewModel: FinishedGamesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Biten Oyunlar",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF3E3E3E)
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFBB86FC).copy(alpha = 0.2f))
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Geri",
                                tint = Color(0xFF6A1B9A)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                Divider(color = Color.LightGray, thickness = 1.dp)
            }
        },
        containerColor = Color(0xFFFFF8E1)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
            ) {
                when (val state = uiState) {
                    is FinishedGamesUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6A1B9A))
                        }
                    }
                    is FinishedGamesUiState.Success -> {
                        FinishedGamesList(
                            gameInfoList = state.gameInfoList
                        )
                    }
                    is FinishedGamesUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Hata: ${state.message}",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color(0xFF3E3E3E)
                                    )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                ElevatedButton(
                                    onClick = { viewModel.loadFinishedGames() },
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = Color(0xFFBA68C8),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Tekrar Dene",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is FinishedGamesUiState.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Henüz tamamlanmış oyun bulunmamaktadır.",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFF3E3E3E),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinishedGamesList(
    gameInfoList: List<GameInfo>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(gameInfoList) { gameInfo ->
            FinishedGameItem(gameInfo = gameInfo)
        }
    }
}

@Composable
fun FinishedGameItem(gameInfo: GameInfo) {
    val (containerColor, resultColor, resultText) = when (gameInfo.result) {
        GameResult.WIN -> Triple(Color(0xFFEAF7E8), Color(0xFF4CAF50), "Kazandın")
        GameResult.LOSS -> Triple(Color(0xFFFAEAEA), Color(0xFFF44336), "Kaybettin")
        GameResult.DRAW -> Triple(Color(0xFFFFF3E0), Color(0xFFFF9800), "Berabere")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Oyun Tarihi",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF757575)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatDate(gameInfo.timestamp),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3E3E3E)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .background(resultColor, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = resultText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.LightGray, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                PlayerScoreInfo(
                    label = "Sen",
                    username = gameInfo.playerUsername,
                    score = gameInfo.playerScore,
                    color = Color(0xFF4CAF50)
                )

                Text(
                    text = "VS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                PlayerScoreInfo(
                    label = "Rakip",
                    username = gameInfo.opponentUsername,
                    score = gameInfo.opponentScore,
                    color = Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun PlayerScoreInfo(
    label: String,
    username: String,
    score: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF757575)
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = username,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E3E3E)
            ),
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.2f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = score.toString(),
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 18.sp
            )
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("tr"))
    return sdf.format(Date(timestamp))
}