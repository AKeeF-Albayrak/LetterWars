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

    fun register(email: String, password: String, username: String) {
        viewModelScope.launch {

            if (!isValidEmail(email)) {
                _uiState.value = "Geçerli bir e-posta adresi giriniz (ör: yazlab2@kocaeli.edu.tr)"
                return@launch
            }

            if (!isValidPassword(password)) {
                _uiState.value = "Şifre en az 8 karakter, büyük/küçük harf ve rakam içermelidir."
                return@launch
            }

            val isUsernameTaken = authRepository.isUsernameTaken(username)
            if (isUsernameTaken) {
                _uiState.value = "Bu kullanıcı adı zaten kullanılıyor."
                return@launch
            }
            val result = authRepository.registerUser(email, password, username)
            _uiState.value = if (result.isSuccess) {
                "Kayıt başarılı!"
            } else {
                "Hata: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val regex = Regex("^[A-Za-z0-9+_.-]+@(.+)$")
        return regex.matches(email)
    }

    private fun isValidPassword(password: String): Boolean {
        val regex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}\$")
        return regex.matches(password)
    }

}
