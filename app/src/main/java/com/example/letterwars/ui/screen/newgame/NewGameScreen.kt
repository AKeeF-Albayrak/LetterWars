package com.example.letterwars.ui.screen.newgame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.letterwars.ui.screen.common.FloatingLettersBackground
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(
    navController: NavController,
    viewModel: NewGameViewModel = viewModel()
) {
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
                            "Yeni Oyun",
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
        ) {
            FloatingLettersBackground()

            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                ) {
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.95f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GameTypeSection(
                                title = "Hızlı Oyun",
                                description = "Kısa sürede hızlı oyunlar için ideal"
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                GameOptionButton(
                                    title = "2 Dakika",
                                    description = "Hızlı oyun",
                                    color = Color(0xFFFF8A65),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        navController.navigate("queue?duration=2")
                                    }
                                )

                                GameOptionButton(
                                    title = "5 Dakika",
                                    description = "Standart oyun",
                                    color = Color(0xFFBA68C8),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        navController.navigate("queue?duration=5")
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            GameTypeSection(
                                title = "Genişletilmiş Oyun",
                                description = "Uzun süreli stratejik oyunlar için"
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                GameOptionButton(
                                    title = "12 Saat",
                                    description = "Günlük oyun",
                                    color = Color(0xFF9575CD),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        navController.navigate("queue?duration=720")
                                    }
                                )

                                GameOptionButton(
                                    title = "24 Saat",
                                    description = "Uzun oyun",
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        navController.navigate("queue?duration=1440")
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            ElevatedButton(
                                onClick = {
                                    navController.navigate("queue?duration=5")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = Color(0xFFFF8A65),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    "Sıraya Gir",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameTypeSection(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6A1B9A)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF757575)
        )
    }
}

@Composable
fun GameOptionButton(
    title: String,
    description: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier
            .height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = color,
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}