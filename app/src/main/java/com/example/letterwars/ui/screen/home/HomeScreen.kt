package com.example.letterwars.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.letterwars.data.model.User

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Letter Wars")
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Default.Person, contentDescription = "Profil")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            user?.let {
                UserInfoSection(it)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onStartNewGame, modifier = Modifier.fillMaxWidth()) {
                Text("Yeni Oyun")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onViewActiveGames, modifier = Modifier.fillMaxWidth()) {
                Text("Aktif Oyunlar")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onViewFinishedGames, modifier = Modifier.fillMaxWidth()) {
                Text("Biten Oyunlar")
            }
        }
    }
}

@Composable
fun UserInfoSection(user: User) {
    val winRate = user.getWinRate()
    Text(text = "Hoş geldin, ${user.username}")
    Text(text = "Başarı Oranı: %$winRate")
}