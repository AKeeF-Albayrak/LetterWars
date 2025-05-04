package com.example.letterwars.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.letterwars.data.model.User
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onStartNewGame: () -> Unit,
    onViewActiveGames: () -> Unit,
    onViewFinishedGames: () -> Unit,
    onProfileClick: () -> Unit
) {
    val user by viewModel.user.collectAsState()
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
                            "Letter Wars",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF3E3E3E)
                            )
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = onProfileClick,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFBB86FC).copy(alpha = 0.2f))
                        ) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = "Profil",
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
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    user?.let {
                        UserInfoCard(it)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    GameActionButtons(
                        onStartNewGame = onStartNewGame,
                        onViewActiveGames = onViewActiveGames,
                        onViewFinishedGames = onViewFinishedGames
                    )
                }
            }
        }
    }
}

@Composable
fun UserInfoCard(user: User) {
    val winRate = user.getWinRate()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFD54F)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF6A1B9A)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Hoş geldin, ${user.username}",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Başarı Oranı",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF757575)
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "%$winRate",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF3E3E3E)
                )
            )
        }
    }
}


@Composable
fun GameActionButtons(
    onStartNewGame: () -> Unit,
    onViewActiveGames: () -> Unit,
    onViewFinishedGames: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ElevatedButton(
            onClick = onStartNewGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color(0xFFFF8A65),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Yeni Oyun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedButton(
            onClick = onViewActiveGames,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
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
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Aktif Oyunlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedButton(
            onClick = onViewFinishedGames,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color(0xFF9575CD),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Biten Oyunlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}