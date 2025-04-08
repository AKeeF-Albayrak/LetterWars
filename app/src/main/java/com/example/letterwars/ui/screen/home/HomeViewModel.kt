package com.example.letterwars.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.letterwars.data.firebase.FirebaseUserDataSource
import com.example.letterwars.data.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val userDataSource: FirebaseUserDataSource = FirebaseUserDataSource()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    init {
        loadUser()
    }

    private fun loadUser() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            userDataSource.getUser(uid) { fetchedUser ->
                _user.value = fetchedUser
            }
        }
    }
}