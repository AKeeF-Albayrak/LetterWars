package com.example.letterwars.ui.screen.finishedgames

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.GameResult
import com.example.letterwars.data.model.GameStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class FinishedGamesUiState {
    object Loading : FinishedGamesUiState()
    data class Success(val gameInfoList: List<GameInfo>) : FinishedGamesUiState()
    data class Error(val message: String) : FinishedGamesUiState()
    object Empty : FinishedGamesUiState()
}

data class GameInfo(
    val result: GameResult,
    val timestamp: Long,
    val playerUsername: String,
    val opponentUsername: String,
    val playerScore: Int,
    val opponentScore: Int
)

class FinishedGamesViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<FinishedGamesUiState>(FinishedGamesUiState.Loading)
    val uiState: StateFlow<FinishedGamesUiState> = _uiState.asStateFlow()

    init {
        loadFinishedGames()
    }

    fun loadFinishedGames() = viewModelScope.launch {
        _uiState.value = FinishedGamesUiState.Loading

        val currentUserId = auth.currentUser?.uid
            ?: return@launch _uiState.run { value = FinishedGamesUiState.Error("Kullanıcı oturumu bulunamadı") }

        try {

            val p1 = db.collection("games")
                .whereEqualTo("player1Id", currentUserId)
                .whereEqualTo("status", "FINISHED")
                .get().await()

            val p2 = db.collection("games")
                .whereEqualTo("player2Id", currentUserId)
                .whereEqualTo("status", "FINISHED")
                .get().await()

            val gameInfoListFuture = (p1.documents + p2.documents).map { doc ->
                val winnerId = doc.getString("winnerId")
                val timestamp = doc.getLong("createdAt") ?: System.currentTimeMillis()

                val player1Id = doc.getString("player1Id") ?: ""
                val player2Id = doc.getString("player2Id") ?: ""
                val player1Score = doc.getLong("player1Score")?.toInt() ?: 0
                val player2Score = doc.getLong("player2Score")?.toInt() ?: 0

                val isPlayer1 = currentUserId == player1Id

                val playerId = if (isPlayer1) player1Id else player2Id
                val opponentId = if (isPlayer1) player2Id else player1Id

                val playerScore = if (isPlayer1) player1Score else player2Score
                val opponentScore = if (isPlayer1) player2Score else player1Score

                val result = when {
                    winnerId == currentUserId -> GameResult.WIN
                    winnerId.isNullOrBlank() -> GameResult.DRAW
                    winnerId.equals("DRAW", true) -> GameResult.DRAW
                    else -> GameResult.LOSS
                }

                val playerUserFuture = db.collection("users").document(playerId).get().await()
                val opponentUserFuture = db.collection("users").document(opponentId).get().await()

                val playerUsername = playerUserFuture.getString("username")
                    ?: playerUserFuture.getString("email")
                    ?: "Oyuncu"

                val opponentUsername = opponentUserFuture.getString("username")
                    ?: opponentUserFuture.getString("email")
                    ?: "Rakip"

                GameInfo(
                    result = result,
                    timestamp = timestamp,
                    playerUsername = playerUsername,
                    opponentUsername = opponentUsername,
                    playerScore = playerScore,
                    opponentScore = opponentScore
                )
            }

            if (gameInfoListFuture.isEmpty()) {
                _uiState.value = FinishedGamesUiState.Empty
            } else {
                _uiState.value = FinishedGamesUiState.Success(
                    gameInfoListFuture.sortedByDescending { it.timestamp }
                )
            }

        } catch (e: Exception) {
            _uiState.value = FinishedGamesUiState.Error(e.message ?: "Bilinmeyen bir hata oluştu")
        }
    }
}