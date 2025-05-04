package com.example.letterwars.ui.screen.activegames

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.ActiveGameInfo
import com.example.letterwars.data.model.GameStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed class ActiveGamesUiState {
    object Loading : ActiveGamesUiState()
    data class Success(val gameInfoList: List<ActiveGameInfo>) : ActiveGamesUiState()
    data class Error(val message: String) : ActiveGamesUiState()
    object Empty : ActiveGamesUiState()
}

class ActiveGamesViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<ActiveGamesUiState>(ActiveGamesUiState.Loading)
    val uiState: StateFlow<ActiveGamesUiState> = _uiState.asStateFlow()

    init { loadActiveGames() }

    fun loadActiveGames() = viewModelScope.launch {
        _uiState.value = ActiveGamesUiState.Loading

        val uid = auth.currentUser?.uid
        if (uid == null) {
            _uiState.value = ActiveGamesUiState.Error("Kullanıcı oturumu bulunamadı")
            return@launch
        }

        try {
            val docsP1 = db.collection("games")
                .whereEqualTo("player1Id", uid)
                .whereEqualTo("status", GameStatus.IN_PROGRESS.toString())
                .get().await().documents

            val docsP2 = db.collection("games")
                .whereEqualTo("player2Id", uid)
                .whereEqualTo("status", GameStatus.IN_PROGRESS.toString())
                .get().await().documents

            val now = System.currentTimeMillis()
            val list = (docsP1 + docsP2).map { d ->
                val started  = d.getLong("startTimeMillis")  ?: now
                val expires  = d.getLong("expireTimeMillis") ?: now
                val isMyTurn = d.getString("currentTurnPlayerId") == uid
                val remainingLabel = formatRemaining(expires - now)
                val id       = d.id

                ActiveGameInfo(started, isMyTurn, remainingLabel, id)
            }.sortedByDescending { it.startedAt }

            _uiState.value =
                if (list.isEmpty()) ActiveGamesUiState.Empty
                else ActiveGamesUiState.Success(list)

        } catch (e: Exception) {
            _uiState.value = ActiveGamesUiState.Error(e.localizedMessage ?: "Bilinmeyen hata")
        }
    }

    private fun formatRemaining(ms: Long): String = when {
        ms <= 0L -> "Süre doldu"
        ms < TimeUnit.HOURS.toMillis(1) -> {
            val m = TimeUnit.MILLISECONDS.toMinutes(ms)
            val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
            String.format("%02d:%02d", m, s)
        }
        else -> {
            val h = TimeUnit.MILLISECONDS.toHours(ms)
            val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
            "${h}sa ${m}dk"
        }
    }
}
