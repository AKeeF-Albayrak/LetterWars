package com.example.letterwars.ui.screen.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow("")
    val uiState = _uiState.asStateFlow()

    fun register(email: String, password: String, username: String, age: Int) {
        viewModelScope.launch {
            val result = authRepository.registerUser(email, password, username, age)
            _uiState.value = if (result.isSuccess) {
                "Kayıt başarılı!"
            } else {
                "Hata: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }
}
