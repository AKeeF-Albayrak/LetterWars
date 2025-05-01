package com.example.letterwars.ui.screen.queue

import QueueViewModel
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.util.concurrent.TimeUnit

@Composable
fun QueueScreen(
    navController: NavController,
    viewModel: QueueViewModel = viewModel(),
    onMatchFound: (String) -> Unit
) {
    var elapsedTime by remember { mutableStateOf(0L) }
    var isInQueue by remember { mutableStateOf(true) }
    var showMatchFoundCard by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    val gameDuration = viewModel.gameDuration
    val gameDurationText = when {
        gameDuration.minutes < 60 -> "${gameDuration.minutes} dakika"
        else -> "${gameDuration.minutes / 60} saat"
    }

    val formattedTime = remember(elapsedTime) {
        String.format(
            "%02d:%02d",
            TimeUnit.SECONDS.toMinutes(elapsedTime) % 60,
            elapsedTime % 60
        )
    }

    LaunchedEffect(isInQueue) {
        while (isInQueue) {
            delay(1000L)
            elapsedTime++
        }
    }

    // Oyun ID'sini dinliyoruz
    val gameId by viewModel.gameId.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    // gameId veya isSearching değiştiğinde bildirimi göster
    LaunchedEffect(gameId, isSearching) {
        Log.d("QueueScreen", "🔵 LaunchedEffect: gameId=$gameId, isSearching=$isSearching")

        if (gameId != null && !isSearching) {
            Log.d("QueueScreen", "🟢 Maç bulundu! gameId=$gameId")
            isInQueue = false
            showMatchFoundCard = true
            delay(2000L)
            onMatchFound(gameId!!)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulsating")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E1)) // Beyaza yakın sıcak arka plan
    ) {
        FloatingLettersBackground()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
            ) {
                Card(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
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
                        Text(
                            text = "Oyun Aranıyor",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6A1B9A) // Ana mor renk
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE1BEE7) // Açık mor arka plan
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Seçilen Oyun Süresi",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF757575)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = gameDurationText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6A1B9A)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Animasyonlu daireler
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFBB86FC).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp * scale)
                                    .clip(CircleShape)
                                    .background(Color(0xFFBB86FC).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp * scale)
                                        .clip(CircleShape)
                                        .background(Color(0xFFBB86FC).copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF6A1B9A))
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Bekleme Süresi",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF757575)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6A1B9A)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Tahmini Bekleme: 01:30",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF757575)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF3E5F5) // Çok açık mor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Sıradaki Oyuncular",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF757575)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val queueUserCount by viewModel.queueUserCount.collectAsState()

                                Text(
                                    text = queueUserCount.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6A1B9A)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        ElevatedButton(
                            onClick = {
                                isInQueue = false
                                viewModel.leaveQueue()
                                navController.popBackStack()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFFF44336), // Kırmızı
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                "Sıradan Çık",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (showMatchFoundCard) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E9) // Açık yeşil arka plan
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎉 Karşılaşma Bulundu!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50) // Yeşil metin
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Hazırlanıyor...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF388E3C) // Daha koyu yeşil metin
                    )
                }
            }
        }
    }
}