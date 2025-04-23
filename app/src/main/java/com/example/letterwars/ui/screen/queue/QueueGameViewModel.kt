package com.example.letterwars.ui.screen.queue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.GameDuration
import com.example.letterwars.data.repository.GameRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class QueueViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repo = GameRepository()
    private val playerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    private val durationMinutes: Int =
        savedStateHandle.get<String>("duration")?.toIntOrNull() ?: 5
    val gameDuration: GameDuration = GameDuration.fromMinutes(durationMinutes)

    private val _isSearching = MutableStateFlow(true)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _gameId = MutableStateFlow<String?>(null)
    val gameId: StateFlow<String?> = _gameId

    init { joinQueue() }

    private fun joinQueue() = viewModelScope.launch {
        repo.joinMatchQueue(playerId, gameDuration) { ready, gameId ->
            if (ready && gameId != null) _gameId.value = gameId
            _isSearching.value = !ready
        }
    }

    fun leaveQueue() = viewModelScope.launch {
        repo.leaveMatchQueue(playerId, gameDuration) { /* ignore result */ }
        _isSearching.value = false
    }

    override fun onCleared() {
        viewModelScope.launch {
            leaveQueue()
        }
        super.onCleared()
    }

}
