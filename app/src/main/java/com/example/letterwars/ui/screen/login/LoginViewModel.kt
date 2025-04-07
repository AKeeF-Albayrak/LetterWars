package com.example.letterwars.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository = AuthRepository()) : ViewModel() {

    private val _uiState = MutableStateFlow("")
    val uiState = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            val result = authRepository.loginUserWithUsername(username, password)
            _uiState.value = if (result.isSuccess) "Giriş Başarılı" else "Hata: ${result.exceptionOrNull()?.message}"
        }
    }
}