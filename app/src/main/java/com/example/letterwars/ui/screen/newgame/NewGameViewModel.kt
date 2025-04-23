package com.example.letterwars.ui.screen.newgame

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class NewGameViewModel : ViewModel() {
    val playerId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
}
