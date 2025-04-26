package com.example.letterwars.ui.screen.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.model.Game
import com.example.letterwars.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(
    private val repository: GameRepository = GameRepository()
) : ViewModel() {

    private val _game = MutableStateFlow<Game?>(null)
    val game: StateFlow<Game?> = _game

    fun loadGame(gameId: String) {
        viewModelScope.launch {
            val fetchedGame = repository.getGame(gameId)
            _game.value = fetchedGame
        }
    }
}
