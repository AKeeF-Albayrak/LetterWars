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
    data class Success(val gameInfoList: List<Pair<GameResult, Long>>) : FinishedGamesUiState()
    data class Error(val message: String) : FinishedGamesUiState()
    object Empty : FinishedGamesUiState()
}

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
            // FINISHED durumundaki ve kullanıcının taraf olduğu tüm maçlar
            val p1 = db.collection("games")
                .whereEqualTo("player1Id", currentUserId)
                .whereEqualTo("status", "FINISHED")
                .get().await()

            val p2 = db.collection("games")
                .whereEqualTo("player2Id", currentUserId)
                .whereEqualTo("status", "FINISHED")
                .get().await()

            val gameInfoList = (p1.documents + p2.documents).map { doc ->
                val winnerId  = doc.getString("winnerId")
                val timestamp = doc.getLong("createdAt") ?: System.currentTimeMillis()

                val result = when {
                    winnerId == currentUserId           -> GameResult.WIN
                    winnerId.isNullOrBlank()            -> GameResult.DRAW   // boş bırakıldıysa berabere
                    winnerId.equals("DRAW", true)       -> GameResult.DRAW   // “DRAW” sabiti kullanıldıysa
                    else                                -> GameResult.LOSS
                }
                result to timestamp
            }

            if (gameInfoList.isEmpty()) {
                _uiState.value = FinishedGamesUiState.Empty
            } else {
                _uiState.value = FinishedGamesUiState.Success(
                    gameInfoList.sortedByDescending { it.second }   // en yeni üstte
                )
            }

        } catch (e: Exception) {
            _uiState.value = FinishedGamesUiState.Error(e.message ?: "Bilinmeyen bir hata oluştu")
        }
    }
}