package com.example.letterwars.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.firebase.FirebaseUserDataSource
import com.example.letterwars.data.model.User
import com.example.letterwars.data.repository.GameRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val userDataSource: FirebaseUserDataSource = FirebaseUserDataSource(),
    private val gameRepository: GameRepository = GameRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    init {
        loadAndUpdateUserBeforeExpose()
    }

    private fun loadAndUpdateUserBeforeExpose() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()

            gameRepository.checkTurnExpirationForUser(uid, currentTime)


            val user = userDataSource.getUser(uid)

            if (user != null) {

                val userGames = gameRepository.getGamesByUser(uid)

                val totalGames = userGames.count { it.status == com.example.letterwars.data.model.GameStatus.FINISHED }
                val wonGames = userGames.count { it.winnerId == uid }

                val updatedUser = user.copy(
                    totalGames = totalGames,
                    wonGames = wonGames
                )

                userDataSource.updateUserInDatabase(updatedUser)

                _user.value = updatedUser
            }
        }
    }
}
