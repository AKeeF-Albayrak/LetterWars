package com.example.letterwars.ui.screen.newgame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.letterwars.ui.screen.common.FloatingLettersBackground

@Composable
fun NewGameScreen(
    navController: NavController,
    viewModel: NewGameViewModel = viewModel()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Floating letters background
        FloatingLettersBackground()

        // New Game content
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .align(Alignment.Center),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Yeni Oyun",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Quick Game Section
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
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // Navigate directly to queue screen with game duration parameter
                            navController.navigate("queue?duration=2")
                        }
                    )

                    GameOptionButton(
                        title = "5 Dakika",
                        description = "Standart oyun",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // Navigate directly to queue screen with game duration parameter
                            navController.navigate("queue?duration=5")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Extended Game Section
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
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // Navigate directly to queue screen with game duration parameter
                            navController.navigate("queue?duration=720") // 12 hours in minutes
                        }
                    )

                    GameOptionButton(
                        title = "24 Saat",
                        description = "Uzun oyun",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // Navigate directly to queue screen with game duration parameter
                            navController.navigate("queue?duration=1440") // 24 hours in minutes
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Back button
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Geri Dön",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun GameTypeSection(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun GameOptionButton(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(100.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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

