package com.example.letterwars.ui.screen.activegames

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.letterwars.data.model.ActiveGameInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveGamesScreen(
    navController: NavController,
    viewModel: ActiveGamesViewModel = viewModel()
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
                            "Aktif Oyunlar",
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
                    is ActiveGamesUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6A1B9A))
                        }
                    }
                    is ActiveGamesUiState.Success -> {
                        ActiveGamesList(
                            games = state.gameInfoList,
                            onContinue = { gameId ->
                                navController.navigate("game/$gameId")
                            }
                        )
                    }
                    is ActiveGamesUiState.Error -> {
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
                                    onClick = { viewModel.loadActiveGames() },
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
                    is ActiveGamesUiState.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Aktif oyununuz bulunmuyor.",
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
private fun ActiveGamesList(
    games: List<ActiveGameInfo>,
    onContinue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(games) { game ->
            ActiveGameItem(game, onContinue)
        }
    }
}

@Composable
private fun ActiveGameItem(
    info: ActiveGameInfo,
    onContinue: (String) -> Unit
) {
    val yourTurn       = info.isYourTurn
    val backgroundTint = if (yourTurn) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            /* ---------- üst satır ---------- */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Başlangıç Tarihi",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF757575)
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatDate(info.startedAt),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3E3E3E)
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val turnText  = if (yourTurn) "Sıra Sende" else "Rakipte"
                    val turnColor = if (yourTurn) Color(0xFF4CAF50) else Color(0xFF9575CD)

                    Box(
                        modifier = Modifier
                            .background(
                                if (yourTurn) Color(0xFFE8F5E9) else Color(0xFFF3E5F5),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = turnText,
                            color = turnColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = info.remainingTimeLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            ElevatedButton(
                onClick  = { onContinue(info.gameId) },
                enabled  = yourTurn,
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (yourTurn) Color(0xFF4CAF50) else Color.LightGray,
                    contentColor   = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Devam Et",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("tr"))
    return sdf.format(Date(timestamp))
}
